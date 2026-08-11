// GenreScreen.kt
// The page behind every mood/genre tile on the search landing screen.
//
// Structurally a clone of the home feed — full-bleed artwork header, sections of horizontally
// scrolling cards, and a "From the community" row of big fanned-cover cards — with the same
// scroll-driven top bar: the genre's own title rises out of the header and becomes the bar title,
// and travels back down on the way up. Like the rest of the rebuilt search surfaces it uses no
// Material 3 components.
//
// The header artwork is the genre's own top playlist cover rather than a bundled stock image: it
// is real content for the category, it needs no asset shipped with the app and no licence, and it
// changes as the category's featured playlists change. Categories that return no artwork fall back
// to a gradient built from the tile's own colour, so the header never renders as a blank slab.

package com.example.musicfy.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.viewmodels.CommunityPlaylistItem
import com.example.musicfy.viewmodels.GenreViewModel

/** How much of the header has to scroll away before the title has fully moved into the bar. */
private val HeaderHeight = 300.dp

@Composable
fun GenreScreen(
    navController: NavController,
    pureBlack: Boolean = false,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    val browseResult by viewModel.result.collectAsState()
    val community by viewModel.communityPlaylists.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // Latched and animated on the shared curve, exactly like the search bar — see
    // rememberCollapseProgress. The threshold is most of the header's height, so the title only
    // hands over once the artwork has genuinely gone.
    val enterPx = with(density) { (HeaderHeight - statusBar - 96.dp).toPx().coerceAtLeast(1f) }
    val latch = remember(listState) { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val next = when {
                listState.firstVisibleItemIndex > 0 -> true
                offset > enterPx -> true
                offset < enterPx * 0.55f -> false
                else -> latch[0]
            }
            latch[0] = next
            next
        }
    }
    val collapse = animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "genreCollapse",
    )
    val progressProvider = remember(collapse) { { collapse.value } }

    val title = browseResult?.title.orEmpty()
    val headerArt = remember(browseResult) {
        browseResult?.items
            ?.asSequence()
            ?.flatMap { it.items.asSequence() }
            ?.mapNotNull(YTItem::thumbnail)
            ?.firstOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
    ) {
        Box(modifier = Modifier.fillMaxSize().glassRoot(glassState)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            ) {
                item(key = "header") {
                    GenreHeader(
                        title = title,
                        artworkUrl = headerArt,
                        pureBlack = pureBlack,
                        progressProvider = progressProvider,
                    )
                }

                // Sections the server returned with no playable items at all rendered as a bare
                // heading and a blank strip. They are dropped rather than shown empty.
                val sections = browseResult?.items?.filter { it.items.isNotEmpty() }
                if (sections == null) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SearchLoadingDots()
                        }
                    }
                } else {
                    if (sections.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Nothing here yet",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = SearchColors.Secondary,
                                )
                            }
                        }
                    }
                    sections.forEachIndexed { index, section ->
                        item(key = "section_${index}_${section.title}") {
                            if (index == 0) Spacer(modifier = Modifier.height(10.dp))
                            SearchSectionHeader(
                                title = section.title ?: "Featured",
                                ruleAbove = false,
                                ruleBelow = false,
                                large = false,
                            )
                        }
                        item(key = "row_${index}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SearchHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(
                                    items = section.items,
                                    key = { it.id },
                                    contentType = { "genreCard" },
                                ) { item ->
                                    GenreItemCard(
                                        item = item,
                                        onClick = {
                                            when (item) {
                                                is SongItem -> playerConnection?.playQueue(
                                                    YouTubeQueue.radio(item.toMediaMetadata())
                                                )

                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                is PlaylistItem ->
                                                    navController.navigate("online_playlist/${item.id}")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        item(key = "gap_$index") { Spacer(modifier = Modifier.height(26.dp)) }

                        // Slotted in after the first server section, the way the home feed puts the
                        // community row between its own rows rather than at the very bottom.
                        if (index == 0) {
                            val cards = community
                            if (cards == null || cards.isNotEmpty()) {
                                item(key = "community_header") {
                                    SearchSectionHeader(
                                        title = "From the community",
                                        ruleAbove = false,
                                        ruleBelow = false,
                                        large = false,
                                    )
                                }
                                item(key = "community_row") {
                                    if (cards == null) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(300.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            SearchLoadingDots()
                                        }
                                    } else {
                                        LazyRow(
                                            contentPadding = PaddingValues(
                                                horizontal = SearchHorizontalPadding
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            items(
                                                items = cards,
                                                key = { it.playlist.id },
                                                contentType = { "communityCard" },
                                            ) { card ->
                                                GenreCommunityCard(
                                                    item = card,
                                                    onClick = {
                                                        navController.navigate(
                                                            "online_playlist/${card.playlist.id}"
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                item(key = "community_gap") { Spacer(modifier = Modifier.height(26.dp)) }
                            }
                        }
                    }
                }
            }
        }

        GenreTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            title = title,
            pureBlack = pureBlack,
            onBack = { navController.navigateUp() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Full-bleed artwork, the genre title, and its description.
 *
 * The artwork parallaxes at half the scroll rate and the title rises at full rate, which is what
 * makes the title read as travelling up into the bar rather than the whole header sliding as one
 * slab. Both are graphicsLayer reads — no recomposition per frame.
 */
@Composable
private fun GenreHeader(
    title: String,
    artworkUrl: String?,
    pureBlack: Boolean,
    progressProvider: () -> Float,
) {
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val parallaxPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            // Without this the artwork's parallax (which moves it DOWN as the page scrolls up)
            // slid the blurred image and its gradient straight out of the header and across the
            // first row of cards — the "gradient flying" over the sections.
            .clipToBounds()
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl.resize(720, 720),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = progressProvider() * parallaxPx
                        // Heavily blurred: this is an ambient wash behind the title, not a picture
                        // the user is meant to read. Built once here, not per frame — the radius
                        // is constant and only the layer's transform changes.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(
                                    60f,
                                    60f,
                                    android.graphics.Shader.TileMode.CLAMP,
                                )
                                .asComposeRenderEffect()
                        }
                        alpha = 1f - progressProvider() * 0.5f
                    },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF3A3A44), Color(0xFF1A1A1F), pageColor)
                        )
                    )
            )
        }

        // Fades the artwork into the page on both edges so there is no hard seam under the status
        // bar or above the first row.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        0f to pageColor.copy(alpha = 0.55f),
                        0.35f to pageColor.copy(alpha = 0.25f),
                        0.72f to pageColor.copy(alpha = 0.85f),
                        1f to pageColor,
                    )
                    onDrawBehind { drawRect(brush = brush) }
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = SearchHorizontalPadding, end = SearchHorizontalPadding, bottom = 28.dp)
                .graphicsLayer {
                    val p = progressProvider()
                    // Gone by the time the bar's own copy has faded in, so the two are never both
                    // legible at once.
                    alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    val scale = 1f - p * 0.12f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = SearchColors.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = genreDescription(title),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    color = SearchColors.Secondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** Back affordance plus the title the header hands over to, over the same progressive glass. */
@Composable
private fun GenreTopBar(
    glassState: GlassState,
    progressProvider: () -> Float,
    title: String,
    pureBlack: Boolean,
    onBack: () -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val risePx = with(density) { 18.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth()) {
        val showGlass by remember { derivedStateOf { progressProvider() > 0.01f } }
        if (showGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progressProvider().coerceIn(0f, 1f) }
            ) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 42f * progressProvider().coerceIn(0f, 1f) },
                    foundationColor = pageColor,
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        onDrawBehind {
                            val p = progressProvider().coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to pageColor.copy(alpha = p * 0.95f),
                                    0.6f to pageColor.copy(alpha = p * 0.6f),
                                    1f to Color.Transparent,
                                )
                            )
                        }
                    }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBar + SearchTopClearance)
                .height(56.dp)
                .padding(horizontal = SearchHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = SearchColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    // Arrives only once the header's own copy has gone, and rises the last few dp
                    // into place rather than simply appearing.
                    val p = progressProvider()
                    alpha = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    translationY = (1f - alpha) * risePx
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Cards
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** One playlist/album/artist/song in a genre section row. */
@Composable
private fun GenreItemCard(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name }.orEmpty().ifEmpty { "Album" }
        is ArtistItem -> "Artist"
        is PlaylistItem -> item.author?.name ?: "Playlist"
    }

    Column(
        modifier = Modifier
            .width(146.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        SearchArtwork(
            url = item.thumbnail,
            size = 146.dp,
            circle = item is ArtistItem,
            corner = 10.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = SearchColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = SearchColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The big "From the community" card: the playlist's own songs fanned out behind its title, exactly
 * the treatment the home feed uses — rebuilt here on Box/clip/background instead of an M3 `Card`.
 */
@Composable
private fun GenreCommunityCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(268.dp)
            .height(320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF151519))
            .searchCardBorder(20.dp)
            .clickable(onClick = onClick),
    ) {
        // The fan. Rotated as one group so the covers stay in register with each other, then
        // clipped by the card — the same construction as the home card, minus its per-card palette
        // extraction (three bitmap decodes per card is too much for a row that scrolls).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .graphicsLayer {
                    rotationZ = 35f
                    scaleX = 1.3f
                    scaleY = 1.3f
                    translationX = 40.dp.toPx()
                    translationY = -26.dp.toPx()
                },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.songs.take(3).forEach { song ->
                        AsyncImage(
                            model = song.thumbnail.resize(256, 256),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.songs.drop(3).take(2).forEach { song ->
                        AsyncImage(
                            model = song.thumbnail.resize(256, 256),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 16.dp, bottom = 22.dp),
        ) {
            Text(
                text = item.playlist.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.playlist.songCountText?.let { count ->
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}
