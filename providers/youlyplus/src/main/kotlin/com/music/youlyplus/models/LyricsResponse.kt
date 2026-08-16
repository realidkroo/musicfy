// LyricsResponse.kt

package com.music.youlyplus.models

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(

    val id: Int? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,

    val lyrics: List<LyricsItem>? = null,
    val type: String? = null,

    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
)

@Serializable
data class LyricsItem(
    val text: String? = null,
    val time: Long? = null,
    val duration: Long? = null,
    val syllabus: List<Syllable>? = null,
    /**
     * Which voice sings this line, e.g. `{"singer":"v1"}`.
     *
     * The API has always returned this and the model never declared it, so it was dropped on
     * deserialisation. That is why duet lyrics rendered flush left: YouLyPlus is the default
     * preferred provider, and the one field that says who is singing was being thrown away before
     * anything downstream could act on it.
     */
    val element: LyricsElement? = null,
)

@Serializable
data class LyricsElement(
    val singer: String? = null,
)

@Serializable
data class Syllable(
    val text: String? = null,
    val time: Long? = null,
    val duration: Long? = null,
    val isBackground: Boolean? = null,
)
