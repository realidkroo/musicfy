// DetailCollapsingTopBar.kt

package com.example.musicfy.ui.component.detail

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val DetailCollapseEasing = androidx.compose.animation.core.CubicBezierEasing(0.59f, 0.53f, 0f, 1f)
private const val DetailMorphDurationMs = 1000
private const val DetailFadeDurationMs = 300

@androidx.compose.runtime.Immutable
data class DetailCollapseState(
    val headerContentAlpha: Float,
    val morphProgress: Float,
)

@Composable
fun rememberDetailCollapseProgress(
    lazyListState: LazyListState,
    headerHeightPx: Float,
    triggerOffset: Dp = 48.dp,
): DetailCollapseState {
    val density = LocalDensity.current
    val triggerOffsetPx = with(density) { triggerOffset.toPx() }

    val isCollapsed by remember(lazyListState, headerHeightPx) {
        derivedStateOf {
            if (headerHeightPx <= 0f) {
                false
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                true
            } else {
                lazyListState.firstVisibleItemScrollOffset > (headerHeightPx - triggerOffsetPx).coerceAtLeast(0f)
            }
        }
    }

    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    val morph = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(isCollapsed) {
        if (isCollapsed) {
            fade.animateTo(1f, androidx.compose.animation.core.tween(DetailFadeDurationMs, easing = DetailCollapseEasing))
            morph.animateTo(1f, androidx.compose.animation.core.tween(DetailMorphDurationMs, easing = DetailCollapseEasing))
        } else {
            morph.animateTo(0f, androidx.compose.animation.core.tween(DetailMorphDurationMs, easing = DetailCollapseEasing))
            fade.animateTo(0f, androidx.compose.animation.core.tween(DetailFadeDurationMs, easing = DetailCollapseEasing))
        }
    }

    return DetailCollapseState(
        headerContentAlpha = 1f - fade.value,
        morphProgress = morph.value,
    )
}

@Composable
fun DetailCollapsingTopBar(

    progress: Float,
    glassState: GlassState,
    thumbnailUrl: String?,
    title: String,
    subtitle: String? = null,
    accentColor: Color? = null,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit = {},

    coverBoundsInWindow: Rect? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {

    val animatedAccentColor by androidx.compose.animation.animateColorAsState(
        targetValue = accentColor ?: MaterialTheme.colorScheme.surface,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "detailTopBarAccentColor",
    )
    val backdropTint = animatedAccentColor.copy(alpha = 0.55f)
    val density = LocalDensity.current

    val morphProgress = progress

    var barPositionInWindow by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var slotBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { barPositionInWindow = it.positionInWindow() }
    ) {
        if (morphProgress > 0.01f) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 40f * morphProgress },
                tint = backdropTint,
                direction = BlurDirection.BottomToTop,

                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .graphicsLayer { alpha = morphProgress }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f * (1f - 0.5f * morphProgress)))
                    .combinedClickable(onClick = onBackClick, onLongClick = onBackLongClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .onGloballyPositioned {
                        slotBoundsInWindow = Rect(it.positionInWindow(), it.size.toSize())
                    }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = morphProgress }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            actions()
        }

        val slot = slotBoundsInWindow
        if (morphProgress > 0.001f && slot != null && !thumbnailUrl.isNullOrEmpty()) {
            val start = coverBoundsInWindow ?: slot
            val currentRect = lerp(start, slot, morphProgress)
            val localLeft = currentRect.left - barPositionInWindow.x
            val localTop = currentRect.top - barPositionInWindow.y
            val cornerRadius = lerp(0.dp, 8.dp, morphProgress)

            val motionBlurRadius = sin(morphProgress * PI.toFloat()).coerceAtLeast(0f) * 48f

            val bleedMargin = 24.dp

            Box(
                modifier = Modifier
                    .offset { IntOffset(localLeft.roundToInt(), localTop.roundToInt()) }
                    .size(
                        with(density) { currentRect.width.toDp() },
                        with(density) { currentRect.height.toDp() }
                    )

                    .clip(RoundedCornerShape(cornerRadius))
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,

                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(
                            with(density) { currentRect.width.toDp() } + bleedMargin * 2,
                            with(density) { currentRect.height.toDp() } + bleedMargin * 2,
                        )
                        .graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && motionBlurRadius > 0.5f) {
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    motionBlurRadius, motionBlurRadius, android.graphics.Shader.TileMode.DECAL
                                ).asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        }
                )
            }
        }
    }
}

private fun IntSize.toSize() = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp =
    androidx.compose.ui.unit.lerp(start, stop, fraction)
