// CsvImport.kt

package com.example.musicfy.importer

/**
 * Reads playlist CSVs from TuneMyMusic (any streaming service), Exportify-style exports and
 * Musicfy's own export. Columns are found by header name, so their order doesn't matter.
 *
 * Musicfy's export uses TuneMyMusic's column names plus "Duration (ms)", "Video ID" and
 * "Thumbnail", so the two formats share one reader and Musicfy rows skip search entirely.
 */
fun parseImportCsv(text: String, fallbackPlaylistName: String = "Imported playlist"): ParsedImport {
    val rows = parseCsvRows(text).filter { row -> row.any { it.isNotBlank() } }
    if (rows.isEmpty()) return ParsedImport(emptyList(), emptyMap())

    val header = rows.first().map { normalizeHeader(it) }
    fun column(vararg names: String): Int = names.firstNotNullOfOrNull { name ->
        header.indexOf(normalizeHeader(name)).takeIf { it >= 0 }
    } ?: -1

    val titleIdx = column("Track name", "Track", "Title", "Song", "Song name", "Name")
    val artistIdx = column("Artist name", "Artist name(s)", "Artist", "Artists", "Artist names")
    val albumIdx = column("Album", "Album name", "Album title")
    val playlistIdx = column("Playlist name", "Playlist")
    val typeIdx = column("Type")
    val durationMsIdx = column("Duration (ms)", "Duration ms", "Track duration (ms)")
    val durationIdx = column("Duration", "Length", "Time")
    val videoIdIdx = column("Video ID", "YouTube ID", "VideoId")
    val thumbnailIdx = column("Thumbnail", "Thumbnail URL")
    if (titleIdx < 0) return ParsedImport(emptyList(), emptyMap())

    val liked = mutableListOf<ImportedTrack>()
    val playlists = linkedMapOf<String, MutableList<ImportedTrack>>()

    for (row in rows.drop(1)) {
        fun cell(idx: Int): String? = idx.takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

        val type = cell(typeIdx)?.lowercase()
        val isLiked = type == "favorite" || type == "favorites" || type == "liked" || type == "liked songs"
        // TuneMyMusic also lists albums/artists the user follows; only songs are importable
        if (type != null && !isLiked && type != "playlist" && type != "track" && type != "song") continue

        val playlistName = cell(playlistIdx) ?: fallbackPlaylistName
        val title = cell(titleIdx)
        if (title == null) {
            // a playlist row with no track keeps an empty playlist from a Musicfy export
            if (!isLiked && playlistIdx >= 0 && cell(playlistIdx) != null) playlists.getOrPut(playlistName) { mutableListOf() }
            continue
        }
        val videoId = cell(videoIdIdx)?.takeIf { YOUTUBE_ID_REGEX.matches(it) }
        val artist = cell(artistIdx).orEmpty()
        // without an artist, search is a guess — only keep those rows when the exact id is known
        if (artist.isEmpty() && videoId == null) continue

        val track = ImportedTrack(
            title = title,
            artist = artist,
            album = cell(albumIdx),
            durationMs = cell(durationMsIdx)?.toLongOrNull() ?: cell(durationIdx)?.let(::parseDurationMs),
            videoId = videoId,
            thumbnailUrl = cell(thumbnailIdx)?.takeIf { it.startsWith("https://") },
        )
        if (isLiked) liked.add(track) else playlists.getOrPut(playlistName) { mutableListOf() }.add(track)
    }

    return ParsedImport(liked, playlists)
}

/** Kept for the setup wizard, which only ever offered TuneMyMusic files. */
fun parseTuneMyMusicCsv(text: String): ParsedImport = parseImportCsv(text)

/** One row of a Musicfy export; [playlistName] is null for liked songs. */
data class CsvExportRow(
    val playlistName: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val videoId: String?,
    val thumbnailUrl: String?,
)

fun writeMusicfyCsv(rows: List<CsvExportRow>): String = buildString {
    appendCsvLine(listOf("Type", "Playlist name", "Track name", "Artist name", "Album", "Duration (ms)", "Video ID", "Thumbnail"))
    for (row in rows) {
        appendCsvLine(
            listOf(
                if (row.playlistName == null) "Favorite" else "Playlist",
                row.playlistName.orEmpty(),
                row.title.orEmpty(),
                row.artist.orEmpty(),
                row.album.orEmpty(),
                row.durationMs?.takeIf { it > 0 }?.toString().orEmpty(),
                row.videoId.orEmpty(),
                row.thumbnailUrl.orEmpty(),
            )
        )
    }
}

private val YOUTUBE_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

private fun normalizeHeader(name: String): String =
    name.removePrefix("﻿").trim().lowercase().replace(Regex("[\\s_]+"), " ")

/** "3:25", "1:02:03" or plain seconds. */
private fun parseDurationMs(text: String): Long? {
    val parts = text.split(':').map { it.trim().toLongOrNull() ?: return null }
    val seconds = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> return null
    }
    return (seconds * 1000).takeIf { it > 0 }
}

private fun StringBuilder.appendCsvLine(fields: List<String>) {
    fields.forEachIndexed { index, field ->
        if (index > 0) append(',')
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            append('"').append(field.replace("\"", "\"\"")).append('"')
        } else {
            append(field)
        }
    }
    append("\r\n")
}

private fun parseCsvRows(text: String): List<List<String>> {
    val content = text.removePrefix("﻿")
    // Excel in some locales saves "CSV" with semicolons; pick whichever the header uses more
    val headerLine = content.substringBefore('\n')
    val delimiter = if (headerLine.count { it == ';' } > headerLine.count { it == ',' }) ';' else ','
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
                delimiter -> endField()
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
