// CipherSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.utils.cipher.PlayerJsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Rate limiter for manual cipher refreshes.
 *
 * Each refresh re-downloads YouTube's player script, so hammering the button is both
 * pointless and a good way to get throttled upstream.
 */
private object CipherRefreshRateLimiter {
    private const val MAX_REFRESHES = 3
    private val WINDOW_MS = TimeUnit.MINUTES.toMillis(10)

    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    private fun prune(now: Long) {
        while (timestamps.isNotEmpty() && now - timestamps.first() > WINDOW_MS) {
            timestamps.removeFirst()
        }
    }

    @Synchronized
    fun remaining(): Int {
        prune(System.currentTimeMillis())
        return (MAX_REFRESHES - timestamps.size).coerceAtLeast(0)
    }

    /** Milliseconds until another refresh is allowed, or 0 if one is available now. */
    @Synchronized
    fun cooldownMs(): Long {
        val now = System.currentTimeMillis()
        prune(now)
        if (timestamps.size < MAX_REFRESHES) return 0L
        return (timestamps.first() + WINDOW_MS - now).coerceAtLeast(0L)
    }

    @Synchronized
    fun record() {
        timestamps.addLast(System.currentTimeMillis())
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0s"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CipherSettingsScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()

    var status by remember { mutableStateOf(PlayerJsFetcher.readCacheStatus()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Drives the countdown; updated once a second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Referencing `now` here keeps these recomputing as the clock ticks.
    val remainingMs = remember(status, now) { status.remainingMs }
    val cooldownMs = remember(now) { CipherRefreshRateLimiter.cooldownMs() }
    val remainingRefreshes = remember(now) { CipherRefreshRateLimiter.remaining() }

    val progress = remember(status, now) {
        if (!status.hasCache) 0f
        else (1f - remainingMs.toFloat() / PlayerJsFetcher.CACHE_TTL_MS.toFloat()).coerceIn(0f, 1f)
    }

    val statusLabel = when {
        !status.hasCache -> "Not loaded"
        status.isExpired -> "Stale — refreshes on next playback"
        else -> "Ready"
    }

    SubSettingsScaffold(
        title = "Cipher",
        onBack = { navController.navigateUp() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (status.isExpired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "YouTube signs its stream URLs with a player script that changes " +
                        "periodically. musicfy caches that script and re-downloads it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                CipherInfoRow("Player ID", status.playerHash ?: "—")
                CipherInfoRow(
                    "Last updated",
                    status.lastUpdatedMs?.let {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                    } ?: "—",
                )
                CipherInfoRow(
                    "Script size",
                    if (status.sizeBytes > 0) "${status.sizeBytes / 1024} KB" else "—",
                )
                CipherInfoRow(
                    "Next refresh",
                    if (!status.hasCache) "—" else if (remainingMs <= 0L) "Due now" else "in ${formatDuration(remainingMs)}",
                )

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(if (isRefreshing) "Refreshing…" else "Refresh now") },
                    descriptionText = when {
                        isRefreshing -> "Downloading the latest player script"
                        cooldownMs > 0L -> "Rate limited — try again in ${formatDuration(cooldownMs)}"
                        else -> "$remainingRefreshes manual refresh${if (remainingRefreshes == 1) "" else "es"} left"
                    },
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    onClick = onClick@{
                        if (isRefreshing || CipherRefreshRateLimiter.cooldownMs() > 0L) return@onClick
                        CipherRefreshRateLimiter.record()
                        isRefreshing = true
                        message = null
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                PlayerJsFetcher.invalidateCache()
                                PlayerJsFetcher.getPlayerJs(forceRefresh = true)
                            }
                            status = PlayerJsFetcher.readCacheStatus()
                            message = if (result != null) {
                                "Updated to player ${result.second}"
                            } else {
                                "Refresh failed — check your connection and try again"
                            }
                            isRefreshing = false
                        }
                    },
                ),
                SettingsItem(
                    title = { Text("Clear cached script") },
                    descriptionText = "Forces a fresh download on the next stream",
                    icon = painterResource(R.drawable.restore),
                    iconShape = CircleShape,
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { PlayerJsFetcher.invalidateCache() }
                            status = PlayerJsFetcher.readCacheStatus()
                            message = "Cached player script cleared"
                        }
                    },
                ),
            ),
        )
    }
}

@Composable
private fun CipherInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
