// TrackMatcher.kt

package com.example.musicfy.importer

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the YouTube Music song for a track from another service. Search results are scored on
 * title, artist and duration; a song result wins over a music video unless no song is close.
 * One matcher lives for one import, so repeated tracks (same song in two playlists) search once.
 */
class TrackMatcher {
    private val cache = HashMap<String, SongItem?>()

    suspend fun match(track: ImportedTrack): SongItem? {
        track.ytItem?.let { return it }
        track.videoId?.let { id ->
            return SongItem(
                id = id,
                title = track.title,
                artists = splitArtists(track.artist).map { com.music.innertube.models.Artist(name = it, id = null) },
                album = null,
                duration = track.durationMs?.let { (it / 1000).toInt() }?.takeIf { it > 0 },
                thumbnail = track.thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            )
        }

        val key = "${normalizeTitle(track.title)}|${normalizeText(track.artist)}|${track.durationMs?.div(5000)}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }

        val result = findBest(track)
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun findBest(track: ImportedTrack): SongItem? {
        val firstArtist = splitArtists(track.artist).firstOrNull().orEmpty()
        val query = "${cleanTitleForSearch(track.title)} $firstArtist".trim()

        val songs = search(query, YouTube.SearchFilter.FILTER_SONG)
        val bestSong = songs.map { it to score(track, it) }.maxByOrNull { it.second }
        if (bestSong != null && bestSong.second >= SONG_ACCEPT) return bestSong.first

        // some tracks only exist as uploads/videos on YouTube
        val videos = search(query, YouTube.SearchFilter.FILTER_VIDEO)
        val bestVideo = videos.map { it to score(track, it) }.maxByOrNull { it.second }
        if (bestVideo != null && bestVideo.second >= VIDEO_ACCEPT) return bestVideo.first

        return bestSong?.takeIf { it.second >= SONG_FALLBACK_ACCEPT }?.first
    }

    private suspend fun search(query: String, filter: YouTube.SearchFilter): List<SongItem> {
        repeat(2) { attempt ->
            try {
                val result = YouTube.search(query, filter).getOrThrow()
                return result.items.filterIsInstance<SongItem>().take(10)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt == 0) kotlinx.coroutines.delay(800)
            }
        }
        return emptyList()
    }

    private fun score(track: ImportedTrack, candidate: SongItem): Double {
        val targetTitle = normalizeTitle(track.title)
        val candidateTitle = normalizeTitle(candidate.title)
        var titleScore = similarity(targetTitle, candidateTitle)

        val targetArtists = splitArtists(track.artist).map(::normalizeText).filter { it.isNotEmpty() }
        val candidateArtists = candidate.artists.map { normalizeText(it.name) }.filter { it.isNotEmpty() }
        var artistScore = if (targetArtists.isEmpty()) 0.5 else targetArtists.maxOf { target ->
            candidateArtists.maxOfOrNull { similarity(target, it) } ?: 0.0
        }

        // videos are often titled "Artist - Title (Official Video)"
        if (artistScore < 0.6 && targetArtists.any { it.isNotEmpty() && candidateTitle.contains(it) }) {
            artistScore = 0.8
            titleScore = max(titleScore, similarity(targetTitle, targetArtists.fold(candidateTitle) { acc, a -> acc.replace(a, " ") }.trim()))
        }

        val durationScore = durationScore(track.durationMs, candidate.duration)

        // a remix/live/instrumental that the source didn't ask for is a different song; whole words
        // on the raw titles, since cleaning drops "- Live at ..." and "live" is inside "alive"
        val rawTarget = " ${normalizeText(track.title)} "
        val rawCandidate = " ${normalizeText(candidate.title)} "
        val versionPenalty = VERSION_WORDS.count { word ->
            rawCandidate.contains(" $word ") != rawTarget.contains(" $word ")
        } * 0.15

        if (titleScore < MIN_TITLE) return 0.0
        return titleScore * 0.55 + artistScore * 0.3 + durationScore * 0.15 - versionPenalty
    }

    private fun durationScore(sourceMs: Long?, candidateSeconds: Int?): Double {
        if (sourceMs == null || sourceMs <= 0 || candidateSeconds == null || candidateSeconds <= 0) return 0.5
        val diff = abs(sourceMs / 1000.0 - candidateSeconds)
        return when {
            diff <= 3 -> 1.0
            diff <= 8 -> 0.7
            diff <= 20 -> 0.3
            diff <= 60 -> 0.0
            else -> -1.0
        }
    }

    companion object {
        private const val SONG_ACCEPT = 0.62
        private const val VIDEO_ACCEPT = 0.7
        private const val SONG_FALLBACK_ACCEPT = 0.5
        private const val MIN_TITLE = 0.45

        private val VERSION_WORDS = listOf("remix", "live", "instrumental", "acoustic", "karaoke", "cover", "sped up", "slowed", "nightcore")

        private val NOISE_IN_BRACKETS = Regex(
            "[(\\[][^)\\]]*\\b(feat|ft|with|remaster|remastered|version|edit|mono|stereo|deluxe|explicit|clean|bonus|from|official|audio|video|lyrics?|visuali[sz]er|hd|hq|4k)\\b[^)\\]]*[)\\]]",
            RegexOption.IGNORE_CASE,
        )
        private val DASH_SUFFIX = Regex(
            "\\s[-–—]\\s.*(remaster|version|edit|mono|stereo|mix\\b|from |live at|bonus).*$",
            RegexOption.IGNORE_CASE,
        )
        private val FEAT_TAIL = Regex("\\s(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
        private val ARTIST_SPLIT = Regex("\\s*(,|&|;|/|\\sx\\s|\\sand\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s|\\swith\\s)\\s*", RegexOption.IGNORE_CASE)
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val MARKS = Regex("\\p{M}+")

        fun splitArtists(artist: String): List<String> =
            artist.replace('\u00A0', ' ').split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

        private fun cleanTitleForSearch(title: String): String =
            title.replace(NOISE_IN_BRACKETS, " ").replace(DASH_SUFFIX, "").replace(FEAT_TAIL, "").replace(Regex("\\s+"), " ").trim()
                .ifEmpty { title }

        fun normalizeTitle(title: String): String = normalizeText(cleanTitleForSearch(title))

        fun normalizeText(text: String): String {
            val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(MARKS, "")
            return folded.replace(NON_WORD, " ").trim()
        }

        /** Best of token overlap and character edit distance, so both reordered words and scripts without spaces score fairly. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            val tokensA = a.split(' ').toSet()
            val tokensB = b.split(' ').toSet()
            val intersection = tokensA.intersect(tokensB).size
            val jaccard = intersection.toDouble() / (tokensA.size + tokensB.size - intersection)
            val compactA = a.replace(" ", "")
            val compactB = b.replace(" ", "")
            val edit = 1.0 - levenshtein(compactA, compactB).toDouble() / max(compactA.length, compactB.length)
            // one title fully containing the other ("Song" vs "Song Pt. 1") is close but not exact
            val containment = if (compactA.length >= 3 && compactB.length >= 3 &&
                (compactA.contains(compactB) || compactB.contains(compactA))
            ) 0.8 else 0.0
            return maxOf(jaccard, edit, containment)
        }

        private fun levenshtein(a: String, b: String): Int {
            if (a.length > 200 || b.length > 200) return max(a.length, b.length) - min(a.length, b.length)
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val tmp = prev; prev = curr; curr = tmp
            }
            return prev[b.length]
        }
    }
}
