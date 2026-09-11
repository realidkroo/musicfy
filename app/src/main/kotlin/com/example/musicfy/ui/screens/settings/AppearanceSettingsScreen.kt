// AppearanceSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.example.musicfy.ui.component.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AppleMusicDarkChromeKey
import com.example.musicfy.constants.AudioQualityBadgeDevKey
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.HideAudioQualityBadgeKey
import com.example.musicfy.constants.LocalSongAutoMetadataKey
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.ShowPlayerBottomCardKey
import com.example.musicfy.constants.StopPlaybackOnTaskRemovedKey
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.screens.DarkMode
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import androidx.compose.runtime.remember

@Composable
fun AppearanceSettingsScreen(navController: NavController) {

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
    val (lyricsWaveAnimation, onLyricsWaveAnimationChange) = rememberPreference(
        LyricsWaveAnimationKey,
        defaultValue = true
    )
    val (lyricsHighBloom, onLyricsHighBloomChange) = rememberPreference(
        LyricsHighBloomKey,
        defaultValue = true
    )

    SubSettingsScaffold(
        title = "Appearance",
        onBack = { navController.navigateUp() },
    ) {
        val unavailable = remember { unavailableEffects() }
        if (unavailable.isNotEmpty()) {
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
                        text = "Not available on Android ${Build.VERSION.RELEASE}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Everything else works normally - these effects need a newer " +
                            "Android and fall back to a flat version here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    unavailable.forEach { effect ->
                        Text(
                            text = "\u2022  $effect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                add(
                    SettingsItem(
                        title = { Text("Lyrics letter animation") },
                        highlightKey = "Lyrics letter animation",
                        descriptionText = "Letters lift and bloom as they're sung",
                        icon = painterResource(R.drawable.lyrics),
                        iconShape = CircleShape,
                        onClick = { onLyricsWaveAnimationChange(!lyricsWaveAnimation) },
                        trailingContent = {
                            AppSwitch(
                                checked = lyricsWaveAnimation,
                                onCheckedChange = onLyricsWaveAnimationChange,
                            )
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("High quality bloom") },
                        highlightKey = "High quality bloom",
                        descriptionText = "Rounder glow, more GPU work",
                        icon = painterResource(R.drawable.lyrics),
                        iconShape = CircleShape,
                        isVisible = lyricsWaveAnimation,
                        isSubOption = true,
                        onClick = { onLyricsHighBloomChange(!lyricsHighBloom) },
                        trailingContent = {
                            AppSwitch(
                                checked = lyricsHighBloom,
                                onCheckedChange = onLyricsHighBloomChange,
                            )
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Yt video background") },
                        highlightKey = "Yt video background",
                        descriptionText = "Plays yt video on canvas",
                        icon = painterResource(R.drawable.slow_motion_video),
                        iconShape = CircleShape,
                        onClick = { onPlayVideoBackgroundChange(!playVideoBackground) },
                        trailingContent = {
                            AppSwitch(checked = playVideoBackground, onCheckedChange = onPlayVideoBackgroundChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Timestamp matching") },
                        descriptionText = "Based on subtitle",
                        icon = painterResource(R.drawable.lyrics),
                        iconShape = CircleShape,
                        isVisible = playVideoBackground,
                        isSubOption = true,
                        onClick = { onLyricsSyncChange(!lyricsSync) },
                        trailingContent = {
                            AppSwitch(checked = lyricsSync, onCheckedChange = onLyricsSyncChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Animated canvas") },
                        highlightKey = "Animated canvas",
                        descriptionText = "Animated canvas ( not from youtube )",
                        icon = painterResource(R.drawable.sparks),
                        iconShape = CircleShape,
                        onClick = { onCanvasEnabledChange(!canvasEnabled) },
                        trailingContent = {
                            AppSwitch(checked = canvasEnabled, onCheckedChange = onCanvasEnabledChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Canvas on Wi-Fi only") },
                        descriptionText = "Skip mobile data",
                        icon = painterResource(R.drawable.wifi_proxy),
                        iconShape = CircleShape,
                        isVisible = canvasEnabled,
                        isSubOption = true,
                        onClick = { onCanvasWifiOnlyChange(!canvasWifiOnly) },
                        trailingContent = {
                            AppSwitch(checked = canvasWifiOnly, onCheckedChange = onCanvasWifiOnlyChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Blur") },
                        highlightKey = "Blur",
                        descriptionText = "Wide blur effects across the app",
                        icon = painterResource(R.drawable.gradient),
                        iconShape = CircleShape,

                        onClick = { onDisableBlurChange(!disableBlur) },
                        trailingContent = {
                            AppSwitch(
                                checked = !disableBlur,
                                onCheckedChange = { enabled -> onDisableBlurChange(!enabled) }
                            )
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Audio quality badge") },
                        highlightKey = "Audio quality badge",
                        descriptionText = "Dev — we'll use this later",
                        icon = painterResource(R.drawable.graphic_eq),
                        iconShape = CircleShape,
                        onClick = { onAudioQualityBadgeDevChange(!audioQualityBadgeDev) },
                        trailingContent = {
                            AppSwitch(checked = audioQualityBadgeDev, onCheckedChange = onAudioQualityBadgeDevChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Player customization") },
                        highlightKey = "Player customization",
                        descriptionText = "Cover style, disc options and background",
                        icon = painterResource(R.drawable.crop),
                        iconShape = CircleShape,
                        onClick = { navController.navigate("player_customize") },
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Player bottom card") },
                        highlightKey = "Player bottom card",
                        descriptionText = "Lyrics and queue card",
                        icon = painterResource(R.drawable.album),
                        iconShape = CircleShape,
                        onClick = { onShowPlayerBottomCardChange(!showPlayerBottomCard) },
                        trailingContent = {
                            AppSwitch(checked = showPlayerBottomCard, onCheckedChange = onShowPlayerBottomCardChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Local song auto metadata") },
                        descriptionText = "Auto-clean imported metadata",
                        icon = painterResource(R.drawable.edit),
                        iconShape = CircleShape,
                        onClick = { onLocalSongAutoMetadataChange(!localSongAutoMetadata) },
                        trailingContent = {
                            AppSwitch(checked = localSongAutoMetadata, onCheckedChange = onLocalSongAutoMetadataChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Dark text on light art") },
                        descriptionText = "Dark controls on bright art",
                        icon = painterResource(R.drawable.palette),
                        iconShape = CircleShape,
                        onClick = { onAppleMusicDarkChromeChange(!appleMusicDarkChrome) },
                        trailingContent = {
                            AppSwitch(checked = appleMusicDarkChrome, onCheckedChange = onAppleMusicDarkChromeChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Hide quality badge") },
                        descriptionText = "Hide Hi-Res and Lossless badges",
                        icon = painterResource(R.drawable.close),
                        iconShape = CircleShape,
                        onClick = { onHideAudioQualityBadgeChange(!hideAudioQualityBadge) },
                        trailingContent = {
                            AppSwitch(checked = hideAudioQualityBadge, onCheckedChange = onHideAudioQualityBadgeChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Stop playback when closed") },
                        descriptionText = "Stop when swiped from recents",
                        icon = painterResource(R.drawable.logout),
                        iconShape = CircleShape,
                        onClick = { onStopPlaybackOnTaskRemovedChange(!stopPlaybackOnTaskRemoved) },
                        trailingContent = {
                            AppSwitch(checked = stopPlaybackOnTaskRemoved, onCheckedChange = onStopPlaybackOnTaskRemovedChange)
                        }
                    )
                )
            }
        )
    }
}

/**
 * Visual effects this device's Android version cannot render.
 *
 * Nothing is stripped from the build - these simply have no implementation below the API that
 * introduced them, so they fall back to a flat equivalent. Surfaced in Appearance so an older
 * phone looks deliberately plainer rather than broken.
 */
private fun unavailableEffects(): List<String> = buildList {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        add("Backdrop blur behind the navigation pill, mini player and sheets (needs Android 12)")
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        add("Liquid cover warp and the lyrics glow sweep (needs Android 13)")
    }
}
