// SongVersions.kt

package com.example.musicfy.utils

import com.example.musicfy.ui.player.normalizeCanvasArtistName
import com.example.musicfy.ui.player.normalizeCanvasSongTitle
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * lists the app puts together itself (YouTube's feed rows, the charts) play songs, not videos. a
 * music video in one is swapped for the song version, which brings the album cover with it. a video
 * with no song version keeps playing its own audio but gets the album cover from iTunes and is
 * treated as a song, so the player shows that cover instead of starting the video.
 *
 * the video rows (Live Shows, Music Videos, the Videos search tab) never come through here.
 */
object SongVersions {

    // video id -> what plays in its place, for the whole session; a miss maps to the video itself
    private val resolved = ConcurrentHashMap<String, SongItem>()
    private val gate = Semaphore(4)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun preferSongs(items: List<YTItem>): List<YTItem> = coroutineScope {
        items.map { item ->
            async {
                if (item is SongItem && item.isVideoSong) songVersion(item) else item
            }
        }.awaitAll().distinctBy { it.id }
    }

    suspend fun songVersion(video: SongItem): SongItem {
        resolved[video.id]?.let { return it }
        val result = gate.withPermit {
            findSong(video) ?: withItunesCover(video)
        } ?: video
        resolved[video.id] = result
        return result
    }

    private suspend fun findSong(video: SongItem): SongItem? {
        val artist = normalizeCanvasArtistName(video.artists.joinToString { it.name })
        val title = normalizeCanvasSongTitle(stripArtistPrefix(video.title, artist))
        if (title.length < 2 || artist.length < 2) return null

        val songs = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .filterNot { it.isVideoSong }
        return songs.firstOrNull { song ->
            sameTitle(normalizeCanvasSongTitle(song.title), title) &&
                song.artists.any { sameArtist(normalizeCanvasArtistName(it.name), artist) }
        }
    }

    private suspend fun withItunesCover(video: SongItem): SongItem? = withContext(Dispatchers.IO) {
        val artist = normalizeCanvasArtistName(video.artists.joinToString { it.name })
        val title = normalizeCanvasSongTitle(stripArtistPrefix(video.title, artist))
        if (title.length < 2 || artist.length < 2) return@withContext null

        val cover = runCatching {
            val term = URLEncoder.encode("$title $artist", Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=5")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val results = JSONObject(response.body.string()).optJSONArray("results")
                    ?: return@use null
                (0 until results.length())
                    .mapNotNull { results.optJSONObject(it) }
                    .firstOrNull { track ->
                        sameTitle(normalizeCanvasSongTitle(track.optString("trackName")), title) &&
                            sameArtist(normalizeCanvasArtistName(track.optString("artistName")), artist)
                    }
                    ?.optString("artworkUrl100")
                    ?.takeIf { it.isNotBlank() }
                    // the same artwork at full size
                    ?.replace("100x100bb", "1200x1200bb")
            }
        }.getOrNull() ?: return@withContext null

        video.copy(thumbnail = cover, musicVideoType = MUSIC_VIDEO_TYPE_ATV)
    }

    // "Artist - Title" is how most video titles read
    private fun stripArtistPrefix(title: String, artist: String): String =
        title.replace(Regex("^\\s*${Regex.escape(artist)}\\s*[-–—|:]\\s*", RegexOption.IGNORE_CASE), "")

    private fun sameTitle(a: String, b: String) =
        a.equals(b, ignoreCase = true) ||
            (a.length >= 4 && b.length >= 4 && (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)))

    private fun sameArtist(a: String, b: String) =
        a.isNotEmpty() && b.isNotEmpty() && (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true))
}
