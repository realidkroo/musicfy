// detailcollapsingtopbarkt
// shared scroll-collapsing top bar for the album/playlist/liked-songs detail
// as the user scrolls past the big hero cover this bar reveals: the same
// (not a separately faded-in copy) morphing — sliding and shrinking
// wherever it actually is on screen right now toward a small slot beside the
// button — plus the title/subtitle fading in and a progressive blur backdrop
// glasskit pipeline as the home top bar) behind it all scrolling back up
// the collapse is a discrete auto-playing transition (a single tween that
// completion once triggered) rather than being scrubbed frame-by-frame off
// scroll offset — binding it directly to scroll made the reveal window so
// read as an instant cut instead of a real animation crossing the threshold
// flips a target and lets the tween play itself out with its own easing curve

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

// matches the requested cubic-bezier(059 053 0 1) — a curve that starts and
// slower with a fast middle used everywhere in this transition (bar reveal
// fade cover morph blur) so all of it reads as one unified motion instead of
// running on different curves/durations
private val DetailCollapseEasing = androidx.compose.animation.core.CubicBezierEasing(0.59f, 0.53f, 0f, 1f)
private const val DetailMorphDurationMs = 1000
private const val DetailFadeDurationMs = 300

// headercontentalpha: 1 = the header's own text/gradient fully visible 0 = fully
@androidx.compose.runtime.Immutable
data class DetailCollapseState(
    val headerContentAlpha: Float,
    val morphProgress: Float,
)

// two fully sequential animations sharing one trigger not two slices of one
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

    // fade: 0 = header content visible 1 = faded away morph: 0 = cover untouched
    // fully morphed named separately from the public
    // so the "1 = faded" internal direction here doesn't leak into the public api
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
    // the morph fraction from detailcollapsestate — 0 = cover untouched 1 = fully
    // morphed into the slot named "progress" at the call site for brevity but
    // already the fully-sequenced value; no further remapping happens in here
    progress: Float,
    glassState: GlassState,
    thumbnailUrl: String?,
    title: String,
    subtitle: String? = null,
    accentColor: Color? = null,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit = {},
    // live on-screen position/size of the actual hero cover (in window
    // updated every frame while it's still composed the mini cover is morphed
    // this real rect to its slot beside the back button so it reads as the same
    // image continuing to move rather than a new element fading in from nothing
    coverBoundsInWindow: Rect? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    // animated here (rather than trusting callers to only ever pass an
    // color) so the tint always fades in gradually instead of instantly snapping
    // moment cover-color extraction finishes upstream
    val animatedAccentColor by androidx.compose.animation.animateColorAsState(
        targetValue = accentColor ?: MaterialTheme.colorScheme.surface,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "detailTopBarAccentColor",
    )
    val backdropTint = animatedAccentColor.copy(alpha = 0.55f)
    val density = LocalDensity.current
    // kept as a local val (instead of renaming every use below) so the diff
    // the previous single-phase version stays readable — this already is the
    // sequenced morph fraction from detailcollapsestate not something derived
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
                // fewer steps than the default (5) — each step re-draws the same
                // semi-transparent tint on top of the last and with 5 of them
                // stacked the compounding alpha read as a bright "glow" instead of
                // a clean blur 3 (same as home's own top bar) is enough for a
                // smooth falloff without the buildup
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

            // invisible placeholder that just claims the mini-cover's slot in the row
            // layout so its real on-screen position/size (the morph's target) can be
            // measured continuously independent of whether the cover is drawn here
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

        // the morphing cover itself positioned absolutely in this box using window
        // coordinates translated into this composable's local space it doesn't exist
        // at all until phase b starts (morphprogress > 0) — up to that point the
        // own cover is still the only copy on screen at full size unmoved the instant
        // morphprogress ticks above 0 this mounts starting at that same real position
        // (coverboundsinwindow) which is exactly when the header's own cover
        // to invisible (see playlistscreenheader/albumscreen) — a clean handoff with
        // window where both exist from there it slides/shrinks into the slot beside
        // back button picking up blur mid-transition (peaking at morphprogress=05
        // sharp again at both ends) so the motion itself reads as smooth glide rather
        // than a hard-edged shape sliding across the screen
        val slot = slotBoundsInWindow
        if (morphProgress > 0.001f && slot != null && !thumbnailUrl.isNullOrEmpty()) {
            val start = coverBoundsInWindow ?: slot
            val currentRect = lerp(start, slot, morphProgress)
            val localLeft = currentRect.left - barPositionInWindow.x
            val localTop = currentRect.top - barPositionInWindow.y
            val cornerRadius = lerp(0.dp, 8.dp, morphProgress)
            // plain sine not sqrt — sqrt made the blur ramp up almost immediately at the
            // start of the transition instead of building gradually which read as an
            // abrupt "snap into blur" rather than a smooth continuous change
            val motionBlurRadius = sin(morphProgress * PI.toFloat()).coerceAtLeast(0f) * 48f
            // fixed bleed margin not a percentage scale: the box this overlay renders at
            // ranges from ~32dp (the collapsed slot) up to the full screen width (the
            // hero cover) so a multiplicative scale gives nowhere near enough real pixels
            // at the small end for a 48px blur to sample a flat 24dp on every side is
            // comfortably more than the max blur radius regardless of box size
            val bleedMargin = 24.dp

            Box(
                modifier = Modifier
                    .offset { IntOffset(localLeft.roundToInt(), localTop.roundToInt()) }
                    .size(
                        with(density) { currentRect.width.toDp() },
                        with(density) { currentRect.height.toDp() }
                    )
                    // outer clip is the only hard edge here — the blurred content inside
                    // is genuinely larger than this (see the asyncimage's own oversized
                    // size() below) so this cuts a clean rounded rect out of it rather
                    // than exposing the blur's own boundary
                    .clip(RoundedCornerShape(cornerRadius))
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // a blur samples pixels beyond its own edge to soften near-boundary
                    // content; when the blurred layer's layout bounds are exactly the
                    // visible rect (no bleed) it has nothing but transparent/edge-clamped
                    // pixels to sample there and android's two-pass (horizontal+vertical)
                    // rendereffect blur turns that into a sharp "+"-shaped seam at the
                    // corners instead of a clean soft edge this needs a real oversized
                    // size() (an actual bigger measured/rendered layer) not a draw-time
                    // graphicslayer scale — scale only stretches the already-blurred
                    // result after the fact it doesn't give the blur itself any more
                    // pixels to work with so it doesn't fix the seam
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
