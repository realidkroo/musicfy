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
    // playlists and albums open the same way now: the page grows out of the card
    if (key.startsWith("playlist-") || key.startsWith("album-")) return containerTransformSource(key, cornerRadius, coverUrl)
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedTransitionScope) {
        this@homeSharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

/**
 * the key an album or playlist cover hands its page over with, matching the one the page itself
 * grows from ("album-<id>" in AlbumScreen, "playlist-<id>" in the playlist screens). anything that
 * doesn't open a cover page has none.
 */
fun coverTransitionKey(item: com.music.innertube.models.YTItem): String? = when (item) {
    is com.music.innertube.models.AlbumItem -> "album-${item.id}"
    is com.music.innertube.models.PlaylistItem -> "playlist-${item.id}"
    else -> null
}
