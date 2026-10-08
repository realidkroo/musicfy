// HomeViewModel.kt

package com.example.musicfy.viewmodels

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.models.filterAiGenerated
import com.music.innertube.pages.ChartsPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.utils.completed
import com.example.musicfy.constants.DisableAiFilterKey
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.HideVideoSongsKey
import com.example.musicfy.constants.HideYoutubeShortsKey
import com.example.musicfy.constants.OfflineModeKey
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.QuickPicks
import com.example.musicfy.constants.QuickPicksKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.db.entities.SpeedDialItem
import com.example.musicfy.extensions.filterVideoSongs
import com.example.musicfy.extensions.toEnum
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.models.SimilarRecommendation
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.utils.ArtistVideos
import com.example.musicfy.utils.FeedVideoKind
import com.example.musicfy.utils.HomeVideo
import com.example.musicfy.utils.SongVersions
import com.example.musicfy.utils.SyncUtils
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get
import com.example.musicfy.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.example.musicfy.constants.LastPlayedLikedSongsTimeKey
import com.example.musicfy.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

@kotlinx.serialization.Serializable
data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    musicImportService: com.example.musicfy.importer.MusicImportService,
) : ViewModel() {
    private val homeFeedCache = HomeFeedCache(context)

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<LocalItem>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val localPlaylists = MutableStateFlow<List<com.example.musicfy.db.entities.Playlist>?>(null)

    /**
     * A new install's hero: one song from each artist picked in onboarding, until there's history.
     * Null while it's still being asked for (the hero keeps its bones), empty when there's nothing
     * to ask (history already, offline) or the asking failed.
     */
    val starterSongs = MutableStateFlow<List<StarterSong>?>(null)

    /** Library artists (onboarding's picks on a new install), for the hero before there's any history. */
    val libraryArtists = database.artists(com.example.musicfy.constants.ArtistSortType.CREATE_DATE, true)
        .map { artists -> artists.filter { it.artist.isYouTubeArtist }.take(5) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val recentlyPlayed = MutableStateFlow<List<LocalItem>?>(null)

    val mostPlayedSongsForHome = MutableStateFlow<List<Song>?>(null)

    val recentHistorySongs = MutableStateFlow<List<Song>?>(null)

    val artistListItems = MutableStateFlow<List<ArtistGroup>?>(null)

    val allTimeHits = MutableStateFlow<List<YTItem>?>(null)

    val liveShows = MutableStateFlow<List<HomeVideo>?>(null)
    val musicVideos = MutableStateFlow<List<HomeVideo>?>(null)

    /** "Because you like", "More like", genre and new-release rows, in the order Home shows them */
    val homeCategories = MutableStateFlow<List<HomeCategory>?>(null)
    private val categoryParts = ConcurrentHashMap<HomeCategory.Kind, List<HomeCategory>>()

    /** YouTube's own all-video shelves in the feed, by section index */
    val feedVideoKinds: StateFlow<Map<Int, FeedVideoKind>> = homePage
        .map { page ->
            page?.sections.orEmpty()
                .mapIndexedNotNull { index, section ->
                    ArtistVideos.feedShelfKind(section.title, section.items)?.let { index to it }
                }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // YouTube's "Live performances" and "Music videos for you" fold into Home's rows of the same
    // name, so each shows once
    val liveRow: StateFlow<List<HomeVideo>?> = combine(liveShows, homePage, feedVideoKinds) { own, page, kinds ->
        ArtistVideos.merge(own, feedVideos(page, kinds, FeedVideoKind.Live))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val musicVideoRow: StateFlow<List<HomeVideo>?> = combine(musicVideos, homePage, feedVideoKinds) { own, page, kinds ->
        ArtistVideos.merge(own, feedVideos(page, kinds, FeedVideoKind.MusicVideos))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun feedVideos(page: HomePage?, kinds: Map<Int, FeedVideoKind>, kind: FeedVideoKind): List<HomeVideo> =
        page?.sections.orEmpty().flatMapIndexed { index, section ->
            if (kinds[index] == kind) section.items.filterIsInstance<SongItem>().map(ArtistVideos::feedVideo) else emptyList()
        }

    // one fetch per artist page per load, shared by every row that reads one
    private val artistPages = ConcurrentHashMap<String, Deferred<ArtistPage?>>()

    private suspend fun artistPage(id: String): ArtistPage? {
        val page = artistPages.computeIfAbsent(id) {
            viewModelScope.async(Dispatchers.IO) { YouTube.artist(id).getOrNull() }
        }.await()
        // a failed fetch shouldn't stick for the rest of the load
        if (page == null) artistPages.remove(id)
        return page
    }

    private val recommender = HomeRecommender(database, ::artistPage)
    private val signals = ListeningSignals(context)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val lastPlayedSong = database.events()
        .map { it.firstOrNull()?.song?.toMediaMetadata() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {

                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            is com.example.musicfy.db.entities.Playlist -> com.music.innertube.models.PlaylistItem(
                                id = item.id,
                                title = item.title,
                                author = Artist(name = "Local Playlist", id = null),
                                songCountText = item.songCount.toString() + " songs",
                                thumbnail = item.thumbnails.firstOrNull() ?: "",
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {

                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is com.example.musicfy.db.entities.Playlist -> com.music.innertube.models.PlaylistItem(
                                id = item.id,
                                title = item.title,
                                author = Artist(name = "Local Playlist", id = null),
                                songCountText = item.songCount.toString() + " songs",
                                thumbnail = item.thumbnails.firstOrNull() ?: "",
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true

            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is com.example.musicfy.db.entities.Playlist -> otherSources.add(com.music.innertube.models.PlaylistItem(
                            id = item.id,
                            title = item.title,
                            author = Artist(name = "Local Playlist", id = null),
                            songCountText = item.songCount.toString() + " songs",
                            thumbnail = item.thumbnails.firstOrNull() ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.pinToSpeedDial(speedDialItem)
            }
        }
    }

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        val playEvents = database.events().first()
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        val recent = playEvents.map { it.song }.distinctBy { it.id }
        if (recent.isEmpty() && likedSongs.isEmpty()) return

        // seeds from three directions, so the cards don't all orbit one song: something you liked,
        // something you play all the time, something you played just now. one seed per artist.
        val playCounts = playEvents.groupingBy { it.song.id }.eachCount()
        val mostPlayed = recent.sortedByDescending { playCounts[it.id] ?: 0 }
        val seeds = buildList<Song> {
            fun takeFrom(songs: List<Song>, count: Int) {
                songs.shuffled()
                    .filter { song ->
                        none { it.id == song.id } &&
                            none { it.artists.firstOrNull()?.id == song.artists.firstOrNull()?.id }
                    }
                    .take(count)
                    .forEach { add(it) }
            }
            takeFrom(likedSongs, 2)
            takeFrom(mostPlayed.take(20), 2)
            takeFrom(recent.take(15), 2)
            if (size < 5) takeFrom(recent, 5 - size)
        }

        // anything you've already heard isn't much of a discovery
        val heard = recent.mapTo(HashSet()) { it.id }
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val candidates = page.songs.filter { item ->
                                item.id != seed.id &&
                                    !(hideVideoSongs && item.isVideoSong) &&
                                    !(hideExplicit && item.explicit)
                            }
                            val recommendation = candidates.filter { it.id !in heard }.shuffled().firstOrNull()
                                ?: candidates.shuffled().firstOrNull()

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        // an empty result means the requests failed, not that there is nothing to show. one card
        // per artist, so a single act can't take over the hero.
        val found = items.toList()
            .distinctBy { it.recommendation.id }
            .distinctBy { (it.recommendation as? SongItem)?.artists?.firstOrNull()?.name ?: it.recommendation.id }
        if (found.isNotEmpty()) dailyDiscover.value = found.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val thirtyDaysAgo = System.currentTimeMillis() - 86400000L * 30L
                val topSongs = database.mostPlayedSongs(fromTimeStamp = thirtyDaysAgo, limit = 20)
                    .first()
                    .filterVideoSongs(hideVideoSongs)

                quickPicks.value = topSongs.distinctBy { it.id }.take(20)
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.events().first().firstOrNull()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    artistPage(seed.id)?.let { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }

            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {

                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        // a failed fetch used to overwrite the cached section (and the cache itself) with nothing,
        // which is how "From the community" vanished until the next lucky refresh
        if (playlists.isNotEmpty()) {
            val fresh = playlists.shuffled()
            communityPlaylists.value = fresh
            homeFeedCache.saveCommunityPlaylists(fresh)
        }
    }

    private suspend fun loadRecentlyPlayed() {
        val songs = database.recentlyPlayedSongs(limit = 8).first()
        val albums = database.recentlyPlayedAlbums(limit = 6).first()
        val playlists = database.recentlyPlayedPlaylists(limit = 6).first()

        val merged = mutableListOf<LocalItem>()
        val maxLen = maxOf(songs.size, albums.size, playlists.size)
        for (i in 0 until maxLen) {
            songs.getOrNull(i)?.let { merged.add(it) }
            albums.getOrNull(i)?.let { merged.add(it) }
            playlists.getOrNull(i)?.let { merged.add(it) }
        }

        val lastPlayedLikedSongs = context.dataStore.get(LastPlayedLikedSongsTimeKey, 0L)
        if (System.currentTimeMillis() - lastPlayedLikedSongs < 86400000L * 7) {
            val likedCount = database.likedSongsCount().first()
            val likedPlaylistEntity = com.example.musicfy.db.entities.Playlist(
                playlist = com.example.musicfy.db.entities.PlaylistEntity(id = "liked", name = context.getString(R.string.liked)),
                songCount = likedCount,
                songThumbnails = listOf()
            )
            merged.removeAll { it.id == "liked" }
            merged.add(0, likedPlaylistEntity)
        }

        recentlyPlayed.value = merged.take(15)
    }

    private suspend fun loadMostPlayedForHome() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val thirtyDaysAgo = System.currentTimeMillis() - 86400000L * 30L
        mostPlayedSongsForHome.value = database.mostPlayedSongs(fromTimeStamp = thirtyDaysAgo, limit = 20)
            .first()
            .filterVideoSongs(hideVideoSongs)
            .distinctBy { it.id }
            .take(20)
    }

    private suspend fun loadRecentHistory() {
        recentHistorySongs.value = database.events().first().map { it.song }.take(15)
    }

    private suspend fun loadAllTimeHits() {

        var page = YouTube.getChartsPage().onFailure { reportException(it) }.getOrNull()
        var section = page?.sections?.firstOrNull { it.chartType == ChartsPage.ChartType.TOP }
            ?: page?.sections?.firstOrNull { it.items.isNotEmpty() }
        if (section == null) {
            delay(1500)
            page = YouTube.getChartsPage().onFailure { reportException(it) }.getOrNull()
            section = page?.sections?.firstOrNull { it.chartType == ChartsPage.ChartType.TOP }
                ?: page?.sections?.firstOrNull { it.items.isNotEmpty() }
        }
        // a chart is a list of songs here, even when YouTube ranks the videos
        val hits = section?.items?.distinctBy { it.id }?.take(20)?.let { SongVersions.preferSongs(it) }
        if (!hits.isNullOrEmpty()) {
            allTimeHits.value = hits
            homeFeedCache.saveAllTimeHits(hits)
        }
    }

    /**
     * "Live Shows" and "Music Videos for You": the Videos and Live performances shelves off the pages
     * of the artists you've played most this month, a few each, dealt out one artist at a time
     */
    private suspend fun loadArtistVideos() {
        // the same switch that keeps video versions out of every other list
        if (context.dataStore.get(HideVideoSongsKey, false)) {
            liveShows.value = null
            musicVideos.value = null
            return
        }
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 30
        val artists = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .sortedByDescending { it.timeListened ?: 0 }
            .take(6)
        if (artists.isEmpty()) return

        val perArtist = coroutineScope {
            artists.map { artist ->
                async(Dispatchers.IO) {
                    val shelves = ArtistVideos.shelves(artistPage(artist.id)?.sections.orEmpty())
                    // a few of each artist's six most popular, so a refresh doesn't show the same three
                    fun pick(videos: List<SongItem>) = videos.filterExplicit(hideExplicit)
                        .take(6).shuffled().take(3)
                        .map { ArtistVideos.homeVideo(it, artist.artist.name) }
                    pick(shelves.liveShows) to pick(shelves.musicVideos)
                }
            }.awaitAll()
        }

        val live = ArtistVideos.interleave(perArtist.map { it.first }, limit = 12).distinctBy { it.item.id }
        val liveIds = live.mapTo(HashSet()) { it.item.id }
        val official = ArtistVideos.interleave(
            perArtist.map { (_, videos) -> videos.filterNot { it.item.id in liveIds } },
            limit = 12,
        ).distinctBy { it.item.id }

        // nothing back (offline mid-load, a hiccup) keeps what's already on screen
        if (live.isNotEmpty()) {
            liveShows.value = live
            homeFeedCache.saveLiveShows(live)
        }
        if (official.isNotEmpty()) {
            musicVideos.value = official
            homeFeedCache.saveMusicVideos(official)
        }
    }

    /** Home's recommendation rows. each kind shows as soon as it's ready rather than waiting on the slowest */
    private suspend fun loadCategories() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val profile = recommender.profile(signals)
        val artistSeeds = profile.topArtists.take(8).shuffled().take(3)

        supervisorScope {
            launchSafely {
                publishCategories(
                    HomeCategory.Kind.BecauseYouLike,
                    recommender.becauseYouLike(profile, profile.songSeeds.take(4), hideExplicit),
                )
            }
            launchSafely {
                publishCategories(
                    HomeCategory.Kind.MoreLikeArtist,
                    recommender.moreLike(profile, artistSeeds, hideExplicit),
                )
            }
            launchSafely {
                val genres = recommender.genres(profile, hideExplicit)
                publishCategories(HomeCategory.Kind.YourGenre, genres.filter { it.kind == HomeCategory.Kind.YourGenre })
                publishCategories(HomeCategory.Kind.NewGenre, genres.filter { it.kind == HomeCategory.Kind.NewGenre })
            }
            launchSafely {
                recommender.newFromArtists(profile, hideExplicit)?.let {
                    publishCategories(HomeCategory.Kind.NewFromArtists, listOf(it))
                }
            }
        }
        homeCategories.value?.let { homeFeedCache.saveCategories(it) }
    }

    // an empty result is a failed request, so the rows already on screen stay
    @Synchronized
    private fun publishCategories(kind: HomeCategory.Kind, rows: List<HomeCategory>) {
        if (rows.isEmpty()) return
        categoryParts[kind] = rows
        homeCategories.value = HomeRecommender.arrange(categoryParts.toMap())
    }

    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

        getQuickPicks()
        loadRecentlyPlayed()
        loadMostPlayedForHome()
        loadRecentHistory()

        forgottenFavorites.value = database.forgottenFavorites().first().distinctBy { it.id }
            .filterVideoSongs(hideVideoSongs).shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
            .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)

        val mostPlayedSongsIds = keepListeningSongs.map { it.id }
        val playlists = database.playlistsByUpdatedDateAsc().first()
        val matchingPlaylistIds = if (mostPlayedSongsIds.isNotEmpty()) {
            database.playlistIdsContainingSongs(mostPlayedSongsIds).toSet()
        } else emptySet()
        val keepListeningPlaylists = playlists
            .filter { it.id in matchingPlaylistIds }
            .shuffled().take(3)

        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists + keepListeningPlaylists).shuffled()

        val lastPlayedLikedSongs = context.dataStore.get(LastPlayedLikedSongsTimeKey, 0L)
        if (System.currentTimeMillis() - lastPlayedLikedSongs < 86400000L * 7) {
            val likedCount = database.likedSongsCount().first()
            val likedPlaylistEntity = com.example.musicfy.db.entities.Playlist(
                playlist = com.example.musicfy.db.entities.PlaylistEntity(id = "liked", name = context.getString(R.string.liked)),
                songCount = likedCount,
                songThumbnails = listOf()
            )
            val newList = keepListening.value?.toMutableList() ?: mutableListOf()
            newList.removeAll { it.id == "liked" }
            newList.add(0, likedPlaylistEntity)
            keepListening.value = newList
        }

        localPlaylists.value = playlists.distinctBy { it.id }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album || it is com.example.musicfy.db.entities.Playlist }
    }

    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        artistPage(artist.id)?.let { page ->
                            page.sections.takeLast(3).forEach { section -> items += section.items }
                        }
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(12)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(10) +
                                    page.albums.shuffled().take(5) +
                                    page.artists.shuffled().take(3) +
                                    page.playlists.shuffled().take(3))
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            artistPage(artistId)?.let { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            val nonNullResults = results.filterNotNull()
            // nothing came back: keep what the artist list already shows
            if (nonNullResults.isEmpty()) return@coroutineScope
            similarRecommendations.value = nonNullResults.shuffled()

            data class GroupAccumulator(
                var artistId: String?,
                var artistName: String,
                var artistThumbnailUrl: String?,
                val items: MutableList<YTItem> = mutableListOf(),
            )

            val groups = linkedMapOf<String, GroupAccumulator>()
            fun addToGroup(key: String, name: String, thumbnailUrl: String?, id: String?, item: YTItem?) {
                val group = groups.getOrPut(key) { GroupAccumulator(id, name, thumbnailUrl) }
                if (thumbnailUrl != null && group.artistThumbnailUrl == null) group.artistThumbnailUrl = thumbnailUrl
                if (item != null && group.items.none { it.id == item.id }) group.items.add(item)
            }

            nonNullResults.forEach { rec ->
                val seed = rec.title
                rec.items.forEach { item ->
                    when {
                        item is ArtistItem -> addToGroup(item.id ?: item.title, item.title, item.thumbnail, item.id, null)
                        seed is com.example.musicfy.db.entities.Artist -> addToGroup(seed.id, seed.title, seed.thumbnailUrl, seed.id, item)
                        else -> {
                            val itemArtist = when (item) {
                                is SongItem -> item.artists.firstOrNull()
                                is AlbumItem -> item.artists?.firstOrNull()
                                else -> null
                            }
                            if (itemArtist?.name != null) {
                                addToGroup(itemArtist.id ?: itemArtist.name, itemArtist.name, null, itemArtist.id, item)
                            }
                        }
                    }
                }
            }

            artistListItems.value = groups.values
                .filter { it.items.isNotEmpty() }
                .map {
                    ArtistGroup(
                        artistName = it.artistName,
                        artistId = it.artistId,
                        artistThumbnailUrl = it.artistThumbnailUrl,
                        items = it.items.take(5)
                    )
                }
                .shuffled()
                .take(15)
        }
    }

    private suspend fun loadStarterSongs() {
        if (database.eventCount().first() > 0) {
            starterSongs.value = emptyList()
            return
        }
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val picked = database.artists(com.example.musicfy.constants.ArtistSortType.CREATE_DATE, true).first()
            .filter { it.artist.isYouTubeArtist }
            .take(5)
        try {
            starterSongs.value = coroutineScope {
                picked.map { artist ->
                    async {
                        // an artist page leads with its top songs; one of the first few, so a
                        // reinstall doesn't greet you with the very same card
                        val top = artistPage(artist.id)?.sections.orEmpty().firstNotNullOfOrNull { section ->
                            section.items.filterIsInstance<SongItem>().filterExplicit(hideExplicit).takeIf { it.isNotEmpty() }
                        } ?: return@async null
                        StarterSong(
                            song = top.take(3).random(),
                            artistName = artist.artist.name,
                            artistThumbnail = artist.artist.thumbnailUrl,
                        )
                    }
                }.awaitAll().filterNotNull()
            }
        } finally {
            // a failed or cancelled ask must not leave the hero waiting on it
            if (starterSongs.value == null) starterSongs.value = emptyList()
        }
    }

    private fun isCommunityOrTrendingSection(title: String): Boolean {
        val titleLower = title.lowercase()
        return "trending" in titleLower || "community" in titleLower
    }

    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val disableAiFilter = context.dataStore.get(DisableAiFilterKey, false)

        // each source on its own: one failing request must not cancel the others (that took whole
        // sections down with it), and an unexpected throw shouldn't reach the crash handler
        supervisorScope {
            launchSafely { loadStarterSongs() }
            launchSafely { getDailyDiscover() }
            launchSafely { getCommunityPlaylists() }
            launchSafely { loadSimilarRecommendations() }
            launchSafely {
                YouTube.home().onSuccess { page ->
                    val filteredSections = page.sections.mapNotNull { section ->
                        if (isCommunityOrTrendingSection(section.title)) return@mapNotNull null
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts)
                            .filterAiGenerated(disableAiFilter).distinctBy { it.id }
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                    homePage.value = page.copy(sections = songVersionsOf(filteredSections))
                    homeFeedCache.saveHomePage(homePage.value!!)
                }.onFailure { reportException(it) }
            }
            launchSafely {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    )
                    homeFeedCache.saveExplorePage(explorePage.value!!)
                }.onFailure { reportException(it) }
            }
            launchSafely { loadAllTimeHits() }
            launchSafely { loadArtistVideos() }
            launchSafely { loadCategories() }
            if (YouTube.cookie != null) {
                launchSafely { loadAccountPlaylists() }
            }
        }

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private fun CoroutineScope.launchSafely(block: suspend () -> Unit) = launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
        }
    }

    private val loadMutex = Mutex()

    @Volatile
    private var reloadRequested = false

    /**
     * init, pull-to-refresh and the network-back reload can all ask at once. overlapping runs used to
     * race each other's writes, so a request that lands mid-load now just queues one more pass.
     */
    private suspend fun load() {
        if (!loadMutex.tryLock()) {
            reloadRequested = true
            return
        }
        try {
            do {
                reloadRequested = false
                loadOnce()
            } while (reloadRequested)
        } finally {
            loadMutex.unlock()
        }
    }

    private suspend fun loadOnce() {
        isLoading.value = true
        signals.markRefreshed()
        artistPages.clear()

        selectedChip.value = null
        previousHomePage.value = null

        loadLocalDataPhase()
        isLoading.value = false

        val offlineMode = context.dataStore.get(OfflineModeKey, false)
        if (!offlineMode) {
            // set the cookie here rather than trusting the account collector to have run first;
            // when it lost that race the account playlists were silently skipped
            context.dataStore.get(InnerTubeCookieKey, "").takeIf { it.isNotEmpty() }?.let { YouTube.cookie = it }
            loadNetworkDataPhase()
        } else if (starterSongs.value == null) {
            starterSongs.value = emptyList()
        }
        lastLoadAt = SystemClock.elapsedRealtime()
    }

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            val nextPage = songVersionsOf(nextSections.sections)
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextPage).mapNotNull { section ->
                    if (isCommunityOrTrendingSection(section.title)) return@mapNotNull null
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts).distinctBy { it.id }
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = songVersionsOf(
                    nextSections.sections.map { section ->
                        section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts).distinctBy { it.id })
                    }
                )
            )
            selectedChip.value = chip
        }
    }

    /** the feed's song rows get song versions (see [SongVersions]); its video rows stay videos */
    private suspend fun songVersionsOf(sections: List<HomePage.Section>): List<HomePage.Section> = coroutineScope {
        sections.map { section ->
            async {
                if (ArtistVideos.feedShelfKind(section.title, section.items) != null) {
                    section
                } else {
                    section.copy(items = SongVersions.preferSongs(section.items))
                }
            }
        }.awaitAll()
    }

    @Volatile
    private var lastLoadAt = 0L
    private val listeningRefresh = Mutex()

    /**
     * Home has no pull to refresh; it refreshes itself when your listening says it's worth it. this
     * runs when the player is closed and when Home comes back into view. two skips or four songs
     * played through rebuild the rows grown from your listening; after half an hour away the whole
     * page reloads. never more than once a minute, and never on top of a load already running.
     */
    fun onListeningBreak() {
        val sinceLoad = SystemClock.elapsedRealtime() - lastLoadAt
        if (lastLoadAt == 0L || sinceLoad < 60_000L || loadMutex.isLocked || listeningRefresh.isLocked) return
        val stale = sinceLoad > 30 * 60_000L
        val tasteMoved = signals.skipsSinceRefresh >= 2 || signals.playsSinceRefresh >= 4
        if (!stale && !tasteMoved) return
        viewModelScope.launch(Dispatchers.IO) {
            if (stale) load() else refreshFromListening()
        }
    }

    // only what grows from your listening: your own rows, the hero's picks, the recommendation and
    // video rows. YouTube's feed and the charts don't change with a skip, so they're left alone.
    private suspend fun refreshFromListening() {
        if (!listeningRefresh.tryLock()) return
        try {
            signals.markRefreshed()
            lastLoadAt = SystemClock.elapsedRealtime()
            artistPages.clear()
            loadLocalDataPhase()
            if (context.dataStore.get(OfflineModeKey, false)) return
            supervisorScope {
                launchSafely { getDailyDiscover() }
                launchSafely { loadCategories() }
                launchSafely { loadArtistVideos() }
            }
        } finally {
            listeningRefresh.unlock()
        }
    }

    private var listening: Job? = null
    private var listeningTo: PlayerConnection? = null

    /** follows the player to tell skips from songs played through; see [ListeningSignals] */
    fun attachPlayer(connection: PlayerConnection) {
        if (listeningTo === connection) return
        listeningTo = connection
        listening?.cancel()
        listening = viewModelScope.launch {
            var current: MediaMetadata? = null
            var playedMs = 0L
            var playingSince = -1L
            combine(connection.mediaMetadata, connection.isEffectivelyPlaying) { metadata, playing -> metadata to playing }
                .collect { (metadata, playing) ->
                    val now = SystemClock.elapsedRealtime()
                    if (playingSince >= 0) {
                        playedMs += now - playingSince
                        playingSince = -1L
                    }
                    if (metadata?.id != current?.id) {
                        current?.let { signals.onTrackLeft(it, playedMs) }
                        current = metadata
                        playedMs = 0L
                    }
                    if (playing && metadata != null) playingSince = now
                }
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>().distinctBy { it.id }
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts).distinctBy { it.id }
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        // every finished import brings new songs, playlists and likes: rebuild Home around them
        viewModelScope.launch {
            musicImportService.progress
                .map { it.isDone && it.matchedTracks > 0 }
                .distinctUntilChanged()
                .collect { finishedWithSongs -> if (finishedWithSongs) refresh() }
        }

        viewModelScope.launch(Dispatchers.IO) {
            homeFeedCache.loadHomePage()?.let { homePage.value = it }
            homeFeedCache.loadCommunityPlaylists()?.let { communityPlaylists.value = it }
            homeFeedCache.loadAllTimeHits()?.let { allTimeHits.value = it }
            homeFeedCache.loadExplorePage()?.let { explorePage.value = it }
            if (!context.dataStore.get(HideVideoSongsKey, false)) {
                homeFeedCache.loadLiveShows()?.let { liveShows.value = it }
                homeFeedCache.loadMusicVideos()?.let { musicVideos.value = it }
            }
            homeFeedCache.loadCategories()?.let { cached ->
                // seeded per kind, so a fresh kind arriving doesn't wipe the cached others
                cached.groupBy { it.kind }.forEach { (kind, rows) -> categoryParts.putIfAbsent(kind, rows) }
                if (homeCategories.value == null) homeCategories.value = cached
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            // its database writes ripple through every Home row; not while the launch intro plays
            com.example.musicfy.ui.launch.LaunchGate.awaitIntro()
            syncUtils.tryAutoSync()
        }

        // distinct, so any other preference write no longer re-fetches the account. collectLatest,
        // so a cookie change mid-fetch wins instead of being dropped like it was before.
        viewModelScope.launch(Dispatchers.IO) {
            var isFirstCookie = true
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collectLatest { cookie ->
                    val signedInLater = !isFirstCookie
                    isFirstCookie = false

                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie

                        YouTube.accountInfo().onSuccess { info ->
                            accountName.value = info.name
                            accountImageUrl.value = info.thumbnailUrl
                        }.onFailure {
                            reportException(it)
                        }
                        // the first load() fetches these itself, a later sign-in has to do it here
                        if (signedInLater) loadAccountPlaylists()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}

/** a song by one of the artists picked in onboarding, put forward on a new install's hero */
data class StarterSong(
    val song: SongItem,
    val artistName: String,
    val artistThumbnail: String?,
)
