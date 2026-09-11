package com.example.musicfy.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.R

/**
 * Transport control appearance.
 *
 * [DEFAULT] keeps the existing oversized glyphs; the rest are drawn here. The Material 3 variants
 * take their colours from the caller so they can follow the artwork palette rather than the
 * device theme.
 */
enum class ButtonStyle(val displayName: String) {
    DEFAULT("Default"),
    M3_EXPRESSIVE("Material 3 Expressive"),
    M3_EXPRESSIVE_PLAIN("Material 3 Expressive - no label"),
    M3_TONAL("Material 3 tonal"),
    LEGACY("Android legacy"),
}

/**
 * Colours for the drawn button styles.
 *
 * Defaults are the neutral white-on-glass look; the player passes an artwork-derived palette in
 * so Material 3 styles pick up the cover instead of the wallpaper.
 */
data class TransportColors(
    val accent: Color = Color.White,
    val onAccent: Color = Color.Black,
    val container: Color = Color.White.copy(alpha = 0.15f),
    val onContainer: Color = Color.White,
)

@Composable
fun PlayerTransportButtons(
    style: ButtonStyle,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    colors: TransportColors = TransportColors(),
    buttonSize: Dp = 74.dp,
    iconSize: Dp = 54.dp,
    gap: Dp = 30.dp,
) {
    when (style) {
        ButtonStyle.M3_EXPRESSIVE, ButtonStyle.M3_EXPRESSIVE_PLAIN, ButtonStyle.M3_TONAL ->
            M3Transport(
                style = style,
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                colors = colors,
                modifier = modifier,
            )

        ButtonStyle.LEGACY -> LegacyTransport(
            isPlaying = isPlaying,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            colors = colors,
            modifier = modifier,
        )

        ButtonStyle.DEFAULT -> Unit // Drawn by the caller's existing animated glyphs.
    }
}

@Composable
private fun M3Transport(
    style: ButtonStyle,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    colors: TransportColors,
    modifier: Modifier = Modifier,
) {
    val showLabel = style == ButtonStyle.M3_EXPRESSIVE
    val tonal = style == ButtonStyle.M3_TONAL

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        M3RoundButton(
            icon = R.drawable.skip_previous,
            enabled = canSkipPrevious,
            onClick = onPrevious,
            container = colors.container,
            content = colors.onContainer,
        )

        // The play control is a pill rather than a circle - the shape difference is what makes it
        // read as primary without needing to be larger than everything else.
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pillWidth by animateDpAsState(
            targetValue = if (pressed) 118.dp else 128.dp,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
            label = "m3PlayPill",
        )

        Row(
            modifier = Modifier
                .width(pillWidth)
                .height(56.dp)
                .clip(RoundedCornerShape(if (tonal) 18.dp else 28.dp))
                .background(if (tonal) colors.container else colors.accent)
                .clickable(interactionSource = interaction, indication = null, onClick = onPlayPause),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (tonal) colors.onContainer else colors.onAccent,
                modifier = Modifier.size(22.dp),
            )
            if (showLabel) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "Pause" else "Play",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (tonal) colors.onContainer else colors.onAccent,
                )
            }
        }

        M3RoundButton(
            icon = R.drawable.skip_next,
            enabled = canSkipNext,
            onClick = onNext,
            container = colors.container,
            content = colors.onContainer,
        )
    }
}

@Composable
private fun M3RoundButton(
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) content else content.copy(alpha = 0.38f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The pre-Material look: flat evenly spaced glyphs with no containers. */
@Composable
private fun LegacyTransport(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    colors: TransportColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegacyGlyph(R.drawable.skip_previous, canSkipPrevious, onPrevious, colors.onContainer, 34.dp)
        LegacyGlyph(
            icon = if (isPlaying) R.drawable.pause else R.drawable.play,
            enabled = true,
            onClick = onPlayPause,
            tint = colors.onContainer,
            size = 44.dp,
        )
        LegacyGlyph(R.drawable.skip_next, canSkipNext, onNext, colors.onContainer, 34.dp)
    }
}

@Composable
private fun LegacyGlyph(icon: Int, enabled: Boolean, onClick: () -> Unit, tint: Color, size: Dp) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}
