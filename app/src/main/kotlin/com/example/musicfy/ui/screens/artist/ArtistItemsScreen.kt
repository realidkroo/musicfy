// ArtistItemsScreen.kt

package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.HomeCardHighlight
import com.example.musicfy.ui.component.HomeVideoCard
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistStatsStyle
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.playlistCoverHeight
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.screens.library.LibraryIndexRail
import com.example.musicfy.ui.screens.library.indexSections
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.ArtistVideos
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.viewmodels.ArtistItemsViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import kotlinx.coroutines.launch

/** what a shelf's page holds, read off its items: YouTube's titles are in the account's language */
private enum class ItemsKind(val word: String) {
    Songs("songs"), Albums("albums"), Videos("videos"), Playlists("playlists"), Artists("artists")
}

private fun kindOf(items: List<YTItem>): ItemsKind? {
    val first = items.firstOrNull() ?: return null
    return when (first) {
        is SongItem -> {
            val songs = items.filterIsInstance<SongItem>()
            if (songs.count { it.album != null } * 2 >= songs.size) ItemsKind.Songs else ItemsKind.Videos
        }
        is AlbumItem -> ItemsKind.Albums
        is PlaylistItem -> ItemsKind.Playlists
        is ArtistItem -> ItemsKind.Artists
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val pageTitle by viewModel.title.collectAsState()
    val itemsPage by viewModel.itemsPage.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val items = remember(itemsPage) { itemsPage?.items.orEmpty().distinctBy { it.id } }
    val kind = remember(items) { kindOf(items) }

    val (bannerName, bannerUrl) = rememberArtistBanner(viewModel.artistId)
    val artistName = bannerName.orEmpty()
    // the videos page wears a video's own frame instead of the artist's banner
    val coverUrl = if (kind == ItemsKind.Videos) {
        items.firstOrNull()?.id?.let { "https://i.ytimg.com/vi/$it/hq720.jpg" }
    } else {
        bannerUrl
    }

    // "Albums" -> "twenty one pilots albums"; a page titled with the artist's own name says what it holds
    val heading = when {
        kind == ItemsKind.Songs -> if (artistName.isNotBlank()) "Top $artistName songs" else pageTitle
        artistName.isBlank() -> pageTitle
        pageTitle.isBlank() || pageTitle.equals(artistName, ignoreCase = true) -> "$artistName ${kind?.word.orEmpty()}"
        else -> "$artistName ${pageTitle.replaceFirstChar { it.lowercase() }}"
    }

    var isGrid by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val gridMode = isGrid ?: (kind == ItemsKind.Albums || kind == ItemsKind.Playlists || kind == ItemsKind.Artists)

    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading_more" } }
            .collect { if (it) viewModel.loadMore() }
    }

    val songs = remember(items) { items.filterIsInstance<SongItem>() }
    val isThisQueue = heading.isNotBlank() && queueTitle == heading

    fun playSongs(start: SongItem? = null, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = heading,
                items = list.map { it.toMediaItem() },
                startIndex = start?.let { list.indexOf(it).coerceAtLeast(0) } ?: 0,
            )
        )
    }

    fun showMenu(item: YTItem) {
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

    fun open(item: YTItem) {
        when (item) {
            is SongItem -> if (item.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else if (kind == ItemsKind.Songs) {
                playSongs(start = item)
            } else {
                playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
            }
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    // the A-Z list: rows in index order, and where each letter starts for the rail
    val indexed = remember(items, gridMode, kind) {
        if (!gridMode && (kind == ItemsKind.Albums || kind == ItemsKind.Playlists)) indexSections(items) { it.title } else null
    }
    val rows = remember(indexed, items) { indexed?.flatMap { it.second } ?: items }
    val sectionStarts = remember(indexed) {
        buildMap {
            var position = 0
            indexed?.forEach { (section, entries) ->
                // +1 for the header item above the rows
                put(section.key, position + 1)
                position += entries.size
            }
        }
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        overlay = {
            if (indexed != null && rows.size >= 12) {
                LibraryIndexRail(
                    sections = indexed.map { it.first },
                    onSectionSelected = { key ->
                        sectionStarts[key]?.let { index -> coroutineScope.launch { lazyListState.animateScrollToItem(index) } }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        if (itemsPage == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_rows") {
                if (loadFailed) ItemsRetry(onRetry = viewModel::retry) else PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        item(key = "header", contentType = "header") {
            if (kind == ItemsKind.Songs) {
                PlaylistHeader(
                    title = heading,
                    creators = listOfNotNull(
                        artistName.takeIf { it.isNotBlank() }?.let {
                            CreatorUi(name = it, avatarUrl = viewModel.artistId?.let { id -> com.example.musicfy.utils.ArtistPageCache.header(id)?.avatarUrl }, artistId = viewModel.artistId)
                        }
                    ),
                    stats = "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else playSongs() },
                    onShuffleClick = { playSongs(shuffled = true) },
                    onCreatorClick = { creator -> creator.artistId?.let { navController.navigate("artist/$it") } },
                )
            } else {
                ArtistSubpageHeader(
                    title = heading,
                    subtitle = "${items.size}${if (itemsPage?.continuation != null) "+" else ""} ${kind?.word.orEmpty()}",
                    // the artists' page is a grid of faces only
                    isGrid = if (kind == ItemsKind.Artists) null else gridMode,
                    onGridChange = { isGrid = it },
                )
            }
        }

        when {
            kind == ItemsKind.Songs -> itemsIndexed(rows, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { position, item ->
                val song = item as SongItem
                SwipeActionsBox(
                    modifier = Modifier.animateItem(),
                    start = { librarySwipeAction(song) },
                    end = { queueSwipeAction { song.toMediaItem() } },
                ) {
                    PlaylistTrackRow(
                        thumbnailUrl = song.thumbnail,
                        title = song.title,
                        subtitle = listOfNotNull(
                            song.album?.name ?: song.artists.joinToString { it.name },
                            song.duration?.let { makeTimeString(it * 1000L) },
                        ).joinToString(" • "),
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.revealOnAppear(key = "song_${song.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                        onClick = { open(song) },
                        onLongClick = { showMenu(song) },
                        onMenuClick = { showMenu(song) },
                    )
                }
            }

            kind == ItemsKind.Videos && !gridMode -> items(rows, key = { it.id }, contentType = { "video_card" }) { item ->
                val video = item as SongItem
                val tidy = remember(video.id, artistName) { ArtistVideos.homeVideo(video, artistName) }
                HomeVideoCard(
                    title = tidy.title,
                    subtitle = listOfNotNull(
                        tidy.subtitle.ifBlank { null },
                        video.duration?.let { makeTimeString(it * 1000L) },
                    ).joinToString(" · "),
                    videoId = video.id,
                    fallbackThumbnailUrl = video.thumbnail,
                    width = screenWidth - PlaylistInset * 2,
                    isActive = video.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .padding(horizontal = PlaylistInset, vertical = 7.dp)
                        .animateItem()
                        .revealOnAppear(key = "video_${video.id}", seenState = revealSeen)
                        .combinedClickable(
                            interactionSource = null,
                            indication = HomeCardHighlight,
                            onClick = { open(video) },
                            onLongClick = { showMenu(video) },
                        ),
                )
            }

            gridMode -> items(rows.chunked(2), key = { pair -> pair.joinToString("|") { it.id } }, contentType = { "grid_row" }) { pair ->
                SubpageGridRow(
                    items = pair,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .animateItem()
                        .revealOnAppear(key = "grid_${pair.first().id}", seenState = revealSeen),
                ) { item ->
                    when (item) {
                        is SongItem -> {
                            val tidy = remember(item.id, artistName) { ArtistVideos.homeVideo(item, artistName) }
                            SubpageVideoCell(
                                title = tidy.title,
                                subtitle = item.duration?.let { makeTimeString(it * 1000L) },
                                videoId = item.id,
                                fallbackThumbnailUrl = item.thumbnail,
                                isActive = item.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onClick = { open(item) },
                                onLongClick = { showMenu(item) },
                            )
                        }
                        else -> SubpageSquareCell(
                            title = item.title,
                            subtitle = cellSubtitle(item),
                            thumbnailUrl = item.thumbnail,
                            round = item is ArtistItem,
                            sharedElementKey = com.example.musicfy.ui.component.coverTransitionKey(item),
                            isActive = item is AlbumItem && item.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            onClick = { open(item) },
                            onLongClick = { showMenu(item) },
                        )
                    }
                }
            }

            else -> itemsIndexed(rows, key = { _, it -> it.id }, contentType = { _, _ -> "row" }) { position, item ->
                PlaylistTrackRow(
                    thumbnailUrl = item.thumbnail,
                    title = item.title,
                    subtitle = cellSubtitle(item).orEmpty(),
                    isActive = item is AlbumItem && item.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    coverShape = if (item is ArtistItem) CircleShape else com.example.musicfy.ui.component.detail.TrackShape,
                    coverModifier = Modifier.containerTransformSource(
                        key = com.example.musicfy.ui.component.coverTransitionKey(item),
                        cornerRadius = 9.dp,
                        coverUrl = item.thumbnail,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .revealOnAppear(key = "row_${item.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                    onClick = { open(item) },
                    onLongClick = { showMenu(item) },
                    onMenuClick = { showMenu(item) },
                )
            }
        }

        if (itemsPage?.continuation != null) {
            item(key = "loading_more") {
                PlaylistTrackBones(rows = 2)
            }
        } else if (kind == ItemsKind.Songs && songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = songs.sumOf { it.duration ?: 0 })
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

private fun cellSubtitle(item: YTItem): String? = when (item) {
    is AlbumItem -> item.year?.toString()
    is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
    is SongItem -> item.artists.joinToString { it.name }.ifBlank { null }
    is ArtistItem -> null
}

@Composable
private fun ItemsRetry(onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Couldn't load this page. Tap to try again",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
