package com.example.musicfy.playback.custom

import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.constants.EnableMonochromePlaybackBackendKey
import com.example.musicfy.constants.MonochromePlaybackApiUrlKey
import com.example.musicfy.constants.TurnstileSiteKeyKey
import com.example.musicfy.db.MusicDatabase
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONObject
import timber.log.Timber

// monochrome's in-house lossless playback api (track-apimonochrometf) matching
class MonochromePlaybackStreamFetcher(
    private val dataStore: androidx.datastore.core.DataStore<Preferences>,
    private val database: MusicDatabase,
    private val httpClient: OkHttpClient,
    private val turnstileSolver: TurnstileSolver? = null,
    private val onStatusUpdate: (String) -> Unit = {}
) {
    suspend fun fetchStreamUrl(mediaId: String): CustomStreamResult? {
        val prefs = dataStore.data.first()
        val enabled = prefs[EnableMonochromePlaybackBackendKey] ?: true
        if (!enabled) {
            Timber.tag("MonochromePlayback").d("Backend disabled, skipping")
            return null
        }

        val apiBaseUrl = prefs[MonochromePlaybackApiUrlKey]?.takeIf { it.isNotBlank() }
            ?.trim()?.removeSuffix("/")
            ?: "https://track-api.monochrome.tf"

        val siteKey = prefs[TurnstileSiteKeyKey]?.takeIf { it.isNotBlank() } ?: "0x4AAAAAADgxqF6QVMm0GLHH"

        val song = database.song(mediaId).first()
        if (song == null) {
            Timber.tag("MonochromePlayback").w("No song metadata for $mediaId, skipping")
            return null
        }
        val title = song.title.takeIf { it.isNotBlank() } ?: run {
            Timber.tag("MonochromePlayback").w("Blank title for $mediaId, skipping")
            return null
        }
        val artist = song.artists.joinToString(", ") { it.name }
        if (artist.isBlank()) {
            Timber.tag("MonochromePlayback").w("Blank artist for $mediaId ($title), skipping")
            return null
        }
        val duration = song.song.duration.takeIf { it > 0 }
        Timber.tag("MonochromePlayback").d("Requesting playback for '$title' by '$artist' via $apiBaseUrl")

        val requestBody = JSONObject().apply {
            put("song_name", title)
            put("artist", artist)
            if (duration != null) put("duration", duration)
        }.toString()

        if (turnstileSolver == null) return null

        onStatusUpdate("Checking Monochrome Playback...")

        val exchangeUrl = "$apiBaseUrl/auth/turnstile"
        val playbackUrl = "$apiBaseUrl/playback"

        for (attempt in 0 until 2) {
            // solve turnstile exchange for a session and post /playback — all from the
            // webview context since the exchanged session is fingerprint-bound to that
            val fetchResult = turnstileSolver.fetchPlaybackWithTurnstile(
                siteKey, exchangeUrl, playbackUrl, requestBody, forceRefresh = attempt > 0
            )

            if (fetchResult == null) {
                Timber.tag("MonochromePlayback").w("Turnstile/playback fetch failed (attempt $attempt)")
                continue
            }

            val (status, body) = fetchResult
            if (status == 401 && attempt == 0) {
                Timber.tag("MonochromePlayback").w("Session rejected, refreshing and retrying")
                continue
            }
            if (status == 429) {
                Timber.tag("MonochromePlayback").w("Rate limited")
                return null
            }
            if (status !in 200..299) {
                Timber.tag("MonochromePlayback").w("Playback request failed: $status body=${body?.take(500)}")
                return null
            }

            val url = body?.let { JSONObject(it).optString("url").takeIf { u -> u.isNotBlank() } } ?: return null

            onStatusUpdate("Ready to play from Monochrome Playback!")
            Timber.tag("MonochromePlayback").i("Found stream: $url")
            return CustomStreamResult(streamUrl = url, source = "Monochrome Playback")
        }

        return null
    }
}
