package com.example.musicfy.ui.screens.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

const val TUNE_MY_MUSIC_URL = "https://www.tunemymusic.com"

@Composable
fun TuneMyMusicInstructionsStep() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = 100.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Image(
            painter = painterResource(R.drawable.tune_my_music),
            contentDescription = "Open tunemymusic.com",
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable { uriHandler.openUri(TUNE_MY_MUSIC_URL) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "To Import your music",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "1. You want to visit tunemymusic.com.",
            fontSize = 15.sp,
            color = Color(0xFFE0E0E0),
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .clickable { uriHandler.openUri(TUNE_MY_MUSIC_URL) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.tune_my_music),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Tune My Music",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "A free third-party service that transfers your music library and playlists between streaming platforms.",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "2. Follow the instruction on Tune My music. you need to choose your music provider that you want to import here\n\n" +
                "3. You may need to login into your account. a subscription may needed. or you can choose any playlist then paste the url there, if available.\n\n" +
                "4. Select what you want to import\n\n" +
                "5. Important. Scroll down a bit, select Export to file, then choose CSV",
            fontSize = 15.sp,
            color = Color(0xFFE0E0E0),
            lineHeight = 21.sp
        )
    }
}
