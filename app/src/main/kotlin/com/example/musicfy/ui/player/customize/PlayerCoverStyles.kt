// PlayerCoverStyles.kt
// Presentation metadata for PlayerCoverStyle: what each style is called, whether it draws a
// vinyl, and which option rows the customization page shows underneath its preview.
//
// The enum itself lives in constants/PreferenceKeys.kt (which stays a plain key registry). Every
// question the editor asks about a style is answered here, in one table, so adding an eighth
// style is a matter of one enum constant plus one row in each `when` below — not a hunt through
// the UI.

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

/** True for every style that renders a vinyl platter rather than a plain rectangle of artwork. */
val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

/**
 * Whether the artwork sits in the inset, lower "stage" box rather than filling the top of the
 * player.
 *
 * True for everything except the full-bleed cover and the two oversized disc variants — those
 * three are *meant* to run to the screen edges, so boxing them would defeat the style. The rest
 * were sitting too high with too much dead space beneath them; the stage bottom-aligns them
 * against the controls instead.
 */
val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        -> false
        else -> true
    }

/** The two oversized variants, gated behind Settings → Experimental. */
val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

/**
 * Whether this style draws the record player's tonearm.
 *
 * Only the oversized label variant has the empty platter area to rest one on without covering
 * the artwork (concept screen 87). The skip choreography reads this to decide whether to play
 * its lift/drop windows or go straight to the disc swap.
 */
val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

/**
 * Label shown under "Style" on the customization page.
 *
 * The concept screens reuse one caption for both small-disc variants and again for both big-disc
 * ones; two identically-labelled pages in a carousel are unusable, so the pairs are split by what
 * actually differs between them — whether the artwork fills the platter ("inside") or sits on it
 * as a centre label ("label").
 */
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

/**
 * One row in the option card below a style's preview.
 *
 * Most of these point at preferences that already existed and are already exposed in Settings →
 * Appearance; surfacing them here is a second view onto the same key, not a parallel setting.
 */
sealed interface CoverOption {
    val title: String
    val description: String
    @get:DrawableRes val icon: Int

    /**
     * A switch row.
     *
     * [inverted] exists for DisableBlurKey, whose stored sense is negative while every place it
     * is shown to the user phrases it positively ("Blur", on = blurring) — the same treatment
     * AppearanceSettingsScreen already gives it.
     *
     * [parent] indents this row under another switch and hides it while that switch is off,
     * matching the "Timestamp matching" sub-option in concept screen 82.
     */
    data class Switch(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    /** A free-text row that opens an inline editor. */
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

/** The option rows the customization page shows below this style's preview. */
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
