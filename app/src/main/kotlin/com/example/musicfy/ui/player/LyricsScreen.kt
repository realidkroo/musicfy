// LyricsScreen.kt
// Full lyrics page for the expanded player, styled after Monochrome's fullscreen lyrics pane:
// small cover top-left (tap to close back to the normal player view) instead of the usual big
// centered artwork, a progressively-highlighted/blurred synced lyrics column, and the same
// progress slider + transport row as the normal player underneath. Romanization is computed
// locally (deterministic script transliteration already in LyricsUtils, no network/API-key
// step) and shown as a smaller sub-line under each lyric line.

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

/**
 * Where the active line sits, measured from the top of the viewport as a fraction of screen
 * height. The old layout used a fixed -232dp scroll offset, which put the highlighted line down
 * near the transport row; a fraction keeps it in the upper third on any screen size.
 */
private const val ActiveLineViewportFraction = 0.2f

/** Idle time on the highlighted line before the page goes immersive. */
private const val ImmersiveDelayMs = 3_500L

/** How long the list takes to glide the newly-active line into position. */
private const val RecenterDurationMs = 950

/**
 * Scroll movement, in px between two snapshots, needed to count as a deliberate direction rather
 * than fling settle or a fingertip wobble. Low enough to feel immediate, high enough that resting
 * a finger on the list doesn't toggle the mode.
 */
private const val ScrollDirectionThresholdPx = 6L

/** How long after the user stops scrolling before the list recenters on the active line. */
private const val IdleRecenterDelayMs = 3_500L

/** Where the top fade finishes and the bottom fade begins, as fractions of the list's height. */
private const val TopFadeFraction = 0.10f
private const val BottomFadeStart = 0.62f

/**
 * How far the list is allowed to reach back down into the controls block before fading out.
 * The timestamp labels sit a little below the block's top edge, so ending the list exactly at
 * that edge left a visible band of dead space above the numbers.
 */
private val HeaderOverlapAllowance = 28.dp

@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    // Same value BottomSheetPlayer passes to PlayerControls — needed here for the identical
    // screenHeight * 0.19f - 37.dp bottom-anchor formula PlayerControls uses. Without it, this
    // page's transport row just sat at the natural bottom of a Column (~16dp of padding),
    // landing ~119dp lower on screen than the main player's — confirmed by measuring both
    // screens' actual rendered bounds via uiautomator, not eyeballed.
    screenHeight: androidx.compose.ui.unit.Dp,
    /** Height occupied by the shared slider + transport block, measured from the sheet's bottom. */
    contentBottomInset: androidx.compose.ui.unit.Dp,
    /** Reports the immersive state so the shared transport block can fade with it. */
    onImmersiveChange: (Boolean) -> Unit = {},
    /** True when the bottom sheet is being dragged, used to suppress heavy visual effects. */
    isSheetDragging: Boolean = false,
    /**
     * True while this page is itself opening or closing.
     *
     * During that morph the whole page is being scaled, faded and translated — which already forces
     * a full-screen offscreen composite every frame — and the sheet is fully expanded, so neither
     * [isSheetDragging] nor `userScrolling` was true and every per-line blur and wave shader kept
     * running straight through it. Measured at 29ms median frame time (a 120Hz frame is 8.3ms).
     */
    isMorphing: Boolean = false,
    /** Opens the shared player action sheet. */
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

    // Non-snapshot read of the same ticker, for anything that wants the live position without
    // subscribing to it. StateFlow.value is not snapshot state, so calling this does not register
    // a recomposition dependency — the active lyric line drives its own repaint off frame
    // callbacks instead, which is what keeps the other visible lines from recomposing 15x a
    // second just to be handed a timestamp they never use.
    val positionProvider = remember(playerConnection) {
        { playerConnection.progressState.value.position }
    }

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

    // Keyed on `lines` — without the key, remember caches a derivedStateOf whose lambda captured
    // the FIRST value of `lines`, which is the empty list from before the lyrics finished
    // loading. currentIndex then stayed -1 forever, so no line ever became ACTIVE and the
    // word-by-word karaoke path never ran at all.
    val currentIndex by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, progress.position) }
    }

    // The TOPMOST line that is still being sung.
    //
    // Lines overlap: a phrase often has not finished when the next one's timestamp arrives, and
    // treating exactly one line as active meant the unfinished one went dim and blurred mid-word
    // while the list scrolled off it. Everything from here down to [currentIndex] is active at
    // once — each unblurs, scales up and runs its own sweep — and the page only moves on once the
    // upper one has actually finished.
    //
    // Walking backwards stops at the first line that has genuinely ended, so this is normally
    // currentIndex itself and only ever a line or two above it. A line without word timings has no
    // knowable end, so it ends where the next one starts and never overlaps.
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
    // LazyListState has no built-in way to tell "user dragged" apart from "we scrolled it
    // programmatically" — isAutoScrolling is set around our own animateScrollToItem call so
    // the isScrollInProgress flip it causes isn't mistaken for the user grabbing the list.
    var isAutoScrolling by remember { mutableStateOf(false) }

    // True while the user is actually dragging. Drives two things: blur is switched off entirely
    // (both because it reads better while scanning lyrics, and because per-line RenderEffects are
    // the single most expensive thing on this screen), and the idle timer below.
    val userScrolling by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }

    val suppressEffects = userScrolling || isSheetDragging || isMorphing

    LaunchedEffect(userScrolling) {
        if (userScrolling) followPlayback = false
    }

    // Idle auto-recenter: once the user stops scrolling and leaves it alone for a few seconds,
    // snap back to following playback rather than stranding them wherever they let go. Replaces
    // the old manual "jump to current line" arrow button.
    LaunchedEffect(userScrolling, followPlayback) {
        if (!followPlayback && !userScrolling) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    // Immersive mode: settle on the highlighted line for a few seconds and the transport block and
    // the bottom fade retreat, handing the whole page to the lyrics. Any scroll brings them back,
    // as does tapping the cover. Re-entered automatically once things go quiet again.
    var immersive by remember { mutableStateOf(false) }

    // Keyed on `immersive` as well as `userScrolling`. Without that, the effect only re-ran when a
    // scroll started or stopped, so any other way out of immersive mode — tapping the cover, or
    // scrolling up, below — left the timer un-armed and the page never went immersive again until
    // the next scroll. That is the "sometimes not triggered".
    LaunchedEffect(userScrolling, immersive) {
        if (!userScrolling && !immersive) {
            delay(ImmersiveDelayMs)
            immersive = true
        }
    }

    // Direction-driven: scrolling down (further into the song) hands the page over to the lyrics
    // immediately, scrolling back up brings the chrome back. Auto-scroll is excluded — the
    // recenter animation is a downward scroll too, and letting it toggle anything meant the mode
    // flipped on its own every time playback advanced a line.
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
    // Reset on close so reopening the player always starts with the controls visible.
    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }

    // Animated rather than switched: the list's bottom inset and its fade both interpolate, so the
    // lyrics grow into the vacated space instead of jumping when the controls leave.
    val immersion by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsImmersion",
    )

    // Places the active line at ActiveLineViewportFraction down the list's viewport.
    //
    // animateScrollToItem's offset is where the item's top edge ends up relative to the viewport's
    // top edge, so a negative value pushes it down. The fraction is taken from the list's ACTUAL
    // measured viewport (layoutInfo.viewportSize), not from screenHeight — the list is shorter
    // than the screen (header above, controls below), so a screen-derived offset overshot and
    // left the highlighted line sitting far too low.
    // Follows the ANCHOR, not the last active line. While two lines overlap, the page stays put on
    // the upper one — the one still being sung — and only travels once it has finished and the
    // anchor moves down to catch up. Scrolling on `currentIndex` would leave the still-singing
    // line stranded above the viewport.
    LaunchedEffect(anchorIndex, followPlayback, lines) {
        val target = anchorIndex
        if (!followPlayback || target !in lines.indices) return@LaunchedEffect
        // On the very first frame the viewport isn't measured yet; wait for it rather than
        // scrolling by a bogus zero-derived offset.
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }
        // Where the active line's top edge should sit, in px from the viewport's top edge. It is
        // exactly the list's own top contentPadding — that padding exists so the FIRST line can
        // reach this position without the list being able to scroll above it.
        val restingOffset = (viewportHeight * ActiveLineViewportFraction).roundToInt()
        // try/finally, because this effect is cancelled every time currentIndex changes — which is
        // once per lyric line, routinely mid-animation. Clearing the flag on the normal path only
        // meant one cancelled recenter left it stuck true FOREVER, and from then on `userScrolling`
        // (which is `isScrollInProgress && !isAutoScrolling`) could never become true again. That
        // one latched boolean is why immersive mode stopped responding to scrolling and why the
        // blur-while-scrolling suppression stopped firing.
        try {
        isAutoScrolling = true
        // animateScrollToItem takes no animationSpec — it always runs its own fixed spring, which
        // is what made the recenter snap. Where the target line is already on screen its exact
        // pixel delta is known, so the move can be an ordinary animateScrollBy on a chosen curve.
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible != null) {
            // LazyListItemInfo.offset is NOT measured from the visible top edge — it is measured
            // from the start of the content area, i.e. from *after* the top contentPadding.
            // layoutInfo.viewportStartOffset is that padding, negated. So the item's real distance
            // below the visible edge is (offset - viewportStartOffset), and the delta to scroll is
            // that minus where it should end up.
            //
            // Subtracting restingOffset from the raw offset instead, as this did, left the padding
            // in the sum a second time and parked the line at twice the intended depth — which is
            // why it kept landing mid-screen no matter what the fraction was lowered to.
            val current = visible.offset - listState.layoutInfo.viewportStartOffset
            listState.animateScrollBy(
                (current - restingOffset).toFloat(),
                animationSpec = tween(
                    durationMillis = RecenterDurationMs,
                    easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f),
                ),
            )
        } else {
            // Off screen — the distance isn't measurable, so fall back to the built-in scroll.
            //
            // scrollOffset is deliberately 0, NOT -restingOffset. animateScrollToItem measures
            // from the start of the content area, which is already past contentPadding.top — and
            // that padding IS restingOffset. Passing the offset as well applied it twice and left
            // the active line sitting at roughly double the intended depth, which is why it kept
            // reading as too low no matter what the fraction was set to.
            listState.animateScrollToItem(target, 0)
        }
        } finally {
            isAutoScrolling = false
        }
    }

    // Whether playback is sitting in an instrumental gap right now. Hoisted out of the item loop
    // because the line ABOVE the gap needs it too — it has to give up the highlight while the dots
    // hold it — and a per-item value can't be read by a different item.
    //
    // The position read stays inside derivedStateOf deliberately. Read directly it would subscribe
    // the whole page to the 15Hz progress ticker; here only this boolean flipping invalidates.
    val interludeActive by remember(currentIndex, lines) {
        derivedStateOf {
            val next = currentIndex + 1
            if (next !in lines.indices) return@derivedStateOf false
            // A line's real end is its last word's endTime. Without word timings there is no way
            // to know when singing stopped, so no gap is claimed rather than guessing — the intro
            // (nothing before it) is the one case that needs no previous line.
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

    // Style one vs style two: same page, same sweep, per-letter warp on or off.
    val (lyricsWaveAnimation) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (lyricsHighBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar: small cover (tap to close) + title/subtitle + menu. Matches the 36dp the
        // lyrics list/timestamp use. Matches MorphingCover's lyricsArtX/Y/Size, which have
        // to stay in lockstep with regardless.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 36.dp)
                .padding(top = 28.dp, bottom = 16.dp)
        ) {
            // Intentionally empty: the artwork that lands here is MorphingCover's single shared
            // cover, animating down from the full-size player position (see lyricsProgress in
            // BottomSheetPlayer). Drawing a second AsyncImage here is what put two covers on
            // screen at once. This box only reserves the same 50dp footprint so the title column
            // beside it doesn't shift, and keeps the tap-to-close target over the artwork.
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    // In immersive mode the cover is the way back to the controls; it only
                    // closes the page once they are already showing.
                    .clickable { if (immersive) immersive = false else onClose() }
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Also intentionally empty, same reasoning: MorphingSongInfo (in BottomSheetPlayer's
            // sharedContent) is the ONE title+artist block, travelling in from the main player
            // instead of this page drawing its own second copy. weight(1f) alone reserves the
            // menu icon's space on the right.
            Spacer(modifier = Modifier.weight(1f))

            // Same slot and size as before, restyled to match the like/repeat buttons on the
            // player: a filled circle with the dots turned horizontal.
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

        // The list stops at the top of the timestamp row rather than running to the bottom of the
        // sheet. Previously it filled the whole remaining height and the fade was expressed as a
        // fraction of *that*, which put the fade behind the transport controls — so lyrics stayed
        // fully opaque straight through the timestamp and only dimmed near the very bottom edge.
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
                    // Three pulsing dots in the corner rather than "Loading lyrics…" across the
                    // middle of the page. Loading is a transient state and a sentence in the
                    // centre reads as content — the dots stay out of the way and vacate cleanly
                    // the moment real lines arrive.
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
                    // Fractions of the LIST's own height, not the screen's. Using screen height
                    // here is what produced the huge dead gap above the first line: the top pad
                    // was 28% of the full screen inside a viewport much shorter than that.
                    contentPadding = PaddingValues(
                        top = listHeight * ActiveLineViewportFraction,
                        bottom = listHeight * 0.55f,
                    ),
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()
                        // Dissolves the list at both edges: under the header at the top, and right
                        // at the timestamp at the bottom. DstIn against a vertical alpha ramp,
                        // which needs its own offscreen layer to composite against.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        // drawWithCache, not drawWithContent: the gradient depends only on the
                        // list's size, so it is built once per resize instead of allocating a new
                        // Brush (and its colour/stop arrays) on every frame of every scroll.
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
                        // Distance to the nearest ACTIVE line, not to a single index — with an
                        // overlap in play there can be more than one, and blur has to fall away
                        // from the whole active block rather than from its last line.
                        val distance = when {
                            currentIndex < 0 -> index - currentIndex
                            index < anchorIndex -> index - anchorIndex
                            index > currentIndex -> index - currentIndex
                            else -> 0
                        }
                        val state = when {
                            // During an instrumental gap the dots ARE the highlight, so the line
                            // that finished singing hands the highlight over to them instead of
                            // both being lit at once.
                            currentIndex >= 0 && index in anchorIndex..currentIndex ->
                                if (interludeActive) LyricsLineState.PAST else LyricsLineState.ACTIVE
                            index == currentIndex + 1 -> LyricsLineState.UPCOMING
                            index < currentIndex -> LyricsLineState.PAST
                            else -> LyricsLineState.DEFAULT
                        }
                        // Blur deepens with distance from the active line — stage 0 sharp,
                        // stage 1 one line away, stage 2 for everything beyond. Suppressed
                        // entirely while the user is scrolling.
                        val blurStage = when {
                            suppressEffects -> 0
                            distance == 0 -> 0
                            kotlin.math.abs(distance) == 1 -> 1
                            else -> 2
                        }

                        // The dots belong to the gap immediately ahead of the current line, so they
                        // render above the line that is about to be sung, and only while that gap
                        // is the highlighted position. `interludeActive` (hoisted above) already
                        // carries the timing test; the index check is what stops a seek satisfying
                        // it for some unrelated line further down the list and dropping a stray
                        // set of dots at the top of the page.
                        if (interludeActive && index == currentIndex + 1) {
                            // currentIndex is -1 before the first line is due, which makes this
                            // branch true for index 0 — the song's intro. There is no previous
                            // line to take an end time from, and indexing one is what crashed;
                            // the intro's gap simply runs from the start of the track.
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

            // The "jump back to current line" arrow used to sit here. Removed — the idle timer
            // above now recenters on its own a few seconds after you stop scrolling, so a manual
            // button is redundant.

            // Sits alongside the active lyric line rather than at the big player's own heart
            // position, which left it stranded in the middle of the page.
            //
            // Retreats with the rest of the chrome in immersive mode. Composed out entirely at the
            // end of the fade rather than left at alpha 0: a faded-out Box still hit-tests, and
            // this one sits over the lyrics list.
            if (immersion < 0.99f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = screenHeight * ActiveLineViewportFraction)
                        .size(36.dp)
                        .graphicsLayer {
                            alpha = 1f - immersion
                            // Drifts out to the right as it goes, matching the transport block
                            // sliding down rather than dissolving on the spot.
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

        // The progress slider and transport row deliberately do NOT live here any more. They are
        // a single shared instance in BottomSheetPlayer, declared outside the showLyrics branch,
        // so they stay mounted and motionless while this page opens and closes. This page used to
        // build its own copy inside a slideInVertically AnimatedVisibility, which is what made the
        // controls and timestamp bar slide up and briefly double on every open.
        //
        // The list above reserves room for them via its own bottom contentPadding.
    }
}

/**
 * Three dots that breathe while lyrics are being fetched.
 *
 * Deliberately not a spinner and not a sentence: it sits in the corner of the lyrics area, says
 * "something is coming" without claiming any of the space the lines are about to occupy, and needs
 * no layout change when they arrive.
 */
@Composable
private fun LyricsLoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            // Staggered by a third of the cycle each, so the three read as a travelling pulse
            // rather than as one flashing group.
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
