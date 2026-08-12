// playerslidercolorskt
// what is this for you ask its for player slider colors ofc

package com.example.musicfy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.musicfy.constants.PlayerBackgroundStyle

// player slider color configuration for consistent styling across all slider
object PlayerSliderColors {

    // standard slider colors for all slider types
    @Composable
    fun getSliderColors(
        activeColor: Color,
        playerBackground: PlayerBackgroundStyle,
        useDarkTheme: Boolean
    ): SliderColors {
        val inactiveTrackColor = when (playerBackground) {
            // the flat fill is the only backdrop that follows the app theme rather than
            // being a dark image derived surface so it s the only one that needs the
            // theme s darker track
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
