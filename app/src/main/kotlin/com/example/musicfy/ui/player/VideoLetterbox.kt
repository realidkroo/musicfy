// VideoLetterbox.kt

package com.example.musicfy.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What one sampled frame says about the bars on one axis: top and bottom (letterbox) or left
 * and right (pillarbox).
 */
internal sealed interface AxisReading {
    /** The frame can't tell: a fade, a black frame, a dark scene, or bars that don't match. */
    data object Unknown : AxisReading

    /** Bars cover [inset] of the frame on each side of this axis. 0 means there are none. */
    data class Inset(val inset: Float) : AxisReading
}

internal data class FrameReading(
    /** Bars on the top and bottom. */
    val vertical: AxisReading,
    /** Bars on the left and right. */
    val horizontal: AxisReading,
)

/**
 * Finds black bars baked into a music video's frames, so the video view can zoom past them and
 * zoom back out when a scene drops them. Many music videos switch between letterboxed and full
 * frame scenes, so this runs on every sample rather than once per video.
 *
 * Pure so it can be unit tested; [YouTubeVideoBackground] feeds it small frame samples.
 */
internal object VideoLetterbox {
    /** Largest bar, per side, worth believing. Anything taller is a dark frame, not a bar. */
    const val MAX_INSET = 0.4f

    // A pixel this dark is bar-black. Encoded black decodes to roughly 0-16.
    private const val DARK_LUMA = 26

    // A pixel this bright is clearly picture.
    private const val BRIGHT_LUMA = 44

    // A bar line is almost entirely black and has no real brightness in it.
    private const val BAR_DARK_FRACTION = 0.96f
    private const val BAR_MAX_MEAN = 16f

    // A line inside a bar that carries a channel logo or a subtitle is still mostly black.
    private const val MOSTLY_DARK_FRACTION = 0.6f

    // How far a logo or subtitle may interrupt a bar before the bar is considered to have ended.
    private const val MAX_GAP = 0.06f

    // A bar must end in a hard edge onto picture. A dark scene fades out gradually instead.
    private const val EDGE_PROBE_LINES = 3
    private const val EDGE_BRIGHT_FRACTION = 0.1f
    private const val EDGE_MIN_MEAN = 34f

    // Bars thinner than this are encoder edge noise, not letterboxing.
    private const val MIN_BAR_LINES = 2

    // Crop one extra line so the soft, half-grey line at the bar's edge never shows.
    private const val EDGE_MARGIN_LINES = 1

    // Real letterboxing is centred. Bars that differ by more than this are a scene, not bars.
    private const val SYMMETRY_MIN_LINES = 3
    private const val SYMMETRY_FRACTION = 0.2f

    // A frame with almost nothing bright in it is a fade or a black frame.
    private const val FRAME_MIN_BRIGHT_FRACTION = 0.01f

    private const val EDGE_UNKNOWN = -1
    private const val EDGE_NONE = 0

    private class LineStats(val darkFraction: Float, val brightFraction: Float, val mean: Float) {
        val isBar get() = darkFraction >= BAR_DARK_FRACTION && mean <= BAR_MAX_MEAN
        val isMostlyDark get() = darkFraction >= MOSTLY_DARK_FRACTION
        val isPicture get() = brightFraction >= EDGE_BRIGHT_FRACTION || mean >= EDGE_MIN_MEAN
    }

    /**
     * Reads one frame, given as ARGB [pixels] of [width] x [height] covering the whole video frame.
     */
    fun analyze(pixels: IntArray, width: Int, height: Int): FrameReading {
        val unknown = FrameReading(AxisReading.Unknown, AxisReading.Unknown)
        if (width < 8 || height < 8 || pixels.size < width * height) return unknown

        val luma = IntArray(width * height)
        var brightPixels = 0
        for (i in luma.indices) {
            val p = pixels[i]
            val l = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            luma[i] = l
            if (l >= BRIGHT_LUMA) brightPixels++
        }
        if (brightPixels < luma.size * FRAME_MIN_BRIGHT_FRACTION) return unknown

        val rowStats = Array(height) { y -> lineStats(width) { x -> luma[y * width + x] } }
        val columnStats = Array(width) { x -> lineStats(height) { y -> luma[y * width + x] } }

        return FrameReading(
            vertical = readAxis(rowStats),
            horizontal = readAxis(columnStats),
        )
    }

    private inline fun lineStats(length: Int, lumaAt: (Int) -> Int): LineStats {
        var dark = 0
        var bright = 0
        var sum = 0L
        for (i in 0 until length) {
            val l = lumaAt(i)
            if (l <= DARK_LUMA) dark++
            if (l >= BRIGHT_LUMA) bright++
            sum += l
        }
        return LineStats(dark.toFloat() / length, bright.toFloat() / length, sum.toFloat() / length)
    }

    private fun readAxis(lines: Array<LineStats>): AxisReading {
        val count = lines.size
        val start = readEdge(count) { lines[it] }
        val end = readEdge(count) { lines[count - 1 - it] }

        // Picture touching either edge means there is no letterbox on this axis.
        if (start == EDGE_NONE || end == EDGE_NONE) return AxisReading.Inset(0f)
        if (start == EDGE_UNKNOWN || end == EDGE_UNKNOWN) return AxisReading.Unknown

        val tolerance = max(SYMMETRY_MIN_LINES.toFloat(), max(start, end) * SYMMETRY_FRACTION)
        if (abs(start - end) > tolerance) return AxisReading.Unknown

        val inset = (min(start, end) + EDGE_MARGIN_LINES).toFloat() / count
        return AxisReading.Inset(inset.coerceAtMost(MAX_INSET))
    }

    /**
     * How many lines of bar sit at one edge, walking inwards: [EDGE_NONE] when picture starts
     * right at the edge, [EDGE_UNKNOWN] when the frame is too dark to tell.
     */
    private inline fun readEdge(count: Int, lineAt: (Int) -> LineStats): Int {
        val maxGap = max(1, (count * MAX_GAP).toInt())
        var extent = 0
        var i = 0
        while (i < count) {
            val line = lineAt(i)
            if (line.isBar) {
                extent = i + 1
            } else if (!line.isMostlyDark || i - extent >= maxGap) {
                break
            }
            i++
        }

        if (extent > count * MAX_INSET) return EDGE_UNKNOWN
        if (extent < MIN_BAR_LINES) return EDGE_NONE

        val probeEnd = min(count, extent + EDGE_PROBE_LINES)
        for (j in extent until probeEnd) {
            if (lineAt(j).isPicture) return extent
        }
        return EDGE_UNKNOWN
    }
}

/**
 * Turns per-frame readings for one axis into a steady inset, so a single odd frame never moves
 * the zoom. Bars must show up in [samplesToZoomIn] agreeing samples before the view zooms in,
 * and be gone for [samplesToZoomOut] before it zooms back out. Unknown readings are skipped.
 */
internal class LetterboxStabilizer(
    private val samplesToZoomIn: Int = 3,
    private val samplesToZoomOut: Int = 2,
    private val tolerance: Float = 0.015f,
    private val maxSampleAgeMs: Long = 2_500L,
) {
    /** The inset the view should currently be zoomed past. */
    var inset: Float = 0f
        private set

    private val recentTimes = ArrayDeque<Long>()
    private val recentInsets = ArrayDeque<Float>()

    /** Feeds one reading taken at [nowMs]; returns true when [inset] changed. */
    fun offer(reading: AxisReading, nowMs: Long): Boolean {
        while (recentTimes.isNotEmpty() && nowMs - recentTimes.first() > maxSampleAgeMs) {
            recentTimes.removeFirst()
            recentInsets.removeFirst()
        }
        if (reading !is AxisReading.Inset) return false

        recentTimes.addLast(nowMs)
        recentInsets.addLast(reading.inset)
        while (recentInsets.size > max(samplesToZoomIn, samplesToZoomOut)) {
            recentTimes.removeFirst()
            recentInsets.removeFirst()
        }

        val needed = if (reading.inset > inset) samplesToZoomIn else samplesToZoomOut
        if (recentInsets.size < needed) return false

        val window = recentInsets.takeLast(needed)
        if (window.max() - window.min() > tolerance) return false
        // Every sample in the window has to point the same way, away from where the view is.
        if (window.any { abs(it - inset) <= tolerance }) return false
        if (window.any { it > inset } && window.any { it < inset }) return false

        // The widest agreeing reading, so no sliver of bar is left showing.
        inset = window.max()
        return true
    }
}

internal data class VideoTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
) {
    companion object {
        val Identity = VideoTransform(1f, 1f, 0f, 0f)
    }
}

/**
 * Scale and translation, applied about the view's top-left corner, that take a video stretched
 * to fill a [viewWidth] x [viewHeight] view and show it at its real [videoAspect], centre-cropped
 * so the picture inside the bars ([insetX] per side horizontally, [insetY] vertically) covers
 * the whole view. With no bars this is the same as a plain centre-crop.
 */
internal fun videoCropTransform(
    viewWidth: Float,
    viewHeight: Float,
    videoAspect: Float,
    insetX: Float,
    insetY: Float,
): VideoTransform {
    if (viewWidth <= 0f || viewHeight <= 0f) return VideoTransform.Identity
    val viewAspect = viewWidth / viewHeight
    val aspect = if (videoAspect > 0f && videoAspect.isFinite()) videoAspect else viewAspect

    val keepX = 1f - 2f * insetX.coerceIn(0f, VideoLetterbox.MAX_INSET)
    val keepY = 1f - 2f * insetY.coerceIn(0f, VideoLetterbox.MAX_INSET)
    val pictureAspect = aspect * keepX / keepY

    val pictureWidth: Float
    val pictureHeight: Float
    if (pictureAspect > viewAspect) {
        pictureHeight = viewHeight
        pictureWidth = viewHeight * pictureAspect
    } else {
        pictureWidth = viewWidth
        pictureHeight = viewWidth / pictureAspect
    }

    val frameWidth = pictureWidth / keepX
    val frameHeight = pictureHeight / keepY
    return VideoTransform(
        scaleX = frameWidth / viewWidth,
        scaleY = frameHeight / viewHeight,
        translationX = (viewWidth - frameWidth) / 2f,
        translationY = (viewHeight - frameHeight) / 2f,
    )
}
