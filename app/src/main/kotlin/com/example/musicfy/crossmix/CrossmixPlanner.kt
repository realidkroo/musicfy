package com.example.musicfy.crossmix

import com.example.musicfy.lyrics.LyricsUtils
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One planned hand-over. Both songs share a clock counted in the outgoing song's beats: beat 0 is
 * [outStartMs] in the outgoing song, the outgoing side has handed over by beat [lengthBeats], and
 * the incoming song's [inCueMs] is heard at beat [entryBeat].
 */
data class CrossmixPlan(
    val style: CrossmixStyle,
    val variation: Int,
    val lengthBeats: Int,
    val outStartMs: Long,
    val outBeatMs: Double,
    val inCueMs: Long,
    val entryBeat: Float,
    /** incoming media ms per outgoing beat: its own beat length when the tempos are matched */
    val inMediaPerBeat: Double,
    /** the incoming song's playback speed during the mix (1 when not tempo-matched) */
    val inSpeed: Float,
    val beatMatched: Boolean,
    /** the transition beat the incoming player starts on (silently, if before [entryBeat]) */
    val inStartBeat: Float,
) {
    /** incoming media time heard at transition beat [beat] */
    fun inMediaAt(beat: Float): Double = inCueMs + (beat - entryBeat) * inMediaPerBeat

    /** where the incoming player starts */
    val inStartMs: Long get() = inMediaAt(inStartBeat).toLong().coerceAtLeast(0L)

    /** outgoing media time of transition beat [beat] */
    fun outMediaAt(beat: Float): Double = outStartMs + beat * outBeatMs

    /** the outgoing player can be let go after this (tails included) */
    val outReleaseMs: Long get() = outMediaAt(lengthBeats + style.tailBeats + 1f).toLong()

    /** after this beat the incoming song is untouched */
    val inSettledBeat: Float get() = lengthBeats + 8f
}

/** what the planner sees of each song */
data class CrossmixTrackInput(
    val mediaId: String,
    val durationMs: Long,
    val analysis: TrackAnalysis?,
    val lyrics: String?,
)

object CrossmixPlanner {
    /** the hand-over may start this long before the outgoing song's end, and no earlier */
    const val MaxLeadMs = 30_000L
    /** the next song may be entered this far in, and no further */
    const val MaxEntryMs = 120_000L

    private const val MaxSpeedChange = 0.06f

    fun plan(
        outgoing: CrossmixTrackInput,
        incoming: CrossmixTrackInput,
        recentStyles: List<CrossmixStyle> = emptyList(),
    ): CrossmixPlan {
        val out = outgoing.analysis
        val inc = incoming.analysis
        val random = Random((outgoing.mediaId + "→" + incoming.mediaId).hashCode())
        val naturalEnd = naturalEnd(outgoing)
        val windowStart = (outgoing.durationMs - MaxLeadMs).coerceAtLeast(0L)

        if (out == null || !out.isBeatReliable) {
            return smoothFade(outgoing, incoming, naturalEnd, random)
        }
        val outGrid = out.localGrid((naturalEnd - 12_000L).coerceAtLeast(0L)) ?: return smoothFade(outgoing, incoming, naturalEnd, random)

        val tempo = tempoFactor(out, inc)
        val keyDistance = Harmony.camelotDistance(out.key, inc?.key)?.takeIf {
            out.keyConfidence > 0.3f && (inc?.keyConfidence ?: 0f) > 0.3f
        }
        val outLines = parseLines(outgoing.lyrics)
        val inLines = parseLines(incoming.lyrics)
        val bars = out.barStartsMs()

        data class Candidate(val plan: CrossmixPlan, val score: Float)
        val candidates = ArrayList<Candidate>()

        for (style in CrossmixStyle.entries) {
            if (style.needsBeatMatch && tempo == null) continue
            val styleFit = styleFit(style, out, inc, tempo != null, keyDistance, outLines, naturalEnd)
            if (styleFit <= -5f) continue
            for (length in style.lengths) {
                val lengthMs = length * outGrid.periodMs
                // the outgoing song must have handed over (tails aside) before it runs out, and the
                // whole thing must start no earlier than 30 s before the end
                val latestEnd = naturalEnd - 150L
                val earliestEnd = (windowStart + lengthMs).toLong()
                if (earliestEnd > latestEnd) continue
                val ends = bars.filter { it in earliestEnd..latestEnd }
                    .ifEmpty { listOf(outGrid.snap(latestEnd).let { if (it > latestEnd) (it - outGrid.periodMs).toLong() else it }) }
                    .filter { it >= earliestEnd }
                for (end in ends) {
                    val start = (end - lengthMs).toLong()
                    if (start < windowStart) continue
                    val exitScore = exitScore(out, outGrid, end, naturalEnd, outLines, style)
                    val cue = pickCue(style, out, inc, end, length, outGrid.periodMs, tempo, inLines)
                    // overlapping styles lock the beat whenever the tempos allow; hand-over
                    // styles only need the next song's downbeat on the outgoing one's
                    val match = if (tempo != null && (style.needsBeatMatch || !style.entersAtEnd)) {
                        beatMatch(inc!!, cue.timeMs, tempo, outGrid.periodMs)
                    } else {
                        null
                    }
                    if (style.needsBeatMatch && match == null) continue
                    val inBeat = match?.inBeatMs ?: outGrid.periodMs
                    val entryBeat = if (style.entersAtEnd) length.toFloat() else 0f
                    // up to a bar of silent run-up before the next song is heard, so its beat is
                    // locked before anyone can hear it
                    val preRoll = min(4f, (cue.timeMs / inBeat).toFloat()).coerceAtLeast(0f)
                    val startBeat = entryBeat - preRoll
                    val plan = CrossmixPlan(
                        style = style,
                        variation = random.nextInt(4),
                        lengthBeats = length,
                        outStartMs = start,
                        outBeatMs = outGrid.periodMs,
                        inCueMs = cue.timeMs,
                        entryBeat = entryBeat,
                        inMediaPerBeat = inBeat,
                        inSpeed = match?.speed ?: 1f,
                        beatMatched = match != null,
                        inStartBeat = startBeat,
                    )
                    val lengthFit = lengthFit(style, length, out, inc)
                    candidates += Candidate(plan, styleFit + exitScore + cue.score + lengthFit)
                }
            }
        }

        if (candidates.isEmpty()) return smoothFade(outgoing, incoming, naturalEnd, random)

        // the best handful, weighted toward the top: musical first, then a little variety, and
        // never the same style as the last couple of transitions
        val ranked = candidates
            .filter { it.plan.style !in recentStyles.takeLast(2) }
            .ifEmpty { candidates }
            .sortedByDescending { it.score }
        val bestPerStyle = ranked.distinctBy { it.plan.style }.take(4)
        val top = bestPerStyle.first().score
        val weights = bestPerStyle.map { exp((it.score - top) * 2.5f) }
        var pick = random.nextFloat() * weights.sum()
        for ((index, weight) in weights.withIndex()) {
            pick -= weight
            if (pick <= 0f) return bestPerStyle[index].plan
        }
        return bestPerStyle.first().plan
    }

    /** how the next song's beats count against this one's: 1 same, 2 its beats are twice as fast */
    private class Tempo(val factor: Float, val inBeatMs: Double)

    private data class Match(val inBeatMs: Double, val speed: Float)

    /**
     * whether the two tempos can be laid on each other at all: the same, or near enough to nudge
     * (6% at most, so nobody hears the stretch), allowing for half and double time
     */
    private fun tempoFactor(out: TrackAnalysis, inc: TrackAnalysis?): Tempo? {
        if (inc == null || !inc.isBeatReliable) return null
        val outBpm = out.bpm ?: return null
        val inBpm = inc.bpm ?: return null
        for (factor in floatArrayOf(1f, 2f, 0.5f)) {
            if (abs(outBpm / (inBpm * factor) - 1f) <= MaxSpeedChange) {
                return Tempo(factor, 60_000.0 / (inBpm * factor))
            }
        }
        return null
    }

    /**
     * the exact beat length of the next song where it's entered, from the beats fitted around
     * the cue rather than the song-wide tempo (an error of a fraction of a percent there would
     * drift a long blend off the beat), and the speed that lays it on the outgoing beat. within
     * 0.3% nothing is stretched: the renderer's delay line keeps the two locked, which is cleaner
     * than any time-stretch.
     */
    private fun beatMatch(inc: TrackAnalysis, cueMs: Long, tempo: Tempo, outBeatMs: Double): Match? {
        val local = inc.localGrid(cueMs + 4_000L)?.periodMs ?: return null
        // the grid counts the song's own beats; half or double time counts them in pairs or halves
        val inBeat = local / tempo.factor
        val speed = (inBeat / outBeatMs).toFloat()
        if (abs(speed - 1f) > MaxSpeedChange) return null
        return Match(inBeat, if (abs(speed - 1f) < 0.003f) 1f else speed)
    }

    /** how well a style suits these two songs; mostly beat and energy, a little lyrics */
    private fun styleFit(
        style: CrossmixStyle,
        out: TrackAnalysis,
        inc: TrackAnalysis?,
        beatMatchable: Boolean,
        keyDistance: Int?,
        outLines: List<Line>,
        naturalEnd: Long,
    ): Float {
        val outEnergy = out.energyAround(naturalEnd - 10_000L, 16_000L)
        val inEnergy = inc?.vibe?.energy ?: 0.5f
        val delta = inEnergy - out.vibe.energy
        val dance = min(out.vibe.danceability, inc?.vibe?.danceability ?: 0.4f)
        val calm = 1f - max(out.vibe.energy, inEnergy)
        val clash = keyDistance != null && keyDistance >= 3
        val harmonic = keyDistance != null && keyDistance <= 1
        val vocalsNearEnd = outLines.any { it.end > naturalEnd - 8_000L }
        val outro = outEnergy < 0.45f
        val overlaps = !style.entersAtEnd

        var score = when (style) {
            CrossmixStyle.SilkBlend -> 0.6f + calm * 0.6f + (if (harmonic) 0.3f else 0f)
            CrossmixStyle.BassSwap -> 0.4f + dance * 1.4f + out.vibe.bassWeight * 0.4f
            CrossmixStyle.FilterSweep -> 0.5f + dance * 0.4f + (if (clash) 0.3f else 0f)
            CrossmixStyle.HighPassLift -> 0.3f + max(0f, delta) * 1.4f + dance * 0.3f
            CrossmixStyle.EchoOut -> 0.4f + (if (!beatMatchable) 0.6f else 0f) + max(0f, -delta) * 0.6f
            CrossmixStyle.DubThrow -> 0.2f + out.vibe.bassWeight * 0.6f + (1f - out.vibe.brightness) * 0.4f
            CrossmixStyle.ReverbWash -> 0.3f + calm * 1.2f + (if (outro) 0.4f else 0f)
            CrossmixStyle.Backspin -> 0.2f + out.vibe.energy * 0.7f + (if (!beatMatchable) 0.5f else 0f) + max(0f, delta) * 0.3f
            CrossmixStyle.RewindWash -> 0.15f + out.vibe.energy * 0.5f + (if (!beatMatchable) 0.4f else 0f)
            CrossmixStyle.TapeStop -> 0.2f + (if (!beatMatchable) 0.5f else 0f) + max(0f, -delta) * 0.5f + out.vibe.energy * 0.3f
            CrossmixStyle.PowerDown -> 0.15f + max(0f, -delta) * 0.9f + (if (out.vibe.minor) 0.2f else 0f)
            CrossmixStyle.LoopRoll -> 0.2f + dance * 0.9f + max(0f, delta) * 0.6f
            CrossmixStyle.StutterGate -> 0.1f + dance * 1.1f + out.vibe.brightness * 0.3f
            CrossmixStyle.RiserBuild -> 0.1f + max(0f, delta) * 1.6f + dance * 0.4f
            CrossmixStyle.Downlifter -> 0.15f + max(0f, -delta) * 1.3f
            CrossmixStyle.PhaserGlide -> 0.3f + out.vibe.brightness * 0.4f + calm * 0.2f
            CrossmixStyle.LofiMelt -> 0.2f + (1f - out.vibe.brightness) * 0.5f + calm * 0.6f
            CrossmixStyle.SpatialOrbit -> 0.35f + calm * 0.7f + (if (outro) 0.2f else 0f)
            CrossmixStyle.DopplerPass -> 0.3f + abs(delta) * 0.4f + calm * 0.2f
            CrossmixStyle.VocalHandoff -> 0.1f + (if (vocalsNearEnd) 0.9f else -0.6f) + (if (!beatMatchable) 0.3f else 0f)
            CrossmixStyle.DropCut -> 0.1f + dance * 0.9f + inEnergy * 0.5f + max(0f, delta) * 0.4f
            CrossmixStyle.GateFade -> 0.2f + dance * 0.8f
            CrossmixStyle.HarmonicGlide -> 0.2f + (if (harmonic) 1.1f else -0.8f) + dance * 0.3f
            CrossmixStyle.BreakdownBridge -> 0.3f + (if (clash) 0.7f else 0f) + calm * 0.2f
            CrossmixStyle.ThunderDrop -> 0.1f + max(0f, delta) * 1.3f + out.vibe.bassWeight * 0.4f
        }
        // two songs in clashing keys shouldn't sit on top of each other for long
        if (overlaps && clash && style != CrossmixStyle.BreakdownBridge && style != CrossmixStyle.FilterSweep) score -= 0.6f
        // a quiet outro wants a gentle hand-over, not a stunt
        if (outro && style.entersAtEnd && style != CrossmixStyle.VocalHandoff && style != CrossmixStyle.EchoOut) score -= 0.3f
        return score
    }

    /** where the outgoing song lets go: a phrase boundary, after a sung line, as late as sensible */
    private fun exitScore(
        out: TrackAnalysis,
        grid: LocalGrid,
        endMs: Long,
        naturalEnd: Long,
        lines: List<Line>,
        style: CrossmixStyle,
    ): Float {
        val bars = out.barStartsMs()
        val barIndex = bars.indexOfFirst { abs(it - endMs) < grid.periodMs / 3 }
        val phrase = when {
            barIndex < 0 -> 0f
            barIndex % 8 == 0 -> 1f
            barIndex % 4 == 0 -> 0.6f
            barIndex % 2 == 0 -> 0.25f
            else -> 0f
        }
        val skipped = (naturalEnd - endMs).coerceAtLeast(0L) / 1_000f
        // a few seconds of outro left is natural; much more is cutting the song short
        val lateness = -max(0f, skipped - 6f) * 0.045f
        // the music dropping away just after is a natural seam
        val drop = (out.energyAround(endMs - 3_000L, 4_000L) - out.energyAround(endMs + 3_000L, 4_000L)).coerceIn(-0.3f, 0.5f)
        // lyrics weigh in lightly: don't hand over in the middle of a sung line
        val midLine = lines.any { endMs > it.start + 300L && endMs < it.end - 200L }
        val afterLine = lines.any { endMs - it.end in 0L..2_500L }
        var lyric = (if (midLine) -0.35f else 0f) + (if (afterLine) 0.15f else 0f)
        if (style == CrossmixStyle.VocalHandoff) lyric *= 2f
        return phrase * 0.9f + lateness + drop * 0.6f + lyric
    }

    private data class Cue(val timeMs: Long, val score: Float)

    /**
     * where to enter the next song, at most two minutes in: on a phrase start, at an energy that
     * suits the hand-over (a drop for the drop styles, a calm intro for the long blends), without a
     * vocal stepping on the outgoing song, and skipping as little of it as will do
     */
    private fun pickCue(
        style: CrossmixStyle,
        out: TrackAnalysis,
        inc: TrackAnalysis?,
        exitMs: Long,
        lengthBeats: Int,
        outBeatMs: Double,
        tempo: Tempo?,
        inLines: List<Line>,
    ): Cue {
        if (inc == null) return Cue(0L, 0f)
        val bars = inc.barStartsMs()
        val start = inc.leadingSilenceEndMs
        val candidates = (listOf(start) + bars.filter { it in start..MaxEntryMs }.toList()).distinct()
        val outEnergy = out.energyAround(exitMs - 4_000L)
        val overlapMs = if (style.entersAtEnd) 0.0 else lengthBeats * (tempo?.inBeatMs ?: outBeatMs)
        // a blend that rides on the next song's beat needs that beat at its cue
        val needsGrid = !style.entersAtEnd && tempo != null
        var best = Cue(start, -10f)
        for (cue in candidates) {
            val barIndex = bars.indexOfFirst { abs(it - cue) < 60L }
            val phrase = when {
                cue == start -> 0.7f
                barIndex < 0 -> 0f
                barIndex % 8 == 0 -> 1f
                barIndex % 4 == 0 -> 0.5f
                else -> 0.1f
            }
            val before = inc.energyAround(cue - 3_000L, 4_000L)
            val after = inc.energyAround(cue + 3_000L, 4_000L)
            val energyFit = if (style.entersAtEnd) {
                // a drop: the next song should hit as it comes in, at least as hard as this one ends
                (after - before).coerceIn(-0.2f, 0.5f) + (1f - abs(after - max(outEnergy, 0.6f))) * 0.5f
            } else {
                // a blend: the next song rises under this one, so a quieter start sits better
                (1f - abs(after - outEnergy * 0.8f)) * 0.6f
            }
            val skipPenalty = cue / 60_000f * 0.45f
            val vocalClash = !style.entersAtEnd && inLines.any { it.start in cue..(cue + overlapMs.toLong()) }
            val gridless = needsGrid && inc.localGrid(cue + 4_000L) == null
            val score = phrase * 0.8f + energyFit - skipPenalty - (if (vocalClash) 0.3f else 0f) - (if (gridless) 2f else 0f)
            if (score > best.score) best = Cue(cue, score)
        }
        return best
    }

    private fun lengthFit(style: CrossmixStyle, length: Int, out: TrackAnalysis, inc: TrackAnalysis?): Float {
        val calm = 1f - max(out.vibe.energy, inc?.vibe?.energy ?: 0.5f)
        val longest = style.lengths.last().toFloat()
        // calm songs breathe in long blends, busy ones in short ones
        return (length / longest - 0.5f) * (calm - 0.4f) * 0.6f
    }

    /**
     * no reliable beat to work with: a smooth equal-power blend that finishes before the song
     * runs out, starting after the last sung line where that fits
     */
    private fun smoothFade(
        outgoing: CrossmixTrackInput,
        incoming: CrossmixTrackInput,
        naturalEnd: Long,
        random: Random,
    ): CrossmixPlan {
        val lines = parseLines(outgoing.lyrics)
        val fadeMs = 9_000L
        val latestStart = (naturalEnd - fadeMs).coerceAtLeast(0L)
        val earliestStart = (outgoing.durationMs - MaxLeadMs).coerceAtLeast(0L)
        val afterLyrics = lines.lastOrNull { it.end in earliestStart..latestStart }?.end
        val start = (afterLyrics ?: latestStart).coerceIn(earliestStart, latestStart.coerceAtLeast(earliestStart))
        val beatMs = fadeMs / 16.0
        val cue = incoming.analysis?.leadingSilenceEndMs?.coerceAtMost(MaxEntryMs) ?: 0L
        return CrossmixPlan(
            style = CrossmixStyle.SilkBlend,
            variation = random.nextInt(4),
            lengthBeats = 16,
            outStartMs = start,
            outBeatMs = beatMs,
            inCueMs = cue,
            entryBeat = 0f,
            inMediaPerBeat = beatMs,
            inSpeed = 1f,
            beatMatched = false,
            inStartBeat = 0f,
        )
    }

    /** where the song really ends: before any trailing silence */
    private fun naturalEnd(track: CrossmixTrackInput): Long {
        val analysis = track.analysis
        val silence = analysis?.trailingSilenceStartMs?.takeIf { analysis.complete && it > track.durationMs / 2 }
        return min(track.durationMs, silence ?: track.durationMs)
    }

    private data class Line(val start: Long, val end: Long)

    private fun parseLines(lyrics: String?): List<Line> {
        if (lyrics.isNullOrBlank()) return emptyList()
        val entries = runCatching { LyricsUtils.parseLyrics(lyrics) }.getOrNull().orEmpty()
            .filter { it.text.isNotBlank() }
        return entries.mapIndexed { index, entry ->
            // a line lasts until the next one, or ~4 s for the last
            val next = entries.getOrNull(index + 1)?.time ?: (entry.time + 4_000L)
            Line(entry.time, min(next, entry.time + 8_000L))
        }
    }
}
