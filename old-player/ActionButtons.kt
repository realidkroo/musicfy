// actionbuttons kt
// extracted from player kt s controlscontent closure the repeat like new design
// or more like old design action button group plus the shared pressscaleiconbutton

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toggleRepeatMode
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.menu.OldPlayerMenu
import com.example.musicfy.ui.utils.ShowMediaInfo
import com.example.musicfy.ui.utils.ShowOffsetDialog

// the repeat like new design or more like old design action button group a direct sibling of songinfo inside player kt s header row
@Composable
fun RowScope.ActionButtons(
    useNewPlayerDesign: Boolean,
    showInlineLyrics: Boolean,
    mediaMetadata: MediaMetadata,
    currentSong: Song?,
    currentLyrics: LyricsEntity?,
    repeatMode: Int,
    textButtonColor: Color,
    TextBackgroundColor: Color,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    if (useNewPlayerDesign) {
        val liked = currentSong?.song?.liked == true
        val lyricsOptionsAction = {
            menuState.show {
                com.example.musicfy.ui.menu.LyricsMenu(
                    lyricsProvider = { currentLyrics },
                    songProvider = { currentSong?.song },
                    mediaMetadataProvider = { mediaMetadata },
                    onDismiss = menuState::dismiss,
                    onShowOffsetDialog = {
                        bottomSheetPageState.show {
                            ShowOffsetDialog(songProvider = { currentSong?.song })
                        }
                    }
                )
            }
        }
        val actionsContent: @Composable () -> Unit = {
            if (showInlineLyrics) {
                PressScaleIconButton(
                    icon = R.drawable.more_horiz,
                    tint = TextBackgroundColor,
                    containerColor = TextBackgroundColor.copy(alpha = 0.18f),
                    iconSize = 18.dp,
                    modifier = Modifier.size(30.dp),
                    onClick = lyricsOptionsAction
                )
            } else {
                PressScaleIconButton(
                    icon = R.drawable.repeat,
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                        TextBackgroundColor.copy(alpha = 0.58f)
                    else TextBackgroundColor,
                    containerColor = if (repeatMode != Player.REPEAT_MODE_OFF)
                        TextBackgroundColor.copy(alpha = 0.34f)
                    else TextBackgroundColor.copy(alpha = 0.18f),
                    iconSize = 19.dp,
                    boldIcon = true,
                    badgeText = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                    badgeColor = TextBackgroundColor,
                    modifier = Modifier.size(30.dp),
                    onClick = { playerConnection.player.toggleRepeatMode() }
                )
            }

            PressScaleIconButton(
                icon = if (liked)
                    R.drawable.ic_untitled_heart
                else R.drawable.ic_untitled_heart_unfill,
                tint = if (liked) TextBackgroundColor.copy(alpha = 0.58f) else TextBackgroundColor,
                containerColor = if (liked)
                    TextBackgroundColor.copy(alpha = 0.34f)
                else TextBackgroundColor.copy(alpha = 0.18f),
                iconSize = 18.dp,
                modifier = Modifier.size(30.dp),
                onClick = playerConnection::toggleLike
            )
        }
        if (showInlineLyrics) {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                actionsContent()
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actionsContent()
            }
        }
    } else {
        AnimatedContent(targetState = showInlineLyrics, label = "DownloadButton") { showLyrics ->
            if (showLyrics) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor.copy(alpha = 0.2f))
                        .clickable { onToggleFullScreen() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.fullscreen),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor.copy(alpha = 0.2f))
                        .clickable {
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
                        },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
            if (showLyrics) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor.copy(alpha = 0.2f))
                        .clickable {
                            menuState.show {
                                com.example.musicfy.ui.menu.LyricsMenu(
                                    lyricsProvider = { currentLyrics },
                                    songProvider = { currentSong?.song },
                                    mediaMetadataProvider = { mediaMetadata },
                                    onDismiss = menuState::dismiss,
                                    onShowOffsetDialog = {
                                        bottomSheetPageState.show {
                                            ShowOffsetDialog(
                                                songProvider = { currentSong?.song }
                                            )
                                        }
                                    }
                                )
                            }
                        },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor.copy(alpha = 0.2f))
                        .clickable(onClick = playerConnection::toggleLike),
                ) {
                    Icon(
                        painter = painterResource(
                            if (currentSong?.song?.liked == true)
                                R.drawable.ic_untitled_heart
                            else R.drawable.ic_untitled_heart_unfill
                        ),
                        contentDescription = null,
                        tint = textButtonColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PressScaleIconButton(
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    containerColor: Color = Color.Transparent,
    enabled: Boolean = true,
    boldIcon: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color = tint,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = 0.54f,
            stiffness = 720f
        ),
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
        if (boldIcon) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = 0.45.dp)
            )
        }
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize)
        )
        if (badgeText != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 3.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor)
            ) {
                Text(
                    text = badgeText,
                    color = MaterialTheme.colorScheme.surface,
                    fontSize = 6.5.sp,
                    lineHeight = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
