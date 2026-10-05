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

    val morphingGlassState = remember { GlassState() }
    var showLyrics by remember { mutableStateOf(false) }
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
        if (editPhase != PlayerEditPhase.NONE) showLyrics = false
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
            showSleepTimer = false
        }
    }

    LaunchedEffect(state.isExpanded) {
        if (!state.isExpanded && showLyrics) {
            showLyrics = false
        }
    }

    val isSheetInTransition by remember(state) {
        derivedStateOf { !state.isExpanded && !state.isCollapsed && !state.isDismissed }
    }

    var songInfoSourceRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var chevronButtonRect by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    var controlsInset by remember { mutableStateOf(220.dp) }

    /**
     * Distance from the bottom of the seek bar to the bottom of the screen.
     *
     * The lyrics list is inset by this so its bottom fade lands *below* the seek bar. Insetting
     * by the whole controls block instead put the fade up on the repeat/like buttons.
     */
    var lyricsBottomInset by remember { mutableStateOf(120.dp) }

    var lyricsImmersive by remember { mutableStateOf(false) }
    val controlsHidden by animateFloatAsState(

        targetValue = if (lyricsImmersive || editPhase == PlayerEditPhase.CUSTOMIZING) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsControlsHide",
    )

    val lyricsProgress by animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,

        animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)),
        label = "lyricsCoverMorph",
    )

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
                    onArtBoundsChanged = { coverArtRect = it },
                )
                MorphingSongInfo(
                    trackInfo = trackInfo,
                    lyricsProgressProvider = { lyricsProgress },
                    sourceRectProvider = { songInfoSourceRect },

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

            if (lyricsMounted) {
                LyricsScreen(
                    onClose = { showLyrics = false },
                    screenHeight = screenHeight,

                    contentBottomInset = lyricsBottomInset,
                    onImmersiveChange = { lyricsImmersive = it },
                    isSheetDragging = isSheetInTransition,
                    isMorphing = lyricsMorphing,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = lyricsProgress
                            alpha = (p / 0.45f).coerceIn(0f, 1f)
                            val s = 0.88f + 0.12f * p
                            scaleX = s
                            scaleY = s

                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.86f)
                            translationY = (1f - p) * size.height * 0.10f
                        },
                )
            }

            if (showPlayerBottomCard && deckMounted) {
                PlayerBottomCardStack(
                    glassState = morphingGlassState,
                    progressProvider = progressProvider,
                    onOpenLyrics = { showLyrics = true },

                    onOpenQueue = { playerConnection.player.seekToNext() },
                    onLongPress = { editPhase = PlayerEditPhase.STYLE_SELECT },
                    lyricsProgressProvider = { lyricsProgress },

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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = (screenHeight * 0.19f) - 37.dp)
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
                        modifier = Modifier.graphicsLayer { alpha = 1f - lyricsProgress }
                    ) {
                        SongInfoRow(
                            onTitlePositioned = { songInfoSourceRect = it },
                            onChevronPositioned = { chevronButtonRect = it },
                            isExpandMenuOpen = showExpandMenu,
                            onToggleExpandMenu = { showExpandMenu = !showExpandMenu }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Repeat + like live here while the lyrics are up: the song info row they
                    // normally sit in is faded out, so they stack above the progress bar instead.
                    // Height tracks the lyrics transition so the controls don't jump when it opens.
                    if (lyricsMounted) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                                .padding(bottom = 12.dp * lyricsProgress)
                                .height(75.dp * lyricsProgress)
                                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                                .graphicsLayer { alpha = lyricsProgress },
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            PressScaleActionButton(
                                icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                                tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                                containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                                onClick = playerConnection::toggleLike,
                                hasShadow = false,
                            )
                            // On the lyrics page this slot is the options button, not the
                            // chevron: the lyrics tools live in that menu, so it sits where the
                            // thumb already is instead of up in the top-right corner. It fades in
                            // with the lyrics, right where the chevron fades out.
                            PressScaleActionButton(
                                icon = R.drawable.more_horiz,
                                tint = Color.White.copy(alpha = 0.90f),
                                containerColor = Color.White.copy(alpha = 0.15f),
                                onClick = {
                                    menuFromLyrics = true
                                    showActionMenu = true
                                },
                                hasShadow = false,
                            )
                        }
                    }

                    // Column, not Box: PlayerProgressSlider emits the track and the timestamp
                    // row as two siblings, so a Box would stack the timestamps on top of the
                    // seek bar instead of below it.
                    Column(
                        modifier = Modifier
                            .offset(y = -SeekBarLift)
                            .onGloballyPositioned { coords ->
                                val rootHeight = coords.findRootCoordinates().size.height
                                val bottomY = coords.positionInRoot().y + coords.size.height
                                val insetPx = (rootHeight - bottomY).coerceAtLeast(0f)
                                val inset = with(density) { insetPx.toDp() }
                                if ((inset - lyricsBottomInset).value.absoluteValue > 0.5f) {
                                    lyricsBottomInset = inset
                                }
                            }
                    ) {
                        PlayerProgressSlider()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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

        ExpandMenuOverlay(
            visible = showExpandMenu,
            onDismiss = { showExpandMenu = false },
            onOtherMenuClick = {
                menuFromLyrics = false
                showActionMenu = true
            },
            onSleepTimerClick = { showSleepTimer = true },
            sleepTimerActive = playerConnection.service.sleepTimer.isActive,
            seeVideo = seeVideo,
            onSeeVideoToggle = { seeVideo = !seeVideo },
            onRepeatClick = { playerConnection.player.toggleRepeatMode() },
            repeatMode = transportState.repeatMode,
            chevronRect = chevronButtonRect,
            screenHeight = screenHeight,
            controlsDrawShift = controlsDrawShift,
        )

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
                    .padding(end = PlayerHorizontalPadding, bottom = menuBottomDp),
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
                                .graphicsLayer {
                                    val progress = p.coerceIn(0f, 1.25f)
                                    alpha = progress.coerceIn(0f, 1f)
                                    translationY = (1f - progress) * 55f
                                    val scale = (0.7f + 0.3f * progress).coerceAtLeast(0.01f)
                                    scaleX = scale
                                    scaleY = scale
                                    val blurPx = (1f - progress.coerceIn(0f, 1f)) * 20f
                                    renderEffect = BlurEffectCache.get(blurPx)
                                }
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
                    .padding(end = PlayerHorizontalPadding, bottom = chevronBottomDp)
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
