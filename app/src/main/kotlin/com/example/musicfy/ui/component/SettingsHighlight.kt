package com.example.musicfy.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Carries "take me to this setting" from a settings search result to the page that owns it.
 *
 * Held here rather than as a navigation argument because the settings routes are registered
 * without arguments - `navigate("appearance_settings?highlight=x")` would simply fail to match.
 *
 * [pending] is claimed by the first matching row that composes, which then flashes and clears it,
 * so returning to the same page later does not re-trigger the highlight.
 */
object SettingsHighlight {
    /** Requested but not yet claimed by a row. */
    var pending: String? by mutableStateOf(null)

    /** Currently flashing. */
    var active: String? by mutableStateOf(null)
}
