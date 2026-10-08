package com.example.musicfy.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    @Test
    fun tuneMyMusicLikedAndPlaylists() {
        val csv = "﻿Track name,Artist name,Album,Playlist name,Type,ISRC\r\n" +
            "Blinding Lights,The Weeknd,After Hours,,Favorite,USUG11904206\r\n" +
            "\"Hello, World\",\"Artist A, Artist B\",Album X,Road Trip,Playlist,\r\n" +
            "Song Two,Artist C,,Road Trip,Playlist,\r\n" +
            "Some Album,Artist D,,,Album,\r\n" +
            "No Artist,,,Road Trip,Playlist,\r\n"
        val parsed = parseImportCsv(csv)

        assertEquals(1, parsed.likedSongs.size)
        assertEquals("Blinding Lights", parsed.likedSongs[0].title)
        assertEquals(listOf("Road Trip"), parsed.playlists.keys.toList())
        val road = parsed.playlists.getValue("Road Trip")
        // the album row is skipped, and so is the row without an artist (nothing to search with)
        assertEquals(2, road.size)
        assertEquals("Hello, World", road[0].title)
        assertEquals("Artist A, Artist B", road[0].artist)
        assertNull(road[1].album)
        assertNull(road[0].videoId)
    }

    @Test
    fun musicfyExportRoundTrips() {
        val rows = listOf(
            CsvExportRow(null, "Liked \"Quoted\" Song", "Ärtist", "Album", 215_000, "dQw4w9WgXcQ", "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"),
            CsvExportRow("Mix, Vol. 1", "Line\nBreak", "日本のアーティスト", null, null, "abcdefghijk", null),
            CsvExportRow("Empty one", null, null, null, null, null, null),
            CsvExportRow("Local files", "From phone", "Someone", null, 1000, null, null),
        )
        val parsed = parseImportCsv(writeMusicfyCsv(rows))

        assertEquals(1, parsed.likedSongs.size)
        with(parsed.likedSongs[0]) {
            assertEquals("Liked \"Quoted\" Song", title)
            assertEquals("Ärtist", artist)
            assertEquals(215_000L, durationMs)
            assertEquals("dQw4w9WgXcQ", videoId)
            assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", thumbnailUrl)
        }
        assertEquals(listOf("Mix, Vol. 1", "Empty one", "Local files"), parsed.playlists.keys.toList())
        with(parsed.playlists.getValue("Mix, Vol. 1").single()) {
            assertEquals("Line\nBreak", title)
            assertEquals("日本のアーティスト", artist)
            assertEquals("abcdefghijk", videoId)
        }
        assertTrue(parsed.playlists.getValue("Empty one").isEmpty())
        // no YouTube id: falls back to searching by name
        assertNull(parsed.playlists.getValue("Local files").single().videoId)
    }

    @Test
    fun exportifyStyleUsesFileNameAsPlaylist() {
        val csv = "\"Track URI\",\"Track Name\",\"Artist Name(s)\",\"Album Name\",\"Duration (ms)\"\n" +
            "\"spotify:track:1\",\"Song\",\"Band\",\"LP\",\"201000\"\n"
        val parsed = parseImportCsv(csv, fallbackPlaylistName = "My Exportify")
        val track = parsed.playlists.getValue("My Exportify").single()
        assertEquals("Song", track.title)
        assertEquals("Band", track.artist)
        assertEquals(201_000L, track.durationMs)
    }

    @Test
    fun semicolonCsvAndClockDurations() {
        val csv = "Title;Artist;Duration\nSong;Band;3:25\nOther;Band;1:02:03\n"
        val tracks = parseImportCsv(csv, fallbackPlaylistName = "Excel").playlists.getValue("Excel")
        assertEquals(205_000L, tracks[0].durationMs)
        assertEquals(3_723_000L, tracks[1].durationMs)
    }

    @Test
    fun garbageGivesEmptyImport() {
        assertEquals(0, parseImportCsv("").totalSongs)
        assertEquals(0, parseImportCsv("just,some,words\n1,2,3").totalSongs)
    }
}
