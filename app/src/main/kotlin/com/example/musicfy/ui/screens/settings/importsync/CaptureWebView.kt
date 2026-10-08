// CaptureWebView.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.view.ContextThemeWrapper
import android.content.Context
import android.view.ViewGroup
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import kotlinx.coroutines.delay
import timber.log.Timber

/** Desktop Chrome: Tidal's web player refuses phones, and the others show their full web player. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private const val BRIDGE_NAME = "MusicfyCapture"

/**
 * Wraps fetch and XHR so the headers the web player sends (its own session token) reach the app.
 * Only the header names below are reported, each value once.
 */
private val HOOK_JS = """
(function() {
  if (window.__musicfyHooked) return;
  window.__musicfyHooked = true;
  var wanted = { 'authorization': 1, 'media-user-token': 1 };
  var seen = {};
  var seenCount = 0;
  function report(name, value, url) {
    try {
      name = String(name).toLowerCase();
      if (!wanted[name] || !value) return;
      var key = name + '|' + value;
      if (seen[key]) return;
      if (seenCount > 200) { seen = {}; seenCount = 0; }
      seen[key] = 1; seenCount++;
      window.$BRIDGE_NAME.onHeader(name, String(value), String(url || location.href));
    } catch (e) {}
  }
  function scan(headers, url) {
    try {
      if (!headers) return;
      if (typeof Headers !== 'undefined' && headers instanceof Headers) {
        headers.forEach(function(v, k) { report(k, v, url); });
      } else if (Array.isArray(headers)) {
        headers.forEach(function(pair) { report(pair[0], pair[1], url); });
      } else if (typeof headers === 'object') {
        Object.keys(headers).forEach(function(k) { report(k, headers[k], url); });
      }
    } catch (e) {}
  }
  try {
    var originalFetch = window.fetch;
    if (originalFetch) {
      window.fetch = function(input, init) {
        try {
          var url = (typeof input === 'string') ? input : (input && input.url);
          if (input && typeof input === 'object' && input.headers) scan(input.headers, url);
          if (init && init.headers) scan(init.headers, url);
        } catch (e) {}
        return originalFetch.apply(this, arguments);
      };
    }
  } catch (e) {}
  try {
    var originalOpen = XMLHttpRequest.prototype.open;
    var originalSet = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function(method, url) {
      try { this.__musicfyUrl = url; } catch (e) {}
      return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
      report(name, value, this.__musicfyUrl);
      return originalSet.apply(this, arguments);
    };
  } catch (e) {}
})();
""".trimIndent()

private class CaptureBridge(private val onHeader: (String, String, String) -> Unit) {
    @JavascriptInterface
    fun onHeader(name: String?, value: String?, url: String?) {
        if (name.isNullOrBlank() || value.isNullOrBlank()) return
        onHeader(name, value, url.orEmpty())
    }
}

/** The full-screen version, with its own header: Import settings → Sign in and choose. */
@Composable
fun CaptureWebView(
    title: String,
    subtitle: String,
    startUrl: String,
    playerOrigins: Set<String>,
    desktopSite: Boolean,
    onHeader: (name: String, value: String, url: String) -> Unit,
    onTick: (WebView) -> Unit,
    onExit: () -> Unit,
) {
    var pageLoading by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        WebLoginHeader(title = title, subtitle = subtitle, onBack = onExit)
        Box(modifier = Modifier.height(2.dp).fillMaxWidth()) {
            if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        CaptureWebContent(
            startUrl = startUrl,
            playerOrigins = playerOrigins,
            desktopSite = desktopSite,
            onHeader = onHeader,
            onTick = onTick,
            onExit = onExit,
            onLoadingChange = { pageLoading = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * A service's own sign-in page, without any chrome. Whatever session headers its web player sends
 * are passed to [onHeader]; [onTick] runs on every page load and every couple of seconds, for
 * anything that has to be read out of the page instead. Back walks the page's history, then
 * [onExit].
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun CaptureWebContent(
    startUrl: String,
    playerOrigins: Set<String>,
    desktopSite: Boolean,
    onHeader: (name: String, value: String, url: String) -> Unit,
    onTick: (WebView) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val currentOnHeader by rememberUpdatedState(onHeader)
    val currentOnTick by rememberUpdatedState(onTick)
    val currentOnUrlChange by rememberUpdatedState(onUrlChange)
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    val bridge = remember { CaptureBridge { name, value, url -> currentOnHeader(name, value, url) } }
    val documentStartSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    fun WebView.configure(isPopup: Boolean) {
        // A WebView with no LayoutParams gets WRAP_CONTENT, and Chromium then lays the page out at
        // zero height (vh/dvh = 0, the initial containing block 0 tall). Spotify's sign-in page is
        // `position: absolute; inset: 0`, so it came out 48px tall and the whole form was clipped:
        // a blank page that had loaded fine.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = true
        // zooming out to fit the widest element only suits a desktop site; on a phone site one
        // oversized hidden element shrank the whole page out of sight
        settings.loadWithOverviewMode = desktopSite
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.userAgentString = if (desktopSite) DESKTOP_USER_AGENT else chromeUserAgent(settings.userAgentString)
        disableForcedDarkening()
        hideAppIdentity()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(bridge, BRIDGE_NAME)
        // only the web player is watched; the sign-in pages themselves are left untouched
        if (documentStartSupported && playerOrigins.isNotEmpty()) {
            runCatching { WebViewCompat.addDocumentStartJavaScript(this, HOOK_JS, playerOrigins) }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isPopup) {
                    currentOnLoadingChange(true)
                    url?.let(currentOnUrlChange)
                }
                // without document-start scripts, hook as early as we can; later requests still count
                if (!documentStartSupported && isPlayerPage(url, playerOrigins)) view.evaluateJavascript(HOOK_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!isPopup) {
                    currentOnLoadingChange(false)
                    url?.let(currentOnUrlChange)
                }
                if (isPlayerPage(url, playerOrigins)) view.evaluateJavascript(HOOK_JS, null)
                currentOnTick(view)
            }

            // when a sign-in page stays blank, these say why (logcat tag SignInWeb, or chrome://inspect)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) Timber.tag(LOG_TAG).w("page failed: %s %s", error.errorCode, error.description)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) Timber.tag(LOG_TAG).w("page answered HTTP %s: %s", response.statusCode, request.url?.host)
            }

            // a second way to see request headers, for requests the page hook misses
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString().orEmpty()
                request.requestHeaders?.forEach { (name, value) ->
                    if (name.equals("authorization", true) || name.equals("media-user-token", true)) {
                        currentOnHeader(name, value, url)
                    }
                }
                return null
            }
        }

        webChromeClient = object : WebChromeClient() {
            // Apple's authorize step and "Continue with Google/Apple" open popups that report back to this page
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // only windows the user asked for: a script opening one on its own would cover the page
                if (!isUserGesture) {
                    Timber.tag(LOG_TAG).d("blocked a window opened without a tap")
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                popupWebView?.let(::destroyLater)
                val popup = WebView(view.context).apply { configure(isPopup = true) }
                popupWebView = popup
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    Timber.tag(LOG_TAG).w("%s (%s:%d)", message.message(), message.sourceId(), message.lineNumber())
                }
                return false
            }

            override fun onCloseWindow(window: WebView) {
                if (window == popupWebView) {
                    popupWebView = null
                    destroyLater(window)
                }
            }
        }
    }

    BackHandler {
        val popup = popupWebView
        val main = mainWebView
        when {
            popup != null && popup.canGoBack() -> popup.goBack()
            popup != null -> {
                popupWebView = null
                destroyLater(popup)
            }
            main != null && main.canGoBack() -> main.goBack()
            else -> onExit()
        }
    }

    // single-page apps sign in without a new page load, so look again every couple of seconds
    LaunchedEffect(mainWebView) {
        val view = mainWebView ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            currentOnTick(view)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupWebView?.let(::destroyLater)
            popupWebView = null
            mainWebView?.let {
                it.stopLoading()
                destroyLater(it)
            }
            mainWebView = null
        }
    }

    Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(lightThemed(context)).apply {
                        configure(isPopup = false)
                        loadUrl(startUrl)
                        mainWebView = this
                    }
                },
            )
            popupWebView?.let { popup ->
                key(popup) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        factory = { popup },
                    )
                }
            }
    }
}

@Composable
fun WebLoginHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun isPlayerPage(url: String?, playerOrigins: Set<String>): Boolean =
    url != null && playerOrigins.any { url.startsWith(it) }

private const val LOG_TAG = "SignInWeb"

/**
 * WebView's own user agent says "; wv" and "Version/4.0", both of which sign-in pages read as an
 * embedded browser. Without them it's the same Chrome-for-phones string, real version included.
 */
internal fun chromeUserAgent(webViewAgent: String): String =
    webViewAgent.replace("; wv", "").replace(Regex("""\s?Version/\d+(\.\d+)*"""), "")

/**
 * WebView names the app in an X-Requested-With header on every request, which is how sites spot an
 * embedded browser. Android lets an app opt out; sign-in pages get no app name.
 */
@SuppressLint("RequiresFeature")
internal fun WebView.hideAppIdentity() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
    }
}

/**
 * Sign-in pages draw their own dark mode. WebView's automatic darkening (and MIUI's forced dark)
 * repainted them on top of it and left light text on light fields.
 */
@SuppressLint("RequiresFeature")
internal fun WebView.disableForcedDarkening() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) isForceDarkAllowed = false
}

/**
 * The context sign-in pages are shown in: the app's, with a light theme. On Android 13+ a WebView
 * takes `prefers-color-scheme` from its context's theme, and Musicfy's is dark. Apple Music then
 * went dark around its sign-in frame, which has no dark mode and a see-through background, so the
 * frame's dark text sat on a dark page - only the code boxes and links showed. Spotify and Tidal
 * draw their own dark pages either way. Popups are made from this view's context, so they get it too.
 */
private fun lightThemed(context: Context): Context =
    ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)

/** Lets Compose detach the view first; destroying an attached WebView logs errors. */
private fun destroyLater(webView: WebView) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ webView.destroy() }, 500)
}

/** Signs this app's web views out of one service, so the next sign-in can use another account. */
fun clearWebSession(origins: List<String>) {
    val cookies = CookieManager.getInstance()
    origins.forEach { origin ->
        val host = origin.removePrefix("https://")
        val parentDomain = host.split('.').takeLast(2).joinToString(".")
        cookies.getCookie(origin)?.split(';')?.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty()) {
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/")
                cookies.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.$parentDomain")
            }
        }
        // web players keep their session in local storage too
        WebStorage.getInstance().deleteOrigin(origin)
    }
    cookies.flush()
}
