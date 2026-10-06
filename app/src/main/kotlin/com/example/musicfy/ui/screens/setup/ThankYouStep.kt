package com.example.musicfy.ui.screens.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import kotlinx.coroutines.delay

@Composable
fun ThankYouStep() {
    var showCard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showCard = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Thank you",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-2).sp,
            lineHeight = 48.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "for using musicfy!",
            fontSize = 16.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 22.sp,
            letterSpacing = (-0.5).sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showCard,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 600)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF222222))
            ) {

                Text(
                    text = "MUSICFYIT",
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF333333),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            rotationZ = -90f
                            translationX = 130.dp.toPx()
                        },
                    letterSpacing = 2.sp
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Made with <3 by roo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF757575))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.frame_51_3),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "v6.7.5b",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "This app still in beta!",
                        fontSize = 14.sp,
                        color = Color(0xFFB3B3B3),
                        lineHeight = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        }
        }
    }
}
