// TuneMyMusicCsvParser.kt
// Parses the CSV export produced by tunemymusic.com — the same schema regardless of which
// source app (Apple Music, Spotify, YouTube Music, Tidal, Qobuz, ...) the user exported from,
// since Tune My Music normalizes everything into one shared column layout before it lets you
// export to file. Verified against a real "My Apple Music Library.csv" export:
// `Track name,Artist name,Album,Playlist name,Type,ISRC,<Provider> - id`
// Type is one of: "Favorite" (a liked/library song), "Playlist" (belongs to a named playlist),
// "Album" (a saved album — not a track row) or "Artist" (a followed artist — not a track row).
// Only Favorite/Playlist rows carry an actual song to import.

package com.example.musicfy.importer

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String?,
)

data class ParsedImport(
    val likedSongs: List<ImportedTrack>,
    val playlists: Map<String, List<ImportedTrack>>,
) {
    val totalSongs: Int get() = likedSongs.size + playlists.values.sumOf { it.size }
    val totalPlaylists: Int get() = playlists.size
}

fun parseTuneMyMusicCsv(text: String): ParsedImport {
    val rows = parseCsvRows(text)
    if (rows.isEmpty()) return ParsedImport(emptyList(), emptyMap())

    val header = rows.first().map { it.trim() }
    val titleIdx = header.indexOf("Track name")
    val artistIdx = header.indexOf("Artist name")
    val albumIdx = header.indexOf("Album")
    val playlistIdx = header.indexOf("Playlist name")
    val typeIdx = header.indexOf("Type")
    if (titleIdx < 0 || artistIdx < 0 || typeIdx < 0) return ParsedImport(emptyList(), emptyMap())

    val liked = mutableListOf<ImportedTrack>()
    val playlists = linkedMapOf<String, MutableList<ImportedTrack>>()

    for (row in rows.drop(1)) {
        val type = row.getOrNull(typeIdx)?.trim().orEmpty()
        // "Album"/"Artist" rows describe a saved album or a followed artist, not a song row —
        // there's no track to import from them.
        if (type != "Playlist" && type != "Favorite") continue

        val title = row.getOrNull(titleIdx)?.trim().orEmpty()
        val artist = row.getOrNull(artistIdx)?.trim().orEmpty()
        if (title.isBlank() || artist.isBlank()) continue
        val album = albumIdx.takeIf { it >= 0 }
            ?.let { row.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() }

        val track = ImportedTrack(title = title, artist = artist, album = album)
        if (type == "Favorite") {
            liked.add(track)
        } else {
            val playlistName = playlistIdx.takeIf { it >= 0 }
                ?.let { row.getOrNull(it)?.trim() }
                ?.takeIf { it.isNotBlank() }
                ?: "Imported playlist"
            playlists.getOrPut(playlistName) { mutableListOf() }.add(track)
        }
    }

    return ParsedImport(liked, playlists)
}

/**
 * Minimal RFC4180-ish CSV tokenizer: handles quoted fields, embedded commas/newlines inside
 * quotes, and doubled `""` as an escaped quote — enough for Tune My Music's real export format
 * (verified against a real sample file), without pulling in a CSV library dependency.
 */
private fun parseCsvRows(text: String): List<List<String>> {
    val content = text.removePrefix("\uFEFF")
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0

    fun endField() {
        row.add(field.toString())
        field.clear()
    }

    fun endRow() {
        endField()
        rows.add(row)
        row = mutableListOf()
    }

    while (i < content.length) {
        val c = content[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> endField()
                '\r' -> {}
                '\n' -> endRow()
                else -> field.append(c)
            }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()

    return rows
}
