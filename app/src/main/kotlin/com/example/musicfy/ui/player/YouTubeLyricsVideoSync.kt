// YouTubeLyricsVideoSync.kt
// The "sync with lyrics" sub-option of the YouTube video background: instead of matching the
// video to the song purely by proportional/raw position, this pairs the song's own timed lyrics
// with the video's YouTube transcript (auto-generated captions, or the recognized-language track
// when available — YouTube.transcript() already returns whichever track it serves by default)
// by matching line TEXT, so the video seeks to wherever it's actually singing the same words —
// not just "the same fraction of the way through".

package com.example.musicfy.ui.player

import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.music.innertube.YouTube

/** (songTimeMs, videoTimeMs) — a confident lyric-line match between the song and the video. */
typealias LyricVideoAnchor = Pair<Long, Long>

private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
private const val MIN_MATCH_SIMILARITY = 0.35

/**
 * Fetches the video's transcript and aligns it against the song's own lyrics by line-text
 * similarity. Returns null if the video has no transcript, the song has no lyrics, or nothing
 * matched confidently enough — callers should fall back to plain proportional position sync in
 * every one of those cases, since a bad mapping (perceptibly wrong video moment) is worse than no
 * mapping at all.
 */
suspend fun buildLyricVideoAnchors(videoId: String, songLyricsRaw: String?): List<LyricVideoAnchor>? {
    if (videoId.isBlank() || songLyricsRaw.isNullOrBlank()) return null

    val transcriptLrc = YouTube.transcript(videoId).getOrNull() ?: return null
    val videoLines = LyricsUtils.parseLyrics(transcriptLrc).filter { it.text.isNotBlank() }
    if (videoLines.isEmpty()) return null

    val songLines = LyricsUtils.parseLyrics(songLyricsRaw).filter { it.text.isNotBlank() }
    if (songLines.isEmpty()) return null

    val videoTokenSets = videoLines.map { it to tokenize(it.text) }

    val anchors = songLines.mapNotNull { songLine ->
        val songTokens = tokenize(songLine.text)
        if (songTokens.isEmpty()) return@mapNotNull null

        val best = videoTokenSets.maxByOrNull { (_, videoTokens) -> jaccardSimilarity(songTokens, videoTokens) }
            ?: return@mapNotNull null
        val bestSimilarity = jaccardSimilarity(songTokens, best.second)
        if (bestSimilarity < MIN_MATCH_SIMILARITY) return@mapNotNull null

        songLine.time to best.first.time
    }

    // Keep the mapping monotonic in both directions (song time and video time both strictly
    // increasing) — a handful of noisy mismatched lines otherwise cause the interpolated seek
    // target to jump backwards mid-playback, which reads as a stutter.
    val monotonic = mutableListOf<LyricVideoAnchor>()
    for (anchor in anchors) {
        val last = monotonic.lastOrNull()
        if (last == null || (anchor.first > last.first && anchor.second > last.second)) {
            monotonic.add(anchor)
        }
    }

    return monotonic.takeIf { it.size >= 2 }
}

/**
 * Interpolates a video-timeline target from the anchor list for the song's current position.
 * Returns null (fall back to proportional sync) before the first anchor or after the last one,
 * where there's nothing to interpolate between.
 */
fun resolveAnchoredVideoPositionMs(anchors: List<LyricVideoAnchor>, songPositionMs: Long): Long? {
    if (anchors.size < 2) return null
    if (songPositionMs <= anchors.first().first || songPositionMs >= anchors.last().first) return null

    val nextIndex = anchors.indexOfFirst { it.first > songPositionMs }
    if (nextIndex <= 0) return null
    val (prevSongMs, prevVideoMs) = anchors[nextIndex - 1]
    val (nextSongMs, nextVideoMs) = anchors[nextIndex]

    val songSpan = (nextSongMs - prevSongMs).coerceAtLeast(1L)
    val fraction = ((songPositionMs - prevSongMs).toFloat() / songSpan).coerceIn(0f, 1f)
    return prevVideoMs + ((nextVideoMs - prevVideoMs) * fraction).toLong()
}

private fun tokenize(text: String): Set<String> =
    TOKEN_REGEX.findAll(text.lowercase()).map { it.value }.toSet()

private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.intersect(b).size
    val union = a.size + b.size - intersection
    return if (union == 0) 0.0 else intersection.toDouble() / union
}

/** Caches the built anchor list per track — resolving it means a transcript fetch plus O(n*m)
 * text matching, not something to redo on every recomposition/track revisit. A cached `null`
 * means "tried, nothing confident enough" — distinct from "never tried". */
object LyricVideoAnchorCache {
    private const val maxSize = 32
    private val map = LinkedHashMap<String, List<LyricVideoAnchor>?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(mediaId: String): Boolean = map.containsKey(mediaId)

    @Synchronized
    fun get(mediaId: String): List<LyricVideoAnchor>? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, anchors: List<LyricVideoAnchor>?) {
        if (mediaId.isBlank()) return
        map[mediaId] = anchors
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}
