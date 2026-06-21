// PlayerBottomCards.kt
// this thing is for player bottom cards

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.ui.theme.InterFontFamily
import kotlinx.coroutines.launch

@Composable
fun PlayerBottomCards(
    currentLyricsLine: String?,
    currentLyricsEntry: LyricsEntry?,
    playbackPosition: Long,
    nextQueueTitle: String?,
    nextQueueArtist: String?,
    textColor: Color,
    cardColor: Color,
    onCardTap: () -> Unit,
    modifier: Modifier = Modifier,
    revealReady: Boolean = true,
) {
    val surfaceColor = cardColor.copy(alpha = 0.30f)
    val rearSurfaceColor = cardColor.copy(alpha = 0.24f)
    val backReveal = remember { Animatable(0f) }
    val frontReveal = remember { Animatable(0f) }

    LaunchedEffect(revealReady) {
        if (!revealReady) {
            backReveal.snapTo(0f)
            frontReveal.snapTo(0f)
        } else {
            launch {
                backReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
            }
            launch {
                frontReveal.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 380, delayMillis = 70, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val rearProgress = backReveal.value
    val rearHorizontalPadding = lerp(0.dp, 12.dp, rearProgress)
    val rearHeight = lerp(132.dp, 104.dp, rearProgress)
    val rearOffsetY = lerp(88.dp, 46.dp, rearProgress)
    val frontHeight = 132.dp
    val frontOffsetY = 88.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clipToBounds()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        EmptyPreviewCard(
            cardColor = rearSurfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rearHorizontalPadding)
                .height(rearHeight)
                .align(Alignment.BottomCenter)
                .offset(y = rearOffsetY)
                .graphicsLayer {
                    alpha = rearProgress
                }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(frontHeight)
                .align(Alignment.BottomCenter)
                .offset(y = frontOffsetY)
                .graphicsLayer {
                    translationY = (1f - frontReveal.value) * 22.dp.toPx()
                }
                .drawWithContent {
                    if (frontReveal.value > 0.04f) {
                        drawRoundRect(
                            color = Color.Black,
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
        )
        PlayerPreviewCard(
            currentLyricsLine = currentLyricsLine,
            currentLyricsEntry = currentLyricsEntry,
            playbackPosition = playbackPosition,
            nextQueueTitle = nextQueueTitle,
            nextQueueArtist = nextQueueArtist,
            textColor = textColor,
            cardColor = surfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(frontHeight)
                .align(Alignment.BottomCenter)
                .offset(y = frontOffsetY)
                .graphicsLayer {
                    alpha = frontReveal.value
                    translationY = (1f - frontReveal.value) * 22.dp.toPx()
                },
            onClick = onCardTap
        )
    }
}

@Composable
private fun EmptyPreviewCard(
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(24.dp)
            )
    )
}

@Composable
private fun PlayerPreviewCard(
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
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 24.dp, top = 8.dp)
        ) {
            AnimatedContent(
                targetState = currentLyricsLine?.takeIf { it.isNotBlank() }?.repairSyllableSpacing() ?: "...",
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 140))
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
    }
}

private val LyricsFontSize = 18.sp

private fun String.repairSyllableSpacing(): String {
    val compacted = replace(Regex("[\\t\\u00A0]+"), " ").trim()
    if (compacted.isBlank()) return compacted
    if (!Regex(" {2,}").containsMatchIn(compacted)) {
        return compacted.replace(Regex(" +"), " ")
    }

    return compacted
        .split(Regex(" {2,}"))
        .joinToString(" ") { group ->
            val parts = group.trim().split(Regex(" +")).filter { it.isNotBlank() }
            if (parts.size > 1 && parts.all { part -> part.length <= 4 && part.any(Char::isLetter) }) {
                parts.joinToString("")
            } else {
                group.trim().replace(Regex(" +"), " ")
            }
        }
}

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

    val plainText = remember(text) {
        text.trim().repairSyllableSpacing()
    }

    val canRenderWordTiming = remember(words, plainText) {
        !words.isNullOrEmpty() && words.joinToString(" ") { it.text }.trim() == plainText
    }

    val wordStates = remember(words, playbackPosition, canRenderWordTiming) {
        if (!canRenderWordTiming || words.isNullOrEmpty()) {
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
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
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
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
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
        1f + 0.035f * kotlin.math.sin(word.wordProgress.toDouble() * Math.PI).toFloat()
    } else {
        1f
    }
    val inactiveAlpha = if (word.hasPassed) 1f else 0.42f

    Text(
        text = word.text,
        fontSize = fontSize,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        style = if (word.isActive) {
            MaterialTheme.typography.bodyMedium.copy(
                brush = Brush.horizontalGradient(
                    0f to baseColor,
                    word.wordProgress.coerceAtLeast(0.01f) to baseColor,
                    (word.wordProgress + 0.18f).coerceAtMost(1f) to baseColor.copy(alpha = 0.42f),
                    1f to baseColor.copy(alpha = 0.42f)
                ),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                letterSpacing = 0.sp,
            )
        } else {
            MaterialTheme.typography.bodyMedium.copy(
                color = baseColor.copy(alpha = inactiveAlpha),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                letterSpacing = 0.sp,
            )
        },
        modifier = Modifier.graphicsLayer(scaleX = wordScale, scaleY = wordScale)
    )
}
