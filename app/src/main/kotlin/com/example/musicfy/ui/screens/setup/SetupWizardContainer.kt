package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.musicfy.utils.dataStore
import com.example.musicfy.constants.SetupCompletedKey
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
@Composable
fun SetupWizardContainer(
    isVisible: Boolean,
    isStacked: Boolean = false,
    onSetupCompleted: (String, Uri?) -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val overlayProgress = remember { Animatable(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isFirstLaunch by remember { mutableStateOf(true) }
    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val smoothMotion = tween<Float>(durationMillis = 450, easing = FastOutSlowInEasing)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (isFirstLaunch) {
                kotlinx.coroutines.delay(1000)
                isFirstLaunch = false
            }
            overlayProgress.animateTo(1f, smoothMotion)
        } else {
            isFirstLaunch = false
            overlayProgress.animateTo(0f, smoothMotion)
        }
    }

    // calculate dynamic progress combining transition progress and drag offset
    val dragProgress = (1f - (dragOffsetY / 1200f)).coerceIn(0f, 1f)
    val effectiveProgress = overlayProgress.value * dragProgress

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val backgroundTopEdge = topInset + 8.dp
        // if stacked under another modal remove padding so it peeks perfectly at
        val foregroundTopEdge = if (isStacked) 0.dp else backgroundTopEdge + 12.dp

        // main app content shrinks dynamically based on setup wizard position
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.08f * effectiveProgress
                    scaleX = scale
                    scaleY = scale

                    val targetTranslationY = topInset.toPx() + 8.dp.toPx() - (size.height * 0.04f)
                    translationY = targetTranslationY * effectiveProgress

                    clip = true
                    val radius = (32f * effectiveProgress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())
                }
        ) {
            content()
        }

        // setup wizard overlay
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
                    .padding(top = foregroundTopEdge * effectiveProgress)
                    .graphicsLayer {
                        val inverseProgress = 1f - overlayProgress.value
                        // travel a bit past the full layer height so the wizard is
                        // fully off screen before it unmounts instead of getting cut
                        // off mid slide on taller screens
                        translationY = inverseProgress * (size.height * 1.15f) + dragOffsetY
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
