// PlayerCoverLayout.kt
// Where the artwork sits in the expanded player, for each cover style.
//
// One function, two callers: MorphingCover turns it into its full-player morph endpoints, and
// the customization page's preview stage reproduces the same arrangement at whatever size the
// preview happens to be. Keeping it here is what stops the preview and the real player drifting
// apart as the styles get tuned.

package com.example.musicfy.ui.player.customize

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.PlayerCoverStyle

/** Fraction of the player's height the artwork region occupies, measured from the top. */
const val CoverRegionFraction = 0.63f

/** Horizontal inset of a boxed-stage artwork from each edge of the player. */
private val StageInset = 24.dp

@Immutable
data class CoverArtBox(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

/**
 * The artwork's rect within the expanded player.
 *
 * Full-bleed and oversized-disc styles get the whole artwork region — running off the edges is
 * the point of them. Everything else gets an inset square, centred in that region.
 */
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
