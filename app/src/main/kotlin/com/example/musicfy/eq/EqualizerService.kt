// equalizerservicekt
// what is this for you ask its for equalizer service ofc

package com.example.musicfy.eq


import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.musicfy.eq.audio.CustomEqualizerAudioProcessor
import com.example.musicfy.eq.data.ParametricEQ
import com.example.musicfy.eq.data.SavedEQProfile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// service for managing custom eq using exoplayer s audioprocessor supports 10+
@Singleton
class EqualizerService @Inject constructor() {

    @SuppressLint("UnsafeOptInUsageError")
    private val audioProcessors = mutableListOf<CustomEqualizerAudioProcessor>()
    private var pendingProfile: SavedEQProfile? = null
    private var shouldDisable: Boolean = false

    companion object {
        private const val TAG = "EqualizerService"
    }

    // add an audio processor instance this should be called when exoplayer is
    @OptIn(UnstableApi::class)
    fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.add(processor)
        Timber.tag(TAG).d("Audio processor added. Total: ${audioProcessors.size}")

        // apply pending profile if one was set before processor was available
        if (shouldDisable) {
            processor.disable()
            // don t clear shoulddisable here as we might add more processors
        } else if (pendingProfile != null) {
            val profile = pendingProfile!!
            applyProfileToProcessor(processor, profile)
            // don t clear pendingprofile here
        }
    }

    // remove an audio processor instance
    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    // apply an eq profile if audio processor is not set stores as pending profile
    @OptIn(UnstableApi::class)
    fun applyProfile(profile: SavedEQProfile): Result<Unit> {
        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG)
                .w("No audio processors set yet. Storing profile as pending: ${profile.name}")
            pendingProfile = profile
            shouldDisable = false
            return Result.success(Unit)
        }

        pendingProfile = profile // keep it for future processors
        shouldDisable = false
        
        var success = true
        var lastError: Exception? = null

        audioProcessors.forEach { processor ->
            try {
                applyProfileToProcessor(processor, profile)
            } catch (e: Exception) {
                success = false
                lastError = e
            }
        }

        return if (success) Result.success(Unit) else Result.failure(lastError ?: Exception("Unknown error"))
    }

    private fun applyProfileToProcessor(processor: CustomEqualizerAudioProcessor, profile: SavedEQProfile) {
        val parametricEQ = ParametricEQ(
            preamp = profile.preamp,
            bands = profile.bands
        )
        processor.applyProfile(parametricEQ)
    }

    // disable the equalizer flat response if audio processor is not set stores
    @OptIn(UnstableApi::class)
    fun disable() {
        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG).w("No audio processors set yet. Storing disable as pending")
            shouldDisable = true
            pendingProfile = null
            return
        }

        shouldDisable = true // keep state
        pendingProfile = null

        audioProcessors.forEach { processor ->
            try {
                processor.disable()
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to disable equalizer: ${e.message}")
            }
        }
        Timber.tag(TAG).d("Equalizer disabled on all processors")
    }

    // check if audio processor is set
    fun isInitialized(): Boolean {
        return audioProcessors.isNotEmpty()
    }

    // check if equalizer is enabled
    @OptIn(UnstableApi::class)
    fun isEnabled(): Boolean {
        return audioProcessors.any { it.isEnabled() }
    }

    // get information about the current eq capabilities
    fun getEqualizerInfo(): EqualizerInfo {
        return EqualizerInfo(
            supportsUnlimitedBands = true,
            maxBands = Int.MAX_VALUE,
            description = "Custom ExoPlayer AudioProcessor with biquad filters"
        )
    }

    // release resources not needed for audioprocessor but kept for api compatibility
    fun release() {
        // audioprocessor is managed by exoplayer we just clear our reference
        audioProcessors.clear()
        Timber.tag(TAG).d("Audio processor references cleared")
    }
}

// information about equalizer capabilities
data class EqualizerInfo(
    val supportsUnlimitedBands: Boolean,
    val maxBands: Int,
    val description: String
)
