// PoTokenGenerator.kt

package com.example.musicfy.utils.potoken

import android.webkit.CookieManager
import com.example.musicfy.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    /**
     * Any well-known video id works to boot the engine; the resulting player token is discarded
     * and only the session token it mints alongside is kept.
     */
    private val PREWARM_VIDEO_ID = "dQw4w9WgXcQ"

    private companion object {
        const val MINT_COOLDOWN_MS = 5 * 60 * 1000L
    }

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false

    /**
     * After a failed mint we stop trying for a while. A cold start costs up to 45 seconds, and
     * paying that on every single track turns a broken BotGuard into a player that looks frozen.
     */
    @Volatile
    private var mintCooldownUntilMs = 0L

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /** True once a session token has been minted, so diagnostics can show the real state. */
    val hasSessionToken: Boolean
        get() = webPoTokenStreamingPot != null && webPoTokenGenerator?.isExpired == false

    /**
     * Boot the BotGuard engine ahead of playback.
     *
     * A cold start costs tens of seconds. Paying that inside the ExoPlayer resolver stalls the
     * first track, and if it gives up we fall through to a client that can only serve YouTube's
     * ~32 second preview window - so the cold start itself can look like a playback bug.
     */
    suspend fun preWarm(sessionId: String) {
        if (poTokenFor(PREWARM_VIDEO_ID, sessionId) != null) {
            Timber.tag(TAG).d("PoToken pre-warm complete")
        } else {
            Timber.tag(TAG).w("PoToken pre-warm did not produce a token")
        }
    }

    /**
     * Suspend entry point. Prefer this on the playback path - the blocking variant pins the
     * ExoPlayer loading thread for however long BotGuard takes.
     */
    suspend fun poTokenFor(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) return null
        if (System.currentTimeMillis() < mintCooldownUntilMs) {
            Timber.tag(TAG).d("Skipping poToken mint - cooling down after a failure")
            return null
        }
        return try {
            getWebClientPoToken(videoId, sessionId, forceRecreate = false)
        } catch (e: BadWebViewException) {
            Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
            webViewBadImpl = true
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation failed: ${e.javaClass.simpleName}: ${e.message}")
            startMintCooldown()
            null
        }
    }

    private fun startMintCooldown() {
        mintCooldownUntilMs = System.currentTimeMillis() + MINT_COOLDOWN_MS
    }

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        return try {
            Timber.tag(TAG).d("Calling runBlocking to generate poToken...")
            runBlocking { getWebClientPoToken(videoId, sessionId, forceRecreate = false) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    null
                }
                else -> throw e
            }
        }
    }

    private suspend fun getWebClientPoToken(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate ||
                        webPoTokenGenerator == null ||
                        webPoTokenGenerator!!.isExpired ||
                        webPoTokenStreamingPot == null ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
                    webPoTokenSessionId = sessionId

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    webPoTokenGenerator = null
                    webPoTokenStreamingPot = null

                    val generator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)
                    webPoTokenStreamingPot = try {
                        generator.generatePoToken(webPoTokenSessionId!!)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { runCatching { generator.close() } }
                        throw e
                    }
                    webPoTokenGenerator = generator
                    Timber.tag(TAG).d("Streaming poToken generated for sessionId=${webPoTokenSessionId?.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {

                throw throwable
            } else {

                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        Timber.tag(TAG).d("poToken generated: session=${streamingPot.take(20)}... video=${playerPot.take(20)}...")

        // Session token in the player request, video token on the stream url - see PoTokenResult.
        return PoTokenResult(
            playerRequestPoToken = streamingPot,
            streamingDataPoToken = playerPot,
        )
    }
}
