package com.example.musicfy.ui.component

import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * High-performance RenderEffect cache for Compose.
 *
 * On Android 12-14 (API 31-34), HWUI applies Gaussian blurs at full layer resolution.
 * If radius is large (>54f), Skia cannot compute the convolution in a single pass,
 * causing multi-pass FBO buffer allocations and GPU fill-rate pipeline stalls.
 * On Android 15-16 (API 35+), HWUI has hardware pyramid downsampling and handles
 * arbitrary radii smoothly.
 *
 * This cache:
 * 1. Clamps extreme radii on API <= 34 to 54f by default to avoid multi-pass GPU stalls.
 * 2. Quantizes radii to prevent thousands of native SkImageFilter allocations.
 * 3. Returns null for radius <= 0.5f to immediately free offscreen layer overhead.
 */
object BlurEffectCache {
    private val composeCache = ConcurrentHashMap<Int, RenderEffect>()
    private val nativeCache = ConcurrentHashMap<Int, android.graphics.RenderEffect>()

    /**
     * Retrieves or creates a cached Compose RenderEffect.
     * On Android 12-14 (API <= 34), blurs > 54f can cause multi-pass GPU FBO stalls,
     * so they are clamped unless [allowLargeRadius] is explicitly enabled.
     * On Android 15/16 (API 35+), full radius is preserved with HW pyramid downsampling.
     */
    fun get(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
        allowLargeRadius: Boolean = false,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return composeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect
                .createBlurEffect(r, r, tileMode)
                .asComposeRenderEffect()
        }
    }

    /**
     * Retrieves or creates a cached android.graphics.RenderEffect for View/TextureView.
     */
    fun getNative(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP
    ): android.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return nativeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect.createBlurEffect(r, r, tileMode)
        }
    }
}
