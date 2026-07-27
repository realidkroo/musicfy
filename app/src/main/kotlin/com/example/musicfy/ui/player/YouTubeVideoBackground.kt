// YouTubeVideoBackground.kt
// Plays the official YouTube music video for the current track as a real (ExoPlayer, not
// WebView/embed) video layer inside the cover art card, zoomed/cropped the same way the static
// cover is. Muted — audio keeps coming from the real playback pipeline, not this video's own
// track. Fades in once the first frame renders; the caller keeps the static cover underneath so
// there's never a blank gap while resolving/loading.

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

@Composable
fun YouTubeVideoBackground(
    streamUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    if (streamUrl.isNullOrBlank()) return
    val context = LocalContext.current
    var isVideoReady by remember(streamUrl) { mutableStateOf(false) }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
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

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
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
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    )
}
