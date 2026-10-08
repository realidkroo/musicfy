// ImportSyncComponents.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.importer.LinkSource
import com.example.musicfy.importer.ParsedImport
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.ImportSyncViewModel
import com.example.musicfy.viewmodels.PendingImport
import com.music.innertube.utils.parseCookieString

const val ImportSyncRoute = "import_sync"
const val ImportProvidersRoute = "import_providers"
const val ImportTuneMyMusicRoute = "import_tunemymusic"
const val ImportProgressRoute = "import_progress"
const val YouTubeSyncRoute = "youtube_sync"
const val YouTubeLoginRoute = "youtube_login"

@Composable
fun rememberYouTubeSignedIn(): Boolean {
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    return remember(cookie) { runCatching { "SAPISID" in parseCookieString(cookie) }.getOrDefault(false) }
}

/** Same surface as a grouped settings card, for content that isn't a list of rows. */
@Composable
fun ImportInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.musicfy.ui.screens.search.SearchColors.Field),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
    }
}

@Composable
fun LinkInputDialog(
    source: LinkSource,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text("Import from ${source.label}") },
        buttons = {
            TextButton(onClick = {
                clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { link = it }
            }) { Text("Paste") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = link.isNotBlank(),
                onClick = { onSubmit(link.trim()) },
            ) { Text("Continue") }
        },
    ) {
        Text(
            text = "Paste a link to a public playlist or album. Share it from ${source.label} with \"Copy link\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            singleLine = true,
            placeholder = { Text(source.example, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank()) onSubmit(link.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

/**
 * Shows whatever the pending import is doing (reading, failed, ready to review) and, once the
 * user confirms, starts it and opens the progress page. Put one on every screen that loads imports.
 */
@Composable
fun PendingImportHost(
    viewModel: ImportSyncViewModel,
    navController: NavController,
) {
    val pending by viewModel.pending.collectAsState()
    val context = LocalContext.current
    val signedIn = rememberYouTubeSignedIn()

    when (val state = pending) {
        PendingImport.Idle -> Unit
        is PendingImport.Loading -> DefaultDialog(
            onDismiss = viewModel::dismissPending,
            buttons = { TextButton(onClick = viewModel::dismissPending) { Text("Cancel") } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(state.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        is PendingImport.Failed -> DefaultDialog(
            onDismiss = viewModel::dismissPending,
            horizontalAlignment = Alignment.Start,
            title = { Text("Couldn't import") },
            buttons = { TextButton(onClick = viewModel::dismissPending) { Text("OK") } },
        ) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
        }
        is PendingImport.Ready -> ImportReviewDialog(
            parsed = state.parsed,
            sourceLabel = state.sourceLabel,
            signedIn = signedIn,
            onDismiss = viewModel::dismissPending,
            onImport = { mirror ->
                if (!viewModel.startImport(state.parsed, mirror)) {
                    Toast.makeText(context, "An import is already running", Toast.LENGTH_SHORT).show()
                    viewModel.dismissPending()
                }
                navController.navigate(ImportProgressRoute) { launchSingleTop = true }
            },
        )
    }
}

@Composable
private fun ImportReviewDialog(
    parsed: ParsedImport,
    sourceLabel: String,
    signedIn: Boolean,
    onDismiss: () -> Unit,
    onImport: (mirrorToYouTube: Boolean) -> Unit,
) {
    var mirror by rememberSaveable { mutableStateOf(signedIn) }
    val names = parsed.playlists.keys.toList()

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = {
            Text(
                text = names.singleOrNull()?.takeIf { parsed.likedSongs.isEmpty() } ?: "Import from $sourceLabel",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = { onImport(mirror && signedIn) }) { Text("Import") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (parsed.likedSongs.isNotEmpty()) {
                Text("${parsed.likedSongs.size} liked songs", style = MaterialTheme.typography.bodyLarge)
            }
            if (names.isNotEmpty()) {
                val songCount = parsed.playlists.values.sumOf { it.size }
                Text(
                    "${names.size} ${if (names.size == 1) "playlist" else "playlists"} · $songCount songs",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (names.size > 1) {
                    Text(
                        text = names.take(6).joinToString(", ") + if (names.size > 6) " and ${names.size - 6} more" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val needsSearch = parsed.likedSongs.any { it.videoId == null } || parsed.playlists.values.any { list -> list.any { it.videoId == null } }
            if (needsSearch) {
                Text(
                    "Each song is searched on YouTube Music. Large libraries take a few minutes; you can leave this page while it runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            parsed.warnings.forEach { warning ->
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (signedIn) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Also create on YouTube", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Playlists and likes are added to your YouTube account too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    AppSwitch(checked = mirror, onCheckedChange = { mirror = it })
                }
                if (!mirror && parsed.likedSongs.isNotEmpty()) {
                    Text(
                        "YouTube sync keeps likes the same on both sides, so these likes will still be added to YouTube the next time it syncs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
