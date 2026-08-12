// playbackspeedsheetkt
// speed and pitch driven straight into exoplayer's playbackparameters

// speed and pitch are separate axes on purpose: moving speed alone is the
// plays faster without the chipmunk effect because media3 time-stretches)
// when you actually want that shift

package com.example.musicfy.ui.player.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import com.example.musicfy.LocalPlayerConnection

private val CardSurface = MenuRowSurface

@Composable
fun PlaybackSpeedSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player

    val speed = remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val pitch = remember { mutableFloatStateOf(player.playbackParameters.pitch) }

    // both axes go in together — playbackparameters is a single value so setting
    // stale copy of the other would quietly reset it
    fun apply() {
        player.playbackParameters = PlaybackParameters(
            speed.floatValue.coerceIn(0.25f, 3f),
            pitch.floatValue.coerceIn(0.25f, 3f),
        )
    }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.46f, fullDetent = 0.46f) { _ ->
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Playback speed and tempo",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(18.dp))

            ValueSlider(
                label = "Speed",
                value = speed.floatValue,
                onValueChange = { speed.floatValue = it; apply() },
            )
            Spacer(modifier = Modifier.height(14.dp))
            ValueSlider(
                label = "Pitch",
                value = pitch.floatValue,
                onValueChange = { pitch.floatValue = it; apply() },
            )

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            speed.floatValue = 1f
                            pitch.floatValue = 1f
                            apply()
                        },
                    )
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = "RESET TO NORMAL",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format("%.2f×", value),
                color = Color(0xFFB3B3B3),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LineSlider(
            // the slider works in 01; the 025×-2× range is mapped in and out here
            // 005 so the number under your finger is one you'd actually choose
            value = ((value - SpeedMin) / (SpeedMax - SpeedMin)).coerceIn(0f, 1f),
            onValueChange = { fraction ->
                val raw = SpeedMin + fraction * (SpeedMax - SpeedMin)
                onValueChange(kotlin.math.round(raw / 0.05f) * 0.05f)
            },
        )
    }
}

private const val SpeedMin = 0.25f
private const val SpeedMax = 2f
