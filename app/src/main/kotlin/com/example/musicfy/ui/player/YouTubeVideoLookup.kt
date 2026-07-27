// YouTubeVideoLookup.kt
// Finds the official YouTube music video (not a lyrics video / user upload) for a track by
// title+artist, resolves it to a playable video stream URL, and caches the result per mediaId
// — same shape as CanvasArtworkUtils.kt's CanvasArtworkPlaybackCache.

package com.example.musicfy.ui.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.example.musicfy.utils.YTPlayerUtils

/**
 * Caches the resolved video stream URL per track. A cached `null` means "looked up, no official
 * music video found" — distinct from "never looked up" (absence of the key) — so a track without
 * an OMV isn't re-searched on every recomposition.
 */
object YouTubeVideoUrlCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, String?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(mediaId: String): Boolean = map.containsKey(mediaId)

    @Synchronized
    fun get(mediaId: String): String? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, streamUrl: String?) {
        if (mediaId.isBlank()) return
        map[mediaId] = streamUrl
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

/**
 * Searches YouTube Music's "Videos" tab for `title artist`, picks the first official-music-video
 * result whose title/artist plausibly matches, and resolves it to a playable stream URL. Returns
 * null on no match or any resolution failure — this is a cosmetic feature, so failures should
 * fall back to the static cover, not surface an error.
 */
suspend fun findOfficialMusicVideoStreamUrl(title: String, artist: String): String? {
    if (title.isBlank() || artist.isBlank()) return null

    val results = YouTube.search(query = "$title $artist", filter = YouTube.SearchFilter.FILTER_VIDEO)
        .getOrNull()?.items.orEmpty()

    val normalizedTitle = normalizeCanvasSongTitle(title)
    val normalizedArtist = normalizeCanvasArtistName(artist)

    val match = results
        .filterIsInstance<SongItem>()
        .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
        .firstOrNull { candidate ->
            val candidateTitle = normalizeCanvasSongTitle(candidate.title)
            val candidateArtist = normalizeCanvasArtistName(candidate.artists.joinToString { it.name })
            (candidateTitle.contains(normalizedTitle, ignoreCase = true) || normalizedTitle.contains(candidateTitle, ignoreCase = true)) &&
                (candidateArtist.contains(normalizedArtist, ignoreCase = true) || normalizedArtist.contains(candidateArtist, ignoreCase = true))
        }
        ?: return null

    return YTPlayerUtils.resolveVideoStreamUrl(match.id).getOrNull()
}
