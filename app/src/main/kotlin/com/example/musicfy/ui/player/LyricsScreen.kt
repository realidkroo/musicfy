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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import androidx.media3.common.Player
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.extensions.toggleRepeatMode
import com.example.musicfy.constants.AiProviderKey
import com.example.musicfy.constants.DeeplApiKey
import com.example.musicfy.constants.DeeplFormalityKey
import com.example.musicfy.constants.LyricsHighBloomKey
import com.example.musicfy.constants.OpenRouterApiKey
import com.example.musicfy.constants.OpenRouterBaseUrlKey
import com.example.musicfy.constants.OpenRouterModelKey
import com.example.musicfy.constants.LyricsRomanizeBelarusianKey
import com.example.musicfy.constants.LyricsRomanizeBulgarianKey
import com.example.musicfy.constants.LyricsRomanizeChineseKey
import com.example.musicfy.constants.LyricsRomanizeHindiKey
import com.example.musicfy.constants.LyricsRomanizeJapaneseKey
import com.example.musicfy.constants.LyricsRomanizeKoreanKey
import com.example.musicfy.constants.LyricsRomanizeKyrgyzKey
import com.example.musicfy.constants.LyricsRomanizeMacedonianKey
import com.example.musicfy.constants.LyricsRomanizePunjabiKey
import com.example.musicfy.constants.LyricsRomanizeRussianKey
import com.example.musicfy.constants.LyricsRomanizeSerbianKey
import com.example.musicfy.constants.LyricsRomanizeUkrainianKey
import com.example.musicfy.constants.LyricsWaveAnimationKey
import com.example.musicfy.constants.TranslateLanguageKey
import com.example.musicfy.constants.TranslateModeKey
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsTranslationHelper
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

private const val ActiveLineViewportFraction = 0.055f

/**
 * Floor under [ActiveLineViewportFraction], and in practice the value that wins: the active line
 * rests exactly one lyric row from the top, leaving the just-finished line — and only that line —
 * above it. One row is a 40sp line plus 16dp of vertical padding on each side.
 */
private val MinPastLineVisibleHeight = 72.dp

private const val ImmersiveDelayMs = 3_500L

private const val RecenterDurationMs = 950

/** Shared with LyricsInterludeDots, whose collapse has to run in step with the recenter scroll. */
internal const val LyricsRecenterDurationMs = RecenterDurationMs

/** Style 2's recenter curve (the original). */
internal val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

/**
 * Style 1's recenter curve: eases in instead of starting at speed, so the list never gets a kick
 * at the start of a line change.
 */
internal val LyricsSmoothRecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

internal fun recenterEasingFor(style: com.example.musicfy.constants.LyricsMotionStyle) =
    if (style == com.example.musicfy.constants.LyricsMotionStyle.MOTION) LyricsSmoothRecenterEasing else LyricsRecenterEasing

/**
 * Style 1 follow-through on an auto-scroll, like Apple Music: each line below the active one trails
 * the scroll a little more than the one above, so the list settles in a visible cascade instead of
 * moving as one rigid sheet. Each row chases its place on a critically damped spring - it eases out
 * and eases in, never jerks - with this much more lag per row of distance below the active line.
 */
private const val StaggerLagPerRowMs = 55f
private const val StaggerMaxRows = 9

private const val ScrollDirectionThresholdPx = 6L

private const val IdleRecenterDelayMs = 3_500L

private const val TopFadeFraction = 0.10f

/**
 * Where a start-aligned line's text begins inside its row, and the middle of its first line: the
 * point the card's lyric grows from. (The row's padding and tap inset put the text at the artwork's
 * left edge, 16dp down; a lyric line is 40sp tall.)
 */
private val LineAnchorX = LyricsHeaderArtX
private val LineAnchorY = 16.dp + 20.dp

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density) =
    with(density) { this@toPx.toPx() }
private const val BottomFadeStart = 0.62f


@Composable
fun LyricsScreen(
    onClose: () -> Unit,

    screenHeight: androidx.compose.ui.unit.Dp,

    contentBottomInset: androidx.compose.ui.unit.Dp,

    onImmersiveChange: (Boolean) -> Unit = {},

    isSheetDragging: Boolean = false,

    isMorphing: Boolean = false,

    /**
     * How far the page has grown out of the bottom card (unclamped); 1 when it isn't growing from
     * it. Drives the rows rising into place and the card's line flying to its own.
     */
    enterProgressProvider: () -> Float = { 1f },

    morph: CardMorphState? = null,

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

    // Romanisation preferences. Every one of these keys existed already and none of them were
    // read by this screen — it romanised unconditionally, so turning any of them off did nothing.
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeRussian) = rememberPreference(LyricsRomanizeRussianKey, defaultValue = true)
    val (romanizeUkrainian) = rememberPreference(LyricsRomanizeUkrainianKey, defaultValue = true)
    val (romanizeSerbian) = rememberPreference(LyricsRomanizeSerbianKey, defaultValue = true)
    val (romanizeBulgarian) = rememberPreference(LyricsRomanizeBulgarianKey, defaultValue = true)
    val (romanizeBelarusian) = rememberPreference(LyricsRomanizeBelarusianKey, defaultValue = true)
    val (romanizeKyrgyz) = rememberPreference(LyricsRomanizeKyrgyzKey, defaultValue = true)
    val (romanizeMacedonian) = rememberPreference(LyricsRomanizeMacedonianKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizePunjabi) = rememberPreference(LyricsRomanizePunjabiKey, defaultValue = true)

    // Detect the script ONCE over the whole lyric body rather than per line.
    //
    // Per-line detection was the "multi language exists but not at their target" bug: in a song
    // that mixes English and Japanese, the English lines detect as nothing and the Japanese ones
    // as Japanese, so romanisation appeared under a scattered subset of lines. Worse, a Japanese
    // line made mostly of kanji looks identical to Chinese character-by-character, so individual
    // lines of one song were being sent to the pinyin romanizer and came back as Mandarin
    // readings of Japanese words. A song has one language; decide it from all the evidence.
    val bodyScript = remember(lines) {
        LyricsUtils.dominantScript(lines.joinToString(" ") { it.text })
    }
    val romanizerEnabled = when (bodyScript) {
        LyricsUtils.Script.KANA -> romanizeJapanese
        LyricsUtils.Script.HANGUL -> romanizeKorean
        LyricsUtils.Script.HAN -> romanizeChinese
        LyricsUtils.Script.DEVANAGARI -> romanizeHindi
        LyricsUtils.Script.CYRILLIC -> true
        else -> false
    }

    LaunchedEffect(lines, bodyScript, romanizerEnabled) {
        if (!romanizerEnabled) {
            lines.forEach { it.romanizedTextFlow.value = null }
            return@LaunchedEffect
        }
        // One sequential pass on a background dispatcher, not one coroutine per line. Launching a
        // coroutine per line meant a 60-line song fired 60 concurrent kuromoji tokenizations the
        // instant the lyrics loaded, which is what stalled the player on opening a Japanese track.
        withContext(Dispatchers.Default) {
            val body = lines.joinToString(" ") { it.text }
            // Gurmukhi and the individual Cyrillic languages aren't separable by codepoint block
            // alone, so they still go through the existing detectors — but on the whole body.
            val punjabi = LyricsUtils.isPunjabi(body)
            val cyrillicAllowed = when {
                bodyScript != LyricsUtils.Script.CYRILLIC -> false
                LyricsUtils.isUkrainian(body) -> romanizeUkrainian
                LyricsUtils.isSerbian(body) -> romanizeSerbian
                LyricsUtils.isBelarusian(body) -> romanizeBelarusian
                LyricsUtils.isKyrgyz(body) -> romanizeKyrgyz
                LyricsUtils.isMacedonian(body) -> romanizeMacedonian
                LyricsUtils.isBulgarian(body) -> romanizeBulgarian
                else -> romanizeRussian
            }

            suspend fun romanizeOne(text: String): String? = when {
                punjabi && romanizePunjabi -> LyricsUtils.romanizePunjabi(text)
                bodyScript == LyricsUtils.Script.KANA -> LyricsUtils.romanizeJapanese(text)
                bodyScript == LyricsUtils.Script.HANGUL -> LyricsUtils.romanizeKorean(text)
                bodyScript == LyricsUtils.Script.HAN -> LyricsUtils.romanizeChinese(text)
                bodyScript == LyricsUtils.Script.DEVANAGARI -> LyricsUtils.romanizeHindi(text)
                cyrillicAllowed -> LyricsUtils.romanizeCyrillic(text)
                else -> null
            }

            for (entry in lines) {
                if (entry.text.isBlank()) continue

                // Japanese and Chinese get readings placed ABOVE each word, the way furigana works
                // on a printed lyric sheet, rather than the whole line's romanisation dumped
                // underneath as one unbroken string. Everything else stays a sub-line: Cyrillic
                // and Devanagari are alphabetic, so a per-word reading over them adds nothing.
                val ruby = LyricsUtils.rubyFor(entry.text, bodyScript)
                entry.rubyFlow.value = ruby

                entry.romanizedTextFlow.value = if (ruby != null) {
                    // The ruby row carries the reading now; a duplicate underneath is just noise.
                    null
                } else {
                    romanizeOne(entry.text)?.takeIf { it.isNotBlank() && it != entry.text }
                }
            }
        }
    }

    // Translations. LyricsTranslationHelper has always written these flows and the lyrics menu has
    // always offered the toggle, but nothing in the live player collected either the flows or the
    // trigger — the menu was emitting into a void. Both ends are connected here.
    val (translateLanguage) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (translateMode) = rememberPreference(TranslateModeKey, defaultValue = "line")

    LaunchedEffect(lines, lyricsEntity, translateLanguage, translateMode) {
        if (lines.isEmpty()) return@LaunchedEffect
        LyricsTranslationHelper.loadTranslationsFromDatabase(
            lyrics = lines,
            lyricsEntity = lyricsEntity,
            targetLanguage = translateLanguage,
            mode = translateMode,
        )
    }

    LaunchedEffect(lines) {
        LyricsTranslationHelper.clearTranslationsTrigger.collect {
            lines.forEach { it.translatedTextFlow.value = null }
        }
    }

    // The other half: the menu's "AI lyrics translation" button emits manualTrigger, and until now
    // nothing collected it, so tapping it did nothing at all.
    val (aiProvider) = rememberPreference(AiProviderKey, defaultValue = "OpenRouter")
    val (openRouterKey) = rememberPreference(OpenRouterApiKey, defaultValue = "")
    val (openRouterBaseUrl) = rememberPreference(
        OpenRouterBaseUrlKey,
        defaultValue = "https://openrouter.ai/api/v1",
    )
    val (openRouterModel) = rememberPreference(OpenRouterModelKey, defaultValue = "")
    val (deeplKey) = rememberPreference(DeeplApiKey, defaultValue = "")
    val (deeplFormality) = rememberPreference(DeeplFormalityKey, defaultValue = "default")

    LaunchedEffect(lines, mediaMetadata?.id) {
        LyricsTranslationHelper.manualTrigger.collect {
            if (lines.isEmpty()) return@collect

            // No AI key configured is the common case, and it used to mean the toggle did nothing.
            // Google Translate needs no account, so fall back to it rather than failing silently.
            val hasAiKey = openRouterKey.isNotBlank() || deeplKey.isNotBlank()
            if (!hasAiKey) {
                LyricsTranslationHelper.translateWithGoogle(
                    lyrics = lines,
                    targetLanguage = translateLanguage.ifBlank { "en" },
                    mode = translateMode,
                    scope = this,
                    songId = mediaMetadata?.id.orEmpty(),
                    database = viewModel.database,
                )
                return@collect
            }

            LyricsTranslationHelper.translateLyrics(
                lyrics = lines,
                targetLanguage = translateLanguage,
                apiKey = openRouterKey,
                baseUrl = openRouterBaseUrl,
                model = openRouterModel,
                mode = translateMode,
                scope = this,
                context = context,
                provider = aiProvider,
                deeplApiKey = deeplKey,
                deeplFormality = deeplFormality,
                songId = mediaMetadata?.id.orEmpty(),
                database = viewModel.database,
            )
        }
    }

    val currentIndex by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, progress.position) }
    }

    val anchorIndex by remember(lines) {
        derivedStateOf {
            var i = LyricsUtils.findCurrentLineIndex(lines, progress.position)
            // findCurrentLineIndex returns lines.size once playback is past the final line. Don't
            // walk back from there looking for an anchor — there is no active line to anchor.
            if (i <= 0 || i >= lines.size) return@derivedStateOf i
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

    // Every instrumental gap in the song, as its own list row. Building these up front — rather
    // than conditionally rendering dots inside the next line's item, as before — is what stops the
    // list jumping. A gap's row grows in when the gap starts and collapses, in step with the
    // recenter scroll, when the next line takes over (see the recenter effect below).
    val rows = remember(lines) { buildLyricRows(lines) }

    /** Lyric line index -> its row in [rows]. Gap rows sit between lines, so the two differ. */
    val rowOfLine = remember(rows, lines) {
        IntArray(lines.size).also { map ->
            rows.forEachIndexed { rowIndex, row -> if (row is LyricRow.Line) map[row.index] = rowIndex }
        }
    }

    val (motionStyle) = com.example.musicfy.utils.rememberEnumPreference(
        com.example.musicfy.constants.LyricsMotionStyleKey,
        defaultValue = com.example.musicfy.constants.LyricsMotionStyle.MOTION,
    )
    val cascade = motionStyle == com.example.musicfy.constants.LyricsMotionStyle.MOTION

    // Opens already on the playing line. Starting at the top and scrolling down to it fought the
    // open transition - the line the card grows into would still be moving.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = rowOfLine.getOrNull(anchorIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: 0,
    )
    var followPlayback by remember { mutableStateOf(true) }

    // The line the card is showing: the page's copy of it is what the card's line grows into.
    val cardLine by remember(lines) { derivedStateOf { cardLyricLineIndex(lines, progress.position) } }
    val cardLineRow = rowOfLine.getOrNull(cardLine) ?: -1
    if (morph != null) {
        LaunchedEffect(cardLine) {
            if (cardLine < 0) morph.pageLyricAnchor = androidx.compose.ui.geometry.Offset.Unspecified
        }
    }

    /** Per-row trailing offset (px) of the cascade - see [StaggerTauPerRowMs]. Read in layers only. */
    val staggerLags = remember(lines) { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    var staggerAnchorRow by remember(lines) { androidx.compose.runtime.mutableIntStateOf(-1) }

    val staggerVelocities = remember(lines) { HashMap<Int, Float>() }

    LaunchedEffect(staggerLags) {
        while (true) {
            snapshotFlow { staggerLags.isNotEmpty() }.first { it }
            var lastNanos = 0L
            while (staggerLags.isNotEmpty()) {
                androidx.compose.runtime.withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                    lastNanos = now
                    val anchor = staggerAnchorRow
                    val done = ArrayList<Int>()
                    for ((row, lag) in staggerLags.toMap()) {
                        val k = (row - anchor).coerceIn(1, StaggerMaxRows)
                        // Critically damped toward 0, solved exactly (stable at any frame time).
                        val omega = 1000f / (StaggerLagPerRowMs * k)
                        val v = staggerVelocities[row] ?: 0f
                        val e = kotlin.math.exp(-omega * dt)
                        val tmp = (v + omega * lag) * dt
                        val nextLag = (lag + tmp) * e
                        val nextV = (v - omega * tmp) * e
                        if (kotlin.math.abs(nextLag) < 0.3f && kotlin.math.abs(nextV) < 4f) {
                            done += row
                        } else {
                            staggerLags[row] = nextLag
                            staggerVelocities[row] = nextV
                        }
                    }
                    done.forEach {
                        staggerLags.remove(it)
                        staggerVelocities.remove(it)
                    }
                }
            }
        }
    }

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

    LaunchedEffect(anchorIndex, followPlayback, lines, motionStyle) {
        val target = anchorIndex
        if (!followPlayback || target !in lines.indices) return@LaunchedEffect
        val targetRow = rowOfLine[target]

        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }

        val minOffsetPx = with(density) { MinPastLineVisibleHeight.toPx() }
        val restingOffset = maxOf(viewportHeight * ActiveLineViewportFraction, minOffsetPx).roundToInt()

        try {
        isAutoScrolling = true

        // The sheet itself on the move - the page reopening with the player, the list just built
        // and still at its top: put the line straight where it rests. Animating it there stacked
        // a long scroll, composing every row it passed, on top of the sheet's own open.
        if (isSheetDragging) {
            listState.scrollToItem(targetRow, 0)
            return@LaunchedEffect
        }

        // Compared by ROW index. This used to compare the line index against the list's item
        // index, and every gap row before the target shifted the two apart - after the first
        // instrumental break it measured the wrong item and scrolled to the wrong place.
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
        if (visible != null) {

            val current = visible.offset - layoutInfo.viewportStartOffset
            // A gap row above the target that's on its way out collapses during this same scroll
            // (same duration and easing), and that collapse alone lifts the target by its height.
            // Scroll only the rest, or the line would overshoot its resting place.
            val collapsing = layoutInfo.visibleItemsInfo
                .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                .sumOf { it.size }
            val distance = (current - restingOffset - collapsing).toFloat()

            staggerAnchorRow = targetRow
            listState.scroll {
                var previous = 0f
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = distance,
                    animationSpec = tween(durationMillis = RecenterDurationMs, easing = recenterEasingFor(motionStyle)),
                ) { value, _ ->
                    val consumed = scrollBy(value - previous)
                    previous += consumed
                    if (cascade && consumed != 0f) {
                        // Rows below the active line hold back by what just scrolled, then catch
                        // up on their own (decay above) - the further down, the later.
                        for (item in listState.layoutInfo.visibleItemsInfo) {
                            if (item.index > targetRow) {
                                staggerLags[item.index] = (staggerLags[item.index] ?: 0f) + consumed
                            }
                        }
                    }
                }
            }
        } else {

            listState.animateScrollToItem(targetRow, 0)
        }
        } finally {
            isAutoScrolling = false
        }
    }

    /** Which gap, if any, is happening right now. [NoInterlude] when a line is being sung. */
    val activeInterlude by remember(lines, rows) {
        derivedStateOf {
            val position = progress.position
            // Ends with the same lead-in the next line starts with, so the dots collapse on the
            // very frame that line takes over and the recenter scroll can account for it.
            rows.firstOrNull {
                it is LyricRow.Interlude && position >= it.startMs &&
                    position + LyricsUtils.LineLeadInMs < it.endMs
            }?.let { (it as LyricRow.Interlude).afterIndex } ?: NoInterlude
        }
    }
    val interludeActive = activeInterlude != NoInterlude

    // Only honour {agent:v1}/{agent:v2} when the song actually has more than one voice. A solo
    // track whose provider tagged every line as v2 would otherwise render entirely right-aligned.
    //
    // Untagged lines count as the lead voice rather than being ignored. Counting only non-null
    // agents meant a song that tags just its answering lines — some TTML leaves the primary
    // singer implicit — saw a single distinct agent and fell back to all-left, which is exactly
    // the case where left/right matters most. Background vocals are excluded because they are
    // centred regardless and would otherwise fake a second voice on a solo track.
    val useAgentAlignment = remember(lines) {
        lines.filter { !it.isBackground }
            .map { it.agent ?: "v1" }
            .distinct()
            .size > 1
    }

    val (lyricsWaveAnimation) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (lyricsHighBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    // Drives the per-line loading dots and lifts the readings out of the way. The button that
    // starts a translation lives in the translation sheet, not here.
    val translationStatus by LyricsTranslationHelper.status.collectAsState()
    val isTranslating = translationStatus is LyricsTranslationHelper.TranslationStatus.Translating

    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize()) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = LyricsHeaderArtX)
                // Matches where the morph layer actually draws the cover, so the hitbox sits on
                // the artwork and the menu centres against it.
                .padding(top = LyricsHeaderArtTopFromStatusBar, bottom = 10.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(LyricsHeaderArtSize)
                    .clip(RoundedCornerShape(10.dp))

                    .clickable { if (immersive) immersive = false else onClose() }
            )

            Spacer(modifier = Modifier.width(18.dp))

            // No menu button up here: on the lyrics page the options button takes the chevron's
            // slot next to the seek bar (BottomSheetPlayer), within reach of the thumb.
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                // Ends at the top of the seek bar (contentBottomInset). It used to reach 28dp past
                // its bottom, so lines scrolled behind the bar and the timestamps.
                .padding(bottom = (contentBottomInset * (1f - immersion)).coerceAtLeast(0.dp))
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
                        top = maxOf(listHeight * ActiveLineViewportFraction, MinPastLineVisibleHeight),
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
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                    ) { listIndex, row ->
                        when (row) {
                            is LyricRow.Interlude -> LyricsInterludeDots(
                                modifier = Modifier.graphicsLayer {
                                    translationY = staggerLags[listIndex] ?: 0f
                                    applyPageRowEnter(enterProgressProvider(), listIndex - cardLineRow.coerceAtLeast(0))
                                },
                                startMs = row.startMs,
                                endMs = row.endMs,
                                // A provider, not the position itself: passing the value would
                                // recompose this row on every playback tick.
                                positionProvider = positionProvider,
                                accentColor = accent,
                                visible = activeInterlude == row.afterIndex,
                                collapseEasing = recenterEasingFor(motionStyle),
                                // The gap belongs to whoever sings next, so it waits on that
                                // singer's side rather than always sitting down the middle.
                                alignment = lines.getOrNull(row.afterIndex + 1)
                                    ?.let { alignmentFor(it, useAgentAlignment) }
                                    ?: LyricsAlignment.CENTER,
                            )

                            is LyricRow.Line -> {
                                val index = row.index
                                val entry = row.entry

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

                                val romanized by entry.romanizedTextFlow.collectAsState()
                                val ruby by entry.rubyFlow.collectAsState()
                                val translated by entry.translatedTextFlow.collectAsState()
                                val rowIndex = rowOfLine[index]
                                val lineAlignment = alignmentFor(entry, useAgentAlignment)
                                // Only a start-aligned line has its text where the card's starts.
                                val growsFromCard = morph != null && index == cardLine &&
                                    lineAlignment == LyricsAlignment.START
                                LyricsGlowLine(
                                    modifier = Modifier
                                        .then(
                                            if (growsFromCard) {
                                                Modifier.onGloballyPositioned { coords ->
                                                    // Before this row's own layer, so the flight
                                                    // below doesn't move the point it flies to.
                                                    val origin = coords.positionInRoot()
                                                    morph!!.pageLyricAnchor = androidx.compose.ui.geometry.Offset(
                                                        origin.x + LineAnchorX.toPx(density),
                                                        origin.y + LineAnchorY.toPx(density),
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .graphicsLayer {
                                            translationY = staggerLags[rowIndex] ?: 0f
                                            val e = enterProgressProvider()
                                            val flew = growsFromCard && applyCardFlight(
                                                e = e,
                                                cardAnchor = morph!!.cardLyricAnchor.let {
                                                    if (it == androidx.compose.ui.geometry.Offset.Unspecified) it
                                                    else androidx.compose.ui.geometry.Offset(it.x - morph.cardLyricScroll, it.y)
                                                },
                                                pageAnchor = morph.pageLyricAnchor,
                                                localAnchor = androidx.compose.ui.geometry.Offset(
                                                    LineAnchorX.toPx(),
                                                    LineAnchorY.toPx(),
                                                ),
                                                cardToPage = CardToPageLyricScale,
                                            )
                                            if (!flew) applyPageRowEnter(e, rowIndex - cardLineRow.coerceAtLeast(0))
                                        },
                                    entry = entry,
                                    state = state,
                                    blurStage = blurStage,
                                    suppressEffects = suppressEffects,
                                    positionProvider = positionProvider,
                                    accentColor = accent,
                                    subLine = romanized,
                                    ruby = ruby,
                                    // Readings sit under the line normally. With a translation on
                                    // screen they move above it, so the line keeps one thing on
                                    // each side instead of two stacked underneath.
                                    //
                                    // Keyed on "translation requested", not "translation arrived",
                                    // so the layout settles once when you press the button rather
                                    // than shifting a second time when the text lands.
                                    rubyPlacement = if (isTranslating || !translated.isNullOrBlank()) {
                                        RubyPlacement.ABOVE
                                    } else {
                                        RubyPlacement.BELOW
                                    },
                                    translationLoading = isTranslating && translated.isNullOrBlank(),
                                    translation = translated,
                                    alignment = lineAlignment,
                                    waveEnabled = lyricsWaveAnimation && !suppressEffects,
                                    motionStyle = motionStyle,
                                    highBloom = lyricsHighBloom,
                                    onClick = {
                                        playerConnection.player.seekTo(entry.time)
                                        followPlayback = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Repeat and like used to float here at the top right; they now sit under the playback
            // timestamps in BottomSheetPlayer, where the song info row's own pair lives.
        }

    }
}

/** Sentinel for "no instrumental gap is playing right now". */
private const val NoInterlude = Int.MIN_VALUE

/**
 * One row of the lyrics list. Instrumental gaps are rows in their own right rather than something
 * a lyric line grows when the playhead reaches it — see the comment at the [buildLyricRows] call
 * site for why that matters to scrolling.
 */
private sealed interface LyricRow {
    /** Stable across the life of the song, so LazyColumn never re-creates a row. */
    val key: Long

    data class Line(val index: Int, val entry: LyricsEntry) : LyricRow {
        override val key get() = index.toLong()
    }

    /**
     * @param afterIndex the line this gap follows, or -1 for the intro before the first line.
     */
    data class Interlude(val afterIndex: Int, val startMs: Long, val endMs: Long) : LyricRow {
        // Offset into a range no line index can reach, so lines and interludes never collide.
        override val key get() = -1_000_000L - afterIndex
    }
}

private fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)

    // A long instrumental intro gets dots too. The old code could not show this: it keyed the dots
    // off "the item for currentIndex + 1", and before the first line currentIndex is -1.
    if (lines.first().time >= MinInterludeGapMs) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }

    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = LyricsUtils.lineEndMs(lines, index)
        if (next.time - gapStart >= MinInterludeGapMs) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

/**
 * Maps a line's `{agent:…}` tag onto a side of the screen. `v1` leads on the left, `v2` answers on
 * the right, `v1000` is a group/chorus line and sits centred; background vocals centre as well.
 */
private fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean): LyricsAlignment = when {
    !useAgentAlignment -> LyricsAlignment.START
    entry.isBackground -> LyricsAlignment.CENTER
    entry.agent == "v2" -> LyricsAlignment.END
    entry.agent == "v1000" -> LyricsAlignment.CENTER
    else -> LyricsAlignment.START
}

@Composable
internal fun LyricsLoadingDots(
    modifier: Modifier = Modifier,
    /** Start-aligned with a little air between the dots for the bottom card; centred on the page. */
    alignStart: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignStart) Arrangement.spacedBy(5.dp) else Arrangement.Center,
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
