// PlayerControls.kt
// v0 minimal replacement for the old 545-line extraction: just the fullscreen transport row
// (previous / play-pause / next). No volume, no Bluetooth row, no cast branching, no old-design
// variant. Old file kept for reference at /old-player/PlayerControls.kt.

package com.example.musicfy.ui.player

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.extensions.togglePlayPause

/**
 * Fullscreen player controls: title/artist + like/repeat, seek slider + time, then the
 * previous / play-pause / next transport row. Each row reads its own scoped slice of
 * [com.example.musicfy.ui.player.PlayerUiState] rather than a dozen pre-collected params from a
 * root composable — [PlayerProgressSlider] in particular collects the 15Hz position ticker
 * itself, so that recomposition rate stays local to it instead of the whole column.
 */
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        // Shifted up via offset (a draw-time transform), not extra layout space — offset()
        // doesn't change how much room this group reserves in the Column, so the transport row
        // below stays exactly where it was; only this group's drawn position moves, up into the
        // cover art rather than leaving a dead gap below it.
        Column(modifier = Modifier.offset(y = (-64).dp)) {
            Spacer(modifier = Modifier.height(20.dp))

            SongInfoRow(onTitlePositioned = onTitlePositioned)

            Spacer(modifier = Modifier.height(16.dp))

            PlayerProgressSlider()
        }

        // Breathing room before the transport row — independent of the offset above.
        Spacer(modifier = Modifier.height(24.dp))

        PlayerTransportRow()
    }
}

/**
 * Previous / play-pause / next — the ONE definition of this row's sizing and spacing. Used to
 * exist a second time, hand-duplicated inside LyricsScreen with different numbers (56dp buttons
 * instead of 74dp, 24dp horizontal padding instead of PlayerHorizontalPadding's 32dp) that had
 * drifted from this one — re-matching those numbers by hand once already turned out to be
 * error-prone (missed the padding difference), so this is now the single source both screens
 * call, which makes another drift structurally impossible rather than just less likely.
 */
@Composable
fun PlayerTransportRow(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val transportState by playerConnection.uiState.transportState.collectAsState()

    Row(
        horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
    ) {
        AnimatedPressScaleSkipButton(
            icon = R.drawable.avd_skip_previous,
            onClick = playerConnection::seekToPrevious,
            enabled = transportState.canSkipPrevious,
            tint = Color.White,
            iconSize = 54.dp,
            modifier = Modifier.size(74.dp)
        )

        AnimatedPressScalePlayPauseButton(
            isPlaying = transportState.isPlaying,
            playbackState = transportState.playbackState,
            onClick = {
                if (transportState.playbackState == Player.STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.togglePlayPause()
                }
            },
            tint = Color.White,
            iconSize = 54.dp,
            modifier = Modifier.size(74.dp)
        )

        AnimatedPressScaleSkipButton(
            icon = R.drawable.avd_skip_next,
            onClick = playerConnection::seekToNext,
            enabled = transportState.canSkipNext,
            tint = Color.White,
            iconSize = 54.dp,
            modifier = Modifier.size(74.dp)
        )
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
internal fun AnimatedPressScaleSkipButton(
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    var trigger by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleIconButton"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.36f
            }
            // Clip and fill ONLY when there is actually a container to draw. These buttons are
            // called with a transparent container, so the rounded clip was shaping nothing while
            // still cropping every glyph wider than the button — which is what cut the skip icons.
            .then(
                if (containerColor == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(50)).background(containerColor)
                }
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = {
                    trigger++
                    onClick()
                }
            )
    ) {
        val avd = AnimatedImageVector.animatedVectorResource(icon)
        key(trigger) {
            var atEnd by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (trigger > 0) atEnd = true
            }
            val painter = rememberAnimatedVectorPainter(avd, atEnd)
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                // The skip vectors are 100x64, NOT square. Drawing them into a square
                // size(iconSize) box letterboxed them, and the .scale(2f) that followed then blew
                // the result out to roughly 108x69 — well past the 74dp rounded-rect clip on the
                // Box above, which sheared the leading and trailing triangles off exactly while
                // the animation slid them outward. That is the "cut".
                //
                // Sized by its real aspect instead, small enough to sit inside the clip with room
                // for the animation's own travel, and with no post-hoc scale — a vector asked for
                // the size it will actually occupy rasterises crisply, where a magnified one does
                // not.
                modifier = Modifier
                    // requiredWidth, NOT width: the parent Box is size(74.dp), and a plain
                    // width() is still clamped by the incoming max constraint, so asking for
                    // 108dp there silently produced 74dp — smaller than the original. The old
                    // .scale(2f) never hit this because a draw-time scale bypasses layout
                    // entirely. requiredWidth ignores the parent's constraint the same way,
                    // while still being a real layout size so the vector rasterises sharp.
                    .requiredWidth(iconSize * SkipIconWidthFactor)
                    .aspectRatio(SkipIconAspect)
            )
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
internal fun AnimatedPressScalePlayPauseButton(
    isPlaying: Boolean,
    playbackState: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleIconButton"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.36f
            }
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            )
    ) {
        if (playbackState == Player.STATE_ENDED) {
            Image(
                painter = painterResource(R.drawable.replay),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(iconSize)
            )
        } else {
            val avd = AnimatedImageVector.animatedVectorResource(R.drawable.avd_play_to_pause)
            val painter = rememberAnimatedVectorPainter(avd, atEnd = isPlaying)
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Native aspect of avd_skip_next / avd_skip_previous (100x64 viewport).
 *
 * Kept as a constant because it is a property of the assets, not a design choice: forcing these
 * into a square box is what letterboxed them before.
 */
private const val SkipIconAspect = 100f / 64f

/**
 * Skip glyph width as a multiple of the caller's iconSize.
 *
 * 2x reproduces exactly the size the old `.size(iconSize).scale(2f)` produced (108x69dp at the
 * transport row's 54dp), because that is the size the design wants. The difference is that it is
 * now the glyph's real layout size rather than a magnified small one, so it rasterises sharp — and
 * the button no longer clips it (see below), so the animation's outward slide stays visible
 * instead of being sheared off at the button's edge.
 */
private const val SkipIconWidthFactor = 2f
