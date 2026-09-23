// SongInfoRow.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
private data class SongDisplayInfo(
    val mediaId: String,
    val title: String,
    val artist: String,
)

@Composable
fun SongInfoRow(
    modifier: Modifier = Modifier,
    isExpandMenuOpen: Boolean = false,
    onToggleExpandMenu: () -> Unit = {},
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onChevronPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()
    val queueState by playerConnection.uiState.queueState.collectAsState()

    var previousQueueIndex by remember { mutableIntStateOf(queueState.currentIndex) }
    var slideDirection by remember { mutableIntStateOf(1) }

    LaunchedEffect(queueState.currentIndex) {
        if (queueState.currentIndex != previousQueueIndex) {
            slideDirection = if (queueState.currentIndex < previousQueueIndex) -1 else 1
            previousQueueIndex = queueState.currentIndex
        }
    }

    val songDisplay = remember(trackInfo.mediaId, trackInfo.title, trackInfo.artist) {
        SongDisplayInfo(
            mediaId = trackInfo.mediaId,
            title = trackInfo.title,
            artist = trackInfo.artist,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { onTitlePositioned(it.boundsInRoot()) }

                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        0.82f to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                }
        ) {
            AnimatedContent(
                targetState = songDisplay,
                transitionSpec = {
                    val isForward = slideDirection >= 0
                    if (isForward) {
                        (slideInHorizontally(
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) { width -> (-width * 0.45f).toInt() } +
                                fadeIn(animationSpec = tween(320)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                                ) { width -> (width * 0.45f).toInt() } +
                                        fadeOut(animationSpec = tween(220))
                            )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) { width -> (width * 0.45f).toInt() } +
                                fadeIn(animationSpec = tween(320)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                                ) { width -> (-width * 0.45f).toInt() } +
                                        fadeOut(animationSpec = tween(220))
                            )
                    }.using(SizeTransform(clip = false))
                },
                label = "SongInfoSlideAnimation",
                modifier = Modifier.fillMaxWidth()
            ) { targetSong ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = targetSong.title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                    )
                    if (targetSong.artist.isNotBlank()) {
                        Text(
                            text = targetSong.artist,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PressScaleActionButton(
                icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                onClick = playerConnection::toggleLike,
                hasShadow = false
            )
            val arrowRotation by animateFloatAsState(
                targetValue = if (isExpandMenuOpen) 180f else 0f, 
                label = "arrowRot",
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
            )
            val arrowScale by animateFloatAsState(
                targetValue = if (isExpandMenuOpen) 0.86f else 1f, 
                label = "arrowScale",
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
            )
            val chevronBgColor by animateColorAsState(
                targetValue = if (isExpandMenuOpen) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                label = "chevronBgColor",
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            val chevronTint by animateColorAsState(
                targetValue = if (isExpandMenuOpen) Color.White else Color.White.copy(alpha = 0.90f),
                label = "chevronTint",
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            PressScaleActionButton(
                icon = R.drawable.expand_less,
                tint = chevronTint,
                containerColor = chevronBgColor,
                onClick = onToggleExpandMenu,
                hasShadow = false,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        onChevronPositioned(coords.boundsInRoot())
                    }
                    .graphicsLayer {
                        rotationZ = arrowRotation
                        scaleX = arrowScale
                        scaleY = arrowScale
                        alpha = if (isExpandMenuOpen) 0f else 1f
                    }
            )
        }
    }
}

/** Shared with the lyrics page, so its repeat/like pair matches the player's exactly. */
@Composable
fun PressScaleActionButton(
    icon: Int,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boldIcon: Boolean = false,
    badgeText: String? = null,
    hasShadow: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleActionButton"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (hasShadow) Modifier.shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                else Modifier
            )
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            )
    ) {

        if (boldIcon) {
            androidx.compose.foundation.Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier
                    .size(18.dp)
                    .offset(x = 0.45.dp)
            )
        }
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(18.dp)
        )
        if (badgeText != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint)
            ) {
                Text(
                    text = badgeText,
                    color = Color.Black,
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
