package com.example.musicfy.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun titleNoiseIsStripped() {
        assertEquals("yesterday", TrackMatcher.normalizeTitle("Yesterday - Remastered 2009"))
        assertEquals("solar eclipse", TrackMatcher.normalizeTitle("Solar Eclipse (feat. Don Toliver)"))
        assertEquals("song", TrackMatcher.normalizeTitle("Song [Official Music Video]"))
        assertEquals("song", TrackMatcher.normalizeTitle("Song feat. Someone"))
        // a remix is a different song, so it must survive cleaning
        assertEquals("song remix", TrackMatcher.normalizeTitle("Song (Remix)"))
        assertEquals("cafe", TrackMatcher.normalizeText("Café"))
    }

    @Test
    fun artistsSplitOnCommonSeparators() {
        assertEquals(listOf("Pitbull", "Sensato"), TrackMatcher.splitArtists("Pitbull, Sensato"))
        assertEquals(listOf("Drake", "Don Toliver"), TrackMatcher.splitArtists("Drake & Don Toliver"))
        assertEquals(listOf("A", "B", "C"), TrackMatcher.splitArtists("A feat. B, C"))
    }

    @Test
    fun similarityHandlesScriptsWithoutSpaces() {
        assertEquals(1.0, TrackMatcher.similarity("夜に駆ける", "夜に駆ける"), 0.0)
        assertTrue(TrackMatcher.similarity("夜に駆ける", "夜に駆けるyoasobi") >= 0.8)
        assertTrue(TrackMatcher.similarity("blinding lights", "lights blinding") >= 0.99)
        assertTrue(TrackMatcher.similarity("blinding lights", "save your tears") < 0.4)
    }
}
