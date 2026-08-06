package com.example.musicfy.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.ParsedImport

@Composable
fun ReviewImportStep(parsed: ParsedImport) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = 100.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E))
                .clickable { uriHandler.openUri(TUNE_MY_MUSIC_URL) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.lib_outline),
                contentDescription = "Open tunemymusic.com",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Double check again.",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = buildString {
                append("${parsed.totalSongs} song${if (parsed.totalSongs == 1) "" else "s"} found")
                if (parsed.totalPlaylists > 0) {
                    append(" across ${parsed.totalPlaylists} playlist${if (parsed.totalPlaylists == 1) "" else "s"}")
                }
                if (parsed.likedSongs.isNotEmpty()) {
                    append(", plus ${parsed.likedSongs.size} liked")
                }
            },
            fontSize = 14.sp,
            color = Color(0xFFB3B3B3)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (parsed.likedSongs.isNotEmpty()) {
            ReviewCard(name = "Liked Songs", tracks = parsed.likedSongs)
            Spacer(modifier = Modifier.height(10.dp))
        }
        parsed.playlists.forEach { (name, tracks) ->
            ReviewCard(name = name, tracks = tracks)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReviewCard(name: String, tracks: List<ImportedTrack>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${tracks.size} song${if (tracks.size == 1) "" else "s"}",
            fontSize = 13.sp,
            color = Color(0xFF999999)
        )
        if (tracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            tracks.take(3).forEach { track ->
                Text(
                    text = "${track.title} — ${track.artist}",
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (tracks.size > 3) {
                Text(
                    text = "and ${tracks.size - 3} more…",
                    fontSize = 13.sp,
                    color = Color(0xFF777777),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
