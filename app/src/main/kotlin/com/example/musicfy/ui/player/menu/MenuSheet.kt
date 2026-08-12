// menusheetkt
// shared chrome for the player s sheets the surface the grab bar the in and
// and the drag scroll behaviour that lets one be expanded or thrown away

// all three sheets actions sleep timer playback speed go through this so
// the motion can only ever be defined once

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

// sheet colours deliberately darker than the beta notice s #161616
val MenuSurface = Color(0xFF0B0B0C)
val MenuRowSurface = Color(0xFF161619)
// menu open close back curve cubic bezier 057 053 0 1 the same curve the
internal val MenuEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

// slightly longer than the old 380ms the curve above spends its tail
internal const val MenuAnimMillis = 440

// how much of a drag a locked sheet actually follows
private const val LockedDragResistance = 0.18f

// furthest a locked sheet will travel before it stops moving at all in px
private const val LockedDragMaxPx = 90f

// fling speed px s past which a release overrides the nearest snap point
private const val FlingThreshold = 700f

// don t expand behind in step with it
@Composable
fun MenuSheetSurface(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    halfDetent: Float = 0.56f,
    fullDetent: Float = 0.94f,
    // sizes the sheet to its content instead of to halfdetent capped at
    wrapHeight: Boolean = false,
    // blocks the scrim back and drag to dismiss for a sheet mid download
    dismissEnabled: Boolean = true,
    revealProvider: ((Float) -> Unit)? = null,
    content: @Composable ColumnScope.(dragHandle: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var closing by remember { mutableStateOf(false) }

    // filled in once the sheet s height is known so back and the scrim can both
    val closeRef = remember { mutableStateOf({}) }
    BackHandler(onBack = { closeRef.value() })

    // offset in pixels from fully expanded 0 = top detent restingoffset = where
    // sheetheight = gone one continuous quantity with three snap points which is
    // bottom sheet model and fixes both of the previous behaviours

    // it used to hold a 01 expansion clamped at 0 so a downward drag could
    // accumulate past the resting detent and pull to dismiss was unreachable
    // out was the scrim or back
    // the settle used to run while the nested scroll fling was also being
    // two fighting is what read as a spring
    val offsetPx = remember { mutableFloatStateOf(Float.MAX_VALUE) }

    // in wrap mode the sheet s own measured height is the travel so it has to be
    // rather than derived from a detent
    val measuredHeightPx = remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetPx = with(density) { (maxHeight * fullDetent).toPx() }
        val fullHeightPx = if (wrapHeight) {
            measuredHeightPx.floatValue.takeIf { it > 0f } ?: maxSheetPx
        } else {
            maxSheetPx
        }
        // wrap sheets have no half detent they open at their natural height which
        // the only size they have
        val restingOffsetPx = if (wrapHeight) 0f else fullHeightPx * (1f - halfDetent / fullDetent)

        // 1 when the sheet is at rest or above ramping to 0 as it leaves drives
        // the caller s zoom out
        fun revealFraction(): Float =
            (1f - (offsetPx.floatValue - restingOffsetPx) / (fullHeightPx - restingOffsetPx).coerceAtLeast(1f))
                .coerceIn(0f, 1f)

        // every write to the offset goes through here reporting from the writer
        // from a launchedeffect keyed on the value is what keeps the offset out of
        // entirely keyed that way the whole sheet recomposed on every frame of
        // animation which is what made switching away and back pop
        fun setOffset(value: Float) {
            offsetPx.floatValue = value
            revealProvider?.invoke(revealFraction())
        }

        // first frame start off screen then run in to the resting detent

        // keyed on unit not on fullheightpx a wrap height sheet does not know its
        // the first frame onsizechanged reports it a frame or two later so
        // changes just after composition keyed on that value the change cancelled
        // mid animation and the relaunch immediately no opped because the sentinel
        // was already false the sheet was left parked at whatever offset the aborted
        // had reached ie still translated off the bottom of the screen composed and
        // interactive its backhandler and clicks worked but never visible that is
        // made the update detail sheet and the home update prompt not open while
        // fixed height sheets in the same file were fine

        // waiting for a real measurement instead means the entrance starts from the
        // actual height and nothing can interrupt it once it is running
        LaunchedEffect(Unit) {
            if (offsetPx.floatValue != Float.MAX_VALUE) return@LaunchedEffect
            val start = if (wrapHeight) {
                // suspends until the content has been measured at least once
                snapshotFlow { measuredHeightPx.floatValue }.first { it > 0f }
            } else {
                maxSheetPx
            }
            // wrap sheets open at their natural height so their resting offset is simply
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

        // nearest snap point with velocity allowed to override it
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
                    // dragging up closes the gap to the top detent before the list scrolls
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
                    // leftover downward scroll means the body is at its top push the sheet down
                    // all the way to dismissal if the drag keeps going not while locked
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
                        // locked a download is in flight rather than ignoring the drag which
                        // reads as the sheet being broken it gives a little heavily damped and
                        // capped then springs back on release the gesture is answered and the
                        // answer is visibly no
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
                    // generous grab area the visible bar is 4dp the target is not
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
