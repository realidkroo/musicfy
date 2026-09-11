package com.example.musicfy.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.utils.makeTimeString

/** How far a horizontal drag must travel before it counts as a skip. */
private const val SwipeSkipThresholdPx = 120f

/** Height of the stacked elapsed/total pair, used to centre it on the playhead. */
private val TimesBlockHeight = 116.dp

/** Keeps the pair clear of the status bar when playback is near the start. */
private val StatusBarAllowance = 96.dp

/**
 * The "Simple" player: no transport controls, just the track and a background that fills as it
 * plays.
 *
 * Playback is driven entirely by gesture - double tap toggles, swipe right skips forward, swipe
 * left goes back - so the only visible affordances are the three shortcuts along the bottom.
 *
 * [compact] swaps the oversized title for a cover thumbnail with title and artist.
 */
@Composable
fun SimplePlayerStyle(
    compact: Boolean,
    surface: Color,
    played: Color,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val progress by playerConnection.uiState.progressState.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val duration = progress.duration.takeIf { it > 0L } ?: 1L
    val fraction = (progress.position.toFloat() / duration).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { playerConnection.togglePlayPause() })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total > SwipeSkipThresholdPx -> playerConnection.seekToNext()
                            total < -SwipeSkipThresholdPx -> playerConnection.seekToPrevious()
                        }
                    },
                    onHorizontalDrag = { _, amount -> total += amount },
                )
            },
    ) {
        // The played portion of the screen *is* the progress bar - the boundary is the playhead,
        // which is why the two timestamps sit either side of it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceAtLeast(0.001f))
                .background(played)
                .align(Alignment.TopCenter)
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val boundary = maxHeight * fraction
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .offset(y = (boundary - TimesBlockHeight / 2f).coerceAtLeast(StatusBarAllowance)),
            ) {
                Text(
                    text = makeTimeString(progress.position),
                    color = Color.White,
                    fontSize = 54.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = makeTimeString(progress.duration.coerceAtLeast(0L)),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 54.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp)
                // statusBarsPadding alone left the row jammed against the status icons.
                .padding(top = 28.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (compact) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        text = mediaMetadata?.title.orEmpty(),
                        color = Color.White,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenMenu,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SimpleAction(
                    icon = if (mediaMetadata?.liked == true) {
                        R.drawable.ic_untitled_heart
                    } else {
                        R.drawable.ic_untitled_heart_unfill
                    },
                    onClick = playerConnection::toggleLike,
                )
                SimpleAction(
                    icon = if (transportState.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    onClick = { playerConnection.player.shuffleModeEnabled = !transportState.shuffleModeEnabled },
                )
                SimpleAction(icon = R.drawable.lyrics, onClick = onOpenMenu)
            }
        }
    }
}

@Composable
private fun SimpleAction(icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
