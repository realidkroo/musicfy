package com.example.musicfy.crossmix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every knob the Crossmix renderer has, at one moment. The defaults are "the track, untouched".
 * A style fills one of these for each side of the transition from the beat position alone, so the
 * same style always sounds the same and nothing depends on when the main thread gets around to it.
 */
class FxParams {
    /** level of the track itself; echo and reverb returns have their own */
    var gain = 1f
    var lowCutHz = 0f
    var highCutHz = OpenHz
    var resonance = 0.707f
    var lowBand = 1f
    var midBand = 1f
    var highBand = 1f
    var echoSend = 0f
    var echoBeats = 0.75f
    var echoFeedback = 0.45f
    /** 0 dark (dub), 1 bright repeats */
    var echoTone = 0.6f
    var echoPingPong = false
    var reverbSend = 0f
    var reverbSize = 0.84f
    /** 1 holds the reverb tail forever (a freeze) */
    var reverbFreeze = 0f
    var gateDepth = 0f
    /** gate steps per beat: 2 eighths, 4 sixteenths */
    var gateDivision = 4f
    var gateDuty = 0.5f
    var flangerMix = 0f
    var flangerRateHz = 0.3f
    var crush = 0f
    var pan = 0f
    var width = 1f
    /** 0..1 how far the sound circles the listener; one turn per [orbitBeats] */
    var orbit = 0f
    var orbitBeats = 8f
    var noiseLevel = 0f
    var noiseHz = 1_000f
    var deck = Deck.Live
    /** spin-back and tape-stop: 0..1 through the move. loop roll: the loop length in beats */
    var deckAmount = 0f
    /** loop roll: the beat (in transition beats) the current loop started on */
    var deckMark = 0f

    fun reset() {
        gain = 1f; lowCutHz = 0f; highCutHz = OpenHz; resonance = 0.707f
        lowBand = 1f; midBand = 1f; highBand = 1f
        echoSend = 0f; echoBeats = 0.75f; echoFeedback = 0.45f; echoTone = 0.6f; echoPingPong = false
        reverbSend = 0f; reverbSize = 0.84f; reverbFreeze = 0f
        gateDepth = 0f; gateDivision = 4f; gateDuty = 0.5f
        flangerMix = 0f; flangerRateHz = 0.3f; crush = 0f
        pan = 0f; width = 1f; orbit = 0f; orbitBeats = 8f
        noiseLevel = 0f; noiseHz = 1_000f
        deck = Deck.Live; deckAmount = 0f; deckMark = 0f
    }

    enum class Deck { Live, Loop, SpinBack, TapeStop }

    companion object {
        const val OpenHz = 20_000f
    }
}

/** which side of the transition a renderer is playing */
enum class CrossmixRole { Outgoing, Incoming }

/**
 * The transitions Crossmix can play, each a small piece of DJ technique. [needsBeatMatch] styles
 * lay the two tracks over each other on the beat; the others hand over on a downbeat and work with
 * any two tempos. [entersAtEnd]: the next song comes in once the outgoing one is gone (a drop),
 * rather than rising under it.
 */
enum class CrossmixStyle(
    val label: String,
    val needsBeatMatch: Boolean,
    val entersAtEnd: Boolean,
    /** lengths that suit it, in beats */
    val lengths: IntArray,
    /** beats the outgoing side keeps ringing after the hand-over (echo and reverb tails) */
    val tailBeats: Int = 0,
) {
    SilkBlend("Silk blend", false, false, intArrayOf(16, 24, 32)),
    BassSwap("Bass swap", true, false, intArrayOf(32, 48, 64)),
    FilterSweep("Filter sweep", false, false, intArrayOf(16, 24, 32)),
    HighPassLift("High-pass lift", false, true, intArrayOf(8, 16)),
    EchoOut("Echo out", false, true, intArrayOf(4, 8), tailBeats = 12),
    DubThrow("Dub throw", false, true, intArrayOf(8), tailBeats = 16),
    ReverbWash("Reverb wash", false, false, intArrayOf(16, 24), tailBeats = 16),
    Backspin("Backspin", false, true, intArrayOf(4, 8)),
    RewindWash("Rewind wash", false, true, intArrayOf(8), tailBeats = 12),
    TapeStop("Tape stop", false, true, intArrayOf(4, 8)),
    PowerDown("Power down", false, true, intArrayOf(8), tailBeats = 8),
    LoopRoll("Loop roll", false, true, intArrayOf(8, 16)),
    StutterGate("Stutter gate", true, false, intArrayOf(16, 32)),
    RiserBuild("Riser build", false, true, intArrayOf(16, 32), tailBeats = 4),
    Downlifter("Downlifter", false, false, intArrayOf(16, 24), tailBeats = 8),
    PhaserGlide("Phaser glide", false, false, intArrayOf(16, 32)),
    LofiMelt("Lo-fi melt", false, false, intArrayOf(16, 24)),
    SpatialOrbit("Spatial orbit", false, false, intArrayOf(16, 32), tailBeats = 8),
    DopplerPass("Doppler pass", false, false, intArrayOf(8, 16)),
    VocalHandoff("Vocal hand-off", false, true, intArrayOf(4, 8), tailBeats = 8),
    DropCut("Drop cut", true, true, intArrayOf(8, 16)),
    GateFade("Gate fade", true, false, intArrayOf(16, 32)),
    HarmonicGlide("Harmonic glide", true, false, intArrayOf(32, 48, 64)),
    BreakdownBridge("Breakdown bridge", false, false, intArrayOf(16, 24), tailBeats = 4),
    ThunderDrop("Thunder drop", false, true, intArrayOf(8, 16), tailBeats = 12);

    /**
     * fills [out] for one side at transition beat [beat] of a [length]-beat transition. beat 0 is
     * where the transition starts; [length] is where the outgoing song has fully handed over.
     * [variation] (0..3) picks between close variants so a style doesn't sound identical each time.
     */
    fun evaluate(role: CrossmixRole, beat: Float, length: Float, variation: Int, out: FxParams) {
        out.reset()
        val p = (beat / length).coerceIn(0f, 1f)
        val outgoing = role == CrossmixRole.Outgoing
        when (this) {
            SilkBlend -> if (outgoing) {
                out.gain = fadeOut(p)
                out.highCutHz = sweepDown(ramp(p, 0.45f, 1f), OpenHz, 5_500f)
            } else {
                out.gain = fadeIn(p)
                out.lowCutHz = sweepDown(ramp(p, 0f, 0.6f), 320f, 0f)
            }

            BassSwap -> {
                // the classic three-band mix: the newcomer's bass waits for the phrase change
                val swap = length / 2f
                val swapMix = ramp(beat, swap - 0.5f, swap + 0.5f)
                if (outgoing) {
                    out.lowBand = 1f - swapMix
                    out.highBand = 1f - 0.35f * ramp(p, 0.5f, 1f)
                    out.gain = fadeOut(ramp(p, 0.75f, 1f))
                } else {
                    out.gain = fadeIn(ramp(p, 0f, 0.25f))
                    out.lowBand = swapMix
                    out.midBand = 0.55f + 0.45f * ramp(p, 0.25f, 0.5f)
                }
            }

            FilterSweep -> if (outgoing) {
                out.highCutHz = sweepDown(smooth(p), OpenHz, 260f)
                out.resonance = 0.707f + 1.1f * sin(PI.toFloat() * p)
                out.gain = fadeOut(ramp(p, 0.7f, 1f))
            } else {
                out.lowCutHz = sweepDown(smooth(p), 1_400f, 0f)
                out.resonance = 0.707f + 0.6f * sin(PI.toFloat() * p)
                out.gain = fadeIn(ramp(p, 0f, 0.5f))
            }

            HighPassLift -> if (outgoing) {
                out.lowCutHz = sweepUp(ramp(p, 0f, 1f).pow(1.4f), 20f, 1_900f)
                out.resonance = 0.9f
                out.reverbSend = 0.25f * p
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.25f)
            }

            EchoOut -> if (outgoing) {
                out.echoBeats = if (variation % 2 == 0) 0.75f else 0.5f
                out.echoSend = ramp(beat, length - 4f, length - 0.5f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.5f + 0.18f * ramp(beat, length - 4f, length)
                out.echoTone = 0.55f
                out.highCutHz = sweepDown(ramp(beat, length - 2f, length), OpenHz, 7_000f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.5f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 2f), 420f, 0f)
            }

            DubThrow -> if (outgoing) {
                out.echoBeats = 0.5f
                out.echoPingPong = true
                out.echoSend = ramp(beat, length - 6f, length - 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.72f
                out.echoTone = 0.25f
                out.reverbSend = 0.18f * ramp(beat, length - 4f, length)
                out.highCutHz = sweepDown(ramp(beat, length - 4f, length), OpenHz, 1_800f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 1f)
                out.highCutHz = sweepUp(ramp(beat, length, length + 4f), 900f, OpenHz)
            }

            ReverbWash -> if (outgoing) {
                out.reverbSend = 0.95f * smooth(p)
                out.reverbSize = 0.9f
                out.reverbFreeze = ramp(p, 0.8f, 1f) * 0.6f
                out.highCutHz = sweepDown(ramp(p, 0.3f, 1f), OpenHz, 2_200f)
                out.gain = fadeOut(ramp(p, 0.15f, 0.9f))
                out.reverbSend *= cutAt(beat, length + 0.01f).coerceAtLeast(0f)
            } else {
                out.gain = fadeIn(ramp(p, 0.35f, 1f))
                out.highCutHz = sweepUp(ramp(p, 0.35f, 1f), 1_200f, OpenHz)
                out.reverbSend = 0.3f * (1f - ramp(p, 0.6f, 1f))
            }

            Backspin -> if (outgoing) {
                val spinBeats = if (variation % 2 == 0) 2f else 1.5f
                val t = ramp(beat, length - spinBeats, length)
                if (t > 0f) { out.deck = FxParams.Deck.SpinBack; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 2_400f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.12f)
            }

            RewindWash -> if (outgoing) {
                val t = ramp(beat, length - 2f, length)
                if (t > 0f) { out.deck = FxParams.Deck.SpinBack; out.deckAmount = t }
                out.reverbSend = 0.75f * ramp(beat, length - 3f, length - 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.reverbSize = 0.92f
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.25f)
                out.highCutHz = sweepUp(ramp(beat, length, length + 4f), 700f, OpenHz)
            }

            TapeStop -> if (outgoing) {
                val stopBeats = if (variation % 2 == 0) 2f else 3f
                val t = ramp(beat, length - stopBeats, length)
                if (t > 0f) { out.deck = FxParams.Deck.TapeStop; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 1_600f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.12f)
            }

            PowerDown -> if (outgoing) {
                val t = ramp(beat, length - 4f, length)
                if (t > 0f) { out.deck = FxParams.Deck.TapeStop; out.deckAmount = t }
                out.highCutHz = sweepDown(t, OpenHz, 900f)
                out.reverbSend = 0.5f * t * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 2f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 4f), 650f, 0f)
            }

            LoopRoll -> if (outgoing) {
                // a beat, then half, quarter and eighth beats: the roll speeds up into the drop
                val rollStart = length - 4f
                if (beat >= rollStart) {
                    val step = (beat - rollStart).toInt().coerceIn(0, 3)
                    out.deck = FxParams.Deck.Loop
                    out.deckAmount = 1f / (1 shl step)
                    out.deckMark = rollStart + step
                }
                out.lowCutHz = sweepUp(ramp(beat, rollStart - 4f, length), 20f, 1_300f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.06f)
            }

            StutterGate -> if (outgoing) {
                out.gateDivision = if (variation % 2 == 0) 4f else 2f
                out.gateDepth = 0.9f * smooth(ramp(p, 0.1f, 0.8f))
                out.gateDuty = 0.55f - 0.3f * p
                out.echoSend = 0.18f * ramp(p, 0.5f, 1f)
                out.echoBeats = 0.25f
                out.gain = fadeOut(ramp(p, 0.75f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.35f, 0.85f))
                out.lowBand = ramp(p, 0.75f, 0.8f)
            }

            RiserBuild -> if (outgoing) {
                out.noiseLevel = 0.3f * ramp(p, 0.2f, 1f).pow(1.6f) * cutAt(beat, length)
                out.noiseHz = sweepUp(ramp(p, 0.2f, 1f), 350f, 9_000f)
                out.lowCutHz = sweepUp(ramp(p, 0.3f, 1f), 20f, 850f)
                out.reverbSend = 0.35f * ramp(p, 0.4f, 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
                out.lowBand = 1f + 0.18f * (1f - ramp(beat, length, length + 1f))
            }

            Downlifter -> if (outgoing) {
                out.noiseLevel = 0.22f * (1f - ramp(p, 0.05f, 0.6f)) * ramp(p, 0f, 0.05f)
                out.noiseHz = sweepDown(ramp(p, 0f, 0.6f), 7_000f, 280f)
                out.highCutHz = sweepDown(ramp(p, 0f, 0.8f), OpenHz, 600f)
                out.reverbSend = 0.45f * ramp(p, 0.1f, 0.7f)
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.3f, 1f))
                out.highCutHz = sweepUp(ramp(p, 0.3f, 1f), 1_800f, OpenHz)
            }

            PhaserGlide -> if (outgoing) {
                out.flangerMix = 0.65f * smooth(ramp(p, 0f, 0.6f))
                out.flangerRateHz = 0.2f + 0.3f * p
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.flangerMix = 0.5f * (1f - ramp(p, 0.4f, 1f))
                out.flangerRateHz = 0.35f
                out.gain = fadeIn(ramp(p, 0f, 0.8f))
            }

            LofiMelt -> if (outgoing) {
                out.crush = 0.85f * smooth(ramp(p, 0f, 0.8f))
                out.highCutHz = sweepDown(ramp(p, 0f, 1f), OpenHz, 2_800f)
                out.width = 1f - 0.6f * p
                out.gain = fadeOut(ramp(p, 0.25f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0.1f, 0.9f))
                out.highCutHz = sweepUp(ramp(p, 0.1f, 1f), 2_800f, OpenHz)
                out.crush = 0.35f * (1f - ramp(p, 0.2f, 0.7f))
            }

            SpatialOrbit -> if (outgoing) {
                out.orbit = smooth(ramp(p, 0f, 0.8f))
                out.orbitBeats = if (variation % 2 == 0) 8f else 16f
                out.width = 1f + 0.6f * p
                out.reverbSend = 0.55f * ramp(p, 0.2f, 1f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = fadeOut(ramp(p, 0.3f, 1f))
            } else {
                // arrives from the other side and settles in the middle, narrow to wide
                out.pan = -0.75f * (1f - smooth(ramp(p, 0.2f, 1f)))
                out.width = 0.3f + 0.7f * ramp(p, 0.3f, 1f)
                out.gain = fadeIn(ramp(p, 0.15f, 0.9f))
            }

            DopplerPass -> if (outgoing) {
                out.pan = 0.85f * smooth(p)
                out.highCutHz = sweepDown(ramp(p, 0.3f, 1f), OpenHz, 1_500f)
                out.gain = fadeOut(ramp(p, 0.2f, 1f))
            } else {
                out.pan = -0.85f * (1f - smooth(p))
                out.highCutHz = sweepUp(ramp(p, 0f, 0.7f), 1_500f, OpenHz)
                out.gain = fadeIn(ramp(p, 0f, 0.8f))
            }

            VocalHandoff -> if (outgoing) {
                // the last line rings out into a room while the song itself steps aside
                out.reverbSend = 0.7f * ramp(beat, length - 1f, length) * cutAt(beat, length + 1f).coerceAtLeast(0f)
                out.reverbSize = 0.88f
                out.gain = cutAt(beat, length + 0.5f)
                out.highCutHz = sweepDown(ramp(beat, length - 1f, length + 0.5f), OpenHz, 3_000f)
            } else {
                out.gain = entry(beat, length, 1f)
                out.lowCutHz = sweepDown(ramp(beat, length, length + 2f), 300f, 0f)
            }

            DropCut -> if (outgoing) {
                out.lowCutHz = sweepUp(ramp(beat, length - 2f, length), 20f, 650f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
            }

            GateFade -> if (outgoing) {
                out.gateDivision = 2f
                out.gateDepth = smooth(ramp(p, 0f, 0.4f))
                out.gateDuty = 0.9f - 0.75f * p
                out.gain = fadeOut(ramp(p, 0.6f, 1f))
            } else {
                out.gain = fadeIn(ramp(p, 0f, 0.6f))
                out.lowBand = ramp(beat, length / 2f - 0.5f, length / 2f + 0.5f)
            }

            HarmonicGlide -> if (outgoing) {
                out.midBand = 1f - 0.4f * ramp(p, 0.3f, 0.7f)
                out.highCutHz = sweepDown(ramp(p, 0.4f, 1f), OpenHz, 8_000f)
                out.lowBand = 1f - ramp(p, 0.55f, 0.65f)
                out.gain = fadeOut(ramp(p, 0.5f, 1f))
            } else {
                out.midBand = 0.5f + 0.5f * ramp(p, 0.3f, 0.7f)
                out.lowBand = ramp(p, 0.55f, 0.65f)
                out.gain = fadeIn(ramp(p, 0f, 0.5f))
            }

            BreakdownBridge -> if (outgoing) {
                out.highCutHz = sweepDown(ramp(p, 0f, 0.35f), OpenHz, 480f)
                out.resonance = 1.1f
                out.reverbSend = 0.3f * ramp(p, 0.2f, 0.7f)
                out.gain = fadeOut(ramp(p, 0.4f, 0.85f))
            } else {
                out.highCutHz = sweepUp(ramp(p, 0.6f, 1f), 480f, OpenHz)
                out.resonance = 1.1f - 0.4f * ramp(p, 0.6f, 1f)
                out.gain = fadeIn(ramp(p, 0.15f, 0.55f))
            }

            ThunderDrop -> if (outgoing) {
                out.lowBand = 1f - ramp(beat, length - 2f, length - 1f)
                out.echoBeats = 0.75f
                out.echoSend = 0.7f * ramp(beat, length - 2f, length - 0.5f) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.echoFeedback = 0.55f
                out.reverbSend = 0.45f * ramp(beat, length - 2f, length) * cutAt(beat, length + 0.01f).coerceAtLeast(0f)
                out.gain = cutAt(beat, length)
            } else {
                out.gain = entry(beat, length, 0.03f)
                out.lowBand = 1f + 0.25f * (1f - ramp(beat, length, length + 1f))
            }
        }
        if (outgoing) {
            // a beat after the hand-over nothing new goes into the echoes and the room: only the
            // tails already in them carry on, and they die away on their own
            val feed = cutAt(beat, length + 1f)
            out.echoSend *= feed
            out.reverbSend *= feed
            out.noiseLevel *= feed
        } else if (beat < 0f) {
            // before the incoming side has begun it makes no sound at all, effects included
            out.gain = 0f
            out.echoSend = 0f
            out.reverbSend = 0f
            out.noiseLevel = 0f
        }
    }
}

/** a cut sitting here is open: nothing filtered */
private const val OpenHz = FxParams.OpenHz

// a 0..1 ramp from a to b
internal fun ramp(x: Float, a: Float, b: Float): Float = if (b <= a) (if (x >= b) 1f else 0f) else ((x - a) / (b - a)).coerceIn(0f, 1f)

internal fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** equal-power fades: two uncorrelated songs at these levels add up to the same loudness */
internal fun fadeOut(x: Float): Float = cos(x.coerceIn(0f, 1f) * PI.toFloat() / 2f)
internal fun fadeIn(x: Float): Float = sin(x.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/** full until [at], gone 1/16 of a beat later: a cut that lands on the beat but never clicks */
internal fun cutAt(beat: Float, at: Float): Float = 1f - ramp(beat, at - 0.0625f, at)

/** silent until [at], then in over [overBeats] (as short as a few ms for a drop) */
internal fun entry(beat: Float, at: Float, overBeats: Float): Float = fadeIn(ramp(beat, at, at + overBeats))

/** exponential sweeps, the way ears hear filter movement */
internal fun sweepDown(t: Float, from: Float, to: Float): Float = sweep(t, from, to)
internal fun sweepUp(t: Float, from: Float, to: Float): Float = sweep(t, from, to)

private fun sweep(t: Float, from: Float, to: Float): Float {
    if (t <= 0f) return from
    if (t >= 1f) return to
    val a = from.coerceAtLeast(20f)
    val b = to.coerceAtLeast(20f)
    val value = a * (b / a).pow(t)
    // a cut that sweeps to "off" (0 Hz) ends exactly there instead of hovering at 20 Hz
    return if (to <= 0f && t >= 0.98f) 0f else value
}
