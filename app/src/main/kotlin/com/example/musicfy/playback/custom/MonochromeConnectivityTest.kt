package com.example.musicfy.playback.custom

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

sealed interface MonochromeConnectivityResult {
    data object Success : MonochromeConnectivityResult
    data class Unreachable(val reason: String) : MonochromeConnectivityResult
    data object TurnstileNeeded : MonochromeConnectivityResult
}

suspend fun testMonochromeConnectivity(
    context: Context,
    apiBaseUrl: String = MonochromePlaybackStreamFetcher.DEFAULT_API_BASE_URL,
    siteKey: String = MonochromePlaybackStreamFetcher.DEFAULT_SITE_KEY,
    apiToken: String = MonochromePlaybackStreamFetcher.DEFAULT_API_TOKEN,
): MonochromeConnectivityResult {
    val baseUrl = apiBaseUrl.trim().removeSuffix("/")

    val unreachableReason = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url("$baseUrl/api/v2/track/").get().build()
            client.newCall(request).execute().use { null }
        } catch (e: Exception) {
            Timber.tag("MonochromeConnTest").w(e, "Reachability check failed")
            e.javaClass.simpleName
        }
    }
    if (unreachableReason != null) {
        return MonochromeConnectivityResult.Unreachable(unreachableReason)
    }

    // Reaching the host is not enough - the API only answers with a valid session
    // token, so solve the challenge once here and cache it for playback to reuse.
    val solver = TurnstileSolver(context)
    val jwt = solver.getSessionJwt(
        siteKey = siteKey,
        exchangeUrl = "$baseUrl/api/auth/turnstile",
        apiToken = apiToken,
        forceRefresh = true,
        ignoreCooldown = true,
    )

    return if (jwt != null) {
        MonochromeConnectivityResult.Success
    } else {
        MonochromeConnectivityResult.TurnstileNeeded
    }
}
