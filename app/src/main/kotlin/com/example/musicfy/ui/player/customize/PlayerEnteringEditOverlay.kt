// playerenteringeditoverlaykt
// the beat between "you held the artwork" and "here are the parts you can
// blurs out behind a single line of text holds then fades away into the

// the blur itself is not drawn here it is a rendereffect on the whole player
// bottomsheetplayer which owns both) because the concept frame blurs
// title progress bar transport buttons — and this overlay has to stay sharp
// drawing a blurred copy from the glasskit capture would only have covered
// than blurring them since that capture is scoped to morphingcover's subtree

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

// how long the message stays fully up before it starts leaving
private const val HoldMillis = 2_200L

// fade used both on the way in and on the way out
private const val FadeMillis = 360

// total time from long press to the selection layer: [fademillis] in [holdmillis]
@Composable
fun PlayerEnteringEditOverlay(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // without this back during the hold falls through to the sheet's own handler
    // the player instead of simply calling the long press off
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
        // hands over only once this layer has actually faded so the outlines appear
        // clean screen instead of crossing through the message
        delay(FadeMillis.toLong())
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            // nothing is interactive for the duration — including the sheet's own drag
            // which lives on an ancestor and would otherwise still collapse the player
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // drawbehind not background(): `background(colorcopy(alpha =  * alpha))`
            // evaluates the fade in composition so this full-screen overlay recomposed
            // rebuilt its whole modifier chain on every frame of the fade inside the
            // same read happens in the draw phase and costs a repaint instead
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
