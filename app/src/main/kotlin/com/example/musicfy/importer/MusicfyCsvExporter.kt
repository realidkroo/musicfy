// MusicfyCsvExporter.kt

package com.example.musicfy.importer

import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Liked songs and library playlists as a CSV that Musicfy imports back exactly (rows carry the
 * YouTube id) and that other tools can still read (TuneMyMusic column names).
 */
object MusicfyCsvExporter {

    data class Summary(val likedSongs: Int, val playlists: Int, val playlistSongs: Int)

    suspend fun build(database: MusicDatabase): Pair<String, Summary> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<CsvExportRow>()

        // newest first, which is the order an import re-creates
        val liked = database.likedSongsByCreateDateAsc().first().asReversed()
        liked.forEach { rows += it.toRow(playlistName = null) }

        val playlists = database.playlistsByNameAsc().first()
            .filterNot { it.playlist.id == PlaylistEntity.LIKED_PLAYLIST_ID || it.playlist.id == PlaylistEntity.DOWNLOADED_PLAYLIST_ID }
        val usedNames = HashSet<String>()
        var playlistSongs = 0
        playlists.forEach { playlist ->
            // two playlists with one name would merge on import; keep them apart
            var name = playlist.playlist.name.ifBlank { "Untitled playlist" }
            var suffix = 2
            while (!usedNames.add(name)) name = "${playlist.playlist.name} ($suffix)".also { suffix++ }

            val songs = database.playlistSongs(playlist.playlist.id).first().sortedBy { it.map.position }
            if (songs.isEmpty()) {
                rows += CsvExportRow(name, null, null, null, null, null, null)
            } else {
                songs.forEach { rows += it.song.toRow(playlistName = name) }
                playlistSongs += songs.size
            }
        }

        writeMusicfyCsv(rows) to Summary(liked.size, playlists.size, playlistSongs)
    }

    private fun Song.toRow(playlistName: String?) = CsvExportRow(
        playlistName = playlistName,
        title = song.title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.title ?: song.albumName,
        durationMs = song.duration.takeIf { it > 0 }?.times(1000L),
        // files on this phone have no YouTube id; the import searches for those by name instead
        videoId = song.id.takeUnless { song.isLocal },
        thumbnailUrl = song.thumbnailUrl?.takeIf { it.startsWith("https://") },
    )
}
