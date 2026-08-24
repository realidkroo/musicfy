// BottomSheetPlayer.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.media3.common.Player
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.extensions.toggleRepeatMode
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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
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

    var showActionMenu by remember { mutableStateOf(false) }
    // Which surface opened the menu. The lyrics tools only appear when it came from the lyrics
    // page, where they have something visible to act on.
    var menuFromLyrics by remember { mutableStateOf(false) }

    val menuReveal = remember { mutableFloatStateOf(0f) }

    var showEditHint by remember { mutableStateOf(false) }
    val coverStyle by rememberEnumPreference(PlayerCoverStyleKey, PlayerCoverStyle.EDGE_TO_EDGE)
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
                    onOpenMenu = {
                        menuFromLyrics = true
                        showActionMenu = true
                    },
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 20.dp)
                        .size(40.dp)

                        .graphicsLayer { alpha = (1f - lyricsProgress / 0.35f).coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable {
                            menuFromLyrics = false
                            showActionMenu = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        tint = Color.White,

                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
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
                            val repeatMode = transportState.repeatMode
                            PressScaleActionButton(
                                icon = R.drawable.repeat,
                                boldIcon = true,
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.85f),
                                containerColor = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                                badgeText = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                                onClick = { playerConnection.player.toggleRepeatMode() },
                            )
                            PressScaleActionButton(
                                icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                                tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                                containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                                onClick = playerConnection::toggleLike,
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

        if (showActionMenu) {
            PlayerActionMenu(
                fromLyrics = menuFromLyrics,
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

        if (editPhase == PlayerEditPhase.ENTERING) {
            PlayerEnteringEditOverlay(
                onFinished = {

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

private const val EnterBlurQuantPx = 6f

private val EnterBlurCache = HashMap<Int, androidx.compose.ui.graphics.RenderEffect>()

private fun enterBlurEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || radius <= 0.5f) {
        return null
    }
    val step = (radius / EnterBlurQuantPx).roundToInt().coerceAtLeast(1)
    return EnterBlurCache.getOrPut(step) {
        val r = step * EnterBlurQuantPx
        android.graphics.RenderEffect

            .createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
