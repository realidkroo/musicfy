package com.example.musicfy.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

@Composable
fun ImportProviderStep() {
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
                .background(Color(0xFF6C5CE7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.lib_outline),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Import data from other music provider",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 28.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Clicking continue will bring you how to import your music profile, playlist and album data from other music provider! this service is provided by third party and you must have existing account and may need an active subscription to that app ( ex. Apple music )",
            fontSize = 15.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 21.sp
        )
    }
}
