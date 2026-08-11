// ExperimentalSettingsScreen.kt
// Music Haptics + its sub-options, a link to Advanced Audio Settings, Beta notice, and
// Repeat Initial Setup — all pre-existing features, just consolidated into their own
// section instead of living inside the old flat SettingsScreen.

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import com.example.musicfy.ui.component.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.BetaNoticeDismissedKey
import com.example.musicfy.constants.HapticFocus
import com.example.musicfy.constants.HapticFocusKey
import com.example.musicfy.constants.HapticSensitivity
import com.example.musicfy.constants.MusicHapticsEnabledKey
import com.example.musicfy.constants.MusicHapticsSensitivityKey
import com.example.musicfy.constants.SetupCompletedKey
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettingsScreen(navController: NavController) {
    val (musicHapticsEnabled, onMusicHapticsEnabledChange) = rememberPreference(MusicHapticsEnabledKey, defaultValue = false)
    val (musicHapticsSensitivity, onMusicHapticsSensitivityChange) = rememberEnumPreference(MusicHapticsSensitivityKey, defaultValue = HapticSensitivity.MEDIUM)
    val (hapticFocus, onHapticFocusChange) = rememberEnumPreference(HapticFocusKey, defaultValue = HapticFocus.VIBE)
    val (showBigDiscStyles, onShowBigDiscStylesChange) = rememberPreference(
        com.example.musicfy.constants.ShowBigDiscStylesKey,
        defaultValue = false,
    )

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SubSettingsScaffold(
        title = "Experimental",
        onBack = { navController.navigateUp() },
    ) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text("Big disc cover styles") },
                            descriptionText = "Unfinished — oversized discs that bleed off screen",
                            icon = painterResource(R.drawable.album),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onShowBigDiscStylesChange(!showBigDiscStyles) },
                            trailingContent = {
                                AppSwitch(
                                    checked = showBigDiscStyles,
                                    onCheckedChange = onShowBigDiscStylesChange,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Music haptics") },
                            descriptionText = "Vibrate to the beat — uses battery",
                            icon = painterResource(R.drawable.music_note),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onMusicHapticsEnabledChange(!musicHapticsEnabled) },
                            trailingContent = {
                                AppSwitch(checked = musicHapticsEnabled, onCheckedChange = onMusicHapticsEnabledChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptics sensitivity") },
                            description = {
                                Text(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> "Low"
                                        HapticSensitivity.MEDIUM -> "Medium"
                                        HapticSensitivity.HIGH -> "High"
                                    }
                                )
                            },
                            icon = painterResource(R.drawable.sliders),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onMusicHapticsSensitivityChange(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> HapticSensitivity.MEDIUM
                                        HapticSensitivity.MEDIUM -> HapticSensitivity.HIGH
                                        HapticSensitivity.HIGH -> HapticSensitivity.LOW
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptic focus") },
                            description = {
                                Text(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> "Balance"
                                        HapticFocus.VOCAL -> "Focus on vocal"
                                        HapticFocus.VIBE -> "Focus on vibe"
                                        HapticFocus.BASS -> "Focus on bass"
                                    }
                                )
                            },
                            icon = painterResource(R.drawable.equalizer),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onHapticFocusChange(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> HapticFocus.VOCAL
                                        HapticFocus.VOCAL -> HapticFocus.VIBE
                                        HapticFocus.VIBE -> HapticFocus.BASS
                                        HapticFocus.BASS -> HapticFocus.BALANCE
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Advanced audio settings") },
                            descriptionText = "Custom APIs, Hi-Res, Spatial Audio",
                            icon = painterResource(R.drawable.tune),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("advanced_audio_settings") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Repeat initial setup") },
                            descriptionText = "Re-run the setup wizard",
                            icon = painterResource(R.drawable.restore),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.updateData { prefs ->
                                        prefs.toMutablePreferences().apply {
                                            set(SetupCompletedKey, false)
                                        }
                                    }
                                }
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Show beta warning on launch") },
                            descriptionText = "Show the beta popup on launch",
                            icon = painterResource(R.drawable.warning),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.updateData { prefs ->
                                        prefs.toMutablePreferences().apply {
                                            set(BetaNoticeDismissedKey, false)
                                        }
                                    }
                                }
                            }
                        )
                    )
                }
            )
    }
}
