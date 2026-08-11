// OnlineSearchResult.kt
// The results page, rebuilt on the same primitives (and the same collapsing top bar) as the search
// landing screen, so scrolling results behaves exactly like scrolling the moods grid.
//
// No Material 3 components: the old version was OutlinedTextField + ChipsRow + NavigationTitle +
// YouTubeListItem + IconButton. Rows, chips, the top-result card and the overflow affordance are
// all drawn here.

package com.example.musicfy.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.os.Build
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PauseSearchHistoryKey
import com.example.musicfy.db.entities.SearchHistory
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * The category selector. "All" is the summary page; the rest map onto a YouTube search filter.
 *
 * Every filter the old screen offered is still here, featured playlists included — they were the
 * two entries most easily lost when the chip row was rebuilt, so they are listed explicitly rather
 * than derived from an enum that might not carry them.
 */
private val SearchCategories = listOf(
    "All" to null,
    "Music" to FILTER_SONG,
    "Artist" to FILTER_ARTIST,
    "Video" to FILTER_VIDEO,
    "Album" to FILTER_ALBUM,
    "Playlist" to FILTER_COMMUNITY_PLAYLIST,
    "Featured" to FILTER_FEATURED_PLAYLIST,
)

@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val summary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf { searchFilter?.value?.let { viewModel.viewStateMap[it] } }
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.query, TextRange(viewModel.query.length)))
    }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val progressProvider = remember(collapse) { { collapse.value } }
    val blurActive by remember(listState) { derivedStateOf { !listState.isScrollInProgress } }

    val selectedIndex = remember(searchFilter) {
        SearchCategories.indexOfFirst { it.second?.value == searchFilter?.value }.coerceAtLeast(0)
    }

    // The list is per-category; leaving the previous category's scroll position in place made a
    // freshly-selected chip open somewhere in the middle of its results.
    LaunchedEffect(searchFilter) { listState.scrollToItem(0) }

    // Continuation paging: fires once the tail is within a few rows of the viewport rather than
    // exactly at the end, so the next page is usually already in by the time it is needed.
    LaunchedEffect(listState, itemsPage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            if (nearEnd && itemsPage?.continuation != null) viewModel.loadMore()
        }
    }

    BackHandler { navController.navigateUp() }

    val submit: (String) -> Unit = remember(navController, pauseSearchHistory) {
        { raw ->
            val text = raw.trim()
            if (text.isNotEmpty() && text != viewModel.query) {
                focusManager.clearFocus()
                navController.navigate("search/${URLEncoder.encode(text, "UTF-8")}") {
                    popUpTo("search/${URLEncoder.encode(viewModel.query, "UTF-8")}") {
                        inclusive = true
                    }
                }
                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query { insert(SearchHistory(query = text)) }
                    }
                }
            }
        }
    }

    val onItemClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                }
            }

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    val onItemLongClick: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(item, navController, menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(item, navController, menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(item, menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // Resolved here rather than inside the list builder: `remember` is composable-only, and the
    // scan over every summary section should happen once per result set, not once per relayout.
    val top = remember(summary, viewModel.query) {
        summary?.let { pickTopResult(it.summaries.flatMap { section -> section.items }, viewModel.query) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { blurActive && progressProvider() > 0.01f })
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = searchTopBarHeight(withTitle = true, extra = 46.dp),
                    bottom = bottomInset + 24.dp,
                ),
            ) {
                if (searchFilter == null) {
                    // "All": the summary page — a hero top result, then each of the server's own
                    // grouped sections.
                    val page = summary
                    if (page == null) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else {
                        if (top != null) {
                            item(key = "top_header") {
                                SearchSectionHeader(title = "Top Results", ruleAbove = false)
                            }
                            item(key = "top_card") {
                                TopResultCard(
                                    item = top,
                                    isActive = mediaMetadata?.id == top.id,
                                    isPlaying = isPlaying,
                                    onClick = { onItemClick(top) },
                                    onLongClick = { onItemLongClick(top) },
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                            }
                        }

                        page.summaries.forEachIndexed { index, section ->
                            val rows = section.items.filter { it.id != top?.id }
                            if (rows.isNotEmpty()) {
                                item(key = "summary_header_${index}_${section.title}") {
                                    SearchSectionHeader(
                                        title = if (index == 0) "Exact matches" else section.title,
                                    )
                                }
                                items(
                                    items = rows,
                                    key = { "summary_${index}_${it.id}" },
                                    contentType = { "resultRow" },
                                ) { item ->
                                    ResultRow(
                                        item = item,
                                        isActive = mediaMetadata?.id == item.id,
                                        isPlaying = isPlaying,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                        onMenu = { onItemLongClick(item) },
                                    )
                                }
                                item(key = "summary_gap_$index") {
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    val page = itemsPage
                    if (page == null) {
                        item(key = "filter_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else if (page.items.isEmpty()) {
                        item(key = "filter_empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No results",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = SearchColors.Secondary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = page.items,
                            key = { it.id },
                            contentType = { "resultRow" },
                        ) { item ->
                            ResultRow(
                                item = item,
                                isActive = mediaMetadata?.id == item.id,
                                isPlaying = isPlaying,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                onMenu = { onItemLongClick(item) },
                            )
                        }
                    }
                }
            }
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = "Search",
            blurActive = blurActive,
            trailing = {
                SearchAvatar(imageUrl = null, onClick = { navController.navigate("settings") })
            },
            below = {
                SearchCategoryRow(
                    categories = SearchCategories.map { it.first },
                    selectedIndex = selectedIndex,
                    onSelect = { index -> viewModel.filter.value = SearchCategories[index].second },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = submit,
                placeholder = "Search for any tracks, albums, lyrics...",
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = SearchColors.Secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { navController.navigateUp() },
                    )
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Top result
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Chooses the single item to feature.
 *
 * Two rules, in order:
 *
 *  1. An artist whose name matches what was typed wins outright. Searching an artist should open on
 *     that artist, not on whichever of their tracks the server happened to rank first.
 *  2. Otherwise the highest-ranked SONG wins, and the *music* upload is preferred over the video
 *     one. YouTube frequently returns the music video ahead of the track; [SongItem.isVideoSong] is
 *     exactly that distinction (anything whose musicVideoType is not ATV), so video uploads are
 *     only featured when there is no audio version in the results at all.
 */
private fun pickTopResult(items: List<YTItem>, query: String): YTItem? {
    if (items.isEmpty()) return null
    val normalised = query.trim().lowercase()

    items.filterIsInstance<ArtistItem>()
        .firstOrNull { it.title.trim().lowercase() == normalised }
        ?.let { return it }

    val songs = items.filterIsInstance<SongItem>()
    songs.firstOrNull { !it.isVideoSong }?.let { return it }

    // Nothing but video uploads (or no songs at all) — fall back to whatever ranked first.
    return songs.firstOrNull() ?: items.first()
}

/** The featured result: large square artwork, the item's details, and a round play affordance. */
@Composable
private fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kind = when (item) {
        is SongItem -> if (item.isVideoSong) "Video" else "Song"
        is AlbumItem -> "Album"
        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist"
    }
    val detail = when (item) {
        is SongItem -> listOfNotNull(
            kind,
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            kind,
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.year?.toString(),
        ).joinToString("  •  ")

        is ArtistItem -> kind
        is PlaylistItem -> listOfNotNull(kind, item.author?.name?.let { "by $it" }, item.songCountText)
            .joinToString("  •  ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(SearchColors.Tile)
            .searchCardBorder(16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // The card takes its colour from the record itself: the same artwork, blown up and
        // blurred hard behind the content, then dimmed. It costs no extra network work (the
        // thumbnail is already in Coil's cache for the foreground copy) and no palette pass —
        // the blur IS the theme colour, and it tracks whatever is featured.
        if (item.thumbnail != null) {
            AsyncImage(
                model = item.thumbnail?.resize(256, 256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 0.55f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(70f, 70f, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Black.copy(alpha = 0.62f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
        SearchArtwork(
            url = item.thumbnail,
            size = 108.dp,
            circle = item is ArtistItem,
            corner = 8.dp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // No play affordance on an artist: tapping one opens their page, so a play button
            // would promise something the card does not do.
            if (item !is ArtistItem) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActive && isPlaying) R.drawable.ic_untitled_pause else R.drawable.play
                        ),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Result rows
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** One result: artwork, title, subtitle, and the horizontal overflow dots. */
@Composable
private fun ResultRow(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> listOfNotNull(
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            "Album",
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
        ).joinToString("  •  ")

        is ArtistItem -> "Artist"
        is PlaylistItem -> listOfNotNull("Playlist", item.author?.name).joinToString("  •  ")
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                url = item.thumbnail,
                size = 46.dp,
                circle = item is ArtistItem,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    // The row currently playing is the one thing that gets a colour of its own —
                    // it replaces the old list item's animated playing-bars indicator.
                    color = if (isActive) Color(0xFF7FD1FF) else SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SearchOverflowDots(onClick = onMenu)
        }
        SearchRule()
    }
}

/** m:ss for a duration in seconds. */
private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
