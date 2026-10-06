// DiscSkipChoreography.kt

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

private const val SkipDurationMillis = 760

private const val LiftEnd = 0.24f
private const val SwapStart = 0.24f
private const val SwapEnd = 0.68f

private val SwapEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

@Stable
class DiscSkipState internal constructor() {
    internal val progress = Animatable(1f)

    var outgoingArtworkUrl by mutableStateOf<String?>(null)
        internal set

    internal var direction by mutableFloatStateOf(1f)

    internal var settledArtworkUrl: String? = null

    val isSwapping: Boolean get() = progress.value < 1f

    fun tonearmLift(): Float {
        val p = progress.value
        return when {
            p <= LiftEnd -> (p / LiftEnd).coerceIn(0f, 1f)
            p >= SwapEnd -> 1f - ((p - SwapEnd) / (1f - SwapEnd)).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    private fun swap(): Float {
        val raw = ((progress.value - SwapStart) / (SwapEnd - SwapStart)).coerceIn(0f, 1f)
        return SwapEasing.transform(raw)
    }

    fun outgoingOffset(width: Float): Float = -direction * width * 1.35f * swap()

    fun incomingOffset(width: Float): Float = direction * width * 1.35f * (1f - swap())

    fun outgoingAlpha(): Float = (1f - swap() * 1.4f).coerceIn(0f, 1f)

    fun incomingAlpha(): Float = (swap() * 1.6f).coerceIn(0f, 1f)

    fun outgoingScale(): Float = 1f - 0.10f * swap()
}

@Composable
fun rememberDiscSkipState(
    mediaId: String,
    artworkUrl: String?,
    queueIndex: Int,
): DiscSkipState {
    val state = remember { DiscSkipState() }

    val lastIndex = remember { mutableFloatStateOf(queueIndex.toFloat()) }

    LaunchedEffect(mediaId) {
        val previousArtwork = state.settledArtworkUrl
        state.settledArtworkUrl = artworkUrl

        val movedBackward = queueIndex < lastIndex.floatValue
        lastIndex.floatValue = queueIndex.toFloat()

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
