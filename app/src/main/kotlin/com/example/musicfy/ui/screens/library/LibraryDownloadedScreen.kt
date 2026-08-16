// LibraryDownloadedScreen.kt
//
// A clone of the Library home page scoped to downloaded/local content: same collapsing header,
// same category cards, same wide cards — with "Add local music" at the top and, since you are
// already inside Downloaded, the "Downloaded" card replaced by "All local music".

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.search.SearchAvatar
import com.example.musicfy.ui.screens.search.SearchHorizontalPadding
import com.example.musicfy.ui.screens.search.rememberCollapseProgress
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.LibraryHomeViewModel

@Composable
fun LibraryDownloadedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current

    val downloaded by viewModel.downloadedSongs.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")
    val snackbarHostState = remember { SnackbarHostState() }

    var query by remember { mutableStateOf(TextFieldValue()) }
    val results = remember(query.text, downloaded) {
        val q = query.text.trim()
        if (q.isBlank()) emptyList() else searchLibrary(q, downloaded, emptyList(), emptyList(), emptyList())
    }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        LibraryScaffold(
            title = "Downloaded",
            subtitle = null,
            searchPlaceholder = "Search anything on your library",
            query = query,
            onQueryChange = { query = it },
            listState = listState,
            glassState = glassState,
            collapseProvider = { collapse.value },
            pureBlack = pureBlack,
            bottomInset = bottomInset,
            trailing = {
                SearchAvatar(
                    imageUrl = profilePicStr.ifBlank { null },
                    onClick = { navController.navigateToTab("settings") },
                )
            },
        ) {
            if (query.text.isNotBlank()) {
                librarySearchResults(
                    results = results,
                    query = query.text,
                    onOpen = { openLibraryItem(it, navController, playerConnection) },
                )
                return@LibraryScaffold
            }

            item(key = "rule_top") {
                LibraryRule()
                Spacer(modifier = Modifier.height(16.dp))
            }

            item(key = "add_local") {
                LibraryAddLocalMusicCard(
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(16.dp))
                LibraryRule()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Nothing downloaded and nothing imported means every card below would be an empty
            // shell linking to an empty page. One centred message says the same thing honestly.
            if (downloaded.isEmpty() && localSongs.isEmpty()) {
                item(key = "empty") { LibraryEmptyState() }
                return@LibraryScaffold
            }

            item(key = "categories") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryCategoryCard(
                            title = "Songs",
                            count = downloaded.size,
                            covers = downloaded.map { it.song.thumbnailUrl },
                            onClick = { navController.navigate("auto_playlist/downloaded") },
                            modifier = Modifier.weight(1f),
                        )
                        LibraryCategoryCard(
                            title = "Albums",
                            count = albums.size,
                            covers = albums.map { it.album.thumbnailUrl },
                            onClick = { navController.navigate("library/albums") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryCategoryCard(
                            title = "Artist",
                            count = artists.size,
                            covers = artists.map { it.artist.thumbnailUrl },
                            onClick = { navController.navigate("library/artists") },
                            modifier = Modifier.weight(1f),
                        )
                        LibraryCategoryCard(
                            title = "Playlist",
                            count = playlists.size,
                            covers = playlists.flatMap { it.thumbnails },
                            onClick = { navController.navigate("library/playlists") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (downloaded.isNotEmpty()) {
                item(key = "recently_added") {
                    LibraryWideCard(
                        title = "Recently added",
                        label = downloaded.size.toString(),
                        covers = downloaded.map { it.song.thumbnailUrl },
                        onClick = { navController.navigate("library/added") },
                        modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (likedSongs.isNotEmpty()) {
                item(key = "liked_songs") {
                    LibraryWideCard(
                        title = "Liked songs",
                        label = likedSongs.size.toString(),
                        covers = likedSongs.map { it.song.thumbnailUrl },
                        onClick = { navController.navigate("auto_playlist/liked") },
                        modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Only songs imported from the device — cached streams are "downloaded", not "local",
            // and lumping them together here would make this card a duplicate of the one above.
            if (localSongs.isNotEmpty()) {
                item(key = "all_local") {
                    LibraryWideCard(
                        title = "All local music",
                        label = localSongs.size.toString(),
                        covers = localSongs.map { it.song.thumbnailUrl },
                        onClick = { navController.navigate("auto_playlist/local") },
                        modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
