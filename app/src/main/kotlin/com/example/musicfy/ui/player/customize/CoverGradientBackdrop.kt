// CoverGradientBackdrop.kt

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

    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("time", time())
    renderEffect = android.graphics.RenderEffect
        .createRuntimeShaderEffect(shader, "image")
        .asComposeRenderEffect()
    clip = true
}
