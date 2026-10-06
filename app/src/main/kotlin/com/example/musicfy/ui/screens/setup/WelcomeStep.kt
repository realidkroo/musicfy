package com.example.musicfy.ui.screens.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.component.FadeInCover
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.utils.resize
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.BlurDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.sin
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity

enum class SpawningState { Hidden, Spawning, Playing, Despawning }

enum class CurrentAnimation {
    Disc,
    Grid
}

@Composable
fun WelcomeStep(isHiding: Boolean = false) {
    var currentAnimation by remember { mutableStateOf(CurrentAnimation.Disc) }
    var thumbnails by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val fetched = withContext(Dispatchers.IO) {
            listOf("top hits", "classic rock", "j-pop", "pop hits 2020s")
                .flatMap { query ->
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.map { it.thumbnail.resize(544, 544) }
                        .orEmpty()
                }
                .distinct()
        }
        if (fetched.size >= 12) {
            thumbnails = fetched.shuffled().take(21)
            return@LaunchedEffect
        }

        val predefinedCovers = listOf(
            "https://i.ytimg.com/vi/Zi_XLOBDo_Y/sddefault.jpg",
            "https://i.ytimg.com/vi/fJ9rUzIMcZQ/sddefault.jpg",
            "https://i.ytimg.com/vi/hTWKbfoikeg/sddefault.jpg",
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
            "https://i.ytimg.com/vi/1w7OgIMMRc4/sddefault.jpg",
            "https://i.ytimg.com/vi/A_MjCqQoLLA/sddefault.jpg",
            "https://i.ytimg.com/vi/Gs069dndIYk/sddefault.jpg",
            "https://i.ytimg.com/vi/4fndeDfaWCg/sddefault.jpg",
            "https://i.ytimg.com/vi/PWgvGjAhvIw/sddefault.jpg",
            "https://i.ytimg.com/vi/_Yhyp-_hX2s/sddefault.jpg",
            "https://i.ytimg.com/vi/kXYiU_JCYtU/sddefault.jpg",
            "https://i.ytimg.com/vi/yKNxeF4KMsY/sddefault.jpg",
            "https://i.ytimg.com/vi/rYEDA3JcQqw/sddefault.jpg",
            "https://i.ytimg.com/vi/4NRXx6U8ABQ/sddefault.jpg",
            "https://i.ytimg.com/vi/oygrmJFKYZY/sddefault.jpg",
            "https://i.ytimg.com/vi/ZRtdQ81jPUQ/sddefault.jpg",
            "https://i.ytimg.com/vi/M2cckDmNLMI/sddefault.jpg",
            "https://i.ytimg.com/vi/mpjTE0qPqJc/sddefault.jpg",
            "https://i.ytimg.com/vi/Qp3b-RXtz4w/sddefault.jpg",
            "https://i.ytimg.com/vi/OPf0YbXqDm0/sddefault.jpg",
            "https://i.ytimg.com/vi/JGwWNGJdvx8/sddefault.jpg"
        ).shuffled()

        thumbnails = predefinedCovers
    }

    val infiniteBgTransition = rememberInfiniteTransition(label = "bg_morph")
    val morphProgress by infiniteBgTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg_morph_progress"
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (isHiding) 0f else 1f,
        animationSpec = tween(600),
        label = "bg_alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) {

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha }) {
            val center1 = Offset(size.width * 0.2f + (size.width * 0.6f * morphProgress), size.height * 0.3f)
            val center2 = Offset(size.width * 0.8f - (size.width * 0.4f * morphProgress), size.height * 0.7f + (size.height * 0.2f * morphProgress))

            drawRect(Color.Black)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0A1F13).copy(alpha = 0.5f), Color.Transparent),
                    center = center1,
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = center1
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF07170E).copy(alpha = 0.4f), Color.Transparent),
                    center = center2,
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = center2
            )
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (currentAnimation == CurrentAnimation.Disc) {
                DiscAnimation(isHiding = isHiding, onSequenceComplete = {
                    currentAnimation = CurrentAnimation.Grid
                })
            } else {
                GridAnimation(isHiding = isHiding, thumbnails = thumbnails, onSequenceComplete = {
                    currentAnimation = CurrentAnimation.Disc
                })
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color(0xFF121212).copy(alpha = 0.8f),
                        0.8f to Color(0xFF121212),
                        1f to Color(0xFF121212)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 175.dp, end = 32.dp)
        ) {
            Text(
                text = "Welcome to\nmusicfy!",
                fontSize = 42.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A way to listen and own your\nmusic, differently.",
                fontSize = 16.sp,
                color = Color(0xFFB3B3B3),
                lineHeight = 22.sp,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

@Composable
fun DiscAnimation(isHiding: Boolean, onSequenceComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteDisc")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DiscRotation"
    )

    val armShake by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArmShake"
    )

    var currentState by remember { mutableStateOf(SpawningState.Spawning) }
    var isInteracting by remember { mutableStateOf(false) }
    var playDuration by remember { mutableLongStateOf(5000L) }

    val discOffset = remember { Animatable(1f) }
    val armBaseRotation = remember { Animatable(-90f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isHiding) {
        if (isHiding && currentState != SpawningState.Hidden && currentState != SpawningState.Despawning) {
            currentState = SpawningState.Despawning
        }
    }

    LaunchedEffect(currentState, isInteracting) {
        if (isInteracting && !isHiding) return@LaunchedEffect

        when (currentState) {
            SpawningState.Hidden -> {
                discOffset.snapTo(1f)
                armBaseRotation.snapTo(-90f)

                if (!isHiding) {
                    onSequenceComplete()
                }
            }
            SpawningState.Spawning -> {

                coroutineScope.launch {
                    armBaseRotation.animateTo(-30f, tween(800, easing = FastOutSlowInEasing))
                }
                delay(400)

                coroutineScope.launch {
                    discOffset.animateTo(0.0f, spring(dampingRatio = 0.6f, stiffness = 100f))
                }

                delay(300)
                armBaseRotation.animateTo(-15f, spring(dampingRatio = 0.6f, stiffness = 200f))
                currentState = SpawningState.Playing
            }
            SpawningState.Playing -> {
                delay(playDuration)
                currentState = SpawningState.Despawning
            }
            SpawningState.Despawning -> {

                armBaseRotation.animateTo(-30f, tween(400, easing = FastOutSlowInEasing))

                coroutineScope.launch {
                    discOffset.animateTo(1.0f, spring(dampingRatio = 0.8f, stiffness = 60f))
                }
                delay(300)

                armBaseRotation.animateTo(-90f, tween(600, easing = FastOutSlowInEasing))
                delay(600)
                currentState = SpawningState.Hidden
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width + (discOffset.value * size.width * 1.5f)
            val cy = size.height * 0.3f - (discOffset.value * size.height * 0.6f)
            val discCenter = Offset(cx, cy)
            val maxRadius = size.width * 0.85f

            val discScale = 1.0f + (discOffset.value * 0.5f)

            scale(scale = discScale, pivot = discCenter) {
                rotate(rotation, pivot = discCenter) {

                    drawCircle(
                        color = Color(0xFF2A2A2A),
                        radius = maxRadius,
                        center = discCenter
                    )

                    drawCircle(
                        color = Color(0xFF242424),
                        radius = maxRadius * 0.75f,
                        center = discCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        color = Color(0xFF242424),
                        radius = maxRadius * 0.5f,
                        center = discCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        color = Color(0xFF333333),
                        radius = maxRadius * 0.35f,
                        center = discCenter
                    )

                    drawCircle(
                        color = Color(0xFF555555),
                        radius = maxRadius * 0.06f,
                        center = Offset(discCenter.x, discCenter.y - maxRadius * 0.2f)
                    )

                    drawCircle(
                        color = Color(0xFF444444),
                        radius = maxRadius * 0.12f,
                        center = discCenter,
                        style = Stroke(width = 4.dp.toPx())
                    )

                    drawCircle(
                        color = Color(0xFF121212),
                        radius = maxRadius * 0.08f,
                        center = discCenter
                    )
                }
            }

            val armBaseX = -size.width * 0.1f
            val armBaseY = size.height * 0.45f

            val activeShake = if (currentState == SpawningState.Playing && !isInteracting) armShake else 0f

            rotate(degrees = armBaseRotation.value + activeShake, pivot = Offset(armBaseX, armBaseY)) {
                val armLength = size.width * 0.8f
                val armEndX = armBaseX + armLength
                val armEndY = armBaseY

                drawLine(
                    color = Color(0xFF444444),
                    start = Offset(armBaseX, armBaseY),
                    end = Offset(armEndX, armEndY),
                    strokeWidth = 14.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val headWidth = 80.dp.toPx()
                val headHeight = 28.dp.toPx()

                drawRoundRect(
                    color = Color(0xFF666666),
                    topLeft = Offset(armEndX - headWidth / 2, armEndY - headHeight / 2),
                    size = Size(headWidth, headHeight),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
            }
        }

        val angleRad = armBaseRotation.value * kotlin.math.PI / 180.0
        val armLength = canvasWidth * 0.8f
        val armBaseX = -canvasWidth * 0.1f
        val armBaseY = canvasHeight * 0.45f
        val handleX = armBaseX + armLength * kotlin.math.cos(angleRad)
        val handleY = armBaseY + armLength * kotlin.math.sin(angleRad)

        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (handleX - 40.dp.toPx()).toInt(),
                        (handleY - 40.dp.toPx()).toInt()
                    )
                }
                .size(80.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (currentState != SpawningState.Hidden && !isHiding) {
                                isInteracting = true
                                currentState = SpawningState.Playing
                                playDuration = 3000L
                            }
                        },
                        onDragEnd = {
                            if (isInteracting) {
                                isInteracting = false
                                coroutineScope.launch {
                                    armBaseRotation.animateTo(-15f, spring(dampingRatio = 0.6f, stiffness = 200f))
                                }
                            }
                        },
                        onDragCancel = {
                            if (isInteracting) {
                                isInteracting = false
                                coroutineScope.launch {
                                    armBaseRotation.animateTo(-15f, spring(dampingRatio = 0.6f, stiffness = 200f))
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (isInteracting) {
                                change.consume()
                                val rotationDelta = (dragAmount.y / canvasHeight) * 80f
                                val newRotation = (armBaseRotation.value + rotationDelta).coerceIn(-45f, 10f)
                                coroutineScope.launch {
                                    armBaseRotation.snapTo(newRotation)
                                }
                            }
                        }
                    )
                }
        )
    }
}
@Composable
fun GridAnimation(isHiding: Boolean, thumbnails: List<String>, onSequenceComplete: () -> Unit) {
    val itemSize = 170.dp
    val spacing = 14.dp

    val density = LocalDensity.current
    val pxItemSize = remember(density) { with(density) { itemSize.toPx() } }
    val pxSpacing = remember(density) { with(density) { spacing.toPx() } }

    val panOffset = remember { Animatable(0f) }

    val scales = remember(thumbnails.size) { List(thumbnails.size) { Animatable(0f) } }
    var isForceHiding by remember { mutableStateOf(false) }

    LaunchedEffect(isHiding) {
        if (isHiding && !isForceHiding) {
            isForceHiding = true
            val despawnJobs = thumbnails.mapIndexed { index, _ ->
                launch {
                    val col = index / 3
                    delay(col * 20L)
                    scales[index].animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f))
                }
            }
            despawnJobs.forEach { it.join() }
        }
    }

    LaunchedEffect(thumbnails.size) {
        if (thumbnails.isEmpty() || isForceHiding) return@LaunchedEffect

        val totalCols = (thumbnails.size + 2) / 3
        val contentWidth = totalCols * (pxItemSize + pxSpacing)

        launch {
            panOffset.animateTo(-contentWidth * 0.4f, tween(7000, easing = LinearEasing))
        }

        thumbnails.forEachIndexed { index, _ ->
            launch {
                val col = index / 3
                delay(col * 120L + (index % 3) * 60L)
                scales[index].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 150f))
            }
        }

        delay(6000)

        val despawnJobs = thumbnails.mapIndexed { index, _ ->
            launch {
                val col = index / 3
                delay(col * 40L)
                scales[index].animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f))
            }
        }
        despawnJobs.forEach { it.join() }
        delay(500)
        onSequenceComplete()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val startY = -pxItemSize * 0.25f

        thumbnails.forEachIndexed { index, url ->
            val col = index / 3
            val row = index % 3

            val rowOffset = if (row % 2 != 0) (pxItemSize + pxSpacing) * 0.5f else 0f

            val xPos = 60f + col * (pxItemSize + pxSpacing) + rowOffset
            val yPos = startY + row * (pxItemSize + pxSpacing)

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = xPos + panOffset.value
                        translationY = yPos
                        scaleX = scales[index].value
                        scaleY = scales[index].value
                        transformOrigin = TransformOrigin.Center
                        alpha = scales[index].value.coerceIn(0f, 1f)
                    }
                    .size(itemSize)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF242424))
            ) {
                if (url.isNotEmpty()) {

                    FadeInCover(url = url, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
