// SubSettingsScaffold.kt

package com.example.musicfy.ui.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import kotlinx.coroutines.flow.collectLatest
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

private const val CollapseDistanceDp = 96f

private val TitleMorphEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

private const val TitleMorphStepMillis = 1000

private const val TitleMorphMaxBlurPx = 26f

private const val BlurRampDistanceDp = 28f

@Composable
fun SubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val headerFoundationColor = Color.Black

    val collapseDistancePx = with(density) { CollapseDistanceDp.dp.toPx() }
    val progressProvider = { (scrollState.value / collapseDistancePx).coerceIn(0f, 1f) }

    val blurRampPx = with(density) { BlurRampDistanceDp.dp.toPx() }
    val blurProgressProvider = { (scrollState.value / blurRampPx).coerceIn(0f, 1f) }

    var titleHeightPx by remember { mutableIntStateOf(0) }

    val animatedTitleProgress = remember { Animatable(0f) }
    LaunchedEffect(scrollState) {
        snapshotFlow { progressProvider() }
            .collectLatest { target ->
                animatedTitleProgress.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = TitleMorphStepMillis, easing = TitleMorphEasing),
                )
            }
    }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()

                .glassRoot(glassState, isActive = { true })
                .verticalScroll(scrollState)
                .padding(horizontal = PagePadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))
            content()

            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = blurProgressProvider() }
        ) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 40f * blurProgressProvider() },
                foundationColor = headerFoundationColor.copy(alpha = 0.55f),
                direction = BlurDirection.BottomToTop,
                steps = 5,
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

                        val p = animatedTitleProgress.value

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
