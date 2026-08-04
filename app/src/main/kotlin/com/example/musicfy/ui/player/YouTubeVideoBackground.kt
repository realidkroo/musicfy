// YouTubeVideoBackground.kt
// Plays the official YouTube music video for the current track as a real (ExoPlayer, not
// WebView/embed) video layer inside the cover art card, zoomed/cropped the same way the static
// cover is. Muted — audio keeps coming from the real playback pipeline, not this video's own
// track. Fades in once the first frame renders; the caller keeps the static cover underneath so
// there's never a blank gap while resolving/loading.
//
// Wrapped in glassRoot so MorphingCover.kt's fullscreen ambient backdrop can capture these same
// live decoded frames and redraw a heavily blurred, mirrored copy of them as its background
// instead of decoding the video a second time — see VideoBackdropBlur in MorphingCover.kt.

package com.example.musicfy.ui.player

import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun YouTubeVideoBackground(
    streamUrl: String?,
    isPlaying: Boolean,
    positionMsProvider: () -> Long,
    // Song-time -> video-time anchors from the "sync with lyrics" sub-option (see
    // YouTubeLyricsVideoSync.kt). Null falls back to plain proportional position sync below.
    lyricVideoAnchors: List<LyricVideoAnchor>? = null,
    // Shared with the fullscreen ambient backdrop (VideoBackdropBlur in MorphingCover.kt) — its
    // blurred/mirrored copy reads whatever gets captured here instead of decoding twice.
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

    val aspectRatioFrameLayout = remember(exoPlayer) {
        AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatioFrameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
                }
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

    // Keeps the video's own timeline aligned with the song's real position — without this,
    // seeking within the song (or ordinary drift against a video of a different length) leaves
    // this stuck wherever it happened to be. When lyric anchors are available, the target is the
    // video moment actually singing the current lyric line instead of just the same fraction of
    // the way through.
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

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(400),
        label = "videoBgAlpha",
    )

    AndroidView(
        factory = { _ ->
            aspectRatioFrameLayout.apply {
                isEnabled = false
                isClickable = false
                isFocusable = false
                if (childCount == 0) {
                    val textureView = TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    addView(textureView)
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .glassRoot(glassState, isActive = { isPlaying })
    )
}
