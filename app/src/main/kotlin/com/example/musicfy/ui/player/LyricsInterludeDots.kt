// LyricsInterludeDots.kt
// The three dots that count down an instrumental gap between two sung lines.
//
// Two things about the previous version made this "just broken":
//
//   1. There were two competing implementations. LyricsUtils.insertInstrumentalPauses() injected
//      literal LyricsEntry(text = "•••") rows into the parsed lyrics for gaps over 15s, while this
//      composable drew an animated row for gaps over 4s. A long gap therefore rendered BOTH — a
//      static 32sp "•••" that never animated and never went away, sitting next to the real dots.
//      The injected rows also counted as lyric lines for indexing and scrolling. Those rows are
//      gone now; this is the only interlude UI.
//
//   2. The animation was driven by six animateFloatAsState calls whose targetValue was recomputed
//      from the playback position on every frame. Setting a new target every frame restarts the
//      animation every frame, so nothing ever settled and each frame allocated a new spec — the
//      jank was the animation system fighting itself. Progress is a direct function of time here,
//      so it needs no animation at all: it is read in the draw phase and applied in a
//      graphicsLayer, which never recomposes and never relayouts.
//
// Enter and exit are still animated, because those are genuine state changes rather than a
// continuous function of the clock.

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val DotCount = 3

/** Gaps shorter than this are just breathing room between lines, not an interlude. */
const val MinInterludeGapMs = 4_000L

/** Row height, fixed so the item never changes size as the dots pulse. */
private val RowHeight = 56.dp

private const val RestAlpha = 0.25f
private const val RestScale = 0.78f
private const val LitScale = 1.12f

/** How far past the dot the glow reaches, as a multiple of its radius. */
private const val GlowRadiusFactor = 2.6f
private const val GlowPeakAlpha = 0.5f

/**
 * Floor applied to the accent before it is used as a glow.
 *
 * The accent arrives from PlayerColorExtractor.darkenIfTooLight(), which is exactly right for text
 * sitting ON the colour and exactly wrong for light emitted BY something: a dark-artwork track
 * produced a near-black accent, so the dots lerped white -> almost-black as they lit and read as
 * black dots. Light sources get lightened, never darkened.
 */
private fun Color.asGlow(): Color {
    val target = 0.85f
    return Color(
        red = red + (target - red).coerceAtLeast(0f) * 0.65f,
        green = green + (target - green).coerceAtLeast(0f) * 0.65f,
        blue = blue + (target - blue).coerceAtLeast(0f) * 0.65f,
        alpha = 1f,
    )
}

/** How long a dot takes to travel from unlit to lit, as a fraction of its own slice. */
private const val DotFadeFraction = 0.55f

/**
 * The last beat before the next line: all three dots swell together, so the gap ends on a visible
 * cue instead of the row simply vanishing.
 */
private const val FinaleFraction = 0.88f

/**
 * @param startMs when the previous line stopped being sung.
 * @param endMs when the next line starts.
 * @param positionProvider read every frame in the draw phase. Deliberately a lambda rather than a
 *   Long parameter: passing the position by value would recompose this row 60+ times a second, and
 *   inside a LazyColumn that is the difference between a smooth scroll and a stuttering one.
 * @param visible drives the enter/exit fade. Passing false does not remove the row — the caller
 *   keeps it in the list until the fade finishes, so the list never changes height mid-animation.
 */
@Composable
fun LyricsInterludeDots(
    startMs: Long,
    endMs: Long,
    positionProvider: () -> Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    /** Which side the gap sits on, following the voice that is about to sing. */
    alignment: LyricsAlignment = LyricsAlignment.CENTER,
) {
    val span = (endMs - startMs).coerceAtLeast(1L)
    val glowColor = remember(accentColor) { accentColor.asGlow() }

    // One float per dot, written in a frame callback and read in graphicsLayer. Neither step
    // schedules a recomposition.
    val fills = remember { Array(DotCount) { mutableFloatStateOf(0f) } }
    val finale = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(startMs, endMs, positionProvider) {
        while (true) {
            withFrameMillis {
                val progress = ((positionProvider() - startMs).toFloat() / span).coerceIn(0f, 1f)
                for (index in 0 until DotCount) {
                    val sliceStart = index.toFloat() / DotCount
                    val within = (progress - sliceStart) * DotCount / DotFadeFraction
                    fills[index].floatValue = smoothstep(within)
                }
                finale.floatValue =
                    smoothstep((progress - FinaleFraction) / (1f - FinaleFraction))
            }
        }
    }

    val presence by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "interludePresence",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = 36.dp)
            .graphicsLayer { alpha = presence },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when (alignment) {
            LyricsAlignment.START -> Arrangement.Start
            LyricsAlignment.CENTER -> Arrangement.Center
            LyricsAlignment.END -> Arrangement.End
        },
    ) {
        repeat(DotCount) { index ->
            Spacer(
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        val fill = fills[index].floatValue
                        // The finale lifts every dot, including ones already lit, so the whole
                        // row pulses once just before the next line arrives.
                        val swell = finale.floatValue
                        val s = RestScale + (LitScale - RestScale) * fill + 0.14f * swell
                        scaleX = s
                        scaleY = s
                        alpha = RestAlpha + (1f - RestAlpha) * maxOf(fill, swell)
                    }
                    // drawBehind, not background(): the colour depends on the per-frame fill, and
                    // reading that state during composition would subscribe this row to it and
                    // recompose it every single frame — the very thing this rewrite removes.
                    .drawBehind {
                        val fill = fills[index].floatValue
                        val glow = maxOf(fill, finale.floatValue)
                        val radius = size.minDimension / 2f

                        // The glow is the only thing carrying the artwork's colour. The dot itself
                        // stays white and only brightens, so it can never render as a dark blob on
                        // a dark-artwork track.
                        if (glow > 0.01f) {
                            val glowRadius = radius * GlowRadiusFactor
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = GlowPeakAlpha * glow),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = glowRadius,
                                ),
                                radius = glowRadius,
                            )
                        }

                        drawCircle(Color.White.copy(alpha = 0.75f + 0.25f * fill), radius)
                    }
            )
            if (index < DotCount - 1) Spacer(modifier = Modifier.size(10.dp))
        }
    }
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}
