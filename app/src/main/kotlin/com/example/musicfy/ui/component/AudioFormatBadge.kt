package com.example.musicfy.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.musicfy.R
import com.example.musicfy.db.entities.FormatEntity

@Composable
fun AudioFormatBadge(
    format: FormatEntity?,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier
) {
    if (format == null) return

    val mimeType = format.mimeType.lowercase()
    val codecs = format.codecs.lowercase()
    
    val iconRes = when {
        mimeType.contains("flac") || mimeType.contains("alac") -> {
            if (format.sampleRate != null && format.sampleRate >= 48000) R.drawable.hi_res_flac
            else R.drawable.flac
        }
        mimeType.contains("mp4") || mimeType.contains("aac") || codecs.contains("aac") -> R.drawable.aac
        mimeType.contains("mp3") || mimeType.contains("mpeg") -> R.drawable.low_res_mp3
        codecs.contains("ac-3") || codecs.contains("ec-3") -> R.drawable.dolby
        else -> null
    }

    if (iconRes != null) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Audio Format",
            colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
            modifier = modifier.height(height)
        )
    }
}
