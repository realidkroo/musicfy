// YouTubeSubtitleLyricsProvider.kt

package com.example.musicfy.lyrics

import android.content.Context
import com.music.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    // Must match the registry key, not the display name: this string is what gets persisted on
    // LyricsEntity.provider and later fed back through LyricsProviderRegistry, which only knows
    // the un-spaced form. See LyricsProviderRegistry.getDisplayName for the pretty version.
    override val name = "YouTubeSubtitle"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
