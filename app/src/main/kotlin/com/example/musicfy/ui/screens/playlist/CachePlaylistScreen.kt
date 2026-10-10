// CachePlaylistScreen.kt

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
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.HideExplicitKey
import com.example.musicfy.constants.SongSortDescendingKey
import com.example.musicfy.constants.SongSortType
import com.example.musicfy.constants.SongSortTypeKey
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.EmptyPlaceholder
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistSortControl
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.CachePlaylistMenu
import com.example.musicfy.ui.menu.SelectionSongMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.CachePlaylistViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val title = stringResource(R.string.cached_playlist)

    val sortedSongs = remember(cachedSongs, sortType, sortDescending, hideExplicit) {
        val visible = if (hideExplicit) cachedSongs.filter { !it.song.explicit } else cachedSongs
        val sorted = when (sortType) {
            SongSortType.CREATE_DATE -> visible.sortedBy { it.song.dateDownload ?: LocalDateTime.MIN }
            SongSortType.NAME -> visible.sortedBy { it.song.title }
            SongSortType.ARTIST -> visible.sortedBy { song -> song.artists.joinToString(separator = "") { it.name } }
            SongSortType.PLAY_TIME -> visible.sortedBy { it.song.totalPlayTime }
        }
        if (sortDescending) sorted.reversed() else sorted
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
    if (inSelectMode) BackHandler(onBack = onExitSelectionMode)

    var query by remember { mutableStateOf(TextFieldValue()) }
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    val filteredSongs = remember(sortedSongs, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) sortedSongs else sortedSongs.filter { song ->
            song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) }
        }
    }
    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { id -> if (filteredSongs.none { it.id == id }) selection.remove(id) }
    }

    val totalSeconds = remember(sortedSongs) { sortedSongs.sumOf { it.song.duration } }
    val featuredArtists = remember(sortedSongs) {
        featuredArtistsOf(sortedSongs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val downloadState = rememberSongsDownloadState(remember(sortedSongs) { sortedSongs.map { it.song.id } })
    val isThisQueue = queueTitle == title

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        if (sortedSongs.isEmpty()) return
        val list = if (shuffled) sortedSongs.shuffled() else sortedSongs
        playerConnection.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = startIndex))
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = sortedSongs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = generatedStats(sortedSongs.size, totalSeconds),
        lazyListState = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play() },
        onShuffleClick = { play(shuffled = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Search cached songs",
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        creator = "musicfy",
        onMoreClick = {
            menuState.show {
                CachePlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(sortedSongs.map { it.toMediaItem() }) },
                    onDownload = { toggleSongsDownload(context, sortedSongs, downloadState) },
                    onDismiss = menuState::dismiss,
                )
            }
        },
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
        if (sortedSongs.isEmpty()) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(icon = R.drawable.music_note, text = stringResource(R.string.playlist_is_empty))
            }
        } else if (filteredSongs.isEmpty()) {
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
                            play(startIndex = sortedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
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
                                isFromCache = true,
                            )
                        }
                    },
                    trailing = if (inSelectMode) {
                        { Checkbox(checked = song.id in selection, onCheckedChange = onCheckedChange) }
                    } else null,
                )
            }
        }

        if (query.text.isBlank() && sortedSongs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = sortedSongs.size, totalSeconds = totalSeconds)
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
