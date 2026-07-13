// SettingsScreen.kt
// this thing is for settings screen

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AppleMusicDarkChromeKey
import com.example.musicfy.constants.LocalSongAutoMetadataKey
import com.example.musicfy.constants.StopPlaybackOnTaskRemovedKey
import com.example.musicfy.constants.UseNewPlayerDesignKey
import com.example.musicfy.ui.component.Material3SettingsGroup
import com.example.musicfy.ui.component.Material3SettingsItem
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.rememberPreference
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (showPlayerBottomCard, onShowPlayerBottomCardChange) = rememberPreference(
        com.example.musicfy.constants.ShowPlayerBottomCardKey,
        defaultValue = true
    )
    val (localSongAutoMetadata, onLocalSongAutoMetadataChange) = rememberPreference(
        LocalSongAutoMetadataKey,
        defaultValue = true
    )
    val (stopPlaybackOnTaskRemoved, onStopPlaybackOnTaskRemovedChange) = rememberPreference(
        StopPlaybackOnTaskRemovedKey,
        defaultValue = false
    )
    val (appleMusicDarkChrome, onAppleMusicDarkChromeChange) = rememberPreference(
        AppleMusicDarkChromeKey,
        defaultValue = false
    )
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Material3SettingsGroup(
                title = "Appearance",
                items = listOf(
                    Material3SettingsItem(
                        title = { Text("Squared Player Design") },
                        description = { Text("Use padded squared album art instead of full bleed art in the player") },
                        icon = painterResource(R.drawable.play),
                        onClick = { onUseNewPlayerDesignChange(!useNewPlayerDesign) },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = useNewPlayerDesign,
                                onCheckedChange = onUseNewPlayerDesignChange
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text("Show Player Bottom Card") },
                        description = { Text("Enable or disable the white lyrics and queue card at the bottom of the player") },
                        icon = painterResource(R.drawable.album),
                        onClick = { onShowPlayerBottomCardChange(!showPlayerBottomCard) },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = showPlayerBottomCard,
                                onCheckedChange = onShowPlayerBottomCardChange
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text("Local Song Auto Metadata") },
                        description = { Text("Automatically clean imported song title, artist, cover art, and lyrics metadata when possible") },
                        icon = painterResource(R.drawable.music_note),
                        onClick = { onLocalSongAutoMetadataChange(!localSongAutoMetadata) },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = localSongAutoMetadata,
                                onCheckedChange = onLocalSongAutoMetadataChange
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text("Dark Player Text On Light Art") },
                        description = { Text("Use dark controls on bright Apple Music style backgrounds") },
                        icon = painterResource(R.drawable.contrast),
                        onClick = { onAppleMusicDarkChromeChange(!appleMusicDarkChrome) },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = appleMusicDarkChrome,
                                onCheckedChange = onAppleMusicDarkChromeChange
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text("Stop Playback When Closed") },
                        description = { Text("Stop the current song when the app is swiped away from recents") },
                        icon = painterResource(R.drawable.close),
                        onClick = { onStopPlaybackOnTaskRemovedChange(!stopPlaybackOnTaskRemoved) },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = stopPlaybackOnTaskRemoved,
                                onCheckedChange = onStopPlaybackOnTaskRemovedChange
                            )
                        }
                    )
                )
            )
            Material3SettingsGroup(
                title = "Advanced",
                items = listOf(
                    Material3SettingsItem(
                        title = { Text("Advanced Audio Settings") },
                        description = { Text("Custom API endpoints, Lossless & Hi-Res streaming options, and Spatial Audio") },
                        icon = painterResource(R.drawable.music_note),
                        onClick = { navController.navigate("advanced_audio_settings") }
                    ),
                    Material3SettingsItem(
                        title = { Text("Repeat Initial Setup") },
                        description = { Text("Re-run the welcome and profile setup wizard") },
                        icon = painterResource(R.drawable.refresh),
                        onClick = {
                            coroutineScope.launch {
                                context.dataStore.updateData { prefs ->
                                    prefs.toMutablePreferences().apply {
                                        set(com.example.musicfy.constants.SetupCompletedKey, false)
                                    }
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
