// LibraryEntityListScreen.kt
//
// The generic A-Z list behind Songs, Artists and Playlists. One implementation for all three: the
// only real differences are the subtitle, the thumbnail shape, and the row height.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.screens.search.rememberCollapseProgress
import kotlinx.coroutines.launch

/** A flattened row: either a letter divider or an item. */
private sealed interface IndexedRow<out T> {
    data class Header(val letter: Char) : IndexedRow<Nothing>
    data class Entry<T>(val item: T) : IndexedRow<T>
}

@Composable
fun <T> LibraryEntityListScreen(
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    items: List<T>,
    idOf: (T) -> String,
    nameOf: (T) -> String,
    subtitleOf: (T) -> String?,
    thumbnailOf: (T) -> String?,
    onClick: (T) -> Unit,
    onLongClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    largeRows: Boolean = false,
    pureBlack: Boolean = false,
) {
    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val scope = rememberCoroutineScope()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // Sorted here rather than trusting the caller: an A-Z rail against an unsorted list is
    // meaningless, and every caller wants alphabetical anyway.
    val rows = remember(items, query.text) {
        val q = query.text.trim()
        val visible = if (q.isBlank()) items else items.filter { nameOf(it).contains(q, ignoreCase = true) }
        val sorted = visible.sortedBy { nameOf(it).trim().lowercase() }
        buildList<IndexedRow<T>> {
            groupByLetter(sorted, nameOf).forEach { (letter, entries) ->
                add(IndexedRow.Header(letter))
                entries.forEach { add(IndexedRow.Entry(it)) }
            }
        }
    }

    val letterIndex = remember(rows) {
        buildMap {
            rows.forEachIndexed { index, row ->
                // +1 for the leading rule item, so the rail scrolls to the right place.
                if (row is IndexedRow.Header) put(row.letter, index + 1)
            }
        }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
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
        ) {
            item(key = "top_rule") {
                LibraryRule()
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (rows.isEmpty()) {
                item(key = "empty") { LibraryEmptyState() }
                return@LibraryScaffold
            }

            itemsIndexed(
                items = rows,
                key = { _, row ->
                    when (row) {
                        is IndexedRow.Header -> "h_${row.letter}"
                        is IndexedRow.Entry -> idOf(row.item)
                    }
                },
            ) { _, row ->
                when (row) {
                    is IndexedRow.Header -> LibraryLetterHeader(row.letter)
                    is IndexedRow.Entry -> LibraryListRow(
                        title = nameOf(row.item),
                        subtitle = subtitleOf(row.item),
                        thumbnailUrl = thumbnailOf(row.item),
                        large = largeRows,
                        onClick = { onClick(row.item) },
                        onLongClick = { onLongClick(row.item) },
                    )
                }
            }
        }

        if (letterIndex.isNotEmpty()) {
            LibraryAlphabetIndex(
                availableLetters = letterIndex.keys,
                onLetterSelected = { letter ->
                    letterIndex[letter]?.let { index ->
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
