// PlayerEnteringEditOverlay.kt
// The beat between "you held the artwork" and "here are the parts you can edit": the player
// blurs out behind a single line of text, holds, then fades away into the selection layer.
//
// The blur itself is NOT drawn here. It is a RenderEffect on the whole player subtree (see
// BottomSheetPlayer, which owns both), because the concept frame blurs everything — artwork,
// title, progress bar, transport buttons — and this overlay has to stay sharp on top of it.
// Drawing a blurred copy from the GlassKit capture would only have covered the controls rather
// than blurring them, since that capture is scoped to MorphingCover's subtree.

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

/** How long the message stays fully up before it starts leaving. */
private const val HoldMillis = 2_200L

/** Fade used both on the way in and on the way out. */
private const val FadeMillis = 360

/**
 * Total time from long press to the selection layer: [FadeMillis] in, [HoldMillis] held,
 * [FadeMillis] out — smooth edit mode entry with dark overlay and blurred background.
 */
@Composable
fun PlayerEnteringEditOverlay(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Without this, back during the hold falls through to the sheet's own handler and collapses
    // the player instead of simply calling the long press off.
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
        // Hands over only once this layer has actually faded, so the outlines appear against a
        // clean screen instead of crossing through the message.
        delay(FadeMillis.toLong())
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            // Nothing is interactive for the duration — including the sheet's own drag handling,
            // which lives on an ancestor and would otherwise still collapse the player.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // drawBehind, not background(): `background(Color.copy(alpha = ... * alpha))`
            // evaluates the fade in COMPOSITION, so this full-screen overlay recomposed and
            // rebuilt its whole modifier chain on every frame of the fade. Inside the lambda the
            // same read happens in the draw phase and costs a repaint instead.
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
