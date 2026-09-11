// MusicfySettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsHighlight
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold

/**
 * One searchable setting, and the page it lives on.
 *
 * Search results deliberately render as plain rows rather than live controls: a switch in a result
 * list would change something the user cannot see the context of. Tapping takes them to the page
 * that owns it, with [highlightKey] passed along so the page can call it out on arrival.
 */
private data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
) {
    val highlightKey: String get() = title
}

private val SettingsIndex = listOf(
    SettingsEntry("Lyrics letter animation", "Appearance", "appearance_settings", "lyrics glow wave letters"),
    SettingsEntry("High quality bloom", "Appearance", "appearance_settings", "lyrics bloom quality"),
    SettingsEntry("Yt video background", "Appearance", "appearance_settings", "youtube video background"),
    SettingsEntry("Animated canvas", "Appearance", "appearance_settings", "canvas animation spotify"),
    SettingsEntry("Blur", "Appearance", "appearance_settings", "blur glass performance lag"),
    SettingsEntry("Audio quality badge", "Appearance", "appearance_settings", "badge quality indicator"),
    SettingsEntry("Player customization", "Appearance", "appearance_settings", "player customise cover style"),
    SettingsEntry("Player bottom card", "Appearance", "appearance_settings", "bottom card lyrics queue"),
    SettingsEntry("Crossfade", "Playback", "playback_settings", "fade transition gapless"),
    SettingsEntry("Equalizer", "Playback", "playback_settings", "eq bass treble"),
    SettingsEntry("Audio quality", "Playback", "playback_settings", "bitrate stream high low lossless"),
    SettingsEntry("Skip silence", "Playback", "playback_settings", "silence trim gap"),
    SettingsEntry("Audio normalization", "Playback", "playback_settings", "loudness volume gain"),
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Advanced audio settings", "Experimental", "experimental_settings", "monochrome lossless hi-res atmos"),
    SettingsEntry("Cipher", "Experimental", "experimental_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "experimental_settings", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),
    SettingsEntry("Reset app data", "Other settings", "other_settings", "wipe delete erase clear"),
)

@Composable
fun MusicfySettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    val results = remember(query) {
        val q = query.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            SettingsIndex.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.keywords.contains(q, ignoreCase = true) ||
                    it.page.contains(q, ignoreCase = true)
            }
        }
    }

    SubSettingsScaffold(
        title = "Settings",
        onBack = { navController.navigateUp() },
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search settings",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Text(
                    text = "Nothing matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                SettingsGroup(
                    style = SettingsGroupStyle.Grouped,
                    items = results.map { entry ->
                        SettingsItem(
                            title = { Text(entry.title) },
                            descriptionText = entry.page,
                            icon = painterResource(R.drawable.settings),
                            iconShape = CircleShape,
                            onClick = {
                                // Carried out-of-band rather than as a route argument: the target
                                // routes are registered without one, so "route?highlight=x" would
                                // simply fail to match and throw.
                                SettingsHighlight.pending = entry.highlightKey
                                navController.navigate(entry.route)
                            },
                        )
                    },
                )
            }
            return@SubSettingsScaffold
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Import data") },
                    descriptionText = "Import data from other music provider/musicfy backup",
                    icon = painterResource(R.drawable.backup),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("other_settings") },
                ),
            ),
        )

        Spacer(Modifier.height(12.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("General") },
                    icon = painterResource(R.drawable.settings),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("other_settings") },
                ),
                SettingsItem(
                    title = { Text("Appearance") },
                    icon = painterResource(R.drawable.contrast),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("appearance_settings") },
                ),
                SettingsItem(
                    title = { Text("Playback") },
                    icon = painterResource(R.drawable.play),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("playback_settings") },
                ),
                SettingsItem(
                    title = { Text("Experimental settings") },
                    icon = painterResource(R.drawable.biotech),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("experimental_settings") },
                ),
                SettingsItem(
                    title = { Text("Other settings") },
                    icon = painterResource(R.drawable.settings),
                    iconShape = CircleShape,
                    onClick = { navController.navigate("other_settings") },
                ),
            ),
        )

        Spacer(Modifier.height(120.dp))
    }
}
