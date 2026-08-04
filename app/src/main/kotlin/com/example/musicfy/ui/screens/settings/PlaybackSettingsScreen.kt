// PlaybackSettingsScreen.kt
// Surfaces playback/audio features whose actual engines already exist and work in
// MusicService.kt (crossfade's dual-player swap, the custom biquad equalizer, skip-silence's
// two mechanisms, LoudnessEnhancer-based normalization) but had no Settings UI at all —
// crossfade and skip-silence/normalization were only ever set from their DataStore defaults,
// and the equalizer was only reachable from the player's 3-dot menu.

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back_ios), contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsGroup(
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text("Crossfade") },
                            description = { Text("Smoothly blend the end of one track into the next") },
                            icon = painterResource(R.drawable.linear_scale),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onCrossfadeEnabledChange(!crossfadeEnabled) },
                            trailingContent = {
                                Switch(checked = crossfadeEnabled, onCheckedChange = onCrossfadeEnabledChange)
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
                                description = { Text("Don't crossfade between consecutive tracks of the same album") },
                                icon = painterResource(R.drawable.album),
                                iconShape = androidx.compose.foundation.shape.CircleShape,
                                onClick = { onCrossfadeGaplessChange(!crossfadeGapless) },
                                trailingContent = {
                                    Switch(checked = crossfadeGapless, onCheckedChange = onCrossfadeGaplessChange)
                                }
                            )
                        )
                    }
                    add(
                        SettingsItem(
                            title = { Text("Equalizer") },
                            description = { Text("Adjust playback with a 10-band parametric equalizer") },
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
                            onClick = { onAudioQualityChange(nextQuality(audioQuality)) }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Skip silence") },
                            description = { Text("Fast forward through silent parts of songs") },
                            icon = painterResource(R.drawable.fast_forward),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onSkipSilenceChange(!skipSilence) },
                            trailingContent = {
                                Switch(checked = skipSilence, onCheckedChange = onSkipSilenceChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Instantly skip silence") },
                            description = { Text("Jump ahead during silent moments instead of speeding up playback") },
                            icon = painterResource(R.drawable.skip_next),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = skipSilence,
                            onClick = { onSkipSilenceInstantChange(!skipSilenceInstant) },
                            trailingContent = {
                                Switch(checked = skipSilenceInstant, onCheckedChange = onSkipSilenceInstantChange, enabled = skipSilence)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Audio normalization") },
                            description = { Text("Even out loudness differences between tracks") },
                            icon = painterResource(R.drawable.volume_up),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onAudioNormalizationChange(!audioNormalization) },
                            trailingContent = {
                                Switch(checked = audioNormalization, onCheckedChange = onAudioNormalizationChange)
                            }
                        )
                    )
                }
            )
        }
    }
}
