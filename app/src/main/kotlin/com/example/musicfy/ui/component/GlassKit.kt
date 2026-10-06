package com.example.musicfy.ui.component

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
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

/**
 * How far below full resolution a glass surface draws what's under it before blurring.
 *
 * A blur this wide removes everything a full-resolution picture of the backdrop adds - Skia
 * itself shrinks the picture by halves before blurring a large radius - so drawing the backdrop
 * at half (or quarter) size and blurring it by a half (or quarter) radius gives the same glass.
 * What it saves is drawing all of the backdrop that sits under the glass at full resolution, plus
 * the first passes of the blur, every frame the glass or what's under it moves. Small radii keep
 * full resolution: there the detail still shows.
 */
internal fun glassDownscale(effectiveRadius: Float): Int = when {
    effectiveRadius >= 64f -> 4
    effectiveRadius >= 16f -> 2
    else -> 1
}

/** The size a downscaled layout was given, before it was divided down. */
private class FullSize {
    var width = 0
    var height = 0
}

/** Lays its content out at 1/[downscale] of the space it takes, from the top left. */
private fun Modifier.downscaledLayout(downscale: () -> Int, full: FullSize): Modifier = layout { measurable, constraints ->
    val k = downscale().coerceAtLeast(1)
    val fullWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val fullHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    full.width = fullWidth
    full.height = fullHeight
    val placeable = measurable.measure(
        Constraints.fixed((fullWidth + k - 1) / k, (fullHeight + k - 1) / k)
    )
    layout(fullWidth, fullHeight) { placeable.place(0, 0) }
}

/** The last masked glass effect, reused while its radius, size and mask stay the same. */
private class MaskedEffectCache {
    var radius = -1f
    var width = -1f
    var height = -1f
    var tileOrdinal = -1
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

/**
 * The blur and the mask in one effect: the blurred content kept only where [mask] is opaque
 * (DstIn) - what drawing the glass into an offscreen layer and DstIn-ing the mask over it did,
 * without that extra layer.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun maskedBlurEffect(radius: Float, tileMode: android.graphics.Shader.TileMode, mask: android.graphics.Shader): RenderEffect {
    val content = if (radius > 0.5f) {
        android.graphics.RenderEffect.createBlurEffect(radius, radius, tileMode)
    } else {
        android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    }
    return android.graphics.RenderEffect.createBlendModeEffect(
        content,
        android.graphics.RenderEffect.createShaderEffect(mask),
        android.graphics.BlendMode.DST_IN,
    ).asComposeRenderEffect()
}

/**
 * Whatever [draw] draws, blurred by [radius] (the radius actually applied - see
 * [BlurEffectCache.effectiveRadius]) and optionally faded by [mask] (DstIn).
 *
 * Drawn at the resolution [glassDownscale] picks for the radius: into a layer 1/k the size, blurred
 * by radius/k and scaled back up - see [glassDownscale] for why that's the same picture for less
 * work. [draw] works in full-size coordinates and gets the full size; the scaling is done here.
 */
@Composable
internal fun DownscaledBlur(
    radius: () -> Float,
    tileMode: android.graphics.Shader.TileMode?,
    mask: Brush?,
    modifier: Modifier = Modifier,
    draw: DrawScope.(fullSize: Size) -> Unit,
) {
    val currentRadius by rememberUpdatedState(radius)
    // A radius that animates only re-lays this out when it crosses into another resolution step.
    val downscale by remember { derivedStateOf { glassDownscale(currentRadius()) } }
    val maskCache = remember { MaskedEffectCache() }
    val full = remember { FullSize() }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                // Without RenderEffect (below API 31) the mask is applied the old way.
                if (mask != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = mask, blendMode = BlendMode.DstIn)
                        }
                } else Modifier
            )
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .downscaledLayout({ downscale }, full)
                .graphicsLayer {
                    val k = downscale.toFloat()
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                    val r = currentRadius() / k
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val mode = tileMode ?: android.graphics.Shader.TileMode.DECAL
                        if (mask is ShaderBrush) {
                            val quantized = if (r > 0.5f) BlurEffectCache.quantize(r) else 0f
                            val cache = maskCache
                            if (cache.effect == null || cache.radius != quantized || cache.width != size.width ||
                                cache.height != size.height || cache.tileOrdinal != mode.ordinal || cache.mask != mask
                            ) {
                                cache.effect = maskedBlurEffect(quantized, mode, mask.createShader(size))
                                cache.radius = quantized
                                cache.width = size.width
                                cache.height = size.height
                                cache.tileOrdinal = mode.ordinal
                                cache.mask = mask
                            }
                            cache.effect
                        } else {
                            BlurEffectCache.get(r, mode)
                        }
                    } else {
                        null
                    }
                    clip = true
                }
        ) {
            val k = downscale.toFloat()
            val fullSize = Size(full.width.toFloat(), full.height.toFloat())
            scale(1f / k, 1f / k, pivot = Offset.Zero) { draw(fullSize) }
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

    /**
     * Null means DECAL, resolved inside the API 31 guard below.
     *
     * `Shader.TileMode.DECAL` is API 31, and naming it as a default argument makes every caller
     * that omits the parameter evaluate it - throwing NoSuchFieldError on Android 8 through 11.
     * The value is only ever *used* above API 31, so it is resolved there instead.
     */
    tileMode: android.graphics.Shader.TileMode? = null,
    /**
     * Fades the finished glass (backdrop, foundation and tint alike) by this brush's alpha, as a
     * DstIn would. On API 31+ the mask runs inside the blur's own effect; it used to need an
     * offscreen layer of its own around the glass.
     */
    mask: Brush? = null,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }

    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (blurStrengthPx) = rememberPreference(com.example.musicfy.constants.BlurStrengthPxKey, defaultValue = 25f)

    val currentBlurRadius by rememberUpdatedState(blurRadius)

    DownscaledBlur(
        // The blur actually applied, clamped the way BlurEffectCache clamps it.
        radius = {
            if (disableBlur) 0f
            else BlurEffectCache.effectiveRadius(currentBlurRadius() * (blurStrengthPx / 25f))
        },
        tileMode = tileMode,
        mask = mask,
        modifier = modifier
            .onGloballyPositioned { position = it.positionInWindow() }
            .then(if (shape != null) Modifier.clip(shape) else Modifier),
    ) { fullSize ->
        if (foundationColor != null) {
            drawRect(color = foundationColor, size = fullSize)
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
            drawRect(color = tint, size = fullSize)
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
                // Each step fades in over its first slice. The mask used to be DstIn-ed over the
                // step in an offscreen layer of its own - one more full-width buffer per step,
                // every frame the bar's backdrop moved; it now runs inside the step's blur.
                val mask = remember(direction, internalFadeDistance) {
                    if (direction == BlurDirection.TopToBottom) {
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
                }

                GlassPillBackground(
                    state = state,
                    blurRadius = radiusProvider,
                    tint = tint,
                    foundationColor = foundationColor,
                    mask = mask,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .align(if (direction == BlurDirection.TopToBottom) androidx.compose.ui.Alignment.BottomCenter else androidx.compose.ui.Alignment.TopCenter)
                )
            }
        }
    }
}
