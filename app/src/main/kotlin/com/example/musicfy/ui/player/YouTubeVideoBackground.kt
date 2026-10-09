// YouTubeVideoBackground.kt

package com.example.musicfy.ui.player

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

// Frames are sampled at this size for bar detection: a 0.7% step per line, and cheap to read.
private const val LETTERBOX_SAMPLE_SIZE = 144
private const val LETTERBOX_SAMPLE_INTERVAL_MS = 300L
private const val LETTERBOX_ZOOM_MS = 700

private fun VideoSize.displayAspect(): Float =
    if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else 0f

@Composable
fun YouTubeVideoBackground(
    streamUrl: String?,
    isPlaying: Boolean,
    positionMsProvider: () -> Long,

    lyricVideoAnchors: List<LyricVideoAnchor>? = null,

    glassState: GlassState,
    modifier: Modifier = Modifier,
) {
    if (streamUrl.isNullOrBlank()) return
    val context = LocalContext.current
    var isVideoReady by remember(streamUrl) { mutableStateOf(false) }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            prepare()
        }
    }

    var videoAspect by remember(exoPlayer) { mutableFloatStateOf(exoPlayer.videoSize.displayAspect()) }
    var viewSize by remember(exoPlayer) { mutableStateOf(IntSize.Zero) }

    // The video fills the view stretched, and is then scaled and moved as a whole to show it at
    // its real aspect, centre-cropped. Scaling the view rather than the texture keeps the
    // texture's own content the full, untouched frame, which is what bar detection reads.
    val textureView = remember(exoPlayer) {
        TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            pivotX = 0f
            pivotY = 0f
        }
    }

    val videoFrame = remember(textureView) {
        FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = true
        }
    }

    // How much of the frame, per side, is black bar the view zooms past.
    val barInsetX = remember(exoPlayer) { Animatable(0f) }
    val barInsetY = remember(exoPlayer) { Animatable(0f) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val aspect = videoSize.displayAspect()
                if (aspect > 0f) videoAspect = aspect
            }
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    LaunchedEffect(exoPlayer, streamUrl, lyricVideoAnchors) {
        while (isActive) {
            val duration = exoPlayer.duration
            if (duration > 0) {
                val songPositionMs = positionMsProvider()
                val target = lyricVideoAnchors
                    ?.let { resolveAnchoredVideoPositionMs(it, songPositionMs) }
                    ?: (songPositionMs % duration)
                if (abs(exoPlayer.currentPosition - target) > 750) {
                    exoPlayer.seekTo(target.coerceIn(0, duration))
                }
            }
            delay(500)
        }
    }

    // Music videos often bake black bars into the frame, and many only have them in some scenes.
    // Sample the playing frame a few times a second and zoom past whatever bars it has.
    LaunchedEffect(exoPlayer, isVideoReady) {
        if (!isVideoReady) return@LaunchedEffect
        val sample = Bitmap.createBitmap(LETTERBOX_SAMPLE_SIZE, LETTERBOX_SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(LETTERBOX_SAMPLE_SIZE * LETTERBOX_SAMPLE_SIZE)
        val vertical = LetterboxStabilizer()
        val horizontal = LetterboxStabilizer()
        val zoomSpec = tween<Float>(LETTERBOX_ZOOM_MS, easing = FastOutSlowInEasing)

        while (isActive) {
            delay(LETTERBOX_SAMPLE_INTERVAL_MS)
            // A paused frame never changes, and a texture with no frame yet reads as black.
            if (!exoPlayer.isPlaying || !textureView.isAvailable) continue
            val copied = runCatching { textureView.getBitmap(sample) }.isSuccess
            if (!copied) continue
            sample.getPixels(pixels, 0, LETTERBOX_SAMPLE_SIZE, 0, 0, LETTERBOX_SAMPLE_SIZE, LETTERBOX_SAMPLE_SIZE)

            val reading = VideoLetterbox.analyze(pixels, LETTERBOX_SAMPLE_SIZE, LETTERBOX_SAMPLE_SIZE)
            val now = SystemClock.uptimeMillis()
            if (vertical.offer(reading.vertical, now)) {
                launch { barInsetY.animateTo(vertical.inset, zoomSpec) }
            }
            if (horizontal.offer(reading.horizontal, now)) {
                launch { barInsetX.animateTo(horizontal.inset, zoomSpec) }
            }
        }
    }

    LaunchedEffect(textureView) {
        snapshotFlow {
            videoCropTransform(
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                videoAspect = videoAspect,
                insetX = barInsetX.value,
                insetY = barInsetY.value,
            )
        }.collect { transform ->
            textureView.scaleX = transform.scaleX
            textureView.scaleY = transform.scaleY
            textureView.translationX = transform.translationX
            textureView.translationY = transform.translationY
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(400),
        label = "videoBgAlpha",
    )

    AndroidView(
        factory = { _ ->
            videoFrame.apply {
                isEnabled = false
                isClickable = false
                isFocusable = false
                if (childCount == 0) {
                    addView(textureView)
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .graphicsLayer { this.alpha = alpha }
            .glassRoot(glassState, isActive = { isPlaying })
    )
}
