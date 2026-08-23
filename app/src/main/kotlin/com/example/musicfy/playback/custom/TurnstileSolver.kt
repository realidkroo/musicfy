package com.example.musicfy.playback.custom

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class TurnstileSolver(private val context: Context) {

    private var cachedToken: String? = null
    private var tokenTimestamp: Long = 0L

    private val solveMutex = Mutex()

    @Synchronized
    fun getCachedToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && (now - tokenTimestamp) < 4 * 60 * 1000) {
            return cachedToken
        }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun getTurnstileToken(siteKey: String, forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            getCachedToken()?.let { return it }
        }

        return solveMutex.withLock {

        if (!forceRefresh) {
            getCachedToken()?.let { return@withLock it }
        }

        withTimeoutOrNull(15000) {
            val deferred = CompletableDeferred<String?>()

            Handler(Looper.getMainLooper()).post {
                var webView: WebView? = null
                try {
                    webView = WebView(context.applicationContext)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    webView.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onTokenReceived(token: String) {
                            Timber.tag("TurnstileSolver").d("Turnstile token received: ${token.take(15)}...")
                            synchronized(this@TurnstileSolver) {
                                cachedToken = token
                                tokenTimestamp = System.currentTimeMillis()
                            }
                            if (!deferred.isCompleted) {
                                deferred.complete(token)
                            }
                            destroyWebView(webView)
                        }

                        @JavascriptInterface
                        fun onError(error: String) {
                            Timber.tag("TurnstileSolver").w("Turnstile error: $error")
                            if (!deferred.isCompleted) {
                                deferred.complete(null)
                            }
                            destroyWebView(webView)
                        }
                    }, "AndroidTurnstile")

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Timber.tag("TurnstileSolver").d("Turnstile page loaded")
                        }
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
                        </head>
                        <body style="margin:0; padding:0; background:transparent;">
                            <div id="turnstile-container"></div>
                            <script>
                                window.onload = function() {
                                    if (window.turnstile) {
                                        render();
                                    } else {
                                        var checkInterval = setInterval(function() {
                                            if (window.turnstile) {
                                                clearInterval(checkInterval);
                                                render();
                                            }
                                        }, 100);
                                    }
                                };

                                function render() {
                                    try {
                                        turnstile.render('#turnstile-container', {
                                            sitekey: '$siteKey',
                                            callback: function(token) {
                                                if (window.AndroidTurnstile) {
                                                    window.AndroidTurnstile.onTokenReceived(token);
                                                }
                                            },
                                            'error-callback': function(err) {
                                                if (window.AndroidTurnstile) {
                                                    window.AndroidTurnstile.onError(err || 'Turnstile error');
                                                }
                                            }
                                        });
                                    } catch(e) {
                                        if (window.AndroidTurnstile) {
                                            window.AndroidTurnstile.onError(e.message || 'Render exception');
                                        }
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    webView.loadDataWithBaseURL("https://monochrome.tf", html, "text/html", "UTF-8", null)

                } catch (e: Exception) {
                    Timber.tag("TurnstileSolver").e(e, "Failed to initialize WebView for Turnstile")
                    if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                    destroyWebView(webView)
                }
            }

            deferred.await()
        }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchWithTurnstile(siteKey: String, exchangeUrl: String, targetUrl: String, forceRefresh: Boolean = false): Pair<Int, String?>? {
        return solveMutex.withLock {

        withTimeoutOrNull(20000) {
            val deferred = CompletableDeferred<Pair<Int, String?>?>()

            Handler(Looper.getMainLooper()).post {
                var webView: WebView? = null
                try {
                    webView = WebView(context.applicationContext)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    webView.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onTokenReceived(token: String) {
                            Timber.tag("TurnstileSolver").d("Turnstile token received: ${token.take(15)}...")
                        }

                        @JavascriptInterface
                        fun onFetchComplete(status: Int, responseBody: String?) {
                            Timber.tag("TurnstileSolver").d("Fetch complete, status: $status")
                            if (!deferred.isCompleted) {
                                deferred.complete(Pair(status, responseBody))
                            }
                            destroyWebView(webView)
                        }

                        @JavascriptInterface
                        fun onError(error: String) {
                            Timber.tag("TurnstileSolver").w("Turnstile/Fetch error: $error")
                            if (!deferred.isCompleted) {
                                deferred.complete(null)
                            }
                            destroyWebView(webView)
                        }
                    }, "AndroidTurnstile")

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Timber.tag("TurnstileSolver").d("Turnstile page loaded for fetch")
                        }
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
                        </head>
                        <body style="margin:0; padding:0; background:transparent;">
                            <div id="turnstile-container"></div>
                            <script>
                                async function doFetch(token) {
                                    try {
                                        const exchangeResponse = await fetch('$exchangeUrl', {
                                            method: 'POST',
                                            headers: { 'Content-Type': 'application/json' },
                                            body: JSON.stringify({ cf_turnstile_response: token })
                                        });
                                        const exchangeText = await exchangeResponse.text();
                                        if (!exchangeResponse.ok) {
                                            if (window.AndroidTurnstile) {
                                                window.AndroidTurnstile.onError('Exchange failed: ' + exchangeResponse.status + ' ' + exchangeText);
                                            }
                                            return;
                                        }
                                        let jwt;
                                        try {
                                            jwt = JSON.parse(exchangeText).access_token;
                                        } catch (parseErr) {}
                                        if (!jwt) {
                                            if (window.AndroidTurnstile) {
                                                window.AndroidTurnstile.onError('Exchange returned no access_token');
                                            }
                                            return;
                                        }

                                        const response = await fetch('$targetUrl', {
                                            headers: {
                                                'X-Turnstile-JWT': jwt
                                            }
                                        });
                                        const text = await response.text();
                                        if (window.AndroidTurnstile) {
                                            window.AndroidTurnstile.onFetchComplete(response.status, text);
                                        }
                                    } catch(e) {
                                        if (window.AndroidTurnstile) {
                                            window.AndroidTurnstile.onError(e.message || 'Fetch exception');
                                        }
                                    }
                                }

                                window.onload = function() {
                                    if (window.turnstile) {
                                        render();
                                    } else {
                                        var checkInterval = setInterval(function() {
                                            if (window.turnstile) {
                                                clearInterval(checkInterval);
                                                render();
                                            }
                                        }, 100);
                                    }
                                };

                                function render() {
                                    try {
                                        turnstile.render('#turnstile-container', {
                                            sitekey: '$siteKey',
                                            callback: function(token) {
                                                if (window.AndroidTurnstile) {
                                                    window.AndroidTurnstile.onTokenReceived(token);
                                                }
                                                doFetch(token);
                                            },
                                            'error-callback': function(err) {
                                                if (window.AndroidTurnstile) {
                                                    window.AndroidTurnstile.onError(err || 'Turnstile error');
                                                }
                                            }
                                        });
                                    } catch(e) {
                                        if (window.AndroidTurnstile) {
                                            window.AndroidTurnstile.onError(e.message || 'Render exception');
                                        }
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    webView.loadDataWithBaseURL("https://monochrome.tf", html, "text/html", "UTF-8", null)

                } catch (e: Exception) {
                    Timber.tag("TurnstileSolver").e(e, "Failed to initialize WebView for Turnstile")
                    if (!deferred.isCompleted) {
                        deferred.complete(null)
                    }
                    destroyWebView(webView)
                }
            }

            deferred.await()
        }
        }
    }

    /**
     * Session JWTs are shared process-wide: the settings connectivity check, the
     * challenge dialog and the playback fetcher each build their own solver, but a
     * challenge solved in one should count for all of them.
     */
    private companion object SessionCache {
        var cachedSessionJwt: String? = null
        var sessionJwtExpirySeconds: Long = 0L

        /**
         * When a headless solve fails we stop attempting for a while. Otherwise every
         * single track would sit through the full WebView timeout before falling back
         * to YouTube, which looks like the player has frozen.
         */
        var headlessCooldownUntilMs: Long = 0L
        const val HEADLESS_COOLDOWN_MS = 10 * 60 * 1000L

        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /** True when a recent headless attempt failed and we should not retry yet. */
    fun isHeadlessSolvingOnCooldown(): Boolean = synchronized(SessionCache) {
        System.currentTimeMillis() < headlessCooldownUntilMs
    }

    private fun startHeadlessCooldown() = synchronized(SessionCache) {
        headlessCooldownUntilMs = System.currentTimeMillis() + HEADLESS_COOLDOWN_MS
    }

    /** Cleared once a challenge is solved elsewhere, e.g. via the visible dialog. */
    private fun clearHeadlessCooldown() = synchronized(SessionCache) {
        headlessCooldownUntilMs = 0L
    }

    private val authHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Reads the `exp` claim so a JWT is refreshed slightly before it actually lapses. */
    private fun jwtExpirySeconds(jwt: String): Long? {
        return try {
            val payload = jwt.split(".").getOrNull(1) ?: return null
            val decoded = String(
                android.util.Base64.decode(
                    payload,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
            )
            JSONObject(decoded).optLong("exp").takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    fun getCachedSessionJwt(): String? = synchronized(SessionCache) {
        val nowSeconds = System.currentTimeMillis() / 1000
        // 60s of leeway so a token cannot expire mid-request.
        if (cachedSessionJwt != null && sessionJwtExpirySeconds > nowSeconds + 60) {
            cachedSessionJwt
        } else {
            null
        }
    }

    fun clearSessionJwt() = synchronized(SessionCache) {
        cachedSessionJwt = null
        sessionJwtExpirySeconds = 0L
    }

    private fun storeSessionJwt(jwt: String) {
        synchronized(SessionCache) {
            cachedSessionJwt = jwt
            sessionJwtExpirySeconds = jwtExpirySeconds(jwt) ?: (System.currentTimeMillis() / 1000 + 3600)
        }
        clearHeadlessCooldown()
    }

    /**
     * Trades a solved Turnstile token for a session JWT.
     *
     * This runs over plain HTTP rather than inside the WebView: the API sets
     * `Access-Control-Allow-Origin: *`, and Cloudflare only rejects clients that omit
     * a browser-like User-Agent, so there is no reason to keep it in the page.
     *
     * @return the JWT, or null if the exchange was refused.
     */
    suspend fun exchangeTokenForJwt(
        exchangeUrl: String,
        apiToken: String,
        turnstileToken: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("turnstile_token", turnstileToken).toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(exchangeUrl)
                .post(body)
                .header("Authorization", "Bearer $apiToken")
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_UA)
                .header("Origin", "https://monochrome.tf")
                .header("Referer", "https://monochrome.tf/")
                .build()

            authHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful) {
                    Timber.tag("TurnstileSolver").w("Exchange failed: ${response.code} ${text?.take(200)}")
                    return@withContext null
                }
                val json = JSONObject(text ?: return@withContext null)
                val jwt = listOf("access_token", "jwt", "token")
                    .firstNotNullOfOrNull { json.optString(it).takeIf { v -> v.isNotBlank() } }

                if (jwt == null) {
                    Timber.tag("TurnstileSolver").w("Exchange returned no JWT")
                    return@withContext null
                }
                storeSessionJwt(jwt)
                Timber.tag("TurnstileSolver").i("Monochrome session JWT obtained")
                jwt
            }
        } catch (e: Exception) {
            Timber.tag("TurnstileSolver").e(e, "Exchange threw")
            null
        }
    }

    /**
     * Completes authentication using a Turnstile token solved elsewhere, such as the
     * visible challenge dialog.
     */
    suspend fun completeChallenge(exchangeUrl: String, apiToken: String, turnstileToken: String): Boolean =
        exchangeTokenForJwt(exchangeUrl, apiToken, turnstileToken) != null

    /**
     * Returns a usable session JWT, solving a challenge headlessly if the cached one
     * has lapsed.
     *
     * Headless solving works for the non-interactive challenge variants. When
     * Cloudflare decides a visible challenge is required this returns null, and the
     * caller should send the user to the challenge dialog instead.
     *
     * @param ignoreCooldown set by user-initiated actions so an explicit "test
     *   connection" is never silently skipped by the back-off.
     */
    suspend fun getSessionJwt(
        siteKey: String,
        exchangeUrl: String,
        apiToken: String,
        forceRefresh: Boolean = false,
        ignoreCooldown: Boolean = false,
    ): String? {
        if (!forceRefresh) {
            getCachedSessionJwt()?.let { return it }
        }

        if (ignoreCooldown) clearHeadlessCooldown()

        if (isHeadlessSolvingOnCooldown()) {
            Timber.tag("TurnstileSolver").d("Headless solving on cooldown, not attempting")
            return null
        }

        return solveMutex.withLock {
            if (!forceRefresh) {
                getCachedSessionJwt()?.let { return@withLock it }
            }
            // Turnstile tokens are single-use, so never reuse a cached one here.
            val turnstileToken = solveTurnstileToken(siteKey)
            if (turnstileToken == null) {
                Timber.tag("TurnstileSolver").w("Headless Turnstile solve failed; backing off")
                startHeadlessCooldown()
                return@withLock null
            }
            exchangeTokenForJwt(exchangeUrl, apiToken, turnstileToken)
                ?: run {
                    startHeadlessCooldown()
                    null
                }
        }
    }

    /**
     * Renders a Turnstile widget in an offscreen WebView and returns the solved token.
     *
     * The view is measured and laid out explicitly: Turnstile will not run its
     * challenge in a view that never gets a layout pass, which is the usual reason a
     * detached WebView silently times out.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun solveTurnstileToken(siteKey: String, timeoutMs: Long = 20000): String? =
        withTimeoutOrNull(timeoutMs) {
            val deferred = CompletableDeferred<String?>()

            Handler(Looper.getMainLooper()).post {
                var webView: WebView? = null
                try {
                    webView = WebView(context.applicationContext)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        userAgentString = BROWSER_UA
                    }
                    // Give the widget a real viewport so the challenge actually runs.
                    webView.visibility = View.VISIBLE
                    webView.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    )
                    webView.layout(0, 0, 1080, 720)
                    webView.webChromeClient = WebChromeClient()

                    webView.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onTokenReceived(token: String) {
                            Timber.tag("TurnstileSolver").d("Turnstile solved (${token.length} chars)")
                            if (!deferred.isCompleted) deferred.complete(token)
                            destroyWebView(webView)
                        }

                        @JavascriptInterface
                        fun onError(error: String) {
                            Timber.tag("TurnstileSolver").w("Turnstile failed: $error")
                            if (!deferred.isCompleted) deferred.complete(null)
                            destroyWebView(webView)
                        }
                    }, "AndroidTurnstile")

                    webView.webViewClient = object : WebViewClient() {}
                    webView.loadDataWithBaseURL(
                        "https://monochrome.tf",
                        turnstileHtml(siteKey),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                } catch (e: Exception) {
                    Timber.tag("TurnstileSolver").e(e, "Failed to initialise Turnstile WebView")
                    if (!deferred.isCompleted) deferred.complete(null)
                    destroyWebView(webView)
                }
            }

            deferred.await()
        }

    /**
     * Page hosting the Turnstile widget. Shared by the offscreen solver and the
     * visible challenge dialog so both behave identically.
     */
    fun turnstileHtml(siteKey: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
            <style>
                html,body { margin:0; padding:0; background:transparent; }
                #turnstile-container { display:flex; justify-content:center; padding:8px; }
            </style>
        </head>
        <body>
            <div id="turnstile-container"></div>
            <script>
                function report(fn, arg) {
                    if (window.AndroidTurnstile && window.AndroidTurnstile[fn]) {
                        window.AndroidTurnstile[fn](arg);
                    }
                }
                function render() {
                    try {
                        turnstile.render('#turnstile-container', {
                            sitekey: '$siteKey',
                            action: 'auth',
                            callback: function(token) { report('onTokenReceived', token); },
                            'error-callback': function(err) { report('onError', String(err) || 'error'); },
                            'timeout-callback': function() { report('onError', 'timeout'); }
                        });
                    } catch (e) {
                        report('onError', e.message || 'render exception');
                    }
                }
                window.onload = function() {
                    if (window.turnstile) { render(); return; }
                    var waited = 0;
                    var iv = setInterval(function() {
                        if (window.turnstile) { clearInterval(iv); render(); }
                        else if ((waited += 100) > 15000) {
                            clearInterval(iv);
                            report('onError', 'turnstile script never loaded');
                        }
                    }, 100);
                };
            </script>
        </body>
        </html>
    """.trimIndent()

    private fun destroyWebView(webView: WebView?) {
        Handler(Looper.getMainLooper()).post {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (e: Exception) {
                Timber.tag("TurnstileSolver").w(e, "Error destroying WebView")
            }
        }
    }
}
