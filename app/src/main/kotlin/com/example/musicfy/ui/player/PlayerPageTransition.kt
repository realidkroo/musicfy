// PlayerPageTransition.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The pages that open over the player. Only one is ever open. */
internal enum class PlayerPage { NONE, LYRICS, QUEUE }

/**
 * Opening from the player: a little give at the end, so the page lands rather than stops. A fling
 * carries its speed into it (see [PlayerPageTransition.release]).
 */
private val OpenSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 230f, visibilityThreshold = 0.0005f)

/** Closing back into the card: no bounce - it settles into its slot. */
private val CloseSpring = spring<Float>(dampingRatio = 1f, stiffness = 280f, visibilityThreshold = 0.0005f)

/** Switching straight from one page to the other, under a header that stays put. */
private val SwitchTween = tween<Float>(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

/** A fling faster than this (progress per second) decides the direction on its own. */
private const val FlingDecides = 1.4f

/** How far ahead a slow release is projected before picking a side. */
private const val ReleaseProjectionSec = 0.14f

/** Past this (projected) the page opens; short of it, it falls back into the card. */
private const val OpenThreshold = 0.38f

/**
 * The most fling speed handed to the spring (progress per second). Enough to carry straight on,
 * not so much that a hard flick throws the rows far past their places before they settle.
 */
private const val MaxCarriedVelocity = 4.5f

/**
 * Drives the lyrics and queue pages.
 *
 * One [header] value carries the cover and title up into the page header. While a page is growing
 * out of the bottom card - opened from the player, or closing back into it - that page is *linked*:
 * its own progress is the header's, so the card, the header and the page are one motion and can't
 * drift apart. Switching from one page to the other unlinks them and crossfades the two pages under
 * a header that doesn't move.
 *
 * All the values are Animatables read in layer/draw blocks, so a transition runs without
 * recomposing the player every frame.
 */
@Stable
internal class PlayerPageTransition(private val scope: CoroutineScope) {
    /** Where things are headed. */
    var page by mutableStateOf(PlayerPage.NONE)
        private set

    /** The page tied to [header] - see the class doc. */
    var linked by mutableStateOf(PlayerPage.NONE)
        private set

    /** True from the moment a swipe takes hold until the finger lifts. */
    var dragging by mutableStateOf(false)
        private set

    /**
     * The page [page] was switched to from, when it was opened over the other one rather than from
     * the player - where a swipe down ([back]) returns to. NONE when it grew out of the player.
     */
    var previous by mutableStateOf(PlayerPage.NONE)
        private set

    private val header = Animatable(0f).apply { updateBounds(lowerBound = 0f) }
    private val lyrics = Animatable(0f)
    private val queue = Animatable(0f)
    private var job: Job? = null
    private var dragValue = 0f

    /** Cover and title moving into the header. Can run a hair past 1 when the open spring lands. */
    val headerProgress: Float get() = header.value

    val lyricsProgress: Float get() = if (linked == PlayerPage.LYRICS) header.value else lyrics.value
    val queueProgress: Float get() = if (linked == PlayerPage.QUEUE) header.value else queue.value

    fun progressOf(target: PlayerPage): Float = when (target) {
        PlayerPage.LYRICS -> lyricsProgress
        PlayerPage.QUEUE -> queueProgress
        PlayerPage.NONE -> 0f
    }

    /** True while [target] is growing out of (or back into) the card rather than crossfading. */
    fun isCardMorph(target: PlayerPage): Boolean = linked == target && target != PlayerPage.NONE

    /**
     * How far [target] has grown out of the card - unclamped, so the open spring's landing reaches
     * the page's rows. 1 whenever the page isn't growing from the card (a crossfade, or open).
     */
    fun cardMorphProgress(target: PlayerPage): Float =
        if (isCardMorph(target)) header.value else 1f

    private fun animatableOf(target: PlayerPage) = if (target == PlayerPage.QUEUE) queue else lyrics
    private fun other(target: PlayerPage) = if (target == PlayerPage.QUEUE) PlayerPage.LYRICS else PlayerPage.QUEUE

    fun open(target: PlayerPage) {
        if (target == PlayerPage.NONE) {
            close()
            return
        }
        // Opened over the other page: remember it, so a swipe down can go back to it. Opened again
        // while already showing, nothing changes.
        if (page == PlayerPage.NONE) previous = PlayerPage.NONE
        else if (page != target) previous = page
        page = target
        job?.cancel()
        job = scope.launch {
            if (linked == target || header.value < 0.001f) {
                // From the player (or turning round mid-close): grow out of the card.
                if (linked != target) {
                    lyrics.snapTo(0f)
                    queue.snapTo(0f)
                    linked = target
                }
                header.animateTo(1f, OpenSpring)
            } else {
                // From the other page: the header stays, the pages trade places.
                if (linked != PlayerPage.NONE) {
                    animatableOf(linked).snapTo(header.value.coerceIn(0f, 1f))
                    linked = PlayerPage.NONE
                }
                coroutineScope {
                    launch { header.animateTo(1f, OpenSpring) }
                    launch { animatableOf(target).animateTo(1f, SwitchTween) }
                    launch { animatableOf(other(target)).animateTo(0f, SwitchTween) }
                }
                // Settled: tie the page to the header again, so closing it shrinks it into the card.
                animatableOf(target).snapTo(1f)
                linked = target
            }
        }
    }

    /**
     * One step back: to the page this one was opened over, or - when it grew out of the player -
     * closed into the card. Either way the next step back is to the player.
     */
    fun back() {
        val to = previous
        if (to == PlayerPage.NONE) {
            close()
        } else {
            open(to)
            previous = PlayerPage.NONE
        }
    }

    fun close() {
        page = PlayerPage.NONE
        previous = PlayerPage.NONE
        job?.cancel()
        job = scope.launch {
            if (linked == PlayerPage.NONE) {
                // Closed in the middle of a switch: the page more in view goes back into the card.
                linked = if (lyrics.value >= queue.value) PlayerPage.LYRICS else PlayerPage.QUEUE
            }
            val fading = animatableOf(other(linked))
            coroutineScope {
                launch { header.animateTo(0f, CloseSpring) }
                launch { fading.animateTo(0f, SwitchTween) }
            }
        }
    }

    /** Closes with no animation - the player itself is going away. */
    fun reset() {
        page = PlayerPage.NONE
        previous = PlayerPage.NONE
        dragging = false
        job?.cancel()
        job = scope.launch {
            header.snapTo(0f)
            lyrics.snapTo(0f)
            queue.snapTo(0f)
            linked = PlayerPage.NONE
        }
    }

    /**
     * Starts a swipe that pulls [target] up out of the card. Only from the plain player - with a
     * page open, the page's own list owns vertical drags.
     */
    fun beginDrag(target: PlayerPage): Boolean {
        if (target == PlayerPage.NONE || page != PlayerPage.NONE || header.value > 0.02f) return false
        job?.cancel()
        dragValue = header.value
        dragging = true
        linked = target
        job = scope.launch {
            lyrics.snapTo(0f)
            queue.snapTo(0f)
        }
        return true
    }

    /** Follows the finger. [delta] is in progress units: + opens. */
    fun dragBy(delta: Float) {
        if (!dragging) return
        dragValue = (dragValue + delta).coerceIn(0f, 1f)
        val value = dragValue
        // A snap still queued when the finger lifts would cancel the release spring and leave the
        // page stuck where the finger was - so it only lands while the drag is still on.
        scope.launch { if (dragging) header.snapTo(value) }
    }

    /**
     * Lets go: opens or falls back depending on where it is and how fast it was moving, and hands
     * that speed to the spring so the motion carries straight on instead of restarting from rest.
     *
     * @param velocity progress per second, + opening.
     */
    fun release(velocity: Float) {
        if (!dragging) return
        dragging = false
        val projected = dragValue + velocity * ReleaseProjectionSec
        val open = if (kotlin.math.abs(velocity) > FlingDecides) velocity > 0f else projected > OpenThreshold
        val target = linked
        page = if (open) target else PlayerPage.NONE
        job?.cancel()
        val from = dragValue
        job = scope.launch {
            header.snapTo(from)
            header.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = if (open) OpenSpring else CloseSpring,
                initialVelocity = velocity.coerceIn(-MaxCarriedVelocity, MaxCarriedVelocity),
            )
        }
    }
}

@Composable
internal fun rememberPlayerPageTransition(): PlayerPageTransition {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerPageTransition(scope) }
}

/** The bottom card deck: which card is in front, and the flip between them. */
@Stable
internal class PlayerDeckState {
    val flip = Animatable(0f)
    var frontIndex by mutableIntStateOf(0)

    /** The page the card in front opens - what a swipe up on the player brings up. */
    val focusedPage: PlayerPage get() = if (frontIndex == 1) PlayerPage.QUEUE else PlayerPage.LYRICS
}

/**
 * Where the card's content and its page counterpart sit on screen, so one can grow into the other.
 *
 * Anchors are root coordinates. A lyric anchor is where its text starts (its origin, so a line the
 * card has slid sideways still lines up) at the middle of its first line; a queue anchor is the top
 * left of the song's artwork.
 */
@Stable
class CardMorphState {
    /** The front card's slot, down to the screen's bottom edge. */
    var cardRect by mutableStateOf<Rect?>(null)

    var cardLyricAnchor by mutableStateOf(Offset.Unspecified)
    var cardArtAnchor by mutableStateOf(Offset.Unspecified)

    var pageLyricAnchor by mutableStateOf(Offset.Unspecified)
    var pageArtAnchor by mutableStateOf(Offset.Unspecified)

    /** How far the card's lyric is slid left right now (px), so its origin can be found. */
    var cardLyricScroll = 0f
}

/** Card lyric size over page lyric size (the active line is drawn 6% up). */
internal const val CardToPageLyricScale = 17f / (32f * 1.06f)

/** Card artwork over queue row artwork. */
internal const val CardToPageArtScale = 28f / 44f

/**
 * A rounded rect clip at an arbitrary place inside the layer. A data class on purpose: the layer
 * only rebuilds its outline when the shape stops being equal to the last one, so a shape that
 * mutated its own fields would be stuck on its first outline.
 */
internal data class InsetRoundRectShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(left, top, right, bottom, CornerRadius(radius, radius)))
}

internal fun mixF(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

internal fun smoothstepF(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The card growing into the full screen: its rect at progress [e] (clamped, the bounce is elsewhere). */
internal fun expandingCardRect(card: Rect, screenWidth: Float, screenHeight: Float, e: Float): Rect {
    val t = e.coerceIn(0f, 1f)
    return Rect(
        left = mixF(card.left, 0f, t),
        top = mixF(card.top, 0f, t),
        right = mixF(card.right, screenWidth, t),
        bottom = mixF(card.bottom, screenHeight, t),
    )
}

/** Side margin of the card on the player - where a page grows from when the card isn't shown. */
private val CardSideMargin = 26.dp

/** The front card's slot, or where it would be when the card is turned off. */
internal fun CardMorphState.cardRectOr(width: Float, height: Float, density: Density): Rect =
    cardRect ?: with(density) {
        Rect(CardSideMargin.toPx(), height - PlayerCardHeight.toPx(), width - CardSideMargin.toPx(), height + 100.dp.toPx())
    }

/** How dark the player behind gets at the middle of the motion. Back to clear at either end. */
private const val BehindDimPeak = 0.22f

/**
 * The card's glass growing into the full screen, under the page that grows with it, plus a light
 * dim over the player behind. It starts as exactly the card in front (same fill, border and
 * corners) so the hand-over from the deck is invisible, and its glass thins out as it fills the
 * screen, leaving the page on the player's own background.
 */
@Composable
internal fun ExpandingCardSurface(
    transition: PlayerPageTransition,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
) {
    val active by remember {
        derivedStateOf {
            transition.linked != PlayerPage.NONE &&
                transition.headerProgress > 0.0005f && transition.headerProgress < 0.9995f
        }
    }
    if (!active) return
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val e = transition.headerProgress
                val t = e.coerceIn(0f, 1f)
                val dim = BehindDimPeak * kotlin.math.sin(Math.PI.toFloat() * t)
                if (dim > 0.002f) drawRect(Color.Black.copy(alpha = dim))

                val card = morph.cardRectOr(size.width, size.height, this)
                val r = expandingCardRect(card, size.width, size.height, e)
                val radius = mixF(PlayerCardCorner.toPx(), 0f, t)
                val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                val fill = FrontCardFillAlpha * (1f - smoothstepF((t - 0.3f) / 0.65f))
                if (fill > 0.002f) {
                    drawRoundRect(Color.White.copy(alpha = fill), r.topLeft, r.size, corner)
                }
                val border = FrontCardBorderAlpha * (1f - smoothstepF(t / 0.55f))
                if (border > 0.002f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = border),
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = corner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
    )
}

/**
 * A page's own layer. Growing out of the card, it's clipped to the growing card - the page is
 * revealed inside it, and its rows slide up into place within it. Switching between pages, it
 * crossfades with a slight zoom as before.
 */
internal fun Modifier.pageTransitionLayer(
    page: PlayerPage,
    transition: PlayerPageTransition,
    morph: CardMorphState,
    crossfadeProgress: () -> Float,
): Modifier = graphicsLayer {
    if (transition.isCardMorph(page)) {
        val e = transition.headerProgress
        if (e < 0.9995f) {
            val r = expandingCardRect(morph.cardRectOr(size.width, size.height, this), size.width, size.height, e)
            shape = InsetRoundRectShape(
                r.left,
                r.top,
                r.right,
                r.bottom,
                mixF(PlayerCardCorner.toPx(), 0f, e.coerceIn(0f, 1f)),
            )
            clip = true
        } else {
            shape = RectangleShape
            clip = false
        }
    } else {
        val p = crossfadeProgress()
        alpha = (p / 0.45f).coerceIn(0f, 1f)
        val s = 0.88f + 0.12f * p
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0.86f)
        translationY = (1f - p) * size.height * 0.10f
    }
}

/**
 * Rows of a page arriving with it as it grows out of the card: they rise into place from lower
 * down - further down the list, further to travel, so they land as a cascade - and fade in.
 * Unclamped [e], so a fast open overshoots a hair and settles.
 */
internal fun androidx.compose.ui.graphics.GraphicsLayerScope.applyPageRowEnter(
    e: Float,
    rowsBelow: Int,
) {
    if (e >= 0.9995f && e <= 1.0005f) return
    val slide = (PageRowSlideBase + PageRowSlideStep * rowsBelow.coerceIn(0, 12)).toPx()
    translationY += slide * (1f - e)
    alpha *= smoothstepF((e - 0.14f) / 0.55f)
}

private val PageRowSlideBase = 120.dp
private val PageRowSlideStep = 26.dp

/**
 * The page's copy of the card's content - the lyric line, or the next song - growing out of the
 * card: it starts on top of the card's copy at the card's size and travels to its own place,
 * fading in as the card's copy fades out, so the two read as one thing changing size.
 *
 * @param localAnchor the anchor inside this element, matching [pageAnchor] on screen.
 * @param cardToPage the card's size over the page's.
 * @return false when there is nowhere to come from - the caller treats it as a plain row.
 */
internal fun androidx.compose.ui.graphics.GraphicsLayerScope.applyCardFlight(
    e: Float,
    cardAnchor: Offset,
    pageAnchor: Offset,
    localAnchor: Offset,
    cardToPage: Float,
): Boolean {
    if (e >= 0.9995f && e <= 1.0005f) return true
    if (cardAnchor == Offset.Unspecified || pageAnchor == Offset.Unspecified) return false
    if (size.width <= 0f || size.height <= 0f) return false
    transformOrigin = TransformOrigin(localAnchor.x / size.width, localAnchor.y / size.height)
    val s = mixF(cardToPage, 1f, e)
    scaleX *= s
    scaleY *= s
    translationX += (cardAnchor.x - pageAnchor.x) * (1f - e)
    translationY += (cardAnchor.y - pageAnchor.y) * (1f - e)
    alpha *= smoothstepF((e - 0.06f) / 0.36f)
    return true
}
