// PlayerProgressSlider.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.media3.common.Player
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.ui.component.PlayerSliderTrack
import com.example.musicfy.utils.makeTimeString

@Composable
fun PlayerProgressSlider(
    modifier: Modifier = Modifier,
    /**
     * Whether the bar is on screen. The player stays composed while it's collapsed, and this used
     * to follow the playback position (15 times a second) all the same - recomposing the slider
     * and asking for a new frame each time, behind whatever screen was open. While inactive it
     * holds the last position and does nothing.
     */
    active: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val progressFlow = playerConnection.uiState.progressState
    val progress by produceState(initialValue = progressFlow.value, active) {
        if (active) progressFlow.collect { value = it }
    }
    val transportState by playerConnection.uiState.transportState.collectAsState()
    val seekBarStyle by com.example.musicfy.utils.rememberEnumPreference(
        com.example.musicfy.constants.SeekBarStyleKey,
        com.example.musicfy.ui.component.SeekBarStyle.DEFAULT,
    )

    // The Material 3 bars follow the artwork palette; the plain and default bars stay white so
    // they keep working over any cover.
    val artworkColor = com.example.musicfy.LocalArtworkColor.current
    val isM3Bar = seekBarStyle == com.example.musicfy.ui.component.SeekBarStyle.M3_EXPRESSIVE ||
        seekBarStyle == com.example.musicfy.ui.component.SeekBarStyle.M3_EXPRESSIVE_LINE
    val barActiveColor = if (isM3Bar) {
        com.example.musicfy.ui.theme.ArtworkPalette.from(artworkColor).accent
    } else {
        Color.White
    }

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    val displayedPosition = sliderPosition ?: progress.position

    val durationKnown = progress.duration != C.TIME_UNSET && progress.duration > 0L
    val buffering = transportState.playbackState == Player.STATE_BUFFERING
    // Loading the track itself (not a rebuffer mid-song): no length yet, or still at the start.
    // That's when the time read a meaningless 00:00 with nothing on the right.
    val loadingTrack = buffering && (!durationKnown || progress.position < LoadingStartWindowMs)
    // Held back a moment, so a short rebuffer after a seek doesn't flash the bar.
    val showBuffering by rememberSettled(buffering)
    val showLoading by rememberSettled(loadingTrack)

    val trackInteractionSource = remember { MutableInteractionSource() }
    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
    val trackHeight by animateDpAsState(
        targetValue = if (isTrackDragged || isTrackPressed) 14.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "playerProgressTrackHeight"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Slider(
            value = displayedPosition.toFloat(),
            valueRange = 0f..(if (progress.duration == C.TIME_UNSET || progress.duration <= 0L) 0f else progress.duration.toFloat()),
            onValueChange = { sliderPosition = it.toLong() },
            onValueChangeFinished = {
                sliderPosition?.let { playerConnection.player.seekTo(it) }
                sliderPosition = null
            },
            interactionSource = trackInteractionSource,
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                val range = sliderState.valueRange
                val span = (range.endInclusive - range.start).takeIf { it > 0f }
                val fraction = span?.let { (sliderState.value - range.start) / it } ?: 0f
                com.example.musicfy.ui.component.SeekBarTrack(
                    style = seekBarStyle,
                    fraction = fraction,
                    activeColor = barActiveColor,
                    inactiveColor = Color.White.copy(alpha = 0.24f),
                    animateWave = transportState.isPlaying,
                    active = isTrackDragged || isTrackPressed,
                    loading = showBuffering,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = PlayerHorizontalPadding)
        )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp)
    ) {
        val timeStyle = MaterialTheme.typography.labelMedium
        val timeColor = Color.White.copy(alpha = 0.85f)
        AnimatedContent(
            targetState = showLoading,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "seekTimeLoading",
        ) { loading ->
            if (loading) {
                LoadingLabel(style = timeStyle, color = timeColor)
            } else {
                Text(
                    text = makeTimeString(displayedPosition),
                    style = timeStyle,
                    color = timeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (durationKnown && !showLoading) makeTimeString(progress.duration) else "",
            style = timeStyle,
            color = timeColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Buffering this close to the start of a track counts as the track loading. */
private const val LoadingStartWindowMs = 1_500L

/** How long a loading state has to last before it shows. */
private const val LoadingShowDelayMs = 200L

/** [value], except a true only shows once it has held for [LoadingShowDelayMs]. False is instant. */
@Composable
private fun rememberSettled(value: Boolean): State<Boolean> = produceState(initialValue = false, value) {
    if (value) {
        kotlinx.coroutines.delay(LoadingShowDelayMs)
        this.value = true
    } else {
        this.value = false
    }
}

/** "Loading" with its three dots lighting one after another. */
@Composable
private fun LoadingLabel(style: TextStyle, color: Color) {
    val transition = rememberInfiniteTransition(label = "loadingLabel")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = "Loading", style = style, color = color, maxLines = 1)
        repeat(3) { index ->
            val lit by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "loadingDot$index",
            )
            Text(
                text = ".",
                style = style,
                color = color,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = lit },
            )
        }
    }
}
