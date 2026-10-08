// FadeInCover.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/** a cover that fades in once it has actually loaded, instead of popping in */
@Composable
fun FadeInCover(
    url: String,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "cover_fade"
    )

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onState = { state -> if (state is AsyncImagePainter.State.Success) loaded = true },
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    )
}
