// searchscreenkt
// the search tab s landing page rebuilt from scratch on the primitives in

// three states live on this one screen and the search field never moves
// declared once in the shared top bar so tapping it does not hand over to a
// the old m3 searchbar did that component swapped its inline bar for a
// which is what made the caret jump and the keyboard flicker on open

// browse moods and genres grids scrolling collapses the search title
// history field focused nothing typed
// suggest field focused with a query suggestions plus top results

// nothing here uses material 3 components the old version was built on
// secondarytabrow + tab + circularwavyprogressindicator all of it is gone

package com.example.musicfy.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.utils.YouTubeUrlParser
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PauseSearchHistoryKey
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.db.entities.SearchHistory
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.MoodAndGenresViewModel
import com.example.musicfy.viewmodels.coverKey
import com.example.musicfy.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun SearchScreen(
    navController: NavController,
    pureBlack: Boolean,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var active by rememberSaveable { mutableStateOf(false) }

    val suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
    val suggestionState by suggestionViewModel.viewState.collectAsState()

    // only pushed while the field is open keeping it fed while browsing would
    // request and a history query for text the user cannot even see
    LaunchedEffect(active, query.text) {
        if (active) suggestionViewModel.query.value = query.text
    }

    val browseListState = rememberLazyListState()
    val activeListState = rememberLazyListState()
    val glassState = remember { GlassState() }

    val collapse = rememberCollapseProgress(browseListState)
    // same curve and duration as the scroll collapse so opening the field and
    // move the bar identically a shorter different easing here made the two
    // unrelated animations depending on how the bar happened to get to the top
    val activeProgress = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "searchActive",
    )
    // one value drives the whole bar scrolled away or opened for input both put
    // top read through a lambda so every consumer resolves it in the draw layout
    val progressProvider = remember(collapse, activeProgress) {
        { maxOf(collapse.value, activeProgress.value) }
    }

    // boolean so it costs two recompositions per gesture rather than one per
    val blurActive by remember(browseListState, activeListState) {
        derivedStateOf {
            !browseListState.isScrollInProgress && !activeListState.isScrollInProgress
        }
    }

    val commit: (String) -> Unit = remember(navController, pauseSearchHistory) {
        { raw ->
            val text = raw.trim()
            if (text.isNotEmpty()) {
                focusManager.clearFocus()
                active = false
                when (val parsed = YouTubeUrlParser.parse(text)) {
                    is YouTubeUrlParser.ParsedUrl.Video ->
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))

                    is YouTubeUrlParser.ParsedUrl.Artist ->
                        navController.navigate("artist/${parsed.id}")

                    null ->
                        navController.navigate("search/${URLEncoder.encode(text, "UTF-8")}")
                }
                if (!pauseSearchHistory) {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.query { insert(SearchHistory(query = text)) }
                    }
                }
            }
        }
    }

    BackHandler(enabled = active) {
        active = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // focus follows the state rather than the state following focus so
    // the field back navigating into an open field all take the same path
    LaunchedEffect(active) {
        if (active) {
            runCatching { focusRequester.requestFocus() }
        } else {
            keyboardController?.hide()
        }
    }

    // leaving the tab must not strand a keyboard over the next screen
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val showBrowse by remember { derivedStateOf { activeProgress.value < 0.995f } }
    val showActive by remember { derivedStateOf { activeProgress.value > 0.005f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
    ) {
        // everything the top bar blurs has to be captured here and the bar itself
        // outside this subtree or it would blur its own output
        // the capture only has a consumer once the bar has something to blur
        // records the entire scrolling list into a rendernode on every frame of
        // nothing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { blurActive && progressProvider() > 0.01f })
        ) {
            if (showBrowse) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - activeProgress.value }
                ) {
                    SearchBrowseContent(
                        listState = browseListState,
                        navController = navController,
                    )
                }
            }
            if (showActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = activeProgress.value }
                ) {
                    SearchActiveContent(
                        listState = activeListState,
                        query = query.text,
                        state = suggestionState,
                        onFill = { text ->
                            query = TextFieldValue(text, TextRange(text.length))
                        },
                        onSubmit = commit,
                        onDeleteHistory = { entry ->
                            coroutineScope.launch(Dispatchers.IO) {
                                database.query { delete(entry) }
                            }
                        },
                        navController = navController,
                    )
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
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navController.navigate("settings") })
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = commit,
                placeholder = "Search for any tracks, albums, lyrics...",
                focused = active,
                leading = if (active) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = SearchColors.Secondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    active = false
                                    focusManager.clearFocus()
                                },
                        )
                    }
                } else {
                    null
                },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = SearchColors.Secondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { query = TextFieldValue("") },
                        )
                    }
                } else {
                    null
                },
                focusRequester = focusRequester,
                onFocusChanged = { focused -> if (focused) active = true },
                readOnlyClick = if (!active) {
                    { active = true }
                } else {
                    null
                },
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// browse state
// ───────────────────────────────────────────────────────────────────────────

// moods and genres two tiles to a row rows are pre chunked into list items rather
@Composable
private fun SearchBrowseContent(
    listState: LazyListState,
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val sections by viewModel.moodAndGenres.collectAsState()
    val bottomInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = searchTopBarHeight(withTitle = true),
            bottom = bottomInset + 24.dp,
        ),
    ) {
        val list = sections
        if (list == null) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchLoadingDots()
                }
            }
        } else {
            list.forEachIndexed { sectionIndex, section ->
                item(key = "header_${sectionIndex}_${section.title}") {
                    SearchSectionHeader(
                        title = section.title,
                        ruleAbove = false,
                        ruleBelow = true,
                    )
                }
                val rows = section.items.chunked(2)
                itemsIndexed(
                    items = rows,
                    key = { rowIndex, _ -> "row_${sectionIndex}_$rowIndex" },
                    contentType = { _, _ -> "moodRow" },
                ) { _, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SearchHorizontalPadding, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->
                            // requested from composition so only tiles the user actually brings on
                            // screen ever cost a network round trip the viewmodel dedupes
                            LaunchedEffect(item.endpoint.browseId, item.endpoint.params) {
                                viewModel.requestCovers(item.endpoint.browseId, item.endpoint.params)
                            }
                            MoodTile(
                                title = item.title,
                                stripeColor = item.stripeColor,
                                covers = viewModel.covers[
                                    coverKey(item.endpoint.browseId, item.endpoint.params)
                                ].orEmpty(),
                                onClick = {
                                    navController.navigate(
                                        "genre/${item.endpoint.browseId}?params=${item.endpoint.params}"
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                item(key = "gap_${sectionIndex}") { Spacer(modifier = Modifier.height(22.dp)) }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// active state history or suggestions + top results
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchActiveContent(
    listState: LazyListState,
    query: String,
    state: com.example.musicfy.viewmodels.SearchSuggestionViewState,
    onFill: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    navController: NavController,
) {
    val playerConnection = LocalPlayerConnection.current
    val bottomInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = searchTopBarHeight(withTitle = false),
            bottom = bottomInset + 24.dp,
        ),
    ) {
        if (query.isEmpty()) {
            if (state.history.isNotEmpty()) {
                item(key = "history_header") {
                    SearchSectionHeader(title = "Search History")
                }
                items(
                    items = state.history,
                    key = { "history_${it.query}" },
                    contentType = { "history" },
                ) { entry ->
                    SearchSimpleRow(
                        text = entry.query,
                        leadingIcon = R.drawable.history,
                        onClick = { onSubmit(entry.query) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RowGlyph(R.drawable.close) { onDeleteHistory(entry) }
                                Spacer(modifier = Modifier.width(4.dp))
                                RowGlyph(R.drawable.arrow_top_left) { onFill(entry.query) }
                            }
                        },
                    )
                }
            }
        } else {
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestion_header") {
                    SearchSectionHeader(title = "Suggestion")
                }
                items(
                    items = state.suggestions,
                    key = { "suggestion_$it" },
                    contentType = { "suggestion" },
                ) { suggestion ->
                    SearchSimpleRow(
                        text = suggestion,
                        pill = true,
                        onClick = { onSubmit(suggestion) },
                        trailing = { RowGlyph(R.drawable.arrow_top_left) { onFill(suggestion) } },
                    )
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "top_result_header") {
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchSectionHeader(title = "Top Result")
                }
                items(
                    items = state.items,
                    key = { "item_${it.id}" },
                    contentType = { "ytItem" },
                ) { item ->
                    SuggestionResultRow(
                        item = item,
                        onClick = {
                            when (item) {
                                is SongItem -> playerConnection?.playQueue(
                                    YouTubeQueue.radio(item.toMediaMetadata())
                                )

                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        },
                    )
                }
            }

            if (state.suggestions.isEmpty() && state.items.isEmpty()) {
                item(key = "suggest_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SearchLoadingDots()
                    }
                }
            }
        }
    }
}

// small tappable glyph used at the end of a suggestion history row
@Composable
private fun RowGlyph(icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SearchColors.Secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// a recommended item under top result while typing artwork title and what kind of thing it is
@Composable
private fun SuggestionResultRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> "Album" + (item.artists?.takeIf { it.isNotEmpty() }
            ?.let { " • " + it.joinToString { a -> a.name } } ?: "")

        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist" + (item.author?.name?.let { " • $it" } ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchArtwork(
            url = item.thumbnail,
            size = 44.dp,
            circle = item is ArtistItem,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = SearchColors.Primary,
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
    }
}
