// networkreloadkt
// this thing is for network reload

package com.example.musicfy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.musicfy.utils.NetworkConnectivityObserver

// the underlying connectivitymanager callback networkconnectivityobserver
// onlost onavailable for any network capability change a wi fi cellular
// routine radio reselection not just a genuine was offline for a while now
// transition treating every such blip as a real reconnect was triggering a
// home reload viewmodel refresh see homescreenkt far more often than
// requiring the disconnected state to have actually persisted for a bit
// next available event counts as a real reload trigger filters those blips
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
