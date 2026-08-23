// MonochromeChallengeDialog.kt

package com.example.musicfy.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.musicfy.playback.custom.TurnstileSolver
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shows the Cloudflare Turnstile challenge in a real, on-screen WebView.
 *
 * Monochrome's API gates its public API token behind Turnstile. Most of the time the
 * challenge passes without interaction and the offscreen solver handles it, but when
 * Cloudflare decides a visible challenge is needed this dialog is the only way through.
 *
 * @param onResult invoked with true once a session token has been obtained.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonochromeChallengeDialog(
    apiBaseUrl: String,
    apiToken: String,
    siteKey: String,
    onDismiss: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val solver = remember { TurnstileSolver(context) }

    var isExchanging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify with Monochrome") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Monochrome's API is protected by Cloudflare. Complete the check " +
                        "below once and musicfy will reuse the session for about an hour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {}
                            setBackgroundColor(0)

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onTokenReceived(token: String) {
                                    post {
                                        isExchanging = true
                                        error = null
                                    }
                                    scope.launch {
                                        val ok = solver.completeChallenge(
                                            exchangeUrl = "${apiBaseUrl.trimEnd('/')}/api/auth/turnstile",
                                            apiToken = apiToken,
                                            turnstileToken = token,
                                        )
                                        isExchanging = false
                                        if (ok) {
                                            onResult(true)
                                        } else {
                                            error = "Verification was rejected. Please try again."
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onError(message: String) {
                                    Timber.tag("MonochromeChallenge").w("Turnstile error: $message")
                                    post { error = "Challenge failed: $message" }
                                }
                            }, "AndroidTurnstile")

                            loadDataWithBaseURL(
                                "https://monochrome.tf",
                                solver.turnstileHtml(siteKey),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )

                if (isExchanging) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
