// zoomoutpopupcontainerkt
// generic version of the whole screen shrinks into a rounded card behind a
// used by betanoticecontainer betanoticescreen same animation math but
// as a slot instead of being hardcoded to the beta notice so any full screen
// same background zooms out motion for its own popup

package com.example.musicfy.ui.component

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// global trigger for zoomoutpopupcontainer mounted once at the app root see
@Stable
class ZoomOutOverlayState {
    var isVisible by mutableStateOf(false)
        private set
    var content by mutableStateOf<@Composable () -> Unit>({})
        private set

    fun show(content: @Composable () -> Unit) {
        this.content = content
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
    }
}

val LocalZoomOutOverlayState = compositionLocalOf { ZoomOutOverlayState() }

@Composable
fun ZoomOutPopupContainer(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    popupContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val overlayProgress = remember { Animatable(0f) }
    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val smoothMotion = tween<Float>(durationMillis = 450, easing = FastOutSlowInEasing)

    LaunchedEffect(isVisible) {
        overlayProgress.animateTo(if (isVisible) 1f else 0f, smoothMotion)
    }

    val effectiveProgress = overlayProgress.value

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val backgroundTopEdge = topInset + 8.dp
        val foregroundTopEdge = backgroundTopEdge + 12.dp

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

        if (effectiveProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveProgress.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.7f * effectiveProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = foregroundTopEdge * effectiveProgress)
                    .graphicsLayer {
                        val inverseProgress = 1f - effectiveProgress
                        translationY = size.height * inverseProgress
                    }
            ) {
                popupContent()
            }
        }
    }
}
