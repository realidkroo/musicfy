// playerbottomcardstackkt
// the compact two card deck at the bottom of the expanded player a lyrics
// current synced line and a queue card showing what s up next swiping up or
// one is in front tapping the front card opens the corresponding full view

// the deck reads as physical layers rather than a crossfade the back card
// peeking above the front one scaled slightly down and dimmed during a drag
// together along one continuous progress value front sinking away back
// releasing halfway animates from wherever it actually is instead of snapping

// both cards are translucent by design each one draws its own frosted
// glasspillbackground over the player s existing glassroot capture so the
// player shows through the deck without that a plain semi transparent fill
// flat backdrop and the layers read as solid slabs with nothing visible

package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.viewmodels.LyricsScreenViewModel
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardHeight = 50.dp
private val CardCorner = 28.dp

// how far the card body runs past the bottom of the screen nothing is drawn
private val BottomBleed = 100.dp

// only the top corners are rounded the deck is anchored flush to the bottom of
private val CardShape = RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner)

// squircle not a circle deriving this from the card s own corner to height ratio
private val ArtworkSize = 28.dp
private val ArtworkShape = RoundedCornerShape(ArtworkSize * 0.3f)

// how far above the front card the back card s edge peeks out
private val PeekOffset = 9.dp

// back card is inset horizontally on both sides so the deck narrows going back
private val PeekInset = 10.dp

private const val BackCardScale = 0.94f

// drag distance that counts as a full flip past halfway on release commits the swap
private val FlipDistance = 70.dp

@Composable
fun PlayerBottomCardStack(
    glassState: GlassState,
    progressProvider: () -> Float,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    // 0 on the main player 1 on the lyrics page forces the queue card to the front as
    lyricsProgressProvider: () -> Float = { 0f },
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    // mid drag on the player sheet the glassroot capture this reads is still
    // cards frosted backings sample stale offsets and the deck flashes shows
    // until something forces a redraw same guard seamblur already uses for the

    // 098 not 09 bottomsheet s own clip shape grows its visibleheight from a
    // to the full expandedbound as progress goes 0 > 1 see sheetclipshape in
    // at progress 09 that clip can still be ~10% of the sheet s height short of
    // edge on a tall screen tens of dp comfortably more than bottombleed below
    // exactly the gap that was showing as a flying bottom border mid swipe at
    // shortfall is small enough that bottombleed covers it with real margin to
    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // only lyricsscreen used to trigger the fetch so if you never opened that
    // genuinely nothing in the db and this card said no lyrics for songs that
    val lyricsViewModel: LyricsScreenViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(lyricsViewModel::ensureLyricsLoaded)
    }

    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()
    // non snapshot accessor for the karaoke sweep which samples per frame rather
    // subscribing to the 15hz ticker see rememberkaraokefilledchars
    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position }
    }
    val queueItems by playerConnection.queueItems.collectAsState()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsState()

    // same parse + index lookup lyricsscreen uses so the line shown here is
    // full view would highlight no second source of truth for what s playing
    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyrics(raw)
    }
    // keyed on lines for the same reason as lyricsscreen an unkeyed remember
    // the empty pre load list so the card permanently read no lyrics for this
    // the lyrics had arrived
    val currentEntry by remember(lines) {
        derivedStateOf {
            val idx = LyricsUtils.findCurrentLineIndex(lines, progress.position)
            lines.getOrNull(idx)
        }
    }

    val nextSong = remember(queueItems, currentMediaItemIndex) {
        queueItems.getOrNull(currentMediaItemIndex + 1)
    }

    // 0f = lyrics in front 1f = queue in front fractional values are mid drag
    // lets a released swipe continue from its real position rather than jumping
    val flip = remember { Animatable(0f) }
    val flipDistancePx = with(density) { FlipDistance.toPx() }

    // which card is settled in front 0 = lyrics 1 = queue tracked separately
    // because the gesture is a toggle not a directional scrub a swipe always
    // the other card regardless of which way it goes that s what makes swiping
    // back to lyrics instead of dead ending once the queue card is in front
    var frontIndex by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    // draggable + clickable rather than a raw detectverticaldraggestures with
    // those two fight over the initial down event the child s tap detector
    // the parent s drag detector sees it which is why the deck could stick this
    // built to coexist clickable cancels itself once drag passes touch slop
    val dragState = rememberDraggableState { delta ->
        dragAccum += -delta // up = positive
        val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
        val target = if (frontIndex == 0) frac else 1f - frac
        scope.launch { flip.snapTo(target) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight + PeekOffset)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { dragAccum = 0f },
                onDragStopped = {
                    // only ~40% of a full flip is needed to commit so a short flick works
                    // anything less springs back to where it started
                    if (abs(dragAccum) >= flipDistancePx * 0.4f) {
                        frontIndex = 1 - frontIndex
                    }
                    scope.launch {
                        flip.animateTo(
                            targetValue = frontIndex.toFloat(),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                },
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                if (frontIndex == 1) onOpenQueue() else onOpenLyrics()
            }
    ) {
        // blend the user s flip position toward queue in front by however far the
        // has opened at lyricsprogress 1 this pins the queue card fully forward
        // where the deck was left at 0 it is exactly the drag value so returning to
        // restores whichever card the user had chosen
        val lyricsP = lyricsProgressProvider()
        val p = flip.value + (1f - flip.value) * lyricsP

        // draw order has to follow the flip not just scale alpha whichever card is
        // behind is emitted first so the other one paints over it with a fixed
        // lyrics card stayed on top no matter how far you dragged which is why
        // like it did nothing at all
        val queueLeading = p >= 0.5f

        @Composable
        fun queueCard() = DeckCard(glassState = glassState, frontness = p) {
            QueueCardContent(
                nextTitle = nextSong?.title,
                nextArtist = nextSong?.artist,
                artworkUri = nextSong?.artworkUri?.toString(),
            )
        }

        @Composable
        fun lyricsCard() = DeckCard(glassState = glassState, frontness = 1f - p) {
            LyricsCardContent(entry = currentEntry, positionMs = progress.position)
        }

        // on the lyrics page there is no second card to stack the lyrics themselves
        // screen so the deck is a single queue card dropping the lyrics card once
        // essentially open also stops its peek edge showing behind the queue card
        val lyricsCardGone = lyricsP > 0.98f

        if (queueLeading) {
            if (!lyricsCardGone) lyricsCard()
            queueCard()
        } else {
            queueCard()
            if (!lyricsCardGone) lyricsCard()
        }
    }
}

// one layer of the deck frontness is 1 when this card is fully in front and 0
@Composable
private fun BoxScope.DeckCard(
    glassState: GlassState,
    frontness: Float,
    content: @Composable () -> Unit,
) {
    val f = frontness.coerceIn(0f, 1f)
    val scale = lerp(BackCardScale, 1f, f)

    // how much of this card is actually exposed above the other one

    // with a live blur stacking the two cards was visually forgiving with flat
    // fills it is not the back card s 006 white under the front card s 011
    // 0163 so the whole front card read ~48% milkier than intended and the deck
    // two stacked slabs of tint instead of one card with an edge peeking behind

    // so the back card is cut it only paints the strip that is genuinely
    // front card and nothing underneath it same final look one layer of tint
    // no blur

    // geometry both cards are cardheight tall and bottom aligned in the same
    // about their bottom edge then raised by their peek offset so a card s top
    // scale cardheight + peek above the container s bottom comparing that
    // card gives the exposed strip directly negative means this card is the
    // nothing needs cutting
    val otherF = 1f - f
    val thisTopAboveBottom = CardHeight * scale + lerp(PeekOffset, 0.dp, f)
    val otherTopAboveBottom = CardHeight * lerp(BackCardScale, 1f, otherF) + lerp(PeekOffset, 0.dp, otherF)
    val exposedOnScreen = thisTopAboveBottom - otherTopAboveBottom
    // converted back into this card s own pre scale coordinates which is where
    val cutHeight = if (exposedOnScreen > 0.dp) exposedOnScreen / scale else null

    // the card s layout height stays exactly cardheight only the painted
    // past the screen edge making the whole box taller an earlier attempt
    // which sits in the box s top cardheight clean out of the visible strip
    // is bottom anchored inside a container only cardheight + peekoffset tall
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // back card rides above the front one so its top edge is the part that peeks
                translationY = -lerp(PeekOffset.toPx(), 0f, f)
                // scale about the bottom edge so the peek gap above stays put while the card
                // grows shrinks instead of the whole thing drifting vertically
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .padding(horizontal = lerp(PeekInset, 0.dp, f))
    ) {
        // deliberately not clipped by the parent this is taller than the card and
        // its top so the extra height and the border s closing bottom line with it
        // the bottom of the screen that s what makes the card read as continuous
        // screen edge rather than a slab with a visible cut across its bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardHeight + BottomBleed)
                .align(Alignment.TopCenter)
                .clip(CardShape)
                // the cut itself applied here rather than to the fill alone so the border s
                // side lines get trimmed with it otherwise the back card s outline kept
                // running down behind the front card and gave the cut away
                .drawWithContent {
                    val cutPx = cutHeight?.toPx()
                    if (cutPx == null || cutPx >= size.height) {
                        drawContent()
                    } else if (cutPx > 0f) {
                        clipRect(bottom = cutPx) { this@drawWithContent.drawContent() }
                    }
                }
        ) {
            // flat translucent fill not a live blur

            // this used to be glasspillbackground with blurradius 40 a rendereffect
            // re evaluated every frame twice once per card over the player s glassroot
            // capture that is a full offscreen render pass plus a 40px gaussian per card
            // frame running during the deck flip and during the whole sheet morph

            // it buys almost nothing here what sits behind this deck is the player
            // which is already a blurred image morphingcover bakes the blur into a
            // bitmap via backdropblurtransformation blurring an already blurred surface
            // close to a no op visually the depth cue people actually read comes from the
            // layering the peek edge the scale step and the body running off the
            // the screen none of which needs a blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.White.copy(alpha = lerp(0.06f, 0.11f, f)),
                        shape = CardShape,
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = lerp(0.10f, 0.20f, f)),
                        shape = CardShape,
                    )
            )
        }

        // content fades fully out on the back card not just down to 045

        // 045 was survivable while each card drew a 40px blur because the front
        // backing masked whatever was behind it with flat translucent fills there is
        // doing that masking at 011 alpha the front card is essentially clear so
        // card s text showed straight through it the lyrics line and the next
        // rendering on top of each other

        // the back card is completely covered by the front one anyway its 6dp peek
        // pure edge no content so a straight crossfade on frontness is correct the
        // alphas sum to 1 throughout the flip and reach 0 1 at the ends
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = f * f * (3f - 2f * f) }
        ) {
            content()
        }
    }
}

// just the line no leading icon no lyrics caption above it the queue card
@Composable
private fun LyricsCardContent(entry: LyricsEntry?, positionMs: Long) {
    val text = entry?.text?.takeIf { it.isNotBlank() }
    val words = entry?.words

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        val style = MaterialTheme.typography.bodyLarge
        val modifier = Modifier.weight(1f).fadingRightEdge()

        if (text != null && !words.isNullOrEmpty()) {
            // same word by word fill the full lyrics page uses sharing its helpers so
            // can t drift apart the card highlights in step with the page
            val filled = karaokeFilledChars(text, words, positionMs)
            Text(
                text = karaokeAnnotated(
                    text = text,
                    filledChars = filled,
                    highlightColor = Color.White,
                    baseColor = Color.White.copy(alpha = 0.45f),
                ),
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = modifier,
            )
        } else {
            Text(
                text = text ?: "No lyrics for this song",
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = if (text != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun QueueCardContent(
    nextTitle: String?,
    nextArtist: String?,
    artworkUri: String?,
) {
    Row(
        // bottom not centervertically the artwork 28dp is shorter than the
        // block so centering the two against each other let the artwork hang
        // where the title s own bottom sits reads as inconsistent padding even
        // itself is even bottom aligning both puts the artwork s bottom edge and the
        // bottom edge on the same line
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
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
            // lineheight pinned tight on both lines the default leading on
            // left a visible gap between the caption and the title inside a 50dp card
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

// right edge dissolve matching the treatment on the player s title artist and the
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
