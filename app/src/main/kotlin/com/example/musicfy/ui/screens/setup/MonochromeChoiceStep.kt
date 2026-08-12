package com.example.musicfy.ui.screens.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

// setup step asking whether to turn on the monochrome backend the answer is given
@Composable
fun MonochromeChoiceStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.monochrome_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Enable monochrome backend",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Monochrome lets you stream Hi-Res FLAC instead of the regular YouTube audio. It's experimental — their servers can be slow or unreachable, so you can always turn it on later in settings.",
            fontSize = 15.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 21.sp
        )
    }
}
