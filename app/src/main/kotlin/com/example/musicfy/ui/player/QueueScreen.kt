// QueueScreen.kt

package com.example.musicfy.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.ui.component.PlayingIndicatorBox
import com.example.musicfy.ui.component.SwipeAction
import com.example.musicfy.ui.component.SwipeActionsBox
import com.example.musicfy.ui.component.SwipeQueueColor
import com.example.musicfy.ui.component.SwipeSaveColor
import com.example.musicfy.ui.player.models.QueueItemData
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val RowArtSize = 44.dp

/** How far a finger has to scroll one way before the controls hide or come back. */
private const val ImmersiveScrollPx = 24f

/** The artwork's top inside a 60dp row - where the card's artwork grows to. */
private val RowArtTop = (60.dp - RowArtSize) / 2
private val RowArtShape = RoundedCornerShape(8.dp)

/** The handle's touch target. Centred on the button column below (34dp at [PlayerHorizontalPadding]). */
private val HandleSize = 44.dp
private val RowEndPadding = PlayerHorizontalPadding + 17.dp - HandleSize / 2

private val RowHighlightInset = 10.dp
private val RowHighlightShape = RoundedCornerShape(14.dp)

private val ListTopFade = 18.dp

/** Long, like the lyrics: rows passing under the button column fade out into the seek bar. */
private val ListBottomFade = 96.dp

/** Thumbnails are drawn at 44dp: ask for a small image, not the 1200px one the player uses. */
private const val RowArtPx = 144

/** If the player never reports a committed move back (the song vanished meanwhile), stop waiting. */
private const val CommitWaitMs = 1_000L

/**
 * The queue page, opened from the player like the lyrics and laid out the same way: the cover and
 * title morph into the header (drawn by the player, not here), the controls stay below. Unlike
 * lyrics it never hides the controls on its own.
 *
 * Sections, top to bottom: History (what already played - scrolled past on open, so the page
 * opens on the song playing), Now Playing, Continue Playing (what the user queued), and Autoplay
 * (similar songs the infinite queue lined up after it). Songs that are still to come are
 * reordered with the handle on the right, within their own section.
 */
@Composable
fun QueueScreen(
    onClose: () -> Unit,
    /** Distance from the bottom of the screen to the top of the seek bar: the list fades out there. */
    contentBottomInset: Dp,
    /**
     * How far the player's button column reaches up from there. Rows scroll under it, as lyrics
     * do, but the list keeps this much room at its end so the last songs can still be brought out
     * from beneath the buttons.
     */
    controlsClearance: Dp,
    /** Sends a heart flying when the header's like button likes the song. */
    likeBurst: LikeBurstState? = null,
    /**
     * How far the page has grown out of the bottom card (unclamped); 1 when it isn't growing from
     * it. The next song's row grows out of the card's, the rest rise into place.
     */
    enterProgressProvider: () -> Float = { 1f },
    morph: CardMorphState? = null,
    /** Scrolling down the list hides the player's controls, like the lyrics; scrolling up shows them. */
    onImmersiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    // One value for list + current index + title, so the sections are never split against a
    // stale index.
    val snapshot by playerConnection.queueSnapshot.collectAsState()
    val queueItems = snapshot.items
    val currentIndex = snapshot.currentIndex
    val queueTitle = snapshot.title
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val haptic = LocalHapticFeedback.current

    val playerRows = remember(queueItems, currentIndex) { buildQueueRows(queueItems, currentIndex) }

    // The list's own copy while a drag is reordering it. Moves land here straight away and reach
    // the player once, when the finger lifts - one timeline change per drag rather than one per
    // row passed, and no player update can yank the rows around mid-drag.
    var localRows by remember { mutableStateOf<List<QueueRow>?>(null) }
    var draggingSection by remember { mutableStateOf<QueueSection?>(null) }
    var awaitingCommit by remember { mutableStateOf(false) }
    val rows = localRows ?: playerRows

    // The committed order comes back from the player as a new playerRows: switch back to it then.
    LaunchedEffect(playerRows) {
        if (draggingSection == null) {
            localRows = null
            awaitingCommit = false
        }
    }
    LaunchedEffect(awaitingCommit) {
        if (awaitingCommit) {
            delay(CommitWaitMs)
            localRows = null
            awaitingCommit = false
        }
    }

    val listState = rememberLazyListState(
        // Open on Now Playing with History tucked above it. From then on the list keeps that
        // header where it is as songs move from one section into the next.
        initialFirstVisibleItemIndex = rows.indexOfFirst { it is QueueRow.Header && it.section == QueueSection.NOW_PLAYING }
            .coerceAtLeast(0),
    )
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        // A song dragged down under the buttons scrolls the list on, rather than only at the seek bar.
        scrollThresholdPadding = PaddingValues(bottom = controlsClearance),
    ) { from, to ->
        val current = localRows ?: return@rememberReorderableLazyListState
        val fromIndex = current.indexOfFirst { it.key == from.key }
        val toIndex = current.indexOfFirst { it.key == to.key }
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        localRows = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun commitDrag(key: Any) {
        val section = draggingSection
        draggingSection = null
        val local = localRows ?: return
        val index = local.indexOfFirst { it.key == key }
        val moved = local.getOrNull(index) as? QueueRow.Song
        val currentUid = queueItems.getOrNull(currentIndex)?.uid
        // Against the player's order as it is now, not as it was when the drag began: a song that
        // started playing mid-drag isn't in the list any more and has nowhere to be moved to.
        val playerIndex = moved?.let { song -> playerRows.indexOfFirst { it.key == song.key } } ?: -1
        val newAfter = moved?.let { precedingUid(local, index, currentUid) }
        val oldAfter = precedingUid(playerRows, playerIndex, currentUid)
        if (section == null || moved == null || playerIndex < 0 || newAfter == null || newAfter == oldAfter) {
            localRows = null
            return
        }
        playerConnection.moveQueueItem(moved.item.uid, newAfter)
        awaitingCommit = true
    }

    // Like the lyrics: scroll down and the controls slide away so the list has the screen, scroll
    // back up and they return. Driven by the user's own scrolling only - read off the list's nested
    // scroll, which programmatic scrolls (the reorder's edge scroll, a song moving sections) don't
    // go through - so the controls never vanish under a finger that's dragging a song.
    var immersive by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }
    val immersion by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 420,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
        ),
        label = "queueImmersion",
    )
    val scrollWatcher = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            private var travel = 0f

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (draggingSection != null) return androidx.compose.ui.geometry.Offset.Zero
                val dy = consumed.y
                if (dy == 0f) return androidx.compose.ui.geometry.Offset.Zero
                // Accumulated in one direction, so a jittery finger doesn't flip it back and forth.
                travel = if ((travel < 0f) == (dy < 0f)) travel + dy else dy
                if (travel < -ImmersiveScrollPx) immersive = true
                if (travel > ImmersiveScrollPx || !listState.canScrollBackward) immersive = false
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // The song the card shows as "Next Song": its row is what the card grows into.
    val nextUid = queueItems.getOrNull(currentIndex + 1)?.uid
    val nextRowIndex = rows.indexOfFirst { it is QueueRow.Song && it.item.uid == nextUid && it.section != QueueSection.HISTORY }
    if (morph != null) {
        LaunchedEffect(nextRowIndex) {
            if (nextRowIndex < 0) morph.pageArtAnchor = androidx.compose.ui.geometry.Offset.Unspecified
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(modifier = modifier.fillMaxSize()) {
        // Same box as the lyrics header: the cover is drawn here by the morph layer, so this only
        // holds its hitbox. The like button moved up here from the button column, which carries
        // shuffle and autoplay on this page.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = LyricsHeaderArtX, end = PlayerHorizontalPadding)
                .padding(top = LyricsHeaderArtTopFromStatusBar, bottom = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(LyricsHeaderArtSize)
                    .clip(RoundedCornerShape(10.dp))
                    // With the controls hidden, the first tap brings them back; the next closes.
                    .clickable { if (immersive) immersive = false else onClose() },
            )
            Spacer(modifier = Modifier.weight(1f))
            PressScaleActionButton(
                icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                onClick = {
                    if (likeBurst != null) {
                        likeBurst.likeFromButton(LikeSlot.QUEUE, trackInfo.liked, playerConnection::toggleLike)
                    } else {
                        playerConnection.toggleLike()
                    }
                },
                hasShadow = false,
                modifier = Modifier.likeButtonTarget(likeBurst, LikeSlot.QUEUE),
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 2.dp, bottom = controlsClearance + 8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Ends at the top of the seek bar and fades into it, like the lyrics. Rows run on
                // under the button column; the bottom padding above lets the last ones clear it.
                // With the controls hidden it runs on to the bottom of the screen. Read while
                // measuring, so the 420ms slide doesn't recompose the page every frame.
                .layout { measurable, constraints ->
                    val inset = (contentBottomInset * (1f - immersion)).roundToPx().coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = (constraints.minHeight - inset).coerceAtLeast(0),
                            maxHeight = (constraints.maxHeight - inset).coerceAtLeast(0),
                        )
                    )
                    layout(placeable.width, placeable.height + inset) { placeable.place(0, 0) }
                }
                .nestedScroll(scrollWatcher)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val top = (ListTopFade.toPx() / size.height).coerceIn(0f, 0.5f)
                    val bottom = 1f - (ListBottomFade.toPx() / size.height).coerceIn(0f, 0.5f)
                    val fade = Brush.verticalGradient(
                        0f to Color.Transparent,
                        top to Color.Black,
                        bottom to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                },
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
                contentType = { _, row -> row.contentType },
            ) { rowIndex, row ->
                val growsFromCard = morph != null && rowIndex == nextRowIndex
                // On each row's own root (not a wrapper): animateItem and the reorder's lift only
                // work on the item's root node.
                val enter = Modifier
                        .then(
                            if (growsFromCard) {
                                Modifier.onGloballyPositioned { coords ->
                                    // Before the row's own layer, so its flight doesn't move the
                                    // point it flies to.
                                    val origin = coords.positionInRoot()
                                    with(density) {
                                        morph!!.pageArtAnchor = androidx.compose.ui.geometry.Offset(
                                            origin.x + LyricsHeaderArtX.toPx(),
                                            origin.y + RowArtTop.toPx(),
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            val e = enterProgressProvider()
                            val flew = growsFromCard && applyCardFlight(
                                e = e,
                                cardAnchor = morph!!.cardArtAnchor,
                                pageAnchor = morph.pageArtAnchor,
                                localAnchor = androidx.compose.ui.geometry.Offset(
                                    LyricsHeaderArtX.toPx(),
                                    RowArtTop.toPx(),
                                ),
                                cardToPage = CardToPageArtScale,
                            )
                            if (!flew) applyPageRowEnter(e, rowIndex - nextRowIndex.coerceAtLeast(0))
                        }
                when (row) {
                    is QueueRow.Header -> QueueSectionHeader(
                        section = row.section,
                        queueTitle = queueTitle,
                        onClearHistory = {
                            playerConnection.removeQueueItems(
                                rows.mapNotNull { (it as? QueueRow.Song)?.takeIf { song -> song.section == QueueSection.HISTORY }?.item?.uid }
                            )
                        },
                        modifier = Modifier.animateItem().then(enter),
                    )

                    QueueRow.Empty -> Text(
                        text = "Nothing else queued",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .animateItem()
                            .then(enter)
                            .padding(start = LyricsHeaderArtX, top = 6.dp, bottom = 12.dp),
                    )

                    is QueueRow.Song -> ReorderableItem(
                        state = reorderState,
                        key = row.key,
                        modifier = enter,
                        // Only rows of the section being dragged in take part, so a song can't be
                        // dropped into another section (or into History) by dragging past a header.
                        enabled = row.section.reorderable &&
                            (draggingSection == null || draggingSection == row.section),
                    ) { isDragging ->
                        val uid = row.item.uid
                        val nowPlaying = row.section == QueueSection.NOW_PLAYING
                        SwipeActionsBox(
                            // The song playing can't be removed or moved next.
                            enabled = draggingSection == null && !nowPlaying,
                            start = {
                                SwipeAction(
                                    icon = R.drawable.remove,
                                    color = SwipeSaveColor,
                                    label = stringResource(R.string.remove_from_queue),
                                ) { playerConnection.removeQueueItems(listOf(uid)) }
                            },
                            end = {
                                SwipeAction(
                                    icon = R.drawable.playlist_play,
                                    color = SwipeQueueColor,
                                    label = stringResource(R.string.play_next),
                                ) { playerConnection.playQueueItemNext(uid) }
                            },
                        ) {
                            QueueSongRow(
                                item = row.item,
                                dimmed = row.section == QueueSection.HISTORY,
                                nowPlaying = nowPlaying,
                                isDragging = isDragging,
                                onClick = {
                                    if (nowPlaying) playerConnection.togglePlayPause() else playerConnection.playQueueItem(uid)
                                },
                                handle = if (!row.section.reorderable) {
                                    null
                                } else {
                                    {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(HandleSize)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        localRows = rows
                                                        draggingSection = row.section
                                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                    },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                        commitDrag(row.key)
                                                    },
                                                ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.queue_drag_handle),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = if (isDragging) 0.9f else 0.5f),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class QueueSection(val reorderable: Boolean) {
    HISTORY(false),
    NOW_PLAYING(false),
    CONTINUE(true),
    AUTOPLAY(true),
}

private sealed interface QueueRow {
    val key: Any
    val contentType: String

    data class Header(val section: QueueSection) : QueueRow {
        override val key: Any get() = "queue_header_${section.name}"
        override val contentType get() = "header"
    }

    data class Song(val item: QueueItemData, val section: QueueSection, override val key: Any) : QueueRow {
        override val contentType get() = "song"
    }

    data object Empty : QueueRow {
        override val key: Any get() = "queue_empty"
        override val contentType get() = "empty"
    }
}

/**
 * Splits the play order around the current song, which gets a section of its own. The rows keep
 * each song's key as it moves History <- Now Playing <- Continue, so a song change slides the
 * rows up a place rather than swapping them out. Autoplay songs are the tail of what's to come
 * (the service only appends them and keeps user-queued songs in front), so Continue Playing runs
 * up to the first of them.
 */
private fun buildQueueRows(items: List<QueueItemData>, currentIndex: Int): List<QueueRow> {
    val rows = ArrayList<QueueRow>(items.size + 4)
    // Window uids are hashed down to an Int; on the off chance two collide, keep the lazy keys
    // unique anyway rather than crash the list.
    val usedKeys = HashSet<Any>(items.size * 2)
    fun addSong(index: Int, section: QueueSection) {
        val item = items[index]
        val key: Any = if (usedKeys.add(item.uid)) item.uid else "${item.uid}#$index"
        rows += QueueRow.Song(item, section, key)
    }

    val current = currentIndex.coerceIn(-1, items.lastIndex)
    if (current > 0) {
        rows += QueueRow.Header(QueueSection.HISTORY)
        for (i in 0 until current) addSong(i, QueueSection.HISTORY)
    }
    if (current >= 0) {
        rows += QueueRow.Header(QueueSection.NOW_PLAYING)
        addSong(current, QueueSection.NOW_PLAYING)
    }

    rows += QueueRow.Header(QueueSection.CONTINUE)
    var i = current + 1
    val continueStart = rows.size
    while (i < items.size && !items[i].isAutoplay) addSong(i++, QueueSection.CONTINUE)

    if (i < items.size) {
        rows += QueueRow.Header(QueueSection.AUTOPLAY)
        while (i < items.size) addSong(i++, QueueSection.AUTOPLAY)
    } else if (rows.size == continueStart) {
        rows += QueueRow.Empty
    }
    return rows
}

/**
 * The song a row plays after, which is where a dropped song gets moved to: the row above it - at
 * the top of Continue Playing that's the Now Playing row. History is never an anchor.
 */
private fun precedingUid(rows: List<QueueRow>, index: Int, currentUid: Int?): Int? {
    if (index < 0) return null
    for (i in index - 1 downTo 0) {
        val song = rows[i] as? QueueRow.Song ?: continue
        return if (song.section == QueueSection.HISTORY) currentUid else song.item.uid
    }
    return currentUid
}

@Composable
private fun QueueSectionHeader(
    section: QueueSection,
    queueTitle: String?,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (section) {
        QueueSection.HISTORY -> stringResource(R.string.history)
        QueueSection.NOW_PLAYING -> "Now Playing"
        QueueSection.CONTINUE -> "Continue Playing"
        QueueSection.AUTOPLAY -> "Autoplay"
    }
    val subtitle = when (section) {
        QueueSection.HISTORY, QueueSection.NOW_PLAYING -> null
        QueueSection.CONTINUE -> queueTitle?.takeIf { it.isNotBlank() }?.let { "Queue from $it" }
        QueueSection.AUTOPLAY -> "Similar music"
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = LyricsHeaderArtX, end = PlayerHorizontalPadding, top = 16.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (section == QueueSection.HISTORY) {
            Text(
                text = "Clear",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun QueueSongRow(
    item: QueueItemData,
    dimmed: Boolean,
    /** The song playing: a light highlight and the playing bars over its cover. */
    nowPlaying: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    handle: (@Composable () -> Unit)?,
) {
    val artwork = remember(item.artworkUri) { item.artworkUri?.toString()?.resize(RowArtPx, RowArtPx) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer {
                val s = if (isDragging) 1.02f else 1f
                scaleX = s
                scaleY = s
            }
            // Inset like the swipe slab, so the press highlight and the lifted row share its shape.
            .padding(horizontal = RowHighlightInset)
            .clip(RowHighlightShape)
            .background(
                when {
                    isDragging -> Color.White.copy(alpha = 0.12f)
                    nowPlaying -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                enabled = !isDragging,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(start = LyricsHeaderArtX - RowHighlightInset, end = RowEndPadding - RowHighlightInset),
    ) {
        Box(modifier = Modifier.size(RowArtSize)) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RowArtShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .graphicsLayer { alpha = if (dimmed) 0.6f else 1f },
            )
            if (nowPlaying) NowPlayingIndicator()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (dimmed) 0.6f else 1f },
        ) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (handle != null) {
            Spacer(modifier = Modifier.width(4.dp))
            handle()
        } else {
            // Same text width as the rows that have a handle.
            Spacer(modifier = Modifier.width(4.dp + HandleSize))
        }
    }
}

/**
 * The playing bars (or the play glyph while paused) over the Now Playing cover. Its own scope, so
 * play/pause recomposes this and not the row or the list.
 */
@Composable
private fun NowPlayingIndicator() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    PlayingIndicatorBox(
        isActive = true,
        playWhenReady = isPlaying,
        shape = RowArtShape,
        modifier = Modifier.fillMaxSize(),
    )
}
