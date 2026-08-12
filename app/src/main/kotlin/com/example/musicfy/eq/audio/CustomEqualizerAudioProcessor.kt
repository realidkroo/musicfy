// customequalizeraudioprocessorkt
// the file functioned as custom equalizer audio processor

package com.example.musicfy.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.musicfy.eq.data.ParametricEQ
import com.example.musicfy.eq.data.ParametricEQBand
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

// custom audio processor for exoplayer that applies parametric eq using biquad
@UnstableApi
@SuppressWarnings("Deprecated")
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var equalizerEnabled = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var filters: List<BiquadFilter> = emptyList()
    private var preampGain: Double = 1.0  // linear preamp gain multiplier
    private var pendingProfile: ParametricEQ? = null

    companion object {
        private const val TAG = "CustomEqualizerAudioProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    // apply an eq profile
    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        if (sampleRate == 0) {
            // audio processor not configured yet store as pending
            Timber.tag(TAG)
                .d("Audio processor not configured yet. Storing profile as pending with ${parametricEQ.bands.size} bands")
            pendingProfile = parametricEQ
            return
        }

        // convert preamp from db to linear gain
        preampGain = 10.0.pow(parametricEQ.preamp / 20.0)

        createFilters(parametricEQ.bands)
        equalizerEnabled = true

        // reset filter states to ensure clean transition
        filters.forEach { it.reset() }

        Timber.tag(TAG)
            .d("Applied EQ profile with ${filters.size} bands and ${parametricEQ.preamp} dB preamp")
    }

    // disable the equalizer
    @Synchronized
    fun disable() {
        equalizerEnabled = false
        filters = emptyList()
        preampGain = 1.0
        pendingProfile = null
        Timber.tag(TAG).d("Equalizer disabled")
    }

    // check if equalizer is enabled
    fun isEnabled(): Boolean = equalizerEnabled

    // create biquad filters from parametriceq bands only creates filters for enabled
    private fun createFilters(bands: List<ParametricEQBand>) {
        if (sampleRate == 0) {
            Timber.tag(TAG).w("Cannot create filters: sample rate not set")
            return
        }

        // filter out disabled bands and frequencies above nyquist limit
        filters = bands
            .filter { it.enabled && it.frequency < sampleRate / 2.0 }
            .map { band ->
                BiquadFilter(
                    sampleRate = sampleRate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType
                )
            }

        Timber.tag(TAG)
            .d("Created ${filters.size} biquad filters from ${bands.size} bands (PK/LSC/HSC)")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG)
            .d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        // apply pending profile if one exists
        pendingProfile?.let { profile ->
            preampGain = 10.0.pow(profile.preamp / 20.0)
            createFilters(profile.bands)
            equalizerEnabled = true
            pendingProfile = null
            Timber.tag(TAG)
                .d("Applied pending profile with ${filters.size} bands and ${profile.preamp} dB preamp")
        }

        // only support 16 bit pcm stereo mono
        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            val exception = AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            throw exception // rethrow unsupported
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!equalizerEnabled || filters.isEmpty()) {
            // passthrough mode directly use input as output
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            // ensure output buffer is large enough
            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) {
            return
        }

        // ensure we have our own output buffer reuse if possible to avoid
        // note we must not use inputbuffer as outputbuffer if we modify it
        if (outputBuffer === EMPTY_BUFFER || outputBuffer === inputBuffer) {
            // need new buffer was empty or same as input
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else if (outputBuffer.capacity() < inputSize) {
            // need larger buffer
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            // reuse existing buffer most common path
            outputBuffer.clear()
        }

        // process audio samples
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                // ensure the output buffer is ready to receive data
                // we don t set limit here because putshort will advance position
                processAudioBuffer16Bit(inputBuffer, outputBuffer)
            }
            else -> {
                // unsupported format passthrough
                outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
        // inputbuffer position is already updated by processaudiobuffer16bit put
    }

    // process 16 bit pcm audio through all biquad filters
    private fun processAudioBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        // ensure we are reading from the current position
        // input is ready to be read from position to limit
        // output is ready to be written to from position

        val sampleCount = input.remaining() / 2 // 2 bytes per 16 bit sample

        repeat(sampleCount / channelCount) {
            when (channelCount) {
                1 -> {
                    // mono
                    val sample = input.getShort().toDouble() / 32768.0 // normalize to 1 1
                    var processed = sample

                    // apply all filters in series
                    for (filter in filters) {
                        processed = filter.processSample(processed)
                    }

                    // apply preamp gain
                    processed *= preampGain

                    // clamp and convert back to 16 bit
                    val outputSample = (processed * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    output.putShort(outputSample)
                }
                2 -> {
                    // stereo
                    val leftSample = input.getShort().toDouble() / 32768.0
                    val rightSample = input.getShort().toDouble() / 32768.0

                    var processedLeft = leftSample
                    var processedRight = rightSample

                    // apply all filters in series
                    for (filter in filters) {
                        val (left, right) = filter.processStereo(processedLeft, processedRight)
                        processedLeft = left
                        processedRight = right
                    }

                    // apply preamp gain
                    processedLeft *= preampGain
                    processedRight *= preampGain

                    // clamp and convert back to 16 bit
                    val outputLeft = (processedLeft * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    val outputRight = (processedRight * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

                    output.putShort(outputLeft)
                    output.putShort(outputRight)
                }
                else -> {
                    // should not happen as configure rejects > 2 channels
                    repeat(channelCount) {
                        output.putShort(input.getShort())
                    }
                }
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        // return output buffer ready for reading already flipped in queueinput
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer.remaining() == 0
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false

        // reset filter states
        filters.forEach { it.reset() }
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        filters.forEach { it.reset() }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
