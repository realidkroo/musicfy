// autoplaylistscreenkt
// this thing is for auto playlist screen

package com.example.musicfy.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import com.example.musicfy.constants.LastPlayedLikedSongsTimeKey
import com.example.musicfy.utils.dataStore
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.HideExplicitKey
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
import com.example.musicfy.ui.component.ExpandableText
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.detail.rememberDetailCollapseProgress
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.musicfy.ui.component.homeSharedElement
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.component.SortHeader
import com.example.musicfy.ui.menu.AutoPlaylistMenu
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.listItemShape
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.Dispatchers
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
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
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
    val mutableSongs =
        remember {
            mutableStateListOf<Song>()
        }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val likeLength =
        remember(songs) {
            songs?.fastSumBy { it.song.duration } ?: 0
        }

    val playlistId = viewModel.playlist
    val playlistType = when (playlistId) {
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

    var screenBackgroundColor by remember { mutableStateOf<Color?>(null) }
    val animatedBgColor by animateColorAsState(
        targetValue = screenBackgroundColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 600)
    )

    val detailAccentColor = com.example.musicfy.LocalDetailAccentColor.current
    androidx.compose.runtime.SideEffect { detailAccentColor.value = screenBackgroundColor }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { detailAccentColor.value = null }
    }

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    
    LaunchedEffect(Unit) {
        println("[UPLOAD_DEBUG] AutoPlaylistScreen LaunchedEffect: playlistId=$playlistId, playlistType=$playlistType, ytmSync=$ytmSync")
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) {
                    println("[UPLOAD_DEBUG] AutoPlaylistScreen: Calling syncLikedSongs()")
                    viewModel.syncLikedSongs()
                }
                if (playlistType == PlaylistType.UPLOADED) {
                    println("[UPLOAD_DEBUG] AutoPlaylistScreen: Calling syncUploadedSongs()")
                    viewModel.syncUploadedSongs()
                }
            }
        } else {
            println("[UPLOAD_DEBUG] AutoPlaylistScreen: ytmSync is false, not syncing")
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
        if (songs?.isEmpty() == true) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs?.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } == true) {
                    Download.STATE_COMPLETED
                } else if (songs?.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                                downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    } == true
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

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
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs!!.forEach { song ->
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

    val state = rememberLazyListState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val canRefresh = playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED

    var headerHeightPx by remember { mutableStateOf(0f) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val collapseState = rememberDetailCollapseProgress(state, headerHeightPx)
    val glassState = remember { GlassState() }
    val headerThumbnailUrl = remember(songs) {
        songs?.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
            .then(
                if (canRefresh) {
                    Modifier.pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        LazyColumn(
            state = state,
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.glassRoot(glassState, isActive = { collapseState.morphProgress > 0f }),
        ) {
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            AutoPlaylistHeader(
                                name = playlist,
                                playlistType = playlistType,
                                songs = songs!!,
                                likeLength = likeLength,
                                downloadState = downloadState,
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                menuState = menuState,
                                onColorExtracted = { screenBackgroundColor = it },
                                onCoverPositioned = { coverBounds = it },
                                headerContentAlpha = collapseState.headerContentAlpha,
                                morphProgress = collapseState.morphProgress,
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                            )
                        }
                    }

                    item(key = "songs_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (filteredSongs.isNotEmpty()) {
                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(song.id)
                            } else {
                                selection.remove(song.id)
                            }
                        }

                        com.example.musicfy.ui.component.detail.DetailTrackRow(
                            thumbnailUrl = song.song.thumbnailUrl,
                            title = song.song.title,
                            subtitle = "${song.artists.joinToString { it.name }} • ${makeTimeString(song.song.duration * 1000L)}",
                            isActive = song.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showDivider = index != filteredSongs.lastIndex,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                if (inSelectMode) {
                                    onCheckedChange(song.id !in selection)
                                } else if (song.song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    coroutineScope.launch {
                                        if (viewModel.playlist == "liked") {
                                            context.dataStore.edit { it[LastPlayedLikedSongsTimeKey] = System.currentTimeMillis() }
                                        }
                                    }
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist,
                                            items = songs!!.map { it.toMediaItem() },
                                            startIndex = songs!!.indexOfFirst { it.id == song.id }
                                        ),
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
            }
            item {
                Spacer(Modifier.height(50.dp))
            }
        }

        DraggableScrollbar(
            modifier = Modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                        .asPaddingValues()
                )
                .align(Alignment.CenterEnd),
            scrollState = state,
            headerItems = 2
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

        if (inSelectMode || isSearching) {
            TopAppBar(
                title = {
                    if (inSelectMode) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, selection.size, selection.size),
                            style = MaterialTheme.typography.titleLarge
                        )
                    } else if (isSearching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
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
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else if (inSelectMode) {
                                onExitSelectionMode()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching && !inSelectMode) {
                                navController.backToMain()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (inSelectMode) R.drawable.close else R.drawable.arrow_back_ios
                            ),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (inSelectMode) {
                        Checkbox(
                            checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                            onCheckedChange = {
                                if (selection.size == filteredSongs.size) {
                                    selection.clear()
                                } else {
                                    selection.clear()
                                    selection.addAll(filteredSongs.map { it.id })
                                }
                            }
                        )
                        IconButton(
                            enabled = selection.isNotEmpty(),
                            onClick = {
                                menuState.show {
                                    SelectionSongMenu(
                                        songSelection = filteredSongs.filter { it.id in selection },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    } else if (!isSearching) {
                        IconButton(
                            onClick = { isSearching = true }
                        ) {
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
        } else {
            com.example.musicfy.ui.component.detail.DetailCollapsingTopBar(
                progress = collapseState.morphProgress,
                glassState = glassState,
                thumbnailUrl = headerThumbnailUrl,
                title = playlist,
                subtitle = null,
                accentColor = screenBackgroundColor,
                coverBoundsInWindow = coverBounds,
                onBackClick = { navController.navigateUp() },
                onBackLongClick = { navController.backToMain() },
            )
        }
    }
}@Composable
private fun AutoPlaylistHeader(
    name: String,
    playlistType: PlaylistType,
    songs: List<Song>,
    likeLength: Int,
    downloadState: Int,
    onShowRemoveDownloadDialog: () -> Unit,
    menuState: com.example.musicfy.ui.component.MenuState,
    onColorExtracted: (Color) -> Unit,
    onCoverPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    headerContentAlpha: Float = 1f,
    morphProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()

    val staticDescription = remember(name, songs.size, likeLength) {
        val trackCountText = context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size)
        "$name is a personalized collection featuring $trackCountText.${
            if (likeLength > 0) " Total listening time is ${makeTimeString(likeLength * 1000L)}." else ""
        }"
    }

    com.example.musicfy.ui.component.detail.PlaylistScreenHeader(
        thumbnailUrl = songs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        title = name,
        // no creator name for these liked downloaded uploaded are auto generated
        // system views not a playlist you made with a name worth attaching
        userName = "",
        description = staticDescription,
        isPlaying = isPlaying,
        onColorExtracted = onColorExtracted,
        onCoverPositioned = onCoverPositioned,
        headerContentAlpha = headerContentAlpha,
        morphProgress = morphProgress,
        onPlayClick = {
            coroutineScope.launch {
                if (name == context.getString(R.string.liked)) {
                    context.dataStore.edit { it[LastPlayedLikedSongsTimeKey] = System.currentTimeMillis() }
                }
            }
            playerConnection.playQueue(ListQueue(title = name, items = songs.map { it.toMediaItem() }))
        },
        onShuffleClick = {
            coroutineScope.launch {
                if (name == context.getString(R.string.liked)) {
                    context.dataStore.edit { it[LastPlayedLikedSongsTimeKey] = System.currentTimeMillis() }
                }
            }
            playerConnection.playQueue(ListQueue(title = name, items = songs.shuffled().map { it.toMediaItem() }))
        },
        onMoreClick = {
            menuState.show {
                AutoPlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(songs.map { it.toMediaItem() }) },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> onShowRemoveDownloadDialog()
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
        modifier = modifier
    )
}


enum class PlaylistType {
    LIKE, DOWNLOAD, UPLOADED, OTHER
}
