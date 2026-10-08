// TuneMyMusicImportScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.viewmodels.ImportSyncViewModel

private const val TUNEMYMUSIC_URL = "https://www.tunemymusic.com/transfer"

@Composable
fun TuneMyMusicImportScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadFile(uri, sourceLabel = "TuneMyMusic")
    }

    SubSettingsScaffold(
        title = "TuneMyMusic CSV",
        onBack = { navController.navigateUp() },
    ) {
        ImportInfoCard {
            Text(
                "Works with any service TuneMyMusic supports, including private playlists and liked songs.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Step(1, "Open TuneMyMusic and pick the service you're coming from (Spotify, Apple Music, Tidal, Amazon Music…).")
                Step(2, "Sign in there and choose the playlists and liked songs to move.")
                Step(3, "For the destination, choose \"Export to file\", then CSV.")
                Step(4, "Come back here and choose the downloaded .csv file.")
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Open TuneMyMusic") },
                    descriptionText = "tunemymusic.com in your browser",
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TUNEMYMUSIC_URL)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
                SettingsItem(
                    title = { Text("Choose CSV file") },
                    descriptionText = "Exportify and other playlist CSVs work too",
                    icon = painterResource(R.drawable.playlist_add),
                    iconShape = CircleShape,
                    onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                ),
            ),
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
