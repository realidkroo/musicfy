// OdometerText.kt

package com.example.musicfy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.theme.InterFontFamily

/**
 * Layout-stable Weatheify-style odometer digit counter component.
 * Animates numbers smoothly using vertical digit reels with motion blur alpha gradient masks.
 */
@Composable
fun OdometerNumber(
    value: String,
    fontSize: TextUnit = 46.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily = InterFontFamily,
    color: Color = Color.White,
    letterSpacing: TextUnit = (-2.0).sp,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        value.forEach { char ->
            if (char.isDigit()) {
                SingleOdometerDigit(
                    targetDigit = char.digitToInt(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = color,
                    letterSpacing = letterSpacing,
                )
            } else {
                Text(
                    text = char.toString(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = color,
                    letterSpacing = letterSpacing,
                )
            }
        }
    }
}

@Composable
private fun SingleOdometerDigit(
    targetDigit: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily,
    color: Color,
    letterSpacing: TextUnit,
) {
    val density = LocalDensity.current
    val digitHeightDp: Dp = with(density) { fontSize.toDp() * 1.2f }
    val digitWidthDp: Dp = with(density) { fontSize.toDp() * 0.62f }
    val digitHeightPx = with(density) { digitHeightDp.toPx() }

    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetDigit) {
        animatable.animateTo(
            targetValue = targetDigit.toFloat(),
            animationSpec = tween(
                durationMillis = 850,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .width(digitWidthDp)
            .height(digitHeightDp)
            .clipToBounds()
            // Fade the digits themselves at the reel edges (no painted colour, so any background stays clean)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.22f to Color.Black,
                        0.78f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentVal = animatable.value
        val baseIndex = currentVal.toInt()
        val fractional = currentVal - baseIndex

        // Render current digit and next digit on rolling vertical reel
        for (i in 0..9) {
            val offsetFromCurrent = i - currentVal
            val yOffsetDp = with(density) { (offsetFromCurrent * digitHeightPx).toDp() }

            if (offsetFromCurrent in -1.2f..1.2f) {
                val alpha = (1f - kotlin.math.abs(offsetFromCurrent)).coerceIn(0f, 1f)
                val blurScale = 1f + kotlin.math.abs(fractional) * 0.08f

                Text(
                    text = i.toString(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    color = color.copy(alpha = alpha),
                    letterSpacing = letterSpacing,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color.copy(alpha = alpha),
                    ),
                    modifier = Modifier
                        .offset(y = yOffsetDp)
                        .graphicsLayer {
                            scaleY = blurScale
                        }
                )
            }
        }
    }
}
