package com.example.musicfy.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

/** One pass of the loading sweep across the bar. */
private const val LoadingSweepMillis = 1500

/** The sweep's length, as a share of the bar. */
private const val LoadingSweepLength = 0.34f

/** How bright the sweep gets at its middle. */
private const val LoadingSweepPeak = 0.7f

/** Eases in and out of each pass, so the light speeds across the middle and slows at the ends. */
private val LoadingSweepEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

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
    /** The track is still loading: a light sweeps along the bar instead of the wave moving. */
    loading: Boolean = false,
) {
    val wavy = style == SeekBarStyle.M3_EXPRESSIVE || style == SeekBarStyle.M3_EXPRESSIVE_LINE

    // The wave flattens out when playback is paused, the user grabs the bar, or the track is still
    // loading, so the shape always reflects whether audio is actually moving.
    val waveScale by animateFloatAsState(
        targetValue = if (animateWave && !active && !loading) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "seekWaveScale",
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (loading) 1f else 0f,
        animationSpec = tween(durationMillis = if (loading) 320 else 220),
        label = "seekLoading",
    )

    // One clock for the wave and the loading sweep, running only while one of them is on screen.
    // This used to be an infinite transition that never stopped, so the bar - and the frame loop
    // with it - redrew every frame even with the music paused and the bar flat.
    val needsClock = (wavy && waveScale > 0f) || (wavy && animateWave && !active && !loading) ||
        loading || loadingAlpha > 0f
    val clockMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(needsClock) {
        if (!needsClock) return@LaunchedEffect
        while (true) {
            withFrameMillis { clockMs.longValue = it }
        }
    }

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
        val phase = if (wavy && waveScale > 0f) {
            (clockMs.longValue % WaveSpeedMillis) / WaveSpeedMillis.toFloat()
        } else {
            0f
        }
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
        if (loadingAlpha > 0.005f) {
            val t = (clockMs.longValue % LoadingSweepMillis) / LoadingSweepMillis.toFloat()
            drawLoadingSweep(
                progress = LoadingSweepEasing.transform(t),
                color = activeColor,
                alpha = loadingAlpha,
                thick = style == SeekBarStyle.DEFAULT,
            )
        }
    }
}

/**
 * A soft light running along the bar while the track loads. It fades in at its leading edge and
 * out at its tail, so it reads as light passing through the bar rather than a block moving on it.
 */
private fun DrawScope.drawLoadingSweep(progress: Float, color: Color, alpha: Float, thick: Boolean) {
    val y = size.height / 2f
    val stroke = if (thick) size.height else 4.dp.toPx()
    val inset = if (thick) size.height / 2f else 0f
    val length = size.width * LoadingSweepLength
    val centre = -length / 2f + (size.width + length) * progress
    val from = centre - length / 2f
    val to = centre + length / 2f
    val start = from.coerceAtLeast(inset)
    val end = to.coerceAtMost(size.width - inset)
    if (end <= start) return
    drawLine(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = color.alpha * LoadingSweepPeak * alpha),
            1f to Color.Transparent,
            startX = from,
            endX = to,
        ),
        start = Offset(start, y),
        end = Offset(end, y),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
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
