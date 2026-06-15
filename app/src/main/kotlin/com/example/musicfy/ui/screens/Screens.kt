// Screens.kt
// what is this for you ask its for screens ofc

package com.example.musicfy.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.example.musicfy.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    val iconIdInactive: Int,
    val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.raw.home,
        iconIdActive = R.raw.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.raw.search,
        iconIdActive = R.raw.search_filled,
        route = "search_input"
    )

    object Library : Screens(
        titleId = R.string.your_library,
        iconIdInactive = R.raw.library_music_outlined,
        iconIdActive = R.raw.library_music_filled,
        route = "library"
    )

    object Settings : Screens(
        titleId = R.string.settings,
        iconIdInactive = R.drawable.settings,
        iconIdActive = R.drawable.settings,
        route = "settings"
    )

    companion object {
        val MainScreens = listOf(Home, Search, Library, Settings)
    }
}

enum class NavigationTab {
    HOME, SEARCH
}

enum class DarkMode {
    AUTO, ON, OFF
}

enum class LyricsPosition {
    LEFT, CENTER, RIGHT
}

enum class OptionStats {
    WEEKS, MONTHS, YEARS, CONTINUOUS
}
