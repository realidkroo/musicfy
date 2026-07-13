package com.example.musicfy.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.musicfy.constants.HapticFocus
import com.example.musicfy.constants.HapticSensitivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AudioProcessor that detects strong beats (like bass hits) and triggers haptic feedback callbacks.
 */
@UnstableApi
@Suppress("DEPRECATION")
class HapticAudioProcessor(
    private val onHapticUpdate: (Int, Boolean) -> Unit
) : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var sensitivity: HapticSensitivity = HapticSensitivity.MEDIUM

    @Volatile
    var focus: HapticFocus = HapticFocus.VIBE

    // Haptic parameters
    private var smoothedAmplitude: Float = 0f
    
    private var lastVibrateTimeMs: Long = 0
    private val hapticUpdateIntervalMs: Long = 30 // Send vibration command every 30ms
    
    // Kick detection & Filtering
    private var longTermRms: Double = 0.0
    private var shortTermRms: Double = 0.0
    private var lastKickTimeMs: Long = 0

    // Filter states
    private var filterState1: Double = 0.0
    private var filterState2: Double = 0.0

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        if (enabled && sampleRate > 0 && channelCount > 0) {
            processHaptics(inputBuffer)
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private var filterState3: Double = 0.0
    private var filterState4: Double = 0.0

    private fun processHaptics(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()

        var sumSquares = 0.0
        repeat(frameCount) { frameIndex ->
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                val sampleValue = inputBuffer.getShort(sampleIndex).toDouble()
                
                val filteredValue = when (focus) {
                    HapticFocus.BALANCE -> sampleValue
                    HapticFocus.BASS -> {
                        // Low pass filter ~ 250Hz (alpha ~ 0.034 at 44.1kHz)
                        filterState1 = filterState1 + 0.034 * (sampleValue - filterState1)
                        filterState1
                    }
                    HapticFocus.VOCAL -> {
                        // Band pass: HP at 300Hz (alpha ~ 0.959), LP at 3000Hz (alpha ~ 0.298)
                        val hp = 0.959 * (filterState1 + sampleValue - filterState2)
                        filterState2 = sampleValue
                        filterState1 = hp
                        // LP
                        val lp = filterState3 + 0.298 * (hp - filterState3)
                        filterState3 = lp
                        lp
                    }
                    HapticFocus.VIBE -> {
                        // Low pass ~ 1000Hz (alpha ~ 0.12)
                        filterState1 = filterState1 + 0.12 * (sampleValue - filterState1)
                        filterState1
                    }
                }
                
                sumSquares += filteredValue * filteredValue
            }
        }
        
        val totalSamples = frameCount * channelCount
        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples) else 0.0

        val maxRms = when (sensitivity) {
            HapticSensitivity.HIGH -> 6000.0
            HapticSensitivity.MEDIUM -> 12000.0
            HapticSensitivity.LOW -> 22000.0
        }

        var intensity = (rms / maxRms).toFloat().coerceIn(0f, 1f)
        intensity = Math.pow(intensity.toDouble(), 1.5).toFloat()

        if (intensity < 0.03f) {
            intensity = 0f
        }

        val targetAmplitude = intensity * 255f
        
        val attackSmoothing = 0.4f
        val decaySmoothing = 0.6f
        
        if (targetAmplitude > smoothedAmplitude) {
            smoothedAmplitude = (smoothedAmplitude * attackSmoothing) + (targetAmplitude * (1f - attackSmoothing))
        } else {
            smoothedAmplitude = (smoothedAmplitude * decaySmoothing) + (targetAmplitude * (1f - decaySmoothing))
        }
        
        val finalAmplitude = smoothedAmplitude.toInt().coerceIn(0, 255)

        // Highlight (Transient) Detection
        shortTermRms = (shortTermRms * 0.6) + (rms * 0.4)
        longTermRms = (longTermRms * 0.98) + (rms * 0.02)
        val currentTime = System.currentTimeMillis()
        
        var isHighlight = false
        // Vibrate mainly on transients (highlights)
        if (shortTermRms > (longTermRms * 1.6) && shortTermRms > 1500.0) {
            if (currentTime - lastKickTimeMs > 150) {
                isHighlight = true
                lastKickTimeMs = currentTime
            }
        }

        if (currentTime - lastVibrateTimeMs >= hapticUpdateIntervalMs) {
            lastVibrateTimeMs = currentTime
            
            if (isHighlight) {
                // Strong pulse on highlights
                onHapticUpdate(finalAmplitude.coerceAtLeast(150), true)
            } else if (finalAmplitude > 40) {
                // Very subtle background vibration instead of continuous buzzing
                onHapticUpdate(finalAmplitude / 4, false)
            }
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        smoothedAmplitude = 0f
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
