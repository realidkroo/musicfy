// PlayerCustomizeScreen.kt
// The "Currently editing" page (concept screens 82–92).
//
// This page is OPAQUE and self-contained. An earlier version was a translucent sheet laid over
// the live player, using a punched hole to reveal the real backdrop shrinking into the "Bg Style"
// slot — that leaked the whole app through the page, and at mid-morph you saw both the shrinking
// backdrop and a ghost of the un-shrunk one, which read as a duplicate. Everything here is now
// drawn by this file over a solid surface, and there is exactly one preview element: a single
// stage that morphs between "fills the player's artwork region" and "the small rounded box".
//
// Two axes of movement, both on the same curve:
//  - vertical: a section value in 0..1, cover section → background section. Not a scroll. It is
//    a drag that always settles on 0 or 1, which is what makes it impossible to overscroll and
//    what gives the automatic glide once you push past the options.
//  - horizontal: one drag surface for the whole page, dispatching to whichever section's
//    carousel is active, whose offset the stage's contents travel with.

package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerBackgroundStyleKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.PlayerCoverStyleKey
import com.example.musicfy.constants.ShowBigDiscStylesKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.player.SeamBlur
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

/** The page's motion curve, and the duration everything settles over. */
internal val SwipeEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
internal const val SwipeDurationMillis = 900

/** Fraction of a page a horizontal drag must cover to commit rather than spring back. */
private const val SnapCommitThreshold = 0.18f

/** Fling speed (px/s) past which a horizontal drag commits regardless of how far it travelled. */
private const val CarouselFlingVelocity = 400f

/**
 * A one-axis carousel, replacing the two HorizontalPagers this page used to stack on top of each
 * other as invisible gesture surfaces.
 *
 * Stacking them was the bug behind "they're on the same hitbox": both were full-screen siblings,
 * so the topmost one was hit-tested first and swallowed drags meant for the other, whether or not
 * its own scrolling was disabled. There is now exactly one drag surface for the whole page, and
 * it dispatches to whichever carousel the current section owns.
 *
 * [position] is continuous, in page units. Everything else is derived from it, so the visual
 * offset and the committed page can never disagree.
 */
@Stable
private class CarouselState(val count: Int, initial: Int) {
    val position = mutableFloatStateOf(initial.toFloat())

    /** The settled page. derivedStateOf, so readers wake on a page change, not every frame. */
    val page by derivedStateOf { position.floatValue.roundToInt().coerceIn(0, count - 1) }

    /** Distance from the settled page, in -0.5..0.5 — the pager's currentPageOffsetFraction. */
    fun offset(): Float = position.floatValue - position.floatValue.roundToInt()

    fun drag(deltaPages: Float, fromPage: Int) {
        // One page per gesture, so a fast swipe can't skate across several styles at once.
        position.floatValue = (position.floatValue + deltaPages)
            .coerceIn(
                (fromPage - 1).coerceAtLeast(0).toFloat(),
                (fromPage + 1).coerceAtMost(count - 1).toFloat(),
            )
    }

    fun targetAfterDrag(fromPage: Int, velocity: Float): Int {
        val travelled = position.floatValue - fromPage
        return when {
            velocity < -CarouselFlingVelocity -> fromPage + 1
            velocity > CarouselFlingVelocity -> fromPage - 1
            travelled > SnapCommitThreshold -> fromPage + 1
            travelled < -SnapCommitThreshold -> fromPage - 1
            else -> fromPage
        }.coerceIn(0, count - 1)
    }
}

/** Fling speed (px/s) past which a vertical drag commits regardless of how far it travelled. */
private const val SectionFlingVelocity = 700f

/**
 * The page's own surface — a solid dark grey.
 *
 * Not transparent. Letting the live player show through is what put the room behind "Bg Style":
 * on the background section the stage has shrunk to a small box, so everything around it is this
 * surface, and if it isn't painted you are looking straight at the player's own full-bleed cover.
 * The stage covers the whole screen at section 0, so this is only ever visible once the page has
 * genuinely moved on from the cover — see the file header.
 */
private val PageSurface = Color(0xFF121214)
private val StageCorner = 30.dp

/** Height of the blurred band behind the header, below the status bar inset. */
private val HeaderBlurHeight = 92.dp

/** Caption under each background page, matching the concept screens. */
private val PlayerBackgroundStyle.displayName: String
    get() = when (this) {
        PlayerBackgroundStyle.COVER_GRADIENT -> "simple gradient based on cover"
        PlayerBackgroundStyle.SOLID -> "simple gray color"
        PlayerBackgroundStyle.DARK_GRADIENT -> "simple dark static gradient"
        PlayerBackgroundStyle.APPLE_MUSIC -> "Apple music style cover morph"
    }

/**
 * Travel and fade for content riding a pager's drag.
 *
 * Reaching zero alpha at exactly half a page is what hides the content swap: the pager flips
 * `currentPage` at that same instant, so the outgoing style has already faded out and the
 * incoming one fades back in from the opposite side.
 */
internal fun GraphicsLayerScope.applySwipe(offset: Float, distance: Float) {
    translationX = -offset * distance
    alpha *= (1f - (abs(offset) * 2f)).coerceIn(0f, 1f)
}

@Composable
fun PlayerCustomizeScreen(
    /** Steps back one level — to the part-selection layer, or out of the settings route. */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Shown when this page was opened from the player menu rather than by long-pressing the
     * artwork — the gesture is the faster route and there is nothing on screen that hints at it.
     */
    showLongPressHint: Boolean = false,
) {
    BackHandler(onBack = onBack)

    val playerConnection = LocalPlayerConnection.current
    val trackInfo by playerConnection?.uiState?.trackInfo?.collectAsState()
        ?: remember { mutableStateOf(TrackInfo()) }
    val isPlaying = playerConnection?.uiState?.transportState?.collectAsState()?.value?.isPlaying == true
    val queueIndex = playerConnection?.uiState?.queueState?.collectAsState()?.value?.currentIndex ?: 0

    var coverStyle by rememberEnumPreference(PlayerCoverStyleKey, PlayerCoverStyle.EDGE_TO_EDGE)
    var backgroundStyle by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        PlayerBackgroundStyle.COVER_GRADIENT,
    )

    // The oversized disc variants are gated behind an Experimental switch. Filtered rather than
    // removed, and the currently-selected style is always kept in the list — hiding the style
    // someone is already using would strand the carousel on a page that doesn't exist.
    val (showBigDiscStyles) = rememberPreference(ShowBigDiscStylesKey, defaultValue = false)
    val coverStyles = remember(showBigDiscStyles, coverStyle) {
        PlayerCoverStyle.entries.filter { style ->
            showBigDiscStyles || !style.isBigDisc || style == coverStyle
        }
    }
    val backgroundStyles = remember { PlayerBackgroundStyle.entries.toList() }

    // Keyed on the list itself: flipping the Experimental switch changes how many styles exist,
    // and a carousel holding a stale count would let you drag past the end.
    val coverCarousel = remember(coverStyles.size) {
        CarouselState(coverStyles.size, coverStyles.indexOf(coverStyle).coerceAtLeast(0))
    }
    val backgroundCarousel = remember {
        CarouselState(backgroundStyles.size, backgroundStyles.indexOf(backgroundStyle).coerceAtLeast(0))
    }

    // What the stage draws, taken straight from the carousel rather than from the preference.
    //
    // Reading it back out of DataStore put an async round-trip in the middle of the swap: the
    // page flipped, the cover faded to nothing, and the new style only arrived a frame or three
    // later once the write had landed and the snapshot updated — which is the flicker. The
    // preference is still written, just no longer on the visual path.
    val shownCoverStyle = coverStyles.getOrNull(coverCarousel.page) ?: coverStyle
    val shownBackgroundStyle = backgroundStyles.getOrNull(backgroundCarousel.page) ?: backgroundStyle

    LaunchedEffect(shownCoverStyle) {
        if (coverStyle != shownCoverStyle) coverStyle = shownCoverStyle
    }
    LaunchedEffect(shownBackgroundStyle) {
        if (backgroundStyle != shownBackgroundStyle) backgroundStyle = shownBackgroundStyle
    }

    // 0 = cover section, 1 = background section. A plain float rather than a ScrollState: it can
    // only ever hold a value in 0..1, so there is nothing to overscroll, and releasing always
    // animates to one end on the page's own curve instead of coasting to a stop wherever the
    // finger left off.
    //
    // Written directly from the drag callback — no coroutine per delta. An Animatable would have
    // meant `scope.launch { snapTo(...) }` on every single frame of the gesture, which is a
    // dispatch hop between the finger moving and the frame that shows it; that lag is what a
    // drag reads as when it feels heavy. The settle below is the only animated part.
    // Reveal, not a cut. PlayerEditOverlay dissolves its outlines away before handing over, and
    // this fades and settles up into place over the same beat, so the two read as one move.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(SwipeDurationMillis / 2, easing = SwipeEasing))
    }

    val stageGlass = remember { GlassState() }
    // True from the moment a drag starts until its settle animation has finished.
    //
    // The stage is GPU-bound, not CPU-bound: measured mid-swipe at 16ms of GPU work per frame
    // with the render thread parked 12ms in swapBuffers, while measure/layout sat at 0.03ms. The
    // three things making that up — the warp shader's per-frame uniform, the glassRoot capture's
    // duplicate draw of the whole stage, and SeamBlur's 130px blur over it — are all effects
    // nobody can resolve while the artwork is flying sideways under their thumb, so none of them
    // run during the gesture. They come back the instant it settles.
    //
    // A plain Boolean: it flips twice per gesture, so the recomposition cost is nil, and the
    // continuous values it gates stay draw-phase reads exactly as before.
    var interacting by remember { mutableStateOf(false) }
    val sectionValue = remember { mutableFloatStateOf(0f) }
    val sectionProvider = remember { { sectionValue.floatValue } }
    val onBackgroundSection by remember { derivedStateOf { sectionValue.floatValue > 0.5f } }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Opaque, and declared BEFORE the reveal layer on purpose. With it inside, the
            // entrance faded the surface itself along with everything on it, so for the length of
            // that animation the page was translucent and you could see straight through to
            // whatever was behind the player. The surface is now always solid; only its contents
            // fade in.
            .background(PageSurface)
            .graphicsLayer {
                val r = reveal.value
                alpha = r
                val s = 0.98f + 0.02f * r
                scaleX = s
                scaleY = s
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val sectionTravelPx = screenHeightPx * 0.45f

        val dragState = rememberDraggableState { delta ->
            sectionValue.floatValue =
                (sectionValue.floatValue - delta / sectionTravelPx).coerceIn(0f, 1f)
        }

        // How far a finger travels for one style. A third of the screen, with an 18% commit
        // threshold, puts a switch inside roughly a 25dp flick.
        val pageWidthPx = with(density) { (screenWidth * 0.33f).toPx() }
        var dragFromPage by remember { mutableIntStateOf(0) }
        val activeCarousel = if (onBackgroundSection) backgroundCarousel else coverCarousel
        val carouselDragState = rememberDraggableState { delta ->
            activeCarousel.drag(-delta / pageWidthPx, dragFromPage)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // ONE horizontal surface for the whole page, on the same node as the vertical
                // one. draggable is orientation-locked, so a horizontal gesture goes here and a
                // vertical one goes below, with no sibling hit-testing to get in the way.
                .draggable(
                    state = carouselDragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragFromPage = activeCarousel.page
                        interacting = true
                    },
                    onDragStopped = { velocity ->
                        val target = activeCarousel.targetAfterDrag(dragFromPage, velocity)
                        animate(
                            initialValue = activeCarousel.position.floatValue,
                            targetValue = target.toFloat(),
                            animationSpec = tween(SwipeDurationMillis, easing = SwipeEasing),
                        ) { value, _ -> activeCarousel.position.floatValue = value }
                        interacting = false
                    },
                )
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { interacting = true },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -SectionFlingVelocity -> 1f
                            velocity > SectionFlingVelocity -> 0f
                            sectionValue.floatValue > 0.5f -> 1f
                            else -> 0f
                        }
                        // Runs on the frame clock, so it lands a new value every vsync — 120 of
                        // them a second on a 120Hz panel — rather than stepping.
                        animate(
                            initialValue = sectionValue.floatValue,
                            targetValue = target,
                            animationSpec = tween(SwipeDurationMillis, easing = SwipeEasing),
                        ) { value, _ -> sectionValue.floatValue = value }
                        interacting = false
                    },
                )
        ) {
            // glassRoot goes on this fixed full-screen wrapper, NOT on the stage inside it. The
            // stage is re-measured every frame of the section drag, and a capture whose
            // RenderNode is torn down and re-created at a new size on every one of those frames
            // is what was flickering. This wrapper never changes size, so the node is allocated
            // once and only its contents are re-recorded.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // isActive gates the capture's duplicate draw pass. Only the seam band
                    // and the header blur read it, and both have faded out by 0.5, so past
                    // there the recording is pure waste. Threshold sits well clear of that so a
                    // consumer never reads an empty node.
                    .glassRoot(stageGlass, isActive = { !interacting && sectionProvider() < 0.55f })
            ) {
                PreviewStage(
                    sectionProvider = sectionProvider,
                    coverSwipeProvider = { coverCarousel.offset() },
                    backgroundSwipeProvider = { backgroundCarousel.offset() },
                    coverStyle = shownCoverStyle,
                    backgroundStyle = shownBackgroundStyle,
                    backgroundStyles = backgroundStyles,
                    backgroundPage = backgroundCarousel.page,
                    showNeighbours = onBackgroundSection,
                    animateBackdrop = !interacting,
                    trackInfo = trackInfo,
                    isPlaying = isPlaying,
                    queueIndex = queueIndex,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    statusBarTop = statusBarTop,
                )
            }

            // The soft band bridging the artwork's bottom edge into everything below it — the
            // same SeamBlur the player draws, reading a capture of the stage instead of a capture
            // of MorphingCover. Declared as a SIBLING of the stage, never inside it: it blurs
            // whatever the glassRoot recorded, so nesting it would have it capture itself.
            SeamBlur(
                glassState = stageGlass,
                progressProvider = { 1f },
                trackInfo = trackInfo,
                maxHeight = screenHeight,
                // There is no seam to soften once the stage has shrunk into the background slot.
                fadeProvider = {
                    if (interacting) 0f else (1f - sectionProvider() * 2f).coerceIn(0f, 1f)
                },
            )

            // Legibility wash over the artwork, so the captions and the option card stay
            // readable against a bright cover — the same treatment the page had before. Gone by
            // the time the background section arrives, which is dark enough on its own.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - sectionProvider() * 2f).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.32f))
            )

            // The gesture surface for the horizontal carousel. Deliberately the FULL screen and
            // not the stage's own bounds: on the background section the stage is a small inset
            // box, and confining the swipe to it left almost nothing to grab. Transparent — the
            // stage below is what you actually see. Only one is mounted at a time so their hit
            // regions can never overlap.
            // ---- Cover section content ----
            SectionContent(
                topOffset = screenHeight * CoverRegionFraction + 14.dp,
                enterFromBelow = false,
                sectionProvider = sectionProvider,
                screenHeightPx = screenHeightPx,
            ) {
                PageDots(count = coverStyles.size, selected = coverCarousel.page)
                Spacer(modifier = Modifier.height(18.dp))
                SectionCaption(title = "Style", subtitle = shownCoverStyle.displayName)
                Spacer(modifier = Modifier.height(18.dp))
                OptionCard(
                    options = shownCoverStyle.options,
                    swipeProvider = { coverCarousel.offset() },
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Scroll down to edit background",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            // ---- Background section content ----
            SectionContent(
                topOffset = screenHeight *
                    (BackgroundStageTopFraction + BackgroundStageHeightFraction) + 26.dp,
                enterFromBelow = true,
                sectionProvider = sectionProvider,
                screenHeightPx = screenHeightPx,
            ) {
                SectionCaption(
                    title = "Bg Style",
                    subtitle = shownBackgroundStyle.displayName,
                    centered = true,
                )
                Spacer(modifier = Modifier.height(20.dp))
                PageDots(count = backgroundStyles.size, selected = backgroundCarousel.page)
            }
        }

        // ---- Header chrome, above everything ----
        //
        // No blur or scrim behind the header: over a bright cover the band read as a dark
        // slab across the top, and it was a second consumer of the stage capture, so dropping it
        // removes a full-screen blur pass per frame as well.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 16.dp)
                .fillMaxWidth()
                .padding(horizontal = 76.dp),
        ) {
            Text(
                text = "Currently editing",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                // Tightened so the subtitle sits right under the title rather than floating
                // a default line-height away from it.
                lineHeight = 18.sp,
            )
            Text(
                text = if (onBackgroundSection) "Background" else "cover",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 12.sp,
            )
        }

        EditOverlayBackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

        if (showLongPressHint) {
            LongPressHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }

    // Keeps the carousels honest if a preference is changed from somewhere else while this
    // page is open.
    LaunchedEffect(coverStyle) {
        val index = coverStyles.indexOf(coverStyle)
        if (index >= 0 && index != coverCarousel.page) {
            coverCarousel.position.floatValue = index.toFloat()
        }
    }
    LaunchedEffect(backgroundStyle) {
        val index = backgroundStyles.indexOf(backgroundStyle)
        if (index >= 0 && index != backgroundCarousel.page) {
            backgroundCarousel.position.floatValue = index.toFloat()
        }
    }
}

// Resting geometry of the background section's stage, as fractions of the screen — read off
// concept screen 89.
private const val BackgroundStageTopFraction = 0.13f
private const val BackgroundStageHeightFraction = 0.63f
private const val BackgroundStageWidthFraction = 0.78f

/** Gap between one background page's box and the next. */
private val StagePageGap = 18.dp

/**
 * Lays a node out as the morphing stage: the player's whole artwork region at section 0, the
 * inset rounded box at section 1.
 *
 * A real re-measure rather than a scale, so the contents re-flow into the smaller box instead of
 * being squashed — the two rects have quite different aspect ratios. [sectionProvider] is read in
 * the layout phase, so dragging costs relayouts of this one node, not recompositions.
 */
private fun Modifier.stageLayout(
    sectionProvider: () -> Float,
    screenWidth: Dp,
    screenHeight: Dp,
    density: androidx.compose.ui.unit.Density,
    /**
     * This box's position in the background carousel, in pages, relative to the settled one.
     * Scaled by the section value so it has no effect at all on the cover section, where the
     * stage is the full screen and there is no carousel to be part of.
     */
    pageOffsetProvider: () -> Float = { 0f },
): Modifier = this.layout { measurable, constraints ->
    val s = sectionProvider()
    val fullW = with(density) { screenWidth.toPx() }
    val fullH = with(density) { screenHeight.toPx() }

    // Starts as the WHOLE screen, not just the artwork region: at section 0 the stage has to be
    // indistinguishable from the player's own full-bleed backdrop, which is what keeps the cover
    // section looking exactly as it did. It is also why there is no duplicate — the page's grey
    // is a flat surface *underneath*, revealed only as this shrinks, never a second copy of the
    // artwork alongside it.
    val startW = fullW
    val startH = fullH
    val endW = fullW * BackgroundStageWidthFraction
    val endH = fullH * BackgroundStageHeightFraction

    val w = lerp(startW, endW, s)
    val h = lerp(startH, endH, s)
    val gap = with(density) { StagePageGap.toPx() }
    val x = lerp(0f, (fullW - endW) / 2f, s) + pageOffsetProvider() * (endW + gap) * s
    val y = lerp(0f, fullH * BackgroundStageTopFraction, s)

    val placeable = measurable.measure(
        Constraints.fixed(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.toInt(), y.toInt())
    }
}

/**
 * The one and only preview: background plus artwork, morphing as a single element.
 *
 * The artwork is placed by the same [coverArtBox] the real player uses, converted to fractions of
 * the artwork region so it lands correctly at any stage size.
 */
@Composable
private fun PreviewStage(
    sectionProvider: () -> Float,
    coverSwipeProvider: () -> Float,
    backgroundSwipeProvider: () -> Float,
    coverStyle: PlayerCoverStyle,
    backgroundStyle: PlayerBackgroundStyle,
    backgroundStyles: List<PlayerBackgroundStyle>,
    backgroundPage: Int,
    showNeighbours: Boolean,
    /** False while a gesture is in flight — see `interacting` in PlayerCustomizeScreen. */
    animateBackdrop: Boolean,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    queueIndex: Int,
    screenWidth: Dp,
    screenHeight: Dp,
    statusBarTop: Dp,
) {
    val density = LocalDensity.current
    val artBox = remember(coverStyle, screenWidth, screenHeight, statusBarTop) {
        coverArtBox(coverStyle, screenWidth, screenHeight, statusBarTop)
    }
    // Fractions of the WHOLE player, matching the stage's own start rect — so at section 0 the
    // artwork lands in precisely the place MorphingCover would have put it.
    val fx = artBox.x / screenWidth
    val fy = artBox.y / screenHeight
    val fw = artBox.width / screenWidth
    val fh = artBox.height / screenHeight

    // Neighbouring background pages, parked one stage-width to either side. Mounted only once the
    // background section is in play, and their alpha still ramps from zero at the halfway point,
    // so they arrive with the section rather than popping in.
    if (showNeighbours) {
        // Two extra backdrops; only the immediate neighbours, never the whole list.
        listOf(-1, 1).forEach { delta ->
            val neighbour = backgroundStyles.getOrNull(backgroundPage + delta) ?: return@forEach
            Box(
                modifier = Modifier
                    .stageLayout(
                        sectionProvider = sectionProvider,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        density = density,
                        pageOffsetProvider = { delta - backgroundSwipeProvider() },
                    )
                    .graphicsLayer {
                        val s = sectionProvider()
                        alpha = ((s - 0.5f) * 2f).coerceIn(0f, 1f) * 0.55f
                        shape = RoundedCornerShape(StageCorner.toPx() * s)
                        clip = true
                    }
            ) {
                PlayerBackgroundPreview(
                    style = neighbour,
                    thumbnailUrl = trackInfo.thumbnailUrl,
                    width = screenWidth,
                    height = screenHeight,
                    // Peeking previews: no warp clock, and no shader at all. Each instance was
                    // otherwise compiling its own AGSL program for a box you can barely see.
                    animate = false,
                    warp = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .stageLayout(
                sectionProvider = sectionProvider,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
                // The stage itself is the carousel's current page, so it travels with the drag
                // instead of the background inside it sliding within a stationary box.
                pageOffsetProvider = { -backgroundSwipeProvider() },
            )
            .graphicsLayer {
                shape = RoundedCornerShape(StageCorner.toPx() * sectionProvider())
                clip = true
            }
    ) {
        PlayerBackgroundPreview(
            style = backgroundStyle,
            thumbnailUrl = trackInfo.thumbnailUrl,
            // Allocated at full-screen size, never at the stage's current (animating) size — the
            // layer is fixed by design and the stage's own clip is what reveals more or less of
            // it. Re-measuring it every frame would reallocate its GPU buffer every frame.
            width = screenWidth,
            height = screenHeight,
            animate = animateBackdrop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = (constraints.maxWidth * fw).toInt().coerceAtLeast(1)
                    val h = (constraints.maxHeight * fh).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (constraints.maxWidth * fx).toInt(),
                            (constraints.maxHeight * fy).toInt(),
                        )
                    }
                }
                .graphicsLayer {
                    // Holds full strength through the first half of the morph and only then
                    // dissolves, so the artwork travels *with* the shrinking stage instead of
                    // vanishing the moment the drag starts.
                    alpha = (1f - ((sectionProvider() - 0.5f) / 0.5f)).coerceIn(0f, 1f)
                    applySwipe(coverSwipeProvider(), size.width * 1.15f)
                }
        ) {
            CoverPreviewContent(
                style = coverStyle,
                trackInfo = trackInfo,
                isPlaying = isPlaying,
                queueIndex = queueIndex,
            )
        }
    }
}

@Composable
private fun CoverPreviewContent(
    style: PlayerCoverStyle,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    queueIndex: Int,
) {
    if (style.isDisc) {
        DiscCoverStack(
            style = style,
            artworkUrl = trackInfo.thumbnailUrl,
            mediaId = trackInfo.mediaId,
            queueIndex = queueIndex,
            isPlaying = isPlaying,
            spinActive = true,
            editMode = true,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val url = trackInfo.thumbnailUrl ?: return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url.resize(1200, 1200))
            .crossfade(300)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (style == PlayerCoverStyle.SQUARED) {
                    Modifier.clip(RoundedCornerShape(22.dp))
                } else Modifier
            ),
    )
}

/**
 * A section's text block, parked at [topOffset] and travelling out of the way as the other
 * section takes over.
 */
@Composable
private fun SectionContent(
    topOffset: Dp,
    enterFromBelow: Boolean,
    sectionProvider: () -> Float,
    screenHeightPx: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topOffset)
            .graphicsLayer {
                val s = sectionProvider()
                if (enterFromBelow) {
                    translationY = (1f - s) * screenHeightPx * 0.30f
                    alpha = ((s - 0.5f) * 2.5f).coerceIn(0f, 1f)
                } else {
                    translationY = -s * screenHeightPx * 0.30f
                    alpha = (1f - s * 2.5f).coerceIn(0f, 1f)
                }
            },
        content = content,
    )
}

/**
 * [centered] follows the concept screens: the cover section's "Style" is left-aligned against its
 * option card (82–88), the background section's "Bg Style" is centred under its box (89–92).
 */
@Composable
private fun SectionCaption(title: String, subtitle: String, centered: Boolean = false) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = subtitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        repeat(count) { index ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isSelected) 0.95f else 0.40f))
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * The option rows for the selected style.
 *
 * The card does not slide between styles — it re-shapes. animateContentSize on the page's own
 * curve grows or shrinks the card as the row count changes, so going from four options to two
 * reads as the card closing up rather than one card leaving and another arriving.
 */
@Composable
private fun OptionCard(options: List<CoverOption>, swipeProvider: () -> Float) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .animateContentSize(
                animationSpec = tween(durationMillis = SwipeDurationMillis, easing = SwipeEasing)
            )
            // Only the rows' opacity tracks the drag; the card's own body stays put and morphs.
            .padding(vertical = 6.dp)
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = swipeFade(swipeProvider()) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    when (option) {
                        is CoverOption.Switch -> SwitchOptionRow(option)
                        is CoverOption.Text -> TextOptionRow(option)
                    }
                }
            }
        }
    }
}

private fun swipeFade(offset: Float): Float = (1f - (abs(offset) * 2f)).coerceIn(0f, 1f)

@Composable
private fun SwitchOptionRow(option: CoverOption.Switch) {
    val parent = option.parent
    // A sub-option is only meaningful while its parent is on — mirrors how the same pair is
    // presented in Settings → Appearance.
    val parentEnabled = if (parent == null) {
        true
    } else {
        val (parentValue) = rememberPreference(parent.key, defaultValue = parent.default)
        if (parent.inverted) !parentValue else parentValue
    }
    if (!parentEnabled) return

    var stored by rememberPreference(option.key, defaultValue = option.default)
    val checked = if (option.inverted) !stored else stored

    OptionRowShell(
        icon = option.icon,
        title = option.title,
        description = option.description,
        indented = parent != null,
        onClick = { stored = if (option.inverted) checked else !checked },
    ) {
        AppSwitch(
            checked = checked,
            onCheckedChange = { wanted -> stored = if (option.inverted) !wanted else wanted },
        )
    }
}

@Composable
private fun TextOptionRow(option: CoverOption.Text) {
    var stored by rememberPreference(option.key, defaultValue = "")
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        com.example.musicfy.ui.component.TextFieldDialog(
            title = { Text(option.title) },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = stored,
                selection = androidx.compose.ui.text.TextRange(stored.length),
            ),
            placeholder = { Text(option.placeholder) },
            // Clearing the field is a legitimate action here: an empty disc name hides the plate.
            isInputValid = { true },
            onDone = { stored = it },
            onDismiss = { showDialog = false },
        )
    }

    OptionRowShell(
        icon = option.icon,
        title = option.title,
        description = stored.ifBlank { option.description },
        indented = false,
        onClick = { showDialog = true },
        trailing = null,
    )
}

@Composable
private fun OptionRowShell(
    icon: Int,
    title: String,
    description: String,
    indented: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = if (indented) 34.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/**
 * One-off toast-style note that the artwork can be held to get here directly. Fades itself out
 * after a few seconds rather than needing to be dismissed.
 */
@Composable
private fun LongPressHint(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4_000)
        visible = false
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = SwipeEasing),
        label = "longPressHint",
    )
    if (alpha < 0.01f) return

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Tip: hold the cover on the player to edit it directly",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
