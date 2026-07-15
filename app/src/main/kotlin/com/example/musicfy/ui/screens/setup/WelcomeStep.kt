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
import com.example.musicfy.ui.component.GlassState
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
    
    // Preloaded classic and popular song thumbnails to prevent lag on launch
    LaunchedEffect(Unit) {
        val predefinedCovers = listOf(
            "https://i.ytimg.com/vi/Zi_XLOBDo_Y/sddefault.jpg", // Michael Jackson - Billie Jean
            "https://i.ytimg.com/vi/fJ9rUzIMcZQ/sddefault.jpg", // Queen - Bohemian Rhapsody
            "https://i.ytimg.com/vi/hTWKbfoikeg/sddefault.jpg", // Nirvana - Smells Like Teen Spirit
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg", // Rick Astley
            "https://i.ytimg.com/vi/1w7OgIMMRc4/sddefault.jpg", // Guns N' Roses
            "https://i.ytimg.com/vi/A_MjCqQoLLA/sddefault.jpg", // The Beatles
            "https://i.ytimg.com/vi/Gs069dndIYk/sddefault.jpg", // Earth, Wind & Fire
            "https://i.ytimg.com/vi/4fndeDfaWCg/sddefault.jpg", // Backstreet Boys
            "https://i.ytimg.com/vi/PWgvGjAhvIw/sddefault.jpg", // Outkast
            "https://i.ytimg.com/vi/_Yhyp-_hX2s/sddefault.jpg", // Eminem
            "https://i.ytimg.com/vi/kXYiU_JCYtU/sddefault.jpg", // Linkin Park
            "https://i.ytimg.com/vi/yKNxeF4KMsY/sddefault.jpg", // Coldplay
            "https://i.ytimg.com/vi/rYEDA3JcQqw/sddefault.jpg", // Adele
            "https://i.ytimg.com/vi/4NRXx6U8ABQ/sddefault.jpg", // The Weeknd
            "https://i.ytimg.com/vi/oygrmJFKYZY/sddefault.jpg", // Dua Lipa
            "https://i.ytimg.com/vi/ZRtdQ81jPUQ/sddefault.jpg", // YOASOBI (J-Pop)
            "https://i.ytimg.com/vi/M2cckDmNLMI/sddefault.jpg", // Kenshi Yonezu (J-Pop)
            "https://i.ytimg.com/vi/mpjTE0qPqJc/sddefault.jpg", // LiSA (J-Pop)
            "https://i.ytimg.com/vi/Qp3b-RXtz4w/sddefault.jpg", // Ado (J-Pop)
            "https://i.ytimg.com/vi/OPf0YbXqDm0/sddefault.jpg", // Mark Ronson
            "https://i.ytimg.com/vi/JGwWNGJdvx8/sddefault.jpg"  // Ed Sheeran
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
        // Morphing Dark Green/Black Gradient Background
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = bgAlpha }) {
            val center1 = Offset(size.width * 0.2f + (size.width * 0.6f * morphProgress), size.height * 0.3f)
            val center2 = Offset(size.width * 0.8f - (size.width * 0.4f * morphProgress), size.height * 0.7f + (size.height * 0.2f * morphProgress))
            
            // Draw a base pure black
            drawRect(Color.Black)
            
            // Draw very subtle radial gradients for the glowing morph effect
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

        // The Animation covering the upper right area
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
        // Progressive Blur overlay at the bottom covering the text area
        ProgressiveGlassBackground(
            state = glassState,
            maxBlurRadius = { 120f },
            foundationColor = Color(0xFF121212),
            tint = Color.Transparent,
            direction = BlurDirection.TopToBottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
        )
        
        // The Text at bottom left - shifted up to avoid next button overlap
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

    // Gentle shake for the arm
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
    
    val discOffset = remember { Animatable(1f) } // 1f = hidden (top right), 0f = visible
    val armBaseRotation = remember { Animatable(-90f) } // -90f = hidden, -15f = playing
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
                // Trigger transition to GridAnimation instead of spawning again
                if (!isHiding) {
                    onSequenceComplete()
                }
            }
            SpawningState.Spawning -> {
                // Tail appears first from vertical to partially horizontal
                coroutineScope.launch {
                    armBaseRotation.animateTo(-30f, tween(800, easing = FastOutSlowInEasing))
                }
                delay(400) // slight overlap
                
                // Disc slides in
                coroutineScope.launch {
                    discOffset.animateTo(0.0f, spring(dampingRatio = 0.6f, stiffness = 100f))
                }
                
                // Tail goes to playing position
                delay(300)
                armBaseRotation.animateTo(-15f, spring(dampingRatio = 0.6f, stiffness = 200f))
                currentState = SpawningState.Playing
            }
            SpawningState.Playing -> {
                delay(playDuration)
                currentState = SpawningState.Despawning
            }
            SpawningState.Despawning -> {
                // Arm lifts up
                armBaseRotation.animateTo(-30f, tween(400, easing = FastOutSlowInEasing))
                
                // Disc slides out
                coroutineScope.launch {
                    discOffset.animateTo(1.0f, spring(dampingRatio = 0.8f, stiffness = 60f))
                }
                delay(300)
                // Arm retracts fully
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
            val cx = size.width + (discOffset.value * size.width * 1.5f) // increased to 1.5f so it stays hidden when scaled
            val cy = size.height * 0.3f - (discOffset.value * size.height * 0.6f)
            val discCenter = Offset(cx, cy)
            val maxRadius = size.width * 0.85f
            
            // Depth effect: scale up the disc as it moves away
            val discScale = 1.0f + (discOffset.value * 0.5f)
            
            scale(scale = discScale, pivot = discCenter) {
                rotate(rotation, pivot = discCenter) {
                    // Main disc
                    drawCircle(
                        color = Color(0xFF2A2A2A),
                        radius = maxRadius,
                        center = discCenter
                    )
                    // Inner groove 1
                    drawCircle(
                        color = Color(0xFF242424),
                        radius = maxRadius * 0.75f,
                        center = discCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Inner groove 2
                    drawCircle(
                        color = Color(0xFF242424),
                        radius = maxRadius * 0.5f,
                        center = discCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Label area
                    drawCircle(
                        color = Color(0xFF333333),
                        radius = maxRadius * 0.35f,
                        center = discCenter
                    )
                    // Logo mark on the label to show rotation
                    drawCircle(
                        color = Color(0xFF555555),
                        radius = maxRadius * 0.06f,
                        center = Offset(discCenter.x, discCenter.y - maxRadius * 0.2f)
                    )
                    // Ring around hole
                    drawCircle(
                        color = Color(0xFF444444),
                        radius = maxRadius * 0.12f,
                        center = discCenter,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    // Center hole (background color)
                    drawCircle(
                        color = Color(0xFF121212),
                        radius = maxRadius * 0.08f,
                        center = discCenter
                    )
                }
            }
            
            // Reader Arm
            val armBaseX = -size.width * 0.1f
            val armBaseY = size.height * 0.45f
            
            val activeShake = if (currentState == SpawningState.Playing && !isInteracting) armShake else 0f
            
            rotate(degrees = armBaseRotation.value + activeShake, pivot = Offset(armBaseX, armBaseY)) {
                val armLength = size.width * 0.8f
                val armEndX = armBaseX + armLength
                val armEndY = armBaseY
                
                // Draw arm line
                drawLine(
                    color = Color(0xFF444444),
                    start = Offset(armBaseX, armBaseY),
                    end = Offset(armEndX, armEndY),
                    strokeWidth = 14.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Draw stylus head (pill shape)
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
        
        // Touch target overlay
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
    // Initialize animatables safely
    val scales = remember(thumbnails.size) { List(thumbnails.size) { Animatable(0f) } }
    var isForceHiding by remember { mutableStateOf(false) }

    LaunchedEffect(isHiding) {
        if (isHiding && !isForceHiding) {
            isForceHiding = true
            val despawnJobs = thumbnails.mapIndexed { index, _ ->
                launch {
                    val col = index / 3
                    delay(col * 20L) // faster hide when force hiding
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
        
        // Pan leftwards slowly to give a scanning effect
        launch {
            panOffset.animateTo(-contentWidth * 0.4f, tween(7000, easing = LinearEasing))
        }
        
        // Stagger spawn based on column index
        thumbnails.forEachIndexed { index, _ ->
            launch {
                val col = index / 3
                delay(col * 120L + (index % 3) * 60L) 
                scales[index].animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 150f))
            }
        }
        
        delay(6000) // Play for 6 seconds
        
        // Despawn sequence
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
        // Start drawing higher up so top row clips slightly more
        val startY = -pxItemSize * 0.25f
        
        thumbnails.forEachIndexed { index, url ->
            val col = index / 3
            val row = index % 3
            
            // Offset rows slightly to right for a masonry look
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
                    .background(Color(0xFF242424)) // Fallback/loading color
            ) {
                if (url.isNotEmpty()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
