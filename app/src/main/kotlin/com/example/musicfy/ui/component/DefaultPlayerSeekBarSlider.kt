package com.example.musicfy.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A settings slider that uses the player's default rounded seek-bar treatment. */
@Composable
fun DefaultPlayerSeekBarSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val activeColor = if (enabled) MaterialTheme.colorScheme.onSurface else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val inactiveColor = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f) else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = (range.endInclusive - range.start).takeIf { it > 0f }
            val fraction = span?.let { (sliderState.value - range.start) / it } ?: 0f
            SeekBarTrack(
                style = SeekBarStyle.DEFAULT,
                fraction = fraction,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                animateWave = false,
                active = isDragged || isPressed,
            )
        },
        modifier = modifier.fillMaxWidth().height(28.dp),
    )
}
