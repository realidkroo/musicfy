// LaunchIntro.kt

package com.example.musicfy.ui.launch

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.example.musicfy.ui.component.BlurEffectCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * A cold start, in order:
 *
 *  1. The system splash shows the filled mark. On the app's first frame [LaunchIntroOverlay]
 *     draws the very same mark in the very same spot and the splash goes (see SplashScreenExit).
 *  2. The mark holds still while the essentials come up underneath it: the player service, the
 *     theme, and Home (its logo and bones). Anything slow here is invisible, nothing moves.
 *  3. The mark flies into Home's logo, crossfading from the filled splash mark into the outline
 *     one with a blur through the middle, while the skeleton rises in ([launchRise]).
 *  4. Only then does the heavy work get the main thread: Home swaps its bones for content, the
 *     PoToken WebView boots, auto-sync runs ([LaunchGate.awaitIntro]).
 */

/** process-wide: heavy startup work waits here until the launch intro has landed */
object LaunchGate {
    private val introDone = MutableStateFlow(false)
    private val splashGone = MutableStateFlow(false)

    /** a launch that plays the intro shows Home's skeleton first, even when its data is in already */
    @Volatile
    internal var holdHome = false

    /** a fresh launch from the launcher: the intro will play */
    fun begin() {
        holdHome = true
        splashGone.value = false
        introDone.value = false
    }

    /** no intro this time (a deep link, a recreated activity): nothing waits */
    fun skip() {
        holdHome = false
        introDone.value = true
    }

    /** the system splash has stepped aside; the app's own mark is the one on screen now */
    fun onSplashGone() {
        splashGone.value = true
    }

    internal fun finish() {
        introDone.value = true
    }

    internal suspend fun awaitSplashGone(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { splashGone.first { it } }
    }

    /**
     * Suspends until the intro has landed. Never longer than [timeoutMillis]: a process started
     * for the player service alone has no intro, and its work shouldn't sit waiting for one.
     */
    suspend fun awaitIntro(timeoutMillis: Long = 8_000L) {
        withTimeoutOrNull(timeoutMillis) { introDone.first { it } }
    }
}

// the flight: still -> a gentle start -> a long settle into the logo. scale leads the move a
// little, so the big mark has shrunk before it crosses the screen
private const val FlightMillis = 660
private val MoveEasing = CubicBezierEasing(0.55f, 0f, 0.1f, 1f)
private val ScaleEasing = CubicBezierEasing(0.45f, 0f, 0.1f, 1f)

// the skeleton: each piece rises a little after the one above it
private const val RiseDelayMillis = 80
private const val RiseMillis = 520
private const val RiseStaggerMillis = 50
private const val RiseOrders = 4
private const val RiseTotalMillis = RiseMillis + (RiseOrders - 1) * RiseStaggerMillis
private val RiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val RiseDistance = 56.dp

// the essentials get this long, then the intro plays regardless. generous on purpose: Home's
// first real composition is the heaviest frame of the launch, and it has to land while the mark
// stands still, not halfway through its flight (on a debug build it can take a couple of seconds)
private const val EssentialsTimeoutMillis = 2_500L

// the splash steps aside once the first frame is up; a launch with no splash at all moves on after this
private const val SplashTimeoutMillis = 1_500L

// blur through the middle of the morph, in the mark's own units (it scales with the mark)
private const val BlurPeak = 8f

// the system splash draws an icon with no background at 288dp, masked to a 192dp circle
private val SplashIconSize = 288.dp
private const val SplashViewport = 235f
private const val SplashMaskRadius = 96f / 288f * SplashViewport

// ic_splash_mark and ic_musicfy_mark are the same shapes; the splash one sits 58,48 further in
private val MarkOffset = Offset(58f, 48f)
private const val MarkViewportWidth = 128f
private const val MarkViewportHeight = 139f

// the splash viewport's centre: the point the flight scales around
private val Pivot = Offset(SplashViewport / 2f, SplashViewport / 2f)

// everything the marks draw, plus room for the blur, in splash units: the layer the blur runs on
private val ArtBounds = RectF(44f, 36f, 204f, 200f)

@Stable
class LaunchIntroState internal constructor(val plays: Boolean) {
    internal val flight = Animatable(if (plays) 0f else 1f)
    internal val rise = Animatable(if (plays) 0f else 1f)

    /** Home's logo in root coordinates: where the mark lands */
    internal var markBounds by mutableStateOf<Rect?>(null)

    /** the real Home (not its shell) has composed once */
    internal var homeComposed by mutableStateOf(false)

    /** the mark has reached the logo; the real logo shows from this frame on */
    internal var landed by mutableStateOf(!plays)

    /** everything has settled; the overlay and every launch modifier step out of the way */
    var done by mutableStateOf(!plays)
        internal set

    internal fun riseAt(order: Int): Float {
        val millis = rise.value * RiseTotalMillis - order * RiseStaggerMillis
        return RiseEasing.transform((millis / RiseMillis).coerceIn(0f, 1f))
    }
}

val LocalLaunchIntro = staticCompositionLocalOf<LaunchIntroState?> { null }

@Composable
fun rememberLaunchIntroState(plays: Boolean): LaunchIntroState = remember { LaunchIntroState(plays) }

/**
 * Home's logo: the spot the launch mark lands on. It reports where it is and stays hidden until
 * the flying mark gets there, then takes over in the same frame.
 */
@Composable
fun Modifier.launchMarkTarget(): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return this
        .onGloballyPositioned { state.markBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (state.landed) 1f else 0f }
}

/**
 * A piece of the skeleton that rises into place during the intro; [order] staggers it after the
 * pieces above it. Read in the draw phase only, so the rise never recomposes anything.
 */
@Composable
fun Modifier.launchRise(order: Int, distance: Dp = RiseDistance): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return graphicsLayer {
        val e = state.riseAt(order)
        alpha = e
        translationY = (1f - e) * distance.toPx()
        // faded per draw rather than through a buffer: some of these pieces are screen-sized
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** the real Home calls this once it has composed; the mark doesn't take off before then */
@Composable
fun ReportLaunchHomeComposed() {
    val state = LocalLaunchIntro.current ?: return
    if (state.done || state.homeComposed) return
    LaunchedEffect(state) { state.homeComposed = true }
}

/**
 * Home holds its skeleton while this is true: the first Home after a launch starts from bones,
 * so its content always arrives with its reveal instead of popping in under the intro.
 */
@Composable
fun rememberLaunchHold(): Boolean {
    var held by remember { mutableStateOf(LaunchGate.holdHome) }
    if (held) {
        LaunchedEffect(Unit) {
            // the intro always ends (its waits all time out), so this is only a backstop
            LaunchGate.awaitIntro(timeoutMillis = 15_000L)
            LaunchGate.holdHome = false
            held = false
        }
    }
    return held
}

/** where the mark is and how big, at one point of its flight */
private class MarkPlacement(val origin: Offset, val unit: Float)

/** this frame's placement, worked out in the layout pass and read by the layer and draw passes */
private class MarkFrame {
    var origin = Offset.Zero
    var unit = 1f
    var layerTopLeft = Offset.Zero
}

/**
 * Sits on top of the whole app for the intro: the black of the splash, and the mark flying from
 * the splash's spot into Home's logo. [essentialsReady] is what the mark waits for before it moves.
 */
@Composable
fun LaunchIntroOverlay(state: LaunchIntroState, essentialsReady: Boolean) {
    if (state.done) return
    val ready by rememberUpdatedState(essentialsReady)
    val art = remember { LaunchMarkArt() }
    val frame = remember { MarkFrame() }

    LaunchedEffect(state) {
        val shownAt = SystemClock.uptimeMillis()
        val inTime = withTimeoutOrNull(EssentialsTimeoutMillis) {
            snapshotFlow { ready && state.homeComposed && state.markBounds != null }.first { it }
        } != null
        // the system splash still covers us until the first frame is up; flying under it would
        // spend the start of the flight where nobody can see it
        LaunchGate.awaitSplashGone(SplashTimeoutMillis)
        Timber.tag("LaunchIntro").d(
            "take-off after %dms, essentials %s (player+theme=%b home=%b logo=%b)",
            SystemClock.uptimeMillis() - shownAt, if (inTime) "ready" else "timed out",
            ready, state.homeComposed, state.markBounds != null,
        )
        // whatever just came up draws its first (heavy) frame while the mark is still standing
        withFrameNanos { }
        withFrameNanos { }
        val flightAt = SystemClock.uptimeMillis()
        var worstFrame = 0L
        coroutineScope {
            val watch = launch {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    worstFrame = maxOf(worstFrame, (now - last) / 1_000_000L)
                    last = now
                }
            }
            launch { state.rise.animateTo(1f, tween(RiseTotalMillis, RiseDelayMillis, LinearEasing)) }
            state.flight.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
            state.landed = true
            watch.cancel()
        }
        Timber.tag("LaunchIntro").d(
            "landed %dms after take-off, worst frame %dms", SystemClock.uptimeMillis() - flightAt, worstFrame,
        )
        LaunchGate.finish()
        state.done = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // the splash's black, fading off the skeleton; it also keeps taps off the skeleton
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .drawBehind {
                    val backdrop = 1f - smoothstep(0.18f, 0.7f, state.flight.value)
                    if (backdrop > 0f) drawRect(Color.Black.copy(alpha = backdrop))
                }
        )

        // the mark, on a layer just big enough for it so the blur stays cheap at every size
        Spacer(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placement = placementAt(state, IntSize(constraints.maxWidth, constraints.maxHeight))
                    val left = floor(placement.origin.x + ArtBounds.left * placement.unit).toInt()
                    val top = floor(placement.origin.y + ArtBounds.top * placement.unit).toInt()
                    val right = ceil(placement.origin.x + ArtBounds.right * placement.unit).toInt()
                    val bottom = ceil(placement.origin.y + ArtBounds.bottom * placement.unit).toInt()
                    frame.origin = placement.origin
                    frame.unit = placement.unit
                    frame.layerTopLeft = Offset(left.toFloat(), top.toFloat())
                    val placeable = measurable.measure(Constraints.fixed(right - left, bottom - top))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(left, top)
                    }
                }
                .graphicsLayer {
                    val t = state.flight.value
                    renderEffect = BlurEffectCache.get(BlurPeak * bell((t - 0.04f) / 0.76f) * frame.unit)
                }
                .drawBehind {
                    val t = state.flight.value
                    if (state.landed) return@drawBehind
                    val fade = if (state.markBounds == null) 1f - smoothstep(0.55f, 1f, t) else 1f
                    val filledAlpha = (1f - smoothstep(0.3f, 0.78f, t)) * fade
                    val outlineAlpha = smoothstep(0.12f, 0.6f, t) * fade
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.save()
                        // the layer starts at the art's top left, rounded down to a whole pixel
                        native.translate(frame.origin.x - frame.layerTopLeft.x, frame.origin.y - frame.layerTopLeft.y)
                        native.scale(frame.unit, frame.unit)
                        art.draw(native, filledAlpha, outlineAlpha)
                        native.restore()
                    }
                }
        )
    }
}

/** the mark's place at the flight's current point, for an overlay of [size] */
private fun Density.placementAt(state: LaunchIntroState, size: IntSize): MarkPlacement {
    val t = state.flight.value
    val splashSize = SplashIconSize.toPx()
    val startUnit = splashSize / SplashViewport
    val startOrigin = Offset(size.width / 2f - splashSize / 2f, size.height / 2f - splashSize / 2f)

    val target = state.markBounds
    val endUnit: Float
    val endOrigin: Offset
    if (target != null) {
        // the logo is an Icon: its art is fitted and centred in the logo's box
        endUnit = min(target.width / MarkViewportWidth, target.height / MarkViewportHeight)
        val artLeft = target.left + (target.width - MarkViewportWidth * endUnit) / 2f
        val artTop = target.top + (target.height - MarkViewportHeight * endUnit) / 2f
        endOrigin = Offset(artLeft, artTop) - MarkOffset * endUnit
    } else {
        // nowhere to land (Home never laid out): settle smaller where it is and fade
        endUnit = startUnit * 0.6f
        endOrigin = startOrigin + Pivot * (startUnit - endUnit)
    }

    val unit = startUnit * (endUnit / startUnit).pow(ScaleEasing.transform(t))
    val pivot = lerp(startOrigin + Pivot * startUnit, endOrigin + Pivot * endUnit, MoveEasing.transform(t))
    return MarkPlacement(origin = pivot - Pivot * unit, unit = unit)
}

/** the two marks, in the splash drawable's units, ready to draw at any scale */
private class LaunchMarkArt {
    private val triangle = path(
        "M163.5 96.3494C180.167 105.972 180.167 130.028 163.5 139.651L108 171.694C91.3333 181.316 " +
            "70.5 169.288 70.5 150.043L70.5 85.957C70.5 66.712 91.3333 54.6839 108 64.3064L163.5 96.3494Z"
    )
    private val wave = path(
        "M71.5 120C76 120 86.2 120 91 120C97 120 95 127.5 104 127.5C113 127.5 110.5 112 121.5 112" +
            "C132.5 112 126.5 126.634 137.5 126.634C148.5 126.634 144.5 119.128 155 119.128" +
            "C163.4 119.128 167.667 119.128 171 119.128"
    )
    private val mask = Path().apply { addCircle(Pivot.x, Pivot.y, SplashMaskRadius, Path.Direction.CW) }

    // ic_splash_mark: a light shape with a dark outline and a dark wave through it
    private val fill = paint(0xFFFFFDFB.toInt()) { style = Paint.Style.FILL }
    private val darkOutline = paint(0xFF212121.toInt()) {
        style = Paint.Style.STROKE
        strokeWidth = 19f
        strokeJoin = Paint.Join.ROUND
    }
    private val darkWave = paint(0xFF212121.toInt()) {
        style = Paint.Style.STROKE
        strokeWidth = 13f
    }

    // ic_musicfy_mark: the same outline and wave, white, nothing filled
    private val lightOutline = paint(android.graphics.Color.WHITE) {
        style = Paint.Style.STROKE
        strokeWidth = 19f
        strokeJoin = Paint.Join.ROUND
    }
    private val lightWave = paint(android.graphics.Color.WHITE) {
        style = Paint.Style.STROKE
        strokeWidth = 13f
        strokeCap = Paint.Cap.ROUND
    }

    fun draw(canvas: android.graphics.Canvas, filledAlpha: Float, outlineAlpha: Float) {
        // each mark is faded as a whole, so its overlapping strokes never show through each other
        if (filledAlpha > 0.002f) {
            canvas.saveLayerAlpha(ArtBounds, (filledAlpha * 255f).toInt())
            canvas.clipPath(mask)
            canvas.drawPath(triangle, fill)
            canvas.drawPath(triangle, darkOutline)
            canvas.drawPath(wave, darkWave)
            canvas.restore()
        }
        if (outlineAlpha > 0.002f) {
            canvas.saveLayerAlpha(ArtBounds, (outlineAlpha * 255f).toInt())
            canvas.drawPath(triangle, lightOutline)
            canvas.drawPath(wave, lightWave)
            canvas.restore()
        }
    }

    private fun path(data: String): Path = PathParser.createPathFromPathData(data)

    private inline fun paint(color: Int, setup: Paint.() -> Unit) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            setup()
        }
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0 at both ends, 1 in the middle, soft all the way */
private fun bell(x: Float): Float {
    if (x <= 0f || x >= 1f) return 0f
    val s = sin(PI.toFloat() * x)
    return s * s
}
