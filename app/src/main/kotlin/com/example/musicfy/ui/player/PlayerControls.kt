// PlayerControls.kt

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.example.musicfy.ui.component.tapOrHold

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    Column(modifier = modifier.fillMaxWidth()) {

        Column(modifier = Modifier.offset(y = (-64).dp)) {
            Spacer(modifier = Modifier.height(20.dp))

            SongInfoRow(onTitlePositioned = onTitlePositioned)

            Spacer(modifier = Modifier.height(16.dp))

            PlayerProgressSlider()
        }

        Spacer(modifier = Modifier.height(24.dp))

        PlayerTransportRow()
    }
}

/** Holding next plays this much faster, holding previous this much slower, until let go. */
private const val HoldFastFactor = 2f
private const val HoldSlowFactor = 0.5f

/**
 * Temporary speed while a skip button is held. Relative to the speed the user set (and keeping
 * their pitch), and always put back exactly as it was on release.
 */
private class HoldSpeed(private val player: androidx.media3.exoplayer.ExoPlayer) {
    private var saved: androidx.media3.common.PlaybackParameters? = null

    fun start(factor: Float) {
        val base = saved ?: player.playbackParameters.also { saved = it }
        player.playbackParameters = androidx.media3.common.PlaybackParameters(
            (base.speed * factor).coerceIn(0.25f, 4f),
            base.pitch,
        )
    }

    fun end() {
        val base = saved ?: return
        saved = null
        player.playbackParameters = base
    }
}

@Composable
fun PlayerTransportRow(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val transportState by playerConnection.uiState.transportState.collectAsState()

    val holdSpeed = remember(playerConnection) { HoldSpeed(playerConnection.player) }
    // A hold that outlives the row (the player closed under the finger) still lets go.
    androidx.compose.runtime.DisposableEffect(holdSpeed) { onDispose { holdSpeed.end() } }
    val hold = remember(holdSpeed) {
        com.example.musicfy.ui.component.TransportHold(
            onPreviousHoldStart = { holdSpeed.start(HoldSlowFactor) },
            onNextHoldStart = { holdSpeed.start(HoldFastFactor) },
            onHoldEnd = holdSpeed::end,
        )
    }

    val buttonStyle by com.example.musicfy.utils.rememberEnumPreference(
        com.example.musicfy.constants.ButtonStyleKey,
        com.example.musicfy.ui.component.ButtonStyle.DEFAULT,
    )
    val artworkColor = com.example.musicfy.LocalArtworkColor.current
    val palette = remember(artworkColor) {
        com.example.musicfy.ui.theme.ArtworkPalette.from(artworkColor)
    }

    if (buttonStyle != com.example.musicfy.ui.component.ButtonStyle.DEFAULT) {
        com.example.musicfy.ui.component.PlayerTransportButtons(
            style = buttonStyle,
            isPlaying = transportState.isPlaying,
            canSkipPrevious = transportState.canSkipPrevious,
            canSkipNext = transportState.canSkipNext,
            onPrevious = playerConnection::seekToPrevious,
            onPlayPause = {
                if (transportState.playbackState == Player.STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.playWhenReady = !playerConnection.player.playWhenReady
                }
            },
            onNext = playerConnection::seekToNext,
            hold = hold,
            colors = com.example.musicfy.ui.component.TransportColors(
                accent = palette.accent,
                onAccent = palette.onAccent,
                container = palette.container,
                onContainer = palette.onContainer,
            ),
            modifier = modifier.padding(horizontal = PlayerHorizontalPadding),
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
    ) {
        // Fixed 74dp buttons look right at the default display size and shrink into the layout at
        // smaller ones, where the same dp covers a smaller share of a now-wider viewport. Sized
        // against the row instead, clamped so they never grow silly or drop below a comfortable
        // touch target.
        val buttonSize = (maxWidth * 0.21f).coerceIn(64.dp, 96.dp)
        val iconSize = buttonSize * 0.73f
        val gap = (maxWidth * 0.09f).coerceIn(22.dp, 40.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedPressScaleSkipButton(
                icon = R.drawable.avd_skip_previous,
                onClick = playerConnection::seekToPrevious,
                enabled = transportState.canSkipPrevious,
                tint = Color.White,
                iconSize = iconSize,
                onHoldStart = hold.onPreviousHoldStart,
                onHoldEnd = hold.onHoldEnd,
                holdLabel = "0.5\u00D7",
                holdPulseMs = 900L,
                modifier = Modifier.size(buttonSize)
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
                iconSize = iconSize,
                modifier = Modifier.size(buttonSize)
            )

            AnimatedPressScaleSkipButton(
                icon = R.drawable.avd_skip_next,
                onClick = playerConnection::seekToNext,
                enabled = transportState.canSkipNext,
                tint = Color.White,
                iconSize = iconSize,
                onHoldStart = hold.onNextHoldStart,
                onHoldEnd = hold.onHoldEnd,
                holdLabel = "2\u00D7",
                holdPulseMs = 420L,
                modifier = Modifier.size(buttonSize)
            )
        }
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
    /** Held: runs from the long press until the finger lifts. A tap still skips. */
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    /** Shown over the button while it's held, e.g. "2x". */
    holdLabel: String? = null,
    /** How often the arrow replays while held - quicker for faster. */
    holdPulseMs: Long = 600L,
) {
    var trigger by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleIconButton"
    )
    // The arrow keeps playing while held, so the button itself shows it's fast-forwarding.
    LaunchedEffect(holding) {
        while (holding) {
            trigger++
            kotlinx.coroutines.delay(holdPulseMs)
        }
    }
    val badge by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "holdBadge",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.36f
            }

            .then(
                if (containerColor == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(50)).background(containerColor)
                }
            )
            .tapOrHold(
                enabled = enabled,
                onTap = {
                    trigger++
                    onClick()
                },
                onHoldStart = onHoldStart?.let { start ->
                    {
                        holding = true
                        start()
                    }
                },
                onHoldEnd = {
                    holding = false
                    onHoldEnd?.invoke()
                },
                interactionSource = interactionSource,
            )
    ) {
        if (holdLabel != null && badge > 0.01f) {
            androidx.compose.material3.Text(
                text = holdLabel,
                color = Color.Black,
                fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = badge.coerceIn(0f, 1f)
                        val s = 0.6f + 0.4f * badge
                        scaleX = s
                        scaleY = s
                        translationY = -(10.dp.toPx() + 12.dp.toPx() * badge)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
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

                modifier = Modifier

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

private const val SkipIconAspect = 100f / 64f

private const val SkipIconWidthFactor = 2f
