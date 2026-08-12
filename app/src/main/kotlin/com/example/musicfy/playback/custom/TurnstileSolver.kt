package com.example.musicfy.playback.custom

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber

class TurnstileSolver(private val context: Context) {

    private var cachedToken: String? = null
    private var tokenTimestamp: Long = 0L
    // only one webview based solve fetch may run at a time concurrent retries
    // exoplayer s auto retry on error would otherwise spin up many webviews at
    // looks like abuse to cloudflare turnstile and gets tokens rejected
    private val solveMutex = Mutex()

    @Synchronized
    fun getCachedToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && (now - tokenTimestamp) < 4 * 60 * 1000) { // 4 minutes cache
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
        // another caller may have solved it while we were waiting for the lock
        if (!forceRefresh) {
            getCachedToken()?.let { return@withLock it }
        }

        withTimeoutOrNull(15000) { // 15 seconds max
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

    // solves turnstile exchanges the raw widget response for a backend jwt via
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchWithTurnstile(siteKey: String, exchangeUrl: String, targetUrl: String, forceRefresh: Boolean = false): Pair<Int, String?>? {
        return solveMutex.withLock {
        // exchange tokens are single use at cloudflare s edge never reuse a cached
        // token here unlike getturnstiletoken s cache which is for callers that
        // the token s presence not a working exchange

        withTimeoutOrNull(20000) { // 20 seconds max
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

    // same fingerprint bound exchange as fetchwithturnstile but for apis like
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchPlaybackWithTurnstile(
        siteKey: String,
        exchangeUrl: String,
        playbackUrl: String,
        playbackBodyJson: String,
        forceRefresh: Boolean = false
    ): Pair<Int, String?>? {
        return solveMutex.withLock {
        // exchange tokens are single use at cloudflare s edge always solve fresh
        // reuse a cached raw token across exchange attempts

        withTimeoutOrNull(20000) { // 20 seconds max
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
                            Timber.tag("TurnstileSolver").d("Turnstile page loaded for playback fetch")
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
                                            body: JSON.stringify({ turnstile_token: token })
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

                                        const response = await fetch('$playbackUrl', {
                                            method: 'POST',
                                            headers: {
                                                'Content-Type': 'application/json',
                                                'Authorization': 'Bearer ' + jwt
                                            },
                                            body: ${JSONObject.quote(playbackBodyJson)}
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
                                            action: 'auth',
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
