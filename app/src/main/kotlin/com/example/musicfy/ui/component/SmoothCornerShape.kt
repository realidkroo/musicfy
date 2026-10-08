// SmoothCornerShape.kt

package com.example.musicfy.ui.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A rectangle whose corners are quarters of a superellipse rather than of a circle: the "squircle"
 * of app icons. The curve meets each straight edge with no change of curvature, which is what lets
 * it read as one soft shape instead of a box with its corners sanded.
 *
 * [radius] is how far in from the edges each corner reaches, as for a rounded rectangle; a larger
 * [exponent] squares the corner up, 2 is a plain circle. Used for the covers and for the rows and
 * cards they sit in, so the two families share a curve - a row's radius is its cover's plus the
 * padding between them, and the corners stay concentric.
 */
@Immutable
data class SmoothCornerShape(
    val radius: Dp,
    val exponent: Float = 4f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(
            Path().apply { addSmoothRoundedRect(size.width, size.height, r, exponent.toDouble()) }
        )
    }
}

/** Points per corner: plenty for an edge this smooth at any size a corner is drawn. */
private const val CornerSteps = 12

private fun Path.addSmoothRoundedRect(w: Float, h: Float, r: Float, exponent: Double) {
    val power = 2.0 / exponent

    // How far a point on the quarter-superellipse is from the corner's centre, along each axis,
    // for the angle t of 0..90 degrees: r * |cos t|^(2/n) and r * |sin t|^(2/n).
    fun along(t: Double): Float = (r * abs(cos(t)).pow(power)).toFloat()
    fun across(t: Double): Float = (r * abs(sin(t)).pow(power)).toFloat()

    moveTo(r, 0f)
    lineTo(w - r, 0f)

    // top right: from the top edge round to the right edge
    for (i in 1..CornerSteps) {
        val t = PI / 2 * i / CornerSteps
        lineTo(w - r + across(t), r - along(t))
    }
    lineTo(w, h - r)

    // bottom right
    for (i in 1..CornerSteps) {
        val t = PI / 2 * i / CornerSteps
        lineTo(w - r + along(t), h - r + across(t))
    }
    lineTo(r, h)

    // bottom left
    for (i in 1..CornerSteps) {
        val t = PI / 2 * i / CornerSteps
        lineTo(r - across(t), h - r + along(t))
    }
    lineTo(0f, r)

    // top left
    for (i in 1..CornerSteps) {
        val t = PI / 2 * i / CornerSteps
        lineTo(r - along(t), r - across(t))
    }
    close()
}
