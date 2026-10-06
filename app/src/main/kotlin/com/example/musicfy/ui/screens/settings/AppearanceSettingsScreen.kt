// AppearanceSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.BlurStrengthPxKey
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.EnableElementBlurAnimatorKey
import com.example.musicfy.constants.EnableProgressiveBlurKey
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.LyricsMotionStyle
import com.example.musicfy.constants.LyricsMotionStyleKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.DefaultPlayerSeekBarSlider
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SubSettingsSearchBar
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

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
    val (disableBlur, onDisableBlurChange) = rememberPreference(
        DisableBlurKey,
        defaultValue = false
    )
    val (progressiveBlur, onProgressiveBlurChange) = rememberPreference(
        EnableProgressiveBlurKey,
        defaultValue = true
    )
    val (elementBlurAnimator, onElementBlurAnimatorChange) = rememberPreference(
        EnableElementBlurAnimatorKey,
        defaultValue = true
    )
    val (blurStrengthPx, onBlurStrengthPxChange) = rememberPreference(
        BlurStrengthPxKey,
        defaultValue = 25f
    )
    val (lyricsWaveAnimation, onLyricsWaveAnimationChange) = rememberPreference(
        LyricsWaveAnimationKey,
        defaultValue = true
    )
    val (lyricsHighBloom, onLyricsHighBloomChange) = rememberPreference(
        LyricsHighBloomKey,
        defaultValue = true
    )
    val (lyricsMotionStyle, onLyricsMotionStyleChange) = rememberEnumPreference(
        LyricsMotionStyleKey,
        defaultValue = LyricsMotionStyle.MOTION,
    )

    SubSettingsScaffold(
        title = "Appearance",
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        // Section 1: Player appearance
        Text(
            text = "Player appearance",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )

        // Card 1: Player appearance pages
        val playerPageItems = listOf(
            SettingsItem(
                title = { Text("Customize player") },
                highlightKey = "Player customization",
                descriptionText = "Customize player cover, background, style",
                icon = painterResource(R.drawable.album),
                iconShape = CircleShape,
                onClick = { navController.navigate("player_customize") },
            ),
            SettingsItem(
                title = { Text("Customize lyrics tab") },
                highlightKey = "Lyrics letter animation",
                descriptionText = "Lyrics style and typography",
                icon = painterResource(R.drawable.lyrics),
                iconShape = CircleShape,
                onClick = { onLyricsWaveAnimationChange(!lyricsWaveAnimation) },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (playerPageItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = playerPageItems,
            )
            Spacer(Modifier.height(14.dp))
        }

        // Card 2: Player appearance toggles
        val playerToggleItems = listOf(
            SettingsItem(
                title = { Text("Lyrics animation") },
                highlightKey = "Lyrics animation style",
                descriptionText = lyricsMotionStyle.description,
                icon = painterResource(R.drawable.lyrics),
                iconShape = CircleShape,
                onClick = {
                    onLyricsMotionStyleChange(
                        if (lyricsMotionStyle == LyricsMotionStyle.MOTION) LyricsMotionStyle.LEGACY else LyricsMotionStyle.MOTION
                    )
                },
                trailingContent = {
                    LyricsStyleToggle(selected = lyricsMotionStyle, onSelect = onLyricsMotionStyleChange)
                },
            ),
            SettingsItem(
                title = { Text("Lyrics Shader") },
                highlightKey = "Lyrics letter animation",
                // Style 1's rise is drawn, not shaded, so it keeps working with this off.
                descriptionText = "Glow on sung lyrics (and Style 2's letter wave)",
                icon = painterResource(R.drawable.biotech),
                iconShape = CircleShape,
                onClick = { onLyricsWaveAnimationChange(!lyricsWaveAnimation) },
                trailingContent = {
                    AppSwitch(
                        checked = lyricsWaveAnimation,
                        onCheckedChange = onLyricsWaveAnimationChange,
                    )
                },
            ),
            SettingsItem(
                title = { Text("High quality bloom") },
                highlightKey = "High quality bloom",
                descriptionText = "Extra soft bloom pass on lyrics",
                icon = painterResource(R.drawable.sparks),
                iconShape = CircleShape,
                isVisible = lyricsWaveAnimation,
                isSubOption = true,
                onClick = { onLyricsHighBloomChange(!lyricsHighBloom) },
                trailingContent = {
                    AppSwitch(
                        checked = lyricsHighBloom,
                        onCheckedChange = onLyricsHighBloomChange,
                        enabled = lyricsWaveAnimation,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Always enable Youtube background") },
                highlightKey = "Yt video background",
                descriptionText = "Stream video background on player when available",
                icon = painterResource(R.drawable.slow_motion_video),
                iconShape = CircleShape,
                onClick = { onPlayVideoBackgroundChange(!playVideoBackground) },
                trailingContent = {
                    AppSwitch(
                        checked = playVideoBackground,
                        onCheckedChange = onPlayVideoBackgroundChange,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Timestamp matching") },
                descriptionText = "Based on subtitle",
                icon = painterResource(R.drawable.lyrics),
                iconShape = CircleShape,
                isVisible = playVideoBackground,
                isSubOption = true,
                onClick = { onLyricsSyncChange(!lyricsSync) },
                trailingContent = {
                    AppSwitch(
                        checked = lyricsSync,
                        onCheckedChange = onLyricsSyncChange,
                        enabled = playVideoBackground,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Enable animated canvas") },
                highlightKey = "Animated canvas",
                descriptionText = "Looping visuals for supported tracks",
                icon = painterResource(R.drawable.sparks),
                iconShape = CircleShape,
                onClick = { onCanvasEnabledChange(!canvasEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = canvasEnabled,
                        onCheckedChange = onCanvasEnabledChange,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Canvas on Wi-Fi only") },
                descriptionText = "Skip mobile data",
                icon = painterResource(R.drawable.wifi_proxy),
                iconShape = CircleShape,
                isVisible = canvasEnabled,
                isSubOption = true,
                onClick = { onCanvasWifiOnlyChange(!canvasWifiOnly) },
                trailingContent = {
                    AppSwitch(
                        checked = canvasWifiOnly,
                        onCheckedChange = onCanvasWifiOnlyChange,
                        enabled = canvasEnabled,
                    )
                },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (playerToggleItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = playerToggleItems,
            )
            Spacer(Modifier.height(18.dp))
        }

        // Section 2: Global Appearance
        Text(
            text = "Global Appearance",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )

        // Card 3: Global Appearance toggles
        val globalToggleItems = listOf(
            SettingsItem(
                title = { Text("Blur") },
                highlightKey = "Blur",
                descriptionText = "Glass blur across player and navigation",
                icon = painterResource(R.drawable.contrast),
                iconShape = CircleShape,
                onClick = { onDisableBlurChange(!disableBlur) },
                trailingContent = {
                    AppSwitch(
                        checked = !disableBlur,
                        onCheckedChange = { enabled -> onDisableBlurChange(!enabled) },
                    )
                },
            ),
            SettingsItem(
                title = { Text("Progressive blur") },
                descriptionText = "Smooth progressive gradient blur",
                icon = painterResource(R.drawable.gradient),
                iconShape = CircleShape,
                isVisible = !disableBlur,
                isSubOption = true,
                onClick = { onProgressiveBlurChange(!progressiveBlur) },
                trailingContent = {
                    AppSwitch(
                        checked = progressiveBlur,
                        onCheckedChange = onProgressiveBlurChange,
                        enabled = !disableBlur,
                    )
                },
            ),
            SettingsItem(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Blur strength")
                        Text(
                            text = "${blurStrengthPx.roundToInt()}px",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                    }
                },
                description = {
                    DefaultPlayerSeekBarSlider(
                        value = blurStrengthPx,
                        onValueChange = { onBlurStrengthPxChange(it) },
                        valueRange = 10f..50f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                },
                icon = painterResource(R.drawable.tune),
                iconShape = CircleShape,
                isVisible = !disableBlur,
                isSubOption = true,
            ),
            SettingsItem(
                title = { Text("Animation") },
                highlightKey = "Animation",
                descriptionText = "Smooth UI transitions and element blurs",
                icon = painterResource(R.drawable.speed),
                iconShape = CircleShape,
                onClick = { onElementBlurAnimatorChange(!elementBlurAnimator) },
                trailingContent = {
                    AppSwitch(
                        checked = elementBlurAnimator,
                        onCheckedChange = onElementBlurAnimatorChange,
                    )
                },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (globalToggleItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = globalToggleItems,
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}

/** Two-option pill for the lyrics animation style: "1" is the new motion, "2" the legacy wave. */
@Composable
private fun LyricsStyleToggle(
    selected: LyricsMotionStyle,
    onSelect: (LyricsMotionStyle) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LyricsMotionStyle.entries.forEach { style ->
            val isSelected = style == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Transparent,
                label = "lyricsStyleChip",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                label = "lyricsStyleChipText",
            )
            Text(
                text = style.displayName,
                color = content,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(style) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
