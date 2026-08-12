// equalizerviewmodelkt
// backs the new equalizerscreen the dsp itself
// and its manager equalizerservice already existed and were already wired
// exoplayer instances this is just the first ui ever built for it in

package com.example.musicfy.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.eq.EqualizerService
import com.example.musicfy.eq.data.EQProfileRepository
import com.example.musicfy.eq.data.ParametricEQBand
import com.example.musicfy.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

val EqualizerBandFrequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

private const val PROFILE_ID = "musicfy_custom_eq"

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerService: EqualizerService,
    private val profileRepository: EQProfileRepository,
) : ViewModel() {

    var enabled = mutableStateOf(false)
        private set

    val bandGains = mutableStateListOf(*Array(EqualizerBandFrequencies.size) { 0f })

    init {
        val active = profileRepository.getActiveProfile()
        if (active != null && active.id == PROFILE_ID) {
            enabled.value = true
            active.bands.forEachIndexed { index, band ->
                if (index < bandGains.size) bandGains[index] = band.gain.toFloat()
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        if (value) {
            applyCurrent()
        } else {
            equalizerService.disable()
            viewModelScope.launch { profileRepository.setActiveProfile(null) }
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in bandGains.indices) return
        bandGains[index] = gainDb
        if (enabled.value) applyCurrent()
    }

    fun reset() {
        for (i in bandGains.indices) bandGains[i] = 0f
        if (enabled.value) applyCurrent()
    }

    private fun applyCurrent() {
        val bands = EqualizerBandFrequencies.mapIndexed { index, freq ->
            ParametricEQBand(frequency = freq, gain = bandGains.getOrElse(index) { 0f }.toDouble())
        }
        val profile = SavedEQProfile(
            id = PROFILE_ID,
            name = "Custom",
            deviceModel = "Custom",
            bands = bands,
            preamp = 0.0,
            isCustom = true,
            isActive = true,
        )
        equalizerService.applyProfile(profile)
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
            profileRepository.setActiveProfile(profile.id)
        }
    }
}
