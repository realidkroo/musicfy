// setupimportviewmodelkt
// thin hilt bridge so the setup wizard s compose tree can reach the singleton
// musicimportservice same shape as equalizerviewmodel wrapping
// itself keeps running in the service s own scope even if this viewmodel the

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import com.example.musicfy.importer.ImportProgress
import com.example.musicfy.importer.MusicImportService
import com.example.musicfy.importer.ParsedImport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SetupImportViewModel @Inject constructor(
    private val musicImportService: MusicImportService,
) : ViewModel() {
    val progress: StateFlow<ImportProgress> = musicImportService.progress

    fun startImport(parsed: ParsedImport) {
        musicImportService.startImport(parsed)
    }
}
