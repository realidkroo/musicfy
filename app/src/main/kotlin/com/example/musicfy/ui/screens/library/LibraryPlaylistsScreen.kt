// LibraryPlaylistsScreen.kt

package com.example.musicfy.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.menu.PlaylistMenu
import com.example.musicfy.viewmodels.LibraryHomeViewModel

@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsState()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    LibraryEntityListScreen(
        title = "Playlist",
        subtitle = "All playlist on your library listed here.",
        searchPlaceholder = "Find song listed here, by the lyrics, or the artist",
        items = playlists,
        idOf = { it.id },
        nameOf = { it.playlist.name },
        subtitleOf = { "${it.songCount} songs" },
        thumbnailOf = { it.thumbnails.firstOrNull() },
        largeRows = true,
        onClick = { playlist -> navController.navigate("local_playlist/${playlist.id}") },
        onLongClick = { playlist ->
            menuState.show {
                PlaylistMenu(
                    playlist = playlist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
