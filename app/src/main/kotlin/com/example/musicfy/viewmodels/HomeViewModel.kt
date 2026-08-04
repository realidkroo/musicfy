// HomeViewModel.kt
// the file functioned as home view model

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
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
import com.music.innertube.pages.ChartsPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.HideVideoSongsKey
import com.example.musicfy.constants.HideYoutubeShortsKey
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
import com.example.musicfy.utils.SyncUtils
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get
import com.example.musicfy.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.example.musicfy.constants.LastPlayedLikedSongsTimeKey
import com.example.musicfy.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // Recently Played: songs/albums/playlists only (no artists), true chronological recency.
    val recentlyPlayed = MutableStateFlow<List<LocalItem>?>(null)

    // Most Played: songs only, ranked #1-#4 by position. Deliberately independent of the
    // QuickPicksKey preference (which can switch `quickPicks` to a "last listen" mode) since
    // this section must always mean genuine most-played, not whatever Quick Picks currently is.
    val mostPlayedSongsForHome = MutableStateFlow<List<Song>?>(null)

    // History: raw chronological songs, duplicates allowed (unlike HistoryScreen's day-grouped dedup).
    val recentHistorySongs = MutableStateFlow<List<Song>?>(null)

    // Artist List: all "similar to X artist" seeds flattened + deduped into one row.
    val artistListItems = MutableStateFlow<List<ArtistGroup>?>(null)

    // All Time Hits - sourced from YouTube's charts TOP section.
    val allTimeHits = MutableStateFlow<List<YTItem>?>(null)

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
                // Keep Listening (History/Heavy Rotation)
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
                // Quick Picks (Most Played)
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
            // Visual feedback for the animation
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

            // Probability: 80% User Songs, 20% Other Sources
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
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }


    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    // Track if we're currently processing account data
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        
        // Require at least 10 listens before showing Daily Discover
        val playEvents = database.events().first()
        if (playEvents.size < 10) return

        val likedSongs = database.likedSongsByCreateDateAsc().first()
        
        val eligibleSeeds = if (likedSongs.isNotEmpty()) {
            (likedSongs + playEvents.map { it.song }).distinctBy { it.id }
        } else {
            playEvents.map { it.song }.distinctBy { it.id }
        }

        val seeds = eligibleSeeds.shuffled().take(5)

        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

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

        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
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
                    YouTube.artist(seed.id).onSuccess { page ->
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
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
        homeFeedCache.saveCommunityPlaylists(communityPlaylists.value.orEmpty())
    }

    private suspend fun loadRecentlyPlayed() {
        val songs = database.recentlyPlayedSongs(limit = 8).first()
        val albums = database.recentlyPlayedAlbums(limit = 6).first()
        val playlists = database.recentlyPlayedPlaylists(limit = 6).first()

        // Each list is already recency-sorted individually; there's no timestamp carried
        // through to this layer to do a true global sort across types, so a round-robin
        // merge (song, album, playlist, ...) approximates "most recent first" well enough
        // for a home-row preview without adding extra query plumbing.
        val merged = mutableListOf<LocalItem>()
        val maxLen = maxOf(songs.size, albums.size, playlists.size)
        for (i in 0 until maxLen) {
            songs.getOrNull(i)?.let { merged.add(it) }
            albums.getOrNull(i)?.let { merged.add(it) }
            playlists.getOrNull(i)?.let { merged.add(it) }
        }

        // Liked Songs is a synthetic playlist (a `song.liked` boolean, not a real row in
        // the `playlist` table) so recentlyPlayedPlaylists()'s join can never surface it —
        // same synthetic-injection approach already used for keepListening below.
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
        // One retry before giving up for the session — this was a single one-shot call
        // with no fallback, so any transient failure/empty response meant the section
        // silently never showed up again until the next full reload.
        var page = YouTube.getChartsPage().onFailure { reportException(it) }.getOrNull()
        var section = page?.sections?.firstOrNull { it.chartType == ChartsPage.ChartType.TOP }
            ?: page?.sections?.firstOrNull { it.items.isNotEmpty() }
        if (section == null) {
            delay(1500)
            page = YouTube.getChartsPage().onFailure { reportException(it) }.getOrNull()
            section = page?.sections?.firstOrNull { it.chartType == ChartsPage.ChartType.TOP }
                ?: page?.sections?.firstOrNull { it.items.isNotEmpty() }
        }
        allTimeHits.value = section?.items?.distinctBy { it.id }?.take(20)
        allTimeHits.value?.let { homeFeedCache.saveAllTimeHits(it) }
    }

    /**
     * Phase 1: Reads all local DB data and immediately drops the loading indicator.
     * Guarantees the UI shows real content before any network call is made.
     */
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
        
        // Find playlists that contain the most played songs using a single batch query
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

    /**
     * Fetches all three recommendation sources (artists, songs, albums) concurrently
     * using async/awaitAll, replacing the previous sequential mapNotNull chains.
     */
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
                        YouTube.artist(artist.id).onSuccess { page ->
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
                            YouTube.artist(artistId).onSuccess { page ->
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
            similarRecommendations.value = nonNullResults.shuffled()

            // Artist List: collapse every "similar to X" seed above into one card per artist
            // (covers + artist avatar) instead of N separate per-seed rows.
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

    // Our own FromTheCommunity section (getCommunityPlaylists()) already covers this
    // content — without dropping YouTube's own equivalent section here, it renders as
    // a second, separate row showing overlapping playlists right alongside it.
    private fun isCommunityOrTrendingSection(title: String): Boolean {
        val titleLower = title.lowercase()
        return "trending" in titleLower || "community" in titleLower
    }

    /**
     * Phase 2: Fires all network sections concurrently.
     * Because isLoading is already false, each section streams into the UI
     * as its data arrives — no spinner blocking the user.
     */
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        coroutineScope {
            launch(Dispatchers.IO) { getDailyDiscover() }
            launch(Dispatchers.IO) { getCommunityPlaylists() }
            launch(Dispatchers.IO) { loadSimilarRecommendations() }
            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    val filteredSections = page.sections.mapNotNull { section ->
                        if (isCommunityOrTrendingSection(section.title)) return@mapNotNull null
                        val filteredItems = section.items
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts).distinctBy { it.id }
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                    homePage.value = page.copy(sections = filteredSections)
                    homeFeedCache.saveHomePage(homePage.value!!)
                }.onFailure { reportException(it) }
            }
            launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    )
                    homeFeedCache.saveExplorePage(explorePage.value!!)
                }.onFailure { reportException(it) }
            }
            launch(Dispatchers.IO) { loadAllTimeHits() }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        // Update combined YT items once all network data has settled
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true

        // loadNetworkDataPhase() below unconditionally overwrites homePage with the
        // fresh unfiltered feed — clearing the chip selection here keeps the chip pill
        // in sync with what's actually on screen instead of staying visually
        // "selected" over content that silently reverted to unfiltered.
        selectedChip.value = null
        previousHomePage.value = null

        // Phase 1: Local DB only — UI renders immediately after this
        loadLocalDataPhase()
        isLoading.value = false

        // Phase 2: All network sections in parallel — streams in progressively
        loadNetworkDataPhase()
    }

    private val _isLoadingMore = MutableStateFlow(false)
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

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
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
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts).distinctBy { it.id })
                }
            )
            selectedChip.value = chip
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
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        // Show last session's cached feed instantly, before the network phase (below)
        // even starts — this is what makes the algorithmic sections feel instant on a
        // cold app start instead of blank until the network responds. The network
        // phase always still runs and overwrites these with fresh data once it lands.
        viewModelScope.launch(Dispatchers.IO) {
            homeFeedCache.loadHomePage()?.let { homePage.value = it }
            homeFeedCache.loadCommunityPlaylists()?.let { communityPlaylists.value = it }
            homeFeedCache.loadAllTimeHits()?.let { allTimeHits.value = it }
            homeFeedCache.loadExplorePage()?.let { explorePage.value = it }
        }

        // Load home data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()
        }

        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }



        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    // Avoid processing if already processing
                    if (isProcessingAccountData) return@collect

                    // Always process cookie changes, even if same value (for logout/login scenarios)
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            // Update YouTube.cookie manually to ensure it's set
                            YouTube.cookie = cookie

                            // Fetch new account data
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
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
