// morphingsonginfokt
// title + artist s counterpart to morphingcover s own shrink into the lyrics
// this opening lyrics hard cut songinforow s title away it lives behind the
// if else in bottomsheetplayer so it unmounts the instant lyrics opens and
// header text popped in already at full size no travel just a swap this
// artist block that scales and translates from wherever songinforow was
// captured live via ontitlepositioned that row s nested offsets make the
// hard to reproduce by hand to the fixed slot beside the shrunken cover

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

// target text block once landed in the lyrics header same x as the shrunken cover s right edge
private val TargetX = 36.dp + 60.dp + 18.dp

// collapsed sizes a real legible header title subtitle not a shrunk down caption
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
    // read directly here not inside graphicslayer real font size interpolation
    // a genuine recomposition every frame regardless changing textstylefontsize
    // remeasure unlike a pure graphicslayer transform so there s no
    // to protect better to read lp once and use the same value for position
    // than have graphicslayer s deferred read and this composition time read
    val lp = lyricsProgressProvider()
    if (lp <= 0f) return

    val density = LocalDensity.current
    val targetXPx = with(density) { TargetX.toPx() }
    val targetYPx = with(density) { targetY.toPx() }
    // text reflows ellipsizes at its pre scale layout width not however wide it
    // up post transform using the source row s own real width keeps that
    // matching what was actually on screen at rest rather than measuring against
    // narrower target width the whole time and truncating too early throughout
    val widthDp = with(density) { source.width.toDp() }

    // real font size interpolation instead of a graphicslayer visual scale a
    // rendered at full size text is a stretched raster of fixed glyphs it
    // being squeezed not as genuinely resizing interpolating textstylefontsize
    // re measures and re rasterizes the glyphs at their true size every frame
    // morphingcover s art gets an actual new layout size each frame rather than
    // squeeze the cost is a real recomposition per frame which a pure transform
    // acceptable for a one shot ~500ms transition not something continuous
    val titleSize = lerp(MaterialTheme.typography.titleLarge.fontSize, CollapsedTitleSize, lp)
    val artistSize = lerp(MaterialTheme.typography.titleMedium.fontSize, CollapsedArtistSize, lp)

    Column(
        modifier = modifier
            .width(widthDp)
            .graphicsLayer {
                // fades in across the first third of the travel instead of an almost instant
                // 15% progress pop smoother while still resolving to visible well before
                // move finishes can t fade across the full duration songinforow disappears
                // the very first frame lyrics opens hard unmount not animated so this still
                // has to cover for that reasonably quickly or there s a blank beat at the
                alpha = (lp / 0.35f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = androidx.compose.ui.util.lerp(source.left, targetXPx, lp)
                translationY = androidx.compose.ui.util.lerp(source.top, targetYPx, lp)
            },
    ) {
        // no ellipsis an over long title scrolls and whatever is still hanging past
        // edge dissolves to nothing instead of being cut off with dots the dstin
        // own offscreen layer so it erases from these glyphs rather than punching a
        // the page behind them the same treatment songinforow gives its own copy
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
                // softwrap off so the glyphs are allowed to run past the edge and be faded
                // with it on the line breaks instead and the fade has nothing to work on
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

// how wide the title block is allowed to be on the lyrics page the full width
private val maxTextWidth = 210.dp
