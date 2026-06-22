package com.example.musicfy.playback.custom

import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.constants.*
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.utils.get
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Resolves streams via Custom APIs (Amazon Music, Tidal, Deezer, Qobuz)
 * based on user settings.
 */
class CustomStreamFetcher(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    private val database: MusicDatabase,
    private val httpClient: OkHttpClient
) {
    suspend fun fetchStreamUrl(videoId: String): String? {
        val prefs = dataStore.data.first()
        val customApiEnabled = prefs[EnableCustomApiKey] ?: false
        
        if (!customApiEnabled) {
            return null // Fallback to InnerTube/YouTube
        }

        val amazonApiUrl = prefs[AmazonMusicApiUrlKey]?.trim()?.removeSuffix("/") ?: ""
        val tidalToAsinUrl = prefs[TidalToAsinUrlKey]?.trim()?.removeSuffix("/") ?: ""
        val bypassToken = prefs[TurnstileBypassTokenKey]?.trim() ?: ""

        if (amazonApiUrl.isEmpty() || tidalToAsinUrl.isEmpty()) {
            Timber.tag("CustomStreamFetcher").w("API URLs are missing for Amazon stream resolution")
            return null
        }

        val song = database.song(videoId).first()
        val artistsText = song?.artists?.joinToString(" ") { it.name } ?: ""
        if (song == null || song.title.isBlank() || artistsText.isBlank()) {
            Timber.tag("CustomStreamFetcher").w("Song metadata missing for search")
            return null
        }

        val query = "${song.title} $artistsText"
        Timber.tag("CustomStreamFetcher").d("Looking up ASIN for query: $query")
        
        val asin = getAsin(tidalToAsinUrl, query)
        if (asin == null) {
            Timber.tag("CustomStreamFetcher").w("Could not resolve ASIN for query: $query")
            return null
        }

        val spatialAudio = prefs[EnableSpatialAudioKey] ?: false
        val audioQualityRaw = prefs[AudioQualityKey] ?: "LOSSLESS"
        
        // Map app quality settings to API parameters
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

        Timber.tag("CustomStreamFetcher").d("Fetching stream for ASIN $asin at quality $amazonQuality")
        return getAmazonStreamUrl(amazonApiUrl, asin, amazonQuality, bypassToken)
    }

    private fun getAsin(baseUrl: String, query: String): String? {
        try {
            val url = "$baseUrl/api/search/songs".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("query", query)
                ?.build() ?: return null
                
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            if (json.optBoolean("success", false)) {
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val first = data.optJSONObject(0)
                    return first?.optString("id")
                }
            }
        } catch (e: Exception) {
            Timber.tag("CustomStreamFetcher").e(e, "Failed to fetch ASIN")
        }
        return null
    }

    private fun getAmazonStreamUrl(baseUrl: String, asin: String, quality: String, bypassToken: String): String? {
        try {
            val urlBuilder = "$baseUrl/api/track/$asin".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("quality", quality)
            
            if (bypassToken.isNotBlank()) {
                urlBuilder?.addQueryParameter("bypass_token", bypassToken)
            }
            
            val request = Request.Builder().url(urlBuilder!!.build()).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            return json.optString("stream_url").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.tag("CustomStreamFetcher").e(e, "Failed to fetch Amazon stream")
        }
        return null
    }
}
