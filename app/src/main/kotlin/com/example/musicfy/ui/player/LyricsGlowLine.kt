// lyricsglowlinekt
// per-line renderer for the lyrics page lines away from playback are dim
// down; the active one is sharp and white with a soft left-to-right sweep
// lyricsentrywords when the provider supplied word timestamps an optional
// romanized/translated sub-line renders beneath the main text

// the sweep is deliberately one effect: a gradient boundary travelling
// versions stacked a per-letter lift a per-letter scale and a multi-pass
// it each of those needed the text redrawn again per frame through its own
// made the page struggle and the offset bloom passes read as ghosted
// than as light the reference this is modelled on does none of that — it is
// through the word and nothing else

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

// lyrics dominate this page — this is the primary content not a caption under the
private val LyricsFontSize = 32.sp
private val LyricsLineHeight = 40.sp

// blur radius per stage stage 0 is the active line and its immediate surroundings
private val BlurStageRadii = floatArrayOf(0f, 5f, 12f)

// rendereffects are immutable and comparatively expensive to build and there are
private const val BlurQuantStepPx = 0.5f
private val BlurEffectCache: Array<androidx.compose.ui.graphics.RenderEffect?> =
    arrayOfNulls((BlurStageRadii.max() / BlurQuantStepPx).toInt() + 2)

// the blur for a radius snapped to a [blurquantsteppx] grid and built at most
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

// three clearly separated tones not four similar ones: the line being sung the
private const val UpcomingAlpha = 0.5f
private const val PastAlpha = 0.24f
private const val DefaultAlpha = 0.38f

// alpha of the not-yet-sung tail of the active line has to stay above
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
    // style two is this page with the per-letter warp turned off; the sweep itself is unchanged
    waveEnabled: Boolean = true,
    // eight-tap bloom instead of four off trades a slightly flatter halo for fragment cost
    highBloom: Boolean = true,
    // drops this line's blur rendereffect outright rather than letting it ramp down
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
    // ramped not snapped this used to jump straight between the three cached
    // changing stage — or the whole list dropping to stage 0 the instant a
    // between sharp and blurred with no transition at all

    // a tweened radius does mean a different rendereffect object per frame which
    // snapping was avoiding so the radius is quantised to a fixed grid and the
    // grid are built once and shared (see [blureffectforradius]) — the animation
    // eye while the number of distinct rendereffects stays small and fixed
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
            // 8dp was fine at the old 22sp font — enough slack to absorb the active
            // scale-up (below) without it visually reaching into the neighbouring line
            // current 32sp/40sp size that same 6% is a much bigger absolute pixel amount
            // 8dp is no longer enough clearance: the scaled-up active line bleeds into
            // line sits right above/below it which is what showed up as ghosted
            // text scale itself reserves no extra layout space (it's paint-time only) so
            // padding is the only thing that can give it room to grow into

            // horizontal 36dp — matches playerprogressslider's time-label row
            // + 4dp) and the header row above so lyrics text lines up with the timestamp
            // the cover/title instead of sitting at its own separate inset
            .padding(vertical = 16.dp, horizontal = 36.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                // read here in the draw phase so the ramp repaints without recomposing the
                renderEffect = if (suppressEffects) null else blurEffectForRadius(blurRadius)
            }
    ) {
        // bracketed asides — "lift your head to the sky (sky)" or a whole line of
        // backing vocals not lyrics in their own right they come out of the lead
        // brackets and render smaller underneath sliding up out of the line they
        val parts = remember(entry.text, entry.words) {
            splitBackingVocal(entry.text, entry.words)
        }
        val leadText = parts.lead
        val words = parts.leadWords

        // the wave retracts rather than being switched off when a line stopped being
        // whole sweeping renderer used to be swapped for a plain text on that same
        // lifted letter snapped flat in one step this amplitude eases to zero on the
        // as the line's own alpha and scale and the sweep stays composed until it
        // the letters settle back down as the line dims out from under them
        val waveAmplitude by animateFloatAsState(
            targetValue = if (state == LyricsLineState.ACTIVE) 1f else 0f,
            animationSpec = animSpec,
            label = "lyricsWaveAmplitude",
        )
        // the karaoke path renders its own sub-line so the pronunciation can sweep
        // the main line; the static path below keeps rendering it separately
        // whether this entry should be sweeping at all independent of which half is
        // a whole-line aside has no lead text but is still a sung line and still
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
                // sung text is white not the accent accentcolor comes out of
                // playercolorextractordarkeniftoolight() so using it as the fill colour meant
                // the lit part of the line was painted in a deliberately darkened cover
                // dark text on a dark backdrop
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
                // a whole-line aside has nothing above it on this row to emerge from so it
                // fades in place instead of sliding
                slide = leadText.isNotEmpty(),
                words = parts.backingWords,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                sweep = sweepActive,
                // expansion runs on the line's clock not its own it used to have a private
                // 420ms animation keyed off `active` so the aside started folding away the
                // moment the next line took over — while its own sweep was still running and
                // while the line itself was still 600ms from finishing its fade sharing
                // waveamplitude keeps the collapse in step with everything else on the row
                expandProvider = { waveAmplitude },
                highBloom = highBloom,
            )
        }

        // only for non-karaoke lines — karaokesweeptext draws its own sweeping
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

// width of the soft lit/unlit boundary in characters behind the sweep head
private const val EdgeSoftChars = 1

// fallback boundary width in px for the wrapped-line case where the measurement can't be taken
private const val EdgeSoftPx = 26f

// how far past the head the lit region reaches so the letter under it isn't cut in half
private const val EdgeLeadPx = 6f

// half-widths of the lift wave in characters behind and ahead of the sweep head
private const val WaveCharsBehind = 4f
private const val WaveCharsAhead = 3f

// peak lift in px of the letters directly under the head
private const val WaveLiftPx = 4.5f

// peak vertical stretch of those letters applied about the baseline so they grow upward
private const val WaveZoom = 0.09f

// radius in px of the bloom's sample taps
private const val BloomRadiusPx = 2.6f

// peak bloom strength on a fully held word
private const val BloomStrength = 1.6f

// where the bloom sits relative to the head and how wide it is both in characters
private const val BloomLagChars = 1.6f
private const val BloomSpanChars = 3.5f

// the lift the stretch and the bloom as one filter over the already-rendered line
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

    // sampling from below the output pixel is what makes the letter appear to
    float2 p = coord;
    p.y += liftPx * bell;
    // stretch about the baseline so the glyph grows upward instead of drifting
    p.y = baseY + (p.y - baseY) / (1.0 + zoomAmt * bell);

    half4 c = content.eval(p);

    if (bloom > 0.0 && onLine > 0.0) {
        // the bloom gets its own bell wider than the lift's and centred behind the
        // than on it sharing the lift's bell put the glow exactly where the sweep's
        // boundary is — the one place the lit copy is half-transparent and the dim
        // dim — so it landed on the faintest pixels on screen and was invisible
        float bdx = coord.x - bloomX;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;

        // four diagonal taps always; the axis-aligned four only on the high setting
        // alone still read as a halo — they just make it very slightly less round
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

// the sweep: two stacked copies of the line the bright one revealed up to the
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

    // held in plain holders rather than mutablestate: the layout only changes
    // width does and routing either through snapshot state would add a
    // that exists specifically to avoid them
    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }
    // one path rewound in place every frame a fresh path per frame was the only
    // left on this path
    val sung = remember(text) { Path() }

    // one wave layer for the whole line wrapping both copies

    // this used to be two — one per text — each allocating its own runtimeshader
    // rendereffect every frame so a line with a bracketed aside ran four
    // four offscreen buffers per frame and two overlapping lines ran eight the
    // pixel-registered by construction so displacing them together is identical
    // makes it structurally impossible for them to drift apart which the old
    // by feeding both shaders the same uniforms and hoping
    val wave = rememberWaveModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)

    Column {
        Box(modifier = wave) {
            // dim copy — the whole line always drawn plainly

            // it is not masked an earlier version punched the sung region back out of it
            // two copies were exact complements which was only necessary because letters
            // being lifted off their own positions and exposing the dim copy underneath
            // sweep as a pure gradient the two register perfectly and opaque white
            // what is beneath it — so this needs no offscreen layer and no second mask
            Text(
                text = text,
                style = style,
                color = baseColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                onTextLayout = { layoutHolder[0] = it },
            )

            // bright copy revealed up to the head
            Text(
                text = text,
                style = style,
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                modifier = Modifier
                    // the dstin below needs a layer of its own to act on — the wave's layer now
                    // sits outside this box so it can't serve double duty declaring it here
                    // rather than opening a manual savelayer inside the draw lambda means the
                    // compositor owns and recycles the buffer instead of one being allocated and
                    // torn down every frame
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f) return@drawWithContent
                        val len = text.length
                        if (len == 0) return@drawWithContent

                        val headOffset = h.toInt().coerceIn(0, len)
                        // sub-character interpolation so the edge glides between glyphs rather
                        // than stepping from one to the next
                        val headX = if (headOffset >= len) {
                            layout.getLineRight(layout.getLineForOffset(len - 1))
                        } else {
                            val x0 = layout.getHorizontalPosition(headOffset, true)
                            val x1 = layout.getHorizontalPosition(
                                (headOffset + 1).coerceAtMost(len),
                                true,
                            )
                            // at a wrap x1 jumps back to the left margin; clamping stops the edge
                            // sliding backwards across the line break
                            if (x1 >= x0) x0 + (x1 - x0) * (h - headOffset) else x0
                        }
                        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(len - 1))
                        val lineTop = layout.getLineTop(headLine)
                        val lineBottom = layout.getLineBottom(headLine)

                        // the boundary trails behind the head: the letters just sung are the ones
                        // part-way lit and nothing ahead of the voice brightens early
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

                        // soften the leading edge confined to the head's own line: a horizontal
                        // gradient spanning the whole node would also fade every fully-sung line
                        // above it wherever their glyphs happened to sit to the right of the head
                        // skipped once the line is fully sung so the last letter isn't left faded
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

// a lyric line split into the lead vocal and whatever was bracketed off as a
internal class LyricLineParts(
    val lead: String,
    val backing: String?,
    val leadWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
    val backingWords: List<com.example.musicfy.lyrics.WordTimestamp>?,
)

// assigns each word timing to the half of the line it actually came from handing
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
        // classified on its first character: a word never straddles a bracket
        // bracket itself is a delimiter
        if (inBracket.getOrElse(start) { false }) backing.add(word) else lead.add(word)
    }
    return lead to backing
}

// pulls every bracketed aside out of a lyric line providers deliver backing
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
    // an unclosed bracket means the line isn't really structured this way; leave
    // rather than silently swallowing the rest of it
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

// a backing vocal: small and it rises into place out of the line above rather
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
    // expansion is driven by the aside's own word timings not by the line's

    // it used to follow the line's wave amplitude and an aside is almost always
    // of its line — so the line began retreating while the aside was still
    // collapsed out from under it that is the "stuck" highlight: the sweep was
    // container was closing on top of it
    val expand = rememberBackingExpand(words, positionProvider, expandProvider)

    Box(
        modifier = Modifier
            .clipToBounds()
            // the height is animated in the layout pass not faked with a translation

            // a graphicslayer translation is paint-only: the row still reserved the
            // height at all times so every line that happened to have one sat further
            // neighbours than the lines around it — the spacing that jumps from lyric to
            // and the text stayed on screen on inactive lines because there was nothing
            // go away reporting a collapsing height instead means the row genuinely
            // nothing and the list closes the gap as it does

            // the measurement happens at full size and only the reported height shrinks
            // text never re-wraps while it animates
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val e = expand.floatValue.coerceIn(0f, 1f)
                val height = (placeable.height * e).roundToInt()
                layout(placeable.width, height) {
                    // anchoring the text's bottom to the bottom of the collapsing box with the
                    // clip above makes it descend out from underneath the lead line rather than
                    // fade in beside it
                    placeable.place(0, if (slide) height - placeable.height else 0)
                }
            }
            .graphicsLayer { alpha = expand.floatValue.coerceIn(0f, 1f) },
    ) {
        // the aside sweeps too it is a sung line like any other — the provider's
        // the bracketed words are still in this entry's list and karaokesweeptext
        // searching for each word inside the text it was handed so passing the whole
        // gives this half exactly the timings that belong to it
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

// lead-in before the aside's first word and tail after its last in seconds
private const val BackingLeadInSec = 0.35
private const val BackingTailSec = 0.6

// how open the backing vocal's box should be from its own timings opens a beat
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

// smoothstep removes the corners a linear ramp leaves at both ends of a transition
private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

// builds the wave [modifier] for one copy of the line both the dim and the bright
@Composable
private fun rememberWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,
    // 01 master amplitude so the wave can retract instead of being switched off
    amplitudeProvider: () -> Float,
    // eight bloom taps instead of four — rounder halo roughly double the fragment cost
    highBloom: Boolean,
): Modifier {
    // null below api 33 — agsl didn't exist before tiramisu there the line
    // the wave which is a missing flourish rather than a broken page
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(WaveAgsl)
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    // one reusable slot per line holding the last effect and the quantised
    // from rendereffect is immutable so an animated shader has to make a new one
    // the picture would actually differ rebuilding unconditionally meant a fresh
    // frame per layer per active line same trick as blureffectforradius above
    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        // cut off well before zero below a fiftieth of full strength the
        // fraction of a pixel and the bloom is invisible but the shader pass costs
        // same — so every line still finishing its 600ms retract was paying full
        // something nobody can see
        if (layout == null || textLength == 0 || h <= 0f || amp <= WaveCutoff) {
            // dropping the effect entirely once the amplitude reaches zero also drops
            // shader cost for every line that isn't being sung
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

        // span converted from characters to px off this line's own metrics so the
        // same number of letters whatever the text or the font is doing
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

        // quantised so a sub-pixel head movement doesn't mint a new effect at 120hz
        // sweep crosses half a pixel every few frames so this drops allocation by
        // order of magnitude without any visible stepping
        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue)
    }
}

// amplitude below which the wave is dropped entirely rather than rendered faintly
private const val WaveCutoff = 0.02f

// quantisation grids for the wave's uniforms — see [waveeffectcache]
private const val WaveHeadStepPx = 0.5f
private const val WaveAmpStep = 0.02f

// holds one line's wave rendereffect and rebuilds it only when the picture would
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

// sweep position plus the two per-word quantities the wave and the bloom are driven by
@androidx.compose.runtime.Stable
internal class KaraokeHead {
    // how far through the line the sweep has reached in fractional character offsets
    val offset = mutableFloatStateOf(0f)

    // 01 — bloom intensity driven by the current word's seconds-per-character and
    val glow = mutableFloatStateOf(0f)
}

// seconds per character at which a word counts as rattled off (no bloom) versus
private const val FastSecPerChar = 0.1f
private const val SlowSecPerChar = 0.34f

// fractions of a word's duration the bloom spends swelling and releasing
private const val GlowAttackFraction = 0.35f
private const val GlowReleaseFraction = 0.3f

// one word's timing paired with the character range it occupies in the rendered line
private class TimedRange(
    val start: Int,
    val end: Int,
    val startTime: Double,
    val endTime: Double,
)

// how many characters of [text] are sung at [positionms] plain function no frame
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

// splits [text] at [filledchars] into a highlighted prefix and a dimmed remainder
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

// tracks how far through the line playback is in fractional character offsets
@Composable
private fun rememberKaraokeHead(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
): KaraokeHead {
    // words matched to their character offsets once timing data that doesn't
    // the line is dropped here rather than left in the list so the frame loop
    // array and never has to skip holes
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
        // playerconnectionprogressstate is a 15hz ticker (delay(66l)) sampling it
        // displayed frame does not make the sweep smooth — at 120hz the same value
        // eight times in a row so the fill still advances in 66ms steps that
        // the rendering is what looked like lag even after the draw-phase rewrite

        // so the position is extrapolated between ticks: remember when a new value
        // the wall-clock time elapsed since playback is linear between ticks so this
        // and every real tick re-anchors it
        var lastTick = -1L
        var lastTickAtMs = 0L
        while (true) {
            withFrameMillis { frameMs ->
                val tick = positionProvider()
                if (tick != lastTick) {
                    lastTick = tick
                    lastTickAtMs = frameMs
                }
                // capped so a paused or stalled player can't let the estimate run away; one
                // interval of slack bridges the gap and it self-corrects on the next
                val elapsed = (frameMs - lastTickAtMs).coerceIn(0L, 120L)
                val positionSec = (tick + elapsed) / 1000.0

                var next = 0f
                var nextGlow = 0f
                for (index in timed.indices) {
                    val word = timed[index]
                    if (positionSec < word.startTime) break

                    if (positionSec >= word.endTime) {
                        next = word.end.toFloat()
                        // coast across the silence into the next word instead of parking on the
                        // last character and then jumping the sweep never fully stops which is
                        // what makes the effect read as continuous rather than as a series of
                        // separate word animations that stall between each other
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

                    // bloom only on words the singer actually holds measured per character so a
                    // short word stretched over a long note reaches full strength while a long
                    // word of the same duration doesn't
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
                // eased down rather than cut so the bloom fades out over the gap after a held
                // note instead of vanishing the instant the word's timestamp ends
                val glowNow = head.glow.floatValue
                val glowNext =
                    if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.12f
                if (glowNext != glowNow) head.glow.floatValue = glowNext
            }
        }
    }

    return head
}
