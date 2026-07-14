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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.BlurDirection
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class WelcomeAnimState {
    Cassette, Albums, Blank
}

@Composable
fun WelcomeStep() {
    val glassState = remember { GlassState() }

    Box(
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) {
        // The Disc Animation covering the upper right area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState)
        ) {
            DiscAnimation()
        }
        
        // Progressive Blur overlay at the bottom covering the text area
        ProgressiveGlassBackground(
            state = glassState,
            maxBlurRadius = 120f,
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
                .padding(start = 32.dp, bottom = 145.dp, end = 32.dp)
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
fun DiscAnimation() {
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
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Disc Center on the right edge, slightly above middle
        val cx = size.width
        val cy = size.height * 0.3f
        val discCenter = Offset(cx, cy)
        val maxRadius = size.width * 0.85f
        
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
        
        // Reader Arm
        // Pivot offscreen left
        val armBaseX = -size.width * 0.1f
        val armBaseY = size.height * 0.45f
        
        // Base rotation + shake
        rotate(degrees = -15f + armShake, pivot = Offset(armBaseX, armBaseY)) {
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
}
