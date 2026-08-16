// LibraryHomeScreen.kt
//
// The Library tab's landing page: collapsing title + search, an optional Pinned grid, the four
// category cards, and the Recently added / Downloaded cards.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.search.SearchArtwork
import com.example.musicfy.ui.screens.search.SearchAvatar
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.ui.screens.search.SearchField
import com.example.musicfy.ui.screens.search.SearchGlassTopBar
import com.example.musicfy.ui.screens.search.SearchHorizontalPadding
import com.example.musicfy.ui.screens.search.rememberCollapseProgress
import com.example.musicfy.ui.screens.search.searchTopBarHeight
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.LibraryHomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as InnertubeArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@Composable
fun LibraryHomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current

    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()

    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    val results = remember(query.text, songs, artists, albums, playlists) {
        val q = query.text.trim()
        if (q.isBlank()) emptyList() else searchLibrary(q, songs, artists, albums, playlists)
    }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    LibraryScaffold(
        title = "Library",
        subtitle = null,
        searchPlaceholder = "Search anything on your library",
        query = query,
        onQueryChange = { query = it },
        listState = listState,
        glassState = glassState,
        collapseProvider = { collapse.value },
        pureBlack = pureBlack,
        trailing = {
            SearchAvatar(
                imageUrl = profilePicStr.ifBlank { null },
                onClick = { navController.navigateToTab("settings") },
            )
        },
        bottomInset = bottomInset,
        modifier = modifier,
    ) {
        if (query.text.isNotBlank()) {
            librarySearchResults(
                results = results,
                query = query.text,
                onOpen = { item -> openLibraryItem(item, navController, playerConnection) },
            )
            return@LibraryScaffold
        }

        // A completely empty library gets one honest message instead of four zero-count cards and
        // two empty shelves.
        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty() &&
            playlists.isEmpty() && pinnedItems.isEmpty()
        ) {
            item(key = "empty") { LibraryEmptyState() }
            return@LibraryScaffold
        }

        // Pinned disappears entirely when nothing is pinned rather than showing an empty 3x3 of
        // placeholders, which on a fresh install would be the first thing on the screen.
        if (pinnedItems.isNotEmpty()) {
            item(key = "pinned_header") {
                LibrarySectionTitle("Pinned")
                Spacer(modifier = Modifier.height(12.dp))
                LibraryRule()
                Spacer(modifier = Modifier.height(16.dp))
            }
            item(key = "pinned_grid") {
                // Chunked rows rather than a nested lazy grid: a scrolling grid inside a scrolling
                // column has no bounded height, and nine items never needs its own scroller.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pinnedItems.take(9).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { item ->
                                LibraryPinnedTile(
                                    thumbnailUrl = item.thumbnail,
                                    onClick = { openLibraryItem(item, navController, playerConnection) },
                                    onLongClick = { viewModel.unpin(item.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keeps the last row's tiles the same size as a full row's.
                            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                LibraryRule()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item(key = "categories") {
            LibraryCategoryGrid(
                songCount = songs.size,
                albumCount = albums.size,
                artistCount = artists.size,
                playlistCount = playlists.size,
                songCovers = songs.map { it.song.thumbnailUrl },
                albumCovers = albums.map { it.album.thumbnailUrl },
                artistCovers = artists.map { it.artist.thumbnailUrl },
                playlistCovers = playlists.flatMap { it.thumbnails },
                onSongs = { navController.navigate("library/songs") },
                onAlbums = { navController.navigate("library/albums") },
                onArtists = { navController.navigate("library/artists") },
                onPlaylists = { navController.navigate("library/playlists") },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (songs.isNotEmpty()) {
            item(key = "recently_added") {
                LibraryWideCard(
                    title = "Recently added",
                    label = songs.size.toString(),
                    covers = songs.map { it.song.thumbnailUrl },
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

        // Kept even when empty: it's the way in to "Add local music", so hiding it would strip the
        // only route to importing anything.
        item(key = "downloaded") {
            LibraryWideCard(
                title = "Downloaded",
                label = downloadedSongs.size.toString(),
                covers = downloadedSongs.map { it.song.thumbnailUrl },
                onClick = { navController.navigate("library/downloaded") },
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
            )
        }
    }
}

/**
 * Shared page frame for every Library screen: collapsing title/subtitle, a pinned search pill, and
 * the caller's content below.
 *
 * Every Library screen carries its own search that only covers that screen's contents, which is
 * why the field lives in the scaffold rather than in one global place.
 */
@Composable
fun LibraryScaffold(
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    listState: LazyListState,
    glassState: GlassState,
    collapseProvider: () -> Float,
    pureBlack: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
            .glassRoot(glassState, isActive = { !listState.isScrollInProgress && collapseProvider() > 0.01f }),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = searchTopBarHeight(
                    withTitle = true,
                    titleBlockHeight = if (subtitle != null) LibraryTitleBlockHeight else 62.dp,
                ),
                bottom = bottomInset + 24.dp,
            ),
            content = content,
        )

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = collapseProvider,
            pureBlack = pureBlack,
            title = title,
            subtitle = subtitle,
            titleBlockHeight = if (subtitle != null) LibraryTitleBlockHeight else 62.dp,
            blurActive = !listState.isScrollInProgress,
            trailing = trailing,
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = {},
                placeholder = searchPlaceholder,
            )
        }
    }
}

@Composable
private fun LibraryCategoryGrid(
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    playlistCount: Int,
    songCovers: List<String?>,
    albumCovers: List<String?>,
    artistCovers: List<String?>,
    playlistCovers: List<String?>,
    onSongs: () -> Unit,
    onAlbums: () -> Unit,
    onArtists: () -> Unit,
    onPlaylists: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryCategoryCard(
                title = "Songs",
                count = songCount,
                covers = songCovers,
                onClick = onSongs,
                modifier = Modifier.weight(1f),
            )
            LibraryCategoryCard(
                title = "Albums",
                count = albumCount,
                covers = albumCovers,
                onClick = onAlbums,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryCategoryCard(
                title = "Artist",
                count = artistCount,
                covers = artistCovers,
                onClick = onArtists,
                modifier = Modifier.weight(1f),
            )
            LibraryCategoryCard(
                title = "Playlist",
                count = playlistCount,
                covers = playlistCovers,
                onClick = onPlaylists,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search within the library
// ---------------------------------------------------------------------------------------------

internal fun androidx.compose.foundation.lazy.LazyListScope.librarySearchResults(
    results: List<YTItem>,
    query: String,
    onOpen: (YTItem) -> Unit,
) {
    if (results.isEmpty()) {
        item(key = "no_results") {
            Text(
                text = "Nothing in your library matches \"$query\"",
                color = SearchColors.Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = SearchHorizontalPadding, vertical = 24.dp),
            )
        }
        return
    }
    items(results, key = { it.id }) { item ->
        val subtitle = when (item) {
            is SongItem -> item.artists.joinToString { it.name }
            is AlbumItem -> "Album"
            is ArtistItem -> "Artist"
            is PlaylistItem -> "Playlist"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(item) }
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(url = item.thumbnail, size = 44.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = item.title,
                    color = SearchColors.Primary,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, color = SearchColors.Secondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

internal fun openLibraryItem(
    item: YTItem,
    navController: NavController,
    playerConnection: com.example.musicfy.playback.PlayerConnection?,
) {
    when (item) {
        is SongItem -> playerConnection?.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
        is AlbumItem -> navController.navigate("album/${item.browseId}")
        is ArtistItem -> navController.navigate("artist/${item.id}")
        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
    }
}

internal fun searchLibrary(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<Album>,
    playlists: List<Playlist>,
): List<YTItem> {
    val q = query.lowercase()
    val results = ArrayList<YTItem>()
    songs.filter { it.song.title.lowercase().contains(q) }.take(8).forEach {
        results += SongItem(
            id = it.id,
            title = it.title,
            artists = it.artists.map { a -> InnertubeArtist(name = a.name, id = a.id) },
            thumbnail = it.song.thumbnailUrl ?: "",
            explicit = it.song.explicit,
        )
    }
    artists.filter { it.artist.name.lowercase().contains(q) }.take(6).forEach {
        results += ArtistItem(
            id = it.id,
            title = it.title,
            thumbnail = it.artist.thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    albums.filter { it.album.title.lowercase().contains(q) }.take(6).forEach {
        results += AlbumItem(
            browseId = it.id,
            playlistId = it.album.playlistId ?: "",
            title = it.title,
            artists = it.artists.map { a -> InnertubeArtist(name = a.name, id = a.id) },
            thumbnail = it.album.thumbnailUrl ?: "",
            explicit = it.album.explicit,
        )
    }
    playlists.filter { it.playlist.name.lowercase().contains(q) }.take(6).forEach {
        results += PlaylistItem(
            id = it.id,
            title = it.title,
            author = null,
            songCountText = null,
            thumbnail = it.thumbnails.firstOrNull(),
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    return results
}
