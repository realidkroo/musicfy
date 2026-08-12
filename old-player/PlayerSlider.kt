// PlayerSlider.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.constants.SliderStyle
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.CastConnectionHandler
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.PlayerSliderTrack
import com.example.musicfy.ui.component.SquigglySlider
import com.example.musicfy.ui.component.WavySlider
import com.example.musicfy.ui.menu.OldPlayerMenu
import com.example.musicfy.ui.theme.PlayerSliderColors
import com.example.musicfy.ui.utils.ShowMediaInfo
import com.example.musicfy.utils.makeTimeString

@Composable
fun PlayerSlider(
    useNewPlayerDesign: Boolean,
    newPlayerHeaderLift: Dp,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    effectivePosition: Long,
    duration: Long,
    isCasting: Boolean,
    castHandler: CastConnectionHandler?,
    onManualSeek: () -> Unit,
    onPositionChange: (Long) -> Unit,
    playerConnection: PlayerConnection,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    sliderStyle: SliderStyle,
    squigglySlider: Boolean,
    playerBackground: PlayerBackgroundStyle,
    useDarkTheme: Boolean,
    effectiveIsPlaying: Boolean,
    showAudioQualityBadge: Boolean,
    hideAudioQualityBadge: Boolean,
    sleepTimerEnabled: Boolean,
    onShowSleepTimerDialog: () -> Unit,
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    sleepTimerTimeLeft: Long,
    audioQuality: AudioQuality,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    fun handleSeekFinished() {
        sliderPosition?.let {
            if (isCasting) {
                castHandler?.seekTo(it)
                onManualSeek()
            } else {
                playerConnection.player.seekTo(it)
            }
            onPositionChange(it)
        }
        onSliderPositionChange(null)
    }

    if (useNewPlayerDesign) {
        val trackInteractionSource = remember { MutableInteractionSource() }
        val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
        val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
        val isTrackActive = isTrackDragged || isTrackPressed
        val trackHeight by animateDpAsState(
            targetValue = if (isTrackActive) 14.dp else 7.dp,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 520f
            ),
            label = "newPlayerTrackHeight"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .offset(y = newPlayerHeaderLift)
        ) {
            Slider(
                value = (sliderPosition ?: effectivePosition).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = {
                    onSliderPositionChange(it.toLong())
                },
                onValueChangeFinished = { handleSeekFinished() },
                enabled = true,
                interactionSource = trackInteractionSource,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        trackHeight = trackHeight,
                        colors = SliderDefaults.colors(
                            activeTrackColor = TextBackgroundColor,
                            inactiveTrackColor = TextBackgroundColor.copy(alpha = 0.24f)
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(28.dp)
                    .padding(horizontal = PlayerHorizontalPadding)
            )
        }
    } else when (sliderStyle) {
        SliderStyle.DEFAULT -> {
            Slider(
                value = (sliderPosition ?: effectivePosition).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = {
                    onSliderPositionChange(it.toLong())
                },
                onValueChangeFinished = { handleSeekFinished() },
                enabled = true,
                colors = PlayerSliderColors.getSliderColors(
                    activeColor = if (useNewPlayerDesign) textButtonColor else textButtonColor.copy(alpha = 0.7f),
                    playerBackground = playerBackground,
                    useDarkTheme = useDarkTheme
                ),
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
            )
        }

        SliderStyle.WAVY -> {
            if (squigglySlider) {
                SquigglySlider(
                    value = (sliderPosition ?: effectivePosition).toFloat(),
                    valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                    onValueChange = {
                        onSliderPositionChange(it.toLong())
                    },
                    onValueChangeFinished = { handleSeekFinished() },
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    colors = PlayerSliderColors.getSliderColors(
                        activeColor = if (useNewPlayerDesign) textButtonColor else textButtonColor.copy(alpha = 0.7f),
                        playerBackground = playerBackground,
                        useDarkTheme = useDarkTheme
                    ),
                    isPlaying = effectiveIsPlaying,
                )
            } else {
                WavySlider(
                    value = (sliderPosition ?: effectivePosition).toFloat(),
                    valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                    onValueChange = {
                        onSliderPositionChange(it.toLong())
                    },
                    onValueChangeFinished = { handleSeekFinished() },
                    colors = PlayerSliderColors.getSliderColors(
                        activeColor = if (useNewPlayerDesign) textButtonColor else textButtonColor.copy(alpha = 0.7f),
                        playerBackground = playerBackground,
                        useDarkTheme = useDarkTheme
                    ),
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    isPlaying = effectiveIsPlaying
                )
            }
        }

        SliderStyle.SLIM -> {
            val trackInteractionSource = remember { MutableInteractionSource() }
            val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
            val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
            val isTrackActive = (isTrackDragged || isTrackPressed) && !useNewPlayerDesign

            val trackHeight by animateDpAsState(
                targetValue = if (isTrackActive) 16.dp else 10.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "trackHeight"
            )

            Slider(
                value = (sliderPosition ?: effectivePosition).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = {
                    onSliderPositionChange(it.toLong())
                },
                onValueChangeFinished = { handleSeekFinished() },
                enabled = true,
                interactionSource = trackInteractionSource,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        trackHeight = trackHeight,
                        colors = PlayerSliderColors.getSliderColors(
                            activeColor = if (useNewPlayerDesign) textButtonColor else textButtonColor.copy(alpha = 0.7f),
                            playerBackground = playerBackground,
                            useDarkTheme = useDarkTheme
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )
        }
    }

    Spacer(Modifier.height(if (useNewPlayerDesign) 0.dp else 4.dp))

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp)
            .offset(y = newPlayerHeaderLift),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = makeTimeString(effectivePosition),
                style = MaterialTheme.typography.labelMedium,
                color = TextBackgroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = sliderPosition != null,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(140, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                ) + fadeIn(tween(90)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(110)
                ) + fadeOut(tween(90)),
            ) {
                Text(
                    text = sliderPosition?.let { " > ${makeTimeString(it)}" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!useNewPlayerDesign && ((showAudioQualityBadge && !hideAudioQualityBadge) || sleepTimerEnabled)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TextBackgroundColor.copy(alpha = 0.08f))
                    .border(
                        width = 0.5.dp,
                        color = TextBackgroundColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        if (sleepTimerEnabled) {
                            onShowSleepTimerDialog()
                        } else {
                            menuState.show {
                                OldPlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        mediaMetadata.id.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                AnimatedContent(
                    targetState = sleepTimerEnabled,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "QualityTimerSwitcher"
                ) { isTimerActive ->
                    if (isTimerActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sleep_timer),
                                contentDescription = null,
                                tint = TextBackgroundColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0)),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = TextBackgroundColor.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    } else if (!hideAudioQualityBadge) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "QualityIconTransition")
                            val animatedRotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "QualityIconRotation"
                            )

                            val iconBrush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    TextBackgroundColor.copy(alpha = 1.0f),
                                    Color.Transparent
                                )
                            )

                            Icon(
                                painter = painterResource(R.drawable.stream_old_player),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(12.dp)
                                    .graphicsLayer(alpha = 0.99f)
                                    .drawWithCache {
                                        onDrawWithContent {
                                            drawContent()
                                            rotate(animatedRotation) {
                                                drawRect(iconBrush, blendMode = BlendMode.SrcIn)
                                            }
                                        }
                                    }
                            )
                            Text(
                                text = when (audioQuality) {
                                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                                    AudioQuality.LOSSLESS -> "Lossless"
                                    AudioQuality.HI_RES_LOSSLESS -> "Hi-Res"
                                }.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = TextBackgroundColor.copy(alpha = 0.8f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
            style = MaterialTheme.typography.labelMedium,
            color = TextBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
