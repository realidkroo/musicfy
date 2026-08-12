// sleeptimersheetkt
// sleep timer in two stages: a compact card showing the duration with a
// into a picker

// the picker is material3's timeinput — the keyboard half of the standard
// a hand-rolled odometer sat here first and was not worth it: a rolling
// so it needed a separate gesture bolted on to actually change anything and
// in that seam typed entry is unambiguous accessible for free and the

// hours and minutes only no seconds: the service's sleeptimerstart takes
// seconds field would have been a control that quietly rounds away

// the service already owns the timer itself (playback/sleeptimerkt) — this

package com.example.musicfy.ui.player.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.ui.component.AppSwitch

private val CardSurface = MenuRowSurface
private val SheetEasing = MenuEasing

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val sleepTimer = playerConnection.service.sleepTimer

    // collapsed = the summary card with the chevron; expanded = the hh/mm/ss
    var expanded by remember { mutableStateOf(false) }

    val timeState = androidx.compose.material3.rememberTimePickerState(
        initialHour = 0,
        initialMinute = 30,
        is24Hour = true,
    )

    var endAfterSong by remember { mutableStateOf(sleepTimer.pauseWhenSongEnd) }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.62f, fullDetent = 0.86f) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 28.dp)
                // the card grows into the picker rather than the sheet jumping to a new size
                .animateContentSize(animationSpec = tween(420, easing = SheetEasing))
        ) {
            Text(
                text = "Sleep timer",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!expanded) {
                // stage one: the duration as one line tap anywhere to open the picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { expanded = true },
                        )
                ) {
                    Text(
                        text = compactDuration(timeState.hour, timeState.minute),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = "Edit duration",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.TimeInput(
                        state = timeState,
                        colors = androidx.compose.material3.TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = Color.White.copy(alpha = 0.12f),
                            timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.06f),
                            timeSelectorSelectedContentColor = Color.White,
                            timeSelectorUnselectedContentColor = Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SheetButton(
                    label = "SET TIMER",
                    enabled = timeState.hour > 0 || timeState.minute > 0,
                    onClick = {
                        sleepTimer.start((timeState.hour * 60 + timeState.minute).coerceAtLeast(1))
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(text = "or", color = Color(0xFF8A8A8A), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "end after this music ends",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(
                    checked = endAfterSong,
                    onCheckedChange = { wanted ->
                        endAfterSong = wanted
                        if (wanted) sleepTimer.start(-1) else sleepTimer.clear()
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(text = "or", color = Color(0xFF8A8A8A), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))

            SheetButton(
                label = "CANCEL TIMER",
                enabled = sleepTimer.isActive,
                onClick = {
                    sleepTimer.clear()
                    endAfterSong = false
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun compactDuration(h: Int, m: Int): String = buildString {
    if (h > 0) append("${h}h ")
    append("${m}m")
}.trim()
