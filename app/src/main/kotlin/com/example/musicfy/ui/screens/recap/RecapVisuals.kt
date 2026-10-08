// RecapVisuals.kt
//
// The pieces the Musicfy page, the recap story and Stats share: one clock, the dot grid, the giant
// slow-moving words behind everything, the moving cover/artist gradient, odometer numbers and the
// gradient-to-white text. All of them animate in draw or layer lambdas only, so a running page
// redraws but never recomposes.

package com.example.musicfy.ui.screens.recap

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import android.content.Context
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.ui.component.asAgslPaintShader
import com.example.musicfy.ui.component.createAgslShader
import com.example.musicfy.ui.component.setAgslUniform
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Clock

/**
 * One time base for the page and the story. The giant words' positions are a pure function of it,
 * so when the story takes over from the page the column carries on exactly where it was.
 */
internal object RecapClock {
    private val epoch = System.nanoTime()
    fun seconds(frameNanos: Long): Double = (frameNanos - epoch) / 1_000_000_000.0
}

@Stable
internal class RecapTime {
    var frameNanos by mutableLongStateOf(System.nanoTime())

    val seconds: Double get() = RecapClock.seconds(frameNanos)

    /** Seconds wrapped to a small range, for sin/cos sway where float precision matters more than continuity. */
    val sway: Float get() = (seconds % 3600.0).toFloat()
}

/** Ticks every frame while [running]. Read it only inside draw/graphicsLayer lambdas. */
@Composable
internal fun rememberRecapTime(running: Boolean = true): RecapTime {
    val time = remember { RecapTime() }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withInfiniteAnimationFrameNanos { time.frameNanos = it }
    }
    return time
}

// ---------------------------------------------------------------------------------------------
// Easing

internal fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)
internal fun easeInCubic(t: Float): Float = t * t * t
internal fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

internal fun easeOutBack(t: Float, overshoot: Float = 1.5f): Float {
    val c3 = overshoot + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + overshoot * x * x
}

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Item [index] of [count]'s share of a staggered 0..1 [progress]; each item runs for [length] of it. */
internal fun stagger(progress: Float, index: Int, count: Int, length: Float = 0.55f): Float {
    if (count <= 1) return progress.coerceIn(0f, 1f)
    val start = index * (1f - length) / (count - 1)
    return ((progress - start) / length).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------------------------
// Dot grid

/** The square dot pattern behind the page header and the story: one tiled shader, one draw. */
internal fun Modifier.recapDots(
    color: () -> Color,
    alpha: () -> Float = { 1f },
    spacing: Dp = 17.dp,
    radius: Dp = 1.3.dp,
): Modifier = drawWithCache {
    val step = spacing.toPx().roundToInt().coerceAtLeast(4)
    val tile = ImageBitmap(step, step)
    androidx.compose.ui.graphics.Canvas(tile).drawCircle(
        Offset(step / 2f, step / 2f),
        radius.toPx(),
        Paint().apply {
            this.color = Color.White
            isAntiAlias = true
        },
    )
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    var lastColor = Color.Unspecified
    var filter: ColorFilter? = null
    // behind the content: over the giant words (drawn before this), under any children
    onDrawBehind {
        val a = alpha()
        if (a <= 0.004f) return@onDrawBehind
        val c = color()
        if (c != lastColor) {
            lastColor = c
            filter = ColorFilter.tint(c, BlendMode.SrcIn)
        }
        drawRect(brush, alpha = a, colorFilter = filter)
    }
}

// ---------------------------------------------------------------------------------------------
// Giant words

/** The text-plane font size: one size everywhere, so a column handed between screens matches. */
internal val GiantFontSize = 178.dp

@Stable
internal class GiantRow(word: String, filled: Boolean) {
    var word by mutableStateOf(word)

    /** 0 = empty, 1 = full. Falling, it slides out to [side]; rising, it slides in from [side]. */
    val fill = Animatable(if (filled) 1f else 0f)
    var side by mutableFloatStateOf(1f)

    suspend fun slideOut(durationMs: Int = 520) {
        side = -1f
        fill.animateTo(0f, androidx.compose.animation.core.tween(durationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }

    suspend fun slideIn(newWord: String = word, durationMs: Int = 900) {
        if (fill.value <= 0.001f) {
            word = newWord
        } else if (newWord != word) {
            slideOut()
            word = newWord
        }
        side = 1f
        fill.animateTo(1f, androidx.compose.animation.core.tween(durationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
}

/**
 * Rows of one huge word, endlessly sliding. Rotated 90° the rows read as columns (the page header
 * shows only row 0, hugging the right edge); turning the plane back is the story's "warp" from
 * columns into rows. Rows are indexed -[half]..[half] from the plane's centre.
 */
@Stable
internal class GiantTextState(word: String, val half: Int = 4, filledRows: IntRange = 0..0) {
    val rows: List<GiantRow> = (-half..half).map { GiantRow(word, it in filledRows) }
    fun row(index: Int): GiantRow = rows[index + half]

    val rotation = Animatable(90f)

    /** Plane centre as a fraction of the width. */
    val centerX = Animatable(PageColumnCenterX)

    /** Plane centre's offset from the vertical middle, in px, screen space. */
    val offsetY = Animatable(0f)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    /** Read in draw, so a colour made of animated values follows them without recomposing. */
    var color: () -> Color = { Color(0xFF272727) }

    companion object {
        const val PageColumnCenterX = 0.86f
    }
}

/** Half-resolution bitmaps of each word, so a 178dp glyph run is a textured quad, not a path fill. */
private class GiantWordCache(private val measurer: TextMeasurer) {
    class Word(val image: ImageBitmap, val tileWidth: Float, val drawSize: IntSize, val capCenterY: Float)

    private val words = HashMap<String, Word>()

    fun get(word: String, fontPx: Float, density: Density): Word = words.getOrPut(word) {
        val renderScale = 0.5f
        val sizePx = fontPx * renderScale
        val style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (sizePx / density.density / density.fontScale).sp,
            letterSpacing = (-0.03).em,
            color = Color.White,
        )
        val layout = measurer.measure(word, style, softWrap = false, maxLines = 1, overflow = TextOverflow.Visible)
        val w = layout.size.width.coerceAtLeast(1)
        val h = layout.size.height.coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawText(layout)
        }
        // Inter's cap height is ~0.727 em: centre the capitals, not the line box, on the row
        val capCenter = layout.firstBaseline - 0.727f * sizePx / 2f
        Word(
            image = image,
            tileWidth = (w + sizePx * 0.22f) / renderScale,
            drawSize = IntSize((w / renderScale).roundToInt(), (h / renderScale).roundToInt()),
            capCenterY = capCenter / renderScale,
        )
    }
}

/**
 * Draws a [GiantTextState] across the whole of its bounds. [centerY] puts the plane's centre
 * somewhere other than the middle - the page header pins it to the middle of the *screen*, so the
 * column it shows is the very one the full-screen story continues.
 */
internal fun Modifier.giantText(
    state: GiantTextState,
    time: RecapTime,
    measurer: TextMeasurer,
    centerY: (DrawScope.() -> Float)? = null,
): Modifier = drawWithCache {
    val cache = GiantWordCache(measurer)
    val fontPx = GiantFontSize.toPx()
    val capHeight = 0.727f * fontPx
    val pitch = capHeight * 1.16f
    val baseSpeed = 22.dp.toPx()
    var lastColor = Color.Unspecified
    var filter: ColorFilter? = null
    onDrawBehind {
        val planeAlpha = state.alpha.value
        if (planeAlpha <= 0.003f) return@onDrawBehind
        val color = state.color()
        if (color != lastColor) {
            lastColor = color
            filter = ColorFilter.tint(color, BlendMode.SrcIn)
        }
        val span = hypot(size.width, size.height) * 1.1f / state.scale.value.coerceAtLeast(0.2f)
        val seconds = time.seconds
        withTransform({
            translate(state.centerX.value * size.width, (centerY?.invoke(this@onDrawBehind) ?: (size.height / 2f)) + state.offsetY.value)
            rotate(state.rotation.value, pivot = Offset.Zero)
            scale(state.scale.value, state.scale.value, pivot = Offset.Zero)
        }) {
            state.rows.forEachIndexed { i, row ->
                val fill = row.fill.value
                if (fill <= 0.002f) return@forEachIndexed
                val index = i - state.half
                val word = cache.get(row.word, fontPx, this)
                val direction = if (index % 2 == 0) -1.0 else 1.0
                val speed = baseSpeed * (1.0 + 0.18 * ((index + 8) % 3))
                val phase = ((seconds * speed) % word.tileWidth) * direction
                val slide = (1f - easeOutCubic(fill)) * span * row.side
                val base = phase.toFloat() + slide
                val tiles = ceil((base + span / 2f) / word.tileWidth)
                var x = base - tiles * word.tileWidth
                val top = index * pitch - word.capCenterY
                while (x < span / 2f) {
                    drawImage(
                        image = word.image,
                        dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
                        dstSize = word.drawSize,
                        alpha = planeAlpha * fill,
                        colorFilter = filter,
                    )
                    x += word.tileWidth
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Moving gradient from a picture

/** Four tones taken from a cover or an artist photo, dark enough that white text always reads. */
@Stable
internal class RecapPalette(val c0: Color, val c1: Color, val c2: Color, val c3: Color) {
    companion object {
        val Story = RecapPalette(Color(0xFF0C0C0C), Color(0xFF0C0C0C), Color(0xFF0C0C0C), Color(0xFF0C0C0C))

        /** The mock's red, for when a picture can't be read. */
        val Fallback = RecapPalette(Color(0xFF732C2A), Color(0xFF7A3B26), Color(0xFF4E1D1C), Color(0xFF6B3324))
    }
}

internal fun lerp(a: RecapPalette, b: RecapPalette, t: Float): RecapPalette = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> RecapPalette(lerp(a.c0, b.c0, t), lerp(a.c1, b.c1, t), lerp(a.c2, b.c2, t), lerp(a.c3, b.c3, t))
}

internal object RecapPalettes {
    private val cache = LruCache<String, RecapPalette>(24)

    suspend fun extract(context: Context, url: String?): RecapPalette {
        if (url.isNullOrBlank()) return RecapPalette.Fallback
        cache.get(url)?.let { return it }
        return try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url.resize(120, 120))
                    .size(96, 96)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                    ?: return@withContext RecapPalette.Fallback
                val palette = withContext(Dispatchers.Default) {
                    val p = Palette.from(bitmap).maximumColorCount(16).generate()
                    val dominant = p.swatches.maxByOrNull { it.population }?.rgb ?: return@withContext RecapPalette.Fallback
                    RecapPalette(
                        c0 = tone(dominant, 0.30f, 0f),
                        c1 = tone(p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: dominant, 0.35f, 14f),
                        c2 = tone(p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: dominant, 0.19f, -10f),
                        c3 = tone(p.mutedSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: dominant, 0.26f, 24f),
                    )
                }
                cache.put(url, palette)
                palette
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RecapPalette.Fallback
        }
    }

    // the swatch's hue at a fixed lightness, saturation capped; [hueShift] keeps two swatches of
    // the same colour from collapsing into one flat tone
    private fun tone(argb: Int, lightness: Float, hueShift: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        val grey = hsl[1] < 0.08f
        hsl[0] = (hsl[0] + if (grey) 0f else hueShift + 360f) % 360f
        hsl[1] = if (grey) hsl[1] else hsl[1].coerceIn(0.32f, 0.68f)
        hsl[2] = lightness
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/**
 * A slow mesh of the palette's four colours, bent by a gentle warp. Android 13+ draws it as one
 * shader pass (blob centres are worked out here, so the GPU does two sines a pixel); older phones
 * get three soft blobs over the base colour.
 */
@Composable
internal fun MeshGradient(
    palette: () -> RecapPalette,
    time: RecapTime,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) createAgslShader(MeshShaderSource) else null }
    Spacer(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                val a = alpha()
                if (a <= 0.003f) return@onDrawBehind
                val p = palette()
                val t = time.sway * 0.22f
                val aspect = size.height / size.width
                val centers = floatArrayOf(
                    0.15f + 0.30f * sin(t * 0.9f), aspect * (0.18f + 0.14f * cos(t * 0.7f)),
                    0.85f + 0.22f * cos(t * 0.8f + 1.3f), aspect * (0.38f + 0.20f * sin(t * 0.6f + 2.1f)),
                    0.25f + 0.28f * cos(t * 0.55f + 3.2f), aspect * (0.72f + 0.16f * sin(t * 0.85f + 0.7f)),
                    0.80f + 0.20f * sin(t * 0.75f + 4.1f), aspect * (0.94f + 0.10f * cos(t * 0.95f)),
                )
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    MeshShader.setUniforms(shader, size.width, size.height, t, centers, p)
                    drawRect(ShaderBrush(shader.asAgslPaintShader()), alpha = a)
                } else {
                    drawMeshFallback(p, centers, a)
                }
            }
        },
    )
}

private fun DrawScope.drawMeshFallback(p: RecapPalette, centers: FloatArray, alpha: Float) {
    drawRect(p.c0, alpha = alpha)
    val blobs = arrayOf(p.c1, p.c2, p.c3)
    val radius = size.width * 0.95f
    for (i in blobs.indices) {
        val c = Offset(centers[(i + 1) * 2] * size.width, centers[(i + 1) * 2 + 1] * size.width)
        drawCircle(
            Brush.radialGradient(listOf(blobs[i], blobs[i].copy(alpha = 0f)), center = c, radius = radius),
            radius = radius,
            center = c,
            alpha = alpha,
        )
    }
}

private object MeshShader {
    @RequiresApi(33)
    fun setUniforms(shader: Any, w: Float, h: Float, t: Float, centers: FloatArray, p: RecapPalette) {
        shader.setAgslUniform("resolution", w, h)
        shader.setAgslUniform("time", t)
        val s = shader as android.graphics.RuntimeShader
        s.setFloatUniform("a", centers[0], centers[1])
        s.setFloatUniform("b", centers[2], centers[3])
        s.setFloatUniform("c", centers[4], centers[5])
        s.setFloatUniform("d", centers[6], centers[7])
        s.setFloatUniform("c0", p.c0.red, p.c0.green, p.c0.blue)
        s.setFloatUniform("c1", p.c1.red, p.c1.green, p.c1.blue)
        s.setFloatUniform("c2", p.c2.red, p.c2.green, p.c2.blue)
        s.setFloatUniform("c3", p.c3.red, p.c3.green, p.c3.blue)
    }
}

private const val MeshShaderSource = """
uniform float2 resolution;
uniform float time;
uniform float2 a;
uniform float2 b;
uniform float2 c;
uniform float2 d;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;

float weight(float2 p, float2 q) {
    float2 v = p - q;
    float w = 1.0 / (0.03 + dot(v, v) * 2.4);
    return w * w;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / resolution.x;
    p += 0.06 * float2(sin(p.y * 3.3 + time * 2.1), cos(p.x * 2.9 - time * 1.7));
    float wa = weight(p, a);
    float wb = weight(p, b);
    float wc = weight(p, c);
    float wd = weight(p, d);
    float3 col = (c0 * wa + c1 * wb + c2 * wc + c3 * wd) / (wa + wb + wc + wd);
    // a little dither so the dark ramps don't band
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""

// ---------------------------------------------------------------------------------------------
// Odometer

/**
 * [text] whose digits roll into place like an odometer as [progress] goes 0..1, staggered left to
 * right, while letters rise in. Drawn glyph by glyph with tabular digits, so nothing reflows.
 */
@Composable
internal fun OdometerText(
    text: String,
    style: TextStyle,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: () -> Color = { style.color },
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tnum = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val full = remember(text, tnum) { measurer.measure(text, tnum, softWrap = false, maxLines = 1) }
    val lefts = remember(full) { FloatArray(text.length) { full.getBoundingBox(it).left } }
    val glyphs = remember(text, tnum) {
        val chars = (text.toSet() + ('0'..'9')).filter { it != ' ' }
        chars.associateWith { measurer.measure(it.toString(), tnum, softWrap = false, maxLines = 1) }
    }
    val width = with(density) { full.size.width.toDp() }
    val height = with(density) { full.size.height.toDp() }
    Spacer(
        modifier = modifier
            .size(width, height)
            .drawWithCache {
                val lineH = full.size.height.toFloat()
                onDrawBehind {
                    val p = progress().coerceIn(0f, 1f)
                    val c = color()
                    for (i in text.indices) {
                        val ch = text[i]
                        if (ch == ' ') continue
                        val local = stagger(p, i, text.length, length = 0.62f)
                        if (local <= 0f) continue
                        val x = lefts[i]
                        if (ch.isDigit()) {
                            val digit = ch - '0'
                            val spins = 1 + i % 3
                            val value = (spins * 10 + digit) * easeOutCubic(local)
                            val base = floor(value).toInt()
                            val frac = value - base
                            val fadeIn = (local * 5f).coerceAtMost(1f)
                            clipRect(left = x - 4f, top = 0f, right = x + (glyphs[ch]?.size?.width ?: 0) + 4f, bottom = lineH) {
                                glyphs['0' + base % 10]?.let { drawText(it, color = c, topLeft = Offset(x, -frac * lineH), alpha = (1f - frac) * fadeIn) }
                                if (frac > 0.001f) {
                                    glyphs['0' + (base + 1) % 10]?.let { drawText(it, color = c, topLeft = Offset(x, (1f - frac) * lineH), alpha = frac * fadeIn) }
                                }
                            }
                        } else {
                            val e = easeOutCubic(local)
                            glyphs[ch]?.let { drawText(it, color = c, topLeft = Offset(x, (1f - e) * lineH * 0.35f), alpha = e) }
                        }
                    }
                }
            },
    )
}

// ---------------------------------------------------------------------------------------------
// Gradient text that settles to white

/**
 * Tints whatever it wraps (text) with a moving gradient of [colors], easing to plain white as
 * [whiteness] reaches 1. The offscreen layer only exists while the tint is showing.
 */
internal fun Modifier.gradientToWhite(colors: () -> List<Color>, whiteness: () -> Float, time: RecapTime): Modifier = this
    // SrcIn has to blend into the text's own buffer: without one it lands on everything already
    // drawn under the text and fills its whole box (the white bar under the artist photo)
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .then(
        Modifier.drawWithContent {
            drawContent()
            val w = whiteness()
            if (w >= 0.999f) return@drawWithContent
            val shift = ((time.seconds * 0.35) % 1.0).toFloat() * size.width * 2f
            val tinted = colors().map { lerp(it, Color.White, w) }
            drawRect(
                Brush.linearGradient(
                    tinted + tinted.first(),
                    start = Offset(-size.width * 2f + shift, 0f),
                    end = Offset(shift, size.height),
                    tileMode = TileMode.Repeated,
                ),
                blendMode = BlendMode.SrcIn,
            )
        },
    )

/** Brightened copies of a palette for text: the mesh tones themselves are too dark to read on it. */
internal fun RecapPalette.textColors(): List<Color> = listOf(c1, c3, c0, c2).map { c ->
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = (hsl[1] + 0.2f).coerceAtMost(0.95f)
    hsl[2] = 0.78f
    Color(ColorUtils.HSLToColor(hsl))
}

