// SeamBlur.kt
// Blurred band bridging the cover art into the morphing backdrop below it, so the boundary
// between them reads as one soft blend instead of a hard image/background seam. Lives as a
// sibling of MorphingCover (added in BottomSheetPlayer.kt), not inside it, because it reads a
// GlassState that MorphingCover registers as a glassRoot — if this were nested inside
// MorphingCover's own glassRoot-wrapped subtree it would end up capturing (and blurring) itself.
//
// Reuses GlassKit.kt's ProgressiveGlassBackground — the same real backdrop-blur system
// HomeScreen.kt already uses for its scroll-driven top bar blur — instead of an earlier
// hand-rolled "duplicate the cover, stretch it, blur it" attempt. That approach kept showing a
// smeared-photo artifact (stretching a sharp, recognizable photo into a band and only lightly
// blurring parts of it) and a visible hard edge (the blur wasn't actually strong enough right at
// the cover's true boundary). Blurring the *real* rendered content behind this band avoids both
// problems entirely — there's nothing to "leak" or look wrong, it just shows what's actually
// there, blurred.

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
) {
    if (trackInfo.thumbnailUrl == null) return
    val showSeamBlur by remember { derivedStateOf { progressProvider() > 0.85f } }
    if (!showSeamBlur) return

    val context = LocalContext.current
    // Just above the cover's actual bottom edge (~0.56 of maxHeight — see MorphingCover.kt's
    // fullArtHeight).
    val bandTop = maxHeight * 0.55f
    val bandBottom = maxHeight * 0.74f

    // Theme-colored scrim (same Palette-extraction approach as AlbumGradient.kt) so the white
    // title/time text stays readable even if the cover happens to be light/white — "slightly"
    // tinted, not strong enough to read as a deliberate color effect on its own.
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bandBottom - bandTop)
            .offset(y = bandTop)
    ) {
        // One continuous blur across the whole band (not split into two stacked halves) — the
        // earlier top-half/bottom-half split each independently blurred its own bounded region,
        // and right at the shared boundary between them the two separately-computed blurs didn't
        // quite line up, showing as a visible seam in the middle. A single blur pass has nothing
        // to seam against. The "fades in, solid, fades out" shape now comes entirely from the
        // opacity mask below, not from varying the blur radius itself.
        //
        // TileMode.CLAMP (rather than GlassPillBackground's default DECAL) replicates the true
        // left/right edge pixel outward instead of fading to transparent there — this box's own
        // width IS the intended blur extent (edge-to-edge across the screen), so there's no
        // "outside" for the blur to taper into. An earlier attempt widened the layer past the
        // screen with DECAL still active, hoping to push the taper off-screen; that didn't work
        // because the captured content itself (from MorphingCover's glassRoot) is only ever
        // screen-width, so the widened layer was just transparent past the real edge — DECAL
        // still faded the real content right where it met that transparency, at the exact same
        // visible edge as before. CLAMP has no such transparent region to fade into.
        GlassPillBackground(
            state = glassState,
            blurRadius = { 130f },
            tileMode = android.graphics.Shader.TileMode.CLAMP,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.25f to Color.Black,
                            0.8f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
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
