// LyricsScreen.kt
// Full lyrics page for the expanded player, styled after Monochrome's fullscreen lyrics pane:
// small cover top-left (tap to close back to the normal player view) instead of the usual big
// centered artwork, a progressively-highlighted/blurred synced lyrics column, and the same
// progress slider + transport row as the normal player underneath. Romanization is computed
// locally (deterministic script transliteration already in LyricsUtils, no network/API-key
// step) and shown as a smaller sub-line under each lyric line.

package com.example.musicfy.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.ui.component.AudioFormatBadge
import com.example.musicfy.ui.theme.PlayerColorExtractor
import com.example.musicfy.viewmodels.LyricsScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsScreenViewModel = hiltViewModel()
    val density = LocalDensity.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val progress by playerConnection.progressState.collectAsState()
    val queueItems by playerConnection.queueItems.collectAsState()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsState()

    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.let(viewModel::ensureLyricsLoaded)
    }

    // Reuses the same extractor as the detail screens' backgrounds — muted/desaturated
    // majority color, not the raw vivid cover color — so the karaoke highlight and glow
    // match the app's established accent-color language instead of introducing a new one.
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

    val currentIndex by remember {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, progress.position) }
    }

    val listState = rememberLazyListState()
    var followPlayback by remember { mutableStateOf(true) }
    // LazyListState has no built-in way to tell "user dragged" apart from "we scrolled it
    // programmatically" — isAutoScrolling is set around our own animateScrollToItem call so
    // the isScrollInProgress flip it causes isn't mistaken for the user grabbing the list.
    var isAutoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAutoScrolling) {
            followPlayback = false
        }
    }
    val centerOffsetPx = with(density) { (-160).dp.roundToPx() }
    LaunchedEffect(currentIndex, followPlayback, lines) {
        if (followPlayback && currentIndex in lines.indices) {
            isAutoScrolling = true
            listState.animateScrollToItem(currentIndex, centerOffsetPx)
            isAutoScrolling = false
        }
    }

    val nextSong = queueItems.getOrNull(currentMediaItemIndex + 1)
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar: small cover (tap to close) + title/subtitle + format badge + menu.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClose)
            ) {
                if (mediaMetadata?.thumbnailUrl != null) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mediaMetadata?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (currentFormat != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AudioFormatBadge(
                            format = currentFormat,
                            tint = Color.White,
                            height = 16.dp,
                            audioQuality = AudioQuality.AUTO,
                        )
                    }
                }
                Text(
                    text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (lines.isEmpty()) {
                Text(
                    text = if (lyricsEntity == null) "Loading lyrics…" else "No lyrics found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 220.dp),
                ) {
                    itemsIndexed(lines, key = { index, _ -> index }) { index, entry ->
                        val state = when {
                            index == currentIndex -> LyricsLineState.ACTIVE
                            index == currentIndex + 1 -> LyricsLineState.UPCOMING
                            index < currentIndex -> LyricsLineState.PAST
                            else -> LyricsLineState.DEFAULT
                        }
                        val romanized by entry.romanizedTextFlow.collectAsState()
                        LyricsGlowLine(
                            entry = entry,
                            state = state,
                            positionMs = progress.position,
                            accentColor = accent,
                            subLine = romanized,
                            onClick = {
                                playerConnection.player.seekTo(entry.time)
                                followPlayback = true
                            },
                        )
                    }
                }
            }

            // "Jump back to current line" — only shown once the user has scrolled away from
            // the auto-following position, same idea as Spotify's own lyrics pill.
            if (!followPlayback) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { followPlayback = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_less),
                        contentDescription = "Jump to current line",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, top = 56.dp)
                    .size(36.dp)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            PlayerProgressSlider()
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val transportState by playerConnection.uiState.transportState.collectAsState()
                AnimatedPressScaleSkipButton(
                    icon = R.drawable.avd_skip_previous,
                    onClick = playerConnection::seekToPrevious,
                    enabled = transportState.canSkipPrevious,
                    tint = Color.White,
                    iconSize = 40.dp,
                    modifier = Modifier.size(56.dp)
                )
                AnimatedPressScalePlayPauseButton(
                    isPlaying = transportState.isPlaying,
                    playbackState = transportState.playbackState,
                    onClick = {
                        if (transportState.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.togglePlayPause()
                        }
                    },
                    tint = Color.White,
                    iconSize = 40.dp,
                    modifier = Modifier.size(56.dp)
                )
                AnimatedPressScaleSkipButton(
                    icon = R.drawable.avd_skip_next,
                    onClick = playerConnection::seekToNext,
                    enabled = transportState.canSkipNext,
                    tint = Color.White,
                    iconSize = 40.dp,
                    modifier = Modifier.size(56.dp)
                )
            }

            if (nextSong != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { playerConnection.seekToNext() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (nextSong.artworkUri != null) {
                        AsyncImage(
                            model = nextSong.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next Song",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "${nextSong.title} - ${nextSong.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
