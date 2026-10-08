// ImportProgressScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.viewmodels.ImportSyncViewModel

private const val UNMATCHED_SHOWN = 200

@Composable
fun ImportProgressScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.importProgress.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun leave() {
        if (!navController.popBackStack(ImportSyncRoute, inclusive = false)) navController.navigateUp()
    }

    SubSettingsScaffold(
        title = if (progress.isRunning) "Importing" else "Import",
        onBack = { navController.navigateUp() },
    ) {
        ImportInfoCard {
            val fraction = if (progress.totalTracks > 0) progress.processedTracks.toFloat() / progress.totalTracks else 0f
            Text(
                text = when {
                    progress.isRunning -> "${progress.processedTracks} of ${progress.totalTracks} songs"
                    progress.cancelled -> "Stopped"
                    progress.error != null -> "Import failed"
                    progress.isDone -> "Done"
                    else -> "No import running"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (progress.isRunning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    text = progress.currentLabel.ifEmpty { "Starting…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You can leave this page; the import keeps going and sends a notification when it's done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (progress.isDone || progress.processedTracks > 0) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryLine("Found on YouTube Music", "${progress.matchedTracks} of ${progress.totalTracks}")
                    if (progress.playlistsCreated > 0) SummaryLine("New playlists", progress.playlistsCreated.toString())
                    if (progress.likedAdded > 0) SummaryLine("Songs liked", progress.likedAdded.toString())
                    if (progress.unmatched.isNotEmpty()) SummaryLine("Not found", progress.unmatched.size.toString())
                }
            }
            progress.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (progress.youtubeFailures.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Imported here but not fully on YouTube: ${progress.youtubeFailures.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                if (progress.isRunning) {
                    add(
                        SettingsItem(
                            title = { Text("Stop import") },
                            descriptionText = "Songs and playlists imported so far are kept",
                            icon = painterResource(R.drawable.close),
                            iconShape = CircleShape,
                            onClick = viewModel::cancelImport,
                        )
                    )
                } else {
                    add(
                        SettingsItem(
                            title = { Text("Done") },
                            icon = painterResource(R.drawable.check),
                            iconShape = CircleShape,
                            onClick = {
                                viewModel.clearFinishedImport()
                                leave()
                            },
                        )
                    )
                }
                if (progress.unmatched.isNotEmpty()) {
                    add(
                        SettingsItem(
                            title = { Text("Copy songs not found") },
                            descriptionText = "To look them up by hand",
                            icon = painterResource(R.drawable.share),
                            iconShape = CircleShape,
                            onClick = {
                                val text = progress.unmatched.joinToString("\n") { miss ->
                                    buildString {
                                        append(miss.track.title)
                                        if (miss.track.artist.isNotEmpty()) append(" - ").append(miss.track.artist)
                                        miss.playlistName?.let { append("  [").append(it).append(']') }
                                    }
                                }
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied ${progress.unmatched.size} songs", Toast.LENGTH_SHORT).show()
                            },
                        )
                    )
                }
            },
        )

        // listed once finished: re-laying out hundreds of rows on every progress tick is wasted work
        if (progress.unmatched.isNotEmpty() && !progress.isRunning) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                title = "Not found",
                style = SettingsGroupStyle.Grouped,
                items = progress.unmatched.take(UNMATCHED_SHOWN).map { miss ->
                    SettingsItem(
                        title = { Text(miss.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        descriptionText = listOfNotNull(miss.track.artist.ifEmpty { null }, miss.playlistName ?: "Liked songs")
                            .joinToString(" · "),
                    )
                },
            )
            if (progress.unmatched.size > UNMATCHED_SHOWN) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "and ${progress.unmatched.size - UNMATCHED_SHOWN} more. Copy the list to see them all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
