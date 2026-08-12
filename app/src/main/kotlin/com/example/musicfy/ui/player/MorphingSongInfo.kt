// MorphingSongInfo.kt

package com.example.musicfy.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.player.models.TrackInfo

private val TargetX = 36.dp + 60.dp + 18.dp

private val CollapsedTitleSize = 18.sp
private val CollapsedArtistSize = 14.sp

@Composable
fun MorphingSongInfo(
    trackInfo: TrackInfo,
    lyricsProgressProvider: () -> Float,
    sourceRectProvider: () -> Rect?,
    targetY: Dp,
    modifier: Modifier = Modifier,
) {
    val source = sourceRectProvider() ?: return

    val lp = lyricsProgressProvider()
    if (lp <= 0f) return

    val density = LocalDensity.current
    val targetXPx = with(density) { TargetX.toPx() }
    val targetYPx = with(density) { targetY.toPx() }

    val widthDp = with(density) { source.width.toDp() }

    val titleSize = lerp(MaterialTheme.typography.titleLarge.fontSize, CollapsedTitleSize, lp)
    val artistSize = lerp(MaterialTheme.typography.titleMedium.fontSize, CollapsedArtistSize, lp)

    Column(
        modifier = modifier
            .width(widthDp)
            .graphicsLayer {

                alpha = (lp / 0.35f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = androidx.compose.ui.util.lerp(source.left, targetXPx, lp)
                translationY = androidx.compose.ui.util.lerp(source.top, targetYPx, lp)
            },
    ) {

        Column(
            modifier = Modifier
                .widthIn(max = maxTextWidth)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        0.86f to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                }
        ) {
            Text(
                text = trackInfo.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize),
                fontWeight = FontWeight.Bold,
                maxLines = 1,

                softWrap = false,
                color = Color.White,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2500,
                    repeatDelayMillis = 2500,
                    velocity = 26.dp,
                ),
            )
            if (trackInfo.artist.isNotBlank()) {
                Text(
                    text = trackInfo.artist,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = artistSize),
                    maxLines = 1,
                    softWrap = false,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2500,
                        repeatDelayMillis = 2500,
                        velocity = 26.dp,
                    ),
                )
            }
        }
    }
}

private val maxTextWidth = 210.dp
