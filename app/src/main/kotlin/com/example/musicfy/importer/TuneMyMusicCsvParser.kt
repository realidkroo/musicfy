// tunemymusiccsvparserkt
// parses the csv export produced by tunemymusiccom the same schema
// source app apple music spotify youtube music tidal qobuz the user
// since tune my music normalizes everything into one shared column layout
// export to file verified against a real my apple music librarycsv export
// track nameartist namealbumplaylist nametypeisrc<provider> id
// type is one of favorite a liked library song playlist belongs to a
// album a saved album not a track row or artist a followed artist
// only favorite playlist rows carry an actual song to import

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
        // album artist rows describe a saved album or a followed artist not a
        // there s no track to import from them
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

// minimal rfc4180 ish csv tokenizer handles quoted fields embedded
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
