// discskipchoreographykt
// the mechanical track-change transition for the disc cover styles: lift the
// record slide the old disc out slide the new one in set the arm back down

// one animatable drives all three stages everything downstream reads it
// lambdas in the draw phase so a transition costs draw-phase invalidations
// subtree is never recomposed mid-slide

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

// total duration of a full lift → swap → drop cycle
private const val SkipDurationMillis = 760

// stage boundaries as fractions of the whole cycle
private const val LiftEnd = 0.24f
private const val SwapStart = 0.24f
private const val SwapEnd = 0.68f

private val SwapEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

// live state of a disc swap settled state is [progress] == 1: no outgoing disc
@Stable
class DiscSkipState internal constructor() {
    internal val progress = Animatable(1f)

    // artwork of the track being slid away null once the swap has settled
    var outgoingArtworkUrl by mutableStateOf<String?>(null)
        internal set

    // +1 when moving forward in the queue (old disc exits left) -1 when moving back
    internal var direction by mutableFloatStateOf(1f)

    // the artwork currently on screen so the next change knows what to slide out
    internal var settledArtworkUrl: String? = null

    // true while a swap is in flight — used to suppress the platter's idle rotation
    val isSwapping: Boolean get() = progress.value < 1f

    // 0 = cartridge resting on the record 1 = swung clear rises across the lift stage
    fun tonearmLift(): Float {
        val p = progress.value
        return when {
            p <= LiftEnd -> (p / LiftEnd).coerceIn(0f, 1f)
            p >= SwapEnd -> 1f - ((p - SwapEnd) / (1f - SwapEnd)).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    // how far through the swap stage alone eased 0 before it starts 1 after it ends
    private fun swap(): Float {
        val raw = ((progress.value - SwapStart) / (SwapEnd - SwapStart)).coerceIn(0f, 1f)
        return SwapEasing.transform(raw)
    }

    // horizontal displacement of the departing disc in pixels given the art box's width
    fun outgoingOffset(width: Float): Float = -direction * width * 1.35f * swap()

    // horizontal displacement of the arriving disc — starts off the opposite edge
    fun incomingOffset(width: Float): Float = direction * width * 1.35f * (1f - swap())

    fun outgoingAlpha(): Float = (1f - swap() * 1.4f).coerceIn(0f, 1f)

    fun incomingAlpha(): Float = (swap() * 1.6f).coerceIn(0f, 1f)

    // slight shrink as a disc leaves so it reads as travelling away rather than sideways
    fun outgoingScale(): Float = 1f - 0.10f * swap()
}

// drives a [discskipstate] from track changes keyed on [mediaid] alone: a second
@Composable
fun rememberDiscSkipState(
    mediaId: String,
    artworkUrl: String?,
    queueIndex: Int,
): DiscSkipState {
    val state = remember { DiscSkipState() }
    // not a keyed remember: this has to survive the track change that triggers
    val lastIndex = remember { mutableFloatStateOf(queueIndex.toFloat()) }

    LaunchedEffect(mediaId) {
        val previousArtwork = state.settledArtworkUrl
        state.settledArtworkUrl = artworkUrl

        val movedBackward = queueIndex < lastIndex.floatValue
        lastIndex.floatValue = queueIndex.toFloat()

        // nothing to slide out of the way on the very first track or when the
        // actually change (same image different id) — animating then reads as a
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
