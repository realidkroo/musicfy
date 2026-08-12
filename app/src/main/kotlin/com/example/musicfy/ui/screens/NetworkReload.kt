// NetworkReload.kt

package com.example.musicfy.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.musicfy.utils.NetworkConnectivityObserver

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
