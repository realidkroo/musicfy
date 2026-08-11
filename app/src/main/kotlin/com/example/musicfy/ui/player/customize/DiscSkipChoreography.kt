// DiscSkipChoreography.kt
// The mechanical track-change transition for the disc cover styles: lift the tonearm off the
// record, slide the old disc out, slide the new one in, set the arm back down.
//
// One Animatable drives all three stages. Everything downstream reads it through () -> Float
// lambdas in the draw phase, so a transition costs draw-phase invalidations only — the disc
// subtree is never recomposed mid-slide.

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Total duration of a full lift → swap → drop cycle. */
private const val SkipDurationMillis = 760

// Stage boundaries as fractions of the whole cycle.
private const val LiftEnd = 0.24f
private const val SwapStart = 0.24f
private const val SwapEnd = 0.68f

private val SwapEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

/**
 * Live state of a disc swap.
 *
 * Settled state is [progress] == 1: no outgoing disc, arm down, incoming disc at rest. That is
 * also the initial state, so a player that never skips never animates anything.
 */
@Stable
class DiscSkipState internal constructor() {
    internal val progress = Animatable(1f)

    /** Artwork of the track being slid away. Null once the swap has settled. */
    var outgoingArtworkUrl by mutableStateOf<String?>(null)
        internal set

    /** +1 when moving forward in the queue (old disc exits left), -1 when moving back. */
    internal var direction by mutableFloatStateOf(1f)

    /** The artwork currently on screen, so the next change knows what to slide out. */
    internal var settledArtworkUrl: String? = null

    /** True while a swap is in flight — used to suppress the platter's idle rotation. */
    val isSwapping: Boolean get() = progress.value < 1f

    /**
     * 0 = cartridge resting on the record, 1 = swung clear. Rises across the lift stage, holds
     * while the discs exchange, falls across the drop stage.
     */
    fun tonearmLift(): Float {
        val p = progress.value
        return when {
            p <= LiftEnd -> (p / LiftEnd).coerceIn(0f, 1f)
            p >= SwapEnd -> 1f - ((p - SwapEnd) / (1f - SwapEnd)).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    /** How far through the swap stage alone, eased. 0 before it starts, 1 after it ends. */
    private fun swap(): Float {
        val raw = ((progress.value - SwapStart) / (SwapEnd - SwapStart)).coerceIn(0f, 1f)
        return SwapEasing.transform(raw)
    }

    /** Horizontal displacement of the departing disc, in pixels, given the art box's width. */
    fun outgoingOffset(width: Float): Float = -direction * width * 1.35f * swap()

    /** Horizontal displacement of the arriving disc — starts off the opposite edge. */
    fun incomingOffset(width: Float): Float = direction * width * 1.35f * (1f - swap())

    fun outgoingAlpha(): Float = (1f - swap() * 1.4f).coerceIn(0f, 1f)

    fun incomingAlpha(): Float = (swap() * 1.6f).coerceIn(0f, 1f)

    /** Slight shrink as a disc leaves, so it reads as travelling away rather than sideways. */
    fun outgoingScale(): Float = 1f - 0.10f * swap()
}

/**
 * Drives a [DiscSkipState] from track changes.
 *
 * Keyed on [mediaId] alone: a second skip landing mid-transition cancels the running animation
 * and restarts from the new track's own state rather than queueing behind it. [queueIndex] is
 * read (not keyed) at the moment of the change purely to work out which way the discs should
 * travel — a forward move sends the old disc left, a backward move sends it right.
 *
 * Only ever called from DiscCoverStack, which is itself only composed for disc styles — so
 * switching to a non-disc style unmounts this state entirely rather than needing to disable it.
 */
@Composable
fun rememberDiscSkipState(
    mediaId: String,
    artworkUrl: String?,
    queueIndex: Int,
): DiscSkipState {
    val state = remember { DiscSkipState() }
    // Not a keyed remember: this has to survive the track change that triggers the animation.
    val lastIndex = remember { mutableFloatStateOf(queueIndex.toFloat()) }

    LaunchedEffect(mediaId) {
        val previousArtwork = state.settledArtworkUrl
        state.settledArtworkUrl = artworkUrl

        val movedBackward = queueIndex < lastIndex.floatValue
        lastIndex.floatValue = queueIndex.toFloat()

        // Nothing to slide out of the way on the very first track, or when the artwork did not
        // actually change (same image, different id) — animating then reads as a glitch.
        if (previousArtwork == null || previousArtwork == artworkUrl) {
            state.outgoingArtworkUrl = null
            state.progress.snapTo(1f)
            return@LaunchedEffect
        }

        state.direction = if (movedBackward) -1f else 1f
        state.outgoingArtworkUrl = previousArtwork
        state.progress.snapTo(0f)
        state.progress.animateTo(1f, tween(durationMillis = SkipDurationMillis))
        state.outgoingArtworkUrl = null
    }

    return state
}
