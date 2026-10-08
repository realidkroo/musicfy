// YouTubeSyncScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AccountEmailKey
import com.example.musicfy.constants.AccountNameKey
import com.example.musicfy.constants.YtPullHistoryKey
import com.example.musicfy.constants.YtReportPlaysKey
import com.example.musicfy.constants.YtSyncNewPlaylistsKey
import com.example.musicfy.constants.YtmSyncKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.ImportSyncViewModel

@Composable
fun YouTubeSyncScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val signedIn = rememberYouTubeSignedIn()
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val (autoSync, onAutoSyncChange) = rememberPreference(YtmSyncKey, true)
    val (syncNewPlaylists, onSyncNewPlaylistsChange) = rememberPreference(YtSyncNewPlaylistsKey, false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, true)
    val (pullHistory, onPullHistoryChange) = rememberPreference(YtPullHistoryKey, false)
    val state by viewModel.youTubeState.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmUpload by remember { mutableStateOf(false) }

    SubSettingsScaffold(
        title = "Sync",
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = "YouTube account",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                if (signedIn) {
                    SettingsItem(
                        title = { Text(accountName.ifBlank { "Signed in" }) },
                        descriptionText = accountEmail.ifBlank { "YouTube Music" },
                        icon = painterResource(R.drawable.account),
                        iconShape = CircleShape,
                        trailingContent = {
                            TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
                        },
                    )
                } else {
                    SettingsItem(
                        title = { Text("Sign in to YouTube") },
                        descriptionText = "Needed for sync. Your login stays on this phone",
                        icon = painterResource(R.drawable.login),
                        iconShape = CircleShape,
                        onClick = { navController.navigate(YouTubeLoginRoute) },
                    )
                }
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Keep in sync",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                toggleItem(
                    title = "Sync library on launch",
                    description = "Likes, library, artists and saved playlists from YouTube",
                    icon = R.drawable.sync,
                    checked = autoSync,
                    enabled = signedIn,
                    onChange = onAutoSyncChange,
                ),
                toggleItem(
                    title = "New playlists go to YouTube",
                    description = "Playlists you create here are created on YouTube too",
                    icon = R.drawable.playlist_add,
                    checked = syncNewPlaylists,
                    enabled = signedIn,
                    onChange = onSyncNewPlaylistsChange,
                ),
                toggleItem(
                    title = "Send plays to YouTube history",
                    description = "Songs you play here show up in your YouTube history",
                    icon = R.drawable.history,
                    checked = reportPlays,
                    enabled = signedIn,
                    onChange = onReportPlaysChange,
                ),
                toggleItem(
                    title = "Copy YouTube history here",
                    description = "Plays from your other devices join Musicfy's history on each sync",
                    icon = R.drawable.history,
                    checked = pullHistory,
                    enabled = signedIn,
                    onChange = onPullHistoryChange,
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Now",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Sync now") },
                    descriptionText = if (pullHistory) "Library, playlists and history" else "Library and playlists",
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::syncNow,
                ),
                SettingsItem(
                    title = { Text("Copy YouTube history now") },
                    descriptionText = "Only adds plays Musicfy doesn't have yet",
                    icon = painterResource(R.drawable.history),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = viewModel::pullHistoryNow,
                ),
                SettingsItem(
                    title = { Text("Put local playlists on YouTube") },
                    descriptionText = "Creates a YouTube copy of every playlist that only exists here",
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                    enabled = signedIn && !state.running,
                    onClick = { confirmUpload = true },
                ),
            ),
        )

        if (state.running || state.message != null) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                if (state.running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(state.label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::clearYouTubeMessage) { Text("OK") }
                }
            }
        }

        if (!signedIn) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Import from a YouTube playlist link doesn't need an account. Use Import → YouTube Music.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmSignOut) {
        DefaultDialog(
            onDismiss = { confirmSignOut = false },
            horizontalAlignment = Alignment.Start,
            title = { Text("Sign out of YouTube?") },
            buttons = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
        ) {
            Text(
                "Everything already in your library stays. Sync stops until you sign in again.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmUpload) {
        DefaultDialog(
            onDismiss = { confirmUpload = false },
            horizontalAlignment = Alignment.Start,
            title = { Text("Put local playlists on YouTube?") },
            buttons = {
                TextButton(onClick = { confirmUpload = false }) { Text("Cancel") }
                TextButton(onClick = {
                    confirmUpload = false
                    viewModel.uploadLocalPlaylists()
                }) { Text("Create") }
            },
        ) {
            Text(
                "Each playlist that only exists in Musicfy gets a private copy on your YouTube account. After that, changes go to both.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = title,
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)
