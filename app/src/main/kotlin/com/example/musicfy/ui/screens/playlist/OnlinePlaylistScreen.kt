// onlineplaylistscreenkt
// this thing is part of online playlist screen

package com.example.musicfy.ui.screens.playlist

import android.content.Intent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.firstOrNull
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.statusBarsPadding
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.WatchEndpoint
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.playback.queues.YouTubePlaylistQueue
import com.example.musicfy.ui.component.homeSharedElement
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.detail.rememberDetailCollapseProgress
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.NavigationTitle
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSelectionSongMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.listItemShape
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.core.net.toUri
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.OnlineBlur
import com.example.musicfy.constants.AppBarHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val downloadUtil = LocalDownloadUtil.current
    val context = LocalContext.current

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songs) {
        if (songs.isNullOrEmpty()) return@LaunchedEffect
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

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 150
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

    var headerHeightPx by remember { mutableStateOf(0f) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val collapseState = rememberDetailCollapseProgress(lazyListState, headerHeightPx)
    val glassState = remember { GlassState() }
    val headerThumbnailUrl = remember(playlist, songs) {
        playlist?.thumbnail ?: songs.firstOrNull { !it.thumbnail.isNullOrEmpty() }?.thumbnail
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).union(WindowInsets.ime).asPaddingValues(),
            modifier = Modifier.glassRoot(glassState, isActive = { collapseState.morphProgress > 0f }),
        ) {
            if (playlist == null || songs.isEmpty()) {
                if (isLoading) {
                    item(key = "loading_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                }
            } else {
                playlist?.let { playlist ->
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            OnlinePlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                dbPlaylist = dbPlaylist,
                                downloadState = downloadState,
                                navController = navController,
                                coroutineScope = coroutineScope,
                                continuation = viewModel.continuation,
                                onColorExtracted = { screenBackgroundColor = it },
                                onCoverPositioned = { coverBounds = it },
                                headerContentAlpha = collapseState.headerContentAlpha,
                                morphProgress = collapseState.morphProgress,
                                modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height.toFloat() },
                            )
                        }
                    }

                    itemsIndexed(filteredSongs) { index, (_, songItem) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(songItem.id)
                            } else {
                                selection.remove(songItem.id)
                            }
                        }

                        com.example.musicfy.ui.component.detail.DetailTrackRow(
                            thumbnailUrl = songItem.thumbnail,
                            title = songItem.title,
                            subtitle = "${songItem.artists.joinToString { it.name }} • ${makeTimeString(songItem.duration?.times(1000L))}",
                            isActive = mediaMetadata?.id == songItem.id,
                            isPlaying = isPlaying,
                            showDivider = index != filteredSongs.lastIndex,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                if (inSelectMode) {
                                    onCheckedChange(songItem.id !in selection)
                                } else if (songItem.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubePlaylistQueue(
                                            playlistId = playlist.id,
                                            playlistTitle = playlist.title,
                                            initialSongs = filteredSongs.map { it.second },
                                            initialContinuation = viewModel.continuation,
                                            startIndex = index
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

                    if (isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }

                    if (!isSearching && songs.isNotEmpty()) {
                        item(key = "featured_artists") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Text(
                                    text = "Featured Artists",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                                ) {
                                    val uniqueArtists = songs.flatMap { it.artists }.distinctBy { it.id }.take(10)
                                    items(uniqueArtists.size, key = { uniqueArtists[it].id ?: uniqueArtists[it].name }) { index ->
                                        val artist = uniqueArtists[index]
                                        var thumbnailUrl by remember { mutableStateOf<String?>(null) }

                                        LaunchedEffect(artist.id) {
                                            artist.id?.let { id ->
                                                val dbArtist = database.artist(id).firstOrNull()
                                                if (dbArtist?.artist?.thumbnailUrl != null) {
                                                    thumbnailUrl = dbArtist.artist.thumbnailUrl
                                                } else {
                                                    com.music.innertube.YouTube.artist(id).onSuccess { page ->
                                                        page.artist.thumbnail?.let { url ->
                                                            thumbnailUrl = url
                                                            database.query {
                                                                insert(com.example.musicfy.db.entities.ArtistEntity(id = id, name = artist.name, thumbnailUrl = url))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                artist.id?.let { navController.navigate("artist/$it") }
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (thumbnailUrl != null) {
                                                    AsyncImage(
                                                        model = thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(R.drawable.person),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(40.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 100.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (relatedItems.isNotEmpty() && !isSearching) {
                        item(key = "related_title") {
                            NavigationTitle(
                                title = "Related Playlists",
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(key = "related_items") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
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

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(50.dp))
                    }
                }
            }
        }

        if (inSelectMode || isSearching) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
                                    selection.addAll(filteredSongs.map { it.second.id })
                                }
                            }
                        )
                        IconButton(
                            enabled = selection.isNotEmpty(),
                            onClick = {
                                menuState.show {
                                    YouTubeSelectionSongMenu(
                                        songSelection = filteredSongs.filter { it.second.id in selection }
                                            .map { it.second },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelectionMode
                                    )
                                }
                            }
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
                }
            )
        } else {
            com.example.musicfy.ui.component.detail.DetailCollapsingTopBar(
                progress = collapseState.morphProgress,
                glassState = glassState,
                thumbnailUrl = headerThumbnailUrl,
                title = playlist?.title.orEmpty(),
                subtitle = playlist?.author?.name,
                accentColor = screenBackgroundColor,
                coverBoundsInWindow = coverBounds,
                onBackClick = { navController.navigateUp() },
                onBackLongClick = { navController.backToMain() },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    downloadState: Int,
    navController: NavController,
    coroutineScope: CoroutineScope,
    continuation: String?,
    onColorExtracted: (Color) -> Unit,
    onCoverPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    headerContentAlpha: Float = 1f,
    morphProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val hasExplicitContent = remember(songs) {
        songs.any { it.explicit }
    }

    val density = LocalDensity.current
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset = with(density) {
        -(systemBarsTopPadding + AppBarHeight).roundToPx()
    }

    val totalDuration = songs.sumOf { it.duration ?: 0 }
    val staticDescription = remember(songs.size, totalDuration) {
        val trackCountText = context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size)
        val durationText = if (totalDuration > 0) {
            val hours = totalDuration / 3600
            val minutes = (totalDuration % 3600) / 60
            if (hours > 0) " • ${hours}h ${minutes}m" else " • ${minutes}m"
        } else ""
        "$trackCountText$durationText"
    }

    val isSaved = dbPlaylist?.playlist?.bookmarkedAt != null
    // a real youtube creator s name if this playlist has one otherwise hidden
    // entirely rather than falling back to you the signed in account since an
    // auto generated yt playlist radio mixes algorithmic lists etc isn t yours
    val creatorName = playlist.author?.name.orEmpty()

    com.example.musicfy.ui.component.detail.PlaylistScreenHeader(
        thumbnailUrl = playlist.thumbnail ?: songs.firstOrNull { !it.thumbnail.isNullOrEmpty() }?.thumbnail,
        title = playlist.title,
        userName = creatorName,
        description = staticDescription,
        isPlaying = isPlaying && mediaMetadata?.album?.id == playlist.id,
        onColorExtracted = onColorExtracted,
        onCoverPositioned = onCoverPositioned,
        headerContentAlpha = headerContentAlpha,
        morphProgress = morphProgress,
        onPlayClick = {
            if (songs.isNotEmpty()) {
                playerConnection.playQueue(
                    YouTubePlaylistQueue(
                        playlistId = playlist.id,
                        playlistTitle = playlist.title,
                        initialSongs = songs,
                        initialContinuation = continuation
                    )
                )
            }
        },
        onShuffleClick = {
            if (songs.isNotEmpty()) {
                playerConnection.playQueue(
                    YouTubePlaylistQueue(
                        playlistId = playlist.id,
                        playlistTitle = playlist.title,
                        initialSongs = songs.shuffled(),
                        initialContinuation = continuation
                    )
                )
            }
        },
        onMoreClick = {
            menuState.show {
                YouTubePlaylistMenu(
                    playlist = playlist,
                    songs = songs,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    onImportedPlaylist = { playlistId ->
                        navController.navigate("local_playlist/$playlistId")
                    },
                )
            }
        },
        modifier = modifier
    )
}
