// lyricsinterludedotskt
// the three dot countdown shown in place of a lyric during an instrumental
// or any long pause between sung lines dots light one after another 1 → 2 →
// gap s duration so the wait reads as progress toward the next line rather
// having stalled

// sized and positioned to occupy a lyric line s own slot in the list so the
// don t jump when it appears and disappears

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

// below this a gap isn t worth interrupting the lyric flow for
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
            // each dot owns an equal slice of the gap and fills across its own slice so
            // three light in sequence instead of all reacting to the same overall
            val sliceStart = index.toFloat() / DotCount
            val fill = ((progress - sliceStart) * DotCount).coerceIn(0f, 1f)

            val alpha by animateFloatAsState(
                targetValue = 0.25f + (0.75f * fill),
                animationSpec = tween(220),
                label = "interludeDotAlpha",
            )
            // a gentle swell as each dot lands gives the sequence a pulse rather than
            // static circles changing opacity
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
