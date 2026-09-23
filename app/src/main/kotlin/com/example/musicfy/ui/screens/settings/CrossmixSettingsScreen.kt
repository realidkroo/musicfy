package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.CrossfadeDurationKey
import com.example.musicfy.constants.CrossfadeEnabledKey
import com.example.musicfy.constants.CrossfadeGaplessKey
import com.example.musicfy.constants.CrossmixMode
import com.example.musicfy.constants.CrossmixModeKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.DefaultPlayerSeekBarSlider
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

private const val AutoCrossfadeDurationSeconds = 5f

@Composable
fun CrossmixSettingsScreen(navController: NavController) {
    val (storedMode, onStoredModeChange) = rememberEnumPreference(
        CrossmixModeKey,
        defaultValue = CrossmixMode.AUTO_CROSSFADE,
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = AutoCrossfadeDurationSeconds,
    )
    val (disableForGapless, onDisableForGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )

    // Users upgrading from the previous Crossfade switch land on the equivalent manual mode.
    val selectedMode = if (storedMode == CrossmixMode.OFF && crossfadeEnabled) {
        CrossmixMode.MANUAL_CROSSFADE
    } else {
        storedMode
    }

    fun selectMode(mode: CrossmixMode) {
        onStoredModeChange(mode)
        onCrossfadeEnabledChange(mode != CrossmixMode.OFF)
        if (mode == CrossmixMode.AUTO_CROSSFADE) {
            onCrossfadeDurationChange(AutoCrossfadeDurationSeconds)
        } else if (mode == CrossmixMode.CROSSMIX && crossfadeDuration < 8f) {
            onCrossfadeDurationChange(12f)
        }
    }

    fun modeControl(mode: CrossmixMode): @Composable () -> Unit = {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { selectMode(mode) },
        )
    }

    SubSettingsScaffold(
        title = "Crossmix",
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                add(
                    SettingsItem(
                        title = { Text("Off") },
                        descriptionText = "Play tracks one after another",
                        icon = painterResource(R.drawable.close),
                        onClick = { selectMode(CrossmixMode.OFF) },
                        trailingContent = modeControl(CrossmixMode.OFF),
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Auto crossfade") },
                        descriptionText = "Use the automatic 5-second transition",
                        icon = painterResource(R.drawable.linear_scale),
                        onClick = { selectMode(CrossmixMode.AUTO_CROSSFADE) },
                        trailingContent = modeControl(CrossmixMode.AUTO_CROSSFADE),
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Disable for gapless albums") },
                        descriptionText = "Keep consecutive tracks from the same album intact",
                        icon = painterResource(R.drawable.album),
                        isSubOption = true,
                        isVisible = selectedMode == CrossmixMode.AUTO_CROSSFADE,
                        onClick = { onDisableForGaplessChange(!disableForGapless) },
                        trailingContent = {
                            AppSwitch(
                                checked = disableForGapless,
                                onCheckedChange = onDisableForGaplessChange,
                            )
                        },
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Manual crossfade") },
                        descriptionText = "Set a fixed overlap between tracks",
                        icon = painterResource(R.drawable.linear_scale),
                        onClick = { selectMode(CrossmixMode.MANUAL_CROSSFADE) },
                        trailingContent = modeControl(CrossmixMode.MANUAL_CROSSFADE),
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Transition length · ${crossfadeDuration.toInt()}s") },
                        description = {
                            Column(Modifier.fillMaxWidth()) {
                                DefaultPlayerSeekBarSlider(
                                    value = crossfadeDuration.coerceIn(2f, 30f),
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 2f..30f,
                                    steps = 27,
                                )
                            }
                        },
                        icon = painterResource(R.drawable.linear_scale),
                        isSubOption = true,
                        isVisible = selectedMode == CrossmixMode.MANUAL_CROSSFADE,
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Disable for gapless albums") },
                        descriptionText = "Keep consecutive tracks from the same album intact",
                        icon = painterResource(R.drawable.album),
                        isSubOption = true,
                        isVisible = selectedMode == CrossmixMode.MANUAL_CROSSFADE,
                        onClick = { onDisableForGaplessChange(!disableForGapless) },
                        trailingContent = {
                            AppSwitch(
                                checked = disableForGapless,
                                onCheckedChange = onDisableForGaplessChange,
                            )
                        },
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Crossmix") },
                        descriptionText = "Longer, lyric-aware blends that bring the next intro forward",
                        icon = painterResource(R.drawable.graphic_eq),
                        onClick = { selectMode(CrossmixMode.CROSSMIX) },
                        trailingContent = modeControl(CrossmixMode.CROSSMIX),
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Transition length · ${crossfadeDuration.toInt()}s") },
                        description = {
                            Column(Modifier.fillMaxWidth()) {
                                DefaultPlayerSeekBarSlider(
                                    value = crossfadeDuration.coerceIn(8f, 20f),
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 8f..20f,
                                    steps = 11,
                                )
                            }
                        },
                        icon = painterResource(R.drawable.linear_scale),
                        isSubOption = true,
                        isVisible = selectedMode == CrossmixMode.CROSSMIX,
                    )
                )
                add(
                    SettingsItem(
                        title = { Text("Disable for gapless albums") },
                        descriptionText = "Keep consecutive tracks from the same album intact",
                        icon = painterResource(R.drawable.album),
                        isSubOption = true,
                        isVisible = selectedMode == CrossmixMode.CROSSMIX,
                        onClick = { onDisableForGaplessChange(!disableForGapless) },
                        trailingContent = {
                            AppSwitch(
                                checked = disableForGapless,
                                onCheckedChange = onDisableForGaplessChange,
                            )
                        },
                    )
                )
            },
        )
    }
}
