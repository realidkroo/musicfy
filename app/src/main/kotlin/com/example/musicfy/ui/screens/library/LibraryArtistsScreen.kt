// LibraryArtistsScreen.kt

package com.example.musicfy.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.menu.ArtistMenu
import com.example.musicfy.viewmodels.LibraryHomeViewModel

@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val artists by viewModel.artists.collectAsState()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    LibraryEntityListScreen(
        title = "Artist",
        subtitle = "All artist on your library listed here.",
        searchPlaceholder = "Find an artist by name",
        items = artists,
        idOf = { it.id },
        nameOf = { it.artist.name },
        subtitleOf = { if (it.songCount == 1) "1 song" else "${it.songCount} songs" },
        thumbnailOf = { it.artist.thumbnailUrl },
        roundThumbnails = true,
        onClick = { artist -> navController.navigate("artist/${artist.id}") },
        onLongClick = { artist ->
            menuState.show {
                ArtistMenu(
                    originalArtist = artist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
