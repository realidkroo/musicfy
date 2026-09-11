package com.example.musicfy.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Seek bar appearance, chosen per player style.
 *
 * The wavy variants are drawn as a [Path] rather than an AGSL shader, so unlike the lyrics glow
 * they animate identically on every supported Android version.
 */
enum class SeekBarStyle(val displayName: String) {
    DEFAULT("Default"),
    M3_EXPRESSIVE("Material 3 Expressive 1"),
    M3_EXPRESSIVE_LINE("Material 3 Expressive slider 2"),
    PLAIN("plain"),
}

private const val WaveSpeedMillis = 1400
private val WaveLength = 22.dp
private val WaveAmplitude = 3.5.dp

/**
 * Draws the seek bar for [style].
 *
 * [fraction] is playback position in 0..1. [active] lifts the bar while the user is dragging,
 * matching the existing default track's behaviour.
 */
@Composable
fun SeekBarTrack(
    style: SeekBarStyle,
    fraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    animateWave: Boolean = true,
    active: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "seekWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = WaveSpeedMillis, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seekWavePhase",
    )

    // The wave flattens out when playback is paused or the user grabs the bar, so the shape always
    // reflects whether audio is actually moving.
    val waveScale by animateFloatAsState(
        targetValue = if (animateWave && !active) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "seekWaveScale",
    )

    val height: Dp = when (style) {
        SeekBarStyle.DEFAULT -> if (active) 14.dp else 7.dp
        SeekBarStyle.M3_EXPRESSIVE, SeekBarStyle.M3_EXPRESSIVE_LINE -> 20.dp
        SeekBarStyle.PLAIN -> 16.dp
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val f = fraction.coerceIn(0f, 1f)
        when (style) {
            SeekBarStyle.DEFAULT -> drawDefaultTrack(f, activeColor, inactiveColor)
            SeekBarStyle.PLAIN -> drawPlainTrack(f, activeColor, inactiveColor)
            SeekBarStyle.M3_EXPRESSIVE -> drawWavyTrack(
                fraction = f,
                phase = phase,
                waveScale = waveScale,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumb = ThumbShape.ROUND,
            )
            SeekBarStyle.M3_EXPRESSIVE_LINE -> drawWavyTrack(
                fraction = f,
                phase = phase,
                waveScale = waveScale,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumb = ThumbShape.LINE,
            )
        }
    }
}

private enum class ThumbShape { ROUND, LINE }

private fun DrawScope.drawDefaultTrack(fraction: Float, activeColor: Color, inactiveColor: Color) {
    val h = size.height
    val y = size.height / 2f
    drawLine(
        color = inactiveColor,
        start = Offset(h / 2f, y),
        end = Offset(size.width - h / 2f, y),
        strokeWidth = h,
        cap = StrokeCap.Round,
    )
    val end = (h / 2f + (size.width - h) * fraction).coerceAtLeast(h / 2f)
    drawLine(
        color = activeColor,
        start = Offset(h / 2f, y),
        end = Offset(end, y),
        strokeWidth = h,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPlainTrack(fraction: Float, activeColor: Color, inactiveColor: Color) {
    val y = size.height / 2f
    val stroke = 4.dp.toPx()
    val thumbRadius = 7.dp.toPx()
    drawLine(
        color = inactiveColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    val x = size.width * fraction
    drawLine(
        color = activeColor,
        start = Offset(0f, y),
        end = Offset(x, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawCircle(color = activeColor, radius = thumbRadius, center = Offset(x, y))
}

/**
 * Material 3's expressive slider: the played portion ripples, the rest stays flat, and the thumb
 * sits on the boundary between them.
 */
private fun DrawScope.drawWavyTrack(
    fraction: Float,
    phase: Float,
    waveScale: Float,
    activeColor: Color,
    inactiveColor: Color,
    thumb: ThumbShape,
) {
    val y = size.height / 2f
    val stroke = 4.dp.toPx()
    val wavelength = WaveLength.toPx()
    val amplitude = WaveAmplitude.toPx() * waveScale

    val thumbGap = when (thumb) {
        ThumbShape.ROUND -> 9.dp.toPx()
        ThumbShape.LINE -> 7.dp.toPx()
    }
    val usable = (size.width - thumbGap).coerceAtLeast(0f)
    val x = usable * fraction

    if (x > 0.5f) {
        val path = Path().apply {
            moveTo(0f, y)
            var px = 0f
            // 2dp steps: fine enough that the curve reads as smooth, coarse enough to stay cheap
            // when this redraws every frame.
            val step = 2.dp.toPx()
            while (px < x) {
                val t = px / wavelength + phase * 2f
                lineTo(px, y + sin(t * 2f * Math.PI.toFloat()) * amplitude)
                px += step
            }
            lineTo(x, y + sin((x / wavelength + phase * 2f) * 2f * Math.PI.toFloat()) * amplitude)
        }
        drawPath(path = path, color = activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }

    if (x < size.width) {
        drawLine(
            color = inactiveColor,
            start = Offset((x + thumbGap).coerceAtMost(size.width), y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    when (thumb) {
        ThumbShape.ROUND -> drawCircle(color = activeColor, radius = 7.dp.toPx(), center = Offset(x, y))
        ThumbShape.LINE -> drawLine(
            color = activeColor,
            start = Offset(x, y - size.height / 2.4f),
            end = Offset(x, y + size.height / 2.4f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
