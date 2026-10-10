// ArtistAlbumsScreen.kt

package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.db.entities.Album
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.AlbumMenu
import com.example.musicfy.ui.screens.library.LibraryIndexRail
import com.example.musicfy.ui.screens.library.indexSections
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.viewmodels.ArtistAlbumsViewModel
import kotlinx.coroutines.launch

/** the artist's albums in your library: big squares two up, or an A-Z list with the rail */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    var isGrid by rememberSaveable { mutableStateOf(true) }

    val (cachedName, bannerUrl) = rememberArtistBanner(artist?.artist?.id)
    val name = artist?.artist?.name ?: cachedName.orEmpty()

    val indexed = remember(albums, isGrid) { if (isGrid) null else indexSections(albums) { it.album.title } }
    val rows = remember(indexed, albums) { indexed?.flatMap { it.second } ?: albums }
    val sectionStarts = remember(indexed) {
        buildMap {
            var position = 0
            indexed?.forEach { (section, entries) ->
                put(section.key, position + 1)
                position += entries.size
            }
        }
    }

    fun showMenu(album: Album) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            AlbumMenu(originalAlbum = album, navController = navController, onDismiss = menuState::dismiss)
        }
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = bannerUrl ?: albums.firstOrNull()?.album?.thumbnailUrl,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        overlay = {
            if (indexed != null && rows.size >= 12) {
                LibraryIndexRail(
                    sections = indexed.map { it.first },
                    onSectionSelected = { key ->
                        sectionStarts[key]?.let { index -> coroutineScope.launch { lazyListState.animateScrollToItem(index) } }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        item(key = "header", contentType = "header") {
            ArtistSubpageHeader(
                title = if (name.isNotBlank()) "$name albums" else "Albums",
                subtitle = "${albums.size} in your library",
                isGrid = isGrid,
                onGridChange = { isGrid = it },
            )
        }

        if (isGrid) {
            items(rows.chunked(2), key = { pair -> pair.joinToString("|") { it.id } }, contentType = { "grid_row" }) { pair ->
                SubpageGridRow(
                    items = pair,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .animateItem()
                        .revealOnAppear(key = "grid_${pair.first().id}", seenState = revealSeen),
                ) { album ->
                    SubpageSquareCell(
                        title = album.album.title,
                        subtitle = album.album.year?.toString(),
                        thumbnailUrl = album.album.thumbnailUrl,
                        sharedElementKey = "album-${album.id}",
                        isActive = album.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        onClick = { navController.navigate("album/${album.id}") },
                        onLongClick = { showMenu(album) },
                    )
                }
            }
        } else {
            itemsIndexed(rows, key = { _, album -> album.id }, contentType = { _, _ -> "row" }) { position, album ->
                PlaylistTrackRow(
                    thumbnailUrl = album.album.thumbnailUrl,
                    title = album.album.title,
                    subtitle = listOfNotNull(album.album.year?.toString(), "${album.album.songCount} songs").joinToString(" • "),
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    coverModifier = Modifier.containerTransformSource(
                        key = "album-${album.id}",
                        cornerRadius = 9.dp,
                        coverUrl = album.album.thumbnailUrl,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .revealOnAppear(key = "row_${album.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                    onClick = { navController.navigate("album/${album.id}") },
                    onLongClick = { showMenu(album) },
                    onMenuClick = { showMenu(album) },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
