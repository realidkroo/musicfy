// ArtistSimilarViewModel.kt

package com.example.musicfy.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.utils.ArtistPageCache
import com.example.musicfy.utils.ArtistVideos
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.ArtistPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** the world around one artist, grouped by kind rather than by who it came from */
data class SimilarGroups(
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
)

@HiltViewModel
class ArtistSimilarViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!

    private val _page = MutableStateFlow(ArtistPageCache.page(artistId))
    val page: StateFlow<ArtistPage?> = _page

    /** what the Home box showed, when the page was opened from one: it leads every group */
    val picks = ArtistPageCache.picks(artistId)
    private val pickedSongs = picks?.items.orEmpty().filterIsInstance<SongItem>().distinctBy { it.id }
    private val pickedAlbums = picks?.items.orEmpty().filterIsInstance<AlbumItem>().distinctBy { it.id }
    private val pickedPlaylists = picks?.items.orEmpty().filterIsInstance<PlaylistItem>().distinctBy { it.id }
    private val pickedArtists = picks?.items.orEmpty().filterIsInstance<ArtistItem>().distinctBy { it.id }

    private val _groups = MutableStateFlow(
        picks?.let {
            SimilarGroups(
                artists = pickedArtists,
                playlists = pickedPlaylists,
                songs = pickedSongs,
                albums = pickedAlbums,
            )
        }
    )
    val groups: StateFlow<SimilarGroups?> = _groups

    /** the songs and albums come from the similar artists' own pages, so they land a little later */
    private val _deepLoading = MutableStateFlow(true)
    val deepLoading: StateFlow<Boolean> = _deepLoading

    init {
        viewModelScope.launch {
            val page = _page.value ?: YouTube.artist(artistId)
                .onFailure(::reportException)
                .getOrNull()
                ?.also {
                    ArtistPageCache.putPage(artistId, it)
                    _page.value = it
                }
            if (page == null) {
                _groups.value = _groups.value ?: SimilarGroups()
                _deepLoading.value = false
                return@launch
            }

            val artists = (pickedArtists + page.sections
                .filter { it.items.firstOrNull() is ArtistItem }
                .flatMap { it.items.filterIsInstance<ArtistItem>() })
                .distinctBy { it.id }
            val playlists = (pickedPlaylists + page.sections
                .filter { it.items.firstOrNull() is PlaylistItem }
                .flatMap { it.items.filterIsInstance<PlaylistItem>() })
                .distinctBy { it.id }
            _groups.value = SimilarGroups(artists = artists, playlists = playlists, songs = pickedSongs, albums = pickedAlbums)

            // a handful of the similar artists' pages at once, each given a few seconds; the slow
            // or failing ones are just left out
            val pages = withContext(Dispatchers.IO) {
                artists.take(5).map { artist ->
                    async {
                        ArtistPageCache.page(artist.id)
                            ?: withTimeoutOrNull(8_000) { YouTube.artist(artist.id).getOrNull() }
                                ?.also { ArtistPageCache.putPage(artist.id, it) }
                    }
                }.awaitAll()
            }.filterNotNull()

            // the box's own songs first, then three top songs from each similar artist, one artist
            // at a time so no single one fills the row
            // one list, deduplicated as a whole: a repeated id would crash the row's keys
            val songs = (pickedSongs + ArtistVideos.interleave(
                pages.map { similar ->
                    similar.sections
                        .firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
                        ?.items?.filterIsInstance<SongItem>()
                        ?.take(3)
                        .orEmpty()
                },
                limit = 15,
            )).distinctBy { it.id }
            // and each one's newest record
            val albums = (pickedAlbums + pages.mapNotNull { similar ->
                similar.sections
                    .filter { it.items.firstOrNull() is AlbumItem }
                    .flatMap { it.items.filterIsInstance<AlbumItem>() }
                    .maxByOrNull { it.year ?: 0 }
            }).distinctBy { it.id }

            _groups.value = SimilarGroups(artists = artists, playlists = playlists, songs = songs, albums = albums)
            _deepLoading.value = false
        }
    }
}
