// OnlinePlaylistScreen.kt

package com.example.musicfy.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.playback.queues.YouTubePlaylistQueue
import com.example.musicfy.playback.queues.YouTubeQueue
import androidx.compose.material3.IconButton
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistSectionTitle
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSelectionSongMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.menu.toggleSavedPlaylist
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.viewmodels.OnlinePlaylistViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val downloadUtil = LocalDownloadUtil.current

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs.mapIndexed { i, s -> i to s }
        else songs.mapIndexed { i, s -> i to s }.filter {
            it.second.title.contains(query.text, true) ||
                    it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val coverUrl = playlist?.thumbnail ?: songs.firstOrNull { !it.thumbnail.isNullOrEmpty() }?.thumbnail
    val totalSeconds = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val isThisQueue = playlist != null && queueTitle == playlist?.title

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlistId}",
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        showCover = !isSearching,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        onMoreClick = playlist?.let { currentPlaylist ->
            {
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = currentPlaylist,
                        songs = songs,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                        onImportedPlaylist = { playlistId ->
                            navController.navigate("local_playlist/$playlistId")
                        },
                    )
                }
            }
        },
        topBarOverride = if (inSelectMode || isSearching) {
            {
                OnlinePlaylistSearchBar(
                    inSelectMode = inSelectMode,
                    isSearching = isSearching,
                    query = query,
                    onQueryChange = { query = it },
                    focusRequester = focusRequester,
                    selectionCount = selection.size,
                    allSelected = selection.size == filteredSongs.size && selection.isNotEmpty(),
                    onToggleSelectAll = {
                        if (selection.size == filteredSongs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(filteredSongs.map { it.second.id })
                        }
                    },
                    onSelectionMenu = {
                        menuState.show {
                            YouTubeSelectionSongMenu(
                                songSelection = filteredSongs.filter { it.second.id in selection }.map { it.second },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode
                            )
                        }
                    },
                    onStartSearch = { isSearching = true },
                    onBack = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (inSelectMode) {
                            onExitSelectionMode()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onBackLong = {
                        if (!isSearching && !inSelectMode) navController.backToMain()
                    },
                )
            }
        } else null,
        overlay = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null || songs.isEmpty()) {
            if (isLoading) {
                item(key = "loading_header") {
                    Spacer(Modifier.height(com.example.musicfy.ui.component.detail.playlistCoverHeight() * 0.975f))
                }
                item(key = "loading_tracks") {
                    PlaylistTrackBones()
                }
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) {
            item(key = "playlist_header") {
                val isEditableRemote = currentPlaylist.id == "LM" || currentPlaylist.isEditable
                PlaylistHeader(
                    title = currentPlaylist.title,
                    creators = listOfNotNull(
                        currentPlaylist.author?.let { CreatorUi(name = it.name, artistId = it.id) }
                    ),
                    stats = playlistStats(context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size), totalSeconds),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = songs,
                                    initialContinuation = viewModel.continuation
                                )
                            )
                        }
                    },
                    onShuffleClick = {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = currentPlaylist.id,
                                playlistTitle = currentPlaylist.title,
                                initialSongs = songs.shuffled(),
                                initialContinuation = viewModel.continuation
                            )
                        )
                    },
                    isSaved = if (isEditableRemote) null else dbPlaylist?.playlist?.bookmarkedAt != null,
                    onSaveClick = {
                        database.toggleSavedPlaylist(currentPlaylist, dbPlaylist, songs, coroutineScope)
                    },
                )
            }
        }

        itemsIndexed(filteredSongs, key = { _, (index, song) -> "${index}_${song.id}" }) { position, (_, songItem) ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(songItem.id) else selection.remove(songItem.id)
            }

            SwipeActionsBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                start = { librarySwipeAction(songItem) },
                end = { queueSwipeAction { songItem.toMediaItem() } },
            ) {
                PlaylistTrackRow(
                    thumbnailUrl = songItem.thumbnail,
                    title = songItem.title,
                    subtitle = "${songItem.artists.joinToString { it.name }} • ${makeTimeString(songItem.duration?.times(1000L))}",
                    isActive = mediaMetadata?.id == songItem.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${songItem.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(songItem.id !in selection)
                        } else if (songItem.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = filteredSongs.map { it.second },
                                    initialContinuation = viewModel.continuation,
                                    startIndex = position
                                )
                            )
                        }
                    },
                    onLongClick = {
                        if (!inSelectMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            inSelectMode = true
                            onCheckedChange(true)
                        }
                    },
                    onMenuClick = {
                        menuState.show {
                            YouTubeSongMenu(songItem, navController, menuState::dismiss)
                        }
                    },
                    trailing = if (inSelectMode) {
                        { Checkbox(checked = songItem.id in selection, onCheckedChange = onCheckedChange) }
                    } else null,
                )
            }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                PlaylistTrackBones(rows = 2)
            }
        }

        if (!isSearching) {
            if (!isLoadingMore && viewModel.continuation == null) {
                item(key = "end_of_line") {
                    PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
                }
            }

            if (featuredArtists.isNotEmpty()) {
                item(key = "featured_artists") {
                    FeaturedArtistsRow(
                        artists = featuredArtists,
                        onArtistClick = { navController.navigate("artist/${it.id}") },
                        modifier = Modifier.padding(top = 25.dp),
                    )
                }
            }

            if (relatedItems.isNotEmpty()) {
                item(key = "related_title") {
                    PlaylistSectionTitle(
                        title = "Related Playlists",
                        modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
                    )
                }

                item(key = "related_items") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(relatedItems) { item ->
                            YouTubeGridItem(
                                item = item,
                                modifier = Modifier
                                    .width(160.dp)
                                    .combinedClickable(
                                        onClick = {
                                            when (item) {
                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                is AlbumItem -> navController.navigate("album/${item.browseId}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                is SongItem -> playerConnection.playQueue(
                                                    YouTubeQueue(WatchEndpoint(videoId = item.id))
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                when (item) {
                                                    is PlaylistItem -> YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                        onImportedPlaylist = { playlistId ->
                                                            navController.navigate("local_playlist/$playlistId")
                                                        }
                                                    )
                                                    is SongItem -> YouTubeSongMenu(
                                                        song = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is AlbumItem -> YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is ArtistItem -> YouTubeArtistMenu(
                                                        artist = item,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

/** "21 songs · 1h 23m" */
internal fun playlistStats(songCountText: String, totalSeconds: Int): String {
    if (totalSeconds <= 0) return songCountText
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val duration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$songCountText · $duration"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlinePlaylistSearchBar(
    inSelectMode: Boolean,
    isSearching: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    selectionCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onSelectionMenu: () -> Unit,
    onStartSearch: () -> Unit,
    onBack: () -> Unit,
    onBackLong: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        title = {
            if (inSelectMode) {
                Text(
                    text = pluralStringResource(R.plurals.n_song, selectionCount, selectionCount),
                    style = MaterialTheme.typography.titleLarge
                )
            } else if (isSearching) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        navigationIcon = {
            com.example.musicfy.ui.component.IconButton(onClick = onBack, onLongClick = onBackLong) {
                Icon(
                    painter = painterResource(if (inSelectMode) R.drawable.close else R.drawable.arrow_back_ios),
                    contentDescription = null
                )
            }
        },
        actions = {
            if (inSelectMode) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
                IconButton(
                    enabled = selectionCount > 0,
                    onClick = onSelectionMenu
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            } else if (!isSearching) {
                IconButton(onClick = onStartSearch) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null
                    )
                }
            }
        }
    )
}
