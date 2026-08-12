// detailcoverbackgroundkt
// shared background for the rebuilt album/playlist/liked-songs detail
// vertical gradient tinted with the color sampled from the cover art fading
// into the app's normal background color same coil-bitmap -> palette ->
// playercolorextractor pipeline albumgradientkt already uses (see that file
// ui/theme/playercolorextractorkt) — reused directly rather than
// just applied full-bleed behind a whole screen instead of one small grid

package com.example.musicfy.ui.component.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailCoverBackground(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onColorExtracted: (Color) -> Unit = {},
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val isDarkTheme = backgroundColor.luminance() < 0.5f

    var extractedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUrl, isDarkTheme) {
        extractedColor = null
        if (thumbnailUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(8)
                            .resizeBitmapArea(100 * 100)
                            .generate()
                    }
                    val colors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColorInt,
                    )
                    extractedColor = colors.firstOrNull()?.let {
                        PlayerColorExtractor.darkenIfTooLight(it, isDarkTheme = isDarkTheme)
                    }
                    extractedColor?.let { onColorExtracted(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = extractedColor ?: backgroundColor,
        animationSpec = tween(durationMillis = 600),
        label = "DetailCoverBackgroundColor",
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to animatedColor,
                0.35f to animatedColor.copy(alpha = 0.55f),
                0.65f to animatedColor.copy(alpha = 0.18f),
                1f to backgroundColor,
            )
        )
    )
}
