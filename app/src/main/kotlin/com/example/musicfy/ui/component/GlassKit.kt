package com.example.musicfy.ui.component

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
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

@Stable
class GlassState {
    /**
     * The backdrop render node, deliberately typed [Any].
     *
     * `android.graphics.RenderNode` is API 29. Naming it in a property type puts it into this
     * class's accessor signatures, so ART has to resolve it as soon as an accessor is reached -
     * on Android 8 and 9 that is a `NoClassDefFoundError`, even though every real *use* of the
     * node is already version-guarded. Since the glass system backs the nav pill, the player and
     * most screens, that turns into crashes all over the app on older devices.
     *
     * Every touch goes through the guarded helpers below, which cast internally.
     */
    var renderNode by mutableStateOf<Any?>(null)
    var rootPosition by mutableStateOf(Offset.Zero)
}

/*
 * RenderNode helpers. Each is annotated so it is only ever entered above the API level that
 * defines the class, which keeps the reference out of any method reachable on older devices.
 */

@RequiresApi(Build.VERSION_CODES.Q)
internal fun glassNodeFor(existing: Any?, width: Int, height: Int): Any =
    (existing as? RenderNode ?: RenderNode("GlassRoot")).apply { setPosition(0, 0, width, height) }

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeHasContent(node: Any): Boolean = (node as RenderNode).hasDisplayList()

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeBeginRecording(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeEndRecording(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeWidth(node: Any): Int = (node as RenderNode).width

@RequiresApi(Build.VERSION_CODES.S)
internal fun glassNodeHeight(node: Any): Int = (node as RenderNode).height

@RequiresApi(Build.VERSION_CODES.S)
internal fun android.graphics.Canvas.drawGlassNode(node: Any) {
    drawRenderNode(node as RenderNode)
}

fun Modifier.glassRoot(state: GlassState, isActive: () -> Boolean = { true }): Modifier = this
    .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
    .drawWithCache {
        val width = size.width.toInt()
        val height = size.height.toInt()

        val node: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            glassNodeFor(state.renderNode, width, height)
        } else null

        state.renderNode = node

        onDrawWithContent {
            val drawContextCanvas = drawContext.canvas

            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isActive()) {
                val nativeCanvas = glassNodeBeginRecording(node)
                val composeCanvas = Canvas(nativeCanvas)

                drawContext.canvas = composeCanvas
                drawContent()

                drawContext.canvas = drawContextCanvas
                glassNodeEndRecording(node)

                if (glassNodeHasContent(node)) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
                }
            } else {
                drawContent()
            }
        }
    }

/**
 * Backdrop colour for the floating glass chrome - the navigation pill and the mini player.
 *
 * Deliberately a fixed dark tone rather than `MaterialTheme.colorScheme.surfaceContainer`: the
 * Material You colour follows the wallpaper accent and washes these surfaces out. They are drawn
 * with white icons over a white hairline border, so they are meant to read as dark glass.
 */
val GlassChromeColor = Color(0xFF0F0F12)

/** Backdrop blur for that chrome, heavier than the 24f default so it reads as properly frosted. */
const val GlassChromeBlurRadius = 48f

@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    shape: Shape? = null,

    /**
     * Null means DECAL, resolved inside the API 31 guard below.
     *
     * `Shader.TileMode.DECAL` is API 31, and naming it as a default argument makes every caller
     * that omits the parameter evaluate it - throwing NoSuchFieldError on Android 8 through 11.
     * The value is only ever *used* above API 31, so it is resolved there instead.
     */
    tileMode: android.graphics.Shader.TileMode? = null,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }

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
                        tileMode ?: android.graphics.Shader.TileMode.DECAL
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
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassNodeHasContent(node)) {
                val relX = position.x - state.rootPosition.x
                val relY = position.y - state.rootPosition.y
                translate(left = -relX, top = -relY) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
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
