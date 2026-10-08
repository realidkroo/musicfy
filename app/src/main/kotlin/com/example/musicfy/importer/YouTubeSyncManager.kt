// YouTubeSyncManager.kt

package com.example.musicfy.importer

import android.content.Context
import com.example.musicfy.constants.YtPullHistoryKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.Event
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.extensions.isUserLoggedIn
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.utils.SyncUtils
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get
import com.music.innertube.YouTube
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class YtSyncState(
    val running: Boolean = false,
    val label: String = "",
    val message: String? = null,
)

/** The manual YouTube actions on the Sync page; automatic library sync stays in [SyncUtils]. */
@Singleton
class YouTubeSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Timber.e(e, "YouTube sync task failed") }
    )
    private var job: Job? = null

    private val _state = MutableStateFlow(YtSyncState())
    val state: StateFlow<YtSyncState> = _state.asStateFlow()

    fun syncNow() = launchTask("Syncing with YouTube…") {
        requireSignedIn()
        syncUtils.performFullSyncSuspend()
        if (context.dataStore.get(YtPullHistoryKey, false)) {
            setLabel("Copying YouTube history…")
            val copied = pullHistory()
            "Synced. $copied ${if (copied == 1) "play" else "plays"} copied from YouTube history."
        } else {
            "Synced your likes, library and playlists."
        }
    }

    fun pullHistoryNow() = launchTask("Copying YouTube history…") {
        requireSignedIn()
        val copied = pullHistory()
        if (copied == 0) "Nothing new in your YouTube history." else "$copied ${if (copied == 1) "play" else "plays"} copied from YouTube history."
    }

    fun uploadLocalPlaylists() = launchTask("Creating playlists on YouTube…") {
        requireSignedIn()
        val locals = database.playlistsByNameAsc().first().filter {
            it.playlist.browseId == null && it.playlist.isEditable && !it.playlist.isLocal &&
                it.playlist.id != PlaylistEntity.LIKED_PLAYLIST_ID && it.playlist.id != PlaylistEntity.DOWNLOADED_PLAYLIST_ID
        }
        if (locals.isEmpty()) return@launchTask "Every playlist is already on YouTube."
        var created = 0
        val failed = mutableListOf<String>()
        locals.forEachIndexed { index, playlist ->
            setLabel("Creating \"${playlist.playlist.name}\" (${index + 1}/${locals.size})")
            if (YouTubePlaylistMirror.mirror(database, playlist.playlist.id, playlist.playlist.name)) created++
            else failed += playlist.playlist.name
            delay(400)
        }
        buildString {
            append("Created $created ${if (created == 1) "playlist" else "playlists"} on YouTube.")
            if (failed.isNotEmpty()) append(" Failed: ${failed.joinToString(", ")}.")
        }
    }

    fun clearMessage() {
        if (!_state.value.running) _state.value = YtSyncState()
    }

    private class NotSignedInException : Exception("Sign in to YouTube first.")

    private fun requireSignedIn() {
        if (!context.isUserLoggedIn()) throw NotSignedInException()
    }

    private fun setLabel(label: String) {
        _state.value = _state.value.copy(label = label)
    }

    private fun launchTask(label: String, block: suspend () -> String) {
        if (job?.isActive == true) return
        _state.value = YtSyncState(running = true, label = label)
        job = scope.launch {
            var message: String? = null
            try {
                message = block()
            } catch (e: CancellationException) {
                message = "Stopped."
                throw e
            } catch (e: NotSignedInException) {
                message = e.message
            } catch (e: Exception) {
                Timber.e(e, "YouTube sync task failed")
                message = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _state.value = YtSyncState(running = false, message = message)
            }
        }
    }

    /**
     * Copies YouTube Music history into Musicfy's history. YouTube only says which day-ish bucket a
     * play is in ("Today", "Last week", "March 2026"), so each play gets a time inside its bucket.
     * A song that already has a play in that bucket is skipped — that covers songs played here (we
     * report those to YouTube) and earlier copies, so pulling again never doubles up.
     */
    private suspend fun pullHistory(): Int {
        // English section titles, whatever the app language, so the buckets can be read
        val page = YouTube.musicHistory(hlOverride = "en").getOrThrow()
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        val existing = HashMap<String, MutableList<LocalDate>>()
        database.events().first().forEach { existing.getOrPut(it.event.songId) { mutableListOf() } += it.event.timestamp.toLocalDate() }

        var copied = 0
        page.sections.orEmpty().forEach { section ->
            val bucket = bucketFor(section.title, today) ?: return@forEach
            section.songs.forEachIndexed { index, song ->
                val dates = existing[song.id]
                if (dates != null && dates.any { !it.isBefore(bucket.from) && !it.isAfter(bucket.to) }) return@forEachIndexed

                // newest first within a bucket; never in the future, never before the bucket
                val timestamp = bucket.anchor(now).minusMinutes(index * 4L)
                    .coerceAtLeast(bucket.from.atStartOfDay())
                val playTimeMs = (song.duration ?: 0) * 1000L
                database.withTransaction {
                    insert(song.toMediaMetadata())
                    insert(Event(songId = song.id, timestamp = timestamp, playTime = playTimeMs))
                    if (playTimeMs > 0) incrementTotalPlayTime(song.id, playTimeMs)
                }
                existing.getOrPut(song.id) { mutableListOf() } += timestamp.toLocalDate()
                copied++
            }
        }
        return copied
    }

    private class Bucket(val from: LocalDate, val to: LocalDate) {
        fun anchor(now: LocalDateTime): LocalDateTime =
            if (to == now.toLocalDate()) now else to.atTime(21, 0)
    }

    private fun bucketFor(title: String, today: LocalDate): Bucket? {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        return when (title.trim().lowercase(Locale.ENGLISH)) {
            "today" -> Bucket(today, today)
            "yesterday" -> today.minusDays(1).let { Bucket(it, it) }
            "this week" -> {
                val to = today.minusDays(2)
                if (to.isBefore(thisMonday)) Bucket(thisMonday, thisMonday) else Bucket(thisMonday, to)
            }
            "last week" -> Bucket(thisMonday.minusDays(7), thisMonday.minusDays(1))
            else -> parseMonth(title.trim(), today)?.let { month ->
                val end = minOf(month.atEndOfMonth(), today)
                Bucket(month.atDay(1), end)
            }
        }
    }

    private fun parseMonth(title: String, today: LocalDate): YearMonth? {
        runCatching { return YearMonth.parse(title, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }
        // a bare month name means the most recent one with that name
        return runCatching {
            val month = java.time.Month.valueOf(title.uppercase(Locale.ENGLISH))
            val thisYear = YearMonth.of(today.year, month)
            if (thisYear.isAfter(YearMonth.from(today))) thisYear.minusYears(1) else thisYear
        }.getOrNull()
    }

    private fun LocalDateTime.coerceAtLeast(min: LocalDateTime): LocalDateTime = if (isBefore(min)) min else this
}
