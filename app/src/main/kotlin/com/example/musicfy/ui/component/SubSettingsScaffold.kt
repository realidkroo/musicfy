// SubSettingsScaffold.kt
// Shared chrome for the drill-down settings pages (appearance / playback / experimental /
// advanced audio). The large page title doesn't scroll away — it morphs continuously into the
// slot beside the back button, with a progressive blur building underneath it as content
// passes below. The main SettingsScreen deliberately does NOT use this: it keeps its own
// profile-header treatment.

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
import androidx.compose.ui.util.lerp
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin

private val PagePadding = 20.dp
private val BackButtonSize = 40.dp

/** Gap between the status bar and the back button — measured *below* the inset, not through it. */
private val HeaderTopPadding = 16.dp

/** Vertical centre of the back button, measured from the top of the header. */
private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2

/** Where the big title sits before any scrolling: below the back button. */
private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp

/** Collapsed, the title tucks into the gap to the right of the back button. */
private val CollapsedTitleX = BackButtonSize + 14.dp

/**
 * Header footprint below the status bar — sized to just clear the title with a small margin,
 * not the ~30dp of extra empty space the previous height left. Content starts below this plus
 * the inset.
 */
private val ExpandedHeaderHeight = 118.dp

/**
 * headlineLarge is ~32sp; 0.62 lands it near 20sp, which reads as a normal top-bar title
 * without ever swapping text styles mid-animation (a style swap would pop, a scale won't).
 */
private const val CollapsedTitleScale = 0.62f

/** Scroll distance over which the title finishes migrating up beside the back button. */
private const val CollapseDistanceDp = 96f

/** Requested easing curve for the title morph: cubic-bezier(0.5, 0.45, 0, 1). */
private val TitleMorphEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

/** Duration of each eased step while chasing a moving scroll target. */
private const val TitleMorphStepMillis = 1000

/**
 * Peak defocus at the midpoint of the morph (sin(progress * PI) is 0 at both ends, 1 at the
 * middle) — the title visibly softens as it crosses through the transition, then sharpens again,
 * rather than staying crisp through what would otherwise be a purely geometric move.
 */
private const val TitleMorphMaxBlurPx = 26f

/**
 * The header's glass effect (blur + darkening scrim) ramps over this much shorter distance,
 * independent of the slower title migration above. Sharing one distance for both was the actual
 * bug behind "sharp text with a glow": at any modest scroll short of the full 96dp, blur radius
 * (36f * progress) was still tiny — e.g. ~7px at 20% progress — while the scrim had already
 * started darkening, so legible text just got a dark tint instead of turning into a proper
 * frosted blur. The glass needs to hit full strength almost immediately on scroll; the title can
 * take its time migrating separately.
 */
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
    // Same opaque-foundation pattern SettingsScreen uses for its own header blur — matching it
    // exactly is what makes the ProgressiveGlassBackground call below behave the same way.
    val headerFoundationColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface

    // Read as lambdas rather than captured values so the graphicsLayer/blur below re-read them
    // during the draw phase instead of forcing recomposition on every scroll pixel.
    val collapseDistancePx = with(density) { CollapseDistanceDp.dp.toPx() }
    val progressProvider = { (scrollState.value / collapseDistancePx).coerceIn(0f, 1f) }

    // Deliberately separate from progressProvider — see BlurRampDistanceDp above.
    val blurRampPx = with(density) { BlurRampDistanceDp.dp.toPx() }
    val blurProgressProvider = { (scrollState.value / blurRampPx).coerceIn(0f, 1f) }

    // The morph target depends on the title's own measured height, so the collapsed position
    // can centre it against the back button exactly instead of guessing at a font metric.
    var titleHeightPx by remember { mutableIntStateOf(0) }

    // The title no longer maps 1:1 to raw scroll position — that read directly like a scrollbar
    // (freezes exactly where your finger stopped, no sense of motion). This instead chases the
    // scroll-derived target through an Animatable: every new target retriggers a tween along the
    // requested cubic-bezier(0.5, 0.45, 0, 1) curve, and because Animatable.animateTo interrupts
    // whatever's already in flight, a continuous scroll just keeps redirecting it toward the
    // newest target — a fast-scrolling bounce/overshoot was tried here and pulled back out, so
    // this is plain eased chasing the whole way, no spring. Once scrolling stops and nothing new
    // arrives, the one in-flight tween simply finishes and holds — which is what makes it stop
    // cleanly the instant scrolling does, with no extra "settle" step tacked on.
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
    // The header lives under the status bar, so every vertical metric below is offset by it —
    // without this the back button and title drew straight through the clock and signal icons.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Always record, never gate on scroll. Gating left the RenderNode without a
                // display list until something forced an unrelated recomposition (confirmed by
                // toggling the global Blur switch making blur start working) — a settings list
                // is cheap enough to double-draw that this isn't worth chasing further.
                .glassRoot(glassState, isActive = { true })
                .verticalScroll(scrollState)
                .padding(horizontal = PagePadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))
            content()
            // Clears the (now always visible) bottom navigation bar and mini-player.
            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        // Blur sits between the content and the chrome so the title/back button stay crisp on
        // top of it. ProgressiveGlassBackground itself is left at full strength — untouched by
        // the gradient below — because masking the blur's own opacity to control darkness was
        // the actual bug two attempts ago: it coupled "how dark" to "how visible the blur is",
        // so turning one down turned the other down with it.
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

            // Darkening painted on top, normally (not a mask) — a real paint this time, not the
            // no-op from the very first attempt. That one failed because foundationColor was
            // fully opaque black, so black-on-black is invisible regardless of alpha. Now the
            // layer underneath is blurred real content (whatever colours/brightness the scrolled
            // rows actually have), so a semi-transparent black tint on top genuinely darkens it.
            //
            // Darker peak, longer reach: stronger at the top edge and the taper now runs to 75%
            // of the header's height instead of clearing out by 55%.
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

        // Fixed chrome. This Box adds no pointer-input modifier of its own, so only the back
        // button actually consumes touches — content scrolling underneath stays interactive.
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
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
                        // The animated (eased/spring-settled) value, not raw scroll — see
                        // animatedTitleProgress above.
                        val p = animatedTitleProgress.value
                        // Anchor the scale to the top-left corner so the only thing moving the
                        // glyphs is the translation below — scaling about the centre would make
                        // the text drift sideways as it shrinks.
                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(1f, CollapsedTitleScale, p)
                        scaleX = scale
                        scaleY = scale

                        val collapsedTop =
                            BackButtonCenterY.toPx() - (titleHeightPx * CollapsedTitleScale) / 2f
                        translationX = lerp(0f, CollapsedTitleX.toPx(), p)
                        translationY = lerp(ExpandedTitleTop.toPx(), collapsedTop, p)

                        val blurPx = sin(p.coerceIn(0f, 1f) * PI.toFloat()) * TitleMorphMaxBlurPx
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.5f) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurPx, blurPx, android.graphics.Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    }
            )
        }
    }
}
