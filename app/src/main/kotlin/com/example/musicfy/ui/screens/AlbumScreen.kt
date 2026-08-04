// AlbumScreen.kt
// this thing is for album screen

package com.example.musicfy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.musicfy.constants.AppBarHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.HideVideoSongsKey
import com.example.musicfy.constants.AlbumCanvasEnabledKey
import com.example.musicfy.db.entities.Album
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.playback.queues.LocalAlbumRadio
import com.example.musicfy.ui.component.AlbumGradient
import com.example.musicfy.ui.component.ExpandableText
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.detail.rememberDetailCollapseProgress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.graphicsLayer
import com.example.musicfy.ui.component.homeSharedElement
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.LinkSegment
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.NavigationTitle
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.menu.AlbumMenu
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.ui.utils.fadingEdge
import com.example.musicfy.ui.player.CanvasArtworkPlayer
import com.example.musicfy.utils.listItemShape
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val description by viewModel.description.collectAsState()
    val descriptionRuns by viewModel.descriptionRuns.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    val albumCanvasEnabled by rememberPreference(key = AlbumCanvasEnabledKey, defaultValue = false)

    val canvasArtwork = null

    val filteredSongs = remember(albumWithSongs, hideExplicit, hideVideoSongs) {
        var songs = albumWithSongs?.songs ?: emptyList()
        if (hideExplicit) {
            songs = songs.filter { !it.song.explicit }
        }
        if (hideVideoSongs) {
            songs = songs.filter { !it.song.isVideo }
        }
        songs
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
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED ||
                                downloads[it]?.state == Download.STATE_DOWNLOADING ||
                                downloads[it]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    val hasExplicitContent = remember(albumWithSongs) {
        albumWithSongs?.album?.explicit == true
    }

    val lazyListState = rememberLazyListState()

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    var screenBackgroundColor by remember { mutableStateOf<Color?>(null) }
    val detailAccentColor = com.example.musicfy.LocalDetailAccentColor.current
    androidx.compose.runtime.SideEffect { detailAccentColor.value = screenBackgroundColor }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { detailAccentColor.value = null }
    }

    var headerHeightPx by remember { mutableStateOf(0f) }
    var coverBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val collapseState = rememberDetailCollapseProgress(lazyListState, headerHeightPx)
    val glassState = remember { GlassState() }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.glassRoot(glassState, isActive = { collapseState.morphProgress > 0f }),
    ) {
        val albumWithSongs = albumWithSongs
        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty()) {
             item(key = "album_header") {
                val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
                val density = LocalDensity.current
                val headerOffset = with(density) {
                    -(systemBarsTopPadding + AppBarHeight).roundToPx()
                }
                val representativeSongId = remember(albumWithSongs) { albumWithSongs.songs.firstOrNull()?.id }
                val format by (representativeSongId?.let { database.format(it) } ?: kotlinx.coroutines.flow.flowOf(null))
                    .collectAsState(initial = null)

                // Two sequential phases sharing one timeline (see DetailCollapsingTopBar):
                // phase A fades this header's own gradient+text away while the cover PHOTO
                // stays fully visible and unmoved; only once that finishes does
                // DetailCollapsingTopBar's morph overlay take over — at exactly that instant
                // this cover hard-cuts to invisible, a clean handoff with no window where
                // both the real cover and the morphing copy are on screen together.
                val headerContentAlpha = collapseState.headerContentAlpha
                val coverHandedOff = collapseState.morphProgress > 0.001f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                ) {
                    // Background gradient tinted from the cover's own color, sized to
                    // match this whole header (matchParentSize measures after the cover
                    // + content column below establish the Box's real height). Fades with
                    // the header content (phase A), not the cover photo.
                    com.example.musicfy.ui.component.detail.DetailCoverBackground(
                        thumbnailUrl = albumWithSongs.album.thumbnailUrl,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = headerContentAlpha },
                        onColorExtracted = { screenBackgroundColor = it },
                    )

                    // Album Image with offset (like ArtistScreen) — same shared-element
                    // key as before, unchanged, so the cover-expand open transition
                    // still works.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .homeSharedElement("album-${viewModel.albumId}")
                            .offset {
                                IntOffset(x = 0, y = headerOffset)
                            }
                            .graphicsLayer { alpha = if (coverHandedOff) 0f else 1f }
                            .onGloballyPositioned {
                                val pos = it.positionInWindow()
                                coverBounds = androidx.compose.ui.geometry.Rect(
                                    pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height
                                )
                            }
                    ) {
                        AsyncImage(
                            model = albumWithSongs.album.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Content column positioned at bottom part of the image
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = LocalContext.current.resources.displayMetrics.widthPixels.let { screenWidth ->
                                    with(density) {
                                        ((screenWidth / 1.2f) - 144).toDp()
                                    }
                                }
                            )
                            .padding(bottom = 16.dp)
                            .padding(horizontal = 32.dp)
                            .graphicsLayer { alpha = headerContentAlpha },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val subtitle = if (albumWithSongs.artists.size == 1) {
                            albumWithSongs.artists.first().name
                        } else {
                            albumWithSongs.artists.joinToString { it.name }
                        }
                        val metaText = buildString {
                            if (albumWithSongs.album.year != null) append("${albumWithSongs.album.year}")
                            val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                            val hours = totalDuration / 3600
                            val minutes = (totalDuration % 3600) / 60
                            if (isNotEmpty()) append(" • ")
                            append(if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m")
                        }

                        Text(
                            text = albumWithSongs.album.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(enabled = albumWithSongs.artists.size == 1) {
                                    albumWithSongs.artists.firstOrNull()?.let { navController.navigate("artist/${it.id}") }
                                }
                        )
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val staticDescription = remember(albumWithSongs) {
                            "${albumWithSongs.album.title} is an album by ${albumWithSongs.artists.joinToString { it.name }}${
                                if (albumWithSongs.album.year != null) ", released in ${albumWithSongs.album.year}" else ""
                            }. This collection features ${albumWithSongs.songs.size} tracks showcasing their musical artistry."
                        }
                        ExpandableText(
                            text = description ?: staticDescription,
                            runs = descriptionRuns?.map {
                                LinkSegment(text = it.text, url = it.navigationEndpoint?.urlEndpoint?.url)
                            },
                            collapsedMaxLines = 2,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(Modifier.height(20.dp))

                        val isSaved = albumWithSongs.album.bookmarkedAt != null
                        com.example.musicfy.ui.component.detail.DetailActionRow(
                            isPlaying = isPlaying && mediaMetadata?.album?.id == albumWithSongs.album.id,
                            onPlayClick = {
                                if (isPlaying && mediaMetadata?.album?.id == albumWithSongs.album.id) {
                                    playerConnection.player.pause()
                                } else if (mediaMetadata?.album?.id == albumWithSongs.album.id) {
                                    playerConnection.player.play()
                                } else {
                                    playerConnection.service.getAutomix(playlistId)
                                    playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                }
                            },
                            onShuffleClick = {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(
                                    LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled())),
                                )
                            },
                            secondaryAction = if (isSaved) {
                                com.example.musicfy.ui.component.detail.DetailSecondaryAction.DownloadAction(
                                    state = downloadState,
                                    onClick = {
                                        albumWithSongs.songs.forEach { song ->
                                            val downloadRequest = DownloadRequest.Builder(song.id, song.id.toUri())
                                                .setCustomCacheKey(song.id)
                                                .setData(song.song.title.toByteArray())
                                                .build()
                                            DownloadService.sendAddDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                downloadRequest,
                                                false,
                                            )
                                        }
                                    },
                                )
                            } else {
                                com.example.musicfy.ui.component.detail.DetailSecondaryAction.AddToLibrary(
                                    onClick = { database.query { update(albumWithSongs.album.toggleLike()) } }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (format != null || hasExplicitContent) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (format != null) {
                                    com.example.musicfy.ui.component.AudioFormatBadge(
                                        format = format,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        height = 20.dp,
                                    )
                                }
                                if (hasExplicitContent) {
                                    Icon(
                                        painter = painterResource(R.drawable.explicit),
                                        contentDescription = stringResource(R.string.explicit),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                        subtitle = "${song.artists.joinToString { it.name }} • ${com.example.musicfy.utils.makeTimeString(song.song.duration * 1000L)}",
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showDivider = index != filteredSongs.lastIndex,
                        onClick = {
                            if (inSelectMode) {
                                onCheckedChange(song.id !in selection)
                            } else if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(
                                    LocalAlbumRadio(albumWithSongs, startIndex = index),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (otherVersions.isNotEmpty()) {
                item(key = "other_versions_title") {
                    NavigationTitle(
                        title = stringResource(R.string.other_versions),
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "other_versions_list") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                    ) {
                        items(
                            items = otherVersions.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = mediaMetadata?.album?.id == item.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = { navController.navigate("album/${item.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            if (releasesForYou.isNotEmpty()) {
                item(key = "releases_for_you_title") {
                    NavigationTitle(
                        title = stringResource(R.string.releases_for_you),
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "releases_for_you_list") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                    ) {
                        items(
                            items = releasesForYou.distinctBy { it.id },
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = mediaMetadata?.album?.id == item.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = { navController.navigate("album/${item.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(50.dp))
            }
        } else {
            item(key = "loading") {
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
    }

    if (inSelectMode) {
        TopAppBar(
            title = {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            },
            navigationIcon = {
                IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                    )
                }
            },
            actions = {
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
                                songSelection = selection.mapNotNull { songId ->
                                    filteredSongs.find { it.id == songId }
                                },
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
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    } else {
        // Collapsing top bar: plain back button while the hero cover is visible,
        // morphing into a compact bar (mini cover + title, blurred backdrop) as the
        // user scrolls past it — same behavior as the other rebuilt detail screens.
        com.example.musicfy.ui.component.detail.DetailCollapsingTopBar(
            progress = collapseState.morphProgress,
            glassState = glassState,
            thumbnailUrl = albumWithSongs?.album?.thumbnailUrl,
            title = albumWithSongs?.album?.title.orEmpty(),
            subtitle = albumWithSongs?.artists?.joinToString { it.name } ?: "",
            accentColor = screenBackgroundColor,
            coverBoundsInWindow = coverBounds,
            onBackClick = { navController.navigateUp() },
            onBackLongClick = { navController.backToMain() },
            actions = {
                albumWithSongs?.let { albumWithSongs ->
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .border(
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.25f)
                                ),
                                shape = CircleShape
                            )
                            .background(
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_TEXT,
                                        "https://music.youtube.com/playlist?list=${albumWithSongs.album.playlistId}"
                                    )
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        intent,
                                        null
                                    )
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ios_share),
                                contentDescription = stringResource(R.string.share),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = Album(
                                            albumWithSongs.album,
                                            albumWithSongs.artists
                                        ),
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = stringResource(R.string.more_options),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
        )
    }
}
