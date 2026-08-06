package com.example.musicfy.playback.custom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Outcome of a real, end-to-end probe of the Monochrome Playback backend (track-api.monochrome.tf):
 * a plain reachability check first, then an actual Turnstile solve + authenticated request, so the
 * three outcomes map onto genuinely different failure points rather than being guessed/simulated.
 */
sealed interface MonochromeConnectivityResult {
    data object Success : MonochromeConnectivityResult
    data class Unreachable(val reason: String) : MonochromeConnectivityResult
    data object TurnstileNeeded : MonochromeConnectivityResult
}

/**
 * Probes the Monochrome Playback backend before the user commits to turning the backend on.
 *
 * Two stages, in order, so a failure can be attributed to the right cause:
 * 1. A plain HEAD request to the API host — if this fails, the server itself is down/blocked
 *    (or this device has no route to it), independent of Turnstile entirely.
 * 2. A real Turnstile solve + authenticated /playback request (same WebView-based flow
 *    [MonochromePlaybackStreamFetcher] uses during actual playback, just with a throwaway query —
 *    the song won't be found, but any real HTTP response back proves the challenge was solved and
 *    the session was accepted). If the host was reachable in stage 1 but this still comes back
 *    null, the failure is specifically the Turnstile challenge, not the server.
 */
suspend fun testMonochromeConnectivity(
    context: Context,
    apiBaseUrl: String = "https://track-api.monochrome.tf",
    siteKey: String = "0x4AAAAAADgxqF6QVMm0GLHH",
): MonochromeConnectivityResult {
    val baseUrl = apiBaseUrl.trim().removeSuffix("/")

    val unreachableReason = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(baseUrl).head().build()
            client.newCall(request).execute().use { null }
        } catch (e: Exception) {
            Timber.tag("MonochromeConnTest").w(e, "Reachability check failed")
            e.javaClass.simpleName
        }
    }
    if (unreachableReason != null) {
        return MonochromeConnectivityResult.Unreachable(unreachableReason)
    }

    val solver = TurnstileSolver(context)
    val playbackBody = JSONObject().apply {
        put("song_name", "Connectivity Test")
        put("artist", "Musicfy")
    }.toString()

    val result = solver.fetchPlaybackWithTurnstile(
        siteKey = siteKey,
        exchangeUrl = "$baseUrl/auth/turnstile",
        playbackUrl = "$baseUrl/playback",
        playbackBodyJson = playbackBody,
    )

    // Any real HTTP response (even a 404 for this made-up song) proves the Turnstile challenge
    // was solved and the exchanged session was accepted — that's what we're actually testing.
    return if (result != null) {
        MonochromeConnectivityResult.Success
    } else {
        MonochromeConnectivityResult.TurnstileNeeded
    }
}
