package com.example.musicfy.playback.custom

import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.constants.EnableMonochromeBackendKey
import com.example.musicfy.constants.MonochromeInstancesKey
import com.example.musicfy.constants.AudioQualityKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.utils.get
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

class MonochromeStreamFetcher(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    private val database: MusicDatabase,
    private val httpClient: OkHttpClient
) {
    suspend fun fetchStreamUrl(mediaId: String): CustomStreamResult? {
        val prefs = dataStore.data.first()
        val monochromeEnabled = prefs[EnableMonochromeBackendKey] ?: false

        if (!monochromeEnabled) {
            return null
        }

        val instancesString = prefs[MonochromeInstancesKey]?.takeIf { it.isNotBlank() }
            ?: "https://api.monochrome.tf,https://monochrome-api.samidy.com"
        val instances = instancesString.split(",").map { it.trim().removeSuffix("/") }.filter { it.isNotEmpty() }
        
        if (instances.isEmpty()) {
            throw IOException("Monochrome Backend is enabled but no instances are configured.")
        }

        val song = database.song(mediaId).first()
        val artistsText = song?.artists?.joinToString(" ") { it.name } ?: ""
        if (song == null || song.title.isBlank() || artistsText.isBlank()) {
            throw IOException("Song metadata missing for search in Monochrome backend")
        }

        val query = "${song.title} $artistsText"
        Timber.tag("MonochromeFetcher").d("Looking up track in Monochrome for query: $query")

        val audioQualityRaw = prefs[AudioQualityKey] ?: "LOSSLESS"
        val monochromeQuality = when (audioQualityRaw) {
            "AUTO" -> "LOSSLESS"
            "HI_RES_LOSSLESS" -> "HI_RES_LOSSLESS"
            "LOSSLESS" -> "LOSSLESS"
            "HIGH" -> "HIGH"
            "LOW" -> "LOW"
            else -> "LOSSLESS"
        }
        val manifestFormats = when (monochromeQuality) {
            "HI_RES_LOSSLESS" -> listOf("FLAC_HIRES")
            "LOSSLESS" -> listOf("FLAC")
            "HIGH" -> listOf("AACLC")
            "LOW" -> listOf("HEAACV1")
            else -> listOf("FLAC")
        }

        val streamingInstancesString = prefs[com.example.musicfy.constants.StreamingInstancesKey]?.takeIf { it.isNotBlank() }
            ?: "https://hifi.geeked.wtf,https://maus.qqdl.site,https://vogel.qqdl.site,https://katze.qqdl.site,https://hund.qqdl.site,https://wolf.qqdl.site"
        val streamingInstances = streamingInstancesString.split(",").map { it.trim().removeSuffix("/") }.filter { it.isNotEmpty() }

        var lastError: Exception? = null

        // try instances in a shuffled order for basic load balancing or just
        for (instance in instances.shuffled()) {
            try {
                // 1 search for the track
                val searchUrl = "$instance/search/".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("s", query)
                    ?.build() ?: continue

                val searchRequest = Request.Builder().url(searchUrl).build()
                val searchResponse = httpClient.newCall(searchRequest).execute()
                
                if (!searchResponse.isSuccessful) {
                    Timber.tag("MonochromeFetcher").w("Search failed on $instance: ${searchResponse.code}")
                    continue
                }

                val searchBody = searchResponse.body?.string() ?: continue
                val searchJson = JSONObject(searchBody)
                // monochrome's "s" (track) search returns items under dataitems
                val items = searchJson.optJSONObject("data")?.optJSONArray("items")

                if (items == null || items.length() == 0) {
                    Timber.tag("MonochromeFetcher").w("No track found for query: $query on $instance")
                    continue
                }

                val trackId = items.optJSONObject(0)?.optString("id")
                if (trackId.isNullOrBlank()) {
                    continue
                }

                Timber.tag("MonochromeFetcher").d("Found track ID $trackId for query $query")

                // 2 fetch the stream manifest using streaming instances
                for (streamingInstance in streamingInstances.shuffled()) {
                    try {
                        val manifestUrlBuilder = "$streamingInstance/trackManifests/".toHttpUrlOrNull()?.newBuilder()
                            ?.addQueryParameter("id", trackId)
                            ?.addQueryParameter("quality", monochromeQuality)
                            ?.addQueryParameter("adaptive", "false")
                            ?: continue
                        manifestFormats.forEach { manifestUrlBuilder.addQueryParameter("formats", it) }
                        val manifestUrl = manifestUrlBuilder.build()

                        val manifestRequest = Request.Builder().url(manifestUrl).build()
                        val manifestResponse = httpClient.newCall(manifestRequest).execute()

                        if (!manifestResponse.isSuccessful) {
                            Timber.tag("MonochromeFetcher").w("Manifest fetch failed on $streamingInstance: ${manifestResponse.code}")
                            continue
                        }

                        val manifestBody = manifestResponse.body?.string() ?: continue
                        // response is a json:api envelope: { data: { data: { attributes: { uri:
                        val signedManifestUri = JSONObject(manifestBody)
                            .optJSONObject("data")
                            ?.optJSONObject("data")
                            ?.optJSONObject("attributes")
                            ?.optString("uri")
                            ?.takeIf { it.isNotBlank() }

                        if (signedManifestUri == null) {
                            Timber.tag("MonochromeFetcher").w("No manifest uri in response from $streamingInstance")
                            continue
                        }

                        val signedManifestRequest = Request.Builder().url(signedManifestUri).build()
                        val signedManifestResponse = httpClient.newCall(signedManifestRequest).execute()
                        if (!signedManifestResponse.isSuccessful) {
                            Timber.tag("MonochromeFetcher").w("Signed manifest fetch failed on $streamingInstance: ${signedManifestResponse.code}")
                            continue
                        }

                        val signedManifestBody = signedManifestResponse.body?.string() ?: continue
                        val result = extractUrlFromManifest(signedManifestBody)
                        if (result != null) {
                            Timber.tag("MonochromeFetcher").i("Found stream on $streamingInstance: ${result.streamUrl}")
                            return result.copy(source = "Monochrome")
                        }
                    } catch (e: Exception) {
                        Timber.tag("MonochromeFetcher").w(e, "Error using streaming instance $streamingInstance")
                    }
                }

                // 3 fallback: legacy /track/ endpoint hosted directly on the search api
                // (base64-encoded manifest) used when the dedicated streaming/cdn hosts are
                try {
                    val legacyUrl = "$instance/track/".toHttpUrlOrNull()?.newBuilder()
                        ?.addQueryParameter("id", trackId)
                        ?.addQueryParameter("quality", monochromeQuality)
                        ?.build()

                    if (legacyUrl != null) {
                        val legacyRequest = Request.Builder().url(legacyUrl).build()
                        val legacyResponse = httpClient.newCall(legacyRequest).execute()

                        if (legacyResponse.isSuccessful) {
                            val legacyBody = legacyResponse.body?.string()
                            val result = legacyBody?.let { extractUrlFromManifest(it) }
                            if (result != null) {
                                Timber.tag("MonochromeFetcher").i("Found stream via legacy endpoint on $instance: ${result.streamUrl}")
                                return result.copy(source = "Monochrome")
                            }
                        } else {
                            Timber.tag("MonochromeFetcher").w("Legacy track fetch failed on $instance: ${legacyResponse.code}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("MonochromeFetcher").w(e, "Error using legacy track endpoint on $instance")
                }
            } catch (e: Exception) {
                Timber.tag("MonochromeFetcher").e(e, "Error using instance $instance")
                lastError = e
            }
        }

        throw IOException("Monochrome backend forced: Stream not found or all instances failed. Last error: ${lastError?.message}")
    }

    private fun extractUrlFromManifest(manifestText: String): CustomStreamResult? {
        try {
            val trimmed = manifestText.trim()

            // the signed manifest uri is usually fetched as plain text: either a json
            // object with a "urls" array or a raw dash mpd xml document
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val urls = json.optJSONArray("urls")
                if (urls != null && urls.length() > 0) {
                    return CustomStreamResult(streamUrl = urls.getString(0), source = "Monochrome")
                }
            }

            if (trimmed.contains("<MPD")) {
                val dashUrl = "data:application/dash+xml;base64," + android.util.Base64.encodeToString(
                    manifestText.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                return CustomStreamResult(streamUrl = dashUrl, isDash = true, source = "Monochrome")
            }

            // fall back to treating it as base64 encoded json/xml in case an instance
            // still returns the older inline-manifest format
            val decodedBytes = android.util.Base64.decode(manifestText, android.util.Base64.DEFAULT)
            val decodedString = String(decodedBytes)

            if (decodedString.trim().startsWith("{")) {
                val json = JSONObject(decodedString)
                val urls = json.optJSONArray("urls")
                if (urls != null && urls.length() > 0) {
                    return CustomStreamResult(streamUrl = urls.getString(0), source = "Monochrome")
                }
            }

            if (decodedString.contains("<MPD")) {
                val dashUrl = "data:application/dash+xml;base64," + android.util.Base64.encodeToString(decodedBytes, android.util.Base64.NO_WRAP)
                return CustomStreamResult(streamUrl = dashUrl, isDash = true, source = "Monochrome")
            }

            // try matching a url with regex as a last resort
            val urlPattern = java.util.regex.Pattern.compile("https?://[\\w\\-.~:?#\\[\\]@!$&'()*+,;=%/]+")
            val matcher = urlPattern.matcher(decodedString)
            if (matcher.find()) {
                return CustomStreamResult(streamUrl = matcher.group(), source = "Monochrome")
            }
        } catch (e: Exception) {
            Timber.tag("MonochromeFetcher").e(e, "Failed to decode manifest: $manifestText")
        }
        return null
    }
}
