// PlayerControls.kt
// Extracted from Player.kt's `controlsContent` closure: the transport buttons row
// (skip/play-pause/skip), the volume slider, and the connected-Bluetooth-device row.

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.media3.common.Player
import android.media.AudioManager
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.core.isBuds
import com.example.musicfy.core.isSpeaker
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.playback.CastConnectionHandler
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.ui.component.PlayerSliderTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlayerControls(
    isFullScreen: Boolean,
    useNewPlayerDesign: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    playerConnection: PlayerConnection,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    effectiveIsPlaying: Boolean,
    playbackState: Int,
    isCasting: Boolean,
    castIsPlaying: Boolean,
    castHandler: CastConnectionHandler?,
    castVolume: Float,
    systemVolume: Float,
    maxSystemVolume: Float,
    audioManager: AudioManager,
    bluetoothDeviceName: String?,
) {
    AnimatedVisibility(
        visible = !isFullScreen,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column {
            if (useNewPlayerDesign) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {
                    AnimatedPressScaleSkipButton(
                        icon = R.drawable.avd_skip_previous,
                        onClick = playerConnection::seekToPrevious,
                        enabled = canSkipPrevious,
                        tint = TextBackgroundColor,
                        iconSize = 54.dp,
                        modifier = Modifier.size(74.dp)
                    )

                    AnimatedPressScalePlayPauseButton(
                        isPlaying = effectiveIsPlaying,
                        playbackState = playbackState,
                        onClick = {
                            if (isCasting) {
                                if (castIsPlaying) {
                                    castHandler?.pause()
                                } else {
                                    castHandler?.play()
                                }
                            } else if (playbackState == Player.STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.togglePlayPause()
                            }
                        },
                        tint = TextBackgroundColor,
                        iconSize = 54.dp,
                        modifier = Modifier.size(74.dp)
                    )

                    AnimatedPressScaleSkipButton(
                        icon = R.drawable.avd_skip_next,
                        onClick = playerConnection::seekToNext,
                        enabled = canSkipNext,
                        tint = TextBackgroundColor,
                        iconSize = 54.dp,
                        modifier = Modifier.size(74.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedResizableSkipButton(
                            icon = R.drawable.avd_skip_previous,
                            enabled = canSkipPrevious,
                            color = TextBackgroundColor,
                            modifier =
                        Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                            .alpha(1f),
                            onClick = playerConnection::seekToPrevious,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier =
                        Modifier
                            .size(64.dp) // Adjusted to better match 48.dp side buttons visually
                            .clip(CircleShape)
                            .clickable {
                                if (isCasting) {
                                    if (castIsPlaying) {
                                        castHandler?.pause()
                                    } else {
                                        castHandler?.play()
                                    }
                                } else if (playbackState == Player.STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                    ) {
                        if (playbackState == Player.STATE_ENDED) {
                            Image(
                                painter = painterResource(R.drawable.replay),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(TextBackgroundColor),
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                            )
                        } else {
                            @OptIn(ExperimentalAnimationGraphicsApi::class)
                            val avd = AnimatedImageVector.animatedVectorResource(R.drawable.avd_play_to_pause)
                            @OptIn(ExperimentalAnimationGraphicsApi::class)
                            val painter = rememberAnimatedVectorPainter(avd, atEnd = effectiveIsPlaying)
                            Image(
                                painter = painter,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(TextBackgroundColor),
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedResizableSkipButton(
                            icon = R.drawable.avd_skip_next,
                            enabled = canSkipNext,
                            color = TextBackgroundColor,
                            modifier =
                        Modifier
                            .size(48.dp)
                            .align(Alignment.Center)
                            .alpha(1f),
                            onClick = playerConnection::seekToNext,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp)) //space between play and audio

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {
                    val volumeInteractionSource = remember { MutableInteractionSource() }
                    val isVolumeDragged by volumeInteractionSource.collectIsDraggedAsState()
                    val isVolumePressed by volumeInteractionSource.collectIsPressedAsState()
                    val isVolumeActive = isVolumeDragged || isVolumePressed

                    // Internal state to track drag value and avoid system feedback lag
                    var dragVolume by remember { mutableFloatStateOf(systemVolume) }

                    // Use a coroutine to update system volume to avoid UI blocking on fast swipes
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(systemVolume) {
                        if (!isVolumeActive) dragVolume = systemVolume
                    }

                    // Smoothly animate the volume position when changed via buttons
                    val animatedSystemVolume by animateFloatAsState(
                        targetValue = systemVolume,
                        animationSpec = tween(150, easing = LinearOutSlowInEasing),
                        label = "animatedSystemVolume"
                    )

                    val volume = if (isCasting) castVolume else {
                        if (isVolumeActive) dragVolume else animatedSystemVolume
                    }

                    val volumeTrackHeight by animateDpAsState(
                        targetValue = if (isVolumeActive) 16.dp else 10.dp,
                        animationSpec = spring(
                            dampingRatio = 0.7f, // Slightly more stable damping
                            stiffness = 600f // Balanced stiffness for high-speed stability
                        ),
                        label = "volumeTrackHeight"
                    )

                    val volumeIconScale by animateFloatAsState(
                        targetValue = if (isVolumeActive) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 600f
                        ),
                        label = "volumeIconScale"
                    )

                    Icon(
                        painter = painterResource(R.drawable.volume_mute),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                    )

                    Spacer(Modifier.width(12.dp))

                    Slider(
                        value = volume,
                        onValueChange = { newVolume ->
                            dragVolume = newVolume
                            if (isCasting) {
                                castHandler?.setVolume(newVolume)
                            } else {
                                // Non-blocking update to prevent "fast swipe" lag
                                scope.launch(Dispatchers.Default) {
                                    val newStep = (newVolume * maxSystemVolume).roundToInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStep, 0)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        interactionSource = volumeInteractionSource,
                        thumb = {},
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = textButtonColor.copy(alpha = 0.7f),
                                    inactiveTrackColor = textButtonColor.copy(alpha = 0.15f)
                                ),
                                trackHeight = volumeTrackHeight
                            )
                        }
                    )

                    Spacer(Modifier.width(12.dp))

                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                    )
                }

                var lastNonNullName by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(bluetoothDeviceName) {
                    if (bluetoothDeviceName != null) lastNonNullName = bluetoothDeviceName
                }

                AnimatedVisibility(
                    visible = !useNewPlayerDesign && bluetoothDeviceName != null,
                    enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                    exit = fadeOut(tween(400)) + shrinkVertically(tween(400)),
                    label = "BluetoothInfoVisibility"
                ) {
                    val nameToShow = bluetoothDeviceName ?: lastNonNullName
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(
                                    when {
                                        isSpeaker(nameToShow) -> R.drawable.speaker_applemusic
                                        isBuds(nameToShow) -> R.drawable.apple_airpods
                                        else -> R.drawable.apple_headset
                                    }
                                ),
                                contentDescription = null,
                                tint = textButtonColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(
                                    when {
                                        isSpeaker(nameToShow) -> 18.dp
                                        isBuds(nameToShow) -> 20.dp
                                        else -> 16.dp
                                    }
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = nameToShow ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = textButtonColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun AnimatedPressScaleSkipButton(
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    var trigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
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
                onClick = {
                    trigger++
                    onClick()
                }
            )
    ) {
        val avd = AnimatedImageVector.animatedVectorResource(icon)
        androidx.compose.runtime.key(trigger) {
            var atEnd by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (trigger > 0) atEnd = true
            }
            val painter = rememberAnimatedVectorPainter(avd, atEnd)
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(iconSize).scale(2.0f)
            )
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun AnimatedPressScalePlayPauseButton(
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

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AnimatedResizableSkipButton(
    icon: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    indication: Indication? = null,
    onClick: () -> Unit = {},
) {
    var trigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val avd = AnimatedImageVector.animatedVectorResource(icon)
    androidx.compose.runtime.key(trigger) {
        var atEnd by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (trigger > 0) atEnd = true
        }
        val painter = rememberAnimatedVectorPainter(avd, atEnd)
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = {
                        trigger++
                        onClick()
                    },
                )
                .alpha(if (enabled) 1f else 0.5f)
                .scale(2.0f),
        )
    }
}
