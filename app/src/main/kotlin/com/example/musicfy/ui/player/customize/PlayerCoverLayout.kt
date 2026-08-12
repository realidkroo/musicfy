// PlayerCoverLayout.kt

package com.example.musicfy.ui.player.customize

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.PlayerCoverStyle

const val CoverRegionFraction = 0.63f

private val StageInset = 24.dp

@Immutable
data class CoverArtBox(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

fun coverArtBox(
    style: PlayerCoverStyle,
    maxWidth: Dp,
    maxHeight: Dp,
    statusBarTop: Dp,
): CoverArtBox {
    val regionHeight = maxHeight * CoverRegionFraction
    if (!style.usesBoxedStage) {
        return CoverArtBox(x = 0.dp, y = 0.dp, width = maxWidth, height = regionHeight)
    }
    val side = (maxWidth - StageInset * 2).coerceAtLeast(0.dp)
    val y = ((regionHeight - side) / 2).coerceAtLeast(statusBarTop + 16.dp)
    return CoverArtBox(x = StageInset, y = y, width = side, height = side)
}
