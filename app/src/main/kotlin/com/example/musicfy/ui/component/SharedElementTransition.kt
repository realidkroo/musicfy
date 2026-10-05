// SharedElementTransition.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * shared cover between a grid card and the screen it opens. playlists open with the full container
 * transform (the whole screen grows out of the card), albums keep the plain cover morph.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.homeSharedElement(
    key: String?,
    cornerRadius: Dp = HomeCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null) return this
    if (key.startsWith("playlist-")) return containerTransformSource(key, cornerRadius, coverUrl)
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedTransitionScope) {
        this@homeSharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
