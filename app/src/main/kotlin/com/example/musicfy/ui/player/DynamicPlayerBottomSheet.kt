package com.example.musicfy.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.example.musicfy.ui.component.BottomSheetState
import kotlin.math.roundToInt
import kotlin.math.pow

/**
 * A custom transition layout for the player that morphs from the MiniPlayer "pill"
 * to the FullScreen player instead of using a standard bottom-up sliding sheet.
 */
@Composable
fun DynamicPlayerBottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = state.progress.coerceIn(0f, 1f)
    val density = LocalDensity.current

    // Morphing calculations
    val cornerRadius = androidx.compose.ui.unit.lerp(16.dp, 0.dp, progress)
    val horizontalPadding = androidx.compose.ui.unit.lerp(8.dp, 0.dp, progress)
    val bottomPadding = androidx.compose.ui.unit.lerp(8.dp, 0.dp, progress)

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background layer (morphing bounds)
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .height(state.value) // Height morphs dynamically based on swipe!
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding
                )
                .graphicsLayer {
                    shape = RoundedCornerShape(cornerRadius)
                    clip = true
                }
                .pointerInput(state) {
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
            // Draw background
            Box(Modifier.fillMaxSize()) { background() }
            
            if (!state.isCollapsed && !state.isDismissed) {
                BackHandler(onBack = state::collapseSoft)
            }

            // Crossfade content based on progress
            // MiniPlayer fades out quickly in the first 30% of the drag
            val miniPlayerAlpha = (1f - (progress / 0.3f)).coerceIn(0f, 1f)
            
            // FullPlayer fades in after 20%
            val fullPlayerAlpha = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)

            // Main Player Content
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(state.expandedBound) // Prevents vertical squishing during morph!
                        .align(androidx.compose.ui.Alignment.BottomCenter) // Anchor controls to bottom of expanding pill
                        .graphicsLayer { alpha = fullPlayerAlpha }
                ) {
                    content()
                }
            }

            // MiniPlayer Content
            if (progress < 1f && (onDismiss == null || !state.isDismissed)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(state.collapsedBound)
                        .graphicsLayer { alpha = miniPlayerAlpha }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { state.expandSoft() },
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    collapsedContent()
                }
            }
        }
    }
}
