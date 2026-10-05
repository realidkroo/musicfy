// HeroCarousel.kt

package com.example.musicfy.ui.component

import android.content.Context
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicfy.R
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.UsernameKey
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.player.CanvasArtworkPlaybackCache
import com.example.musicfy.ui.player.CanvasArtworkPlayer
import com.example.musicfy.ui.player.normalizeCanvasArtistName
import com.example.musicfy.ui.player.normalizeCanvasSongTitle
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.DailyDiscoverItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** the hero takes this share of the screen height (527 of 874 in the Figma frame) */
const val HeroHeightFraction = 0.6f

// the first section starts inside the hero's faded-out bottom rather than under it, which puts
// its title 61dp below the Play pill like the Figma (it was 68)
private val HeroSectionTuck = 7.dp

// how far a page actually travels while it crossfades: a little drift reads as sliding,
// a full-width slide is what made two covers look stacked on each other mid-swipe
private const val HeroMediaTravel = 0.18f
private const val HeroTextTravel = 0.42f
private val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private const val HeroSlideMillis = 1150
private const val HeroAdvanceMillis = 5500L
private val HeroPillContent = Color(0xFF2F2F2F)
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroLabelColor = Color.White.copy(alpha = 0.69f)

// once the hero starts scrolling away its video holds still and the carousel stops turning, so
// the scroll's blur only ever sees a still frame
private const val HeroStillFrom = 0.08f

/** the small line above a hero title: [text], then a tiny [coverUrl] and the [subject] it's about */
@Immutable
data class HeroLabel(
    val text: String,
    val coverUrl: String? = null,
    val subject: String? = null,
)

sealed interface HeroCarouselItem {
    fun label(isPlaying: Boolean): HeroLabel
    val mainText: String
    val subText: String
    val thumbnailUrl: String?
    val mediaId: String?
    val songTitle: String?
    val artistName: String?
    val albumTitle: String?

    /** albums, artists and playlists open a page instead of playing straight away */
    val opensPage: Boolean get() = false

    fun onPlay(playerConnection: PlayerConnection, navController: NavController)
}

data class KeepListeningItem(val item: MediaMetadata, val isLastPlayed: Boolean = false) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel(
        when {
            isPlaying -> "Now playing"
            isLastPlayed -> "Continue"
            else -> "Recently played"
        }
    )
    override val mainText: String = item.title
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
            playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item))
        }
    }
}

/** why a recommendation is on the hero. it's picked per card, so neighbouring cards never say the same thing */
enum class DiscoverReason { YouMayAlsoLike, BecauseYouLike, MoreFrom, BecauseYouPlayed, InspiredBy }

/** a recommendation card, labelled with the song it grew out of (and that song's tiny cover) */
data class DiscoverItem(
    val item: DailyDiscoverItem,
    val reason: DiscoverReason = DiscoverReason.YouMayAlsoLike,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel {
        if (isPlaying) return HeroLabel("Now playing")
        val seed = item.seed
        val seedCover = seed.song.thumbnailUrl
        return when (reason) {
            DiscoverReason.YouMayAlsoLike -> HeroLabel("You may also like")
            DiscoverReason.BecauseYouLike -> HeroLabel("Because you like", seedCover, seed.song.title)
            DiscoverReason.MoreFrom -> HeroLabel(
                "More from",
                seedCover,
                sharedArtist(item) ?: seed.artists.firstOrNull()?.name ?: seed.song.title,
            )
            DiscoverReason.BecauseYouPlayed -> HeroLabel("Because you played", seedCover, seed.song.title)
            DiscoverReason.InspiredBy -> HeroLabel("Inspired by", seedCover, seed.song.title)
        }
    }
    override val mainText: String = item.recommendation.title
    override val subText: String = (item.recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: ""
    override val thumbnailUrl: String? = item.recommendation.thumbnail
    override val mediaId: String? = item.recommendation.id
    override val songTitle: String? = item.recommendation.title
    override val artistName: String? = (item.recommendation as? SongItem)?.artists?.joinToString { it.name } ?: ""
    override val albumTitle: String? = (item.recommendation as? SongItem)?.album?.name
    override val opensPage: Boolean = item.recommendation !is SongItem

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

/** the "Gacha here! / Random plays" card (Figma 25:1508): roll a reel of covers for a random song */
data object GachaItem : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("Gacha here!")
    override val mainText: String = "Random plays"
    override val subText: String = ""
    override val thumbnailUrl: String? = null
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) = Unit
}

/** an artist the recommendation shares with the song it came from, if there is one */
private fun sharedArtist(item: DailyDiscoverItem): String? {
    val rec = item.recommendation as? SongItem ?: return null
    return rec.artists.firstOrNull { artist ->
        item.seed.artists.any { seedArtist ->
            (artist.id != null && artist.id == seedArtist.id) || artist.name.equals(seedArtist.name, ignoreCase = true)
        }
    }?.name
}

/**
 * the wording for each recommendation: "You may also like" leads, then whatever is most specific
 * to that card (the same artist, a song you liked, a song you played), never the same phrase twice
 * in a row and no phrase more than twice in all.
 */
private fun discoverReasons(items: List<DailyDiscoverItem>): List<DiscoverReason> {
    val picked = ArrayList<DiscoverReason>(items.size)
    items.forEachIndexed { index, item ->
        val options = buildList {
            if (index == 0) add(DiscoverReason.YouMayAlsoLike)
            if (sharedArtist(item) != null) add(DiscoverReason.MoreFrom)
            if (item.seed.song.liked) add(DiscoverReason.BecauseYouLike)
            add(DiscoverReason.BecauseYouPlayed)
            add(DiscoverReason.InspiredBy)
            add(DiscoverReason.YouMayAlsoLike)
        }
        picked += options.firstOrNull { option ->
            option != picked.lastOrNull() && picked.count { it == option } < 2
        } ?: DiscoverReason.YouMayAlsoLike
    }
    return picked
}

@Composable
fun HeroCarousel(
    keepListening: List<LocalItem>?,
    lastPlayedSong: MediaMetadata?,
    dailyDiscover: List<DailyDiscoverItem>?,
    playerConnection: PlayerConnection,
    navController: NavController,
    heroScrollProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    gachaPool: List<MediaMetadata> = emptyList(),
) {
    val showGacha = gachaPool.size >= GachaMinPool
    val carouselItems = remember(keepListening, lastPlayedSong, dailyDiscover, showGacha) {
        buildList {
            lastPlayedSong?.let { add(KeepListeningItem(it, isLastPlayed = true)) }

            // the first two recommendations, then the gacha, then the rest
            val discover = dailyDiscover?.take(5).orEmpty()
            val reasons = discoverReasons(discover)
            discover.forEachIndexed { index, item ->
                add(DiscoverItem(item, reasons[index]))
                if (index == 1 && showGacha) add(GachaItem)
            }
            if (showGacha && discover.size < 2) add(GachaItem)

            val regular = count { it !is GachaItem }
            if (regular < 5) {
                keepListening?.filterIsInstance<com.example.musicfy.db.entities.Song>()
                    ?.filter { song -> none { it is KeepListeningItem && it.mediaId == song.id } }
                    ?.take(5 - regular)
                    ?.forEach { song ->
                        add(KeepListeningItem(song.toMediaMetadata(), isLastPlayed = false))
                    }
            }
        }
    }

    val heroHeight = (LocalConfiguration.current.screenHeightDp * HeroHeightFraction).dp

    // scrolling away it does exactly what a playlist cover does: rides up, zooms, goes soft, fades.
    // the list already carries it up, so it only adds a little on top
    ScrollAwayCover(
        progress = heroScrollProgressProvider,
        follow = 0.2f,
        softFrom = HeroStillFrom,
        modifier = modifier
            .layout { measurable, constraints ->
                // drawn full height, but the list only makes room for all but the tuck
                val placeable = measurable.measure(constraints)
                val tuck = HeroSectionTuck.roundToPx()
                layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) {
                    placeable.place(0, 0)
                }
            }
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // null while the first load is still out, so the bones get their reveal; an empty list
        // after loading means a fresh install, which gets the onboarding hero instead
        RevealWhenLoaded(
            data = carouselItems.takeIf { it.isNotEmpty() || !isLoading },
            modifier = Modifier.fillMaxSize(),
            bones = { HeroBones() },
        ) { items ->
            if (items.isEmpty()) {
                val (username) = rememberPreference(UsernameKey, defaultValue = "")
                OnboardingHero(
                    username = username,
                    onGetStarted = {
                        navController.navigate(Screens.Search.route) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                HeroPager(
                    items = items,
                    playerConnection = playerConnection,
                    navController = navController,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    gachaPool = gachaPool,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(
    items: List<HeroCarouselItem>,
    playerConnection: PlayerConnection,
    navController: NavController,
    heroScrollProgressProvider: () -> Float,
    gachaPool: List<MediaMetadata>,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val nowPlaying by playerConnection.mediaMetadata.collectAsState()

    // with one card an infinite pager wraps onto itself and auto-advances against nothing,
    // so a single card is just a static hero
    val isSingleItem = items.size == 1
    val pagerState = rememberPagerState(
        initialPage = if (isSingleItem) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % items.size),
        pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
    )

    // the gacha lives up here rather than in its page, so a roll survives the page being swiped
    // out of composition and back
    val gacha = remember { GachaState() }
    val sounds = remember { GachaSounds(context.applicationContext) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    // the reel's covers are picked once; they're only picked again (while it's idle) if there were
    // too few songs to fill it and more have turned up, so the covers never shuffle under your eyes
    LaunchedEffect(gachaPool) {
        if (gacha.phase != GachaPhase.Idle) return@LaunchedEffect
        val available = gachaPool.distinctBy { it.id }.size
        if (gacha.candidates.isEmpty() || (gacha.candidates.size < GachaCandidates && available > gacha.candidates.size)) {
            gacha.candidates = pickGachaCandidates(gachaPool)
        }
    }

    // every cover that passes the pointer ticks, like a case opening: a click, a buzz you can
    // feel, and the pointer knocked back a little
    LaunchedEffect(gacha) {
        var lastBuzz = 0L
        snapshotFlow { gacha.reel.value.roundToInt() }
            .drop(1)
            .collect {
                if (gacha.phase != GachaPhase.Rolling) return@collect
                sounds.tick()
                val now = SystemClock.uptimeMillis()
                if (now - lastBuzz >= 45L) {
                    lastBuzz = now
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                launch {
                    gacha.pointerKick.snapTo(1f)
                    gacha.pointerKick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
            }
    }

    fun roll() {
        if (gacha.phase == GachaPhase.Rolling || gacha.candidates.size < 2) return
        scope.launch {
            gacha.phase = GachaPhase.Rolling
            sounds.prepare()
            try {
                gacha.candidates = refreshHiddenCandidates(gacha, gachaPool)
                val candidates = gacha.candidates
                val count = candidates.size
                val winner = Random.nextInt(count)
                // a random length every time, nine seconds at the most
                val duration = Random.nextInt(GachaMinRollMillis, GachaMaxRollMillis + 1)
                val start = gacha.reel.value.roundToInt()
                gacha.reel.snapTo(start.toFloat())
                // whole turns first (more of them on a longer roll), then on to the winner
                val spins = count * (2 + duration / 2500)
                val landing = start + spins + (winner - (start + spins)).mod(count)
                gacha.reel.animateTo(landing.toFloat(), tween(duration, easing = GachaRollEasing))

                val won = candidates[winner]
                gacha.result = won
                gacha.phase = GachaPhase.Landed
                sounds.win()
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                launch {
                    gacha.landPop.snapTo(1f)
                    gacha.landPop.animateTo(1.1f, tween(120))
                    gacha.landPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f))
                }
                playerConnection.playQueue(YouTubeQueue.radio(won))
            } finally {
                if (gacha.phase == GachaPhase.Rolling) gacha.phase = GachaPhase.Idle
            }
        }
    }

    if (!isSingleItem) {
        val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(pagerState, items.size, isDragged, gacha.phase) {
            if (isDragged) return@LaunchedEffect
            // restarts after every manual swipe, so the next auto-advance never lands right on top of it
            while (true) {
                val onGacha = items[pagerState.currentPage.mod(items.size)] is GachaItem
                // the gacha card waits for you: twice as long while it's idle, and once it has
                // rolled it stays until you swipe on
                if (onGacha && gacha.phase != GachaPhase.Idle) return@LaunchedEffect
                delay(if (onGacha) HeroAdvanceMillis * 2 else HeroAdvanceMillis)
                // scrolled away, there's nothing to show the next card to
                if (heroScrollProgressProvider() > HeroStillFrom) continue
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1,
                    animationSpec = tween(HeroSlideMillis, easing = HeroSlideEasing),
                )
            }
        }

        // warm the next cover so a swipe never fades in from an empty page
        LaunchedEffect(pagerState.currentPage, items) {
            items[(pagerState.currentPage + 1).mod(items.size)].thumbnailUrl?.let { url ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context).data(url.resize(1200, 1200)).build()
                )
            }
        }
    }

    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val edgeBlur = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // a playing canvas repaints the hero, and both blur bands with it, on every video frame. while
    // Home is receding behind an opened playlist, or coming back from one, that lands on top of the
    // transition and makes it stutter, so the video holds still until Home has settled. coming back,
    // the player isn't even built until then: Home is composed fresh on the way back.
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    val navSettled by remember(navTransition) {
        derivedStateOf {
            navTransition == null ||
                (navTransition.currentState == EnterExitState.Visible && navTransition.targetState == EnterExitState.Visible)
        }
    }
    var canvasAttached by remember { mutableStateOf(false) }
    LaunchedEffect(navSettled) {
        if (navSettled) canvasAttached = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            // no swiping off a reel that's still spinning
            userScrollEnabled = !isSingleItem && gacha.phase != GachaPhase.Rolling,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page.mod(items.size)]
            if (item is GachaItem) {
                GachaPage(
                    state = gacha,
                    pageOffset = { pagerState.offsetOf(page) },
                    isResultPlaying = isPlaying && gacha.result != null && nowPlaying?.id == gacha.result?.id,
                    onShown = { sounds.prepare() },
                    onRoll = { roll() },
                )
            } else {
                HeroPage(
                    item = item,
                    pageOffset = { pagerState.offsetOf(page) },
                    isThisPlaying = isPlaying && nowPlaying?.id == item.mediaId,
                    isPagerMoving = { pagerState.isScrollInProgress },
                    canvasAttached = canvasAttached,
                    canvasMayPlay = navSettled,
                    edgeBlur = edgeBlur,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    onPrimaryClick = {
                        if (isPlaying && nowPlaying?.id == item.mediaId) {
                            playerConnection.pause()
                        } else {
                            item.onPlay(playerConnection, navController)
                        }
                    },
                )
            }
        }
    }
}

/** signed distance of [page] from the settled position: 0 on screen, +1 one page to the right */
private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction

@Composable
private fun HeroPage(
    item: HeroCarouselItem,
    pageOffset: () -> Float,
    isThisPlaying: Boolean,
    isPagerMoving: () -> Boolean,
    canvasAttached: Boolean,
    canvasMayPlay: Boolean,
    edgeBlur: Boolean,
    heroScrollProgressProvider: () -> Float,
    onPrimaryClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .heroPageCrossfade(pageOffset)
    ) {
        HeroMedia(
            item = item,
            edgeBlur = edgeBlur,
            isPagerMoving = isPagerMoving,
            canvasAttached = canvasAttached,
            canvasMayPlay = canvasMayPlay,
            heroScrollProgressProvider = heroScrollProgressProvider,
        )

        // per page and under the text: drawn once over the whole pager it would dim the titles too
        HeroScrims()

        Box(
            modifier = Modifier
                .matchParentSize()
                .heroTextDrift(pageOffset)
        ) {
            HeroText(
                label = item.label(isThisPlaying),
                title = if (item.subText.isNotEmpty()) "${item.mainText} - ${item.subText}" else item.mainText,
                pill = when {
                    item.opensPage -> HeroPillKind.View
                    isThisPlaying -> HeroPillKind.Pause
                    else -> HeroPillKind.Play
                },
                onPillClick = onPrimaryClick,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

// pages to the right fade in over the current one; the page underneath stays opaque, so the
// crossfade never dips through to black
private fun Modifier.heroPageCrossfade(pageOffset: () -> Float): Modifier = graphicsLayer {
    val d = pageOffset()
    alpha = 1f - d.coerceIn(0f, 1f)
    translationX = -d * size.width * (1f - HeroMediaTravel)
}

// text drifts further than the cover and is gone by half way, so the outgoing and incoming
// titles never sit on top of each other
private fun Modifier.heroTextDrift(pageOffset: () -> Float): Modifier = graphicsLayer {
    val d = pageOffset()
    translationX = d * size.width * HeroTextTravel
    alpha = (1f - abs(d) * 1.9f).coerceIn(0f, 1f)
}

@Composable
private fun HeroMedia(
    item: HeroCarouselItem,
    edgeBlur: Boolean,
    isPagerMoving: () -> Boolean,
    canvasAttached: Boolean,
    canvasMayPlay: Boolean,
    heroScrollProgressProvider: () -> Float,
) {
    val context = LocalContext.current
    val canvasEnabled by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val canvasWifiOnly by rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }

    LaunchedEffect(item.mediaId, canvasEnabled, canvasWifiOnly) {
        val id = item.mediaId ?: return@LaunchedEffect
        if (!canvasEnabled) return@LaunchedEffect

        val connectivityManager = context.getSystemService<android.net.ConnectivityManager>()
        if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) return@LaunchedEffect

        CanvasArtworkPlaybackCache.get(id)?.let {
            canvasArtwork = it
            return@LaunchedEffect
        }

        val song = normalizeCanvasSongTitle(item.songTitle ?: "")
        val artist = normalizeCanvasArtistName(item.artistName ?: "")
        if (song.isBlank() || artist.isBlank()) return@LaunchedEffect

        val storefront = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
        val fetched = withContext(Dispatchers.IO) {
            MonochromeApiCanvas.getBySongArtist(song, artist, item.albumTitle)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(song, artist, item.albumTitle, storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        }
        if (fetched != null) {
            canvasArtwork = fetched
            CanvasArtworkPlaybackCache.put(id, fetched)
        }
    }

    // derived so scrolling only recomposes when the hero starts moving, not on every frame. the
    // video stops as soon as it does, so the scroll's blur is working from a still frame
    val heroAtRest by remember { derivedStateOf { heroScrollProgressProvider() < HeroStillFrom } }

    val imageRequest = remember(item.thumbnailUrl, context) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl?.resize(1200, 1200))
            .crossfade(300)
            .build()
    }
    val mediaLayer = rememberGraphicsLayer()

    Box(modifier = Modifier.fillMaxSize()) {
        // the page's media is recorded once and drawn again, blurred, into the edge bands below.
        // they're siblings of this box, never inside it, so the layer can't end up drawing itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (edgeBlur) {
                        Modifier.drawWithContent {
                            mediaLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(mediaLayer)
                        }
                    } else Modifier
                )
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (canvasAttached && canvasEnabled && canvasArtwork?.preferredAnimationUrl != null) {
                CanvasArtworkPlayer(
                    primaryUrl = canvasArtwork?.preferredAnimationUrl,
                    fallbackUrl = null,
                    isPlaying = canvasMayPlay && heroAtRest && !isPagerMoving(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (edgeBlur) {
            HeroBlurBand(mediaLayer, atTop = true, heightFraction = 0.46f, radius = 22f)
            HeroBlurBand(mediaLayer, atTop = false, heightFraction = 0.5f, radius = 30f)
        }
    }
}

/**
 * progressive blur: the page's media drawn again, blurred, and faded out toward the middle of the
 * hero, so it's strongest right behind the top bar and behind the title and button.
 */
@Composable
private fun BoxScope.HeroBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
        }
    }
    Box(
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = BlurEffectCache.get(radius, Shader.TileMode.CLAMP)
                    clip = true
                }
                .drawBehind {
                    val dy = if (atTop) 0f else size.height - mediaLayer.size.height.toFloat()
                    translate(top = dy) { drawLayer(mediaLayer) }
                }
        )
    }
}

/** the Figma dim (the cover sits at 64% on black) and the top and bottom gradients */
@Composable
private fun BoxScope.HeroScrims() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val h = size.height
                val topEnd = h * 0.463f
                val bottomStart = h * 0.533f
                val bottomEnd = h * 0.989f
                val top = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.86f),
                    0.475f to Color.Black.copy(alpha = 0.59f),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = topEnd,
                )
                val bottom = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.457f to Color.Black.copy(alpha = 0.59f),
                    1f to Color.Black,
                    startY = bottomStart,
                    endY = bottomEnd,
                )
                onDrawBehind {
                    drawRect(Color.Black.copy(alpha = 0.36f))
                    drawRect(top, size = Size(size.width, topEnd))
                    drawRect(bottom, topLeft = Offset(0f, bottomStart), size = Size(size.width, h - bottomStart))
                }
            }
    )
}

/** label, title and pill, sitting on the hero's bottom left like the Figma */
@Composable
private fun HeroText(
    label: HeroLabel,
    title: String,
    pill: HeroPillKind,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier,
    pillEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.padding(start = HomeContentInset, end = HomeContentInset, bottom = 64.dp)
    ) {
        HeroLabelLine(label)
        AnimatedContent(
            targetState = title,
            transitionSpec = { heroLabelSwap() },
            label = "heroTitle",
        ) { text ->
            Text(
                text = text,
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(11.dp))
        HeroPill(kind = pill, onClick = onPillClick, enabled = pillEnabled)
    }
}

/** "Because you like [tiny cover] Song": the reason, then the song it grew out of */
@Composable
private fun HeroLabelLine(label: HeroLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.text,
            style = HomeLabelStyle,
            color = HeroLabelColor,
            maxLines = 1,
        )
        if (label.coverUrl != null) {
            Spacer(Modifier.width(6.dp))
            AsyncImage(
                model = label.coverUrl.resize(120, 120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(BoneColor),
            )
        }
        if (label.subject != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.subject,
                style = HomeLabelStyle,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private enum class HeroPillKind { Play, Pause, View, Roll, Rolling }

/**
 * the 33dp light pill from the Figma: dark glyph and label on white at 64%. it squishes under the
 * finger, and when it changes (play to pause, roll to rolling) the new content pops in while the
 * pill eases to its new width.
 */
@Composable
private fun HeroPill(
    kind: HeroPillKind,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(HeroPillFill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { heroGlyphSwap() },
            label = "heroPill",
        ) { current ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(33.dp)
                    .padding(
                        start = when (current) {
                            HeroPillKind.Play, HeroPillKind.Pause -> 21.dp
                            HeroPillKind.View -> 17.dp
                            HeroPillKind.Roll, HeroPillKind.Rolling -> 11.dp
                        },
                        end = if (current == HeroPillKind.Roll || current == HeroPillKind.Rolling) 13.dp else 17.dp,
                    )
            ) {
                when (current) {
                    HeroPillKind.Play, HeroPillKind.Pause -> {
                        Icon(
                            painter = painterResource(
                                if (current == HeroPillKind.Pause) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play
                            ),
                            contentDescription = null,
                            tint = HeroPillContent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    HeroPillKind.Roll, HeroPillKind.Rolling -> {
                        GachaDie(spinning = current == HeroPillKind.Rolling)
                        Spacer(Modifier.width(8.dp))
                    }
                    HeroPillKind.View -> Unit
                }
                Text(
                    text = when (current) {
                        HeroPillKind.Play -> "Play"
                        HeroPillKind.Pause -> "Pause"
                        HeroPillKind.View -> "View"
                        HeroPillKind.Roll -> "roll!!"
                        HeroPillKind.Rolling -> "rolling!!"
                    },
                    style = HomeLabelStyle,
                    color = HeroPillContent,
                )
                if (current == HeroPillKind.View) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        tint = HeroPillContent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun <S> AnimatedContentTransitionScope<S>.heroGlyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f)) + fadeIn(tween(130)))
        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.75f, stiffness = 520f) })

private fun <S> AnimatedContentTransitionScope<S>.heroLabelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
        .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
        .using(SizeTransform(clip = false))

/** what the hero looks like while the first load is still out */
@Composable
private fun HeroBones() {
    BonesHost(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Brush.verticalGradient(listOf(BoneColor.copy(alpha = 0.12f), Color.Transparent)))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = HomeContentInset, bottom = 64.dp)
            ) {
                Bone(width = 72.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Bone(width = 220.dp, height = 22.dp)
                Spacer(Modifier.height(13.dp))
                Bone(width = 93.dp, height = 33.dp, shape = RoundedCornerShape(50))
            }
        }
    }
}

// ---- the gacha card ---------------------------------------------------------------------------

// the reel's geometry, in Figma points on the 527pt tall hero (frame 25:1508); scaled to the real hero
private const val GachaDesignHeight = 527f
private const val GachaItemSize = 187f
private const val GachaFocusSize = 221f
private const val GachaPitch = 207f
private const val GachaFocusGap = 17f
private const val GachaCenterY = 230.5f
private const val GachaOverhang = 28f
private const val GachaCornerRadius = 25f
private const val GachaPointerX = 120f
private const val GachaPointerY = 236f
private const val GachaPointerWidth = 68f
private const val GachaPointerHeight = 75f

private const val GachaCandidates = 10
private const val GachaMinPool = 4
private const val GachaMinRollMillis = 3500
private const val GachaMaxRollMillis = 9000

// quick off the mark, then a long slow-down so the last few covers crawl past the pointer
private val GachaRollEasing = CubicBezierEasing(0.15f, 0.45f, 0.2f, 1f)

private val GachaPointerColor = Color(0xFFF0908D)
private val GachaPipColor = Color(0xFF3C3C3C)

// the pink-to-lavender behind the reel, before the hero's top and bottom shading
private val GachaBackground = Brush.verticalGradient(
    0f to Color(0xFFDEA0AC),
    0.27f to Color(0xFFD9A7B6),
    0.41f to Color(0xFFCEA9BF),
    0.52f to Color(0xFFC9AFC8),
    0.63f to Color(0xFFC0B1D0),
    0.77f to Color(0xFFB9B7DD),
    1f to Color(0xFFACB9DF),
)
private val GachaTopShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.85f),
    0.17f to Color.Black.copy(alpha = 0.6f),
    0.23f to Color.Black.copy(alpha = 0.47f),
    0.4f to Color.Transparent,
)
private val GachaBottomShade = Brush.verticalGradient(
    0.655f to Color.Transparent,
    0.695f to Color.Black.copy(alpha = 0.17f),
    0.787f to Color.Black.copy(alpha = 0.5f),
    0.882f to Color.Black.copy(alpha = 0.73f),
    0.975f to Color.Black.copy(alpha = 0.93f),
    1f to Color.Black,
)

private enum class GachaPhase { Idle, Rolling, Landed }

@Stable
private class GachaState {
    var candidates by mutableStateOf<List<MediaMetadata>>(emptyList())
    var phase by mutableStateOf(GachaPhase.Idle)
    var result by mutableStateOf<MediaMetadata?>(null)

    /** reel position in covers: the cover at `round(reel) mod count` sits under the pointer */
    val reel = Animatable(0f)
    val pointerKick = Animatable(0f)
    val landPop = Animatable(1f)
}

private fun pickGachaCandidates(pool: List<MediaMetadata>): List<MediaMetadata> =
    pool.distinctBy { it.id }.shuffled().take(GachaCandidates)

/** swaps the covers that are out of sight for fresh ones, so every roll has something new on it */
private fun refreshHiddenCandidates(state: GachaState, pool: List<MediaMetadata>): List<MediaMetadata> {
    val current = state.candidates
    if (current.isEmpty()) return pickGachaCandidates(pool)
    val spare = pool
        .distinctBy { it.id }
        .filter { song -> current.none { it.id == song.id } }
        .shuffled()
        .iterator()
    val reel = state.reel.value
    return current.mapIndexed { index, song ->
        if (abs(reelDistance(index, reel, current.size)) > 2.5f && spare.hasNext()) spare.next() else song
    }
}

/** where cover [index] sits relative to the pointer, in slots: 0 under it, +1 the one below */
private fun reelDistance(index: Int, reel: Float, count: Int): Float {
    var d = (reel - index) % count
    if (d < -count / 2f) d += count
    if (d >= count / 2f) d -= count
    return d
}

/** the reel's sounds: a dry click per cover and a chime for the winner. made on first use, kept for the hero's life */
private class GachaSounds(private val context: Context) {
    private var pool: SoundPool? = null
    private var tickSound = 0
    private var winSound = 0
    private var lastTick = 0L

    fun prepare() {
        if (pool != null) return
        val created = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        tickSound = created.load(context, R.raw.gacha_tick, 1)
        winSound = created.load(context, R.raw.gacha_win, 1)
        pool = created
    }

    fun tick() {
        // at full speed the covers pass faster than clicks can sound apart; it becomes a rattle
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 28L) return
        lastTick = now
        pool?.play(tickSound, 0.5f, 0.5f, 1, 0, 0.94f + Random.nextFloat() * 0.12f)
    }

    fun win() {
        pool?.play(winSound, 0.75f, 0.75f, 2, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
    }
}

/**
 * the gacha card: a column of covers running down the right edge with the coral pointer aimed at
 * the big one in the middle. roll (or tap that cover) and the reel spins, ticking past the pointer,
 * slows right down, and lands on a song that starts playing.
 */
@Composable
private fun GachaPage(
    state: GachaState,
    pageOffset: () -> Float,
    isResultPlaying: Boolean,
    onShown: () -> Unit,
    onRoll: () -> Unit,
) {
    val rolling = state.phase == GachaPhase.Rolling
    val result = state.result

    // the sounds load when the card is first seen, so the very first roll already clicks
    LaunchedEffect(Unit) { onShown() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .heroPageCrossfade(pageOffset)
            .clipToBounds()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val unit = maxHeight / GachaDesignHeight
            val focus = unit * GachaFocusSize
            val coverShape = RoundedCornerShape(unit * GachaCornerRadius)
            val candidates = state.candidates
            val count = candidates.size

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GachaBackground)
            )

            candidates.forEachIndexed { index, song ->
                key(song.id) {
                    AsyncImage(
                        model = song.thumbnailUrl?.resize(544, 544),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = unit * GachaOverhang, y = unit * GachaCenterY - focus / 2)
                            .size(focus)
                            .graphicsLayer {
                                val d = reelDistance(index, state.reel.value, count)
                                val near = (1f - abs(d)).coerceIn(0f, 1f)
                                val landed = if (near > 0.5f) state.landPop.value else 1f
                                val scale = (GachaItemSize + (GachaFocusSize - GachaItemSize) * near) / GachaFocusSize * landed
                                scaleX = scale
                                scaleY = scale
                                // anchored on the right edge, like the Figma, so the covers grow leftwards
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                val slots = d * GachaPitch + GachaFocusGap * d.coerceIn(-1f, 1f)
                                translationY = slots * unit.toPx()
                                alpha = if (abs(d) > 2.7f) 0f else 1f
                                shape = coverShape
                                clip = true
                            }
                            .background(Color.White),
                    )
                }
            }

            // the hero's own top and bottom shading, over the reel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(GachaTopShade)
                        drawRect(GachaBottomShade)
                    }
            )

            // the pointer, knocked back a little by every cover that ticks past it
            Icon(
                painter = painterResource(R.drawable.ic_untitled_play),
                contentDescription = null,
                tint = GachaPointerColor,
                modifier = Modifier
                    .offset(
                        x = unit * (GachaPointerX - GachaPointerWidth / 2f),
                        y = unit * (GachaPointerY - GachaPointerHeight / 2f),
                    )
                    .size(width = unit * GachaPointerWidth, height = unit * GachaPointerHeight)
                    .graphicsLayer {
                        val kick = state.pointerKick.value
                        translationX = -kick * 7.dp.toPx()
                        rotationZ = -kick * 9f
                    }
            )

            // the big cover is a roll button too
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = unit * GachaOverhang, y = unit * GachaCenterY - focus / 2)
                    .size(focus)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = !rolling,
                        onClick = onRoll,
                    )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .heroTextDrift(pageOffset)
        ) {
            HeroText(
                label = HeroLabel(
                    when {
                        result == null || rolling -> "Gacha here!"
                        isResultPlaying -> "Now playing"
                        else -> "You got"
                    }
                ),
                title = if (result == null || rolling) {
                    "Random plays"
                } else {
                    val artists = result.artists.joinToString(", ") { it.name }
                    if (artists.isNotEmpty()) "${result.title} - $artists" else result.title
                },
                pill = if (rolling) HeroPillKind.Rolling else HeroPillKind.Roll,
                pillEnabled = !rolling,
                onPillClick = onRoll,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/** the die on the roll pill: a white rounded square showing five. it tumbles while the reel spins */
@Composable
private fun GachaDie(spinning: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(620, easing = LinearEasing))
            }
        } else {
            // settle on the nearest face, with a little wobble
            val rest = (rotation.value / 90f).roundToInt() * 90f
            rotation.animateTo(rest, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    Canvas(
        modifier = Modifier
            .size(21.dp)
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        drawRoundRect(color = Color.White, cornerRadius = CornerRadius(4.5.dp.toPx()))
        val pip = 2.1.dp.toPx()
        val near = size.width * 0.28f
        val far = size.width * 0.72f
        val mid = size.width / 2f
        listOf(
            Offset(near, near), Offset(far, near),
            Offset(mid, mid),
            Offset(near, far), Offset(far, far),
        ).forEach { drawCircle(color = GachaPipColor, radius = pip, center = it) }
    }
}
