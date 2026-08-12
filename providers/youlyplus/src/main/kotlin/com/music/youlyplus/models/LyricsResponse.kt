// lyricsresponse kt
// what is this for you ask its for lyrics response ofc

package com.music.youlyplus.models

import kotlinx.serialization.Serializable

// response model for the lyricsplus kpoe api v2 lyrics get fields mirror what youlyplus extension parses from the backend
@Serializable
data class LyricsResponse(
    // lrclib style
    val id: Int? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,

    // kpoe style array of lines
    val lyrics: List<LyricsItem>? = null,
    val type: String? = null,

    // common metadata
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
)

@Serializable
data class LyricsItem(
    val text: String? = null,
    val time: Long? = null,         // milliseconds
    val duration: Long? = null,     // milliseconds
    val syllabus: List<Syllable>? = null,
)

@Serializable
data class Syllable(
    val text: String? = null,
    val time: Long? = null,         // milliseconds
    val duration: Long? = null,     // milliseconds
    val isBackground: Boolean? = null,
)


