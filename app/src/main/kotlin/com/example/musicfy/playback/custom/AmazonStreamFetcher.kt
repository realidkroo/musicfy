package com.example.musicfy.playback.custom

import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.constants.*
import com.example.musicfy.db.MusicDatabase
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

class AmazonStreamFetcher(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    private val database: MusicDatabase,
    private val httpClient: OkHttpClient,
    private val turnstileSolver: TurnstileSolver? = null,
    private val onStatusUpdate: (String) -> Unit = {}
) {

    suspend fun fetchStreamUrl(mediaId: String): CustomStreamResult? {
        onStatusUpdate("Checking if the song is available on Amazon...")

        val prefs = dataStore.data.first()
        val enableAmazonMusicBackend = prefs[EnableAmazonMusicBackendKey] ?: false

        if (!enableAmazonMusicBackend) {
            return null
        }

        onStatusUpdate("The song is found on Amazon Music, preparing stream...")

        // amz.geeked.wtf is the only instance the reference web client actually uses/defaults
        // to; amz-music(2).geeked.wtf are non-canonical hosts that just waste retry attempts.
        val baseUrls = listOf("https://amz.geeked.wtf")

        val customInstance = prefs[com.example.musicfy.constants.AmazonMusicApiUrlKey]?.takeIf { it.isNotBlank() }
            ?.trim()?.removeSuffix("/")
        val instanceCandidates = customInstance?.let { listOf(it) } ?: baseUrls.shuffled()

        val turnstileBypassToken = prefs[TurnstileBypassTokenKey]?.takeIf { it.isNotBlank() }
        val turnstileSiteKey = prefs[TurnstileSiteKeyKey]?.takeIf { it.isNotBlank() } ?: "0x4AAAAAADgxqF6QVMm0GLHH"

        val song = database.song(mediaId).first()

        val title = song?.title ?: mediaId
        val artist = song?.artists?.joinToString(", ") { it.name } ?: ""
        val album = song?.album?.title ?: ""
        val duration = song?.song?.duration?.takeIf { it > 0 }?.toString() ?: ""

        val spatialAudio = prefs[EnableSpatialAudioKey] ?: false
        val audioQualityRaw = prefs[AudioQualityKey] ?: "LOSSLESS"

        val amazonQuality = if (spatialAudio) {
            "DOLBY_ATMOS"
        } else {
            when (audioQualityRaw) {
                "AUTO" -> "UHD"
                "HI_RES_LOSSLESS" -> "UHD"
                "LOSSLESS" -> "HD"
                "HIGH" -> "SD_HIGH"
                "LOW" -> "SD_LOW"
                else -> "HD"
            }
        }

        val params = mutableListOf<String>()
        params.add("track=${java.net.URLEncoder.encode(title, "UTF-8")}")
        if (artist.isNotEmpty()) params.add("artist=${java.net.URLEncoder.encode(artist, "UTF-8")}")
        if (album.isNotEmpty()) params.add("album=${java.net.URLEncoder.encode(album, "UTF-8")}")
        if (duration.isNotEmpty()) params.add("duration=$duration")
        params.add("quality=$amazonQuality")
        if (turnstileBypassToken != null) {
            params.add("bypass_token=$turnstileBypassToken")
        }

        for (instance in instanceCandidates) {
            try {
                val fetchUrl = "$instance/api/track/?${params.joinToString("&")}"
                Timber.tag("AmazonFetcher").d("Fetching from $fetchUrl")

                var responseBodyStr: String? = null

                if (turnstileBypassToken != null || turnstileSolver == null) {
                    // Bypass token supplied directly as a query param — no Turnstile needed.
                    val requestBuilder = Request.Builder().url(fetchUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .addHeader("Origin", "https://monochrome.tf")
                        .addHeader("Referer", "https://monochrome.tf/")

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(500)
                        Timber.tag("AmazonFetcher").w("Fetch failed on $instance: ${response.code} body=$errBody")
                        response.close()
                        continue
                    }
                    responseBodyStr = response.body?.string()
                } else {
                    // Solve Turnstile, exchange for a JWT, and fetch — all from the same WebView
                    // context, since the exchanged JWT is fingerprint-bound to that context.
                    onStatusUpdate("Solving Cloudflare Turnstile...")
                    val exchangeUrl = "$instance/api/auth/turnstile"
                    var fetchResult = turnstileSolver.fetchWithTurnstile(turnstileSiteKey, exchangeUrl, fetchUrl, forceRefresh = false)

                    if (fetchResult != null && (fetchResult.first == 401 || fetchResult.first == 428)) {
                        Timber.tag("AmazonFetcher").w("Received ${fetchResult.first} on $instance, refreshing Turnstile...")
                        onStatusUpdate("Turnstile expired or rejected (Code ${fetchResult.first}), refreshing...")
                        fetchResult = turnstileSolver.fetchWithTurnstile(turnstileSiteKey, exchangeUrl, fetchUrl, forceRefresh = true)
                    }

                    if (fetchResult == null || fetchResult.first !in 200..299) {
                        Timber.tag("AmazonFetcher").w("WebView fetch failed on $instance: ${fetchResult?.first} body=${fetchResult?.second?.take(500)}")
                        continue
                    }

                    onStatusUpdate("Turnstile token acquired!")
                    responseBodyStr = fetchResult.second
                }

                if (responseBodyStr != null) {
                    val json = JSONObject(responseBodyStr)
                    val result = extractStreamUrl(json)
                    if (result != null) {
                        onStatusUpdate("Ready to play from Amazon!")
                        Timber.tag("AmazonFetcher").i("Found stream on $instance: ${result.streamUrl}")
                        return result.copy(source = "Amazon Music")
                    }
                }
            } catch (e: Exception) {
                Timber.tag("AmazonFetcher").w(e, "Error using instance $instance, trying next")
            }
        }

        return null
    }

    private fun extractStreamUrl(json: JSONObject): CustomStreamResult? {
        val payload = when {
            json.has("stream_url") -> json
            json.has("data") -> json.optJSONObject("data")
            json.has("track") -> json.optJSONObject("track")
            json.has("result") -> json.optJSONObject("result")
            else -> null
        } ?: return null

        val streamUrl = payload.optString("stream_url").takeIf { it.isNotBlank() } ?: return null

        val decryptionKey = payload.optString("decryption_key").takeIf { it.isNotBlank() }
            ?: payload.optString("decryptionKey").takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("decryption")?.optString("key")?.takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("drm")?.optString("decryption_key")?.takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("drm")?.optString("decryptionKey")?.takeIf { it.isNotBlank() }
            ?: json.optString("decryption_key").takeIf { it.isNotBlank() }
            ?: json.optString("decryptionKey").takeIf { it.isNotBlank() }

        // We assume CENC (which will trigger DASH behavior) if a decryption key is present
        return CustomStreamResult(
            streamUrl = streamUrl,
            decryptionKey = decryptionKey,
            isDash = decryptionKey != null,
            source = "Amazon Music"
        )
    }
}
