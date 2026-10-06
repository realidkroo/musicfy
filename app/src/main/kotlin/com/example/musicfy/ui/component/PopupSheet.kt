// PopupSheet.kt
//
// The one popup/bottom-sheet primitive for the whole app. See docs/popup-sheet.skill.md
// for the usage contract AI agents (and humans) should follow before building a new popup.
//
// Replaces the formerly-separate BottomSheetMenu/BottomSheetPage/ZoomOutPopupContainer, which
// were three near-identical reimplementations of "show a sheet over the app". This file merges
// them into one engine: PopupSheetHost zooms the app content out slightly and dims it behind
// the active sheet - a plain alpha scrim, deliberately NOT a render-node blur (a full-screen
// blur was tried and reverted: capturing + blurring the whole app every frame is expensive, and
// GlassKit's progressive multi-step blur, built for an ~86dp top bar strip, compounds opacity
// across its overlapping steps when stretched over a full screen until it reads as solid black
// instead of a translucent backdrop).
//
// PopupSheetState is a small stack, not a single slot: calling .show() again while a sheet is
// already visible pushes a new frame on top. Every frame keeps one composition for its whole
// life. It is never rebuilt as a separate "peek" copy when something is pushed over it, nor
// remounted with a fresh slide-up entrance when that something is popped - both were tried, and
// both read as a new window opening (and threw away the covered sheet's state). A covered frame
// recedes in place instead: it scales down (RecedeScale) and rises just enough that a
// PeekHeight strip of its top edge - its handle - shows above the sheet in front. The sheet in
// front opens at least as tall as the one it covers (1:1, per the reference mock), so that strip
// is all that ever shows of it. The previous "handle-only peek" assumed the front sheet would
// always be the taller one; Donate pushed over the taller app-version sheet broke that and
// showed the whole upper half of the sheet behind.
//
// All motion is physics, not timed curves (one shared `PopupSpring`), and each frame owns exactly
// one live value - `Frame.offset` - that its drag, settle, entrance and exit all write. Everything
// else is derived from those values at draw time: a covered frame's recede from the offset of the
// frame in front of it, the app's zoom/dim from the stack's most-open frame. Nothing runs a second
// animation that could drift out of step with the first, so the sheet behind tracks a drag on the
// sheet in front exactly, and pushing or popping never un-zooms the app. While-dragging updates
// write the `mutableFloatStateOf` synchronously - never a suspend call from inside draggable's
// onDelta or a NestedScrollConnection callback (see the skill doc for the two broken versions
// that did). A suspend `animate()` call only ever drives a settle/entrance/exit - one per
// transition, not one per pointer event.

package com.example.musicfy.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.player.menu.MenuSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backing state for a popup sheet stack. Three composition-local instances of this exist
 * ([LocalMenuState], [LocalBottomSheetPageState], [LocalZoomOutOverlayState]) purely so
 * independent corners of the app (action menus, info pages, app-level overlays like Donate)
 * don't fight over one shared "currently showing" slot - [PopupSheetHost] is what actually
 * renders whichever of them is active.
 */
@Stable
class PopupSheetState {
    internal val frames: SnapshotStateList<Frame> = mutableStateListOf()

    val isVisible: Boolean get() = frames.isNotEmpty()

    /**
     * How far the app behind has receded, 0..1. The most-open frame wins, so the zoom/dim only
     * follows a sheet's own drag/entrance/exit when it's the last one up - pushing a sheet, or
     * dragging/popping the one in front of another, never un-zooms the app.
     */
    internal val presence: Float
        get() {
            var max = 0f
            for (i in frames.indices) max = maxOf(max, frames[i].presence)
            return max
        }

    internal class Frame(
        locked: Boolean,
        val buttonBar: (@Composable () -> Unit)?,
        val fullBleed: Boolean,
        /** Height of the sheet this one covered when it was pushed - it opens at least this tall. */
        val minHeightPx: Float,
        val content: @Composable ColumnScope.() -> Unit,
    ) {
        /** Shown unlocked = keeps its handle row even while [locked], so locking can't shift the content. */
        val hasHandle = !locked

        var locked by mutableStateOf(locked)

        /** The frame's one live position: 0 = fully open, 1 = fully off-screen below. Starts off-screen. */
        var offset by mutableFloatStateOf(1f)

        /** 1 = fully open, 0 = gone. */
        val presence: Float get() = (1f - offset).coerceIn(0f, 1f)

        /** Laid-out sheet height in px; 0 until first measured. */
        var heightPx by mutableFloatStateOf(0f)

        /** Set once the frame starts leaving. It's removed from the stack when its exit finishes. */
        var closing by mutableStateOf(false)
    }

    /**
     * Shows [content]. If this state is already visible, pushes it as a new frame on top of the
     * current one, which recedes behind it (shrinks, peeks its handle above the new sheet) and
     * comes back forward when the new one is dismissed - so call this from inside an existing
     * frame's content to "continue into" another screen (e.g. the app-version sheet's
     * `popup.showDonateSheet()`), and `dismiss()` to go back.
     */
    fun show(
        locked: Boolean = false,
        buttonBar: (@Composable () -> Unit)? = null,
        fullBleed: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        // A frame already on its way out stays on top until it's gone, so the new one slides in
        // underneath it rather than over a sheet that's leaving.
        var index = frames.size
        while (index > 0 && frames[index - 1].closing) index--
        val covered = frames.getOrNull(index - 1)
        frames.add(index, Frame(locked, buttonBar, fullBleed, covered?.heightPx ?: 0f, content))
    }

    /** Closes the front sheet: it plays its exit, then is popped, and the one behind comes forward. */
    fun dismiss() {
        frames.lastOrNull { !it.closing }?.closing = true
    }

    /**
     * Locks or unlocks the front sheet in place - the same effect as `show(locked = ...)`, without
     * re-showing anything (re-showing would push a duplicate frame with fresh content state).
     */
    fun setLocked(locked: Boolean) {
        frames.lastOrNull { !it.closing }?.locked = locked
    }

    /** Backdrop/peek taps: ignored while the front sheet is locked or already leaving. */
    internal fun dismissFromOutside() {
        val top = frames.lastOrNull() ?: return
        if (!top.closing && !top.locked) top.closing = true
    }

    internal fun remove(frame: Frame) {
        frames.remove(frame)
    }
}

// Back-compat names for the pre-unification call sites (song/playlist menus, media-info pages,
// app-level overlays like Donate) - they all still say "MenuState" / "BottomSheetPageState" /
// "ZoomOutOverlayState" and don't need to change.
typealias MenuState = PopupSheetState
typealias BottomSheetPageState = PopupSheetState
typealias ZoomOutOverlayState = PopupSheetState

// Kept as separate composition locals (not one shared state) so a menu and a page/overlay
// popup can never silently clobber each other's content mid-transition.
val LocalMenuState = compositionLocalOf { PopupSheetState() }
val LocalBottomSheetPageState = compositionLocalOf { PopupSheetState() }
val LocalZoomOutOverlayState = compositionLocalOf { PopupSheetState() }

private val TabletBreakpoint = 600.dp

/** Leaves this much of the screen visible above the sheet - status bar, zoomed-out app behind. */
private const val MaxSheetHeightFraction = 0.86f

/**
 * One spring, used everywhere in this file for every transition - smooth, unhurried, no overshoot.
 * Positions here are fractions of a sheet's travel, so the visibility threshold has to be tiny:
 * the default (0.01) ended every animation by snapping the last 1% - ~20px on a tall sheet -
 * which showed as a hitch right as the sheet settled.
 */
private val PopupSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = 0.0005f,
)

/** How much the app behind every sheet zooms out. */
private const val BackdropRecede = 0.08f

/** A covered sheet shrinks to this scale (about its top edge)... */
private const val RecedeScale = 0.9f

/** ...and rises until this much of its top edge - its handle - shows above the sheet in front. */
private val PeekHeight = 24.dp

private const val FlingCloseThreshold = 1200f
private const val DragCloseThreshold = 0.35f

/**
 * Wraps the whole app. When any of [states] has a visible frame, [content] zooms out and dims
 * behind the active sheet - this is the single place that owns that transform, so every popup
 * in the app (menus, info pages, donate, update prompts, ...) gets it for free.
 */
@Composable
fun PopupSheetHost(
    states: List<PopupSheetState>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = states.firstOrNull { it.isVisible }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read inside the layer, not in composition: it changes on every frame of
                    // every drag/transition, and this way only this layer re-runs, not the app.
                    val p = active?.presence ?: 0f
                    val scale = 1f - BackdropRecede * p
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape((28f * p).dp)
                    clip = p > 0f
                }
        ) {
            content()
        }

        // Only gated on `active`, which changes once per open/close - never on the live
        // presence, which would recompose everything from here down on every drag delta.
        if (active != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f * active.presence }
                    .background(Color.Black)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = active::dismissFromOutside,
                    )
            )

            key(active) {
                PopupSheetStack(state = active)
            }
        }
    }
}

/** Phone/tablet geometry shared by every frame in a stack. */
@Immutable
private data class StackMetrics(
    val isTablet: Boolean,
    val parentHeightPx: Float,
    val peekPx: Float,
) {
    /** Where an open sheet of this height sits: bottom-anchored on phones, centered on tablets. */
    fun layoutTop(sheetHeightPx: Float): Float =
        if (isTablet) (parentHeightPx - sheetHeightPx) / 2f else parentHeightPx - sheetHeightPx

    /** How far that sheet slides to get from open to fully off the bottom of the screen. */
    fun travel(sheetHeightPx: Float): Float = parentHeightPx - layoutTop(sheetHeightPx)
}

@Composable
private fun PopupSheetStack(state: PopupSheetState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = StackMetrics(
            isTablet = maxWidth >= TabletBreakpoint,
            parentHeightPx = constraints.maxHeight.toFloat(),
            peekPx = with(LocalDensity.current) { PeekHeight.toPx() },
        )

        // One keyed slot per frame, all at this one call site: pushing or popping a frame never
        // disturbs the others' compositions, so the frame behind keeps its content (scroll
        // position, in-flight work) and simply recedes / comes forward in place.
        for (frame in state.frames.toList()) {
            key(frame) {
                PopupSheetFrame(state = state, frame = frame, metrics = metrics)
            }
        }
    }
}

/** How many sheets are in front of `this[index]`, fractional mid-transition. 0 = it's in front. */
private fun List<PopupSheetState.Frame>.depthAbove(index: Int): Float {
    var depth = 0f
    for (i in index + 1 until size) depth += this[i].presence
    return depth
}

/**
 * Where `this[index]`'s top edge rests, its own open/close slide aside: tucked
 * [StackMetrics.peekPx] above the resting top edge of the sheet directly in front of it - or flush
 * behind that one once it has receded too, since only one sheet ever peeks (any further back fade
 * out). Interpolated by how present the sheet in front is, so it follows that sheet's entrance,
 * exit and drag exactly. Because the sheet in front opens at least as tall as this one, this is
 * always "a bit up", never down.
 */
private fun List<PopupSheetState.Frame>.restingTopOf(index: Int, metrics: StackMetrics): Float {
    val ownTop = metrics.layoutTop(this[index].heightPx)
    if (index >= lastIndex) return ownTop
    val inFront = this[index + 1]
    val inFrontDepth = depthAbove(index + 1).coerceAtMost(1f)
    val target = restingTopOf(index + 1, metrics) - metrics.peekPx * (1f - inFrontDepth)
    return ownTop + (target - ownTop) * inFront.presence
}

/**
 * Drives one frame's [PopupSheetState.Frame.offset] - the only thing in this file that animates.
 * At most one animation runs at a time (starting one cancels the last, a drag cancels any), and
 * once the exit has started nothing interrupts it: the frame is removed when it finishes.
 */
private class FrameMotion(
    private val frame: PopupSheetState.Frame,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var exiting = false

    val isRunning: Boolean get() = job?.isActive == true

    /** A finger took over: hold the sheet wherever it is right now. */
    fun stop() {
        if (!exiting) job?.cancel()
    }

    /** [velocity] is in offset units (fractions of the sheet's travel) per second. */
    fun animateTo(target: Float, velocity: Float = 0f) {
        if (!exiting) run(target, velocity) {}
    }

    fun exit(velocity: Float = 0f, onGone: () -> Unit) {
        if (exiting) return
        exiting = true
        frame.closing = true
        run(1f, velocity, onGone)
    }

    private fun run(target: Float, velocity: Float, onEnd: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            animate(frame.offset, target, velocity, PopupSpring) { value, _ -> frame.offset = value }
            onEnd()
        }
    }
}

/**
 * One frame of the stack, for its whole life - front, covered, front again, leaving. Its position
 * is its own `offset` plus the recede derived from the frames in front of it ([restingTopOf]),
 * all applied in one graphicsLayer, so nothing here recomposes while things move.
 */
@Composable
private fun BoxWithConstraintsScope.PopupSheetFrame(
    state: PopupSheetState,
    frame: PopupSheetState.Frame,
    metrics: StackMetrics,
) {
    val scope = rememberCoroutineScope()
    val motion = remember(frame) { FrameMotion(frame, scope) }

    // The front frame is the topmost one that isn't leaving. Everything else is inert: no drag,
    // no back, no taps through to its content.
    val isFront = state.frames.lastOrNull { !it.closing } === frame
    val interactive = isFront && !frame.locked
    val currentInteractive by rememberUpdatedState(interactive)
    val currentMetrics by rememberUpdatedState(metrics)

    fun travelPx(): Float = currentMetrics.travel(frame.heightPx).coerceAtLeast(1f)

    fun close(velocity: Float = 0f) = motion.exit(velocity) { state.remove(frame) }

    fun settle(velocityPx: Float) {
        if (!currentInteractive) {
            if (frame.offset > 0f) motion.animateTo(0f)
            return
        }
        val shouldClose = when {
            velocityPx > FlingCloseThreshold -> true
            velocityPx < -FlingCloseThreshold -> false
            else -> frame.offset > DragCloseThreshold
        }
        val velocity = velocityPx / travelPx()
        if (shouldClose) close(velocity) else motion.animateTo(0f, velocity)
    }

    LaunchedEffect(frame) {
        // Hold the entrance until the sheet has been measured and drawn once, parked off-screen
        // at offset 1 so that frame is invisible - same reason as MenuSheetSurface: composing and
        // laying out the content is the expensive frame, and it shouldn't be the animation's first.
        snapshotFlow { frame.heightPx }.first { it > 0f }
        withFrameNanos { }
        if (!frame.closing) motion.animateTo(0f)
    }

    // dismiss() (a Done button, the backdrop, a peek tap) only flags the frame; this plays the exit.
    LaunchedEffect(frame) {
        snapshotFlow { frame.closing }.first { it }
        close()
    }

    // Covered mid-drag: settle back open behind the new sheet instead of staying half-dragged.
    LaunchedEffect(isFront) {
        if (!isFront && !frame.closing && frame.offset > 0f && !motion.isRunning) motion.animateTo(0f)
    }

    // A locked front sheet still swallows back, so back can't navigate the app away underneath it.
    BackHandler(enabled = isFront) {
        if (!frame.locked) close()
    }

    val dragHandle = Modifier.draggable(
        state = rememberDraggableState { delta ->
            frame.offset = (frame.offset + delta / travelPx()).coerceIn(0f, 1f)
        },
        orientation = Orientation.Vertical,
        enabled = interactive,
        onDragStarted = { motion.stop() },
        onDragStopped = { velocity -> settle(velocity) },
    )

    // Overscrolling the content itself (past its own top) also drags the sheet toward dismissed -
    // the same gesture MenuSheetSurface uses, so "swipe down to close" works from anywhere in the
    // content, not just the small handle. Every update is a direct, synchronous offset write.
    val nestedScrollConnection = remember(frame) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Content scrolling back up while the sheet is pulled down: close that gap first.
                if (!currentInteractive || available.y >= 0f || frame.offset <= 0f) return Offset.Zero
                motion.stop()
                val travel = travelPx()
                val take = (-available.y).coerceAtMost(frame.offset * travel)
                frame.offset = (frame.offset - take / travel).coerceAtLeast(0f)
                return Offset(0f, -take)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only a finger pulls the sheet down - fling momentum hitting the top of the
                // content would otherwise yank the sheet down and spring it back.
                if (!currentInteractive || available.y <= 0f || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                motion.stop()
                frame.offset = (frame.offset + available.y / travelPx()).coerceAtMost(1f)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentInteractive || frame.offset <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // isRunning: onPreFling may already have settled this release.
                if (!currentInteractive || frame.offset <= 0f || motion.isRunning) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    val shape = remember(metrics.isTablet) { sheetSurfaceShape(metrics.isTablet) }
    val minHeight = if (metrics.isTablet) 0.dp else with(LocalDensity.current) { frame.minHeightPx.toDp() }

    Box(
        modifier = Modifier
            .align(if (metrics.isTablet) Alignment.Center else Alignment.BottomCenter)
            .then(sheetSizeModifier(metrics.isTablet))
            .onSizeChanged { frame.heightPx = it.height.toFloat() }
            .graphicsLayer {
                val frames = state.frames
                val index = frames.indexOf(frame)
                if (index < 0) return@graphicsLayer
                val depth = frames.depthAbove(index)
                translationY = frames.restingTopOf(index, metrics) - metrics.layoutTop(frame.heightPx) +
                    frame.offset * metrics.travel(size.height)
                val scale = 1f - (1f - RecedeScale) * depth.coerceAtMost(2f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (depth - 1f).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 0f)
                this.shape = shape
                clip = true
            }
            .drawBehind {
                // Steps up to the row gray as it recedes, so the strip peeking above the sheet in
                // front reads as a separate layer against both that sheet and the dimmed app.
                val frames = state.frames
                val index = frames.indexOf(frame)
                val recede = if (index < 0) 0f else frames.depthAbove(index).coerceAtMost(1f)
                drawRect(lerp(MenuSurface, MenuRowSurface, recede))
            }
            // The whole sheet is a touch target, not just the parts of its content that have one -
            // otherwise a tap on an empty patch (beside a row, around the button bar) fell
            // through to the backdrop or the sheet behind and closed something.
            .pointerInput(Unit) {}
            .then(if (isFront) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        SheetBody(
            frame = frame,
            fillHeight = metrics.isTablet,
            minHeight = minHeight,
            dragHandle = dragHandle,
            contentModifier = Modifier.nestedScroll(nestedScrollConnection),
        )

        if (!isFront) {
            // Covered (or leaving): the content is inert. Tapping the strip that peeks above the
            // front sheet brings this one back, the same as tapping the backdrop.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!frame.closing) state.dismissFromOutside() },
                    )
            )
        }
    }
}

private fun sheetSurfaceShape(isTablet: Boolean): Shape =
    RoundedCornerShape(
        topStart = if (isTablet) 28.dp else 24.dp,
        topEnd = if (isTablet) 28.dp else 24.dp,
        bottomStart = if (isTablet) 28.dp else 0.dp,
        bottomEnd = if (isTablet) 28.dp else 0.dp,
    )

private fun BoxWithConstraintsScope.sheetSizeModifier(isTablet: Boolean): Modifier =
    if (isTablet) {
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxHeight(0.84f)
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight * MaxSheetHeightFraction)
    }

/**
 * Handle + content at the top, the button bar pinned to the bottom. The two only separate when
 * the sheet is taller than what's in it: a pushed sheet opened 1:1 with a taller one behind it
 * ([minHeight]), or the fixed-height tablet card ([fillHeight]).
 */
@Composable
private fun SheetBody(
    frame: PopupSheetState.Frame,
    fillHeight: Boolean,
    minHeight: Dp,
    dragHandle: Modifier,
    contentModifier: Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = minHeight)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // weight(fill = false): measured after the button bar, so long content scrolls within
        // what's left instead of pushing the button bar off the bottom of a full-height sheet.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            if (frame.hasHandle) {
                val handleAlpha = animateFloatAsState(
                    targetValue = if (frame.locked) 0f else 1f,
                    animationSpec = PopupSpring,
                    label = "popupSheetHandle",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .then(dragHandle),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .graphicsLayer { alpha = handleAlpha.value }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            } else {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(contentModifier)
                    .then(if (frame.fullBleed) Modifier else Modifier.padding(horizontal = 20.dp)),
                content = frame.content,
            )
        }

        frame.buttonBar?.let { bar ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                bar()
            }
        }
    }
}

/** Elevated row/card/button sitting directly on a [MenuSurface] sheet - see docs/popup-sheet.skill.md. */
val PopupSheetRowSurface: Color get() = MenuRowSurface
