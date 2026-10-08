package com.example.musicfy.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen
import com.example.musicfy.ui.launch.LaunchGate
import timber.log.Timber

// with the intro the app already draws the same mark in the same spot under the splash, so this
// fade only hides any sub-pixel difference between the two; without it, it's the whole exit
private const val HandOffFadeMillis = 90L
private const val PlainFadeMillis = 200L

private val FadeEasing = PathInterpolator(0.4f, 0f, 1f, 1f)

/**
 * No hold and no zoom: the splash steps aside the moment the app has drawn its first frame.
 * When [introPlays], the launch intro (ui/launch/LaunchIntro.kt) has the mark from here on.
 */
fun SplashScreen.handOffToLaunchIntro(introPlays: () -> Boolean) {
    setOnExitAnimationListener { provider ->
        val root = provider.view
        val icon = provider.iconView
        val at = IntArray(2).also(icon::getLocationInWindow)
        Timber.tag("LaunchIntro").d(
            "splash icon %dx%d at %d,%d in a %dx%d window",
            icon.width, icon.height, at[0], at[1], root.width, root.height,
        )

        ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
            duration = if (introPlays()) HandOffFadeMillis else PlainFadeMillis
            interpolator = FadeEasing
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    provider.remove()
                    LaunchGate.onSplashGone()
                }
            })
            start()
        }
    }
}
