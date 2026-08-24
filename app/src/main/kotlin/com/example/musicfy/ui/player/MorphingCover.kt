// MorphingCover.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.musicfy.constants.PlayVideoBackgroundKey
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
    val miniArtSize: Dp,
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
    val miniArtSizePx: Float,
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
            val artW = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)

            val lp = lyricsProgressProvider()
            if (lp <= 0f) {
                floatArrayOf(artX, artY, artW, artH)
            } else {

                val lyricsX = lerpF(endpointsPx.miniArtXPx, endpointsPx.lyricsArtXPx, p) + hOffset
                val lyricsY = lerpF(endpointsPx.miniArtYPx, endpointsPx.lyricsArtYPx, p)
                val lyricsSize = lerpF(endpointsPx.miniArtSizePx, endpointsPx.lyricsArtSizePx, p)
                floatArrayOf(
                    lerpF(artX, lyricsX, lp),
                    lerpF(artY, lyricsY, lp),
                    lerpF(artW, lyricsSize, lp),
                    lerpF(artH, lyricsSize, lp),
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

    coverStyle: PlayerCoverStyle = PlayerCoverStyle.EDGE_TO_EDGE,

    backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_GRADIENT,

    editMode: Boolean = false,

    onLongPressCover: (() -> Unit)? = null,

    onArtBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val emptyQueue = remember { kotlinx.coroutines.flow.MutableStateFlow(com.example.musicfy.ui.player.models.QueueState()) }
    val queueState by (playerConnection?.uiState?.queueState ?: emptyQueue).collectAsState()

    if (playerConnection != null) {
        LaunchedEffect(queueState.currentIndex, queueState.items) {
            val nextUrl = queueState.items.getOrNull(queueState.currentIndex + 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)
                ?: return@LaunchedEffect

            val request = ImageRequest.Builder(context)
                .data(nextUrl)
                .size(CoilSize(1200, 1200))
                .precision(Precision.INEXACT)
                .build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    val endpoints = remember(maxWidth, maxHeight, statusBarTop, coverStyle, 2) {
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

        val miniTextX = miniArtX + miniArtSize + 12.dp
        val miniTextWidth = (miniPlayX - 10.dp - miniTextX).coerceAtLeast(0.dp)

        val artBox = coverArtBox(coverStyle, maxWidth, maxHeight, statusBarTop)

        MorphEndpoints(
            miniArtSize = miniArtSize,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            miniTextX = miniTextX,
            miniTextWidth = miniTextWidth,

            lyricsArtSize = 60.dp,
            lyricsArtX = 36.dp,
            lyricsArtY = statusBarTop + 28.dp,
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
                miniArtSizePx = endpoints.miniArtSize.toPx(),
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

    val isDiscStyle = coverStyle.isDisc

    val longPressEnabled by remember {
        derivedStateOf { progressProvider() > 0.9f && lyricsProgressProvider() < 0.1f }
    }

    val discSpinActive by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.9f && lyricsProgressProvider() < 0.6f }
    }

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

    val playVideoBackground by rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    var videoInfo by remember(trackInfo.mediaId) { mutableStateOf<OfficialMusicVideo?>(null) }
    LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist, playVideoBackground) {
        val mediaId = trackInfo.mediaId
        val titleStr = trackInfo.title
        val artistStr = trackInfo.artist
        if (!playVideoBackground || mediaId.isBlank() || titleStr.isBlank() || artistStr.isBlank()) {
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

    val lyricsSyncEnabled by rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = false)
    val currentLyrics by playerConnection?.currentLyrics?.collectAsState(initial = null)
        ?: androidx.compose.runtime.mutableStateOf(null)
    var lyricVideoAnchors by remember(trackInfo.mediaId) { mutableStateOf<List<LyricVideoAnchor>?>(null) }
    LaunchedEffect(videoInfo?.videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val mediaId = trackInfo.mediaId
        val videoId = videoInfo?.videoId
        val lyricsRaw = currentLyrics?.lyrics
            ?.takeIf { it.isNotBlank() && it != com.example.musicfy.db.entities.LyricsEntity.LYRICS_NOT_FOUND }
        if (!lyricsSyncEnabled || videoId == null || lyricsRaw == null) {
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

                            alpha = (progressProvider() / PILL_FADE_END).coerceIn(0f, 1f)

                            clip = true
                        }

                        .background(containerColor)
                ) {

                    if (backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {

                        PlayerBackgroundContent(
                            style = backgroundStyle,
                            thumbnailUrl = trackInfo.thumbnailUrl,
                            pureBlack = pureBlack,
                            modifier = Modifier.requiredSize(maxWidth, maxHeight)
                        )
                    } else if (playVideoBackground && videoInfo != null) {
                        VideoBackdropBlur(
                            glassState = videoGlassState,
                            modifier = Modifier.requiredSize(maxWidth, maxHeight)
                        )
                    } else {

                        CoverGradientBackdrop(
                            thumbnailUrl = trackInfo.thumbnailUrl,
                            width = maxWidth,
                            height = maxHeight,
                            animate = warpClockActive,
                            shader = warpShader,
                            timeProvider = { warpTimeState.floatValue },
                        )
                    }
                }
            }
        }

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

                        if (longPressEnabled && onLongPressCover != null) {
                            Modifier.pointerInput(onLongPressCover) {
                                detectTapGestures(onLongPress = { onLongPressCover() })
                            }
                        } else Modifier
                    )
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
                ) {

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(trackInfo.thumbnailUrl?.resize(1200, 1200))
                        .allowHardware(true)
                        .crossfade(300)

                        .size(CoilSize(1200, 1200))
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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
                        modifier = Modifier.fillMaxSize()
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

        Box(
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
                }
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = trackInfo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,

                    softWrap = false,
                )
                val subtitle = listOf(trackInfo.artist, trackInfo.album)
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

@Composable
private fun VideoBackdropBlur(
    glassState: GlassState,
    modifier: Modifier = Modifier,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    if (disableBlur) return

    Box(modifier = modifier) {

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // createBlurEffect is API 31; this block runs on every device.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            90f, 90f, android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
        ) {
            drawFillScaledVideoNode(glassState, flip = false)
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.6f
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            90f, 90f, android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
        ) {
            drawFillScaledVideoNode(glassState, flip = true)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFillScaledVideoNode(
    glassState: GlassState,
    flip: Boolean,
) {
    val node = glassState.renderNode
    if (node == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    if (!glassNodeHasContent(node)) return
    val nodeWidth = glassNodeWidth(node).toFloat()
    val nodeHeight = glassNodeHeight(node).toFloat()
    if (nodeWidth <= 0f || nodeHeight <= 0f) return

    val fillScale = maxOf(size.width / nodeWidth, size.height / nodeHeight)
    val scaledWidth = nodeWidth * fillScale
    val scaledHeight = nodeHeight * fillScale
    val offsetX = (size.width - scaledWidth) / 2f
    val offsetY = (size.height - scaledHeight) / 2f

    withTransform({
        translate(left = offsetX, top = if (flip) offsetY + scaledHeight else offsetY)
        scale(scaleX = fillScale, scaleY = if (flip) -fillScale else fillScale, pivot = Offset.Zero)
    }) {
        drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
    }
}

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
