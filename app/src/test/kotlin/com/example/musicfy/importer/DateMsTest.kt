package com.example.musicfy.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateMsTest {

    @Test
    fun readsEachServicesTimestamps() {
        // Spotify and Apple: ISO with a Z
        assertEquals(1717713548000L, dateMs("2024-06-06T22:39:08Z"))
        // Tidal: milliseconds and an offset without a colon
        assertEquals(1600104583587L, dateMs("2020-09-14T17:29:43.587+0000"))
        assertEquals(1600104583000L, dateMs("2020-09-14T19:29:43+02:00"))
    }

    @Test
    fun missingOrOddIsNull() {
        assertNull(dateMs(null))
        assertNull(dateMs(""))
        assertNull(dateMs("yesterday"))
    }
}
