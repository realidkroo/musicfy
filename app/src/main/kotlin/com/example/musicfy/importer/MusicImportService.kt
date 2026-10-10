// MusicImportService.kt

package com.example.musicfy.importer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.musicfy.R
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ImportedSong
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.extensions.isUserLoggedIn
import com.example.musicfy.models.toMediaMetadata
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Music import coroutine exception")
        }
    }
    private val importJob = SupervisorJob()
    private val importScope = CoroutineScope(Dispatchers.IO + importJob + exceptionHandler)
    private var runningJob: Job? = null

    /** Where the import being run comes from; read by the steps below, one import runs at a time. */
    @Volatile private var source: ImportSource = ImportSource.OTHER

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** Starts an import in the background; false if one is already running. */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean {
        if (isRunning) return false
        source = parsed.provider ?: ImportSource.OTHER
        _progress.value = ImportProgress(isRunning = true, totalTracks = parsed.totalSongs, source = parsed.provider)
        runningJob = importScope.launch {
            try {
                runImport(parsed, options)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
                val done = _progress.value
                notifyImportComplete(done.matchedTracks, done.totalTracks)
            } catch (e: CancellationException) {
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                _progress.update {
                    it.copy(isRunning = false, isDone = true, currentLabel = "", error = e.message ?: "Import failed")
                }
            }
        }
        return true
    }

    /** Stops after the current song; everything imported so far stays. */
    fun cancel() {
        runningJob?.cancel()
    }

    /** Forgets a finished import's summary so the next one starts clean. */
    fun clearFinished() {
        if (!isRunning) _progress.value = ImportProgress()
    }

    private suspend fun runImport(parsed: ParsedImport, options: ImportOptions) {
        val matcher = TrackMatcher()
        val signedIn = context.isUserLoggedIn()
        val mirror = options.mirrorToYouTube && signedIn

        if (parsed.likedSongs.isNotEmpty()) importLiked(matcher, parsed.likedSongs, mirror)
        if (parsed.librarySongs.isNotEmpty()) importLibrarySongs(matcher, parsed.librarySongs)

        for ((name, tracks) in parsed.playlists) {
            coroutineContext.ensureActive()
            importPlaylist(matcher, name, tracks, parsed.playlistCovers[name], mirror, signedIn)
        }
    }

    private suspend fun importLiked(matcher: TrackMatcher, tracks: List<ImportedTrack>, mirror: Boolean) {
        val matched = matchAll(matcher, tracks, playlistName = null, label = "Liked songs").distinctBy { it.item.id }
        if (matched.isEmpty()) return

        database.withTransaction {
            matched.forEach { insert(it.item.toMediaMetadata()) }
        }
        val dates = sourceDates(matched)
        fileIntoLibrary(matched.map { it.item.id }, dates)

        // liked when they were liked on the source, so they sit among the existing likes in that order
        val newlyLiked = mutableListOf<String>()
        matched.forEach { (_, item) ->
            val song = database.getSongById(item.id)?.song ?: return@forEach
            if (!song.liked) {
                database.update(song.copy(liked = true, likedDate = dates.getValue(item.id)))
                newlyLiked += item.id
            }
        }
        _progress.update { it.copy(likedAdded = it.likedAdded + newlyLiked.size) }

        // remembered either way: a like that fails now (or before signing in) goes up on the next sync
        PendingYouTubeLikes.add(context, newlyLiked)
        if (mirror && newlyLiked.isNotEmpty()) {
            _progress.update { it.copy(currentLabel = "Liking songs on YouTube") }
            val pushed = mutableListOf<String>()
            newlyLiked.forEach { id ->
                coroutineContext.ensureActive()
                if (YouTube.likeVideo(id, true).isSuccess) pushed += id
                delay(LIKE_DELAY_MS)
            }
            PendingYouTubeLikes.remove(context, pushed)
            if (pushed.size < newlyLiked.size) {
                _progress.update { it.copy(youtubeFailures = it.youtubeFailures + "Liked songs (${newlyLiked.size - pushed.size} will retry on next sync)") }
            }
        }
    }

    /** An account's whole song library: into the Library's Songs, and nowhere else. */
    private suspend fun importLibrarySongs(matcher: TrackMatcher, tracks: List<ImportedTrack>) {
        val matched = matchAll(matcher, tracks, playlistName = null, label = "Library songs").distinctBy { it.item.id }
        if (matched.isEmpty()) return
        database.withTransaction {
            matched.forEach { insert(it.item.toMediaMetadata()) }
        }
        fileIntoLibrary(matched.map { it.item.id }, sourceDates(matched))
    }

    /**
     * When each song was added on the source, so the Library and Liked songs list them in the
     * source's order. A song the source gave no date gets one just before the song above it - a
     * source without dates lists newest first.
     */
    private fun sourceDates(matched: List<Matched>): Map<String, LocalDateTime> {
        val now = LocalDateTime.now()
        val dates = HashMap<String, LocalDateTime>()
        var previous: LocalDateTime? = null
        matched.forEach { (track, item) ->
            val date = track.addedAtMs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                ?: previous?.minusSeconds(1)
                ?: now
            dates[item.id] = date
            previous = date
        }
        return dates
    }

    /**
     * Imports from before library songs had their own place made them a playlist named after the
     * service ("Apple Music library"), and older ones never put anything in the Library at all.
     * Their songs are filed into the Library and tagged with the service, once. The playlist stays:
     * it may have been edited since, and deleting it is the user's call.
     */
    suspend fun repairEarlierImports() {
        if (isRunning) return
        val legacy = database.playlistsByNameAsc().first()
            .filter { it.playlist.isEditable && it.playlist.browseId == null && it.playlist.name in LegacyLibraryPlaylists }
        for (playlist in legacy) {
            val songIds = database.playlistSongs(playlist.id).first().map { it.song.id }
            if (songIds.isEmpty()) continue
            val tagged = database.importedSongIds().toSet()
            val missing = songIds.filter { id ->
                id !in tagged || database.getSongById(id)?.song?.inLibrary == null
            }
            if (missing.isEmpty()) continue
            source = LegacyLibraryPlaylists.getValue(playlist.playlist.name)
            fileIntoLibrary(missing)
        }
        source = ImportSource.OTHER
    }

    /** [cover] is the playlist's own picture on the source; without one the app shows its songs' covers. */
    private suspend fun importPlaylist(
        matcher: TrackMatcher,
        name: String,
        tracks: List<ImportedTrack>,
        cover: String?,
        mirror: Boolean,
        signedIn: Boolean,
    ) {
        val matched = matchAll(matcher, tracks, playlistName = name, label = name).map { it.item }
        val songIds = matched.map { it.id }.distinct()
        // nothing found: don't leave an empty playlist behind (an intentionally empty one has no tracks at all)
        if (songIds.isEmpty() && tracks.isNotEmpty()) return

        // re-importing the same playlist (e.g. a Musicfy backup) tops it up instead of duplicating it
        val existing = database.playlistsByNameAsc().first()
            .firstOrNull { it.playlist.name == name && it.playlist.isEditable }
            ?.playlist

        val playlist = existing ?: PlaylistEntity(
            name = name,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
            thumbnailUrl = cover,
        )

        val start = if (existing == null) 0 else database.playlistSongs(playlist.id).first().size
        var newIds: List<String> = emptyList()
        database.withTransaction {
            if (existing == null) insert(playlist)
            // topping up one that has no picture of its own: it gets the source's
            else if (existing.thumbnailUrl == null && cover != null) update(existing.copy(thumbnailUrl = cover))
            matched.distinctBy { it.id }.forEach { insert(it.toMediaMetadata()) }
            val already = if (existing == null || songIds.isEmpty()) emptySet() else playlistDuplicates(playlist.id, songIds).toSet()
            newIds = songIds.filterNot { it in already }
            newIds.forEachIndexed { index, songId ->
                insert(PlaylistSongMap(songId = songId, playlistId = playlist.id, position = start + index))
            }
        }
        fileIntoLibrary(songIds)
        if (existing == null) _progress.update { it.copy(playlistsCreated = it.playlistsCreated + 1) }

        val browseId = existing?.browseId
        when {
            // already linked to YouTube: keep both sides in step
            browseId != null && signedIn && newIds.isNotEmpty() -> {
                _progress.update { it.copy(currentLabel = "Adding to \"$name\" on YouTube") }
                YouTube.addVideosToPlaylist(browseId, newIds).onFailure {
                    _progress.update { p -> p.copy(youtubeFailures = p.youtubeFailures + name) }
                }
            }
            browseId == null && mirror && (newIds.isNotEmpty() || existing == null) -> {
                _progress.update { it.copy(currentLabel = "Creating \"$name\" on YouTube") }
                mirrorPlaylist(playlist.id, name)
            }
        }
    }

    /**
     * Puts imported songs where all music lives. The Library's Songs, Artists and Albums list what
     * is `inLibrary`, so an imported song that only sat in a playlist - or in Liked songs - never
     * showed up there. Each is also recorded as imported from [source], which is what the
     * "Imported from" cards list and what the YouTube library sync reads to leave these songs be.
     *
     * Local only: this does not touch the user's YouTube library (see mirrorToYouTube for that).
     */
    private suspend fun fileIntoLibrary(songIds: List<String>, addedAt: Map<String, LocalDateTime> = emptyMap()) {
        if (songIds.isEmpty()) return
        val now = LocalDateTime.now()
        val from = source.key
        database.withTransaction {
            songIds.distinct().forEach { id ->
                val song = getSongById(id)?.song ?: return@forEach
                if (song.inLibrary == null) update(song.copy(inLibrary = addedAt[id] ?: now))
                insert(ImportedSong(songId = id, source = from, importedAt = now))
            }
        }
    }

    private suspend fun mirrorPlaylist(playlistId: String, name: String) {
        if (!YouTubePlaylistMirror.mirror(database, playlistId, name)) {
            _progress.update { it.copy(youtubeFailures = it.youtubeFailures + name) }
        }
    }

    /** A source track and the YouTube Music song found for it. */
    private data class Matched(val track: ImportedTrack, val item: SongItem)

    /** Matches in parallel (a few searches at a time) and keeps the source order. */
    private suspend fun matchAll(
        matcher: TrackMatcher,
        tracks: List<ImportedTrack>,
        playlistName: String?,
        label: String,
    ): List<Matched> = coroutineScope {
        val semaphore = Semaphore(MATCH_PARALLELISM)
        tracks.map { track ->
            async {
                semaphore.withPermit {
                    val item = try {
                        matcher.match(track)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Match failed for ${track.title}")
                        null
                    }
                    _progress.update { p ->
                        p.copy(
                            processedTracks = p.processedTracks + 1,
                            matchedTracks = p.matchedTracks + if (item != null) 1 else 0,
                            currentLabel = "$label — ${track.title}",
                            unmatched = if (item == null) p.unmatched + UnmatchedTrack(track, playlistName) else p.unmatched,
                        )
                    }
                    item?.let { Matched(track, it) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun notifyImportComplete(matched: Int, total: Int) {
        val channelId = "music_import"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Music import", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.musicfy_notification)
            .setContentTitle("Import finished")
            .setContentText("Matched $matched of $total songs")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { notificationManager.notify(4242, notification) }
    }

    private companion object {
        /** What earlier versions named the playlist they made of a service's whole library. */
        val LegacyLibraryPlaylists = mapOf("Apple Music library" to ImportSource.APPLE_MUSIC)

        const val MATCH_PARALLELISM = 4
        const val LIKE_DELAY_MS = 120L
    }
}
