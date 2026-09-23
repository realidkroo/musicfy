package com.example.musicfy.crossmix

import com.example.musicfy.lyrics.LyricsUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Offline metadata used by Crossmix. All times are in milliseconds. */
data class TrackAnalysis(
    val bpm: Float?,
    val beatGridMs: LongArray,
    val key: String?,
    val keyConfidence: Float,
    val onsetMs: LongArray,
    val leadingSilenceEndMs: Long,
    val trailingSilenceStartMs: Long,
    val quietPointsMs: LongArray,
)

data class CutPointResult(
    val timeMs: Long,
    val sourceLine: String? = null,
    val snappedToBeat: Boolean = false,
)

data class CrossmixTransitionPlan(
    val startMs: Long,
    val durationMs: Long,
    val nextTrackStartMs: Long,
    val nextTrackSpeed: Float,
    val usesBeatMatch: Boolean,
    val cutPoint: CutPointResult,
    val mixProfile: CrossmixMixProfile,
)

/** All time-based effect parameters are derived from the outgoing track's beat period. */
data class CrossmixMixProfile(
    val delayMs: Int,
    val tremoloHz: Float,
    val rhythmicDepth: Float,
    val filterDepth: Float,
    val harmonicPitchRatio: Float,
    val harmonicClash: Boolean,
)

/**
 * A small, dependency-free implementation of spectral-flux tempo/key analysis. It deliberately
 * runs off the playback thread and accepts already decoded PCM, keeping UI and audio output safe.
 */
object CrossmixAudioAnalyzer {
    private const val AnalysisSampleRate = 22_050
    private const val FrameSize = 1_024
    private const val HopSize = 512
    private const val SilenceWindowMs = 100
    private val MajorProfile = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MinorProfile = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)
    private val NoteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun analyzePcm16(
        pcm: ByteArray,
        sampleRate: Int,
        channelCount: Int,
    ): TrackAnalysis? {
        if (sampleRate <= 0 || channelCount <= 0 || pcm.size < channelCount * 4) return null
        val mono = downmixAndResample(pcm, sampleRate, channelCount)
        if (mono.size < FrameSize * 2) return null

        val frameCount = 1 + (mono.size - FrameSize) / HopSize
        val flux = FloatArray(frameCount)
        val rms = FloatArray(frameCount)
        val chroma = FloatArray(12)
        val previousMagnitude = FloatArray(FrameSize / 2 + 1)
        var frame = 0
        var offset = 0
        while (offset + FrameSize <= mono.size) {
            val spectrum = magnitudeSpectrum(mono, offset)
            var totalFlux = 0f
            var energy = 0.0
            for (i in spectrum.indices) {
                totalFlux += max(0f, spectrum[i] - previousMagnitude[i])
                previousMagnitude[i] = spectrum[i]
            }
            for (i in 0 until FrameSize) energy += mono[offset + i] * mono[offset + i]
            flux[frame] = totalFlux
            rms[frame] = kotlin.math.sqrt(energy / FrameSize).toFloat()
            addChroma(spectrum, chroma)
            frame++
            offset += HopSize
        }

        val smoothedFlux = smooth(flux)
        val onsets = pickOnsets(smoothedFlux)
        val bpm = estimateTempo(smoothedFlux)
        val beats = bpm?.let { buildBeatGrid(onsets, it, mono.size) } ?: LongArray(0)
        val key = estimateKey(chroma)
        val silence = findSilenceBoundaries(rms, mono.size)
        val quietPoints = quietPoints(rms, mono.size)

        return TrackAnalysis(
            bpm = bpm,
            beatGridMs = beats,
            key = key.first,
            keyConfidence = key.second,
            onsetMs = onsets,
            leadingSilenceEndMs = silence.first,
            trailingSilenceStartMs = silence.second,
            quietPointsMs = quietPoints,
        )
    }

    private fun downmixAndResample(pcm: ByteArray, inputRate: Int, channels: Int): FloatArray {
        val sourceFrames = pcm.size / (channels * 2)
        val mono = FloatArray(sourceFrames)
        var byteOffset = 0
        for (frame in 0 until sourceFrames) {
            var sum = 0f
            repeat(channels) {
                val lo = pcm[byteOffset++].toInt() and 0xff
                val hi = pcm[byteOffset++].toInt()
                sum += ((hi shl 8) or lo).toShort() / 32768f
            }
            mono[frame] = sum / channels
        }
        if (inputRate == AnalysisSampleRate) return mono
        val outputSize = (sourceFrames.toLong() * AnalysisSampleRate / inputRate).toInt()
        return FloatArray(outputSize) { index ->
            val position = index.toDouble() * inputRate / AnalysisSampleRate
            val left = position.toInt().coerceIn(0, mono.lastIndex)
            val right = (left + 1).coerceAtMost(mono.lastIndex)
            val fraction = (position - left).toFloat()
            mono[left] + (mono[right] - mono[left]) * fraction
        }
    }

    private fun magnitudeSpectrum(samples: FloatArray, offset: Int): FloatArray {
        val real = FloatArray(FrameSize)
        val imaginary = FloatArray(FrameSize)
        for (i in 0 until FrameSize) {
            val window = 0.5f - 0.5f * cos((2.0 * PI * i) / (FrameSize - 1)).toFloat()
            real[i] = samples[offset + i] * window
        }
        fft(real, imaginary)
        return FloatArray(FrameSize / 2 + 1) { i -> kotlin.math.sqrt(real[i] * real[i] + imaginary[i] * imaginary[i]) }
    }

    private fun fft(real: FloatArray, imaginary: FloatArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val rr = real[i]; real[i] = real[j]; real[j] = rr
                val ii = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = ii
            }
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2.0 * PI / length
            val wLengthReal = cos(angle).toFloat()
            val wLengthImaginary = sin(angle).toFloat()
            for (start in real.indices step length) {
                var wReal = 1f
                var wImaginary = 0f
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val oddReal = real[odd] * wReal - imaginary[odd] * wImaginary
                    val oddImaginary = real[odd] * wImaginary + imaginary[odd] * wReal
                    val evenReal = real[even]
                    val evenImaginary = imaginary[even]
                    real[even] = evenReal + oddReal
                    imaginary[even] = evenImaginary + oddImaginary
                    real[odd] = evenReal - oddReal
                    imaginary[odd] = evenImaginary - oddImaginary
                    val nextReal = wReal * wLengthReal - wImaginary * wLengthImaginary
                    wImaginary = wReal * wLengthImaginary + wImaginary * wLengthReal
                    wReal = nextReal
                }
            }
            length = length shl 1
        }
    }

    private fun addChroma(spectrum: FloatArray, chroma: FloatArray) {
        for (bin in 2 until spectrum.size) {
            val frequency = bin * AnalysisSampleRate.toFloat() / FrameSize
            if (frequency !in 55f..5_000f) continue
            val midi = (69 + 12 * (ln(frequency / 440f) / ln(2.0)).toFloat()).roundToInt()
            chroma[(midi % 12 + 12) % 12] += spectrum[bin]
        }
    }

    private fun smooth(values: FloatArray): FloatArray = FloatArray(values.size) { index ->
        var sum = 0f
        var count = 0
        for (i in (index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(values.lastIndex)) {
            sum += values[i]
            count++
        }
        sum / count
    }

    private fun pickOnsets(flux: FloatArray): LongArray {
        val output = ArrayList<Long>()
        for (i in 4 until flux.size - 4) {
            var mean = 0.0
            var count = 0
            for (j in (i - 16).coerceAtLeast(0)..(i + 16).coerceAtMost(flux.lastIndex)) { mean += flux[j]; count++ }
            mean /= count
            if (flux[i] <= mean * 1.35) continue
            if ((i - 2..i + 2).all { flux[i] >= flux[it] }) output += frameToMs(i)
        }
        return output.toLongArray()
    }

    private fun estimateTempo(flux: FloatArray): Float? {
        val framesPerSecond = AnalysisSampleRate.toFloat() / HopSize
        val minLag = (60f / 180f * framesPerSecond).roundToInt()
        val maxLag = (60f / 60f * framesPerSecond).roundToInt()
        var bestLag = 0
        var bestScore = 0.0
        for (lag in minLag..min(maxLag, flux.size / 2)) {
            var score = 0.0
            for (i in 0 until flux.size - lag) score += flux[i] * flux[i + lag]
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        if (bestLag == 0 || bestScore == 0.0) return null
        val rawBpm = (60f * framesPerSecond / bestLag).coerceIn(60f, 180f)
        // Spectral-flux autocorrelation commonly latches on to a half-time pulse. Bias that
        // ambiguous region toward the tempo range in which ordinary pop/dance beat grids live.
        return if (rawBpm < 85f) rawBpm * 2f else rawBpm
    }

    private fun buildBeatGrid(onsets: LongArray, bpm: Float, sampleCount: Int): LongArray {
        val period = 60_000f / bpm
        val durationMs = sampleCount * 1_000L / AnalysisSampleRate
        val anchor = onsets.maxByOrNull { onset -> onsets.count { abs(it - onset) < period / 3 } } ?: 0L
        val beats = ArrayList<Long>()
        var predicted = anchor.toFloat()
        while (predicted <= durationMs) {
            val closest = onsets.minByOrNull { abs(it - predicted) }
            beats += if (closest != null && abs(closest - predicted) <= 75f) closest else predicted.toLong()
            predicted += period
        }
        return beats.distinct().toLongArray()
    }

    private fun estimateKey(chroma: FloatArray): Pair<String?, Float> {
        if (chroma.sum() <= 0f) return null to 0f
        var bestLabel: String? = null
        var bestScore = -1f
        for (root in 0 until 12) {
            for ((suffix, template) in listOf(" major" to MajorProfile, " minor" to MinorProfile)) {
                val score = correlation(chroma, template, root)
                if (score > bestScore) { bestScore = score; bestLabel = NoteNames[root] + suffix }
            }
        }
        return bestLabel to bestScore.coerceIn(0f, 1f)
    }

    private fun correlation(chroma: FloatArray, template: FloatArray, rotation: Int): Float {
        val xMean = chroma.average().toFloat()
        val yMean = template.average().toFloat()
        var numerator = 0f; var xEnergy = 0f; var yEnergy = 0f
        for (i in 0 until 12) {
            val x = chroma[(i + rotation) % 12] - xMean
            val y = template[i] - yMean
            numerator += x * y; xEnergy += x * x; yEnergy += y * y
        }
        return if (xEnergy == 0f || yEnergy == 0f) 0f else numerator / kotlin.math.sqrt(xEnergy * yEnergy)
    }

    private fun findSilenceBoundaries(rms: FloatArray, sampleCount: Int): Pair<Long, Long> {
        val threshold = (rms.maxOrNull() ?: 0f) * 0.01f
        val first = rms.indexOfFirst { it > threshold }.coerceAtLeast(0)
        val last = rms.indexOfLast { it > threshold }.coerceAtLeast(first)
        return frameToMs(first) to min(frameToMs(last + 1), sampleCount * 1_000L / AnalysisSampleRate)
    }

    private fun quietPoints(rms: FloatArray, sampleCount: Int): LongArray {
        val durationMs = sampleCount * 1_000L / AnalysisSampleRate
        val lookbackStart = (durationMs - 30_000L).coerceAtLeast(0L)
        return rms.indices
            .filter { frameToMs(it) >= lookbackStart }
            .sortedBy { rms[it] }
            .take(8)
            .map(::frameToMs)
            .toLongArray()
    }

    private fun frameToMs(frame: Int): Long = frame.toLong() * HopSize * 1_000L / AnalysisSampleRate
}

object LyricsCutPointSelector {
    fun select(
        lyrics: String?,
        trackDurationMs: Long,
        beatGridMs: LongArray,
        quietPointsMs: LongArray,
        fadeDurationMs: Long,
        maxLookbackMs: Long = 30_000L,
    ): CutPointResult {
        val latestStart = (trackDurationMs - maxLookbackMs).coerceAtLeast(0L)
        val target = (trackDurationMs - fadeDurationMs).coerceAtLeast(latestStart)
        val lines = lyrics?.let(LyricsUtils::parseLyrics).orEmpty()
        val candidates = lines.mapIndexed { index, line ->
            val end = lines.getOrNull(index + 1)?.time ?: trackDurationMs
            Triple(line.text, line.time, end)
        // A crossfade must complete before the current item ends, so a lyric line that runs
        // beyond the available fade window cannot be used as its start point.
        }.filter { (_, _, end) -> end in latestStart..target }

        val candidate = candidates
            .filter { (text) -> text.trim().split(Regex("\\s+")).count { it.isNotBlank() } <= 4 }
            .maxByOrNull { it.third }
            ?: candidates.minByOrNull { abs(it.third - target) }
        // Let the line resolve before the outgoing music yields. Snapping only forward prevents
        // a beat just before the final word from pulling the transition back into the lyric.
        val unbounded = candidate?.third?.plus(350L)
            ?: quietPointsMs.minByOrNull { abs(it - target) }
            ?: target
        val time = unbounded.coerceIn(latestStart, target)
        val followingBeat = beatGridMs.firstOrNull { it >= time && it - time <= 800L }
        return if (followingBeat != null) {
            CutPointResult(followingBeat, candidate?.first, snappedToBeat = true)
        } else {
            CutPointResult(time, candidate?.first)
        }
    }
}

object CrossmixTransitionPlanner {
    fun plan(
        current: TrackAnalysis?,
        next: TrackAnalysis?,
        lyrics: String?,
        trackDurationMs: Long,
        fadeDurationMs: Long,
    ): CrossmixTransitionPlan {
        val cut = LyricsCutPointSelector.select(
            lyrics = lyrics,
            trackDurationMs = trackDurationMs,
            beatGridMs = current?.beatGridMs ?: longArrayOf(),
            quietPointsMs = current?.quietPointsMs ?: longArrayOf(),
            fadeDurationMs = fadeDurationMs,
        )
        val speed = beatMatchSpeed(current, next)
        val profile = createMixProfile(current, next, speed)
        return CrossmixTransitionPlan(
            startMs = cut.timeMs,
            durationMs = fadeDurationMs,
            nextTrackStartMs = next?.leadingSilenceEndMs ?: 0L,
            nextTrackSpeed = speed ?: 1f,
            usesBeatMatch = speed != null,
            cutPoint = cut,
            mixProfile = profile,
        )
    }

    private fun beatMatchSpeed(current: TrackAnalysis?, next: TrackAnalysis?): Float? {
        val currentBpm = current?.bpm ?: return null
        val nextBpm = next?.bpm ?: return null
        if (current.keyConfidence < 0.35f || next.keyConfidence < 0.35f) return null
        if (!keysCompatible(current.key, next.key)) return null
        return (currentBpm / nextBpm).takeIf { it in 0.92f..1.08f }
    }

    private fun keysCompatible(first: String?, second: String?): Boolean {
        val distance = keyDistance(first, second) ?: return true
        return distance <= 2 || distance == 5
    }

    private fun createMixProfile(
        current: TrackAnalysis?,
        next: TrackAnalysis?,
        tempoRatio: Float?,
    ): CrossmixMixProfile {
        val currentBpm = current?.bpm
        val nextBpm = next?.bpm
        val beatPeriodMs = currentBpm?.let { 60_000f / it } ?: 500f
        val bpmGap = if (currentBpm != null && nextBpm != null) {
            abs(currentBpm - nextBpm) / currentBpm
        } else {
            0.08f
        }
        // Rhythmic treatments are rich for a clean beat match and fade away as matching becomes
        // uncertain, where a purely tonal transition is less likely to sound accidentally offbeat.
        val rhythmicDepth = if (tempoRatio != null) {
            ((0.08f - bpmGap) / 0.05f).coerceIn(0f, 1f)
        } else {
            0.15f
        }
        val harmonicDistance = keyDistance(current?.key, next?.key)
        val harmonicClash = harmonicDistance == 6
        val semitoneShift = harmonicSemitoneShift(current?.key, next?.key)
        val pitchRatio = semitoneShift?.let { Math.pow(2.0, it / 12.0).toFloat() } ?: 1f
        return CrossmixMixProfile(
            delayMs = (beatPeriodMs / 2f).roundToInt().coerceIn(120, 600),
            tremoloHz = (2_000f / beatPeriodMs).coerceIn(1.5f, 8f),
            rhythmicDepth = rhythmicDepth,
            filterDepth = if (harmonicClash) 1f else 0.62f + (1f - rhythmicDepth) * 0.18f,
            harmonicPitchRatio = pitchRatio,
            harmonicClash = harmonicClash,
        )
    }

    private fun keyDistance(first: String?, second: String?): Int? {
        val a = keyIndex(first) ?: return null
        val b = keyIndex(second) ?: return null
        return abs(a - b).let { min(it, 12 - it) }
    }

    /** Small (one or two semitone) moves can be safely nudged toward the outgoing root. */
    private fun harmonicSemitoneShift(current: String?, next: String?): Int? {
        val a = keyIndex(current) ?: return null
        val b = keyIndex(next) ?: return null
        var signed = a - b
        if (signed > 6) signed -= 12
        if (signed < -6) signed += 12
        return signed.takeIf { abs(it) in 1..2 }
    }

    private fun keyIndex(key: String?): Int? {
        val root = key?.substringBefore(' ') ?: return null
        return listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").indexOf(root)
            .takeIf { it >= 0 }
    }
}
