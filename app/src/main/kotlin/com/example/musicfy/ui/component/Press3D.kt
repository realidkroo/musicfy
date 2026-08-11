// Press3D.kt
// The "press it and it tilts toward your thumb" effect used by the mini-player pill and the
// navigation bar.
//
// Two things make it read as a physical object rather than a button that shrinks:
//
//   1. It tilts about the point you actually touched. Pressing the left edge rotates the left edge
//      away from you; pressing dead centre only scales. That is the whole reason the effect needs
//      the touch position and not just a pressed/not-pressed boolean.
//   2. The tilt is applied with a real perspective camera distance, so near edges genuinely grow
//      and far edges shrink. Without it, rotationX/Y is an affine squash and looks like a fold.
//
// The gesture is OBSERVED, never consumed: everything here runs on PointerEventPass.Initial and
// consumes nothing, so whatever click/drag handling already exists on the target keeps working
// untouched. That matters for the pill in particular, whose tap-to-expand and drag-to-dismiss are
// owned by BottomSheet several layers up.

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

/** How far the surface may tilt, in degrees, at the very corner. */
private const val DefaultMaxTilt = 7f

/** Scale at full press. */
private const val DefaultPressedScale = 0.97f

/**
 * Springs chosen to settle without visible overshoot on release — a bouncy return reads as a
 * wobble on something that is supposed to feel solid.
 */
private val TiltSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 420f,
)

/**
 * Press feedback that tilts toward the touch point.
 *
 * @param maxTilt degrees of rotation at the extreme edge.
 * @param pressedScale scale at full press.
 * @param enabled when false the modifier is inert, so callers can switch it off (e.g. the pill
 *   while the player is already expanded) without restructuring their modifier chain.
 */
fun Modifier.press3D(
    maxTilt: Float = DefaultMaxTilt,
    pressedScale: Float = DefaultPressedScale,
    enabled: Boolean = true,
    /**
     * Pivot for the tilt and the scale.
     *
     * Defaults to the centre, which is right for a surface that fills its own node. The mini
     * player passes the pill's centre instead: its node is the whole fullscreen morph container,
     * so a centred pivot would swing the pill around a point most of a screen away from it.
     */
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = composed {
    if (!enabled) return@composed this

    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    // Where the finger went down, as -1..1 from the centre on each axis. Held as plain state and
    // only read inside the graphicsLayer block, so moving it never recomposes anything.
    var normalized by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Initial pass, unconsumed: this is an observer, not a gesture handler.
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

                    // Hold until every pointer is up or the gesture is cancelled.
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
                // Y touch drives rotation about X, and vice versa — pressing the top edge tips the
                // top away from the viewer. Negated on X so the pressed side goes back, not
                // forward, which is what makes it feel pushed rather than pulled.
                rotationX = -normalized.y * maxTilt * t
                rotationY = normalized.x * maxTilt * t
                // Without a camera distance the rotations are a flat shear. Scaled off the
                // surface's own width so a wide bar and a small pill get the same apparent depth
                // rather than the bar looking violently foreshortened.
                cameraDistance = (size.width.takeIf { it > 0 }?.toFloat() ?: 1000f) * 3f
                transformOrigin = origin
            }
        }
}
