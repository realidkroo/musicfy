// ArtistScreen.kt

package com.example.musicfy.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.example.musicfy.ui.utils.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalAppContentObscured
import com.example.musicfy.LocalDetailAccentColor
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.canvas.appleArtworkUrl
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.ShowArtistBackgroundVideoKey
import com.example.musicfy.constants.ShowArtistDescriptionKey
import com.example.musicfy.constants.ShowArtistSubscriberCountKey
import com.example.musicfy.constants.ShowMonthlyListenersKey
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.playback.queues.YouTubeAlbumRadio
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.HomeCardHighlight
import com.example.musicfy.ui.component.HomeCardSpacing
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.HomeRowHeight
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.HomeVideoCard
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.detail.FeaturedArtistUi
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistStatsStyle
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.playlistCoverHeight
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.component.rememberCoverBlurAvailable
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.AlbumMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.screens.settings.importsync.rememberYouTubeSignedIn
import com.example.musicfy.ui.theme.rememberCoverThemeColor
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.ArtistVideos
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.ArtistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** an artist page's shelves, sorted by what they hold; YouTube's titles are in the account's language */
private class ArtistShelves(sections: List<ArtistSection>) {
    val topSongs = sections.firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
    val albumShelves = sections.filter { it.items.firstOrNull() is AlbumItem }
    private val videoKinds = ArtistVideos.shelves(sections)
    val videos: List<SongItem> = videoKinds.musicVideos
    val live: List<SongItem> = videoKinds.liveShows
    val videoShelf = shelfHolding(sections, videos.firstOrNull())
    val liveShelf = shelfHolding(sections, live.firstOrNull())
    val playlistShelves = sections.filter { it.items.firstOrNull() is PlaylistItem }
    val similar = sections.firstOrNull { it.items.firstOrNull() is ArtistItem }

    // anything this page doesn't know how to lay out still shows, as plain cards
    val others = sections.filter { section ->
        section !== topSongs && section !in albumShelves && section !== videoShelf && section !== liveShelf &&
            section !in playlistShelves && section !== similar &&
            !(section.items.firstOrNull() is SongItem && (videos.isNotEmpty() || live.isNotEmpty()) &&
                section.items.all { item -> item is SongItem && (item.album == null) })
    }

    private fun shelfHolding(sections: List<ArtistSection>, item: SongItem?): ArtistSection? =
        item?.let { first -> sections.firstOrNull { section -> section.items.any { it.id == first.id } } }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val page by viewModel.page.collectAsState()
    val header by viewModel.header.collectAsState()
    val apple by viewModel.apple.collectAsState()
    val release by viewModel.release.collectAsState()
    val releaseSaved by viewModel.releaseSaved.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val showSubscriberCount by rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)
    val showBackgroundVideo by rememberPreference(key = ShowArtistBackgroundVideoKey, defaultValue = true)
    val profilePic by rememberPreference(ProfilePicUriKey, defaultValue = "")
    val signedIn = rememberYouTubeSignedIn()

    val name = header?.name ?: page?.artist?.title ?: libraryArtist?.artist?.name.orEmpty()
    val photo = page?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl
    val appleStill = apple?.previewFrameUrl?.let { appleArtworkUrl(it, 1200, 1200) }
    val shelves = remember(page) { page?.let { ArtistShelves(it.sections) } }
    val bio = page?.description?.takeIf { it.isNotBlank() } ?: apple?.bio?.takeIf { it.isNotBlank() }
    val isLocalArtist = libraryArtist?.artist?.isLocal == true

    // the colour comes off the banner: Apple's frame once it's there, the photo until then
    val themeColor by rememberCoverThemeColor(appleStill ?: photo?.resize(544, 544))
    val background = animateColorAsState(
        targetValue = themeColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(600),
        label = "artistTheme",
    )
    val accent = LocalDetailAccentColor.current
    SideEffect { accent.value = themeColor }
    DisposableEffect(Unit) { onDispose { accent.value = null } }

    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val bannerHeight = playlistCoverHeight()
    val bannerHeightPx = with(LocalDensity.current) { bannerHeight.toPx() }
    val scrollProgress = remember(lazyListState, bannerHeightPx) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) 1f
            else (lazyListState.firstVisibleItemScrollOffset / bannerHeightPx).coerceIn(0f, 1f)
        }
    }
    // the bar frosts once the name has gone under it, with the playlist bar's spring
    val collapsed by remember(scrollProgress) { derivedStateOf { scrollProgress.value > 0.62f } }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed) {
        morph.animateTo(
            targetValue = if (collapsed) 1f else 0f,
            animationSpec = spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    val glassState = remember { GlassState() }

    // the loop is only built once the page has finished opening, and only runs while the banner
    // is at rest and on screen: it repaints the banner and both blur bands every frame it plays
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(420)
        opened = true
    }
    val atRest by remember { derivedStateOf { scrollProgress.value < 0.02f } }
    val obscured = LocalAppContentObscured.current.value
    val edgeBlur = rememberCoverBlurAvailable()

    val isThisQueue = name.isNotEmpty() && queueTitle == name
    val liked = libraryArtist?.artist?.bookmarkedAt != null

    fun playTopSongs() {
        coroutineScope.launch {
            val top = if (page != null) viewModel.topSongs() else emptyList()
            when {
                top.isNotEmpty() -> playerConnection.playQueue(
                    ListQueue(title = name, items = top.map { it.toMediaItem() })
                )
                librarySongs.isNotEmpty() -> playerConnection.playQueue(
                    ListQueue(title = name, items = librarySongs.map { it.toMediaItem() })
                )
                else -> page?.artist?.shuffleEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
            }
        }
    }

    fun openMore(endpoint: BrowseEndpoint?) {
        endpoint ?: return
        navController.navigate("artist/${viewModel.artistId}/items?browseId=${endpoint.browseId}?params=${endpoint.params}")
    }

    fun playSong(song: SongItem) {
        if (song.id == mediaMetadata?.id) {
            playerConnection.togglePlayPause()
        } else {
            playerConnection.playQueue(
                YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata())
            )
        }
    }

    fun showItemMenu(item: YTItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    onImportedPlaylist = { navController.navigate("local_playlist/$it") },
                )
            }
        }
    }

    fun openItem(item: YTItem) {
        when (item) {
            is SongItem -> playSong(item)
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(background.value) }
    ) {
        // the bar's frost samples the banner and the list; it sits beside this box, never in it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { morph.value > 0.01f })
        ) {
            ArtistBanner(
                photoUrl = photo?.resize(1200, 1200),
                stillUrl = appleStill,
                motionUrl = header?.motionUrl?.takeIf { opened && showBackgroundVideo },
                playMotion = opened && atRest && !obscured,
                edgeBlur = edgeBlur,
                height = bannerHeight,
                scrollProgress = { scrollProgress.value },
                background = background,
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = maxWidth
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header", contentType = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = bannerHeight * ArtistAvatarAt)
                                .padding(start = PlaylistInset - 3.dp, end = PlaylistInset)
                        ) {
                            ArtistAvatar(url = header?.avatarUrl ?: photo?.resize(544, 544))
                            Spacer(Modifier.height(14.dp))
                            ArtistName(name = name, logoUrl = header?.logoUrl, logoAspect = header?.logoAspect)
                            val stats = artistStats(
                                subscribers = page?.subscriberCountText?.takeIf { showSubscriberCount },
                                monthly = page?.monthlyListenerCount?.takeIf { showMonthlyListeners },
                            )
                            if (stats.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stats,
                                    style = PlaylistStatsStyle,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            ArtistActions(
                                isPlaying = isThisQueue && isPlaying,
                                liked = liked,
                                subscribed = subscribed,
                                showSubscribe = signedIn && page?.artist?.channelId != null,
                                onPlay = { if (isThisQueue) playerConnection.togglePlayPause() else playTopSongs() },
                                onLike = viewModel::toggleLike,
                                onSubscribe = viewModel::toggleSubscribe,
                            )
                        }
                    }

                    item(key = "release", contentType = "release") {
                        val releaseAlbum = release?.album
                        val releaseSong = release?.song
                        val releasePlaying = isPlaying && (
                            (releaseAlbum != null && mediaMetadata?.album?.id == releaseAlbum.browseId) ||
                                (releaseSong != null && mediaMetadata?.id == releaseSong.id)
                            )
                        ArtistReleasePager(
                            release = release,
                            bio = bio?.takeIf { showArtistDescription },
                            artistName = name,
                            releaseSaved = releaseSaved,
                            isReleasePlaying = releasePlaying,
                            onReleaseClick = {
                                when {
                                    releaseAlbum != null -> navController.navigate("album/${releaseAlbum.browseId}")
                                    releaseSong != null -> playSong(releaseSong)
                                }
                            },
                            onReleasePlay = {
                                when {
                                    releasePlaying -> playerConnection.togglePlayPause()
                                    releaseAlbum != null -> playerConnection.playQueue(YouTubeAlbumRadio(releaseAlbum.playlistId))
                                    releaseSong != null -> playSong(releaseSong)
                                }
                            },
                            onReleaseSave = viewModel::toggleReleaseSaved,
                            onBioClick = {
                                coroutineScope.launch {
                                    // the bio is the last thing before the bottom spacer
                                    val target = (lazyListState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0)
                                    lazyListState.animateScrollToItem(target)
                                }
                            },
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }

                    if (page == null && !isLocalArtist) {
                        item(key = "loading", contentType = "loading") {
                            if (loadFailed) {
                                ArtistRetry(onRetry = viewModel::fetchArtistsFromYTM)
                            } else {
                                Column(modifier = Modifier.padding(top = 28.dp)) {
                                    PlaylistTrackBones(rows = 5)
                                }
                            }
                        }
                    }

                    if (shelves != null) {
                        artistShelves(
                            shelves = shelves,
                            screenWidth = screenWidth,
                            artistId = viewModel.artistId,
                            mediaMetadataId = mediaMetadata?.id,
                            mediaAlbumId = mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            artistName = name,
                            revealSeen = revealSeen,
                            onOpenMore = ::openMore,
                            onOpen = ::openItem,
                            onMenu = ::showItemMenu,
                            onPlaySong = ::playSong,
                            onSimilarTitle = { navController.navigate("artist/${viewModel.artistId}/similar") },
                            topSongsMenu = { dismiss ->
                                page?.artist?.shuffleEndpoint?.let { endpoint ->
                                    ArtistMenuItem(text = "Shuffle", icon = R.drawable.shuffle) {
                                        dismiss()
                                        playerConnection.playQueue(YouTubeQueue(endpoint))
                                    }
                                }
                                page?.artist?.radioEndpoint?.let { endpoint ->
                                    ArtistMenuItem(text = "Start radio", icon = R.drawable.radio) {
                                        dismiss()
                                        playerConnection.playQueue(YouTubeQueue(endpoint))
                                    }
                                }
                                page?.artist?.shareLink?.let { link ->
                                    ArtistMenuItem(text = "Copy link", icon = R.drawable.link) {
                                        dismiss()
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Artist Link", link))
                                        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }

                    // the songs and albums of theirs you've saved, which the old page kept behind a button
                    val savedSongs = if (hideExplicit) librarySongs.filter { !it.song.explicit } else librarySongs
                    if (savedSongs.isNotEmpty()) {
                        librarySection(
                            songs = savedSongs,
                            albums = libraryAlbums,
                            artistName = name,
                            mediaMetadataId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            onSongsTitle = { navController.navigate("artist/${viewModel.artistId}/songs") },
                            onAlbumsTitle = { navController.navigate("artist/${viewModel.artistId}/albums") },
                            onPlay = { index ->
                                val song = savedSongs[index]
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(title = name, items = savedSongs.map { it.toMediaItem() }, startIndex = index)
                                    )
                                }
                            },
                            onSongMenu = { song ->
                                menuState.show {
                                    SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                                }
                            },
                            onAlbum = { navController.navigate("album/${it.id}") },
                            onAlbumMenu = { album ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(originalAlbum = album, navController = navController, onDismiss = menuState::dismiss)
                                }
                            },
                        )
                    }

                    if (!bio.isNullOrBlank() && showArtistDescription) {
                        item(key = "about", contentType = "about") {
                            ArtistAbout(name = name, bio = bio, modifier = Modifier.padding(top = 30.dp))
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(50.dp))
                    }
                }
            }
        }

        ArtistTopBar(
            glassState = glassState,
            morph = { morph.value },
            title = name,
            background = background,
            profileImage = profilePic.ifBlank { null },
            onBackClick = { navController.navigateUp() },
            onBackLongClick = { navController.backToMain() },
            onProfileClick = { navController.navigateToTab("settings") },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.artistShelves(
    shelves: ArtistShelves,
    screenWidth: Dp,
    artistId: String,
    mediaMetadataId: String?,
    mediaAlbumId: String?,
    isPlaying: Boolean,
    artistName: String,
    revealSeen: com.example.musicfy.ui.component.RevealSeenState,
    onOpenMore: (BrowseEndpoint?) -> Unit,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    onSimilarTitle: () -> Unit,
    topSongsMenu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    shelves.topSongs?.let { section ->
        val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
        item(key = "top_songs_title", contentType = "shelf_title") {
            ArtistShelfTitle(
                title = "Top songs",
                onClick = section.moreEndpoint?.let { { onOpenMore(it) } },
                menu = topSongsMenu,
                modifier = Modifier
                    .padding(top = 30.dp, bottom = 8.dp)
                    .revealOnAppear(key = "top_songs_title", seenState = revealSeen),
            )
        }
        item(key = "top_songs", contentType = "track_grid") {
            val rows = minOf(3, songs.size)
            val gridState = rememberLazyGridState()
            val snap = remember(gridState) {
                SnapLayoutInfoProvider(lazyGridState = gridState, positionInLayout = { _, _ -> 0f })
            }
            val columnWidth = if (screenWidth * 0.475f >= 320.dp) screenWidth * 0.475f else screenWidth * 0.76f
            LazyHorizontalGrid(
                state = gridState,
                rows = GridCells.Fixed(rows.coerceAtLeast(1)),
                flingBehavior = rememberSnapFlingBehavior(snap),
                contentPadding = PaddingValues(horizontal = PlaylistInset),
                horizontalArrangement = Arrangement.spacedBy(39.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeRowHeight * rows + 6.dp * (rows - 1).coerceAtLeast(0))
                    .revealOnAppear(key = "top_songs", seenState = revealSeen),
            ) {
                itemsIndexed(items = songs, key = { _, song -> song.id }) { _, song ->
                    Box(modifier = Modifier.width(columnWidth)) {
                        HomeTrackRow(
                            thumbnailUrl = song.thumbnail,
                            title = song.title,
                            subtitle = song.album?.name ?: song.artists.joinToString { it.name },
                            isActive = song.id == mediaMetadataId,
                            isPlaying = isPlaying,
                            onClick = { onPlaySong(song) },
                            onLongClick = { onMenu(song) },
                            onMoreClick = { onMenu(song) },
                        )
                    }
                }
            }
        }
    }

    // the first album shelf is albums, the next singles & EPs, whatever YouTube calls them
    shelves.albumShelves.forEachIndexed { index, section ->
        coverShelf(
            key = "albums_$index",
            title = when (index) {
                0 -> "Albums"
                1 -> "Singles & EPs"
                else -> section.title
            },
            items = section.items,
            moreEndpoint = section.moreEndpoint,
            revealSeen = revealSeen,
            mediaAlbumId = mediaAlbumId,
            isPlaying = isPlaying,
            onOpenMore = onOpenMore,
            onOpen = onOpen,
            onMenu = onMenu,
        )
    }

    videoShelf(
        key = "videos",
        title = "Videos",
        videos = shelves.videos,
        moreEndpoint = shelves.videoShelf?.moreEndpoint,
        screenWidth = screenWidth,
        artistName = artistName,
        revealSeen = revealSeen,
        mediaMetadataId = mediaMetadataId,
        isPlaying = isPlaying,
        onOpenMore = onOpenMore,
        onPlay = onPlaySong,
        onMenu = onMenu,
    )

    videoShelf(
        key = "live",
        title = "Concert",
        videos = shelves.live,
        moreEndpoint = shelves.liveShelf?.moreEndpoint,
        screenWidth = screenWidth,
        artistName = artistName,
        revealSeen = revealSeen,
        mediaMetadataId = mediaMetadataId,
        isPlaying = isPlaying,
        onOpenMore = onOpenMore,
        onPlay = onPlaySong,
        onMenu = onMenu,
    )

    shelves.playlistShelves.forEachIndexed { index, section ->
        coverShelf(
            key = "playlists_$index",
            title = if (index == 0) "Featured on" else section.title,
            items = section.items,
            moreEndpoint = section.moreEndpoint,
            revealSeen = revealSeen,
            mediaAlbumId = mediaAlbumId,
            isPlaying = isPlaying,
            onOpenMore = onOpenMore,
            onOpen = onOpen,
            onMenu = onMenu,
        )
    }

    shelves.similar?.let { section ->
        val artists = section.items.filterIsInstance<ArtistItem>()
            .distinctBy { it.id }
            .map { FeaturedArtistUi(id = it.id, name = it.title, thumbnailUrl = it.thumbnail?.resize(300, 300)) }
        item(key = "similar", contentType = "artist_row") {
            FeaturedArtistsRow(
                artists = artists,
                onArtistClick = { artist -> onOpen(section.items.first { it.id == artist.id }) },
                title = "Similar Artist",
                onTitleClick = onSimilarTitle,
                modifier = Modifier
                    .padding(top = 30.dp)
                    .revealOnAppear(key = "similar", seenState = revealSeen),
            )
        }
    }

    shelves.others.forEachIndexed { index, section ->
        coverShelf(
            key = "other_$index",
            title = section.title,
            items = section.items,
            moreEndpoint = section.moreEndpoint,
            revealSeen = revealSeen,
            mediaAlbumId = mediaAlbumId,
            isPlaying = isPlaying,
            onOpenMore = onOpenMore,
            onOpen = onOpen,
            onMenu = onMenu,
        )
    }
}

/** a titled row of Home's square cards (round for artists) */
private fun LazyListScope.coverShelf(
    key: String,
    title: String,
    items: List<YTItem>,
    moreEndpoint: BrowseEndpoint?,
    revealSeen: com.example.musicfy.ui.component.RevealSeenState,
    mediaAlbumId: String?,
    isPlaying: Boolean,
    onOpenMore: (BrowseEndpoint?) -> Unit,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    val distinct = items.distinctBy { it.id }
    if (distinct.isEmpty()) return
    item(key = "${key}_title", contentType = "shelf_title") {
        ArtistShelfTitle(
            title = title,
            onClick = moreEndpoint?.let { { onOpenMore(it) } },
            modifier = Modifier
                .padding(top = 30.dp, bottom = 8.dp)
                .revealOnAppear(key = "${key}_title", seenState = revealSeen),
        )
    }
    item(key = "${key}_row", contentType = "cover_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PlaylistInset),
            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            modifier = Modifier.revealOnAppear(key = "${key}_row", seenState = revealSeen),
        ) {
            items(items = distinct, key = { it.id }) { item ->
                HomeCoverCard(
                    title = item.title,
                    subtitle = when (item) {
                        is AlbumItem -> item.year?.toString()
                        is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
                        is SongItem -> item.artists.joinToString { it.name }
                        is ArtistItem -> null
                    },
                    thumbnailUrl = item.thumbnail,
                    circular = item is ArtistItem,
                    isActive = item is AlbumItem && item.id == mediaAlbumId,
                    isPlaying = isPlaying,
                    sharedElementKey = com.example.musicfy.ui.component.coverTransitionKey(item),
                    modifier = Modifier.combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = { onOpen(item) },
                        onLongClick = { onMenu(item) },
                    ),
                )
            }
        }
    }
}

/** Home's wide video banner, in a row */
private fun LazyListScope.videoShelf(
    key: String,
    title: String,
    videos: List<SongItem>,
    moreEndpoint: BrowseEndpoint?,
    screenWidth: Dp,
    artistName: String,
    revealSeen: com.example.musicfy.ui.component.RevealSeenState,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    onOpenMore: (BrowseEndpoint?) -> Unit,
    onPlay: (SongItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    if (videos.isEmpty()) return
    item(key = "${key}_title", contentType = "shelf_title") {
        ArtistShelfTitle(
            title = title,
            onClick = moreEndpoint?.let { { onOpenMore(it) } },
            modifier = Modifier
                .padding(top = 30.dp, bottom = 8.dp)
                .revealOnAppear(key = "${key}_title", seenState = revealSeen),
        )
    }
    item(key = "${key}_row", contentType = "video_row") {
        val cardWidth = screenWidth - PlaylistInset * 2 - 14.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = PlaylistInset),
            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            modifier = Modifier.revealOnAppear(key = "${key}_row", seenState = revealSeen),
        ) {
            items(items = videos.distinctBy { it.id }, key = { it.id }) { video ->
                val tidy = remember(video.id, artistName) { ArtistVideos.homeVideo(video, artistName) }
                HomeVideoCard(
                    title = tidy.title,
                    subtitle = listOfNotNull(tidy.subtitle.ifBlank { null }, video.duration?.let { makeTimeString(it * 1000L) })
                        .joinToString(" · "),
                    videoId = video.id,
                    fallbackThumbnailUrl = video.thumbnail,
                    width = cardWidth,
                    isActive = video.id == mediaMetadataId,
                    isPlaying = isPlaying,
                    modifier = Modifier.combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = { onPlay(video) },
                        onLongClick = { onMenu(video) },
                    ),
                )
            }
        }
    }
}

private fun LazyListScope.librarySection(
    songs: List<Song>,
    albums: List<Album>,
    artistName: String,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    onSongsTitle: () -> Unit,
    onAlbumsTitle: () -> Unit,
    onPlay: (Int) -> Unit,
    onSongMenu: (Song) -> Unit,
    onAlbum: (Album) -> Unit,
    onAlbumMenu: (Album) -> Unit,
) {
    item(key = "library_title", contentType = "shelf_title") {
        ArtistShelfTitle(
            title = "In your library",
            onClick = onSongsTitle,
            modifier = Modifier.padding(top = 30.dp, bottom = 2.dp),
        )
    }
    items(items = songs.take(5).withIndex().toList(), key = { "library_${it.value.id}" }, contentType = { "track" }) { (index, song) ->
        PlaylistTrackRow(
            thumbnailUrl = song.song.thumbnailUrl,
            title = song.song.title,
            subtitle = listOfNotNull(
                song.album?.title,
                makeTimeString(song.song.duration * 1000L),
            ).joinToString(" · "),
            isActive = song.id == mediaMetadataId,
            isPlaying = isPlaying,
            onClick = { onPlay(index) },
            onLongClick = { onSongMenu(song) },
            onMenuClick = { onSongMenu(song) },
        )
    }
    if (albums.isNotEmpty()) {
        item(key = "library_albums_title", contentType = "shelf_title") {
            ArtistShelfTitle(
                title = "Albums of $artistName you saved",
                onClick = onAlbumsTitle,
                modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
            )
        }
        item(key = "library_albums", contentType = "cover_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PlaylistInset),
                horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            ) {
                items(items = albums, key = { it.id }) { album ->
                    HomeCoverCard(
                        title = album.album.title,
                        subtitle = album.album.year?.toString(),
                        thumbnailUrl = album.album.thumbnailUrl,
                        sharedElementKey = "album-${album.id}",
                        modifier = Modifier.combinedClickable(
                            interactionSource = null,
                            indication = HomeCardHighlight,
                            onClick = { onAlbum(album) },
                            onLongClick = { onAlbumMenu(album) },
                        ),
                    )
                }
            }
        }
    }
}

/** the bio at the foot of the page: six lines, and a tap opens the rest in place */
@Composable
private fun ArtistAbout(name: String, bio: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset)
    ) {
        Text(
            text = "About $name",
            style = AboutTitleStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = bio,
            style = AboutBodyStyle,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 420f))
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = null, indication = null) { expanded = !expanded },
        )
    }
}

@Composable
private fun ArtistRetry(onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        Text(
            text = "Couldn't load this artist. Tap to try again",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
