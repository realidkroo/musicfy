// SeamBlur.kt

package com.example.musicfy.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SeamBlur(
    glassState: GlassState,
    progressProvider: () -> Float,
    trackInfo: TrackInfo,
    maxHeight: Dp,

    fadeProvider: () -> Float = { 1f },
) {
    if (trackInfo.thumbnailUrl == null) return

    val shouldExist by remember { derivedStateOf { progressProvider() > 0f } }
    if (!shouldExist) return

    val context = LocalContext.current

    val bandTop = maxHeight * 0.52f
    val bandBottom = maxHeight * 0.74f

    var scrimColor by remember(trackInfo.mediaId) { mutableStateOf(Color.Black) }
    LaunchedEffect(trackInfo.mediaId, trackInfo.thumbnailUrl) {
        val url = trackInfo.thumbnailUrl ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
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
                        fallbackColor = android.graphics.Color.BLACK,
                    )
                    scrimColor = colors.getOrNull(1) ?: Color.Black
                }
            } catch (_: Exception) {
            }
        }
    }

    // The band is invisible (alpha 0) for the first half of every open and close, and whenever a
    // page is up - but its three layers (the fade, the masked blur and the blur itself) were still
    // drawn then, blur passes and all. Only draw it while it can show. The colour above stays
    // composed either way, so it isn't extracted again each time the band comes back.
    val drawn by remember { derivedStateOf { progressProvider() > 0.5f && fadeProvider() > 0f } }
    if (!drawn) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bandBottom - bandTop)
            .offset(y = bandTop)
            .graphicsLayer {

                val p = progressProvider()
                alpha = ((p - 0.5f) / 0.45f).coerceIn(0f, 1f) * fadeProvider().coerceIn(0f, 1f)
            }
    ) {
        GlassPillBackground(
            state = glassState,
            blurRadius = { 130f },
            tileMode = android.graphics.Shader.TileMode.CLAMP,
            // Faded top and bottom by the blur effect itself, rather than by DstIn-ing a gradient
            // over the glass in an offscreen layer of its own.
            mask = SeamMask,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bandBottom - bandTop)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to scrimColor.copy(alpha = 0.38f),
                        1f to Color.Transparent,
                    )
                )
        )
    }
}

private val SeamMask = Brush.verticalGradient(
    0f to Color.Transparent,
    0.25f to Color.Black,
    0.8f to Color.Black,
    1f to Color.Transparent,
)
