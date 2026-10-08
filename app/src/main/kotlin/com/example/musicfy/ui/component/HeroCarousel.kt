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
import androidx.compose.ui.graphics.painter.ColorPainter
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

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

/**
 * One of the user's own playlists, put forward like any other recommendation; the pill plays it.
 * [play] does the loading, since the songs live in the database and this runs on a tap.
 */
class PlaylistPickItem(
    val id: String,
    val name: String,
    override val thumbnailUrl: String?,
    private val play: () -> Unit,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("From your playlists")
    override val mainText: String = name
    override val subText: String = ""
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) = play()

    override fun equals(other: Any?): Boolean = other is PlaylistPickItem && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * A brand-new install has nothing played yet, so the hero starts from what onboarding brought in:
 * the playlists just imported, or else the artists picked on "Let's build your home".
 */
data class StarterItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val detail: String,
    override val thumbnailUrl: String?,
) : HeroCarouselItem {
    enum class Kind { Playlist, Artist }

    override fun label(isPlaying: Boolean): HeroLabel =
        HeroLabel(if (kind == Kind.Playlist) "From your import" else "Recommended from")
    override val mainText: String = title
    override val subText: String = ""
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override val opensPage: Boolean = true

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        navController.navigate(if (kind == Kind.Playlist) "local_playlist/$id" else "artist/$id")
    }
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
    starterItems: List<StarterItem> = emptyList(),
    playlistPicks: List<PlaylistPickItem> = emptyList(),
) {
    val showGacha = gachaPool.size >= GachaMinPool
    val carouselItems = remember(keepListening, lastPlayedSong, dailyDiscover, showGacha, starterItems, playlistPicks) {
        buildList {
            lastPlayedSong?.let { add(KeepListeningItem(it, isLastPlayed = true)) }

            // the gacha comes straight after the first card, so the first auto-advance shows it;
            // buried behind the recommendations, most people never got that far
            if (showGacha) add(GachaItem)

            val discover = dailyDiscover?.take(5).orEmpty()
            val reasons = discoverReasons(discover)
            discover.forEachIndexed { index, item ->
                add(DiscoverItem(item, reasons[index]))
            }

            // the user's own playlists between the recommendations, a couple at a time
            addAll(playlistPicks.take(2))

            val regular = count { it !is GachaItem }
            if (regular < 5) {
                keepListening?.filterIsInstance<com.example.musicfy.db.entities.Song>()
                    ?.filter { song -> none { it is KeepListeningItem && it.mediaId == song.id } }
                    ?.take(5 - regular)
                    ?.forEach { song ->
                        add(KeepListeningItem(song.toMediaMetadata(), isLastPlayed = false))
                    }
            }

            // nothing played yet: start from the imported playlists or the picked artists
            if (none { it !is GachaItem }) addAll(starterItems.take(5))
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
                launch {
                    gacha.burst.snapTo(0.001f)
                    gacha.burst.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)))
                    gacha.burst.snapTo(0f)
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
                    // also held while the open player covers Home: it kept decoding and drawing
                    // frames under it. It picks up where it was once Home shows again.
                    isPlaying = canvasMayPlay && heroAtRest && !isPagerMoving() &&
                        !com.example.musicfy.LocalAppContentObscured.current.value,
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
    // One layer per band, at the resolution DownscaledBlur picks for the radius: the mask runs in
    // the blur's own effect instead of an offscreen layer around it, and the media is blurred from
    // a half-size copy. Both bands redraw with every frame of the hero's video, so it adds up.
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
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

private const val IdleStepSeconds = 1f / 30f
private const val IdleWakeMillis = 28L

private const val TopShadeEnd = 0.44f
private const val BottomShadeStart = 0.56f

private val WashStops = floatArrayOf(0f, 0.5f, 1f)
private val WanderStops = floatArrayOf(0f, 0.5f, 1f)
private val GlowStops = floatArrayOf(0f, 0.45f, 1f)

/**
 * A gradient made once at unit size and placed by its local matrix; it's only made again when one
 * of its three colours changes, so a still card stops allocating shaders.
 */
private class UnitShader(private val make: (IntArray) -> android.graphics.Shader) {
    private var cached: android.graphics.Shader? = null
    private var c0 = 0
    private var c1 = 0
    private var c2 = 0

    fun shader(a: Int, b: Int, c: Int, matrix: android.graphics.Matrix): android.graphics.Shader {
        val current = cached
        val shader = if (current != null && a == c0 && b == c1 && c == c2) {
            current
        } else {
            make(intArrayOf(a, b, c)).also {
                cached = it
                c0 = a
                c1 = b
                c2 = c
            }
        }
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/** Eases [from] toward [to], landing exactly once it's within a shade, so the colour stops changing. */
private fun settle(from: Color, to: Color, fraction: Float): Color {
    val next = lerp(from, to, fraction)
    return if (abs(next.red - to.red) < 0.002f && abs(next.green - to.green) < 0.002f &&
        abs(next.blue - to.blue) < 0.002f && abs(next.alpha - to.alpha) < 0.002f
    ) to else next
}

private val GachaPointerColor = Color(0xFFF0908D)
private val GachaPipColor = Color(0xFF3C3C3C)

/** the two colours the card takes from a cover: a lit one for the glow and a deep one for the wash */
@Immutable
private class GachaTint(val light: Color, val deep: Color)

// the Figma pink and lavender, until the covers' own colours are known
private val GachaDefaultTint = GachaTint(light = Color(0xFFDEA0AC), deep = Color(0xFF7C82B4))

/**
 * the lit and deep colours of a cover, from a tiny copy of it. the lit one is pushed bright and the
 * deep one dark, so even a grey cover gives the card something to glow with.
 */
private suspend fun gachaTint(context: Context, url: String): GachaTint? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(64)
        .allowHardware(false)
        .build()
    val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
    val lit = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch ?: return@withContext null
    val deep = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.mutedSwatch ?: lit
    GachaTint(
        light = shiftBrightness(lit.rgb, minValue = 0.78f, maxValue = 1f, maxSaturation = 0.8f),
        deep = shiftBrightness(deep.rgb, minValue = 0.28f, maxValue = 0.5f, maxSaturation = 0.85f),
    )
}

private fun shiftBrightness(rgb: Int, minValue: Float, maxValue: Float, maxSaturation: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceAtMost(maxSaturation)
    hsv[2] = hsv[2].coerceIn(minValue, maxValue)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * a black shade as a gradient whose alpha follows [alphaAt] across many stops. the old few-stop
 * shades bent sharply where a stop sat, which read as a visible edge; an eased curve has no corner
 * to see, and the paint is dithered so it doesn't band either.
 */
private fun easedShade(height: Float, alphaAt: (Float) -> Float): android.graphics.LinearGradient {
    val steps = 24
    val positions = FloatArray(steps + 1) { it / steps.toFloat() }
    val colors = IntArray(steps + 1) { android.graphics.Color.argb((alphaAt(positions[it]) * 255f).roundToInt(), 0, 0, 0) }
    return android.graphics.LinearGradient(0f, 0f, 0f, height, colors, positions, Shader.TileMode.CLAMP)
}

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

    /** a ring and a flare from the winning cover; runs 0 to 1 once per win */
    val burst = Animatable(0f)

    /** song id -> the colours of its cover */
    val tints = mutableStateMapOf<String, GachaTint>()
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
 * the big one in the middle. roll (or tap that cover) and the reel spins like a slot machine drum,
 * ticking past the pointer, slows right down, and lands on a song that starts playing.
 *
 * the card is coloured by whichever cover is under the pointer: its colours wash through the
 * background as the reel turns, a glow behind the big cover breathes while it waits and flares with
 * the speed, and a win sends a ring out from the winner. it all runs in the draw phase off a frame
 * clock that only ticks while the card is on screen.
 */
@Composable
private fun GachaPage(
    state: GachaState,
    pageOffset: () -> Float,
    isResultPlaying: Boolean,
    onShown: () -> Unit,
    onRoll: () -> Unit,
) {
    val context = LocalContext.current
    val rolling = state.phase == GachaPhase.Rolling
    val result = state.result

    // the sounds load when the card is first seen, so the very first roll already clicks
    LaunchedEffect(Unit) { onShown() }

    // every cover's colours, worked out once from a tiny copy
    val candidates = state.candidates
    LaunchedEffect(candidates) {
        candidates.forEach { song ->
            if (song.id in state.tints) return@forEach
            val url = song.thumbnailUrl ?: return@forEach
            gachaTint(context, url)?.let { state.tints[song.id] = it }
        }
    }

    // read only while drawing, so a new frame redraws the card without recomposing it
    val light = remember { mutableStateOf(GachaDefaultTint.light) }
    val deep = remember { mutableStateOf(GachaDefaultTint.deep) }
    val clock = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }

    // nor does one hidden under the open player
    val obscured = com.example.musicfy.LocalAppContentObscured.current
    LaunchedEffect(state) {
        var lastNanos = 0L
        var lastReel = state.reel.value
        var idleTime = 0f
        var wasBusy = false
        while (isActive) {
            // the pager keeps its neighbours composed; a card off to the side doesn't need frames
            if (abs(pageOffset()) >= 0.999f || obscured.value) {
                lastNanos = 0L
                snapshotFlow { abs(pageOffset()) < 0.999f && !obscured.value }.first { it }
                continue
            }
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
                lastNanos = now
                val reel = state.reel.value
                val count = state.candidates.size
                // covers per second, smoothed so the stretch and flare ease in and out with the spin
                val instant = abs(reel - lastReel) / dt.coerceAtLeast(1e-3f)
                speed.floatValue += (instant - speed.floatValue) * (1f - exp(-dt / 0.12f))
                lastReel = reel

                // the colours under the pointer, blended between the two covers either side of it.
                // the background trails them a little, so a fast spin washes rather than flashes
                val target = if (count == 0) {
                    GachaDefaultTint
                } else {
                    val base = floor(reel).toInt()
                    val between = reel - base
                    val from = state.tints[state.candidates[base.mod(count)].id] ?: GachaDefaultTint
                    val to = state.tints[state.candidates[(base + 1).mod(count)].id] ?: from
                    GachaTint(lerp(from.light, to.light, between), lerp(from.deep, to.deep, between))
                }
                val busy = speed.floatValue > 0.05f || state.burst.value > 0f
                // at rest the slow sway and breathing only need ~30 steps a second: a step is
                // under 2px of a soft gradient, too small to see, at a quarter of 120 Hz's redraws
                idleTime += dt
                if (busy || idleTime >= IdleStepSeconds) {
                    val step = if (busy) dt else idleTime
                    idleTime = 0f
                    val follow = 1f - exp(-step / 0.22f)
                    light.value = settle(light.value, target.light, follow)
                    deep.value = settle(deep.value, target.deep, follow)
                    clock.floatValue += step
                }
                wasBusy = busy
            }
            // idle, there's no need to wake on every vsync just to wait out the step
            if (!wasBusy) delay(IdleWakeMillis)
        }
    }

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
            val count = candidates.size

            // the wash, the glows and the win ring. Its own layer, so its per-frame redraw doesn't
            // re-record the reel or the shading. The gradients are built once at unit size and only
            // re-coloured when the colours actually move; position and size change through each
            // shader's matrix, so an idle card allocates nothing per frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val focusPx = focus.toPx()
                        val overhangPx = (unit * GachaOverhang).toPx()
                        val glowX = w + overhangPx - focusPx / 2f
                        val glowY = (unit * GachaCenterY).toPx()
                        val washPaint = android.graphics.Paint().apply { isDither = true }
                        val wanderPaint = android.graphics.Paint().apply { isDither = true }
                        val glowPaint = android.graphics.Paint().apply { isDither = true }
                        val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                        }
                        val wash = UnitShader { colors -> android.graphics.LinearGradient(0f, 0f, 1f, 0f, colors, WashStops, Shader.TileMode.CLAMP) }
                        val wander = UnitShader { colors -> android.graphics.RadialGradient(0f, 0f, 1f, colors, WanderStops, Shader.TileMode.CLAMP) }
                        val glow = UnitShader { colors -> android.graphics.RadialGradient(0f, 0f, 1f, colors, GlowStops, Shader.TileMode.CLAMP) }
                        val matrix = android.graphics.Matrix()
                        val values = FloatArray(9)
                        onDrawBehind {
                            val lit = light.value
                            val dark = deep.value
                            val t = clock.floatValue
                            val rush = (speed.floatValue / 30f).coerceIn(0f, 1f)
                            val win = state.burst.value
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                // the deep colour fills the card and lightens towards the top right,
                                // the light swaying slowly so the card never sits still
                                val sway = sin(t * 0.35f) * w * 0.12f
                                val x0 = w * 0.85f + sway
                                val dx = (w * 0.15f - sway) - x0
                                val dy = h
                                // a similarity transform keeps the unit gradient's bands at right angles
                                values[0] = dx; values[1] = -dy; values[2] = x0
                                values[3] = dy; values[4] = dx; values[5] = 0f
                                values[6] = 0f; values[7] = 0f; values[8] = 1f
                                matrix.setValues(values)
                                washPaint.shader = wash.shader(
                                    lerp(dark, lit, 0.55f).toArgb(),
                                    lerp(dark, lit, 0.22f).toArgb(),
                                    dark.toArgb(),
                                    matrix,
                                )
                                native.drawRect(0f, 0f, w, h, washPaint)

                                // a second light wandering on the left, under the text
                                val wanderX = w * (0.18f + 0.08f * sin(t * 0.23f))
                                val wanderY = h * (0.55f + 0.1f * sin(t * 0.31f + 1.3f))
                                val wanderR = w * 0.7f
                                matrix.setScale(wanderR, wanderR)
                                matrix.postTranslate(wanderX, wanderY)
                                wanderPaint.shader = wander.shader(
                                    lit.copy(alpha = 0.32f).toArgb(),
                                    lit.copy(alpha = 0.1f).toArgb(),
                                    Color.Transparent.toArgb(),
                                    matrix,
                                )
                                native.drawCircle(wanderX, wanderY, wanderR, wanderPaint)

                                // the glow behind the big cover: breathing at rest, swelling with the
                                // spin, flaring on a win
                                val breathe = 0.05f * sin(t * 1.4f)
                                val flare = if (win > 0f) sin(win * PI.toFloat()) * 0.55f else 0f
                                val glowR = focusPx * (1.05f + breathe + rush * 0.35f + flare)
                                val glowY2 = glowY + sin(t * 0.8f) * focusPx * 0.04f
                                matrix.setScale(glowR, glowR)
                                matrix.postTranslate(glowX, glowY2)
                                glowPaint.shader = glow.shader(
                                    lit.copy(alpha = (0.75f + rush * 0.2f).coerceAtMost(1f)).toArgb(),
                                    lit.copy(alpha = 0.32f).toArgb(),
                                    Color.Transparent.toArgb(),
                                    matrix,
                                )
                                native.drawCircle(glowX, glowY2, glowR, glowPaint)

                                // the win: a ring that leaves the winner and fades as it grows
                                if (win > 0f && win < 1f) {
                                    ringPaint.color = lerp(lit, Color.White, 0.5f).copy(alpha = (1f - win) * 0.9f).toArgb()
                                    ringPaint.strokeWidth = (1f - win) * 10.dp.toPx() + 1.dp.toPx()
                                    native.drawCircle(glowX, glowY, focusPx * (0.55f + win * 1.1f), ringPaint)
                                }
                            }
                        }
                    }
            )

            // white until a cover loads, as a painter rather than a background behind it: one draw
            // per cover means a fading cover can simply draw at lower alpha, instead of being
            // rendered offscreen and composited every frame (each one a full tile flush on Mali)
            val blankCover = remember { ColorPainter(Color.White) }
            candidates.forEachIndexed { index, song ->
                key(song.id) {
                    AsyncImage(
                        model = song.thumbnailUrl?.resize(544, 544),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = blankCover,
                        error = blankCover,
                        fallback = blankCover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = unit * GachaOverhang, y = unit * GachaCenterY - focus / 2)
                            .size(focus)
                            .graphicsLayer {
                                val d = reelDistance(index, state.reel.value, count)
                                val near = (1f - abs(d)).coerceIn(0f, 1f)
                                val landed = if (near > 0.5f) state.landPop.value else 1f
                                val scale = (GachaItemSize + (GachaFocusSize - GachaItemSize) * near) / GachaFocusSize * landed
                                // a fast reel stretches its covers a touch along the spin
                                val stretch = 1f + (speed.floatValue / 120f).coerceAtMost(0.1f)
                                scaleX = scale
                                scaleY = scale * stretch
                                // anchored on the right edge, like the Figma, so the covers grow leftwards
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                val slots = d * GachaPitch + GachaFocusGap * d.coerceIn(-1f, 1f)
                                translationY = slots * unit.toPx()
                                // a drum: covers tip away as they leave the middle
                                rotationX = (-d * 18f).coerceIn(-50f, 50f)
                                cameraDistance = 14f * density
                                // fade out towards the ends instead of popping out of sight
                                alpha = (2.7f - abs(d)).coerceIn(0f, 1f)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                shape = coverShape
                                clip = true
                            },
                    )
                }
            }

            // the hero's own top and bottom shading, over the reel. Never changes, so it's recorded
            // once in its own layer. Each shade is clear beyond 44% of the height, so it's only
            // drawn over its own part: under half the area of two full-height fills
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val h = size.height
                        val shadePaint = android.graphics.Paint().apply { isDither = true }
                        val top = easedShade(h) { y -> 0.85f * (1f - smoothstep(0f, TopShadeEnd, y)).let { it * it } }
                        val bottom = easedShade(h) { y -> smoothstep(BottomShadeStart, 1f, y) }
                        onDrawBehind {
                            drawIntoCanvas { canvas ->
                                shadePaint.shader = top
                                canvas.nativeCanvas.drawRect(0f, 0f, size.width, h * TopShadeEnd, shadePaint)
                                shadePaint.shader = bottom
                                canvas.nativeCanvas.drawRect(0f, h * BottomShadeStart, size.width, h, shadePaint)
                            }
                        }
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
                        // a little pulse when it lands
                        val win = state.burst.value
                        val pop = if (win > 0f) 1f + 0.18f * sin(win * PI.toFloat()) else 1f
                        scaleX = pop
                        scaleY = pop
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
