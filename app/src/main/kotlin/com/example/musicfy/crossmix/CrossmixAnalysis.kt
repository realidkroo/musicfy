package com.example.musicfy.crossmix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What Crossmix knows about one track. All times are milliseconds into the track.
 *
 * [energy], [bass] and [brightness] are one value per second of [analyzedMs]; [complete] is false
 * when only the opening was decoded (the next song: Crossmix only ever enters it in its first two
 * minutes, so decoding the rest would be wasted work).
 */
data class TrackAnalysis(
    val analyzedMs: Long,
    val complete: Boolean,
    val bpm: Float?,
    /** how sure the tempo is, 0..1. below ~0.35 nothing is beat-matched to it */
    val bpmConfidence: Float,
    val beatsMs: LongArray,
    /** index into [beatsMs] of the first beat that starts a bar */
    val downbeatIndex: Int,
    val key: String?,
    val keyConfidence: Float,
    val leadingSilenceEndMs: Long,
    val trailingSilenceStartMs: Long,
    val energy: FloatArray,
    val bass: FloatArray,
    val brightness: FloatArray,
    val vibe: Vibe,
) {
    val beatMs: Float? get() = bpm?.let { 60_000f / it }

    val isBeatReliable: Boolean get() = bpm != null && bpmConfidence >= 0.35f && beatsMs.size >= 16

    /** the beats that start a bar, in order */
    fun barStartsMs(): LongArray {
        if (beatsMs.isEmpty()) return LongArray(0)
        val first = downbeatIndex.coerceIn(0, 3)
        return LongArray((beatsMs.size - first + 3) / 4) { beatsMs[first + it * 4] }
    }

    /** the 0..1 loudness around [ms], averaged over [windowMs] */
    fun energyAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(energy, ms, windowMs)

    fun bassAround(ms: Long, windowMs: Long = 4_000L): Float = averageOver(bass, ms, windowMs)

    /**
     * the beat grid right around [ms], fitted to the nearby beats with least squares: the phase of
     * the beat at or before [ms] and the local beat length. far steadier than any one detected beat,
     * which is what lets two tracks lock without flamming.
     */
    fun localGrid(ms: Long, span: Int = 12): LocalGrid? {
        if (beatsMs.size < 4) return null
        val nearest = beatsMs.indices.minByOrNull { abs(beatsMs[it] - ms) } ?: return null
        val from = (nearest - span).coerceAtLeast(0)
        val to = (nearest + span).coerceAtMost(beatsMs.lastIndex)
        val count = to - from + 1
        if (count < 4) return null
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in from..to) {
            val x = (i - from).toDouble(); val y = beatsMs[i].toDouble()
            sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denominator = count * sxx - sx * sx
        if (denominator == 0.0) return null
        val period = (count * sxy - sx * sy) / denominator
        val intercept = (sy - period * sx) / count
        if (period <= 200.0 || period >= 1_500.0) return null
        return LocalGrid(firstBeatMs = intercept, periodMs = period, firstIndex = from)
    }

    private fun averageOver(values: FloatArray, ms: Long, windowMs: Long): Float {
        if (values.isEmpty()) return 0.5f
        val from = ((ms - windowMs / 2) / 1_000L).toInt().coerceIn(0, values.lastIndex)
        val to = ((ms + windowMs / 2) / 1_000L).toInt().coerceIn(from, values.lastIndex)
        var sum = 0f
        for (i in from..to) sum += values[i]
        return sum / (to - from + 1)
    }
}

/** a stretch of steady beats: beat n of the stretch falls at firstBeatMs + n * periodMs */
data class LocalGrid(val firstBeatMs: Double, val periodMs: Double, val firstIndex: Int) {
    /** the beat time nearest [ms] on this grid */
    fun snap(ms: Long): Long {
        val n = ((ms - firstBeatMs) / periodMs).roundToInt()
        return (firstBeatMs + n * periodMs).roundToLong()
    }

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}

/**
 * the feel of a track, each 0..1. energy is loudness and density, danceability how strong and
 * regular the pulse is, brightness how much top end, bassWeight how much low end.
 */
data class Vibe(
    val energy: Float,
    val danceability: Float,
    val brightness: Float,
    val bassWeight: Float,
    val minor: Boolean,
)

/**
 * Tempo, beats, key, loudness and vibe from decoded audio, fed as it decodes so a whole song is
 * never held in memory: only a handful of numbers per 12 ms frame are kept.
 */
class StreamingTrackAnalyzer {
    // the last FrameSize samples, written round and round; read oldest-first for each frame
    private val ring = FloatArray(FrameSize)
    private var writeIndex = 0
    private var sinceHop = 0
    private var totalSamples = 0L

    private val window = FloatArray(FrameSize) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FrameSize - 1))).toFloat() }
    private val real = FloatArray(FrameSize)
    private val imaginary = FloatArray(FrameSize)
    private val previousLog = FloatArray(Bins)
    private val bandRise = FloatArray(BandWeights.size)
    private val chroma = FloatArray(12)

    private val flux = GrowingFloats()
    private val rms = GrowingFloats()
    private val low = GrowingFloats()
    private val centroid = GrowingFloats()

    /** [samples] are mono at [SampleRate] */
    fun feed(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            ring[writeIndex] = samples[i]
            writeIndex = (writeIndex + 1) and (FrameSize - 1)
            totalSamples++
            if (totalSamples >= FrameSize && ++sinceHop >= HopSize) {
                sinceHop = 0
                analyzeFrame()
            }
        }
    }

    val analyzedMs: Long get() = totalSamples * 1_000L / SampleRate

    fun finish(complete: Boolean): TrackAnalysis? {
        val frames = flux.size
        if (frames < FramesPerSecondF * 8) return null

        val onset = onsetEnvelope(flux.toArray())
        val tempo = estimateTempo(onset)
        val beats = tempo?.let { trackBeats(onset, it.first) } ?: LongArray(0)
        val lowArray = low.toArray()
        val downbeat = if (beats.size >= 8) pickDownbeat(beats, onset, lowArray) else 0
        val key = estimateKey(chroma)
        val rmsArray = rms.toArray()
        val silence = silenceBounds(rmsArray)
        val perSecond = perSecondCurves(rmsArray, lowArray, centroid.toArray())

        val vibe = Vibe(
            energy = overallEnergy(rmsArray, onset),
            danceability = ((tempo?.second ?: 0f) * 0.7f + pulseStrength(beats, onset) * 0.3f).coerceIn(0f, 1f),
            brightness = perSecond.third.averageOfLoud(perSecond.first),
            bassWeight = perSecond.second.averageOfLoud(perSecond.first),
            minor = key.first?.endsWith("minor") == true,
        )

        return TrackAnalysis(
            analyzedMs = analyzedMs,
            complete = complete,
            bpm = tempo?.first,
            bpmConfidence = tempo?.second ?: 0f,
            beatsMs = beats,
            downbeatIndex = downbeat,
            key = key.first,
            keyConfidence = key.second,
            leadingSilenceEndMs = silence.first,
            trailingSilenceStartMs = silence.second,
            energy = perSecond.first,
            bass = perSecond.second,
            brightness = perSecond.third,
            vibe = vibe,
        )
    }

    private fun analyzeFrame() {
        var energy = 0.0
        for (i in 0 until FrameSize) {
            val v = ring[(writeIndex + i) and (FrameSize - 1)]
            energy += v * v
            real[i] = v * window[i]
            imaginary[i] = 0f
        }
        fft(real, imaginary)

        bandRise.fill(0f)
        var lowEnergy = 0f
        var totalEnergy = 0f
        var weighted = 0f
        for (bin in 1 until Bins) {
            val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
            // log-compressed flux: a quiet hi-hat still registers next to a loud pad
            val compressed = ln(1f + 1_000f * magnitude)
            val rise = compressed - previousLog[bin]
            if (rise > 0f) bandRise[BandOf[bin]] += rise
            previousLog[bin] = compressed
            val power = magnitude * magnitude
            totalEnergy += power
            weighted += power * bin
            if (bin <= LowBins) lowEnergy += power
            val frequency = bin * SampleRate.toFloat() / FrameSize
            if (frequency in 55f..5_000f) {
                val midi = (69 + 12 * (ln(frequency / 440f) / ln(2f))).roundToInt()
                chroma[(midi % 12 + 12) % 12] += magnitude
            }
        }
        // each band's average rise, weighted toward the low end: the kick and the bass carry the
        // beat, while hats (noise spread over hundreds of bins, between the beats) would otherwise
        // outvote a kick that lives in five, and put the whole grid half a beat out
        var onset = 0f
        for (band in BandWeights.indices) onset += BandWeights[band] * bandRise[band] / BandBins[band]
        flux.add(onset)
        rms.add(sqrt(energy / FrameSize).toFloat())
        low.add(if (totalEnergy > 0f) lowEnergy / totalEnergy else 0f)
        centroid.add(if (totalEnergy > 0f) (weighted / totalEnergy) * SampleRate / FrameSize else 0f)
    }

    /** spectral flux with its local average taken away, so only the jumps remain */
    private fun onsetEnvelope(raw: FloatArray): FloatArray {
        val half = (FramesPerSecondF / 2).roundToInt()
        val prefix = DoubleArray(raw.size + 1)
        for (i in raw.indices) prefix[i + 1] = prefix[i] + raw[i]
        val out = FloatArray(raw.size)
        var peak = 0f
        for (i in raw.indices) {
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(raw.size)
            val mean = ((prefix[to] - prefix[from]) / (to - from)).toFloat()
            val value = max(0f, raw[i] - mean)
            out[i] = value
            if (value > peak) peak = value
        }
        if (peak > 0f) for (i in out.indices) out[i] /= peak
        return out
    }

    /**
     * the beat period from the onset curve's autocorrelation, weighted toward the tempos people
     * dance at (centred on 120). returns the bpm and a 0..1 confidence.
     */
    private fun estimateTempo(onset: FloatArray): Pair<Float, Float>? {
        val minLag = (60f / 200f * FramesPerSecondF).roundToInt()
        val maxLag = min((60f / 60f * FramesPerSecondF).roundToInt(), onset.size / 3)
        if (maxLag <= minLag + 2) return null
        val acf = FloatArray(maxLag + 2)
        val n = onset.size
        for (lag in minLag..maxLag + 1) {
            var sum = 0.0
            var i = 0
            while (i + lag < n) { sum += onset[i] * onset[i + lag]; i++ }
            acf[lag] = (sum / (n - lag)).toFloat()
        }
        var bestLag = -1
        var bestScore = 0f
        var total = 0f
        for (lag in minLag..maxLag) {
            val bpm = 60f * FramesPerSecondF / lag
            val prior = exp(-0.5f * (ln(bpm / 120f) / ln(2f) / 0.9f).let { it * it })
            val score = acf[lag] * prior
            total += acf[lag]
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        if (bestLag <= 0 || bestScore <= 0f) return null
        // sub-frame peak, so 128 and 129 bpm don't round to the same lag
        val y0 = acf[bestLag - 1]; val y1 = acf[bestLag]; val y2 = acf[bestLag + 1]
        val curvature = y0 - 2 * y1 + y2
        val shift = if (curvature < 0f) (0.5f * (y0 - y2) / curvature).coerceIn(-0.5f, 0.5f) else 0f
        var bpm = 60f * FramesPerSecondF / (bestLag + shift)
        while (bpm < 78f) bpm *= 2f
        while (bpm > 168f) bpm /= 2f
        val mean = total / (maxLag - minLag + 1)
        val confidence = if (mean > 0f) ((acf[bestLag] / mean - 1.2f) / 2.5f).coerceIn(0f, 1f) else 0f
        return bpm to confidence
    }

    /**
     * Ellis' dynamic-programming beat tracker: the best chain of beats that both lands on onsets
     * and keeps an even spacing. each beat is then nudged to its onset peak with sub-frame accuracy.
     */
    private fun trackBeats(onset: FloatArray, bpm: Float): LongArray {
        val period = 60f * FramesPerSecondF / bpm
        val n = onset.size
        val score = FloatArray(n)
        val back = IntArray(n) { -1 }
        val tightness = 400f
        val longest = (2f * period).roundToInt()
        val shortest = (period / 2f).roundToInt().coerceAtLeast(1)
        // the cost of a gap of d frames, worked out once instead of a log per comparison
        val penalty = FloatArray(longest + 1) { d ->
            if (d == 0) 0f else -tightness * ln(d / period).let { it * it }
        }
        for (t in onset.indices) {
            var best = 0f
            var bestFrom = -1
            val from = (t - longest).coerceAtLeast(0)
            val to = t - shortest
            var tau = from
            while (tau <= to) {
                val candidate = score[tau] + penalty[t - tau]
                if (bestFrom < 0 || candidate > best) { best = candidate; bestFrom = tau }
                tau++
            }
            score[t] = onset[t] + if (bestFrom >= 0) max(0f, best) else 0f
            back[t] = if (bestFrom >= 0 && best > 0f) bestFrom else -1
        }
        var t = (n - period.roundToInt()).coerceAtLeast(0)
        var end = t
        var endScore = -1f
        while (t < n) { if (score[t] > endScore) { endScore = score[t]; end = t }; t++ }
        val frames = ArrayList<Int>()
        var cursor = end
        while (cursor >= 0) { frames += cursor; cursor = back[cursor] }
        frames.reverse()
        return LongArray(frames.size) { index ->
            val f = frames[index]
            val y0 = onset.getOrElse(f - 1) { 0f }; val y1 = onset[f]; val y2 = onset.getOrElse(f + 1) { 0f }
            val curvature = y0 - 2 * y1 + y2
            val shift = if (curvature < 0f) (0.5f * (y0 - y2) / curvature).coerceIn(-0.5f, 0.5f) else 0f
            frameToMs(f + shift)
        }
    }

    /**
     * which of every four beats starts the bar: the one with the most kick under it and the
     * strongest attack. pop and dance music land their kick and their chord changes there.
     */
    private fun pickDownbeat(beats: LongArray, onset: FloatArray, lowShare: FloatArray): Int {
        val scores = FloatArray(4)
        val counts = IntArray(4)
        for (i in beats.indices) {
            val f = msToFrame(beats[i]).coerceIn(0, onset.lastIndex)
            var kick = 0f
            for (j in (f - 1).coerceAtLeast(0)..(f + 2).coerceAtMost(lowShare.lastIndex)) kick += lowShare[j]
            scores[i % 4] += onset[f] * 0.6f + kick * 0.4f
            counts[i % 4]++
        }
        var best = 0
        for (k in 1 until 4) {
            if (scores[k] / max(1, counts[k]) > scores[best] / max(1, counts[best])) best = k
        }
        return best
    }

    private fun pulseStrength(beats: LongArray, onset: FloatArray): Float {
        if (beats.isEmpty()) return 0f
        var sum = 0f
        for (beat in beats) sum += onset[msToFrame(beat).coerceIn(0, onset.lastIndex)]
        val onBeat = sum / beats.size
        val overall = onset.average().toFloat().coerceAtLeast(1e-4f)
        return ((onBeat / overall - 1f) / 3f).coerceIn(0f, 1f)
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
        return if (xEnergy == 0f || yEnergy == 0f) 0f else numerator / sqrt(xEnergy * yEnergy)
    }

    /** where sound starts and stops: 45 dB under the loudest frame */
    private fun silenceBounds(rms: FloatArray): Pair<Long, Long> {
        val threshold = (rms.maxOrNull() ?: 0f) * 0.0056f
        val first = rms.indexOfFirst { it > threshold }.coerceAtLeast(0)
        val last = rms.indexOfLast { it > threshold }.coerceAtLeast(first)
        return frameToMs(first.toFloat()) to min(frameToMs(last + 1f), analyzedMs)
    }

    /** per second: loudness relative to the track's peak (0..1), bass share, brightness */
    private fun perSecondCurves(rms: FloatArray, lowShare: FloatArray, centroidHz: FloatArray): Triple<FloatArray, FloatArray, FloatArray> {
        val seconds = (rms.size / FramesPerSecondF).toInt().coerceAtLeast(1)
        val db = FloatArray(seconds)
        val bass = FloatArray(seconds)
        val bright = FloatArray(seconds)
        for (s in 0 until seconds) {
            var energy = 0.0; var lowSum = 0f; var centroidSum = 0f
            val from = (s * FramesPerSecondF).toInt().coerceAtMost(rms.size)
            val to = min(rms.size, ((s + 1) * FramesPerSecondF).toInt())
            for (f in from until to) { energy += rms[f] * rms[f]; lowSum += lowShare[f]; centroidSum += centroidHz[f] }
            val count = max(1, to - from)
            db[s] = (10.0 * log10(energy / count + 1e-12)).toFloat()
            bass[s] = (lowSum / count * 2.5f).coerceIn(0f, 1f)
            bright[s] = ((centroidSum / count - 600f) / 3_400f).coerceIn(0f, 1f)
        }
        val peak = db.maxOrNull() ?: 0f
        val loudness = FloatArray(seconds) { ((db[it] - (peak - 24f)) / 24f).coerceIn(0f, 1f) }
        return Triple(loudness, bass, bright)
    }

    private fun overallEnergy(rms: FloatArray, onset: FloatArray): Float {
        val sorted = rms.sortedArray()
        val loud = sorted.copyOfRange((sorted.size * 0.4f).toInt(), sorted.size)
        val meanSquare = loud.fold(0.0) { acc, v -> acc + v * v } / max(1, loud.size)
        val db = 10.0 * log10(meanSquare + 1e-12)
        val loudness = ((db + 30.0) / 22.0).toFloat().coerceIn(0f, 1f)
        val density = (onset.count { it > 0.3f } / (onset.size / FramesPerSecondF) / 6f).coerceIn(0f, 1f)
        return (loudness * 0.65f + density * 0.35f).coerceIn(0f, 1f)
    }

    private fun FloatArray.averageOfLoud(loudness: FloatArray): Float {
        var sum = 0f; var count = 0
        for (i in indices) if (loudness.getOrElse(i) { 0f } > 0.5f) { sum += this[i]; count++ }
        return if (count == 0) (if (isEmpty()) 0.5f else average().toFloat()) else sum / count
    }

    // a hit shows up in the onset curve while it's still sliding into the analysis window: the
    // flux peaks with the hit ~3/4 of the way along it, not at its centre. measured on clean kicks
    private val onsetLagSamples = FrameSize * 0.27f

    private fun frameToMs(frame: Float): Long = ((frame * HopSize + FrameSize / 2f + onsetLagSamples) * 1_000f / SampleRate).toLong()

    private fun msToFrame(ms: Long): Int = ((ms * SampleRate / 1_000f - FrameSize / 2f - onsetLagSamples) / HopSize).roundToInt()

    private fun fft(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until re.size) {
            var bit = re.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var length = 2
        while (length <= re.size) {
            val half = length / 2
            for (start in re.indices step length) {
                for (k in 0 until half) {
                    val twiddle = k * (FrameSize / length)
                    val wr = CosTable[twiddle]; val wi = -SinTable[twiddle]
                    val even = start + k; val odd = even + half
                    val oddReal = re[odd] * wr - im[odd] * wi
                    val oddImaginary = re[odd] * wi + im[odd] * wr
                    re[odd] = re[even] - oddReal; im[odd] = im[even] - oddImaginary
                    re[even] += oddReal; im[even] += oddImaginary
                }
            }
            length = length shl 1
        }
    }

    private class GrowingFloats {
        private var values = FloatArray(4_096)
        var size = 0
            private set

        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): FloatArray = values.copyOf(size)
    }

    companion object {
        const val SampleRate = 22_050
        private const val FrameSize = 1_024
        private const val HopSize = 256
        private const val Bins = FrameSize / 2 + 1
        private const val FramesPerSecondF = 22_050f / 256f
        // bins up to ~150 Hz: the kick and the bass line
        private val LowBins = (150f * FrameSize / SampleRate).roundToInt()
        // onset bands: kick and bass, low mids, mids (snare body, vocals), highs (hats, air)
        private val BandEdgesHz = floatArrayOf(160f, 600f, 3_000f)
        private val BandWeights = floatArrayOf(1f, 0.8f, 0.55f, 0.2f)
        private val BandOf = IntArray(Bins) { bin ->
            val hz = bin * SampleRate.toFloat() / FrameSize
            BandEdgesHz.indexOfFirst { hz < it }.let { if (it < 0) BandEdgesHz.size else it }
        }
        private val BandBins = FloatArray(BandWeights.size) { band -> (1 until Bins).count { BandOf[it] == band }.coerceAtLeast(1).toFloat() }
        private val CosTable = FloatArray(FrameSize / 2) { cos(2.0 * PI * it / FrameSize).toFloat() }
        private val SinTable = FloatArray(FrameSize / 2) { sin(2.0 * PI * it / FrameSize).toFloat() }
        private val MajorProfile = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
        private val MinorProfile = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)
        private val NoteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
}

/** key names to pitch classes and the distances DJs mix by */
object Harmony {
    private val Names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun pitchClass(key: String?): Int? = key?.substringBefore(' ')?.let(Names::indexOf)?.takeIf { it >= 0 }

    /**
     * distance on the Camelot wheel: 0 same key, 1 a neighbour or the relative major/minor, more
     * means the two clash when they play together
     */
    fun camelotDistance(a: String?, b: String?): Int? {
        val ca = camelot(a) ?: return null
        val cb = camelot(b) ?: return null
        val ring = abs(ca.first - cb.first).let { min(it, 12 - it) }
        return if (ca.second == cb.second) ring else if (ring == 0) 1 else ring + 1
    }

    private fun camelot(key: String?): Pair<Int, Boolean>? {
        val pc = pitchClass(key) ?: return null
        val minor = key!!.endsWith("minor")
        // the relative major shares a number: A minor (8A) with C major (8B)
        val majorRoot = if (minor) (pc + 3) % 12 else pc
        val number = (majorRoot * 7) % 12
        return number to minor
    }
}
