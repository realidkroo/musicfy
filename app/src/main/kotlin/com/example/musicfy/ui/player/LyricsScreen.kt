// LyricsScreen.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.ui.theme.PlayerColorExtractor
import com.example.musicfy.viewmodels.LyricsScreenViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ActiveLineViewportFraction = 0.2f

private const val ImmersiveDelayMs = 3_500L

private const val RecenterDurationMs = 950

private const val ScrollDirectionThresholdPx = 6L

private const val IdleRecenterDelayMs = 3_500L

private const val TopFadeFraction = 0.10f
private const val BottomFadeStart = 0.62f

private val HeaderOverlapAllowance = 28.dp

@Composable
fun LyricsScreen(
    onClose: () -> Unit,

    screenHeight: androidx.compose.ui.unit.Dp,

    contentBottomInset: androidx.compose.ui.unit.Dp,

    onImmersiveChange: (Boolean) -> Unit = {},

    isSheetDragging: Boolean = false,

    isMorphing: Boolean = false,

    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsScreenViewModel = hiltViewModel()
    val density = LocalDensity.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()

    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position }
    }

    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(viewModel::ensureLyricsLoaded)
    }

    val context = LocalContext.current
    val fallbackColorInt = MaterialTheme.colorScheme.primary.toArgb()
    var accentColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val thumbnailUrl = mediaMetadata?.thumbnailUrl
        accentColor = null
        if (thumbnailUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(8)
                            .resizeBitmapArea(100 * 100)
                            .generate()
                    }
                    val colors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColorInt,
                    )
                    accentColor = colors.firstOrNull()?.let { PlayerColorExtractor.darkenIfTooLight(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyrics(raw)
    }

    LaunchedEffect(lines) {
        lines.forEach { entry ->
            launch {
                val romanized = when {
                    LyricsUtils.isJapanese(entry.text) -> LyricsUtils.romanizeJapanese(entry.text)
                    LyricsUtils.isKorean(entry.text) -> LyricsUtils.romanizeKorean(entry.text)
                    LyricsUtils.isChinese(entry.text) -> LyricsUtils.romanizeChinese(entry.text)
                    LyricsUtils.isRussian(entry.text) || LyricsUtils.isUkrainian(entry.text) ||
                        LyricsUtils.isSerbian(entry.text) || LyricsUtils.isBulgarian(entry.text) ||
                        LyricsUtils.isBelarusian(entry.text) || LyricsUtils.isKyrgyz(entry.text) ||
                        LyricsUtils.isMacedonian(entry.text) -> LyricsUtils.romanizeCyrillic(entry.text)
                    LyricsUtils.isHindi(entry.text) -> LyricsUtils.romanizeHindi(entry.text)
                    LyricsUtils.isPunjabi(entry.text) -> LyricsUtils.romanizePunjabi(entry.text)
                    else -> null
                }
                if (!romanized.isNullOrBlank() && romanized != entry.text) {
                    entry.romanizedTextFlow.value = romanized
                }
            }
        }
    }

    val currentIndex by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, progress.position) }
    }

    val anchorIndex by remember(lines) {
        derivedStateOf {
            var i = LyricsUtils.findCurrentLineIndex(lines, progress.position)
            if (i <= 0) return@derivedStateOf i
            while (i > 0) {
                val previous = i - 1
                val endMs = lines[previous].words
                    ?.lastOrNull()
                    ?.let { (it.endTime * 1000).toLong() }
                    ?: return@derivedStateOf i
                if (endMs <= progress.position) return@derivedStateOf i
                i = previous
            }
            i
        }
    }

    val listState = rememberLazyListState()
    var followPlayback by remember { mutableStateOf(true) }

    var isAutoScrolling by remember { mutableStateOf(false) }

    val userScrolling by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }

    val suppressEffects = userScrolling || isSheetDragging || isMorphing

    LaunchedEffect(userScrolling) {
        if (userScrolling) followPlayback = false
    }

    LaunchedEffect(userScrolling, followPlayback) {
        if (!followPlayback && !userScrolling) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    var immersive by remember { mutableStateOf(false) }

    LaunchedEffect(userScrolling, immersive) {
        if (!userScrolling && !immersive) {
            delay(ImmersiveDelayMs)
            immersive = true
        }
    }

    LaunchedEffect(listState) {
        var previous = -1L
        snapshotFlow {
            listState.firstVisibleItemIndex.toLong() * 1_000_000L +
                listState.firstVisibleItemScrollOffset
        }.collect { now ->
            val last = previous
            previous = now
            if (last < 0L || isAutoScrolling) return@collect
            val delta = now - last
            if (delta > ScrollDirectionThresholdPx) immersive = true
            else if (delta < -ScrollDirectionThresholdPx) immersive = false
        }
    }

    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }

    val immersion by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsImmersion",
    )

    LaunchedEffect(anchorIndex, followPlayback, lines) {
        val target = anchorIndex
        if (!followPlayback || target !in lines.indices) return@LaunchedEffect

        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val restingOffset = (viewportHeight * ActiveLineViewportFraction).roundToInt()

        try {
        isAutoScrolling = true

        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible != null) {

            val current = visible.offset - listState.layoutInfo.viewportStartOffset
            listState.animateScrollBy(
                (current - restingOffset).toFloat(),
                animationSpec = tween(
                    durationMillis = RecenterDurationMs,
                    easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f),
                ),
            )
        } else {

            listState.animateScrollToItem(target, 0)
        }
        } finally {
            isAutoScrolling = false
        }
    }

    val interludeActive by remember(currentIndex, lines) {
        derivedStateOf {
            val next = currentIndex + 1
            if (next !in lines.indices) return@derivedStateOf false

            val gapStart = if (currentIndex < 0) {
                0L
            } else {
                lines[currentIndex].words
                    ?.lastOrNull()
                    ?.let { (it.endTime * 1000).toLong() }
                    ?: return@derivedStateOf false
            }
            val nextTime = lines[next].time
            nextTime - gapStart >= MinInterludeGapMs &&
                progress.position in gapStart until nextTime
        }
    }

    val (lyricsWaveAnimation) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (lyricsHighBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize()) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 36.dp)
                .padding(top = 28.dp, bottom = 16.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))

                    .clickable { if (immersive) immersive = false else onClose() }
            )

            Spacer(modifier = Modifier.width(18.dp))

            Spacer(modifier = Modifier.weight(1f))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenMenu,
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = "Menu",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 90f },
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(
                    bottom = ((contentBottomInset - HeaderOverlapAllowance) * (1f - immersion))
                        .coerceAtLeast(0.dp)
                )
        ) {
            val listHeight = maxHeight
            if (lines.isEmpty()) {
                if (lyricsEntity == null) {

                    LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = "No lyrics found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,

                    contentPadding = PaddingValues(
                        top = listHeight * ActiveLineViewportFraction,
                        bottom = listHeight * 0.55f,
                    ),
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()

                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

                        .drawWithCache {
                            @Suppress("UNUSED_EXPRESSION") immersion
                            val fade = Brush.verticalGradient(
                                0f to Color.Transparent,
                                TopFadeFraction to Color.Black,
                                BottomFadeStart to Color.Black,
                                1f to Color.Transparent,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = fade, blendMode = BlendMode.DstIn)
                            }
                        },
                ) {
                    itemsIndexed(
                        lines,
                        key = { index, _ -> index },
                        contentType = { _, _ -> "lyric" },
                    ) { index, entry ->

                        val distance = when {
                            currentIndex < 0 -> index - currentIndex
                            index < anchorIndex -> index - anchorIndex
                            index > currentIndex -> index - currentIndex
                            else -> 0
                        }
                        val state = when {

                            currentIndex >= 0 && index in anchorIndex..currentIndex ->
                                if (interludeActive) LyricsLineState.PAST else LyricsLineState.ACTIVE
                            index == currentIndex + 1 -> LyricsLineState.UPCOMING
                            index < currentIndex -> LyricsLineState.PAST
                            else -> LyricsLineState.DEFAULT
                        }

                        val blurStage = when {
                            suppressEffects -> 0
                            distance == 0 -> 0
                            kotlin.math.abs(distance) == 1 -> 1
                            else -> 2
                        }

                        if (interludeActive && index == currentIndex + 1) {

                            val gapStart = if (currentIndex < 0) {
                                0L
                            } else {
                                lines[currentIndex].words
                                    ?.lastOrNull()
                                    ?.let { (it.endTime * 1000).toLong() }
                                    ?: 0L
                            }
                            LyricsInterludeDots(
                                startMs = gapStart,
                                endMs = entry.time,
                                positionMs = progress.position,
                                accentColor = accent,
                            )
                        }

                        val romanized by entry.romanizedTextFlow.collectAsState()
                        LyricsGlowLine(
                            entry = entry,
                            state = state,
                            blurStage = blurStage,
                            suppressEffects = suppressEffects,
                            positionProvider = positionProvider,
                            accentColor = accent,
                            subLine = romanized,
                            waveEnabled = lyricsWaveAnimation && !suppressEffects,
                            highBloom = lyricsHighBloom,
                            onClick = {
                                playerConnection.player.seekTo(entry.time)
                                followPlayback = true
                            },
                        )
                    }
                }
            }

            if (immersion < 0.99f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = screenHeight * ActiveLineViewportFraction)
                        .size(36.dp)
                        .graphicsLayer {
                            alpha = 1f - immersion

                            translationX = immersion * size.width * 1.2f
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { playerConnection.toggleLike() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border
                        ),
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

    }
}

@Composable
private fun LyricsLoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->

            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "lyricsLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
