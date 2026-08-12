// bottomsheetplayerkt
// v0 minimal orchestrator replacing the ~2700 line bottomsheetplayer that
// playerkt now at old player playerkt same call signature as before so
// needed no changes only collects the two scoped flows it actually needs
// transportstate instead of ~17 flows at root

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

// how far the title slider sub column is drawn above its own layout position
private val ControlsDrawShift = 64.dp

// peak blur radius in px of the player behind the entering edit mode message
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

    // hoisted so both sharedcontent morphingcover which registers this as a
    // and content seamblur which reads that same captured content can share
    val morphingGlassState = remember { GlassState() }
    var showLyrics by remember { mutableStateOf(false) }
    val (showPlayerBottomCard) = rememberPreference(ShowPlayerBottomCardKey, defaultValue = true)

    // player customization none is the state the player is in essentially
    // are only reachable by long pressing the artwork
    var editPhase by remember { mutableStateOf(PlayerEditPhase.NONE) }
    // the ⋯ button now occupies the slot the lyrics shortcut used to lyrics are
    // from the bottom card deck which is the primary way in anyway
    var showActionMenu by remember { mutableStateOf(false) }
    // 01 of the menu s own open animation reported back so the player can scale
    // it the way the app s other popups do a plain float read in the draw phase
    // player is not recomposed on every frame of the sheet sliding up
    val menuReveal = remember { mutableFloatStateOf(0f) }
    // set when the customization page is opened from the menu rather than by
    // artwork so it can tell you the gesture exists
    var showEditHint by remember { mutableStateOf(false) }
    val coverStyle by rememberEnumPreference(PlayerCoverStyleKey, PlayerCoverStyle.EDGE_TO_EDGE)
    val backgroundStyle by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        PlayerBackgroundStyle.COVER_GRADIENT,
    )
    // rects of the three editable regions captured live so the overlay can
    // they really are rather than reproducing this file s nested layout
    var coverArtRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var controlsRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var bottomCardRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // the lyrics page and the editor both want the whole screen entering one
    LaunchedEffect(editPhase) {
        if (editPhase != PlayerEditPhase.NONE) showLyrics = false
    }

    // blur applied to the entire player while the entering edit mode beat
    // animatable and read inside a graphicslayer draw phase rather than as a
    // float so ramping it costs redraws instead of a recomposition of the player
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
    // boolean so this only recomposes when the layer needs attaching or
    // per frame of the ramp the layer must outlive the entering phase itself to
    // animate back down instead of snapping off
    val enterBlurActive by remember {
        derivedStateOf { editPhase == PlayerEditPhase.ENTERING || enterBlur.value > 0.01f }
    }
    // collapsing or dismissing the player while editing must not leave the
    // top of the mini pill
    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded) {
            editPhase = PlayerEditPhase.NONE
            // the menu has to go with it and its zoom out has to be released left set
            // player would stay scaled down after collapsing which is the
            // state you get when switching away with the sheet open
            showActionMenu = false
            menuReveal.floatValue = 0f
        }
    }

    // automatically transition out of lyrics mode when the user swipes down to
    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded && showLyrics) {
            showLyrics = false
        }
    }

    val isSheetInTransition by remember(state) {
        derivedStateOf { !state.isExpanded && !state.isCollapsed && !state.isDismissed }
    }

    // last real on screen position of songinforow s title+artist for
    // from keeps its value after songinforow unmounts which happens the instant
    // flips since it s behind a hard if else below see songinforow s
    var songInfoSourceRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // height of the shared slider + transport block measured from the bottom of
    // seeded with a rough estimate purely so the first frame isn t wildly wrong
    // lands on the first layout pass
    var controlsInset by remember { mutableStateOf(220.dp) }

    // set by lyricsscreen once it has been idle on the highlighted line slides
    // shared transport block away so the lyrics own the full page apple music
    var lyricsImmersive by remember { mutableStateOf(false) }
    val controlsHidden by animateFloatAsState(
        // only the customization page hides the chrome the part selection layer
        // leaves the controls and the card deck on screen they are two of the
        // can point at so they have to be visible to be pointed at
        targetValue = if (lyricsImmersive || editPhase == PlayerEditPhase.CUSTOMIZING) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsControlsHide",
    )

    // drives the shared cover shrinking into the lyrics page s header slot
    // a hard boolean so the artwork travels between the two positions instead of
    val lyricsProgress by animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,
        // cubic bezier 05 045 0 1 the same curve used for the settings title morph
        animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)),
        label = "lyricsCoverMorph",
    )

    // mount unmount gates for everything below as booleans

    // these conditions used to read the animating floats directly in composition
    // if lyricsprogress > 0001f if controlshidden < 099f which
    // content lambda to them every frame of the 520ms lyrics morph and the
    // rebuilt the entire player subtree morphingcover seamblur the lyrics page
    // and the transport block on top of the drawing those same frames were
    // is the stall when expanding the player and moving into lyrics the work is
    // recomposition per frame not the animations themselves

    // wrapped in derivedstateof the recomposition happens on the two frames
    // actually flips and the continuous values stay where they belong read
    // blocks in the draw phase same reasoning and the same fix as
    val lyricsMounted by remember { derivedStateOf { lyricsProgress > 0.001f } }
    // strictly in flight not settled open not settled closed
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

        // black plate behind the player revealed as the menu scales it down without
        // margin the zoom out opens up is simply transparent and you see straight
        // whatever is behind the player the same reason zoomoutpopupcontainer
        // root black

        // alpha is a draw phase read of menureveal so this costs nothing while the
        // closed and it carries no pointer input so it never intercepts a touch
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = menuReveal.floatValue }
                .background(Color.Black)
        )

        BottomSheet(
            state = state,
            // the blur goes on the sheet as a whole artwork controls and card deck
            // which is why it is attached here rather than inside the content slot the
            // lives in bottomsheet s sharedcontent a sibling of that slot so nothing
            // content could have blurred it the overlay that reads on top is composed
            // down as a sibling of this call outside the blurred subtree so its text
            // sharp
            modifier = modifier
                .graphicsLayer {
                    // zoom out behind the sheet matching zoomoutpopupcontainer elsewhere in the
                    // app identity while the menu is closed
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
            // freezes the sheet s own drag handling for as long as the editor is up that
            // handler lives on an ancestor of everything the editor draws so without
            // swipe down collapsed the player and a swipe left ran the song change
            // both of which tore the editor off the screen mid interaction
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
                    // same statusbartop + 28dp top inset as the cover nudged down so the
                    // bigger 18sp 14sp two line text block sits roughly centred against the
                    // 60dp cover rather than flush with its top edge
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
            // always composed now not gated inside the else branch it used to
            // the instant showlyrics flipped true and hard remount just as abruptly on
            // which read as the blur band cutting instead of fading and since it
            // at the seam next to the cover as a flicker around the cover s corner
            // when closing its own alpha now carries a 1 lyricsprogress factor so it
            // out in step with the cover shrinking away and back in as the cover returns
            // instead of popping either direction
            SeamBlur(
                glassState = morphingGlassState,
                progressProvider = progressProvider,
                trackInfo = trackInfo,
                maxHeight = screenHeight,
                // gone by the time the lyrics page is open the band is there to blend the
                // edge of the cover art into the controls on the lyrics page the cover has
                // so all that remains is a dark gradient bar behind the timestamp row
                fadeProvider = { 1f - lyricsProgress },
            )
            // lyrics page composed for the whole morph not just at the end of it so the
            // can grow into place from the card instead of appearing once the animation

            // it rises from the deck s own position and scales up out of it
            // it from where the card sits up to its resting place and the scale starts
            // card s width ratio so the first thing you see is genuinely the card s
            // widening into the page alpha ramps in over the first ~45% so it is already
            // well before the motion settles
            if (lyricsMounted) {
                LyricsScreen(
                    onClose = { showLyrics = false },
                    screenHeight = screenHeight,
                    // measured not derived from the 019f formula the lyrics list has to stop
                    // exactly at the top of the slider timestamp row and that row s y is the
                    // product of an offset 64dp plus several spacers plus the transport row s
                    // intrinsic height reproducing that arithmetic here is what previously put
                    // the fade in the wrong place and let lyrics run behind the controls
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
                            // grow out of the deck the card sits near the bottom so the
                            // expansion origin is down there rather than the screen s centre
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.86f)
                            translationY = (1f - p) * size.height * 0.10f
                        },
                )
            }

            // the deck is not dismissed on the lyrics page it stays at the same size and
            // and the queue card rides forward into it see lyricsprogressprovider
            // lyrics card is the one that hands over as the queue takes the front slot
            // lyrics card s own frontness drops to 0 and deckcard fades its content out
            // the expanding into the page half of the transition
            // composed out once the immersive fade completes not merely faded the deck
            // the lyrics list and an alpha 0 card still hit tests so leaving it mounted
            // taps and scrolls along the bottom of the page
            if (showPlayerBottomCard && deckMounted) {
                PlayerBottomCardStack(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    onOpenLyrics = { showLyrics = true },
                    // no dedicated full queue screen exists yet so tapping the queue card
                    // skips to the track it s previewing rather than doing nothing
                    onOpenQueue = { playerConnection.player.seekToNext() },
                    lyricsProgressProvider = { lyricsProgress },
                    // flush to the bottom edge no navigationbarspadding no vertical
                    // padding so the deck reads as attached rather than floating
                    // playerbottomcardstack handles keeping its own content clear of the
                    // gesture bar internally
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // measured outside the horizontal padding so the outline spans the deck s
                        // full width rather than hugging the card s inset body
                        .onGloballyPositioned { bottomCardRect = it.boundsInRoot() }
                        .padding(horizontal = 26.dp)
                        // retreats off the bottom edge with the transport block on the same
                        // curve so the whole chrome leaves as one movement
                        .graphicsLayer {
                            alpha = 1f - controlsHidden
                            translationY = controlsHidden * size.height * 0.75f
                        }
                )
            }

            // composed only while the player is showing an alpha 0 button left in the
            // would still be hit testable and would swallow taps meant for the lyrics
            if (lyricsButtonMounted && editPhase == PlayerEditPhase.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 20.dp)
                        .size(40.dp)
                        // fades out early in the morph rather than sitting on top of the opening
                        // lyrics page the page s own header owns that corner from here on
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
                        // same glyph the lyrics page uses turned horizontal
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
            }

            // the one progress slider + transport row for both the player and the lyrics

            // there used to be two playercontrols owned a copy lyricsscreen built its
            // an animatedvisibility with slideinvertically initialoffsety = it 3
            // lyrics unmounted the first and slid the second up from a third of the
            // controls and the timestamp bar visibly travelled and briefly doubled
            // staying put declaring them here outside the if else means the exact same
            // stay mounted across the transition so there is nothing left to move or

            // declared after the branch so it draws above the lyrics list the list
            // it which is what the top bottom fades are for

            // layout is copied verbatim from the old playercontrols including the 64dp
            // trick a draw time shift that deliberately reserves no layout space so the
            // row s position is unaffected by it only the title block fades since
            // takes the title over to the lyrics header
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = (screenHeight * 0.19f) - 37.dp)
                    .graphicsLayer {
                        alpha = 1f - controlsHidden
                        // slides down as it fades rather than dissolving in place so it reads as
                        // retreating off the bottom edge and coming back from it
                        translationY = controlsHidden * size.height * 0.55f
                    }
                    // a faded out block still hit tests and it sits above the lyrics list so
                    // without this the invisible transport row keeps swallowing taps and scrolls
                    // in the lower half of the page alpha graphicslayer do not affect hit
                    // testing the events have to be consumed explicitly
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
                    // publishes how much vertical room this block occupies from the bottom of the
                    // sheet so the lyrics list can end precisely where the timestamp begins
                    .onGloballyPositioned { coords ->
                        val parentHeight = coords.parentLayoutCoordinates?.size?.height ?: return@onGloballyPositioned
                        val topY = coords.positionInParent().y
                        val insetPx = (parentHeight - topY).coerceAtLeast(0f)
                        val inset = with(density) { insetPx.toDp() }
                        if ((inset - controlsInset).value.absoluteValue > 0.5f) {
                            controlsInset = inset
                        }

                        // same node second purpose the edit overlay s outline for this block
                        // the title slider sub column is drawn controlsdrawshift higher than this
                        // node s own top see the offset below which reserves no layout space
                        // so the measured rect has to be raised by the identical amount or the
                        // outline sits low over the transport row
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

            // customization layers declared last so they draw above everything else both
            // composed out entirely when not in use an alpha 0 full screen overlay
            // hit test and swallow every gesture the player depends on
            if (editPhase == PlayerEditPhase.SELECTING) {
                PlayerEditOverlay(
                    coverRect = coverArtRect,
                    controlsRect = controlsRect,
                    bottomCardRect = bottomCardRect,
                    onSelect = { target ->
                        // only the cover leads anywhere yet the other two targets are drawn and
                        // routed so the text controls editor can be added without reopening this
                        // file but they intentionally do nothing for now
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
                    // back goes to the part picker when that is where this page came from opened
                    // straight from the menu there is no picker behind it so back leaves
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

        // outside the bottomsheet call on purpose everything above is inside the
        // entering blur is applied to and this message has to stay sharp over it
        if (editPhase == PlayerEditPhase.ENTERING) {
            PlayerEnteringEditOverlay(
                onFinished = {
                    // guarded a long press that got cancelled or the player collapsing can
                    // move the phase on before this timer fires and it must not drag the user
                    // back into the editor after that
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

// quantisation grid for the edit mode blur in px
private const val EnterBlurQuantPx = 6f

// cached blur effects for the entering edit mode ramp keyed by quantised radius
private val EnterBlurCache = HashMap<Int, androidx.compose.ui.graphics.RenderEffect>()

private fun enterBlurEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || radius <= 0.5f) {
        return null
    }
    val step = (radius / EnterBlurQuantPx).roundToInt().coerceAtLeast(1)
    return EnterBlurCache.getOrPut(step) {
        val r = step * EnterBlurQuantPx
        android.graphics.RenderEffect
            // clamp the player s own bounds are the intended blur extent so edges must
            // opaque rather than washing out to transparent
            .createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
