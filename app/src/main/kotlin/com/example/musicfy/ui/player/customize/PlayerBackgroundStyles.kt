// PlayerBackgroundStyles.kt

package com.example.musicfy.ui.player.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerBackgroundContent(
    style: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PlayerBackgroundStyle.COVER_GRADIENT -> Unit
        PlayerBackgroundStyle.SOLID -> SolidBackground(pureBlack = pureBlack, modifier = modifier)
        PlayerBackgroundStyle.DARK_GRADIENT -> DarkGradientBackground(modifier = modifier)
        PlayerBackgroundStyle.APPLE_MUSIC -> AppleMusicBackground(
            thumbnailUrl = thumbnailUrl,
            modifier = modifier,
        )
    }
}

@Composable
fun PlayerBackgroundPreview(
    style: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,

    warp: Boolean = true,
) {
    if (style != PlayerBackgroundStyle.COVER_GRADIENT) {
        PlayerBackgroundContent(
            style = style,
            thumbnailUrl = thumbnailUrl,
            pureBlack = false,
            modifier = modifier,
        )
        return
    }
    Box(modifier = modifier.background(Color(0xFF1A1A1C))) {
        CoverGradientBackdrop(
            thumbnailUrl = thumbnailUrl,
            width = width,
            height = height,
            animate = animate,
            shader = if (warp) rememberWarpShader() else null,
        )
    }
}

@Composable
private fun SolidBackground(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    Box(modifier = modifier.background(color))
}

@Composable
private fun DarkGradientBackground(modifier: Modifier = Modifier) {
    val brush = remember {
        Brush.verticalGradient(
            0f to Color(0xFF2A2A2E),
            0.55f to Color(0xFF1A1A1D),
            1f to Color(0xFF0E0E10),
        )
    }
    Box(modifier = modifier.background(brush))
}

@Composable
private fun AppleMusicBackground(thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

    var palette by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl == null) {
            palette = emptyList()
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                    ?: return@runCatching
                val generated = withContext(Dispatchers.Default) {
                    Palette.from(bitmap)
                        .maximumColorCount(8)
                        .resizeBitmapArea(100 * 100)
                        .generate()
                }
                palette = com.example.musicfy.ui.theme.PlayerColorExtractor.extractAppleMusicColors(
                    palette = generated,
                    fallbackColor = fallbackColorInt,
                )
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "appleMusicMorph")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "appleMusicDrift",
    )

    val blobColors = List(BlobCount) { index ->
        animateColorAsState(
            targetValue = palette.getOrNull(index) ?: surfaceColor,
            animationSpec = tween(durationMillis = 800),
            label = "appleMusicBlob$index",
        ).value
    }

    val base = palette.firstOrNull() ?: surfaceColor

    Box(
        modifier = modifier
            .background(base)
            .drawColorBlobs(blobColors) { drift },
    )
}

private const val BlobCount = 6

private fun Modifier.drawColorBlobs(
    colors: List<Color>,
    phase: () -> Float,
): Modifier = drawBehind {
    val t = phase()
    colors.forEachIndexed { index, color ->

        val angle = t * (0.6f + index * 0.17f) + index * 1.05f
        val orbitX = size.width * (0.20f + 0.06f * (index % 3))
        val orbitY = size.height * (0.14f + 0.05f * (index % 4))
        val center = Offset(
            x = size.width * (0.28f + 0.15f * (index % 3)) + orbitX * kotlin.math.cos(angle),
            y = size.height * (0.24f + 0.18f * (index % 4)) + orbitY * kotlin.math.sin(angle),
        )
        val radius = size.minDimension * (0.55f + 0.08f * (index % 3))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f)),
        )
    )
}
