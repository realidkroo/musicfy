// YouTubePlaylistQueue.kt

package com.example.musicfy.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = initialContinuation
    private var failedPageLoads = 0

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            if (initialSongs.isNotEmpty()) {
                Queue.Status(
                    title = playlistTitle,
                    items = initialSongs.map { it.toMediaItem() },
                    mediaItemIndex = startIndex,
                )
            } else {
                val playlistPage = YouTube.playlist(playlistId).getOrThrow()
                continuation = playlistPage.songsContinuation
                Queue.Status(
                    title = playlistPage.playlist.title,
                    items = playlistPage.songs.map { it.toMediaItem() },
                    mediaItemIndex = startIndex,
                )
            }
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            var lastException: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    val continuationPage = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                    continuation = continuationPage.continuation
                    failedPageLoads = 0
                    return@withContext continuationPage.songs.map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            if (++failedPageLoads >= MAX_FAILED_PAGE_LOADS) {
                continuation = null
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val MAX_FAILED_PAGE_LOADS = 3
        private const val RETRY_DELAY_MS = 750L
    }
}
