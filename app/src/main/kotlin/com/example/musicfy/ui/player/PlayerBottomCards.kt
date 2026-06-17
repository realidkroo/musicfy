// PlayerBottomCards.kt
// this thing is for player bottom cards

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
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
    val surfaceColor = cardColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(98.dp)
            .clipToBounds()
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
                .offset(y = 44.dp)
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
            .border(
                width = 1.dp,
                color = Color(0xFFB8B8B8).copy(alpha = 0.30f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        if (card == FrontCard.LYRICS) {
            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 24.dp, top = 10.dp)
            ) {
                AnimatedContent(
                    targetState = currentLyricsLine?.takeIf { it.isNotBlank() } ?: "...",
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

private val LyricsFontSize = 18.sp

private data class WordVisualState(
    val text: String,
    val charRange: IntRange,
    val isActive: Boolean,
    val hasPassed: Boolean,
    val wordProgress: Float,
)

@Composable
private fun BottomLyricsPreviewText(
    text: String,
    entry: LyricsEntry?,
    playbackPosition: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val words = entry?.words

    val plainText = remember(entry, text) {
        words?.joinToString(" ") { it.text } ?: text
    }

    val wordStates = remember(words, playbackPosition) {
        if (words.isNullOrEmpty()) {
            emptyList()
        } else {
            var cursor = 0
            words.map { word ->
                val startMs = (word.startTime * 1000).toLong()
                val endMs = (word.endTime * 1000).toLong().coerceAtLeast(startMs + 40L)
                val isActive = playbackPosition in startMs until endMs
                val hasPassed = playbackPosition >= endMs
                val progress = if (isActive) {
                    ((playbackPosition - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val range = cursor until (cursor + word.text.length)
                cursor += word.text.length + 1
                WordVisualState(word.text, range, isActive, hasPassed, progress)
            }
        }
    }

    val activeCharIndex = remember(wordStates) {
        val active = wordStates.firstOrNull { it.isActive }
        val lastPassed = wordStates.lastOrNull { it.hasPassed }
        active?.charRange?.first ?: lastPassed?.let { it.charRange.last + 2 } ?: 0
    }

    AutoResizeLyricsLine(
        plainText = plainText,
        wordStates = wordStates,
        fallbackText = text,
        activeCharIndex = activeCharIndex,
        color = color,
        fontSize = LyricsFontSize,
        lineHeight = 22.sp,
        modifier = modifier,
    )
}

@Composable
private fun AutoResizeLyricsLine(
    plainText: String,
    wordStates: List<WordVisualState>,
    fallbackText: String,
    activeCharIndex: Int,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    var lineBoundaries by remember(plainText) { mutableStateOf<List<IntRange>>(emptyList()) }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = plainText,
            color = Color.Transparent,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Left,
            softWrap = true,
            style = TextStyle(letterSpacing = 0.sp),
            onTextLayout = { result ->
                if (result.lineCount > 0) {
                    val ranges = (0 until result.lineCount).map { line ->
                        result.getLineStart(line) until result.getLineEnd(line, visibleEnd = true)
                    }
                    if (ranges != lineBoundaries) {
                        lineBoundaries = ranges
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    layout(constraints.maxWidth, 0) {
                        placeable.place(0, 0)
                    }
                }
        )

        if (lineBoundaries.isNotEmpty()) {
            val safeIndex = activeCharIndex.coerceIn(0, kotlin.math.max(0, plainText.length - 1))
            val activeLineIndex = lineBoundaries.indexOfLast { safeIndex >= it.first }
                .coerceAtLeast(0)
                .coerceAtMost(lineBoundaries.lastIndex)

            AnimatedContent(
                targetState = activeLineIndex,
                transitionSpec = {
                    val isJump = kotlin.math.abs(targetState - initialState) > 1
                    val exit = scaleOut(
                        animationSpec = tween(200),
                        targetScale = 0.85f
                    ) + fadeOut(tween(160))

                    when {
                        isJump -> EnterTransition.None togetherWith ExitTransition.None
                        targetState > initialState -> (
                            scaleIn(
                                animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                initialScale = 0.8f
                            ) + slideInVertically(
                                animationSpec = tween(320),
                                initialOffsetY = { (it * 0.5f).toInt() }
                            ) + fadeIn(tween(260))
                        ) togetherWith exit
                        else -> (
                            scaleIn(
                                animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                initialScale = 0.8f
                            ) + slideInVertically(
                                animationSpec = tween(320),
                                initialOffsetY = { -(it * 0.5f).toInt() }
                            ) + fadeIn(tween(260))
                        ) togetherWith exit
                    }
                },
                label = "LyricsLineSlide",
                modifier = Modifier.fillMaxWidth()
            ) { lineIdx ->
                val range = lineBoundaries.getOrNull(lineIdx)
                if (wordStates.isEmpty() || range == null) {
                    Text(
                        text = fallbackText,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Left,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(letterSpacing = 0.sp),
                    )
                } else {
                    val lineWords = wordStates.filter {
                        it.charRange.first >= range.first && it.charRange.first <= range.last
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        lineWords.forEachIndexed { idx, word ->
                            LyricsWordItem(word = word, baseColor = color, fontSize = fontSize)
                            if (idx < lineWords.lastIndex) {
                                Text(
                                    text = " ",
                                    color = Color.Transparent,
                                    fontSize = fontSize,
                                    style = TextStyle(letterSpacing = 0.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsWordItem(
    word: WordVisualState,
    baseColor: Color,
    fontSize: TextUnit,
) {
    val wordScale = if (word.isActive) {
        1f + 0.07f * kotlin.math.sin(word.wordProgress.toDouble() * Math.PI).toFloat()
    } else {
        1f
    }

    val styledWord = remember(word.text, word.isActive, word.hasPassed, word.wordProgress, baseColor) {
        buildAnnotatedString {
            if (word.isActive) {
                val sweepPos = word.wordProgress * word.text.length
                val transitionWidth = (word.text.length / 1.6f).coerceIn(0.7f, 2.4f)
                word.text.forEachIndexed { charIdx, ch ->
                    val charCenter = charIdx + 0.5f
                    val localT = (((sweepPos - charCenter) / transitionWidth) + 0.5f).coerceIn(0f, 1f)
                    val glow = kotlin.math.sin(localT.toDouble() * Math.PI).toFloat()
                    val charColor = lerp(baseColor.copy(alpha = 0.42f), baseColor.copy(alpha = 1f), localT)
                    withStyle(
                        SpanStyle(
                            color = charColor,
                            shadow = if (glow > 0.05f) {
                                Shadow(color = baseColor.copy(alpha = 0.34f * glow), blurRadius = 12f)
                            } else null
                        )
                    ) {
                        append(ch)
                    }
                }
            } else {
                val alpha = if (word.hasPassed) 1f else 0.42f
                withStyle(SpanStyle(color = baseColor.copy(alpha = alpha))) {
                    append(word.text)
                }
            }
        }
    }

    Text(
        text = styledWord,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(letterSpacing = 0.sp),
        modifier = Modifier.graphicsLayer(scaleX = wordScale, scaleY = wordScale)
    )
}
