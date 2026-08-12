// onlineblurkt
// what is this for you ask its for online blur ofc

package com.example.musicfy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import com.example.musicfy.ui.player.BackdropBlurTransformation
import com.example.musicfy.ui.utils.fadingEdge
import com.example.musicfy.ui.utils.resize

@Composable
fun OnlineBlur(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (thumbnailUrl != null) {
            // a 50dp modifierblur() re-blurs the full-resolution thumbnail on the gpu
            // frame at this blur strength no source detail survives anyway so the blur is
            // instead baked once into a 48x48 downsample off the main thread and cached
            // coil then stretched back up — same soft wash none of the per-frame cost
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl.resize(48, 48))
                    .allowHardware(false)
                    .transformations(BackdropBlurTransformation(radiusPx = 4))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(bottom = 200.dp)
            )
        }
        
        // shadow/gradient overlay for "face upward" effect to blend with background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    )
                )
        )
    }
}
