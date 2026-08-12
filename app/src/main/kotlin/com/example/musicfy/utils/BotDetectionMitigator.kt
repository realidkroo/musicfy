// botdetectionmitigatorkt
// this thing is for bot detection mitigator

package com.example.musicfy.utils

import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.example.musicfy.constants.VisitorDataKey
import com.example.musicfy.utils.cipher.CipherDeobfuscator
import com.example.musicfy.utils.PlaybackLogManager
import com.example.musicfy.utils.PlaybackLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

// manages bot detection mitigation by tracking playback failures and rotating
object BotDetectionMitigator {
    private const val TAG = "BotDetectionMitigator"

    private val failureCount = AtomicInteger(0)

    // error reasons that indicate geographic restriction – not a bot signal
    // important keep these specific to avoid false positives
    private val GEO_ERROR_SIGNATURES = listOf(
        "not available in your country",
        "not available in your region",
        "not available in this country",
        "not available in this region",
        "geo-restricted",
        "GEO_RESTRICTED",
        "NOT_AVAILABLE_IN_THIS_COUNTRY",
        "only available in certain countries",
        "country restriction",
        "region restriction",
    )

    // error reasons that strongly suggest bot ip flagging by youtube
    private val BOT_ERROR_SIGNATURES = listOf(
        "Sign in to confirm",
        "confirm you're not a bot",
        "automated queries",
        "Error 2000",
        "403",
        "This content isn't available on this device",
    )

    // call this when a playback error occurs returns true if rotation might help
    fun notifyPlaybackFailure(isLoggedIn: Boolean, errorMessage: String? = null): Boolean {
        if (isLoggedIn) return false
        if (isGeoError(errorMessage)) return false

        failureCount.incrementAndGet()
        return true
    }

    // call this when a track starts playing successfully
    fun notifyPlaybackSuccess() {
        failureCount.set(0)
    }

    // rotates the guest session by obtaining a fresh visitordata token while
    suspend fun rotateGuestSession() {
        Timber.tag(TAG).i("Rotating guest session to bypass bot detection...")
        PlaybackLogManager.log(
            PlaybackLogLevel.BOT, 
            "Rotating guest session", 
            "Bypassing bot detection by refreshing visitorData (locale preserved)"
        )
        
        withContext(Dispatchers.IO) {
            // snapshot locale so the new token is issued for the user s actual region
            val currentLocale = YouTube.locale

            // clear only visitordata minimal session change
            YouTube.visitorData = null
            
            YouTube.refreshVisitorData().onSuccess { newData ->
                Timber.tag(TAG).i("New visitorData obtained successfully for region ${currentLocale.gl}.")
                
                // persist to datastore
                CipherDeobfuscator.appContext?.dataStore?.edit { settings ->
                    settings[VisitorDataKey] = newData
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to refresh visitorData during rotation")
                // restore locale context if refresh failed
                YouTube.locale = currentLocale
            }
        }
        
        failureCount.set(0)
    }

    // returns true if message matches known geographic restriction patterns
    fun isGeoError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return GEO_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    // returns true if message matches known bot detection signatures
    fun isBotDetectionError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return BOT_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    fun reset() {
        failureCount.set(0)
    }
}
