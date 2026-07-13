package com.example.musicfy.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
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

    // Haptic parameters
    private var smoothedAmplitude: Float = 0f
    
    private var lastVibrateTimeMs: Long = 0
    private val hapticUpdateIntervalMs: Long = 30 // Send vibration command every 30ms
    
    // Kick detection
    private var longTermRms: Double = 0.0
    private var lastKickTimeMs: Long = 0

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

    private fun processHaptics(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()

        var sumSquares = 0.0
        repeat(frameCount) { frameIndex ->
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                val sampleValue = inputBuffer.getShort(sampleIndex).toDouble()
                sumSquares += sampleValue * sampleValue
            }
        }
        
        val totalSamples = frameCount * channelCount
        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples) else 0.0

        // Sensitivity controls the full-scale reference point.
        // HIGH = low maxRms = quiet sounds map to higher intensity = you feel everything
        // LOW  = high maxRms = only very loud sounds register = gentle vibration
        val maxRms = when (sensitivity) {
            HapticSensitivity.HIGH -> 6000.0
            HapticSensitivity.MEDIUM -> 12000.0
            HapticSensitivity.LOW -> 22000.0
        }

        var intensity = (rms / maxRms).toFloat().coerceIn(0f, 1f)
        
        // Apply a gentle curve so quiet is truly quiet, loud is truly loud
        intensity = Math.pow(intensity.toDouble(), 1.5).toFloat()

        // Noise gate: silence the motor during truly silent passages
        if (intensity < 0.03f) {
            intensity = 0f
        }

        val targetAmplitude = intensity * 255f
        
        // Gentle symmetric smoothing for iOS-like buttery feel
        // Higher factor = smoother/slower response
        val attackSmoothing = 0.4f   // Smoothly ramp up (not instant)
        val decaySmoothing = 0.6f    // Smoothly ramp down (slower than attack)
        
        if (targetAmplitude > smoothedAmplitude) {
            smoothedAmplitude = (smoothedAmplitude * attackSmoothing) + (targetAmplitude * (1f - attackSmoothing))
        } else {
            smoothedAmplitude = (smoothedAmplitude * decaySmoothing) + (targetAmplitude * (1f - decaySmoothing))
        }
        
        val finalAmplitude = smoothedAmplitude.toInt().coerceIn(0, 255)

        // Bass Kick Detection
        longTermRms = (longTermRms * 0.95) + (rms * 0.05)
        val currentTime = System.currentTimeMillis()
        
        var isBassKick = false
        if (rms > (longTermRms * 1.8) && rms > 3000.0) {
            if (currentTime - lastKickTimeMs > 180) {
                isBassKick = true
                lastKickTimeMs = currentTime
            }
        }

        if (currentTime - lastVibrateTimeMs >= hapticUpdateIntervalMs) {
            lastVibrateTimeMs = currentTime
            
            if (finalAmplitude > 3 || isBassKick) {
                onHapticUpdate(finalAmplitude, isBassKick)
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
