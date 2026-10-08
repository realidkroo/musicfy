// ImportModels.kt

package com.example.musicfy.importer

import com.music.innertube.models.SongItem

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long? = null,
    /** Exact YouTube id when the source already knows it (Musicfy CSV, YouTube links) — no search needed. */
    val videoId: String? = null,
    val thumbnailUrl: String? = null,
    /** Full YouTube item when the source is YouTube itself, so nothing is lost converting back. */
    val ytItem: SongItem? = null,
)

data class ParsedImport(
    val likedSongs: List<ImportedTrack>,
    val playlists: Map<String, List<ImportedTrack>>,
    /** Things the user should know before importing, e.g. a source that only shares part of a playlist. */
    val warnings: List<String> = emptyList(),
) {
    val totalSongs: Int get() = likedSongs.size + playlists.values.sumOf { it.size }
    val totalPlaylists: Int get() = playlists.size
}

data class ImportOptions(
    /** Also create the playlists (and likes) on the signed-in YouTube account. */
    val mirrorToYouTube: Boolean = false,
)

/** A track that couldn't be matched, with where it came from so the user can find it by hand. */
data class UnmatchedTrack(
    val track: ImportedTrack,
    val playlistName: String?,
)

data class ImportProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val matchedTracks: Int = 0,
    val currentLabel: String = "",
    val isDone: Boolean = false,
    val cancelled: Boolean = false,
    val playlistsCreated: Int = 0,
    val likedAdded: Int = 0,
    val unmatched: List<UnmatchedTrack> = emptyList(),
    /** Playlists that imported locally but couldn't be created or filled on YouTube. */
    val youtubeFailures: List<String> = emptyList(),
    val error: String? = null,
)

/** A YouTube song needs no matching: the id is exact, and the full item is kept. */
internal fun SongItem.toImportedTrack() = ImportedTrack(
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name,
    durationMs = duration?.times(1000L),
    videoId = id,
    thumbnailUrl = thumbnail,
    ytItem = this,
)
