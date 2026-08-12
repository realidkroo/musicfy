// thumbnailsnaputils kt
// the file functioned as thumbnail snap utils

// musicfy project c 2026 licensed under gpl 3 0 | see git history for contributors snap utilities for thumbnail grid navigation copyright c outertune project custom snaplayoutinfoprovider idea belongs to outertune

package com.example.musicfy.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs

// custom snaplayoutinfoprovider for horizontal grid snapping behavior provides smooth snapping to items based on velocity and position
@ExperimentalFoundationApi
fun ThumbnailSnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider = object : SnapLayoutInfoProvider {
    private val layoutInfo: LazyGridLayoutInfo
        get() = lazyGridState.layoutInfo

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
    
    override fun calculateSnapOffset(velocity: Float): Float {
        val bounds = calculateSnappingOffsetBounds()

        // only snap when velocity exceeds threshold
        if (abs(velocity) < velocityThreshold) {
            return if (abs(bounds.start) < abs(bounds.endInclusive)) {
                bounds.start
            } else {
                bounds.endInclusive
            }
        }

        return when {
            velocity < 0 -> bounds.start
            velocity > 0 -> bounds.endInclusive
            else -> 0f
        }
    }

    private fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

            // find item that is closest to the center
            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }

            // find item that is closest to center but after it
            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }

        return lowerBoundOffset.rangeTo(upperBoundOffset)
    }
}

// calculates the distance from an item s current position to its desired snap position
fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val desiredDistance = positionInLayout(containerSize.toFloat(), item.size.width.toFloat())
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

// extension property to get the viewport size along the scroll axis
val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
