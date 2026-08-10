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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.ShowPlayerBottomCardKey
import com.example.musicfy.ui.component.BottomSheet
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.utils.rememberPreference
import kotlin.math.absoluteValue

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
        targetValue = if (lyricsImmersive) 1f else 0f,
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val progressProvider = remember(state) { { state.progress.coerceIn(0f, 1f) } }
        val horizontalOffsetProvider = remember(state) { { state.horizontalOffset } }

        BottomSheet(
            state = state,
            modifier = modifier,
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
            if (lyricsProgress > 0.001f) {
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
            if (showPlayerBottomCard && controlsHidden < 0.99f) {
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
            if (lyricsProgress < 0.999f) {
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
                        .clickable { showLyrics = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lyrics),
                        contentDescription = "Lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
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
                        if (controlsHidden > 0.99f) {
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
                    }
            ) {
                Column(modifier = Modifier.offset(y = (-64).dp)) {
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
        }
    }
}
