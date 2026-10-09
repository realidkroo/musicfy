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
import com.example.musicfy.lyrics.LyricsProviderRegistry
import com.example.musicfy.lyrics.LyricsResult
import com.example.musicfy.lyrics.LyricsWithProvider
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
        @Suppress("UNUSED_PARAMETER") lyricsEntity: LyricsEntity?,
    ) {
        // Off the main thread. This used to runBlocking a full provider lookup inside a database
        // transaction, and kept the old row deleted - no lyrics on screen - until it finished.
        viewModelScope.launch(Dispatchers.IO) {
            // forceRefresh, or the helper's in-memory cache hands back the exact result being
            // replaced and the refetch becomes a no-op.
            val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
            database.query {
                upsert(LyricsEntity(mediaMetadata.id, lyricsWithProvider.lyrics, lyricsWithProvider.provider))
            }
        }
    }

    /** What one lyrics source has for the current song, as shown in the source picker. */
    sealed interface SourceState {
        data object Checking : SourceState
        data object Off : SourceState
        data object Unavailable : SourceState
        data class Available(val candidate: LyricsHelper.ProviderCandidate) : SourceState
    }

    private val _sources = MutableStateFlow<Map<String, SourceState>>(emptyMap())
    val sources: StateFlow<Map<String, SourceState>> = _sources.asStateFlow()
    private var sourcesJob: Job? = null
    private var sourcesFor: String? = null

    /**
     * Asks every source for this song at once and reports each as it answers, so the picker can
     * say which ones have lyrics before the user taps one. Results are kept for the song, so a
     * pick applies the lyrics already fetched instead of searching again.
     */
    fun checkSources(mediaMetadata: MediaMetadata) {
        if (sourcesFor == mediaMetadata.id && _sources.value.isNotEmpty()) return
        sourcesJob?.cancel()
        sourcesFor = mediaMetadata.id
        _sources.value = LyricsProviderRegistry.providerNames.associateWith { SourceState.Checking }
        sourcesJob = viewModelScope.launch(Dispatchers.IO) {
            val providers = lyricsHelper.orderedProviders()
            val listed = providers.map { it.name }.toSet()
            // Sources missing from a saved order (added after it was saved) are still checked.
            val all = providers + LyricsProviderRegistry.providerNames
                .filter { it !in listed }
                .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
            all.forEachIndexed { order, provider ->
                launch {
                    val state = if (!lyricsHelper.isProviderEnabled(provider)) {
                        SourceState.Off
                    } else {
                        lyricsHelper.fetchCandidate(mediaMetadata, provider, order)
                            ?.let { SourceState.Available(it) }
                            ?: SourceState.Unavailable
                    }
                    _sources.update { it + (provider.name to state) }
                }
            }
        }
    }

    /** Applies a source's already-fetched lyrics to the song straight away. */
    fun useSource(mediaMetadata: MediaMetadata, candidate: LyricsHelper.ProviderCandidate) {
        val choice = LyricsWithProvider(candidate.lyrics, candidate.providerName)
        lyricsHelper.rememberChoice(mediaMetadata.id, choice)
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                upsert(LyricsEntity(mediaMetadata.id, choice.lyrics, choice.provider))
            }
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
