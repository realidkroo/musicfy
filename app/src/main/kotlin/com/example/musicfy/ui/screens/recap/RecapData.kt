// RecapData.kt
//
// Everything the monthly recap and the Stats page show, worked out from the play history alone
// (the `event` table: one row per play of 30 s or more). Nothing here leaves the device.

package com.example.musicfy.ui.screens.recap

import androidx.compose.runtime.Immutable
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.RecapEventRow
import com.example.musicfy.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The recap opens once a month has this many different songs in it. */
internal const val RecapMinSongs = 10

/** ...and not before this day of the month: on the 1st and 2nd the previous month is shown instead. */
internal const val RecapFirstDay = 3

@Immutable
data class StatsRange(val kind: Kind, val year: Int, val month: Int = 1) {
    enum class Kind { Month, Year }

    val start: LocalDate get() = if (kind == Kind.Month) LocalDate.of(year, month, 1) else LocalDate.of(year, 1, 1)
    val endExclusive: LocalDate get() = if (kind == Kind.Month) start.plusMonths(1) else start.plusYears(1)

    val monthName: String get() = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** What the recap calls the period: "October", or "2026" for a whole year. */
    val label: String get() = if (kind == Kind.Month) monthName else "$year"

    /** Days in the month, or 12 for a year: how many buckets [ListeningStats.bucketMs] has. */
    val bucketCount: Int get() = if (kind == Kind.Month) start.lengthOfMonth() else 12

    companion object {
        fun monthOf(date: LocalDate) = StatsRange(Kind.Month, date.year, date.monthValue)
        fun yearOf(date: LocalDate) = StatsRange(Kind.Year, date.year)
    }
}

@Immutable
data class RecapSong(
    val id: String,
    val title: String,
    val artistName: String,
    val artistThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
)

@Immutable
data class RecapArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
    /** The song by this artist played the most in the period. */
    val topSong: RecapSong?,
)

@Immutable
class ListeningStats(
    val range: StatsRange,
    val totalMs: Long,
    val plays: Int,
    val songCount: Int,
    val artistCount: Int,
    val albumCount: Int,
    /** Most played first (by plays, then time), up to [MaxSongs]. */
    val topSongs: List<RecapSong>,
    /** Most listened first (by time), up to [MaxArtists]. */
    val topArtists: List<RecapArtist>,
    /** Listening time per weekday, Monday first. */
    val weekdayMs: List<Long>,
    /** Listening time per hour of the day, 0..23. */
    val hourMs: List<Long>,
    /** Listening time per day of the month, or per month of the year. */
    val bucketMs: List<Long>,
) {
    val isEmpty: Boolean get() = plays == 0

    companion object {
        const val MaxSongs = 20
        const val MaxArtists = 25
    }
}

/** What the recap card's barcode leads to once it's scanned. */
sealed interface RecapAvailability {
    data class Ready(val stats: ListeningStats, val monthProgressPercent: Int) : RecapAvailability

    /** Not unlocked yet; [comeBack] finishes "come back later ...". */
    data class NotYet(val comeBack: String) : RecapAvailability
}

internal object RecapRepository {

    suspend fun load(db: MusicDatabase, range: StatsRange): ListeningStats = withContext(Dispatchers.IO) {
        val events = db.recapEvents(range.start.toStoredMillis(), range.endExclusive.toStoredMillis())
        val ids = events.mapTo(LinkedHashSet()) { it.songId }.toList()
        val songs = ids.chunked(500).flatMap { db.getSongsByIds(it) }.associateBy { it.id }
        withContext(Dispatchers.Default) { aggregate(range, events, songs) }
    }

    /**
     * The month the recap is about today, or why there isn't one yet. [force] (Experimental) opens
     * it anyway with whatever has been played: this month, else last month, else this year.
     */
    suspend fun recapFor(db: MusicDatabase, today: LocalDate, force: Boolean = false): RecapAvailability {
        val normal = recapByTheRules(db, today)
        if (!force || normal is RecapAvailability.Ready) return normal
        val candidates = listOf(StatsRange.monthOf(today), StatsRange.monthOf(today.minusMonths(1)), StatsRange.yearOf(today))
        for (range in candidates) {
            val stats = load(db, range)
            if (stats.songCount > 0) return RecapAvailability.Ready(stats, progressOf(range, today))
        }
        return RecapAvailability.NotYet("once you've played a song")
    }

    private fun progressOf(range: StatsRange, today: LocalDate): Int = when {
        range.kind == StatsRange.Kind.Year && range.year == today.year ->
            (today.dayOfYear * 100f / today.lengthOfYear()).roundToInt().coerceIn(1, 100)
        range.kind == StatsRange.Kind.Month && range.year == today.year && range.month == today.monthValue ->
            (today.dayOfMonth * 100f / today.lengthOfMonth()).roundToInt().coerceIn(1, 100)
        else -> 100
    }

    private suspend fun recapByTheRules(db: MusicDatabase, today: LocalDate): RecapAvailability {
        val thisMonth = StatsRange.monthOf(today)
        if (today.dayOfMonth < RecapFirstDay) {
            val previous = StatsRange.monthOf(today.minusMonths(1))
            val stats = load(db, previous)
            if (stats.songCount >= RecapMinSongs) return RecapAvailability.Ready(stats, 100)
            val unlock = today.withDayOfMonth(RecapFirstDay)
            return RecapAvailability.NotYet(
                "on ${unlock.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $RecapFirstDay, " +
                    "once you've played $RecapMinSongs songs",
            )
        }
        val stats = load(db, thisMonth)
        if (stats.songCount >= RecapMinSongs) {
            val percent = (today.dayOfMonth * 100f / today.lengthOfMonth()).roundToInt().coerceIn(1, 100)
            return RecapAvailability.Ready(stats, percent)
        }
        val missing = RecapMinSongs - stats.songCount
        return RecapAvailability.NotYet(
            "when you've played $missing more ${if (missing == 1) "song" else "songs"} this month",
        )
    }

    /** First day anything was played, for the Stats timeline and as a fallback join date. */
    suspend fun firstPlayDate(db: MusicDatabase): LocalDate? = withContext(Dispatchers.IO) {
        db.firstEventTimestamp()?.let { storedMillisToDateTime(it).toLocalDate() }
    }

    private fun aggregate(range: StatsRange, events: List<RecapEventRow>, songs: Map<String, Song>): ListeningStats {
        class Acc(var ms: Long = 0L, var plays: Int = 0)

        val perSong = HashMap<String, Acc>()
        val weekday = LongArray(7)
        val hour = LongArray(24)
        val buckets = LongArray(range.bucketCount)
        var total = 0L
        for (e in events) {
            val ms = e.playTime.coerceAtLeast(0L)
            total += ms
            perSong.getOrPut(e.songId) { Acc() }.apply {
                this.ms += ms
                plays++
            }
            val at = storedMillisToDateTime(e.timestamp)
            weekday[at.dayOfWeek.value - 1] += ms
            hour[at.hour] += ms
            val bucket = if (range.kind == StatsRange.Kind.Month) at.dayOfMonth - 1 else at.monthValue - 1
            if (bucket in buckets.indices) buckets[bucket] += ms
        }

        fun recapSong(id: String, acc: Acc): RecapSong? {
            val song = songs[id] ?: return null
            return RecapSong(
                id = id,
                title = song.song.title,
                artistName = song.artists.joinToString(", ") { it.name }.ifBlank { song.song.albumName ?: "Unknown artist" },
                artistThumbnailUrl = song.artists.firstNotNullOfOrNull { it.thumbnailUrl },
                thumbnailUrl = song.song.thumbnailUrl,
                playTimeMs = acc.ms,
                plays = acc.plays,
            )
        }

        val songOrder = compareByDescending<RecapSong> { it.plays }.thenByDescending { it.playTimeMs }
        val allSongs = perSong.mapNotNull { (id, acc) -> recapSong(id, acc) }.sortedWith(songOrder)

        // one artist can come back under a few ids (a channel, a topic channel, a local tag), so
        // they're merged by name - otherwise the same person could take two places in the top 10
        class ArtistAcc(val id: String, val name: String, var thumb: String?) {
            var ms = 0L
            var plays = 0
            val songs = ArrayList<RecapSong>()
        }

        val perArtist = LinkedHashMap<String, ArtistAcc>()
        val albums = HashSet<String>()
        for (s in allSongs) {
            val song = songs[s.id] ?: continue
            song.song.albumId?.let { albums += it }
            song.artists.distinctBy { it.name.trim().lowercase() }.forEach { artist ->
                val key = artist.name.trim().lowercase()
                if (key.isEmpty()) return@forEach
                val acc = perArtist.getOrPut(key) { ArtistAcc(artist.id, artist.name.trim(), artist.thumbnailUrl) }
                if (acc.thumb == null) acc.thumb = artist.thumbnailUrl
                acc.ms += s.playTimeMs
                acc.plays += s.plays
                acc.songs += s
            }
        }
        val topArtists = perArtist.values
            .sortedByDescending { it.ms }
            .take(ListeningStats.MaxArtists)
            .map { a ->
                val top = a.songs.sortedWith(songOrder).firstOrNull()
                RecapArtist(
                    id = a.id,
                    name = a.name,
                    thumbnailUrl = a.thumb ?: top?.thumbnailUrl,
                    playTimeMs = a.ms,
                    plays = a.plays,
                    topSong = top,
                )
            }

        return ListeningStats(
            range = range,
            totalMs = total,
            plays = events.size,
            songCount = perSong.size,
            artistCount = perArtist.size,
            albumCount = albums.size,
            topSongs = allSongs.take(ListeningStats.MaxSongs),
            topArtists = topArtists,
            weekdayMs = weekday.toList(),
            hourMs = hour.toList(),
            bucketMs = buckets.toList(),
        )
    }
}

// The event table stores LocalDateTime.now() as if it were UTC (see Converters), so the wall clock
// of a play comes back by reading the millis as UTC - never through the device's zone.
internal fun LocalDate.toStoredMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

internal fun storedMillisToDateTime(millis: Long): LocalDateTime =
    LocalDateTime.ofEpochSecond(Math.floorDiv(millis, 1000L), 0, ZoneOffset.UTC)

internal fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

/** "12h 05m" style, the way the recap and Stats spell listening time. */
internal fun formatHoursMinutes(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

/** Hours with one decimal ("3.4"), for the receipt and the Stats rows. */
internal fun formatHoursDecimal(ms: Long): String {
    val tenths = (ms / 360_000L).coerceAtLeast(0L)
    return "${tenths / 10}.${tenths % 10}"
}

/** Morning / afternoon / evening / night, for the hour a peak falls in. */
internal fun timeOfDayName(hour: Int): String = when (hour) {
    in 5..11 -> "mornings"
    in 12..16 -> "afternoons"
    in 17..21 -> "evenings"
    else -> "nights"
}
