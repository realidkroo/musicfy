// PlayerBottomCardStack.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.viewmodels.LyricsScreenViewModel
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardHeight = 50.dp
private val CardCorner = 28.dp

private val BottomBleed = 100.dp

private val CardShape = RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner)

private val ArtworkSize = 28.dp
private val ArtworkShape = RoundedCornerShape(ArtworkSize * 0.3f)

private val PeekOffset = 9.dp

private val PeekInset = 10.dp

private const val BackCardScale = 0.94f

private val FlipDistance = 70.dp

@Composable
fun PlayerBottomCardStack(
    glassState: GlassState,
    progressProvider: () -> Float,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,

    lyricsProgressProvider: () -> Float = { 0f },
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val lyricsViewModel: LyricsScreenViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(lyricsViewModel::ensureLyricsLoaded)
    }

    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()

    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position }
    }
    val queueItems by playerConnection.queueItems.collectAsState()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsState()

    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyrics(raw)
    }

    val currentEntry by remember(lines) {
        derivedStateOf {
            val idx = LyricsUtils.findCurrentLineIndex(lines, progress.position)
            lines.getOrNull(idx)
        }
    }

    val nextSong = remember(queueItems, currentMediaItemIndex) {
        queueItems.getOrNull(currentMediaItemIndex + 1)
    }

    val flip = remember { Animatable(0f) }
    val flipDistancePx = with(density) { FlipDistance.toPx() }

    var frontIndex by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val dragState = rememberDraggableState { delta ->
        dragAccum += -delta
        val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
        val target = if (frontIndex == 0) frac else 1f - frac
        scope.launch { flip.snapTo(target) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight + PeekOffset)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { dragAccum = 0f },
                onDragStopped = {

                    if (abs(dragAccum) >= flipDistancePx * 0.4f) {
                        frontIndex = 1 - frontIndex
                    }
                    scope.launch {
                        flip.animateTo(
                            targetValue = frontIndex.toFloat(),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                },
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                if (frontIndex == 1) onOpenQueue() else onOpenLyrics()
            }
    ) {

        val lyricsP = lyricsProgressProvider()
        val p = flip.value + (1f - flip.value) * lyricsP

        val queueLeading = p >= 0.5f

        @Composable
        fun queueCard() = DeckCard(glassState = glassState, frontness = p) {
            QueueCardContent(
                nextTitle = nextSong?.title,
                nextArtist = nextSong?.artist,
                artworkUri = nextSong?.artworkUri?.toString(),
            )
        }

        @Composable
        fun lyricsCard() = DeckCard(glassState = glassState, frontness = 1f - p) {
            LyricsCardContent(entry = currentEntry, positionMs = progress.position)
        }

        val lyricsCardGone = lyricsP > 0.98f

        if (queueLeading) {
            if (!lyricsCardGone) lyricsCard()
            queueCard()
        } else {
            queueCard()
            if (!lyricsCardGone) lyricsCard()
        }
    }
}

@Composable
private fun BoxScope.DeckCard(
    glassState: GlassState,
    frontness: Float,
    content: @Composable () -> Unit,
) {
    val f = frontness.coerceIn(0f, 1f)
    val scale = lerp(BackCardScale, 1f, f)

    val otherF = 1f - f
    val thisTopAboveBottom = CardHeight * scale + lerp(PeekOffset, 0.dp, f)
    val otherTopAboveBottom = CardHeight * lerp(BackCardScale, 1f, otherF) + lerp(PeekOffset, 0.dp, otherF)
    val exposedOnScreen = thisTopAboveBottom - otherTopAboveBottom

    val cutHeight = if (exposedOnScreen > 0.dp) exposedOnScreen / scale else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight)
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale

                translationY = -lerp(PeekOffset.toPx(), 0f, f)

                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .padding(horizontal = lerp(PeekInset, 0.dp, f))
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardHeight + BottomBleed)
                .align(Alignment.TopCenter)
                .clip(CardShape)

                .drawWithContent {
                    val cutPx = cutHeight?.toPx()
                    if (cutPx == null || cutPx >= size.height) {
                        drawContent()
                    } else if (cutPx > 0f) {
                        clipRect(bottom = cutPx) { this@drawWithContent.drawContent() }
                    }
                }
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.White.copy(alpha = lerp(0.06f, 0.11f, f)),
                        shape = CardShape,
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = lerp(0.10f, 0.20f, f)),
                        shape = CardShape,
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = f * f * (3f - 2f * f) }
        ) {
            content()
        }
    }
}

@Composable
private fun LyricsCardContent(entry: LyricsEntry?, positionMs: Long) {
    val text = entry?.text?.takeIf { it.isNotBlank() }
    val words = entry?.words

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        val style = MaterialTheme.typography.bodyLarge
        val modifier = Modifier.weight(1f).fadingRightEdge()

        if (text != null && !words.isNullOrEmpty()) {

            val filled = karaokeFilledChars(text, words, positionMs)
            Text(
                text = karaokeAnnotated(
                    text = text,
                    filledChars = filled,
                    highlightColor = Color.White,
                    baseColor = Color.White.copy(alpha = 0.45f),
                ),
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = modifier,
            )
        } else {
            Text(
                text = text ?: "No lyrics for this song",
                style = style,
                fontWeight = FontWeight.SemiBold,
                color = if (text != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun QueueCardContent(
    nextTitle: String?,
    nextArtist: String?,
    artworkUri: String?,
) {
    Row(

        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(ArtworkShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).fadingRightEdge()) {

            Text(
                text = "Next Song",
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = if (nextTitle != null) {
                    listOfNotNull(nextTitle, nextArtist?.takeIf { it.isNotBlank() })
                        .joinToString(" - ")
                } else {
                    "End of queue"
                },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (nextTitle != null) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

private fun Modifier.fadingRightEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fade = Brush.horizontalGradient(
            0f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = fade, blendMode = BlendMode.DstIn)
        }
    }
