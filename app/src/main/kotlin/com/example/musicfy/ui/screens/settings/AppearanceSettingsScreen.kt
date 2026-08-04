// AppearanceSettingsScreen.kt
// Appearance section split out of the old flat SettingsScreen: theme (the first thing that
// actually calls DarkModeKey's setter — it existed as a dead enum before this), YouTube video
// background + its lyrics-sync sub-option, animated canvas + Wi-Fi-only sub-option, the audio
// quality badge (stubbed for a later pass), disable-blur, then the pre-existing Appearance-ish
// toggles kept in a second group so nothing that already worked gets silently dropped.

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AppleMusicDarkChromeKey
import com.example.musicfy.constants.AudioQualityBadgeDevKey
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.HideAudioQualityBadgeKey
import com.example.musicfy.constants.LocalSongAutoMetadataKey
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.ShowPlayerBottomCardKey
import com.example.musicfy.constants.StopPlaybackOnTaskRemovedKey
import com.example.musicfy.constants.UseNewPlayerDesignKey
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.screens.DarkMode
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        com.example.musicfy.constants.DarkModeKey,
        defaultValue = DarkMode.AUTO
    )
    val isSystemDark = isSystemInDarkTheme()
    val isDarkNow = if (darkMode == DarkMode.AUTO) isSystemDark else darkMode == DarkMode.ON

    val (playVideoBackground, onPlayVideoBackgroundChange) = rememberPreference(
        PlayVideoBackgroundKey,
        defaultValue = false
    )
    val (lyricsSync, onLyricsSyncChange) = rememberPreference(
        YtVideoBackgroundLyricsSyncKey,
        defaultValue = false
    )
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(
        CanvasThumbnailAnimationKey,
        defaultValue = true
    )
    val (canvasWifiOnly, onCanvasWifiOnlyChange) = rememberPreference(
        CanvasWifiOnlyKey,
        defaultValue = true
    )
    val (audioQualityBadgeDev, onAudioQualityBadgeDevChange) = rememberPreference(
        AudioQualityBadgeDevKey,
        defaultValue = false
    )
    val (disableBlur, onDisableBlurChange) = rememberPreference(
        DisableBlurKey,
        defaultValue = false
    )

    // Pre-existing toggles, kept working, just moved here from the old flat screen.
    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = true
    )
    val (showPlayerBottomCard, onShowPlayerBottomCardChange) = rememberPreference(
        ShowPlayerBottomCardKey,
        defaultValue = true
    )
    val (localSongAutoMetadata, onLocalSongAutoMetadataChange) = rememberPreference(
        LocalSongAutoMetadataKey,
        defaultValue = true
    )
    val (appleMusicDarkChrome, onAppleMusicDarkChromeChange) = rememberPreference(
        AppleMusicDarkChromeKey,
        defaultValue = false
    )
    val (hideAudioQualityBadge, onHideAudioQualityBadgeChange) = rememberPreference(
        HideAudioQualityBadgeKey,
        defaultValue = false
    )
    val (stopPlaybackOnTaskRemoved, onStopPlaybackOnTaskRemovedChange) = rememberPreference(
        StopPlaybackOnTaskRemovedKey,
        defaultValue = false
    )

    Scaffold(
        topBar = { }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { navController.navigateUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_ios),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(start = 6.dp)
                    )
                }
            }

            Text(
                text = "Appearance",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            SettingsGroup(
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text("Theme") },
                            description = { Text(if (isDarkNow) "Dark" else "Light") },
                            icon = painterResource(R.drawable.contrast),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onDarkModeChange(if (isDarkNow) DarkMode.OFF else DarkMode.ON) },
                            trailingContent = {
                                Switch(
                                    checked = isDarkNow,
                                    onCheckedChange = { checked -> onDarkModeChange(if (checked) DarkMode.ON else DarkMode.OFF) }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("YouTube video background") },
                            description = { Text("Play the music video, blurred, behind the player instead of the cover art") },
                            icon = painterResource(R.drawable.slow_motion_video),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onPlayVideoBackgroundChange(!playVideoBackground) },
                            trailingContent = {
                                Switch(checked = playVideoBackground, onCheckedChange = onPlayVideoBackgroundChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Sync with lyrics") },
                            description = { Text("Match the video background's timing to the song's lyrics where available") },
                            icon = painterResource(R.drawable.lyrics),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            isVisible = playVideoBackground,
                            isSubOption = true,
                            onClick = { onLyricsSyncChange(!lyricsSync) },
                            trailingContent = {
                                Switch(checked = lyricsSync, onCheckedChange = onLyricsSyncChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Animated canvas") },
                            description = { Text("Show looping video backgrounds behind the cover art when available") },
                            icon = painterResource(R.drawable.sparks),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onCanvasEnabledChange(!canvasEnabled) },
                            trailingContent = {
                                Switch(checked = canvasEnabled, onCheckedChange = onCanvasEnabledChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Canvas only on Wi-Fi") },
                            description = { Text("Don't fetch new canvas videos over mobile data") },
                            icon = painterResource(R.drawable.wifi_proxy),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            isVisible = canvasEnabled,
                            isSubOption = true,
                            onClick = { onCanvasWifiOnlyChange(!canvasWifiOnly) },
                            trailingContent = {
                                Switch(checked = canvasWifiOnly, onCheckedChange = onCanvasWifiOnlyChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Audio quality badge") },
                            description = { Text("Dev: enabled — we'll use this later") },
                            icon = painterResource(R.drawable.graphic_eq),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onAudioQualityBadgeDevChange(!audioQualityBadgeDev) },
                            trailingContent = {
                                Switch(checked = audioQualityBadgeDev, onCheckedChange = onAudioQualityBadgeDevChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Disable blur") },
                            description = { Text("Replace progressive/gradient blur across the app with plain dark or color gradients") },
                            icon = painterResource(R.drawable.gradient),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onDisableBlurChange(!disableBlur) },
                            trailingContent = {
                                Switch(checked = disableBlur, onCheckedChange = onDisableBlurChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Squared player design") },
                            description = { Text("Use padded squared album art instead of full bleed art in the player") },
                            icon = painterResource(R.drawable.crop),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onUseNewPlayerDesignChange(!useNewPlayerDesign) },
                            trailingContent = {
                                Switch(checked = useNewPlayerDesign, onCheckedChange = onUseNewPlayerDesignChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Show player bottom card") },
                            description = { Text("Enable or disable the lyrics/queue card at the bottom of the player") },
                            icon = painterResource(R.drawable.album),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onShowPlayerBottomCardChange(!showPlayerBottomCard) },
                            trailingContent = {
                                Switch(checked = showPlayerBottomCard, onCheckedChange = onShowPlayerBottomCardChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Local song auto metadata") },
                            description = { Text("Automatically clean imported song title, artist, cover art, and lyrics metadata") },
                            icon = painterResource(R.drawable.edit),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onLocalSongAutoMetadataChange(!localSongAutoMetadata) },
                            trailingContent = {
                                Switch(checked = localSongAutoMetadata, onCheckedChange = onLocalSongAutoMetadataChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Dark player text on light art") },
                            description = { Text("Use dark controls on bright Apple Music style backgrounds") },
                            icon = painterResource(R.drawable.palette),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onAppleMusicDarkChromeChange(!appleMusicDarkChrome) },
                            trailingContent = {
                                Switch(checked = appleMusicDarkChrome, onCheckedChange = onAppleMusicDarkChromeChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Hide audio quality badge") },
                            description = { Text("Hide the Hi-Res, Lossless, and High quality badges from the player") },
                            icon = painterResource(R.drawable.close),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onHideAudioQualityBadgeChange(!hideAudioQualityBadge) },
                            trailingContent = {
                                Switch(checked = hideAudioQualityBadge, onCheckedChange = onHideAudioQualityBadgeChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Stop playback when closed") },
                            description = { Text("Stop the current song when the app is swiped away from recents") },
                            icon = painterResource(R.drawable.logout),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onStopPlaybackOnTaskRemovedChange(!stopPlaybackOnTaskRemoved) },
                            trailingContent = {
                                Switch(checked = stopPlaybackOnTaskRemoved, onCheckedChange = onStopPlaybackOnTaskRemovedChange)
                            }
                        )
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(180.dp)) // ensure we can scroll past bottom bar/player
        }
    }
}
