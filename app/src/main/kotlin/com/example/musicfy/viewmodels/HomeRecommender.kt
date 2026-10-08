// HomeRecommender.kt

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.core.content.edit
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.utils.ListeningGenres
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.MoodAndGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/** one titled row of recommendations on Home, songs and playlists side by side */
@Serializable
data class HomeCategory(
    val id: String,
    val kind: Kind,
    val title: String,
    val items: List<YTItem>,
    val thumbnailUrl: String? = null,
    // where the title's chevron goes: the artist, or the genre's page
    val endpoint: BrowseEndpoint? = null,
) {
    enum class Kind {
        /** "Because you like <song>": songs that grow out of one you play a lot (Apple's and Spotify's song shelves) */
        BecauseYouLike,

        /** "More like <artist>": other artists' songs from that artist's radio, the way Spotify's artist shelves work */
        MoreLikeArtist,

        /** a genre your top artists are filed under */
        YourGenre,

        /** a genre you don't play yet, so Home isn't only more of the same */
        NewGenre,

        /** this year's singles and albums from the artists you play most (Spotify's Release Radar idea) */
        NewFromArtists,
    }
}

/** what you've been listening to, read once per load and shared by every row */
class TasteProfile(
    /** songs played in the last three months */
    val heard: Set<String>,
    /** songs you skipped lately; they never come back as a recommendation */
    val avoidSongs: Set<String>,
    /** artists you keep skipping, lowercased */
    val avoidArtists: Set<String>,
    /** songs to grow rows from, strongest first, one per artist */
    val songSeeds: List<Song>,
    /** your YouTube artists by listening time this month */
    val topArtists: List<Artist>,
)

/**
 * builds Home's recommendation rows. the ideas are the ones Apple Music and Spotify are known for:
 * shelves grown from a song, from an artist, from a genre, and new releases from who you play.
 * all of them lean on what you play now over what you played months ago, skip anything you skipped,
 * keep songs you've heard to a few per row, and spread a row across artists.
 */
class HomeRecommender(
    private val database: MusicDatabase,
    private val artistPage: suspend (String) -> ArtistPage?,
) {
    private val gate = Semaphore(4)

    suspend fun profile(signals: ListeningSignals): TasteProfile {
        val now = LocalDateTime.now()
        val events = database.events().first()
        val liked = database.likedSongsByCreateDateAsc().first()

        // a play today counts 1, two weeks ago about a third, three months ago next to nothing
        val scores = HashMap<String, Double>()
        val songs = HashMap<String, Song>()
        events.forEach { event ->
            val ageDays = ChronoUnit.MINUTES.between(event.event.timestamp, now) / 1440.0
            if (ageDays > 90) return@forEach
            scores.merge(event.song.id, exp(-ageDays / 14.0), Double::plus)
            songs[event.song.id] = event.song
        }
        // a like says more than a play; the newest likes count
        liked.takeLast(50).forEach { song ->
            scores.merge(song.id, 1.5, Double::plus)
            songs.putIfAbsent(song.id, song)
        }

        val skips = signals.recentSkips()
        val avoidSongs = skips.mapTo(HashSet()) { it.songId }
        val avoidArtists = skips
            .flatMap { it.artists }
            .map { it.lowercase() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 3 }
            .keys

        val songSeeds = weightedSample(
            candidates = scores.entries
                .sortedByDescending { it.value }
                .mapNotNull { (id, score) -> songs[id]?.let { it to score } }
                .filter { (song, _) ->
                    !song.song.isLocal && song.id !in avoidSongs &&
                        song.artists.none { it.name.lowercase() in avoidArtists }
                }
                .distinctBy { (song, _) -> song.artists.firstOrNull()?.name ?: song.id }
                .take(14),
            count = 8,
        )

        val topArtists = database.mostPlayedArtists(
            System.currentTimeMillis() - 86400000L * 30,
            limit = 14,
        ).first()
            .filter { it.artist.isYouTubeArtist && it.artist.name.lowercase() !in avoidArtists }
            .sortedByDescending { it.timeListened ?: 0 }
            .ifEmpty {
                // nothing played yet: the artists picked during onboarding (saved to the library)
                // stand in until listening history takes over
                database.artists(com.example.musicfy.constants.ArtistSortType.CREATE_DATE, true).first()
                    .filter { it.artist.isYouTubeArtist && it.artist.name.lowercase() !in avoidArtists }
                    .take(14)
            }

        return TasteProfile(
            heard = scores.keys,
            avoidSongs = avoidSongs,
            avoidArtists = avoidArtists,
            songSeeds = songSeeds,
            topArtists = topArtists,
        )
    }

    /** "Because you like <song>": the song's related page, mostly new to you */
    suspend fun becauseYouLike(profile: TasteProfile, seeds: List<Song>, hideExplicit: Boolean): List<HomeCategory> =
        coroutineScope {
            seeds.map { seed ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                            ?: return@withPermit null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@withPermit null
                        val songs = pick(page.songs.filter { it.id != seed.id }, profile, hideExplicit, limit = 10, perArtist = 2)
                        val playlists = page.playlists.filterExplicit(hideExplicit).take(3)
                        HomeCategory(
                            id = "because_${seed.id}",
                            kind = HomeCategory.Kind.BecauseYouLike,
                            title = seed.title,
                            items = mix(songs, playlists),
                            thumbnailUrl = seed.thumbnailUrl,
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

    /** "More like <artist>": the artist's radio minus the artist, one song per artist, and playlists they're on */
    suspend fun moreLike(profile: TasteProfile, seeds: List<Artist>, hideExplicit: Boolean): List<HomeCategory> =
        coroutineScope {
            seeds.map { seed ->
                async(Dispatchers.IO) {
                    val page = artistPage(seed.id) ?: return@async null
                    val radio = page.artist.radioEndpoint ?: return@async null
                    val queue = gate.withPermit { YouTube.next(radio).getOrNull()?.items }.orEmpty()
                    val name = seed.artist.name
                    val others = queue.filter { song ->
                        song.artists.none { it.id == seed.id || it.name.equals(name, ignoreCase = true) }
                    }
                    val songs = pick(others, profile, hideExplicit, limit = 12, perArtist = 1)
                    // "Featured on" comes before "Playlists by", and both are all playlists
                    val playlists = page.sections
                        .firstOrNull { section -> section.items.isNotEmpty() && section.items.all { it is PlaylistItem } }
                        ?.items.orEmpty()
                        .filterExplicit(hideExplicit)
                        .take(2)
                    HomeCategory(
                        id = "more_like_${seed.id}",
                        kind = HomeCategory.Kind.MoreLikeArtist,
                        title = name,
                        items = mix(songs, playlists),
                        thumbnailUrl = seed.artist.thumbnailUrl ?: page.artist.thumbnail,
                        endpoint = BrowseEndpoint(browseId = seed.id),
                    )
                }
            }.awaitAll().filterNotNull()
        }

    /**
     * five genres: up to three your top artists are filed under (iTunes knows an artist's genre,
     * YouTube doesn't), weighted by listening time, and the rest ones you don't play yet
     */
    suspend fun genres(profile: TasteProfile, hideExplicit: Boolean): List<HomeCategory> {
        val genres = YouTube.moodAndGenres().getOrNull()
            // the grid whose titles read as genres; the other one is moods
            ?.maxByOrNull { section -> ListeningGenres.genreScore(section.items.map { it.title }) }
            ?.items
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        val artistGenres = coroutineScope {
            profile.topArtists.take(12).map { artist ->
                async(Dispatchers.IO) {
                    artist to gate.withPermit { ListeningGenres.itunesGenre(artist.artist.name) }
                }
            }.awaitAll()
        }
        val weights = HashMap<MoodAndGenres.Item, Long>()
        artistGenres.forEach { (artist, itunesGenre) ->
            val tile = itunesGenre?.let { genre -> ListeningGenres.match(genre, genres) { it.title } } ?: return@forEach
            weights[tile] = (weights[tile] ?: 0L) + (artist.timeListened ?: 0).coerceAtLeast(1)
        }
        val yours = weights.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val fresh = genres.filter { it !in yours && ListeningGenres.isGenre(it.title) }.shuffled().take(5 - yours.size)

        return coroutineScope {
            (yours.map { it to HomeCategory.Kind.YourGenre } + fresh.map { it to HomeCategory.Kind.NewGenre })
                .map { (genre, kind) ->
                    async(Dispatchers.IO) {
                        val page = gate.withPermit {
                            YouTube.browse(genre.endpoint.browseId, genre.endpoint.params).getOrNull()
                        } ?: return@async null
                        val shelves = page.items.map { it.items }
                        val songs = pick(
                            shelves.flatten().filterIsInstance<SongItem>().take(40).shuffled(),
                            profile, hideExplicit, limit = 9, perArtist = 2,
                        )
                        val playlists = shelves
                            .firstOrNull { shelf -> shelf.isNotEmpty() && shelf.all { it is PlaylistItem } }
                            .orEmpty()
                            .filterExplicit(hideExplicit)
                            .shuffled()
                            .take(3)
                        HomeCategory(
                            id = "genre_${genre.endpoint.browseId}_${genre.endpoint.params}",
                            kind = kind,
                            title = genre.title,
                            items = mix(songs, playlists),
                            endpoint = genre.endpoint,
                        )
                    }
                }.awaitAll().filterNotNull()
        }
    }

    /** the newest singles and albums of the artists you play most, this year and last */
    suspend fun newFromArtists(profile: TasteProfile, hideExplicit: Boolean): HomeCategory? {
        val pages = coroutineScope {
            profile.topArtists.take(8).map { async(Dispatchers.IO) { artistPage(it.id) } }.awaitAll()
        }.filterNotNull()
        val since = LocalDate.now().year - 1
        val releases = pages.flatMap { page ->
            // "Albums" and "Singles & EPs" list the newest first
            page.sections
                .filter { section -> section.items.isNotEmpty() && section.items.all { it is AlbumItem } }
                .flatMap { it.items.take(2) }
        }
            .filterIsInstance<AlbumItem>()
            .filter { (it.year ?: 0) >= since }
            .filterExplicit(hideExplicit)
            .distinctBy { it.id }
            .sortedByDescending { it.year }
            .take(12)
        if (releases.size < 3) return null
        return HomeCategory(
            id = "new_from_artists",
            kind = HomeCategory.Kind.NewFromArtists,
            title = "",
            items = releases,
        )
    }

    /**
     * mostly songs you haven't heard, plus a few you have so the row still feels like you. nothing you
     * skipped, no videos (these are song rows), and only [perArtist] songs from any one artist.
     */
    private fun pick(
        songs: List<SongItem>,
        profile: TasteProfile,
        hideExplicit: Boolean,
        limit: Int,
        perArtist: Int,
    ): List<SongItem> {
        val usable = songs
            .filterExplicit(hideExplicit)
            .filter { song ->
                !song.isVideoSong && song.id !in profile.avoidSongs &&
                    song.artists.none { it.name.lowercase() in profile.avoidArtists }
            }
            .distinctBy { it.id }
        val (heard, unheard) = usable.partition { it.id in profile.heard }
        val familiar = (limit / 4).coerceAtLeast(1)
        val chosen = unheard.take(limit * 2).shuffled().take(limit - minOf(familiar, heard.size)) +
            heard.shuffled().take(familiar)
        val perArtistCount = HashMap<String, Int>()
        return chosen.shuffled().filter { song ->
            val artist = song.artists.firstOrNull()?.name?.lowercase() ?: return@filter true
            val count = perArtistCount.merge(artist, 1, Int::plus) ?: 1
            count <= perArtist
        }
    }

    companion object {
        /**
         * puts the rows in the order Home shows them: a song row, an artist row and a genre row in
         * turn, new releases after the first round, the new genres last. a song only shows up in the
         * first row that has it, and a row left with fewer than four cards is dropped.
         */
        fun arrange(parts: Map<HomeCategory.Kind, List<HomeCategory>>): List<HomeCategory> {
            val rounds = listOf(
                HomeCategory.Kind.BecauseYouLike,
                HomeCategory.Kind.MoreLikeArtist,
                HomeCategory.Kind.YourGenre,
            ).map { ArrayDeque(parts[it].orEmpty()) }
            val ordered = buildList {
                var first = true
                while (rounds.any { it.isNotEmpty() }) {
                    rounds.forEach { queue -> queue.removeFirstOrNull()?.let(::add) }
                    if (first) addAll(parts[HomeCategory.Kind.NewFromArtists].orEmpty())
                    first = false
                }
                if (first) addAll(parts[HomeCategory.Kind.NewFromArtists].orEmpty())
                addAll(parts[HomeCategory.Kind.NewGenre].orEmpty())
            }
            val seen = HashSet<String>()
            return ordered.mapNotNull { category ->
                val items = category.items.filter { seen.add(it.id) }
                if (items.size >= 4) category.copy(items = items) else null
            }
        }

        /** a playlist after every three songs, the rest of the playlists at the end */
        private fun mix(songs: List<SongItem>, playlists: List<YTItem>): List<YTItem> = buildList {
            var next = 0
            songs.forEachIndexed { index, song ->
                add(song)
                if (index % 3 == 2 && next < playlists.size) add(playlists[next++])
            }
            addAll(playlists.drop(next))
        }

        /**
         * [count] picks where a stronger score is likelier to win but never certain to, so a refresh
         * doesn't grow every row from the same few songs (Efraimidis-Spirakis weighted sampling)
         */
        private fun <T> weightedSample(candidates: List<Pair<T, Double>>, count: Int): List<T> =
            candidates
                .map { (item, weight) -> item to ln(Random.nextDouble(1e-9, 1.0)) / weight.coerceAtLeast(1e-6) }
                .sortedByDescending { it.second }
                .take(count)
                .map { it.first }
    }
}

/**
 * what the player says about taste between loads: songs dropped in the first seconds, and songs
 * played through. skips are kept across launches (the newest 200), so a song you skipped keeps
 * out of recommendations for two weeks and an artist you skip three times sits out too.
 */
class ListeningSignals(context: Context) {
    @Serializable
    data class Skip(val songId: String, val artists: List<String>, val at: Long)

    private val prefs = context.getSharedPreferences("listening_signals", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val skips: ArrayDeque<Skip> = ArrayDeque(
        runCatching { json.decodeFromString<List<Skip>>(prefs.getString(KEY_SKIPS, null) ?: "[]") }
            .getOrDefault(emptyList())
    )

    @Volatile
    var skipsSinceRefresh = 0
        private set

    @Volatile
    var playsSinceRefresh = 0
        private set

    /** [metadata] stopped being the current song after [listenedMs] of actual playing */
    @Synchronized
    fun onTrackLeft(metadata: MediaMetadata, listenedMs: Long) {
        val durationMs = metadata.duration * 1000L
        when {
            // queued but never heard, like the song restored at launch
            listenedMs < 300 -> Unit
            // an intro, a jingle: too short to say anything
            durationMs in 1 until 40_000 -> Unit
            listenedMs < 30_000 && (durationMs <= 0 || listenedMs < durationMs / 2) -> {
                skips.addLast(Skip(metadata.id, metadata.artists.map { it.name }, System.currentTimeMillis()))
                while (skips.size > MAX_SKIPS) skips.removeFirst()
                skipsSinceRefresh++
                prefs.edit { putString(KEY_SKIPS, json.encodeToString(skips.toList())) }
            }
            else -> playsSinceRefresh++
        }
    }

    @Synchronized
    fun recentSkips(days: Int = 14): List<Skip> {
        val since = System.currentTimeMillis() - 86400000L * days
        return skips.filter { it.at >= since }
    }

    fun markRefreshed() {
        skipsSinceRefresh = 0
        playsSinceRefresh = 0
    }

    private companion object {
        const val KEY_SKIPS = "skips"
        const val MAX_SKIPS = 200
    }
}
