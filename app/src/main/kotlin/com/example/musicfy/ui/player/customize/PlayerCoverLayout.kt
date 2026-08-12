// playercoverlayoutkt
// where the artwork sits in the expanded player for each cover style

// one function two callers: morphingcover turns it into its full-player
// the customization page's preview stage reproduces the same arrangement at
// preview happens to be keeping it here is what stops the preview and the
// apart as the styles get tuned

package com.example.musicfy.ui.player.customize

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.PlayerCoverStyle

// fraction of the player's height the artwork region occupies measured from the top
const val CoverRegionFraction = 0.63f

// horizontal inset of a boxed-stage artwork from each edge of the player
private val StageInset = 24.dp

@Immutable
data class CoverArtBox(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

// the artwork's rect within the expanded player full-bleed and oversized-disc
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
