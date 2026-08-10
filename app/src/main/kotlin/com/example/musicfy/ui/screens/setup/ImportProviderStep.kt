package com.example.musicfy.ui.screens.setup

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
                    painter = painterResource(R.drawable.musicfy_icon),
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
 */
@Composable
private fun WiggleArrow(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val strokeWidth = (h * 0.13f).coerceAtLeast(2f)

        // Wave occupies the middle stretch; the tail and the head sit on the baseline.
        val waveStart = w * 0.05f
        val waveEnd = w * 0.72f
        val amplitude = h * 0.42f
        val waveWidth = waveEnd - waveStart
        val halfWave = waveWidth / 4f

        val path = Path().apply {
            moveTo(waveStart, midY)
            var x = waveStart
            var up = true
            repeat(4) {
                val nextX = x + halfWave
                val peakY = if (up) midY - amplitude else midY + amplitude
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
