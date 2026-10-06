package com.example.musicfy.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A short-lived processor used only while Crossmix is active. Its palette is intentionally larger
 * than any one transition: a profile picks the gentle combinations appropriate for outgoing and
 * incoming music, instead of stacking dramatic effects on every song.
 */
@UnstableApi
@Suppress("DEPRECATION")
class CrossmixTransitionAudioProcessor : AudioProcessor {
    enum class Role { OUTGOING, INCOMING }

    enum class Effect {
        PAN,
        STEREO_WIDTH,
        LOW_PASS,
        HIGH_PASS,
        ECHO,
        DELAY,
        TREMOLO,
        SATURATION,
        SOFT_CLIP,
        BIT_CRUSH,
        TRANSIENT_BOOST,
        LIMITER,
    }

    data class State(
        val role: Role,
        val progress: Float,
        val effects: Set<Effect>,
        val delayMs: Int = 250,
        val tremoloHz: Float = 4f,
        val rhythmicDepth: Float = 0f,
        val filterDepth: Float = 0.7f,
    )

    @Volatile
    var state: State? = null

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var outputBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var lowPassLeft = 0f
    private var lowPassRight = 0f
    private var previousInputLeft = 0f
    private var previousInputRight = 0f
    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayCursor = 0
    private var tremoloPhase = 0f

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT || channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // The ring buffer is sized for the largest musically-derived delay (600 ms).
        val delayFrames = (sampleRate * 0.60f).roundToInt().coerceAtLeast(1)
        delayLeft = FloatArray(delayFrames)
        delayRight = FloatArray(delayFrames)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) {
            outputBuffer = EMPTY_BUFFER
            return
        }
        val output = replaceOutputBuffer(size)
        val current = state
        if (current == null || sampleRate <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val progress = current.progress.coerceIn(0f, 1f)
        val effects = current.effects
        repeat(size / (channelCount * 2)) {
            var left = inputBuffer.getShort().toInt() / 32768f
            var right = if (channelCount == 2) inputBuffer.getShort().toInt() / 32768f else left

            if (Effect.LOW_PASS in effects) {
                // Gradually close the outgoing track's top end, making room for the next attack.
                val alpha = 0.86f - progress * (0.70f * current.filterDepth)
                lowPassLeft += alpha * (left - lowPassLeft)
                lowPassRight += alpha * (right - lowPassRight)
                left = lowPassLeft
                right = lowPassRight
            }
            if (Effect.HIGH_PASS in effects) {
                // Briefly keep the entering bass out of the outgoing track's way, then open it.
                lowPassLeft += 0.10f * (left - lowPassLeft)
                lowPassRight += 0.10f * (right - lowPassRight)
                val amount = (1f - progress) * 0.36f * current.filterDepth
                left -= lowPassLeft * amount
                right -= lowPassRight * amount
            }
            if (Effect.TRANSIENT_BOOST in effects) {
                val boost = 1f + (1f - progress) * (0.18f * current.rhythmicDepth)
                left += (left - previousInputLeft) * boost
                right += (right - previousInputRight) * boost
            }
            previousInputLeft = left
            previousInputRight = right

            val delayFrames = (sampleRate * current.delayMs / 1_000f).roundToInt()
                .coerceIn(1, delayLeft.size - 1)
            val readCursor = (delayCursor - delayFrames + delayLeft.size) % delayLeft.size
            val delayedLeft = delayLeft[readCursor]
            val delayedRight = delayRight[readCursor]
            if (Effect.ECHO in effects || Effect.DELAY in effects) {
                val wet = if (Effect.ECHO in effects) {
                    0.12f * progress * current.rhythmicDepth
                } else {
                    0.08f * (1f - progress) * current.rhythmicDepth
                }
                left += delayedLeft * wet
                right += delayedRight * wet
            }
            delayLeft[delayCursor] = left + delayedLeft * 0.24f
            delayRight[delayCursor] = right + delayedRight * 0.24f
            delayCursor = (delayCursor + 1) % delayLeft.size

            if (Effect.TREMOLO in effects) {
                val depth = 0.10f * progress * current.rhythmicDepth
                val gain = 1f - depth + depth * ((sin(tremoloPhase) + 1f) * 0.5f)
                left *= gain
                right *= gain
                tremoloPhase = (tremoloPhase + (2f * Math.PI.toFloat() * current.tremoloHz / sampleRate)) % (2f * Math.PI.toFloat())
            }
            if (Effect.STEREO_WIDTH in effects && channelCount == 2) {
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f * (1f + (1f - progress) * 0.28f)
                left = mid + side
                right = mid - side
            }
            if (Effect.PAN in effects && channelCount == 2) {
                val pan = when (current.role) {
                    Role.OUTGOING -> -0.22f * progress
                    Role.INCOMING -> 0.22f * (1f - progress)
                }
                left *= 1f - max(0f, pan)
                right *= 1f + minOf(0f, pan)
            }
            if (Effect.SATURATION in effects) {
                left = left / (1f + abs(left) * 0.13f)
                right = right / (1f + abs(right) * 0.13f)
            }
            if (Effect.BIT_CRUSH in effects && progress < 0.12f) {
                left = (left * 2_048).roundToInt() / 2_048f
                right = (right * 2_048).roundToInt() / 2_048f
            }
            if (Effect.SOFT_CLIP in effects || Effect.LIMITER in effects) {
                left = softClip(left)
                right = softClip(right)
            }
            output.putShort((left * 32767f).coerceIn(-32768f, 32767f).roundToInt().toShort())
            if (channelCount == 2) output.putShort((right * 32767f).coerceIn(-32768f, 32767f).roundToInt().toShort())
        }
        output.flip()
    }

    override fun queueEndOfStream() { inputEnded = true }
    override fun getOutput(): ByteBuffer = outputBuffer.also { outputBuffer = EMPTY_BUFFER }
    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        lowPassLeft = 0f
        lowPassRight = 0f
        previousInputLeft = 0f
        previousInputRight = 0f
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayCursor = 0
        tremoloPhase = 0f
    }

    @Deprecated("Deprecated in Java")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        state = null
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        else outputBuffer.clear()
        return outputBuffer
    }

    private fun softClip(value: Float): Float = value / (1f + abs(value) * 0.18f)

    private companion object {
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
