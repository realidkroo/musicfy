package com.example.musicfy.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.musicfy.crossmix.CrossmixRole
import com.example.musicfy.crossmix.CrossmixStyle
import com.example.musicfy.crossmix.FxParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The Crossmix renderer: one per player, idle (a straight copy) except while a transition runs.
 *
 * Every effect is placed by the track's own clock. The sink in front of the processing chain hands
 * over each buffer's timestamp ([onInputTimestamp]), so every sample knows exactly where it sits in
 * its song, and the style is evaluated from the beat position alone. That keeps an echo throw,
 * a spin-back or a cut on the beat no matter how late the main thread runs, and keeps both sides of
 * a transition on one timeline. Parameters move once per 32-sample block and glide sample by sample
 * between blocks, so nothing ever steps or clicks.
 *
 * The output is always exactly as many frames as went in: spin-backs, tape stops and loop rolls
 * read from a recording of what just played instead of changing the length, and the beat-lock
 * delay only shifts content in time. The player's sense of position stays true.
 */
@UnstableApi
@Suppress("DEPRECATION")
class CrossmixTransitionAudioProcessor : AudioProcessor {

    /** a transition, as one side of it sees it: [anchorMs] is transition beat 0 in this track */
    data class Script(
        val role: CrossmixRole,
        val style: CrossmixStyle,
        val variation: Int,
        val lengthBeats: Float,
        val anchorMs: Double,
        val mediaPerBeatMs: Double,
        /** the track's own beat length, for rhythmic effects (echo time, gates) */
        val musicalBeatMs: Double,
        /** beat after which this side is done: the outgoing side silent, the incoming dry */
        val endBeat: Float,
        /**
         * where the track should be when its first buffer arrives, and when that was known
         * ([calibrationNanos] 0: a paused player, which hasn't moved since). checked once against
         * the sink's clock, so a stream offset that never arrived can't put the effects minutes out
         */
        val calibrationMs: Double,
        val calibrationNanos: Long,
    )

    @Volatile private var script: Script? = null
    @Volatile private var pendingTimestampUs = Long.MIN_VALUE
    @Volatile private var streamOffsetUs = 0L
    @Volatile private var targetDelayMs = 0.0
    @Volatile private var finishing = false
    @Volatile private var fadingOut = false
    // the outgoing side, once its part is over, stays silent for good: whatever its clock does next
    @Volatile private var silenced = false

    /** what this side is doing right now, for the service's beat lock and its clean-up */
    @Volatile var currentBeat = Float.NaN
        private set
    @Volatile var currentDelayMs = 0.0
        private set
    @Volatile var idle = true
        private set

    private var sampleRate = 0
    private var channels = 0
    private var outputBuffer = EmptyBuffer
    private var inputEnded = false

    // the track's clock, in frames since its start
    private var mediaFrame = 0.0
    private var clockValid = false
    private var scriptHeard = false
    private var calibrated = false
    private var clockCorrectionMs = 0.0

    private val params = FxParams()
    private val dsp = Dsp()

    /** start a transition on this side. allocation happens here, off the audio thread */
    fun arm(script: Script) {
        if (sampleRate > 0) dsp.ensure(sampleRate)
        finishing = false
        fadingOut = false
        silenced = false
        scriptHeard = false
        calibrated = false
        clockCorrectionMs = 0.0
        targetDelayMs = 0.0
        this.script = script
        idle = false
    }

    /** where the beat lock wants this side, in ms of delay */
    fun setBeatLockDelay(delayMs: Double) {
        targetDelayMs = delayMs.coerceIn(0.0, MaxDelayMs - 10.0)
    }

    /**
     * the incoming side stepping out of the transition now: its effects glide away over ~50 ms,
     * then its beat-lock delay eases back to nothing before the renderer goes idle, so the track
     * never jumps in time
     */
    fun finish() {
        finishing = true
        targetDelayMs = 0.0
    }

    /** the outgoing side going away early (a skip or a seek): fade it to silence quickly */
    fun fadeOutNow() { fadingOut = true }

    fun clear() {
        script = null
        finishing = false
        fadingOut = false
        silenced = false
        targetDelayMs = 0.0
        currentBeat = Float.NaN
    }

    /** from the sink: the timestamp of the buffer about to be processed */
    fun onInputTimestamp(presentationTimeUs: Long) { pendingTimestampUs = presentationTimeUs }

    fun onStreamOffset(offsetUs: Long) { streamOffsetUs = offsetUs }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // anything but 16-bit mono or stereo (a 5.1 file, say) passes straight by: the processor
        // steps out of the chain, and Crossmix sees it can't render this track and doesn't try
        supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..2
        if (!supported) {
            sampleRate = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        if (script != null) dsp.ensure(sampleRate)
        return inputAudioFormat
    }

    @Volatile private var supported = true

    /** whether this side can play a transition: its track is a format the renderer handles */
    val canRender: Boolean get() = supported && sampleRate > 0

    override fun isActive(): Boolean = supported

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val frames = size / (channels * 2)

        val timestamp = pendingTimestampUs
        if (timestamp != Long.MIN_VALUE) {
            pendingTimestampUs = Long.MIN_VALUE
            mediaFrame = (timestamp - streamOffsetUs) * sampleRate / 1_000_000.0
            clockValid = mediaFrame > -sampleRate
        }

        val active = script
        if (active == null || sampleRate <= 0 || !dsp.ready(sampleRate)) {
            output.put(inputBuffer)
            output.flip()
            mediaFrame += frames
            if (active == null) idle = true
            return
        }

        if (silenced) {
            for (i in 0 until frames * channels) output.putShort(0)
            inputBuffer.position(inputBuffer.limit())
            output.flip()
            mediaFrame += frames
            return
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        dsp.render(inputBuffer, output, frames, channels, active)
        output.flip()
        if (active.role == CrossmixRole.Outgoing && !currentBeat.isNaN() && currentBeat > active.endBeat) silenced = true
    }

    override fun queueEndOfStream() { inputEnded = true }
    override fun getOutput(): ByteBuffer = outputBuffer.also { outputBuffer = EmptyBuffer }
    override fun isEnded(): Boolean = inputEnded && outputBuffer === EmptyBuffer

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EmptyBuffer
        inputEnded = false
        clockValid = false
        dsp.clearState()
        // a discontinuity (a seek) after the transition has been heard ends it for this side: the
        // track carries on untouched rather than replaying the transition from wherever it landed
        if (scriptHeard) {
            if (script?.role == CrossmixRole.Incoming) clear() else fadingOut = true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun reset() {
        flush()
        clear()
        sampleRate = 0
        channels = 0
        dsp.release()
        idle = true
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        else outputBuffer.clear()
        return outputBuffer
    }

    /** all the DSP state for one side. sized for the sample rate when a transition is armed */
    private inner class Dsp {
        private var rate = 0
        private var historyL = FloatArray(0)
        private var historyR = FloatArray(0)
        private var historyMask = 0
        private var writeHead = 0L

        private var echoL = FloatArray(0)
        private var echoR = FloatArray(0)
        private var echoCursor = 0
        private var echoToneL = 0f
        private var echoToneR = 0f
        private var echoLowL = 0f
        private var echoLowR = 0f

        private val reverb = Reverb()
        private val highPassL = Svf(); private val highPassR = Svf()
        private val lowPassL = Svf(); private val lowPassR = Svf()
        private val bandLowL = Svf(); private val bandLowR = Svf()
        private val bandHighL = Svf(); private val bandHighR = Svf()
        private val noiseFilter = Svf()

        private var flangerL = FloatArray(0)
        private var flangerR = FloatArray(0)
        private var flangerCursor = 0
        private var flangerPhase = 0.0

        private var crushHoldL = 0f
        private var crushHoldR = 0f
        private var crushCounter = 0f
        private var noiseSeed = 0x2545F491
        private var gateLevel = 1f

        // the deck: where in the recording we're reading
        private var deckMode = FxParams.Deck.Live
        private var readHead = 0.0
        private var deckSpeed = 1.0
        private var loopStart = 0.0
        private var loopFrames = 0.0
        private var loopMarkBeat = Float.NaN
        private var deckFade = 1f

        private var delayFrames = 0.0
        private var finishMix = 0f
        private var outFade = 1f

        // smoothed parameters (current value glides toward the block target)
        private var gain = 0f
        private var lowBand = 1f; private var midBand = 1f; private var highBand = 1f
        private var echoSend = 0f; private var reverbSend = 0f
        private var flangerMix = 0f
        private var crush = 0f
        private var panAngle = 0f
        private var width = 1f
        private var noiseLevel = 0f
        private var started = false

        fun ready(sampleRate: Int) = rate == sampleRate && historyL.isNotEmpty()

        fun ensure(sampleRate: Int) {
            if (rate == sampleRate && historyL.isNotEmpty()) return
            rate = sampleRate
            val historyFrames = Integer.highestOneBit(sampleRate * HistorySeconds) * 2
            historyL = FloatArray(historyFrames); historyR = FloatArray(historyFrames)
            historyMask = historyFrames - 1
            val echoFrames = (sampleRate * MaxEchoSeconds).toInt()
            echoL = FloatArray(echoFrames); echoR = FloatArray(echoFrames)
            val flangerFrames = (sampleRate * 0.02f).toInt() + 4
            flangerL = FloatArray(flangerFrames); flangerR = FloatArray(flangerFrames)
            reverb.setup(sampleRate)
            clearState()
        }

        fun release() {
            rate = 0
            historyL = FloatArray(0); historyR = FloatArray(0)
            echoL = FloatArray(0); echoR = FloatArray(0)
            flangerL = FloatArray(0); flangerR = FloatArray(0)
            reverb.release()
        }

        fun clearState() {
            historyL.fill(0f); historyR.fill(0f)
            writeHead = 0L
            echoL.fill(0f); echoR.fill(0f)
            echoCursor = 0
            echoToneL = 0f; echoToneR = 0f; echoLowL = 0f; echoLowR = 0f
            reverb.clear()
            for (f in arrayOf(highPassL, highPassR, lowPassL, lowPassR, bandLowL, bandLowR, bandHighL, bandHighR, noiseFilter)) f.clear()
            flangerL.fill(0f); flangerR.fill(0f)
            flangerCursor = 0
            deckMode = FxParams.Deck.Live
            loopMarkBeat = Float.NaN
            deckFade = 1f
            delayFrames = 0.0
            finishMix = 0f
            outFade = 1f
            started = false
            gateLevel = 1f
        }

        fun render(input: ByteBuffer, output: ByteBuffer, frames: Int, channelCount: Int, s: Script) {
            val sr = rate.toDouble()
            var processed = 0
            while (processed < frames) {
                val block = min(BlockFrames, frames - processed)
                if (clockValid && !calibrated) calibrate(s, sr)
                val beat = if (clockValid) {
                    ((mediaFrame * 1_000.0 / sr + clockCorrectionMs - s.anchorMs) / s.mediaPerBeatMs).toFloat()
                } else {
                    Float.NaN
                }
                // an unknown clock (never seen in practice) leaves the incoming side audible, never silent
                if (beat.isNaN()) {
                    params.reset()
                    if (s.role == CrossmixRole.Outgoing) params.gain = 1f
                } else {
                    s.style.evaluate(s.role, beat, s.lengthBeats, s.variation, params)
                    currentBeat = beat
                    if (beat >= -0.5f) scriptHeard = true
                }
                if (!started) {
                    // first block: start the glides where the style is, so nothing sweeps in from defaults
                    gain = params.gain; echoSend = params.echoSend; reverbSend = params.reverbSend
                    lowBand = params.lowBand; midBand = params.midBand; highBand = params.highBand
                    flangerMix = params.flangerMix; crush = params.crush; width = params.width
                    noiseLevel = params.noiseLevel
                    started = true
                }
                renderBlock(input, output, block, channelCount, s, beat)
                processed += block
                mediaFrame += block
            }
            val delayMs = delayFrames * 1_000.0 / sr
            currentDelayMs = delayMs
            // done only once nothing is left ringing either: dropping out of the renderer cuts any tail
            val done = !currentBeat.isNaN() && currentBeat > s.endBeat && abs(delayMs) < 0.05 && delayTargetIsZero() && tailsQuiet()
            val settledDelay = abs(delayMs) < 0.05
            if (s.role == CrossmixRole.Incoming && ((finishing && finishMix >= 1f && settledDelay) || done)) {
                this@CrossmixTransitionAudioProcessor.script = null
                idle = true
            }
        }

        private fun delayTargetIsZero() = targetDelayMs <= 0.05

        private fun tailsQuiet() = !reverb.ringing && abs(echoToneL) < 1e-6f && abs(echoToneR) < 1e-6f

        /**
         * once per transition: the sink's clock should put this buffer within a few seconds of
         * where the player is. if it doesn't (a stream offset that never arrived), trust the player
         * instead, plus the usual few hundred ms the renderer runs ahead of what's heard.
         */
        private fun calibrate(s: Script, sr: Double) {
            calibrated = true
            val elapsed = if (s.calibrationNanos > 0L) (System.nanoTime() - s.calibrationNanos) / 1_000_000.0 else 0.0
            val expected = s.calibrationMs + elapsed
            val clockMs = mediaFrame * 1_000.0 / sr
            clockCorrectionMs = if (abs(clockMs - expected) < 5_000.0) 0.0 else expected + 250.0 - clockMs
        }

        private fun renderBlock(input: ByteBuffer, output: ByteBuffer, block: Int, channelCount: Int, s: Script, beat: Float) {
            val sr = rate.toFloat()
            val inv = 1f / block
            val musicalBeatFrames = s.musicalBeatMs * rate / 1_000.0

            // block targets
            val gainT = params.gain
            val lowT = params.lowBand; val midT = params.midBand; val highT = params.highBand
            val echoT = params.echoSend; val reverbT = params.reverbSend
            val flangerT = params.flangerMix; val crushT = params.crush; val widthT = params.width
            val noiseT = params.noiseLevel
            val orbitAngle = if (params.orbit > 0f && !beat.isNaN()) {
                (params.orbit * sin(2.0 * PI * beat / params.orbitBeats)).toFloat()
            } else 0f
            val panT = (params.pan + orbitAngle).coerceIn(-1f, 1f)

            // a sweep resting at its 20 Hz end is "off": nothing is engaged until it really moves
            val hpOn = params.lowCutHz > 21f
            val lpOn = params.highCutHz < 19_000f
            if (hpOn) { highPassL.set(params.lowCutHz, params.resonance, sr); highPassR.copyCoefficients(highPassL) }
            if (lpOn) { lowPassL.set(params.highCutHz, params.resonance, sr); lowPassR.copyCoefficients(lowPassL) }
            val isolate = lowT != 1f || midT != 1f || highT != 1f || lowBand != 1f || midBand != 1f || highBand != 1f
            if (isolate) {
                bandLowL.set(220f, 0.6f, sr); bandLowR.copyCoefficients(bandLowL)
                bandHighL.set(2_600f, 0.6f, sr); bandHighR.copyCoefficients(bandHighL)
            }
            val noiseOn = noiseT > 0f || noiseLevel > 0.0001f
            if (noiseOn) noiseFilter.set(params.noiseHz, 1.6f, sr)

            val echoFrames = (params.echoBeats * musicalBeatFrames).coerceIn(32.0, echoL.size - 2.0).toInt()
            val feedback = params.echoFeedback.coerceIn(0f, 0.92f)
            val toneCoefficient = 0.08f + 0.85f * params.echoTone
            reverb.configure(params.reverbSize, params.reverbFreeze)

            // gate: on for the first part of each step, with 2 ms edges
            val gateOn = params.gateDepth > 0.001f && !beat.isNaN()
            val gateEdge = 1f / (0.002f * sr)
            val stepsPerFrame = params.gateDivision / musicalBeatFrames.toFloat()
            var gatePhase = if (gateOn) frac((beat * s.mediaPerBeatMs / s.musicalBeatMs).toFloat() * params.gateDivision) else 0f

            // the deck
            updateDeck(params, beat, s)
            val maxDelayGlide = if (gain < 0.03f) 0.04 else 0.0035  // frames of delay change per frame
            val targetDelayFrames = targetDelayMs * rate / 1_000.0

            val crushStep = 1f + crushT * 15f
            val crushLevels = 65_536f / (1 shl (crushT * 11f).toInt().coerceIn(0, 11))

            for (i in 0 until block) {
                var l = input.getShort() / 32_768f
                var r = if (channelCount == 2) input.getShort() / 32_768f else l
                val dryL = l; val dryR = r

                // record what just arrived, then read the deck
                val w = (writeHead and historyMask.toLong()).toInt()
                historyL[w] = l; historyR[w] = r
                writeHead++

                // beat-lock delay glides toward its target, slowly enough never to be heard as pitch
                val delayError = targetDelayFrames - delayFrames
                delayFrames += delayError.coerceIn(-maxDelayGlide, maxDelayGlide)

                when (deckMode) {
                    FxParams.Deck.Live -> {
                        if (delayFrames > 0.01) {
                            val pos = writeHead - 1 - delayFrames
                            l = readHistory(historyL, pos); r = readHistory(historyR, pos)
                        }
                    }
                    FxParams.Deck.Loop -> {
                        val offset = ((readHead - loopStart) % loopFrames).let { if (it < 0) it + loopFrames else it }
                        val pos = loopStart + offset
                        l = readHistory(historyL, pos); r = readHistory(historyR, pos)
                        // a 3 ms crossfade over the loop seam
                        val seam = (0.003 * rate)
                        if (offset > loopFrames - seam) {
                            val t = ((offset - (loopFrames - seam)) / seam).toFloat()
                            val head = loopStart + (offset - (loopFrames - seam))
                            l = l * (1f - t) + readHistory(historyL, head) * t
                            r = r * (1f - t) + readHistory(historyR, head) * t
                        }
                        readHead += 1.0
                    }
                    FxParams.Deck.SpinBack, FxParams.Deck.TapeStop -> {
                        l = readHistory(historyL, readHead); r = readHistory(historyR, readHead)
                        readHead += deckSpeed
                        // never read past what has been recorded, nor further back than kept
                        readHead = readHead.coerceIn((writeHead - historyL.size + 8).toDouble(), (writeHead - 1).toDouble())
                    }
                }
                if (deckFade < 1f) {
                    // switching decks crossfades from the live signal over ~4 ms
                    l = dryL + (l - dryL) * deckFade; r = dryR + (r - dryR) * deckFade
                    deckFade = min(1f, deckFade + 1f / (0.004f * sr))
                }
                // the track as it is before any tone shaping: what finishing glides back to
                val plainL = l; val plainR = r

                gain += (gainT - gain) * inv * 4f
                lowBand += (lowT - lowBand) * inv * 4f; midBand += (midT - midBand) * inv * 4f; highBand += (highT - highBand) * inv * 4f
                echoSend += (echoT - echoSend) * inv * 4f; reverbSend += (reverbT - reverbSend) * inv * 4f
                flangerMix += (flangerT - flangerMix) * inv * 4f; crush += (crushT - crush) * inv * 4f
                width += (widthT - width) * inv * 4f; noiseLevel += (noiseT - noiseLevel) * inv * 4f
                panAngle += (panT - panAngle) * inv * 4f

                // three-band isolator: low, high, and what's between
                if (isolate) {
                    val lowL = bandLowL.lowPass(l); val lowR = bandLowR.lowPass(r)
                    val highL = bandHighL.highPass(l); val highR = bandHighR.highPass(r)
                    l = lowL * lowBand + (l - lowL - highL) * midBand + highL * highBand
                    r = lowR * lowBand + (r - lowR - highR) * midBand + highR * highBand
                }
                if (hpOn) { l = highPassL.highPass(l); r = highPassR.highPass(r) }
                if (lpOn) { l = lowPassL.lowPass(l); r = lowPassR.lowPass(r) }

                if (flangerMix > 0.001f) {
                    flangerPhase += params.flangerRateHz / sr
                    if (flangerPhase >= 1.0) flangerPhase -= 1.0
                    val delay = (0.0006 + 0.0042 * (0.5 + 0.5 * sin(2 * PI * flangerPhase))) * rate
                    val readPos = flangerCursor - delay
                    val fl = readRing(flangerL, readPos); val fr = readRing(flangerR, readPos + 0.0007 * rate)
                    flangerL[flangerCursor] = l + fl * 0.55f; flangerR[flangerCursor] = r + fr * 0.55f
                    flangerCursor = (flangerCursor + 1) % flangerL.size
                    l += (fl - l) * flangerMix * 0.5f; r += (fr - r) * flangerMix * 0.5f
                }

                if (crush > 0.001f) {
                    crushCounter += 1f
                    if (crushCounter >= crushStep) {
                        crushCounter -= crushStep
                        crushHoldL = (l * crushLevels).roundToInt() / crushLevels
                        crushHoldR = (r * crushLevels).roundToInt() / crushLevels
                    }
                    l += (crushHoldL - l) * crush; r += (crushHoldR - r) * crush
                }

                // the gate chops the track itself, never its echoes
                var gate = 1f
                if (gateOn) {
                    gatePhase += stepsPerFrame
                    if (gatePhase >= 1f) gatePhase -= floor(gatePhase)
                    val open = gatePhase < params.gateDuty
                    val target = if (open) 1f else 1f - params.gateDepth
                    gateLevel += (target - gateLevel).coerceIn(-gateEdge, gateEdge)
                    gate = gateLevel
                }

                val sendL = l; val sendR = r
                var mixL = l * gain * gate
                var mixR = r * gain * gate

                // beat-synced echo: dark or bright repeats, optionally bouncing side to side
                if (echoSend > 0.0005f || echoL.isNotEmpty() && abs(echoToneL) > 1e-6f) {
                    val read = (echoCursor - echoFrames + echoL.size) % echoL.size
                    val el = echoL[read]; val er = echoR[read]
                    echoToneL += toneCoefficient * (el - echoToneL); echoToneR += toneCoefficient * (er - echoToneR)
                    echoLowL += 0.004f * (echoToneL - echoLowL); echoLowR += 0.004f * (echoToneR - echoLowR)
                    val wetL = echoToneL - echoLowL; val wetR = echoToneR - echoLowR
                    if (params.echoPingPong) {
                        echoL[echoCursor] = sendR * echoSend + wetR * feedback + Denormal
                        echoR[echoCursor] = sendL * echoSend + wetL * feedback + Denormal
                    } else {
                        echoL[echoCursor] = sendL * echoSend + wetL * feedback + Denormal
                        echoR[echoCursor] = sendR * echoSend + wetR * feedback + Denormal
                    }
                    echoCursor = (echoCursor + 1) % echoL.size
                    mixL += wetL; mixR += wetR
                }

                if (reverbSend > 0.0005f || reverb.ringing) {
                    reverb.process(sendL * reverbSend, sendR * reverbSend)
                    mixL += reverb.outL; mixR += reverb.outR
                }

                if (noiseLevel > 0.0001f) {
                    noiseSeed = noiseSeed * 1_664_525 + 1_013_904_223
                    val white = (noiseSeed ushr 9) * (1f / 4_194_304f) - 1f
                    val band = noiseFilter.bandPass(white) * noiseLevel
                    mixL += band; mixR += band
                }

                // width, then an equal-power pan
                if (width != 1f) {
                    val mid = (mixL + mixR) * 0.5f
                    val side = (mixL - mixR) * 0.5f * width
                    mixL = mid + side; mixR = mid - side
                }
                if (abs(panAngle) > 0.001f) {
                    val angle = (panAngle + 1f) * (PI.toFloat() / 4f)
                    val gl = cos(angle) * Sqrt2; val gr = sin(angle) * Sqrt2
                    mixL *= gl; mixR *= gr
                }

                // leaving the transition: glide back to the plain (still beat-locked) track
                if (finishing && s.role == CrossmixRole.Incoming) {
                    finishMix = min(1f, finishMix + 1f / (0.05f * sr))
                    mixL += (plainL - mixL) * finishMix; mixR += (plainR - mixR) * finishMix
                }
                if (fadingOut) {
                    outFade = max(0f, outFade - 1f / (0.06f * sr))
                    mixL *= outFade; mixR *= outFade
                }

                mixL = limit(mixL); mixR = limit(mixR)
                if (mixL.isNaN() || mixR.isNaN()) { mixL = 0f; mixR = 0f; clearState() }
                output.putShort((mixL * 32_767f).roundToInt().coerceIn(-32_768, 32_767).toShort())
                if (channelCount == 2) output.putShort((mixR * 32_767f).roundToInt().coerceIn(-32_768, 32_767).toShort())
            }
        }

        /** decks change on beat boundaries; the read head starts where the music is */
        private fun updateDeck(p: FxParams, beat: Float, s: Script) {
            val musicalBeatFrames = s.musicalBeatMs * rate / 1_000.0
            val live = (writeHead - 1).toDouble()
            when (p.deck) {
                FxParams.Deck.Live -> if (deckMode != FxParams.Deck.Live) { deckMode = FxParams.Deck.Live; deckFade = 0f; loopMarkBeat = Float.NaN }
                FxParams.Deck.Loop -> {
                    val length = (p.deckAmount * musicalBeatFrames).coerceIn(64.0, historyL.size / 2.0)
                    if (deckMode != FxParams.Deck.Loop || p.deckMark != loopMarkBeat) {
                        // a new loop: from the start of the beat it's marked on, in what's been recorded
                        val beatsBack = (beat - p.deckMark).coerceAtLeast(0f) * s.mediaPerBeatMs / s.musicalBeatMs
                        loopStart = live - beatsBack * musicalBeatFrames
                        readHead = live
                        loopFrames = length
                        if (deckMode != FxParams.Deck.Loop) deckFade = 0f
                        deckMode = FxParams.Deck.Loop
                        loopMarkBeat = p.deckMark
                    }
                }
                FxParams.Deck.SpinBack -> {
                    if (deckMode != FxParams.Deck.SpinBack) { readHead = live; deckMode = FxParams.Deck.SpinBack; deckFade = 0f }
                    // a hand pulling the record back hard, then letting it coast to a stop
                    val t = p.deckAmount
                    deckSpeed = -(3.2 * (1.0 - t) * (1.0 - t) + 0.02)
                }
                FxParams.Deck.TapeStop -> {
                    if (deckMode != FxParams.Deck.TapeStop) { readHead = live; deckMode = FxParams.Deck.TapeStop; deckFade = 0f }
                    val t = p.deckAmount.toDouble()
                    deckSpeed = max(0.0, 1.0 - t * sqrt(t))
                }
            }
        }

        private fun readHistory(buffer: FloatArray, position: Double): Float {
            // four-point Hermite: clean even when the read speed swings through zero
            val base = floor(position)
            val f = (position - base).toFloat()
            val i = base.toLong()
            val ym1 = buffer[((i - 1) and historyMask.toLong()).toInt()]
            val y0 = buffer[(i and historyMask.toLong()).toInt()]
            val y1 = buffer[((i + 1) and historyMask.toLong()).toInt()]
            val y2 = buffer[((i + 2) and historyMask.toLong()).toInt()]
            val c1 = 0.5f * (y1 - ym1)
            val c2 = ym1 - 2.5f * y0 + 2f * y1 - 0.5f * y2
            val c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1)
            return ((c3 * f + c2) * f + c1) * f + y0
        }

        private fun readRing(buffer: FloatArray, position: Double): Float {
            var p = position
            while (p < 0) p += buffer.size
            val i = p.toInt() % buffer.size
            val f = (p - floor(p)).toFloat()
            return buffer[i] + (buffer[(i + 1) % buffer.size] - buffer[i]) * f
        }

        private fun frac(x: Float): Float = x - floor(x)

        /** transparent below -1 dBFS, a smooth knee above: summed tails never clip */
        private fun limit(x: Float): Float {
            val a = abs(x)
            if (a <= Knee) return x
            val over = (a - Knee) / (1f - Knee)
            val shaped = Knee + (1f - Knee) * (over / (1f + over))
            return if (x < 0f) -shaped else shaped
        }
    }

    /** a topology-preserving state-variable filter: stays stable while its cutoff sweeps */
    private class Svf {
        private var g = 0f; private var k = 1.414f; private var a1 = 0f; private var a2 = 0f; private var a3 = 0f
        private var ic1 = 0f; private var ic2 = 0f
        private var low = 0f; private var band = 0f; private var high = 0f

        fun set(cutoffHz: Float, q: Float, sampleRate: Float) {
            val fc = cutoffHz.coerceIn(10f, sampleRate * 0.45f)
            g = tan(PI.toFloat() * fc / sampleRate)
            k = 1f / q.coerceIn(0.3f, 8f)
            a1 = 1f / (1f + g * (g + k)); a2 = g * a1; a3 = g * a2
        }

        fun copyCoefficients(other: Svf) { g = other.g; k = other.k; a1 = other.a1; a2 = other.a2; a3 = other.a3 }

        private fun tick(x: Float) {
            val v3 = x - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2f * v1 - ic1 + Denormal
            ic2 = 2f * v2 - ic2 + Denormal
            low = v2; band = v1; high = x - k * v1 - v2
        }

        fun lowPass(x: Float): Float { tick(x); return low }
        fun highPass(x: Float): Float { tick(x); return high }
        fun bandPass(x: Float): Float { tick(x); return band * k }
        fun clear() { ic1 = 0f; ic2 = 0f; low = 0f; band = 0f; high = 0f }
    }

    /** Freeverb, scaled to the sample rate: eight combs and four allpasses a side */
    private class Reverb {
        private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val allpassTuning = intArrayOf(556, 441, 341, 225)
        private val spread = 23
        private var combsL = arrayOf<FloatArray>(); private var combsR = arrayOf<FloatArray>()
        private var combIndex = IntArray(8); private var combIndexR = IntArray(8)
        private val filterStoreL = FloatArray(8); private val filterStoreR = FloatArray(8)
        private var allL = arrayOf<FloatArray>(); private var allR = arrayOf<FloatArray>()
        private var allIndex = IntArray(4); private var allIndexR = IntArray(4)
        private var feedback = 0.84f
        private var damp = 0.25f
        var outL = 0f; private set
        var outR = 0f; private set
        private var energy = 0f
        val ringing: Boolean get() = energy > 1e-6f

        fun setup(sampleRate: Int) {
            val scale = sampleRate / 44_100f
            combsL = Array(8) { FloatArray((combTuning[it] * scale).toInt()) }
            combsR = Array(8) { FloatArray(((combTuning[it] + spread) * scale).toInt()) }
            allL = Array(4) { FloatArray((allpassTuning[it] * scale).toInt()) }
            allR = Array(4) { FloatArray(((allpassTuning[it] + spread) * scale).toInt()) }
            clear()
        }

        fun release() { combsL = arrayOf(); combsR = arrayOf(); allL = arrayOf(); allR = arrayOf() }

        fun clear() {
            combsL.forEach { it.fill(0f) }; combsR.forEach { it.fill(0f) }
            allL.forEach { it.fill(0f) }; allR.forEach { it.fill(0f) }
            filterStoreL.fill(0f); filterStoreR.fill(0f)
            combIndex.fill(0); combIndexR.fill(0); allIndex.fill(0); allIndexR.fill(0)
            outL = 0f; outR = 0f; energy = 0f
        }

        fun configure(size: Float, freeze: Float) {
            feedback = (0.7f + 0.28f * size.coerceIn(0f, 1f)) * (1f - freeze) + 0.995f * freeze
            damp = 0.3f * (1f - freeze)
        }

        fun process(inL: Float, inR: Float) {
            if (combsL.isEmpty()) return
            val input = (inL + inR) * 0.015f
            var l = 0f; var r = 0f
            for (c in 0 until 8) {
                l += comb(combsL[c], combIndex, c, filterStoreL, input)
                r += comb(combsR[c], combIndexR, c, filterStoreR, input)
            }
            for (a in 0 until 4) {
                l = allpass(allL[a], allIndex, a, l)
                r = allpass(allR[a], allIndexR, a, r)
            }
            outL = l * 2.6f; outR = r * 2.6f
            energy = energy * 0.999f + (abs(outL) + abs(outR)) * 0.001f
        }

        private fun comb(buffer: FloatArray, index: IntArray, n: Int, store: FloatArray, input: Float): Float {
            val i = index[n]
            val output = buffer[i]
            store[n] = output * (1f - damp) + store[n] * damp + Denormal
            buffer[i] = input + store[n] * feedback
            index[n] = if (i + 1 >= buffer.size) 0 else i + 1
            return output
        }

        private fun allpass(buffer: FloatArray, index: IntArray, n: Int, input: Float): Float {
            val i = index[n]
            val buffered = buffer[i]
            val output = -input + buffered
            buffer[i] = input + buffered * 0.5f + Denormal
            index[n] = if (i + 1 >= buffer.size) 0 else i + 1
            return output
        }
    }

    private companion object {
        val EmptyBuffer: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        const val BlockFrames = 32
        const val HistorySeconds = 8
        const val MaxEchoSeconds = 2.5f
        const val MaxDelayMs = 450.0
        const val Knee = 0.89f
        const val Denormal = 1e-20f
        val Sqrt2 = sqrt(2f)
    }
}
