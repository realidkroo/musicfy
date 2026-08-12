// songinforowkt
// minimal title artist + like repeat row for the fullscreen player
// songinfokt + actionbuttonskt now at old player which combined this
// lyrics mode thumbnail toggle clipboard copy artist tap navigation and an
// menu all dropped here since none of that exists in this player yet just
// like repeat

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import com.example.musicfy.extensions.toggleRepeatMode

@Composable
fun SongInfoRow(
    modifier: Modifier = Modifier,
    // reports the title+artist column s position in root screen coordinates
    // laid out bottomsheetplayer holds onto the last value even after this
    // which happens the instant lyrics opens since playercontrols is behind a
    // and uses it as the starting rect for morphingsonginfo s travel to the
    // from side of that shared element move captured live instead of
    // this row s own nested offsets paddings make that math error prone to
    onTitlePositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()

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
                // both lines share one fade boundary at the edge nearest the repeat like
                // buttons a long title artist or one still mid marquee scroll dissolves
                // into the icons instead of ending in a hard edge right up against them
                // offscreen compositing is what lets the dstin mask below erase from this
                // column s own already drawn pixels rather than punching through to whatever
                // is behind it
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
            Text(
                text = trackInfo.title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
            )
            if (trackInfo.artist.isNotBlank()) {
                Text(
                    text = trackInfo.artist,
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

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val repeatMode = transportState.repeatMode
            PressScaleActionButton(
                icon = R.drawable.repeat,
                boldIcon = true,
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.85f),
                containerColor = if (repeatMode != Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                badgeText = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                onClick = { playerConnection.player.toggleRepeatMode() }
            )
            PressScaleActionButton(
                icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                tint = if (trackInfo.liked) Color.White else Color.White.copy(alpha = 0.85f),
                containerColor = if (trackInfo.liked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                onClick = playerConnection::toggleLike
            )
        }
    }
}

@Composable
private fun PressScaleActionButton(
    icon: Int,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    boldIcon: Boolean = false,
    badgeText: String? = null,
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
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            )
    ) {
        // drawing the icon twice with a tiny offset old player s
        // fakes a bolder weight without needing a separate bold icon asset
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
