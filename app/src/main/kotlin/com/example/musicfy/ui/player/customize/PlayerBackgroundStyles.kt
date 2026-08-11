// PlayerBackgroundStyles.kt
// The three NEW player backdrops from the "Bg Style" section of the customization page.
//
// PlayerBackgroundStyle.COVER_GRADIENT is deliberately absent from this file. That style is
// implemented by the blurred-artwork + liquid-warp block that already lives inside
// MorphingCover, left exactly where and as it was — it is the default, so an install that never
// opens the editor renders through the identical code path it always did. MorphingCover branches
// to this file only for the other three.

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

/**
 * Renders [style]'s backdrop.
 *
 * Calling this with [PlayerBackgroundStyle.COVER_GRADIENT] draws nothing — see the file header;
 * MorphingCover keeps that case on its own original code path and never routes it here.
 */
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

/**
 * [PlayerBackgroundContent] plus COVER_GRADIENT, which it does not draw.
 *
 * COVER_GRADIENT is the real thing — the same [CoverGradientBackdrop] the player itself renders,
 * warp shader and all — not a lookalike. An earlier version here drew a bigger blurred bitmap
 * with no warp, which made the editor's preview visibly disagree with the player it was
 * previewing.
 *
 * @param width / [height] the size to allocate the backdrop layer at. Pass the *stage's largest*
 *   size, not its current one: the layer is fixed-size by design and the caller's clip reveals
 *   more or less of it.
 */
@Composable
fun PlayerBackgroundPreview(
    style: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    width: Dp,
    height: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** False skips building the AGSL program entirely — for previews too small to show it. */
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

/** "Simple gray color" — a flat fill, no artwork input at all. */
@Composable
private fun SolidBackground(pureBlack: Boolean, modifier: Modifier = Modifier) {
    val color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    Box(modifier = modifier.background(color))
}

/** "Simple dark static gradient" — fixed colours, so it never changes between tracks. */
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

/**
 * "Apple music style cover morph" — a slow drift between six palette colours pulled from the
 * artwork, layered as overlapping radial gradients.
 *
 * Ported from the old player's APPLE_MUSIC branch (/old-player/PlayerBackground.kt), but built
 * from [PlayerColorExtractor.extractAppleMusicColors] and animated radial brushes rather than
 * that version's blurred-bitmap-plus-RuntimeShader stack: no offscreen bitmap, no AGSL, and it
 * works below API 33.
 */
@Composable
private fun AppleMusicBackground(thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

    var palette by remember { mutableStateOf<List<Color>>(emptyList()) }

    // Same load-and-extract shape as AlbumGradient.kt, which is the established call site for
    // PlayerColorExtractor in this codebase.
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

    // One clock for the whole mesh; each blob reads it at a different phase and radius, which is
    // what keeps them from moving as a rigid group.
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

/**
 * Draws [colors] as overlapping soft radial blobs whose centres orbit on the shared [phase].
 *
 * The phase is read inside the draw block, so the mesh animates without recomposing anything —
 * the same draw-phase-read discipline the rest of the player uses for its per-frame values.
 */
private fun Modifier.drawColorBlobs(
    colors: List<Color>,
    phase: () -> Float,
): Modifier = drawBehind {
    val t = phase()
    colors.forEachIndexed { index, color ->
        // Each blob gets its own orbit radius, speed multiplier and starting angle so the group
        // never reads as a single rotating ring.
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
    // Keeps text legible over a bright palette, matching the old player's depth overlay.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f)),
        )
    )
}
