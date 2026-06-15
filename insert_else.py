import sys

with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'r') as f:
    lines = f.readlines()

new_code = """        } else {
            // iOS Pill Morphing Transition
            val cornerRadius = androidx.compose.ui.unit.lerp(20.dp, 0.dp, progress)
            val horizontalPadding = androidx.compose.ui.unit.lerp(24.dp, 0.dp, progress)
            val bottomPadding = androidx.compose.ui.unit.lerp(state.collapsedBound - 64.dp, 0.dp, progress)
            val hazeState = LocalHazeState.current
            val playerConnection = LocalPlayerConnection.current
            
            val coroutineScope = rememberCoroutineScope()
            var dragStartTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            var totalHorizontalDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
            ) {
                // Background fades in over the whole screen, behind everything
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(state.expandedBound)
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .graphicsLayer { alpha = progress }
                ) {
                    background()
                }

                // Shared content behind the controls (morphing elements) drawn OUTSIDE so it doesn't clip
                if (sharedContent != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(state.expandedBound)
                            .align(androidx.compose.ui.Alignment.BottomCenter),
                        content = sharedContent
                    )
                }

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
                        .let {
                            if (hazeState != null && progress < 0.9f) {
                                // Fade out blur slightly as it expands to not clash with fully expanded bg
                                val hazeAlpha = (1f - progress / 0.9f).coerceIn(0f, 1f)
                                it.hazeEffect(
                                    state = hazeState,
                                    style = HazeStyle(
                                        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                                        tint = HazeTint(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f * hazeAlpha)),
                                        blurRadius = 24.dp
                                    )
                                )
                            } else it
                        }
                        .graphicsLayer {
                            shape = RoundedCornerShape(cornerRadius)
                            clip = true
                            translationX = state.horizontalOffset
                        }
                        .pointerInput(state, isExpandable) {
                            if (!isExpandable) return@pointerInput
                            val velocityTracker = VelocityTracker()
                            
                            // CHANGED TO detectDragGestures WITH PROPER X/Y ISOLATION BAKA!
                            androidx.compose.foundation.gestures.detectDragGestures(
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
                } // Close expanding clipping container

                // MiniPlayer content drawn OUTSIDE the expanding container so it doesn't get clipped!
                if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
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
"""

lines.insert(146, new_code + "\n")

# add imports if missing
imports = [
    "import dev.chrisbanes.haze.HazeStyle\n",
    "import dev.chrisbanes.haze.HazeTint\n",
    "import dev.chrisbanes.haze.hazeEffect\n",
    "import com.example.musicfy.ui.theme.LocalHazeState\n",
    "import com.example.musicfy.LocalPlayerConnection\n"
]
for imp in imports:
    if imp not in lines:
        lines.insert(10, imp)

with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'w') as f:
    f.writelines(lines)

print("Inserted else block into BottomSheet.kt!")
