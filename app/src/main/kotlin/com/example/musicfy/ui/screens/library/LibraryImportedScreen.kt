// LibraryImportedScreen.kt
//
// "Imported from Spotify": every song one service brought in, in the same A-Z list as Songs, with the
// service's own icon where the Library home has the avatar.

package com.example.musicfy.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.librarySwipeAction
import com.example.musicfy.ui.component.queueSwipeAction
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.viewmodels.LibraryImportedViewModel

@Composable
fun LibraryImportedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryImportedViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val source = viewModel.source

    val label = source?.label ?: "an unknown source"
    val sourceIcon: (@Composable () -> Unit)? = if (source != null) {
        { ImportSourceIcon(source, size = 40.dp) }
    } else {
        null
    }

    LibraryEntityListScreen(
        title = "Imported from $label",
        subtitle = when (songs.size) {
            1 -> "1 song you brought in from $label."
            else -> "${songs.size} songs you brought in from $label."
        },
        searchPlaceholder = "Find a song imported from $label",
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        onClick = { song ->
            // The order on screen, so the queue starts where the user tapped and runs down the list.
            val ordered = songs.sortedBy { it.song.title.trim().lowercase() }
            playerConnection.playQueue(
                ListQueue(
                    title = "Imported from $label",
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        rowWrapper = { song, row ->
            SwipeActionsBox(
                slab = false,
                start = { librarySwipeAction(song.song) },
                end = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
        trailing = sourceIcon,
    )
}
