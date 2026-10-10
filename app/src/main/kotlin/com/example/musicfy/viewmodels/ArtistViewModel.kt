// ArtistViewModel.kt

package com.example.musicfy.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import com.example.musicfy.canvas.AppleArtistEditorial
import com.example.musicfy.canvas.AppleMusicArtistBackgroundProvider
import com.example.musicfy.canvas.appleArtworkUrl
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.HideVideoSongsKey
import com.example.musicfy.constants.HideYoutubeShortsKey
import com.example.musicfy.constants.ShowArtistVideoKey
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.extensions.filterExplicit
import com.example.musicfy.extensions.filterExplicitAlbums
import com.example.musicfy.utils.ArtistHeader
import com.example.musicfy.utils.ArtistPageCache
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.get
import com.example.musicfy.utils.reportException
import com.example.musicfy.ui.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import com.example.musicfy.artistvideo.ArtistVideoCanvasProvider
import com.example.musicfy.extensions.filterVideoSongs as filterVideoSongsLocal

/**
 * the card under the artist's buttons: their newest release, or, for an artist with none listed,
 * their top song. [album] or [song] is what the card's play and save act on.
 */
data class ArtistRelease(
    val label: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val dateLabel: String?,
    val album: AlbumItem? = null,
    val song: SongItem? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!

    // a page opened a moment ago comes straight back while the fresh copy loads
    private val _page = MutableStateFlow(ArtistPageCache.page(artistId))
    val page: StateFlow<ArtistPage?> = _page

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val _apple = MutableStateFlow<AppleArtistEditorial?>(null)
    val apple: StateFlow<AppleArtistEditorial?> = _apple

    // a song's canvas, only looked for when Apple has no motion for the banner
    private val _canvasMotion = MutableStateFlow<String?>(null)

    private val _subscribed = MutableStateFlow<Boolean?>(null)
    val subscribed: StateFlow<Boolean?> = _subscribed

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** the round photo from YouTube Music, the banner, motion and name logo from Apple Music */
    val header: StateFlow<ArtistHeader?> = combine(_page, libraryArtist, _apple, _canvasMotion) { page, library, apple, canvas ->
        val name = page?.artist?.title ?: library?.artist?.name ?: return@combine null
        val photo = page?.artist?.thumbnail ?: library?.artist?.thumbnailUrl
        ArtistHeader(
            id = artistId,
            name = name,
            avatarUrl = photo?.resize(544, 544),
            bannerUrl = apple?.previewFrameUrl?.let { appleArtworkUrl(it, 1200, 1200) }
                ?: photo?.resize(1200, 1200)
                ?: apple?.bannerUrl?.let { appleArtworkUrl(it, 2400, 600) },
            motionUrl = apple?.motionSquareUrl ?: apple?.motionVideoUrl ?: canvas,
            logoUrl = apple?.logoUrl?.let { url ->
                val aspect = apple?.logoAspect ?: 3f
                appleArtworkUrl(url, 900, (900 / aspect).toInt().coerceAtLeast(1), format = "webp")
            },
            logoAspect = apple?.logoAspect,
        ).also(ArtistPageCache::putHeader)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ArtistPageCache.header(artistId))

    val release: StateFlow<ArtistRelease?> = combine(_page, _apple) { page, apple ->
        page?.let { pickRelease(it, apple) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** whether the newest release is in the library, for the card's + button */
    val releaseSaved: StateFlow<Boolean> = release
        .flatMapLatest { rel ->
            val id = rel?.album?.browseId
            if (id == null) kotlinx.coroutines.flow.flowOf(false)
            else database.album(id).map { it?.album?.bookmarkedAt != null }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }

        // Apple only needs the name, so it starts as soon as the library or YouTube has one
        viewModelScope.launch {
            val name = combine(_page, libraryArtist) { page, library -> page?.artist?.title ?: library?.artist?.name }
                .filterNotNull()
                .first()
            val editorial = withContext(Dispatchers.IO) {
                AppleMusicArtistBackgroundProvider.cached(name)
                    ?: AppleMusicArtistBackgroundProvider.getArtistEditorial(name)
            }
            _apple.value = editorial
            if (editorial?.motionSquareUrl == null && editorial?.motionVideoUrl == null) findCanvasMotion()
        }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            YouTube.artist(artistId)
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    val filtered = page.copy(sections = filteredSections)
                    _page.value = filtered
                    _loadFailed.value = false
                    _subscribed.value = page.subscribed
                    ArtistPageCache.putPage(artistId, filtered)
                }.onFailure {
                    if (_page.value == null) _loadFailed.value = true
                    reportException(it)
                }
        }
    }

    /** the library's heart: kept on this device, apart from following the channel on YouTube */
    fun toggleLike() {
        val page = _page.value
        database.transaction {
            val artist = libraryArtist.value?.artist
            if (artist != null) {
                update(artist.localToggleLike())
            } else if (page != null) {
                insert(
                    ArtistEntity(
                        id = page.artist.id,
                        name = page.artist.title,
                        channelId = page.artist.channelId,
                        thumbnailUrl = page.artist.thumbnail,
                    ).localToggleLike()
                )
            }
        }
    }

    /** follows the channel on the signed-in YouTube account; flips at once and backs out on failure */
    fun toggleSubscribe() {
        val channelId = _page.value?.artist?.channelId ?: return
        val target = _subscribed.value != true
        _subscribed.value = target
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.subscribeChannel(channelId, target).onFailure {
                _subscribed.value = !target
                reportException(it)
            }
        }
    }

    /** every top song, from the shelf's own page when there is one, else the five the shelf shows */
    suspend fun topSongs(): List<SongItem> {
        val section = _page.value?.sections?.firstOrNull { section ->
            (section.items.firstOrNull() as? SongItem)?.album != null
        } ?: return emptyList()
        val shown = section.items.filterIsInstance<SongItem>()
        val more = section.moreEndpoint ?: return shown
        val all = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000) { YouTube.artistItems(more).getOrNull() }
        }?.items?.filterIsInstance<SongItem>()
        return all?.takeIf { it.isNotEmpty() } ?: shown
    }

    /** the + on the release card: saves the album to the library, fetching it first if it's new */
    fun toggleReleaseSaved() {
        val album = release.value?.album ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.album(album.browseId).first()
            if (existing != null) {
                database.query { update(existing.album.toggleLike()) }
                return@launch
            }
            YouTube.album(album.browseId).onSuccess { albumPage ->
                database.transaction { insert(albumPage) }
                database.album(album.browseId).first()?.let { saved ->
                    database.query { update(saved.album.toggleLike()) }
                }
            }.onFailure(::reportException)
        }
    }

    private suspend fun findCanvasMotion() {
        if (!context.dataStore.get(ShowArtistVideoKey, true)) return
        val page = _page.filterNotNull().first()
        val songs = page.sections
            .firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
            ?.items?.filterIsInstance<SongItem>()
            ?.take(3)
            .orEmpty()
        for (song in songs) {
            val canvas = withContext(Dispatchers.IO) {
                runCatching {
                    ArtistVideoCanvasProvider.getBySongArtist(song = song.title, artist = page.artist.title)
                }.getOrNull()
            }
            canvas?.preferredAnimationUrl?.let {
                _canvasMotion.value = it
                return
            }
        }
    }

    private fun pickRelease(page: ArtistPage, apple: AppleArtistEditorial?): ArtistRelease? {
        // albums come first on the page, then singles & EPs; neither shelf says which it is in a
        // way that survives translation, so the order does
        val albumShelves = page.sections.filter { it.items.firstOrNull() is AlbumItem }
        val candidates = albumShelves.flatMapIndexed { shelf, section ->
            section.items.filterIsInstance<AlbumItem>().take(8).map { it to (shelf > 0) }
        }
        val latest = apple?.latestRelease
        val matched = latest?.let { rel ->
            val wanted = normalize(rel.name)
            candidates.firstOrNull { normalize(it.first.title) == wanted }
                ?: candidates.firstOrNull { normalize(it.first.title).startsWith(wanted) || wanted.startsWith(normalize(it.first.title)) }
        }
        val pick = matched
            ?: candidates.maxWithOrNull(compareBy<Pair<AlbumItem, Boolean>> { it.first.year ?: 0 }.thenByDescending { candidates.indexOf(it) })

        if (pick == null) {
            val top = page.sections
                .firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
                ?.items?.firstOrNull() as? SongItem
                ?: return null
            return ArtistRelease(
                label = "Highlight",
                title = top.title,
                subtitle = top.album?.name ?: "Top song",
                thumbnailUrl = top.thumbnail,
                dateLabel = null,
                song = top,
            )
        }

        val (album, fromSingles) = pick
        val date = if (matched != null) latest?.releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } else null
        val isSingle = if (matched != null) latest?.isSingle == true else fromSingles
        val recent = when {
            date != null -> ChronoUnit.DAYS.between(date, LocalDate.now()) in 0..120
            else -> album.year != null && album.year!! >= LocalDate.now().year
        }
        val kind = if (isSingle) "Single" else "Album"
        return ArtistRelease(
            label = if (recent) "New release" else "Latest release",
            title = album.title,
            subtitle = listOfNotNull(
                kind,
                latest?.trackCount?.takeIf { matched != null && it > 1 }?.let { "$it songs" },
            ).joinToString(" · "),
            thumbnailUrl = album.thumbnail,
            dateLabel = date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                ?: album.year?.toString(),
            album = album,
        )
    }

    private fun normalize(title: String): String =
        java.text.Normalizer.normalize(title.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
