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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val MenuSurface = Color(0xFF0B0B0C)
val MenuRowSurface = Color(0xFF161619)

internal val MenuEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

internal const val MenuAnimMillis = 440

private const val LockedDragResistance = 0.18f

private const val LockedDragMaxPx = 90f

private const val FlingThreshold = 700f

/** A covered sheet shrinks to this scale (about its top edge)... */
private const val RecedeScale = 0.9f

/**
 * ...and rises until this much of its top edge shows above the sheet in front. Larger than
 * PopupSheet's 24dp because this sheet's handle pill sits lower (12dp padding inside a 38dp row) -
 * this leaves the same ~8dp of strip above the front sheet's edge under the pill.
 */
private val PeekHeight = 32.dp

/**
 * The player's sheets opened from one another - the action menu, then "Change device output",
 * playback speed, sleep timer or a lyrics tool on top of it, then the language picker on top of
 * the translation sheet. Same stacking as `ui/component/PopupSheet.kt`, matched to the same
 * reference mock: a covered sheet scales to [RecedeScale] and rises so only a [PeekHeight] strip
 * of it (its handle) shows above the sheet in front, the sheet in front opens at least as tall as
 * the visible part of the one it covers (1:1, so the covered content never shows), and sheets
 * further back fade out. Everything is derived at draw time from the front sheet's own live
 * offset, so the sheet behind follows its entrance, exit and drags exactly.
 *
 * Provide one with [LocalMenuSheetStack] around every [MenuSheetSurface] that can open another;
 * a surface with no stack provided just behaves as a standalone sheet.
 */
@Stable
class MenuSheetStack internal constructor(internal val scope: CoroutineScope) {
    internal val entries = mutableStateListOf<MenuSheetEntry>()
}

@Composable
fun rememberMenuSheetStack(): MenuSheetStack {
    val scope = rememberCoroutineScope()
    return remember { MenuSheetStack(scope) }
}

val LocalMenuSheetStack = staticCompositionLocalOf<MenuSheetStack?> { null }

/**
 * Closes the [MenuSheetSurface] this is read inside with its normal exit animation, then calls
 * its `onDismiss`. Buttons inside a sheet's content that finish its job ("Save", "Set timer",
 * picking an entry) should call this rather than the `onDismiss` they were handed: that one
 * removes the sheet on the spot, skipping the slide-out, and the sheet behind it with it.
 */
val LocalMenuSheetClose = staticCompositionLocalOf<() -> Unit> {
    error("LocalMenuSheetClose read outside a MenuSheetSurface")
}

/** One [MenuSheetSurface]'s geometry, published for the sheets behind/in front of it. */
internal class MenuSheetEntry {
    var parentHeightPx by mutableFloatStateOf(0f)
    var sheetHeightPx by mutableFloatStateOf(0f)

    /** translationY of the sheet; Float.MAX_VALUE until its entrance starts. */
    var offsetPx by mutableFloatStateOf(Float.MAX_VALUE)

    /** The offset of its lowest open detent - anything further down is on its way in or out. */
    var restingOffsetPx by mutableFloatStateOf(0f)

    /** 0 = closed, 1 = open at (or above) its resting detent. */
    var presence by mutableFloatStateOf(0f)

    /** Set when the composable left without animating out - see [MenuSheetSurface]'s dispose. */
    var ghost = false

    /** Top edge if nothing were in front of it. */
    fun layoutTop(): Float = parentHeightPx - sheetHeightPx + offsetPx.coerceAtMost(parentHeightPx)

    /**
     * Top edge as an anchor for the sheet behind: never lower than the resting detent, so while
     * this sheet slides in or out the sheet behind interpolates toward a fixed point (by
     * [presence]) instead of chasing it down to the bottom of the screen.
     */
    fun anchorTop(): Float = parentHeightPx - sheetHeightPx + minOf(offsetPx, restingOffsetPx)
}

private fun List<MenuSheetEntry>.depthAbove(index: Int): Float {
    var depth = 0f
    for (i in index + 1 until size) depth += this[i].presence
    return depth
}

/** How far `this[index]` moves (on top of its own offset) to tuck behind the sheets in front. */
private fun List<MenuSheetEntry>.recedeShift(index: Int, peekPx: Float): Float {
    if (index >= lastIndex) return 0f
    val inFront = this[index + 1]
    val inFrontDepth = depthAbove(index + 1).coerceAtMost(1f)
    // Flush behind the sheet in front once that one has receded too: only one sheet ever peeks.
    val target = inFront.anchorTop() + recedeShift(index + 1, peekPx) - peekPx * (1f - inFrontDepth)
    return (target - this[index].anchorTop()) * inFront.presence
}

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
    val stack = LocalMenuSheetStack.current
    val peekPx = with(density) { PeekHeight.toPx() }

    var closing by remember { mutableStateOf(false) }

    val closeRef = remember { mutableStateOf({}) }
    val forceCloseRef = remember { mutableStateOf({}) }
    BackHandler(onBack = { closeRef.value() })

    val entry = remember { MenuSheetEntry() }
    val closeSheet: () -> Unit = remember { { forceCloseRef.value() } }

    // Captured once, when this sheet opens: how much of the sheet it's covering is visible. This
    // sheet then opens at least that tall (1:1), which is what keeps the covered sheet's content
    // hidden behind it - only its peeking strip shows.
    val coveredVisiblePx = remember {
        val entries = stack?.entries?.filter { !it.ghost }.orEmpty()
        val covered = entries.lastOrNull()
        if (covered == null) {
            0f
        } else {
            val index = entries.lastIndex
            val top = covered.layoutTop() + entries.recedeShift(index, peekPx)
            (covered.parentHeightPx - top).coerceAtLeast(0f)
        }
    }
    val isCovering = coveredVisiblePx > 0f

    DisposableEffect(stack, entry) {
        stack?.entries?.add(entry)
        onDispose {
            if (stack == null) return@onDispose
            if (entry.presence <= 0.001f) {
                stack.entries.remove(entry)
            } else {
                // Removed without its exit (the caller dropped it from composition directly).
                // The sheet itself is already gone, but let the one behind come forward smoothly
                // rather than snapping.
                entry.ghost = true
                stack.scope.launch {
                    animate(
                        initialValue = entry.presence,
                        targetValue = 0f,
                        animationSpec = androidx.compose.animation.core.tween(MenuAnimMillis, easing = MenuEasing),
                    ) { value, _ -> entry.presence = value }
                    stack.entries.remove(entry)
                }
            }
        }
    }

    val offsetPx = remember { mutableFloatStateOf(Float.MAX_VALUE) }

    val measuredHeightPx = remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentHeightPx = constraints.maxHeight.toFloat()
        val detentSheetPx = with(density) { (maxHeight * fullDetent).toPx() }
        val maxSheetPx = maxOf(detentSheetPx, coveredVisiblePx)
        val fullHeightPx = if (wrapHeight) {
            measuredHeightPx.floatValue.takeIf { it > 0f } ?: maxSheetPx
        } else {
            maxSheetPx
        }

        val restingOffsetPx = if (wrapHeight) {
            0f
        } else {
            val restVisiblePx = maxOf(with(density) { (maxHeight * halfDetent).toPx() }, coveredVisiblePx)
            (fullHeightPx - restVisiblePx).coerceIn(0f, fullHeightPx)
        }

        SideEffect {
            entry.parentHeightPx = parentHeightPx
            entry.restingOffsetPx = restingOffsetPx
        }

        fun revealFraction(): Float =
            (1f - (offsetPx.floatValue - restingOffsetPx) / (fullHeightPx - restingOffsetPx).coerceAtLeast(1f))
                .coerceIn(0f, 1f)

        fun setOffset(value: Float) {
            offsetPx.floatValue = value
            entry.offsetPx = value
            val reveal = revealFraction()
            entry.presence = reveal
            revealProvider?.invoke(reveal)
        }

        LaunchedEffect(Unit) {
            if (offsetPx.floatValue != Float.MAX_VALUE) return@LaunchedEffect

            // Do not start the entrance until the sheet has actually been measured AND drawn once.
            //
            // This is the whole reason opening the menu felt slow. Everything a menu costs —
            // composing a dozen-plus rows, inflating a vector drawable for each one, the artwork
            // request, reading the audio device — landed on the very frame the animation began,
            // so the opening frames were starved and the slide stuttered before it settled. None
            // of that work got cheaper here; it just no longer happens *during* the animation.
            //
            // offsetPx starts at Float.MAX_VALUE, which parks the sheet far off-screen, so this
            // warm-up frame is invisible: the expensive first layout and draw happen out of sight,
            // and the animation then runs against a composition that is already warm.
            snapshotFlow { measuredHeightPx.floatValue }.first { it > 0f }
            withFrameNanos { }

            val start = if (wrapHeight) measuredHeightPx.floatValue else maxSheetPx
            val target = if (wrapHeight) 0f else restingOffsetPx
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

        forceCloseRef.value = {
            if (!closing) {
                closing = true
                animateTo(fullHeightPx) { onDismiss() }
            }
        }

        closeRef.value = {
            if (dismissEnabled) forceCloseRef.value()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Covering another sheet: the backdrop is already dimmed by the sheet at the
                    // bottom of the stack. Dimming again here would black out the strip of the
                    // covered sheet that's meant to peek above this one.
                    alpha = if (isCovering) 0f else revealFraction()
                }
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

        val sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (wrapHeight) {
                        Modifier.heightIn(
                            min = with(density) { coveredVisiblePx.toDp() },
                            max = with(density) { maxSheetPx.toDp() },
                        )
                    } else {
                        Modifier.height(with(density) { fullHeightPx.toDp() })
                    }
                )
                // Reported for both kinds of sheet, not just wrapHeight ones. A wrapHeight sheet
                // needs the value to know how far to travel; a fixed-height sheet needs it purely
                // as the signal that layout has happened, which is what the entrance waits on.
                .onSizeChanged {
                    measuredHeightPx.floatValue = it.height.toFloat()
                    entry.sheetHeightPx = it.height.toFloat()
                }
                .nestedScroll(sheetNestedScroll)
                .graphicsLayer {
                    var shift = 0f
                    var depth = 0f
                    if (stack != null) {
                        val entries = stack.entries
                        val index = entries.indexOf(entry)
                        if (index >= 0) {
                            shift = entries.recedeShift(index, peekPx)
                            depth = entries.depthAbove(index)
                        }
                    }
                    translationY = offsetPx.floatValue + shift
                    if (depth > 0f) {
                        val scale = 1f - (1f - RecedeScale) * depth.coerceAtMost(2f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        alpha = 1f - (depth - 1f).coerceIn(0f, 1f)
                    }
                    shape = sheetShape
                    clip = true
                }
                .drawBehind {
                    // Steps up to the row gray as it recedes, so the strip peeking above the
                    // sheet in front reads as its own layer instead of melting into it.
                    var recede = 0f
                    if (stack != null) {
                        val entries = stack.entries
                        val index = entries.indexOf(entry)
                        if (index >= 0) recede = entries.depthAbove(index).coerceAtMost(1f)
                    }
                    drawRect(lerp(MenuSurface, MenuRowSurface, recede))
                }
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

            CompositionLocalProvider(LocalMenuSheetClose provides closeSheet) {
                content(dragHandle)
            }
        }
    }
}
