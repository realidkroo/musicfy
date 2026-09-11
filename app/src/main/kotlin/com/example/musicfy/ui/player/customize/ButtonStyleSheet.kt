package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicfy.R
import com.example.musicfy.ui.component.ButtonStyle
import com.example.musicfy.ui.component.PlayerTransportButtons
import com.example.musicfy.ui.component.TransportColors

/**
 * Button style picker.
 *
 * Previews are the real controls with their callbacks stubbed out, so what the sheet shows is what
 * the player renders - including the artwork palette passed in by the caller.
 */
@Composable
fun ButtonStyleSheet(
    selected: ButtonStyle,
    colors: TransportColors,
    onSelect: (ButtonStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF141414))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(64.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f))
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Button styles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ButtonStyle.entries.forEach { style ->
                    Text(
                        text = style.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )

                    val isSelected = style == selected
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = if (isSelected) 0.14f else 0.06f))
                            .then(
                                if (isSelected) {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(style) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (style == ButtonStyle.DEFAULT) {
                            // The default style is drawn by the player's animated glyphs, which
                            // need a live player; this mirrors their shape for the preview only.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf(R.drawable.skip_previous, R.drawable.pause, R.drawable.skip_next)
                                    .forEach { icon ->
                                        Icon(
                                            painter = painterResource(icon),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(if (icon == R.drawable.pause) 34.dp else 30.dp),
                                        )
                                    }
                            }
                        } else {
                            PlayerTransportButtons(
                                style = style,
                                isPlaying = true,
                                canSkipPrevious = true,
                                canSkipNext = true,
                                onPrevious = {},
                                onPlayPause = {},
                                onNext = {},
                                colors = colors,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
