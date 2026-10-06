// LyricsMenuViewModel.kt

package com.example.musicfy.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.db.entities.Song
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.lyrics.LyricsResult
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.utils.NetworkConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    init {
        viewModelScope.launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }

        _isNetworkAvailable.value = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
    }

    fun setCurrentSong(song: Song) {
        _currentSong.value = song
    }

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                lyricsHelper.getAllLyrics(mediaId, title, artist, duration, album) { result ->
                    results.update {
                        it + result
                    }
                }
                isLoading.value = false
            }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        database.query {
            lyricsEntity?.let(::delete)
            val lyricsWithProvider =
                runBlocking {
                    // forceRefresh, or the helper's in-memory cache hands back the exact result
                    // this just deleted and the refetch becomes a no-op.
                    lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                }
            upsert(LyricsEntity(mediaMetadata.id, lyricsWithProvider.lyrics, lyricsWithProvider.provider))
        }
    }

    /**
     * Stores hand-edited lyrics for a song.
     *
     * Saved under the provider name [USER_EDITED] so the edit is recognisable later and so a
     * refetch does not silently overwrite something the user typed without them asking for it.
     */
    fun saveLyrics(songId: String, lyrics: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                upsert(LyricsEntity(id = songId, lyrics = lyrics, provider = USER_EDITED))
            }
        }
    }

    companion object {
        const val USER_EDITED = "UserEdited"
    }
}
