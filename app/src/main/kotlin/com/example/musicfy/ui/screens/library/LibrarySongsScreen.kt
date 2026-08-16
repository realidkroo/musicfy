// LibrarySongsScreen.kt

package com.example.musicfy.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    LibraryEntityListScreen(
        title = "Songs",
        subtitle = "All songs on your library listed here.",
        searchPlaceholder = "Find song listed here, by the lyrics, or the artist",
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        onClick = { song ->
            // Queue the alphabetical order the user is looking at, not the underlying
            // create-date order — otherwise tapping a row starts a queue that jumps somewhere
            // unrelated to what's on screen.
            val ordered = songs.sortedBy { it.song.title.trim().lowercase() }
            playerConnection.playQueue(
                ListQueue(
                    title = "Songs",
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
    )
}
