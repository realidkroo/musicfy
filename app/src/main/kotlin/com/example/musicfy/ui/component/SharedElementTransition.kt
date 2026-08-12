// sharedelementtransitionkt
// infrastructure for the album playlist cover expand into place open
// the tapped grid cover morphs into the destination screen s header cover
// the generic slide+fade every other route uses sharedtransitionscope and
// animatedvisibilityscope aren t otherwise ambient inside a navhost s
// so same convention as this app s other compositionlocals
// localdatabase etc they re provided once mainactivitykt wraps navhost in
// sharedtransitionlayout navigationbuilderkt provides the per destination
// animatedcontentscope and read here via homesharedelement rather than
// two new parameters through every grid item composable and destination

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

// no ops returns the modifier unchanged when key is null or when called
// the shared transition navhost eg a preview safe to apply
// call sites that only sometimes want to participate
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
