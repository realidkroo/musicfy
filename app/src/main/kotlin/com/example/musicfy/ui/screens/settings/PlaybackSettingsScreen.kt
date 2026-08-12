// playbacksettingsscreenkt
// surfaces playback/audio features whose actual engines already exist and
// musicservicekt (crossfade's dual-player swap the custom biquad equalizer
// two mechanisms loudnessenhancer-based normalization) but had no settings
// crossfade and skip-silence/normalization were only ever set from their
// and the equalizer was only reachable from the player's 3-dot menu

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import com.example.musicfy.ui.component.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AudioNormalizationKey
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.AudioQualityKey
import com.example.musicfy.constants.CrossfadeDurationKey
import com.example.musicfy.constants.CrossfadeEnabledKey
import com.example.musicfy.constants.CrossfadeGaplessKey
import com.example.musicfy.constants.SkipSilenceInstantKey
import com.example.musicfy.constants.SkipSilenceKey
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(CrossfadeEnabledKey, defaultValue = false)
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(CrossfadeDurationKey, defaultValue = 5f)
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(CrossfadeGaplessKey, defaultValue = true)
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(SkipSilenceInstantKey, defaultValue = false)
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(AudioNormalizationKey, defaultValue = true)

    var showAudioQualityRestriction by remember { mutableStateOf(false) }

    fun qualityLabel(q: AudioQuality) = when (q) {
        AudioQuality.AUTO -> "Auto"
        AudioQuality.HIGH -> "High"
        AudioQuality.LOW -> "Low"
        AudioQuality.LOSSLESS -> "Lossless"
        AudioQuality.HI_RES_LOSSLESS -> "Hi-Res Lossless"
    }

    fun nextQuality(q: AudioQuality) = when (q) {
        AudioQuality.AUTO -> AudioQuality.LOW
        AudioQuality.LOW -> AudioQuality.HIGH
        AudioQuality.HIGH -> AudioQuality.LOSSLESS
        AudioQuality.LOSSLESS -> AudioQuality.HI_RES_LOSSLESS
        AudioQuality.HI_RES_LOSSLESS -> AudioQuality.AUTO
    }

    SubSettingsScaffold(
        title = "Playback",
        onBack = { navController.navigateUp() },
    ) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text("Crossfade") },
                            descriptionText = "Blend one track into the next",
                            icon = painterResource(R.drawable.linear_scale),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onCrossfadeEnabledChange(!crossfadeEnabled) },
                            trailingContent = {
                                AppSwitch(checked = crossfadeEnabled, onCheckedChange = onCrossfadeEnabledChange)
                            }
                        )
                    )
                    if (crossfadeEnabled) {
                        add(
                            SettingsItem(
                                title = { Text("Crossfade duration") },
                                description = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("${crossfadeDuration.toInt()}s")
                                        Slider(
                                            value = crossfadeDuration,
                                            onValueChange = onCrossfadeDurationChange,
                                            valueRange = 1f..15f,
                                            steps = 13
                                        )
                                    }
                                },
                                icon = painterResource(R.drawable.linear_scale)
                            )
                        )
                        add(
                            SettingsItem(
                                title = { Text("Disable for gapless albums") },
                                descriptionText = "Skip within the same album",
                                icon = painterResource(R.drawable.album),
                                iconShape = androidx.compose.foundation.shape.CircleShape,
                                onClick = { onCrossfadeGaplessChange(!crossfadeGapless) },
                                trailingContent = {
                                    AppSwitch(checked = crossfadeGapless, onCheckedChange = onCrossfadeGaplessChange)
                                }
                            )
                        )
                    }
                    add(
                        SettingsItem(
                            title = { Text("Equalizer") },
                            descriptionText = "10-band parametric EQ",
                            icon = painterResource(R.drawable.equalizer),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("equalizer") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Audio quality") },
                            description = { Text(qualityLabel(audioQuality)) },
                            icon = painterResource(R.drawable.graphic_eq),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { showAudioQualityRestriction = true }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Skip silence") },
                            descriptionText = "Skip silent parts",
                            icon = painterResource(R.drawable.fast_forward),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onSkipSilenceChange(!skipSilence) },
                            trailingContent = {
                                AppSwitch(checked = skipSilence, onCheckedChange = onSkipSilenceChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Instantly skip silence") },
                            descriptionText = "Jump instead of speeding up",
                            icon = painterResource(R.drawable.skip_next),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = skipSilence,
                            onClick = { onSkipSilenceInstantChange(!skipSilenceInstant) },
                            trailingContent = {
                                AppSwitch(checked = skipSilenceInstant, onCheckedChange = onSkipSilenceInstantChange, enabled = skipSilence)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Audio normalization") },
                            descriptionText = "Even out loudness between tracks",
                            icon = painterResource(R.drawable.volume_up),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onAudioNormalizationChange(!audioNormalization) },
                            trailingContent = {
                                AppSwitch(checked = audioNormalization, onCheckedChange = onAudioNormalizationChange)
                            }
                        )
                    )
                }
            )
    }

    if (showAudioQualityRestriction) {
        com.example.musicfy.ui.component.RestrictionPopup(
            featureName = "Audio quality",
            onDismiss = { showAudioQualityRestriction = false }
        )
    }
}
