// SubSettingsScaffold.kt

package com.example.musicfy.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import coil3.compose.rememberAsyncImagePainter
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import com.example.musicfy.ui.screens.search.rememberScrollCollapseProgress
import kotlin.math.PI
import kotlin.math.sin

private val PagePadding = 20.dp
private val BackButtonSize = 40.dp

private val HeaderTopPadding = 16.dp

private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2

private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp

private val CollapsedTitleX = BackButtonSize + 14.dp

private val ExpandedHeaderHeight = 118.dp

private const val CollapsedTitleScale = 0.62f

private const val TitleMorphMaxBlurPx = 26f

private val OverlayEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private val SearchBarHeight = 52.dp

@Composable
fun SubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val headerFoundationColor = Color.Black

    // Latch-and-settle collapse, same as Library/Search: once the threshold is crossed the morph
    // always finishes smoothly, even after the user's finger lifts mid-scroll.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    var titleHeightPx by remember { mutableIntStateOf(0) }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight

    var searchOverlayVisible by remember { mutableStateOf(false) }

    // The search pill is a single element that actually resizes (real layout constraints, not a
    // scaled graphicsLayer) and slides from its resting spot below the header to the icon slot at
    // top-right, so it reads as one shape warping into a circle rather than one thing fading out
    // while an unrelated icon fades in elsewhere. The two rects are plain layout constants - no
    // live position measurement feeding back into the content a RenderNode is capturing.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val expandedBarX = PagePadding
    val expandedBarY = totalHeaderHeight
    val expandedBarWidth = screenWidth - PagePadding * 2
    val collapsedBarX = screenWidth - PagePadding - BackButtonSize
    val collapsedBarY = statusBarTop + HeaderTopPadding

    LaunchedEffect(collapseProgress) {
        if (collapseProgress < 0.5f) searchOverlayVisible = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()

                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress })
                .verticalScroll(scrollState)
                .padding(horizontal = PagePadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))

            if (searchBar != null) {
                // Reserves the pill's resting space in the normal flow; the pill itself is drawn
                // as an overlay below so it can slide out over the header instead of being
                // clipped by the scrolling column.
                Spacer(modifier = Modifier.height(SearchBarHeight + 16.dp))
            }

            content()

            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = collapseProgress }
        ) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 40f * collapseProgress },
                foundationColor = headerFoundationColor.copy(alpha = 0.55f),
                direction = BlurDirection.BottomToTop,
                steps = 2,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val gradient = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.90f),
                            0.20f to Color.Black.copy(alpha = 0.50f),
                            0.45f to Color.Black.copy(alpha = 0.18f),
                            0.75f to Color.Transparent,
                        )
                        onDrawBehind { drawRect(brush = gradient) }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop, start = PagePadding, end = PagePadding)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = HeaderTopPadding)
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2E))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .onSizeChanged { titleHeightPx = it.height }
                    .graphicsLayer {

                        val p = collapseProgress

                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(1f, CollapsedTitleScale, p)
                        scaleX = scale
                        scaleY = scale

                        val collapsedTop =
                            BackButtonCenterY.toPx() - (titleHeightPx * CollapsedTitleScale) / 2f
                        translationX = lerp(0f, CollapsedTitleX.toPx(), p)
                        translationY = lerp(ExpandedTitleTop.toPx(), collapsedTop, p)

                        val blurPx = sin(p.coerceIn(0f, 1f) * PI.toFloat()) * TitleMorphMaxBlurPx
                        renderEffect = BlurEffectCache.get(blurPx, android.graphics.Shader.TileMode.DECAL)
                    }
            )
        }

        if (searchBar != null) {
            val p = collapseProgress
            val pillX = lerpDp(expandedBarX, collapsedBarX, p)
            val pillY = lerpDp(expandedBarY, collapsedBarY, p)
            val pillWidth = lerpDp(expandedBarWidth, BackButtonSize, p)
            val pillHeight = lerpDp(SearchBarHeight, BackButtonSize, p)

            // One element whose actual layout size and position are animated (a true warp, not a
            // scaled copy): the real search field fades out as it shrinks, an icon fades in on top
            // of it, and because both share the same shrinking frame they read as one shape
            // deforming into a circle rather than a new element appearing elsewhere.
            Box(
                modifier = Modifier
                    .offset(x = pillX, y = pillY)
                    .size(width = pillWidth, height = pillHeight)
                    .clip(RoundedCornerShape(lerpDp(26.dp, BackButtonSize / 2, p)))
                    .background(Color(0xFF2C2C2E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - p / 0.6f).coerceIn(0f, 1f) }
                ) {
                    searchBar()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .clickable(enabled = p > 0.6f) {
                            searchOverlayVisible = !searchOverlayVisible
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (searchOverlayVisible) {
                            painterResource(R.drawable.close)
                        } else {
                            rememberAsyncImagePainter(model = R.raw.search)
                        },
                        contentDescription = if (searchOverlayVisible) "Hide search" else "Search",
                        tint = Color.White,
                        modifier = Modifier.size(if (searchOverlayVisible) 18.dp else 16.dp)
                    )
                }
            }

            BackHandler(enabled = searchOverlayVisible) { searchOverlayVisible = false }

            AnimatedVisibility(
                visible = searchOverlayVisible,
                enter = fadeIn(tween(260, easing = OverlayEasing)) +
                    slideInVertically(tween(260, easing = OverlayEasing)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = statusBarTop + HeaderTopPadding + BackButtonSize + 10.dp,
                        start = PagePadding,
                        end = PagePadding,
                    ),
            ) {
                searchBar()
            }
        }
    }
}

@Composable
fun SubSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.example.musicfy.ui.theme.InterFontFamily,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search any settings",
                            fontFamily = com.example.musicfy.ui.theme.InterFontFamily,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Clear",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}
