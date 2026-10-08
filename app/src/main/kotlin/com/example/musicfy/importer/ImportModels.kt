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

/**
 * Where imported music came from. Kept with every imported song (see `ImportedSong`) so the Library
 * can list "Imported from Spotify" and the YouTube library sync knows to leave these songs alone.
 */
enum class ImportSource(val key: String, val label: String) {
    SPOTIFY("spotify", "Spotify"),
    APPLE_MUSIC("apple_music", "Apple Music"),
    TIDAL("tidal", "Tidal"),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music"),
    DEEZER("deezer", "Deezer"),

    /** A file or a service with no name of its own (a TuneMyMusic CSV, a Musicfy backup). */
    OTHER("other", "other sources");

    /** The sources the Library gives a category of their own. */
    val isListed: Boolean get() = this != OTHER

    companion object {
        fun fromKey(key: String?): ImportSource? = entries.firstOrNull { it.key == key }
    }
}

data class ParsedImport(
    val likedSongs: List<ImportedTrack>,
    val playlists: Map<String, List<ImportedTrack>>,
    /** Songs that go straight into the Library (an account's whole song library), in no playlist. */
    val librarySongs: List<ImportedTrack> = emptyList(),
    /** Things the user should know before importing, e.g. a source that only shares part of a playlist. */
    val warnings: List<String> = emptyList(),
    /** Which service this came from; null when that isn't known (a plain CSV). */
    val provider: ImportSource? = null,
) {
    val totalSongs: Int get() = likedSongs.size + librarySongs.size + playlists.values.sumOf { it.size }
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
    /** The service being imported from, for the "Adding 12 of 340 tracks from Spotify" banner. */
    val source: ImportSource? = null,
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
