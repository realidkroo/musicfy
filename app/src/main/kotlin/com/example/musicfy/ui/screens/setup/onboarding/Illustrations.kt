// Illustrations.kt
//
// The crossfade banner on the "Last one" page.

package com.example.musicfy.ui.screens.setup.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Two tracks trading places: the outgoing one's waveform shrinks as the incoming one's grows,
 * under the two gain curves crossing in the middle.
 */
@Composable
internal fun CrossfadeBanner(modifier: Modifier = Modifier) {
    // a still picture: fixed waveform shapes, nothing animating
    val phase = 0.9f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(124.dp)
            .border(1.dp, Color(0xFF5A5A5A), RoundedCornerShape(14.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 30.dp)) {
            val w = size.width
            val h = size.height
            val mid = h / 2f
            val bars = 64
            val gap = w / bars
            for (i in 0 until bars) {
                val x = i * gap + gap / 2f
                val t = i / (bars - 1f)
                // outgoing loud on the left, incoming loud on the right, both quiet in the overlap
                val outgoing = (1f - t) * (1f - t)
                val incoming = t * t
                val wobble = 0.55f + 0.45f * abs(sin(i * 1.7f + phase * (if (i % 2 == 0) 1f else -1f)))
                val amp = (maxOf(outgoing, incoming) * 0.9f + 0.08f) * wobble * mid
                drawLine(
                    Color.White.copy(alpha = 0.55f + 0.4f * maxOf(outgoing, incoming)),
                    Offset(x, mid - amp),
                    Offset(x, mid + amp),
                    strokeWidth = gap * 0.42f,
                    cap = StrokeCap.Round,
                )
            }
            val curveStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            val fadeOut = Path().apply {
                moveTo(0f, 0f)
                cubicTo(w * 0.45f, 0f, w * 0.55f, h, w, h)
            }
            val fadeIn = Path().apply {
                moveTo(0f, h)
                cubicTo(w * 0.45f, h, w * 0.55f, 0f, w, 0f)
            }
            drawPath(fadeOut, Color(0xFF9A9A9A), style = curveStroke)
            drawPath(fadeIn, Color(0xFF9A9A9A), style = curveStroke)
        }
        Text(
            "ei i i eii",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 40.dp),
        )
        Text(
            "i miss you",
            color = Color(0xFF9A9A9A),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp, start = 40.dp),
        )
    }
}
