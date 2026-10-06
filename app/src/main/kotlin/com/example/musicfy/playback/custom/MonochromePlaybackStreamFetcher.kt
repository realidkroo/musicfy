package com.example.musicfy.playback.custom

import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.AudioQualityKey
import com.example.musicfy.constants.EnableMonochromePlaybackBackendKey
import com.example.musicfy.constants.EnableSpatialAudioKey
import com.example.musicfy.constants.MonochromePlaybackApiTokenKey
import com.example.musicfy.constants.MonochromePlaybackApiUrlKey
import com.example.musicfy.constants.TurnstileSiteKeyKey
import com.example.musicfy.db.MusicDatabase
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Resolves a stream from Monochrome's Unified Playback API.
 *
 * The request is a metadata lookup (title / artist / album / duration) rather than an
 * id lookup, so it works for any song the app knows about. Access is gated by a
 * Cloudflare Turnstile challenge that is exchanged for a short-lived session JWT.
 */
class MonochromePlaybackStreamFetcher(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    private val database: MusicDatabase,
    private val httpClient: OkHttpClient,
    private val turnstileSolver: TurnstileSolver? = null,
    private val onStatusUpdate: (String) -> Unit = {}
) {
    companion object {
        const val DEFAULT_API_BASE_URL = "https://music-api.geeked.wtf"
        const val DEFAULT_API_TOKEN = "amp_29b2lIr4mze4tK-P8QDOxfMZ9anCgJ9_uGTUks3nIyo"
        const val DEFAULT_SITE_KEY = "0x4AAAAAADgxqF6QVMm0GLHH"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * Hosts that used to serve this API. A stored value pointing at one of them is
         * stale, so it is ignored in favour of [DEFAULT_API_BASE_URL].
         */
        private val LEGACY_API_BASE_URLS = setOf(
            "https://amz.geeked.wtf",
            "https://track-api.monochrome.tf",
            "https://mono.geeked.wtf",
        )

        /**
         * Canonical quality tokens accepted by the Unified Playback API.
         *
         * The API ladder is DOLBY_ATMOS_* > HI_RES_LOSSLESS > LOSSLESS > HIGH > LOW.
         * AUTO maps to the top of the stereo ladder, matching Monochrome's own client.
         */
        internal fun qualityToken(quality: AudioQuality, spatialAudio: Boolean): String = when {
            quality == AudioQuality.DOLBY_ATMOS || spatialAudio -> "DOLBY_ATMOS_EAC3_HIGH"
            quality == AudioQuality.HI_RES_LOSSLESS -> "HI_RES_LOSSLESS"
            quality == AudioQuality.LOSSLESS -> "LOSSLESS"
            quality == AudioQuality.LOW -> "LOW"
            // The API has no MEDIUM tier; HIGH is its lossy ceiling.
            quality == AudioQuality.MEDIUM || quality == AudioQuality.HIGH -> "HIGH"
            else -> "HI_RES_LOSSLESS"
        }
    }

    suspend fun fetchStreamUrl(mediaId: String): CustomStreamResult? {
        val prefs = dataStore.data.first()
        // Defaults on: the whole custom-backend chain is already gated by
        // EnableMonochromeBackendKey in MusicService.
        if (prefs[EnableMonochromePlaybackBackendKey] == false) {
            Timber.tag("MonochromePlayback").d("Backend disabled, skipping")
            return null
        }

        val storedBaseUrl = prefs[MonochromePlaybackApiUrlKey]?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val apiBaseUrl = storedBaseUrl?.takeUnless { it in LEGACY_API_BASE_URLS } ?: DEFAULT_API_BASE_URL
        if (storedBaseUrl != null && storedBaseUrl != apiBaseUrl) {
            Timber.tag("MonochromePlayback").w("Ignoring retired API host $storedBaseUrl, using $apiBaseUrl")
        }

        val apiToken = prefs[MonochromePlaybackApiTokenKey]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_API_TOKEN
        val siteKey = prefs[TurnstileSiteKeyKey]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_SITE_KEY

        val song = database.song(mediaId).first()
        if (song == null) {
            Timber.tag("MonochromePlayback").w("No song metadata for $mediaId, skipping")
            return null
        }
        val title = song.title.takeIf { it.isNotBlank() } ?: run {
            Timber.tag("MonochromePlayback").w("Blank title for $mediaId, skipping")
            return null
        }
        val artist = song.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() } ?: run {
            Timber.tag("MonochromePlayback").w("Blank artist for $mediaId ($title), skipping")
            return null
        }

        val quality = prefs[AudioQualityKey]?.let { raw ->
            AudioQuality.entries.firstOrNull { it.name == raw }
        } ?: AudioQuality.HI_RES_LOSSLESS
        val spatialAudio = prefs[EnableSpatialAudioKey] == true

        val lookupUrl = "$apiBaseUrl/api/v2/track/".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("track", title)
            ?.addQueryParameter("artist", artist)
            ?.apply {
                song.album?.title?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
                song.song.duration.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) }
                addQueryParameter("intent", "stream")
                addQueryParameter("quality", qualityToken(quality, spatialAudio))
            }
            ?.build()?.toString() ?: return null

        if (turnstileSolver == null) {
            Timber.tag("MonochromePlayback").w("No Turnstile solver available, cannot authenticate")
            return null
        }

        Timber.tag("MonochromePlayback").d("Requesting '$title' by '$artist' via $apiBaseUrl")
        onStatusUpdate("Checking Monochrome Playback...")

        val exchangeUrl = "$apiBaseUrl/api/auth/turnstile"

        // A session JWT is reused until it expires, so a Turnstile challenge is solved
        // about once an hour instead of once per track.
        repeat(2) { attempt ->
            val jwt = turnstileSolver.getSessionJwt(
                siteKey = siteKey,
                exchangeUrl = exchangeUrl,
                apiToken = apiToken,
                forceRefresh = attempt > 0,
            )
            if (jwt == null) {
                Timber.tag("MonochromePlayback").w("Could not obtain a Monochrome session token")
                return null
            }

            val request = Request.Builder()
                .url(lookupUrl)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $apiToken")
                .header("X-Turnstile-JWT", jwt)
                .header("Cache-Control", "no-store")
                // Cloudflare fronts this API and rejects clients that look automated
                // (error 1010), so present the same browser identity as the WebView
                // that solved the challenge.
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Origin", "https://monochrome.tf")
                .header("Referer", "https://monochrome.tf/")
                .build()

            val (status, body) = try {
                httpClient.newCall(request).execute().use { it.code to it.body?.string() }
            } catch (e: Exception) {
                Timber.tag("MonochromePlayback").w(e, "Lookup request failed")
                return null
            }

            when {
                // Session rejected - drop the cached JWT and solve a fresh challenge once.
                (status == 401 || status == 428) && attempt == 0 -> {
                    Timber.tag("MonochromePlayback").w("Session rejected ($status), refreshing token")
                    turnstileSolver.clearSessionJwt()
                    return@repeat
                }
                status == 429 -> {
                    Timber.tag("MonochromePlayback").w("Rate limited by Monochrome Playback")
                    return null
                }
                status == 404 || status == 502 -> {
                    Timber.tag("MonochromePlayback").i("Track not resolvable by Monochrome Playback")
                    return null
                }
                status !in 200..299 -> {
                    Timber.tag("MonochromePlayback").w("Lookup failed: $status body=${body?.take(500)}")
                    return null
                }
            }

            val result = body?.let { parsePlaybackEnvelope(it) }
            if (result != null) {
                onStatusUpdate("Ready to play from Monochrome Playback!")
                Timber.tag("MonochromePlayback").i("Resolved ${result.source} stream for '$title'")
                return result
            }

            Timber.tag("MonochromePlayback").w("Envelope contained no playable resource")
            return null
        }

        return null
    }

    /**
     * Picks the first playable resource out of a Unified Playback envelope.
     *
     * Shape: `{ "schema_version": "2.x", "playback": [ { "url", "kind", "delivery", ... } ] }`
     */
    internal fun parsePlaybackEnvelope(body: String): CustomStreamResult? {
        val envelope = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val schemaMajor = envelope.optString("schema_version").substringBefore('.')
        if (schemaMajor.isNotBlank() && schemaMajor !in setOf("1", "2")) {
            Timber.tag("MonochromePlayback").w("Unsupported schema version: ${envelope.optString("schema_version")}")
            return null
        }

        val playback = envelope.optJSONArray("playback") ?: return null

        for (i in 0 until playback.length()) {
            val resource = playback.optJSONObject(i) ?: continue
            val url = resource.optString("url").takeIf { it.isNotBlank() } ?: continue
            val kind = resource.optString("kind")
            val delivery = resource.optString("delivery")

            if (kind != "audio" && kind != "manifest") continue
            if (delivery != "direct" && delivery != "dash" && delivery != "hls") continue

            return CustomStreamResult(
                streamUrl = url,
                decryptionKey = resource.optString("decryption_key").takeIf { it.isNotBlank() },
                keyId = resource.optString("key_id").takeIf { it.isNotBlank() },
                isDash = delivery == "dash",
                source = "Monochrome (${resource.optString("quality").ifBlank { "unknown" }})",
            )
        }

        return null
    }
}
