// AutoPlaylistScreen.kt

package com.example.musicfy.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.LastPlayedLikedSongsTimeKey
import com.example.musicfy.constants.SongSortDescendingKey
import com.example.musicfy.constants.SongSortType
import com.example.musicfy.constants.SongSortTypeKey
import com.example.musicfy.constants.YtmSyncKey
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.ui.component.DraggableScrollbar
import com.example.musicfy.ui.component.EmptyPlaceholder
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistSortControl
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.playlistCoverHeight
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.AutoPlaylistMenu
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val playlist = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liked)
        "uploaded" -> stringResource(R.string.uploaded_playlist)
        "downloaded" -> stringResource(R.string.offline)
        "local" -> "Local Songs"
        "songs" -> "All Songs"
        "recently_added" -> "Recently Added"
        else -> viewModel.playlist
    }

    val songs by viewModel.likedSongs.collectAsState(null)
    val revealSeen = rememberRevealSeenState()

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val totalSeconds = remember(songs) { songs?.fastSumBy { it.song.duration } ?: 0 }

    val playlistType = when (viewModel.playlist) {
        "liked" -> PlaylistType.LIKE
        "downloaded" -> PlaylistType.DOWNLOAD
        "uploaded" -> PlaylistType.UPLOADED
        else -> PlaylistType.OTHER
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

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
                if (playlistType == PlaylistType.UPLOADED) viewModel.syncUploadedSongs()
            }
        }
    }

    LaunchedEffect(songs) {
        val current = songs
        if (current.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (current.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (current.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs?.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs ?: emptyList()
        else songs?.filter { song ->
            song.song.title.contains(query.text, true) ||
                song.artists.any { it.name.contains(query.text, true) }
        } ?: emptyList()
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.orEmpty().flatMap { it.artists }, id = { it.id }, name = { it.name })
    }

    val state = rememberLazyListState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val canRefresh = playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED
    val coverUrl = remember(songs) {
        songs?.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl
    }

    fun rememberLikedPlay() {
        if (viewModel.playlist == "liked") {
            coroutineScope.launch {
                context.dataStore.edit { it[LastPlayedLikedSongsTimeKey] = System.currentTimeMillis() }
            }
        }
    }

    fun playAll(items: List<Song>, startIndex: Int = 0) {
        rememberLikedPlay()
        playerConnection.playQueue(
            ListQueue(title = playlist, items = items.map { it.toMediaItem() }, startIndex = startIndex)
        )
    }

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlist}",
        coverUrl = coverUrl,
        lazyListState = state,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        showCover = !isSearching,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = if (canRefresh) {
            Modifier.pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            )
        } else Modifier,
        onMoreClick = {
            val current = songs.orEmpty()
            menuState.show {
                AutoPlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(current.map { it.toMediaItem() }) },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                            Download.STATE_DOWNLOADING -> {
                                current.forEach { song ->
                                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.song.id, false)
                                }
                            }
                            else -> {
                                current.forEach { song ->
                                    val downloadRequest = DownloadRequest
                                        .Builder(song.song.id, song.song.id.toUri())
                                        .setCustomCacheKey(song.song.id)
                                        .setData(song.song.title.toByteArray())
                                        .build()
                                    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
                                }
                            }
                        }
                    },
                    onDismiss = { menuState.dismiss() }
                )
            }
        },
        topBarOverride = if (inSelectMode || isSearching) {
            {
                AutoPlaylistSearchBar(
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
                            selection.addAll(filteredSongs.map { it.id })
                        }
                    },
                    onSelectionMenu = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = filteredSongs.filter { it.id in selection },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode,
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
            DraggableScrollbar(
                modifier = Modifier
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .align(Alignment.CenterEnd),
                scrollState = state,
                headerItems = if (isSearching) 0 else 1
            )
            if (canRefresh) {
                Indicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            }
        },
    ) {
        val current = songs
        if (current == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (current.isEmpty()) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                )
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) {
            item(key = "playlist_header") {
                val isThisQueue = queueTitle == playlist
                PlaylistHeader(
                    title = playlist,
                    creators = emptyList(),
                    stats = playlistStats(
                        context.resources.getQuantityString(R.plurals.n_song, current.size, current.size),
                        totalSeconds,
                    ),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) playerConnection.togglePlayPause() else playAll(current)
                    },
                    onShuffleClick = { playAll(current.shuffled()) },
                    sortControl = {
                        PlaylistSortControl(
                            current = sortType,
                            descending = sortDescending,
                            options = SongSortType.entries,
                            label = { type ->
                                stringResource(
                                    when (type) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                )
                            },
                            onSelect = onSortTypeChange,
                            onToggleDirection = { onSortDescendingChange(!sortDescending) },
                        )
                    },
                )
            }
        }

        itemsIndexed(
            items = filteredSongs,
            key = { _, song -> song.id },
        ) { index, song ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(song.id) else selection.remove(song.id)
            }

            SwipeActionsBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                start = { librarySwipeAction(song.song) },
                end = { queueSwipeAction { song.toMediaItem() } },
            ) {
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    title = song.song.title,
                    subtitle = "${song.artists.joinToString { it.name }} • ${makeTimeString(song.song.duration * 1000L)}",
                    isActive = song.song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${song.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(index, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playAll(current, startIndex = current.indexOfFirst { it.id == song.id })
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
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    trailing = if (inSelectMode) {
                        { Checkbox(checked = song.id in selection, onCheckedChange = onCheckedChange) }
                    } else null,
                )
            }
        }

        if (!isSearching) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = current.size, totalSeconds = totalSeconds)
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
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPlaylistSearchBar(
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
                    onClick = onSelectionMenu,
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

enum class PlaylistType {
    LIKE, DOWNLOAD, UPLOADED, OTHER
}
