// MorphingSongInfo.kt
// Title + artist's counterpart to MorphingCover's own shrink into the lyrics header: without
// this, opening lyrics hard-cut SongInfoRow's title away (it lives behind the showLyrics
// if/else in BottomSheetPlayer, so it unmounts the instant lyrics opens) and LyricsScreen's own
// header text popped in already at full size — no travel, just a swap. This draws ONE title +
// artist block that scales and translates from wherever SongInfoRow was actually laid out
// (captured live via onTitlePositioned — that row's nested offsets make the position genuinely
// hard to reproduce by hand) to the fixed slot beside the shrunken cover.

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

/** Target text block once landed in the lyrics header: same X as the shrunken cover's right edge. */
private val TargetX = 36.dp + 60.dp + 18.dp

/** Collapsed sizes — a real, legible header title/subtitle, not a shrunk-down caption. */
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
    // Read directly here, not inside graphicsLayer — real font-size interpolation (below) needs
    // a genuine recomposition every frame regardless (changing TextStyle.fontSize forces a
    // remeasure, unlike a pure graphicsLayer transform), so there's no draw-phase-only path left
    // to protect; better to read lp once and use the same value for position, alpha, and size
    // than have graphicsLayer's deferred read and this composition-time read drift apart.
    val lp = lyricsProgressProvider()
    if (lp <= 0f) return

    val density = LocalDensity.current
    val targetXPx = with(density) { TargetX.toPx() }
    val targetYPx = with(density) { targetY.toPx() }
    // Text reflows/ellipsizes at its pre-scale LAYOUT width, not however wide it visually ends
    // up post-transform — using the source row's own real width keeps that ellipsis point
    // matching what was actually on screen at rest, rather than measuring against the (much
    // narrower) target width the whole time and truncating too early throughout the animation.
    val widthDp = with(density) { source.width.toDp() }

    // Real font-size interpolation instead of a graphicsLayer visual scale: a scaled-down
    // rendered-at-full-size Text is a stretched raster of fixed glyphs — it reads as "a copy
    // being squeezed," not as genuinely resizing. Interpolating TextStyle.fontSize itself
    // re-measures and re-rasterizes the glyphs at their true size every frame, the same way
    // MorphingCover's art gets an actual new layout size each frame rather than a post-hoc
    // squeeze. The cost is a real recomposition per frame, which a pure transform wouldn't need
    // — acceptable for a one-shot ~500ms transition, not something continuous.
    val titleSize = lerp(MaterialTheme.typography.titleLarge.fontSize, CollapsedTitleSize, lp)
    val artistSize = lerp(MaterialTheme.typography.titleMedium.fontSize, CollapsedArtistSize, lp)

    Column(
        modifier = modifier
            .width(widthDp)
            .graphicsLayer {
                // Fades in across the first third of the travel instead of an almost-instant
                // 15%-progress pop — smoother, while still resolving to visible well before the
                // move finishes. Can't fade across the FULL duration: SongInfoRow disappears on
                // the very first frame lyrics opens (hard unmount, not animated), so this still
                // has to cover for that reasonably quickly or there's a blank beat at the start.
                alpha = (lp / 0.35f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = androidx.compose.ui.util.lerp(source.left, targetXPx, lp)
                translationY = androidx.compose.ui.util.lerp(source.top, targetYPx, lp)
            },
    ) {
        // No ellipsis: an over-long title scrolls, and whatever is still hanging past the right
        // edge dissolves to nothing instead of being cut off with dots. The DstIn mask needs its
        // own offscreen layer so it erases from these glyphs rather than punching a hole through
        // the page behind them — the same treatment SongInfoRow gives its own copy.
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
                // softWrap off so the glyphs are allowed to run past the edge and be faded;
                // with it on the line breaks instead and the fade has nothing to work on.
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

/**
 * How wide the title block is allowed to be on the lyrics page: the full width less the artwork
 * slot, its gap, and the menu button's own corner. Past this the marquee takes over.
 */
private val maxTextWidth = 210.dp
