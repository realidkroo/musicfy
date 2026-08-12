// youtubelyricsvideosynckt
// the sync with lyrics sub option of the youtube video background instead
// video to the song purely by proportional raw position this pairs the
// with the video s youtube transcript auto generated captions or the
// when available youtubetranscript already returns whichever track it
// by matching line text so the video seeks to wherever it s actually singing
// not just the same fraction of the way through

package com.example.musicfy.ui.player

import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.music.innertube.YouTube

// songtimems videotimems a confident lyric line match between the song and the video
typealias LyricVideoAnchor = Pair<Long, Long>

private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
private const val MIN_MATCH_SIMILARITY = 0.35

// fetches the video s transcript and aligns it against the song s own lyrics by
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

    // keep the mapping monotonic in both directions song time and video time
    // increasing a handful of noisy mismatched lines otherwise cause the
    // target to jump backwards mid playback which reads as a stutter
    val monotonic = mutableListOf<LyricVideoAnchor>()
    for (anchor in anchors) {
        val last = monotonic.lastOrNull()
        if (last == null || (anchor.first > last.first && anchor.second > last.second)) {
            monotonic.add(anchor)
        }
    }

    return monotonic.takeIf { it.size >= 2 }
}

// interpolates a video timeline target from the anchor list for the song s
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

// text matching not something to redo on every recomposition track revisit a
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
