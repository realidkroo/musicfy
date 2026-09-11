package com.example.musicfy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * A small Material 3 style palette derived from the cover art.
 *
 * Seeded from the artwork rather than the device wallpaper: every Material 3 surface in the player
 * follows the song being played, not the system theme. This is deliberately a tonal palette rather
 * than "the dominant colour applied everywhere" - the on-colours are chosen by contrast, so text
 * and glyphs stay legible over a cover of any brightness.
 */
data class ArtworkPalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val surface: Color,
) {
    companion object {
        val Neutral = ArtworkPalette(
            accent = Color.White,
            onAccent = Color.Black,
            container = Color.White.copy(alpha = 0.15f),
            onContainer = Color.White,
            surface = Color(0xFF121212),
        )

        /**
         * Builds a palette around [seed], typically the artwork's extracted theme colour.
         *
         * Tones are produced by moving the seed's lightness rather than by picking several colours
         * out of the image: separate swatches from one cover often clash, whereas one hue at
         * different lightness always reads as a set.
         */
        fun from(seed: Color): ArtworkPalette {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(seed.toArgb(), hsl)

            // A very desaturated cover would otherwise produce a palette indistinguishable from
            // grey, so lift it enough to stay recognisably tinted.
            val saturation = hsl[1].coerceIn(0.25f, 0.85f)

            val accent = hslColor(hsl[0], saturation, 0.72f)
            val container = hslColor(hsl[0], saturation * 0.55f, 0.26f)
            val surface = hslColor(hsl[0], saturation * 0.35f, 0.11f)

            return ArtworkPalette(
                accent = accent,
                onAccent = contrastingOn(accent),
                container = container,
                onContainer = contrastingOn(container),
                surface = surface,
            )
        }

        private fun hslColor(hue: Float, saturation: Float, lightness: Float): Color =
            Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

        /**
         * Black or white, whichever contrasts better against [background].
         *
         * Measured with [ColorUtils.calculateContrast] rather than a luminance threshold so the
         * choice matches the accessibility ratio the eye actually cares about.
         */
        private fun contrastingOn(background: Color): Color {
            val bg = background.toArgb()
            val onWhite = ColorUtils.calculateContrast(android.graphics.Color.WHITE, bg)
            val onBlack = ColorUtils.calculateContrast(android.graphics.Color.BLACK, bg)
            return if (onWhite >= onBlack) Color.White else Color.Black
        }
    }
}
