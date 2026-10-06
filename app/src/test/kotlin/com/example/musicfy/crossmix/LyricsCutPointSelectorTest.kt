package com.example.musicfy.crossmix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsCutPointSelectorTest {
    @Test
    fun `prefers the latest short lyric line then snaps it to a close beat`() {
        val result = LyricsCutPointSelector.select(
            lyrics = """
                [03:24.00]A much longer lyric line lives here
                [03:29.00]Let go now
                [03:35.00]A final long lyric line keeps going
            """.trimIndent(),
            trackDurationMs = 225_000L,
            beatGridMs = longArrayOf(214_960L, 217_000L),
            quietPointsMs = longArrayOf(215_000L),
            fadeDurationMs = 8_000L,
        )

        assertEquals("Let go now", result.sourceLine)
        // The transition waits for the lyric to resolve, then snaps forward rather than back
        // into the final word.
        assertEquals(217_000L, result.timeMs)
        assertTrue(result.snappedToBeat)
    }

    @Test
    fun `falls back to an energy point when timed lyrics are absent`() {
        val result = LyricsCutPointSelector.select(
            lyrics = null,
            trackDurationMs = 240_000L,
            beatGridMs = longArrayOf(),
            quietPointsMs = longArrayOf(228_000L, 231_500L),
            fadeDurationMs = 8_000L,
        )

        assertEquals(231_500L, result.timeMs)
        assertEquals(null, result.sourceLine)
    }
}
