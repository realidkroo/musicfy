package com.example.musicfy.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun `keeps the space when the gap between words is its own timed piece`() {
        val lines = LyricsUtils.parseLyrics(
            "[00:01.00]<00:01.00>I<00:01.40> <00:01.50>love<00:01.90> <00:02.00>you"
        )
        assertEquals("I love you", lines.single().text)
    }

    @Test
    fun `keeps the space when it comes right after the word tag`() {
        val lines = LyricsUtils.parseLyrics("[00:01.00]<00:01.00>I<00:01.50> love<00:02.00> you")
        assertEquals("I love you", lines.single().text)
    }

    @Test
    fun `one stray double space does not glue short words together`() {
        assertEquals("I love you baby", LyricsUtils.repairRichSyncPlainText("I love you  baby"))
    }

    @Test
    fun `old syllable-split text is still rejoined`() {
        assertEquals("want you", LyricsUtils.repairRichSyncPlainText("wa nt  yo u"))
    }

    @Test
    fun `lines all stamped at zero count as unsynced`() {
        val lines = LyricsUtils.parseLyrics("[00:00.00]first line\n[00:00.00]second line\n[00:00.00]third line")
        assertTrue(LyricsUtils.isUnsynced(lines))
        assertEquals(LyricsUtils.SyncKind.PLAIN, LyricsUtils.syncKind("[00:00.00]first line\n[00:00.00]second line"))
    }

    @Test
    fun `timed lines are not unsynced`() {
        val lines = LyricsUtils.parseLyrics("[00:01.00]first line\n[00:04.00]second line")
        assertFalse(LyricsUtils.isUnsynced(lines))
    }

    @Test
    fun `plain lyrics parse one entry per line`() {
        val lines = LyricsUtils.parsePlainLyrics("[ar:Someone]\nfirst line\n\nsecond line\n")
        assertEquals(listOf("first line", "second line"), lines.map { it.text })
    }
}
