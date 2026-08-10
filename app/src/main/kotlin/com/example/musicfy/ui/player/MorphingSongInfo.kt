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
        Text(
            text = trackInfo.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
        )
        if (trackInfo.artist.isNotBlank()) {
            Text(
                text = trackInfo.artist,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = artistSize),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
