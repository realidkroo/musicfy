package com.example.musicfy.utils

import android.util.Log
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.ui.utils.resize
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ArtistImageResolver {
    private const val TAG = "ArtistImageResolver"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val memoryCache = ConcurrentHashMap<String, String>()

    suspend fun resolveThumbnail(artist: ArtistEntity): String? {
        val normalizedName = artist.name.trim()
        if (normalizedName.isBlank()) return null

        val key = "${artist.id}:${normalizedName.lowercase(Locale.ROOT)}"
        if (memoryCache.containsKey(key)) return memoryCache[key]

        val resolved = fetchAppleArtwork(normalizedName)
            ?: fetchYouTubeThumbnail(artist)

        if (!resolved.isNullOrBlank()) {
            memoryCache[key] = resolved
        }
        return resolved
    }

    private fun fetchAppleArtwork(artistName: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode(artistName, Charsets.UTF_8.name())
            val url = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=25"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val results = JSONObject(body).optJSONArray("results") ?: return@runCatching null
                var bestScore = 0
                var bestArtwork: String? = null

                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val resultArtist = item.optString("artistName").trim()
                    val artwork = item.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                    val score = artistMatchScore(artistName, resultArtist)
                    if (score > bestScore) {
                        bestScore = score
                        bestArtwork = artwork
                            .replace(Regex("(\\d+)x(\\d+)bb"), "600x600bb")
                            .replace(Regex("(\\d+)x(\\d+)"), "600x600")
                    }
                }

                bestArtwork.takeIf { bestScore >= 8 }
            }
        }.onFailure {
            Log.d(TAG, "Apple artwork lookup failed for $artistName", it)
        }.getOrNull()
    }

    private suspend fun fetchYouTubeThumbnail(artist: ArtistEntity): String? {
        val directThumbnail = if (artist.isYouTubeArtist && !artist.isPrivatelyOwnedArtist) {
            YouTube.artist(artist.id)
                .getOrNull()
                ?.artist
                ?.thumbnail
                ?.resize(544, 544)
        } else {
            null
        }

        if (!directThumbnail.isNullOrBlank()) return directThumbnail

        return YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
            .getOrNull()
            ?.items
            ?.filterIsInstance<ArtistItem>()
            ?.mapNotNull { item ->
                val thumbnail = item.thumbnail?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                artistMatchScore(artist.name, item.title) to thumbnail.resize(544, 544)
            }
            ?.filter { it.first >= 5 }
            ?.maxByOrNull { it.first }
            ?.second
    }

    private fun artistMatchScore(expected: String, actual: String): Int {
        val expectedClean = expected.normalizedArtistName()
        val actualClean = actual.normalizedArtistName()
        return when {
            expectedClean.isBlank() || actualClean.isBlank() -> 0
            expectedClean == actualClean -> 12
            expectedClean.contains(actualClean) || actualClean.contains(expectedClean) -> 8
            expectedClean.split(" ").intersect(actualClean.split(" ").toSet()).size >= 2 -> 5
            else -> 0
        }
    }

    private fun String.normalizedArtistName(): String =
        lowercase(Locale.ROOT)
            .replace(Regex("\\b(feat|ft|with)\\.?\\b.*"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
