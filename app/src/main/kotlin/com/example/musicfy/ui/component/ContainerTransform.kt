// ContainerTransform.kt

package com.example.musicfy.ui.component

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** every Home cover card is drawn with this radius, so the open transition starts from it */
val HomeCardCornerRadius = 20.dp

/** the soft spring a screen grows out of its card on (~0.6s), interruptible like iOS opening an app */
const val ContainerOpenDamping = 0.88f
const val ContainerOpenStiffness = 150f

/**
 * the way back is a fixed-length ease instead of a spring. going back has to compose Home from
 * scratch, so its first frame is slow, and a spring does ~40% of its travel in the first 100ms:
 * that part got skipped and the shrink visibly jumped. this curve starts gently (~12% at 100ms),
 * so a slow first frame costs nothing you can see, and a back swipe (which scrubs the transition
 * by time) tracks the finger instead of racing ahead of it. Home comes forward on the same clock.
 */
const val ContainerCloseMillis = 560
val ContainerCloseEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

// both sides of a match animate their own bounds, so they must pick the same spec: shrinking
// means closing
@OptIn(ExperimentalSharedTransitionApi::class)
private val ContainerBoundsTransform = BoundsTransform { initial, target ->
    if (target.width < initial.width) {
        tween(ContainerCloseMillis, easing = ContainerCloseEasing)
    } else {
        spring(
            dampingRatio = ContainerOpenDamping,
            stiffness = ContainerOpenStiffness,
            visibilityThreshold = Rect.VisibilityThreshold,
        )
    }
}

/**
 * what each tapped card looked like (width and corner radius, by key) plus the width an opened
 * screen settles at, so both sides of the transition clip to exactly the same outline.
 * only touched on the main thread.
 */
private object ContainerTransformRegistry {
    val sourceWidths = HashMap<String, Float>()
    val sourceRadii = HashMap<String, Dp>()
    val sourceCovers = HashMap<String, String>()
    var targetWidth = 0f
}

/**
 * the cover the tapped card was showing. the opened screen draws it from its very first frame,
 * instead of growing an empty canvas while its own data is still loading.
 */
fun containerTransformCover(key: String?): String? =
    key?.let { ContainerTransformRegistry.sourceCovers[it] }

/**
 * the corner radius is worked out from the live bounds instead of its own animation clock, so it
 * can never drift from the size spring, and a predictive back drag scrubs both at once.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private class MorphingCornerClip(
    private val key: String,
    private val startRadius: Dp,
    private val endRadiusPx: () -> Float,
) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        state: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val from = ContainerTransformRegistry.sourceWidths[key]
        val to = ContainerTransformRegistry.targetWidth
        val t = if (from != null && to > from + 1f) {
            ((bounds.width - from) / (to - from)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val start = ContainerTransformRegistry.sourceRadii[key] ?: startRadius
        val radius = lerp(with(density) { start.toPx() }, endRadiusPx(), t)
        path.rewind()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }
}

@Composable
private fun rememberMorphingCornerClip(key: String, startRadius: Dp): MorphingCornerClip {
    val view = LocalView.current
    return remember(key, startRadius, view) {
        MorphingCornerClip(key, startRadius) { screenCornerRadiusPx(view) }
    }
}

private fun screenCornerRadiusPx(view: View): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) roundedCornerRadiusPx(view) else 0f

@RequiresApi(Build.VERSION_CODES.S)
private fun roundedCornerRadiusPx(view: View): Float =
    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

/** the tapped side: a cover card on Home (or anywhere a playlist can be opened from) */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformSource(
    key: String?,
    cornerRadius: Dp = HomeCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, cornerRadius)

    return with(sharedScope) {
        this@containerTransformSource
            .onPlaced {
                ContainerTransformRegistry.sourceWidths[key] = it.size.width.toFloat()
                ContainerTransformRegistry.sourceRadii[key] = cornerRadius
                if (coverUrl != null) ContainerTransformRegistry.sourceCovers[key] = coverUrl
            }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                // the opened screen is drawn above the card, so the card only has to be gone
                // under it on the way out and back in under it before it fades off
                enter = fadeIn(tween(220, delayMillis = 100)),
                exit = fadeOut(tween(220, delayMillis = 40)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                clipInOverlayDuringTransition = clip,
            )
    }
}

/** the opened side: the root of the screen that grows out of the card */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformTarget(
    key: String?,
    sourceCornerRadius: Dp = HomeCardCornerRadius,
): Modifier {
    if (key == null) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, sourceCornerRadius)

    return with(sharedScope) {
        this@containerTransformTarget
            .onSizeChanged { ContainerTransformRegistry.targetWidth = it.width.toFloat() }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                // on the way back it stays solid for the first stretch of the shrink, so it doesn't
                // ghost over Home while it's still nearly full screen, and is gone by ~320ms when
                // the card is almost home and its own cover takes over
                enter = fadeIn(tween(280)),
                exit = fadeOut(tween(200, delayMillis = 120)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                zIndexInOverlay = 1f,
                clipInOverlayDuringTransition = clip,
            )
    }
}
