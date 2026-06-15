/**
 * musicfy Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.example.musicfy.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeChild
import com.example.musicfy.LocalHazeState
import com.example.musicfy.LocalPlayerConnection
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.NavigationBarAnimationSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.pow

private val PlayerSheetAnimationSpec = spring<Dp>(
    dampingRatio = 0.96f,
    stiffness = 80f
)

private val PlayerSheetHorizontalAnimationSpec = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = 90f
)

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    isPillTransition: Boolean = false,
    sharedContent: @Composable (BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val progress = state.progress.coerceIn(0f, 1f)
    
    if (!isPillTransition) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    // background fades during about 10%-61% progress
                    alpha = (1.4f * (state.progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
                }
                .fillMaxSize(),
            content = background
        )
    }
        if (!isPillTransition) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val y = (state.expandedBound - state.value).toPx().coerceAtLeast(0f)
                        translationY = y
                        val cornerRadius = if (!state.isExpanded) 16.dp.toPx() else 0f
                        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                        clip = true
                    }
                    .pointerInput(state, isExpandable) {
                        if (!isExpandable) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                state.dispatchRawDelta(dragAmount)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                state.snapTo(state.collapsedBound)
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                state.performFling(velocity, onDismiss)
                            }
                        )
                    }
            ) {
                if (!state.isCollapsed && !state.isDismissed) {
                    BackHandler(onBack = state::collapseSoft)
                }

                if (!state.isCollapsed) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = ((state.progress - 0.15f) * 4).coerceIn(0f, 1f)
                            },
                        content = content
                    )
                }

                if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (isExpandable) state.expandSoft() },
                            )
                            .fillMaxWidth()
                            .height(state.collapsedBound),
                        content = collapsedContent,
                    )
                }

                if (sharedContent != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        content = sharedContent
                    )
                }
            }
        } else {
            // iOS Pill Morphing Transition
            val cornerRadius = androidx.compose.ui.unit.lerp(20.dp, 0.dp, progress)
            val horizontalPadding = androidx.compose.ui.unit.lerp(24.dp, 0.dp, progress)
            val bottomPadding = androidx.compose.ui.unit.lerp(state.collapsedBound - 64.dp, 0.dp, progress)
            val hazeState = LocalHazeState.current
            val playerConnection = LocalPlayerConnection.current
            val containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
            
            val coroutineScope = rememberCoroutineScope()
            val animationSpec = remember {
                PlayerSheetHorizontalAnimationSpec
            }
            var dragStartTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var totalHorizontalDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
            ) {
                // Expanding clipping container anchored to bottom
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(state.value) // Height morphs physically
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            bottom = bottomPadding
                        )
                        .graphicsLayer {
                            shape = RoundedCornerShape(cornerRadius)
                            clip = true
                            translationX = state.horizontalOffset
                        }
                        .let {
                            val blurAlpha = (1f - progress / 0.9f).coerceIn(0f, 1f)
                            if (hazeState != null && blurAlpha > 0f) {
                                it.hazeEffect(
                                    state = hazeState,
                                    style = HazeStyle(
                                        backgroundColor = containerColor.copy(alpha = blurAlpha),
                                        tint = HazeTint(containerColor.copy(alpha = 0.65f * blurAlpha)),
                                        blurRadius = 24.dp
                                    )
                                )
                            } else {
                                it.background(containerColor.copy(alpha = 0.85f * blurAlpha))
                            }
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(cornerRadius))
                        .pointerInput(state, isExpandable) {
                            if (!isExpandable) return@pointerInput
                            val velocityTracker = VelocityTracker()
                            
                            // CHANGED TO detectDragGestures WITH PROPER X/Y ISOLATION BAKA!
                            detectDragGestures(
                                onDragStart = {
                                    dragStartTime = System.currentTimeMillis()
                                    totalHorizontalDrag = 0f
                                    velocityTracker.resetTracking()
                                },
                                onDrag = { change, dragAmount ->
                                    velocityTracker.addPointerInputChange(change)
                                    
                                    // If dragging mostly horizontally, resist vertical scrolling!
                                    if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) && state.isCollapsed) {
                                        totalHorizontalDrag += dragAmount.x
                                        val resistance = 1f - (kotlin.math.abs(state.horizontalOffset) / (size.width / 2f)).coerceIn(0f, 0.8f)
                                        state.horizontalOffset += dragAmount.x * resistance
                                    } else {
                                        state.dispatchRawDelta(dragAmount.y)
                                    }
                                },
                                onDragCancel = {
                                    velocityTracker.resetTracking()
                                    state.snapTo(state.collapsedBound)
                                    coroutineScope.launch {
                                        state.animateHorizontalOffsetTo(0f)
                                    }
                                },
                                onDragEnd = {
                                    val velocity = -velocityTracker.calculateVelocity().y
                                    velocityTracker.resetTracking()
                                    
                                    if (state.isCollapsed || progress < 0.2f) {
                                        val dragDuration = System.currentTimeMillis() - dragStartTime
                                        val horizontalVelocity = if (dragDuration > 0) totalHorizontalDrag / dragDuration else 0f
                                        val currentOffset = state.horizontalOffset
                                        
                                        val shouldChangeSong = (kotlin.math.abs(currentOffset) > 50f && kotlin.math.abs(horizontalVelocity) > 2f) ||
                                                (kotlin.math.abs(currentOffset) > size.width / 3f)
                                                
                                        if (shouldChangeSong && playerConnection != null) {
                                            if (currentOffset > 0) playerConnection.player.seekToPreviousMediaItem()
                                            else playerConnection.player.seekToNext()
                                        }
                                    }
                                    
                                    state.performFling(velocity, onDismiss)
                                    coroutineScope.launch { state.animateHorizontalOffsetTo(0f) }
                                }
                            )
                        }
                ) {
                    // Apply animated padding internally so it doesn't affect sharedContent coordinates
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Background inside pill
                        Box(
                            modifier = Modifier
                                .requiredWidth(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
                                .requiredHeight(state.expandedBound)
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .graphicsLayer { 
                                    alpha = progress 
                                    translationX = -horizontalPadding.toPx()
                                }
                        ) {
                            background()
                        }

                        // Shared content behind the controls
                        if (sharedContent != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .align(androidx.compose.ui.Alignment.BottomCenter),
                                content = sharedContent
                            )
                        }

                        // Full content fixed to expandedBound height so it doesn't squish
                        if (!state.isCollapsed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .requiredHeight(state.expandedBound)
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .graphicsLayer {
                                        // Fades in starting after 20%
                                        alpha = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
                                        // Slide up from bottom like iOS
                                        translationY = (1f - progress) * 200.dp.toPx()
                                    }
                            ) {
                                content()
                            }
                        }
                    }
                } // Close expanding clipping container

                // MiniPlayer content drawn OUTSIDE the expanding container so it doesn't get clipped!
                if (!isPillTransition && !state.isExpanded && (onDismiss == null || !state.isDismissed)) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(state.collapsedBound)
                            .graphicsLayer {
                                // Fades out quickly in first 30%
                                alpha = (1f - (progress / 0.3f)).coerceIn(0f, 1f)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (isExpandable) state.expandSoft() },
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.TopStart
                    ) {
                        collapsedContent()
                    }
                }
                
                if (!state.isCollapsed && !state.isDismissed) {
                    BackHandler(onBack = state::collapseSoft)
                }
            }
        }
    }

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
) : DraggableState by draggableState {
    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    var horizontalOffset by androidx.compose.runtime.mutableFloatStateOf(0f)
        internal set

    suspend fun animateHorizontalOffsetTo(targetValue: Float) {
        androidx.compose.animation.core.animate(
            initialValue = horizontalOffset,
            targetValue = targetValue,
            animationSpec = PlayerSheetHorizontalAnimationSpec
        ) { value, _ ->
            horizontalOffset = value
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(collapsedAnchor)
        coroutineScope.launch {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(expandedAnchor)
        coroutineScope.launch {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    private fun collapse() {
        collapse(PlayerSheetAnimationSpec)
    }

    private fun expand() {
        expand(PlayerSheetAnimationSpec)
    }

    fun collapseSoft() {
        collapse(PlayerSheetAnimationSpec)
    }

    fun expandSoft() {
        expand(PlayerSheetAnimationSpec)
    }

    fun dismiss() {
        onAnchorChanged(dismissedAnchor)
        coroutineScope.launch {
            animatable.animateTo(animatable.lowerBound!!)
        }
    }
    
    suspend fun dismissAndWait() {
        onAnchorChanged(dismissedAnchor)
        animatable.animateTo(animatable.lowerBound!!)
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch {
            animatable.snapTo(value)
        }
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> collapse()
                in l2..l3 -> expand()
                else -> Unit
            }
        }
    }

    val preUpPostDownNestedScrollConnection
        get() = object : NestedScrollConnection {
            var isTopReached = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (isTopReached) {
                    val velocity = -available.y
                    performFling(velocity, null)

                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTopReached = false
                return Velocity.Zero
            }
        }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable = remember {
        Animatable(0.dp, Dp.VectorConverter)
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            expandedAnchor -> expandedBound
            collapsedAnchor -> collapsedBound
            dismissedAnchor -> dismissedBound
            else -> error("Unknown BottomSheet anchor")
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch {
            animatable.animateTo(initialValue, NavigationBarAnimationSpec)
        }

        BottomSheetState(
            draggableState = DraggableState { delta ->
                coroutineScope.launch {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound
        )
    }
}
