// ScrollAwayCover.kt

package com.example.musicfy.ui.component

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.utils.rememberPreference

private const val CoverZoom = 0.22f
private const val CoverSoftBy = 0.55f
private const val CoverFadeFrom = 0.2f
private const val CoverFadeTo = 0.85f
private const val CoverDim = 0.22f
private const val CoverSoftRadius = 30f

/** 0 up to [from], 1 from [to] on, an s-curve in between */
internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** whether [ScrollAwayCover] can blur its frame here: android 12 and up, and the user hasn't turned blur off */
@Composable
fun rememberCoverBlurAvailable(): Boolean {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    return !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * what a big cover does as the list scrolls it away. the playlist backdrop and the Home hero use
 * the same one: the frame rides up a little slower than the list, zooms in, goes soft, dims,
 * dissolves from the bottom up and fades out.
 *
 * the softness blurs the whole frame, not just the picture in it. the frame is recorded once and
 * drawn a second time through a fixed blur, and scrolling only crossfades between the two. a blur
 * whose radius followed the scroll would be worked out again on every frame of it; this one is
 * worked out once and then only composited, which a modest GPU keeps up with easily.
 *
 * [follow] is how far it travels on top of whatever the list does, as a share of its height: a
 * backdrop pinned behind the list wants about -0.8, a list item that already scrolls wants +0.2.
 * [softFrom] holds the blur back until that much progress, for content that is still moving at rest.
 */
@Composable
fun ScrollAwayCover(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    follow: Float = 0f,
    softFrom: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val frameBlur = rememberCoverBlurAvailable()
    val frame = rememberGraphicsLayer()

    Box(
        modifier = modifier
            .graphicsLayer {
                val p = progress()
                translationY = p * size.height * follow
                val zoom = 1f + p * CoverZoom
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0.35f)
                alpha = 1f - smoothstep(CoverFadeFrom, CoverFadeTo, p)
                // the bottom dissolve cuts into its own buffer; at rest there's nothing to cut
                compositingStrategy = if (p > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val p = progress()
                if (p > 0f) {
                    drawRect(Color.Black.copy(alpha = CoverDim * p))
                    val clear = 0.1f + 0.45f * smoothstep(0f, 0.8f, p)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            (1f - clear) to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        // the sharp frame. it sits on its own layer and never reads the scroll, so scrolling
        // doesn't record it again (which would make the soft copy blur again)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .then(
                    if (frameBlur) {
                        Modifier.drawWithContent {
                            frame.record { this@drawWithContent.drawContent() }
                            drawLayer(frame)
                        }
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )
        if (frameBlur) {
            // the soft copy: the same recording through a fixed blur, so only its alpha moves.
            // a sibling of the sharp frame, never inside it, so the recording can't contain itself
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = smoothstep(softFrom, CoverSoftBy, progress())
                        renderEffect = BlurEffectCache.get(CoverSoftRadius, Shader.TileMode.CLAMP)
                        clip = true
                    }
                    .drawBehind { drawLayer(frame) },
            )
        }
    }
}
