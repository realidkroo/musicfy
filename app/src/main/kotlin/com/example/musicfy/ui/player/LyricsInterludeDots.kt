// LyricsInterludeDots.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val DotCount = 3

const val MinInterludeGapMs = 4_000L

@Composable
fun LyricsInterludeDots(
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val span = (endMs - startMs).coerceAtLeast(1L)
    val progress = ((positionMs - startMs).toFloat() / span).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(DotCount) { index ->

            val sliceStart = index.toFloat() / DotCount
            val fill = ((progress - sliceStart) * DotCount).coerceIn(0f, 1f)

            val alpha by animateFloatAsState(
                targetValue = 0.25f + (0.75f * fill),
                animationSpec = tween(220),
                label = "interludeDotAlpha",
            )

            val scale by animateFloatAsState(
                targetValue = 0.8f + (0.35f * fill),
                animationSpec = tween(220),
                label = "interludeDotScale",
            )

            Spacer(
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(if (fill > 0f) accentColor else Color.White)
            )
        }
    }
}
