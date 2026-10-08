// StatsCharts.kt
//
// The Stats page's charts, drawn on Canvas: a line (listening over days, months or hours), weekday
// bars, the genre donut and a ranked list. One series each, so no legend boxes - the card title
// names it. Marks stay thin, the grid recedes, text keeps text colours, and the one accent colour
// marks the peak. Press and drag to read any point.

package com.example.musicfy.ui.screens.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.resize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal val StatsAccent = Color(0xFF88FF76)
internal val StatsInk = Color.White
internal val StatsInkSecondary = Color(0xFFB8B8B8)
internal val StatsInkMuted = Color(0xFF7A7A7A)
private val Grid = Color(0xFF242424)
private val BarRest = Color(0xFF3A3A3A)
private val TooltipBg = Color(0xFF2C2C2C)

/** Categorical order for genres on the dark card (validated: CVD ΔE ≥ 8.4, normal ≥ 19.3 adjacent). */
internal val GenreColors = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
internal val GenreOther = Color(0xFF5A5A5A)

private val AxisStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = StatsInkMuted)
private val TipStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = StatsInk)

/** "3h 05m" for a tooltip, "45m" when under an hour. */
internal fun shortDuration(ms: Long): String {
    val m = (ms / 60_000L).coerceAtLeast(0L)
    return if (m >= 60) "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" else "${m}m"
}

/** Grows in once, the first time it's shown. */
@Composable
private fun rememberGrowIn(key: Any?): Animatable<Float, *> {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(900, easing = Emphasized)) }
    return a
}

/**
 * Listening over time as a line with a soft area under it. [labelAt] names the ticks it returns
 * non-null for; [nameOf] titles a point in the scrub tooltip.
 */
@Composable
internal fun ListeningLine(
    values: List<Long>,
    labelAt: (Int) -> String?,
    nameOf: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val grow = rememberGrowIn(values)
    var touched by remember(values) { mutableIntStateOf(-1) }
    val peak = values.indices.maxByOrNull { values[it] } ?: -1
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .scrub(values.size) { touched = it },
    ) {
        if (values.isEmpty()) return@Canvas
        val top = 22.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val maxV = max(values.max(), 60_000L).toFloat()
        val step = if (values.size > 1) size.width / (values.size - 1) else size.width
        fun pt(i: Int) = Offset(i * step, bottom - (values[i] / maxV) * (bottom - top))

        // recessive grid: baseline and half
        drawLine(Grid, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        drawLine(Grid, Offset(0f, (top + bottom) / 2f), Offset(size.width, (top + bottom) / 2f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawAxisText(measurer, shortDuration(maxV.toLong()), Offset(0f, top - 16.dp.toPx()))

        val line = Path()
        val area = Path()
        values.indices.forEach { i ->
            val p = pt(i)
            if (i == 0) {
                line.moveTo(p.x, p.y)
                area.moveTo(p.x, bottom)
                area.lineTo(p.x, p.y)
            } else {
                // a gentle curve through the points, never overshooting them
                val prev = pt(i - 1)
                val mid = (prev.x + p.x) / 2f
                line.cubicTo(mid, prev.y, mid, p.y, p.x, p.y)
                area.cubicTo(mid, prev.y, mid, p.y, p.x, p.y)
            }
        }
        area.lineTo((values.size - 1) * step, bottom)
        area.close()

        clipRect(right = size.width * grow.value) {
            drawPath(area, Brush.verticalGradient(listOf(StatsAccent.copy(alpha = 0.22f), StatsAccent.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(line, StatsAccent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        values.indices.forEach { i ->
            labelAt(i)?.let { drawAxisText(measurer, it, Offset(i * step, bottom + 4.dp.toPx()), center = true) }
        }

        val shown = if (touched in values.indices) touched else peak
        if (shown in values.indices && grow.value > 0.98f) {
            val p = pt(shown)
            if (touched >= 0) drawLine(StatsInkMuted, Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(Color(0xFF0F0F0F), radius = 6.dp.toPx(), center = p)
            drawCircle(StatsAccent, radius = 4.dp.toPx(), center = p)
            drawTooltip(measurer, "${nameOf(shown)} · ${shortDuration(values[shown])}", p)
        }
    }
}

/** Seven bars, Monday first; the busiest day in the accent, the rest neutral. */
@Composable
internal fun WeekdayBars(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val grow = rememberGrowIn(values)
    var touched by remember(values) { mutableIntStateOf(-1) }
    val peak = values.indices.maxByOrNull { values[it] } ?: -1
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .scrub(values.size, discrete = true) { touched = it },
    ) {
        if (values.isEmpty()) return@Canvas
        val top = 24.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val maxV = max(values.max(), 60_000L).toFloat()
        val slot = size.width / values.size
        val barW = min(slot * 0.46f, 22.dp.toPx())
        val r = CornerRadius(4.dp.toPx())
        drawLine(Grid, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * (bottom - top) * grow.value
            val x = slot * i + (slot - barW) / 2f
            val color = if (i == peak) StatsAccent else BarRest
            if (h > 0.5f) {
                // rounded only at the data end; the base sits square on the baseline
                drawRoundRect(color, Offset(x, bottom - h), Size(barW, h), r)
                drawRect(color, Offset(x, bottom - min(h, r.y)), Size(barW, min(h, r.y)))
            }
            drawAxisText(measurer, labels[i], Offset(slot * i + slot / 2f, bottom + 4.dp.toPx()), center = true)
        }
        val shown = if (touched in values.indices) touched else peak
        if (shown in values.indices && grow.value > 0.98f) {
            val h = (values[shown] / maxV) * (bottom - top)
            drawTooltip(measurer, shortDuration(values[shown]), Offset(slot * shown + slot / 2f, bottom - h))
        }
    }
}

/** The genre ring: top five in categorical order, the rest folded into "Other", 2dp gaps between. */
@Composable
internal fun GenreDonut(shares: List<GenreShare>, modifier: Modifier = Modifier) {
    val grow = rememberGrowIn(shares)
    val slices = remember(shares) { donutSlices(shares) }
    Canvas(modifier = modifier.size(92.dp)) {
        val stroke = 13.dp.toPx()
        val gap = 2.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2f
        val gapDeg = Math.toDegrees((gap / radius).toDouble()).toFloat()
        var start = -90f
        val total = 360f * grow.value
        slices.forEach { (fraction, color) ->
            val sweep = fraction * total
            if (sweep > gapDeg) {
                drawArc(
                    color = color,
                    startAngle = start + gapDeg / 2f,
                    sweepAngle = sweep - gapDeg,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            start += sweep
        }
    }
}

internal fun donutSlices(shares: List<GenreShare>): List<Pair<Float, Color>> {
    val top = shares.take(GenreColors.size)
    val rest = shares.drop(GenreColors.size).sumOf { it.fraction.toDouble() }.toFloat()
    return top.mapIndexed { i, g -> g.fraction to GenreColors[i] } + if (rest > 0.001f) listOf(rest to GenreOther) else emptyList()
}

/** Ranked rows - number, picture, name, detail, and a share bar against the #1. */
@Composable
internal fun RankList(
    rows: List<RankRow>,
    circle: Boolean,
    modifier: Modifier = Modifier,
) {
    val best = rows.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { i, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${i + 1}".padStart(2, '0'),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = StatsInkMuted),
                    modifier = Modifier.width(28.dp),
                )
                if (row.image != null || !row.noImage) {
                    AsyncImage(
                        model = row.image?.resize(160, 160),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(Color(0xFF2A2A2A)),
                        error = ColorPainter(Color(0xFF2A2A2A)),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(if (circle) CircleShape else RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                } else if (row.swatch != null) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(row.swatch))
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = StatsInk),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.detail,
                        style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = StatsInkMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Grid),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((row.value.toFloat() / best).coerceIn(0.02f, 1f))
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) StatsAccent else StatsInkSecondary.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
        }
    }
}

internal class RankRow(
    val title: String,
    val detail: String,
    val value: Long,
    val image: String? = null,
    val swatch: Color? = null,
    val noImage: Boolean = false,
)

// --- drawing helpers -------------------------------------------------------------------------

private fun DrawScope.drawAxisText(measurer: TextMeasurer, text: String, at: Offset, center: Boolean = false) {
    val layout = measurer.measure(text, AxisStyle, softWrap = false, maxLines = 1)
    val x = if (center) (at.x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width) else at.x
    drawText(layout, topLeft = Offset(x, at.y))
}

private fun DrawScope.drawTooltip(measurer: TextMeasurer, text: String, anchor: Offset) {
    val layout = measurer.measure(text, TipStyle, softWrap = false, maxLines = 1)
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = (anchor.x - w / 2f).coerceIn(0f, size.width - w)
    val y = (anchor.y - h - 8.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(TooltipBg, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
    drawText(layout, topLeft = Offset(x + padH, y + padV))
}

/** Press-and-drag to pick the nearest point (or bar); lifting the finger lets go of it. */
private fun Modifier.scrub(count: Int, discrete: Boolean = false, onIndex: (Int) -> Unit): Modifier = pointerInput(count) {
    if (count == 0) return@pointerInput
    fun indexAt(x: Float): Int = if (discrete) {
        (x / (size.width.toFloat() / count)).toInt().coerceIn(0, count - 1)
    } else {
        (x / (size.width.toFloat() / max(1, count - 1))).roundToInt().coerceIn(0, count - 1)
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        onIndex(indexAt(down.position.x))
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            onIndex(indexAt(change.position.x))
        }
        onIndex(-1)
    }
}
