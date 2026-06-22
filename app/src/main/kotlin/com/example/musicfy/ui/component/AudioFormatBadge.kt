// AudioFormatBadge.kt
// this thing is for audio format badge

package com.example.musicfy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.musicfy.R
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.EnableSpatialAudioKey
import com.example.musicfy.db.entities.FormatEntity
import com.example.musicfy.utils.rememberPreference

@Composable
fun AudioFormatBadge(
    format: FormatEntity?,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    audioQuality: AudioQuality = AudioQuality.AUTO,
    fallbackId: String? = null
) {
    val mimeType = format?.mimeType?.lowercase() ?: ""
    val codecs = format?.codecs?.lowercase() ?: ""
    val ext = fallbackId?.substringAfterLast('.')?.lowercase() ?: ""
    
    val (spatialAudio) = rememberPreference(EnableSpatialAudioKey, defaultValue = false)

    val iconRes = when {
        mimeType.contains("flac") || mimeType.contains("alac") || ext == "flac" || ext == "alac" -> {
            val sampleRate = format?.sampleRate ?: 0
            if (sampleRate >= 48000) R.drawable.hi_res_flac else R.drawable.flac
        }
        mimeType.contains("mp4") || mimeType.contains("aac") || codecs.contains("aac") || ext == "m4a" || ext == "aac" -> R.drawable.aac
        codecs.contains("opus") || mimeType.contains("webm") || ext == "webm" || ext == "opus" -> R.drawable.low_res_mp3
        mimeType.contains("mp3") || mimeType.contains("mpeg") || ext == "mp3" -> R.drawable.low_res_mp3
        codecs.contains("ac-3") || codecs.contains("ec-3") -> R.drawable.dolby
        else -> R.drawable.low_res_mp3
    }
    val contentColor = if (tint == Color.Unspecified) Color.White else tint
    val badgeWidth = when (iconRes) {
        R.drawable.low_res_mp3 -> height * (107f / 34f)
        R.drawable.aac -> height * (71f / 34f)
        else -> height * 2.1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.requiredSize(width = badgeWidth, height = height)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Audio Format",
            colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(contentColor) else null,
            modifier = Modifier.requiredSize(width = badgeWidth, height = height)
        )
    }
}
