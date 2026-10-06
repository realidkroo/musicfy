// Press3D.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

private const val DefaultMaxTilt = 7f

private const val DefaultPressedScale = 0.97f

private val TiltSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 420f,
)

fun Modifier.press3D(
    maxTilt: Float = DefaultMaxTilt,
    pressedScale: Float = DefaultPressedScale,
    enabled: Boolean = true,

    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }

    var normalized by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {

                    val down = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.pressed } ?: continue

                    val bounds = this.size
                    if (bounds.width > 0 && bounds.height > 0) {
                        val p = down.position
                        normalized = Offset(
                            x = ((p.x / bounds.width) * 2f - 1f).coerceIn(-1f, 1f),
                            y = ((p.y / bounds.height) * 2f - 1f).coerceIn(-1f, 1f),
                        )
                        size = bounds
                    }
                    scope.launch { press.animateTo(1f, TiltSpring) }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                    }
                    scope.launch { press.animateTo(0f, TiltSpring) }
                }
            }
        }
        .graphicsLayer {
            val t = press.value
            if (t > 0f) {
                val s = 1f - (1f - pressedScale) * t
                scaleX = s
                scaleY = s

                rotationX = -normalized.y * maxTilt * t
                rotationY = normalized.x * maxTilt * t

                cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                transformOrigin = origin
            }
        }
}
