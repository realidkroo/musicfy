// covergradientbackdropkt
// the player s default backdrop a tiny blurred copy of the artwork
// through a liquid warp agsl shader extracted so the customization page
// same thing rather than an approximation of it

// it previously lived inline in morphingcover and the editor drew a
// bitmap no warp two implementations of the background meant the editor s
// disagreed with the player it was previewing there is now one

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size as CoilSize
import com.example.musicfy.ui.player.BackdropBlurTransformation
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.isActive

// the warp program hoisted so a caller can create it once and keep it alive
@Composable
fun rememberWarpShader(): android.graphics.RuntimeShader? = remember {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.graphics.RuntimeShader(
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

// the caller may be measuring this with a smaller animating constraint
@Composable
fun CoverGradientBackdrop(
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    shader: android.graphics.RuntimeShader? = rememberWarpShader(),
    timeProvider: (() -> Float)? = null,
) {
    val context = LocalContext.current

    val ownTime = remember { mutableFloatStateOf(0f) }
    val runOwnClock = timeProvider == null
    LaunchedEffect(animate, runOwnClock) {
        if (!animate || !runOwnClock) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                // absolute frame time mod 100 rather than delta accumulation so restarting
                // loop never drifts
                ownTime.floatValue = ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)
            }
        }
    }
    val time = timeProvider ?: { ownTime.floatValue }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl?.resize(48, 48))
                .allowHardware(false)
                .transformations(BackdropBlurTransformation(radiusPx = 4))
                // decode at the source s own 48x48 not at the node s size this node is
                // requiredsize width height so coil would otherwise upscale a 48px image
                // a full screen software bitmap ~10mb since allowhardware is false and then
                // blur it at that size decoding small and letting fillbounds + the 16x layer
                // stretch it on the gpu looks identical for ~9kb
                .size(CoilSize(48, 48))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .requiredSize(width, height)
                .graphicsLayer {
                    scaleX = 1.6f
                    scaleY = 1.6f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .let { if (shader != null) it.liquidWarpEffect(shader, time) else it }
        )
        // legibility wash
        Box(
            modifier = Modifier
                .requiredSize(width, height)
                .background(Color.Black.copy(alpha = 0.22f))
        )
    }
}

@androidx.annotation.RequiresApi(33)
private fun Modifier.liquidWarpEffect(
    shader: android.graphics.RuntimeShader,
    time: () -> Float,
): Modifier = this.graphicsLayer {
    // both reads happen here in the draw phase that is what keeps the warp s
    // per frame updates from ever recomposing the tree above it
    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("time", time())
    renderEffect = android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader, "image")
        .asComposeRenderEffect()
    clip = true
}
