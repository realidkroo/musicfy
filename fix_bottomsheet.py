import sys

with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'r') as f:
    lines = f.readlines()

# Find the start of the block
start_idx = -1
for i, line in enumerate(lines):
    if 'Box(' in line and 'modifier = modifier' in lines[i+1]:
        start_idx = i
        break

# Find the end of the block
end_idx = -1
for i in range(start_idx, len(lines)):
    if '} // Close expanding clipping container' in lines[i]:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    new_code = """            Box(
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
"""
    
    lines[start_idx:end_idx+1] = [new_code]
    with open('app/src/main/kotlin/com/example/musicfy/ui/component/BottomSheet.kt', 'w') as f:
        f.writelines(lines)
    print("Fixed BottomSheet.kt!")
else:
    print("Could not find block")
