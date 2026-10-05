// PlayingIndicator.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.R
import kotlinx.coroutines.launch
import kotlin.random.Random

/** covers at least this wide count as big: Home cards and grids, rather than list thumbnails */
private val LargeCoverMinWidth = 96.dp

/** the rounded play glyph from the Figma (Image 6), light grey with no circle behind it */
val CoverGlyphColor = Color(0xFFD9D9D9)

/**
 * five small equaliser bars. each one grows out from its middle, up and down at once, rather than
 * up from the floor, and wanders between heights on its own clock.
 */
@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp,
) {
    val levels = remember(bars) { List(bars) { Animatable(0.3f + 0.4f * Random.nextFloat()) } }

    LaunchedEffect(levels) {
        levels.forEach { level ->
            launch {
                while (true) {
                    level.animateTo(
                        targetValue = 0.22f + Random.nextFloat() * 0.78f,
                        animationSpec = tween(
                            durationMillis = 150 + Random.nextInt(140),
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        }
    }

    Canvas(modifier = modifier.width(barWidth * bars + spacing * (bars - 1))) {
        val w = barWidth.toPx()
        val gap = spacing.toPx()
        val radius = CornerRadius(w / 2f)
        levels.forEachIndexed { index, level ->
            val h = size.height * level.value
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (w + gap), (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * what an active cover shows: the bars while it plays, the play glyph while it's paused.
 * big covers keep the art visible: a light dim, the bars at the left middle and the glyph down in
 * the bottom right corner. list thumbnails keep everything centred, just smaller than before.
 */
@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: Boolean,
    color: Color = Color.White,
    dimColor: Color = Color.Black,
    shape: Shape? = null,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(500)),
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val large = maxWidth >= LargeCoverMinWidth
            if (shape != null && dimColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dimColor.copy(alpha = if (large) 0.3f else ActiveBoxAlpha), shape)
                )
            }
            if (large) {
                if (playWhenReady) {
                    PlayingIndicator(
                        color = color,
                        barWidth = 3.dp,
                        spacing = 2.5.dp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 14.dp)
                            .height(20.dp),
                    )
                } else {
                    CoverPlayGlyph(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 9.dp),
                    )
                }
            } else {
                val side = maxWidth.coerceAtMost(maxHeight)
                if (playWhenReady) {
                    PlayingIndicator(
                        color = color,
                        barWidth = 2.dp,
                        spacing = 1.5.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height((side * 0.3f).coerceIn(10.dp, 15.dp)),
                    )
                } else {
                    val glyph = (side * 0.26f).coerceIn(10.dp, 14.dp)
                    Icon(
                        painter = painterResource(R.drawable.ic_untitled_play),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .align(Alignment.Center)
                            // a triangle looks off-centre when it's centred; nudge it right a hair
                            .offset(x = glyph * 0.08f)
                            .size(width = glyph * 0.9f, height = glyph),
                    )
                }
            }
        }
    }
}

/** the 28dp play button from the Figma, for the corner of a big cover */
@Composable
fun CoverPlayGlyph(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_untitled_play),
            contentDescription = null,
            tint = CoverGlyphColor,
            modifier = Modifier
                .offset(x = 1.5.dp)
                .size(width = 17.dp, height = 19.dp),
        )
    }
}
