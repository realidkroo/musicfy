package com.example.musicfy.ui.screens.beta

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BetaNoticeContainer(
    isVisible: Boolean,
    onDismiss: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val overlayProgress = remember { Animatable(0f) }
    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var isFirstLaunch by remember { mutableStateOf(true) }
    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val smoothMotion = tween<Float>(durationMillis = 450, easing = FastOutSlowInEasing)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (isFirstLaunch) {
                kotlinx.coroutines.delay(2000)
                isFirstLaunch = false
            }
            overlayProgress.animateTo(1f, smoothMotion)
        } else {
            isFirstLaunch = false
            overlayProgress.animateTo(0f, smoothMotion)
        }
    }

    val effectiveProgress = overlayProgress.value

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // main app content (shrinks dynamically based on beta notice position)
        val screenHeight = maxHeight
        // the background card's top edge will be exactly at topinset + 8dp
        val backgroundTopEdge = topInset + 8.dp
        val foregroundTopEdge = backgroundTopEdge + 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.08f * effectiveProgress
                    scaleX = scale
                    scaleY = scale

                    // scaling the height by 092f natively pushes the top edge down by sizeheight
                    // to place the top edge exactly at backgroundtopedge we subtract that scale
                    val targetTranslationY = topInset.toPx() + 8.dp.toPx() - (size.height * 0.04f)
                    translationY = targetTranslationY * effectiveProgress

                    clip = true
                    val radius = (32f * effectiveProgress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())
                }
        ) {
            content()
        }

        // beta notice overlay
        if (effectiveProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveProgress.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.7f * effectiveProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Disable background clicks */ }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // animate the padding in along with the alpha/translation
                    .padding(top = foregroundTopEdge * effectiveProgress)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                // add heavy resistance to the drag (rubber-banding)
                                val resistance = if (dragOffset.value > 0) 0.3f else 0.1f 
                                val newOffset = dragOffset.value + dragAmount * resistance
                                dragOffset.snapTo(newOffset.coerceAtLeast(-30f))
                            }
                        }
                    }
                    .graphicsLayer {
                        val inverseProgress = 1f - effectiveProgress
                        translationY = size.height * inverseProgress + dragOffset.value
                    }
            ) {
                BetaNoticeScreen(onDismiss = onDismiss)
            }
        }
    }
}
