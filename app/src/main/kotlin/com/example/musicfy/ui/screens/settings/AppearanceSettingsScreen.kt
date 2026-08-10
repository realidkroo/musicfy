// AppearanceSettingsScreen.kt
// Appearance section split out of the old flat SettingsScreen: theme (the first thing that
// actually calls DarkModeKey's setter — it existed as a dead enum before this), YouTube video
// background + its lyrics-sync sub-option, animated canvas + Wi-Fi-only sub-option, the audio
// quality badge (stubbed for a later pass), blur, then the pre-existing Appearance-ish
// toggles kept in a second group so nothing that already worked gets silently dropped.
//
// Chrome (back button, collapsing title, progressive blur, bottom-bar clearance) comes from
// SubSettingsScaffold; the rows use the Grouped style so parent options with a live sub-option
// render inside a shared pill.

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.musicfy.constants.UseNewPlayerDesignKey
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.screens.DarkMode
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

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
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                add(
                    SettingsItem(
                        title = { Text("Theme") },
                        descriptionText = if (isDarkNow) "Dark" else "Light",
                        icon = painterResource(R.drawable.contrast),
                        iconShape = CircleShape,
                        onClick = { onDarkModeChange(if (isDarkNow) DarkMode.OFF else DarkMode.ON) },
                        trailingContent = {
                            AppSwitch(
                                checked = isDarkNow,
                                onCheckedChange = { checked -> onDarkModeChange(if (checked) DarkMode.ON else DarkMode.OFF) }
                            )
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Lyrics letter animation") },
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
                        descriptionText = "Wide blur effects across the app",
                        icon = painterResource(R.drawable.gradient),
                        iconShape = CircleShape,
                        // Stored key is still "disable blur"; only the presentation is positive,
                        // so the switch reads on = blur showing.
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
                        title = { Text("Squared player design") },
                        descriptionText = "Padded square art in the player",
                        icon = painterResource(R.drawable.crop),
                        iconShape = CircleShape,
                        onClick = { onUseNewPlayerDesignChange(!useNewPlayerDesign) },
                        trailingContent = {
                            AppSwitch(checked = useNewPlayerDesign, onCheckedChange = onUseNewPlayerDesignChange)
                        }
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Player bottom card") },
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
