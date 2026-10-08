// LibraryImportedViewModel.kt

package com.example.musicfy.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.Song
import com.example.musicfy.importer.ImportSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs "Imported from ...": the songs one service brought in, whatever playlist they went into. */
@HiltViewModel
class LibraryImportedViewModel
@Inject
constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** Null for a route with a source this build doesn't know - the screen then has nothing to show. */
    val source: ImportSource? = ImportSource.fromKey(savedStateHandle.get<String>("source"))

    val songs: StateFlow<List<Song>> = (source?.let { database.importedSongsByNameAsc(it.key) } ?: flowOf(emptyList<Song>()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
