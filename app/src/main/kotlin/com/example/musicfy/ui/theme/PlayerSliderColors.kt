// PlayerSliderColors.kt

package com.example.musicfy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.musicfy.constants.PlayerBackgroundStyle

object PlayerSliderColors {

    @Composable
    fun getSliderColors(
        activeColor: Color,
        playerBackground: PlayerBackgroundStyle,
        useDarkTheme: Boolean
    ): SliderColors {
        val inactiveTrackColor = when (playerBackground) {

            PlayerBackgroundStyle.SOLID -> {
                if (useDarkTheme) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            }
            PlayerBackgroundStyle.COVER_GRADIENT, PlayerBackgroundStyle.DARK_GRADIENT, PlayerBackgroundStyle.APPLE_MUSIC -> {
                Color.White.copy(alpha = 0.4f)
            }
        }

        return SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = activeColor,
            inactiveTrackColor = inactiveTrackColor,
            disabledActiveTrackColor = activeColor,
            disabledInactiveTrackColor = inactiveTrackColor,
            disabledThumbColor = activeColor
        )
    }
}
