// AudioSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import com.example.musicfy.constants.SkipSilenceKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SubSettingsSearchBar
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

@Composable
fun AudioSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )

    fun qualityLabel(q: AudioQuality) = when (q) {
        AudioQuality.AUTO -> "Auto"
        AudioQuality.LOW -> "Low"
        AudioQuality.MEDIUM -> "Medium"
        AudioQuality.HIGH -> "High"
        AudioQuality.LOSSLESS -> "Lossless"
        AudioQuality.HI_RES_LOSSLESS -> "Hi-Res Lossless"
        AudioQuality.DOLBY_ATMOS -> "Dolby Atmos"
    }

    fun nextQuality(q: AudioQuality) = when (q) {
        AudioQuality.AUTO -> AudioQuality.LOW
        AudioQuality.LOW -> AudioQuality.MEDIUM
        AudioQuality.MEDIUM -> AudioQuality.HIGH
        AudioQuality.HIGH -> AudioQuality.LOSSLESS
        AudioQuality.LOSSLESS -> AudioQuality.HI_RES_LOSSLESS
        AudioQuality.HI_RES_LOSSLESS -> AudioQuality.DOLBY_ATMOS
        AudioQuality.DOLBY_ATMOS -> AudioQuality.AUTO
    }

    SubSettingsScaffold(
        title = "Audio",
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        val pageItems = listOf(
            SettingsItem(
                title = { Text("CrossMix") },
                highlightKey = "CrossMix",
                descriptionText = "Choose how tracks blend together",
                icon = painterResource(R.drawable.linear_scale),
                iconShape = CircleShape,
                onClick = { navController.navigate("crossmix_settings") },
            ),
            SettingsItem(
                title = { Text("Equalizer") },
                highlightKey = "Equalizer",
                descriptionText = "10-band parametric EQ",
                icon = painterResource(R.drawable.equalizer),
                iconShape = CircleShape,
                onClick = { navController.navigate("equalizer") },
            ),
            SettingsItem(
                title = { Text("Audio Quality") },
                highlightKey = "Audio quality",
                descriptionText = qualityLabel(audioQuality),
                icon = painterResource(R.drawable.graphic_eq),
                iconShape = CircleShape,
                onClick = { onAudioQualityChange(nextQuality(audioQuality)) },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (pageItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = pageItems,
            )
            Spacer(Modifier.height(14.dp))
        }

        val toggleItems = listOf(
            SettingsItem(
                title = { Text("Skip Silence") },
                highlightKey = "Skip silence",
                descriptionText = "Skip silent intervals in tracks",
                icon = painterResource(R.drawable.skip_next),
                iconShape = CircleShape,
                onClick = { onSkipSilenceChange(!skipSilence) },
                trailingContent = {
                    AppSwitch(
                        checked = skipSilence,
                        onCheckedChange = onSkipSilenceChange,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Audio Normalization") },
                highlightKey = "Audio normalization",
                descriptionText = "Balance loudness across all tracks",
                icon = painterResource(R.drawable.volume_up),
                iconShape = CircleShape,
                onClick = { onAudioNormalizationChange(!audioNormalization) },
                trailingContent = {
                    AppSwitch(
                        checked = audioNormalization,
                        onCheckedChange = onAudioNormalizationChange,
                    )
                },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (toggleItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = toggleItems,
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
