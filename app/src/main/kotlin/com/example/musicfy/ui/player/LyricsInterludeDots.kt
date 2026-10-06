// LyricsInterludeDots.kt
// The three dots that count down an instrumental gap between two sung lines.
//
// Two things about an earlier version made this "just broken", and both still matter:
//
//   1. There were two competing implementations. LyricsUtils.insertInstrumentalPauses() injected
//      literal LyricsEntry(text = "•••") rows into the parsed lyrics for gaps over 15s, while this
//      composable drew an animated row for gaps over 4s. Those injected rows are gone; this is the
//      only interlude UI.
//
//   2. Progress is a direct function of the playback clock, so it is read in a frame callback and
//      applied in graphicsLayer/drawBehind - never through animateFloatAsState with a target that
//      changes every frame (that restarted the animation every frame and never settled).
//
// What it does now, Apple Music style: the row grows in on a spring when the gap starts (the
// lines below move down to make room) and collapses when the next line takes over, in step with
// the recenter scroll, so that line slides up into the dots' place. The dots pop in with a little
// spring, bob slowly while they wait, and each one glows only as it lights up, then lets the glow
// fade.

package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DotCount = 3

/** Gaps shorter than this are just breathing room between lines, not an interlude. */
const val MinInterludeGapMs = 4_000L

/** Height of the row when fully grown in. */
private val RowHeight = 56.dp

private val DotSize = 12.dp
private val DotGap = 7.dp

private const val RestAlpha = 0.3f
private const val RestScale = 0.8f
private const val LitScale = 1.1f

/** How far past the dot the glow reaches, as a multiple of its radius. */
private const val GlowRadiusFactor = 2.8f
private const val GlowPeakAlpha = 0.55f

/** After a dot finishes lighting, its glow fades over roughly this long. */
private const val GlowFadeMs = 900f

/** Idle bob: one slow breath up and down, each dot a little behind the one before it. */
private const val BobPeriodMs = 2_600f
private val BobHeight = 1.8.dp
private const val BobPhaseStep = 0.9f

/** Pop-in stagger between dots. */
private const val PopStaggerMs = 70L

/**
 * Floor applied to the accent before it is used as a glow.
 *
 * The accent arrives from PlayerColorExtractor.darkenIfTooLight(), which is right for text sitting
 * ON the colour and wrong for light emitted BY something: a dark-artwork track gave a near-black
 * glow. Light sources get lightened, never darkened.
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
 * @param positionProvider read every frame, never passed by value (that would recompose the row
 *   60+ times a second inside a LazyColumn).
 * @param visible whether this gap is the one playing. Drives the row growing in / collapsing; the
 *   row itself stays in the list either way, at zero height when hidden.
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
    /** Must be the recenter scroll's curve, so the collapse and the scroll are one motion. */
    collapseEasing: androidx.compose.animation.core.Easing = LyricsRecenterEasing,
) {
    val span = (endMs - startMs).coerceAtLeast(1L)
    val glowColor = remember(accentColor) { accentColor.asGlow() }

    // Written in a frame callback and read in graphicsLayer/drawBehind - no recomposition.
    val fills = remember { Array(DotCount) { mutableFloatStateOf(0f) } }
    val glows = remember { Array(DotCount) { mutableFloatStateOf(0f) } }
    val finale = remember { mutableFloatStateOf(0f) }
    val clockMs = remember { mutableLongStateOf(0L) }

    val height = remember { Animatable(if (visible) 1f else 0f) }
    val pops = remember { Array(DotCount) { Animatable(if (visible) 1f else 0f) } }

    LaunchedEffect(visible) {
        if (visible) {
            launch {
                height.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            pops.forEachIndexed { index, pop ->
                launch {
                    delay(index * PopStaggerMs)
                    // A touch of bounce: the dots land, they don't just appear.
                    pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
                }
            }
        } else {
            // Same duration and easing as the recenter scroll that runs at this exact moment, so the
            // next line slides into the dots' place in one motion (see LyricsScreen).
            launch {
                height.animateTo(0f, tween(LyricsRecenterDurationMs, easing = collapseEasing))
            }
            pops.forEach { pop ->
                launch { pop.animateTo(0f, tween(LyricsRecenterDurationMs / 2, easing = collapseEasing)) }
            }
        }
    }

    // Only ticks while the row is on screen and grown; a hidden gap row costs nothing per frame.
    LaunchedEffect(startMs, endMs, positionProvider, visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameMs ->
                clockMs.longValue = frameMs
                val progress = ((positionProvider() - startMs).toFloat() / span).coerceIn(0f, 1f)
                val finaleNow = smoothstep((progress - FinaleFraction) / (1f - FinaleFraction))
                finale.floatValue = finaleNow
                for (index in 0 until DotCount) {
                    val sliceStart = index.toFloat() / DotCount
                    val within = (progress - sliceStart) * DotCount / DotFadeFraction
                    val fill = smoothstep(within)
                    fills[index].floatValue = fill

                    // Glow rides the fill up while the dot is lighting, then lets go slowly once it's
                    // lit - a flare, not a constant halo. The finale gives one last soft pulse.
                    val litAt = sliceStart + DotFadeFraction / DotCount
                    val glow = if (progress < litAt) {
                        fill
                    } else {
                        exp(-((progress - litAt) * span) / GlowFadeMs)
                    }
                    glows[index].floatValue = maxOf(glow, sin(PI.toFloat() * finaleNow) * 0.7f)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The row's height is part of the list's layout, so it's set here rather than faked
            // with a translation: lines below really move to make room, and really take the space
            // back when the gap ends.
            .layout { measurable, constraints ->
                val full = RowHeight.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                val shown = (full * height.value.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, shown) {
                    placeable.place(0, (shown - full) / 2)
                }
            }
            .height(RowHeight)
            .padding(horizontal = LyricsHeaderArtX)
            .graphicsLayer { alpha = height.value.coerceIn(0f, 1f) },
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
                    .size(DotSize)
                    .graphicsLayer {
                        val fill = fills[index].floatValue
                        // The finale lifts every dot, including lit ones, so the whole row pulses
                        // once just before the next line arrives.
                        val swell = finale.floatValue
                        val pop = pops[index].value
                        val s = (RestScale + (LitScale - RestScale) * fill + 0.14f * swell) * pop
                        scaleX = s
                        scaleY = s
                        alpha = (RestAlpha + (1f - RestAlpha) * maxOf(fill, swell)) * pop.coerceIn(0f, 1f)

                        val phase = clockMs.longValue / BobPeriodMs * 2f * PI.toFloat() - index * BobPhaseStep
                        translationY = sin(phase) * BobHeight.toPx()
                    }
                    // drawBehind, not background(): the colour depends on per-frame state, and
                    // reading it in composition would recompose this row every frame.
                    .drawBehind {
                        val fill = fills[index].floatValue
                        val glow = glows[index].floatValue
                        val radius = size.minDimension / 2f

                        // The glow is the only thing carrying the artwork's colour. The dot stays
                        // white and only brightens, so it can't read as a dark blob.
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
            if (index < DotCount - 1) Spacer(modifier = Modifier.size(DotGap))
        }
    }
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
