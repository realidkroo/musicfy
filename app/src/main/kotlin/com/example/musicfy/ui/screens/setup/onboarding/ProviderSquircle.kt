// ProviderSquircle.kt
//
// Each service's app icon, clipped to the same squircle app icons use.

package com.example.musicfy.ui.screens.setup.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.R
import com.example.musicfy.importer.account.AccountService

@Composable
internal fun ProviderSquircle(service: AccountService, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val art = when (service) {
        // logo on its own black square
        AccountService.SPOTIFY -> ProviderArt(R.drawable.provider_spotify, SolidColor(Color(0xFF191414)))
        // round logo on white: shown as its circle, on black
        AccountService.TIDAL -> ProviderArt(R.drawable.provider_tidal, SolidColor(Color.Black), circular = true)
        // red disc: red behind it, so the squircle reads as one red icon
        AccountService.YOUTUBE_MUSIC -> ProviderArt(R.drawable.provider_youtube_music, SolidColor(Color(0xFFFF0000)), zoom = 1.06f)
        // already an app icon with rounded corners; enlarged a touch so its own corners fall outside
        AccountService.APPLE_MUSIC -> ProviderArt(
            R.drawable.provider_apple_music,
            Brush.verticalGradient(listOf(Color(0xFFFA5C70), Color(0xFFFA2D48))),
            zoom = 1.1f,
        )
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(SquircleShape)
            .background(art.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(art.drawable),
            contentDescription = service.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(art.zoom)
                .then(if (art.circular) Modifier.clip(CircleShape) else Modifier),
        )
    }
}

private class ProviderArt(
    @DrawableRes val drawable: Int,
    val background: Brush,
    val circular: Boolean = false,
    val zoom: Float = 1f,
)

/** The app-icon squircle as a Shape, for clipping. */
internal val SquircleShape = GenericShape { size, _ -> addPath(squirclePath(size)) }

/** A continuous-corner squircle (superellipse, n = 5), the shape app icons use. */
private fun squirclePath(size: Size): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rx = size.width / 2f
    val ry = size.height / 2f
    val n = 5.0
    val steps = 72
    for (i in 0..steps) {
        val t = 2 * Math.PI * i / steps
        val cos = Math.cos(t)
        val sin = Math.sin(t)
        val x = cx + rx * Math.signum(cos) * Math.pow(Math.abs(cos), 2.0 / n)
        val y = cy + ry * Math.signum(sin) * Math.pow(Math.abs(sin), 2.0 / n)
        if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
    }
    path.close()
    return path
}
