package com.example.musicfy.ui.screens.setup

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

@Composable
fun ImportProviderStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Tune My Music ──wiggle──> musicfy, mirroring the concept art.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.tune_my_music),
                contentDescription = "Tune My Music",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            WiggleArrow(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(44.dp)
                    .height(22.dp)
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = "musicfy",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Import data from other music provider",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 28.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Clicking continue will bring you how to import your music profile, playlist and album data from other music provider! this service is provided by third party and you must have existing account and may need an active subscription to that app ( ex. Apple music )",
            fontSize = 15.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 21.sp
        )
    }
}

/**
 * The hand-drawn squiggle-with-arrowhead from the concept: a flat lead-in, two waves, then a
 * short straight run into the head. Drawn rather than shipped as a vector so it scales with the
 * row and picks up the text colour.
 *
 * The wave itself continuously ripples left-to-right, toward the musicfy icon it points at — a
 * small bit of life that reinforces "your data is travelling this direction" instead of sitting
 * as a static squiggle between the two app icons.
 *
 * Each of the four bumps gets its own amplitude driven by the SAME looping [phase], just sampled
 * at a different offset — so the peaks swell and settle in sequence, left bump first, reading as
 * one ripple travelling along the path rather than four things independently pulsing. Only the
 * peak height (the cubic's control-point Y) is scaled; every segment's start/end point is always
 * exactly (x, midY), so the curve can never show a seam no matter what the amplitude is doing —
 * unlike shifting the wave's x-phase, which would fight the fixed start/end anchors instead.
 *
 * [phase] runs 0→1 on a plain linear loop (tween + RepeatMode.Restart): because the driving
 * function is `sin(2*PI*phase - offset)`, phase 0 and phase 1 land on the exact same value, so the
 * restart is invisible — the ripple reads as one continuous, endless flow rather than a
 * cycle-and-snap-back.
 */
@Composable
private fun WiggleArrow(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wiggleArrow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wiggleArrowPhase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val strokeWidth = (h * 0.13f).coerceAtLeast(2f)

        // Wave occupies the middle stretch; the tail and the head sit on the baseline.
        val waveStart = w * 0.05f
        val waveEnd = w * 0.72f
        // A touch taller than the original static wave, and the per-bump travel below pushes it
        // higher still at each bump's own peak moment — "more wiggle" without the baseline ever
        // looking cramped.
        val amplitude = h * 0.48f
        val waveWidth = waveEnd - waveStart
        val halfWave = waveWidth / 4f
        val twoPi = (2.0 * Math.PI).toFloat()

        val path = Path().apply {
            moveTo(waveStart, midY)
            var x = waveStart
            var up = true
            repeat(4) { bumpIndex ->
                val nextX = x + halfWave
                // Never fully flat, never doubled — a smooth 0.55..1.15 envelope so the ripple is
                // always visibly moving without any bump vanishing to a flat line or overshooting
                // into a spike.
                val travel = 0.55f + 0.60f * ((1f + kotlin.math.sin(twoPi * phase - bumpIndex * 0.9f)) / 2f)
                val peakY = if (up) midY - amplitude * travel else midY + amplitude * travel
                cubicTo(
                    x + halfWave * 0.5f, peakY,
                    nextX - halfWave * 0.5f, peakY,
                    nextX, midY
                )
                x = nextX
                up = !up
            }
            lineTo(w - strokeWidth, midY)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Arrowhead
        val headSize = h * 0.34f
        val tipX = w - strokeWidth / 2f
        drawPath(
            path = Path().apply {
                moveTo(tipX - headSize, midY - headSize)
                lineTo(tipX, midY)
                lineTo(tipX - headSize, midY + headSize)
            },
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
