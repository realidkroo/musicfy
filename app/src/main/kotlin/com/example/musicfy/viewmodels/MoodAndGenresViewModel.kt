// moodandgenresviewmodelkt
// backs the search landing page s browse by moods and genre grids

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

// featured playlist artwork for the mood genre tiles cached for the whole process
private object MoodCoverCache {
    val covers = mutableStateMapOf<String, List<String>>()
    val requested = java.util.Collections.synchronizedSet(mutableSetOf<String>())
}

// cache key for one tile every mood shares a single browseid and is distinguished
fun coverKey(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    // coverkey > up to two cover urls for that category s featured playlists
    val covers = MoodCoverCache.covers

    // at most three category browses in flight at once the grids can bring 20+ tiles
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

    // fetch the artwork for one tile once safe to call from composition on every
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
                        // allow a later retry a tile that failed once offline throttled should
                        // not be stuck blank for the rest of the process
                        MoodCoverCache.requested.remove(key)
                    }
            }
        }
    }
}
