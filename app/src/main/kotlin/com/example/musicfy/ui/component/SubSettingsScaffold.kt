// subsettingsscaffoldkt
// shared chrome for the drill down settings pages appearance playback
// advanced audio the large page title doesn t scroll away it morphs
// slot beside the back button with a progressive blur building underneath it
// passes below the main settingsscreen deliberately does not use this it
// profile header treatment

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

// gap between the status bar and the back button measured below the inset not through it
private val HeaderTopPadding = 16.dp

// vertical centre of the back button measured from the top of the header
private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2

// where the big title sits before any scrolling below the back button
private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp

// collapsed the title tucks into the gap to the right of the back button
private val CollapsedTitleX = BackButtonSize + 14.dp

// header footprint below the status bar sized to just clear the title with a
private val ExpandedHeaderHeight = 118.dp

// headlinelarge is ~32sp 062 lands it near 20sp which reads as a normal top bar
private const val CollapsedTitleScale = 0.62f

// scroll distance over which the title finishes migrating up beside the back button
private const val CollapseDistanceDp = 96f

// requested easing curve for the title morph cubic bezier 05 045 0 1
private val TitleMorphEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

// duration of each eased step while chasing a moving scroll target
private const val TitleMorphStepMillis = 1000

// peak defocus at the midpoint of the morph sin progress pi is 0 at both ends
private const val TitleMorphMaxBlurPx = 26f

// the header s glass effect blur + darkening scrim ramps over this much shorter
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
    // same opaque foundation pattern settingsscreen uses for its own header blur
    // exactly is what makes the progressiveglassbackground call below behave the
    val headerFoundationColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface

    // read as lambdas rather than captured values so the graphicslayer blur
    // during the draw phase instead of forcing recomposition on every scroll
    val collapseDistancePx = with(density) { CollapseDistanceDp.dp.toPx() }
    val progressProvider = { (scrollState.value / collapseDistancePx).coerceIn(0f, 1f) }

    // deliberately separate from progressprovider see blurrampdistancedp above
    val blurRampPx = with(density) { BlurRampDistanceDp.dp.toPx() }
    val blurProgressProvider = { (scrollState.value / blurRampPx).coerceIn(0f, 1f) }

    // the morph target depends on the title s own measured height so the
    // can centre it against the back button exactly instead of guessing at a
    var titleHeightPx by remember { mutableIntStateOf(0) }

    // the title no longer maps 1 1 to raw scroll position that read directly
    // freezes exactly where your finger stopped no sense of motion this
    // scroll derived target through an animatable every new target retriggers a
    // requested cubic bezier 05 045 0 1 curve and because animatableanimateto
    // whatever s already in flight a continuous scroll just keeps redirecting it
    // newest target a fast scrolling bounce overshoot was tried here and
    // this is plain eased chasing the whole way no spring once scrolling stops
    // arrives the one in flight tween simply finishes and holds which is what
    // cleanly the instant scrolling does with no extra settle step tacked on
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
    // the header lives under the status bar so every vertical metric below is
    // without this the back button and title drew straight through the clock and
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // always record never gate on scroll gating left the rendernode without a
                // display list until something forced an unrelated recomposition confirmed
                // toggling the global blur switch making blur start working a settings
                // is cheap enough to double draw that this isn t worth chasing further
                .glassRoot(glassState, isActive = { true })
                .verticalScroll(scrollState)
                .padding(horizontal = PagePadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))
            content()
            // clears the now always visible bottom navigation bar and mini player
            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        // blur sits between the content and the chrome so the title back button stay
        // top of it progressiveglassbackground itself is left at full strength
        // the gradient below because masking the blur s own opacity to control
        // the actual bug two attempts ago it coupled how dark to how visible the
        // so turning one down turned the other down with it
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

            // darkening painted on top normally not a mask a real paint this time
            // no op from the very first attempt that one failed because foundationcolor
            // fully opaque black so black on black is invisible regardless of alpha now
            // layer underneath is blurred real content whatever colours brightness the
            // rows actually have so a semi transparent black tint on top genuinely

            // darker peak longer reach stronger at the top edge and the taper now runs
            // of the header s height instead of clearing out by 55%
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

        // fixed chrome this box adds no pointer input modifier of its own so only
        // button actually consumes touches content scrolling underneath stays
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
                        // the animated eased spring settled value not raw scroll see
                        // animatedtitleprogress above
                        val p = animatedTitleProgress.value
                        // anchor the scale to the top left corner so the only thing moving the
                        // glyphs is the translation below scaling about the centre would make
                        // the text drift sideways as it shrinks
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
