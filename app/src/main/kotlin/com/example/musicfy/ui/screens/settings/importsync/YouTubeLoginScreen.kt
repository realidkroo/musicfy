// YouTubeLoginScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import com.example.musicfy.constants.AccountChannelHandleKey
import com.example.musicfy.constants.AccountEmailKey
import com.example.musicfy.constants.AccountNameKey
import com.example.musicfy.constants.DataSyncIdKey
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.VisitorDataKey
import com.example.musicfy.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.AccountInfo
import com.music.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/** Reads the session ids YouTube Music keeps in its page config. */
private const val READ_CONFIG_JS = """
(function() {
  try {
    var get = function(k) {
      if (window.ytcfg && ytcfg.get) { var v = ytcfg.get(k); if (v) return v; }
      if (window.yt && yt.config_ && yt.config_[k]) return yt.config_[k];
      return null;
    };
    return { visitorData: get('VISITOR_DATA'), dataSyncId: get('DATASYNC_ID') };
  } catch (e) { return null; }
})();
"""

/** The full-screen sign-in page used from the Sync settings. */
@Composable
fun YouTubeLoginScreen(navController: NavController) {
    val context = LocalContext.current
    var pageLoading by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sign in to YouTube", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Google's own sign-in page. Musicfy never sees your password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        Box(modifier = Modifier.height(2.dp).fillMaxWidth()) {
            if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        YouTubeLoginWebContent(
            onSignedIn = { info ->
                Toast.makeText(context, info?.name?.let { "Signed in as $it" } ?: "Signed in to YouTube", Toast.LENGTH_SHORT).show()
                navController.navigateUp()
            },
            onExit = { navController.navigateUp() },
            onLoadingChange = { pageLoading = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * Google sign-in in a WebView, without chrome. Once it lands on music.youtube.com signed in, the
 * session cookie and ids are saved the same way the rest of the app reads them (App picks them up
 * live) and [onSignedIn] gets the account, if YouTube shared it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginWebContent(
    onSignedIn: (AccountInfo?) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onUrlChange: (String) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val currentOnUrlChange by rememberUpdatedState(onUrlChange)
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finishLogin(view: WebView, url: String) {
        if (saving || finished) return
        val cookie = CookieManager.getInstance().getCookie(url) ?: return
        if ("SAPISID" !in runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())) return
        saving = true
        view.evaluateJavascript(READ_CONFIG_JS) { raw ->
            val config = runCatching { JSONObject(raw) }.getOrNull()
            val visitorData = config?.optString("visitorData")?.takeIf { it.isNotBlank() && it != "null" }
            val dataSyncId = config?.optString("dataSyncId")?.takeIf { it.isNotBlank() && it != "null" }
            scope.launch(Dispatchers.IO) {
                try {
                    // account details need the new session; App would set these from the prefs anyway
                    YouTube.cookie = cookie
                    visitorData?.let { YouTube.visitorData = it }
                    YouTube.dataSyncId = dataSyncId?.let {
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                    val info = YouTube.accountInfo().getOrNull()
                    context.dataStore.edit { settings ->
                        settings[InnerTubeCookieKey] = cookie
                        visitorData?.let { settings[VisitorDataKey] = it }
                        dataSyncId?.let { settings[DataSyncIdKey] = it }
                        settings[AccountNameKey] = info?.name.orEmpty()
                        settings[AccountEmailKey] = info?.email.orEmpty()
                        settings[AccountChannelHandleKey] = info?.channelHandle.orEmpty()
                    }
                    withContext(Dispatchers.Main) {
                        finished = true
                        currentOnSignedIn(info)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Saving YouTube login failed")
                    withContext(Dispatchers.Main) {
                        saving = false
                        Toast.makeText(context, "Couldn't finish signing in. Try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack() && !saving) view.goBack() else onExit()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    // Google refuses sign-in from pages that announce themselves as an embedded WebView
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    disableForcedDarkening()
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            currentOnLoadingChange(true)
                            url?.let(currentOnUrlChange)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            currentOnLoadingChange(false)
                            url?.let(currentOnUrlChange)
                            if (url != null && url.startsWith("https://music.youtube.com")) finishLogin(view, url)
                        }
                    }
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
        )
        if (saving) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
