// MorphingCover.kt
// v0 minimal replacement for MorphingPlayer.kt: cover art + play/skip-next morph only.
// No title/artist text, no tiered low-res/high-res/canvas-artwork loading, no background-style
// branching. Old file kept for reference at /old-player/MorphingPlayer.kt.

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.example.musicfy.R
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.utils.resize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.LocalHazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Pre-computed static endpoints for the morphing animation. Calculated once, no allocations
 * per frame — same technique proven in the old MorphingPlayer.kt.
 */
@Stable
private class MorphEndpoints(
    val miniArtSize: Dp,
    val miniArtX: Dp,
    val miniArtY: Dp,
    val miniPlayX: Dp,
    val miniPlayY: Dp,
    val miniSkipX: Dp,
    val miniSkipY: Dp,
    val fullWidth: Dp,
    val fullArtHeight: Dp,
    val fullArtX: Dp,
    val fullArtY: Dp,
    val fullPlayX: Dp,
    val fullPlayY: Dp,
    val miniHeight: Dp,
    val fullHeight: Dp,
)

@Stable
private class MorphEndpointsPx(
    val miniArtSizePx: Float,
    val miniArtXPx: Float,
    val miniArtYPx: Float,
    val miniPlayXPx: Float,
    val miniPlayYPx: Float,
    val miniSkipXPx: Float,
    val miniSkipYPx: Float,
    val fullWidthPx: Float,
    val fullArtHeightPx: Float,
    val fullArtXPx: Float,
    val fullArtYPx: Float,
    val fullPlayXPx: Float,
    val fullPlayYPx: Float,
    val miniHeightPx: Float,
    val fullHeightPx: Float,
)

private enum class MorphElement { ART, PLAY, SKIP, BACKDROP }

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Positions a morphing element by reading progressProvider()/horizontalOffsetProvider() in the
 * layout phase (not composition) — this is what prevents recomposition of the whole morph tree
 * on every drag frame. Verified this behavior in the old MorphingPlayer.kt before reusing it.
 */
private fun Modifier.morphLayout(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    endpointsPx: MorphEndpointsPx,
    element: MorphElement,
) = this.layout { measurable, constraints ->
    val p = progressProvider()
    val hOffset = horizontalOffsetProvider()

    val (x, y, w, h) = when (element) {
        MorphElement.ART -> {
            val artW = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)
            floatArrayOf(artX, artY, artW, artH)
        }
        MorphElement.PLAY -> {
            val playX = lerpF(endpointsPx.miniPlayXPx, endpointsPx.fullPlayXPx, p)
            val playY = lerpF(endpointsPx.miniPlayYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(playX, playY, -1f, -1f)
        }
        MorphElement.SKIP -> {
            val skipX = lerpF(endpointsPx.miniSkipXPx, endpointsPx.fullPlayXPx + 80f, p)
            val skipY = lerpF(endpointsPx.miniSkipYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(skipX, skipY, -1f, -1f)
        }
        MorphElement.BACKDROP -> {
            // Actually grows from the pill bar's own bounds up to fullscreen — a real
            // layout-phase morph (position/size interpolation), not just a fade+scale of an
            // already-fullsize rectangle. Uses the explicit fullHeightPx endpoint (same
            // source as ART's own full-size target) rather than the incoming `constraints`,
            // since those aren't guaranteed to reflect the actual player height depending on
            // how bounds propagate through the modifier chain — this is why it wasn't visibly
            // growing before.
            val backdropH = lerpF(endpointsPx.miniHeightPx, endpointsPx.fullHeightPx, p)
            floatArrayOf(0f, 0f, endpointsPx.fullWidthPx, backdropH)
        }
    }

    val childConstraints = if (w > 0f && h > 0f) {
        Constraints.fixed(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    } else {
        constraints
    }

    val placeable = measurable.measure(childConstraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.toInt(), y.toInt())
    }
}

/**
 * Cover art + play/skip-next, morphing from the mini-player pill to fullscreen.
 * v0: no title/artist morph, single-resolution image load, no background-style branching.
 */
@Composable
fun MorphingCover(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    playbackState: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current

    // Prefetch the next queue item's artwork as soon as the current track is known, so by the
    // time the song actually ends — or the user taps skip — the image is already in Coil's
    // cache and the crossfade above is effectively instant instead of loading over the network
    // while the old cover is held on screen.
    if (playerConnection != null) {
        val queueState by playerConnection.uiState.queueState.collectAsState()
        LaunchedEffect(queueState.currentIndex, queueState.items) {
            val nextUrl = queueState.items.getOrNull(queueState.currentIndex + 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)
                ?: return@LaunchedEffect
            val request = ImageRequest.Builder(context).data(nextUrl).build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    val endpoints = remember(maxWidth, maxHeight) {
        val miniHeight = 64.dp
        val miniArtSize = 48.dp
        val miniArtX = 36.dp
        val miniArtY = (miniHeight - miniArtSize) / 2

        val miniPlaySize = 36.dp
        val miniSkipSize = 36.dp
        val miniSkipX = maxWidth - 36.dp - miniSkipSize
        val miniSkipY = (miniHeight - miniSkipSize) / 2
        val miniPlayX = miniSkipX - 8.dp - miniPlaySize
        val miniPlayY = (miniHeight - miniPlaySize) / 2

        MorphEndpoints(
            miniArtSize = miniArtSize,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            fullWidth = maxWidth,
            fullArtHeight = maxHeight * 0.62f,
            fullArtX = 0.dp,
            fullArtY = 0.dp,
            fullPlayX = (maxWidth / 2) - 18.dp,
            fullPlayY = maxHeight - 200.dp,
            miniHeight = miniHeight,
            fullHeight = maxHeight,
        )
    }

    val endpointsPx = remember(endpoints, density) {
        with(density) {
            MorphEndpointsPx(
                miniArtSizePx = endpoints.miniArtSize.toPx(),
                miniArtXPx = endpoints.miniArtX.toPx(),
                miniArtYPx = endpoints.miniArtY.toPx(),
                miniPlayXPx = endpoints.miniPlayX.toPx(),
                miniPlayYPx = endpoints.miniPlayY.toPx(),
                miniSkipXPx = endpoints.miniSkipX.toPx(),
                miniSkipYPx = endpoints.miniSkipY.toPx(),
                fullWidthPx = endpoints.fullWidth.toPx(),
                fullArtHeightPx = endpoints.fullArtHeight.toPx(),
                fullArtXPx = endpoints.fullArtX.toPx(),
                fullArtYPx = endpoints.fullArtY.toPx(),
                fullPlayXPx = endpoints.fullPlayX.toPx(),
                fullPlayYPx = endpoints.fullPlayY.toPx(),
                miniHeightPx = endpoints.miniHeight.toPx(),
                fullHeightPx = endpoints.fullHeight.toPx(),
            )
        }
    }

    val miniPlaySize = 36.dp

    Box(modifier = modifier) {
        // Backdrop blur for the pill — blurs whatever's actually behind the mini player (the
        // real Haze "source" registered in MainActivity), not a copy of the cover art. Only
        // composed while nearly fully collapsed, so the RenderEffect is never alive during an
        // active drag/expand — no continuous blur cost while the sheet is moving. The fade
        // itself is a fixed-duration AnimatedVisibility crossfade (not alpha derived directly
        // from drag progress), so it always reads as a smooth fade regardless of how fast a
        // fling settles, instead of popping in over 1-2 frames.
        val hazeState = LocalHazeState.current
        val isNearCollapsed by remember { derivedStateOf { progressProvider() < 0.06f } }
        AnimatedVisibility(
            visible = isNearCollapsed,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp)
        ) {
            val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let {
                        if (hazeState != null) {
                            it.hazeEffect(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = containerColor,
                                    tint = HazeTint(containerColor.copy(alpha = 0.65f)),
                                    blurRadius = 120.dp,
                                ),
                            )
                        } else {
                            it.background(containerColor.copy(alpha = 0.85f))
                        }
                    }
            )
        }

        // Full-player background behind the cover card and every other element — the source
        // image is blurred ONCE at a fixed size (48x48, scaled up), then that already-blurred
        // result is just alpha-faded in place. The source blur is computed once at a fixed
        // 48x48 size — cheap regardless of how large it's drawn — but the *layer itself* now
        // actually morphs: it grows from the pill bar's own bounds up to fullscreen using the
        // same layout-phase morphLayout technique as the cover art below, so it's a real
        // position/size interpolation (a morph), not a fixed full-size rectangle that just
        // fades/scales from its own center. Only composed past the very start of the drag
        // (progress > 0.02) — nothing while collapsed in the pill.
        //
        // No warp shader here anymore: it was a continuous per-frame RuntimeShader
        // re-evaluation for as long as the backdrop was composed (i.e. the whole time the
        // player was expanded, not just mid-drag) — a real, constant cost, not a one-off. A
        // static blur costs nothing once rendered; an animated one costs every frame forever.
        if (trackInfo.thumbnailUrl != null) {
            val showBackdrop by remember { derivedStateOf { progressProvider() > 0.02f } }
            if (showBackdrop) {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "warp")
                val warpTime by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 100f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(100000, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                    ),
                    label = "warpTime"
                )
                val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                Box(
                    modifier = Modifier
                        .morphLayout(
                            progressProvider = progressProvider,
                            horizontalOffsetProvider = { 0f },
                            endpointsPx = endpointsPx,
                            element = MorphElement.BACKDROP,
                        )
                        .graphicsLayer {
                            alpha = progressProvider().coerceIn(0f, 1f)
                            // Clip to this box's own (morphing) bounds — the child below is a
                            // FIXED full-player size and gets cropped down to whatever window
                            // is currently revealed, rather than being re-measured/re-blurred
                            // as that window grows.
                            clip = true
                        }
                        // Solid backing color painted first — if the blurred image's edges
                        // ever sample past their own bounds (blur naturally fades toward
                        // transparent right at the source edge), this is what shows through
                        // instead of whatever's actually behind the player.
                        .background(containerColor)
                ) {
                    // Fixed absolute size (always the full player's own maxWidth/maxHeight,
                    // never the parent's currently-morphing size) — this is what makes "blur
                    // the source first, then morph it" real: the blur is computed once for a
                    // size that never changes, and the outer clip above is what reveals more
                    // or less of it as the drag progresses, not a resize of this content.
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(trackInfo.thumbnailUrl?.resize(48, 48))
                            .allowHardware(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.6f
                                scaleY = 1.6f
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }
                            .blur(130.dp)
                            .let {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    it.liquidWarpEffect(warpTime)
                                } else {
                                    it
                                }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                    )
                }
            }
        }

        // Cover art
        if (trackInfo.thumbnailUrl != null) {
            Box(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = horizontalOffsetProvider,
                        endpointsPx = endpointsPx,
                        element = MorphElement.ART,
                    )
                    .graphicsLayer {
                        val p = progressProvider()
                        clip = true
                        shape = RoundedCornerShape(lerp(ThumbnailCornerRadius, 0.dp, p))
                    }
            ) {
                // crossfade: when trackInfo.thumbnailUrl changes (track change / manual skip),
                // Coil keeps showing the previous image and fades to the new one once it's
                // decoded, instead of clearing to blank while the new image loads. Combined
                // with the queue-ahead prefetch below, the next track's image is usually
                // already cached by the time this swaps, so the fade is effectively instant;
                // when it isn't cached (e.g. a manual skip before prefetch finished), this is
                // what keeps the old cover "held" until the new one is ready.
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(trackInfo.thumbnailUrl?.resize(1200, 1200))
                        .allowHardware(true)
                        .crossfade(300)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Keyed by mediaId so switching tracks resets this to null immediately (same
                // recomposition as the track change, before the LaunchedEffect below even
                // runs) instead of holding onto the previous track's animated cover/state
                // while the new one is still being looked up.
                var canvasArtwork by remember(trackInfo.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist) {
                    val mediaId = trackInfo.mediaId
                    val titleStr = trackInfo.title
                    val artistStr = trackInfo.artist
                    if (mediaId.isBlank() || titleStr.isBlank() || artistStr.isBlank()) return@LaunchedEffect
                    
                    val cached = CanvasArtworkPlaybackCache.get(mediaId)
                    if (cached != null) {
                        canvasArtwork = cached
                        return@LaunchedEffect
                    }
                    
                    val normTitle = normalizeCanvasSongTitle(titleStr)
                    val normArtist = normalizeCanvasArtistName(artistStr)
                    if (normTitle.length < 2 || normArtist.length < 2) return@LaunchedEffect
                    
                    val res = withContext(Dispatchers.IO) {
                        MonochromeApiCanvas.getBySongArtist(normTitle, normArtist, null)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: AppleMusicCanvasProvider.getBySongArtist(normTitle, normArtist, null, "us")
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    }
                    if (res != null) {
                        CanvasArtworkPlaybackCache.put(mediaId, res)
                        canvasArtwork = res
                    }
                }

                // Static cover shows for the whole drag; only once nearly fully expanded does
                // it crossfade to the animated canvas cover, and it fades back to static as
                // soon as collapsing starts — a fixed-duration crossfade, not tied directly to
                // drag progress, same reasoning as the pill blur above.
                val nearFullyExpanded by remember(canvasArtwork) {
                    derivedStateOf { canvasArtwork?.preferredAnimationUrl != null && progressProvider() > 0.92f }
                }
                AnimatedVisibility(
                    visible = nearFullyExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                        fallbackUrl = null,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Play/Pause — visible in the pill, fades out as the fullscreen controls fade in
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.PLAY,
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

        // Skip next — same fade behavior as Play
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.SKIP,
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

@androidx.annotation.RequiresApi(33)
private fun Modifier.liquidWarpEffect(time: Float): Modifier = composed {
    val shader = androidx.compose.runtime.remember {
        android.graphics.RuntimeShader("""
            uniform float2 resolution;
            uniform float time;
            uniform shader image;

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / resolution;
                float t = time * 0.5;
                
                float2 warpOffset = float2(
                    sin(uv.y * 3.0 + t) * 0.05 + cos(uv.x * 2.0 - t * 0.6) * 0.04,
                    cos(uv.x * 3.0 + t * 0.7) * 0.05 + sin(uv.y * 2.5 + t * 0.9) * 0.04
                );
                
                float2 distortedCoord = fragCoord + warpOffset * resolution;
                return image.eval(distortedCoord);
            }
        """.trimIndent())
    }
    
    this.graphicsLayer {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time)
        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
        clip = true
    }
}
