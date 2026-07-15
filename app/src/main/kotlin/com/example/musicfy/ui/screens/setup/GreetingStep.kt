package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun GreetingStep(
    username: String,
    profilePicUri: Uri?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = 120.dp), // Adjust bottom padding for the next button
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Profile Picture Placeholder (visuals drawn by SetupWizardScreen overlay for smooth morphing)
        Spacer(
            modifier = Modifier.size(110.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "hi, ${username.lowercase()}!",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-2).sp,
            lineHeight = 48.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Welcome to musicfy!",
            fontSize = 16.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 22.sp,
            letterSpacing = (-0.5).sp
        )
    }
}
