// pagehelper kt
// this thing is for page helper

package com.music.innertube.pages

import com.music.innertube.models.Menu
import com.music.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import com.music.innertube.models.Run

object PageHelper {
    // icon types for library management youtube changed these in feb 2026
    // old icons library_add not in library library_saved library_remove in library
    // new icons bookmark_border not in library bookmark in library
    // note keep keep_off are for pin to listen again different from library
    private val LIBRARY_ADD_ICONS = setOf("LIBRARY_ADD", "BOOKMARK_BORDER")
    private val LIBRARY_SAVED_ICONS = setOf("LIBRARY_SAVED", "BOOKMARK", "LIBRARY_REMOVE")
    private val ALL_LIBRARY_ICONS = LIBRARY_ADD_ICONS + LIBRARY_SAVED_ICONS

    // data class to hold both library feedback tokens extracted from a menu
    data class LibraryFeedbackTokens(
        val addToken: String?,      // token to add song to library from bookmark_border
        val removeToken: String?    // token to remove song from library from bookmark
    )

    // check if an icon type is a library related icon for filtering menu items excludes keep keep_off which are for pin to listen again
    fun isLibraryIcon(iconType: String?): Boolean {
        if (iconType == null) return false
        // exclude keep keep_off listen again pins
        if (iconType == "KEEP" || iconType == "KEEP_OFF") return false
        return iconType in ALL_LIBRARY_ICONS || iconType.startsWith("LIBRARY_")
    }

    // check if an icon type indicates the song is not in library add state
    fun isAddLibraryIcon(iconType: String?): Boolean {
        return iconType in LIBRARY_ADD_ICONS
    }

    // check if an icon type indicates the song is in library saved remove state
    fun isSavedLibraryIcon(iconType: String?): Boolean {
        return iconType in LIBRARY_SAVED_ICONS
    }

    fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
        val filteredRuns = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }

    // extract library feedback tokens from a list of menu items this function iterates through all toggle menu items and extracts tokens based on their icon types ensuring we don t confuse library tokens with pin to listen again tokens keep keep_off youtube s icon system feb 2026 bookmark_border song not in library > defaulttoken = add toggledtoken = remove bookmark song is in library > defaulttoken = remove toggledtoken = add keep keep_off pin to listen again completely different must be ignored
    fun extractLibraryTokensFromMenuItems(
        menuItems: List<Menu.MenuRenderer.Item>?
    ): LibraryFeedbackTokens {
        if (menuItems == null) return LibraryFeedbackTokens(null, null)

        var addToken: String? = null
        var removeToken: String? = null

        for (item in menuItems) {
            val toggleRenderer = item.toggleMenuServiceItemRenderer ?: continue
            val iconType = toggleRenderer.defaultIcon.iconType

            // skip keep keep_off icons pin to listen again these are not library actions
            if (iconType == "KEEP" || iconType == "KEEP_OFF") continue

            // only process library related icons
            if (!isLibraryIcon(iconType)) continue

            val defaultToken = toggleRenderer.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
            val toggledToken = toggleRenderer.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken

            // determine which token is which based on icon type
            when {
                isAddLibraryIcon(iconType) -> {
                    // bookmark_border or library_add default=add toggled=remove
                    if (addToken == null) addToken = defaultToken
                    if (removeToken == null) removeToken = toggledToken
                }
                isSavedLibraryIcon(iconType) -> {
                    // bookmark or library_saved remove default=remove toggled=add
                    if (removeToken == null) removeToken = defaultToken
                    if (addToken == null) addToken = toggledToken
                }
            }
        }

        return LibraryFeedbackTokens(addToken, removeToken)
    }

    // extract feedback token for library operations youtube s new icon system feb 2026 bookmark_border song not in library > defaulttoken = add toggledtoken = remove bookmark song is in library > defaulttoken = remove toggledtoken = add
    fun extractFeedbackToken(menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?, type: String): String? {
        if (menu == null) return null
        val defaultToken = menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
        val toggledToken = menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken
        val iconType = menu.defaultIcon.iconType

        // determine if the current icon indicates song is not in library
        // bookmark_border or library_add = song is not in library default action is add
        val songNotInLibrary = iconType in LIBRARY_ADD_ICONS

        return when (type) {
            "LIBRARY_ADD" -> {
                // we want the add token
                if (songNotInLibrary) {
                    // icon shows add state default action adds to library
                    defaultToken
                } else {
                    // icon shows saved state toggled action would add back
                    toggledToken
                }
            }
            "LIBRARY_REMOVE", "LIBRARY_SAVED" -> {
                // we want the remove token
                if (songNotInLibrary) {
                    // icon shows add state toggled action would remove
                    toggledToken
                } else {
                    // icon shows saved state default action removes from library
                    defaultToken
                }
            }
            else -> if (iconType == type) defaultToken else toggledToken
        }
    }
}