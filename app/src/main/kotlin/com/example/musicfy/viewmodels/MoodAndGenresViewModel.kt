// MoodAndGenresViewModel.kt
// Backs the search landing page's "browse by moods" and genre grids.

package com.example.musicfy.viewmodels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import com.music.innertube.pages.MoodAndGenres
import com.example.musicfy.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Featured-playlist artwork for the mood/genre tiles, cached for the whole process rather than per
 * ViewModel.
 *
 * Each entry costs one `browse` round trip, and there are ~40 tiles across the two grids. Scoping
 * the cache to the ViewModel meant every visit to the search tab refetched everything the user
 * scrolled past last time; an object outlives navigation, so a tile is fetched at most once per
 * app run.
 */
private object MoodCoverCache {
    val covers = mutableStateMapOf<String, List<String>>()
    val requested = java.util.Collections.synchronizedSet(mutableSetOf<String>())
}

/**
 * Cache key for one tile.
 *
 * Every mood shares a single browseId and is distinguished only by its `params` — keying on the
 * browseId alone collapsed the whole grid onto one entry, so every mood tile drew the artwork of
 * whichever category happened to resolve first.
 */
fun coverKey(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    /** [coverKey] -> up to two cover URLs for that category's featured playlists. */
    val covers = MoodCoverCache.covers

    /**
     * At most three category browses in flight at once.
     *
     * The grids can bring 20+ tiles on screen in one fling, and firing a browse per tile saturated
     * the connection pool — the visible tiles' own artwork (and anything the player was streaming)
     * queued behind a burst of requests for tiles that had already scrolled away. Three keeps the
     * covers filling in visibly while leaving headroom.
     */
    private val gate = Semaphore(3)

    init {
        viewModelScope.launch {
            YouTube
                .moodAndGenres()
                .onSuccess {
                    moodAndGenres.value = it
                }.onFailure {
                    reportException(it)
                }
        }
    }

    /**
     * Fetch the artwork for one tile, once. Safe to call from composition on every recomposition of
     * a visible tile — the [MoodCoverCache.requested] guard makes every call after the first a
     * set lookup.
     */
    fun requestCovers(browseId: String, params: String?) {
        val key = coverKey(browseId, params)
        if (!MoodCoverCache.requested.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            gate.withPermit {
                YouTube.browse(browseId, params)
                    .onSuccess { result ->
                        val urls = result.items
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull(YTItem::thumbnail)
                            .distinct()
                            .take(2)
                            .toList()
                        if (urls.isNotEmpty()) {
                            MoodCoverCache.covers[key] = urls
                        }
                    }
                    .onFailure {
                        // Allow a later retry — a tile that failed once (offline, throttled) should
                        // not be stuck blank for the rest of the process.
                        MoodCoverCache.requested.remove(key)
                    }
            }
        }
    }
}
