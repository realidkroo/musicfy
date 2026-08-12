// PlayerColorExtractor.kt

package com.example.musicfy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object PlayerColorExtractor {

    suspend fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int
    ): List<Color> = withContext(Dispatchers.Default) {

        val primaryColor = palette.dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(palette.getDominantColor(fallbackColor))

        listOf(
            primaryColor,
            primaryColor.copy(
                red = (primaryColor.red * 0.6f).coerceAtLeast(0f),
                green = (primaryColor.green * 0.6f).coerceAtLeast(0f),
                blue = (primaryColor.blue * 0.6f).coerceAtLeast(0f)
            ),
            Color.Black
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

        hsv[1] *= if (isDarkTheme) 0.5f else 0.7f

        if (isDarkTheme) {

            if (hsv[2] > luminanceThreshold) {
                hsv[2] = (hsv[2] * (1f - darkenAmount)).coerceAtLeast(0.12f)
            }
        } else {

            hsv[2] = hsv[2].coerceAtLeast(0.55f)
        }

        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    object Config {
        const val MAX_COLOR_COUNT = 32
        const val BITMAP_AREA = 8000
        const val IMAGE_SIZE = 200

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
