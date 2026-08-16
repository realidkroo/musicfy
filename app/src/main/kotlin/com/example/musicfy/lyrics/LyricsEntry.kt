// LyricsEntry.kt

package com.example.musicfy.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double
)

/**
 * One reading positioned over the characters it belongs to.
 *
 * [start] inclusive, [end] exclusive, both indices into the line's own text — that is what lets the
 * renderer put the reading physically above its word instead of dumping the whole line's
 * romanisation underneath as one run-on string.
 */
data class RubyToken(
    val start: Int,
    val end: Int,
    val text: String,
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    /** Per-word readings drawn above the line. Null when this script isn't romanised as ruby. */
    val rubyFlow: MutableStateFlow<List<RubyToken>?> = MutableStateFlow(null),
    val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val agent: String? = null,
    val isBackground: Boolean = false
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
