// PlayerCustomizeScreen.kt

package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerBackgroundStyleKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.PlayerCoverStyleKey
import com.example.musicfy.constants.ShowBigDiscStylesKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.player.SeamBlur
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

internal val SwipeEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
internal const val SwipeDurationMillis = 900

private const val SnapCommitThreshold = 0.18f

private const val CarouselFlingVelocity = 400f

@Stable
private class CarouselState(val count: Int, initial: Int) {
    val position = mutableFloatStateOf(initial.toFloat())

    val page by derivedStateOf { position.floatValue.roundToInt().coerceIn(0, count - 1) }

    fun offset(): Float = position.floatValue - position.floatValue.roundToInt()

    fun drag(deltaPages: Float, fromPage: Int) {

        position.floatValue = (position.floatValue + deltaPages)
            .coerceIn(
                (fromPage - 1).coerceAtLeast(0).toFloat(),
                (fromPage + 1).coerceAtMost(count - 1).toFloat(),
            )
    }

    fun targetAfterDrag(fromPage: Int, velocity: Float): Int {
        val travelled = position.floatValue - fromPage
        return when {
            velocity < -CarouselFlingVelocity -> fromPage + 1
            velocity > CarouselFlingVelocity -> fromPage - 1
            travelled > SnapCommitThreshold -> fromPage + 1
            travelled < -SnapCommitThreshold -> fromPage - 1
            else -> fromPage
        }.coerceIn(0, count - 1)
    }
}

private const val SectionFlingVelocity = 700f

private val PageSurface = Color(0xFF121214)
private val StageCorner = 30.dp

private val HeaderBlurHeight = 92.dp

private val PlayerBackgroundStyle.displayName: String
    get() = when (this) {
        PlayerBackgroundStyle.COVER_GRADIENT -> "simple gradient based on cover"
        PlayerBackgroundStyle.SOLID -> "simple gray color"
        PlayerBackgroundStyle.DARK_GRADIENT -> "simple dark static gradient"
        PlayerBackgroundStyle.APPLE_MUSIC -> "Apple music style cover morph"
    }

internal fun GraphicsLayerScope.applySwipe(offset: Float, distance: Float) {
    translationX = -offset * distance
    alpha *= (1f - (abs(offset) * 2f)).coerceIn(0f, 1f)
}

@Composable
fun PlayerCustomizeScreen(

    onBack: () -> Unit,
    modifier: Modifier = Modifier,

    showLongPressHint: Boolean = false,
) {
    BackHandler(onBack = onBack)

    val playerConnection = LocalPlayerConnection.current
    val trackInfo by playerConnection?.uiState?.trackInfo?.collectAsState()
        ?: remember { mutableStateOf(TrackInfo()) }
    val isPlaying = playerConnection?.uiState?.transportState?.collectAsState()?.value?.isPlaying == true
    val queueIndex = playerConnection?.uiState?.queueState?.collectAsState()?.value?.currentIndex ?: 0

    var coverStyle by rememberEnumPreference(PlayerCoverStyleKey, PlayerCoverStyle.EDGE_TO_EDGE)
    var backgroundStyle by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        PlayerBackgroundStyle.COVER_GRADIENT,
    )

    val (showBigDiscStyles) = rememberPreference(ShowBigDiscStylesKey, defaultValue = false)
    val coverStyles = remember(showBigDiscStyles, coverStyle) {
        PlayerCoverStyle.entries.filter { style ->
            showBigDiscStyles || !style.isBigDisc || style == coverStyle
        }
    }
    val backgroundStyles = remember { PlayerBackgroundStyle.entries.toList() }

    val coverCarousel = remember(coverStyles.size) {
        CarouselState(coverStyles.size, coverStyles.indexOf(coverStyle).coerceAtLeast(0))
    }
    val backgroundCarousel = remember {
        CarouselState(backgroundStyles.size, backgroundStyles.indexOf(backgroundStyle).coerceAtLeast(0))
    }

    val shownCoverStyle = coverStyles.getOrNull(coverCarousel.page) ?: coverStyle
    val shownBackgroundStyle = backgroundStyles.getOrNull(backgroundCarousel.page) ?: backgroundStyle

    LaunchedEffect(shownCoverStyle) {
        if (coverStyle != shownCoverStyle) coverStyle = shownCoverStyle
    }
    LaunchedEffect(shownBackgroundStyle) {
        if (backgroundStyle != shownBackgroundStyle) backgroundStyle = shownBackgroundStyle
    }

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(SwipeDurationMillis / 2, easing = SwipeEasing))
    }

    val stageGlass = remember { GlassState() }

    var interacting by remember { mutableStateOf(false) }
    val sectionValue = remember { mutableFloatStateOf(0f) }
    val sectionProvider = remember { { sectionValue.floatValue } }
    val onBackgroundSection by remember { derivedStateOf { sectionValue.floatValue > 0.5f } }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()

            .background(PageSurface)
            .graphicsLayer {
                val r = reveal.value
                alpha = r
                val s = 0.98f + 0.02f * r
                scaleX = s
                scaleY = s
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val screenHeightPx = with(density) { screenHeight.toPx() }
        val sectionTravelPx = screenHeightPx * 0.45f

        val dragState = rememberDraggableState { delta ->
            sectionValue.floatValue =
                (sectionValue.floatValue - delta / sectionTravelPx).coerceIn(0f, 1f)
        }

        val pageWidthPx = with(density) { (screenWidth * 0.33f).toPx() }
        var dragFromPage by remember { mutableIntStateOf(0) }
        val activeCarousel = if (onBackgroundSection) backgroundCarousel else coverCarousel
        val carouselDragState = rememberDraggableState { delta ->
            activeCarousel.drag(-delta / pageWidthPx, dragFromPage)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()

                .draggable(
                    state = carouselDragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragFromPage = activeCarousel.page
                        interacting = true
                    },
                    onDragStopped = { velocity ->
                        val target = activeCarousel.targetAfterDrag(dragFromPage, velocity)
                        animate(
                            initialValue = activeCarousel.position.floatValue,
                            targetValue = target.toFloat(),
                            animationSpec = tween(SwipeDurationMillis, easing = SwipeEasing),
                        ) { value, _ -> activeCarousel.position.floatValue = value }
                        interacting = false
                    },
                )
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { interacting = true },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -SectionFlingVelocity -> 1f
                            velocity > SectionFlingVelocity -> 0f
                            sectionValue.floatValue > 0.5f -> 1f
                            else -> 0f
                        }

                        animate(
                            initialValue = sectionValue.floatValue,
                            targetValue = target,
                            animationSpec = tween(SwipeDurationMillis, easing = SwipeEasing),
                        ) { value, _ -> sectionValue.floatValue = value }
                        interacting = false
                    },
                )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()

                    .glassRoot(stageGlass, isActive = { !interacting && sectionProvider() < 0.55f })
            ) {
                PreviewStage(
                    sectionProvider = sectionProvider,
                    coverSwipeProvider = { coverCarousel.offset() },
                    backgroundSwipeProvider = { backgroundCarousel.offset() },
                    coverStyle = shownCoverStyle,
                    backgroundStyle = shownBackgroundStyle,
                    backgroundStyles = backgroundStyles,
                    backgroundPage = backgroundCarousel.page,
                    showNeighbours = onBackgroundSection,
                    animateBackdrop = !interacting,
                    trackInfo = trackInfo,
                    isPlaying = isPlaying,
                    queueIndex = queueIndex,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    statusBarTop = statusBarTop,
                )
            }

            SeamBlur(
                glassState = stageGlass,
                progressProvider = { 1f },
                trackInfo = trackInfo,
                maxHeight = screenHeight,

                fadeProvider = {
                    if (interacting) 0f else (1f - sectionProvider() * 2f).coerceIn(0f, 1f)
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - sectionProvider() * 2f).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.32f))
            )

            SectionContent(
                topOffset = screenHeight * CoverRegionFraction + 14.dp,
                enterFromBelow = false,
                sectionProvider = sectionProvider,
                screenHeightPx = screenHeightPx,
            ) {
                PageDots(count = coverStyles.size, selected = coverCarousel.page)
                Spacer(modifier = Modifier.height(18.dp))
                SectionCaption(title = "Style", subtitle = shownCoverStyle.displayName)
                Spacer(modifier = Modifier.height(18.dp))
                OptionCard(
                    options = shownCoverStyle.options,
                    swipeProvider = { coverCarousel.offset() },
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Scroll down to edit background",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            SectionContent(
                topOffset = screenHeight *
                    (BackgroundStageTopFraction + BackgroundStageHeightFraction) + 26.dp,
                enterFromBelow = true,
                sectionProvider = sectionProvider,
                screenHeightPx = screenHeightPx,
            ) {
                SectionCaption(
                    title = "Bg Style",
                    subtitle = shownBackgroundStyle.displayName,
                    centered = true,
                )
                Spacer(modifier = Modifier.height(20.dp))
                PageDots(count = backgroundStyles.size, selected = backgroundCarousel.page)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop + 16.dp)
                .fillMaxWidth()
                .padding(horizontal = 76.dp),
        ) {
            Text(
                text = "Currently editing",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,

                lineHeight = 18.sp,
            )
            Text(
                text = if (onBackgroundSection) "Background" else "cover",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 12.sp,
            )
        }

        EditOverlayBackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

        if (showLongPressHint) {
            LongPressHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }
    }

    LaunchedEffect(coverStyle) {
        val index = coverStyles.indexOf(coverStyle)
        if (index >= 0 && index != coverCarousel.page) {
            coverCarousel.position.floatValue = index.toFloat()
        }
    }
    LaunchedEffect(backgroundStyle) {
        val index = backgroundStyles.indexOf(backgroundStyle)
        if (index >= 0 && index != backgroundCarousel.page) {
            backgroundCarousel.position.floatValue = index.toFloat()
        }
    }
}

private const val BackgroundStageTopFraction = 0.13f
private const val BackgroundStageHeightFraction = 0.63f
private const val BackgroundStageWidthFraction = 0.78f

private val StagePageGap = 18.dp

private fun Modifier.stageLayout(
    sectionProvider: () -> Float,
    screenWidth: Dp,
    screenHeight: Dp,
    density: androidx.compose.ui.unit.Density,

    pageOffsetProvider: () -> Float = { 0f },
): Modifier = this.layout { measurable, constraints ->
    val s = sectionProvider()
    val fullW = with(density) { screenWidth.toPx() }
    val fullH = with(density) { screenHeight.toPx() }

    val startW = fullW
    val startH = fullH
    val endW = fullW * BackgroundStageWidthFraction
    val endH = fullH * BackgroundStageHeightFraction

    val w = lerp(startW, endW, s)
    val h = lerp(startH, endH, s)
    val gap = with(density) { StagePageGap.toPx() }
    val x = lerp(0f, (fullW - endW) / 2f, s) + pageOffsetProvider() * (endW + gap) * s
    val y = lerp(0f, fullH * BackgroundStageTopFraction, s)

    val placeable = measurable.measure(
        Constraints.fixed(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.toInt(), y.toInt())
    }
}

@Composable
private fun PreviewStage(
    sectionProvider: () -> Float,
    coverSwipeProvider: () -> Float,
    backgroundSwipeProvider: () -> Float,
    coverStyle: PlayerCoverStyle,
    backgroundStyle: PlayerBackgroundStyle,
    backgroundStyles: List<PlayerBackgroundStyle>,
    backgroundPage: Int,
    showNeighbours: Boolean,

    animateBackdrop: Boolean,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    queueIndex: Int,
    screenWidth: Dp,
    screenHeight: Dp,
    statusBarTop: Dp,
) {
    val density = LocalDensity.current
    val artBox = remember(coverStyle, screenWidth, screenHeight, statusBarTop) {
        coverArtBox(coverStyle, screenWidth, screenHeight, statusBarTop)
    }

    val fx = artBox.x / screenWidth
    val fy = artBox.y / screenHeight
    val fw = artBox.width / screenWidth
    val fh = artBox.height / screenHeight

    if (showNeighbours) {

        listOf(-1, 1).forEach { delta ->
            val neighbour = backgroundStyles.getOrNull(backgroundPage + delta) ?: return@forEach
            Box(
                modifier = Modifier
                    .stageLayout(
                        sectionProvider = sectionProvider,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        density = density,
                        pageOffsetProvider = { delta - backgroundSwipeProvider() },
                    )
                    .graphicsLayer {
                        val s = sectionProvider()
                        alpha = ((s - 0.5f) * 2f).coerceIn(0f, 1f) * 0.55f
                        shape = RoundedCornerShape(StageCorner.toPx() * s)
                        clip = true
                    }
            ) {
                PlayerBackgroundPreview(
                    style = neighbour,
                    thumbnailUrl = trackInfo.thumbnailUrl,
                    width = screenWidth,
                    height = screenHeight,

                    animate = false,
                    warp = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .stageLayout(
                sectionProvider = sectionProvider,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,

                pageOffsetProvider = { -backgroundSwipeProvider() },
            )
            .graphicsLayer {
                shape = RoundedCornerShape(StageCorner.toPx() * sectionProvider())
                clip = true
            }
    ) {
        PlayerBackgroundPreview(
            style = backgroundStyle,
            thumbnailUrl = trackInfo.thumbnailUrl,

            width = screenWidth,
            height = screenHeight,
            animate = animateBackdrop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = (constraints.maxWidth * fw).toInt().coerceAtLeast(1)
                    val h = (constraints.maxHeight * fh).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (constraints.maxWidth * fx).toInt(),
                            (constraints.maxHeight * fy).toInt(),
                        )
                    }
                }
                .graphicsLayer {

                    alpha = (1f - ((sectionProvider() - 0.5f) / 0.5f)).coerceIn(0f, 1f)
                    applySwipe(coverSwipeProvider(), size.width * 1.15f)
                }
        ) {
            CoverPreviewContent(
                style = coverStyle,
                trackInfo = trackInfo,
                isPlaying = isPlaying,
                queueIndex = queueIndex,
            )
        }
    }
}

@Composable
private fun CoverPreviewContent(
    style: PlayerCoverStyle,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    queueIndex: Int,
) {
    if (style.isDisc) {
        DiscCoverStack(
            style = style,
            artworkUrl = trackInfo.thumbnailUrl,
            mediaId = trackInfo.mediaId,
            queueIndex = queueIndex,
            isPlaying = isPlaying,
            spinActive = true,
            editMode = true,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val url = trackInfo.thumbnailUrl ?: return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url.resize(1200, 1200))
            .crossfade(300)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (style == PlayerCoverStyle.SQUARED) {
                    Modifier.clip(RoundedCornerShape(22.dp))
                } else Modifier
            ),
    )
}

@Composable
private fun SectionContent(
    topOffset: Dp,
    enterFromBelow: Boolean,
    sectionProvider: () -> Float,
    screenHeightPx: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topOffset)
            .graphicsLayer {
                val s = sectionProvider()
                if (enterFromBelow) {
                    translationY = (1f - s) * screenHeightPx * 0.30f
                    alpha = ((s - 0.5f) * 2.5f).coerceIn(0f, 1f)
                } else {
                    translationY = -s * screenHeightPx * 0.30f
                    alpha = (1f - s * 2.5f).coerceIn(0f, 1f)
                }
            },
        content = content,
    )
}

@Composable
private fun SectionCaption(title: String, subtitle: String, centered: Boolean = false) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = subtitle,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        repeat(count) { index ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isSelected) 0.95f else 0.40f))
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OptionCard(options: List<CoverOption>, swipeProvider: () -> Float) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .animateContentSize(
                animationSpec = tween(durationMillis = SwipeDurationMillis, easing = SwipeEasing)
            )

            .padding(vertical = 6.dp)
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = swipeFade(swipeProvider()) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    when (option) {
                        is CoverOption.Switch -> SwitchOptionRow(option)
                        is CoverOption.Text -> TextOptionRow(option)
                    }
                }
            }
        }
    }
}

private fun swipeFade(offset: Float): Float = (1f - (abs(offset) * 2f)).coerceIn(0f, 1f)

@Composable
private fun SwitchOptionRow(option: CoverOption.Switch) {
    val parent = option.parent

    val parentEnabled = if (parent == null) {
        true
    } else {
        val (parentValue) = rememberPreference(parent.key, defaultValue = parent.default)
        if (parent.inverted) !parentValue else parentValue
    }
    if (!parentEnabled) return

    var stored by rememberPreference(option.key, defaultValue = option.default)
    val checked = if (option.inverted) !stored else stored

    OptionRowShell(
        icon = option.icon,
        title = option.title,
        description = option.description,
        indented = parent != null,
        onClick = { stored = if (option.inverted) checked else !checked },
    ) {
        AppSwitch(
            checked = checked,
            onCheckedChange = { wanted -> stored = if (option.inverted) !wanted else wanted },
        )
    }
}

@Composable
private fun TextOptionRow(option: CoverOption.Text) {
    var stored by rememberPreference(option.key, defaultValue = "")
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        com.example.musicfy.ui.component.TextFieldDialog(
            title = { Text(option.title) },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = stored,
                selection = androidx.compose.ui.text.TextRange(stored.length),
            ),
            placeholder = { Text(option.placeholder) },

            isInputValid = { true },
            onDone = { stored = it },
            onDismiss = { showDialog = false },
        )
    }

    OptionRowShell(
        icon = option.icon,
        title = option.title,
        description = stored.ifBlank { option.description },
        indented = false,
        onClick = { showDialog = true },
        trailing = null,
    )
}

@Composable
private fun OptionRowShell(
    icon: Int,
    title: String,
    description: String,
    indented: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = if (indented) 34.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun LongPressHint(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4_000)
        visible = false
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = SwipeEasing),
        label = "longPressHint",
    )
    if (alpha < 0.01f) return

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Tip: hold the cover on the player to edit it directly",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
