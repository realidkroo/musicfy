// AdvancedAudioSettingsScreen.kt

package com.example.musicfy.ui.screens.settings
import com.example.musicfy.ui.component.AppSwitch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.*
import com.example.musicfy.ui.component.LocalZoomOutOverlayState
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAudioSettingsScreen(navController: NavController) {
    val (enableCustomApi, onEnableCustomApiChange) = rememberPreference(EnableCustomApiKey, defaultValue = false)
    val (enableMonochromeBackend, onEnableMonochromeBackendChange) = rememberPreference(EnableMonochromeBackendKey, defaultValue = false)
    val (enableSpatialAudio, onEnableSpatialAudioChange) = rememberPreference(EnableSpatialAudioKey, defaultValue = false)
    val (fallbackToYouTube, onFallbackToYouTubeChange) = rememberPreference(
        com.example.musicfy.constants.MonochromeFallbackToYouTubeKey,
        defaultValue = true,
    )

    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)

    val (amazonMusicApiUrl, onAmazonMusicApiUrlChange) = rememberPreference(AmazonMusicApiUrlKey, defaultValue = "https://amz.geeked.wtf")
    val (tidalToAsinUrl, onTidalToAsinUrlChange) = rememberPreference(TidalToAsinUrlKey, defaultValue = "https://t2a.geeked.wtf")
    val (turnstileSiteKey, onTurnstileSiteKeyChange) = rememberPreference(TurnstileSiteKeyKey, defaultValue = "0x4AAAAAADgxqF6QVMm0GLHH")
    val (turnstileBypassToken, onTurnstileBypassTokenChange) = rememberPreference(TurnstileBypassTokenKey, defaultValue = "")

    val (deezerFallbackEnabled, onDeezerFallbackEnabledChange) = rememberPreference(DeezerFallbackEnabledKey, defaultValue = true)
    val (deezerFallbackUrl, onDeezerFallbackUrlChange) = rememberPreference(DeezerFallbackUrlKey, defaultValue = "https://dzr.tabs-vs-spaces.wtf")

    val (apiInstances, onApiInstancesChange) = rememberPreference(
        ApiInstancesKey,
        defaultValue = "https://hifi.geeked.wtf,https://eu-central.monochrome.tf,https://us-west.monochrome.tf,https://api.monochrome.tf,https://monochrome-api.samidy.com,https://maus.qqdl.site,https://vogel.qqdl.site,https://katze.qqdl.site,https://hund.qqdl.site,https://tidal.kinoplus.online,https://wolf.qqdl.site"
    )
    val (streamingInstances, onStreamingInstancesChange) = rememberPreference(
        StreamingInstancesKey,
        defaultValue = "https://hifi.geeked.wtf,https://maus.qqdl.site,https://vogel.qqdl.site,https://katze.qqdl.site,https://hund.qqdl.site,https://wolf.qqdl.site"
    )
    val (qobuzInstances, onQobuzInstancesChange) = rememberPreference(
        QobuzInstancesKey,
        defaultValue = "https://qobuz.kennyy.com.br"
    )
    val (monochromeInstances, onMonochromeInstancesChange) = rememberPreference(
        MonochromeInstancesKey,
        defaultValue = "https://api.monochrome.tf,https://monochrome-api.samidy.com"
    )

    val (enableAmazonMusicBackend, onEnableAmazonMusicBackendChange) = rememberPreference(
        EnableAmazonMusicBackendKey,
        defaultValue = false
    )

    val (amazonMusicInstances, onAmazonMusicInstancesChange) = rememberPreference(
        AmazonMusicInstancesKey,
        defaultValue = "https://amz.geeked.wtf"
    )

    val (streamDebugToasts, onStreamDebugToastsChange) = rememberPreference(
        EnableStreamDebugToastsKey,
        defaultValue = true
    )

    val zoomOutOverlayState = LocalZoomOutOverlayState.current
    val showMonochromeOnboarding = {
        zoomOutOverlayState.show {
            MonochromeOnboardingContent(
                onEnabled = { onEnableMonochromeBackendChange(true) },
                onDismiss = { zoomOutOverlayState.dismiss() },
            )
        }
    }

    SubSettingsScaffold(
        title = "Advanced Audio",
        onBack = { navController.navigateUp() },
    ) {
            SettingsGroup(
                title = "Streaming APIs",
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text("Turn on Monochrome Backend (Includes Amazon)") },
                        descriptionText = "High quality — disables YT fallback",
                        icon = painterResource(R.drawable.music_note),
                        onClick = {
                            if (enableMonochromeBackend) {
                                onEnableMonochromeBackendChange(false)
                            } else {
                                showMonochromeOnboarding()
                            }
                        },
                        trailingContent = {
                            AppSwitch(
                                checked = enableMonochromeBackend,
                                onCheckedChange = { checked ->
                                    if (checked) showMonochromeOnboarding() else onEnableMonochromeBackendChange(false)
                                }
                            )
                        }
                    )
                )
            )

            if (enableMonochromeBackend) {
                SettingsGroup(
                    title = "Debug",
                    style = SettingsGroupStyle.Grouped,
                    items = listOf(
                        SettingsItem(
                            title = { Text("Show stream status toasts") },
                            descriptionText = "Status popups while resolving",
                            icon = painterResource(R.drawable.info),
                            onClick = { onStreamDebugToastsChange(!streamDebugToasts) },
                            trailingContent = {
                                AppSwitch(
                                    checked = streamDebugToasts,
                                    onCheckedChange = onStreamDebugToastsChange
                                )
                            }
                        )
                    )
                )

                Text(
                    text = "Streaming Quality",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )

                Text(
                    text = "Lossless tiers stream from the Monochrome backend and need it enabled above. The other tiers stream from YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )

                Text(
                    text = "Monochrome",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.DOLBY_ATMOS,
                    title = "Dolby Atmos (E-AC-3 JOC)",
                    onClick = { onAudioQualityChange(AudioQuality.DOLBY_ATMOS) }
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.HI_RES_LOSSLESS,
                    title = "Hi-Res Lossless (FLAC up to 24-bit)",
                    onClick = { onAudioQualityChange(AudioQuality.HI_RES_LOSSLESS) }
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.LOSSLESS,
                    title = "Lossless (FLAC 16-bit/44.1kHz)",
                    onClick = { onAudioQualityChange(AudioQuality.LOSSLESS) }
                )

                Text(
                    text = "YouTube",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.HIGH,
                    title = "High (best available, OPUS/AAC)",
                    onClick = { onAudioQualityChange(AudioQuality.HIGH) }
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.MEDIUM,
                    title = "Medium (~128-160kbps)",
                    onClick = { onAudioQualityChange(AudioQuality.MEDIUM) }
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.LOW,
                    title = "Low (~50-70kbps)",
                    onClick = { onAudioQualityChange(AudioQuality.LOW) }
                )
                QualitySelectionRow(
                    selected = audioQuality == AudioQuality.AUTO,
                    title = "Auto (best the active backend offers)",
                    onClick = { onAudioQualityChange(AudioQuality.AUTO) }
                )

                SettingsGroup(
                    title = "Fallback",
                    style = SettingsGroupStyle.Grouped,
                    items = listOf(
                        SettingsItem(
                            title = { Text("Fall back to YouTube Music") },
                            descriptionText = "When Monochrome cannot serve a track, stream it from YouTube Music instead of failing",
                            icon = painterResource(R.drawable.play),
                            onClick = { onFallbackToYouTubeChange(!fallbackToYouTube) },
                            trailingContent = {
                                AppSwitch(
                                    checked = fallbackToYouTube,
                                    onCheckedChange = onFallbackToYouTubeChange
                                )
                            }
                        )
                    )
                )

                SettingsGroup(
                    title = "Spatial Audio",
                    style = SettingsGroupStyle.Grouped,
                    items = listOf(
                        SettingsItem(
                            title = { Text("Enable Dolby Atmos") },
                            descriptionText = "Request Spatial Audio when available",
                            icon = painterResource(R.drawable.album),
                            onClick = { onEnableSpatialAudioChange(!enableSpatialAudio) },
                            trailingContent = {
                                AppSwitch(
                                    checked = enableSpatialAudio,
                                    onCheckedChange = onEnableSpatialAudioChange
                                )
                            }
                        )
                    )
                )

                Text(
                    text = "Amazon Music",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
                Text(
                    text = "Use Amazon Music streams first, then fall back to Qobuz, then Deezer as a last resort.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = amazonMusicApiUrl,
                    onValueChange = onAmazonMusicApiUrlChange,
                    label = { Text("Amazon Music API URL") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = tidalToAsinUrl,
                    onValueChange = onTidalToAsinUrlChange,
                    label = { Text("Tidal to ASIN URL") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = turnstileSiteKey,
                    onValueChange = onTurnstileSiteKeyChange,
                    label = { Text("Turnstile Site Key") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                OutlinedTextField(
                    value = turnstileBypassToken,
                    onValueChange = onTurnstileBypassTokenChange,
                    label = { Text("Turnstile Bypass Token") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Text(
                    text = "Deezer Fallback",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
                Text(
                    text = "Last-resort source, used only when both Amazon Music and Qobuz fail. Tops out at 16-bit lossless (FLAC).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SettingsItem(
                    title = { Text("Enable Deezer Fallback") },
                    icon = painterResource(R.drawable.play),
                    onClick = { onDeezerFallbackEnabledChange(!deezerFallbackEnabled) },
                    trailingContent = {
                        AppSwitch(
                            checked = deezerFallbackEnabled,
                            onCheckedChange = onDeezerFallbackEnabledChange
                        )
                    }
                )
                OutlinedTextField(
                    value = deezerFallbackUrl,
                    onValueChange = onDeezerFallbackUrlChange,
                    label = { Text("Deezer Fallback API URL") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Text(
                    text = "API Instances",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 8.dp)
                )

                if (enableMonochromeBackend) {
                    InstanceListManager("Monochrome API Instances", monochromeInstances, onMonochromeInstancesChange)
                    InstanceListManager("Streaming Instances", streamingInstances, onStreamingInstancesChange)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
    }
}

@Composable
fun QualitySelectionRow(selected: Boolean, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun InstanceListManager(title: String, rawList: String, onListChange: (String) -> Unit) {
    var newInstance by remember { mutableStateOf("") }
    val instances = rawList.split(",").filter { it.isNotBlank() }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newInstance,
                onValueChange = { newInstance = it },
                modifier = Modifier.weight(1f),
                label = { Text("Add instance URL") }
            )
            IconButton(onClick = {
                if (newInstance.isNotBlank()) {
                    val updated = if (rawList.isBlank()) newInstance else "$rawList,$newInstance"
                    onListChange(updated)
                    newInstance = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        instances.forEach { instance ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = instance, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val updated = instances.filter { it != instance }.joinToString(",")
                    onListChange(updated)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}
