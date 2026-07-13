package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(onComplete: (String, Uri?) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    
    var username by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) }
    
    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp) // Leave space at the top so the scaled background is visible
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Color(0xFF121212)) // Dark gray/black surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            
            // Pager Content
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false, // Must use buttons to navigate
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomeStep()
                    1 -> ProfileSetupStep(
                        username = username,
                        onUsernameChange = { username = it },
                        profilePicUri = profilePicUri,
                        onProfilePicChange = { profilePicUri = it }
                    )
                    2 -> GreetingStep(username = username, profilePicUri = profilePicUri)
                    3 -> ThankYouStep()
                }
            }
            
            // Bottom Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                if (pagerState.currentPage == 3) {
                    Button(
                        onClick = { onComplete(username, profilePicUri) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (pagerState.currentPage == 2) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(3)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Next", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (pagerState.currentPage == 1) {
                    Button(
                        onClick = {
                            if (username.isNotBlank()) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        },
                        enabled = username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333),
                            disabledContainerColor = Color(0xFF222222)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Next", 
                            color = if (username.isNotBlank()) Color.White else Color.Gray, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Next", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
