// NetworkReload.kt
// this thing is for network reload

package com.example.musicfy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.musicfy.utils.NetworkConnectivityObserver

// The underlying ConnectivityManager callback (NetworkConnectivityObserver) fires
// onLost/onAvailable for ANY network capability change — a Wi-Fi/cellular handoff or
// routine radio reselection, not just a genuine "was offline for a while, now back"
// transition. Treating every such blip as a real reconnect was triggering a full
// Home reload (viewModel::refresh, see HomeScreen.kt) far more often than intended.
// Requiring the disconnected state to have actually persisted for a bit before the
// next "available" event counts as a real reload trigger filters those blips out.
private const val MinOfflineDurationMs = 3000L

@Composable
fun NetworkReload(
    onReload: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        val observer = NetworkConnectivityObserver(context.applicationContext)
        var offlineSinceMs: Long? = null
        try {
            observer.networkStatus.collect { isConnected ->
                if (isConnected) {
                    val offlineSince = offlineSinceMs
                    if (offlineSince != null && System.currentTimeMillis() - offlineSince >= MinOfflineDurationMs) {
                        onReload()
                    }
                    offlineSinceMs = null
                } else if (offlineSinceMs == null) {
                    offlineSinceMs = System.currentTimeMillis()
                }
            }
        } finally {
            observer.unregister()
        }
    }
}
