package com.example.musicfy.ui.screens.beta

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun BetaNoticeContainer(
    isVisible: Boolean,
    onDismiss: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val overlayProgress = remember { Animatable(0f) }
    var isFirstLaunch by remember { mutableStateOf(true) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (isFirstLaunch) {
                kotlinx.coroutines.delay(2000)
                isFirstLaunch = false
            }
            overlayProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 220f))
        } else {
            isFirstLaunch = false
            overlayProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 220f))
        }
    }

    val effectiveProgress = overlayProgress.value

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main App Content (shrinks dynamically based on Beta Notice position)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.08f * effectiveProgress
                    scaleX = scale
                    scaleY = scale

                    clip = true
                    val radius = (32f * effectiveProgress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())
                }
        ) {
            content()
        }

        // Beta Notice Overlay
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
                    .graphicsLayer {
                        val inverseProgress = 1f - effectiveProgress
                        translationY = size.height * inverseProgress
                    }
            ) {
                BetaNoticeScreen(onDismiss = onDismiss)
            }
        }
    }
}
