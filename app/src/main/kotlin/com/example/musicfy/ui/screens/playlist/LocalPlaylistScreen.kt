// LocalPlaylistScreen.kt

package com.example.musicfy.ui.screens.playlist

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.AccountNameKey
import com.example.musicfy.constants.PlaylistEditLockKey
import com.example.musicfy.constants.PlaylistSongSortDescendingKey
import com.example.musicfy.constants.PlaylistSongSortType
import com.example.musicfy.constants.PlaylistSongSortTypeKey
import com.example.musicfy.constants.PlaylistSortType
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.SongSortType
import com.example.musicfy.db.entities.PlaylistEvent
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.extensions.move
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.BoneColor
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.ui.component.DraggableScrollbar
import com.example.musicfy.ui.component.EmptyPlaceholder
import com.example.musicfy.playlistcover.PlaylistCoverStore
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.showEditPlaylistSheet
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.TextFieldDialog
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.removeFromPlaylistSwipeAction
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistSectionTitle
import com.example.musicfy.ui.component.detail.PlaylistSortControl
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.playlistCoverHeight
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.LocalPlaylistMenu
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.LocalPlaylistViewModel
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val mutableSongs = remember { mutableStateListOf<com.example.musicfy.db.entities.PlaylistSong>() }
    val playlistLength = remember(songs) { songs.fastSumBy { it.song.song.duration } }
    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    var showSortDialog by remember { mutableStateOf(false) }
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.song.song.title.contains(query.text, ignoreCase = true) ||
                    song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
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

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
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
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist?.playlist?.name.orEmpty()),
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
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.song.id, false)
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist?.playlist?.name.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                            // Its generated or picked cover is no use to anyone now.
                            PlaylistCoverStore.deleteStale(context.applicationContext, viewModel.playlistId, null)
                        }
                        navController.popBackStack()
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    var showAddSongsDialog by remember { mutableStateOf(false) }
    if (showAddSongsDialog) {
        playlist?.let { currentPlaylist ->
            AddSongsToPlaylistDialog(
                playlistId = currentPlaylist.id,
                existingSongIds = songs.map { it.song.id }.toSet(),
                nextPosition = songs.size,
                onDismiss = { showAddSongsDialog = false },
            )
        }
    }

    // the header is the only item above the songs, and only while not searching
    val headerItems by rememberUpdatedState(if (isSearching) 0 else 1)
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                currentDragInfo.first to (to.index - headerItems)
            }
            mutableSongs.move(from.index - headerItems, to.index - headerItems)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                database.transaction {
                    move(viewModel.playlistId, from, to)
                }

                if (viewModel.playlist.value?.playlist?.browseId != null) {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val successorIndex = if (from > to) to else to + 1
                        val successorSetVideoId = playlistSongMap.getOrNull(successorIndex)?.setVideoId

                        playlistSongMap.getOrNull(from)?.setVideoId?.let { setVideoId ->
                            YouTube.moveSongPlaylist(
                                viewModel.playlist.value?.playlist?.browseId!!,
                                setVideoId,
                                successorSetVideoId
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val coverUrl = remember(playlist, songs) {
        playlist?.thumbnails?.firstOrNull()
            ?: songs.firstOrNull { !it.song.song.thumbnailUrl.isNullOrEmpty() }?.song?.song?.thumbnailUrl
    }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.song.artists }, id = { it.id }, name = { it.name })
    }
    val creatorAvatar = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }

    fun play(items: List<com.example.musicfy.db.entities.PlaylistSong>, startIndex: Int = 0) {
        val current = playlist ?: return
        playerConnection.playQueue(
            ListQueue(
                title = current.playlist.name,
                items = items.map { it.song.toMediaItem() },
                startIndex = startIndex,
            )
        )
        database.query { insert(PlaylistEvent(playlistId = current.id, timestamp = LocalDateTime.now())) }
    }

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
                    LocalPlaylistMenu(
                        playlist = currentPlaylist,
                        songs = songs,
                        context = context,
                        downloadState = downloadState,
                        // Over the menu, not instead of it: Done comes back to it.
                        onEdit = {
                            val top = songs.minWithOrNull(compareBy({ it.map.position }, { it.map.id }))?.song
                            menuState.showEditPlaylistSheet(
                                playlist = currentPlaylist.playlist,
                                database = database,
                                context = context,
                                topSongId = top?.id,
                                topSongArtworkUrl = top?.song?.thumbnailUrl,
                            )
                        },
                        onSync = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val playlistPage = YouTube.playlist(currentPlaylist.playlist.browseId!!)
                                    .completed()
                                    .getOrNull() ?: return@launch
                                database.transaction {
                                    clearPlaylist(currentPlaylist.id)
                                    playlistPage.songs
                                        .map(SongItem::toMediaMetadata)
                                        .onEach(::insert)
                                        .mapIndexed { position, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = currentPlaylist.id,
                                                position = position,
                                                setVideoId = song.setVideoId
                                            )
                                        }
                                        .forEach(::insert)
                                }
                            }
                            coroutineScope.launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                            }
                        },
                        onDelete = { showDeletePlaylistDialog = true },
                        onDownload = {
                            when (downloadState) {
                                Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                                Download.STATE_DOWNLOADING -> {
                                    songs.forEach { song ->
                                        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.song.id, false)
                                    }
                                }
                                else -> {
                                    songs.forEach { song ->
                                        val downloadRequest = DownloadRequest
                                            .Builder(song.song.id, song.song.id.toUri())
                                            .setCustomCacheKey(song.song.id)
                                            .setData(song.song.song.title.toByteArray())
                                            .build()
                                        DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
                                    }
                                }
                            }
                        },
                        onQueue = {
                            playerConnection.addToQueue(items = songs.map { it.song.toMediaItem() })
                        },
                        onDismiss = { menuState.dismiss() },
                        locked = locked,
                        onToggleLock = { locked = !locked },
                        onShowSortDialog = { showSortDialog = true },
                        onSearch = { isSearching = true },
                        onPlayNext = {
                            playerConnection.playNext(songs.map { it.song.toMediaItem() })
                        },
                    )
                }
            }
        },
        topBarOverride = if (inSelectMode || isSearching) {
            {
                LocalPlaylistSearchBar(
                    inSelectMode = inSelectMode,
                    isSearching = isSearching,
                    query = query,
                    onQueryChange = { query = it },
                    focusRequester = focusRequester,
                    selectionCount = selection.size,
                    allSelected = selection.size == songs.size && selection.isNotEmpty(),
                    onToggleSelectAll = {
                        if (selection.size == songs.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(songs.map { it.map.id })
                        }
                    },
                    onSelectionMenu = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.song },
                                songPosition = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.map },
                                onDismiss = menuState::dismiss,
                                clearAction = onExitSelectionMode
                            )
                        }
                    },
                    onExitSelection = onExitSelectionMode,
                    onBack = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                )
            }
        } else null,
        overlay = {
            DraggableScrollbar(
                modifier = Modifier
                    .padding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues())
                    .align(Alignment.CenterEnd),
                scrollState = lazyListState,
                headerItems = headerItems
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (songs.isEmpty() && currentPlaylist.playlist.remoteSongCount == 0) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                    modifier = Modifier.animateItem()
                )
            }
        } else if (!isSearching) {
            item(key = "playlist_header") {
                val isThisQueue = queueTitle == currentPlaylist.playlist.name
                val savedRemote = currentPlaylist.playlist.browseId != null && !currentPlaylist.playlist.isEditable
                PlaylistHeader(
                    title = currentPlaylist.playlist.name,
                    creators = if (accountName.isNotBlank()) listOf(CreatorUi(name = accountName, avatarUrl = creatorAvatar)) else emptyList(),
                    stats = playlistStats(
                        context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size),
                        playlistLength,
                    ),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) playerConnection.togglePlayPause() else play(songs)
                    },
                    onShuffleClick = { play(songs.shuffled()) },
                    isSaved = if (savedRemote) currentPlaylist.playlist.bookmarkedAt != null else null,
                    onSaveClick = {
                        database.query { update(currentPlaylist.playlist.toggleLike()) }
                    },
                    sortControl = {
                        PlaylistSortControl(
                            current = sortType,
                            descending = sortDescending,
                            options = PlaylistSongSortType.entries,
                            label = { stringResource(playlistSortLabel(it)) },
                            onSelect = onSortTypeChange,
                            onToggleDirection = { onSortDescendingChange(!sortDescending) },
                            showDirection = { it != PlaylistSongSortType.CUSTOM },
                        )
                    },
                )
            }
        }

        itemsIndexed(
            items = if (isSearching) filteredSongs else mutableSongs,
            key = { _, song -> song.map.id },
        ) { index, song ->
            ReorderableItem(
                state = reorderableState,
                key = song.map.id,
            ) {
                val currentItem by rememberUpdatedState(song)

                fun deleteFromPlaylist() {
                    database.transaction {
                        coroutineScope.launch {
                            playlist?.playlist?.browseId?.let { browseId ->
                                val setVideoId = getSetVideoId(currentItem.map.songId)
                                setVideoId?.setVideoId?.let { setVideoIdValue ->
                                    YouTube.removeFromPlaylist(browseId, currentItem.map.songId, setVideoIdValue)
                                }
                            }
                        }
                        move(currentItem.map.playlistId, currentItem.map.position, Int.MAX_VALUE)
                        delete(currentItem.map.copy(position = Int.MAX_VALUE))
                    }
                }

                // takes the song out, with a few seconds to put it back exactly where it was
                fun removeWithUndo() {
                    val removed = currentItem
                    deleteFromPlaylist()
                    val browseId = playlist?.playlist?.browseId
                    com.example.musicfy.ui.component.TopToaster.show(
                        text = "Removed ${removed.song.song.title} from ${playlist?.playlist?.name ?: "playlist"}",
                        thumbnail = removed.song.song.thumbnailUrl,
                        iconRes = R.drawable.playlist_add,
                        actionLabel = context.getString(R.string.undo),
                        onAction = {
                            coroutineScope.launch {
                                database.transaction {
                                    insert(removed.map.copy(position = Int.MAX_VALUE))
                                    move(removed.map.playlistId, Int.MAX_VALUE, removed.map.position)
                                }
                                // a synced playlist gets it back on YouTube too (at the end there)
                                browseId?.let { launch(Dispatchers.IO) { YouTube.addToPlaylist(it, removed.map.songId) } }
                            }
                        },
                    )
                }

                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) selection.add(song.map.id) else selection.remove(Integer.valueOf(song.map.id))
                }

                val content: @Composable () -> Unit = {
                    PlaylistTrackRow(
                        thumbnailUrl = song.song.song.thumbnailUrl,
                        title = song.song.song.title,
                        subtitle = "${song.song.artists.joinToString { it.name }} • ${makeTimeString(song.song.song.duration * 1000L)}",
                        isActive = song.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.revealOnAppear(
                            key = "track_${song.map.id}",
                            seenState = revealSeen,
                            delayMillis = minOf(index, 8) * 28,
                        ),
                        onClick = {
                            if (inSelectMode) {
                                onCheckedChange(!selection.contains(song.map.id))
                            } else if (song.song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                play(songs, startIndex = songs.indexOfFirst { it.map.id == song.map.id })
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
                                    originalSong = song.song,
                                    playlistSong = song,
                                    playlistBrowseId = playlist?.playlist?.browseId,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        trailing = when {
                            inSelectMode -> {
                                { Checkbox(checked = selection.contains(song.map.id), onCheckedChange = onCheckedChange) }
                            }
                            sortType == PlaylistSongSortType.CUSTOM && !locked && !isSearching && editable -> {
                                {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song.song,
                                                        playlistSong = song,
                                                        playlistBrowseId = playlist?.playlist?.browseId,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_horiz),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.78f),
                                            )
                                        }
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                }
                            }
                            else -> null
                        },
                    )
                }

                SwipeActionsBox(
                    modifier = Modifier.animateItem(),
                    enabled = !inSelectMode,
                    // in your own playlist sliding right takes the song out; a saved one you can't
                    // edit adds it to your library instead. sliding left always queues it.
                    start = {
                        if (editable) {
                            removeFromPlaylistSwipeAction { removeWithUndo() }
                        } else {
                            librarySwipeAction(song.song.song)
                        }
                    },
                    end = { queueSwipeAction { song.song.toMediaItem() } },
                ) {
                    content()
                }
            }
        }

        if (!isSearching && editable) {
            item(key = "add_music_row") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { showAddSongsDialog = true })
                        .padding(start = 36.dp, end = 24.dp, top = 9.dp, bottom = 9.dp)
                        .animateItem(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(39.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(BoneColor),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_music),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 11.dp),
                    )
                }
            }
        }

        if (!isSearching && songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = playlistLength)
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

        if (!isSearching) {
            item(key = "related_playlists") {
                val allPlaylists by database.playlists(PlaylistSortType.CREATE_DATE, true).collectAsState(initial = emptyList())
                val relatedPlaylists = remember(allPlaylists, playlist) {
                    allPlaylists.filter { it.id != playlist?.id }.shuffled().take(5)
                }

                if (relatedPlaylists.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    ) {
                        PlaylistSectionTitle(title = "Related Playlists")
                        Spacer(Modifier.height(9.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(relatedPlaylists, key = { it.id }) { related ->
                                Column(
                                    modifier = Modifier
                                        .width(137.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { navController.navigate("local_playlist/${related.id}") }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(BoneColor)
                                    ) {
                                        val thumbUrl = related.playlist.thumbnailUrl ?: related.thumbnails.firstOrNull()
                                        if (thumbUrl != null) {
                                            AsyncImage(
                                                model = thumbUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.music_note),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .align(Alignment.Center)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = related.playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.92f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.n_song, related.songCount, related.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort order") },
            text = {
                Column {
                    PlaylistSongSortType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortType == type,
                                onClick = {
                                    if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(playlistSortLabel(type)) +
                                    if (sortType == type) (if (sortDescending) " (Desc)" else " (Asc)") else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun playlistSortLabel(type: PlaylistSongSortType): Int = when (type) {
    PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
    PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
    PlaylistSongSortType.NAME -> R.string.sort_by_name
    PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
    PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalPlaylistSearchBar(
    inSelectMode: Boolean,
    isSearching: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    selectionCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onSelectionMenu: () -> Unit,
    onExitSelection: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selectionCount, selectionCount))
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
            if (inSelectMode) {
                IconButton(onClick = onExitSelection) {
                    Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(painter = painterResource(R.drawable.arrow_back_ios), contentDescription = null)
                }
            }
        },
        actions = {
            if (inSelectMode) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
                IconButton(
                    enabled = selectionCount > 0,
                    onClick = onSelectionMenu
                ) {
                    Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                }
            }
        }
    )
}

@Composable
private fun AddSongsToPlaylistDialog(
    playlistId: String,
    existingSongIds: Set<String>,
    nextPosition: Int,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val allSongs by database.songs(SongSortType.CREATE_DATE, descending = true)
        .collectAsState(initial = emptyList())
    val pickable = remember(allSongs, existingSongIds) {
        allSongs.filter { it.id !in existingSongIds }
    }
    val selected = remember { mutableStateListOf<String>() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.85f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.add_music),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            database.transaction {
                                selected.forEachIndexed { i, songId ->
                                    insert(
                                        PlaylistSongMap(
                                            songId = songId,
                                            playlistId = playlistId,
                                            position = nextPosition + i,
                                            setVideoId = null,
                                        )
                                    )
                                }
                            }
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.add_music) + " (${selected.size})")
                    }
                }
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = pickable, key = { it.id }) { song ->
                        val isSelected = song.id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelected) selected.remove(song.id) else selected.add(song.id)
                                    },
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = song.song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
