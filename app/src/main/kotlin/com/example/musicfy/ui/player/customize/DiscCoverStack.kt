// DiscCoverStack.kt
// Composes the pieces of a disc cover style into the thing MorphingCover actually places in the
// artwork box: the platter (possibly two of them, mid-swap), the tonearm, the idle spin clock,
// and the preferences that drive all three.
//
// MorphingCover calls this ONLY for styles where PlayerCoverStyle.isDisc is true. The
// edge-to-edge and squared styles keep using the artwork stack that was already there, untouched
// — the point of this file is to be additive, not to re-route the existing player.

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.musicfy.constants.DiscNameKey
import com.example.musicfy.constants.DiscRealisticModeKey
import com.example.musicfy.constants.DiscRotatingAnimationKey
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.isActive

/** Degrees per second at rest. A real 33⅓ rpm platter (200°/s) reads as a blur at this size. */
private const val SpinDegreesPerSecond = 26f

/**
 * @param spinActive whether the idle rotation clock should run at all. The caller computes this
 *   with derivedStateOf from the sheet's own progress, exactly like MorphingCover's existing
 *   `warpClockActive` — an always-mounted composable must not keep waking Choreographer 60×/s
 *   while the player sits collapsed in the mini pill.
 */
@Composable
fun DiscCoverStack(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    mediaId: String,
    queueIndex: Int,
    isPlaying: Boolean,
    spinActive: Boolean,
    editMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val (rotatingEnabled) = rememberPreference(DiscRotatingAnimationKey, defaultValue = true)
    val (realistic) = rememberPreference(DiscRealisticModeKey, defaultValue = true)
    val (discName) = rememberPreference(DiscNameKey, defaultValue = "")

    val skip = rememberDiscSkipState(
        mediaId = mediaId,
        artworkUrl = artworkUrl,
        queueIndex = queueIndex,
    )

    // Accumulated platter angle. A plain float state written from the frame clock and read only
    // inside graphicsLayer — never destructured with `by` at composable scope, which would
    // recompose this whole subtree every single frame.
    val angle = remember { mutableFloatStateOf(0f) }
    val lastFrameNanos = remember { mutableLongStateOf(0L) }

    // spinActive already carries the caller's own gating (sheet progress, lyrics progress),
    // so a platter that has dissolved into the square artwork stops costing a frame clock.
    val spinning = rotatingEnabled && isPlaying && spinActive
    LaunchedEffect(spinning) {
        if (!spinning) {
            // Drop the anchor so the next run measures from its own first frame instead of
            // jumping the platter forward by however long playback was paused.
            lastFrameNanos.longValue = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val previous = lastFrameNanos.longValue
                lastFrameNanos.longValue = frameTimeNanos
                if (previous != 0L) {
                    val deltaSeconds = (frameTimeNanos - previous) / 1_000_000_000f
                    angle.floatValue = (angle.floatValue + SpinDegreesPerSecond * deltaSeconds).mod(360f)
                }
            }
        }
    }

    Box(modifier = modifier) {
        // Departing disc. Only mounted while a swap is actually in flight.
        val outgoing = skip.outgoingArtworkUrl
        if (outgoing != null) {
            DiscCover(
                style = style,
                artworkUrl = outgoing,
                discName = discName,
                realistic = realistic,
                editMode = false,
                rotationProvider = { angle.floatValue },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = skip.outgoingOffset(size.width)
                        alpha = skip.outgoingAlpha()
                        val s = skip.outgoingScale()
                        scaleX = s
                        scaleY = s
                    },
            )
        }

        DiscCover(
            style = style,
            artworkUrl = artworkUrl,
            discName = discName,
            realistic = realistic,
            editMode = editMode,
            rotationProvider = { angle.floatValue },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = skip.incomingOffset(size.width)
                    alpha = skip.incomingAlpha()
                },
        )

        if (style.hasTonearm) {
            DiscTonearm(
                liftProvider = { skip.tonearmLift() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
