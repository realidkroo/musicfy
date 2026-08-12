package com.example.musicfy.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.SystemClock
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreen
import kotlin.math.max

private const val MinHoldMillis = 300L
private const val IconZoomDuration = 600L
private const val IconFadeDuration = 300L
private const val BackgroundFadeDuration = 500L
private const val BackgroundFadeStartDelay = IconZoomDuration

// icon grows well past the screen bounds so it visibly fills the frame before
// dissolving to black instead of shrinking away to a barely visible dot
private const val FullBleedCoverageFactor = 1.8f

private val ZoomEasing = PathInterpolator(0.3f, 0f, 0.1f, 1f)
private val FadeEasing = PathInterpolator(0.4f, 0f, 1f, 1f)

// icon zooms up until it fills the screen while fading to the black splash
fun SplashScreen.setZoomFadeExitAnimation() {
    val readySinceMillis = SystemClock.elapsedRealtime()
    setKeepOnScreenCondition { SystemClock.elapsedRealtime() - readySinceMillis < MinHoldMillis }

    setOnExitAnimationListener { splashScreenViewProvider ->
        val iconView = splashScreenViewProvider.iconView
        val rootView = splashScreenViewProvider.view

        val iconWidth = iconView.width.takeIf { it > 0 } ?: 1
        val targetScale = max(rootView.width, rootView.height).toFloat() / iconWidth * FullBleedCoverageFactor

        val zoom = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, targetScale),
                ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, targetScale),
            )
            duration = IconZoomDuration
            interpolator = ZoomEasing
        }

        // icon stays fully visible while it grows then dissolves into the
        // black background during the back half of the zoom
        val iconFade = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f).apply {
            duration = IconFadeDuration
            startDelay = IconZoomDuration - IconFadeDuration
            interpolator = FadeEasing
        }

        val backgroundFade = ObjectAnimator.ofFloat(rootView, View.ALPHA, 1f, 0f).apply {
            duration = BackgroundFadeDuration
            startDelay = BackgroundFadeStartDelay
            interpolator = FadeEasing
        }

        AnimatorSet().apply {
            playTogether(zoom, iconFade, backgroundFade)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // dropped frames near the end can leave the last drawn
                    // frame short of the true end value snap explicitly
                    iconView.alpha = 0f
                    rootView.alpha = 0f
                    splashScreenViewProvider.remove()
                }
            })
            start()
        }
    }
}
