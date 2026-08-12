// musicimportservicekt
// runs the actual csv -> library import in the background: searches youtube
// parsed track picks the best match by title/artist similarity and writes
// playlists into the local database a hilt singleton with its own
// as syncutils) so the import keeps running even after the setup wizard that
// dismissed — thousands of songs at one search each is not something the

package com.example.musicfy.importer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.musicfy.R
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.db.entities.SongEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val matchedTracks: Int = 0,
    val currentLabel: String = "",
    val isDone: Boolean = false,
)

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

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    fun startImport(parsed: ParsedImport) {
        if (runningJob?.isActive == true) return
        runningJob = importScope.launch {
            val total = parsed.totalSongs
            _progress.value = ImportProgress(isRunning = true, totalTracks = total)
            var processed = 0
            var matched = 0

            for (track in parsed.likedSongs) {
                _progress.value = _progress.value.copy(currentLabel = track.title)
                if (importLikedTrack(track)) matched++
                processed++
                _progress.value = _progress.value.copy(processedTracks = processed, matchedTracks = matched)
            }

            for ((playlistName, tracks) in parsed.playlists) {
                val playlistEntity = PlaylistEntity(name = playlistName, isEditable = true)
                database.withTransaction { insert(playlistEntity) }
                var position = 0
                for (track in tracks) {
                    _progress.value = _progress.value.copy(currentLabel = "$playlistName — ${track.title}")
                    val song = importTrack(track)
                    if (song != null) {
                        matched++
                        database.withTransaction {
                            insert(PlaylistSongMap(songId = song.id, playlistId = playlistEntity.id, position = position))
                        }
                        position++
                    }
                    processed++
                    _progress.value = _progress.value.copy(processedTracks = processed, matchedTracks = matched)
                }
            }

            _progress.value = _progress.value.copy(isRunning = false, isDone = true, currentLabel = "")
            notifyImportComplete(matched, total)
        }
    }

    private suspend fun importTrack(track: ImportedTrack): SongEntity? {
        val best = searchBestMatch(track) ?: return null
        val mediaMetadata = best.toMediaMetadata()
        database.withTransaction { insert(mediaMetadata) }
        return mediaMetadata.toSongEntity()
    }

    private suspend fun importLikedTrack(track: ImportedTrack): Boolean {
        val best = searchBestMatch(track) ?: return false
        val mediaMetadata = best.toMediaMetadata()
        database.withTransaction { insert(mediaMetadata, SongEntity::toggleLike) }
        return true
    }

    private suspend fun searchBestMatch(track: ImportedTrack): SongItem? {
        val results = runCatching {
            YouTube.search("${track.title} ${track.artist}", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
        }.getOrDefault(emptyList())
        return pickBestMatch(track, results)
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
}

private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
private const val MIN_TITLE_SIMILARITY = 0.3

private fun pickBestMatch(track: ImportedTrack, candidates: List<SongItem>): SongItem? {
    if (candidates.isEmpty()) return null
    val targetTitle = tokenize(track.title)
    val targetArtist = tokenize(track.artist)

    val scored = candidates.map { candidate ->
        val titleScore = jaccardSimilarity(targetTitle, tokenize(candidate.title))
        val artistScore = candidate.artists.maxOfOrNull { jaccardSimilarity(targetArtist, tokenize(it.name)) } ?: 0.0
        candidate to (titleScore * 0.7 + artistScore * 0.3)
    }
    val best = scored.maxByOrNull { it.second } ?: return null
    val bestTitleScore = jaccardSimilarity(targetTitle, tokenize(best.first.title))
    return best.first.takeIf { bestTitleScore >= MIN_TITLE_SIMILARITY }
}

private fun tokenize(text: String): Set<String> =
    TOKEN_REGEX.findAll(text.lowercase()).map { it.value }.toSet()

private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.intersect(b).size
    val union = a.size + b.size - intersection
    return if (union == 0) 0.0 else intersection.toDouble() / union
}
