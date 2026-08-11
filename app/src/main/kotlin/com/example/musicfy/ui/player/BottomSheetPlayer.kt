// BottomSheetPlayer.kt
// v0 minimal orchestrator, replacing the ~2,700-line BottomSheetPlayer that used to live in
// Player.kt (now at /old-player/Player.kt). Same call signature as before, so MainActivity.kt
// needed no changes. Only collects the two scoped flows it actually needs (trackInfo,
// transportState) instead of ~17 flows at root.

package com.example.musicfy.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerBackgroundStyleKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.PlayerCoverStyleKey
import com.example.musicfy.constants.ShowPlayerBottomCardKey
import com.example.musicfy.ui.component.BottomSheet
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.player.customize.PlayerCustomizeScreen
import com.example.musicfy.ui.player.customize.PlayerEditOverlay
import com.example.musicfy.ui.player.customize.PlayerEditPhase
import com.example.musicfy.ui.player.customize.PlayerEditTarget
import com.example.musicfy.ui.player.customize.PlayerEnteringEditOverlay
import com.example.musicfy.ui.player.menu.PlayerActionMenu
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * How far the title/slider sub-column is drawn above its own layout position.
 *
 * Modifier.offset applied at draw/placement time reserves no layout space, which is what keeps
 * the transport row's position independent of it. Shared as a constant so the edit overlay's
 * outline can undo exactly the same shift when it measures this block.
 */
private val ControlsDrawShift = 64.dp

/** Peak blur radius, in px, of the player behind the "Entering edit mode" message. */
private const val EnterBlurRadius = 34f

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

    // Hoisted so both sharedContent (MorphingCover, which registers this as a glassRoot source)
    // and content (SeamBlur, which reads that same captured content) can share one instance.
    val morphingGlassState = remember { GlassState() }
    var showLyrics by remember { mutableStateOf(false) }
    val (showPlayerBottomCard) = rememberPreference(ShowPlayerBottomCardKey, defaultValue = true)

    // Player customization. NONE is the state the player is in essentially always; the other two
    // are only reachable by long-pressing the artwork.
    var editPhase by remember { mutableStateOf(PlayerEditPhase.NONE) }
    // The ⋯ button now occupies the slot the lyrics shortcut used to. Lyrics are still reachable
    // from the bottom card deck, which is the primary way in anyway.
    var showActionMenu by remember { mutableStateOf(false) }
    // 0..1 of the menu's own open animation, reported back so the player can scale away beneath
    // it the way the app's other popups do. A plain float read in the draw phase, so the whole
    // player is not recomposed on every frame of the sheet sliding up.
    val menuReveal = remember { mutableFloatStateOf(0f) }
    // Set when the customization page is opened from the menu rather than by long-pressing the
    // artwork, so it can tell you the gesture exists.
    var showEditHint by remember { mutableStateOf(false) }
    val coverStyle by rememberEnumPreference(PlayerCoverStyleKey, PlayerCoverStyle.EDGE_TO_EDGE)
    val backgroundStyle by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        PlayerBackgroundStyle.COVER_GRADIENT,
    )
    // Rects of the three editable regions, captured live so the overlay can outline exactly where
    // they really are rather than reproducing this file's nested layout arithmetic.
    var coverArtRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var controlsRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var bottomCardRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // The lyrics page and the editor both want the whole screen; entering one leaves the other.
    LaunchedEffect(editPhase) {
        if (editPhase != PlayerEditPhase.NONE) showLyrics = false
    }

    // Blur applied to the entire player while the "Entering edit mode" beat plays. Held as an
    // Animatable and read inside a graphicsLayer (draw phase) rather than as a composable-scope
    // float, so ramping it costs redraws instead of a recomposition of the player every frame.
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
    // Boolean, so this only recomposes when the layer needs attaching or detaching — never once
    // per frame of the ramp. The layer must outlive the ENTERING phase itself to let the blur
    // animate back down instead of snapping off.
    val enterBlurActive by remember {
        derivedStateOf { editPhase == PlayerEditPhase.ENTERING || enterBlur.value > 0.01f }
    }
    // Collapsing or dismissing the player while editing must not leave the editor stranded on
    // top of the mini pill.
    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded) {
            editPhase = PlayerEditPhase.NONE
            // The menu has to go with it, and its zoom-out has to be released — left set, the
            // player would stay scaled down after collapsing, which is the shrunken-then-popping
            // state you get when switching away with the sheet open.
            showActionMenu = false
            menuReveal.floatValue = 0f
        }
    }

    // Automatically transition out of lyrics mode when the user swipes down to dismiss the player
    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded && showLyrics) {
            showLyrics = false
        }
    }

    val isSheetInTransition by remember(state) {
        derivedStateOf { !state.isExpanded && !state.isCollapsed && !state.isDismissed }
    }

    // Last real on-screen position of SongInfoRow's title+artist, for MorphingSongInfo to travel
    // from. Keeps its value after SongInfoRow unmounts (which happens the instant showLyrics
    // flips, since it's behind a hard if/else below) — see SongInfoRow's onTitlePositioned doc.
    var songInfoSourceRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // Height of the shared slider + transport block, measured from the bottom of the sheet.
    // Seeded with a rough estimate purely so the first frame isn't wildly wrong; the real value
    // lands on the first layout pass.
    var controlsInset by remember { mutableStateOf(220.dp) }

    // Set by LyricsScreen once it has been idle on the highlighted line. Slides and fades the
    // shared transport block away so the lyrics own the full page, Apple Music style.
    var lyricsImmersive by remember { mutableStateOf(false) }
    val controlsHidden by animateFloatAsState(
        // Only the customization page hides the chrome. The part-selection layer deliberately
        // leaves the controls and the card deck on screen — they are two of the three things you
        // can point at, so they have to be visible to be pointed at.
        targetValue = if (lyricsImmersive || editPhase == PlayerEditPhase.CUSTOMIZING) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsControlsHide",
    )

    // Drives the shared cover shrinking into the lyrics page's header slot. Animated rather than
    // a hard boolean so the artwork travels between the two positions instead of teleporting.
    val lyricsProgress by animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,
        // cubic-bezier(0.5, 0.45, 0, 1) — the same curve used for the settings title morph.
        animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)),
        label = "lyricsCoverMorph",
    )

    // Mount/unmount gates for everything below, as booleans.
    //
    // These conditions used to read the animating floats directly in composition
    // (`if (lyricsProgress > 0.001f)`, `if (controlsHidden < 0.99f)`), which subscribes this whole
    // content lambda to them: every frame of the 520ms lyrics morph and the 420ms chrome fade
    // rebuilt the ENTIRE player subtree — MorphingCover, SeamBlur, the lyrics page, the card deck
    // and the transport block — on top of the drawing those same frames were already doing. That
    // is the stall when expanding the player and moving into lyrics: the work is a full
    // recomposition per frame, not the animations themselves.
    //
    // Wrapped in derivedStateOf, the recomposition happens on the two frames where each boolean
    // actually flips, and the continuous values stay where they belong — read inside graphicsLayer
    // blocks in the draw phase. Same reasoning (and the same fix) as enterBlurActive above.
    val lyricsMounted by remember { derivedStateOf { lyricsProgress > 0.001f } }
    // Strictly in flight — not settled open, not settled closed.
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

        // Black plate behind the player, revealed as the menu scales it down. Without it the
        // margin the zoom-out opens up is simply transparent and you see straight through to
        // whatever is behind the player — the same reason ZoomOutPopupContainer paints its own
        // root black.
        //
        // Alpha is a draw-phase read of menuReveal, so this costs nothing while the menu is
        // closed, and it carries no pointer input, so it never intercepts a touch.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = menuReveal.floatValue }
                .background(Color.Black)
        )

        BottomSheet(
            state = state,
            // The blur goes on the sheet as a whole — artwork, controls and card deck alike —
            // which is why it is attached here rather than inside the content slot: the cover
            // lives in BottomSheet's sharedContent, a sibling of that slot, so nothing inside
            // content could have blurred it. The overlay that reads on top is composed further
            // down as a sibling of this call, outside the blurred subtree, so its text stays
            // sharp.
            modifier = modifier
                .graphicsLayer {
                    // Zoom-out behind the sheet, matching ZoomOutPopupContainer elsewhere in the
                    // app. Identity while the menu is closed.
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
            // Freezes the sheet's own drag handling for as long as the editor is up. That
            // handler lives on an ancestor of everything the editor draws, so without this a
            // swipe down collapsed the player and a swipe left ran the song-change gesture,
            // both of which tore the editor off the screen mid-interaction.
            isExpandable = editPhase == PlayerEditPhase.NONE,
            isPillTransition = true,
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
                    modifier = Modifier.fillMaxSize(),
                    coverStyle = coverStyle,
                    backgroundStyle = backgroundStyle,
                    editMode = editPhase != PlayerEditPhase.NONE,
                    onLongPressCover = { editPhase = PlayerEditPhase.ENTERING },
                    onArtBoundsChanged = { coverArtRect = it },
                )
                MorphingSongInfo(
                    trackInfo = trackInfo,
                    lyricsProgressProvider = { lyricsProgress },
                    sourceRectProvider = { songInfoSourceRect },
                    // Same statusBarTop + 28dp top inset as the cover, nudged down so the
                    // (bigger, 18sp/14sp) two-line text block sits roughly centred against the
                    // 60dp cover rather than flush with its top edge.
                    targetY = statusBarTopInset + 38.dp,
                )
            },
            onDismiss = {
                playerConnection.service.clearAutomix()
                playerConnection.player.stop()
                playerConnection.player.clearMediaItems()
            },
            collapsedContent = {},
        ) {
            // Always composed now, not gated inside the else branch — it used to hard-unmount
            // the instant showLyrics flipped true (and hard-remount just as abruptly on close),
            // which read as the blur band "cutting" instead of fading, and — since it sits right
            // at the seam next to the cover — as a flicker around the cover's corner specifically
            // when closing. Its own alpha now carries a (1 - lyricsProgress) factor so it fades
            // out in step with the cover shrinking away, and back in as the cover returns,
            // instead of popping either direction.
            SeamBlur(
                glassState = morphingGlassState,
                progressProvider = progressProvider,
                trackInfo = trackInfo,
                maxHeight = screenHeight,
                // Gone by the time the lyrics page is open. The band is there to blend the bottom
                // edge of the cover art into the controls; on the lyrics page the cover has left,
                // so all that remains is a dark gradient bar behind the timestamp row.
                fadeProvider = { 1f - lyricsProgress },
            )
            // Lyrics page. Composed for the whole morph, not just at the end of it, so the text
            // can grow into place from the card instead of appearing once the animation finishes.
            //
            // It rises from the deck's own position and scales up out of it: translationY carries
            // it from where the card sits up to its resting place, and the scale starts at the
            // card's width ratio so the first thing you see is genuinely the card's footprint
            // widening into the page. Alpha ramps in over the first ~45% so it is already legible
            // well before the motion settles.
            if (lyricsMounted) {
                LyricsScreen(
                    onClose = { showLyrics = false },
                    screenHeight = screenHeight,
                    // Measured, not derived from the 0.19f formula. The lyrics list has to stop
                    // exactly at the top of the slider/timestamp row, and that row's Y is the
                    // product of an offset(-64dp) plus several spacers plus the transport row's
                    // intrinsic height — reproducing that arithmetic here is what previously put
                    // the fade in the wrong place and let lyrics run behind the controls.
                    contentBottomInset = controlsInset,
                    onImmersiveChange = { lyricsImmersive = it },
                    isSheetDragging = isSheetInTransition,
                    isMorphing = lyricsMorphing,
                    onOpenMenu = { showActionMenu = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = lyricsProgress
                            alpha = (p / 0.45f).coerceIn(0f, 1f)
                            val s = 0.88f + 0.12f * p
                            scaleX = s
                            scaleY = s
                            // Grow out of the deck: the card sits near the bottom, so the
                            // expansion origin is down there rather than the screen's centre.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.86f)
                            translationY = (1f - p) * size.height * 0.10f
                        },
                )
            }

            // The deck is NOT dismissed on the lyrics page. It stays at the same size and place,
            // and the queue card rides forward into it — see lyricsProgressProvider below. The
            // lyrics card is the one that hands over: as the queue takes the front slot, the
            // lyrics card's own frontness drops to 0 and DeckCard fades its content out, which is
            // the "expanding into the page" half of the transition.
            // Composed out once the immersive fade completes, not merely faded: the deck sits above
            // the lyrics list and an alpha-0 card still hit-tests, so leaving it mounted swallowed
            // taps and scrolls along the bottom of the page.
            if (showPlayerBottomCard && deckMounted) {
                PlayerBottomCardStack(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    onOpenLyrics = { showLyrics = true },
                    // No dedicated full queue screen exists yet, so tapping the queue card
                    // skips to the track it's previewing rather than doing nothing.
                    onOpenQueue = { playerConnection.player.seekToNext() },
                    lyricsProgressProvider = { lyricsProgress },
                    // Flush to the bottom edge (no navigationBarsPadding, no vertical
                    // padding) so the deck reads as attached rather than floating —
                    // PlayerBottomCardStack handles keeping its own content clear of the
                    // gesture bar internally.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Measured OUTSIDE the horizontal padding, so the outline spans the deck's
                        // full width rather than hugging the card's inset body.
                        .onGloballyPositioned { bottomCardRect = it.boundsInRoot() }
                        .padding(horizontal = 26.dp)
                        // Retreats off the bottom edge with the transport block, on the same
                        // curve, so the whole chrome leaves as one movement.
                        .graphicsLayer {
                            alpha = 1f - controlsHidden
                            translationY = controlsHidden * size.height * 0.75f
                        }
                )
            }

            // Composed only while the player is showing — an alpha-0 button left in the tree
            // would still be hit-testable and would swallow taps meant for the lyrics page.
            if (lyricsButtonMounted && editPhase == PlayerEditPhase.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 20.dp)
                        .size(40.dp)
                        // Fades out early in the morph rather than sitting on top of the opening
                        // lyrics page; the page's own header owns that corner from here on.
                        .graphicsLayer { alpha = (1f - lyricsProgress / 0.35f).coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { showActionMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        tint = Color.White,
                        // Same glyph the lyrics page uses, turned horizontal.
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
            }

            // The ONE progress slider + transport row, for both the player and the lyrics page.
            //
            // There used to be two: PlayerControls owned a copy, LyricsScreen built its own inside
            // an AnimatedVisibility with slideInVertically(initialOffsetY = { it / 3 }). Opening
            // lyrics unmounted the first and slid the second up from a third of the screen, so the
            // controls and the timestamp bar visibly travelled — and briefly doubled — instead of
            // staying put. Declaring them here, outside the if/else, means the exact same nodes
            // stay mounted across the transition, so there is nothing left to move or duplicate.
            //
            // Declared after the branch so it draws above the lyrics list; the list scrolls behind
            // it, which is what the top/bottom fades are for.
            //
            // Layout is copied verbatim from the old PlayerControls, including the -64dp offset
            // trick (a draw-time shift that deliberately reserves no layout space, so the transport
            // row's position is unaffected by it). Only the title block fades, since MorphingSongInfo
            // takes the title over to the lyrics header.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = (screenHeight * 0.19f) - 37.dp)
                    .graphicsLayer {
                        alpha = 1f - controlsHidden
                        // Slides down as it fades rather than dissolving in place, so it reads as
                        // retreating off the bottom edge and coming back from it.
                        translationY = controlsHidden * size.height * 0.55f
                    }
                    // A faded-out block still hit-tests, and it sits above the lyrics list, so
                    // without this the invisible transport row keeps swallowing taps and scrolls
                    // in the lower half of the page. alpha/graphicsLayer do NOT affect hit
                    // testing — the events have to be consumed explicitly.
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
                    // Publishes how much vertical room this block occupies from the bottom of the
                    // sheet, so the lyrics list can end precisely where the timestamp begins.
                    .onGloballyPositioned { coords ->
                        val parentHeight = coords.parentLayoutCoordinates?.size?.height ?: return@onGloballyPositioned
                        val topY = coords.positionInParent().y
                        val insetPx = (parentHeight - topY).coerceAtLeast(0f)
                        val inset = with(density) { insetPx.toDp() }
                        if ((inset - controlsInset).value.absoluteValue > 0.5f) {
                            controlsInset = inset
                        }

                        // Same node, second purpose: the edit overlay's outline for this block.
                        // The title/slider sub-column is drawn ControlsDrawShift higher than this
                        // node's own top (see the offset below, which reserves no layout space),
                        // so the measured rect has to be raised by the identical amount or the
                        // outline sits low over the transport row.
                        val bounds = coords.boundsInRoot()
                        controlsRect = androidx.compose.ui.geometry.Rect(
                            left = bounds.left,
                            top = bounds.top - with(density) { ControlsDrawShift.toPx() },
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
            ) {
                Column(modifier = Modifier.offset(y = -ControlsDrawShift)) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier.graphicsLayer { alpha = 1f - lyricsProgress }
                    ) {
                        SongInfoRow(onTitlePositioned = { songInfoSourceRect = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerProgressSlider()
                }

                Spacer(modifier = Modifier.height(24.dp))

                PlayerTransportRow()
            }

            // Customization layers, declared last so they draw above everything else. Both are
            // composed out entirely when not in use — an alpha-0 full-screen overlay would still
            // hit-test and swallow every gesture the player depends on.
            if (editPhase == PlayerEditPhase.SELECTING) {
                PlayerEditOverlay(
                    coverRect = coverArtRect,
                    controlsRect = controlsRect,
                    bottomCardRect = bottomCardRect,
                    onSelect = { target ->
                        // Only the cover leads anywhere yet. The other two targets are drawn and
                        // routed so the text/controls editor can be added without reopening this
                        // file, but they intentionally do nothing for now.
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
                    // Back goes to the part picker when that is where this page came from. Opened
                    // straight from the menu there is no picker behind it, so back leaves.
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

        if (showActionMenu) {
            PlayerActionMenu(
                onDismiss = {
                    showActionMenu = false
                    menuReveal.floatValue = 0f
                },
                onEditPlayer = {
                    showEditHint = true
                    editPhase = PlayerEditPhase.CUSTOMIZING
                },
                onReveal = { menuReveal.floatValue = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Outside the BottomSheet call on purpose: everything above is inside the subtree the
        // entering blur is applied to, and this message has to stay sharp over it.
        if (editPhase == PlayerEditPhase.ENTERING) {
            PlayerEnteringEditOverlay(
                onFinished = {
                    // Guarded: a long press that got cancelled (or the player collapsing) can
                    // move the phase on before this timer fires, and it must not drag the user
                    // back into the editor after that.
                    if (editPhase == PlayerEditPhase.ENTERING) {
                        editPhase = PlayerEditPhase.SELECTING
                    }
                },
                onCancel = { editPhase = PlayerEditPhase.NONE },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Quantisation grid for the edit-mode blur, in px. */
private const val EnterBlurQuantPx = 6f

/**
 * Cached blur effects for the "entering edit mode" ramp, keyed by quantised radius.
 *
 * RenderEffect is immutable, so animating a radius mints a brand new one on every frame — roughly
 * forty full-screen blur objects over the 360ms ramp, each of which forces HWUI to rebuild the blur
 * setup for the entire player subtree. Snapping the radius to a 6px grid produces about six
 * distinct effects for the whole animation, and most frames then hand HWUI the *same object* it saw
 * last frame, which is what lets it reuse its cached layer instead of re-deriving one.
 *
 * Six pixels of Gaussian radius is far below what is visible at this blur strength. Exactly the
 * trick LyricsGlowLine.blurEffectForRadius already uses per lyric line, applied to the one blur in
 * the app that covers the whole screen.
 *
 * Only ever touched from the draw phase on the main thread, so the lazy fill needs no locking.
 */
private val EnterBlurCache = HashMap<Int, androidx.compose.ui.graphics.RenderEffect>()

private fun enterBlurEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || radius <= 0.5f) {
        return null
    }
    val step = (radius / EnterBlurQuantPx).roundToInt().coerceAtLeast(1)
    return EnterBlurCache.getOrPut(step) {
        val r = step * EnterBlurQuantPx
        android.graphics.RenderEffect
            // CLAMP: the player's own bounds are the intended blur extent, so edges must stay
            // opaque rather than washing out to transparent.
            .createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
