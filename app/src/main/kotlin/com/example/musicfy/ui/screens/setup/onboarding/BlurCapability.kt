// BlurCapability.kt
//
// Should onboarding suggest turning blur off? Phone specs alone don't answer it: a Galaxy A03
// (Unisoc, 720p at 60 Hz) and A24 (Helio G99, 90 Hz) run the glass fine, while a Redmi Note 11 Pro
// (Helio G96, 1080p at 120 Hz) has a stronger GPU on paper and still drops frames, because it has
// to fill four times the pixels per second. So the real answer comes from timing frames: the Hello
// card page draws a blurred glow plus confetti for ~3 s, which costs about what the app's glass
// does, and every frame's arrival is recorded. A small table of phones confirmed either way
// settles the borderline cases.

package com.example.musicfy.ui.screens.setup.onboarding

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.util.Locale

object BlurCapability {

    enum class Verdict { Fine, Laggy, NotApplicable }

    /** Frame timings from the Hello page. */
    data class Probe(
        val frames: Int,
        val slowFrames: Int,
        val averageFrameMs: Float,
        val refreshHz: Float,
    ) {
        val slowRatio: Float get() = if (frames == 0) 0f else slowFrames.toFloat() / frames
    }

    data class Result(val verdict: Verdict, val summary: String)

    /** Chips confirmed to stutter under the app's glass. */
    private val knownLaggy = listOf(
        "mt6781", // Helio G96 — Redmi Note 11 Pro 4G, the reference "can, but laggy" phone
    )

    /** Chips confirmed fine, even though the frame timing can be borderline. */
    private val knownFine = listOf(
        "mt6789", // Helio G99 — Galaxy A24
        "t606", "ums9230", // Unisoc T606 — Galaxy A03
        "sc9863", // Unisoc SC9863A — Galaxy A03 Core
    )

    fun socName(): String {
        val parts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Build.SOC_MANUFACTURER)
                add(Build.SOC_MODEL)
            }
            add(Build.HARDWARE)
            add(Build.BOARD)
        }
        return parts.filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").lowercase(Locale.ROOT)
    }

    fun refreshRate(context: Context): Float {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        return display?.refreshRate?.takeIf { it >= 30f } ?: 60f
    }

    fun decide(context: Context, probe: Probe?): Result {
        // below Android 12 the app draws its glass without a blur at all, so there's nothing to turn off
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Result(Verdict.NotApplicable, "Blur needs Android 12")
        }
        val soc = socName()
        val socLabel = displaySoc()
        val hz = probe?.refreshHz ?: refreshRate(context)
        val timing = probe?.let { "${(it.slowRatio * 100).toInt()}% slow frames at ${hz.toInt()} Hz" } ?: "${hz.toInt()} Hz"

        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        if (lowRam) return Result(Verdict.Laggy, "$socLabel · low-memory device")
        if (knownLaggy.any { it in soc }) return Result(Verdict.Laggy, "$socLabel · $timing")

        val probeSaysLaggy = probe != null && probe.frames >= 20 && (
            probe.slowRatio > SLOW_RATIO_LIMIT ||
                probe.averageFrameMs > (1000f / probe.refreshHz) * AVERAGE_LIMIT
            )
        if (knownFine.any { it in soc }) {
            // only a really bad run overrides a phone known to cope
            val terrible = probe != null && probe.slowRatio > 0.5f
            return Result(if (terrible) Verdict.Laggy else Verdict.Fine, "$socLabel · $timing")
        }
        return Result(if (probeSaysLaggy) Verdict.Laggy else Verdict.Fine, "$socLabel · $timing")
    }

    private fun displaySoc(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val maker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            if (model != null) return listOfNotNull(maker, model).joinToString(" ")
        }
        return Build.HARDWARE.ifBlank { Build.MODEL }
    }

    /** More than this share of frames arriving late (over 1.5 refresh intervals) reads as stutter. */
    private const val SLOW_RATIO_LIMIT = 0.2f

    /** Or the average frame taking this much longer than one refresh interval. */
    private const val AVERAGE_LIMIT = 1.3f
}

/** Collects frame intervals; fed from a withFrameNanos loop on the Hello page. */
internal class FrameProbeRecorder(private val refreshHz: Float) {
    private var last = 0L
    private var started = 0L
    private var frames = 0
    private var slow = 0
    private var totalNanos = 0L

    fun onFrame(nanos: Long) {
        if (started == 0L) started = nanos
        // the first ~400 ms are composition, image decodes and shader compiles: not representative
        if (nanos - started < WARMUP_NANOS) {
            last = nanos
            return
        }
        // an interval counts only when both its frames are past the warm-up
        if (last != 0L && last - started >= WARMUP_NANOS) {
            val interval = nanos - last
            frames++
            totalNanos += interval
            if (interval > (1_000_000_000f / refreshHz) * 1.5f) slow++
        }
        last = nanos
    }

    fun result(): BlurCapability.Probe = BlurCapability.Probe(
        frames = frames,
        slowFrames = slow,
        averageFrameMs = if (frames == 0) 0f else totalNanos / frames / 1_000_000f,
        refreshHz = refreshHz,
    )

    private companion object {
        const val WARMUP_NANOS = 400_000_000L
    }
}
