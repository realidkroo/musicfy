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
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.utils.rememberPreference

// rendernode-based glass blur system ported from weatherify captures the rendered
@Stable
class GlassState {
    var renderNode by mutableStateOf<RenderNode?>(null)
    var rootPosition by mutableStateOf(Offset.Zero)
}

fun Modifier.glassRoot(state: GlassState, isActive: () -> Boolean = { true }): Modifier = this
    .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
    .drawWithCache {
        val width = size.width.toInt()
        val height = size.height.toInt()

        val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            val existingNode = state.renderNode
            if (existingNode != null) {
                existingNode.setPosition(0, 0, width, height)
                existingNode
            } else {
                RenderNode("GlassRoot").apply {
                    setPosition(0, 0, width, height)
                }
            }
        } else null

        state.renderNode = node

        onDrawWithContent {
            val drawContextCanvas = drawContext.canvas
            // recording into the rendernode and immediately redrawing it back onto the
            // canvas is a full duplicate draw pass of this subtree's content skip it
            // nothing downstream is currently reading the captured node (isactive is a
            // read same category as the provider lambdas consumers pass in so checking
            // never triggers recomposition) — falls back to the plain drawcontent() path
            // exactly what already happens today when there's no consumer or on
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isActive()) {
                val nativeCanvas = node.beginRecording()
                val composeCanvas = Canvas(nativeCanvas)

                drawContext.canvas = composeCanvas
                drawContent()

                drawContext.canvas = drawContextCanvas
                node.endRecording()

                if (node.hasDisplayList()) {
                    drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
                }
            } else {
                drawContent()
            }
        }
    }

@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    shape: Shape? = null,
    // decal (the default) samples transparent past the layer's own bounds which
    // want when the layer is meant to fade out at its edges (eg a pill floating
    // clamp instead replicates the edge pixel outward so a layer whose bounds
    // area you actually want blurred edge-to-edge (no fade) reads as fully
    // its true boundary — use it wherever the caller's own bounds are the
    tileMode: android.graphics.Shader.TileMode = android.graphics.Shader.TileMode.DECAL,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    // "disable blur" replaces the actual blurred capture with a flat
    // fallback everywhere in the app — same gate for every consumer of this
    // (nav bar mini-player pill profile menu detail top bar home top bar
    // rather than each call site reimplementing its own on/off branch
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInWindow() }
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .graphicsLayer {
                val currentBlur = if (disableBlur) 0f else blurRadius()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentBlur > 1f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        currentBlur,
                        currentBlur,
                        tileMode
                    ).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
                clip = true
            }
    ) {
        if (foundationColor != null) {
            drawRect(color = foundationColor)
        }
        if (!disableBlur) {
            val node = state.renderNode
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && node.hasDisplayList()) {
                val relX = position.x - state.rootPosition.x
                val relY = position.y - state.rootPosition.y
                translate(left = -relX, top = -relY) {
                    drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
                }
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
    maxBlurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    direction: BlurDirection = BlurDirection.TopToBottom,
    modifier: Modifier = Modifier,
    steps: Int = 5
) {
    Box(modifier = modifier) {
        for (i in 1..steps) {
            val fraction = i.toFloat() / steps
            // quadratic curve for smoother visual radius growth
            val radiusProvider = { maxBlurRadius() * (fraction * fraction) }
            
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
                        .drawWithCache {
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
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                            }
                        }
                ) {
                    GlassPillBackground(
                        state = state,
                        blurRadius = radiusProvider,
                        tint = tint,
                        foundationColor = foundationColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
