// youtubevideolookupkt
// finds the official youtube music video not a lyrics video user upload
// title+artist resolves it to a playable video stream url and caches the
// same shape as canvasartworkutilskt s canvasartworkplaybackcache

package com.example.musicfy.ui.player

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.example.musicfy.utils.YTPlayerUtils

// resolved official music video result the video s own id needed separately to
data class OfficialMusicVideo(
    val videoId: String,
    val streamUrl: String,
)

// caches the resolved video per track a cached null means looked up no
object YouTubeVideoUrlCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, OfficialMusicVideo?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(mediaId: String): Boolean = map.containsKey(mediaId)

    @Synchronized
    fun get(mediaId: String): OfficialMusicVideo? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, video: OfficialMusicVideo?) {
        if (mediaId.isBlank()) return
        map[mediaId] = video
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

// searches youtube music s videos tab for title artist picks the first
suspend fun findOfficialMusicVideo(title: String, artist: String): OfficialMusicVideo? {
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

    val streamUrl = YTPlayerUtils.resolveVideoStreamUrl(match.id).getOrNull() ?: return null
    return OfficialMusicVideo(videoId = match.id, streamUrl = streamUrl)
}
