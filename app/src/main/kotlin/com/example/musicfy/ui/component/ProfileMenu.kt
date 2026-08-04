// ProfileMenu.kt
// The dropdown menu that opens from HomeScreen's top-bar profile trigger (both the plain
// circular-avatar state and the scrolled "pill" state are the *same* composable there, so this
// only ever needs to handle one shape). Rather than a popup that fades/scales in on top of the
// trigger, the trigger's own on-screen bounds are morphed — via the same layout-phase
// Constraints.fixed position/size interpolation MorphingCover.kt uses for the mini-to-full
// player — into this card's bounds, so it visibly grows out of wherever the trigger actually was
// (circle or pill) rather than popping up as a disconnected element. The avatar image morphs
// along its own separate path into the header's avatar slot, so it reads as one photo travelling
// there rather than two images cross-fading.
package com.example.musicfy.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.isActive

data class ProfileMenuItem(
    val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)

private val HeaderHeight = 78.dp
private val ItemHeight = 52.dp
private val DividerBlock = 17.dp
private val CardVerticalPadding = 8.dp
private val AvatarSize = 46.dp
private val AvatarPadding = 16.dp
private val CardHorizontalPadding = 18.dp

private data class PxRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    companion object {
        val Zero = PxRect(0f, 0f, 0f, 0f)
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

private fun lerpRect(start: PxRect, end: PxRect, t: Float) = PxRect(
    x = lerp(start.x, end.x, t),
    y = lerp(start.y, end.y, t),
    w = lerp(start.w, end.w, t),
    h = lerp(start.h, end.h, t),
)

private fun Rect.toPxRect() = PxRect(left, top, width, height)

// Genuine position/size morph (not a fake fade+scale) — same technique as MorphingCover.kt's
// morphLayout: the child is measured at its CURRENT interpolated size every frame and placed at
// its current interpolated position, so it visibly travels/resizes rather than cross-fading.
private fun Modifier.morphBounds(rectProvider: () -> PxRect): Modifier = this.layout { measurable, constraints ->
    val r = rectProvider()
    val w = r.w.toInt().coerceAtLeast(1)
    val h = r.h.toInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(r.x.toInt(), r.y.toInt())
    }
}

private fun rowReveal(progress: Float, start: Float, end: Float) =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

@RequiresApi(33)
private fun Modifier.warpEffect(
    shader: RuntimeShader,
    time: () -> Float,
    amount: () -> Float,
): Modifier = this.graphicsLayer {
    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("time", time())
    shader.setFloatUniform("amount", amount())
    renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
}

private const val WarpShaderSrc = """
    uniform float2 resolution;
    uniform float time;
    uniform float amount;
    uniform shader image;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float t = time * 0.6;
        float2 warpOffset = float2(
            sin(uv.y * 4.0 + t) * 0.04 + cos(uv.x * 3.0 - t * 0.7) * 0.03,
            cos(uv.x * 4.0 + t * 0.8) * 0.04 + sin(uv.y * 3.0 + t) * 0.03
        ) * amount;
        return image.eval(fragCoord + warpOffset * resolution);
    }
"""

/**
 * Full-screen overlay hosting the profile dropdown. [visible] is a plain composition-scope
 * boolean (mount/unmount, including the tail of the close animation); [progressProvider] is the
 * continuous 0..1 draw-phase value actually driving the morph every frame.
 *
 * [triggerBoundsProvider]/[avatarBoundsProvider] report the trigger's and avatar's CURRENT
 * on-screen bounds (root coordinates) — reading them fresh each time means this works correctly
 * for both the plain-circle and scrolled-pill trigger shapes without needing to know which one
 * is active.
 */
@Composable
fun ProfileMenuOverlay(
    visible: Boolean,
    progressProvider: () -> Float,
    onDismissRequest: () -> Unit,
    glassState: GlassState,
    triggerBoundsProvider: () -> Rect?,
    avatarBoundsProvider: () -> Rect?,
    accountName: String,
    accountSubtitle: String,
    profileImageRequest: ImageRequest?,
    onProfileClick: () -> Unit,
    items: List<ProfileMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val density = LocalDensity.current

    var overlayRootPosition by remember { mutableStateOf(Offset.Zero) }

    val isAnimating by remember {
        derivedStateOf {
            val p = progressProvider()
            p > 0.001f && p < 0.999f
        }
    }
    val warpShader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(WarpShaderSrc) else null
    }
    val warpTime = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isAnimating) {
        if (!isAnimating) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                warpTime.floatValue = ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayRootPosition = it.positionInRoot() }
    ) {
        // Scrim — dismiss on tap outside the card.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progressProvider().coerceIn(0f, 1f) * 0.55f }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
        )

        val cardWidthPx = with(density) { (maxWidth * 0.68f).coerceIn(220.dp, 300.dp).toPx() }
        val cardHeightPx = with(density) {
            val extraDivider = if (items.size >= 2) DividerBlock else 0.dp
            (CardVerticalPadding * 2 + HeaderHeight + DividerBlock + ItemHeight * items.size + extraDivider).toPx()
        }
        val gapPx = with(density) { 8.dp.toPx() }

        val endRectProvider = {
            val trigger = triggerBoundsProvider()
            if (trigger == null) {
                PxRect.Zero
            } else {
                val rightLocal = trigger.right - overlayRootPosition.x
                val bottomLocal = trigger.bottom - overlayRootPosition.y
                PxRect(
                    x = rightLocal - cardWidthPx,
                    y = bottomLocal + gapPx,
                    w = cardWidthPx,
                    h = cardHeightPx,
                )
            }
        }
        val startRectProvider = {
            val trigger = triggerBoundsProvider()
            if (trigger == null) PxRect.Zero else PxRect(
                x = trigger.left - overlayRootPosition.x,
                y = trigger.top - overlayRootPosition.y,
                w = trigger.width,
                h = trigger.height,
            )
        }
        val currentCardRectProvider = {
            lerpRect(startRectProvider(), endRectProvider(), progressProvider().coerceIn(0f, 1f))
        }

        val avatarSizePx = with(density) { AvatarSize.toPx() }
        val avatarPadPx = with(density) { AvatarPadding.toPx() }
        val avatarStartRectProvider = {
            val avatarRect = avatarBoundsProvider()
            if (avatarRect == null) PxRect.Zero else avatarRect.toPxRect().let {
                PxRect(it.x - overlayRootPosition.x, it.y - overlayRootPosition.y, it.w, it.h)
            }
        }
        val avatarEndRectProvider = {
            val end = endRectProvider()
            PxRect(x = end.x + avatarPadPx, y = end.y + avatarPadPx, w = avatarSizePx, h = avatarSizePx)
        }
        val currentAvatarRectProvider = {
            lerpRect(avatarStartRectProvider(), avatarEndRectProvider(), progressProvider().coerceIn(0f, 1f))
        }

        val cardWidthDp = with(density) { cardWidthPx.toDp() }
        val cardHeightDp = with(density) { cardHeightPx.toDp() }
        val endCornerPx = with(density) { 26.dp.toPx() }

        Box(
            modifier = Modifier
                .morphBounds(currentCardRectProvider)
                .graphicsLayer {
                    clip = true
                    // Interpolated as two fixed endpoint radii (not re-derived from the box's
                    // own current, already-morphing dimensions each frame) — a stadium/pill
                    // corner at t=0 easing to a fixed 26dp card corner at t=1, matching whatever
                    // shape the trigger actually had (circle or pill both reduce to "half the
                    // smaller side") without ever snapping back to a stadium shape mid-animation.
                    val start = startRectProvider()
                    val startCornerPx = minOf(start.w, start.h) / 2f
                    val t = progressProvider().coerceIn(0f, 1f)
                    shape = RoundedCornerShape(CornerSize(lerp(startCornerPx, endCornerPx, t)))
                }
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(cardWidthDp, cardHeightDp)
                    .let {
                        if (warpShader != null) {
                            it.warpEffect(
                                shader = warpShader,
                                time = { warpTime.floatValue },
                                amount = {
                                    val p = progressProvider().coerceIn(0f, 1f)
                                    kotlin.math.sin(p * Math.PI.toFloat())
                                }
                            )
                        } else it
                    }
            ) {
                GlassPillBackground(
                    state = glassState,
                    blurRadius = { 32f * progressProvider().coerceIn(0f, 1f) },
                    tint = Color.Black.copy(alpha = 0.30f),
                    tileMode = android.graphics.Shader.TileMode.CLAMP,
                    modifier = Modifier.requiredSize(cardWidthDp, cardHeightDp)
                )
            }

            Column(
                modifier = Modifier
                    .requiredSize(cardWidthDp, cardHeightDp)
                    .padding(vertical = CardVerticalPadding)
            ) {
                var rowIndex = 0
                val totalRows = 2 + items.size + (if (items.size >= 2) 1 else 0)
                fun nextWindow(): Pair<Float, Float> {
                    val step = (1f - 0.15f) / totalRows
                    val s = 0.15f + step * rowIndex
                    rowIndex++
                    val e = (s + step * 1.8f).coerceAtMost(1f)
                    return s to e
                }

                val (headerStart, headerEnd) = nextWindow()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeaderHeight)
                        .clickable { onProfileClick() }
                        .padding(horizontal = CardHorizontalPadding)
                ) {
                    // The real avatar floats above this slot as an independently-morphing
                    // element (see below) — this Spacer just reserves its layout space.
                    Spacer(modifier = Modifier.size(AvatarSize))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.graphicsLayer {
                            val a = rowReveal(progressProvider(), headerStart, headerEnd)
                            alpha = a
                            translationX = (1f - a) * with(density) { 24.dp.toPx() }
                        }
                    ) {
                        Text(
                            text = accountName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = accountSubtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val (div1Start, div1End) = nextWindow()
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier
                        .padding(horizontal = CardHorizontalPadding)
                        .graphicsLayer { alpha = rowReveal(progressProvider(), div1Start, div1End) }
                )

                items.forEachIndexed { index, item ->
                    if (index == items.lastIndex && items.size >= 2) {
                        val (dStart, dEnd) = nextWindow()
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .padding(horizontal = CardHorizontalPadding)
                                .graphicsLayer { alpha = rowReveal(progressProvider(), dStart, dEnd) }
                        )
                    }
                    val (iStart, iEnd) = nextWindow()
                    MenuItemRow(
                        item = item,
                        onDismissRequest = onDismissRequest,
                        revealStart = iStart,
                        revealEnd = iEnd,
                        progressProvider = progressProvider,
                    )
                }
            }
        }

        // Floating avatar — genuinely travels from the top-bar trigger's real on-screen position
        // to its resting spot in the header, instead of two separate images cross-fading.
        if (profileImageRequest != null) {
            Box(
                modifier = Modifier
                    .morphBounds(currentAvatarRectProvider)
                    .clip(CircleShape)
            ) {
                AsyncImage(
                    model = profileImageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    item: ProfileMenuItem,
    onDismissRequest: () -> Unit,
    revealStart: Float,
    revealEnd: Float,
    progressProvider: () -> Float,
) {
    val density = LocalDensity.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .clickable {
                onDismissRequest()
                item.onClick()
            }
            .graphicsLayer {
                val a = rowReveal(progressProvider(), revealStart, revealEnd)
                alpha = a
                translationX = (1f - a) * with(density) { 24.dp.toPx() }
            }
            .padding(horizontal = CardHorizontalPadding)
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
