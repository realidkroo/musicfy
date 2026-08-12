// PlayerProgressSlider.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.ui.component.PlayerSliderTrack
import com.example.musicfy.utils.makeTimeString

@Composable
fun PlayerProgressSlider(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val progress by playerConnection.uiState.progressState.collectAsState()

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    val displayedPosition = sliderPosition ?: progress.position

    val trackInteractionSource = remember { MutableInteractionSource() }
    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
    val trackHeight by animateDpAsState(
        targetValue = if (isTrackDragged || isTrackPressed) 14.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "playerProgressTrackHeight"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Slider(
            value = displayedPosition.toFloat(),
            valueRange = 0f..(if (progress.duration == C.TIME_UNSET || progress.duration <= 0L) 0f else progress.duration.toFloat()),
            onValueChange = { sliderPosition = it.toLong() },
            onValueChangeFinished = {
                sliderPosition?.let { playerConnection.player.seekTo(it) }
                sliderPosition = null
            },
            interactionSource = trackInteractionSource,
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                PlayerSliderTrack(
                    sliderState = sliderState,
                    trackHeight = trackHeight,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = PlayerHorizontalPadding)
        )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp)
    ) {
        Text(
            text = makeTimeString(displayedPosition),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (progress.duration != C.TIME_UNSET && progress.duration > 0L) makeTimeString(progress.duration) else "",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
