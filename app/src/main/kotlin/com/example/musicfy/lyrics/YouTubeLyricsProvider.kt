// YouTubeLyricsProvider.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint

object YouTubeLyricsProvider : LyricsProvider {
    // Must match the registry key (see YouTubeSubtitleLyricsProvider): a spaced name never
    // matched the registry, so this source could not be picked or marked as in use.
    override val name = "YouTubeMusic"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        runCatching {
            val nextResult = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
            YouTube
                .lyrics(
                    endpoint = nextResult.lyricsEndpoint
                        ?: throw IllegalStateException("Lyrics endpoint not found"),
                ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
        }
}
