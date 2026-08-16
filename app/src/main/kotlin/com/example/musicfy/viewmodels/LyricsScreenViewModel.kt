// LyricsScreenViewModel.kt

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsScreenViewModel
@Inject
constructor(
    private val lyricsHelper: LyricsHelper,
    // Exposed so the lyrics screen can hand it to LyricsTranslationHelper, which persists a
    // finished translation against the song row.
    val database: MusicDatabase,
) : ViewModel() {
    private val requestedIds = mutableSetOf<String>()

    fun ensureLyricsLoaded(mediaMetadata: MediaMetadata) {
        val id = mediaMetadata.id
        if (!requestedIds.add(id)) return

        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.lyrics(id).first()
            if (existing != null) return@launch

            val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
            database.query {
                upsert(
                    LyricsEntity(
                        id = id,
                        lyrics = lyricsWithProvider.lyrics,
                        provider = lyricsWithProvider.provider,
                    ),
                )
            }
        }
    }
}
