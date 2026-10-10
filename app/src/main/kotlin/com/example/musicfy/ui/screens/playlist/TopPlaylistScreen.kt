// TopPlaylistScreen.kt

package com.example.musicfy.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.MyTopFilter
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.DefaultDialog
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistSortControl
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.TopPlaylistMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.viewmodels.TopPlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopPlaylistScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: TopPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val songs by viewModel.topSongs.collectAsState(null)
    val period by viewModel.topPeriod.collectAsState()
    val name = stringResource(R.string.my_top) + " ${viewModel.top}"

    var query by remember { mutableStateOf(TextFieldValue()) }
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

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
    if (inSelectMode) BackHandler(onBack = onExitSelectionMode)

    val all = songs.orEmpty()
    val filteredSongs = remember(all, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) all else all.filter { song ->
            song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) }
        }
    }
    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { id -> if (filteredSongs.none { it.id == id }) selection.remove(id) }
    }

    val totalSeconds = remember(all) { all.sumOf { it.song.duration } }
    val featuredArtists = remember(all) {
        featuredArtistsOf(all.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val downloadState = rememberSongsDownloadState(remember(all) { all.map { it.song.id } })
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    val isThisQueue = queueTitle == name

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        if (all.isEmpty()) return
        val list = if (shuffled) all.shuffled() else all
        playerConnection.playQueue(ListQueue(title = name, items = list.map { it.toMediaItem() }, startIndex = startIndex))
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(onClick = {
                    showRemoveDownloadDialog = false
                    toggleSongsDownload(context, all, Download.STATE_COMPLETED)
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    GeneratedPlaylistPage(
        title = name,
        coverUrl = all.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = generatedStats(all.size, totalSeconds),
        lazyListState = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play() },
        onShuffleClick = { play(shuffled = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Search your top songs",
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        creator = "musicfy",
        onMoreClick = {
            menuState.show {
                TopPlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(all.map { it.toMediaItem() }) },
                    onDownload = {
                        if (downloadState == Download.STATE_COMPLETED) {
                            showRemoveDownloadDialog = true
                        } else {
                            toggleSongsDownload(context, all, downloadState)
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        sortControl = {
            PlaylistSortControl(
                current = period,
                descending = false,
                options = MyTopFilter.entries,
                label = { filter ->
                    stringResource(
                        when (filter) {
                            MyTopFilter.ALL_TIME -> R.string.all_time
                            MyTopFilter.DAY -> R.string.past_24_hours
                            MyTopFilter.WEEK -> R.string.past_week
                            MyTopFilter.MONTH -> R.string.past_month
                            MyTopFilter.YEAR -> R.string.past_year
                        }
                    )
                },
                onSelect = { viewModel.topPeriod.value = it },
                onToggleDirection = {},
                showDirection = { false },
            )
        },
        topBarOverride = if (inSelectMode) {
            {
                GeneratedSelectionBar(
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
                    onClose = onExitSelectionMode,
                )
            }
        } else null,
    ) {
        if (songs == null) {
            item(key = "loading") { PlaylistTrackBones() }
        } else if (filteredSongs.isEmpty() && query.text.isNotBlank()) {
            item(key = "no_results") { GeneratedNoResults(query.text) }
        }

        itemsIndexed(filteredSongs, key = { _, song -> song.id }, contentType = { _, _ -> "track" }) { position, song ->
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
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${song.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            play(startIndex = all.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
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
                            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                        }
                    },
                    trailing = if (inSelectMode) {
                        { Checkbox(checked = song.id in selection, onCheckedChange = onCheckedChange) }
                    } else null,
                )
            }
        }

        if (query.text.isBlank() && all.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = all.size, totalSeconds = totalSeconds)
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
