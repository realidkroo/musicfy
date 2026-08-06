// SetupImportViewModel.kt
// Thin Hilt bridge so the setup wizard's Compose tree can reach the singleton
// MusicImportService (same shape as EqualizerViewModel wrapping EqualizerService) — the import
// itself keeps running in the service's own scope even if this ViewModel/the wizard is torn down.

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
