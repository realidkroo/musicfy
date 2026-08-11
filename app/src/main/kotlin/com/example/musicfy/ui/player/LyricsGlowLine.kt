// LyricsGlowLine.kt
// Per-line renderer for the lyrics page. Lines away from playback are dim, blurred and scaled
// down; the active one is sharp and white, with a soft left-to-right sweep across it timed off
// LyricsEntry.words when the provider supplied word timestamps. An optional smaller
// romanized/translated sub-line renders beneath the main text.
//
// The sweep is deliberately ONE effect: a gradient boundary travelling through the line. Earlier
// versions stacked a per-letter lift, a per-letter scale and a multi-pass additive bloom on top of
// it. Each of those needed the text redrawn again per frame through its own clip, which is what
// made the page struggle, and the offset bloom passes read as ghosted duplicate letters rather
// than as light. The reference this is modelled on does none of that — it is a graded wash moving
// through the word, and nothing else.

package com.example.musicfy.ui.player

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.musicfy.lyrics.LyricsEntry

enum class LyricsLineState { ACTIVE, UPCOMING, PAST, DEFAULT }

/**
 * Lyrics dominate this page — this is the primary content, not a caption under the player.
 *
 * Two different gaps matter here and they must not look the same:
 *
 *  - Between the wrapped rows of ONE lyric line — governed by [LyricsLineHeight]. These rows are
 *    a single sung phrase that merely ran out of screen width, so they need to read as continuous.
 *  - Between two separate lyric lines — governed by the 16dp vertical padding on the row below.
 *
 * At 52sp the wrapped-row gap was as wide as the gap between distinct lines, so a phrase that
 * wrapped looked like two unrelated lines with an instrumental break between them. 40sp against a
 * 32sp font keeps wrapped rows visibly bound together while the 32dp between entries still reads
 * as a real separation.
 */
private val LyricsFontSize = 32.sp
private val LyricsLineHeight = 40.sp

/**
 * Blur radius per stage. Stage 0 is the active line and its immediate surroundings while
 * scrolling, 1 is one line either side, 2 is everything further away.
 */
private val BlurStageRadii = floatArrayOf(0f, 5f, 12f)

/**
 * RenderEffects are immutable and comparatively expensive to build, and there are only three
 * distinct radii. Building them once here avoids allocating a fresh one inside every line's
 * graphicsLayer on every frame of a blur transition, which is what the old code did.
 */
private const val BlurQuantStepPx = 0.5f
private val BlurEffectCache: Array<androidx.compose.ui.graphics.RenderEffect?> =
    arrayOfNulls((BlurStageRadii.max() / BlurQuantStepPx).toInt() + 2)

/**
 * The blur for a radius, snapped to a [BlurQuantStepPx] grid and built at most once per step.
 *
 * Animating the radius directly would allocate a fresh RenderEffect on every frame of every
 * transition, on every line — which is why this was a hard three-way switch before. Quantising
 * caps the number of distinct effects at about two dozen for the whole app, and half a pixel of
 * blur is far below what anyone can see, so the ramp still looks continuous.
 *
 * Called from the draw phase, and only ever from the main thread, so the lazy fill needs no
 * synchronisation.
 */
private fun blurEffectForRadius(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.05f) return null
    val step = (radius / BlurQuantStepPx).roundToInt().coerceIn(1, BlurEffectCache.lastIndex)
    BlurEffectCache[step]?.let { return it }
    val quantised = step * BlurQuantStepPx
    return android.graphics.RenderEffect
        .createBlurEffect(quantised, quantised, android.graphics.Shader.TileMode.CLAMP)
        .asComposeRenderEffect()
        .also { BlurEffectCache[step] = it }
}

/**
 * Three clearly separated tones, not four similar ones: the line being sung, the line either side
 * of it, and everything else.
 */
private const val UpcomingAlpha = 0.5f
private const val PastAlpha = 0.24f
private const val DefaultAlpha = 0.38f

/**
 * Alpha of the not-yet-sung tail of the ACTIVE line.
 *
 * Has to stay above [UpcomingAlpha], or the unsung half of the line you are ON renders darker than
 * the line below it and the page reads upside down. Above it, but not far: the contrast that
 * matters is between the sung and unsung halves of this line.
 */
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
    /** Style two is this page with the per-letter warp turned off; the sweep itself is unchanged. */
    waveEnabled: Boolean = true,
    /** Eight-tap bloom instead of four. Off trades a slightly flatter halo for fragment cost. */
    highBloom: Boolean = true,
    /**
     * Drops this line's blur RenderEffect outright rather than letting it ramp down.
     *
     * [blurStage] going to 0 only *retargets* the 350ms radius tween, so for most of a transition
     * every visible line is still running a real Gaussian pass — which is precisely the frames
     * where the page can least afford one. Suppression has to be immediate to be worth anything.
     *
     * Note this also stops [blurRadius] being read in the draw phase at all while suppressed, so
     * the still-running tween cannot invalidate the layer either.
     */
    suppressEffects: Boolean = false,
) {
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
    // Ramped, not snapped. This used to jump straight between the three cached radii, so a line
    // changing stage — or the whole list dropping to stage 0 the instant a scroll began — popped
    // between sharp and blurred with no transition at all.
    //
    // A tweened radius does mean a different RenderEffect object per frame, which is what the
    // snapping was avoiding. So the radius is quantised to a fixed grid and the effects for that
    // grid are built once and shared (see [blurEffectForRadius]) — the animation is smooth to the
    // eye while the number of distinct RenderEffects stays small and fixed.
    val blurRadius by animateFloatAsState(
        targetValue = BlurStageRadii[blurStage.coerceIn(0, BlurStageRadii.lastIndex)],
        animationSpec = tween(durationMillis = 350),
        label = "lyricsLineBlur",
    )

    val baseColor = LocalContentColor.current
    val textColor = if (state == LyricsLineState.ACTIVE) Color.White else baseColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 8dp was fine at the old 22sp font — enough slack to absorb the active line's 6%
            // scale-up (below) without it visually reaching into the neighbouring line. At the
            // current 32sp/40sp size that same 6% is a much bigger absolute pixel amount, and
            // 8dp is no longer enough clearance: the scaled-up active line bleeds into whatever
            // line sits right above/below it, which is what showed up as ghosted overlapping
            // text. Scale itself reserves no extra layout space (it's paint-time only), so this
            // padding is the only thing that can give it room to grow into.
            //
            // Horizontal 36dp — matches PlayerProgressSlider's time-label row (PlayerHorizontalPadding
            // + 4.dp) and the header row above, so lyrics text lines up with the timestamp and
            // the cover/title instead of sitting at its own separate inset.
            .padding(vertical = 16.dp, horizontal = 36.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                // Read here, in the draw phase, so the ramp repaints without recomposing the line.
                renderEffect = if (suppressEffects) null else blurEffectForRadius(blurRadius)
            }
    ) {
        // Bracketed asides — "Lift your head to the sky (Sky)", or a whole line of "(Ooh)" — are
        // backing vocals, not lyrics in their own right. They come out of the lead text, lose the
        // brackets, and render smaller underneath, sliding up out of the line they came from.
        val parts = remember(entry.text, entry.words) {
            splitBackingVocal(entry.text, entry.words)
        }
        val leadText = parts.lead
        val words = parts.leadWords

        // The wave retracts rather than being switched off. When a line stopped being ACTIVE the
        // whole sweeping renderer used to be swapped for a plain Text on that same frame, so every
        // lifted letter snapped flat in one step. This amplitude eases to zero on the same 600ms
        // as the line's own alpha and scale, and the sweep stays composed until it gets there, so
        // the letters settle back down as the line dims out from under them.
        val waveAmplitude by animateFloatAsState(
            targetValue = if (state == LyricsLineState.ACTIVE) 1f else 0f,
            animationSpec = animSpec,
            label = "lyricsWaveAmplitude",
        )
        // The karaoke path renders its own sub-line so the pronunciation can sweep in step with
        // the main line; the static path below keeps rendering it separately.
        // Whether this entry should be sweeping at all, independent of which half is being drawn —
        // a whole-line aside has no lead text but is still a sung line and still sweeps.
        val sweepActive = !words.isNullOrEmpty() &&
            (state == LyricsLineState.ACTIVE || waveAmplitude > 0.01f)
        val karaokeActive = sweepActive && leadText.isNotEmpty()
        if (karaokeActive) {
            KaraokeSweepText(
                text = leadText,
                words = words,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                baseColor = textColor.copy(alpha = ActiveUnsungAlpha),
                // Sung text is white, NOT the accent. accentColor comes out of
                // PlayerColorExtractor.darkenIfTooLight(), so using it as the fill colour meant
                // the lit part of the line was painted in a deliberately darkened cover colour —
                // dark text on a dark backdrop.
                highlightColor = Color.White,
                subLine = subLine,
                highBloom = highBloom,
            )
        } else if (leadText.isNotEmpty()) {
            Text(
                text = leadText,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = LyricsFontSize,
                    lineHeight = LyricsLineHeight,
                ),
                fontWeight = if (state == LyricsLineState.ACTIVE) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
            )
        }

        parts.backing?.let { backing ->
            BackingVocalLine(
                text = backing,
                color = textColor,
                // A whole-line aside has nothing above it on this row to emerge from, so it just
                // fades in place instead of sliding.
                slide = leadText.isNotEmpty(),
                words = parts.backingWords,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                sweep = sweepActive,
                // Expansion runs on the LINE's clock, not its own. It used to have a private
                // 420ms animation keyed off `active`, so the aside started folding away the
                // moment the next line took over — while its own sweep was still running and
                // while the line itself was still 600ms from finishing its fade. Sharing
                // waveAmplitude keeps the collapse in step with everything else on the row.
                expandProvider = { waveAmplitude },
                highBloom = highBloom,
            )
        }

        // Only for non-karaoke lines — KaraokeSweepText draws its own sweeping sub-line.
        if (!karaokeActive && !subLine.isNullOrBlank()) {
            Text(
                text = subLine,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * Width of the soft lit/unlit boundary, in CHARACTERS behind the sweep head.
 *
 * Measured off the real text layout rather than set in pixels: a fixed pixel band is under one
 * letter at 32sp and over three at half that size, so the boundary only looks consistent when it
 * is expressed in characters and resolved against the actual glyph positions.
 */
private const val EdgeSoftChars = 1

/** Fallback boundary width, in px, for the wrapped-line case where the measurement can't be taken. */
private const val EdgeSoftPx = 26f

/** How far past the head the lit region reaches, so the letter under it isn't cut in half. */
private const val EdgeLeadPx = 6f

/** Half-widths of the lift wave, in characters, behind and ahead of the sweep head. */
private const val WaveCharsBehind = 4f
private const val WaveCharsAhead = 3f

/** Peak lift, in px, of the letters directly under the head. */
private const val WaveLiftPx = 4.5f

/** Peak vertical stretch of those letters. Applied about the baseline, so they grow upward. */
private const val WaveZoom = 0.09f

/** Radius, in px, of the bloom's sample taps. */
private const val BloomRadiusPx = 2.6f

/** Peak bloom strength on a fully held word. */
private const val BloomStrength = 1.6f

/**
 * Where the bloom sits relative to the head, and how wide it is, both in characters.
 *
 * It trails the head deliberately. Centred on the head it lands exactly on the sweep's gradient
 * boundary — the one column where the lit copy is half-masked and the dim copy is still dim — so
 * it glows the faintest pixels on screen and reads as nothing at all.
 */
private const val BloomLagChars = 1.6f
private const val BloomSpanChars = 3.5f

/**
 * The lift, the stretch and the bloom, as one filter over the already-rendered line.
 *
 * Every earlier attempt did this by redrawing glyphs — clip to a letter, translate, draw the whole
 * paragraph again; repeat per letter, then four more times for the bloom. That is what made the
 * page struggle, and the offset bloom passes showed up as ghosted duplicate letters because they
 * genuinely were duplicate letters.
 *
 * A shader has none of that problem. The text is rasterised exactly once into the layer, and this
 * reads that raster back at a displaced coordinate, so a letter moving up costs nothing beyond the
 * sample that was already happening. The bloom is four extra taps of the same texture rather than
 * four more paragraph draws, and because it blurs the real raster it cannot ghost.
 *
 * The wave is a smoothstepped bell centred on the head and asymmetric about it — wider behind than
 * ahead, so letters ease back down after the voice has passed while letters it hasn't reached yet
 * are still nearly flat:
 *
 *     ...........[ur]..            <- head, full lift
 *     .......n yo....l
 *     falling i........ov
 *     ..................e ri
 *     ......................ght now  <- untouched
 *
 * `bell` is gated to the head's own line: the displacement is a function of x alone, so without
 * the gate the letters directly above and below on a wrapped line would rise with it.
 */
private const val WaveAgsl = """
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
uniform float bloomX;
uniform float bloomSpan;
uniform float highQuality;

half4 main(float2 coord) {
    float onLine = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;

    float dx = coord.x - headX;
    float span = dx < 0.0 ? spanBehind : spanAhead;
    float a = min(1.0, abs(dx) / max(span, 1.0));
    float w = 1.0 - a;
    float bell = w * w * (3.0 - 2.0 * w) * onLine;

    // Sampling from BELOW the output pixel is what makes the letter appear to rise.
    float2 p = coord;
    p.y += liftPx * bell;
    // Stretch about the baseline, so the glyph grows upward instead of drifting off it.
    p.y = baseY + (p.y - baseY) / (1.0 + zoomAmt * bell);

    half4 c = content.eval(p);

    if (bloom > 0.0 && onLine > 0.0) {
        // The bloom gets its OWN bell, wider than the lift's and centred behind the head rather
        // than on it. Sharing the lift's bell put the glow exactly where the sweep's gradient
        // boundary is — the one place the lit copy is half-transparent and the dim copy is still
        // dim — so it landed on the faintest pixels on screen and was invisible.
        float bdx = coord.x - bloomX;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;

        // Four diagonal taps always; the axis-aligned four only on the high setting. Diagonals
        // alone still read as a halo — they just make it very slightly less round.
        half4 b = content.eval(p + float2(bloomR, bloomR))
                + content.eval(p + float2(bloomR, -bloomR))
                + content.eval(p + float2(-bloomR, bloomR))
                + content.eval(p + float2(-bloomR, -bloomR));
        half weight = 0.25;
        if (highQuality > 0.5) {
            b += content.eval(p + float2(bloomR, 0.0))
               + content.eval(p - float2(bloomR, 0.0))
               + content.eval(p + float2(0.0, bloomR))
               + content.eval(p - float2(0.0, bloomR));
            weight = 0.125;
        }
        c += b * (bloom * bbell * weight);
    }
    return c;
}
"""

/**
 * The sweep: two stacked copies of the line, the bright one revealed up to the playback head with
 * a soft gradient boundary.
 *
 * Both Texts get identical content, style and constraints, so they lay out identically and
 * register exactly on top of one another. Neither one's content ever changes, so each paragraph is
 * measured once, at composition. Everything that animates happens inside [drawWithContent],
 * reading the head position during the DRAW phase — a snapshot value read there invalidates the
 * draw and nothing else, so the sweep advances every frame without recomposing this composable or
 * re-measuring the text. That is what the per-character SpanStyle versions could never do: any
 * per-character style rebuilds the AnnotatedString, which re-runs layout on every frame.
 */
@Composable
private fun KaraokeSweepText(
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
) {
    val head = rememberKaraokeHead(text, words, positionProvider)

    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
    )

    // Held in plain holders rather than MutableState: the layout only changes when the text or the
    // width does, and routing either through snapshot state would add a recomposition to a path
    // that exists specifically to avoid them.
    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }
    // One Path, rewound in place every frame. A fresh Path per frame was the only real allocation
    // left on this path.
    val sung = remember(text) { Path() }

    // ONE wave layer for the whole line, wrapping both copies.
    //
    // This used to be two — one per Text — each allocating its own RuntimeShader and its own
    // RenderEffect every frame, so a line with a bracketed aside ran four displacement passes over
    // four offscreen buffers per frame, and two overlapping lines ran eight. The two copies are
    // pixel-registered by construction, so displacing them together is identical output; it also
    // makes it structurally impossible for them to drift apart, which the old design achieved only
    // by feeding both shaders the same uniforms and hoping.
    val wave = rememberWaveModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)

    Column {
        Box(modifier = wave) {
            // Dim copy — the whole line, always, drawn plainly.
            //
            // It is NOT masked. An earlier version punched the sung region back out of it so the
            // two copies were exact complements, which was only necessary because letters were
            // being lifted off their own positions and exposing the dim copy underneath. With the
            // sweep as a pure gradient the two register perfectly and opaque white simply covers
            // what is beneath it — so this needs no offscreen layer and no second mask.
            Text(
                text = text,
                style = style,
                color = baseColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                onTextLayout = { layoutHolder[0] = it },
            )

            // Bright copy, revealed up to the head.
            Text(
                text = text,
                style = style,
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                modifier = Modifier
                    // The DstIn below needs a layer of its OWN to act on — the wave's layer now
                    // sits outside this Box, so it can't serve double duty. Declaring it here
                    // rather than opening a manual saveLayer inside the draw lambda means the
                    // compositor owns and recycles the buffer, instead of one being allocated and
                    // torn down every frame.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f) return@drawWithContent
                        val len = text.length
                        if (len == 0) return@drawWithContent

                        val headOffset = h.toInt().coerceIn(0, len)
                        // Sub-character interpolation so the edge glides between glyphs rather
                        // than stepping from one to the next.
                        val headX = if (headOffset >= len) {
                            layout.getLineRight(layout.getLineForOffset(len - 1))
                        } else {
                            val x0 = layout.getHorizontalPosition(headOffset, true)
                            val x1 = layout.getHorizontalPosition(
                                (headOffset + 1).coerceAtMost(len),
                                true,
                            )
                            // At a wrap x1 jumps back to the left margin; clamping stops the edge
                            // sliding backwards across the line break.
                            if (x1 >= x0) x0 + (x1 - x0) * (h - headOffset) else x0
                        }
                        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(len - 1))
                        val lineTop = layout.getLineTop(headLine)
                        val lineBottom = layout.getLineBottom(headLine)

                        // The boundary trails BEHIND the head: the letters just sung are the ones
                        // part-way lit, and nothing ahead of the voice brightens early.
                        val softFrom = layout.getHorizontalPosition(
                            (headOffset - EdgeSoftChars).coerceAtLeast(layout.getLineStart(headLine)),
                            true,
                        )
                        val fromX = if (softFrom < headX) softFrom else headX - EdgeSoftPx
                        val toX = headX + EdgeLeadPx

                        sung.rewind()
                        for (line in 0..headLine) {
                            val left = layout.getLineLeft(line)
                            val lineRight = layout.getLineRight(line)
                            val right = if (line < headLine) lineRight else minOf(toX, lineRight)
                            if (right > left) {
                                sung.addRect(
                                    Rect(
                                        left,
                                        layout.getLineTop(line),
                                        right,
                                        layout.getLineBottom(line),
                                    )
                                )
                            }
                        }

                        clipPath(sung) { this@drawWithContent.drawContent() }

                        // Soften the leading edge. Confined to the head's own line: a horizontal
                        // gradient spanning the whole node would also fade every fully-sung line
                        // above it, wherever their glyphs happened to sit to the right of the head.
                        // Skipped once the line is fully sung, so the last letter isn't left faded.
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
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * A lyric line split into the lead vocal and whatever was bracketed off as a backing vocal, with
 * each half's word timings resolved to it.
 */
internal class LyricLineParts(
    val lead: String,
    val backing: String?,
    val leadWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
    val backingWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
)

/**
 * Assigns each word timing to the half of the line it actually came from.
 *
 * Handing the whole list to both halves and letting each search for its own words by text does not
 * work: "the sky (Sky)" leaves both halves hunting for the same string, so the lead can bind to the
 * aside's timing (or vice versa) and that word's sweep jumps to the wrong moment. Repeated words
 * anywhere in the line have the same problem.
 *
 * So the words are located once against the ORIGINAL text — the same sequential scan the sweep
 * itself uses, so it resolves identically — and classified by whether they landed inside a bracket.
 */
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
        // Classified on its first character: a word never straddles a bracket boundary, since the
        // bracket itself is a delimiter.
        if (inBracket.getOrElse(start) { false }) backing.add(word) else lead.add(word)
    }
    return lead to backing
}

/**
 * Pulls every bracketed aside out of a lyric line.
 *
 * Providers deliver backing vocals inline — "Lift your head to the sky (Sky)" — and rendering that
 * verbatim puts literal brackets on screen and gives an aside the same weight as the lyric. Every
 * `(...)` and `[...]` group is lifted out; what remains is the lead vocal, and the extracted text
 * becomes a smaller second line.
 *
 * Whole-line parentheticals fall out of this for free: everything is bracketed, so `lead` comes
 * back empty and only the backing line renders.
 *
 * The karaoke timings survive the split because they are matched by searching for each word's text
 * inside the line it is being rendered on — a word that ended up on the other half simply isn't
 * found and is skipped, so each half sweeps with the timings that belong to it.
 */
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
    // An unclosed bracket means the line isn't really structured this way; leave it untouched
    // rather than silently swallowing the rest of it.
    if (depth != 0) return untouched

    val leadText = lead.toString().replace(WhitespaceRun, " ").trim()
    val backingText = backing.toString().replace(WhitespaceRun, " ").trim()
    if (backingText.isEmpty()) return untouched

    val (leadWords, backingWords) = partitionWords(text, words, inBracket)
    return LyricLineParts(
        lead = leadText,
        backing = backingText,
        leadWords = leadWords.ifEmpty { null },
        backingWords = backingWords.ifEmpty { null },
    )
}

private val WhitespaceRun = Regex("\\s{2,}")

/**
 * A backing vocal: small, and it rises into place out of the line above rather than appearing.
 */
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
) {
    // Expansion is driven by the ASIDE'S OWN word timings, not by the line's.
    //
    // It used to follow the line's wave amplitude, and an aside is almost always sung at the END
    // of its line — so the line began retreating while the aside was still mid-sweep and the box
    // collapsed out from under it. That is the "stuck" highlight: the sweep was running fine, the
    // container was closing on top of it.
    val expand = rememberBackingExpand(words, positionProvider, expandProvider)

    Box(
        modifier = Modifier
            .clipToBounds()
            // The height is animated in the LAYOUT pass, not faked with a translation.
            //
            // A graphicsLayer translation is paint-only: the row still reserved the aside's full
            // height at all times, so every line that happened to have one sat further from its
            // neighbours than the lines around it — the spacing that jumps from lyric to lyric —
            // and the text stayed on screen on inactive lines because there was nothing making it
            // go away. Reporting a collapsing height instead means the row genuinely shrinks to
            // nothing, and the list closes the gap as it does.
            //
            // The measurement happens at full size and only the reported height shrinks, so the
            // text never re-wraps while it animates.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val e = expand.floatValue.coerceIn(0f, 1f)
                val height = (placeable.height * e).roundToInt()
                layout(placeable.width, height) {
                    // Anchoring the text's BOTTOM to the bottom of the collapsing box, with the
                    // clip above, makes it descend out from underneath the lead line rather than
                    // fade in beside it.
                    placeable.place(0, if (slide) height - placeable.height else 0)
                }
            }
            .graphicsLayer { alpha = expand.floatValue.coerceIn(0f, 1f) },
    ) {
        // The aside sweeps too. It is a sung line like any other — the provider's word timings for
        // the bracketed words are still in this entry's list, and KaraokeSweepText locates them by
        // searching for each word inside the text it was handed, so passing the whole list here
        // gives this half exactly the timings that belong to it.
        if (sweep && !words.isNullOrEmpty()) {
            KaraokeSweepText(
                text = text,
                words = words,
                positionProvider = positionProvider,
                waveAmplitudeProvider = waveAmplitudeProvider,
                baseColor = color.copy(alpha = ActiveUnsungAlpha),
                highlightColor = Color.White,
                fontSize = BackingVocalFontSize,
                lineHeight = BackingVocalLineHeight,
                highBloom = highBloom,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = BackingVocalFontSize,
                    lineHeight = BackingVocalLineHeight,
                ),
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        }
    }
}

/** Lead-in before the aside's first word, and tail after its last, in seconds. */
private const val BackingLeadInSec = 0.35
private const val BackingTailSec = 0.6

/**
 * How open the backing vocal's box should be, from its own timings.
 *
 * Opens a beat before its first word is due — which is the delayed, staggered entrance the aside
 * is supposed to have — holds for as long as it is being sung, and only closes once its last word
 * has actually ended. Takes the max with the line's own amplitude so it is never *less* open than
 * the line it belongs to, and so a line without word timings behaves exactly as before.
 */
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
                val next = maxOf(own, lineAmplitudeProvider().coerceIn(0f, 1f))
                if (next != state.floatValue) state.floatValue = next
            }
        }
    }
    return state
}

private val BackingVocalFontSize = 21.sp
private val BackingVocalLineHeight = 26.sp

/** Smoothstep. Removes the corners a linear ramp leaves at both ends of a transition. */
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Builds the wave [Modifier] for one copy of the line.
 *
 * Both the dim and the bright copy get their own instance. They carry identical uniforms, so the
 * two rasters are displaced in lockstep and stay registered on each other — a letter half-lit by
 * the sweep rises as one letter, not as two halves sliding apart.
 *
 * Every uniform is computed inside the graphicsLayer lambda, which runs in the DRAW phase. Reading
 * the head there invalidates the layer's draw and nothing else: no recomposition, no re-measure.
 */
@Composable
private fun rememberWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,
    /** 0..1 master amplitude, so the wave can retract instead of being switched off. */
    amplitudeProvider: () -> Float,
    /** Eight bloom taps instead of four — rounder halo, roughly double the fragment cost. */
    highBloom: Boolean,
): Modifier {
    // Null below API 33 — AGSL didn't exist before Tiramisu. There the line simply renders without
    // the wave, which is a missing flourish rather than a broken page.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(WaveAgsl)
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    // One reusable slot per line, holding the last effect and the quantised uniforms it was built
    // from. RenderEffect is immutable, so an animated shader has to make a new one — but only when
    // the picture would actually differ. Rebuilding unconditionally meant a fresh object every
    // frame, per layer, per active line. Same trick as blurEffectForRadius above.
    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        // Cut off well before zero. Below a fiftieth of full strength the displacement is a
        // fraction of a pixel and the bloom is invisible, but the shader pass costs exactly the
        // same — so every line still finishing its 600ms retract was paying full price for
        // something nobody can see.
        if (layout == null || textLength == 0 || h <= 0f || amp <= WaveCutoff) {
            // Dropping the effect entirely once the amplitude reaches zero also drops the layer's
            // shader cost for every line that isn't being sung.
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

        // Span converted from characters to px off this line's own metrics, so the wave covers the
        // same number of letters whatever the text or the font is doing.
        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        val charWidth = if (lineEnd > lineStart) {
            (layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart)
        } else {
            LyricsFontSize.toPx() * 0.5f
        }

        shader.setFloatUniform("headX", headX)
        shader.setFloatUniform("spanBehind", charWidth * WaveCharsBehind)
        shader.setFloatUniform("spanAhead", charWidth * WaveCharsAhead)
        shader.setFloatUniform("liftPx", WaveLiftPx * amp)
        shader.setFloatUniform("zoomAmt", WaveZoom * amp)
        shader.setFloatUniform("baseY", layout.getLineBaseline(headLine))
        shader.setFloatUniform("lineTop", layout.getLineTop(headLine))
        shader.setFloatUniform("lineBottom", layout.getLineBottom(headLine))
        shader.setFloatUniform("bloom", BloomStrength * head.glow.floatValue * amp)
        shader.setFloatUniform("bloomR", BloomRadiusPx)
        shader.setFloatUniform("bloomX", headX - charWidth * BloomLagChars)
        shader.setFloatUniform("bloomSpan", charWidth * BloomSpanChars)
        shader.setFloatUniform("highQuality", if (highBloom) 1f else 0f)

        // Quantised so a sub-pixel head movement doesn't mint a new effect. At 120Hz a normal
        // sweep crosses half a pixel every few frames, so this drops allocation by roughly an
        // order of magnitude without any visible stepping.
        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue)
    }
}

/**
 * Amplitude below which the wave is dropped entirely rather than rendered faintly.
 */
private const val WaveCutoff = 0.02f

/** Quantisation grids for the wave's uniforms — see [WaveEffectCache]. */
private const val WaveHeadStepPx = 0.5f
private const val WaveAmpStep = 0.02f

/**
 * Holds one line's wave RenderEffect and rebuilds it only when the picture would actually change.
 *
 * RenderEffect is immutable, so animating a shader means constructing a new one — but the previous
 * code did that unconditionally inside the graphicsLayer lambda, i.e. once per layer per frame per
 * active line. Quantising the uniforms first means a rebuild only when the head has moved half a
 * pixel or the amplitude/bloom has crossed a fiftieth, which is far below what is visible.
 *
 * Only ever touched from the draw phase on the main thread, so no synchronisation is needed.
 */
private class WaveEffectCache {
    private var lastHead = Int.MIN_VALUE
    private var lastAmp = Int.MIN_VALUE
    private var lastGlow = Int.MIN_VALUE
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effectFor(
        shader: android.graphics.RuntimeShader,
        headX: Float,
        amplitude: Float,
        glow: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val h = (headX / WaveHeadStepPx).roundToInt()
        val a = (amplitude / WaveAmpStep).roundToInt()
        val g = (glow / WaveAmpStep).roundToInt()
        val cached = effect
        if (cached != null && h == lastHead && a == lastAmp && g == lastGlow) return cached
        lastHead = h
        lastAmp = a
        lastGlow = g
        return android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
            .also { effect = it }
    }
}

/** Sweep position plus the two per-word quantities the wave and the bloom are driven by. */
@androidx.compose.runtime.Stable
internal class KaraokeHead {
    /** How far through the line the sweep has reached, in fractional character offsets. */
    val offset = mutableFloatStateOf(0f)

    /**
     * 0..1 — bloom intensity, driven by the current word's seconds-per-character and shaped by an
     * envelope across the word. Short words sung quickly never reach it; a short word held on a
     * long note does, which is the "sometimes" of it.
     */
    val glow = mutableFloatStateOf(0f)
}

/**
 * Seconds per character at which a word counts as rattled off (no bloom) versus held out (full
 * bloom). Per *character* rather than per word, so a long word sung at a normal pace isn't
 * mistaken for a sustained one — a short word on a long note is exactly what should bloom.
 */
private const val FastSecPerChar = 0.1f
private const val SlowSecPerChar = 0.34f

/** Fractions of a word's duration the bloom spends swelling and releasing. */
private const val GlowAttackFraction = 0.35f
private const val GlowReleaseFraction = 0.3f

/** One word's timing paired with the character range it occupies in the rendered line. */
private class TimedRange(
    val start: Int,
    val end: Int,
    val startTime: Double,
    val endTime: Double,
)

/**
 * How many characters of [text] are sung at [positionMs]. Plain function, no frame loop — for the
 * one-line deck preview, where the 15Hz ticker it already collects is resolution enough and the
 * draw-phase machinery of the full lyrics page would be overkill.
 */
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

/** Splits [text] at [filledChars] into a highlighted prefix and a dimmed remainder. */
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

/**
 * Tracks how far through the line playback is, in fractional character offsets, driven by the
 * *individual* word timestamps rather than one interpolation from the line's first word to its
 * last. Words are matched to their real offsets in the text, so gaps between words (and any
 * punctuation the timing data omits) stay in the right place, and the word currently being sung
 * fills proportionally across its own duration.
 *
 * Returns snapshot state that is read only from inside a draw lambda — see [KaraokeSweepText].
 */
@Composable
private fun rememberKaraokeHead(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
): KaraokeHead {
    // Words matched to their character offsets, once. Timing data that doesn't literally appear in
    // the line is dropped here rather than left in the list, so the frame loop below walks a dense
    // array and never has to skip holes.
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

    val head = remember(text, words) { KaraokeHead() }

    LaunchedEffect(text, timed, positionProvider) {
        // PlayerConnection.progressState is a 15Hz ticker (delay(66L)). Sampling it once per
        // displayed frame does NOT make the sweep smooth — at 120Hz the same value is simply read
        // eight times in a row, so the fill still advances in 66ms steps. That quantisation, not
        // the rendering, is what looked like lag even after the draw-phase rewrite.
        //
        // So the position is extrapolated between ticks: remember when a new value arrived and add
        // the wall-clock time elapsed since. Playback is linear between ticks, so this is accurate,
        // and every real tick re-anchors it.
        var lastTick = -1L
        var lastTickAtMs = 0L
        while (true) {
            withFrameMillis { frameMs ->
                val tick = positionProvider()
                if (tick != lastTick) {
                    lastTick = tick
                    lastTickAtMs = frameMs
                }
                // Capped so a paused or stalled player can't let the estimate run away; one tick
                // interval of slack bridges the gap and it self-corrects on the next.
                val elapsed = (frameMs - lastTickAtMs).coerceIn(0L, 120L)
                val positionSec = (tick + elapsed) / 1000.0

                var next = 0f
                var nextGlow = 0f
                for (index in timed.indices) {
                    val word = timed[index]
                    if (positionSec < word.startTime) break

                    if (positionSec >= word.endTime) {
                        next = word.end.toFloat()
                        // Coast across the silence into the next word instead of parking on the
                        // last character and then jumping. The sweep never fully stops, which is
                        // what makes the effect read as continuous rather than as a series of
                        // separate word animations that stall between each other.
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
                    next = word.start + (length * within).toFloat()

                    // Bloom only on words the singer actually holds. Measured per character, so a
                    // short word stretched over a long note reaches full strength while a long
                    // word of the same duration doesn't.
                    val secPerChar = (span / length).toFloat()
                    val hold = smoothstep(
                        (secPerChar - FastSecPerChar) / (SlowSecPerChar - FastSecPerChar)
                    )
                    val w = within.toFloat()
                    nextGlow = hold *
                        smoothstep(w / GlowAttackFraction) *
                        smoothstep((1f - w) / GlowReleaseFraction)
                    break
                }

                if (next != head.offset.floatValue) head.offset.floatValue = next
                // Eased down rather than cut, so the bloom fades out over the gap after a held
                // note instead of vanishing the instant the word's timestamp ends.
                val glowNow = head.glow.floatValue
                val glowNext =
                    if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.12f
                if (glowNext != glowNow) head.glow.floatValue = glowNext
            }
        }
    }

    return head
}
