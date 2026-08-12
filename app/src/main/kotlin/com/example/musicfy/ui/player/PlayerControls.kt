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
