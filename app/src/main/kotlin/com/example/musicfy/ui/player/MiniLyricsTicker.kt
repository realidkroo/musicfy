// MiniLyricsTicker.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.LyricsMotionStyle
import com.example.musicfy.constants.LyricsMotionStyleKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val TickerFontSize = 17.sp
private val TickerLineHeight = 22.sp

/** The letter motion is sized for 32sp lines; scaled down to match. */
private const val TickerMotionScale = 17f / 32f

/** Soft edges where the line runs past the card. */
private val TickerEdgeFade = 18.dp

/**
 * How far into the card the sweep is kept while a long line slides. Past the middle, so the reader
 * still sees a little of what's coming.
 */
private const val SweepKeepFraction = 0.62f

/** Critically damped follow time of the sideways slide: it trails the sweep, never jumps. */
private const val SlideFollowSec = 0.32f

/** Interlude dots at card size. */
private const val DotsScale = 0.5f

/** The dots row is 56dp tall with its dots inset by the lyric margin; this undoes that inset. */
private val DotsRowInset = LyricsHeaderArtX

private val LineChangeSpring = spring(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
private val LineFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** What the card is showing. Equal slots are the same content, so nothing re-animates. */
@Immutable
private data class TickerSlot(val kind: Int, val index: Int) {
    companion object {
        const val EMPTY = 0
        const val LINE = 1
        const val GAP = 2
        const val END = 3
        /** The lyrics are still being looked up. */
        const val LOADING = 4
    }
}

private fun tickerSlot(lines: List<LyricsEntry>, position: Long): TickerSlot {
    if (lines.isEmpty()) return TickerSlot(TickerSlot.EMPTY, -1)
    val index = LyricsUtils.findCurrentLineIndex(lines, position)
    if (index >= lines.size) return TickerSlot(TickerSlot.END, lines.lastIndex)
    if (index < 0) {
        return if (lines.first().time >= MinInterludeGapMs) {
            TickerSlot(TickerSlot.GAP, -1)
        } else {
            TickerSlot(TickerSlot.LINE, 0)
        }
    }
    // A long instrumental gap after this line reads as dots, like on the lyrics page.
    val next = lines.getOrNull(index + 1)
    if (next != null) {
        val end = LyricsUtils.lineEndMs(lines, index)
        if (next.time - end >= MinInterludeGapMs && position > end) {
            return TickerSlot(TickerSlot.GAP, index)
        }
    }
    return TickerSlot(TickerSlot.LINE, index)
}

/** The line the card is showing at [position], or -1 while it shows something else (a gap). */
internal fun cardLyricLineIndex(lines: List<LyricsEntry>, position: Long): Int {
    val slot = tickerSlot(lines, position)
    return if (slot.kind == TickerSlot.LINE) slot.index else -1
}

/**
 * The lyrics page, card-sized: the current line with the same karaoke sweep and letter motion. A
 * line too long for the card slides left as it's sung, keeping the sweep in view, and each new line
 * rolls up from below as the last one leaves.
 *
 * Only the slot (which line, or a gap) is collected from the playback position, so the card
 * recomposes once per line, not on every position tick; the sweep itself runs in frame callbacks.
 */
@Composable
internal fun MiniLyricsTicker(
    lines: List<LyricsEntry>,
    /** Lyrics have been looked up (an empty [lines] then means there are none). */
    loaded: Boolean,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position }
    }
    val slot by remember(lines, playerConnection) {
        playerConnection.progressState
            .map { tickerSlot(lines, it.position) }
            .distinctUntilChanged()
    }.collectAsState(initial = tickerSlot(lines, playerConnection.progressState.value.position))

    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (waveEnabled) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    // Still looking the lyrics up is its own state, so the dots hand over to the first line (or to
    // "No lyrics") with the same roll as any line change.
    val shown = if (!loaded && slot.kind == TickerSlot.EMPTY) TickerSlot(TickerSlot.LOADING, -1) else slot

    AnimatedContent(
        targetState = shown,
        transitionSpec = {
            // Next line: the old one rolls up and out, the new one up and in - the lyrics page's
            // scroll, in a card.
            (slideInVertically(LineChangeSpring) { it } + fadeIn(LineFadeSpring))
                .togetherWith(slideOutVertically(LineChangeSpring) { -it } + fadeOut(LineFadeSpring))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "miniLyricsLine",
        modifier = modifier.clipToBounds(),
    ) { current ->
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
            when (current.kind) {
                TickerSlot.LINE, TickerSlot.END -> lines.getOrNull(current.index)?.let { entry ->
                    TickerLine(
                        entry = entry,
                        dim = current.kind == TickerSlot.END,
                        lineEndMs = LyricsUtils.lineEndMs(lines, current.index),
                        positionProvider = positionProvider,
                        motionStyle = motionStyle,
                        waveEnabled = waveEnabled,
                        highBloom = highBloom,
                        morph = morph,
                    )
                }

                TickerSlot.GAP -> {
                    val startMs = if (current.index < 0) 0L else LyricsUtils.lineEndMs(lines, current.index)
                    val endMs = lines.getOrNull(current.index + 1)?.time ?: startMs
                    LyricsInterludeDots(
                        startMs = startMs,
                        endMs = endMs,
                        positionProvider = positionProvider,
                        accentColor = Color.White,
                        alignment = LyricsAlignment.START,
                        modifier = Modifier.graphicsLayer {
                            scaleX = DotsScale
                            scaleY = DotsScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            translationX = -DotsRowInset.toPx() * DotsScale
                        },
                    )
                }

                TickerSlot.LOADING -> LyricsLoadingDots(alignStart = true)

                else -> Text(
                    text = "No lyrics for this song",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TickerLine(
    entry: LyricsEntry,
    dim: Boolean,
    lineEndMs: Long,
    positionProvider: () -> Long,
    motionStyle: LyricsMotionStyle,
    waveEnabled: Boolean,
    highBloom: Boolean,
    morph: CardMorphState,
) {
    val parts = remember(entry.text, entry.words) { splitBackingVocal(entry.text, entry.words) }
    val text = parts.lead.ifBlank { entry.text }
    val words = parts.leadWords
    val karaoke = !dim && !words.isNullOrEmpty() && text.isNotEmpty()

    val probe = remember(entry) { SweepProbe() }
    val viewport = remember { mutableFloatStateOf(0f) }
    val scroll = remember(entry) { mutableFloatStateOf(0f) }

    // The sideways slide: follows the sweep for a word-synced line, the clock for a line-synced
    // one. A critically damped follow, so a sweep that jumps (a seek) still glides.
    LaunchedEffect(entry, karaoke) {
        var value = 0f
        var velocity = 0f
        var lastMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                val dt = if (lastMs < 0L) 0.016f else ((frameMs - lastMs) / 1000f).coerceIn(0f, 0.064f)
                lastMs = frameMs
                val overflow = (probe.textWidth - viewport.floatValue).coerceAtLeast(0f)
                val target = when {
                    overflow <= 0f -> 0f
                    karaoke -> (probe.headX - viewport.floatValue * SweepKeepFraction).coerceIn(0f, overflow)
                    else -> {
                        val span = (lineEndMs - entry.time).coerceAtLeast(1L).toFloat()
                        val through = (positionProvider() - entry.time) / span
                        overflow * smoothstepF((through - 0.1f) / 0.75f)
                    }
                }
                val omega = 1f / SlideFollowSec
                val y = value - target
                val e = kotlin.math.exp(-omega * dt)
                val tmp = (velocity + omega * y) * dt
                value = target + (y + tmp) * e
                velocity = (velocity - omega * tmp) * e
                if (kotlin.math.abs(value - target) < 0.05f && kotlin.math.abs(velocity) < 1f) {
                    value = target
                    velocity = 0f
                }
                if (value != scroll.floatValue) scroll.floatValue = value
                morph.cardLyricScroll = value
            }
        }
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport.floatValue = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade only the edge the line actually runs past, and only as far as it does.
                val overflow = (probe.textWidth - size.width).coerceAtLeast(0f)
                if (overflow <= 0f) return@drawWithContent
                val fade = TickerEdgeFade.toPx()
                val left = (scroll.floatValue / fade).coerceIn(0f, 1f)
                val right = ((overflow - scroll.floatValue) / fade).coerceIn(0f, 1f)
                val edge = (fade / size.width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 1f - left),
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - right),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -scroll.floatValue },
        ) {
            if (karaoke) {
                KaraokeSweepText(
                    text = text,
                    words = words!!,
                    positionProvider = positionProvider,
                    waveAmplitudeProvider = { if (waveEnabled) 1f else 0f },
                    riseAmplitudeProvider = { 1f },
                    baseColor = Color.White.copy(alpha = 0.45f),
                    highlightColor = Color.White,
                    fontSize = TickerFontSize,
                    lineHeight = TickerLineHeight,
                    highBloom = highBloom,
                    motionStyle = motionStyle,
                    singleLine = true,
                    motionScale = TickerMotionScale,
                    probe = probe,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = TickerFontSize,
                        lineHeight = TickerLineHeight,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (dim) 0.55f else 1f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { probe.textWidth = it.size.width.toFloat() },
                )
            }
        }
    }
}
