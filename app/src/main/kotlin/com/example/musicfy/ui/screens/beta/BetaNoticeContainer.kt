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

        val screenHeight = maxHeight

        val backgroundTopEdge = topInset + 8.dp
        val foregroundTopEdge = backgroundTopEdge + 56.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.07f * effectiveProgress
                    scaleX = scale
                    scaleY = scale

                    val naturalTopOffset = size.height * (1f - scale) / 2f
                    translationY = (backgroundTopEdge.toPx() - naturalTopOffset) * effectiveProgress

                    clip = true
                    val radius = (32f * effectiveProgress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())
                }
        ) {
            content()
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val dismissThreshold = with(density) { 80.dp.toPx() }

        if (effectiveProgress > 0f) {
            val dragAlphaDim = (dragOffset.value / 400f).coerceIn(0f, 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (effectiveProgress * (1f - dragAlphaDim)).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.7f * effectiveProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {  }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = foregroundTopEdge * effectiveProgress)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragOffset.value > dismissThreshold) {
                                        dragOffset.animateTo(
                                            targetValue = size.height.toFloat(),
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        dragOffset.snapTo(0f)
                                        onDismiss(false)
                                    } else {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val resistance = if (dragOffset.value < 0) 0.15f else 1.0f
                                val newOffset = dragOffset.value + dragAmount * resistance
                                dragOffset.snapTo(newOffset.coerceAtLeast(-40f))
                            }
                        }
                    }
                    .graphicsLayer {
                        val inverseProgress = 1f - effectiveProgress
                        translationY = size.height * inverseProgress + dragOffset.value.coerceAtLeast(-40f)
                    }
            ) {
                BetaNoticeScreen(onDismiss = onDismiss)
            }
        }
    }
}
