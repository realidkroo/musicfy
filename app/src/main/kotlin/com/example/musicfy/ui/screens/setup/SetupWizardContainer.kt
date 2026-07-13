package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.musicfy.utils.dataStore
import com.example.musicfy.constants.SetupCompletedKey
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SetupWizardContainer(
    isVisible: Boolean,
    onSetupCompleted: (String, Uri?) -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val overlayProgress = remember { Animatable(if (isVisible) 1f else 0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            overlayProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 220f))
        } else {
            overlayProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 220f))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main App Content (shrinks and blurs when wizard is visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = overlayProgress.value
                    val scale = 1f - 0.08f * progress
                    scaleX = scale
                    scaleY = scale

                    clip = true
                    val radius = (32f * progress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())

                    val blurPx = (12f * progress).dp.toPx()
                    if (blurPx > 0.1f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                            blurPx,
                            blurPx,
                            android.graphics.Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    } else {
                        renderEffect = null
                    }
                }
        ) {
            content()
        }

        // Setup Wizard Overlay
        if (overlayProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overlayProgress.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Disable background clicks */ }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val inverseProgress = 1f - overlayProgress.value
                        translationY = inverseProgress * 2000f // Slide up from bottom
                        alpha = overlayProgress.value.coerceIn(0f, 1f)
                    }
            ) {
                SetupWizardScreen(onComplete = onSetupCompleted)
            }
        }
    }
}
