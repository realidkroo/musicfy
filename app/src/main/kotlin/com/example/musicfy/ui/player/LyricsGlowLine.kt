// LyricsGlowLine.kt

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
    return android.graphics.RenderEffect
        .createBlurEffect(quantised, quantised, android.graphics.Shader.TileMode.CLAMP)
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

            .padding(vertical = 16.dp, horizontal = 36.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)

                renderEffect = if (suppressEffects) null else blurEffectForRadius(blurRadius)
            }
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
                baseColor = textColor.copy(alpha = ActiveUnsungAlpha),

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

                slide = leadText.isNotEmpty(),
                words = parts.backingWords,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                sweep = sweepActive,

                expandProvider = { waveAmplitude },
                highBloom = highBloom,
            )
        }

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

private const val EdgeSoftChars = 1

private const val EdgeSoftPx = 26f

private const val EdgeLeadPx = 6f

private const val WaveCharsBehind = 4f
private const val WaveCharsAhead = 3f

private const val WaveLiftPx = 4.5f

private const val WaveZoom = 0.09f

private const val BloomRadiusPx = 2.6f

private const val BloomStrength = 1.6f

private const val BloomLagChars = 1.6f
private const val BloomSpanChars = 3.5f

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
        // the bloom gets its own bell wider than the lift s and centred behind the
        // than on it sharing the lift s bell put the glow exactly where the sweep s
        // boundary is the one place the lit copy is half transparent and the dim
        // dim so it landed on the faintest pixels on screen and was invisible
        float bdx = coord.x - bloomX;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;

        // four diagonal taps always the axis aligned four only on the high setting
        // alone still read as a halo they just make it very slightly less round
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

    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }

    val sung = remember(text) { Path() }

    val wave = rememberWaveModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)

    Column {
        Box(modifier = wave) {

            Text(
                text = text,
                style = style,
                color = baseColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                onTextLayout = { layoutHolder[0] = it },
            )

            Text(
                text = text,
                style = style,
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
                modifier = Modifier

                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f) return@drawWithContent
                        val len = text.length
                        if (len == 0) return@drawWithContent

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
                        val headLine = layout.getLineForOffset(headOffset.coerceAtMost(len - 1))
                        val lineTop = layout.getLineTop(headLine)
                        val lineBottom = layout.getLineBottom(headLine)

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
        leadWords = leadWords.ifEmpty { null },
        backingWords = backingWords.ifEmpty { null },
    )
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
) {

    val expand = rememberBackingExpand(words, positionProvider, expandProvider)

    Box(
        modifier = Modifier
            .clipToBounds()

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
                val next = maxOf(own, lineAmplitudeProvider().coerceIn(0f, 1f))
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

@Composable
private fun rememberWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,

    amplitudeProvider: () -> Float,

    highBloom: Boolean,
): Modifier {

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(WaveAgsl)
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

        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue)
    }
}

private const val WaveCutoff = 0.02f

private const val WaveHeadStepPx = 0.5f
private const val WaveAmpStep = 0.02f

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

@androidx.compose.runtime.Stable
internal class KaraokeHead {

    val offset = mutableFloatStateOf(0f)

    val glow = mutableFloatStateOf(0f)
}

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

    val head = remember(text, words) { KaraokeHead() }

    LaunchedEffect(text, timed, positionProvider) {

        var lastTick = -1L
        var lastTickAtMs = 0L
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
                    next = word.start + (length * within).toFloat()

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

                val glowNow = head.glow.floatValue
                val glowNext =
                    if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.12f
                if (glowNext != glowNow) head.glow.floatValue = glowNext
            }
        }
    }

    return head
}
