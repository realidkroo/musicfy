// MorphingSongInfo.kt

package com.example.musicfy.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
    /**
     * The player sheet's progress. With a page left open, the header title stays up while the
     * player closes and opens, and fades with the rest of the page then.
     */
    sheetProgressProvider: () -> Float = { 1f },
) {
    // Whether it can be seen at all. Decided in a derived state so the providers aren't read here:
    // the source rect moves with every frame of a sheet slide, and reading it in composition
    // recomposed this on every one of those frames.
    val shown by remember {
        derivedStateOf {
            lyricsProgressProvider() > 0f &&
                sheetFade(sheetProgressProvider()) > 0f &&
                sourceRectProvider() != null
        }
    }
    if (!shown) return
    val sourceWidth by remember { derivedStateOf { sourceRectProvider()?.width ?: 0f } }

    val lp = lyricsProgressProvider()

    val density = LocalDensity.current
    val targetXPx = with(density) { TargetX.toPx() }
    val targetYPx = with(density) { targetY.toPx() }

    val widthDp = with(density) { sourceWidth.toDp() }

    val titleSize = lerp(MaterialTheme.typography.titleLarge.fontSize, CollapsedTitleSize, lp)
    val artistSize = lerp(MaterialTheme.typography.titleMedium.fontSize, CollapsedArtistSize, lp)

    Column(
        modifier = modifier
            .width(widthDp)
            .graphicsLayer {
                val source = sourceRectProvider() ?: return@graphicsLayer
                val progress = lyricsProgressProvider()
                alpha = (progress / 0.35f).coerceIn(0f, 1f) * sheetFade(sheetProgressProvider())
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = androidx.compose.ui.util.lerp(source.left, targetXPx, progress)
                translationY = androidx.compose.ui.util.lerp(source.top, targetYPx, progress)
            },
    ) {

        Column(
            modifier = Modifier
                // Fixed width, not widthIn: the fade below is positioned as a fraction of this
                // Column, so with wrap-content it started at 86% of whatever the *text* measured
                // and short titles faded for no reason. At a fixed width the transparent band
                // always sits just left of the ... menu, and a title that never reaches it is
                // simply drawn whole.
                .width(maxTextWidth)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        FadeStartFraction to Color.Black,
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

/** The player sheet's own content fade (BottomSheet's pill branch), so the title goes with it. */
private fun sheetFade(sheetProgress: Float): Float = ((sheetProgress.coerceIn(0f, 1f) - 0.25f) / 0.60f).coerceIn(0f, 1f)

private val maxTextWidth = 210.dp

/** Where the title's trailing fade begins, as a fraction of [maxTextWidth] - just shy of the menu. */
private const val FadeStartFraction = 0.9f
