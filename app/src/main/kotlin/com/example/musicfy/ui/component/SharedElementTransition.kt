// SharedElementTransition.kt
// Infrastructure for the album/playlist cover "expand into place" open transition —
// the tapped grid cover morphs into the destination screen's header cover instead of
// the generic slide+fade every other route uses. `SharedTransitionScope` and
// `AnimatedVisibilityScope` aren't otherwise ambient inside a NavHost's destinations,
// so — same convention as this app's other CompositionLocals (LocalPlayerConnection,
// LocalDatabase, etc.) — they're provided once (MainActivity.kt wraps NavHost in
// SharedTransitionLayout; NavigationBuilder.kt provides the per-destination
// AnimatedContentScope) and read here via `homeSharedElement`, rather than threading
// two new parameters through every grid-item composable and destination screen.

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

// No-ops (returns the modifier unchanged) when `key` is null or when called outside
// the shared-transition NavHost (e.g. a preview) — safe to apply unconditionally at
// call sites that only sometimes want to participate.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.homeSharedElement(key: String?): Modifier {
    if (key == null) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedTransitionScope) {
        this@homeSharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
