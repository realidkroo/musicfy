// RecapStory.kt
//
// The monthly recap: everything after the barcode is scratched. It runs in a full-screen dialog
// window so it can cover the nav bar and mini player, and it starts and ends on exactly what the
// Musicfy page is showing - the same card, the same giant column, the same dots - so the window
// never shows as a seam: it fades in over an identical picture, and on the way out it shrinks back
// into the page header before it goes.
//
// One script (StoryDirector.run) drives every beat. Anything that lives across pages - the backdrop,
// the giant words, the avatar, the logo, the card - is a set of Animatables in StoryState; each page
// gets an enter and exit 0..1 and lays itself out from those. All of it is read in draw/layer
// lambdas, so a running story redraws without recomposing.

package com.example.musicfy.ui.screens.recap

import androidx.compose.ui.unit.IntSize
import android.os.SystemClock
import com.example.musicfy.ui.component.rememberDeviceTilt
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.ui.screens.setup.onboarding.IdCardAspect
import com.example.musicfy.ui.screens.setup.onboarding.ProfileIdCard
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// What the story is about, and where it grows from

@Immutable
internal class RecapStoryData(
    val stats: ListeningStats,
    val monthProgress: Int,
    val username: String,
    val photo: Any?,
    val cardNumber: String,
    val joinedText: String,
    val joinedDate: LocalDate,
    val allTimeMs: Long,
) {
    val name: String get() = username.trim().ifEmpty { "you" }
    /** "October" - or "2026" when a forced recap had to fall back to the whole year. */
    val monthName: String get() = stats.range.label
    val artists: List<RecapArtist> = stats.topArtists.take(10)
    val songs: List<RecapSong> = stats.topSongs
    val heroArtist: RecapArtist? = artists.firstOrNull()
    val heroSong: RecapSong? = songs.firstOrNull()

    /** Monday = 0. */
    val favouriteDay: Int = stats.weekdayMs.indices.maxByOrNull { stats.weekdayMs[it] } ?: 0
    val favouriteDayMs: Long = stats.weekdayMs.getOrElse(favouriteDay) { 0L }
    val runnerUpMs: Long = stats.weekdayMs.filterIndexed { i, _ -> i != favouriteDay }.maxOrNull() ?: 0L
}

/** Screen-space (px) positions of the page the story starts from and goes back into. */
@Immutable
internal class RecapAnchors(
    /** The card on the expanded page, where the story picks it up. */
    val expandedCard: Rect,
    /** The card in the collapsed header, where the story leaves it. */
    val headerCard: Rect,
    val headerHeight: Float,
    val topInset: Float,
)

internal const val StoryPageCount = 11
internal const val P_HEY = 0
internal const val P_JOURNEY = 1
internal const val P_MONTH = 2
internal const val P_WEEKDAY = 3
internal const val P_ARTISTS = 4
internal const val P_ARTIST = 5
internal const val P_SONGS = 6
internal const val P_MELODY = 7
internal const val P_SONG = 8
internal const val P_RECEIPT = 9
internal const val P_END = 10

internal val StoryDark = Color(0xFF0C0C0C)
internal val HeaderGrey = Color(0xFF1C1C1C)
internal val StoryTextDark = Color(0xFF111111)
internal val HeaderText = Color(0xFF272727)
internal val StoryDots = Color(0xFF171717)
internal val HeaderDots = Color(0xFF262626)

internal val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

internal suspend fun Animatable<Float, AnimationVector1D>.go(target: Float, ms: Int, easing: Easing = Emphasized, delayMs: Int = 0) {
    animateTo(target, tween(ms, delayMs, easing))
}

private suspend fun together(vararg blocks: suspend CoroutineScope.() -> Unit) = coroutineScope {
    blocks.forEach { launch(block = it) }
}

@Stable
internal class PageAnim {
    val enter = Animatable(0f)
    val exit = Animatable(0f)
}

/** Sizes on the actual screen, plus a mapping from the 852dp-tall mock. */
@Immutable
internal class StoryGeometry(val width: Float, val height: Float, val top: Float, val density: Float) {
    fun dp(v: Float): Float = v * density

    /** A y from the mock (an 852dp-tall phone), proportionally on this one. */
    fun fy(mockDp: Float): Float = mockDp / 852f * height

    val inset: Float get() = dp(36f)

    /** Avatar centre x, centre y and diameter for a page. */
    fun avatar(page: Int): FloatArray = when (page) {
        P_HEY -> floatArrayOf(width / 2f, height * 0.43f, dp(127f))
        P_JOURNEY -> slot(dp(33f), top + dp(95f), dp(91f))
        P_MONTH -> slot(dp(33f), top + dp(106f), dp(143f))
        P_WEEKDAY -> slot(dp(33f), top + dp(91f), dp(85f))
        else -> slot(inset, top + dp(31f), dp(36f))
    }

    private fun slot(left: Float, top: Float, d: Float) = floatArrayOf(left + d / 2f, top + d / 2f, d)
}

/** Every animated value that outlives a single page. */
@Stable
internal class StoryState(val geo: StoryGeometry, startCard: Rect) {
    val reveal = Animatable(0f)
    val backdrop = Animatable(1f)

    /** 1 = the whole screen; 0 = closed down to a horizontal band. */
    val band = Animatable(1f)

    /** 0 = the whole screen; 1 = clipped to the page header (the way back). */
    val headerClip = Animatable(0f)

    /** Colours: 0 = story dark, 1 = page header grey. */
    val toHeader = Animatable(0f)
    val mesh = Animatable(0f)
    val dots = Animatable(1f)
    var paletteFrom by mutableStateOf(RecapPalette.Fallback)
    var paletteTo by mutableStateOf(RecapPalette.Fallback)
    val paletteMix = Animatable(1f)

    val text = GiantTextState("MUSICFY", half = 4, filledRows = 0..0).also { it.color = { textColor() } }
    val logoSide = Animatable(0f)

    val cardX = Animatable(startCard.left)
    val cardY = Animatable(startCard.top)
    val cardRot = Animatable(0f)
    val cardAlpha = Animatable(1f)
    var cardBack by mutableStateOf(true)
    val scan = Animatable(0f)

    val avX = Animatable(geo.avatar(P_HEY)[0])
    val avY = Animatable(geo.avatar(P_HEY)[1])
    val avD = Animatable(geo.avatar(P_HEY)[2])
    val avRing = Animatable(0f)
    val avRingAlpha = Animatable(1f)
    val avAlpha = Animatable(0f)
    val avPop = Animatable(0f)

    val pages = List(StoryPageCount) { PageAnim() }
    var page by mutableIntStateOf(-1)
    var swipeOpen by mutableStateOf(false)
    var dragX by mutableFloatStateOf(0f)

    // artists
    val spin = Animatable(-2.6f)
    val spinGlow = Animatable(0f)
    val heroArtist = Animatable(0f)

    // songs
    val gridDrift = Animatable(0f)
    val gridPick = Animatable(0f)
    val heroCover = Animatable(0f)
    val heroCoverUp = Animatable(0f)

    fun palette(): RecapPalette = lerp(paletteFrom, paletteTo, paletteMix.value)

    fun backgroundColor(): Color = lerp(StoryDark, HeaderGrey, toHeader.value)

    fun textColor(): Color {
        val dark = lerp(StoryTextDark, HeaderText, toHeader.value)
        return lerp(dark, Color.White.copy(alpha = 0.06f), mesh.value)
    }

    fun dotColor(): Color = lerp(StoryDots, HeaderDots, toHeader.value)

    suspend fun crossfadePalette(to: RecapPalette, ms: Int) {
        paletteFrom = palette()
        paletteTo = to
        paletteMix.snapTo(0f)
        paletteMix.go(1f, ms, FastOutSlowInEasing)
    }

    suspend fun avatarTo(page: Int, ms: Int = 900, delayMs: Int = 0) = together(
        { avX.go(geo.avatar(page)[0], ms, delayMs = delayMs) },
        { avY.go(geo.avatar(page)[1], ms, delayMs = delayMs) },
        { avD.go(geo.avatar(page)[2], ms, delayMs = delayMs) },
    )
}

internal enum class StoryGesture { SwipeLeft, SwipeDown, Tap }

// a page has been up at least this long before a tap moves the story on
private const val MinPageMillis = 350L

// ---------------------------------------------------------------------------------------------
// The window

/**
 * Shows the recap over everything. [onCovered]: the story now hides the page completely, so the
 * page may reset itself underneath. [onReturned]: the story is drawing exactly the collapsed header,
 * so the page should show its own card again. [onClosed]: the window is going away.
 */
@Composable
internal fun RecapStoryHost(
    data: RecapStoryData,
    anchors: RecapAnchors,
    payload: String,
    caption: String,
    onCovered: () -> Unit,
    onReturned: () -> Unit,
    onClosed: () -> Unit,
) {
    val exitRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    Dialog(
        onDismissRequest = { exitRequests.trySend(Unit) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.run {
                setWindowAnimations(0)
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                // without this flag the system paints both bars solid black behind a dialog, whatever
                // their colour is set to; with it (and the translucent flags off) they show the story
                @Suppress("DEPRECATION")
                clearFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                )
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                @Suppress("DEPRECATION")
                statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= 28) {
                    attributes = attributes.also {
                        it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
                // a dialog's window is laid out between the status and navigation bars by default,
                // which left both bars black over the story; this takes it to the screen's edges
                if (Build.VERSION.SDK_INT >= 30) {
                    attributes = attributes.also { it.fitInsetsTypes = 0 }
                } else {
                    addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                }
                WindowCompat.setDecorFitsSystemWindows(this, false)
                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }

        // the real queue stops while the recap plays its own snippets, and picks up after
        val playerConnection = LocalPlayerConnection.current
        DisposableEffect(playerConnection) {
            val player = playerConnection?.player
            val wasPlaying = player?.isPlaying == true
            player?.pause()
            onDispose { if (wasPlaying) player?.play() }
        }

        RecapStory(data, anchors, payload, caption, exitRequests, onCovered, onReturned, onClosed)
    }
}

@Composable
private fun RecapStory(
    data: RecapStoryData,
    anchors: RecapAnchors,
    payload: String,
    caption: String,
    exitRequests: Channel<Unit>,
    onCovered: () -> Unit,
    onReturned: () -> Unit,
    onClosed: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val time = rememberRecapTime()
    val measurer = rememberTextMeasurer()
    val currentOnCovered by rememberUpdatedState(onCovered)
    val currentOnReturned by rememberUpdatedState(onReturned)
    val currentOnClosed by rememberUpdatedState(onClosed)
    val gestures = remember { Channel<StoryGesture>(Channel.CONFLATED) }
    val snippet = remember { RecapSnippetPlayer(context, scope) }
    DisposableEffect(snippet) { onDispose { snippet.release() } }

    var origin by remember { mutableStateOf<Offset?>(null) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    var settled by remember { mutableStateOf(false) }
    // the window reaches the screen's edges a frame or two after it opens (see RecapStoryHost);
    // the story is laid out against where it ends up, once it has stopped moving
    LaunchedEffect(Unit) {
        var steady = 0
        var frames = 0
        var lastOrigin: Offset? = null
        var lastSize = IntSize.Zero
        while (steady < 2 && frames < 12) {
            withFrameNanos { }
            frames++
            if (origin != null && origin == lastOrigin && windowSize == lastSize) steady++ else steady = 0
            lastOrigin = origin
            lastSize = windowSize
        }
        settled = true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                if (!settled) {
                    origin = it.positionOnScreen()
                    windowSize = it.size
                }
            },
    ) {
        val o = origin?.takeIf { settled } ?: return@BoxWithConstraints
        val geo = remember(constraints.maxWidth, constraints.maxHeight) {
            StoryGeometry(
                width = constraints.maxWidth.toFloat(),
                height = constraints.maxHeight.toFloat(),
                top = anchors.topInset - o.y,
                density = density.density,
            )
        }
        // the page's screen positions, in this window's coordinates
        val local = remember(o) { { r: Rect -> r.translate(-o.x, -o.y) } }
        val startCard = remember(o) { local(anchors.expandedCard) }
        val headerCard = remember(o) { local(anchors.headerCard) }
        val headerBottom = anchors.headerHeight - o.y
        val s = remember(geo) { StoryState(geo, startCard) }
        val director = remember(s) {
            StoryDirector(s, data, gestures, snippet, haptics::performHapticFeedback, startCard, headerCard)
        }

        // palettes and pictures, fetched before they're needed
        LaunchedEffect(data) {
            val urls = data.artists.map { it.thumbnailUrl } + data.songs.map { it.thumbnailUrl } + data.heroSong?.artistThumbnailUrl
            urls.filterNotNull().distinct().forEach { url ->
                context.imageLoader.enqueue(ImageRequest.Builder(context).data(url.resize(544, 544)).build())
            }
            director.artistPalette = RecapPalettes.extract(context, data.heroArtist?.thumbnailUrl ?: data.heroSong?.thumbnailUrl)
            director.songPalette = RecapPalettes.extract(context, data.heroSong?.thumbnailUrl)
        }

        LaunchedEffect(director) {
            val script = launch { director.run(onCovered = { currentOnCovered() }) }
            val exit = launch {
                // back only counts once the page underneath has been put away
                while (true) {
                    exitRequests.receive()
                    if (director.covered) break
                }
                script.cancel()
            }
            script.join()
            exit.cancel()
            if (script.isCancelled) director.abortToPage() else director.returnToPage()
            currentOnReturned()
            // the page draws its card on its next frame; leave ours over it until then
            withFrameNanos { }
            withFrameNanos { }
            s.backdrop.go(0f, 260, LinearEasing)
            currentOnClosed()
        }

        val swipeThreshold = with(density) { 56.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = s.reveal.value }
                .drawBehind { drawRect(Color.Black, alpha = s.backdrop.value) }
                .pointerInput(s) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDragEnd = {
                            val horizontal = abs(total.x) > abs(total.y)
                            when {
                                horizontal && total.x < -swipeThreshold -> gestures.trySend(StoryGesture.SwipeLeft)
                                !horizontal && total.y > swipeThreshold -> gestures.trySend(StoryGesture.SwipeDown)
                            }
                            scope.launch { animateDragBack(s) }
                        },
                        onDragCancel = { scope.launch { animateDragBack(s) } },
                    ) { change, amount ->
                        change.consume()
                        total += amount
                        if (s.swipeOpen && abs(total.x) > abs(total.y)) {
                            // the page leans after the finger, with resistance, only towards "next"
                            s.dragX = (total.x * 0.28f).coerceIn(-geo.dp(70f), geo.dp(14f))
                        }
                    }
                }
                .pointerInput(s) { detectTapGestures(onTap = { gestures.trySend(StoryGesture.Tap) }) },
        ) {
            // the scene, clipped to the band on the way in and to the header on the way out
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val path = Path()
                        val corner = 26.dp.toPx()
                        onDrawWithContent {
                            val hc = s.headerClip.value
                            val b = s.band.value
                            when {
                                hc > 0f -> {
                                    val bottom = lerpF(size.height, headerBottom, hc)
                                    val r = corner * hc
                                    path.reset()
                                    path.addRoundRect(
                                        RoundRect(
                                            rect = Rect(0f, 0f, size.width, bottom),
                                            bottomLeft = CornerRadius(r),
                                            bottomRight = CornerRadius(r),
                                        ),
                                    )
                                    clipPath(path) { this@onDrawWithContent.drawContent() }
                                }
                                b < 1f -> {
                                    val cy = size.height * 0.6f
                                    val half = lerpF(geo.dp(54f), max(cy, size.height - cy), b)
                                    clipRect(0f, cy - half, size.width, cy + half) { this@onDrawWithContent.drawContent() }
                                }
                                else -> drawContent()
                            }
                        }
                    },
            ) {
                // backdrop colour, the moving palette, the words, the dots
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(s.backgroundColor())
                            val vignette = (1f - s.mesh.value) * (1f - s.toHeader.value)
                            if (vignette > 0.01f) {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                        startY = size.height * 0.7f,
                                        endY = size.height,
                                    ),
                                    topLeft = Offset(0f, size.height * 0.7f),
                                    size = Size(size.width, size.height * 0.3f),
                                    alpha = vignette,
                                )
                            }
                        },
                )
                MeshGradient(palette = { s.palette() }, time = time, alpha = { s.mesh.value }, modifier = Modifier.fillMaxSize())
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .giantText(s.text, time, measurer)
                        .recapDots(color = { s.dotColor() }, alpha = { s.dots.value }),
                )

                StoryPages(s, data, time)

                StoryLogo(s)
                StoryAvatar(s, data)
                StoryCard(s, data, payload, caption, time, cardWidth = startCard.width)
            }
        }
    }
}

private suspend fun animateDragBack(s: StoryState) {
    val a = Animatable(s.dragX)
    a.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { s.dragX = value }
}

// ---------------------------------------------------------------------------------------------
// Persistent elements

@Composable
private fun StoryLogo(s: StoryState) {
    val geo = s.geo
    Icon(
        painter = painterResource(R.drawable.ic_musicfy_mark),
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .requiredSize(31.dp, 34.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                val left = geo.inset
                val right = geo.width - geo.inset - 31.dp.toPx()
                translationX = lerpF(left, right, s.logoSide.value)
                translationY = geo.top + 32.dp.toPx()
            }
            .layoutAtOrigin(),
    )
}

/** Puts a fixed-size child at the parent's top-left so graphicsLayer translations are absolute. */
private fun Modifier.layoutAtOrigin(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) { p.place(0, 0) }
    },
)

private const val AvatarBaseDp = 160

@Composable
private fun StoryAvatar(s: StoryState, data: RecapStoryData) {
    Box(
        modifier = Modifier
            .requiredSize(AvatarBaseDp.dp)
            .layoutAtOrigin()
            .graphicsLayer {
                val base = AvatarBaseDp.dp.toPx()
                translationX = s.avX.value - base / 2f
                translationY = s.avY.value - base / 2f
                val k = s.avD.value / base * s.avPop.value
                scaleX = k
                scaleY = k
                alpha = s.avAlpha.value
            }
            .drawBehind {
                val ring = lerp(Color.White, Color(0xFF626262), s.avRing.value)
                drawCircle(ring, alpha = s.avRingAlpha.value)
            }
            .padding((AvatarBaseDp * 0.075f).dp)
            .clip(CircleShape)
            .background(Color(0xFFE8E8E8)),
        contentAlignment = Alignment.Center,
    ) {
        if (data.photo != null) {
            AsyncImage(
                model = data.photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = data.name.first().uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                color = Color(0xFF2C2C2E),
            )
        }
    }
}

@Composable
private fun StoryCard(s: StoryState, data: RecapStoryData, payload: String, caption: String, time: RecapTime, cardWidth: Float) {
    val density = LocalDensity.current
    val widthDp = with(density) { cardWidth.toDp() }
    val deviceTilt = rememberDeviceTilt()
    Box(
        modifier = Modifier
            .requiredSize(widthDp, widthDp / IdCardAspect)
            .layoutAtOrigin()
            .graphicsLayer {
                translationX = s.cardX.value
                translationY = s.cardY.value
                rotationZ = s.cardRot.value
                rotationX = deviceTilt.rotationX
                rotationY = deviceTilt.rotationY
                alpha = s.cardAlpha.value
                cameraDistance = 16f * this.density
            },
    ) {
        if (s.cardBack) {
            IdCardBack(
                username = data.username,
                payload = payload,
                caption = caption,
                scratch = null,
                time = time,
                scan = { s.scan.value },
                verdict = { ScanVerdict.Valid },
            )
        } else {
            ProfileIdCard(
                username = data.username,
                photo = data.photo,
                cardNumber = data.cardNumber,
                joinedText = data.joinedText,
                showIdCardOf = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The script

internal class StoryDirector(
    private val s: StoryState,
    private val data: RecapStoryData,
    private val gestures: Channel<StoryGesture>,
    private val snippet: RecapSnippetPlayer,
    private val haptic: (HapticFeedbackType) -> Unit,
    private val startCard: Rect,
    private val headerCard: Rect,
) {
    var artistPalette: RecapPalette = RecapPalette.Fallback
    var songPalette: RecapPalette = RecapPalette.Fallback
    var returning = false
        private set
    var covered = false
        private set

    private val geo get() = s.geo

    /** The card's spot on the last page: where the header card is, a little lower. */
    private val endCardTop get() = geo.top + geo.dp(175f)

    suspend fun run(onCovered: () -> Unit) {
        intro(onCovered)
        hey()
        journey()
        awaitNext()
        month()
        // the percentage reads on its own: a second after it has settled, the story moves on
        awaitNextOrAfter(1000)
        weekday()
        awaitNext()
        if (data.heroArtist != null) {
            artists()
            awaitNext()
            songsFromArtist()
        } else {
            songsFromWeekday()
        }
        melody()
        awaitNext()
        receipt()
        awaitNext()
        end()
        awaitGesture(StoryGesture.Tap, StoryGesture.SwipeDown)
    }

    private suspend fun awaitNext() = awaitGesture(StoryGesture.Tap, StoryGesture.SwipeLeft)

    /** A tap or swipe moves on at once; with none, the story moves on by itself after [ms]. */
    private suspend fun awaitNextOrAfter(ms: Long) {
        withTimeoutOrNull(ms) { awaitNext() }
    }

    private suspend fun awaitGesture(vararg kinds: StoryGesture) {
        // taps made while the page was still coming in don't count
        while (gestures.tryReceive().isSuccess) Unit
        s.swipeOpen = StoryGesture.SwipeLeft in kinds
        val openedAt = SystemClock.uptimeMillis()
        try {
            while (true) {
                val gesture = gestures.receive()
                // nor does the second half of a double tap landing just as the page settled
                if (gesture in kinds && SystemClock.uptimeMillis() - openedAt >= MinPageMillis) break
            }
        } finally {
            s.swipeOpen = false
        }
        haptic(HapticFeedbackType.SegmentTick)
    }

    private suspend fun showPage(page: Int) {
        s.page = page
    }

    // -- the card is read, the screen folds into a band and opens on three columns ---------------
    private suspend fun intro(onCovered: () -> Unit) {
        s.reveal.go(1f, 180, LinearEasing)
        covered = true
        onCovered()
        coroutineScope {
            launch {
                delay(1500)
                haptic(HapticFeedbackType.Confirm)
            }
            s.scan.go(1f, 1950, LinearEasing)
        }
        delay(420)
        together(
            { s.band.go(0f, 560, EmphasizedAccelerate) },
            { s.cardX.go(geo.width * 1.15f, 1500, EmphasizedAccelerate, delayMs = 300) },
            { s.cardRot.go(16f, 1500, EmphasizedAccelerate, delayMs = 300) },
            {
                delay(600)
                // behind the band: the single column becomes three
                s.text.centerX.snapTo(0.5f)
                s.text.row(-1).fill.snapTo(1f)
                s.text.row(1).fill.snapTo(1f)
                s.band.go(1f, 760, Emphasized)
            },
        )
        s.cardAlpha.snapTo(0f)
    }

    // -- columns warp into rows, the avatar pops -------------------------------------------------
    private suspend fun hey() {
        showPage(P_HEY)
        together(
            { s.text.rotation.go(0f, 1200, androidx.compose.animation.core.FastOutSlowInEasing) },
            { s.text.scale.go(0.74f, 1200, androidx.compose.animation.core.FastOutSlowInEasing) },
            {
                for (i in listOf(-2, 2, -3, 3, -4, 4)) {
                    launch { s.text.row(i).slideIn("MUSICFY", 1000) }
                    delay(70)
                }
            },
            {
                delay(520)
                s.avAlpha.snapTo(1f)
                haptic(HapticFeedbackType.ContextClick)
                s.avPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            },
            { s.pages[P_HEY].enter.go(1f, 800, delayMs = 760) },
        )
        delay(1300)
    }

    // -- the words climb, leaving room for the first story ---------------------------------------
    private suspend fun journey() {
        showPage(P_JOURNEY)
        together(
            { s.pages[P_HEY].exit.go(1f, 420) },
            { s.avatarTo(P_JOURNEY, 950) },
            { s.avRing.go(1f, 950) },
            { s.text.offsetY.go(-geo.dp(46f), 1100) },
            {
                for (i in -1..4) {
                    launch { s.text.row(i).slideOut(620) }
                    delay(60)
                }
            },
            { s.pages[P_JOURNEY].enter.go(1f, 2200, LinearEasing, delayMs = 380) },
        )
    }

    private suspend fun month() {
        showPage(P_MONTH)
        together(
            { s.pages[P_JOURNEY].exit.go(1f, 700, FastOutSlowInEasing) },
            { s.avatarTo(P_MONTH, 950, delayMs = 120) },
            {
                delay(260)
                for (i in -1..3) {
                    launch { s.text.row(i).slideIn(data.monthName.uppercase(), 1000) }
                    delay(80)
                }
            },
            { s.pages[P_MONTH].enter.go(1f, 2000, LinearEasing, delayMs = 420) },
        )
    }

    private suspend fun weekday() {
        showPage(P_WEEKDAY)
        val dayWord = java.time.DayOfWeek.of(data.favouriteDay + 1).displayName().uppercase()
        together(
            { s.pages[P_MONTH].exit.go(1f, 650, FastOutSlowInEasing) },
            { s.avatarTo(P_WEEKDAY, 950, delayMs = 100) },
            { s.text.offsetY.go(0f, 1200, delayMs = 300) },
            {
                // the month leaves first, then every row becomes the day
                for (i in 3 downTo -1) {
                    launch { s.text.row(i).slideOut(520) }
                    delay(50)
                }
                delay(380)
                for (i in -4..4) {
                    launch { s.text.row(i).slideIn(dayWord, 950) }
                    delay(60)
                }
            },
            { s.pages[P_WEEKDAY].enter.go(1f, 2000, LinearEasing, delayMs = 560) },
        )
    }

    // -- the colour arrives; the top ten spin past and stop on #1 --------------------------------
    private suspend fun artists() {
        showPage(P_ARTISTS)
        val n = data.artists.size.coerceAtLeast(1)
        together(
            { s.pages[P_WEEKDAY].exit.go(1f, 600, FastOutSlowInEasing) },
            { s.text.alpha.go(0f, 900, FastOutSlowInEasing) },
            { s.dots.go(0f, 900) },
            {
                s.crossfadePalette(artistPalette, 10)
                s.mesh.go(1f, 1400, FastOutSlowInEasing)
            },
            { s.logoSide.go(1f, 1000) },
            { s.avatarTo(P_ARTISTS, 1000) },
            { s.avRingAlpha.go(0f, 700) },
            { s.pages[P_ARTISTS].enter.go(1f, 1000, delayMs = 250) },
            {
                // three laps, decelerating onto the hero (lap * n is always artist #1)
                s.spin.animateTo(3f * n, tween(3600, 250, CubicBezierEasing(0.35f, 0.1f, 0.15f, 1f)))
            },
        )
        haptic(HapticFeedbackType.Confirm)
        s.spinGlow.go(1f, 1100, LinearEasing)
        showPage(P_ARTIST)
        data.heroArtist?.topSong?.let { snippet.play(it.id) }
        together(
            { s.heroArtist.go(1f, 1000) },
            { s.pages[P_ARTISTS].exit.go(1f, 650, FastOutSlowInEasing) },
            { s.pages[P_ARTIST].enter.go(1f, 2400, LinearEasing, delayMs = 300) },
        )
    }

    private suspend fun songsFromArtist() {
        snippet.stop(900)
        showPage(P_SONGS)
        together(
            { s.pages[P_ARTIST].exit.go(1f, 650, FastOutSlowInEasing) },
            { s.crossfadePalette(songPalette, 1400) },
            { s.pages[P_SONGS].enter.go(1f, 1400, delayMs = 150) },
            { s.gridDrift.go(1f, 3800, LinearEasing) },
        )
        pickTopSong()
    }

    private suspend fun songsFromWeekday() {
        showPage(P_SONGS)
        together(
            { s.pages[P_WEEKDAY].exit.go(1f, 600, FastOutSlowInEasing) },
            { s.text.alpha.go(0f, 900) },
            { s.dots.go(0f, 900) },
            {
                s.crossfadePalette(songPalette, 10)
                s.mesh.go(1f, 1400, FastOutSlowInEasing)
            },
            { s.logoSide.go(1f, 1000) },
            { s.avatarTo(P_ARTISTS, 1000) },
            { s.avRingAlpha.go(0f, 700) },
            { s.pages[P_SONGS].enter.go(1f, 1400, delayMs = 150) },
            { s.gridDrift.go(1f, 3800, LinearEasing) },
        )
        pickTopSong()
    }

    private suspend fun pickTopSong() {
        haptic(HapticFeedbackType.Confirm)
        s.gridPick.go(1f, 800)
        showPage(P_MELODY)
        data.heroSong?.let { snippet.play(it.id, fadeInMs = 2200) }
        together(
            { s.heroCover.go(1f, 1000) },
            { s.pages[P_SONGS].exit.go(1f, 600, FastOutSlowInEasing) },
            { s.pages[P_MELODY].enter.go(1f, 1000, delayMs = 300) },
        )
    }

    // -- "Hear the melody.." holds, then becomes the top song's page -----------------------------
    private suspend fun melody() {
        delay(3200)
        showPage(P_SONG)
        together(
            { s.heroCoverUp.go(1f, 1000) },
            { s.pages[P_MELODY].exit.go(1f, 500, FastOutSlowInEasing) },
            { s.pages[P_SONG].enter.go(1f, 2000, LinearEasing, delayMs = 250) },
        )
    }

    private suspend fun receipt() {
        snippet.stop(1400)
        showPage(P_RECEIPT)
        together(
            { s.pages[P_SONG].exit.go(1f, 650, FastOutSlowInEasing) },
            { s.pages[P_RECEIPT].enter.go(1f, 1300, delayMs = 250) },
        )
    }

    // -- the receipt drops away, the card is back ------------------------------------------------
    private suspend fun end() {
        showPage(P_END)
        s.cardBack = false
        s.cardRot.snapTo(-8f)
        s.cardX.snapTo(headerCard.left)
        s.cardY.snapTo(geo.height + geo.dp(40f))
        s.text.offsetY.snapTo(0f)
        together(
            { s.pages[P_RECEIPT].exit.go(1f, 700, EmphasizedAccelerate) },
            { s.logoSide.go(0f, 900) },
            { s.avAlpha.go(0f, 500) },
            { s.text.alpha.go(0.9f, 1200, delayMs = 300) },
            {
                for (i in -4..4) {
                    launch { s.text.row(i).slideIn("MUSICFY", 1000) }
                    delay(50)
                }
            },
            { s.cardAlpha.snapTo(1f) },
            { s.cardY.go(endCardTop, 1100, delayMs = 350) },
            { s.cardRot.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)) },
            { s.pages[P_END].enter.go(1f, 1600, LinearEasing, delayMs = 250) },
        )
    }

    // -- back into the page: the colour drains, the column returns, the card slots home ----------
    suspend fun returnToPage() {
        returning = true
        snippet.stop(500)
        val row0 = s.text.row(0)
        together(
            { s.pages[P_END].exit.go(1f, 520, FastOutSlowInEasing) },
            { s.mesh.go(0f, 950, FastOutSlowInEasing) },
            { s.toHeader.go(1f, 950, FastOutSlowInEasing) },
            { s.dots.go(1f, 950) },
            { s.text.alpha.go(1f, 600) },
            {
                for (i in -4..4) if (i != 0) launch { s.text.row(i).slideOut(560) }
                if (row0.word != "MUSICFY" || row0.fill.value < 1f) row0.slideIn("MUSICFY", 700)
            },
            { s.text.rotation.go(90f, 1050, delayMs = 150) },
            { s.text.scale.go(1f, 1050, delayMs = 150) },
            { s.text.centerX.go(GiantTextState.PageColumnCenterX, 1050, delayMs = 150) },
            { s.text.offsetY.go(0f, 1050, delayMs = 150) },
            { s.logoSide.go(0f, 700) },
            { s.avAlpha.go(0f, 300) },
            { s.cardAlpha.go(1f, 300) },
            { s.cardX.go(headerCard.left, 1000, delayMs = 120) },
            { s.cardY.go(headerCard.top, 1000, delayMs = 120) },
            { s.cardRot.go(0f, 800) },
            { s.headerClip.go(1f, 1000, delayMs = 160) },
        )
    }

    /** Back pressed mid-story: let everything go and fold home. */
    suspend fun abortToPage() {
        snippet.stop(400)
        together(
            {
                s.pages.forEach { p -> launch { p.exit.go(1f, 380, FastOutSlowInEasing) } }
            },
            {
                if (s.cardBack || s.cardAlpha.value < 0.5f) {
                    s.cardAlpha.snapTo(0f)
                    s.cardBack = false
                    s.cardRot.snapTo(0f)
                    s.cardX.snapTo(headerCard.left)
                    s.cardY.snapTo(headerCard.top + geo.dp(60f))
                }
            },
            { s.band.go(1f, 300) },
        )
        returnToPage()
    }
}

internal val DateLong: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
