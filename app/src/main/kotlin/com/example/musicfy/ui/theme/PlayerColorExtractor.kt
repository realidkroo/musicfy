// PlayerColorExtractor.kt
// this thing is part of player color extractor

package com.example.musicfy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Player color extraction system for generating gradients from album artwork
 * 
 * This system analyzes album artwork and extracts vibrant, dominant colors
 * to create visually appealing gradients for the music player interface.
 */
object PlayerColorExtractor {

    /**
     * Extracts colors from a palette and creates a gradient
     * 
     * @param palette The color palette extracted from album artwork
     * @param fallbackColor Fallback color to use if extraction fails
     * @return List of colors for gradient (primary, darker variant, black)
     */
    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int
    ): List<Color> = withContext(Dispatchers.Default) {

        // Palette's own "dominant" swatch is already defined as whichever cluster
        // covers the most pixels — i.e. the actual majority color/vibe of the image —
        // so pick that directly instead of letting a small-but-saturated swatch
        // (logo text, a highlight, a single accent) outbid it on vibrancy. Muting/
        // darkening for background use happens afterward in darkenIfTooLight.
        val primaryColor = palette.dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(palette.getDominantColor(fallbackColor))

        // Create sophisticated gradient with 3 color points
        listOf(
            primaryColor, // Start: primary vibrant color
            primaryColor.copy(
                red = (primaryColor.red * 0.6f).coerceAtLeast(0f),
                green = (primaryColor.green * 0.6f).coerceAtLeast(0f),
                blue = (primaryColor.blue * 0.6f).coerceAtLeast(0f)
            ), // Middle: darker version of primary color
            Color.Black // End: black
        )
    }

    suspend fun extractAppleMusicColors(
        palette: Palette,
        fallbackColor: Int
    ): List<Color> = withContext(Dispatchers.Default) {
        val ranked = palette.swatches
            .sortedWith(
                compareByDescending<Palette.Swatch> { it.population }
                    .thenBy { saturationOf(Color(it.rgb)) }
            )
            .map { Color(it.rgb).softenedForPlayerBackground() }
            .distinctBy { it.toArgb() }

        val dominant = palette.dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(palette.getDominantColor(fallbackColor))
        val muted = palette.mutedSwatch?.rgb?.let { Color(it) }
        val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) }
        val lightMuted = palette.lightMutedSwatch?.rgb?.let { Color(it) }

        val base = listOfNotNull(
            dominant,
            muted,
            darkMuted,
            lightMuted
        ).map { it.softenedForPlayerBackground() }

        (base + ranked)
            .ifEmpty { listOf(Color(fallbackColor)) }
            .let { colors ->
                val expanded = if (colors.size >= 6) colors else List(6) { index -> colors[index % colors.size] }
                expanded.take(6)
            }
    }

    private fun saturationOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[1]
    }

    private fun Color.softenedForPlayerBackground(): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.52f).coerceIn(0.04f, 0.42f)
        hsv[2] = hsv[2].coerceIn(0.20f, 0.70f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /**
     * Tames a raw cover-sampled color before it's used to tint a detail-screen
     * background: album art tends to be far more saturated/bright than a backdrop
     * should be (it washes out the white text/icons drawn on top, which assume a
     * dark, muted backdrop), so every color gets desaturated first — not just the
     * bright ones — and anything still bright after that gets pulled further toward
     * black. Operates in HSV rather than a straight lerp-to-black because lerping
     * alone barely touches perceived brightness for already-saturated colors.
     */
    fun darkenIfTooLight(
        color: Color,
        isDarkTheme: Boolean = true,
        luminanceThreshold: Float = 0.45f,
        darkenAmount: Float = 0.35f,
    ): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255f).roundToInt().coerceIn(0, 255),
            (color.green * 255f).roundToInt().coerceIn(0, 255),
            (color.blue * 255f).roundToInt().coerceIn(0, 255),
            hsv,
        )

        // Always desaturate a bit — keeps the background reading as a muted backdrop
        // instead of a second copy of the vivid album art. Lighter touch in light theme
        // so a light cover's pastel actually stays visibly light instead of graying out.
        hsv[1] *= if (isDarkTheme) 0.5f else 0.7f

        if (isDarkTheme) {
            // Dark theme: white text/icons sit on this background everywhere downstream,
            // so bright covers still get forced dark for legibility.
            if (hsv[2] > luminanceThreshold) {
                hsv[2] = (hsv[2] * (1f - darkenAmount)).coerceAtLeast(0.12f)
            }
        } else {
            // Light theme: let a light cover keep a light background — only guarantee a
            // floor so a very dark cover doesn't go pitch black against light chrome.
            hsv[2] = hsv[2].coerceAtLeast(0.55f)
        }

        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /**
     * Configuration constants for color extraction
     */
    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 200
        
        // Color enhancement factors
        const val VIBRANT_SATURATION_THRESHOLD = 0.25f
        const val VIBRANT_BRIGHTNESS_MIN = 0.2f
        const val VIBRANT_BRIGHTNESS_MAX = 0.9f
        
        const val POPULATION_WEIGHT_MULTIPLIER = 2f
        const val VIBRANCY_THRESHOLD_SATURATION = 0.3f
        const val VIBRANCY_THRESHOLD_BRIGHTNESS = 0.3f
        const val VIBRANCY_BONUS = 1.5f
        
        const val DEFAULT_SATURATION_FACTOR = 1.4f
        const val VIBRANT_SATURATION_FACTOR = 1.3f
        const val FALLBACK_SATURATION_FACTOR = 1.1f
        
        const val BRIGHTNESS_MULTIPLIER = 0.9f
        const val BRIGHTNESS_MIN = 0.4f
        const val BRIGHTNESS_MAX = 0.85f
        
        const val DARKER_VARIANT_FACTOR = 0.6f
    }
}
