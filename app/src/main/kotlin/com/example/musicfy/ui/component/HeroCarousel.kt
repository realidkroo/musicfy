// HeroCarousel.kt
// this thing is for hero carousel

package com.example.musicfy.ui.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.playback.queues.LocalAlbumRadio
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.R
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.viewmodels.DailyDiscoverItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.media3.common.MediaMetadata
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.ui.player.CanvasArtworkPlaybackCache
import com.example.musicfy.ui.player.CanvasArtworkPlayer
import com.example.musicfy.ui.player.normalizeCanvasSongTitle
import com.example.musicfy.ui.player.normalizeCanvasArtistName
import java.util.Locale

sealed interface HeroCarouselItem {
    fun getTitleLabel(isPlaying: Boolean): String
    val mainText: String
    val subText: String
    val thumbnailUrl: String?
    val mediaId: String?
    val songTitle: String?
    val artistName: String?
    val albumTitle: String?
    
    fun onPlay(playerConnection: PlayerConnection, navController: NavController)
}

data class KeepListeningItem(val item: com.example.musicfy.models.MediaMetadata, val isLastPlayed: Boolean = false) : HeroCarouselItem {
    override fun getTitleLabel(isPlaying: Boolean): String = if (isPlaying) {
        "Now playing"
    } else if (isLastPlayed) {
        "Continue where you left from"
    } else {
        "Recently played"
    }
    override val mainText: String = item.title ?: ""
    override val subText: String = item.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? = item.thumbnailUrl
    override val mediaId: String? = item.id
    override val songTitle: String? = item.title
    override val artistName: String? = item.artists.joinToString { it.name }
    override val albumTitle: String? = item.album?.title
    
    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        if (isLastPlayed) {
            playerConnection.play()
        } else {
            val endpoint = item.id?.let { WatchEndpoint(videoId = it) }
            if (endpoint != null) {
                playerConnection.playQueue(YouTubeQueue(endpoint, item))
            }
        }
    }
}

data class DiscoverItem(val item: DailyDiscoverItem) : HeroCarouselItem {
    override fun getTitleLabel(isPlaying: Boolean): String = if (isPlaying) "Now playing" else "Sounds like ${item.seed.title}..."
    override val mainText: String = item.recommendation.title
    override val subText: String = (item.recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: ""
    override val thumbnailUrl: String? = item.recommendation.thumbnail
    override val mediaId: String? = item.recommendation.id
    override val songTitle: String? = item.recommendation.title
    override val artistName: String? = (item.recommendation as? SongItem)?.artists?.joinToString { it.name } ?: ""
    override val albumTitle: String? = (item.recommendation as? SongItem)?.album?.name
    
    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        when (val rec = item.recommendation) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(
                    rec.endpoint ?: WatchEndpoint(videoId = rec.id),
                    rec.toMediaMetadata()
                )
            )
            is AlbumItem -> navController.navigate("album/${rec.id}")
            is ArtistItem -> navController.navigate("artist/${rec.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${rec.id}")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    keepListening: List<LocalItem>?,
    lastPlayedSong: com.example.musicfy.models.MediaMetadata?,
    dailyDiscover: List<DailyDiscoverItem>?,
    playerConnection: PlayerConnection,
    navController: NavController,
    scrollOffsetProvider: () -> Float = { 0f },
    heroScrollProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val isPlaying by playerConnection.isPlaying.collectAsState()
    
    val carouselItems = remember(keepListening, lastPlayedSong, dailyDiscover) {
        buildList {
            // Add the last played song
            lastPlayedSong?.let { add(KeepListeningItem(it, isLastPlayed = true)) }
            
            // Add Daily Discover recommendations
            dailyDiscover?.take(5)?.forEach { add(DiscoverItem(it)) }
            
            // Fill the rest up to 5 items with keepListening history
            if (size < 5) {
                keepListening?.filterIsInstance<com.example.musicfy.db.entities.Song>()
                    ?.filter { song -> none { it is KeepListeningItem && it.mediaId == song.id } }
                    ?.take(5 - size)
                    ?.forEach { song ->
                        add(KeepListeningItem(song.toMediaMetadata(), isLastPlayed = false))
                    }
            }
        }
    }

    val carouselHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    if (carouselItems.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(carouselHeight))
        return
    }

    val initialPage = remember(carouselItems.size) {
        (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % carouselItems.size)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })

    LaunchedEffect(pagerState, carouselItems.size) {
        while (true) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val nextPage = pagerState.currentPage + 1
                pagerState.animateScrollToPage(nextPage, animationSpec = tween(800))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(carouselHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val realPage = page % carouselItems.size
            val item = carouselItems[realPage]
            val context = LocalContext.current
            var readyItem by remember { mutableStateOf(item) }

            LaunchedEffect(item) {
                if (readyItem != item) {
                    val url = item.thumbnailUrl?.resize(1200, 1200)
                    if (url != null) {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            .build()
                        context.imageLoader.execute(request)
                    }
                    readyItem = item
                }
            }

            androidx.compose.animation.AnimatedContent(
                targetState = readyItem,
                transitionSpec = {
                    (fadeIn(tween(800)) + scaleIn(initialScale = 0.8f, animationSpec = tween(800))) togetherWith
                    (fadeOut(tween(800)) + scaleOut(targetScale = 1.2f, animationSpec = tween(800)))
                },
                label = "HeroImageTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetItem ->
                var canvasArtwork by remember(targetItem.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                val storefront = remember {
                    val country = Locale.getDefault().country
                    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                }

                LaunchedEffect(targetItem.mediaId) {
                    val id = targetItem.mediaId ?: return@LaunchedEffect
                    CanvasArtworkPlaybackCache.get(id)?.let {
                        canvasArtwork = it
                        return@LaunchedEffect
                    }
                    
                    val s = normalizeCanvasSongTitle(targetItem.songTitle ?: "")
                    val a = normalizeCanvasArtistName(targetItem.artistName ?: "")
                    if (s.isBlank() || a.isBlank()) return@LaunchedEffect
                    
                    val fetched = withContext(Dispatchers.IO) {
                        MonochromeApiCanvas.getBySongArtist(s, a, targetItem.albumTitle)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: AppleMusicCanvasProvider.getBySongArtist(s, a, targetItem.albumTitle, storefront)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    }
                    if (fetched != null) {
                        canvasArtwork = fetched
                        CanvasArtworkPlaybackCache.put(id, fetched)
                    }
                }

                val progress by transition.animateFloat(
                    transitionSpec = { tween(800) },
                    label = "ItemProgress"
                ) { state -> if (state == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }

                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val parallaxModifier = Modifier.graphicsLayer {
                        translationY = scrollOffsetProvider() * 0.5f
                        val heroScrollProgress = heroScrollProgressProvider()
                        alpha = 1f - heroScrollProgress
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val rawBlur = (heroScrollProgress * 30f) + ((1f - progress) * 80f)
                            if (rawBlur > 0f) {
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    rawBlur, rawBlur, android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        }
                    }
                    
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(targetItem.thumbnailUrl?.resize(1200, 1200))
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(parallaxModifier)
                    )

                    if (canvasArtwork?.preferredAnimationUrl != null) {
                        val isVisible = heroScrollProgressProvider() < 0.5f
                        CanvasArtworkPlayer(
                            primaryUrl = canvasArtwork?.preferredAnimationUrl,
                            fallbackUrl = null,
                            isPlaying = isVisible && !pagerState.isScrollInProgress,
                            modifier = Modifier.fillMaxSize().then(parallaxModifier)
                        )
                    }
                }
            }
        }

        // Static dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f), // top dark tint
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black // completely black at the bottom to blend with background
                        )
                    )
                )
        )

        // Dynamic Text Overlay
        val currentMediaId by playerConnection.mediaMetadata.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            val offsetFraction = pagerState.currentPageOffsetFraction
            val currentPage = pagerState.currentPage

            val pagesToRender = buildList {
                add(currentPage to offsetFraction)
                if (offsetFraction > 0) {
                    add(currentPage + 1 to offsetFraction - 1f)
                } else if (offsetFraction < 0) {
                    add(currentPage - 1 to offsetFraction + 1f)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentMainPage = pagerState.currentPage
                val safeMainPage = (currentMainPage % carouselItems.size + carouselItems.size) % carouselItems.size
                val mainItem = carouselItems[safeMainPage]
                val isMainPlaying = isPlaying && currentMediaId?.id == mainItem.mediaId

                IconButton(
                    onClick = {
                        if (isMainPlaying) {
                            playerConnection.pause()
                        } else {
                            mainItem.onPlay(playerConnection, navController)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)) // frosted glass look
                ) {
                    androidx.compose.animation.AnimatedContent(targetState = isMainPlaying, label = "PlayPause") { playing ->
                        Icon(
                            painter = painterResource(if (playing) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play),
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    for ((pageIndex, pageOffset) in pagesToRender) {
                        val safeRealPage = (pageIndex % carouselItems.size + carouselItems.size) % carouselItems.size
                        val item = carouselItems[safeRealPage]
                        val absOffset = kotlin.math.abs(pageOffset)
                        val isThisItemPlaying = isPlaying && currentMediaId?.id == item.mediaId

                        Column(
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - absOffset
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val rawBlur = absOffset * 80f
                                    if (rawBlur > 0f) {
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                            rawBlur, rawBlur, android.graphics.Shader.TileMode.CLAMP
                                        ).asComposeRenderEffect()
                                    } else {
                                        renderEffect = null
                                    }
                                }
                            }
                        ) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = item.getTitleLabel(isThisItemPlaying),
                                label = "TitleLabel",
                                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }
                            ) { textLabel ->
                                Text(
                                    text = textLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (item.subText.isNotEmpty()) "${item.mainText} • ${item.subText}" else item.mainText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

    }
}
