// LibraryAlbumsScreen.kt

package com.example.musicfy.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.viewmodels.LibraryHomeViewModel

@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()

    val entries = remember(albums) {
        albums.map {
            LibraryGridEntry(
                id = it.id,
                title = it.title,
                subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
                thumbnailUrl = it.album.thumbnailUrl,
            )
        }
    }

    LibraryGridScreen(
        title = "Albums",
        subtitle = "All album on your library listed here.",
        searchPlaceholder = "Find song listed here, by the lyrics, or the artist",
        entries = entries,
        onOpen = { navController.navigate("album/${it.id}") },
        // Both buttons open the newest album rather than building a queue across every album:
        // this screen only loads album metadata, so a real "play everything" would mean fetching
        // every album's tracklist up front just to make one button work.
        onPlay = { albums.firstOrNull()?.let { navController.navigate("album/${it.id}") } },
        onShuffle = { albums.randomOrNull()?.let { navController.navigate("album/${it.id}") } },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
