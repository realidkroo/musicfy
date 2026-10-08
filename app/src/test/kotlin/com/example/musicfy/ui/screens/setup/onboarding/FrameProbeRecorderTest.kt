package com.example.musicfy.ui.screens.setup.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameProbeRecorderTest {

    private fun run(refreshHz: Float, intervalsMs: List<Float>): BlurCapability.Probe {
        val recorder = FrameProbeRecorder(refreshHz)
        var t = 1_000_000_000L
        recorder.onFrame(t)
        // warm-up: frames inside the first 400 ms aren't counted, including a slow one there
        t += 450_000_000L
        recorder.onFrame(t)
        intervalsMs.forEach { ms ->
            t += (ms * 1_000_000).toLong()
            recorder.onFrame(t)
        }
        return recorder.result()
    }

    @Test
    fun smoothRunHasNoSlowFrames() {
        val probe = run(120f, List(200) { 8.33f })
        assertEquals(200, probe.frames)
        assertEquals(0, probe.slowFrames)
        assertEquals(8.33f, probe.averageFrameMs, 0.05f)
    }

    @Test
    fun droppedFramesAreCountedAgainstTheRefreshRate() {
        // at 120 Hz anything over 12.5 ms is late; every third frame takes two refreshes
        val probe = run(120f, List(300) { if (it % 3 == 0) 16.7f else 8.33f })
        assertEquals(100, probe.slowFrames)
        assertTrue(probe.slowRatio > 0.3f)
    }

    @Test
    fun sameTimingIsFineAtSixtyHertz() {
        // 16.7 ms is exactly one refresh at 60 Hz, so it's not slow there
        val probe = run(60f, List(120) { 16.7f })
        assertEquals(0, probe.slowFrames)
    }

    @Test
    fun warmupFramesAreIgnored() {
        val recorder = FrameProbeRecorder(60f)
        recorder.onFrame(0L + 1)
        recorder.onFrame(100_000_000L)
        recorder.onFrame(300_000_000L)
        assertEquals(0, recorder.result().frames)
    }
}
