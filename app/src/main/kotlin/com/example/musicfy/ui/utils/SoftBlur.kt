// SoftBlur.kt

package com.example.musicfy.ui.utils

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * a small, heavily blurred copy of an image, for backdrops that go soft as you scroll.
 *
 * it's made once on Coil's worker thread and kept in its memory cache, so a screen only has to
 * crossfade to it. a live RenderEffect blur gets recomputed on every frame its radius changes,
 * which is every frame of a scroll. it also works below Android 12, where RenderEffect doesn't exist.
 *
 * the copy is only [width] px wide; drawn back at full size, the bilinear upscale smooths it out.
 */
class SoftBlurTransformation(
    private val width: Int = 96,
    private val radius: Int = 2,
) : Transformation() {

    override val cacheKey: String = "soft_blur-$width-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val height = (input.height * width / input.width.toFloat()).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== input) scaled.recycle()

        // three box passes each way come out close to a gaussian
        val scratch = IntArray(pixels.size)
        repeat(3) {
            boxBlur(pixels, scratch, width, height, radius, horizontal = true)
            boxBlur(scratch, pixels, width, height, radius, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    override fun equals(other: Any?): Boolean =
        other is SoftBlurTransformation && other.width == width && other.radius == radius

    override fun hashCode(): Int = 31 * width + radius
}

/** one sliding-window pass along every row (or column), edges clamped */
private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val step = if (horizontal) 1 else w
    val window = 2 * r + 1

    for (line in 0 until lines) {
        val start = if (horizontal) line * w else line
        var a = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -r..r) {
            val p = src[start + i.coerceIn(0, length - 1) * step]
            a += p ushr 24
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (i in 0 until length) {
            dst[start + i * step] =
                ((a / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = src[start + (i - r).coerceAtLeast(0) * step]
            val entering = src[start + (i + r + 1).coerceAtMost(length - 1) * step]
            a += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
