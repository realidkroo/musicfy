// LibraryHomeViewModel.kt

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.AlbumSortType
import com.example.musicfy.constants.ArtistSortType
import com.example.musicfy.constants.PlaylistSortType
import com.example.musicfy.constants.SongSortType
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.SpeedDialItem
import com.example.musicfy.utils.ArtistImageResolver
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Backs the Library home screen: the four category counts/covers, the pinned grid, and the
 * Recently added / Downloaded preview cards.
 *
 * Deliberately its own view model rather than reusing [HomeViewModel]'s `speedDialItems` — that
 * flow pads pinned items out to 27 with "keep listening" filler for the Home feed's speed-dial
 * carousel, which is exactly wrong here: the Library screen's Pinned section is only supposed to
 * show what was actually pinned, and hides entirely when that's empty.
 */
@HiltViewModel
class LibraryHomeViewModel
@Inject
constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    val pinnedItems = database.speedDialDao.getAll()
        .map { pinned -> pinned.map { it.toYTItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs = database.songs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists = database.artists(ArtistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums = database.albums(AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedSongs = database.downloadedSongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Songs imported from the device, as opposed to [downloadedSongs] which are streamed tracks
     * cached for offline. The Downloaded screen shows both, and they are genuinely different
     * things — "All local music" would be wrong if it silently included cached streams.
     */
    val localSongs = songs
        .map { list -> list.filter { it.song.isLocal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Artist rows have no picture until something goes and fetches one: the artist table only
        // stores a thumbnail once it has been resolved, and nothing resolves it as a side effect of
        // reading the table. LibraryArtistsViewModel already did this, but the rebuilt Library
        // screens read from here instead, so without this block every artist in the library
        // rendered as a placeholder icon forever.
        //
        // Refreshes anything missing a picture, or whose picture is over ten days old — artists
        // change their images and a permanently cached one goes stale.
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { list ->
                list.map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                            Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
                    }
                    .forEach { artist ->
                        val preferred = ArtistImageResolver.resolveThumbnail(artist)
                        YouTube.artist(artist.id)
                            .onSuccess { page ->
                                database.query { update(artist, page, preferred) }
                            }
                            .onFailure {
                                // The lookup failing doesn't mean we have nothing — the resolver
                                // may still have found an image locally, and dropping it here
                                // would leave the row blank for another ten days.
                                if (preferred != null) {
                                    database.query {
                                        update(
                                            artist.copy(
                                                thumbnailUrl = preferred,
                                                lastUpdateTime = LocalDateTime.now(),
                                            )
                                        )
                                    }
                                }
                            }
                    }
            }
        }
    }

    fun unpin(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.speedDialDao.delete(id)
        }
    }

    fun pin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            database.pinToSpeedDial(SpeedDialItem.fromYTItem(item))
        }
    }
}
