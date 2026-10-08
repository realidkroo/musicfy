// IdCardBack.kt
//
// The back of the profile ID card: a real Code 128 barcode (a phone camera reads it), hidden under
// a silver scratch-off strip, and the green scanner sweep that "reads" it before the recap opens.

package com.example.musicfy.ui.screens.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.screens.setup.onboarding.IdCardAspect
import com.example.musicfy.ui.screens.setup.onboarding.MarqueeStrip
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal enum class ScanVerdict { Valid, Invalid }

internal val ScanGreen = Color(0xFF52FF7A)
internal val ScanRed = Color(0xFFFF4D4D)

/** What the barcode on the back of the card says. Short, so the bars stay wide enough to scan. */
internal fun recapBarcodePayload(range: StatsRange?): String =
    if (range == null) "MFY-NOTYET" else "MFY%04d-%02d".format(range.year, range.month)

/** The human-readable line under the bars, spaced out like the mock's. */
internal fun recapBarcodeCaption(payload: String, cardNumber: String): String =
    payload.toList().joinToString(" ") + "  " + cardNumber.takeLast(8).chunked(4).joinToString(" ")

/** Scratch-off state: the path scratched so far and how much of the strip it has cleared. */
@Stable
internal class ScratchState {
    val path = Path()
    var version by mutableIntStateOf(0)
        private set

    private val cells = BooleanArray(GridCols * GridRows)
    private var clearedCount = 0
    val revealed: Float get() = clearedCount.toFloat() / cells.size

    /** 1 once the leftover coating has dissolved away. */
    val dissolve = Animatable(0f)

    /** Bits of coating flicked off by the finger: x, y, vx, vy, birth (seconds), size. */
    val flakes = ArrayList<FloatArray>()

    private var last: Offset? = null
    private val random = Random(7)

    fun begin(point: Offset, size: Size, brush: Float, now: Float) {
        last = point
        path.moveTo(point.x, point.y)
        path.lineTo(point.x + 0.1f, point.y)
        mark(point, size, brush)
        spawnFlakes(point, now, 3)
        version++
    }

    fun moveTo(point: Offset, size: Size, brush: Float, now: Float) {
        val from = last ?: return begin(point, size, brush, now)
        path.lineTo(point.x, point.y)
        // mark the cells along the segment, not just its end, so fast strokes count fully
        val steps = (hypot(point.x - from.x, point.y - from.y) / (brush * 0.4f)).toInt().coerceIn(1, 40)
        for (s in 1..steps) mark(from + (point - from) * (s.toFloat() / steps), size, brush)
        spawnFlakes(point, now, 1 + random.nextInt(2))
        last = point
        version++
    }

    fun end() {
        last = null
    }

    private fun mark(p: Offset, size: Size, brush: Float) {
        if (size.width <= 0f || size.height <= 0f) return
        val cw = size.width / GridCols
        val ch = size.height / GridRows
        val r = brush / 2f
        val c0 = ((p.x - r) / cw).toInt().coerceIn(0, GridCols - 1)
        val c1 = ((p.x + r) / cw).toInt().coerceIn(0, GridCols - 1)
        val r0 = ((p.y - r) / ch).toInt().coerceIn(0, GridRows - 1)
        val r1 = ((p.y + r) / ch).toInt().coerceIn(0, GridRows - 1)
        for (row in r0..r1) for (col in c0..c1) {
            val cx = (col + 0.5f) * cw
            val cy = (row + 0.5f) * ch
            if (hypot(cx - p.x, cy - p.y) <= r + min(cw, ch) * 0.5f) {
                val i = row * GridCols + col
                if (!cells[i]) {
                    cells[i] = true
                    clearedCount++
                }
            }
        }
    }

    private fun spawnFlakes(at: Offset, now: Float, count: Int) {
        repeat(count) {
            flakes += floatArrayOf(
                at.x, at.y,
                (random.nextFloat() - 0.5f) * 260f,
                -random.nextFloat() * 160f,
                now,
                2.5f + random.nextFloat() * 4f,
            )
        }
        if (flakes.size > 60) flakes.subList(0, flakes.size - 60).clear()
    }

    companion object {
        private const val GridCols = 28
        private const val GridRows = 8
    }
}

/**
 * The card's back face, upright (the flip that shows it is the caller's). [scratch] null = no
 * coating left. [scan] runs 0..1 for the scanner sweep; [verdict] colours its ending.
 */
@Composable
internal fun IdCardBack(
    username: String,
    payload: String,
    caption: String,
    scratch: ScratchState?,
    time: RecapTime,
    modifier: Modifier = Modifier,
    scan: () -> Float = { 0f },
    verdict: () -> ScanVerdict? = { null },
    onScratchProgress: (Float) -> Unit = {},
) {
    val name = username.trim().ifEmpty { "{username}" }
    val modules = remember(payload) { Code128.encode(payload) }
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(IdCardAspect)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF303030)),
    ) {
        val unit = maxWidth / 332f
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val captionStyle = remember(unit) {
                    TextStyle(color = Color(0xFFD9D9D9), fontFamily = FontFamily.Monospace, fontSize = (unit.value * 8.6f).sp)
                }
                val stampStyle = remember(unit) {
                    TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (unit.value * 22f).sp, letterSpacing = (unit.value * 3f).sp)
                }
                // bars + caption + the scanner on top of them
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val u = size.width / 292f
                    val bars = Rect(30f * u, 46f * u, size.width - 30f * u, 118f * u)
                    val s = scan()
                    val v = verdict()
                    drawBarcode(modules, bars, Color(0xFFD9D9D9))
                    val captionLayout = measurer.measure(caption, captionStyle, softWrap = false, maxLines = 1)
                    drawText(captionLayout, topLeft = Offset((size.width - captionLayout.size.width) / 2f, bars.bottom + 6f * u))
                    if (s > 0f) drawScanner(modules, bars, s, v, u)
                    if (v != null && s >= 0.86f) {
                        val p = easeOutBack(((s - 0.86f) / 0.14f).coerceIn(0f, 1f))
                        val label = if (v == ScanVerdict.Valid) "VERIFIED" else "INVALID"
                        val color = if (v == ScanVerdict.Valid) ScanGreen else ScanRed
                        val layout = measurer.measure(label, stampStyle.copy(color = color), softWrap = false, maxLines = 1)
                        val center = bars.center
                        drawContext.canvas.save()
                        drawContext.transform.rotate(-8f, center)
                        drawContext.transform.scale(1.6f - 0.6f * p, 1.6f - 0.6f * p, center)
                        val box = Rect(
                            center.x - layout.size.width / 2f - 10f * u, center.y - layout.size.height / 2f - 4f * u,
                            center.x + layout.size.width / 2f + 10f * u, center.y + layout.size.height / 2f + 4f * u,
                        )
                        drawRoundRect(Color(0xFF151515), box.topLeft, box.size, CornerRadius(8f * u), alpha = 0.92f * p.coerceIn(0f, 1f))
                        drawRoundRect(color, box.topLeft, box.size, CornerRadius(8f * u), style = Stroke(2f * u), alpha = p.coerceIn(0f, 1f))
                        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f), alpha = p.coerceIn(0f, 1f))
                        drawContext.canvas.restore()
                    }
                }
                if (scratch != null) {
                    ScratchCoating(
                        state = scratch,
                        time = time,
                        onProgress = { progress ->
                            onScratchProgress(progress)
                        },
                        onTick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                }
            }
            MarqueeStrip(
                text = "${name}`s CARD",
                color = Color(0xFF4B4B4B),
                background = Color(0xFF131313),
                modifier = Modifier
                    .width(unit * 40f)
                    .fillMaxHeight(),
                fontSizeSp = unit.value * 17f,
            )
        }
    }
}

/** The silver strip over the barcode. Scratched with a finger; dissolves once enough is gone. */
@Composable
private fun ScratchCoating(
    state: ScratchState,
    time: RecapTime,
    onProgress: (Float) -> Unit,
    onTick: () -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val microStyle = remember { TextStyle(color = Color(0xFF8E9096), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 2.sp) }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // the Clear blend needs its own buffer, and only while there's coating to clear
                compositingStrategy = CompositingStrategy.Offscreen
                val d = state.dissolve.value
                alpha = 1f - d
                scaleX = 1f + d * 0.06f
                scaleY = 1f + d * 0.06f
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val strip = stripRect(size.width.toFloat(), size.height.toFloat())
                    val brush = 26.dp.toPx()
                    val local = { p: Offset -> Offset(p.x - strip.left, p.y - strip.top) }
                    state.begin(local(down.position), strip.size, brush, time.sway)
                    down.consume()
                    var travelled = 0f
                    var previous = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        state.moveTo(local(change.position), strip.size, brush, time.sway)
                        travelled += (change.position - previous).getDistance()
                        previous = change.position
                        if (travelled > 22.dp.toPx()) {
                            travelled = 0f
                            onTick()
                        }
                        onProgress(state.revealed)
                    }
                    state.end()
                    onProgress(state.revealed)
                }
            },
    ) {
        state.version // redraw on every stroke
        val strip = stripRect(size.width, size.height)
        val r = CornerRadius(12f * size.width / 292f)
        val now = time.sway
        translate(strip.left, strip.top) {
            drawSilver(strip.size, r, now)
            // repeated micro text, like a lottery ticket's coating
            val micro = measurer.measure("SCRATCH · MUSICFY · ", microStyle, softWrap = false, maxLines = 1)
            clipRect(0f, 0f, strip.width, strip.height) {
                var y = strip.height * 0.18f
                var row = 0
                while (y < strip.height) {
                    var x = -((row * 37f) % micro.size.width)
                    while (x < strip.width) {
                        drawText(micro, topLeft = Offset(x, y - micro.size.height / 2f), alpha = 0.55f)
                        x += micro.size.width
                    }
                    y += strip.height * 0.32f
                    row++
                }
            }
            drawPath(
                path = state.path,
                color = Color.Black,
                style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Clear,
            )
            // flakes falling away from the finger
            val iter = state.flakes.iterator()
            while (iter.hasNext()) {
                val f = iter.next()
                val age = now - f[4]
                if (age > 0.7f || age < 0f) {
                    iter.remove()
                    continue
                }
                val x = f[0] + f[2] * age
                val y = f[1] + f[3] * age + 0.5f * 1400f * age * age
                drawRect(
                    Color(0xFFC9CBD0),
                    topLeft = Offset(x, y),
                    size = Size(f[5], f[5] * 0.7f),
                    alpha = (1f - age / 0.7f).coerceIn(0f, 1f),
                )
            }
        }
    }
}

/** Where the strip sits in the card body: over the bars and their caption. */
private fun stripRect(w: Float, h: Float): Rect {
    val u = w / 292f
    return Rect(18f * u, 36f * u, w - 18f * u, min(h - 14f * u, 142f * u))
}

private fun DrawScope.drawSilver(size: Size, radius: CornerRadius, now: Float) {
    drawRoundRect(
        Brush.linearGradient(
            0f to Color(0xFFB9BBC1),
            0.35f to Color(0xFFE6E7EB),
            0.55f to Color(0xFFA6A8AF),
            0.8f to Color(0xFFD5D7DC),
            1f to Color(0xFF9A9CA3),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        size = size,
        cornerRadius = radius,
    )
    // a highlight that keeps drifting across, inviting a finger
    val sweep = ((now * 0.45f) % 1.6f - 0.3f) * size.width
    drawRoundRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.5f),
            1f to Color.Transparent,
            start = Offset(sweep - size.width * 0.25f, 0f),
            end = Offset(sweep + size.width * 0.1f, size.height),
        ),
        size = size,
        cornerRadius = radius,
        blendMode = BlendMode.Screen,
    )
}

private fun DrawScope.drawBarcode(modules: BooleanArray, area: Rect, color: Color) {
    if (modules.isEmpty()) return
    val m = area.width / modules.size
    var i = 0
    while (i < modules.size) {
        if (!modules[i]) {
            i++
            continue
        }
        var j = i
        while (j < modules.size && modules[j]) j++
        drawRect(color, topLeft = Offset(area.left + i * m, area.top), size = Size((j - i) * m, area.height))
        i = j
    }
}

/**
 * Corner brackets close in, a green laser sweeps the bars twice lighting them up as it passes,
 * then the whole code flashes - green when it's good, red when it isn't.
 */
private fun DrawScope.drawScanner(modules: BooleanArray, bars: Rect, s: Float, verdict: ScanVerdict?, u: Float) {
    val tint = if (verdict == ScanVerdict.Invalid && s > 0.74f) ScanRed else ScanGreen
    val frame = Rect(bars.left - 10f * u, bars.top - 10f * u, bars.right + 10f * u, bars.bottom + 22f * u)

    // brackets
    val bp = easeOutCubic((s / 0.15f).coerceIn(0f, 1f))
    val grow = (1f - bp) * 14f * u
    val arm = 16f * u
    val stroke = 3f * u
    val corners = listOf(
        Offset(frame.left - grow, frame.top - grow) to Offset(1f, 1f),
        Offset(frame.right + grow, frame.top - grow) to Offset(-1f, 1f),
        Offset(frame.left - grow, frame.bottom + grow) to Offset(1f, -1f),
        Offset(frame.right + grow, frame.bottom + grow) to Offset(-1f, -1f),
    )
    corners.forEach { (c, d) ->
        drawLine(tint, c, Offset(c.x + arm * d.x, c.y), strokeWidth = stroke, cap = StrokeCap.Round, alpha = bp)
        drawLine(tint, c, Offset(c.x, c.y + arm * d.y), strokeWidth = stroke, cap = StrokeCap.Round, alpha = bp)
    }

    // the laser: down, then back up
    if (s in 0.15f..0.76f) {
        val t = (s - 0.15f) / 0.61f
        val pass = if (t < 0.5f) easeInOutCubic(t * 2f) else 1f - easeInOutCubic((t - 0.5f) * 2f)
        val y = lerpF(frame.top, frame.bottom, pass)
        val lit = if (t < 0.5f) Rect(bars.left, bars.top, bars.right, min(bars.bottom, y)) else bars
        clipRect(lit.left, lit.top, lit.right, max(lit.top, lit.bottom)) { drawBarcode(modules, bars, tint) }
        val trail = 26f * u * if (t < 0.5f) -1f else 1f
        drawRect(
            Brush.verticalGradient(listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.35f)), startY = y + trail, endY = y),
            topLeft = Offset(frame.left, min(y, y + trail)),
            size = Size(frame.width, kotlin.math.abs(trail)),
        )
        drawLine(tint, Offset(frame.left - 6f * u, y), Offset(frame.right + 6f * u, y), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(frame.left, y), Offset(frame.right, y), strokeWidth = 0.8f * u, alpha = 0.8f)
    } else if (s > 0.76f) {
        // the read: a flash of the whole code
        val f = ((s - 0.76f) / 0.1f).coerceIn(0f, 1f)
        drawBarcode(modules, bars, tint.copy(alpha = if (verdict == null) 1f - f * 0.5f else 1f))
        drawRect(tint, frame.topLeft, frame.size, alpha = 0.22f * (1f - f))
    }
}

/** Code 128, code set B: printable ASCII to a run of modules (true = bar), quiet zones not included. */
internal object Code128 {
    fun encode(text: String): BooleanArray {
        val values = ArrayList<Int>(text.length + 3)
        values += StartB
        var checksum = StartB
        text.filter { it.code in 32..126 }.forEachIndexed { i, ch ->
            val v = ch.code - 32
            values += v
            checksum += (i + 1) * v
        }
        values += checksum % 103
        val bits = ArrayList<Boolean>(values.size * 11 + 13)
        values.forEach { v ->
            val pattern = Patterns[v]
            for (b in 10 downTo 0) bits += (pattern shr b) and 1 == 1
        }
        for (b in 12 downTo 0) bits += (Stop shr b) and 1 == 1
        return bits.toBooleanArray()
    }

    private const val StartB = 104
    private const val Stop = 0b1100011101011

    private val Patterns = intArrayOf(
        0b11011001100, 0b11001101100, 0b11001100110, 0b10010011000, 0b10010001100, 0b10001001100, 0b10011001000, 0b10011000100,
        0b10001100100, 0b11001001000, 0b11001000100, 0b11000100100, 0b10110011100, 0b10011011100, 0b10011001110, 0b10111001100,
        0b10011101100, 0b10011100110, 0b11001110010, 0b11001011100, 0b11001001110, 0b11011100100, 0b11001110100, 0b11101101110,
        0b11101001100, 0b11100101100, 0b11100100110, 0b11101100100, 0b11100110100, 0b11100110010, 0b11011011000, 0b11011000110,
        0b11000110110, 0b10100011000, 0b10001011000, 0b10001000110, 0b10110001000, 0b10001101000, 0b10001100010, 0b11010001000,
        0b11000101000, 0b11000100010, 0b10110111000, 0b10110001110, 0b10001101110, 0b10111011000, 0b10111000110, 0b10001110110,
        0b11101110110, 0b11010001110, 0b11000101110, 0b11011101000, 0b11011100010, 0b11011101110, 0b11101011000, 0b11101000110,
        0b11100010110, 0b11101101000, 0b11101100010, 0b11100011010, 0b11101111010, 0b11001000010, 0b11110001010, 0b10100110000,
        0b10100001100, 0b10010110000, 0b10010000110, 0b10000101100, 0b10000100110, 0b10110010000, 0b10110000100, 0b10011010000,
        0b10011000010, 0b10000110100, 0b10000110010, 0b11000010010, 0b11001010000, 0b11110111010, 0b11000010100, 0b10001111010,
        0b10100111100, 0b10010111100, 0b10010011110, 0b10111100100, 0b10011110100, 0b10011110010, 0b11110100100, 0b11110010100,
        0b11110010010, 0b11011011110, 0b11011110110, 0b11110110110, 0b10101111000, 0b10100011110, 0b10001011110, 0b10111101000,
        0b10111100010, 0b11110101000, 0b11110100010, 0b10111011110, 0b10111101110, 0b11101011110, 0b11110101110, 0b11010000100,
        0b11010010000, 0b11010011100,
    )
}
