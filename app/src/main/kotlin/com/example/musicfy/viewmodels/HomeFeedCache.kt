// HomeFeedCache.kt
// Disk cache for HomeViewModel's four pure-network-sourced feeds (homePage,
// communityPlaylists, allTimeHits, explorePage) — everything else HomeViewModel loads
// is local-DB-backed already and shows up instantly, so only these need caching.
// Same SharedPreferences + kotlinx.serialization.json pattern as EQProfileRepository.kt.

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.core.content.edit
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import kotlinx.serialization.json.Json

class HomeFeedCache(context: Context) {
    private val prefs = context.getSharedPreferences("home_feed_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadHomePage(): HomePage? = load(KEY_HOME_PAGE)
    fun saveHomePage(value: HomePage) = save(KEY_HOME_PAGE, value)

    fun loadCommunityPlaylists(): List<CommunityPlaylistItem>? = load(KEY_COMMUNITY_PLAYLISTS)
    fun saveCommunityPlaylists(value: List<CommunityPlaylistItem>) = save(KEY_COMMUNITY_PLAYLISTS, value)

    fun loadAllTimeHits(): List<YTItem>? = load(KEY_ALL_TIME_HITS)
    fun saveAllTimeHits(value: List<YTItem>) = save(KEY_ALL_TIME_HITS, value)

    fun loadExplorePage(): ExplorePage? = load(KEY_EXPLORE_PAGE)
    fun saveExplorePage(value: ExplorePage) = save(KEY_EXPLORE_PAGE, value)

    // Falls back to null on any decode failure (e.g. a shape change between app
    // versions) rather than crashing — the network phase always runs regardless and
    // will overwrite it with a fresh value.
    private inline fun <reified T> load(key: String): T? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <reified T> save(key: String, value: T) {
        try {
            prefs.edit { putString(key, json.encodeToString(value)) }
        } catch (e: Exception) {
        }
    }

    private companion object {
        const val KEY_HOME_PAGE = "home_page"
        const val KEY_COMMUNITY_PLAYLISTS = "community_playlists"
        const val KEY_ALL_TIME_HITS = "all_time_hits"
        const val KEY_EXPLORE_PAGE = "explore_page"
    }
}
