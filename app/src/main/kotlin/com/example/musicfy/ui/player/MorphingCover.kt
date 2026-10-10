// MorphingCover.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.example.musicfy.R
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.utils.resize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.component.rememberCoverBlurAvailable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.LocalGlassState
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.ForceYtBackdropBlur1500Key
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.DisableVideoAutoplayKey
import com.example.musicfy.utils.YTPlayerUtils
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.player.customize.DiscCoverStack
import com.example.musicfy.ui.player.customize.PlayerBackgroundContent
import com.example.musicfy.ui.player.customize.CoverGradientBackdrop
import com.example.musicfy.ui.player.customize.coverArtBox
import com.example.musicfy.ui.player.customize.isDisc
import com.example.musicfy.ui.component.GlassChromeBlurRadius
import com.example.musicfy.ui.component.agslRenderEffect
import com.example.musicfy.ui.component.createAgslShader
import com.example.musicfy.ui.component.setAgslUniform
import com.example.musicfy.ui.component.drawGlassNode
import com.example.musicfy.ui.component.glassNodeHasContent
import com.example.musicfy.ui.component.glassNodeHeight
import com.example.musicfy.ui.component.glassNodeWidth
import com.example.musicfy.ui.component.GlassChromeColor
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.press3D
import com.example.musicfy.utils.rememberPreference
import androidx.core.content.getSystemService

@Stable
private class MorphEndpoints(
    val miniArtWidth: Dp,
    val miniArtHeight: Dp,
    val miniArtX: Dp,
    val miniArtY: Dp,
    val miniPlayX: Dp,
    val miniPlayY: Dp,
    val miniSkipX: Dp,
    val miniSkipY: Dp,
    val miniTextX: Dp,
    val miniTextWidth: Dp,
    val lyricsArtSize: Dp,
    val lyricsArtX: Dp,
    val lyricsArtY: Dp,
    val fullTextX: Dp,
    val fullTextY: Dp,
    val fullTextWidth: Dp,
    val fullWidth: Dp,

    val fullArtWidth: Dp,
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
    val miniArtWidthPx: Float,
    val miniArtHeightPx: Float,
    val miniArtXPx: Float,
    val miniArtYPx: Float,
    val miniPlayXPx: Float,
    val miniPlayYPx: Float,
    val miniSkipXPx: Float,
    val miniSkipYPx: Float,
    val miniTextXPx: Float,
    val miniTextWidthPx: Float,
    val lyricsArtSizePx: Float,
    val lyricsArtXPx: Float,
    val lyricsArtYPx: Float,
    val fullTextXPx: Float,
    val fullTextYPx: Float,
    val fullTextWidthPx: Float,
    val fullWidthPx: Float,
    val fullArtWidthPx: Float,
    val fullArtHeightPx: Float,
    val fullArtXPx: Float,
    val fullArtYPx: Float,
    val fullPlayXPx: Float,
    val fullPlayYPx: Float,
    val miniHeightPx: Float,
    val fullHeightPx: Float,
)

private enum class MorphElement { ART, PLAY, SKIP, TEXT, BACKDROP }

private const val PILL_FADE_END = 0.15f

private val LyricsHeaderCornerRadius = 12.dp

/*
 * Where the cover actually lands in lyrics mode. LyricsScreen's header has to place its hitbox and
 * its menu button against these exact values - the artwork is drawn by the morph layer, not by the
 * header, so the two are only aligned if they agree on the numbers. They previously did not: the
 * header put its 60dp hitbox at statusBar + 14dp while the art rendered at statusBar + 28dp, so
 * taps landed above the artwork and the menu sat 14dp high of the cover's centre.
 */
internal val LyricsHeaderArtSize = 60.dp
internal val LyricsHeaderArtX = 36.dp
internal val LyricsHeaderArtTopFromStatusBar = 28.dp

private val SquaredCoverCornerRadius = 22.dp

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun discWeight(
    progressProvider: () -> Float,
    lyricsProgressProvider: () -> Float,
): Float {
    val expanded = ((progressProvider() - 0.55f) / 0.30f).coerceIn(0f, 1f)
    val lyrics = ((lyricsProgressProvider() - 0.10f) / 0.40f).coerceIn(0f, 1f)
    return expanded * (1f - lyrics)
}

private fun Modifier.morphLayout(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    endpointsPx: MorphEndpointsPx,
    element: MorphElement,
    lyricsProgressProvider: () -> Float = { 0f },
) = this.layout { measurable, constraints ->
    val p = progressProvider()
    val hOffset = horizontalOffsetProvider()

    val (x, y, w, h) = when (element) {
        MorphElement.ART -> {
            val artW = lerpF(endpointsPx.miniArtWidthPx, endpointsPx.fullArtWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtHeightPx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)

            val lp = lyricsProgressProvider()
            if (lp <= 0f) {
                floatArrayOf(artX, artY, artW, artH)
            } else {

                val lyricsX = lerpF(endpointsPx.miniArtXPx, endpointsPx.lyricsArtXPx, p) + hOffset
                val lyricsY = lerpF(endpointsPx.miniArtYPx, endpointsPx.lyricsArtYPx, p)
                val lyricsW = lerpF(endpointsPx.miniArtWidthPx, endpointsPx.lyricsArtSizePx, p)
                val lyricsH = lerpF(endpointsPx.miniArtHeightPx, endpointsPx.lyricsArtSizePx, p)
                floatArrayOf(
                    lerpF(artX, lyricsX, lp),
                    lerpF(artY, lyricsY, lp),
                    lerpF(artW, lyricsW, lp),
                    lerpF(artH, lyricsH, lp),
                )
            }
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
        MorphElement.TEXT -> {

            val textX = lerpF(endpointsPx.miniTextXPx, endpointsPx.fullTextXPx, p) + hOffset
            val textY = lerpF(0f, endpointsPx.fullTextYPx, p)
            val textW = lerpF(endpointsPx.miniTextWidthPx, endpointsPx.fullTextWidthPx, p)
            floatArrayOf(textX, textY, textW, endpointsPx.miniHeightPx)
        }
        MorphElement.BACKDROP -> {

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

@Composable
fun MorphingCover(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    playbackState: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    collapsedBound: Dp,
    pureBlack: Boolean,
    glassState: GlassState,
    modifier: Modifier = Modifier,

    lyricsProgressProvider: () -> Float = { 0f },

    forceVideoBackground: Boolean = false,

    coverStyle: PlayerCoverStyle = PlayerCoverStyle.EDGE_TO_EDGE,

    backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_GRADIENT,

    editMode: Boolean = false,

    onLongPressCover: (() -> Unit)? = null,

    /** Double tap on the full-size cover, with where it landed in root coordinates (the like heart starts there). */
    onDoubleTapCover: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,

    onArtBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val emptyQueue = remember { kotlinx.coroutines.flow.MutableStateFlow(com.example.musicfy.ui.player.models.QueueState()) }
    val queueState by (playerConnection?.uiState?.queueState ?: emptyQueue).collectAsState()
    var previousQueueIndex by remember { mutableIntStateOf(queueState.currentIndex) }
    var skipDirection by remember { mutableIntStateOf(1) }

    LaunchedEffect(queueState.currentIndex) {
        if (queueState.currentIndex != previousQueueIndex) {
            skipDirection = if (queueState.currentIndex < previousQueueIndex) -1 else 1
            previousQueueIndex = queueState.currentIndex
        }
    }

    if (playerConnection != null) {
        LaunchedEffect(queueState.currentIndex, queueState.items) {
            val nextUrl = queueState.items.getOrNull(queueState.currentIndex + 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)
            val prevUrl = queueState.items.getOrNull(queueState.currentIndex - 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)

            val loader = SingletonImageLoader.get(context)
            if (nextUrl != null) {
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(nextUrl)
                        .size(CoilSize(1200, 1200))
                        .precision(Precision.INEXACT)
                        .build()
                )
            }
            if (prevUrl != null) {
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(prevUrl)
                        .size(CoilSize(1200, 1200))
                        .precision(Precision.INEXACT)
                        .build()
                )
            }
        }
    }

    val isDiscStyle = coverStyle.isDisc

    // Without blur the seam can't soften the edge-to-edge cover's bottom into the backdrop, so the
    // cover fades its own bottom edge out instead - picture, canvas and video alike.
    val fadeCoverEdge = !rememberCoverBlurAvailable() && coverStyle == PlayerCoverStyle.EDGE_TO_EDGE

    val playVideoPref by rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    val disableVideoAutoplay by rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    val currentMetadata by (playerConnection?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) })
        .collectAsState()
    // a track that is a video itself (a music video or live row, the Videos search tab) plays that
    // video here on its own; songs only get one when the video background setting asks for it
    val trackIsVideo = !disableVideoAutoplay &&
        currentMetadata?.id == trackInfo.mediaId &&
        currentMetadata?.isVideoSong == true
    val playVideoBackground = forceVideoBackground || playVideoPref || trackIsVideo
    var videoInfo by remember(trackInfo.mediaId) { mutableStateOf<OfficialMusicVideo?>(null) }
    LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist, playVideoBackground, trackIsVideo) {
        val mediaId = trackInfo.mediaId
        val titleStr = trackInfo.title
        val artistStr = trackInfo.artist
        if (!playVideoBackground || mediaId.isBlank()) {
            videoInfo = null
            return@LaunchedEffect
        }
        if (trackIsVideo) {
            // the audio is this very video, so its own stream lines up exactly and needs no search
            val key = "self:$mediaId"
            if (YouTubeVideoUrlCache.contains(key)) {
                videoInfo = YouTubeVideoUrlCache.get(key)
                return@LaunchedEffect
            }
            val own = withContext(Dispatchers.IO) {
                YTPlayerUtils.resolveVideoStreamUrl(mediaId).getOrNull()?.let { OfficialMusicVideo(mediaId, it) }
            }
            YouTubeVideoUrlCache.put(key, own)
            videoInfo = own
            return@LaunchedEffect
        }
        if (titleStr.isBlank() || artistStr.isBlank()) {
            videoInfo = null
            return@LaunchedEffect
        }
        if (YouTubeVideoUrlCache.contains(mediaId)) {
            videoInfo = YouTubeVideoUrlCache.get(mediaId)
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) { findOfficialMusicVideo(titleStr, artistStr) }
        YouTubeVideoUrlCache.put(mediaId, resolved)
        videoInfo = resolved
    }

    val isVideoActive = !isDiscStyle && playVideoBackground && videoInfo != null
    val targetMiniArtWidth = if (isVideoActive) (48.dp * 16f / 9f) else 48.dp
    val animatedMiniArtWidth by animateDpAsState(
        targetValue = targetMiniArtWidth,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "miniArtWidth"
    )

    val endpoints = remember(maxWidth, maxHeight, statusBarTop, coverStyle, animatedMiniArtWidth) {
        val miniHeight = 64.dp
        val miniArtWidth = animatedMiniArtWidth
        val miniArtHeight = 48.dp
        val miniArtX = 36.dp
        val miniArtY = (miniHeight - miniArtHeight) / 2

        val miniPlaySize = 36.dp
        val miniSkipSize = 36.dp
        val miniSkipX = maxWidth - 36.dp - miniSkipSize
        val miniSkipY = (miniHeight - miniSkipSize) / 2
        val miniPlayX = miniSkipX - 8.dp - miniPlaySize
        val miniPlayY = (miniHeight - miniPlaySize) / 2

        val miniTextX = miniArtX + miniArtWidth + 12.dp
        val miniTextWidth = (miniPlayX - 10.dp - miniTextX).coerceAtLeast(0.dp)

        val artBox = coverArtBox(coverStyle, maxWidth, maxHeight, statusBarTop)

        MorphEndpoints(
            miniArtWidth = miniArtWidth,
            miniArtHeight = miniArtHeight,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            miniTextX = miniTextX,
            miniTextWidth = miniTextWidth,

            lyricsArtSize = LyricsHeaderArtSize,
            lyricsArtX = LyricsHeaderArtX,
            lyricsArtY = statusBarTop + LyricsHeaderArtTopFromStatusBar,
            fullTextX = 24.dp,
            fullTextY = maxHeight * 0.63f + 24.dp,
            fullTextWidth = (maxWidth - 48.dp).coerceAtLeast(0.dp),
            fullWidth = maxWidth,
            fullArtWidth = artBox.width,
            fullArtHeight = artBox.height,
            fullArtX = artBox.x,
            fullArtY = artBox.y,
            fullPlayX = (maxWidth / 2) - 18.dp,
            fullPlayY = maxHeight - 200.dp,
            miniHeight = miniHeight,
            fullHeight = maxHeight,
        )
    }

    val endpointsPx = remember(endpoints, density) {
        with(density) {
            MorphEndpointsPx(
                miniArtWidthPx = endpoints.miniArtWidth.toPx(),
                miniArtHeightPx = endpoints.miniArtHeight.toPx(),
                miniArtXPx = endpoints.miniArtX.toPx(),
                miniArtYPx = endpoints.miniArtY.toPx(),
                miniPlayXPx = endpoints.miniPlayX.toPx(),
                miniPlayYPx = endpoints.miniPlayY.toPx(),
                miniSkipXPx = endpoints.miniSkipX.toPx(),
                miniSkipYPx = endpoints.miniSkipY.toPx(),
                miniTextXPx = endpoints.miniTextX.toPx(),
                miniTextWidthPx = endpoints.miniTextWidth.toPx(),
                lyricsArtSizePx = endpoints.lyricsArtSize.toPx(),
                lyricsArtXPx = endpoints.lyricsArtX.toPx(),
                lyricsArtYPx = endpoints.lyricsArtY.toPx(),
                fullTextXPx = endpoints.fullTextX.toPx(),
                fullTextYPx = endpoints.fullTextY.toPx(),
                fullTextWidthPx = endpoints.fullTextWidth.toPx(),
                fullWidthPx = endpoints.fullWidth.toPx(),
                fullArtWidthPx = endpoints.fullArtWidth.toPx(),
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

    val longPressEnabled by remember {
        derivedStateOf { progressProvider() > 0.9f && lyricsProgressProvider() < 0.1f }
    }
    // Read through these inside the gesture detector, which is keyed on nothing: the callers pass
    // fresh lambdas on every recomposition, and restarting the detector for each one would drop
    // the second tap of a double tap.
    val currentOnLongPress by androidx.compose.runtime.rememberUpdatedState(onLongPressCover)
    val currentOnDoubleTap by androidx.compose.runtime.rememberUpdatedState(onDoubleTapCover)
    val artCoordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }

    val discSpinActive by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.9f && lyricsProgressProvider() < 0.6f }
    }

    // Swiping the full-size cover sideways: the cover follows the finger, and past a distance or a
    // flick it skips (left) or goes back (right). Only while the cover is the full-size one - the
    // mini player's cover is swiped by the sheet itself, and in the lyrics header it is a thumbnail.
    val coverSwipeX = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val coverSwipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val coverSwipeEnabled by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.98f && lyricsProgressProvider() < 0.02f }
    }
    val currentPlayerConnection by androidx.compose.runtime.rememberUpdatedState(playerConnection)

    val collapsedBoundPx = with(density) { collapsedBound.toPx() }

    val warpShader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            createAgslShader(
                """
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
                """.trimIndent()
            )
        } else {
            null
        }
    }

    val warpClockActive by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.02f && lyricsProgressProvider() < 0.6f }
    }
    val warpTimeState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(warpClockActive) {
        if (!warpClockActive) return@LaunchedEffect
        while (isActive) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameTimeNanos ->

                warpTimeState.floatValue = ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)
            }
        }
    }


    val lyricsSyncEnabled by rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = false)
    val currentLyrics by playerConnection?.currentLyrics?.collectAsState(initial = null)
        ?: androidx.compose.runtime.mutableStateOf(null)
    var lyricVideoAnchors by remember(trackInfo.mediaId) { mutableStateOf<List<LyricVideoAnchor>?>(null) }
    LaunchedEffect(videoInfo?.videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val mediaId = trackInfo.mediaId
        val videoId = videoInfo?.videoId
        val lyricsRaw = currentLyrics?.lyrics
            ?.takeIf { it.isNotBlank() && it != com.example.musicfy.db.entities.LyricsEntity.LYRICS_NOT_FOUND }
        // the track's own video is already in step with it; anchors are for lining up a separate one
        if (!lyricsSyncEnabled || videoId == null || videoId == mediaId || lyricsRaw == null) {
            lyricVideoAnchors = null
            return@LaunchedEffect
        }
        if (LyricVideoAnchorCache.contains(mediaId)) {
            lyricVideoAnchors = LyricVideoAnchorCache.get(mediaId)
            return@LaunchedEffect
        }
        val anchors = withContext(Dispatchers.IO) {
            runCatching { buildLyricVideoAnchors(videoId, lyricsRaw) }.getOrNull()
        }
        LyricVideoAnchorCache.put(mediaId, anchors)
        lyricVideoAnchors = anchors
    }

    val videoGlassState = remember(trackInfo.mediaId) { GlassState() }

    val isPillPressable by remember {
        derivedStateOf { progressProvider() < 0.02f && !editMode }
    }

    val pillPressOrigin = remember(endpointsPx.fullHeightPx) {
        val h = endpointsPx.fullHeightPx
        TransformOrigin(0.5f, if (h > 0f) (endpointsPx.miniHeightPx / 2f) / h else 0f)
    }

    Box(
        modifier = modifier
            .glassRoot(glassState, isActive = { !editMode })
            .press3D(
                maxTilt = 6f,
                pressedScale = 0.97f,
                enabled = isPillPressable,
                origin = pillPressOrigin,
            )
    ) {

        val navBackdropGlassState = LocalGlassState.current ?: remember { GlassState() }
        val pillMountedHolder = remember { booleanArrayOf(true) }
        val isPillMounted by remember {
            derivedStateOf {
                val progress = progressProvider()
                val next = when {
                    progress < 0.16f -> true
                    progress > 0.20f -> false
                    else -> pillMountedHolder[0]
                }
                pillMountedHolder[0] = next
                next
            }
        }
        if (isPillMounted) {
            val containerColor = if (pureBlack) Color.Black else GlassChromeColor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {

                        translationY = progressProvider().coerceIn(0f, 1f) * (endpointsPx.fullHeightPx - collapsedBoundPx)
                    }
            ) {
                GlassPillBackground(
                    state = navBackdropGlassState,
                    blurRadius = { GlassChromeBlurRadius },
                    tint = containerColor.copy(alpha = 0.65f),
                    foundationColor = containerColor,

                    tileMode = android.graphics.Shader.TileMode.CLAMP,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (trackInfo.thumbnailUrl != null || backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {
            val showBackdrop by remember { derivedStateOf { progressProvider() > 0.02f } }
            if (showBackdrop) {
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
                            val p = progressProvider()
                            val bgP = ((p - 0.02f) / 0.38f).coerceIn(0f, 1f)
                            alpha = androidx.compose.animation.core.FastOutSlowInEasing.transform(bgP)
                            clip = true
                        }
                        .background(containerColor)
                ) {
                    PlayerBackdropCrossfade(
                        trackInfo = trackInfo,
                        backgroundStyle = backgroundStyle,
                        pureBlack = pureBlack,
                        playVideoBackground = playVideoBackground,
                        videoInfo = videoInfo,
                        videoGlassState = videoGlassState,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        warpClockActive = warpClockActive,
                        warpShader = warpShader,
                        warpTimeState = warpTimeState,
                        lyricsProgressProvider = lyricsProgressProvider,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val isEdgeToEdge = coverStyle == PlayerCoverStyle.EDGE_TO_EDGE
        val targetPauseScale = if (isPlaying) (if (isEdgeToEdge) 1.05f else 1.0f) else (if (isEdgeToEdge) 1.0f else 0.94f)
        val animatedPauseScale by animateFloatAsState(
            targetValue = targetPauseScale,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            label = "pauseScale"
        )
        val targetPauseSaturation = if (isPlaying) 1.0f else 0.80f
        val animatedPauseSaturation by animateFloatAsState(
            targetValue = targetPauseSaturation,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            label = "pauseSaturation"
        )

        if (trackInfo.thumbnailUrl != null || isDiscStyle) {
            Box(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = horizontalOffsetProvider,
                        endpointsPx = endpointsPx,
                        element = MorphElement.ART,
                        lyricsProgressProvider = lyricsProgressProvider,
                    )
                    .graphicsLayer {
                        val p = progressProvider()

                        translationX = coverSwipeX.floatValue
                        scaleX = animatedPauseScale
                        scaleY = animatedPauseScale
                        // A colour filter on a layer forces it offscreen - the whole cover rendered
                        // into a buffer of its own every frame of the morph. While playing the
                        // matrix is the identity anyway, so it's only set while the cover is
                        // (or is turning) grey.
                        colorFilter = if (animatedPauseSaturation < 0.999f) {
                            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(animatedPauseSaturation) })
                        } else {
                            null
                        }

                        clip = !isDiscStyle ||
                            discWeight(progressProvider, lyricsProgressProvider) < 0.5f

                        val expandedRadius = if (coverStyle == PlayerCoverStyle.SQUARED) {
                            SquaredCoverCornerRadius
                        } else {
                            0.dp
                        }
                        val base = lerp(ThumbnailCornerRadius, expandedRadius, p)
                        shape = RoundedCornerShape(lerp(base, LyricsHeaderCornerRadius, lyricsProgressProvider()))
                    }
                    .then(

                        if (longPressEnabled && (onLongPressCover != null || onDoubleTapCover != null)) {
                            Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { currentOnLongPress?.invoke() },
                                        onDoubleTap = { position ->
                                            val coordinates = artCoordinates[0]
                                            val onDoubleTap = currentOnDoubleTap
                                            if (coordinates != null && coordinates.isAttached && onDoubleTap != null) {
                                                onDoubleTap(coordinates.localToRoot(position))
                                            }
                                        },
                                    )
                                }
                                // Same layer as the detector, so its tap positions convert exactly.
                                .onGloballyPositioned { artCoordinates[0] = it }
                        } else Modifier
                    )
                    .pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        var settleJob: kotlinx.coroutines.Job? = null
                        // Past this much travel, or a flick faster than this, the swipe skips.
                        val commitPx = size.width * 0.22f
                        val flingPx = 500.dp.toPx()
                        // The cover trails the finger a little, and never leaves the screen.
                        val resistance = 0.6f
                        val limitPx = size.width * 0.5f

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Not consumed unless this is a swipe on the full-size cover - the
                            // mini player's swipe and the vertical drags belong to the sheet.
                            if (!coverSwipeEnabled) return@awaitEachGesture

                            var travelled = 0f
                            val slopped = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                travelled += over
                            } ?: return@awaitEachGesture

                            settleJob?.cancel()
                            velocityTracker.resetTracking()
                            velocityTracker.addPointerInputChange(slopped)
                            coverSwipeX.floatValue = (travelled * resistance).coerceIn(-limitPx, limitPx)

                            val completed = horizontalDrag(slopped.id) { change ->
                                change.consume()
                                velocityTracker.addPointerInputChange(change)
                                travelled += change.positionChange().x
                                coverSwipeX.floatValue = (travelled * resistance).coerceIn(-limitPx, limitPx)
                            }

                            val velocity = velocityTracker.calculateVelocity().x
                            val player = currentPlayerConnection?.player
                            if (completed && player != null) {
                                if ((travelled < -commitPx || velocity < -flingPx) && player.hasNextMediaItem()) {
                                    player.seekToNext()
                                } else if ((travelled > commitPx || velocity > flingPx) && player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                }
                            }

                            // Back to rest. A new song's cover arrives with its own zoom and blur,
                            // so this only has to bring the old frame home.
                            val from = coverSwipeX.floatValue
                            settleJob = coverSwipeScope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = from,
                                    targetValue = 0f,
                                    initialVelocity = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.8f,
                                        stiffness = 380f,
                                    ),
                                ) { value, _ -> coverSwipeX.floatValue = value }
                            }
                        }
                    }
                    .then(
                        if (onArtBoundsChanged != null) {
                            Modifier.onGloballyPositioned { onArtBoundsChanged(it.boundsInRoot()) }
                        } else Modifier
                    )
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isDiscStyle) {
                                Modifier.graphicsLayer {
                                    alpha = 1f - discWeight(progressProvider, lyricsProgressProvider)
                                }
                            } else Modifier
                        )
                        .then(
                            if (fadeCoverEdge) {
                                Modifier.coverEdgeFade {
                                    // Only on the full-size cover, never the mini player's or the
                                    // lyrics header's thumbnail.
                                    val open = ((progressProvider() - 0.5f) / 0.45f).coerceIn(0f, 1f)
                                    open * (1f - lyricsProgressProvider()).coerceIn(0f, 1f)
                                }
                            } else Modifier
                        )
                ) {

                CoverArtworkCrossfade(
                    thumbnailUrl = trackInfo.thumbnailUrl,
                    mediaId = trackInfo.mediaId,
                    direction = skipDirection,
                    modifier = Modifier.fillMaxSize()
                )

                var canvasArtwork by remember(trackInfo.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                val canvasEnabled by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
                val canvasWifiOnly by rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
                LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist, canvasEnabled, canvasWifiOnly) {
                    val mediaId = trackInfo.mediaId
                    val titleStr = trackInfo.title
                    val artistStr = trackInfo.artist
                    if (!canvasEnabled || mediaId.isBlank() || titleStr.isBlank() || artistStr.isBlank()) return@LaunchedEffect

                    val connectivityManager = context.getSystemService<android.net.ConnectivityManager>()
                    if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) return@LaunchedEffect

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

                val nearFullyExpanded by remember(canvasArtwork, canvasEnabled) {
                    derivedStateOf { canvasEnabled && canvasArtwork?.preferredAnimationUrl != null && progressProvider() > 0.92f }
                }
                AnimatedVisibility(
                    visible = !isDiscStyle && nearFullyExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                        fallbackUrl = null,

                        isPlaying = !editMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!isDiscStyle && playVideoBackground && videoInfo != null) {
                    YouTubeVideoBackground(
                        streamUrl = videoInfo?.streamUrl,
                        isPlaying = isPlaying,
                        positionMsProvider = { playerConnection?.player?.currentPosition ?: 0L },
                        lyricVideoAnchors = lyricVideoAnchors,
                        glassState = videoGlassState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = (1f - lyricsProgressProvider()).coerceIn(0f, 1f)
                            }
                    )
                }
                }

                if (isDiscStyle) {
                    DiscCoverStack(
                        style = coverStyle,
                        artworkUrl = trackInfo.thumbnailUrl,
                        mediaId = trackInfo.mediaId,
                        queueIndex = queueState.currentIndex,
                        isPlaying = isPlaying,
                        spinActive = discSpinActive,
                        editMode = editMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = discWeight(progressProvider, lyricsProgressProvider) },
                    )
                }
            }
        }

        // The mini player's title is fully faded once the sheet is halfway open, yet it was still
        // measured at a new width (re-laying its text out) and drawn through its offscreen fade
        // layer on every frame of the rest of the open and close.
        val miniTextShown by remember { derivedStateOf { progressProvider() < 0.5f } }
        if (miniTextShown) Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    endpointsPx = endpointsPx,
                    element = MorphElement.TEXT,
                )
                .graphicsLayer {
                    alpha = (1f - (progressProvider() / 0.5f)).coerceIn(0f, 1f)

                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {

                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        0.82f to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val miniSongDisplay = remember(trackInfo.mediaId, trackInfo.title, trackInfo.artist, trackInfo.album) {
                trackInfo
            }
            AnimatedContent(
                targetState = miniSongDisplay,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    val isForward = skipDirection >= 0
                    if (isForward) {
                        (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { (-it * 0.45f).toInt() } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { (it * 0.45f).toInt() } + fadeOut(tween(220)))
                    } else {
                        (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { (it * 0.45f).toInt() } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { (-it * 0.45f).toInt() } + fadeOut(tween(220)))
                    }.using(SizeTransform(clip = false))
                },
                label = "MiniSongInfoSlide"
            ) { currentTrack ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    val subtitle = listOf(currentTrack.artist, currentTrack.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

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

private data class CoverItem(
    val mediaId: String,
    val thumbnailUrl: String?,
)

@Composable
private fun CoverArtworkCrossfade(
    thumbnailUrl: String?,
    mediaId: String,
    direction: Int,
    modifier: Modifier = Modifier,
) {
    var currentCover by remember { mutableStateOf(CoverItem(mediaId, thumbnailUrl)) }
    var outgoingCover by remember { mutableStateOf<CoverItem?>(null) }
    var transitionDirection by remember { mutableIntStateOf(direction) }

    val animProgress = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(mediaId, thumbnailUrl) {
        if (mediaId != currentCover.mediaId || thumbnailUrl != currentCover.thumbnailUrl) {
            outgoingCover = currentCover
            currentCover = CoverItem(mediaId, thumbnailUrl)
            transitionDirection = if (direction != 0) direction else 1
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 600,
                    easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                )
            )
            outgoingCover = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val p = animProgress.value
        val isForward = transitionDirection >= 0

        val outgoing = outgoingCover
        if (outgoing != null && p < 0.999f) {
            // 1. Outgoing cover underneath:
            // Zooms dramatically and blurs whole frame as it fades out
            val outgoingScale = if (isForward) 1f + 0.22f * p else 1f - 0.22f * p
            val outgoingBlur = p * 20f
            val outgoingAlpha = if (p < 0.30f) 1f else ((1f - p) / 0.70f).coerceIn(0f, 1f)
            CoverImageLayer(
                item = outgoing,
                scale = outgoingScale,
                alpha = outgoingAlpha,
                blurPx = outgoingBlur,
                isAnimating = true,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Incoming cover on top:
            // Zooms dramatically from initial scale (0.78f or 1.22f) to 1.0f while unblurring whole frame
            val incomingScale = if (isForward) 0.78f + 0.22f * p else 1.22f - 0.22f * p
            val incomingBlur = (1f - p) * 20f
            val incomingAlpha = p.coerceIn(0f, 1f)
            CoverImageLayer(
                item = currentCover,
                scale = incomingScale,
                alpha = incomingAlpha,
                blurPx = incomingBlur,
                isAnimating = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CoverImageLayer(
                item = currentCover,
                scale = 1f,
                alpha = 1f,
                blurPx = 0f,
                isAnimating = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CoverImageLayer(
    item: CoverItem,
    scale: Float,
    alpha: Float,
    blurPx: Float,
    isAnimating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl?.resize(1200, 1200))
            .allowHardware(true)
            .size(CoilSize(1200, 1200))
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }

    // Quantize blur to 0.5f steps to reuse GPU RenderEffect instances & prevent lag
    val quantizedBlur = if (blurPx > 0.3f) (kotlin.math.round(blurPx * 2f) / 2f) else 0f

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                if (isAnimating) {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                renderEffect = if (quantizedBlur > 0f) BlurEffectCache.get(quantizedBlur) else null
                clip = true
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PlayerBackdropCrossfade(
    trackInfo: TrackInfo,
    backgroundStyle: PlayerBackgroundStyle,
    pureBlack: Boolean,
    playVideoBackground: Boolean,
    videoInfo: OfficialMusicVideo?,
    videoGlassState: GlassState,
    maxWidth: Dp,
    maxHeight: Dp,
    warpClockActive: Boolean,
    warpShader: Any?,
    warpTimeState: androidx.compose.runtime.MutableFloatState,
    lyricsProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    // The video's backdrop is a blurred reflection of it. Without blur there's nothing to draw it
    // with, so the cover's gradient stays up under the video rather than fading to a bare surface.
    val blurAvailable = rememberCoverBlurAvailable()
    val isVideoActive = playVideoBackground && videoInfo != null && blurAvailable
    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoActive) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "videoBackdropAlphaFade"
    )

    val lp = lyricsProgressProvider()
    val effectiveVideoAlpha = (videoAlpha * (1f - lp)).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - effectiveVideoAlpha }
        ) {
            if (backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {
                // One instance for every song: these styles ease between songs themselves (the
                // Apple Music blobs glide to the new colours), and a second copy built from scratch
                // for the outgoing song has no colours yet - it showed as a dark flash.
                PlayerBackgroundContent(
                    style = backgroundStyle,
                    thumbnailUrl = trackInfo.thumbnailUrl,
                    pureBlack = pureBlack,
                    modifier = Modifier.requiredSize(maxWidth, maxHeight)
                )
            } else {
                CoverBackdropFade(
                    thumbnailUrl = trackInfo.thumbnailUrl,
                    width = maxWidth,
                    height = maxHeight,
                    warpClockActive = warpClockActive,
                    warpShader = warpShader,
                    warpTimeState = warpTimeState,
                )
            }
        }

        // The video backdrop fades in on top when there is a video, and dissolves when lyrics open.
        if (effectiveVideoAlpha > 0.005f) {
            VideoBackdropBlur(
                glassState = videoGlassState,
                modifier = Modifier
                    .requiredSize(maxWidth, maxHeight)
                    .graphicsLayer { alpha = effectiveVideoAlpha }
            )
        }
    }
}

/** A song's backdrop picture. Wrapped so a slot with no picture (null) differs from one with no art. */
private data class BackdropPicture(val url: String?)

/** The two slots the backdrop fade takes turns with - see [CoverBackdropFade]. */
@Stable
private class BackdropSlots(first: String?) {
    val pictures = arrayOf(
        mutableStateOf<BackdropPicture?>(BackdropPicture(first)),
        mutableStateOf<BackdropPicture?>(null),
    )

    /** Whether each slot's picture has loaded yet. */
    val loaded = arrayOf(mutableStateOf(false), mutableStateOf(false))

    /** The slot holding the current song's picture, drawn over the other. */
    var front by mutableIntStateOf(0)
}

/** How long a new backdrop picture is waited for before the fade goes ahead without it. */
private const val BackdropLoadTimeoutMs = 1500L
private const val BackdropFadeMs = 750

/**
 * The cover-gradient backdrop, fading from one song's picture to the next.
 *
 * Two slots take turns. The next picture goes into the free one, is waited for, and only then fades
 * in *over* the one showing, which stays fully opaque underneath until the fade is done and is then
 * dropped. The old way faded the outgoing picture out while the new one came in, so the dark
 * container behind both showed through the middle of every change - and both pictures were built
 * from scratch, with nothing to draw for their first frames. Here neither is ever transparent
 * before the other is solid behind it.
 *
 * Songs are shown one at a time: skipping through several while a fade runs shows only the newest
 * once it finishes.
 */
@Composable
private fun CoverBackdropFade(
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    warpClockActive: Boolean,
    warpShader: Any?,
    warpTimeState: androidx.compose.runtime.MutableFloatState,
) {
    val slots = remember { BackdropSlots(thumbnailUrl) }
    val frontAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val latest by androidx.compose.runtime.rememberUpdatedState(thumbnailUrl)

    LaunchedEffect(slots) {
        snapshotFlow { latest }.collect { next ->
            // Nothing to show for a song without art: the backdrop is dropped altogether then.
            if (next == null || slots.pictures[slots.front].value?.url == next) return@collect

            val incoming = 1 - slots.front
            slots.loaded[incoming].value = false
            slots.pictures[incoming].value = BackdropPicture(next)
            frontAlpha.snapTo(0f)
            slots.front = incoming

            // A picture that never loads must not hold the fade up forever.
            withTimeoutOrNull(BackdropLoadTimeoutMs) {
                snapshotFlow { slots.loaded[incoming].value }.first { it }
            }
            frontAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = BackdropFadeMs,
                    easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
                )
            )
            slots.pictures[1 - incoming].value = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The slot in front is drawn last. Keyed, so swapping the order moves the layers instead
        // of rebuilding them - the one that was showing keeps its picture through the fade.
        val drawOrder = if (slots.front == 0) intArrayOf(1, 0) else intArrayOf(0, 1)
        for (index in drawOrder) {
            key(index) {
                val picture = slots.pictures[index].value
                if (picture != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (index == slots.front) frontAlpha.value else 1f }
                    ) {
                        CoverGradientBackdrop(
                            thumbnailUrl = picture.url,
                            width = width,
                            height = height,
                            animate = warpClockActive,
                            shader = warpShader,
                            timeProvider = { warpTimeState.floatValue },
                            onLoaded = { slots.loaded[index].value = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoBackdropBlur(
    glassState: GlassState,
    modifier: Modifier = Modifier,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (force1500pxBlur) = rememberPreference(ForceYtBackdropBlur1500Key, defaultValue = false)
    if (disableBlur) return

    var backdropPosInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                backdropPosInWindow = it.positionInWindow()
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffectCache.get(
                        if (force1500pxBlur) 1500f else 120f,
                        android.graphics.Shader.TileMode.CLAMP,
                        allowLargeRadius = true,
                    )
                }
        ) {
            val node = glassState.renderNode
            if (node == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return@Canvas
            if (!glassNodeHasContent(node)) return@Canvas
            val nodeWidth = glassNodeWidth(node).toFloat()
            val nodeHeight = glassNodeHeight(node).toFloat()
            if (nodeWidth <= 0f || nodeHeight <= 0f) return@Canvas

            val videoWindowX = glassState.rootPosition.x
            val videoWindowY = glassState.rootPosition.y
            val hasValidRootPos = videoWindowX > 0f || videoWindowY > 0f

            val videoLeft = if (hasValidRootPos) {
                videoWindowX - backdropPosInWindow.x
            } else {
                (size.width - nodeWidth) / 2f
            }
            val videoTop = if (hasValidRootPos) {
                videoWindowY - backdropPosInWindow.y
            } else {
                (size.height * 0.35f) - (nodeHeight / 2f)
            }

            // Positioned flush at the bottom of the main video frame with slight overlap to prevent any sub-pixel gap
            val reflectionTop = videoTop + nodeHeight - 2f

            // Flipped vertically and scaled slightly up to fill the background seamlessly with zero spacing or gap
            withTransform({
                translate(left = videoLeft, top = reflectionTop)
                scale(scaleX = 1.04f, scaleY = -1.04f, pivot = Offset(nodeWidth / 2f, nodeHeight / 2f))
            }) {
                drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
            }
        }
    }
}

/**
 * Fades the bottom of the cover out into the backdrop, by [strength] (0 is none). The stand-in for
 * the seam blur when there's no blur. Everything inside fades - picture, canvas and video - since
 * the mask goes over the whole layer. That layer is offscreen only while the fade shows.
 */
private fun Modifier.coverEdgeFade(strength: () -> Float): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (strength() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val s = strength()
        if (s <= 0f) return@drawWithContent
        // Eased, so the fade has no visible start or end.
        val start = size.height * (1f - CoverEdgeFadeFraction)
        val steps = 8
        val stops = Array(steps + 1) { i ->
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            t to Color.Black.copy(alpha = 1f - eased * s)
        }
        drawRect(
            brush = Brush.verticalGradient(*stops, startY = start, endY = size.height),
            topLeft = androidx.compose.ui.geometry.Offset(0f, start),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - start),
            blendMode = BlendMode.DstIn,
        )
    }

/** How much of the cover, from its bottom up, [coverEdgeFade] fades. */
private const val CoverEdgeFadeFraction = 0.3f

@androidx.annotation.RequiresApi(33)
private fun Modifier.liquidWarpEffect(
    shader: Any,
    time: () -> Float,
): Modifier = this.graphicsLayer {

    shader.setAgslUniform("resolution", size.width, size.height)
    shader.setAgslUniform("time", time())
    renderEffect = agslRenderEffect(shader, "image").asComposeRenderEffect()
    clip = true
}
