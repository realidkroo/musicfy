// genreviewmodelkt
// backs the genre / mood page opened from the search landing grids

package com.example.musicfy.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.BrowseResult
import com.example.musicfy.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")?.takeIf { it != "null" }

    val result = MutableStateFlow<BrowseResult?>(null)

    // the "from the community" row: the genre's own playlists each resolved to its
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)

    init {
        viewModelScope.launch {
            YouTube.browse(browseId, params)
                .onSuccess { page ->
                    result.value = page
                    loadCommunity(page)
                }
                .onFailure {
                    reportException(it)
                    result.value = BrowseResult(title = null, items = emptyList())
                }
        }
    }

    // only three playlists are expanded and they are fetched in parallel: this row is
    private suspend fun loadCommunity(page: BrowseResult) {
        val candidates = page.items
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<PlaylistItem>()
            .distinctBy { it.id }
            .take(3)
            .toList()

        if (candidates.isEmpty()) {
            communityPlaylists.value = emptyList()
            return
        }

        val resolved = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())
        coroutineScope {
            candidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { detail ->
                        val songs = detail.songs.take(6)
                        if (songs.isNotEmpty()) {
                            resolved.add(
                                CommunityPlaylistItem(
                                    playlist.copy(
                                        songCountText = detail.playlist.songCountText
                                            ?: playlist.songCountText
                                    ),
                                    songs,
                                )
                            )
                        }
                    }
                }
            }.forEach { it.join() }
        }
        communityPlaylists.value = resolved.toList()
    }
}
