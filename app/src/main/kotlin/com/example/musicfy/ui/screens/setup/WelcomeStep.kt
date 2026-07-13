package com.example.musicfy.ui.screens.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class WelcomeAnimState {
    Cassette, Albums, Blank
}

@Composable
fun WelcomeStep() {
    var animState by remember { mutableStateOf(WelcomeAnimState.Cassette) }

    LaunchedEffect(Unit) {
        while (true) {
            animState = WelcomeAnimState.Cassette
            delay(5000)
            animState = WelcomeAnimState.Blank
            delay(500)
            animState = WelcomeAnimState.Albums
            delay(5000)
            animState = WelcomeAnimState.Blank
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Text(
            text = "Welcome to musicfy!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            when (animState) {
                WelcomeAnimState.Cassette -> CassetteAnimation()
                WelcomeAnimState.Albums -> AlbumsAnimation()
                WelcomeAnimState.Blank -> { /* Empty state */ }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun CassetteAnimation() {
    // Entrance animations
    val enterTransition = updateTransition(targetState = true, label = "Enter")
    val handleOffsetX by enterTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 100f) },
        label = "HandleOffsetX"
    ) { if (it) 0f else -500f }
    val cassetteOffsetX by enterTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 100f) },
        label = "CassetteOffsetX"
    ) { if (it) 0f else 500f }

    // Continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "Infinite")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Oscillation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        
        // Oscillation offsets
        val floatY = sin(time) * 10f
        val floatX = sin(time * 0.5f) * 5f
        
        // Draw Handle
        translate(left = handleOffsetX + floatX, top = floatY) {
            val handleStartX = cx - 120.dp.toPx()
            val handleStartY = cy - 20.dp.toPx()
            val handleEndX = cx - 20.dp.toPx()
            val handleEndY = cy
            
            drawLine(
                color = Color.DarkGray,
                start = Offset(handleStartX, handleStartY),
                end = Offset(handleEndX, handleEndY),
                strokeWidth = 12.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Handle head
            drawCircle(
                color = Color.LightGray,
                radius = 16.dp.toPx(),
                center = Offset(handleEndX, handleEndY)
            )
        }

        // Draw Cassette/Record
        translate(left = cassetteOffsetX + floatX, top = floatY) {
            val cassetteCenter = Offset(cx + 60.dp.toPx(), cy)
            
            rotate(rotation, pivot = cassetteCenter) {
                // Outer ring
                drawCircle(
                    color = Color.DarkGray,
                    radius = 80.dp.toPx(),
                    center = cassetteCenter,
                    style = Stroke(width = 16.dp.toPx())
                )
                // Middle fill
                drawCircle(
                    color = Color(0xFF222222),
                    radius = 72.dp.toPx(),
                    center = cassetteCenter
                )
                // Inner ring
                drawCircle(
                    color = Color.Gray,
                    radius = 30.dp.toPx(),
                    center = cassetteCenter,
                    style = Stroke(width = 4.dp.toPx())
                )
                // Center hole
                drawCircle(
                    color = Color.Black,
                    radius = 10.dp.toPx(),
                    center = cassetteCenter
                )
            }
        }
    }
}

@Composable
fun AlbumsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "InfiniteAlbums")
    
    // Scale animation starts small, then grows
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )
    
    // Scroll animation moves left infinitely
    val scrollX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -300.dp.value, // Scroll by roughly the width of one album + spacing
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScrollX"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cy = size.height / 2
        val albumSize = 120.dp.toPx() * scale
        val spacing = 40.dp.toPx()
        val startX = scrollX.dp.toPx()
        
        // Draw 5 squares to create an infinite scrolling effect
        for (i in 0..4) {
            val cx = startX + (i * (albumSize + spacing))
            
            val rectStart = Offset(cx, cy - albumSize / 2)
            
            drawRoundRect(
                color = Color.DarkGray,
                topLeft = rectStart,
                size = Size(albumSize, albumSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
            )
            
            // Draw an inner square for details
            drawRoundRect(
                color = Color.Gray,
                topLeft = Offset(rectStart.x + 16.dp.toPx(), rectStart.y + 16.dp.toPx()),
                size = Size(albumSize - 32.dp.toPx(), albumSize - 32.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
        }
    }
}
