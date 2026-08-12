// PlayerEditOverlay.kt

package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

enum class PlayerEditTarget {
    COVER,

    CONTROLS,

    BOTTOM_CARD,
}

enum class PlayerEditPhase {

    NONE,

    ENTERING,

    SELECTING,

    CUSTOMIZING,
}

@Composable
fun PlayerEditOverlay(
    coverRect: Rect?,
    controlsRect: Rect?,
    bottomCardRect: Rect?,
    onSelect: (PlayerEditTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val appearAnim = remember { Animatable(0f) }
    val appear = appearAnim.value
    var chosen by remember { mutableStateOf<PlayerEditTarget?>(null) }

    LaunchedEffect(Unit) {
        appearAnim.animateTo(1f, tween(360, easing = EditOverlayEasing))
    }
    LaunchedEffect(chosen) {
        val target = chosen ?: return@LaunchedEffect
        appearAnim.animateTo(0f, tween(300, easing = EditOverlayEasing))
        onSelect(target)
    }
    val select: (PlayerEditTarget) -> Unit = { target -> if (chosen == null) chosen = target }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()

            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                }
            }

            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .background(Color.Black.copy(alpha = 0.06f * appear))
    ) {
        val bounds = Rect(
            left = 0f,
            top = 0f,
            right = constraints.maxWidth.toFloat(),
            bottom = constraints.maxHeight.toFloat(),
        )

        EditOverlayBackButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = appear },
        )

        Text(
            text = "Select the part that you want to edit",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 30.dp, start = 76.dp, end = 76.dp)
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * -16f
                },
        )

        val headerBottomPx = with(LocalDensity.current) {
            (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HeaderClearance).toPx()
        }

        if (coverRect != null) {

            val coverIsFullBleed = coverRect.left <= bounds.left + 1f &&
                coverRect.right >= bounds.right - 1f
            EditTargetOutline(
                rect = coverRect,
                bounds = bounds,
                appear = { appear },
                topLimitPx = headerBottomPx,
                sideRailsOnly = coverIsFullBleed,

                fadeBottom = true,
            ) { select(PlayerEditTarget.COVER) }
        }
        if (controlsRect != null) {
            EditTargetOutline(rect = controlsRect, bounds = bounds, appear = { appear }) {
                select(PlayerEditTarget.CONTROLS)
            }
        }
        if (bottomCardRect != null) {
            EditTargetOutline(
                rect = bottomCardRect,
                bounds = bounds,
                appear = { appear },

                clampBottom = false,
            ) { select(PlayerEditTarget.BOTTOM_CARD) }
        }
    }
}

@Composable
fun EditOverlayBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 20.dp, top = 12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back_ios),
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EditTargetOutline(
    rect: Rect,
    bounds: Rect,
    appear: () -> Float,

    topLimitPx: Float = 0f,

    fadeBottom: Boolean = false,

    clampBottom: Boolean = true,

    sideRailsOnly: Boolean = false,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current

    val padPx = with(density) { OutlinePadding.toPx() }
    val marginPx = with(density) { OutlineScreenMargin.toPx() }

    val left = (rect.left - padPx).coerceAtLeast(bounds.left + marginPx)
    val top = (rect.top - padPx).coerceAtLeast(maxOf(bounds.top + marginPx, topLimitPx))
    val right = (rect.right + padPx).coerceAtMost(bounds.right - marginPx)
    val bottom = if (clampBottom) {
        (rect.bottom + padPx).coerceAtMost(bounds.bottom - marginPx)
    } else {
        rect.bottom + padPx
    }
    if (right <= left || bottom <= top) return

    val width = with(density) { (right - left).toDp() }
    val height = with(density) { (bottom - top).toDp() }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .offset { IntOffset(left.toInt(), top.toInt()) }
            .graphicsLayer { alpha = appear() }

            .drawWithCache {
                val strokePx = OutlineStroke.toPx()
                val radiusPx = OutlineCorner.toPx()
                val brush = if (fadeBottom) {
                    Brush.verticalGradient(
                        0f to OutlineColor,
                        FadeStart to OutlineColor,
                        1f to Color.Transparent,
                    )
                } else {
                    SolidColor(OutlineColor)
                }
                onDrawWithContent {
                    drawContent()
                    if (sideRailsOnly) {
                        val x0 = strokePx / 2f
                        val x1 = size.width - strokePx / 2f

                        drawLine(
                            brush = brush,
                            start = Offset(x0, 0f),
                            end = Offset(x0, size.height),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            brush = brush,
                            start = Offset(x1, 0f),
                            end = Offset(x1, size.height),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    } else {
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(strokePx / 2f, strokePx / 2f),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = CornerRadius(radiusPx, radiusPx),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    )
}

private val EditOverlayEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val OutlinePadding = 10.dp
private val OutlineScreenMargin = 12.dp
private val OutlineStroke = 6.dp
private val OutlineCorner = 30.dp
private val OutlineColor = Color.White.copy(alpha = 0.6f)

private const val FadeStart = 0.55f

private val HeaderClearance = 76.dp
