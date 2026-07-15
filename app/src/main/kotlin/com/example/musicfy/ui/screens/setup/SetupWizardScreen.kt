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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SetupWizardScreen(
    onComplete: (String, Uri?) -> Unit,
    onDrag: (Float) -> Unit,
    onDragRelease: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    
    var username by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUncroppedUri by remember { mutableStateOf<Uri?>(null) }
    var isLeavingWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == 0) {
            isLeavingWelcome = false
        }
        if (pagerState.settledPage == 2) {
            kotlinx.coroutines.delay(3000)
            pagerState.animateScrollToPage(3)
        }
    }
    
    BackHandler {
        if (selectedUncroppedUri != null) {
            selectedUncroppedUri = null
        } else if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }
    
    PhotoCropperContainer(
        uri = selectedUncroppedUri,
        onDone = { croppedUri ->
            profilePicUri = croppedUri
            selectedUncroppedUri = null
        },
        onCancel = {
            selectedUncroppedUri = null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = {
                            onDragRelease()
                        },
                        onDragCancel = {
                            onDragRelease()
                        }
                    )
                }
                .padding(top = 48.dp) // Leave space at the top so the scaled background is visible
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFF121212)) // Dark gray/black surface
        ) {
            // Pager Content (fill screen first, drawing behind overlays)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false, // Must use buttons to navigate
                modifier = Modifier.fillMaxSize()
            ) { page ->
                // Pass content padding to non-welcome pages so they don't overlap with the top drag handle
                val pageModifier = Modifier.fillMaxSize().padding(top = 56.dp)
                when (page) {
                    0 -> WelcomeStep(isHiding = isLeavingWelcome)
                    1 -> Box(pageModifier) {
                        ProfileSetupStep(
                            username = username,
                            onUsernameChange = { username = it },
                            profilePicUri = profilePicUri,
                            onProfilePicChange = { selectedUncroppedUri = it }
                        )
                    }
                    2 -> Box(pageModifier) {
                        GreetingStep(username = username, profilePicUri = profilePicUri)
                    }
                    3 -> Box(pageModifier) {
                        ThankYouStep()
                    }
                }
            }
            
            // Morphing Profile Picture Overlay
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                val screenWidth = maxWidth
                
                // Calculate the exact floating scroll position
                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                
                // Show overlay between page 0 (transitioning out) and page 3 (transitioning in)
                if (pageOffset > 0f && pageOffset < 3f) {
                    val progress = (pageOffset - 1f).coerceIn(0f, 1f)
                    
                    val page1Y = 242.dp
                    val page1X = 32.dp // Fixed: left aligned to match hitbox
                    val page1Size = 140.dp
                    
                    val page2Y = screenHeight - 333.dp
                    val page2X = 32.dp
                    val page2Size = 110.dp
                    
                    val currentY = androidx.compose.ui.unit.lerp(page1Y, page2Y, progress)
                    val currentSize = androidx.compose.ui.unit.lerp(page1Size, page2Size, progress)
                    
                    var currentX = androidx.compose.ui.unit.lerp(page1X, page2X, progress)
                    
                    // Make it slide in with Page 1 when coming from Welcome screen
                    if (pageOffset < 1f) {
                        currentX += (screenWidth * (1f - pageOffset))
                    } 
                    // Make it slide out with Page 2 when going to Thank You screen
                    else if (pageOffset > 2f) {
                        currentX -= (screenWidth * (pageOffset - 2f))
                    }
                    
                    Box(
                        modifier = Modifier
                            .offset(x = currentX, y = currentY)
                            .size(currentSize)
                            .clip(CircleShape)
                            .background(Color(0xFF707070)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUri != null) {
                            coil3.compose.AsyncImage(
                                model = profilePicUri,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (progress == 0f) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Picture",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
    
            // Drag Handle overlaid on top
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
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }
        
        // Bottom Action Bar overlaid on top
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                    shape = CircleShape
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
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
                    shape = CircleShape
                ) {
                    Text(
                        "Next", 
                        color = if (username.isNotBlank()) Color.White else Color.Gray, 
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (pagerState.currentPage == 0) {
                Button(
                    onClick = {
                        isLeavingWelcome = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(400)
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    shape = CircleShape
                ) {
                    Text("Next", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}
