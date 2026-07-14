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
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            overlayProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 220f))
        } else {
            overlayProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 220f))
        }
    }

    // Calculate dynamic progress combining transition progress and drag offset
    val dragProgress = (1f - (dragOffsetY / 1200f)).coerceIn(0f, 1f)
    val effectiveProgress = overlayProgress.value * dragProgress

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main App Content (shrinks and blurs dynamically based on setup wizard position)
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

                    val blurPx = (12f * effectiveProgress).dp.toPx()
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
                        val inverseProgress = 1f - overlayProgress.value
                        translationY = inverseProgress * 2000f + dragOffsetY
                    }
            ) {
                SetupWizardScreen(
                    onComplete = onSetupCompleted,
                    onDrag = { delta ->
                        val resistance = 1f - (dragOffsetY / 2000f).coerceIn(0f, 0.8f)
                        val newOffset = dragOffsetY + delta * resistance
                        if (newOffset > 0) {
                            dragOffsetY = newOffset
                        }
                    },
                    onDragRelease = {
                        coroutineScope.launch {
                            androidx.compose.animation.core.animate(
                                initialValue = dragOffsetY,
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                            ) { value, _ ->
                                dragOffsetY = value
                            }
                        }
                    }
                )
            }
        }
    }
}
