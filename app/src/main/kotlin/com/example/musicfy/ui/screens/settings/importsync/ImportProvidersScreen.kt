// ImportProvidersScreen.kt

package com.example.musicfy.ui.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.importer.LinkSource
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.viewmodels.ImportSyncViewModel

@Composable
fun ImportProvidersScreen(
    navController: NavController,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    var linkSource by rememberSaveable { mutableStateOf<LinkSource?>(null) }

    // some file managers label .csv as text/plain or octet-stream, so don't filter too hard
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadFile(uri, sourceLabel = "Musicfy backup")
    }

    SubSettingsScaffold(
        title = "Import",
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            title = "Sign in and choose",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                AccountService.SPOTIFY to "[borked-notworking.]",
                AccountService.APPLE_MUSIC to "Library playlists, Favorite Songs and your whole library",
                AccountService.YOUTUBE_MUSIC to "Liked music and your playlists, exact songs · also supports sync",
                AccountService.TIDAL to "My Tracks and your playlists",
            ).map { (service, description) ->
                SettingsItem(
                    title = { Text(service.label) },
                    highlightKey = "${service.label} account",
                    descriptionText = description,
                    icon = painterResource(R.drawable.login),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(accountImportRoute(service)) },
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Or paste a public link",
            style = SettingsGroupStyle.Grouped,
            items = LinkSource.entries.map { source ->
                SettingsItem(
                    title = { Text(source.label) },
                    highlightKey = source.label,
                    descriptionText = when (source) {
                        LinkSource.SPOTIFY -> "Public playlists (first 100 songs) and albums"
                        LinkSource.APPLE_MUSIC -> "Public playlists and albums"
                        LinkSource.DEEZER -> "Public playlists and albums, any length"
                        LinkSource.YOUTUBE -> "Playlists and albums, exact songs, no searching"
                    },
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    onClick = { linkSource = source },
                )
            },
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "From a file",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Musicfy backup") },
                    highlightKey = "Musicfy backup",
                    descriptionText = "A CSV from Export to Musicfy CSV. Restores exact songs",
                    icon = painterResource(R.drawable.backup),
                    iconShape = CircleShape,
                    onClick = { backupPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                ),
                SettingsItem(
                    title = { Text("TuneMyMusic CSV") },
                    highlightKey = "TuneMyMusic CSV",
                    descriptionText = "Private playlists, liked songs, and every other service",
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Other services",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Amazon Music, SoundCloud, Pandora…") },
                    descriptionText = "These go through TuneMyMusic, which can sign in to them for you",
                    icon = painterResource(R.drawable.library_music),
                    iconShape = CircleShape,
                    onClick = { navController.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )
    }

    linkSource?.let { source ->
        LinkInputDialog(
            source = source,
            onDismiss = { linkSource = null },
            onSubmit = { link ->
                linkSource = null
                viewModel.loadLink(link)
            },
        )
    }

    PendingImportHost(viewModel = viewModel, navController = navController)
}
