// songinfo kt
// extracted from player kt s controlscontent closure the header row
// lyrics mode thumbnail toggle + title artist column

package com.example.musicfy.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.db.entities.FormatEntity
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.SwipeGesture
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.ui.component.AudioFormatBadge
import com.example.musicfy.ui.component.BottomSheetState

// emits the lyrics mode thumbnail toggle + title artist column as siblings into the caller s row see player kt s header row kept as a rowscope extension rather than owning its own row so weight 1f below keeps resolving against that outer row and the surrounding spacer action buttons stay laid out side by side exactly as before
@Composable
fun RowScope.SongInfo(
    mediaMetadata: MediaMetadata,
    showInlineLyrics: Boolean,
    onDismissInlineLyrics: () -> Unit,
    hidePlayerThumbnail: Boolean,
    isFullScreen: Boolean,
    enableLyricsThumbnailPlayPause: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    state: BottomSheetState,
    swipeLyrics: Boolean,
    textButtonColor: Color,
    TextBackgroundColor: Color,
    hideAudioQualityBadge: Boolean,
    currentFormat: FormatEntity?,
    audioQuality: AudioQuality,
    currentSong: Song?,
    playerConnection: PlayerConnection,
    navController: NavController,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    AnimatedContent(
        targetState = showInlineLyrics,
        label = "ThumbnailAnimation"
    ) { showLyrics ->
            if (showLyrics) {
                Row {
                    if (hidePlayerThumbnail) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.musicfy_icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp),
                                tint = textButtonColor.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                .combinedClickable(
                                    enabled = true,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onDismissInlineLyrics,
                                    onLongClick = {
                                        if (isFullScreen && enableLyricsThumbnailPlayPause) {
                                            playerConnection.togglePlayPause()
                                        }
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = mediaMetadata.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isFullScreen && enableLyricsThumbnailPlayPause) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = if (isPlaying) 0f else 0.4f))
                                )

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isPlaying,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (playbackState == Player.STATE_ENDED) R.drawable.replay
                                            else R.drawable.ic_untitled_play
                                        ),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            } else {
                Spacer(modifier = Modifier.width(0.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (state.progress > 0.95f) ((state.progress - 0.95f) * 20f).coerceIn(0f, 1f) else 0f }
                .SwipeGesture(
                    enabled = isFullScreen && swipeLyrics,
                    onSwipeRight = { playerConnection.seekToPrevious() },
                    onSwipeLeft = { playerConnection.seekToNext() }
                )
        ) {
            AnimatedContent(
                targetState = mediaMetadata.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "",
            ) { title ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Black,
                                        0.84f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextBackgroundColor,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                .combinedClickable(
                                    enabled = true,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (mediaMetadata.album != null) {
                                            navController.navigate("album/${mediaMetadata.album.id}")
                                            state.collapseSoft()
                                        }
                                    },
                                    onLongClick = {
                                        val clip = ClipData.newPlainText(context.getString(R.string.copied_title), title)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast
                                            .makeText(context, context.getString(R.string.copied_title), Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mediaMetadata.explicit) {
                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
                if (!hideAudioQualityBadge) {
                    AudioFormatBadge(
                        format = currentFormat,
                        tint = Color.Unspecified,
                        height = 16.dp,
                        modifier = Modifier.padding(end = 6.dp),
                        audioQuality = audioQuality,
                        fallbackId = currentSong?.id
                    )
                }

                if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                    val artistThumbnails = currentSong
                        ?.artists
                        ?.mapNotNull { it.thumbnailUrl?.takeIf(String::isNotBlank) }
                        ?.take(2)
                        .orEmpty()
                    ArtistAvatarStack(
                        thumbnailUrls = artistThumbnails,
                        tint = TextBackgroundColor,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    val annotatedString = buildAnnotatedString {
                        mediaMetadata.artists.forEachIndexed { index, artist ->
                            val tag = "artist_${artist.id.orEmpty()}"
                            pushStringAnnotation(tag = tag, annotation = artist.id.orEmpty())
                            withStyle(SpanStyle(color = TextBackgroundColor, fontSize = 16.sp)) {
                                append(artist.name)
                            }
                            pop()
                            if (index != mediaMetadata.artists.lastIndex) append(", ")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                            .padding(end = 12.dp)
                    ) {
                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        var clickOffset by remember { mutableStateOf<Offset?>(null) }
                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val tapPosition = event.changes.firstOrNull()?.position
                                            if (tapPosition != null) {
                                                clickOffset = tapPosition
                                            }
                                        }
                                    }
                                }
                                .combinedClickable(
                                    enabled = true,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        val tapPosition = clickOffset
                                        val layout = layoutResult
                                        if (tapPosition != null && layout != null) {
                                            val offset = layout.getOffsetForPosition(tapPosition)
                                            annotatedString
                                                .getStringAnnotations(offset, offset)
                                                .firstOrNull()
                                                ?.let { ann ->
                                                    val artistId = ann.item
                                                    if (artistId.isNotBlank()) {
                                                        navController.navigate("artist/$artistId")
                                                        state.collapseSoft()
                                                    }
                                                }
                                        }
                                    },
                                    onLongClick = {
                                        val clip =
                                            ClipData.newPlainText(
                                                context.getString(R.string.copied_artist),
                                                annotatedString
                                            )
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.copied_artist),
                                                Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    }
                                )
                        )
                    }
                }
            }
        }
}

@Composable
private fun ArtistAvatarStack(
    thumbnailUrls: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val avatarCount = thumbnailUrls.size.coerceAtMost(2)
    if (avatarCount == 0) return

    Box(
        modifier = modifier
            .width(if (avatarCount > 1) 30.dp else 18.dp)
            .height(18.dp)
    ) {
        repeat(avatarCount) { index ->
            val thumbnailUrl = thumbnailUrls.getOrNull(index)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = (index * 10).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f))
                    .border(1.dp, tint.copy(alpha = 0.34f), CircleShape)
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
