// BottomSheetPlayer.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.media3.common.Player
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.extensions.toggleRepeatMode
import com.example.musicfy.ui.component.BlurEffectCache
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Build
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.joinAll
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerBackgroundStyleKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.DefaultPlayerCoverStyle
import com.example.musicfy.constants.PlayerCoverStyleKey
import com.example.musicfy.constants.ShowPlayerBottomCardKey
import com.example.musicfy.constants.InfiniteQueueKey
import androidx.activity.compose.BackHandler
import com.example.musicfy.ui.component.BottomSheet
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.player.customize.PlayerCustomizeScreen
import com.example.musicfy.ui.player.customize.PlayerEditOverlay
import com.example.musicfy.ui.player.customize.PlayerEditPhase
import com.example.musicfy.ui.player.customize.PlayerEditTarget
import com.example.musicfy.ui.player.menu.PlayerActionMenu
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * How far the controls are pulled up over the artwork.
 *
 * This only makes sense for an edge-to-edge cover, where the art bleeds behind the controls and
 * the overlap is the intended look. Every other cover style ends its artwork above the controls,
 * so the same 64dp lift dragged the title, timestamps and seek bar back up onto the cover - the
 * overlap that showed up once SQUARED became the default on devices without blur.
 */
private val ControlsDrawShift = 64.dp

/** Small upward nudge for the seek bar and its timestamps, so they sit off the controls below. */
private val SeekBarLift = 6.dp

/** The queue page's third button (autoplay) and the gap above it, on top of the lyrics pair. */
private val QueueButtonExtraHeight = 41.dp

/** Chevron, shuffle and autoplay: three 34dp buttons, 7dp apart. */
private val QueueButtonColumnHeight = 75.dp + QueueButtonExtraHeight

private const val EnterBlurRadius = 34f

/** The old gap from the seek bar down to the buttons (24dp plus the bar's lift). */
private val TransportGapAboveRow = 24.dp + SeekBarLift

/** How far the title row rises as a page opens and its title moves into the header. */
private val TitleRowDrift = 22.dp

/**
 * A slot whose height (and a gap under it) is read at measure time, with its content pinned to the
 * slot's bottom and free to overflow upward - what `height(x).wrapContentHeight(Bottom, unbounded)`
 * did, minus the recomposition every frame the height animates.
 */
private fun Modifier.bottomAnchoredSlot(
    height: () -> androidx.compose.ui.unit.Dp,
    bottomGap: () -> androidx.compose.ui.unit.Dp = { 0.dp },
): Modifier = layout { measurable, constraints ->
    val slot = height().roundToPx().coerceAtLeast(0)
    val gap = bottomGap().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
    )
    val total = (slot + gap).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, total) {
        placeable.place(0, slot - placeable.height)
    }
}

@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()

    val morphingGlassState = remember { GlassState() }
    // The like animation: hearts fly home to the like button on whichever page is showing.
    val likeBurst = remember { LikeBurstState() }
    // The lyrics and queue pages. They share the header morph (cover and title move up the same
    // way) and the slot above the seek bar; only one is ever open. Opened from the player, a page
    // grows out of the bottom card - by tap or by swiping up anywhere on the player.
    val transition = rememberPlayerPageTransition()
    val deck = remember { PlayerDeckState() }
    val morph = remember { CardMorphState() }
    val showLyrics by remember { derivedStateOf { transition.page == PlayerPage.LYRICS } }
    val showQueue by remember { derivedStateOf { transition.page == PlayerPage.QUEUE } }
    likeBurst.slotProvider = {
        when {
            showQueue -> LikeSlot.QUEUE
            showLyrics -> LikeSlot.LYRICS
            else -> LikeSlot.PLAYER
        }
    }
    val (infiniteQueue) = rememberPreference(InfiniteQueueKey, defaultValue = true)
    val (showPlayerBottomCard) = rememberPreference(ShowPlayerBottomCardKey, defaultValue = true)

    var editPhase by remember { mutableStateOf(PlayerEditPhase.NONE) }

    var showExpandMenu by remember { mutableStateOf(false) }
    var seeVideo by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    var showActionMenu by remember { mutableStateOf(false) }
    // Which surface opened the menu. The lyrics tools only appear when it came from the lyrics
    // page, where they have something visible to act on.
    var menuFromLyrics by remember { mutableStateOf(false) }

    val menuReveal = remember { mutableFloatStateOf(0f) }

    var showEditHint by remember { mutableStateOf(false) }
    val coverStyle by rememberEnumPreference(PlayerCoverStyleKey, DefaultPlayerCoverStyle)
    val (playerStyle, onPlayerStyleChange) = rememberEnumPreference(
        com.example.musicfy.constants.PlayerStyleKey,
        com.example.musicfy.constants.PlayerStyle.DEFAULT,
    )
    val controlsDrawShift = if (coverStyle == PlayerCoverStyle.EDGE_TO_EDGE) ControlsDrawShift else 0.dp
    val backgroundStyle by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        PlayerBackgroundStyle.COVER_GRADIENT,
    )

    var coverArtRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var controlsRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var bottomCardRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(editPhase) {
        if (editPhase != PlayerEditPhase.NONE) transition.close()
    }

    val enterBlur = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(editPhase) {
        enterBlur.animateTo(
            targetValue = if (editPhase == PlayerEditPhase.ENTERING) EnterBlurRadius else 0f,
            animationSpec = tween(
                durationMillis = 360,
                easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
            ),
        )
    }

    val enterBlurActive by remember {
        derivedStateOf { editPhase == PlayerEditPhase.ENTERING || enterBlur.value > 0.01f }
    }

    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded) {
            editPhase = PlayerEditPhase.NONE

            showActionMenu = false
            menuReveal.floatValue = 0f
            showExpandMenu = false
            likeBurst.clear()
            showSleepTimer = false
        }
    }

    // A page open when the player closes stays open: next time the player opens, it opens on that
    // page - the same way it opens on the player itself when that's where it was left. Closing the
    // page as the sheet started to collapse also ran the page's own shrink-into-the-card animation
    // on top of the sheet's, which is what made closing from lyrics or the queue so heavy. Only
    // dismissing the player (nothing left to play) forgets the page.
    LaunchedEffect(state.isDismissed) {
        if (state.isDismissed) transition.reset()
    }

    // Any part of the expanded player on screen. The pages and the seek bar's clock only run while
    // it is: collapsed, the pages are unmounted (still remembered) and the seek bar holds still.
    val sheetShowing by remember(state) { derivedStateOf { state.progress > 0f } }

    val isSheetInTransition by remember(state) {
        derivedStateOf { !state.isExpanded && !state.isCollapsed && !state.isDismissed }
    }

    var songInfoSourceRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var chevronButtonRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    // Kept apart from chevronButtonRect: both chevrons used to write that one, so the expand
    // menu anchored to whichever laid out last - often the other, stale one - and jumped.
    var lyricsChevronRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    var controlsInset by remember { mutableStateOf(220.dp) }

    /**
     * Distance from the top of the seek bar to the bottom of the screen.
     *
     * The lyrics list ends at the seek bar, so no line is ever drawn behind the bar or its
     * timestamps; the list's own bottom fade finishes just above it.
     */
    var lyricsBottomInset by remember { mutableStateOf(120.dp) }

    var lyricsImmersive by remember { mutableStateOf(false) }
    // The queue hides the controls the same way when scrolled, each page with its own flag so one
    // page's state never hides the controls on the other.
    var queueImmersive by remember { mutableStateOf(false) }
    val controlsHidden by animateFloatAsState(
        targetValue = if (
            (lyricsImmersive && showLyrics) || (queueImmersive && showQueue) ||
            editPhase == PlayerEditPhase.CUSTOMIZING
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsControlsHide",
    )

    // The header morph: cover and title moving up into the page header. Lyrics and the queue share
    // it, so switching straight from one page to the other leaves the header where it is. Clamped
    // here: the open spring's little landing bounce belongs to the page, not to the cover.
    val lyricsProgress by remember { derivedStateOf { transition.headerProgress.coerceIn(0f, 1f) } }

    // Each page's own content. Opened from the player it is the header's progress (one motion);
    // switching pages crossfades the content under a header that stays put.
    val lyricsPageProgress by remember { derivedStateOf { transition.lyricsProgress.coerceIn(0f, 1f) } }
    val queuePageProgress by remember { derivedStateOf { transition.queueProgress.coerceIn(0f, 1f) } }
    val lyricsPageMounted by remember { derivedStateOf { lyricsPageProgress > 0.001f } }
    val queuePageMounted by remember { derivedStateOf { queuePageProgress > 0.001f } }

    val lyricsMounted by remember { derivedStateOf { lyricsProgress > 0.001f } }

    val lyricsMorphing by remember {
        derivedStateOf { lyricsProgress > 0.001f && lyricsProgress < 0.999f }
    }
    val lyricsButtonMounted by remember { derivedStateOf { lyricsProgress < 0.999f } }
    val deckMounted by remember { derivedStateOf { controlsHidden < 0.99f } }
    val controlsInert by remember { derivedStateOf { controlsHidden > 0.99f } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val progressProvider = remember(state) { { state.progress.coerceIn(0f, 1f) } }
        val horizontalOffsetProvider = remember(state) { { state.horizontalOffset } }

        // Swipe up anywhere on the player (the card keeps its own swipe, the flip) and the card's
        // page follows the finger up out of it. One finger-length to the top of the card is the
        // whole way, so the card's edge stays under the finger.
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val currentPlayerStyle by androidx.compose.runtime.rememberUpdatedState(playerStyle)
        val currentCardShown by androidx.compose.runtime.rememberUpdatedState(showPlayerBottomCard)
        val swipeUp = remember(transition, deck, morph, screenHeightPx) {
            object : com.example.musicfy.ui.component.ExpandedSwipeUp {
                private fun distance() = (morph.cardRect?.top ?: (screenHeightPx * 0.85f)).coerceAtLeast(1f)

                override fun onStart(): Boolean =
                    currentPlayerStyle == com.example.musicfy.constants.PlayerStyle.DEFAULT &&
                        editPhase == PlayerEditPhase.NONE &&
                        transition.beginDrag(if (currentCardShown) deck.focusedPage else PlayerPage.LYRICS)

                override fun onDrag(dy: Float) = transition.dragBy(-dy / distance())

                override fun onEnd(velocityY: Float) = transition.release(-velocityY / distance())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = menuReveal.floatValue }
                .background(Color.Black)
        )

        BottomSheet(
            state = state,

            modifier = modifier
                .graphicsLayer {

                    val r = menuReveal.floatValue
                    if (r > 0.001f) {
                        val scale = 1f - 0.08f * r
                        scaleX = scale
                        scaleY = scale
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp * r)
                        clip = true
                    }
                }
                .then(
                if (enterBlurActive) {
                    Modifier.graphicsLayer {
                        renderEffect = enterBlurEffect(enterBlur.value)
                    }
                } else Modifier
            ),

            isExpandable = editPhase == PlayerEditPhase.NONE,
            isPillTransition = true,
            expandedSwipeUp = swipeUp,
            pureBlack = pureBlack,
            background = {},
            sharedContent = {
                MorphingCover(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    trackInfo = trackInfo,
                    isPlaying = transportState.isPlaying,
                    playbackState = transportState.playbackState,
                    maxWidth = screenWidth,
                    maxHeight = screenHeight,
                    collapsedBound = state.collapsedBound,
                    pureBlack = pureBlack,
                    glassState = morphingGlassState,
                    lyricsProgressProvider = { lyricsProgress },
                    forceVideoBackground = seeVideo,
                    modifier = Modifier.fillMaxSize(),
                    coverStyle = coverStyle,
                    backgroundStyle = backgroundStyle,
                    editMode = editPhase != PlayerEditPhase.NONE,
                    onLongPressCover = { editPhase = PlayerEditPhase.STYLE_SELECT },
                    onDoubleTapCover = { position ->
                        if (editPhase == PlayerEditPhase.NONE) {
                            likeBurst.likeFromDoubleTap(position, trackInfo.liked, playerConnection::toggleLike)
                        }
                    },
                    onArtBoundsChanged = { coverArtRect = it },
                )
                MorphingSongInfo(
                    trackInfo = trackInfo,
                    lyricsProgressProvider = { lyricsProgress },
                    sourceRectProvider = { songInfoSourceRect },
                    sheetProgressProvider = progressProvider,

                    targetY = statusBarTopInset + 38.dp,
                )
                // Above the title after a like, moving with it into the lyrics/queue header.
                LikedLabel(
                    likeCount = likeBurst.likeCount,
                    sourceRectProvider = { songInfoSourceRect },
                    headerTitleX = LyricsHeaderArtX + LyricsHeaderArtSize + 18.dp,
                    headerTitleY = statusBarTopInset + 38.dp,
                    lyricsProgressProvider = { lyricsProgress },
                    sheetProgressProvider = progressProvider,
                )
            },
            onDismiss = {
                playerConnection.service.clearAutomix()
                playerConnection.player.stop()
                playerConnection.player.clearMediaItems()
            },
            collapsedContent = {},
        ) {

            // Alternate styles replace the player outright rather than drawing over it, so none
            // of the default composition - blur, morphing cover, controls - runs while one is
            // active.
            if (playerStyle != com.example.musicfy.constants.PlayerStyle.DEFAULT) {
                val stylePalette = com.example.musicfy.ui.theme.ArtworkPalette.from(
                    com.example.musicfy.LocalArtworkColor.current
                )
                // The alternate styles draw their own full screen, which meant they also swallowed
                // the player's own gestures - there was no way to close them or reach the editor.
                // Both live here so every style gets them for free.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    // Only the top strip pulls the sheet down, so a drag lower on
                                    // the screen is still free for the style's own gestures.
                                    if (change.position.y < size.height * 0.25f && amount > 14f) {
                                        state.collapseSoft()
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { editPhase = PlayerEditPhase.STYLE_SELECT },
                            )
                        },
                ) {
                when (playerStyle) {
                    com.example.musicfy.constants.PlayerStyle.LYRICS_ABSTRACT ->
                        com.example.musicfy.ui.player.styles.LyricsAbstractStyle(
                            surface = stylePalette.surface,
                            accent = stylePalette.accent,
                            onOpenMenu = { showActionMenu = true },
                            modifier = Modifier.fillMaxSize(),
                        )

                    com.example.musicfy.constants.PlayerStyle.SIMPLE,
                    com.example.musicfy.constants.PlayerStyle.SIMPLE_COMPACT ->
                        com.example.musicfy.ui.player.styles.SimplePlayerStyle(
                            compact = playerStyle == com.example.musicfy.constants.PlayerStyle.SIMPLE_COMPACT,
                            surface = stylePalette.surface,
                            played = stylePalette.container,
                            onOpenMenu = { showActionMenu = true },
                            modifier = Modifier.fillMaxSize(),
                        )

                    com.example.musicfy.constants.PlayerStyle.DEFAULT -> Unit
                }
                }
                return@BottomSheet
            }

            SeamBlur(
                glassState = morphingGlassState,
                progressProvider = progressProvider,
                trackInfo = trackInfo,
                maxHeight = screenHeight,

                fadeProvider = { 1f - lyricsProgress },
            )

            // The card growing into the page, under it: its glass widens to the full screen while
            // the player behind dims a touch. Only there while a page is growing or shrinking.
            ExpandingCardSurface(transition = transition, morph = morph)

            // Unmounted while the player is collapsed - even though the page itself is remembered -
            // because its lines keep per-frame clocks of their own that would otherwise tick away
            // behind Home. It mounts again with the first frame of the next open. (Keeping it
            // composed but paused measured worse: waking a page whose position had jumped cost
            // more in one frame than building it fresh.)
            if (lyricsPageMounted && sheetShowing) {
                LyricsScreen(
                    onClose = { transition.close() },
                    screenHeight = screenHeight,

                    contentBottomInset = lyricsBottomInset,
                    onImmersiveChange = { lyricsImmersive = it },
                    isSheetDragging = isSheetInTransition,
                    isMorphing = lyricsMorphing,
                    enterProgressProvider = { transition.cardMorphProgress(PlayerPage.LYRICS) },
                    morph = morph,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTransitionLayer(PlayerPage.LYRICS, transition, morph) { lyricsPageProgress },
                )
            }

            if (queuePageMounted && sheetShowing) {
                QueueScreen(
                    onClose = { transition.close() },
                    // Fades into the seek bar like the lyrics; rows run under the chevron/shuffle/
                    // autoplay column, which reaches this far above the bar.
                    contentBottomInset = lyricsBottomInset,
                    controlsClearance = QueueButtonColumnHeight + 12.dp - SeekBarLift,
                    likeBurst = likeBurst,
                    onImmersiveChange = { queueImmersive = it },
                    enterProgressProvider = { transition.cardMorphProgress(PlayerPage.QUEUE) },
                    morph = morph,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageTransitionLayer(PlayerPage.QUEUE, transition, morph) { queuePageProgress },
                )
            }

            if (showPlayerBottomCard && deckMounted) {
                PlayerBottomCardStack(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    deck = deck,
                    transition = transition,
                    morph = morph,
                    onOpen = { transition.open(it) },
                    onLongPress = { editPhase = PlayerEditPhase.STYLE_SELECT },

                    modifier = Modifier
                        .align(Alignment.BottomCenter)

                        .onGloballyPositioned { bottomCardRect = it.boundsInRoot() }
                        .padding(horizontal = 26.dp)

                        .graphicsLayer {
                            alpha = 1f - controlsHidden
                            translationY = controlsHidden * size.height * 0.75f
                        }
                )
            }

            if (lyricsButtonMounted && editPhase == PlayerEditPhase.NONE) {
                // Removed as per request
            }

            // The transport row sits centred between the seek bar and the card. The seek bar keeps
            // its place; only the gap is split evenly now - it used to be 30dp above the buttons
            // and about 70dp below, which put them visibly high.
            //
            // Measured against where the seek bar is DRAWN: with the edge-to-edge cover the title
            // and bar are drawn controlsDrawShift higher than they're laid out, and leaving that
            // out centred the buttons on the bar's layout spot - 64dp too low under the full cover.
            // The navigation bar is only read when there's no card to sit above: read always, it
            // recomposed the whole player through the bar's hide/show animation at the end of every
            // open and the start of every close, for a value the card layout never uses.
            val belowControls = if (showPlayerBottomCard) {
                PlayerDeckHeight
            } else {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
            }
            val transportGap = (screenHeight * 0.19f - 37.dp - belowControls + TransportGapAboveRow + controlsDrawShift) / 2f
            // What's left of the gap above once the bar's own lift and the draw shift are counted.
            val transportSpacer = (transportGap - SeekBarLift - controlsDrawShift).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = belowControls + transportGap.coerceAtLeast(0.dp))
                    .graphicsLayer {
                        alpha = 1f - controlsHidden

                        translationY = controlsHidden * size.height * 0.55f
                    }

                    .then(
                        if (controlsInert) {
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes
                                            .forEach { it.consume() }
                                    }
                                }
                            }
                        } else Modifier
                    )

                    .onGloballyPositioned { coords ->
                        val parentHeight = coords.parentLayoutCoordinates?.size?.height ?: return@onGloballyPositioned
                        val topY = coords.positionInParent().y
                        val insetPx = (parentHeight - topY).coerceAtLeast(0f)
                        val inset = with(density) { insetPx.toDp() }
                        if ((inset - controlsInset).value.absoluteValue > 0.5f) {
                            controlsInset = inset
                        }

                        val bounds = coords.boundsInRoot()
                        controlsRect = androidx.compose.ui.geometry.Rect(
                            left = bounds.left,
                            top = bounds.top - with(density) { controlsDrawShift.toPx() },
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
            ) {
                Column(modifier = Modifier.offset(y = -controlsDrawShift)) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - lyricsProgress
                            // Drifts up as it hands its title to the header.
                            translationY = -lyricsProgress * TitleRowDrift.toPx()
                        }
                    ) {
                        SongInfoRow(
                            // Only while the sheet is up: during a slide the title's root position
                            // moves every frame, and the header morph that reads it recomposed with
                            // it. Its place on the sheet itself doesn't change.
                            onTitlePositioned = { if (state.isExpanded) songInfoSourceRect = it },
                            onChevronPositioned = { chevronButtonRect = it },
                            isExpandMenuOpen = showExpandMenu,
                            hideChevron = lyricsMounted,
                            onToggleExpandMenu = { showExpandMenu = !showExpandMenu },
                            likeBurst = likeBurst,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chevron + like live here while the lyrics are up: the song info row they
                    // normally sit in is faded out, so they stack above the progress bar instead.
                    // The queue page swaps like (moved to its header) for shuffle and autoplay.
                    // Height tracks the transitions so the controls don't jump when a page opens.
                    if (lyricsMounted) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                                // Read while measuring, not composing: the slot opens with the
                                // transition without recomposing the player on every frame.
                                .bottomAnchoredSlot(
                                    height = { (75.dp + QueueButtonExtraHeight * queuePageProgress) * lyricsProgress },
                                    bottomGap = { 12.dp * lyricsProgress },
                                ),
                            horizontalAlignment = Alignment.End,
                        ) {
                            // The same chevron as in the song info row, not a second one fading
                            // in: it glides from that chevron's spot to this one with the lyrics
                            // transition (the row's own chevron is hidden while this is mounted,
                            // so there's never two of them on screen). Before, this one sat still
                            // at its final spot while the other faded out, which read as a jump.
                            PressScaleActionButton(
                                icon = R.drawable.expand_less,
                                tint = Color.White.copy(alpha = 0.90f),
                                containerColor = Color.White.copy(alpha = 0.15f),
                                onClick = { showExpandMenu = !showExpandMenu },
                                hasShadow = false,
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        lyricsChevronRect = coords.boundsInRoot()
                                    }
                                    .graphicsLayer {
                                        val from = chevronButtonRect
                                        val to = lyricsChevronRect
                                        if (from != null && to != null) {
                                            val k = 1f - lyricsProgress
                                            translationX = (from.center.x - to.center.x) * k
                                            translationY = (from.center.y - to.center.y) * k
                                        } else {
                                            alpha = lyricsProgress
                                        }
                                        // The overlay draws its own (rotated) chevron here while open.
                                        if (showExpandMenu) alpha = 0f
                                    }
                            )
                            Spacer(modifier = Modifier.height(7.dp))
                            // Like (lyrics) and shuffle (queue) share a slot, crossfading when the
                            // page switches. Each is only composed while its page is, so an
                            // invisible one never takes the other's taps.
                            Box {
                                if (lyricsPageMounted) {
                                    PressScaleActionButton(
                                        icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                                        tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                                        onClick = {
                                            likeBurst.likeFromButton(LikeSlot.LYRICS, trackInfo.liked, playerConnection::toggleLike)
                                        },
                                        hasShadow = false,
                                        modifier = Modifier
                                            .likeButtonTarget(likeBurst, LikeSlot.LYRICS)
                                            .graphicsLayer { alpha = lyricsPageProgress },
                                    )
                                }
                                if (queuePageMounted) {
                                    val shuffleOn = transportState.shuffleModeEnabled
                                    PressScaleActionButton(
                                        icon = R.drawable.shuffle,
                                        tint = if (shuffleOn) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (shuffleOn) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleOn },
                                        hasShadow = false,
                                        modifier = Modifier.graphicsLayer { alpha = queuePageProgress },
                                    )
                                }
                            }
                            // Autoplay grows in underneath: the slot opens from nothing, so shuffle
                            // rises off it rather than everything jumping when it mounts.
                            if (queuePageMounted) {
                                Box(
                                    contentAlignment = Alignment.BottomEnd,
                                    modifier = Modifier.bottomAnchoredSlot(
                                        height = { QueueButtonExtraHeight * queuePageProgress },
                                    ),
                                ) {
                                    PressScaleActionButton(
                                        icon = R.drawable.all_inclusive,
                                        tint = if (infiniteQueue) Color.White else Color.White.copy(alpha = 0.85f),
                                        containerColor = if (infiniteQueue) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                                        onClick = { playerConnection.setInfiniteQueueEnabled(!infiniteQueue) },
                                        hasShadow = false,
                                        modifier = Modifier.graphicsLayer { alpha = queuePageProgress },
                                    )
                                }
                            }
                        }
                    }

                    // Column, not Box: PlayerProgressSlider emits the track and the timestamp
                    // row as two siblings, so a Box would stack the timestamps on top of the
                    // seek bar instead of below it.
                    Column(
                        modifier = Modifier
                            .offset(y = -SeekBarLift)
                            .onGloballyPositioned { coords ->
                                // Measured to the TOP of the seek bar: the lyrics list ends there and
                                // its fade finishes above it. Measured to the bottom, lines kept
                                // scrolling behind the bar and the timestamps.
                                //
                                // Only with the sheet up and the controls in place. In root
                                // coordinates this moved on every frame of the sheet's slide (and of
                                // the controls hiding), and the pages that read it recomposed - and
                                // re-laid out their lists - frame by frame for it.
                                if (!state.isExpanded || controlsHidden > 0.001f) return@onGloballyPositioned
                                val rootHeight = coords.findRootCoordinates().size.height
                                val topY = coords.positionInRoot().y
                                val insetPx = (rootHeight - topY).coerceAtLeast(0f)
                                val inset = with(density) { insetPx.toDp() }
                                if ((inset - lyricsBottomInset).value.absoluteValue > 0.5f) {
                                    lyricsBottomInset = inset
                                }
                            }
                    ) {
                        PlayerProgressSlider(active = sheetShowing)
                    }
                }

                Spacer(modifier = Modifier.height(transportSpacer))

                PlayerTransportRow()
            }

            if (editPhase == PlayerEditPhase.SELECTING) {
                PlayerEditOverlay(
                    coverRect = coverArtRect,
                    controlsRect = controlsRect,
                    bottomCardRect = bottomCardRect,
                    onSelect = { target ->

                        if (target == PlayerEditTarget.COVER) {
                            editPhase = PlayerEditPhase.CUSTOMIZING
                        }
                    },
                    onDismiss = { editPhase = PlayerEditPhase.NONE },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (editPhase == PlayerEditPhase.CUSTOMIZING) {
                PlayerCustomizeScreen(

                    onBack = {
                        editPhase = if (showEditHint) {
                            showEditHint = false
                            PlayerEditPhase.NONE
                        } else {
                            PlayerEditPhase.SELECTING
                        }
                    },
                    showLongPressHint = showEditHint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Above the player and its controls, so a heart can fly over everything on its way home.
        LikeBurstOverlay(state = likeBurst)

        ExpandMenuOverlay(
            visible = showExpandMenu,
            onDismiss = { showExpandMenu = false },
            onOtherMenuClick = {
                // The lyrics tools are in this menu now (no button in the lyrics header).
                menuFromLyrics = showLyrics
                showActionMenu = true
            },
            onSleepTimerClick = { showSleepTimer = true },
            sleepTimerActive = playerConnection.service.sleepTimer.isActive,
            seeVideo = seeVideo,
            onSeeVideoToggle = { seeVideo = !seeVideo },
            onRepeatClick = { playerConnection.player.toggleRepeatMode() },
            repeatMode = transportState.repeatMode,
            chevronRect = if (showLyrics || showQueue) lyricsChevronRect ?: chevronButtonRect else chevronButtonRect,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            controlsDrawShift = controlsDrawShift,
        )

        // Pulling down at the top of the queue list stretches the list instead of closing the
        // player. (Lyrics keep the pull-to-close.)
        androidx.compose.runtime.DisposableEffect(state, showQueue) {
            state.collapseOnNestedOverscroll = !showQueue
            onDispose { state.collapseOnNestedOverscroll = true }
        }

        if ((showQueue || showLyrics) && state.isExpanded) {
            // Composed only while a page is open on the expanded player, so it registers after -
            // and wins over - the sheet's own back handler: back shrinks the page into its card
            // first, then collapses the player. Not while collapsed: the page is remembered then,
            // and back belongs to the app.
            BackHandler { transition.close() }
        }

        if (showSleepTimer) {
            com.example.musicfy.ui.player.menu.SleepTimerSheet(
                onDismiss = { showSleepTimer = false }
            )
        }

        if (showActionMenu) {
            PlayerActionMenu(
                fromLyrics = menuFromLyrics,
                onDismiss = {
                    showActionMenu = false
                    menuReveal.floatValue = 0f
                },
                onEditPlayer = {
                    // Same destination as a long press: the style picker, with the current player
                    // zooming back behind it. Jumping straight into the cover editor skipped the
                    // choice of style entirely.
                    editPhase = PlayerEditPhase.STYLE_SELECT
                },
                onReveal = { menuReveal.floatValue = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (editPhase == PlayerEditPhase.STYLE_SELECT) {
            com.example.musicfy.ui.player.customize.PlayerStyleSelector(
                selected = playerStyle,
                artworkUrl = trackInfo.thumbnailUrl,
                onSelect = { onPlayerStyleChange(it) },
                onEdit = {
                    // Straight to "what do you want to edit" rather than dropping the user into
                    // the cover carousel, which assumed the answer.
                    editPhase = PlayerEditPhase.SELECTING
                },
                onDismiss = { editPhase = PlayerEditPhase.NONE },
                modifier = Modifier.fillMaxSize(),
            )
        }

    }
}

/** Transparent margin around each expand-menu item's blur layer - roughly the max blur radius. */
private val MenuItemBlurRoom = 20.dp

/**
 * Grows the layer that follows by [room] on every side without changing the layout footprint:
 * pair with `.padding(room)` after the layer. Lets a blur render past the content's edges
 * instead of being cut off at them.
 */
private fun Modifier.blurBleedRoom(room: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val pad = room.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * pad, vertical = 2 * pad))
        val width = constraints.constrainWidth(placeable.width - 2 * pad)
        val height = constraints.constrainHeight(placeable.height - 2 * pad)
        layout(width, height) { placeable.place(-pad, -pad) }
    }

private fun enterBlurEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect? =
    com.example.musicfy.ui.component.BlurEffectCache.get(radius, android.graphics.Shader.TileMode.CLAMP)

@Composable
private fun ExpandMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOtherMenuClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    sleepTimerActive: Boolean,
    seeVideo: Boolean,
    onSeeVideoToggle: () -> Unit,
    onRepeatClick: () -> Unit,
    repeatMode: Int,
    chevronRect: androidx.compose.ui.geometry.Rect?,
    screenWidth: androidx.compose.ui.unit.Dp,
    screenHeight: androidx.compose.ui.unit.Dp,
    controlsDrawShift: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val menuItems = remember {
        listOf(
            "Other menu" to R.drawable.more_horiz,
            "Sleep timer" to R.drawable.sleep_timer,
            "See video" to R.drawable.slow_motion_video,
            "Repeat" to R.drawable.repeat,
        )
    }

    val density = LocalDensity.current
    val itemProgress = remember { List(menuItems.size) { androidx.compose.animation.core.Animatable(0f) } }
    val bgAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    var isRendered by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isRendered = true
            launch {
                bgAlpha.animateTo(
                    targetValue = 0.62f,
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                )
            }
            itemProgress.forEachIndexed { index, anim ->
                launch {
                    kotlinx.coroutines.delay((menuItems.size - 1 - index) * 40L)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.54f,
                            stiffness = 320f
                        )
                    )
                }
            }
        } else if (isRendered) {
            val bgJob = launch {
                bgAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                )
            }
            val itemsJob = launch {
                val exitJobs = itemProgress.mapIndexed { index, anim ->
                    launch {
                        kotlinx.coroutines.delay(index * 25L)
                        anim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        )
                    }
                }
                exitJobs.forEach { it.join() }
            }
            bgJob.join()
            itemsJob.join()
            isRendered = false
        }
    }

    if (isRendered) {
        val chevronBottomDp = if (chevronRect != null) {
            val screenHeightPx = with(density) { screenHeight.toPx() }
            val bottomPx = (screenHeightPx - chevronRect.bottom).coerceAtLeast(0f)
            with(density) { bottomPx.toDp() }
        } else {
            (screenHeight * 0.19f) + 93.dp - controlsDrawShift
        }

        val menuBottomDp = if (chevronRect != null) {
            val screenHeightPx = with(density) { screenHeight.toPx() }
            val topPx = (screenHeightPx - chevronRect.top).coerceAtLeast(0f)
            with(density) { (topPx + 14.dp.toPx()).toDp() }
        } else {
            chevronBottomDp + 48.dp
        }

        // Right edge of the chevron actually tapped, not an assumed padding - the lyrics-page
        // chevron and the song-info one don't share an exact x.
        val chevronEndDp = if (chevronRect != null) {
            val screenWidthPx = with(density) { screenWidth.toPx() }
            with(density) { (screenWidthPx - chevronRect.right).coerceAtLeast(0f).toDp() }
        } else {
            PlayerHorizontalPadding
        }

        val chevronRotation by animateFloatAsState(
            targetValue = if (visible) 180f else 0f,
            label = "overlayChevronRot",
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f)
        )
        val chevronScale by animateFloatAsState(
            targetValue = if (visible) 0.86f else 1f,
            label = "overlayChevronScale",
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f)
        )
        val overlayChevronBgColor by animateColorAsState(
            targetValue = if (visible) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
            label = "overlayChevronBgColor",
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
        val overlayChevronTint by animateColorAsState(
            targetValue = if (visible) Color.White else Color.White.copy(alpha = 0.90f),
            label = "overlayChevronTint",
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha.value))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            // Menu items placed directly ON TOP of the chevron:
            Column(
                modifier = modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = chevronEndDp, bottom = menuBottomDp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                menuItems.forEachIndexed { index, item ->
                    val p = itemProgress[index].value
                    if (p > 0.005f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                // The blur's layer used to end exactly at the row's edges, with
                                // the button circle and text shadow touching them, and CLAMP
                                // tiling smeared those edge pixels outward - the stray lines at
                                // the sides. Give it room to fade out, and let it fade to clear.
                                .blurBleedRoom(MenuItemBlurRoom)
                                .graphicsLayer {
                                    val progress = p.coerceIn(0f, 1.25f)
                                    alpha = progress.coerceIn(0f, 1f)
                                    translationY = (1f - progress) * 55f
                                    val scale = (0.7f + 0.3f * progress).coerceAtLeast(0.01f)
                                    scaleX = scale
                                    scaleY = scale
                                    val blurPx = (1f - progress.coerceIn(0f, 1f)) * 20f
                                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        // DECAL only exists on API 31+; keep the reference inside
                                        // the check so older devices never resolve it.
                                        BlurEffectCache.get(blurPx, android.graphics.Shader.TileMode.DECAL)
                                    } else {
                                        null
                                    }
                                }
                                .padding(MenuItemBlurRoom)
                        ) {
                            androidx.compose.material3.Text(
                                text = item.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )

                            val isToggled = when (index) {
                                1 -> sleepTimerActive
                                2 -> seeVideo
                                3 -> repeatMode != Player.REPEAT_MODE_OFF
                                else -> false
                            }
                            val iconTint = if (isToggled) Color.White else Color.White.copy(alpha = 0.85f)
                            val containerColor = if (isToggled) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.16f)

                            PressScaleActionButton(
                                icon = item.second,
                                tint = iconTint,
                                containerColor = containerColor,
                                hasShadow = false,
                                badgeText = if (index == 3 && repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                                onClick = {
                                    when (index) {
                                        0 -> { onDismiss(); onOtherMenuClick() }
                                        1 -> { onDismiss(); onSleepTimerClick() }
                                        2 -> { onSeeVideoToggle(); onDismiss() }
                                        3 -> { onRepeatClick() }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Chevron rendered ON TOP of the dim background, directly tappable, zoomed out & rotated 180°:
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = chevronEndDp, bottom = chevronBottomDp)
            ) {
                PressScaleActionButton(
                    icon = R.drawable.expand_less,
                    tint = overlayChevronTint,
                    containerColor = overlayChevronBgColor,
                    onClick = onDismiss,
                    hasShadow = false,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = chevronRotation
                        scaleX = chevronScale
                        scaleY = chevronScale
                    }
                )
            }
        }
    }
}
