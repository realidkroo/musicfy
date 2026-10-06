// LibraryGridScreen.kt
//
// The two-column cover grid behind "Albums" and "Recently added". Both mockups are the same page —
// title, subtitle, search, Play bar, 2-up grid — so they share one implementation.
//
// A grid rather than the A-Z pill list because these are browsed by cover, not scanned by name;
// that's also why neither has an alphabet rail.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.ui.screens.search.SearchHorizontalPadding
import com.example.musicfy.ui.screens.search.rememberCollapseProgress

/** One tile in the grid. */
data class LibraryGridEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
)

@Composable
fun LibraryGridScreen(
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    entries: List<LibraryGridEntry>,
    onOpen: (LibraryGridEntry) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    val visible = remember(entries, query.text) {
        val q = query.text.trim()
        if (q.isBlank()) entries else entries.filter { it.title.contains(q, ignoreCase = true) }
    }
    // Chunked into pairs and laid out as a LazyColumn of rows rather than a LazyVerticalGrid:
    // the collapsing header needs a LazyListState to read scroll from, and a grid state is a
    // different type that rememberCollapseProgress can't consume.
    val rows = remember(visible) { visible.chunked(2) }

    LibraryScaffold(
        title = title,
        subtitle = subtitle,
        searchPlaceholder = searchPlaceholder,
        query = query,
        onQueryChange = { query = it },
        listState = listState,
        glassState = glassState,
        collapseProvider = { collapse.value },
        pureBlack = pureBlack,
        bottomInset = bottomInset,
        modifier = modifier,
    ) {
        item(key = "rule") {
            LibraryRule()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (rows.isEmpty()) {
            item(key = "empty") { LibraryEmptyState() }
            return@LibraryScaffold
        }

        item(key = "playbar") {
            LibraryPlayBar(onPlay = onPlay, onMore = onShuffle)
            Spacer(modifier = Modifier.height(20.dp))
        }

        items(rows.size, key = { rows[it].first().id }) { index ->
            val row = rows[index]
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SearchHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpen(entry) },
                    ) {
                        LibraryGridArtwork(
                            url = entry.thumbnailUrl,
                            corner = 14.dp,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = entry.title,
                            color = SearchColors.Primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!entry.subtitle.isNullOrBlank()) {
                            Text(
                                text = entry.subtitle,
                                color = SearchColors.Secondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // Half-filled last row keeps its tile at column width instead of stretching.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
