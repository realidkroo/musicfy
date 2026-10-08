// CardMoments.kt
//
// The two "card" pages: Hello (after the profile) and Done (the very end). Every moving part is
// driven by one frame clock read only in draw/graphicsLayer lambdas, so nothing recomposes while it
// animates. Hello doubles as the blur benchmark — see BlurCapability.

package com.example.musicfy.ui.screens.setup.onboarding

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Both card pages move on to the next step on their own after this long. */
internal const val CardMomentMillis = 3000L

internal data class CardInfo(
    val username: String,
    val photo: Any?,
    val cardNumber: String,
    val joinedText: String,
)

@Composable
internal fun HelloCardStep(
    card: CardInfo,
    onProbe: (BlurCapability.Probe) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnProbe by rememberUpdatedState(onProbe)
    val currentOnFinished by rememberUpdatedState(onFinished)

    var time by remember { mutableFloatStateOf(0f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val confetti = remember { Confetti.burst(count = 90, seed = card.cardNumber.hashCode()) }

    LaunchedEffect(Unit) {
        val recorder = FrameProbeRecorder(BlurCapability.refreshRate(context))
        var start = 0L
        while (time * 1000f < CardMomentMillis) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                recorder.onFrame(now)
                time = (now - start) / 1_000_000_000f
            }
        }
        currentOnProbe(recorder.result())
        currentOnFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E)), radius = 1400f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch { tiltX.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                        scope.launch { tiltY.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { tiltY.snapTo((tiltY.value + drag.x * 0.12f).coerceIn(-35f, 35f)) }
                    scope.launch { tiltX.snapTo((tiltX.value - drag.y * 0.12f).coerceIn(-35f, 35f)) }
                }
            },
    ) {
        val cardWidth = min(maxWidth.value * 0.86f, 420f).dp
        val cardCenterFromTop = 0.42f

        // the benchmark load: a soft silver glow, blurred every frame like the app's glass is
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(with(density) { 56.dp.toPx() }, with(density) { 56.dp.toPx() }, TileMode.Decal)
                    }
                    alpha = (time / 0.5f).coerceIn(0f, 1f)
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * cardCenterFromTop)
                for (i in 0 until 4) {
                    val a = time * (0.6f + i * 0.17f) + i * 1.7f
                    drawCircle(
                        color = Color(0xFFD7D9DE).copy(alpha = 0.22f - i * 0.04f),
                        radius = size.width * (0.28f + 0.05f * i),
                        center = c + Offset(cos(a) * size.width * 0.12f, sin(a * 1.3f) * size.width * 0.1f),
                    )
                }
            }
        }

        // the circle that sweeps out from the card when the page opens, and a ring behind it
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * cardCenterFromTop)
            val far = hypot(size.width, size.height)
            val p = easeOut((time / 0.9f).coerceIn(0f, 1f))
            if (p < 1f) drawCircle(Color.White.copy(alpha = 0.10f * (1f - p)), radius = far * p, center = c)
            val ring = easeOut(((time - 0.12f) / 1.1f).coerceIn(0f, 1f))
            if (ring in 0.001f..0.999f) {
                drawCircle(
                    Color.White.copy(alpha = 0.35f * (1f - ring)),
                    radius = far * 0.75f * ring,
                    center = c,
                    style = Stroke(width = 3.dp.toPx() * (1f - ring) + 1f),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            confetti.draw(this, origin = Offset(size.width / 2f, size.height * cardCenterFromTop), time = time - 0.15f)
        }

        ProfileIdCard(
            username = card.username,
            photo = card.photo,
            cardNumber = card.cardNumber,
            joinedText = card.joinedText,
            silver = true,
            sheen = { 0.5f + (tiltY.value + cos(time * 1.4f) * 7f) / 45f },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (maxHeight * cardCenterFromTop - (cardWidth / IdCardAspect) / 2).coerceAtLeast(0.dp))
                .width(cardWidth)
                .graphicsLayer {
                    val enter = easeOutBack((time / 0.75f).coerceIn(0f, 1f))
                    val s = 0.6f + 0.4f * enter
                    scaleX = s
                    scaleY = s
                    alpha = (time / 0.25f).coerceIn(0f, 1f)
                    rotationZ = -8f * enter
                    rotationY = (1f - enter) * -55f + tiltY.value + cos(time * 1.4f) * 7f
                    rotationX = tiltX.value + sin(time * 1.1f) * 4f
                    cameraDistance = 14f * this.density
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 40.dp)
                .graphicsLayer {
                    val p = easeOut(((time - 0.35f) / 0.6f).coerceIn(0f, 1f))
                    alpha = p
                    translationY = (1f - p) * 40.dp.toPx()
                },
        ) {
            Text("Hello!", color = Color.White, fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Text("welcome to musicfy!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun DoneStep(card: CardInfo, onFinished: () -> Unit) {
    var time by remember { mutableFloatStateOf(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        var start = 0L
        while (time * 1000f < CardMomentMillis) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                time = (now - start) / 1_000_000_000f
            }
        }
        currentOnFinished()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Onb.Page)) {
        val cardWidth = min(maxWidth.value * 0.86f, 420f).dp

        // a faint wave out from the tick as it lands
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ring = easeOut(((time - 0.1f) / 1.0f).coerceIn(0f, 1f))
            if (ring in 0.001f..0.999f) {
                drawCircle(
                    Color.White.copy(alpha = 0.22f * (1f - ring)),
                    radius = size.width * 0.9f * ring,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        val s = easeOutBack((time / 0.5f).coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Onb.Badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color(0xFF1C1C1C), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "You're good to go",
                color = Onb.Subtitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = ((time - 0.2f) / 0.4f).coerceIn(0f, 1f) },
            )
            Spacer(Modifier.height(36.dp))
            ProfileIdCard(
                username = card.username,
                photo = card.photo,
                cardNumber = card.cardNumber,
                joinedText = card.joinedText,
                silver = true,
                sheen = { 0.3f + time / 3f },
                modifier = Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        val p = easeOutBack(((time - 0.15f) / 0.8f).coerceIn(0f, 1f))
                        translationY = (1f - p) * size.height * 1.4f
                        alpha = ((time - 0.15f) / 0.3f).coerceIn(0f, 1f)
                        rotationZ = -6f + (1f - p) * 10f
                        rotationY = sin(time * 1.2f) * 6f
                        cameraDistance = 14f * this.density
                    },
            )
        }
    }
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private fun easeOutBack(t: Float): Float {
    val c1 = 1.4f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/** Silver confetti: one burst from the card, falling with a little flutter. */
private class Confetti private constructor(private val pieces: List<Piece>) {
    private class Piece(
        val vx: Float,
        val vy: Float,
        val spin: Float,
        val flutter: Float,
        val w: Float,
        val h: Float,
        val round: Boolean,
        val color: Color,
    )

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, origin: Offset, time: Float) = with(scope) {
        if (time <= 0f) return@with
        val unit = size.minDimension / 400f
        val gravity = 900f * unit
        pieces.forEach { p ->
            val x = origin.x + p.vx * unit * time + sin(time * p.flutter) * 10f * unit
            val y = origin.y + p.vy * unit * time + 0.5f * gravity * time * time
            val fade = (1f - ((time - 1.9f) / 0.9f)).coerceIn(0f, 1f)
            if (fade <= 0f || y > size.height + 20f) return@forEach
            val w = p.w * unit
            val h = p.h * unit * (0.35f + 0.65f * kotlin.math.abs(cos(time * p.flutter)))
            rotate(degrees = p.spin * time, pivot = Offset(x, y)) {
                if (p.round) {
                    drawCircle(p.color.copy(alpha = p.color.alpha * fade), radius = w / 2f, center = Offset(x, y))
                } else {
                    drawRect(p.color.copy(alpha = p.color.alpha * fade), topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
                }
            }
        }
    }

    companion object {
        private val palette = listOf(
            Color.White,
            Color(0xFFE9EAEE),
            Color(0xFFC4C6CC),
            Color(0xFF9EA1A8),
            Color(0xFFF5F5F7),
            Color(0xFF7D8088),
        )

        fun burst(count: Int, seed: Int): Confetti {
            val random = Random(seed)
            val pieces = List(count) {
                // mostly upward, fanning out; gravity brings everything back down past the card
                val angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 1.25f).toFloat()
                val speed = 260f + random.nextFloat() * 420f
                Piece(
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    spin = (random.nextFloat() - 0.5f) * 720f,
                    flutter = 4f + random.nextFloat() * 8f,
                    w = 5f + random.nextFloat() * 6f,
                    h = 3f + random.nextFloat() * 4f,
                    round = random.nextFloat() < 0.25f,
                    color = palette[random.nextInt(palette.size)],
                )
            }
            return Confetti(pieces)
        }
    }
}
