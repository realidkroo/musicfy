package com.example.musicfy.playback.custom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

// outcome of a real end to end probe of the monochrome playback backend
sealed interface MonochromeConnectivityResult {
    data object Success : MonochromeConnectivityResult
    data class Unreachable(val reason: String) : MonochromeConnectivityResult
    data object TurnstileNeeded : MonochromeConnectivityResult
}

// probes the monochrome playback backend before the user commits to turning the
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

    // any real http response even a 404 for this made up song proves the
    // was solved and the exchanged session was accepted that s what we re
    return if (result != null) {
        MonochromeConnectivityResult.Success
    } else {
        MonochromeConnectivityResult.TurnstileNeeded
    }
}
