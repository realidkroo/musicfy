// PlayerBottomCards.kt
// this thing is for player bottom cards

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.ui.component.FontSizeRange
import com.example.musicfy.lyrics.LyricsEntry

enum class FrontCard {
    LYRICS, QUEUE
}

@Composable
fun PlayerBottomCards(
    currentLyricsLine: String?,
    currentLyricsEntry: LyricsEntry?,
    playbackPosition: Long,
    nextQueueTitle: String?,
    nextQueueArtist: String?,
    textColor: Color,
    cardColor: Color,
    onCardTap: (FrontCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = lerp(cardColor, Color.White, 0.08f).copy(alpha = 1f)
    val borderColor = textColor.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(105.dp)
            .padding(horizontal = 24.dp)
    ) {
        PlayerPreviewCard(
            card = FrontCard.LYRICS,
            currentLyricsLine = currentLyricsLine,
            currentLyricsEntry = currentLyricsEntry,
            playbackPosition = playbackPosition,
            nextQueueTitle = nextQueueTitle,
            nextQueueArtist = nextQueueArtist,
            textColor = textColor,
            cardColor = surfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 38.dp)
                .graphicsLayer { alpha = 1f },
            onClick = { onCardTap(FrontCard.LYRICS) }
        )
    }
}

@Composable
private fun PlayerPreviewCard(
    card: FrontCard,
    currentLyricsLine: String?,
    currentLyricsEntry: LyricsEntry?,
    playbackPosition: Long,
    nextQueueTitle: String?,
    nextQueueArtist: String?,
    textColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        if (card == FrontCard.LYRICS) {
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 28.dp, top = 16.dp)
            ) {
                var persistentLine by remember { mutableStateOf("...") }
                if (!currentLyricsLine.isNullOrBlank()) {
                    persistentLine = currentLyricsLine
                }
                AnimatedContent(
                    targetState = persistentLine,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(durationMillis = 280),
                            initialOffsetY = { it / 2 }
                        ) + fadeIn(animationSpec = tween(durationMillis = 220))) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(durationMillis = 280),
                                targetOffsetY = { -it / 2 }
                            ) + fadeOut(animationSpec = tween(durationMillis = 220)))
                    },
                    label = "BottomLyricsLine"
                ) { lyricLine ->
                    BottomLyricsPreviewText(
                        text = lyricLine,
                        entry = currentLyricsEntry,
                        playbackPosition = playbackPosition,
                        color = textColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "QUEUE",
                        color = textColor.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = nextQueueTitle ?: "End of queue",
                    color = textColor,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!nextQueueArtist.isNullOrBlank()) {
                    Text(
                        text = nextQueueArtist,
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomLyricsPreviewText(
    text: String,
    entry: LyricsEntry?,
    playbackPosition: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val styledText = remember(text, entry?.words, playbackPosition, color) {
        val words = entry?.words
        if (!words.isNullOrEmpty()) {
            buildAnnotatedString {
                words.forEachIndexed { index, word ->
                    val startMs = (word.startTime * 1000).toLong()
                    val endMs = (word.endTime * 1000).toLong().coerceAtLeast(startMs + 40L)
                    val isActive = playbackPosition in startMs until endMs
                    val hasPassed = playbackPosition >= endMs
                    val wordProgress = if (isActive) {
                        ((playbackPosition - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val alpha = when {
                        hasPassed -> 1f
                        isActive -> 0.42f + (wordProgress * 0.58f)
                        else -> 0.42f
                    }
                    val weight = when {
                        hasPassed || isActive -> FontWeight.Black
                        else -> FontWeight.Bold
                    }
                    withStyle(
                        SpanStyle(
                            color = color.copy(alpha = alpha),
                            fontWeight = weight,
                            shadow = if (isActive) {
                                Shadow(color = color.copy(alpha = 0.34f), blurRadius = 12f)
                            } else null
                        )
                    ) {
                        append(word.text)
                    }
                    if (index < words.lastIndex) append(" ")
                }
            }
        } else {
            AnnotatedString(text)
        }
    }

    AutoResizeAnnotatedText(
        text = styledText,
        fallbackColor = color,
        fontSizeRange = FontSizeRange(min = 14.sp, max = 18.sp, step = 0.5.sp),
        lineHeight = 22.sp,
        modifier = modifier,
    )
}

@Composable
private fun AutoResizeAnnotatedText(
    text: AnnotatedString,
    fallbackColor: Color,
    fontSizeRange: FontSizeRange,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    var fontSizeValue by remember(text) { mutableFloatStateOf(fontSizeRange.max.value) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        color = fallbackColor,
        fontSize = fontSizeValue.sp,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Left,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false,
        style = TextStyle(letterSpacing = 0.sp),
        onTextLayout = {
            if ((it.didOverflowHeight || it.didOverflowWidth) && !readyToDraw) {
                val nextFontSizeValue = fontSizeValue - fontSizeRange.step.value
                if (nextFontSizeValue <= fontSizeRange.min.value) {
                    fontSizeValue = fontSizeRange.min.value
                    readyToDraw = true
                } else {
                    fontSizeValue = nextFontSizeValue
                }
            } else {
                readyToDraw = true
            }
        },
        modifier = modifier
            .drawWithContent { if (readyToDraw) drawContent() }
            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 20.dp)
    )
}
