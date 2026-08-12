// lyricsscreenviewmodelkt
// fetch-and-cache glue for the full lyrics page: reads
// first and only reaches for the network via lyricshelper if nothing's
// song — same fetch+upsert shape as lyricsmenuviewmodelrefetchlyrics /
// auto-fetch so results land in the same table either path writes

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
    private val database: MusicDatabase,
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
