// AccountImportScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.YtmSyncKey
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.importer.account.AccountImportState
import com.example.musicfy.viewmodels.AccountImportViewModel
import com.example.musicfy.viewmodels.ImportSyncViewModel

const val AccountImportRoutePattern = "account_import/{service}"
fun accountImportRoute(service: AccountService) = "account_import/${service.route}"

/** Reads MusicKit's own tokens once the user has signed in on music.apple.com. */
internal const val ApplePageProbeJs = """
(function() {
  try {
    if (!window.MusicKit || !MusicKit.getInstance) return null;
    var m = MusicKit.getInstance();
    if (!m) return null;
    return { dev: m.developerToken || null, user: m.musicUserToken || null };
  } catch (e) { return null; }
})();
"""

/**
 * music.apple.com opens signed out with a Sign In button in its header; press it once per page so
 * the sign-in form comes up straight away. Does nothing once MusicKit is authorized.
 */
internal const val AppleAutoSignInJs = """
(function() {
  try {
    if (window.__musicfySignInPressed) return 'pressed';
    var m = window.MusicKit && MusicKit.getInstance && MusicKit.getInstance();
    if (m && m.isAuthorized) return 'authorized';
    var button = document.querySelector('[data-testid="sign-in-button"]');
    if (!button) return 'waiting';
    window.__musicfySignInPressed = true;
    button.click();
    return 'pressed';
  } catch (e) { return 'error'; }
})();
"""

/** Everything a sign-in page's periodic tick does for [service]. */
internal fun AccountService.onSignInTick(
    view: android.webkit.WebView,
    onApplePageProbe: (String?) -> Unit,
    onCookies: (String?) -> Unit,
) {
    if (this != AccountService.APPLE_MUSIC) return
    view.evaluateJavascript(AppleAutoSignInJs, null)
    view.evaluateJavascript(ApplePageProbeJs, onApplePageProbe)
    onCookies(CookieManager.getInstance().getCookie("https://music.apple.com"))
}

@Composable
fun AccountImportScreen(
    navController: NavController,
    viewModel: AccountImportViewModel = hiltViewModel(),
    importViewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val service = viewModel.service
    val state by viewModel.state.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val signedInToYouTube = rememberYouTubeSignedIn()

    // read and ready: on to the same review dialog every import uses
    LaunchedEffect(ready) {
        ready?.let {
            importViewModel.offer(it, service.label)
            viewModel.consumeReady()
        }
    }

    if (service == AccountService.YOUTUBE_MUSIC) {
        LaunchedEffect(signedInToYouTube) {
            if (signedInToYouTube && viewModel.state.value is AccountImportState.NeedsYouTubeSignIn) viewModel.refreshYouTube()
        }
    }

    fun signInAgain() {
        clearWebSession(service.webOrigins)
        viewModel.signInAgain()
    }

    when (val current = state) {
        AccountImportState.SigningIn -> CaptureWebView(
            title = "Sign in to ${service.label}",
            subtitle = "On ${service.label}'s own page. Musicfy only reads your playlists.",
            startUrl = service.loginUrl.orEmpty(),
            playerOrigins = service.playerOrigins,
            desktopSite = service.desktopSite,
            onHeader = viewModel::onHeader,
            onTick = { view -> service.onSignInTick(view, viewModel::onApplePageProbe, viewModel::onCookies) },
            onExit = { navController.navigateUp() },
        )

        else -> SubSettingsScaffold(
            title = service.label,
            onBack = { navController.navigateUp() },
        ) {
            when (current) {
                AccountImportState.SigningIn -> Unit
                AccountImportState.NeedsYouTubeSignIn -> {
                    ImportInfoCard {
                        Text(
                            "YouTube Music import uses the app's YouTube account. Sign in, then choose playlists.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsGroup(
                        style = SettingsGroupStyle.Grouped,
                        items = listOf(
                            SettingsItem(
                                title = { Text("Sign in to YouTube") },
                                icon = painterResource(R.drawable.login),
                                iconShape = CircleShape,
                                onClick = { navController.navigate(YouTubeLoginRoute) },
                            )
                        ),
                    )
                }
                is AccountImportState.Loading -> BusyCard(current.label, fraction = null)
                is AccountImportState.Reading -> {
                    BusyCard(
                        label = "${current.label} (${current.done + 1} of ${current.total})",
                        fraction = current.done.toFloat() / current.total.coerceAtLeast(1),
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsGroup(
                        style = SettingsGroupStyle.Grouped,
                        items = listOf(
                            SettingsItem(
                                title = { Text("Stop") },
                                icon = painterResource(R.drawable.close),
                                iconShape = CircleShape,
                                onClick = viewModel::cancelReading,
                            )
                        ),
                    )
                }
                is AccountImportState.Failed -> {
                    ImportInfoCard {
                        Text(current.message, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsGroup(
                        style = SettingsGroupStyle.Grouped,
                        items = buildList {
                            if (!current.signedOut) add(
                                SettingsItem(
                                    title = { Text("Try again") },
                                    icon = painterResource(R.drawable.refresh),
                                    iconShape = CircleShape,
                                    onClick = viewModel::retry,
                                )
                            )
                            add(
                                SettingsItem(
                                    title = { Text("Sign in again") },
                                    icon = painterResource(R.drawable.login),
                                    iconShape = CircleShape,
                                    onClick = {
                                        if (service == AccountService.YOUTUBE_MUSIC) navController.navigate(YouTubeLoginRoute)
                                        else signInAgain()
                                    },
                                )
                            )
                        },
                    )
                }
                is AccountImportState.Choosing -> ChoosingContent(
                    service = service,
                    state = current,
                    onToggle = viewModel::toggle,
                    onSelectAll = viewModel::selectAll,
                    onSelectNone = viewModel::selectNone,
                    onImport = viewModel::readSelected,
                    onSwitchAccount = {
                        if (service == AccountService.YOUTUBE_MUSIC) navController.navigate(YouTubeSyncRoute) else signInAgain()
                    },
                )
            }
        }
    }

    notice?.let { message ->
        DefaultDialog(
            onDismiss = viewModel::clearNotice,
            buttons = { TextButton(onClick = viewModel::clearNotice) { Text("OK") } },
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }

    PendingImportHost(viewModel = importViewModel, navController = navController)
}

@Composable
private fun BusyCard(label: String, fraction: Float?) {
    ImportInfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChoosingContent(
    service: AccountService,
    state: AccountImportState.Choosing,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onImport: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    val autoSync by rememberPreference(YtmSyncKey, true)
    val selectedCount = state.selected.size
    val allSelected = selectedCount == state.playlists.size && selectedCount > 0

    SettingsGroup(
        style = SettingsGroupStyle.Grouped,
        items = listOf(
            SettingsItem(
                title = { Text(state.accountName?.let { "Signed in as $it" } ?: "Signed in to ${service.label}") },
                descriptionText = if (service == AccountService.YOUTUBE_MUSIC) "Manage on the Sync page" else "Use a different account",
                icon = painterResource(R.drawable.account),
                iconShape = CircleShape,
                onClick = onSwitchAccount,
            ),
            SettingsItem(
                title = {
                    Text(
                        when (selectedCount) {
                            0 -> "Choose what to import"
                            1 -> "Import 1 selected"
                            else -> "Import $selectedCount selected"
                        }
                    )
                },
                descriptionText = if (selectedCount == 0) "Tick playlists below" else "Next you can also add them to YouTube",
                icon = painterResource(R.drawable.playlist_add),
                iconShape = CircleShape,
                enabled = selectedCount > 0,
                onClick = onImport,
            ),
            SettingsItem(
                title = { Text(if (allSelected) "Select none" else "Select all") },
                icon = painterResource(R.drawable.check),
                iconShape = CircleShape,
                onClick = if (allSelected) onSelectNone else onSelectAll,
            ),
        ),
    )

    if (service == AccountService.YOUTUBE_MUSIC && autoSync) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Library sync is on, so saved YouTube playlists are already in your library. Importing makes separate local copies.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(16.dp))

    if (state.playlists.isEmpty()) {
        ImportInfoCard {
            Text("No playlists in this account.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    SettingsGroup(
        title = "${state.playlists.size} in your library",
        style = SettingsGroupStyle.Grouped,
        items = state.playlists.map { playlist ->
            val checked = playlist.id in state.selected
            SettingsItem(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                descriptionText = listOfNotNull(
                    playlist.trackCount?.let { if (it == 1) "1 song" else "$it songs" },
                    playlist.subtitle,
                ).joinToString(" · ").ifEmpty { null },
                icon = painterResource(if (playlist.isLiked) R.drawable.favorite else R.drawable.playlist_play),
                iconShape = CircleShape,
                onClick = { onToggle(playlist.id) },
                trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle(playlist.id) }) },
            )
        },
    )
}
