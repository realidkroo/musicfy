// ProfileScreen.kt
//
// The Musicfy page. A dark header holds the profile ID card over a dot grid, with a giant MUSICFY
// column sliding up the right edge. Pull the header down and it fills the screen, the card flips to
// a scratch-off barcode, and scratching it opens the month's recap (ui/screens/recap) - which hands
// back to this header when it's done. Below: listening time (into Stats), version, settings.

package com.example.musicfy.ui.screens.settings

import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.component.rememberDeviceTilt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavController
import com.example.musicfy.BuildConfig
import com.example.musicfy.LocalAppContentObscured
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.ProfileCardNumberKey
import com.example.musicfy.constants.ProfileJoinedEpochDayKey
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.UsernameKey
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.HomeContentInset
import com.example.musicfy.ui.component.LocalZoomOutOverlayState
import com.example.musicfy.ui.component.OdometerNumber
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.screens.recap.DateLong
import com.example.musicfy.ui.screens.recap.Emphasized
import com.example.musicfy.ui.screens.recap.GiantTextState
import com.example.musicfy.ui.screens.recap.HeaderDots
import com.example.musicfy.ui.screens.recap.HeaderGrey
import com.example.musicfy.ui.screens.recap.HeaderText
import com.example.musicfy.ui.screens.recap.IdCardBack
import com.example.musicfy.ui.screens.recap.RecapAnchors
import com.example.musicfy.ui.screens.recap.RecapAvailability
import com.example.musicfy.ui.screens.recap.RecapRepository
import com.example.musicfy.ui.screens.recap.RecapStoryData
import com.example.musicfy.ui.screens.recap.RecapStoryHost
import com.example.musicfy.ui.screens.recap.ScanVerdict
import com.example.musicfy.ui.screens.recap.ScratchState
import com.example.musicfy.ui.screens.recap.StoryDark
import com.example.musicfy.ui.screens.recap.StoryDots
import com.example.musicfy.ui.screens.recap.StoryTextDark
import com.example.musicfy.ui.screens.recap.easeOutCubic
import com.example.musicfy.ui.screens.recap.giantText
import com.example.musicfy.ui.screens.recap.go
import com.example.musicfy.ui.screens.recap.lerpF
import com.example.musicfy.ui.screens.recap.recapBarcodeCaption
import com.example.musicfy.ui.screens.recap.recapBarcodePayload
import com.example.musicfy.ui.screens.recap.recapDots
import com.example.musicfy.ui.screens.recap.rememberRecapTime
import com.example.musicfy.ui.screens.setup.onboarding.IdCardAspect
import com.example.musicfy.ui.screens.setup.onboarding.ProfileIdCard
import com.example.musicfy.ui.screens.update.UpdateHeadline
import com.example.musicfy.ui.screens.update.rememberUpdateState
import com.example.musicfy.ui.screens.update.showUpdateSheet
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.stableSystemBars
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private object MusicfyGreeting {
    private fun pick() = listOf("Hello again", "Hey", "Welcome back", "Good to see you").random()

    private val greetingState = mutableStateOf("Hello again")
    private var wasBackgrounded = false

    val current: String get() = greetingState.value

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> wasBackgrounded = true
                    Lifecycle.Event.ON_START -> if (wasBackgrounded) {
                        greetingState.value = pick()
                        wasBackgrounded = false
                    }
                    else -> Unit
                }
            },
        )
    }
}

/** Where the header is in its pull-down / scratch / recap life. */
private enum class HeaderStage { Collapsed, Pulling, Expanding, Expanded, Scanning, Invalid, Story }

private val PageCard = Color(0xFF0F0F0F)

@Composable
fun ProfileScreen(navController: NavController) {
    val database = LocalDatabase.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val zoomOutState = LocalZoomOutOverlayState.current
    val updateState by rememberUpdateState()
    val obscured = LocalAppContentObscured.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val statusBarTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val topPx = with(density) { statusBarTop.toPx() }
    val cornerPx = with(density) { 26.dp.toPx() }

    val (localUsername) = rememberPreference(UsernameKey, "")
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val (cardNumberPref, setCardNumber) = rememberPreference(ProfileCardNumberKey, "")
    val (joinedEpochDay, setJoinedEpochDay) = rememberPreference(ProfileJoinedEpochDayKey, -1L)
    val (forceRecap) = rememberPreference(com.example.musicfy.constants.ForceMonthlyRecapKey, false)

    var liveAccountName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(innerTubeCookie) {
        if (innerTubeCookie.isBlank()) {
            liveAccountName = null
            return@LaunchedEffect
        }
        com.music.innertube.YouTube.cookie = innerTubeCookie
        com.music.innertube.YouTube.accountInfo().onSuccess { liveAccountName = it.name }
    }
    val accountName = localUsername.ifBlank { liveAccountName.orEmpty() }
    val totalListeningMs by database.totalListeningTimeMs().collectAsState(initial = 0L)

    // the onboarding writes the card's number and join date; an install from before it gets them once, here
    LaunchedEffect(cardNumberPref, joinedEpochDay) {
        if (cardNumberPref.isBlank()) setCardNumber((10_000_000_000L + Random.nextLong(89_999_999_999L)).toString())
        if (joinedEpochDay < 0L) setJoinedEpochDay((RecapRepository.firstPlayDate(database) ?: LocalDate.now()).toEpochDay())
    }
    val cardNumber = cardNumberPref.ifBlank { "00000000000" }
    val joinedDate = if (joinedEpochDay >= 0L) LocalDate.ofEpochDay(joinedEpochDay) else LocalDate.now()
    val joinedText = "joined at " + joinedDate.format(DateLong)
    val photo: Any? = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }

    // ---- header state -------------------------------------------------------------------------
    var stage by remember { mutableStateOf(HeaderStage.Collapsed) }
    var expand by remember { mutableFloatStateOf(0f) }
    var expandJob by remember { mutableStateOf<Job?>(null) }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var downInHeader by remember { mutableStateOf(false) }
    val flip = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var tiltTarget by remember { mutableStateOf(Offset.Zero) }
    val deviceTilt = rememberDeviceTilt()
    var scratchKey by remember { mutableIntStateOf(0) }
    val scratch = remember(scratchKey) { ScratchState() }
    val scan = remember { Animatable(0f) }
    var verdict by remember { mutableStateOf<ScanVerdict?>(null) }
    var recapKey by remember { mutableIntStateOf(0) }
    var recap by remember { mutableStateOf<RecapAvailability?>(null) }
    var story by remember { mutableStateOf<Pair<RecapStoryData, RecapAnchors>?>(null) }
    var storyCovering by remember { mutableStateOf(false) }
    var cardHidden by remember { mutableStateOf(false) }
    val contentIn = remember { Animatable(1f) }
    var rootOnScreen by remember { mutableStateOf(Offset.Zero) }
    var cardOnScreen by remember { mutableStateOf(Rect.Zero) }

    val time = rememberRecapTime(running = !storyCovering && !obscured.value)
    val measurer = rememberTextMeasurer()
    val giant = remember {
        GiantTextState("MUSICFY", filledRows = 0..0).apply { color = { lerp(HeaderText, StoryTextDark, expand) } }
    }

    // whether there's a recap waiting, so the pull hint can say so now and then
    var recapWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(forceRecap) {
        recapWaiting = runCatching {
            RecapRepository.recapFor(database, LocalDate.now(), force = forceRecap) is RecapAvailability.Ready
        }.getOrDefault(false)
    }
    // 0 = the chevron, 1 = "swipe down here!!"; it turns into the words every few seconds and back
    val hintWords = remember { Animatable(0f) }
    LaunchedEffect(recapWaiting, stage) {
        if (!recapWaiting || stage != HeaderStage.Collapsed) {
            hintWords.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        while (true) {
            delay(3600)
            hintWords.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            delay(2400)
            hintWords.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(recapKey) {
        if (recapKey == 0) return@LaunchedEffect
        recap = null
        recap = RecapRepository.recapFor(database, LocalDate.now(), force = forceRecap)
    }
    // the finger's tilt, eased so the card feels weighty rather than glued on
    LaunchedEffect(Unit) {
        snapshotFlow { tiltTarget }.collect { t ->
            val settle = t == Offset.Zero
            val spec = if (settle) spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessMediumLow)
            launch { tiltX.animateTo(t.y, spec) }
            launch { tiltY.animateTo(t.x, spec) }
        }
    }

    fun animateExpand(target: Float, ms: Int) {
        expandJob?.cancel()
        val from = expand
        expandJob = scope.launch { animate(from, target, animationSpec = tween(ms, easing = Emphasized)) { v, _ -> expand = v } }
    }

    fun collapse() {
        stage = HeaderStage.Collapsed
        pullPx = 0f
        animateExpand(0f, 650)
        scope.launch { flip.animateTo(0f, tween(650, easing = FastOutSlowInEasing)) }
        scope.launch {
            delay(650)
            scratchKey++
            scan.snapTo(0f)
            verdict = null
        }
    }

    fun expandFully() {
        stage = HeaderStage.Expanding
        pullPx = 0f
        tiltTarget = Offset.Zero
        recapKey++
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        animateExpand(1f, 760)
        scope.launch {
            delay(140)
            flip.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            stage = HeaderStage.Expanded
        }
    }

    fun onScratched() {
        if (stage != HeaderStage.Expanded) return
        stage = HeaderStage.Scanning
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            scratch.dissolve.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            val r = recap ?: snapshotFlow { recap }.filterNotNull().first()
            when (r) {
                is RecapAvailability.Ready -> {
                    stage = HeaderStage.Story
                    val headerTop = rootOnScreen.y
                    val top = topPx
                    story = RecapStoryData(
                        stats = r.stats,
                        monthProgress = r.monthProgressPercent,
                        username = accountName,
                        photo = photo,
                        cardNumber = cardNumber,
                        joinedText = joinedText,
                        joinedDate = joinedDate,
                        allTimeMs = totalListeningMs,
                    ) to RecapAnchors(
                        expandedCard = cardOnScreen,
                        headerCard = Rect(
                            offset = Offset(cardOnScreen.left, headerTop + top + with(density) { CardTopDp.dp.toPx() }),
                            size = cardOnScreen.size,
                        ),
                        headerHeight = headerTop + collapsedHeaderPx(top, cardOnScreen.height, density.density),
                        topInset = headerTop + top,
                    )
                }
                is RecapAvailability.NotYet -> {
                    verdict = ScanVerdict.Invalid
                    scan.animateTo(1f, tween(1950, easing = LinearEasing))
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    stage = HeaderStage.Invalid
                }
            }
        }
    }

    BackHandler(enabled = stage == HeaderStage.Expanded || stage == HeaderStage.Invalid || stage == HeaderStage.Pulling) { collapse() }

    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { rootOnScreen = it.positionOnScreen() }
            .pointerInput(Unit) {
                // a new touch is "in the header" only if the header itself says so (it sees it next)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    downInHeader = false
                }
            },
    ) {
        val viewportH = constraints.maxHeight.toFloat()
        val inset = with(density) { HomeContentInset.toPx() }
        val cardW = constraints.maxWidth - 2 * inset
        val cardH = cardW / IdCardAspect
        val collapsedH = collapsedHeaderPx(topPx, cardH, density.density)
        val cardTopCollapsed = topPx + with(density) { CardTopDp.dp.toPx() }
        val cardTopExpanded = viewportH * 0.49f - cardH / 2f
        val pullRange = (viewportH - collapsedH).coerceAtLeast(1f)
        val collapseProgress by remember { derivedStateOf { (scrollState.value / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f) } }

        val pullConnection = remember(pullRange) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (stage != HeaderStage.Pulling || available.y >= 0f) return Offset.Zero
                    val before = pullPx
                    pullPx = (pullPx + available.y).coerceAtLeast(0f)
                    expand = (pullPx * 0.62f / pullRange).coerceIn(0f, 0.8f)
                    if (pullPx == 0f) stage = HeaderStage.Collapsed
                    return Offset(0f, pullPx - before)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val canPull = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling
                    if (!canPull || !downInHeader || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    stage = HeaderStage.Pulling
                    pullPx += available.y
                    expand = (pullPx * 0.62f / pullRange).coerceIn(0f, 0.8f)
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (stage != HeaderStage.Pulling) return Velocity.Zero
                    if (expand > 0.16f || available.y > 1600f) expandFully() else collapse()
                    return available
                }
            }
        }

        // ---- the scrolling page ---------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { collapseProgress > 0f && stage == HeaderStage.Collapsed }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullConnection)
                    .verticalScroll(scrollState, enabled = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling),
            ) {
                // header: grows to the viewport as it's pulled
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val h = lerpF(collapsedH, viewportH, expand).roundToInt()
                            val p = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                            layout(constraints.maxWidth, h) { p.place(0, 0) }
                        }
                        .pointerInput(Unit) {
                            // tilt follows the finger; nothing is consumed, so scrolling still works
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                downInHeader = true
                                var acc = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling) {
                                        acc += change.positionChange()
                                        tiltTarget = Offset(
                                            (acc.x * 0.09f).coerceIn(-26f, 26f),
                                            (-acc.y * 0.07f).coerceIn(-18f, 18f),
                                        )
                                    }
                                }
                                tiltTarget = Offset.Zero
                            }
                        }
                        .pointerInput(stage) {
                            if (stage != HeaderStage.Expanded && stage != HeaderStage.Invalid) return@pointerInput
                            // expanded: a swipe up folds it away again
                            var dy = 0f
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dy < -with(density) { 64.dp.toPx() }) collapse() else animateExpand(1f, 300)
                                    dy = 0f
                                },
                                onDragCancel = { animateExpand(1f, 300) },
                            ) { change, amount ->
                                change.consume()
                                dy += amount
                                expand = (1f + dy.coerceAtMost(0f) / pullRange * 0.8f).coerceIn(0.4f, 1f)
                            }
                        }
                        .clipBottomRounded { cornerPx * (1f - expand) }
                        .drawBehind { drawRect(lerp(HeaderGrey, StoryDark, expand)) }
                        .giantText(giant, time, measurer, centerY = { windowHeight / 2f - rootOnScreen.y })
                        .recapDots(color = { lerp(HeaderDots, StoryDots, expand) }),
                ) {
                    // greeting (in the header; its scrolled-away twin lives in the top bar)
                    Text(
                        text = "${MusicfyGreeting.current}, ${accountName.ifBlank { "there" }}!",
                        style = PageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = HomeContentInset, end = HomeContentInset, top = statusBarTop + 80.dp)
                            .graphicsLayer {
                                alpha = ((1f - expand * 3f) * (1f - collapseProgress * 1.6f)).coerceIn(0f, 1f) * contentIn.value
                                translationY = (1f - contentIn.value) * 24.dp.toPx()
                            },
                    )

                    // the card: tilts with the finger, drifts a little on its own, flips to the barcode
                    val showBack by remember { derivedStateOf { flip.value >= 0.5f } }
                    val coatingGone by remember(scratch) { derivedStateOf { scratch.dissolve.value >= 1f } }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = HomeContentInset)
                            .fillMaxWidth()
                            .aspectRatio(IdCardAspect)
                            .offset { androidx.compose.ui.unit.IntOffset(0, lerpF(cardTopCollapsed, cardTopExpanded, expand).roundToInt()) }
                            .onGloballyPositioned { coords ->
                                val p = coords.positionOnScreen()
                                cardOnScreen = Rect(p, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                            }
                            .graphicsLayer {
                                val live = stage == HeaderStage.Collapsed || stage == HeaderStage.Pulling
                                val sway = if (live) 1f else 0f
                                val t = time.sway
                                // the phone's lean too; half as much on the back, so scratching stays steady
                                val lean = if (flip.value >= 0.5f) 0.5f else 1f
                                rotationY = flip.value * 180f + tiltY.value + deviceTilt.rotationY * lean + sway * sin(t * 0.9f) * 3f
                                rotationX = tiltX.value + deviceTilt.rotationX * lean + sway * cos(t * 0.7f) * 2f - (if (stage == HeaderStage.Pulling) expand * 14f else 0f)
                                cameraDistance = 14f * this.density
                                alpha = if (cardHidden) 0f else 1f
                            },
                    ) {
                        if (!showBack) {
                            ProfileIdCard(
                                username = accountName,
                                photo = photo,
                                cardNumber = cardNumber,
                                joinedText = joinedText,
                                showIdCardOf = true,
                            )
                        } else {
                            val range = (recap as? RecapAvailability.Ready)?.stats?.range
                            val payload = recapBarcodePayload(range)
                            IdCardBack(
                                username = accountName,
                                payload = payload,
                                caption = recapBarcodeCaption(payload, cardNumber),
                                scratch = scratch.takeIf { !coatingGone && (stage == HeaderStage.Expanding || stage == HeaderStage.Expanded || stage == HeaderStage.Scanning) },
                                time = time,
                                scan = { scan.value },
                                verdict = { verdict },
                                onScratchProgress = { if (it >= 0.55f) onScratched() },
                                modifier = Modifier.graphicsLayer { rotationY = 180f },
                            )
                        }
                    }

                    // pull hint: a chevron that, with a recap waiting, turns into words now and then
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { androidx.compose.ui.unit.IntOffset(0, (cardTopCollapsed + cardH + 14.dp.toPx()).roundToInt()) }
                            .height(30.dp)
                            .graphicsLayer {
                                alpha = (1f - expand * 4f).coerceIn(0f, 1f) * contentIn.value
                                translationY = ((sin(time.sway * 2.4f) + 1f) / 2f) * 4.dp.toPx()
                            },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = "Pull down",
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    val w = hintWords.value
                                    alpha = 1f - w
                                    scaleX = 1f - 0.3f * w
                                    scaleY = 1f - 0.3f * w
                                    renderEffect = BlurEffectCache.get(w * 14f)
                                },
                        )
                        if (recapWaiting) {
                            Text(
                                text = "swipe down here!!",
                                style = MonoSmall.copy(color = Color.White.copy(alpha = 0.6f)),
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer {
                                    val w = hintWords.value
                                    alpha = w
                                    scaleX = 0.85f + 0.15f * w
                                    scaleY = 0.85f + 0.15f * w
                                    renderEffect = BlurEffectCache.get((1f - w) * 14f)
                                },
                            )
                        }
                    }

                    // under the expanded card: "scratch!", or why the code didn't scan
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { androidx.compose.ui.unit.IntOffset(0, (cardTopExpanded + cardH + 34.dp.toPx()).roundToInt()) }
                            .padding(horizontal = 40.dp)
                            .graphicsLayer {
                                alpha = ((expand - 0.75f) / 0.25f).coerceIn(0f, 1f)
                                translationY = (1f - expand) * 40.dp.toPx()
                            },
                    ) {
                        val invalid = stage == HeaderStage.Invalid
                        Text(
                            text = if (invalid) "come back later" else "scratch!",
                            style = PageTitle.copy(fontSize = 20.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.graphicsLayer { alpha = if (invalid) 1f else 1f - scratch.dissolve.value },
                        )
                        if (invalid) {
                            Text(
                                text = (recap as? RecapAvailability.NotYet)?.comeBack.orEmpty(),
                                style = PageTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                text = "swipe up to close",
                                style = MonoSmall.copy(color = Color.White.copy(alpha = 0.35f)),
                                modifier = Modifier.padding(top = 26.dp),
                            )
                        }
                    }
                }

                // ---- everything under the header ----
                Column(
                    modifier = Modifier
                        .padding(horizontal = HomeContentInset)
                        .graphicsLayer {
                            alpha = contentIn.value * (1f - expand * 2f).coerceIn(0f, 1f)
                            translationY = (1f - contentIn.value) * 60.dp.toPx()
                        },
                ) {
                    Spacer(Modifier.height(24.dp))
                    ListeningTimeCard(totalListeningMs, onViewStats = { navController.navigate("stats") })
                    Spacer(Modifier.height(16.dp))
                    VersionCard(
                        updateHeadline = if (updateState is com.example.musicfy.core.updater.UpdateState.Available) UpdateHeadline else null,
                        onAbout = { zoomOutState.showUpdateSheet(updateState) },
                    )
                    Spacer(Modifier.height(28.dp))
                    Text("Settings", style = PageTitle.copy(fontSize = 15.sp), modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                    OpenSettingsRow(onClick = { navController.navigate("musicfy_settings") })
                    Spacer(Modifier.height(180.dp))
                }
            }
        }

        // ---- the glass top bar, a sibling of the glassRoot box (never inside it) ------------------
        if (collapseProgress > 0.005f && stage == HeaderStage.Collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 84.dp)
                    .align(Alignment.TopCenter),
            ) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 55f * collapseProgress },
                    foundationColor = Color.Black.copy(alpha = 0.55f * collapseProgress),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (collapseProgress * 1.5f).coerceIn(0f, 1f) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black, Color.Black.copy(alpha = 0.85f * collapseProgress), Color.Transparent),
                            ),
                        ),
                )
            }
        }

        // ---- logo, and the greeting sliding into the bar beside it ----
        Icon(
            painter = painterResource(R.drawable.ic_musicfy_mark),
            contentDescription = "Musicfy",
            tint = Color.White,
            modifier = Modifier
                .padding(start = HomeContentInset, top = statusBarTop + TopBarLogoTop)
                .size(width = 31.dp, height = 34.dp)
                .graphicsLayer {
                    val k = 1f - collapseProgress * 0.18f
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        )
        if (collapseProgress > 0.01f) {
            Text(
                text = "${MusicfyGreeting.current}, ${accountName.ifBlank { "there" }}!",
                style = PageTitle.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = HomeContentInset + 40.dp, end = HomeContentInset, top = statusBarTop + 37.dp)
                    .graphicsLayer {
                        val p = ((collapseProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        alpha = easeOutCubic(p)
                        translationY = (1f - easeOutCubic(p)) * 12.dp.toPx()
                    },
            )
        }
    }

    story?.let { (data, anchors) ->
        val payload = recapBarcodePayload(data.stats.range)
        RecapStoryHost(
            data = data,
            anchors = anchors,
            payload = payload,
            caption = recapBarcodeCaption(payload, cardNumber),
            onCovered = {
                // hidden under the story: reset to the collapsed header, card and content tucked away
                storyCovering = true
                expandJob?.cancel()
                expand = 0f
                cardHidden = true
                scope.launch {
                    flip.snapTo(0f)
                    scan.snapTo(0f)
                    contentIn.snapTo(0f)
                    scrollState.scrollTo(0)
                }
                scratchKey++
                verdict = null
            },
            onReturned = {
                cardHidden = false
                storyCovering = false
                stage = HeaderStage.Collapsed
            },
            onClosed = {
                story = null
                scope.launch { contentIn.go(1f, 900, Emphasized) }
            },
        )
    }
}

private const val CardTopDp = 130f

/** The logo's top below the status bar: Settings and Library centre a 32dp mark in a 62dp block 18dp down. */
private val TopBarLogoTop = 32.dp

/** Inner padding every card on this page shares. */
private val CardPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp)

/** The collapsed header: inset, logo, greeting, the card, and room for the pull hint. */
private fun collapsedHeaderPx(top: Float, cardH: Float, density: Float): Float = top + CardTopDp * density + cardH + 49f * density

/** Clips to the bounds with only the bottom corners rounded, by a radius that can change every frame. */
private fun Modifier.clipBottomRounded(radius: () -> Float): Modifier = drawWithCache {
    val path = Path()
    onDrawWithContent {
        val r = radius()
        if (r <= 0.5f) {
            drawContent()
            return@onDrawWithContent
        }
        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                bottomLeft = CornerRadius(r),
                bottomRight = CornerRadius(r),
            ),
        )
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

private val PageTitle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    letterSpacing = (-0.6).sp,
    color = Color.White,
)

private val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
private fun ListeningTimeCard(totalMs: Long, onViewStats: () -> Unit) {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PageCard)
            .clickable(onClick = onViewStats)
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeValue((totalMinutes / 60).toString(), "h")
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color.White.copy(alpha = 0.4f)),
            )
            TimeValue((totalMinutes % 60).toString(), "m")
            Spacer(Modifier.weight(1f))
            Text(
                "Total Listening\ntime",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF8A8A8A)),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF474747)))
        Spacer(Modifier.height(12.dp))
        Text(
            "View Stats  >",
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF8A8A8A)),
        )
    }
}

@Composable
private fun TimeValue(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        OdometerNumber(
            value = value,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = Color.White,
            letterSpacing = (-2).sp,
        )
        Text(
            unit,
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White),
            modifier = Modifier.padding(start = 1.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun VersionCard(updateHeadline: String?, onAbout: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PageCard)
            .clickable(onClick = onAbout)
            .padding(CardPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Version", style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF9A9A9A)))
            Text(
                // "7.1.1 build#1093" -> "7.1.1"
                BuildConfig.VERSION_NAME.substringBefore(' '),
                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-2).sp, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (updateHeadline != null) {
                Text(updateHeadline, style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = Color(0xFF88FF76)), maxLines = 1)
            }
        }
        Text(
            if (updateHeadline != null) "Update" else "About app",
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF4D4D4D))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OpenSettingsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(CardPadding),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.settings), contentDescription = null, tint = Color(0xFF232323), modifier = Modifier.size(16.dp))
        }
        Text("Open settings", style = PageTitle.copy(fontSize = 20.sp))
    }
}
