// LyricsGlowLine.kt

package com.example.musicfy.ui.player

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.ui.component.agslRenderEffect
import com.example.musicfy.ui.component.createAgslShader
import com.example.musicfy.ui.component.setAgslUniform

enum class LyricsLineState { ACTIVE, UPCOMING, PAST, DEFAULT }

/**
 * Which side of the screen a line sits on.
 *
 * Word-synced providers mark duet and call-and-response lines with `{agent:v1}` / `{agent:v2}`,
 * which LyricsUtils has always parsed into [com.example.musicfy.lyrics.LyricsEntry.agent]. Until
 * now only the three unused renderers (MetroLyrics, MusicfyLyrics, LyricsV2) did anything with it,
 * so in the live player every voice rendered flush left and duets were impossible to follow.
 */
enum class LyricsAlignment { START, CENTER, END }

/**
 * Side room for a lyric line.
 *
 * Sized for the *zoomed* line, not the resting one: the active line scales to 1.06 in a
 * graphicsLayer, which happens after layout, so a line that already fills the width grows past
 * the screen edges. Long wrapped lines - CJK especially - hit this on every active line.
 */
private val LyricsLineHorizontalPadding = 44.dp

/**
 * Inset of the tap highlight around the text. The line's outer padding gives this back, so the
 * text itself still starts exactly at [LyricsHeaderArtX] - level with the artwork's left edge.
 */
private val LyricsTapPadH = 8.dp
private val LyricsTapPadV = 4.dp
private val LyricsTapShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

/**
 * How far a line-synced (no word timing) line sits below its place while it waits; it rises into
 * place as it becomes the active line. Word-synced lines do the same letter by letter, see
 * [rememberWordMotionModifier].
 */
private val LyricsWaitingDrop = 3.dp

private val LyricsFontSize = 32.sp
private val LyricsLineHeight = 40.sp

private val BlurStageRadii = floatArrayOf(0f, 5f, 12f)

private const val BlurQuantStepPx = 0.5f
private val BlurEffectCache: Array<androidx.compose.ui.graphics.RenderEffect?> =
    arrayOfNulls((BlurStageRadii.max() / BlurQuantStepPx).toInt() + 2)

private fun blurEffectForRadius(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.05f) return null
    val step = (radius / BlurQuantStepPx).roundToInt().coerceIn(1, BlurEffectCache.lastIndex)
    BlurEffectCache[step]?.let { return it }
    val quantised = step * BlurQuantStepPx
    // DECAL, not CLAMP: CLAMP repeats the layer's edge pixels outward, and wherever a glyph sat
    // on the edge (the bar of a "T") that drew a hard streak off the side of the line. DECAL fades
    // to clear past the edge instead. Same API level as the blur itself, so this stays inside the
    // version check above.
    return android.graphics.RenderEffect
        .createBlurEffect(quantised, quantised, android.graphics.Shader.TileMode.DECAL)
        .asComposeRenderEffect()
        .also { BlurEffectCache[step] = it }
}

private const val UpcomingAlpha = 0.5f
private const val PastAlpha = 0.24f
private const val DefaultAlpha = 0.38f

private const val ActiveUnsungAlpha = 0.58f

@Composable
fun LyricsGlowLine(
    entry: LyricsEntry,
    state: LyricsLineState,
    blurStage: Int,
    positionProvider: () -> Long,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLine: String? = null,

    waveEnabled: Boolean = true,

    highBloom: Boolean = true,

    suppressEffects: Boolean = false,

    /** Which voice this line belongs to. See [LyricsAlignment]. */
    alignment: LyricsAlignment = LyricsAlignment.START,

    /** Per-word readings drawn against the line, furigana style. */
    ruby: List<com.example.musicfy.lyrics.RubyToken>? = null,

    /**
     * Which side the readings sit on. Below by default; the caller moves them above when a
     * translation is showing, so the reading and the translation don't stack under the line and
     * push it out of the way.
     */
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,

    /** Translated text, shown under the line when the translate toggle is on. */
    translation: String? = null,

    /**
     * A translation has been asked for but hasn't arrived. Shows three pulsing dots in the slot the
     * translation will occupy, so the wait is visible instead of the screen simply not changing.
     */
    translationLoading: Boolean = false,

    /** Style 1 (words rise, held notes swell) or Style 2 (the original wave). */
    motionStyle: com.example.musicfy.constants.LyricsMotionStyle =
        com.example.musicfy.constants.LyricsMotionStyle.MOTION,
) {
    val wordMotion = motionStyle == com.example.musicfy.constants.LyricsMotionStyle.MOTION
    val targetAlpha = when (state) {
        LyricsLineState.ACTIVE -> 1f
        LyricsLineState.UPCOMING -> UpcomingAlpha
        LyricsLineState.PAST -> PastAlpha
        LyricsLineState.DEFAULT -> DefaultAlpha
    }
    val targetScale = when (state) {
        LyricsLineState.ACTIVE -> 1.06f
        LyricsLineState.UPCOMING -> 0.98f
        LyricsLineState.PAST -> 0.93f
        LyricsLineState.DEFAULT -> 0.95f
    }

    val animSpec = tween<Float>(durationMillis = 600)
    val alpha by animateFloatAsState(targetAlpha, animSpec, label = "lyricsLineAlpha")
    val scale by animateFloatAsState(targetScale, animSpec, label = "lyricsLineScale")

    // Only lines without word timing move as a whole; word-synced ones rise word by word.
    val wholeLineRise = wordMotion && entry.words.isNullOrEmpty()
    val drop by animateFloatAsState(
        targetValue = if (state == LyricsLineState.ACTIVE || !wholeLineRise) 0f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
        ),
        label = "lyricsLineDrop",
    )

    val blurRadius by animateFloatAsState(
        targetValue = BlurStageRadii[blurStage.coerceIn(0, BlurStageRadii.lastIndex)],
        animationSpec = tween(durationMillis = 350),
        label = "lyricsLineBlur",
    )

    val baseColor = LocalContentColor.current
    // Eased like the line's alpha and scale, so the line and the small text under it change
    // colour together instead of snapping while everything else is still fading.
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (state == LyricsLineState.ACTIVE) Color.White else baseColor,
        animationSpec = tween(durationMillis = 600),
        label = "lyricsLineColor",
    )

    val textAlign = when (alignment) {
        LyricsAlignment.START -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.END -> TextAlign.End
    }
    val columnAlign = when (alignment) {
        LyricsAlignment.START -> androidx.compose.ui.Alignment.Start
        LyricsAlignment.CENTER -> androidx.compose.ui.Alignment.CenterHorizontally
        LyricsAlignment.END -> androidx.compose.ui.Alignment.End
    }

    // The anchored side lines up with the artwork; the far side keeps room for the 1.06 zoom
    // (padding is applied before the scale below, so a line that fills the width would otherwise
    // grow past the edge). Both give back the tap highlight's own inset.
    val anchoredInset = LyricsHeaderArtX - LyricsTapPadH
    val zoomInset = LyricsLineHorizontalPadding - LyricsTapPadH
    val (startInset, endInset) = when (alignment) {
        LyricsAlignment.START -> anchoredInset to zoomInset
        LyricsAlignment.CENTER -> zoomInset to zoomInset
        LyricsAlignment.END -> zoomInset to anchoredInset
    }

    Column(
        horizontalAlignment = columnAlign,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = startInset,
                end = endInset,
                top = 16.dp - LyricsTapPadV,
                bottom = 16.dp - LyricsTapPadV,
            )
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = drop * LyricsWaitingDrop.toPx()
                // The active line grows from its own anchored edge. Pinning a right-aligned line
                // to origin 0 would make it swing left as it scaled up, away from the side it is
                // aligned to.
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = when (alignment) {
                        LyricsAlignment.START -> 0f
                        LyricsAlignment.CENTER -> 0.5f
                        LyricsAlignment.END -> 1f
                    },
                    pivotFractionY = 0.5f,
                )

                renderEffect = if (suppressEffects) null else blurEffectForRadius(blurRadius)
            }
    ) {
    // The tap target and its highlight hug the text (rounded, as wide as the words) instead of
    // flashing a full-width bar from screen edge to screen edge.
    //
    // It sits BEHIND the text rather than around it. Clipping the line to the rounded highlight
    // also clipped the words: a letter that rose or swelled into a corner was sliced off.
    Box {
    Spacer(
        modifier = Modifier
            .matchParentSize()
            .clip(LyricsTapShape)
            .clickable(onClick = onClick),
    )
    Column(
        horizontalAlignment = columnAlign,
        modifier = Modifier.padding(horizontal = LyricsTapPadH, vertical = LyricsTapPadV),
    ) {

        val parts = remember(entry.text, entry.words) {
            splitBackingVocal(entry.text, entry.words)
        }
        val leadText = parts.lead
        val words = parts.leadWords

        val waveAmplitude by animateFloatAsState(
            targetValue = if (state == LyricsLineState.ACTIVE) 1f else 0f,
            animationSpec = animSpec,
            label = "lyricsWaveAmplitude",
        )

        val sweepActive = !words.isNullOrEmpty() &&
            (state == LyricsLineState.ACTIVE || waveAmplitude > 0.01f)
        val karaokeActive = sweepActive && leadText.isNotEmpty()
        if (karaokeActive) {
            KaraokeSweepText(
                text = leadText,
                words = words,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                // The rise is drawn, not shaded, so it runs with the shader setting off too.
                riseAmplitudeProvider = { waveAmplitude },
                motionStyle = motionStyle,
                baseColor = textColor.copy(alpha = ActiveUnsungAlpha),

                highlightColor = Color.White,
                // The reading is drawn below, in one place for both paths. It used to move into
                // the karaoke block when the line went active (above any backing vocal, and at a
                // dimmer colour) and back out when it ended, so it jumped every line change.
                subLine = null,
                highBloom = highBloom,
                textAlign = textAlign,
                ruby = ruby,
                rubyPlacement = rubyPlacement,
                rubyColor = textColor.copy(alpha = 0.4f),
            )
        } else if (leadText.isNotEmpty()) {
            // Inactive lines carry their readings too, so the ruby doesn't pop in and out as the
            // active line moves. Needs its own layout capture — the karaoke path's holder only
            // exists while the sweep is running.
            val plainLayout = remember(leadText) { arrayOfNulls<TextLayoutResult>(1) }
            val hasRuby = !ruby.isNullOrEmpty()
            Text(
                text = leadText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = LyricsFontSize,
                    lineHeight = if (hasRuby) {
                        (LyricsLineHeight.value + RubyHeadroomSp).sp
                    } else {
                        LyricsLineHeight
                    },
                    lineHeightStyle = if (hasRuby) rubyLineHeightStyle(rubyPlacement) else null,
                ).withLetterMotionFeatures(wordMotion),
                fontWeight = if (state == LyricsLineState.ACTIVE) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                textAlign = textAlign,
                // No maxLines cap. A capped line ellipsised into "..." and simply lost the rest of
                // the words — a long line is meant to wrap, not to be truncated, and there is
                // nowhere else the reader can go to see what was cut.
                overflow = TextOverflow.Clip,
                onTextLayout = { plainLayout[0] = it },
                // No karaoke head on an inactive line, so nothing is lit — the readings stay
                // uniformly dim, matching the words above them.
                modifier = rememberRubyOverlay(
                    layoutHolder = plainLayout,
                    ruby = ruby,
                    textLength = leadText.length,
                    placement = rubyPlacement,
                    dimColor = textColor.copy(alpha = 0.4f),
                    litColor = textColor.copy(alpha = 0.4f),
                    headProvider = { 0f },
                ),
            )
        }

        FadingSubText(
            text = subLine,
            color = textColor.copy(alpha = 0.6f),
            textAlign = textAlign,
        )

        parts.backing?.let { backing ->
            BackingVocalLine(
                text = backing,
                color = textColor,

                slide = leadText.isNotEmpty(),
                words = parts.backingWords,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                sweep = sweepActive,

                expandProvider = { waveAmplitude },
                highBloom = highBloom,
                textAlign = textAlign,
                motionStyle = motionStyle,
                riseAmplitudeProvider = { waveAmplitude },
            )
        }

        // The translation sits below both the line and its romanisation. It is written by
        // LyricsTranslationHelper, which until now had no reader in the live player at all — the
        // menu toggle wrote into flows nothing was collecting.
        if (translationLoading && translation.isNullOrBlank()) {
            TranslationLoadingDots(
                color = textColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 7.dp),
            )
        } else {
            FadingSubText(
                text = translation,
                color = textColor.copy(alpha = 0.5f),
                textAlign = textAlign,
            )
        }
    }
    }
    }
}

/**
 * A small line under the lyric (romanisation or translation) that fades and unfolds in when its
 * text arrives and folds away when it is cleared, instead of popping in and shoving the rows below.
 */
@Composable
private fun FadingSubText(text: String?, color: Color, textAlign: TextAlign) {
    // Keep the last text while folding away, or the exit would animate an empty line.
    val shown = remember { arrayOfNulls<String>(1) }
    if (!text.isNullOrBlank()) shown[0] = text
    androidx.compose.animation.AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = androidx.compose.animation.fadeIn(tween(SubTextFadeMs)) +
            androidx.compose.animation.expandVertically(tween(SubTextFadeMs)),
        exit = androidx.compose.animation.fadeOut(tween(SubTextFadeMs)) +
            androidx.compose.animation.shrinkVertically(tween(SubTextFadeMs)),
    ) {
        Text(
            text = shown[0].orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            textAlign = textAlign,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private const val SubTextFadeMs = 350

/** Three dots pulsing in sequence, sized to sit in the translation's place. */
@Composable
private fun TranslationLoadingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "translating")
    Row(modifier = modifier) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 620,
                        delayMillis = index * 140,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "translatingDot$index",
            )
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(5.dp)
                    .graphicsLayer { alpha = 0.25f + 0.75f * phase }
                    .background(color, CircleShape)
            )
        }
    }
}

private val RubyFontSize = 11.sp
private val RubyLineHeight = 13.sp

/**
 * Extra height added to every text line to make room for the reading.
 *
 * Applied through lineHeight rather than container padding so the space lands against EVERY visual
 * line, including ones created by wrapping — padding would only feed the first.
 */
private const val RubyHeadroomSp = 14f

/** Gap between the lyric's baseline and its reading. Small on purpose — they read as one unit. */
private const val RubyBaselineGapPx = 2f

/**
 * Line-height style that opens the gap on the correct side: pinning glyphs to the bottom of their
 * line box leaves the slack above them, and vice versa.
 */
private fun rubyLineHeightStyle(placement: RubyPlacement) =
    androidx.compose.ui.text.style.LineHeightStyle(
        alignment = when (placement) {
            RubyPlacement.ABOVE -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Bottom
            RubyPlacement.BELOW -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Top
        },
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
    )

/** Which side of the lyric its reading sits on. */
enum class RubyPlacement { ABOVE, BELOW }

/**
 * Draws [ruby] readings against the characters they belong to, using the layout the text already
 * produced. Nothing here changes the text's own layout, so the karaoke mask — which is built from
 * the same [layoutHolder] — stays exactly in step.
 *
 * @param headProvider character offset of the karaoke head, read every frame. Readings light up as
 *   the head passes them so the pronunciation flows with the line instead of sitting there static
 *   while the words underneath it sweep.
 */
@Composable
private fun rememberRubyOverlay(
    layoutHolder: Array<TextLayoutResult?>,
    ruby: List<com.example.musicfy.lyrics.RubyToken>?,
    textLength: Int,
    placement: RubyPlacement,
    dimColor: Color,
    litColor: Color,
    headProvider: () -> Float,
): Modifier {
    if (ruby.isNullOrEmpty()) return Modifier
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // Colour is deliberately NOT part of this style: baking the per-frame colour into the measure
    // call would miss the TextMeasurer cache on every frame and re-lay-out every reading. The
    // colour is applied at draw time instead, where it is free.
    val style = MaterialTheme.typography.bodySmall.copy(
        fontSize = RubyFontSize,
        lineHeight = RubyLineHeight,
    )

    return Modifier.drawWithContent {
        drawContent()
        val layout = layoutHolder[0] ?: return@drawWithContent
        val head = headProvider()

        for (token in ruby) {
            val start = token.start.coerceIn(0, textLength)
            val end = token.end.coerceIn(start, textLength)
            if (end <= start) continue

            val line = layout.getLineForOffset(start)
            val left = layout.getHorizontalPosition(start, true)
            // A token can straddle a wrap. Clamp it to the line its first character is on rather
            // than measuring to a coordinate that belongs to the line below.
            val right = if (layout.getLineForOffset(end - 1) != line) {
                layout.getLineRight(line)
            } else {
                layout.getHorizontalPosition(end, true)
            }
            if (right <= left) continue

            val measured = measurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(token.text),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )

            // Centre the reading on its word, then keep it inside the line box so a long reading
            // over a short word doesn't run off the edge.
            val centred = (left + right) / 2f - measured.size.width / 2f
            val x = centred.coerceIn(
                0f,
                (size.width - measured.size.width).coerceAtLeast(0f),
            )
            // Hang the reading off the BASELINE, not off the line box.
            //
            // The line box is deliberately taller than the text — that is where the headroom comes
            // from — so pinning to its bottom edge parked the reading at the far end of that empty
            // space, visibly detached from the word it belongs to. Sitting it just under the
            // baseline keeps it attached, and the clamp stops a tall glyph pushing it into the
            // next line.
            val y = when (placement) {
                RubyPlacement.ABOVE ->
                    layout.getLineTop(line).coerceAtMost(
                        layout.getLineBaseline(line) - measured.size.height - RubyBaselineGapPx
                    )
                RubyPlacement.BELOW ->
                    (layout.getLineBaseline(line) + RubyBaselineGapPx)
                        .coerceAtMost(layout.getLineBottom(line) - measured.size.height)
            }

            // Same fill the sweep uses, one word at a time: fully lit once the head is past the
            // token, partially lit while inside it.
            val lit = when {
                head <= start -> 0f
                head >= end -> 1f
                else -> (head - start) / (end - start).toFloat()
            }

            drawText(
                textLayoutResult = measured,
                color = lerpColor(dimColor, litColor, lit),
                topLeft = Offset(x, y),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    if (f <= 0f) return from
    if (f >= 1f) return to
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}

private const val EdgeSoftChars = 1

private const val EdgeSoftPx = 26f

private const val EdgeLeadPx = 6f

private const val WaveCharsBehind = 4f
private const val WaveCharsAhead = 3f

// Style 2 (legacy) wave: a lift-and-zoom bell that rides the head across the letters.
private const val LegacyWaveLiftPx = 4.5f
private const val LegacyWaveZoom = 0.09f

// Style 1 motion. The line is drawn a letter at a time, each letter clipped to its own slot and
// then moved - so a letter can rise without dragging a sliver of the line above or below with it
// (which the old pixel-shifting shader did: the faint line under a rising word).

/** How far an unsung letter waits below its place. It rises into place as it's sung. */
private val WordWaitDrop = 3.dp

/**
 * A held word (a long note) lifts this much more and swells by [HeldZoom], in step with its glow.
 * It swells letter by letter as the sweep reaches each one, and the word spreads to make room.
 */
private val HeldLift = 2.5.dp
private const val HeldZoom = 0.075f

/**
 * Critically damped follow times. A letter's rise tracks the sweep but never snaps (a one-letter
 * word would otherwise jump up in a frame); the swell breathes in and out slower.
 */
private const val RiseFollowSec = 0.08f
private const val SwellFollowSec = 0.2f

/** A word is fully risen by this fraction of the way through it, so it's up before it's over. */
private const val RiseCompleteAt = 0.85f

/**
 * How much of a letter's rise is its own rather than its word's.
 *
 * Words alone hopped up as blocks - a long word was all the way up while the sweep had only just
 * entered it, and a short one popped. Letters alone stepped up like a staircase. Mixed, the word
 * starts lifting as a whole the moment it's sung and the letters the sweep has reached lift ahead
 * of it, so the line rises as one wave running through each word.
 */
private const val LetterRiseShare = 0.55f

/**
 * A letter starts rising this many characters before the sweep reaches it, and is fully up once
 * the sweep is [LetterRiseSpanChars] further on. A wider span is a softer slope between letters.
 */
private const val LetterRiseLeadChars = 0.5f
private const val LetterRiseSpanChars = 2.5f

/** Same for the swell on a held word: each letter grows as the sweep arrives on it. */
private const val LetterSwellLeadChars = 0.35f
private const val LetterSwellSpanChars = 1.4f

/** The held-note swell comes in quickly and holds until the very end of the note. */
private const val SwellAttackFraction = 0.2f
private const val SwellReleaseFraction = 0.12f

/**
 * Room around the moving text, on every side, inside the layers that draw it.
 *
 * A RenderEffect (the bloom) and an offscreen layer (the sweep's highlight copy) only keep what
 * lies inside their own bounds, and those bounds were exactly the text's box. A letter that rose,
 * dropped or swelled past that box - the first word of a line growing left, descenders on the last
 * line dropping - was sliced off at the edge, and only while the bloom was on, so the cut flickered
 * in and out with held notes.
 */
private val MotionBleed = 10.dp

/** Transforms closer than this (px) are drawn as one piece. */
private const val MergeEpsilonPx = 0.1f

/**
 * Ligatures off for Style 1. A ligature is one glyph covering two letters, so moving those letters
 * separately would tear it in half. Both karaoke copies and the resting line use the same setting,
 * so the line keeps its width when it becomes active.
 */
private const val NoLigatures = "liga 0, clig 0"

private fun androidx.compose.ui.text.TextStyle.withLetterMotionFeatures(letterMotion: Boolean) =
    if (letterMotion) copy(fontFeatureSettings = NoLigatures) else this

/**
 * Lays out exactly like the content, but gives the layer that follows [bleed] of extra room on
 * every side. Use as `layerBleed(b).graphicsLayer { ... }.padding(b)`: the layer grows, the content
 * inside it lands where it would have been anyway, and nothing around it moves.
 */
private fun Modifier.layerBleed(bleed: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val px = bleed.roundToPx()
        val placeable = measurable.measure(constraints.offset(px * 2, px * 2))
        layout(
            (placeable.width - px * 2).coerceIn(constraints.minWidth, constraints.maxWidth),
            (placeable.height - px * 2).coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeable.place(-px, -px)
        }
    }

/**
 * Time weight of one space, relative to one letter, when a word runs straight into the next with
 * no gap. The sweep then crosses the space at the tail of the word instead of jumping it - the
 * jump was the abrupt cut at every word boundary.
 */
private const val SpaceTimeWeight = 0.4f

/** Below this the bloom is invisible, so the shader is skipped entirely (no offscreen pass). */
private const val BloomCutoff = 0.01f

/** Style 1 without AGSL (API < 33): a soft light behind a held word stands in for the bloom. */
private const val FakeGlowAlpha = 0.16f

private const val BloomRadiusPx = 2.6f

private const val BloomStrength = 1.6f

private const val BloomLagChars = 1.6f
private const val BloomSpanChars = 3.5f

/**
 * Style 1 shader: bloom only. Words are moved by drawing (see [rememberWordMotionModifier]), so
 * nothing here displaces pixels - it reads each pixel where it is and adds a soft glow trailing the
 * head, strongest on a held word. Runs only while there's glow to show.
 */
private const val BloomAgsl = """
uniform shader content;
uniform float2 layerSize;
uniform float headX;
uniform float lineTop;
uniform float lineBottom;
uniform float lineLeft;
uniform float prevTop;
uniform float prevBottom;
uniform float prevRight;
uniform float hasPrev;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float highQuality;

// Outside the content, eval() returns its edge pixels stretched out - the hard line that ran off
// the side of the active lyric. Samples outside are transparent instead.
half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    half4 c = sampleIn(coord);

    float onCur  = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);
    if (onLine <= 0.0) return c;

    // Distance from the head along the reading flow, wrapping onto the previous visual line.
    float dxCur  = coord.x - headX;
    float dxPrev = -((prevRight - coord.x) + (headX - lineLeft));
    float dx = onPrev > 0.5 ? dxPrev : dxCur;

    float bdx = dx + bloomLag;
    float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
    float bw = 1.0 - ba;
    float bbell = bw * bw * (3.0 - 2.0 * bw);
    if (bbell <= 0.0) return c;

    half4 b = sampleIn(coord + float2(bloomR, bloomR))
            + sampleIn(coord + float2(bloomR, -bloomR))
            + sampleIn(coord + float2(-bloomR, bloomR))
            + sampleIn(coord + float2(-bloomR, -bloomR));
    half weight = 0.25;
    if (highQuality > 0.5) {
        b += sampleIn(coord + float2(bloomR, 0.0))
           + sampleIn(coord - float2(bloomR, 0.0))
           + sampleIn(coord + float2(0.0, bloomR))
           + sampleIn(coord - float2(0.0, bloomR));
        weight = 0.125;
    }
    return c + b * (bloom * bbell * weight);
}
"""

private const val LegacyWaveAgsl = """
uniform shader content;
uniform float headX;
uniform float spanBehind;
uniform float spanAhead;
uniform float liftPx;
uniform float zoomAmt;
uniform float baseY;
uniform float lineTop;
uniform float lineBottom;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float highQuality;

// The visual line the sweep just left, and where it ended. Without these the effect was clamped
// to the head's own line, so the moment a long lyric wrapped, the trailing part of the glow -- the
// few characters behind the head that should still be lit on the line above -- was cut off
// mid-stride instead of easing out. See flowDx below.
uniform float prevTop;
uniform float prevBottom;
uniform float prevRight;
uniform float prevBaseY;
uniform float hasPrev;
uniform float lineLeft;

// Bounds-safe sampling (the only change from the original): outside the content, eval() returned
// the edge pixels stretched out, which drew a hard line off the side of the active lyric.
uniform float2 layerSize;

half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    float onCur  = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);

    // Distance from the head measured along the READING FLOW, not along screen x. On the head's
    // own line that is just the horizontal gap. On the previous line it continues around the
    // wrap: how far the pixel sits from that line's right edge, plus how far the head has already
    // travelled from this line's left edge. That makes the glow spill across the line break the
    // way it would if the text were one continuous strip.
    float dxCur  = coord.x - headX;
    float dxPrev = -((prevRight - coord.x) + (headX - lineLeft));
    float dx = onPrev > 0.5 ? dxPrev : dxCur;

    float bY = onPrev > 0.5 ? prevBaseY : baseY;

    float span = dx < 0.0 ? spanBehind : spanAhead;
    float a = min(1.0, abs(dx) / max(span, 1.0));
    float w = 1.0 - a;
    float bell = w * w * (3.0 - 2.0 * w) * onLine;

    // sampling from below the output pixel is what makes the letter appear to
    float2 p = coord;
    p.y += liftPx * bell;
    // stretch about the baseline so the glyph grows upward instead of drifting
    p.y = bY + (p.y - bY) / (1.0 + zoomAmt * bell);

    half4 c = sampleIn(p);

    if (bloom > 0.0 && onLine > 0.0) {
        // the bloom gets its own bell wider than the lift s and centred behind the
        // than on it sharing the lift s bell put the glow exactly where the sweep s
        // boundary is the one place the lit copy is half transparent and the dim
        // dim so it landed on the faintest pixels on screen and was invisible
        // Expressed as a lag from the head in flow space so it wraps with everything else.
        float bdx = dx + bloomLag;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;

        // four diagonal taps always the axis aligned four only on the high setting
        // alone still read as a halo they just make it very slightly less round
        half4 b = sampleIn(p + float2(bloomR, bloomR))
                + sampleIn(p + float2(bloomR, -bloomR))
                + sampleIn(p + float2(-bloomR, bloomR))
                + sampleIn(p + float2(-bloomR, -bloomR));
        half weight = 0.25;
        if (highQuality > 0.5) {
            b += sampleIn(p + float2(bloomR, 0.0))
               + sampleIn(p - float2(bloomR, 0.0))
               + sampleIn(p + float2(0.0, bloomR))
               + sampleIn(p - float2(0.0, bloomR));
            weight = 0.125;
        }
        c += b * (bloom * bbell * weight);
    }
    return c;
}
"""


/**
 * Where the sweep is, for a caller that has to follow it - the bottom card slides a long line
 * sideways to keep the sung part in view. Written from draw, so reading it costs nothing.
 */
internal class SweepProbe {
    /** The sweep's x in the text's own coordinates, 0 before it starts. */
    var headX = 0f
    var textWidth = 0f
}

@Composable
internal fun KaraokeSweepText(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
    waveAmplitudeProvider: () -> Float,
    baseColor: Color,
    highlightColor: Color,
    subLine: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = LyricsFontSize,
    lineHeight: androidx.compose.ui.unit.TextUnit = LyricsLineHeight,
    highBloom: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    ruby: List<com.example.musicfy.lyrics.RubyToken>? = null,
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,
    rubyColor: Color = Color.White.copy(alpha = 0.4f),
    motionStyle: com.example.musicfy.constants.LyricsMotionStyle =
        com.example.musicfy.constants.LyricsMotionStyle.MOTION,
    /**
     * Style 1 word rise, 0..1 with the line becoming active. Separate from [waveAmplitudeProvider]
     * on purpose: that one is zero when the lyrics shader is switched off, and the rise is drawn
     * without the shader, so it keeps working.
     */
    riseAmplitudeProvider: () -> Float = waveAmplitudeProvider,
    /** One line that never wraps: the bottom card scrolls it sideways instead. */
    singleLine: Boolean = false,
    /** Letter rise and swell distances, relative to the full-size line. */
    motionScale: Float = 1f,
    probe: SweepProbe? = null,
) {
    val wordMotion = motionStyle == com.example.musicfy.constants.LyricsMotionStyle.MOTION
    val head = rememberKaraokeHead(text, words, positionProvider, wordMotion)

    val hasRuby = !ruby.isNullOrEmpty()
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = if (hasRuby) (lineHeight.value + RubyHeadroomSp).sp else lineHeight,
        lineHeightStyle = if (hasRuby) rubyLineHeightStyle(rubyPlacement) else null,
    ).withLetterMotionFeatures(wordMotion)

    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }

    val sung = remember(text) { Path() }

    val effect: Modifier
    val motion: Modifier
    if (wordMotion) {
        effect = rememberBloomModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)
        // Letters move by drawing, inside the bloom layer so the glow sees them where they are.
        // Without AGSL there's no bloom pass, so a soft light behind a held word stands in for it
        // (only while the lyrics shader setting is on - off means no glow at all).
        val fakeGlow = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        motion = rememberWordMotionModifier(
            layoutHolder = layoutHolder,
            head = head,
            riseAmplitudeProvider = riseAmplitudeProvider,
            fakeGlowProvider = { if (fakeGlow) waveAmplitudeProvider() else 0f },
            motionScale = motionScale,
        )
    } else {
        effect = rememberLegacyWaveModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)
        motion = Modifier
    }
    // Applied outside the wave shader so the readings stay still while the sung word lifts — a
    // reading that rides the wave with its word reads as jitter at this size.
    val rubyOverlay = rememberRubyOverlay(
        layoutHolder = layoutHolder,
        ruby = ruby,
        textLength = text.length,
        placement = rubyPlacement,
        dimColor = rubyColor,
        litColor = Color.White,
        headProvider = { head.offset.floatValue },
    )

    Column(modifier = rubyOverlay) {
        // The effect layer is [MotionBleed] bigger than the text on every side (see there), so a
        // letter that moves past the text's box is still inside the layer. The letters are drawn
        // after the padding, in the text's own coordinates.
        Box(
            modifier = Modifier
                .layerBleed(MotionBleed)
                .then(effect)
                .padding(MotionBleed)
                .then(motion)
        ) {

            // Both copies must lay out identically or the sweep mask, which is built from the
            // first copy's TextLayoutResult and clipped over the second, lands in the wrong place.
            // That includes the line cap: capping at 3 lines also ellipsised long lines into
            // "..." and dropped the rest of the words outright.
            Text(
                text = text,
                style = style,
                color = baseColor,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                overflow = TextOverflow.Clip,
                softWrap = !singleLine,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                onTextLayout = {
                    layoutHolder[0] = it
                    probe?.textWidth = it.size.width.toFloat()
                },
            )

            Text(
                text = text,
                style = style,
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                overflow = TextOverflow.Clip,
                softWrap = !singleLine,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                modifier = Modifier
                    // Room around the text, as for the effect layer: an offscreen layer keeps only
                    // what's inside it, and glyph ink that reaches past the line's ends (a "y"
                    // tail, an "f" hook) was lit on the dim copy but cut on the lit one.
                    .layerBleed(MotionBleed)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .padding(MotionBleed)
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f) return@drawWithContent
                        val len = text.length
                        if (len == 0) return@drawWithContent
                        val reach = MotionBleed.toPx()
                        val lastLine = layout.lineCount - 1
                        // The sung area reaches into the room around the text on the outside edges
                        // only - between lines it stops exactly at the boundary, so it never lights
                        // the top of the line below.
                        fun topOf(line: Int) = layout.getLineTop(line) - if (line == 0) reach else 0f
                        fun bottomOf(line: Int) =
                            layout.getLineBottom(line) + if (line == lastLine) reach else 0f

                        val headOffset = h.toInt().coerceIn(0, len)

                        val headX = if (headOffset >= len) {
                            layout.getLineRight(layout.getLineForOffset(len - 1))
                        } else {
                            val x0 = layout.getHorizontalPosition(headOffset, true)
                            val x1 = layout.getHorizontalPosition(
                                (headOffset + 1).coerceAtMost(len),
                                true,
                            )

                            if (x1 >= x0) x0 + (x1 - x0) * (h - headOffset) else x0
                        }
                        probe?.headX = headX
                        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(len - 1))
                        val lineTop = topOf(headLine)
                        val lineBottom = bottomOf(headLine)

                        val softFrom = layout.getHorizontalPosition(
                            (headOffset - EdgeSoftChars).coerceAtLeast(layout.getLineStart(headLine)),
                            true,
                        )
                        val fromX = if (softFrom < headX) softFrom else headX - EdgeSoftPx
                        val toX = headX + EdgeLeadPx

                        sung.rewind()
                        for (line in 0..headLine) {
                            val left = layout.getLineLeft(line) - reach
                            val lineRight = layout.getLineRight(line) + reach
                            val right = if (line < headLine || headOffset >= len) {
                                lineRight
                            } else {
                                minOf(toX, lineRight)
                            }
                            if (right > left) {
                                sung.addRect(Rect(left, topOf(line), right, bottomOf(line)))
                            }
                        }

                        clipPath(sung) { this@drawWithContent.drawContent() }

                        if (headOffset < len) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startX = fromX,
                                    endX = toX,
                                ),
                                topLeft = Offset(fromX, lineTop),
                                size = Size(toX - fromX, lineBottom - lineTop),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            )
        }

        if (!subLine.isNullOrBlank()) {
            Text(
                text = subLine,
                style = MaterialTheme.typography.bodyLarge,
                color = baseColor.copy(alpha = 0.6f),
                textAlign = textAlign,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

internal class LyricLineParts(
    val lead: String,
    val backing: String?,
    val leadWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
    val backingWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
)

private fun partitionWords(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>?,
    inBracket: BooleanArray,
): Pair<List<com.example.musicfy.lyrics.WordTimestamp>, List<com.example.musicfy.lyrics.WordTimestamp>> {
    if (words.isNullOrEmpty()) return emptyList<com.example.musicfy.lyrics.WordTimestamp>() to emptyList()
    val lead = ArrayList<com.example.musicfy.lyrics.WordTimestamp>(words.size)
    val backing = ArrayList<com.example.musicfy.lyrics.WordTimestamp>()
    var searchFrom = 0
    for (word in words) {
        val trimmed = word.text.trim()
        if (trimmed.isEmpty()) continue
        val start = text.indexOf(trimmed, searchFrom)
        if (start < 0) continue
        searchFrom = start + trimmed.length

        if (inBracket.getOrElse(start) { false }) backing.add(word) else lead.add(word)
    }
    return lead to backing
}

internal fun splitBackingVocal(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>?,
): LyricLineParts {
    val untouched = LyricLineParts(text, null, words, null)
    if (text.indexOf('(') < 0 && text.indexOf('[') < 0) return untouched

    val lead = StringBuilder(text.length)
    val backing = StringBuilder()
    val inBracket = BooleanArray(text.length)
    var depth = 0
    text.forEachIndexed { i, ch ->
        when {
            ch == '(' || ch == '[' -> {
                depth++
                inBracket[i] = true
            }
            (ch == ')' || ch == ']') && depth > 0 -> {
                depth--
                inBracket[i] = true
                if (depth == 0) backing.append(' ')
            }
            depth > 0 -> {
                inBracket[i] = true
                backing.append(ch)
            }
            else -> lead.append(ch)
        }
    }

    if (depth != 0) return untouched

    val leadText = lead.toString().replace(WhitespaceRun, " ").trim()
    val backingText = backing.toString().replace(WhitespaceRun, " ").trim()
    if (backingText.isEmpty()) return untouched

    val (leadWords, backingWords) = partitionWords(text, words, inBracket)
    return LyricLineParts(
        lead = leadText,
        backing = backingText,
        leadWords = leadWords.withoutBrackets().ifEmpty { null },
        backingWords = backingWords.withoutBrackets().ifEmpty { null },
    )
}

/**
 * The timed words with their brackets removed, to match the split texts, which have none.
 *
 * Timed words keep the brackets of the original line ("(ooh", "yeah)"). The sweep finds each word
 * by searching the text it draws, and "(ooh" is not in "ooh yeah", so the first and last backing
 * words were never found: the sweep sat still until the middle word and stopped before the end.
 */
private fun List<com.example.musicfy.lyrics.WordTimestamp>.withoutBrackets() =
    mapNotNull { word ->
        val bare = word.text.filterNot { it == '(' || it == ')' || it == '[' || it == ']' }
        when {
            bare == word.text -> word
            bare.isBlank() -> null
            else -> word.copy(text = bare)
        }
    }

private val WhitespaceRun = Regex("\\s{2,}")

@Composable
private fun BackingVocalLine(
    text: String,
    color: Color,
    slide: Boolean,
    words: List<com.example.musicfy.lyrics.WordTimestamp>?,
    positionProvider: () -> Long,
    waveAmplitudeProvider: () -> Float,
    sweep: Boolean,
    expandProvider: () -> Float,
    highBloom: Boolean,
    textAlign: TextAlign = TextAlign.Start,
    motionStyle: com.example.musicfy.constants.LyricsMotionStyle =
        com.example.musicfy.constants.LyricsMotionStyle.MOTION,
    riseAmplitudeProvider: () -> Float = waveAmplitudeProvider,
) {

    val expand = rememberBackingExpand(words, positionProvider, expandProvider)
    val wordMotion = motionStyle == com.example.musicfy.constants.LyricsMotionStyle.MOTION

    // Keep the sweep for as long as the backing line is on screen. It used to drop back to the
    // plain copy the moment the main line finished, while a backing vocal that trails the line
    // was still being sung, so the sweep stopped halfway and the text flashed to full brightness.
    val visible by remember(expand) { derivedStateOf { expand.floatValue > 0.01f } }
    val useSweep = !words.isNullOrEmpty() && (sweep || visible)

    Box(
        modifier = Modifier
            // Clipped only while it opens, and only on the edge it opens from. A permanent clip to
            // its bounds cut the backing words' descenders off as they dropped, and their tops as
            // they swelled, with no room at all around the text.
            .drawWithContent {
                val e = expand.floatValue
                if (e >= 1f) {
                    drawContent()
                } else {
                    val room = size.width + size.height
                    clipRect(
                        left = -room,
                        top = if (slide) 0f else -room,
                        right = size.width + room,
                        bottom = if (slide) size.height + room else size.height,
                    ) { this@drawWithContent.drawContent() }
                }
            }
            // Hidden until it is sung and folded away after: it opens to its full height while
            // its words are being sung and closes again once they are done.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val e = expand.floatValue.coerceIn(0f, 1f)
                val height = (placeable.height * e).roundToInt()
                layout(placeable.width, height) {

                    placeable.place(0, if (slide) height - placeable.height else 0)
                }
            }
            .graphicsLayer { alpha = expand.floatValue.coerceIn(0f, 1f) },
    ) {

        if (useSweep) {
            KaraokeSweepText(
                text = text,
                words = words!!,
                positionProvider = positionProvider,
                waveAmplitudeProvider = waveAmplitudeProvider,
                baseColor = color.copy(alpha = ActiveUnsungAlpha),
                highlightColor = Color.White,
                fontSize = BackingVocalFontSize,
                lineHeight = BackingVocalLineHeight,
                highBloom = highBloom,
                textAlign = textAlign,
                motionStyle = motionStyle,
                riseAmplitudeProvider = riseAmplitudeProvider,
            )
        } else {
            Text(
                text = text,
                // Same font features as the karaoke copy, so the line keeps its width (and its
                // wrapping) when it switches between the two.
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = BackingVocalFontSize,
                    lineHeight = BackingVocalLineHeight,
                ).withLetterMotionFeatures(wordMotion),
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = textAlign,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private const val BackingLeadInSec = 0.35
private const val BackingTailSec = 0.6

@Composable
private fun rememberBackingExpand(
    words: List<com.example.musicfy.lyrics.WordTimestamp>?,
    positionProvider: () -> Long,
    lineAmplitudeProvider: () -> Float,
): androidx.compose.runtime.MutableFloatState {
    val state = remember(words) { mutableFloatStateOf(0f) }
    val firstStart = remember(words) { words?.firstOrNull()?.startTime }
    val lastEnd = remember(words) { words?.lastOrNull()?.endTime }

    LaunchedEffect(words, positionProvider) {
        while (true) {
            withFrameMillis {
                val sec = positionProvider() / 1000.0
                val own = if (firstStart == null || lastEnd == null) {
                    0f
                } else {
                    when {
                        sec >= firstStart && sec <= lastEnd -> 1f
                        sec < firstStart -> smoothstep(
                            ((sec - (firstStart - BackingLeadInSec)) / BackingLeadInSec).toFloat()
                        )
                        else -> 1f - smoothstep(((sec - lastEnd) / BackingTailSec).toFloat())
                    }
                }
                // Timed backing vocals follow their own words: they open just before they are sung
                // and fold away once done. Taking the larger of this and the main line's state kept
                // them open for the whole line and they never hid. Untimed ones still follow the line.
                val next = if (firstStart == null || lastEnd == null) {
                    lineAmplitudeProvider().coerceIn(0f, 1f)
                } else {
                    own
                }
                if (next != state.floatValue) state.floatValue = next
            }
        }
    }
    return state
}

private val BackingVocalFontSize = 21.sp
private val BackingVocalLineHeight = 26.sp

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Style 1: the bloom pass. Skipped (no render effect at all) whenever nothing is glowing. */
@Composable
private fun rememberBloomModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,
    amplitudeProvider: () -> Float,
    highBloom: Boolean,
): Modifier {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createAgslShader(BloomAgsl)
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        val glow = head.glow.floatValue
        val bloom = BloomStrength * glow * amp
        if (layout == null || textLength == 0 || bloom <= BloomCutoff) {
            renderEffect = null
            return@graphicsLayer
        }

        val h = head.offset.floatValue
        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(textLength - 1))
        val headX = if (headOffset >= textLength) {
            layout.getLineRight(headLine)
        } else {
            val x0 = layout.getHorizontalPosition(headOffset, true)
            val x1 = layout.getHorizontalPosition((headOffset + 1).coerceAtMost(textLength), true)
            if (x1 >= x0) x0 + (x1 - x0) * (h - headOffset) else x0
        }
        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        val charWidth = if (lineEnd > lineStart) {
            (layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart)
        } else {
            LyricsFontSize.toPx() * 0.5f
        }

        // The layer is [MotionBleed] bigger than the text on every side; the text starts at o.
        val o = MotionBleed.roundToPx().toFloat()
        shader.setAgslUniform("layerSize", size.width, size.height)
        shader.setAgslUniform("headX", headX + o)
        shader.setAgslUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setAgslUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setAgslUniform("lineLeft", layout.getLineLeft(headLine) + o)
        shader.setAgslUniform("bloom", bloom)
        shader.setAgslUniform("bloomR", BloomRadiusPx)
        shader.setAgslUniform("bloomLag", charWidth * BloomLagChars)
        shader.setAgslUniform("bloomSpan", charWidth * BloomSpanChars)
        shader.setAgslUniform("highQuality", if (highBloom) 1f else 0f)
        if (headLine > 0) {
            shader.setAgslUniform("hasPrev", 1f)
            shader.setAgslUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setAgslUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setAgslUniform("prevRight", layout.getLineRight(headLine - 1) + o)
        } else {
            shader.setAgslUniform("hasPrev", 0f)
            shader.setAgslUniform("prevTop", 0f)
            shader.setAgslUniform("prevBottom", 0f)
            shader.setAgslUniform("prevRight", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, glow, headLine, size.width, size.height)
    }
}

/** Style 2: the original wave - a lift-and-zoom bell that follows the head across the letters. */
@Composable
private fun rememberLegacyWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,

    amplitudeProvider: () -> Float,

    highBloom: Boolean,
): Modifier {

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createAgslShader(LegacyWaveAgsl)
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)

        if (layout == null || textLength == 0 || h <= 0f || amp <= WaveCutoff) {

            renderEffect = null
            return@graphicsLayer
        }

        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(textLength - 1))
        val headX = if (headOffset >= textLength) {
            layout.getLineRight(headLine)
        } else {
            val x0 = layout.getHorizontalPosition(headOffset, true)
            val x1 = layout.getHorizontalPosition((headOffset + 1).coerceAtMost(textLength), true)
            if (x1 >= x0) x0 + (x1 - x0) * (h - headOffset) else x0
        }

        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        val charWidth = if (lineEnd > lineStart) {
            (layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart)
        } else {
            LyricsFontSize.toPx() * 0.5f
        }

        // The layer is [MotionBleed] bigger than the text on every side; the text starts at o.
        // Only the coordinates move - the shader and its look are untouched.
        val o = MotionBleed.roundToPx().toFloat()
        shader.setAgslUniform("layerSize", size.width, size.height)
        shader.setAgslUniform("headX", headX + o)
        shader.setAgslUniform("spanBehind", charWidth * WaveCharsBehind)
        shader.setAgslUniform("spanAhead", charWidth * WaveCharsAhead)
        shader.setAgslUniform("liftPx", LegacyWaveLiftPx * amp)
        shader.setAgslUniform("zoomAmt", LegacyWaveZoom * amp)
        shader.setAgslUniform("baseY", layout.getLineBaseline(headLine) + o)
        shader.setAgslUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setAgslUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setAgslUniform("bloom", BloomStrength * head.glow.floatValue * amp)
        shader.setAgslUniform("bloomR", BloomRadiusPx)
        shader.setAgslUniform("bloomLag", charWidth * BloomLagChars)
        shader.setAgslUniform("bloomSpan", charWidth * BloomSpanChars)
        shader.setAgslUniform("highQuality", if (highBloom) 1f else 0f)
        shader.setAgslUniform("lineLeft", layout.getLineLeft(headLine) + o)

        // Let the trailing glow continue onto the line the sweep just wrapped off, instead of
        // being clipped at the line break.
        if (headLine > 0) {
            shader.setAgslUniform("hasPrev", 1f)
            shader.setAgslUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setAgslUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setAgslUniform("prevRight", layout.getLineRight(headLine - 1) + o)
            shader.setAgslUniform("prevBaseY", layout.getLineBaseline(headLine - 1) + o)
        } else {
            shader.setAgslUniform("hasPrev", 0f)
            shader.setAgslUniform("prevTop", 0f)
            shader.setAgslUniform("prevBottom", 0f)
            shader.setAgslUniform("prevRight", 0f)
            shader.setAgslUniform("prevBaseY", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue, headLine, size.width, size.height)
    }
}

private const val WaveCutoff = 0.02f

private const val WaveHeadStepPx = 0.5f
private const val WaveAmpStep = 0.02f

private class WaveEffectCache {
    private var lastHead = Int.MIN_VALUE
    private var lastAmp = Int.MIN_VALUE
    private var lastGlow = Int.MIN_VALUE
    private var lastLine = Int.MIN_VALUE
    private var lastWidth = Int.MIN_VALUE
    private var lastHeight = Int.MIN_VALUE
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(
        shader: Any,
        headX: Float,
        amplitude: Float,
        glow: Float,
        // RenderEffects are immutable snapshots of the uniforms, so the line index has to be part
        // of the key: on a wrap headX can land on the same quantised bucket it held on the line
        // above, and the stale effect would keep painting the old line's band.
        headLine: Int,
        width: Float,
        height: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val h = (headX / WaveHeadStepPx).roundToInt()
        val a = (amplitude / WaveAmpStep).roundToInt()
        val g = (glow / WaveAmpStep).roundToInt()
        val w = width.roundToInt()
        val ht = height.roundToInt()
        val cached = effect
        if (cached != null && h == lastHead && a == lastAmp && g == lastGlow && headLine == lastLine &&
            w == lastWidth && ht == lastHeight
        ) return cached
        lastWidth = w
        lastHeight = ht
        lastHead = h
        lastAmp = a
        lastGlow = g
        lastLine = headLine
        return agslRenderEffect(shader, "content")
            .asComposeRenderEffect()
            .also { effect = it }
    }
}

@androidx.compose.runtime.Stable
internal class KaraokeHead(
    /** Visual words of the line - [wordStarts] inclusive, [wordEnds] exclusive, as text offsets. */
    val wordStarts: IntArray = IntArray(0),
    val wordEnds: IntArray = IntArray(0),
    /** The letters each word is moved as - see [motionCells]. */
    val cells: MotionCells = MotionCells.Empty,
) {

    val offset = mutableFloatStateOf(0f)

    val glow = mutableFloatStateOf(0f)

    val wordCount: Int get() = wordStarts.size

    val cellCount: Int get() = cells.starts.size

    /** Per letter cell, Style 1: 0 = waiting below its place, 1 = risen into place. */
    val rise = FloatArray(cells.starts.size)
    val riseVelocity = FloatArray(cells.starts.size)

    /** Per letter cell, Style 1: how swollen it is with a held note (0..1). */
    val swell = FloatArray(cells.starts.size)
    val swellVelocity = FloatArray(cells.starts.size)

    /** Bumped whenever [rise]/[swell] change, so the draw that reads them is invalidated. */
    val motionTick = androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * The pieces a line is moved in: letters (grapheme clusters, so an accented letter or an emoji stays
 * whole), or whole words where letters can't be pulled apart cleanly.
 *
 * @property word the visual word each cell belongs to.
 * @property tiled the cells can tile each line edge to edge - plain left-to-right text. Right-to-left
 *   text keeps the old per-word glyph boxes instead.
 */
internal class MotionCells(
    val starts: IntArray,
    val ends: IntArray,
    val word: IntArray,
    val tiled: Boolean,
) {
    companion object {
        val Empty = MotionCells(IntArray(0), IntArray(0), IntArray(0), tiled = false)
    }
}

internal fun motionCells(text: String, wordStarts: IntArray, wordEnds: IntArray): MotionCells {
    val ltr = text.none {
        val dir = Character.getDirectionality(it)
        dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }
    val graphemes = if (ltr) {
        java.text.BreakIterator.getCharacterInstance().also { it.setText(text) }
    } else {
        null
    }
    val starts = ArrayList<Int>(text.length)
    val ends = ArrayList<Int>(text.length)
    val owner = ArrayList<Int>(text.length)
    for (w in wordStarts.indices) {
        val ws = wordStarts[w]
        val we = wordEnds[w]
        if (graphemes == null || !splitsIntoLetters(text, ws, we)) {
            starts += ws
            ends += we
            owner += w
            continue
        }
        var s = ws
        while (s < we) {
            var e = graphemes.following(s)
            if (e == java.text.BreakIterator.DONE || e > we) e = we
            starts += s
            ends += e
            owner += w
            s = e
        }
    }
    return MotionCells(starts.toIntArray(), ends.toIntArray(), owner.toIntArray(), tiled = ltr)
}

/**
 * Scripts whose letters are separate shapes. Joined and shaped scripts (Arabic, Devanagari, Thai...)
 * would be torn apart mid-stroke by moving their letters separately, so their words move whole.
 */
private fun splitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARMENIAN,
            Character.UnicodeScript.GEORGIAN,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return true
}

/**
 * The words a line is drawn as, for per-word motion: runs of non-space characters, except that
 * Chinese and Japanese characters each count as their own word (there are no spaces to split on,
 * and they are sung one at a time).
 */
internal fun visualWords(text: String): Pair<IntArray, IntArray> {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val width = Character.charCount(cp)
        when {
            Character.isWhitespace(cp) -> i += width
            isSingleGlyphWord(cp) -> {
                starts += i
                ends += i + width
                i += width
            }
            else -> {
                val start = i
                while (i < text.length) {
                    val c = text.codePointAt(i)
                    if (Character.isWhitespace(c) || isSingleGlyphWord(c)) break
                    i += Character.charCount(c)
                }
                starts += start
                ends += i
            }
        }
    }
    return starts.toIntArray() to ends.toIntArray()
}

private fun isSingleGlyphWord(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA -> true
    else -> false
}

/** One critically damped spring step toward [target] - exact, so any frame time is stable. */
private fun dampedStep(
    values: FloatArray,
    velocities: FloatArray,
    index: Int,
    target: Float,
    followSec: Float,
    dt: Float,
): Boolean {
    val omega = 1f / followSec
    val y = values[index] - target
    val v = velocities[index]
    if (kotlin.math.abs(y) < 0.0005f && kotlin.math.abs(v) < 0.005f) {
        if (values[index] == target && v == 0f) return false
        values[index] = target
        velocities[index] = 0f
        return true
    }
    val e = kotlin.math.exp(-omega * dt)
    val tmp = (v + omega * y) * dt
    values[index] = target + (y + tmp) * e
    velocities[index] = (v - omega * tmp) * e
    return true
}

/** Gaps up to this count as "the next word starts as this one ends". */
private const val NoGapSec = 0.03

private const val FastSecPerChar = 0.1f
private const val SlowSecPerChar = 0.34f

private const val GlowAttackFraction = 0.35f
private const val GlowReleaseFraction = 0.3f

private class TimedRange(
    val start: Int,
    val end: Int,
    val startTime: Double,
    val endTime: Double,
)

internal fun karaokeFilledChars(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionMs: Long,
): Int {
    var searchFrom = 0
    val ranges = words.map { word ->
        val trimmed = word.text.trim()
        val start = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, searchFrom)
        if (start >= 0) {
            searchFrom = start + trimmed.length
            start to (start + trimmed.length)
        } else null
    }
    val positionSec = positionMs / 1000.0
    var filled = 0
    words.forEachIndexed { index, word ->
        val range = ranges.getOrNull(index) ?: return@forEachIndexed
        when {
            positionSec >= word.endTime -> filled = range.second
            positionSec >= word.startTime -> {
                val span = (word.endTime - word.startTime).takeIf { it > 0.0 } ?: return@forEachIndexed
                val within = ((positionSec - word.startTime) / span).coerceIn(0.0, 1.0)
                filled = range.first + ((range.second - range.first) * within).roundToInt()
            }
        }
    }
    return filled.coerceIn(0, text.length)
}

internal fun karaokeAnnotated(
    text: String,
    filledChars: Int,
    highlightColor: Color,
    baseColor: Color,
) = buildAnnotatedString {
    val cut = filledChars.coerceIn(0, text.length)
    if (cut > 0) {
        withStyle(SpanStyle(color = highlightColor)) { append(text.substring(0, cut)) }
    }
    if (cut < text.length) {
        withStyle(SpanStyle(color = baseColor)) { append(text.substring(cut)) }
    }
}

@Composable
private fun rememberKaraokeHead(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
    /** Style 1: sweep through spaces smoothly and drive the per-word rise/swell springs. */
    wordMotion: Boolean = false,
): KaraokeHead {

    val timed = remember(text, words) {
        var searchFrom = 0
        words.mapNotNull { word ->
            val trimmed = word.text.trim()
            val start = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, searchFrom)
            if (start < 0) return@mapNotNull null
            searchFrom = start + trimmed.length
            TimedRange(start, searchFrom, word.startTime, word.endTime)
        }
    }

    val head = remember(text, words) {
        val (starts, ends) = visualWords(text)
        KaraokeHead(starts, ends, motionCells(text, starts, ends))
    }

    LaunchedEffect(text, timed, positionProvider, wordMotion) {

        var lastTick = -1L
        var lastTickAtMs = 0L
        var lastFrameMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                val tick = positionProvider()
                if (tick != lastTick) {
                    lastTick = tick
                    lastTickAtMs = frameMs
                }

                val elapsed = (frameMs - lastTickAtMs).coerceIn(0L, 120L)
                val positionSec = (tick + elapsed) / 1000.0

                var next = 0f
                var nextGlow = 0f
                var nextSwell = 0f
                for (index in timed.indices) {
                    val word = timed[index]
                    if (positionSec < word.startTime) break

                    if (positionSec >= word.endTime) {
                        next = word.end.toFloat()

                        val following = timed.getOrNull(index + 1) ?: continue
                        val gap = following.startTime - word.endTime
                        if (gap > 0.0 && positionSec < following.startTime) {
                            val through = ((positionSec - word.endTime) / gap).coerceIn(0.0, 1.0)
                            next = word.end +
                                (following.start - word.end) * smoothstep(through.toFloat())
                        }
                        continue
                    }

                    val span = word.endTime - word.startTime
                    if (span <= 0.0) break
                    val within = ((positionSec - word.startTime) / span).coerceIn(0.0, 1.0)
                    val length = (word.end - word.start).coerceAtLeast(1)

                    // When the next word starts the instant this one ends, the space between them
                    // has no time of its own, and the head used to jump it in one frame - the
                    // abrupt cut at every word boundary. Style 1 gives the space a small share of
                    // this word's time instead, so the sweep glides across it.
                    val following = timed.getOrNull(index + 1)
                    val tail = if (
                        wordMotion && following != null &&
                        following.startTime - word.endTime <= NoGapSec &&
                        following.start > word.end
                    ) {
                        following.start - word.end
                    } else {
                        0
                    }
                    next = if (tail > 0) {
                        val letters = length.toFloat()
                        val letterShare = letters / (letters + tail * SpaceTimeWeight)
                        val w = within.toFloat()
                        if (w <= letterShare) {
                            word.start + letters * (w / letterShare)
                        } else {
                            word.end + tail * ((w - letterShare) / (1f - letterShare))
                        }
                    } else {
                        word.start + (length * within).toFloat()
                    }

                    val secPerChar = (span / length).toFloat()
                    val hold = smoothstep(
                        (secPerChar - FastSecPerChar) / (SlowSecPerChar - FastSecPerChar)
                    )
                    val w = within.toFloat()
                    nextGlow = hold *
                        smoothstep(w / GlowAttackFraction) *
                        smoothstep((1f - w) / GlowReleaseFraction)
                    // The swell has its own, squarer envelope: it fills the letters one by one as
                    // they're sung, so it has to still be there when the sweep reaches the last
                    // letter. The glow's longer release had it gone by then.
                    nextSwell = hold *
                        smoothstep(w / SwellAttackFraction) *
                        smoothstep((1f - w) / SwellReleaseFraction)
                    break
                }

                if (next != head.offset.floatValue) head.offset.floatValue = next

                val glowNow = head.glow.floatValue
                val glowNext =
                    if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.12f
                if (glowNext != glowNow) head.glow.floatValue = glowNext

                if (wordMotion && head.cellCount > 0) {
                    val dt = if (lastFrameMs < 0L) 0.016f else ((frameMs - lastFrameMs) / 1000f).coerceIn(0f, 0.064f)
                    val cells = head.cells
                    var changed = false
                    for (c in 0 until head.cellCount) {
                        val w = cells.word[c]
                        val wordStart = head.wordStarts[w]
                        val wordEnd = head.wordEnds[w]
                        val letterStart = cells.starts[c]

                        // The word lifts as a whole as the sweep goes through it...
                        val through = (next - wordStart) / (wordEnd - wordStart).coerceAtLeast(1)
                        val wordRise = smoothstep(through / RiseCompleteAt)
                        // ...and each letter lifts further as the sweep reaches it. Once the word
                        // is over every letter is up, so the last word of a line (where the sweep
                        // stops) doesn't leave its final letters hanging part-way.
                        val letterRise = if (next >= wordEnd) {
                            1f
                        } else {
                            smoothstep((next - letterStart + LetterRiseLeadChars) / LetterRiseSpanChars)
                        }
                        val riseTarget = wordRise + (letterRise - wordRise) * LetterRiseShare

                        // A held note swells the word the sweep is in, letter by letter as each
                        // is reached, then the whole word settles together once the sweep leaves.
                        val swellTarget = if (next >= wordStart && next <= wordEnd) {
                            nextSwell *
                                smoothstep((next - letterStart + LetterSwellLeadChars) / LetterSwellSpanChars)
                        } else {
                            0f
                        }
                        changed = dampedStep(head.rise, head.riseVelocity, c, riseTarget, RiseFollowSec, dt) or changed
                        changed = dampedStep(head.swell, head.swellVelocity, c, swellTarget, SwellFollowSec, dt) or changed
                    }
                    if (changed) head.motionTick.intValue++
                }
                lastFrameMs = frameMs
            }
        }
    }

    return head
}

/**
 * Where each letter cell sits in one text layout, plus per-frame scratch space for its transform.
 * Rebuilt only when the layout object changes. Parallel arrays, one entry per (cell, line) piece -
 * a cell only has more than one piece when it's a whole word that wraps.
 *
 * Pieces are in reading order, so the pieces of one word on one line are consecutive.
 */
private class MotionLayout(
    val count: Int,
    val tiled: Boolean,
    val cell: IntArray,
    val word: IntArray,
    val line: IntArray,
    /** The letter's own advance - what it's centred on and spread by when its word swells. */
    val coreLeft: FloatArray,
    val coreRight: FloatArray,
    /** The slot it's clipped to before it moves. */
    val clipLeft: FloatArray,
    val clipTop: FloatArray,
    val clipRight: FloatArray,
    val clipBottom: FloatArray,
    val baseline: FloatArray,
) {
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val scale = FloatArray(count)
}

private class MotionLayoutCache {
    private var forLayout: TextLayoutResult? = null
    private var cached: MotionLayout? = null

    fun layoutFor(layout: TextLayoutResult, head: KaraokeHead, edgePadPx: Float): MotionLayout {
        val current = cached
        if (current != null && layout === forLayout) return current
        forLayout = layout
        return buildMotionLayout(layout, head, edgePadPx).also { cached = it }
    }
}

/** Side room for glyph overhang in untiled (per-word) slots, only where the neighbour is a space. */
private const val WordBoxPadPx = 3f

private fun buildMotionLayout(
    layout: TextLayoutResult,
    head: KaraokeHead,
    edgePadPx: Float,
): MotionLayout {
    val text = layout.layoutInput.text.text
    val tiled = head.cells.tiled
    val cells = ArrayList<Int>()
    val lines = ArrayList<Int>()
    val segStarts = ArrayList<Int>()
    val segEnds = ArrayList<Int>()
    val lefts = ArrayList<Float>()
    val rights = ArrayList<Float>()
    if (text.isNotEmpty() && layout.lineCount > 0) {
        for (c in 0 until head.cellCount) {
            val start = head.cells.starts[c].coerceIn(0, text.length)
            val end = head.cells.ends[c].coerceIn(start, text.length)
            if (end <= start) continue
            for (line in layout.getLineForOffset(start)..layout.getLineForOffset(end - 1)) {
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line))
                if (segEnd <= segStart) continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                for (i in segStart until segEnd) {
                    if (text[i].isWhitespace()) continue
                    val bounds = layout.getBoundingBox(i)
                    left = minOf(left, bounds.left)
                    right = maxOf(right, bounds.right)
                }
                if (left > right) continue
                cells += c
                lines += line
                segStarts += segStart
                segEnds += segEnd
                lefts += left
                rights += right
            }
        }
    }

    val n = cells.size
    val lastLine = layout.lineCount - 1
    val clipLeft = FloatArray(n)
    val clipRight = FloatArray(n)
    val clipTop = FloatArray(n)
    val clipBottom = FloatArray(n)
    val baseline = FloatArray(n)
    for (k in 0 until n) {
        val line = lines[k]
        // Exact line boundaries between lines, so a moving letter never carries a sliver of the
        // line above or below it. The outside edges reach into the layer's spare room.
        clipTop[k] = layout.getLineTop(line) - if (line == 0) edgePadPx else 0f
        clipBottom[k] = layout.getLineBottom(line) + if (line == lastLine) edgePadPx else 0f
        baseline[k] = layout.getLineBaseline(line)
    }

    if (tiled) {
        // The slots cover each line edge to edge with no gaps: a boundary between two letters is
        // where they meet, a boundary across a space is the middle of the space, and the ends of
        // the line reach into the spare room. Whatever ink a glyph has - an overhang, a tail, a
        // mark - it lands in exactly one slot.
        var a = 0
        while (a < n) {
            var b = a
            while (b + 1 < n && lines[b + 1] == lines[a]) b++
            val line = lines[a]
            clipLeft[a] = minOf(lefts[a], layout.getLineLeft(line)) - edgePadPx
            clipRight[b] = maxOf(rights[b], layout.getLineRight(line)) + edgePadPx
            for (k in a until b) {
                val meet = (rights[k] + lefts[k + 1]) / 2f
                clipRight[k] = meet
                clipLeft[k + 1] = meet
            }
            a = b + 1
        }
    } else {
        for (k in 0 until n) {
            val segStart = segStarts[k]
            val segEnd = segEnds[k]
            val padLeft = if (segStart == 0 || text[segStart - 1].isWhitespace()) WordBoxPadPx else 0f
            val padRight = if (segEnd >= text.length || text[segEnd].isWhitespace()) WordBoxPadPx else 0f
            clipLeft[k] = lefts[k] - padLeft
            clipRight[k] = rights[k] + padRight
        }
    }

    return MotionLayout(
        count = n,
        tiled = tiled,
        cell = cells.toIntArray(),
        word = IntArray(n) { head.cells.word[cells[it]] },
        line = lines.toIntArray(),
        coreLeft = lefts.toFloatArray(),
        coreRight = rights.toFloatArray(),
        clipLeft = clipLeft,
        clipTop = clipTop,
        clipRight = clipRight,
        clipBottom = clipBottom,
        baseline = baseline,
    )
}

/** A swell this small is drawn as none, so a settled word goes back to a single plain draw. */
private fun settledSwell(swell: Float): Float = if (swell < 0.002f) 0f else swell

/**
 * Style 1: draws the line one letter at a time, each clipped to its own slot and then moved.
 *
 * Unsung letters sit [WordWaitDrop] low and rise as they're sung - partly with their word, partly on
 * their own (see [LetterRiseShare]), so the rise runs through each word as a wave. On a held note
 * the letters swell and lift one by one as the sweep reaches them; the word spreads about its
 * centre by its average swell to make room, so neighbours don't crowd each other or drift sideways.
 *
 * Clip first, then move: the clip is in the letter's own (unmoved) space, so only that letter's
 * pixels ever travel. Letters with the same transform are drawn together - the risen part of a
 * line and the waiting part are one draw each - and when nothing is displaced it's a single plain
 * draw, so the cost is a handful of draws around the sweep, not one per letter.
 */
@Composable
private fun rememberWordMotionModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    riseAmplitudeProvider: () -> Float,
    fakeGlowProvider: () -> Float,
    motionScale: Float = 1f,
): Modifier {
    val cache = remember(head) { MotionLayoutCache() }
    return Modifier.drawWithContent {
        // Subscribes this draw to the per-letter springs.
        @Suppress("UNUSED_VARIABLE") val tick = head.motionTick.intValue
        val layout = layoutHolder[0]
        if (layout == null || head.cellCount == 0) {
            drawContent()
            return@drawWithContent
        }
        val m = cache.layoutFor(layout, head, MotionBleed.toPx())
        val n = m.count
        if (n == 0) {
            drawContent()
            return@drawWithContent
        }

        val drop = WordWaitDrop.toPx() * motionScale * riseAmplitudeProvider().coerceIn(0f, 1f)
        val lift = HeldLift.toPx() * motionScale
        val glow = fakeGlowProvider().coerceIn(0f, 1f)

        // Work out every letter's transform, one word-on-a-line at a time.
        var uniform = true
        var i = 0
        while (i < n) {
            var j = i
            while (j + 1 < n && m.word[j + 1] == m.word[i] && m.line[j + 1] == m.line[i]) j++

            var swellSum = 0f
            for (k in i..j) swellSum += settledSwell(head.swell[m.cell[k]])
            val meanSwell = swellSum / (j - i + 1)
            val pivot = (m.coreLeft[i] + m.coreRight[j]) / 2f
            for (k in i..j) {
                val swell = settledSwell(head.swell[m.cell[k]])
                val dy = drop * (1f - head.rise[m.cell[k]]) - lift * swell
                m.dy[k] = if (kotlin.math.abs(dy) < 0.05f) 0f else dy
                m.dx[k] = ((m.coreLeft[k] + m.coreRight[k]) / 2f - pivot) * HeldZoom * meanSwell
                m.scale[k] = 1f + HeldZoom * swell
                if (m.scale[k] != 1f || kotlin.math.abs(m.dy[k] - m.dy[0]) > MergeEpsilonPx) {
                    uniform = false
                }
            }

            // Without AGSL there's no bloom: a soft light behind the held word stands in for it.
            if (glow > 0f && meanSwell > 0.01f) {
                val width = m.coreRight[j] - m.coreLeft[i]
                val height = m.clipBottom[i] - m.clipTop[i]
                val centreY = (m.clipTop[i] + m.clipBottom[i]) / 2f + (m.dy[i] + m.dy[j]) / 2f
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = FakeGlowAlpha * meanSwell * glow), Color.Transparent),
                        center = Offset(pivot, centreY),
                        radius = maxOf(width, height) * 0.75f,
                    ),
                    topLeft = Offset(pivot - width * 0.75f, centreY - height * 0.6f),
                    size = Size(width * 1.5f, height * 1.2f),
                )
            }
            i = j + 1
        }

        // Nothing out of step (the line hasn't started, or everything's up): one draw.
        if (uniform) {
            val dy = m.dy[0]
            if (dy == 0f) {
                drawContent()
            } else {
                withTransform({ translate(0f, dy) }) { this@drawWithContent.drawContent() }
            }
            return@drawWithContent
        }

        var k = 0
        while (k < n) {
            // Neighbouring slots that move together are one slot (tiled slots meet edge to edge).
            if (m.tiled && m.scale[k] == 1f && m.dx[k] == 0f) {
                var e = k
                while (e + 1 < n && m.line[e + 1] == m.line[k] && m.scale[e + 1] == 1f &&
                    m.dx[e + 1] == 0f && kotlin.math.abs(m.dy[e + 1] - m.dy[k]) <= MergeEpsilonPx
                ) e++
                withTransform({ translate(0f, m.dy[k]) }) {
                    clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[e], m.clipBottom[k]) {
                        this@drawWithContent.drawContent()
                    }
                }
                k = e + 1
                continue
            }

            val s = m.scale[k]
            withTransform({
                translate(m.dx[k], m.dy[k])
                if (s != 1f) {
                    scale(s, s, Offset((m.coreLeft[k] + m.coreRight[k]) / 2f, m.baseline[k]))
                }
            }) {
                clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[k], m.clipBottom[k]) {
                    this@drawWithContent.drawContent()
                }
            }
            k++
        }
    }
}
