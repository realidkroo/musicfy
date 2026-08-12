// MenuSheet.kt

package com.example.musicfy.ui.player.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val MenuSurface = Color(0xFF0B0B0C)
val MenuRowSurface = Color(0xFF161619)

internal val MenuEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

internal const val MenuAnimMillis = 440

private const val LockedDragResistance = 0.18f

private const val LockedDragMaxPx = 90f

private const val FlingThreshold = 700f

@Composable
fun MenuSheetSurface(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    halfDetent: Float = 0.56f,
    fullDetent: Float = 0.94f,

    wrapHeight: Boolean = false,

    dismissEnabled: Boolean = true,
    revealProvider: ((Float) -> Unit)? = null,
    content: @Composable ColumnScope.(dragHandle: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var closing by remember { mutableStateOf(false) }

    val closeRef = remember { mutableStateOf({}) }
    BackHandler(onBack = { closeRef.value() })

    val offsetPx = remember { mutableFloatStateOf(Float.MAX_VALUE) }

    val measuredHeightPx = remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetPx = with(density) { (maxHeight * fullDetent).toPx() }
        val fullHeightPx = if (wrapHeight) {
            measuredHeightPx.floatValue.takeIf { it > 0f } ?: maxSheetPx
        } else {
            maxSheetPx
        }

        val restingOffsetPx = if (wrapHeight) 0f else fullHeightPx * (1f - halfDetent / fullDetent)

        fun revealFraction(): Float =
            (1f - (offsetPx.floatValue - restingOffsetPx) / (fullHeightPx - restingOffsetPx).coerceAtLeast(1f))
                .coerceIn(0f, 1f)

        fun setOffset(value: Float) {
            offsetPx.floatValue = value
            revealProvider?.invoke(revealFraction())
        }

        LaunchedEffect(Unit) {
            if (offsetPx.floatValue != Float.MAX_VALUE) return@LaunchedEffect
            val start = if (wrapHeight) {

                snapshotFlow { measuredHeightPx.floatValue }.first { it > 0f }
            } else {
                maxSheetPx
            }

            val target = if (wrapHeight) 0f else start * (1f - halfDetent / fullDetent)
            setOffset(start)
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = androidx.compose.animation.core.tween(MenuAnimMillis, easing = MenuEasing),
            ) { value, _ -> setOffset(value) }
        }

        val animateTo: (Float, () -> Unit) -> Unit = { target, onEnd ->
            scope.launch {
                animate(
                    initialValue = offsetPx.floatValue,
                    targetValue = target,
                    animationSpec = androidx.compose.animation.core.tween(MenuAnimMillis, easing = MenuEasing),
                ) { value, _ -> setOffset(value) }
                onEnd()
            }
        }

        closeRef.value = {
            if (!closing && dismissEnabled) {
                closing = true
                animateTo(fullHeightPx) { onDismiss() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = revealFraction() }
                .background(Color.Black.copy(alpha = 0.6f))
                .then(
                    if (dismissEnabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { closeRef.value() },
                        )
                    } else Modifier
                )
        )

        fun settle(velocity: Float) {
            val current = offsetPx.floatValue
            val target = when {
                !dismissEnabled -> restingOffsetPx
                velocity > FlingThreshold -> if (current > restingOffsetPx * 0.6f) fullHeightPx else restingOffsetPx
                velocity < -FlingThreshold -> 0f
                else -> listOf(0f, restingOffsetPx, fullHeightPx).minBy { kotlin.math.abs(it - current) }
            }
            if (target >= fullHeightPx && dismissEnabled) {
                closing = true
                animateTo(fullHeightPx) { onDismiss() }
            } else {
                animateTo(target) {}
            }
        }

        val sheetNestedScroll = remember(fullHeightPx, restingOffsetPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {

                    if (available.y < 0f && offsetPx.floatValue > 0f) {
                        val take = (-available.y).coerceAtMost(offsetPx.floatValue)
                        setOffset(offsetPx.floatValue - take)
                        return Offset(0f, -take)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {

                    if (available.y > 0f && !dismissEnabled) return Offset.Zero
                    if (available.y > 0f) {
                        setOffset((offsetPx.floatValue + available.y).coerceAtMost(fullHeightPx))
                        return available
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offsetPx.floatValue > 0f && available.y < 0f) {
                        settle(-available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (offsetPx.floatValue > 0f) {
                        settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (wrapHeight) {
                        Modifier
                            .heightIn(max = maxHeight * fullDetent)
                            .onSizeChanged { measuredHeightPx.floatValue = it.height.toFloat() }
                    } else {
                        Modifier.height(maxHeight * fullDetent)
                    }
                )
                .nestedScroll(sheetNestedScroll)
                .graphicsLayer { translationY = offsetPx.floatValue }
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MenuSurface)
        ) {
            val dragHandle = Modifier.draggable(
                state = rememberDraggableState { delta ->
                    if (dismissEnabled) {
                        setOffset((offsetPx.floatValue + delta).coerceIn(0f, fullHeightPx))
                    } else {

                        val resisted = (offsetPx.floatValue + delta * LockedDragResistance)
                            .coerceIn(0f, LockedDragMaxPx)
                        setOffset(resisted)
                    }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> settle(velocity) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .height(38.dp)
                    .then(dragHandle),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(64.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }

            content(dragHandle)
        }
    }
}
