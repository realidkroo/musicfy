// LibraryDesign.kt
//
// Building blocks for the Library tab and its sub-screens (Songs, Artists, Playlists, Albums,
// Recently added, Downloaded).
//
// Reuses SearchColors / SearchField / SearchGlassTopBar from the Search rebuild rather than
// introducing a second palette and a second collapsing header. The two tabs sit one press apart in
// the nav bar; a near-identical-but-not-quite header would read as a bug, and sharing the actual
// implementation is the only way to guarantee the collapse feels the same in both places.

package com.example.musicfy.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.ui.screens.search.SearchHorizontalPadding
import com.example.musicfy.ui.screens.search.SearchTitleBlockHeight
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.launch

/** Title + subtitle block height, for the collapsing header on Library screens. */
val LibraryTitleBlockHeight: Dp = SearchTitleBlockHeight + 16.dp

// ---------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .height(1.dp)
            .background(SearchColors.Divider),
    )
}

@Composable
fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = SearchHorizontalPadding),
    )
}

/**
 * Artwork whose size comes entirely from the caller's modifier, for grid cells whose width isn't
 * known ahead of time.
 *
 * Not a wrapper around SearchArtwork: that one always ends its own chain with `.size(size)`, which
 * chained after a `fillMaxWidth().aspectRatio(1f)` collapses the cell — passing `0.dp` there
 * doesn't opt out of sizing, it forces the artwork to zero.
 *
 * Always a rounded square. The Library deliberately has no circular artwork anywhere, including
 * artists: one shape throughout means rows and grids line up on a single edge, and mixed shapes in
 * the same column read as inconsistency rather than as a category cue.
 */
@Composable
fun LibraryGridArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(SearchColors.TileHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(400, 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.fillMaxSize(0.35f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pinned
// ---------------------------------------------------------------------------------------------

/**
 * A pinned item: artwork only, no label. With a 3-wide grid and a 9-item cap the art alone
 * identifies what's pinned, and a caption under every tile would be nine repetitions of noise.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPinnedTile(
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(SearchColors.TileHigh)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl.resize(300, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category + wide cards
// ---------------------------------------------------------------------------------------------

/**
 * Two real covers, overlapped and tucked into the bottom-right corner of a category card.
 *
 * This is what makes the cards specific rather than decorative: each shows two of that category's
 * actual contents. The back tile is dimmed and offset so the pair reads as a stack rather than two
 * unrelated thumbnails side by side.
 *
 * Fills from the right, because the stack sits in the bottom-right corner — the rightmost tile is
 * the one fully on screen, so the first cover belongs there and the second tucks in behind it.
 * Draws nothing at all when there are no covers: an empty category should leave the corner bare,
 * not show placeholder squares implying content that isn't there.
 */
@Composable
private fun LibraryCoverStack(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    tile: Dp = 34.dp,
) {
    val shown = remember(covers) { covers.filterNotNull().take(2) }
    if (shown.isEmpty()) return

    Box(modifier = modifier.size(width = tile + 14.dp, height = tile)) {
        // Second cover first, so the first one overlaps it.
        shown.getOrNull(1)?.let { back ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer { alpha = 0.55f },
            ) {
                LibraryGridArtwork(url = back, corner = 8.dp, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(tile),
        ) {
            LibraryGridArtwork(url = shown[0], corner = 8.dp, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A 2x2 cluster for the wide cards, filled from the bottom-right backwards.
 *
 * Bottom-right is the brightest, least-occluded cell, so the newest cover goes there and older
 * ones recede up and to the left. Cells with no cover render nothing rather than a placeholder —
 * a library with two songs shows two tiles, not two tiles and two empty boxes.
 */
@Composable
private fun LibraryQuadCollage(
    covers: List<String?>,
    modifier: Modifier = Modifier,
) {
    val shown = remember(covers) { covers.filterNotNull() }
    if (shown.isEmpty()) return

    val alphas = listOf(0.5f, 0.65f, 0.8f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { col ->
                    val cell = row * 2 + col
                    // Cell 3 (bottom-right) takes cover 0, cell 2 takes cover 1, and so on.
                    val cover = shown.getOrNull(3 - cell)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer { alpha = alphas[cell] },
                    ) {
                        if (cover != null) {
                            LibraryGridArtwork(
                                url = cover,
                                corner = 6.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Centred placeholder for a screen or section with nothing in it. */
@Composable
fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Nothing to show here",
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** One of the four small cards: Songs / Albums / Artist / Playlist. */
@Composable
fun LibraryCategoryCard(
    title: String,
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SearchColors.Tile)
            .clickable(onClick = onClick),
    ) {
        // The covers sit flush in the corner and are allowed to run past the card's edge — the
        // clip above crops them, which is what gives the "tucked in" look rather than a floating
        // thumbnail with padding around it.
        LibraryCoverStack(
            covers = covers,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp, end = 8.dp),
        ) {
            // The count is the one piece of live information on the card, so it reads at nearly
            // the same weight as the label rather than as a caption above it.
            Text(
                text = count.toString(),
                color = SearchColors.Primary.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = title,
                color = SearchColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The full-width cards: "Recently added", "Downloaded", "All local music". */
@Composable
fun LibraryWideCard(
    title: String,
    label: String,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SearchColors.Tile)
            .clickable(onClick = onClick),
    ) {
        LibraryQuadCollage(
            covers = covers,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(84.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 14.dp, end = 110.dp),
        ) {
            Text(
                text = label,
                color = SearchColors.Primary.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = title,
                color = SearchColors.Primary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List rows
// ---------------------------------------------------------------------------------------------

/** A pill row in the A-Z lists. [large] gives Playlists their taller thumbnail from the mockup. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListRow(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val art = if (large) 52.dp else 34.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp)
            .clip(if (large) RoundedCornerShape(16.dp) else RoundedCornerShape(percent = 50))
            .background(SearchColors.Tile)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 10.dp, vertical = if (large) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryGridArtwork(
            url = thumbnailUrl,
            corner = 8.dp,
            modifier = Modifier.size(art),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SearchColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = SearchColors.Secondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Letter divider above each alphabetical section. */
@Composable
fun LibraryLetterHeader(letter: Char, modifier: Modifier = Modifier) {
    Text(
        text = letter.uppercaseChar().toString(),
        color = SearchColors.Primary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = SearchHorizontalPadding, vertical = 10.dp),
    )
}

/** "Play" + overflow, above the Albums and Recently-added grids. */
@Composable
fun LibraryPlayBar(
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .background(SearchColors.Tile)
                .clickable(onClick = onPlay),
        ) {
            Text(
                text = "Play",
                color = SearchColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SearchColors.Tile)
                .clickable(onClick = onMore),
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = "Shuffle",
                tint = SearchColors.Secondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// A-Z index
// ---------------------------------------------------------------------------------------------

/** Buckets by first letter, uppercased; anything not a letter lands under '#'. */
fun <T> groupByLetter(items: List<T>, keyOf: (T) -> String): Map<Char, List<T>> {
    val map = LinkedHashMap<Char, MutableList<T>>()
    for (item in items) {
        val first = keyOf(item).trim().firstOrNull()?.uppercaseChar()
        val letter = if (first != null && first.isLetter()) first else '#'
        map.getOrPut(letter) { mutableListOf() }.add(item)
    }
    return map
}

private val AlphabetRail = ('A'..'Z').toList() + '#'

/**
 * The A-Z index down the right edge, plus the big letter that zooms in while you scrub it.
 *
 * Fills its parent so the zoomed letter can sit over the list; the rail itself is pinned to the
 * right edge inside that.
 *
 * One custom down/move/up loop rather than composed tap + drag detectors: a drag detector alone
 * ignores a tap that never crosses touch slop, and stacking a tap detector on top puts two
 * recognisers on one strip, which is a reliable way to make a scrubber drop touches. Handling the
 * pointer directly makes a tap and the first frame of a drag the same path.
 */
@Composable
fun LibraryAlphabetIndex(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var railHeightPx by remember { mutableFloatStateOf(0f) }
    var lastLetter by remember { mutableStateOf<Char?>(null) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var touchYFraction by remember { mutableFloatStateOf(0.5f) }
    val scope = rememberCoroutineScope()
    val presence = remember { Animatable(0f) }

    fun letterAt(yPx: Float): Char {
        val fraction = (yPx / railHeightPx.coerceAtLeast(1f)).coerceIn(0f, 0.9999f)
        return AlphabetRail[(fraction * AlphabetRail.size).toInt().coerceIn(0, AlphabetRail.lastIndex)]
    }

    fun handle(yPx: Float) {
        touchYFraction = (yPx / railHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val letter = letterAt(yPx)
        activeLetter = letter
        if (letter != lastLetter && availableLetters.contains(letter)) {
            lastLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterSelected(letter)
        }
        scope.launch { presence.animateTo(1f, tween(130)) }
    }

    fun release() {
        lastLetter = null
        scope.launch {
            presence.animateTo(0f, tween(280, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)))
            activeLetter = null
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val overlayTravel = (maxHeight - 140.dp).coerceAtLeast(0.dp)

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.68f)
                .width(24.dp)
                .onGloballyPositioned { railHeightPx = it.size.height.toFloat() }
                .pointerInput(availableLetters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        handle(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            handle(change.position.y)
                            change.consume()
                        }
                        release()
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AlphabetRail.forEach { letter ->
                Text(
                    text = letter.lowercaseChar().toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (availableLetters.contains(letter)) {
                        SearchColors.Secondary
                    } else {
                        SearchColors.Secondary.copy(alpha = 0.28f)
                    },
                )
            }
        }

        val letter = activeLetter
        if (letter != null && presence.value > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 44.dp, top = overlayTravel * touchYFraction + 24.dp)
                    .size(88.dp)
                    .graphicsLayer {
                        alpha = presence.value
                        // Scale and fade together: scale alone reads as the letter popping into
                        // existence, fade alone loses the "zoomed in" motion entirely.
                        val s = 0.45f + 0.55f * presence.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.uppercaseChar().toString(),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
