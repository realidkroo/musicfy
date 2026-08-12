// linesliderkt
// the plain track and fill slider the concepts use a rounded line that
// with no thumb no ripple and no material chrome

// material3 s slider brings a visible thumb a state layer and its own touch
// of which match the sheets these appear in this is a drag surface over two

package com.example.musicfy.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 6.dp,
    // touch target is taller than the line itself a 6dp tall strip is unusable
    touchHeight: Dp = 28.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
) {
    val widthPx = remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(onValueChange)

    fun report(x: Float) {
        val w = widthPx.floatValue
        if (w > 0f) latest((x / w).coerceIn(0f, 1f))
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .layout { measurable, constraints ->
                widthPx.floatValue = constraints.maxWidth.toFloat()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> report(offset.x) }
            }
            .pointerInput(Unit) {
                // reports from the absolute position rather than accumulating deltas so the
                // fill always sits exactly under the finger even after a fast drag
                detectHorizontalDragGestures(
                    onDragStart = { offset -> report(offset.x) },
                    onHorizontalDrag = { change, _ -> report(change.position.x) },
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(inactiveColor)
        )
        Box(
            modifier = Modifier
                .height(trackHeight)
                .layout { measurable, constraints ->
                    val w = (constraints.maxWidth * value.coerceIn(0f, 1f)).toInt()
                    val placeable = measurable.measure(
                        Constraints.fixed(w.coerceAtLeast(0), constraints.maxHeight)
                    )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(activeColor)
        )
    }
}
