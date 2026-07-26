// PlayerBackground.kt
// Extracted from Player.kt's BottomSheet `background = { ... }` slot.

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.ui.component.BottomSheetState

private data class AppleBgState(val isVideo: Boolean, val primaryUrl: String?, val fallbackUrl: String?)

@androidx.annotation.RequiresApi(33)
private fun Modifier.liquidWarpEffect(time: Float): Modifier = composed {
    val shader = remember {
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

/**
 * Full-player background renderer for all [PlayerBackgroundStyle] variants.
 * Extracted verbatim from the old `BottomSheetPlayer`'s `background = { ... }` slot in
 * Player.kt so the ~500-line style `when` isn't inlined into the root player composable.
 */
@Composable
fun PlayerBackgroundRenderer(
    playerBackground: PlayerBackgroundStyle,
    bottomSheetBackgroundColor: Color,
    gradientColors: List<Color>,
    state: BottomSheetState,
    useDarkTheme: Boolean,
    mediaMetadata: MediaMetadata?,
    canvasArtwork: CanvasArtwork?,
    enableCanvas: Boolean,
    isPlaying: Boolean,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bottomSheetBackgroundColor)
    ) {
        // Static gradient base (always active)
        if (playerBackground != PlayerBackgroundStyle.DEFAULT) {
            val colors = gradientColors.ifEmpty {
                listOf(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.surface
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.progress.coerceIn(0f, 1f) }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.first().copy(alpha = 0.55f),
                                bottomSheetBackgroundColor
                            )
                        )
                    )
            )
        }

        // Heavy effects fade in on top
        if (playerBackground != PlayerBackgroundStyle.DEFAULT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.progress.coerceIn(0f, 1f) }
            ) {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(400)).togetherWith(fadeOut(tween(400)))
                            },
                            label = "blurBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl)
                                            .size(100, 100)
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(if (useDarkTheme) 60.dp else 45.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(400)).togetherWith(fadeOut(tween(400)))
                            },
                            label = "gradientBackground"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val gradientColorStops = if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.5f to colors[1],
                                        1.0f to colors[2]
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.6f to colors[0].copy(alpha = 0.7f),
                                        1.0f to Color.Black
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    PlayerBackgroundStyle.GLOW_ANIMATED -> {
                        AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    },
                    label = "GlowAnimatedContent"
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "GlowAnimation")

                        val progress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(20000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "glowProgress"
                        )

                        fun rotatedColorAt(index: Int): Color {
                            val size = colors.size
                            val idx = index.toFloat() + progress * size
                            val a = kotlin.math.floor(idx).toInt() % size
                            val b = (a + 1) % size
                            val frac = idx - kotlin.math.floor(idx)
                            return androidx.compose.ui.graphics.lerp(
                                colors.getOrElse(a) { Color.DarkGray },
                                colors.getOrElse(b) { Color.DarkGray },
                                frac
                            )
                        }

                        fun oscillate(
                            min: Float,
                            max: Float,
                            phase: Float,
                            speed: Float = 1f
                        ): Float {
                            val v = kotlin.math.sin(
                                2f * kotlin.math.PI.toFloat() * (progress * speed + phase)
                            )
                            return min + (max - min) * ((v + 1f) * 0.5f)
                        }

                        val color1 = rotatedColorAt(0)
                        val color2 = rotatedColorAt(1)
                        val color3 = rotatedColorAt(2)
                        val color4 = rotatedColorAt(3)
                        val color5 = rotatedColorAt(4)
                        val color6 = rotatedColorAt(5)

                        val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                        val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                        val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                        val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                        val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                        val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                        val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                        val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                        val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                        val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                        val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                        val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                        val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                        val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                        val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                        val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                        val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                        val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val width = size.width
                                    val height = size.height
                                    val baseColor = Color(0xFF050505)

                                    val brush1 = Brush.radialGradient(
                                        colors = listOf(
                                            color1.copy(alpha = 0.85f),
                                            color1.copy(alpha = 0.5f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o1x, height * o1y),
                                        radius = width * r1
                                    )
                                    val brush2 = Brush.radialGradient(
                                        colors = listOf(
                                            color2.copy(alpha = 0.8f),
                                            color2.copy(alpha = 0.45f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o2x, height * o2y),
                                        radius = width * r2
                                    )
                                    val brush3 = Brush.radialGradient(
                                        colors = listOf(
                                            color3.copy(alpha = 0.75f),
                                            color3.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o3x, height * o3y),
                                        radius = width * r3
                                    )
                                    val brush4 = Brush.radialGradient(
                                        colors = listOf(
                                            color4.copy(alpha = 0.7f),
                                            color4.copy(alpha = 0.35f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o4x, height * o4y),
                                        radius = width * r4
                                    )
                                    val brush5 = Brush.radialGradient(
                                        colors = listOf(
                                            color5.copy(alpha = 0.65f),
                                            color5.copy(alpha = 0.3f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o5x, height * o5y),
                                        radius = width * r5
                                    )
                                    val brush6 = Brush.radialGradient(
                                        colors = listOf(
                                            color6.copy(alpha = 0.6f),
                                            color6.copy(alpha = 0.25f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * o6x, height * o6y),
                                        radius = width * r6
                                    )

                                    onDrawBehind {
                                        drawRect(color = baseColor)
                                        drawRect(brush = brush1)
                                        drawRect(brush = brush2)
                                        drawRect(brush = brush3)
                                        drawRect(brush = brush4)
                                        drawRect(brush = brush5)
                                        drawRect(brush = brush6)
                                    }
                                }
                        )
                    }
                }
            }
            PlayerBackgroundStyle.APPLE_MUSIC -> {
                val isVideo = enableCanvas && canvasArtwork != null
                val bgState = AppleBgState(
                    isVideo = isVideo,
                    primaryUrl = if (isVideo) canvasArtwork?.preferredAnimationUrl else mediaMetadata?.thumbnailUrl,
                    fallbackUrl = if (isVideo) canvasArtwork?.videoUrl else null
                )

                AnimatedContent(
                    targetState = bgState,
                    transitionSpec = {
                        fadeIn(tween(400)).togetherWith(fadeOut(tween(400)))
                    },
                    label = "appleMusicBackground"
                ) { currentBgState ->
                    if (currentBgState.primaryUrl != null || currentBgState.fallbackUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            if (currentBgState.isVideo) {
                                val blurRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 150.dp.toPx() }
                                CanvasArtworkPlayer(
                                    primaryUrl = currentBgState.primaryUrl,
                                    fallbackUrl = currentBgState.fallbackUrl,
                                    isPlaying = isPlaying,
                                    blurRadiusPx = blurRadiusPx,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = -1.2f
                                            scaleY = 1.2f
                                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                        }
                                )
                            } else {
                                val infiniteTransition = rememberInfiniteTransition(label = "appleMusicWarp")
                                val time by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 100f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(100000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "warpTime"
                                )

                                // Layer 1: Full-Screen Blurred Background
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(currentBgState.primaryUrl)
                                        .size(128, 128) // Downsample significantly for performance
                                        .allowHardware(false)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = 1.2f
                                            scaleY = 1.2f
                                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                        } // Scale up to hide warped edges
                                        .let {
                                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                val isSheetTransitioning by remember(state) {
                                                    derivedStateOf { state.progress > 0.01f && state.progress < 0.99f }
                                                }
                                                // Freeze time animation during transition to save GPU
                                                val transitionTime = if (isSheetTransitioning) 0f else time
                                                it.blur(50.dp).liquidWarpEffect(transitionTime)
                                            } else {
                                                it.blur(50.dp)
                                            }
                                        }
                                )
                            }

                            // Layer 3: Dynamic overlay for depth
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = if (currentBgState.isVideo) 0.3f else 0.05f),
                                                Color.Black.copy(alpha = if (currentBgState.isVideo) 0.6f else 0.4f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
            PlayerBackgroundStyle.LIVE_MESH -> {
                val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")

                val anchorRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(80000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "anchorRotation"
                )

                val fastRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(40000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "fastRotation"
                )

                val slowRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(60000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "slowRotation"
                )

                AnimatedContent(
                    targetState = mediaMetadata?.thumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1500)).togetherWith(fadeOut(tween(1500)))
                    },
                    label = "liveMeshBackground"
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Scale up to avoid showing edges during rotation
                                    scaleX = 1.7f
                                    scaleY = 1.7f
                                }
                        ) {
                            val matrix = remember {
                                val m = ColorMatrix()
                                m.setToSaturation(1.8f) // Reduced to avoid neon look
                                m
                            }
                            val colorFilter = ColorFilter.colorMatrix(matrix)

                            // Layer 1: The Anchor (Full Image, Counter-Clockwise)
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .size(128, 128) // Downsample significantly for performance
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(100.dp)
                                    .graphicsLayer { rotationZ = anchorRotation }
                            )

                            // Layer 2: Fast Rotating Crop (Top-Left)
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .size(128, 128) // Downsample significantly for performance
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                alignment = androidx.compose.ui.Alignment.TopStart,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(120.dp)
                                    .graphicsLayer {
                                        rotationZ = fastRotation
                                        alpha = 0.6f
                                    }
                            )

                            // Layer 3: Slow Rotating Crop (Bottom-Right)
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .size(128, 128) // Downsample significantly for performance
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                alignment = androidx.compose.ui.Alignment.BottomEnd,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(120.dp)
                                    .graphicsLayer {
                                        rotationZ = slowRotation
                                        alpha = 0.5f
                                    }
                            )

                            // Global dark tint to prevent neon look + vertical gradient for depth
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
            PlayerBackgroundStyle.DEFAULT -> {
                        // Nothing
                    }
                }
            }
        }
    }
}
