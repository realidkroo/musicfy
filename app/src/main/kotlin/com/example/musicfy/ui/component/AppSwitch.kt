// AppSwitch.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbSize = 24.dp
private val ThumbInset = (TrackHeight - ThumbSize) / 2
private val Grey = Color(0xFF8E8E93)

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    val animSpec = tween<Color>(150)

    val trackColor by animateColorAsState(
        targetValue = if (checked) Grey.copy(alpha = alpha) else Color.Transparent,
        animationSpec = animSpec,
        label = "trackColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else Grey.copy(alpha = 0.45f * alpha),
        animationSpec = animSpec,
        label = "borderColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White.copy(alpha = alpha) else Grey.copy(alpha = alpha),
        animationSpec = animSpec,
        label = "thumbColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        animationSpec = tween(150),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center,
        ) {
            thumbContent?.invoke()
        }
    }
}
