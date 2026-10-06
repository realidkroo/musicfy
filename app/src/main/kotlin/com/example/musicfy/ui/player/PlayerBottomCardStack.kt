// PlayerBottomCardStack.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.viewmodels.LyricsScreenViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The card's visible height. Its body carries on below the screen edge - see [BottomBleed]. */
internal val PlayerCardHeight = 54.dp
internal val PlayerCardCorner = 28.dp

/** How far the card in front sits from the screen's bottom edge, back card included. */
private val PeekOffset = 9.dp

/** The whole deck's height on screen: the card plus the back card peeking over it. */
internal val PlayerDeckHeight = PlayerCardHeight + PeekOffset

/**
 * The card is longer than the screen shows: its body runs on past the bottom edge, so the screen
 * cuts it rather than the card ending in a line. (It used to be sized with a plain `height`, which
 * the 50dp slot clamped right back - the "bleed" never happened and the card's bottom border drew
 * a hard line across the bottom of the screen.)
 */
private val BottomBleed = 100.dp

private val ArtworkSize = 28.dp
private val ArtworkShape = RoundedCornerShape(ArtworkSize * 0.3f)

private val PeekInset = 10.dp

private const val BackCardScale = 0.94f

private val FlipDistance = 70.dp

/** Card in front: fill and border. The expanding surface starts from exactly these. */
internal const val FrontCardFillAlpha = 0.11f
internal const val FrontCardBorderAlpha = 0.20f
private const val BackCardFillAlpha = 0.06f
private const val BackCardBorderAlpha = 0.10f

/** Where each card's content starts, so a page can grow it from the right spot. */
private val LyricContentStart = 18.dp
private val QueueContentStart = 14.dp

/** The card content fades as it flies, handing over to the page's copy of it. */
private const val FlightFadeEnd = 0.38f

@Composable
internal fun PlayerBottomCardStack(
    glassState: GlassState,
    progressProvider: () -> Float,
    deck: PlayerDeckState,
    transition: PlayerPageTransition,
    morph: CardMorphState,
    onOpen: (PlayerPage) -> Unit,
    /** Long press opens the player editor, matching the cover's gesture. */
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val lyricsViewModel: LyricsScreenViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(lyricsViewModel::ensureLyricsLoaded)
    }

    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val queueItems by playerConnection.queueItems.collectAsState()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsState()

    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyrics(raw)
    }

    val nextSong = remember(queueItems, currentMediaItemIndex) {
        queueItems.getOrNull(currentMediaItemIndex + 1)
    }

    val flipDistancePx = with(density) { FlipDistance.toPx() }

    // How far the queue card is in front: the flip, pulled forward while lyrics are open and back
    // while the queue is, so the deck always offers the page that isn't showing.
    fun queueFrontness(): Float {
        val lyricsP = transition.lyricsProgress.coerceIn(0f, 1f)
        val queueP = transition.queueProgress.coerceIn(0f, 1f)
        val flip = deck.flip.value
        return (flip + (1f - flip) * lyricsP) * (1f - queueP)
    }

    // Only the order is decided in composition, and it changes once per flip - everything that
    // moves is read in layers below, so the deck doesn't recompose while it animates.
    val queueOnTop by remember { derivedStateOf { queueFrontness() >= 0.5f } }
    // The card growing into its page is drawn over the other one, at the front slot.
    val flying by remember {
        derivedStateOf {
            val linked = transition.linked
            if (linked != PlayerPage.NONE && transition.headerProgress > 0.0005f) linked else PlayerPage.NONE
        }
    }
    // Fully grown into its page, the card is invisible: don't keep its sweep running behind the
    // page. It comes back as soon as the page starts shrinking, and is still faded out until well
    // into the close, so it has settled by the time it shows.
    val absorbed by remember {
        derivedStateOf {
            val linked = transition.linked
            if (linked != PlayerPage.NONE && transition.headerProgress >= 0.98f) linked else PlayerPage.NONE
        }
    }
    val topCard = when (flying) {
        PlayerPage.NONE -> if (queueOnTop) PlayerPage.QUEUE else PlayerPage.LYRICS
        else -> flying
    }
    val order = if (topCard == PlayerPage.QUEUE) {
        listOf(PlayerPage.LYRICS, PlayerPage.QUEUE)
    } else {
        listOf(PlayerPage.QUEUE, PlayerPage.LYRICS)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerDeckHeight)
            .onGloballyPositioned { coords ->
                // The front card's slot, through to the screen's bottom edge: where the expanding
                // surface starts from and shrinks back into.
                val bounds = coords.boundsInRoot()
                val cardTop = bounds.bottom - with(density) { PlayerCardHeight.toPx() }
                val slot = Rect(bounds.left, cardTop, bounds.right, bounds.bottom + with(density) { BottomBleed.toPx() })
                if (morph.cardRect != slot) morph.cardRect = slot
            }
            // Directional on purpose. A plain vertical `draggable` claimed *every* drag that
            // began on the card, so touching it made the player impossible to swipe closed. The
            // flip only ever runs upward, so a downward drag is left unconsumed for the sheet.
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var claimed = false
                    var travelled = 0f
                    var dragAccum = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dy = change.positionChange().y
                        travelled += dy

                        if (!claimed) {
                            if (travelled < -slop) {
                                claimed = true
                                dragAccum = 0f
                            } else if (travelled > slop) {
                                // Heading down: hand the gesture to the sheet and stop watching.
                                break
                            }
                        }

                        if (claimed) {
                            change.consume()
                            dragAccum += -dy
                            val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
                            val target = if (deck.frontIndex == 0) frac else 1f - frac
                            scope.launch { deck.flip.snapTo(target) }
                        }
                    }

                    if (claimed) {
                        if (abs(dragAccum) >= flipDistancePx * 0.4f) {
                            deck.frontIndex = 1 - deck.frontIndex
                        }
                        scope.launch {
                            deck.flip.animateTo(
                                targetValue = deck.frontIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    }
                }
            }
            .pointerInput(onLongPress) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    // Whichever card is in front, not the flip alone: with a page open the deck
                    // reorders itself, and tapping the card on top has to open that card.
                    onTap = { onOpen(if (queueFrontness() >= 0.5f) PlayerPage.QUEUE else PlayerPage.LYRICS) },
                )
            }
    ) {
        // Keyed and in one loop, so reordering the deck moves the cards instead of rebuilding
        // them - the lyric card keeps its line, its sweep and its slide through a flip.
        for (page in order) {
            if (page == absorbed) continue
            key(page) {
                DeckCard(
                    page = page,
                    frontnessProvider = {
                        val q = queueFrontness()
                        if (page == PlayerPage.QUEUE) q else 1f - q
                    },
                    transition = transition,
                    morph = morph,
                ) {
                    if (page == PlayerPage.LYRICS) {
                        MiniLyricsTicker(
                            lines = lines,
                            loaded = lyricsEntity != null,
                            morph = morph,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = LyricContentStart, end = LyricContentStart),
                        )
                    } else {
                        QueueCardContent(
                            nextTitle = nextSong?.title,
                            nextArtist = nextSong?.artist,
                            artworkUri = nextSong?.artworkUri?.toString(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DeckCard(
    page: PlayerPage,
    frontnessProvider: () -> Float,
    transition: PlayerPageTransition,
    morph: CardMorphState,
    content: @Composable () -> Unit,
) {
    // Growing into its page: the card's surface is handed to the expanding one (drawn by the
    // player), and its content flies to where the page has the same thing.
    fun flightProgress(): Float =
        if (transition.linked == page) transition.headerProgress else 0f

    fun frontness(): Float =
        if (flightProgress() > 0.0005f) 1f else frontnessProvider().coerceIn(0f, 1f)

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayerCardHeight)
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                val f = frontness()
                val scale = mixF(BackCardScale, 1f, f)
                scaleX = scale
                scaleY = scale
                translationY = -mixF(PeekOffset.toPx(), 0f, f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                if (flightProgress() > 0.0005f) return@drawBehind
                val f = frontness()
                val inset = mixF(PeekInset.toPx(), 0f, f)
                val corner = PlayerCardCorner.toPx()
                // Only the strip of a back card that shows above the front one is drawn. The front
                // card's top sits CardHeight above the bottom; this card's sits higher by the peek.
                val scale = mixF(BackCardScale, 1f, f)
                val otherF = 1f - f
                val thisTop = PlayerCardHeight.toPx() * scale + mixF(PeekOffset.toPx(), 0f, f)
                val otherTop = PlayerCardHeight.toPx() * mixF(BackCardScale, 1f, otherF) +
                    mixF(PeekOffset.toPx(), 0f, otherF)
                val exposed = (thisTop - otherTop) / scale
                val bottom = size.height + BottomBleed.toPx()
                val visibleBottom = if (exposed > 0f) minOf(exposed, bottom) else bottom
                if (visibleBottom <= 0f) return@drawBehind

                clipRect(bottom = visibleBottom) {
                    val topLeft = Offset(inset, 0f)
                    val cardSize = Size(size.width - inset * 2f, bottom)
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(BackCardFillAlpha, FrontCardFillAlpha, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    // The border runs down the sides and off the screen with the body, so there's
                    // no line along the bottom.
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(BackCardBorderAlpha, FrontCardBorderAlpha, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    // Measured only at rest in front: mid-flip the card is scaled, mid-flight the
                    // numbers would chase themselves.
                    if (flightProgress() > 0.0005f || frontnessProvider() < 0.999f) return@onGloballyPositioned
                    val r = coords.boundsInRoot()
                    with(density) {
                        if (page == PlayerPage.LYRICS) {
                            morph.cardLyricAnchor = Offset(r.left + LyricContentStart.toPx(), r.center.y)
                        } else {
                            morph.cardArtAnchor = Offset(
                                r.left + QueueContentStart.toPx(),
                                r.center.y - ArtworkSize.toPx() / 2f,
                            )
                        }
                    }
                }
                .graphicsLayer {
                    val e = flightProgress()
                    if (e <= 0.0005f) {
                        val f = frontness()
                        alpha = f * f * (3f - 2f * f)
                        return@graphicsLayer
                    }
                    alpha = 1f - smoothstepF(e / FlightFadeEnd)
                    val source: Offset
                    val target: Offset
                    val grow: Float
                    if (page == PlayerPage.LYRICS) {
                        source = morph.cardLyricAnchor.let {
                            if (it.isSpecified()) Offset(it.x - morph.cardLyricScroll, it.y) else it
                        }
                        target = morph.pageLyricAnchor
                        grow = 1f / CardToPageLyricScale
                    } else {
                        source = morph.cardArtAnchor
                        target = morph.pageArtAnchor
                        grow = 1f / CardToPageArtScale
                    }
                    if (!source.isSpecified() || !target.isSpecified()) {
                        // Nothing on the page to become: lift off and fade.
                        translationY = -e * 40.dp.toPx()
                        return@graphicsLayer
                    }
                    // Pivot on the anchor itself, so scaling grows the content out of that point
                    // and the translation carries the point along the path.
                    val start = if (page == PlayerPage.LYRICS) {
                        LyricContentStart.toPx() - morph.cardLyricScroll
                    } else {
                        QueueContentStart.toPx()
                    }
                    val pivotY = if (page == PlayerPage.LYRICS) {
                        size.height / 2f
                    } else {
                        size.height / 2f - ArtworkSize.toPx() / 2f
                    }
                    transformOrigin = TransformOrigin(
                        (start / size.width).coerceIn(-4f, 4f),
                        pivotY / size.height,
                    )
                    val s = mixF(1f, grow, e)
                    scaleX = s
                    scaleY = s
                    translationX = (target.x - source.x) * e
                    translationY = (target.y - source.y) * e
                }
        ) {
            content()
        }
    }
}

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified

@Composable
private fun QueueCardContent(
    nextTitle: String?,
    nextArtist: String?,
    artworkUri: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = QueueContentStart)
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).fadingRightEdge()) {

            Text(
                text = "Next Song",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = if (nextTitle != null) {
                    listOfNotNull(nextTitle, nextArtist?.takeIf { it.isNotBlank() })
                        .joinToString(" - ")
                } else {
                    "End of queue"
                },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (nextTitle != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private fun Modifier.fadingRightEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fade = Brush.horizontalGradient(
            0f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = fade, blendMode = BlendMode.DstIn)
        }
    }
