// MorphingPlayer.kt
// this thing is for morphing player

package com.example.musicfy.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.ui.utils.resize
import androidx.media3.common.Player
import androidx.compose.foundation.basicMarquee

/**
 * Pre-computed static endpoints for the morphing animation.
 * Calculated once and cached — no allocations per frame.
 */
@Stable
private class MorphEndpoints(
    val miniArtSize: Dp,
    val miniArtX: Dp,
    val miniArtY: Dp,
    val miniTextX: Dp,
    val miniTextY: Dp,
    val miniPlayX: Dp,
    val miniPlayY: Dp,
    val miniSkipX: Dp,
    val miniSkipY: Dp,
    val miniTextWidth: Dp,
    val fullWidth: Dp,
    val fullArtHeight: Dp,
    val fullArtX: Dp,
    val fullArtY: Dp,
    val fullTextX: Dp,
    val fullTextY: Dp,
    val fullPlayX: Dp,
    val fullPlayY: Dp,
    val fullTextWidth: Dp,
    val miniHeight: Dp,
)

@Composable
fun MorphingSharedElements(
    progressProvider: () -> Float,
    mediaMetadata: MediaMetadata?,
    canvasArtwork: com.example.musicfy.canvas.CanvasArtwork? = null,
    isPlaying: Boolean,
    playbackState: @Player.State Int,
    maxWidth: Dp, // Static screenWidth
    maxHeight: Dp, // Static screenHeight
    collapsedBound: Dp,
    horizontalOffsetProvider: () -> Float,
    isAppleMusic: Boolean,
    useNewPlayerDesign: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current

    // Pre-compute all static endpoints once. These never change during animation.
    val endpoints = remember(maxWidth, maxHeight, isAppleMusic, useNewPlayerDesign) {
        val miniHeight = 64.dp
        val miniArtSize = 48.dp
        val miniArtX = 12.dp
        val miniArtY = (miniHeight - miniArtSize) / 2

        val miniTextX = miniArtX + miniArtSize + 12.dp
        val miniTextY = 14.dp

        val miniPlaySize = 36.dp
        val miniPlayX = maxWidth - 48.dp - 92.dp
        val miniPlayY = (miniHeight - miniPlaySize) / 2

        val miniSkipSize = 36.dp
        val miniSkipX = maxWidth - 48.dp - 52.dp
        val miniSkipY = (miniHeight - miniSkipSize) / 2

        val fullWidth = if (isAppleMusic) maxWidth else maxWidth - (PlayerHorizontalPadding * 2)
        val fullArtHeight = if (isAppleMusic) maxHeight * 0.62f else fullWidth
        val fullArtX = if (isAppleMusic) 0.dp else PlayerHorizontalPadding
        val fullArtY = if (isAppleMusic) 0.dp else miniArtY

        val fullTextX = PlayerHorizontalPadding
        val fullTextY = if (isAppleMusic) fullArtHeight - 104.dp else miniTextY

        val fullPlayX = (maxWidth / 2) - 18.dp
        val fullPlayY = maxHeight - 200.dp

        val miniTextWidth = miniPlayX - miniTextX - 16.dp
        val fullTextWidth = maxWidth - (PlayerHorizontalPadding * 2)

        MorphEndpoints(
            miniArtSize = miniArtSize,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniTextX = miniTextX,
            miniTextY = miniTextY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            miniTextWidth = miniTextWidth,
            fullWidth = fullWidth,
            fullArtHeight = fullArtHeight,
            fullArtX = fullArtX,
            fullArtY = fullArtY,
            fullTextX = fullTextX,
            fullTextY = fullTextY,
            fullPlayX = fullPlayX,
            fullPlayY = fullPlayY,
            fullTextWidth = fullTextWidth,
            miniHeight = miniHeight,
        )
    }

    // Convert Dp endpoints to Px once — avoids repeated density conversions in graphicsLayer
    val endpointsPx = remember(endpoints, density) {
        with(density) {
            MorphEndpointsPx(
                miniArtSizePx = endpoints.miniArtSize.toPx(),
                miniArtXPx = endpoints.miniArtX.toPx(),
                miniArtYPx = endpoints.miniArtY.toPx(),
                miniTextXPx = endpoints.miniTextX.toPx(),
                miniTextYPx = endpoints.miniTextY.toPx(),
                miniPlayXPx = endpoints.miniPlayX.toPx(),
                miniPlayYPx = endpoints.miniPlayY.toPx(),
                miniSkipXPx = endpoints.miniSkipX.toPx(),
                miniSkipYPx = endpoints.miniSkipY.toPx(),
                miniTextWidthPx = endpoints.miniTextWidth.toPx(),
                fullWidthPx = endpoints.fullWidth.toPx(),
                fullArtHeightPx = endpoints.fullArtHeight.toPx(),
                fullArtXPx = endpoints.fullArtX.toPx(),
                fullArtYPx = endpoints.fullArtY.toPx(),
                fullTextXPx = endpoints.fullTextX.toPx(),
                fullTextYPx = endpoints.fullTextY.toPx(),
                fullPlayXPx = endpoints.fullPlayX.toPx(),
                fullPlayYPx = endpoints.fullPlayY.toPx(),
                fullTextWidthPx = endpoints.fullTextWidth.toPx(),
                miniHeightPx = endpoints.miniHeight.toPx(),
            )
        }
    }

    val miniPlaySize = 36.dp

    // Cached gradient brushes — created once, reused every frame
    val gradientBrush = remember {
        Brush.verticalGradient(
            0f to Color.Black,
            0.62f to Color.Black,
            0.88f to Color.Black.copy(alpha = 0.42f),
            1f to Color.Transparent
        )
    }
    val highArtGradientBrush = remember {
        Brush.verticalGradient(
            0.72f to Color.Black,
            0.90f to Color.Black.copy(alpha = 0.32f),
            1f to Color.Transparent
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Album Art — uses layout {} modifier to read progress without recomposition
        if (mediaMetadata?.thumbnailUrl != null) {
            val isAppleMusicNewDesign = isAppleMusic && useNewPlayerDesign
            val isAppleMusicOldDesign = isAppleMusic && !useNewPlayerDesign
            Box(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = horizontalOffsetProvider,
                        endpointsPx = endpointsPx,
                        element = MorphElement.ART,
                        density = density,
                    )
                    .graphicsLayer {
                        val p = progressProvider()
                        val artCornerRadius = when {
                            isAppleMusicOldDesign -> androidx.compose.ui.unit.lerp(16.dp, 0.dp, p)
                            isAppleMusicNewDesign -> androidx.compose.ui.unit.lerp(16.dp, 0.dp, p)
                            else -> ThumbnailCornerRadius
                        }
                        clip = true
                        shape = RoundedCornerShape(artCornerRadius)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val p = progressProvider()
                            if (p > 0.58f) {
                                drawRect(
                                    brush = gradientBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaMetadata.thumbnailUrl.resize(256, 256))
                        .size(256, 256)
                        .allowHardware(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                )

                // High-res artwork: use derivedStateOf to avoid recomposition churn
                val shouldLoadHighArtwork by remember {
                    derivedStateOf { progressProvider() > 0.965f }
                }
                if (shouldLoadHighArtwork) {
                    val highArtworkAlpha by remember {
                        derivedStateOf { ((progressProvider() - 0.97f) / 0.03f).coerceIn(0f, 1f) }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = highArtworkAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = highArtGradientBrush,
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(mediaMetadata.thumbnailUrl.resize(1200, 1200))
                                .size(1200, 1200)
                                .allowHardware(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Canvas artwork: use derivedStateOf
                val shouldLoadCanvasArtwork by remember(canvasArtwork) {
                    derivedStateOf {
                        progressProvider() > 0.985f &&
                                (canvasArtwork?.preferredAnimationUrl != null || canvasArtwork?.videoUrl != null)
                    }
                }
                if (shouldLoadCanvasArtwork) {
                    val canvasAlpha by remember {
                        derivedStateOf { ((progressProvider() - 0.985f) / 0.015f).coerceIn(0f, 1f) }
                    }
                    CanvasArtworkPlayer(
                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                        fallbackUrl = canvasArtwork?.videoUrl,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = canvasAlpha }
                    )
                }
            }
        }

        // 2. Title and Subtitle — uses layout {} modifier, no recomposition on progress change
        if (mediaMetadata != null) {
            Column(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = { 0f },
                        endpointsPx = endpointsPx,
                        element = MorphElement.TEXT,
                        density = density,
                    )
                    .graphicsLayer {
                        val p = progressProvider()
                        compositingStrategy = CompositingStrategy.Offscreen
                        transformOrigin = TransformOrigin(0f, 0f)
                        alpha = (1f - (p / 0.22f)).coerceIn(0f, 1f)
                    }
            ) {
                Text(
                    text = mediaMetadata.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val artistText = remember(mediaMetadata.artists, mediaMetadata.album) {
                    if (mediaMetadata.artists.isNotEmpty()) {
                        val artistsStr = mediaMetadata.artists.joinToString { it.name }
                        if (mediaMetadata.album != null) {
                            "$artistsStr - ${mediaMetadata.album.title}"
                        } else {
                            artistsStr
                        }
                    } else {
                        mediaMetadata.album?.title
                    }
                }
                
                if (artistText != null) {
                    Text(
                        text = artistText,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }

        // 3. Play/Pause Button — uses layout {} modifier
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.PLAY,
                    density = density,
                )
                .requiredSize(miniPlaySize)
                .graphicsLayer {
                    val p = progressProvider()
                    val playScale = 1f + (p * 1.0f)
                    scaleX = playScale
                    scaleY = playScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = (1f - (p / 0.5f)).coerceIn(0f, 1f)
                }
                .clickable { playerConnection?.togglePlayPause() }
        ) {
            Icon(
                painter = painterResource(
                    if (playbackState == Player.STATE_ENDED) R.drawable.replay
                    else if (isPlaying) R.drawable.ic_untitled_pause 
                    else R.drawable.ic_untitled_play
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp).align(Alignment.Center)
            )
        }

        // 4. Skip Next Button — uses layout {} modifier
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.SKIP,
                    density = density,
                )
                .requiredSize(miniPlaySize)
                .graphicsLayer {
                    val p = progressProvider()
                    val playScale = 1f + (p * 1.0f)
                    scaleX = playScale
                    scaleY = playScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = (1f - (p / 0.5f)).coerceIn(0f, 1f)
                }
                .clickable { playerConnection?.player?.seekToNext() }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_untitled_skip_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp).align(Alignment.Center)
            )
        }
    }
}

/**
 * Identifies which morphing element to position.
 */
private enum class MorphElement {
    ART, TEXT, PLAY, SKIP
}

/**
 * Pre-computed Px endpoints to avoid Dp→Px conversion in layout/draw phase.
 */
@Stable
private class MorphEndpointsPx(
    val miniArtSizePx: Float,
    val miniArtXPx: Float,
    val miniArtYPx: Float,
    val miniTextXPx: Float,
    val miniTextYPx: Float,
    val miniPlayXPx: Float,
    val miniPlayYPx: Float,
    val miniSkipXPx: Float,
    val miniSkipYPx: Float,
    val miniTextWidthPx: Float,
    val fullWidthPx: Float,
    val fullArtHeightPx: Float,
    val fullArtXPx: Float,
    val fullArtYPx: Float,
    val fullTextXPx: Float,
    val fullTextYPx: Float,
    val fullPlayXPx: Float,
    val fullPlayYPx: Float,
    val fullTextWidthPx: Float,
    val miniHeightPx: Float,
)

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Custom layout modifier that positions morphing elements by reading progressProvider()
 * in the layout phase (not composition phase). This prevents recomposition of the
 * entire morphing tree on every animation frame.
 */
private fun Modifier.morphLayout(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    endpointsPx: MorphEndpointsPx,
    element: MorphElement,
    density: androidx.compose.ui.unit.Density,
) = this.layout { measurable, constraints ->
    val p = progressProvider()
    val hOffset = horizontalOffsetProvider()
    val availableHeightPx = constraints.maxHeight.toFloat()
    val miniTopPx = availableHeightPx - endpointsPx.miniHeightPx

    val (x, y, w, h) = when (element) {
        MorphElement.ART -> {
            val artW = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(miniTopPx + endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)
            floatArrayOf(artX, artY, artW, artH)
        }
        MorphElement.TEXT -> {
            val textW = lerpF(endpointsPx.miniTextWidthPx, endpointsPx.fullTextWidthPx, p)
            val textX = lerpF(endpointsPx.miniTextXPx, endpointsPx.fullTextXPx, p)
            val textY = lerpF(miniTopPx + endpointsPx.miniTextYPx, endpointsPx.fullTextYPx, p)
            floatArrayOf(textX, textY, textW, -1f) // -1 = use intrinsic height
        }
        MorphElement.PLAY -> {
            val playX = lerpF(endpointsPx.miniPlayXPx, endpointsPx.fullPlayXPx, p)
            val playY = lerpF(miniTopPx + endpointsPx.miniPlayYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(playX, playY, -1f, -1f)
        }
        MorphElement.SKIP -> {
            val skipX = lerpF(endpointsPx.miniSkipXPx, endpointsPx.fullPlayXPx + with(density) { 80.dp.toPx() }, p)
            val skipY = lerpF(miniTopPx + endpointsPx.miniSkipYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(skipX, skipY, -1f, -1f)
        }
    }

    val childConstraints = if (w > 0f && h > 0f) {
        Constraints.fixed(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    } else if (w > 0f) {
        constraints.copy(maxWidth = w.toInt().coerceAtLeast(1), minWidth = 0)
    } else {
        constraints
    }

    val placeable = measurable.measure(childConstraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.toInt(), y.toInt())
    }
}
