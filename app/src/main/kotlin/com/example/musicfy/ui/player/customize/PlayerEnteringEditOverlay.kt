// PlayerEnteringEditOverlay.kt

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val HoldMillis = 2_200L

private const val FadeMillis = 360

@Composable
fun PlayerEnteringEditOverlay(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {

    androidx.activity.compose.BackHandler(onBack = onCancel)

    var visible by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = FadeMillis,
            easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
        ),
        label = "enteringEditModeFade",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(HoldMillis + FadeMillis)
        visible = false

        delay(FadeMillis.toLong())
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()

            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }

            .drawBehind { drawRect(Color.Black.copy(alpha = 0.52f * alpha)) }
    ) {
        Text(
            text = "Entering edit mode....",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    val scale = 0.90f + 0.10f * alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
