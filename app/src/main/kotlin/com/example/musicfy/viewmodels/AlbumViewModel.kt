// AlbumViewModel.kt

package com.example.musicfy.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.utils.reportException
import com.example.musicfy.utils.ArtistImageResolver
import com.example.musicfy.utils.ArtistPageCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.musicfy.utils.Wikipedia
import com.example.musicfy.utils.AppleMusicAboutAlbum
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
@Inject
constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val playlistId = MutableStateFlow("")
    val albumWithSongs =
        database
            .albumWithSongs(albumId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    var releasesForYou = MutableStateFlow<List<AlbumItem>>(emptyList())
    var description = MutableStateFlow<String?>(null)
    var descriptionRuns = MutableStateFlow<List<com.music.innertube.models.Run>?>(null)

    /** the main artist's other albums and singles, newest first, without this one */
    val moreByArtist = MutableStateFlow<List<AlbumItem>>(emptyList())

    /** YouTube couldn't be reached and nothing was saved to show instead: the page offers a retry */
    val loadFailed = MutableStateFlow(false)

    init {
        load()
    }

    fun retry() {
        loadFailed.value = false
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            if (album?.description != null) {
                description.value = album.description
            }
            // a saved album can start its automix before YouTube has answered
            album?.album?.playlistId?.let { if (playlistId.value.isEmpty()) playlistId.value = it }
            album?.artists?.firstOrNull()?.id?.let(::loadMoreByArtist)
            YouTube
                .album(albumId)
                .onSuccess {
                    loadFailed.value = false
                    playlistId.value = it.album.playlistId
                    if (moreByArtist.value.isEmpty()) {
                        it.album.artists?.firstOrNull()?.id?.let(::loadMoreByArtist)
                    }
                    otherVersions.value = it.otherVersions
                    releasesForYou.value = it.releasesForYou
                    if (it.description != null) {
                        description.value = it.description
                    }
                    descriptionRuns.value = it.descriptionRuns
                    database.transaction {
                        if (album == null) {
                            insert(it)
                        } else {
                            update(album.album, it, album.artists)
                        }
                    }

                    val albumArtists = it.album.artists
                    if (albumArtists?.size == 1) {
                        albumArtists.firstOrNull()?.id?.let { artistId ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val artistEntity = database.getArtistById(artistId)
                                if (artistEntity != null && artistEntity.thumbnailUrl == null) {
                                    val preferredThumbnailUrl = ArtistImageResolver.resolveThumbnail(artistEntity)
                                    YouTube.artist(artistId).onSuccess { artistPage ->
                                        database.query {
                                            getArtistById(artistId)?.let { currentArtist ->
                                                update(currentArtist, artistPage, preferredThumbnailUrl)
                                            }
                                        }
                                    }.onFailure {
                                        if (preferredThumbnailUrl != null) {
                                            database.query {
                                                getArtistById(artistId)?.let { currentArtist ->
                                                    update(currentArtist.copy(thumbnailUrl = preferredThumbnailUrl))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (description.value == null && descriptionRuns.value == null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val artistName = album?.artists?.firstOrNull()?.name
                                ?: database.albumWithSongs(albumId).first()?.artists?.firstOrNull()?.name
                            val wikiDescription = Wikipedia.fetchAlbumInfo(it.album.title, artistName)
                            if (wikiDescription != null) {
                                description.value = wikiDescription
                                val currentAlbum = database.album(albumId).first()
                                if (currentAlbum != null) {
                                    database.query {
                                        update(currentAlbum.album.copy(description = wikiDescription))
                                    }
                                }
                            } else {
                                val appleDescription = AppleMusicAboutAlbum.fetchAlbumDescription(it.album.title, artistName)
                                if (appleDescription != null) {
                                    description.value = appleDescription
                                    val currentAlbum = database.album(albumId).first()
                                    if (currentAlbum != null) {
                                        database.query {
                                            update(currentAlbum.album.copy(description = appleDescription))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                    if (album == null || database.albumWithSongs(albumId).first()?.songs.isNullOrEmpty()) {
                        loadFailed.value = true
                    }
                    if (it.message?.contains("NOT_FOUND") == true) {
                        val albumToDelete = album?.album
                        if (albumToDelete != null) {
                            database.query {
                                delete(albumToDelete)
                            }
                        }
                    }
                }
        }
    }

    private var moreByArtistFor: String? = null

    private fun loadMoreByArtist(artistId: String) {
        if (moreByArtistFor == artistId) return
        moreByArtistFor = artistId
        viewModelScope.launch(Dispatchers.IO) {
            val page = ArtistPageCache.page(artistId)
                ?: YouTube.artist(artistId).getOrNull()?.also { ArtistPageCache.putPage(artistId, it) }
                ?: return@launch
            moreByArtist.value = page.sections
                .filter { it.items.firstOrNull() is AlbumItem }
                .flatMap { section -> section.items.filterIsInstance<AlbumItem>() }
                .filter { it.browseId != albumId }
                .distinctBy { it.browseId }
                .sortedByDescending { it.year ?: 0 }
                .take(16)
        }
    }
}
