// seamblurkt
// blurred band bridging the cover art into the morphing backdrop below it so
// between them reads as one soft blend instead of a hard image/background
// sibling of morphingcover (added in bottomsheetplayerkt) not inside it
// glassstate that morphingcover registers as a glassroot — if this were
// morphingcover's own glassroot-wrapped subtree it would end up capturing

// reuses glasskitkt's progressiveglassbackground — the same real
// homescreenkt already uses for its scroll-driven top bar blur — instead of
// hand-rolled "duplicate the cover stretch it blur it" attempt that approach
// smeared-photo artifact (stretching a sharp recognizable photo into a band
// blurring parts of it) and a visible hard edge (the blur wasn't actually
// the cover's true boundary) blurring the *real* rendered content behind
// problems entirely — there's nothing to "leak" or look wrong it just shows
// there blurred

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
    // extra 01 visibility factor multiplied into the band's own alpha used to take
    fadeProvider: () -> Float = { 1f },
) {
    if (trackInfo.thumbnailUrl == null) return
    // cheap bail-out only — this used to be the actual visibility gate (progress
    // cutoff) which is exactly why the band popped in/out instantly right at
    // instead of fading the real fade now lives below as a continuous alpha
    // skips composing the band at all while it's fully invisible anyway
    // player isn't even open)
    val shouldExist by remember { derivedStateOf { progressProvider() > 0f } }
    if (!shouldExist) return

    val context = LocalContext.current
    // top pulled up further into the cover art (was 055) so the transition into
    // controls zone reads as one long soft blend instead of a short band right
    // cover's actual bottom edge is ~063 of maxheight — see morphingcoverkt's
    val bandTop = maxHeight * 0.52f
    val bandBottom = maxHeight * 0.74f

    // theme-colored scrim (same palette-extraction approach as albumgradientkt)
    // title/time text stays readable even if the cover happens to be light/white
    // tinted not strong enough to read as a deliberate color effect on its own
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
            .graphicsLayer {
                // ramp widened from (085 -> 10) to (05 -> 095): progress sits at a constant
                // 10 for the entire time the player is just open and not being dragged so the
                // fade only ever plays during the open/close gesture itself — a 015-wide
                // is maybe 40-60ms of a few-hundred-ms drag imperceptible as a fade even
                // it technically isn't a hard cutoff anymore this gives it real visible
                val p = progressProvider()
                alpha = ((p - 0.5f) / 0.45f).coerceIn(0f, 1f) * fadeProvider().coerceIn(0f, 1f)
            }
    ) {
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
