package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun SetupFurtherStep(profilePicUri: Uri?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF707070)),
            contentAlignment = Alignment.Center
        ) {
            if (profilePicUri != null) {
                AsyncImage(
                    model = profilePicUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Would you like to setup the app further?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 30.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This is optional, skipping will bring you to the app.",
            fontSize = 15.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 20.sp
        )
    }
}
