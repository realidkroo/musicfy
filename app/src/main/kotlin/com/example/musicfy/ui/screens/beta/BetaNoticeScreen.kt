package com.example.musicfy.ui.screens.beta

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun BetaNoticeScreen(onDismiss: (Boolean) -> Unit) {
    var doNotShowAgain by remember { mutableStateOf(false) }
    var cooldownProgress by remember { mutableFloatStateOf(0f) }
    var isCooldownFinished by remember { mutableStateOf(false) }

    // 5-second cooldown timer
    LaunchedEffect(Unit) {
        val totalTime = 5000L
        val updateInterval = 16L // ~60fps
        var elapsedTime = 0L
        while (elapsedTime < totalTime) {
            delay(updateInterval)
            elapsedTime += updateInterval
            cooldownProgress = (elapsedTime.toFloat() / totalTime).coerceIn(0f, 1f)
        }
        cooldownProgress = 1f
        isCooldownFinished = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Color(0xFF161616)) // Dark gray/black surface
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .width(64.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 56.dp, start = 32.dp, end = 32.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Hello!",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A bit notice you are on version",
                fontSize = 16.sp,
                color = Color(0xFFB3B3B3),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Version Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "Musicfy Beta",
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1.0b",
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "as you can see you are on beta version. please expect some bugs, stuttering. and any feedback is appreciated!",
                fontSize = 15.sp,
                color = Color(0xFFE0E0E0),
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            // Checkbox area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { doNotShowAgain = !doNotShowAgain }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Checkbox
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (doNotShowAgain) Color.White else Color(0xFF666666),
                            shape = CircleShape
                        )
                        .background(if (doNotShowAgain) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (doNotShowAgain) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "i know! Do not show me this ever again!",
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Cooldown / OK Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)) // Base background
                    .clickable(enabled = isCooldownFinished) {
                        if (isCooldownFinished) {
                            onDismiss(doNotShowAgain)
                        }
                    }
            ) {
                // Progress Bar Background
                val animatedProgress by animateFloatAsState(
                    targetValue = cooldownProgress,
                    animationSpec = tween(durationMillis = 16)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .background(Color(0xFF444444)) // Slightly lighter indicating progress
                )

                // Text
                Text(
                    text = "OK",
                    color = if (isCooldownFinished) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
