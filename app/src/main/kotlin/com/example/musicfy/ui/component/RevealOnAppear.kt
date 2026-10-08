// RevealOnAppear.kt

package com.example.musicfy.ui.component

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val RevealEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val BonesFadeOutMs = 280
private const val RevealGapMs = 120
private const val RevealFadeInMs = 520
private val RevealSlideDistance = 20.dp
private const val BonesWaveMs = 2000

// reveals that start together (a whole page of rows landing at once) go one after another
private const val BurstWindowMs = 120L
private const val BurstStepMs = 60
private const val BurstMaxSteps = 6

/** colour of a loading bone, before the wave modulates it */
val BoneColor = Color(0xFFD9D9D9).copy(alpha = 0.22f)

/**
 * which keys already played their reveal. kept with rememberSaveable so coming back to a screen
 * (or scrolling a row back in) doesn't animate everything a second time.
 */
@Stable
class RevealSeenState(initial: Collection<String> = emptyList()) {
    private val seen = HashSet(initial)
    fun hasSeen(key: String) = key in seen
    fun markSeen(key: String) {
        seen.add(key)
    }

    private var burstStartedAt = 0L
    private var burstCount = 0

    /** how long this reveal waits for the ones that started with it */
    internal fun nextBurstDelay(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - burstStartedAt > BurstWindowMs) {
            burstStartedAt = now
            burstCount = 0
        }
        return min(burstCount++, BurstMaxSteps) * BurstStepMs
    }

    companion object {
        val Saver: Saver<RevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { RevealSeenState(it) },
        )
    }
}

@Composable
fun rememberRevealSeenState(): RevealSeenState =
    rememberSaveable(saver = RevealSeenState.Saver) { RevealSeenState() }

/**
 * bones while [data] is null, then: bones fade out, a short empty beat, and the content fades and
 * slides up into place. only the loading -> loaded switch animates, later data updates swap in
 * place so a refresh never flickers.
 */
@Composable
fun <T : Any> RevealWhenLoaded(
    data: T?,
    modifier: Modifier = Modifier,
    bones: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val slidePx = with(LocalDensity.current) { RevealSlideDistance.roundToPx() }

    AnimatedContent(
        targetState = data,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                val delay = BonesFadeOutMs + RevealGapMs
                (fadeIn(tween(RevealFadeInMs, delayMillis = delay, easing = RevealEasing)) +
                    slideInVertically(tween(RevealFadeInMs, delayMillis = delay, easing = RevealEasing)) { slidePx })
                    .togetherWith(fadeOut(tween(BonesFadeOutMs)))
                    .using(SizeTransform(clip = false))
            } else {
                fadeIn(tween(BonesFadeOutMs))
                    .togetherWith(fadeOut(tween(BonesFadeOutMs)))
                    .using(SizeTransform(clip = false))
            }
        },
        label = "RevealWhenLoaded",
        modifier = modifier,
    ) { state ->
        if (state == null) bones() else content(state)
    }
}

/**
 * fades and slides an item up the first time it shows for [key]. [enabled] holds it hidden until
 * something else is ready (a screen transition settling, say) without losing the only-once part.
 * with [cascade], items that appear together take turns instead of all moving at once.
 */
@Composable
fun Modifier.revealOnAppear(
    key: String,
    seenState: RevealSeenState,
    enabled: Boolean = true,
    delayMillis: Int = 0,
    cascade: Boolean = false,
): Modifier {
    val alreadySeen = remember(key) { seenState.hasSeen(key) }
    if (alreadySeen) return this

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        // marked up front: a fast fling that disposes the row mid-animation shouldn't replay it later
        seenState.markSeen(key)
        val wait = delayMillis + if (cascade) seenState.nextBurstDelay() else 0
        progress.animateTo(1f, tween(RevealFadeInMs, delayMillis = wait, easing = RevealEasing))
    }

    val slidePx = with(LocalDensity.current) { RevealSlideDistance.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

/**
 * hosts loading bones and runs one soft opacity wave down through them. the wave is read in the
 * draw pass only, so nothing recomposes while it loops.
 */
@Composable
fun BonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val wave = rememberInfiniteTransition(label = "bones")
    val phase = wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BonesWaveMs, easing = LinearEasing)),
        label = "bonesPhase",
    )

    Column(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val h = size.height
                val band = h * 0.8f + 1f
                val center = -band + phase.value * (h + band * 2f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.5f to Color.Black,
                        1f to Color.Black.copy(alpha = 0.45f),
                        startY = center - band,
                        endY = center + band,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        content = content,
    )
}

@Composable
fun Bone(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(BoneColor, shape),
    )
}
