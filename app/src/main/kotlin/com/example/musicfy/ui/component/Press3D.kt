// press3dkt
// the "press it and it tilts toward your thumb" effect used by the
// navigation bar

// two things make it read as a physical object rather than a button that

// 1 it tilts about the point you actually touched pressing the left edge
// away from you; pressing dead centre only scales that is the whole reason
// the touch position and not just a pressed/not-pressed boolean
// 2 the tilt is applied with a real perspective camera distance so near
// and far edges shrink without it rotationx/y is an affine squash and looks

// the gesture is observed never consumed: everything here runs on
// consumes nothing so whatever click/drag handling already exists on the
// untouched that matters for the pill in particular whose tap-to-expand and
// owned by bottomsheet several layers up

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

// how far the surface may tilt in degrees at the very corner
private const val DefaultMaxTilt = 7f

// scale at full press
private const val DefaultPressedScale = 0.97f

// springs chosen to settle without visible overshoot on release — a bouncy return
private val TiltSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 420f,
)

// press feedback that tilts toward the touch point while the player is already
fun Modifier.press3D(
    maxTilt: Float = DefaultMaxTilt,
    pressedScale: Float = DefaultPressedScale,
    enabled: Boolean = true,
    // pivot for the tilt and the scale defaults to the centre which is right for a
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    // where the finger went down as -11 from the centre on each axis held as
    // only read inside the graphicslayer block so moving it never recomposes
    var normalized by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // initial pass unconsumed: this is an observer not a gesture handler
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

                    // hold until every pointer is up or the gesture is cancelled
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
                // y touch drives rotation about x and vice versa — pressing the top edge
                // top away from the viewer negated on x so the pressed side goes back not
                // forward which is what makes it feel pushed rather than pulled
                rotationX = -normalized.y * maxTilt * t
                rotationY = normalized.x * maxTilt * t
                // without a camera distance the rotations are a flat shear scaled off the
                // surface's own width so a wide bar and a small pill get the same apparent
                // rather than the bar looking violently foreshortened
                cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                transformOrigin = origin
            }
        }
}
