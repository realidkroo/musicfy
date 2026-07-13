package com.example.musicfy.ui.component

import android.graphics.RenderNode
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * RenderNode-based glass blur system ported from weatherify.
 * Captures the rendered content of a root composable and allows
 * re-drawing it with native hardware blur in other locations.
 */
@Stable
class GlassState {
    var renderNode by mutableStateOf<RenderNode?>(null)
    var rootPosition by mutableStateOf(Offset.Zero)
}

fun Modifier.glassRoot(state: GlassState): Modifier = this
    .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
    .drawWithCache {
        val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("GlassRoot").apply {
                setPosition(0, 0, size.width.toInt(), size.height.toInt())
            }
        } else null
        
        state.renderNode = node

        onDrawWithContent {
            val drawContextCanvas = drawContext.canvas
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val nativeCanvas = node.beginRecording()
                val composeCanvas = Canvas(nativeCanvas)

                drawContext.canvas = composeCanvas
                drawContent()
                
                drawContext.canvas = drawContextCanvas
                node.endRecording()
                
                drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
            } else {
                drawContent()
            }
        }
    }

@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: Float = 24f,
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    shape: Shape? = null,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInWindow() }
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.5f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadius,
                        blurRadius,
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
                clip = true
            }
    ) {
        if (foundationColor != null) {
            drawRect(color = foundationColor)
        }
        val node = state.renderNode
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val relX = position.x - state.rootPosition.x
            val relY = position.y - state.rootPosition.y
            translate(left = -relX, top = -relY) {
                drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
            }
        }
        if (tint != Color.Transparent) {
            drawRect(color = tint)
        }
    }
}

enum class BlurDirection { TopToBottom, BottomToTop }

@Composable
fun ProgressiveGlassBackground(
    state: GlassState,
    maxBlurRadius: Float = 24f,
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    direction: BlurDirection = BlurDirection.TopToBottom,
    modifier: Modifier = Modifier
) {
    val steps = 5
    Box(modifier = modifier) {
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            // Quadratic curve for smoother visual radius growth
            val radius = maxBlurRadius * (fraction * fraction)
            
            val fadeStart = (i - 1).toFloat() / steps
            val heightFraction = 1f - fadeStart
            
            if (heightFraction > 0f) {
                val internalFadeDistance = (1f / steps) / heightFraction
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .align(if (direction == BlurDirection.TopToBottom) androidx.compose.ui.Alignment.BottomCenter else androidx.compose.ui.Alignment.TopCenter)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val brush = if (direction == BlurDirection.TopToBottom) {
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    internalFadeDistance to Color.Black,
                                    1f to Color.Black
                                )
                            } else {
                                Brush.verticalGradient(
                                    0f to Color.Black,
                                    (1f - internalFadeDistance) to Color.Black,
                                    1f to Color.Transparent
                                )
                            }
                            drawRect(brush = brush, blendMode = BlendMode.DstIn)
                        }
                ) {
                    GlassPillBackground(
                        state = state,
                        blurRadius = radius,
                        tint = tint,
                        foundationColor = foundationColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
