package com.example.musicfy.crossmix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.musicfy.playback.audio.CrossmixTransitionAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class CrossmixTest {

    // -- analysis ---------------------------------------------------------------------------

    /** a synthetic track: a kick on every beat (harder on the one), off-beat hats, a soft pad */
    private fun synthTrack(bpm: Double, seconds: Int, kickOffsetMs: Double = 250.0): FloatArray {
        val rate = StreamingTrackAnalyzer.SampleRate
        val out = FloatArray(rate * seconds)
        val beat = 60.0 / bpm
        val random = Random(7)
        var t = kickOffsetMs / 1_000.0
        var index = 0
        while (t < seconds) {
            val start = (t * rate).toInt()
            val accent = if (index % 4 == 0) 1.0f else 0.55f
            for (i in 0 until (0.12 * rate).toInt()) {
                val n = start + i
                if (n >= out.size) break
                val env = exp(-i / (0.03 * rate)).toFloat()
                out[n] += accent * 0.8f * env * sin(2 * PI * (55.0 + 60.0 * exp(-i / (0.01 * rate))) * i / rate).toFloat()
            }
            // a hat halfway to the next beat
            val hat = ((t + beat / 2) * rate).toInt()
            for (i in 0 until (0.03 * rate).toInt()) {
                val n = hat + i
                if (n >= out.size) break
                out[n] += 0.12f * exp(-i / (0.006 * rate)).toFloat() * (random.nextFloat() * 2f - 1f)
            }
            t += beat
            index++
        }
        for (n in out.indices) out[n] += 0.06f * sin(2 * PI * 220.0 * n / rate).toFloat()
        return out
    }

    private fun analyze(samples: FloatArray, complete: Boolean = true): TrackAnalysis {
        val analyzer = StreamingTrackAnalyzer()
        var offset = 0
        val chunk = FloatArray(4_096)
        while (offset < samples.size) {
            val count = minOf(chunk.size, samples.size - offset)
            System.arraycopy(samples, offset, chunk, 0, count)
            analyzer.feed(chunk, count)
            offset += count
        }
        return analyzer.finish(complete)!!
    }

    @Test
    fun `finds 128 bpm, an even grid and the downbeat`() {
        val analysis = analyze(synthTrack(128.0, 90))
        val bpm = analysis.bpm!!
        assertEquals(128.0, bpm.toDouble(), 1.0)
        assertTrue("confidence ${analysis.bpmConfidence}", analysis.isBeatReliable)
        val gaps = analysis.beatsMs.toList().zipWithNext { a, b -> (b - a).toDouble() }.sorted()
        assertEquals(60_000.0 / 128, gaps[gaps.size / 2], 6.0)
        // the bar starts on an accented kick: 250 ms + a multiple of four beats
        val bar = analysis.beatsMs[analysis.downbeatIndex]
        val beats = (bar - 250.0) / (60_000.0 / 128)
        val phase = ((beats % 4) + 4) % 4
        assertTrue("downbeat phase $phase", phase < 0.2 || phase > 3.8)
        // the fitted local grid lands on the kicks within a few ms
        val grid = analysis.localGrid(60_000L)!!
        val snapped = grid.snap(60_000L)
        val kicks = (snapped - 250.0) / (60_000.0 / 128)
        assertEquals(kicks, Math.round(kicks).toDouble(), 0.03)
    }

    @Test
    fun `finds 100 bpm rather than its double or half`() {
        val bpm = analyze(synthTrack(100.0, 80)).bpm!!
        assertEquals(100.0, bpm.toDouble(), 1.0)
    }

    @Test
    fun `fast tempos fold into the counted range`() {
        val bpm = analyze(synthTrack(174.0, 80)).bpm!!.toDouble()
        assertTrue("bpm $bpm", abs(bpm - 174.0) < 1.5 || abs(bpm - 87.0) < 1.0)
    }

    // -- planning ---------------------------------------------------------------------------

    private val outgoingTrack by lazy { analyze(synthTrack(128.0, 200)) }
    private val nearTempoTrack by lazy { analyze(synthTrack(126.0, 150), complete = false) }
    private val farTempoTrack by lazy { analyze(synthTrack(100.0, 150), complete = false) }

    @Test
    fun `a plan starts within the last 30 seconds and enters within two minutes`() {
        for (incoming in listOf(nearTempoTrack, farTempoTrack)) {
            for (seed in 0 until 6) {
                val plan = CrossmixPlanner.plan(
                    CrossmixTrackInput("out$seed", 200_000L, outgoingTrack, null),
                    CrossmixTrackInput("in$seed", 0L, incoming, null),
                )
                assertTrue("${plan.style} starts ${plan.outStartMs}", plan.outStartMs >= 200_000L - CrossmixPlanner.MaxLeadMs)
                assertTrue("${plan.style} hands over too late", plan.outMediaAt(plan.lengthBeats.toFloat()) <= 200_000.0)
                assertTrue("cue ${plan.inCueMs}", plan.inCueMs in 0..CrossmixPlanner.MaxEntryMs)
                assertTrue("speed ${plan.inSpeed}", plan.inSpeed in 0.94f..1.06f)
                assertTrue("starts before the song", plan.inStartMs >= 0L)
            }
        }
    }

    @Test
    fun `tempos too far apart are never laid on top of each other`() {
        for (seed in 0 until 12) {
            val plan = CrossmixPlanner.plan(
                CrossmixTrackInput("o$seed", 200_000L, outgoingTrack, null),
                CrossmixTrackInput("i$seed", 0L, farTempoTrack, null),
            )
            assertFalse(plan.style.label, plan.style.needsBeatMatch)
            assertFalse(plan.beatMatched)
            assertEquals(1f, plan.inSpeed)
        }
    }

    @Test
    fun `close tempos are matched by the beats around the cue`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("a", 200_000L, outgoingTrack, null),
            CrossmixTrackInput("b", 0L, nearTempoTrack, null),
            recentStyles = emptyList(),
        )
        if (plan.beatMatched) {
            // 126 laid on 128: the incoming plays 128/126 faster
            assertEquals(128.0 / 126.0, plan.inSpeed.toDouble(), 0.01)
        }
    }

    @Test
    fun `no analysis still gives a smooth fade inside the window`() {
        val plan = CrossmixPlanner.plan(
            CrossmixTrackInput("x", 240_000L, null, null),
            CrossmixTrackInput("y", 0L, null, null),
        )
        assertEquals(CrossmixStyle.SilkBlend, plan.style)
        assertTrue(plan.outStartMs >= 240_000L - CrossmixPlanner.MaxLeadMs)
        assertTrue(plan.outMediaAt(plan.lengthBeats.toFloat()) <= 240_000.0)
    }

    // -- styles: every transition must start and end clean ----------------------------------

    @Test
    fun `every style leaves both songs untouched outside the transition`() {
        val p = FxParams()
        for (style in CrossmixStyle.entries) for (length in style.lengths) for (variation in 0 until 4) {
            val name = "${style.label} $length/$variation"
            style.evaluate(CrossmixRole.Outgoing, -2f, length.toFloat(), variation, p)
            assertEquals("$name out gain before", 1f, p.gain, 1e-4f)
            assertTrue("$name out filtered before", p.lowCutHz <= 21f && p.highCutHz >= 19_000f)
            assertTrue("$name out effects before", p.echoSend == 0f && p.reverbSend == 0f && p.noiseLevel == 0f)
            assertTrue("$name out shaping before", p.gateDepth == 0f && p.crush == 0f && p.flangerMix == 0f)
            assertTrue("$name out space before", p.pan == 0f && p.width == 1f && p.orbit == 0f)
            assertTrue("$name out bands before", p.lowBand == 1f && p.midBand == 1f && p.highBand == 1f)
            assertEquals("$name out deck before", FxParams.Deck.Live, p.deck)

            style.evaluate(CrossmixRole.Incoming, -0.25f, length.toFloat(), variation, p)
            assertEquals("$name in before entry", 0f, p.gain, 0f)

            style.evaluate(CrossmixRole.Outgoing, length + style.tailBeats + 1f, length.toFloat(), variation, p)
            assertTrue("$name out gain after ${p.gain}", p.gain < 1e-3f)
            assertTrue("$name out still feeding effects", p.echoSend == 0f && p.reverbSend == 0f && p.noiseLevel < 1e-4f)

            style.evaluate(CrossmixRole.Incoming, length + 8f, length.toFloat(), variation, p)
            assertEquals("$name in gain after", 1f, p.gain, 1e-3f)
            assertTrue("$name in filtered after (${p.lowCutHz}, ${p.highCutHz})", p.lowCutHz <= 21f && p.highCutHz >= 19_000f)
            assertTrue("$name in effects after", p.echoSend == 0f && p.reverbSend < 1e-3f && p.noiseLevel == 0f)
            assertTrue("$name in shaping after", p.gateDepth < 1e-3f && p.crush < 1e-3f && p.flangerMix < 1e-3f)
            assertTrue("$name in space after", abs(p.pan) < 1e-3f && abs(p.width - 1f) < 1e-3f)
            assertTrue("$name in bands after", abs(p.lowBand - 1f) < 1e-3f && abs(p.midBand - 1f) < 1e-3f && abs(p.highBand - 1f) < 1e-3f)
            assertEquals("$name in deck after", FxParams.Deck.Live, p.deck)
        }
    }

    // -- rendering: no clicks, no clipping, exact at both ends ------------------------------

    private class Rendered(val input: FloatArray, val output: FloatArray)

    private fun render(style: CrossmixStyle, role: CrossmixRole, length: Int, seconds: Double): Rendered {
        val rate = 44_100
        val processor = CrossmixTransitionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val beatMs = 500.0
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = role,
                style = style,
                variation = 1,
                lengthBeats = length.toFloat(),
                anchorMs = 2_000.0,
                mediaPerBeatMs = beatMs,
                musicalBeatMs = beatMs,
                endBeat = if (role == CrossmixRole.Outgoing) length + style.tailBeats + 2f else length + 8f,
                calibrationMs = 0.0,
                calibrationNanos = 0L,
            )
        )
        val frames = (seconds * rate).toInt()
        val input = FloatArray(frames)
        val output = FloatArray(frames)
        val block = 1_024
        var frame = 0
        while (frame < frames) {
            val count = minOf(block, frames - frame)
            val buffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                val v = (0.5 * sin(2 * PI * 440.0 * (frame + i) / rate)).toFloat()
                input[frame + i] = v
                val s = (v * 32_767).toInt().toShort()
                buffer.putShort(s); buffer.putShort(s)
            }
            buffer.flip()
            processor.onInputTimestamp(frame * 1_000_000L / rate)
            processor.queueInput(buffer)
            val out = processor.output
            for (i in 0 until count) {
                val l = out.getShort() / 32_768f
                out.getShort()
                output[frame + i] = l
            }
            frame += count
        }
        return Rendered(input, output)
    }

    @Test
    fun `every style renders without clicks or clipping and ends exactly`() {
        for (style in CrossmixStyle.entries) {
            val length = style.lengths.first()
            for (role in CrossmixRole.entries) {
                val endBeat = if (role == CrossmixRole.Outgoing) length + style.tailBeats + 3 else length + 10
                val seconds = 2.0 + endBeat * 0.5 + 1.0
                val r = render(style, role, length, seconds)
                val name = "${style.label} $role"
                var maxJump = 0f
                for (i in 1 until r.output.size) {
                    val v = r.output[i]
                    assertFalse("$name NaN at $i", v.isNaN())
                    assertTrue("$name clipped at $i", abs(v) <= 1f)
                    maxJump = maxOf(maxJump, abs(v - r.output[i - 1]))
                }
                // a 440 Hz sine at half scale moves at most ~0.03 per sample; pitch moves and noise
                // add some, but anything near the full swing is a click
                assertTrue("$name jump $maxJump", maxJump < 0.6f)

                val beforeEnd = (1.5 * 44_100).toInt()
                if (role == CrossmixRole.Outgoing) {
                    // untouched before the transition, silent once it's over
                    for (i in 0 until beforeEnd) assertEquals("$name before at $i", r.input[i], r.output[i], 2e-4f)
                    val after = ((2.0 + (length + style.tailBeats + 2.5) * 0.5) * 44_100).toInt()
                    for (i in after until r.output.size) assertEquals("$name after at $i", 0f, r.output[i], 2e-3f)
                } else {
                    // silent before it enters, the plain song once it has settled
                    for (i in 0 until beforeEnd) assertEquals("$name before at $i", 0f, r.output[i], 1e-3f)
                    val after = ((2.0 + (length + 9.5) * 0.5) * 44_100).toInt()
                    for (i in after until r.output.size) assertEquals("$name after at $i", r.input[i], r.output[i], 2e-4f)
                }
            }
        }
    }
}
