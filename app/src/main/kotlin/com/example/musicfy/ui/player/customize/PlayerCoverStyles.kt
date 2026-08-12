// playercoverstyleskt
// presentation metadata for playercoverstyle: what each style is called
// vinyl and which option rows the customization page shows underneath its

// the enum itself lives in constants/preferencekeyskt (which stays a plain
// question the editor asks about a style is answered here in one table so
// style is a matter of one enum constant plus one row in each `when` below —
// the ui

package com.example.musicfy.ui.player.customize

import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.Preferences
import com.example.musicfy.R
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.DiscNameKey
import com.example.musicfy.constants.DiscRealisticModeKey
import com.example.musicfy.constants.DiscRotatingAnimationKey
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey

// true for every style that renders a vinyl platter rather than a plain rectangle of artwork
val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

// whether the artwork sits in the inset lower "stage" box rather than filling the
val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        -> false
        else -> true
    }

// the two oversized variants gated behind settings → experimental
val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

// whether this style draws the record player's tonearm only the oversized label
val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

// label shown under "style" on the customization page the concept screens reuse
val PlayerCoverStyle.displayName: String
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE -> "Full edge to edge"
        PlayerCoverStyle.SQUARED -> "Squared"
        PlayerCoverStyle.DISC_SMALL_FULL -> "Disc Music - inside - small"
        PlayerCoverStyle.DISC_SMALL_LABEL -> "Disc Music - label - small"
        PlayerCoverStyle.DISC_BIG_FULL -> "Disc Music - inside - big"
        PlayerCoverStyle.DISC_BIG_LABEL -> "Disc Music - label - big"
        PlayerCoverStyle.DISC_ALBUM -> "Disc + album Music"
    }

// one row in the option card below a style's preview most of these point at
sealed interface CoverOption {
    val title: String
    val description: String
    @get:DrawableRes val icon: Int

    // a switch row [inverted] exists for disableblurkey whose stored sense is
    data class Switch(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    // a free-text row that opens an inline editor
    data class Text(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<String>,
        val placeholder: String,
    ) : CoverOption
}

private val AnimatedCanvasOption = CoverOption.Switch(
    title = "Animated canvas",
    description = "Animated canvas ( not from youtube )",
    icon = R.drawable.sparks,
    key = CanvasThumbnailAnimationKey,
    default = true,
)

private val YtVideoOption = CoverOption.Switch(
    title = "Yt video background",
    description = "Plays yt videos on canvas",
    icon = R.drawable.slow_motion_video,
    key = PlayVideoBackgroundKey,
    default = false,
)

private val TimestampMatchingOption = CoverOption.Switch(
    title = "Timestamp matching",
    description = "Based on subtitle",
    icon = R.drawable.lyrics,
    key = YtVideoBackgroundLyricsSyncKey,
    default = false,
    parent = YtVideoOption,
)

private val RotatingAnimationOption = CoverOption.Switch(
    title = "Rotating animation",
    description = "For the disc",
    icon = R.drawable.refresh,
    key = DiscRotatingAnimationKey,
    default = true,
)

private val RealisticModeOption = CoverOption.Switch(
    title = "Realistic mode",
    description = "For the disc",
    icon = R.drawable.palette,
    key = DiscRealisticModeKey,
    default = true,
)

private val DiscNameOption = CoverOption.Text(
    title = "Disc name",
    description = "Printed on the platter",
    icon = R.drawable.edit,
    key = DiscNameKey,
    placeholder = "Leave empty to hide",
)

private val DiscOptions = listOf(RotatingAnimationOption, RealisticModeOption, DiscNameOption)

// the option rows the customization page shows below this style's preview
val PlayerCoverStyle.options: List<CoverOption>
    get() = when (this) {
        PlayerCoverStyle.SQUARED -> listOf(AnimatedCanvasOption)
        PlayerCoverStyle.EDGE_TO_EDGE -> listOf(
            YtVideoOption,
            TimestampMatchingOption,
            AnimatedCanvasOption,
        )
        else -> DiscOptions
    }
