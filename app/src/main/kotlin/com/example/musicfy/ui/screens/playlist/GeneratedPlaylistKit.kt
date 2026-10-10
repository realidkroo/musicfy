// GeneratedPlaylistKit.kt
//
// The pages the app puts together itself (Recently played, Most played, History, My Top, the
// cache, Home's section pages). They have no cover of their own, so they're the playlist page with
// the cover taken out: the title block sits at the top, the theme colour from the first song sinks
// into black down the screen, the collapsed pill is a full capsule with a round cover, and search
// is the Library's capsule under the buttons instead of a toolbar.

package com.example.musicfy.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.ui.component.IconButton
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistStatsStyle
import com.example.musicfy.ui.component.detail.SectionTitleStyle
import com.example.musicfy.ui.component.detail.playlistCompactHeaderHeight
import com.example.musicfy.ui.screens.search.SearchFieldHeight

/**
 * the frame every generated page sits in. [rows] fills the list under the header; the header,
 * with its search capsule, stays put while you type so the field never loses focus.
 */
@Composable
fun GeneratedPlaylistPage(
    title: String,
    coverUrl: String?,
    stats: String,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    searchPlaceholder: String,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    creator: String? = null,
    onMoreClick: (() -> Unit)? = null,
    sortControl: (@Composable () -> Unit)? = null,
    topBarOverride: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    rows: LazyListScope.() -> Unit,
) {
    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = onBackClick,
        onBackLongClick = onBackLongClick,
        modifier = modifier,
        onMoreClick = onMoreClick,
        showCover = false,
        roundCapsule = true,
        headerHeight = playlistCompactHeaderHeight(),
        contentPadding = contentPadding,
        topBarOverride = topBarOverride,
        overlay = overlay,
    ) {
        item(key = "generated_header", contentType = "header") {
            PlaylistHeader(
                title = title,
                creators = listOfNotNull(creator?.takeIf { it.isNotBlank() }?.let { CreatorUi(name = it) }),
                stats = stats,
                isPlaying = isPlaying,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
                sortControl = sortControl,
                compact = true,
                below = {
                    GeneratedSearchCapsule(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = searchPlaceholder,
                    )
                },
            )
        }
        rows()
    }
}

/**
 * the Library's search capsule: 48dp, ends fully round. it's frosted white instead of the
 * Library's near-black, because these pages sit on a colour rather than on black.
 */
@Composable
fun GeneratedSearchCapsule(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset - 10.dp)
            .height(SearchFieldHeight)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.1f))
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(fontSize = 15.sp),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.text.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onQueryChange(TextFieldValue())
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Clear",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** a day's header in History, or any other break in a generated list */
@Composable
fun GeneratedGroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SectionTitleStyle,
        color = Color.White,
        maxLines = 1,
        modifier = modifier.padding(start = PlaylistInset, end = PlaylistInset, top = 22.dp, bottom = 6.dp),
    )
}

/** what a search that matches nothing says */
@Composable
fun GeneratedNoResults(query: String, modifier: Modifier = Modifier) {
    Text(
        text = "Nothing here matches \"$query\"",
        style = PlaylistStatsStyle.copy(fontSize = 13.sp),
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier.padding(horizontal = PlaylistInset, vertical = 24.dp),
    )
}

/** the bar while songs are being picked: how many, pick all, and their menu */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratedSelectionBar(
    selectionCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onSelectionMenu: () -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(R.plurals.n_song, selectionCount, selectionCount),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, onLongClick = {}) {
                Icon(painter = painterResource(R.drawable.close), contentDescription = null)
            }
        },
        actions = {
            Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
            IconButton(enabled = selectionCount > 0, onClick = onSelectionMenu, onLongClick = {}) {
                Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** "21 songs · 1h 23m" */
fun generatedStats(songCount: Int, totalSeconds: Int): String {
    val count = "$songCount ${if (songCount == 1) "song" else "songs"}"
    if (totalSeconds <= 0) return count
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return "$count · ${if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"}"
}

/** all downloaded, some on their way, or neither, for the ••• menu's download line */
@Composable
fun rememberSongsDownloadState(songIds: List<String>): Int {
    val downloadUtil = com.example.musicfy.LocalDownloadUtil.current
    var state by remember { mutableStateOf(androidx.media3.exoplayer.offline.Download.STATE_STOPPED) }
    androidx.compose.runtime.LaunchedEffect(songIds) {
        if (songIds.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            state = when {
                songIds.all { downloads[it]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED } ->
                    androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
                songIds.all {
                    downloads[it]?.state in setOf(
                        androidx.media3.exoplayer.offline.Download.STATE_QUEUED,
                        androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
                    )
                } -> androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
                else -> androidx.media3.exoplayer.offline.Download.STATE_STOPPED
            }
        }
    }
    return state
}

/** what the menu's download line does from each state: start them, or take them back off */
fun toggleSongsDownload(
    context: android.content.Context,
    songs: List<com.example.musicfy.db.entities.Song>,
    state: Int,
) {
    val service = com.example.musicfy.playback.ExoDownloadService::class.java
    if (state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED ||
        state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
    ) {
        songs.forEach { androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(context, service, it.song.id, false) }
    } else {
        songs.forEach { song ->
            val request = androidx.media3.exoplayer.offline.DownloadRequest
                .Builder(song.song.id, android.net.Uri.parse(song.song.id))
                .setCustomCacheKey(song.song.id)
                .setData(song.song.title.toByteArray())
                .build()
            androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(context, service, request, false)
        }
    }
}
