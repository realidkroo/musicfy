// ProfileIdCard.kt
//
// The profile "ID card" from the mocks: PROFILE USR#…, the photo, the name and the join date, with a
// darker strip on the right whose "{user}'s CARD" text scrolls downward forever. Dark on the profile
// page; silver (a brushed-metal gradient with a moving highlight) on the Hello and Done pages.

package com.example.musicfy.ui.screens.setup.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R

/** Width : height of the card, from the mock (332 x 176). */
internal const val IdCardAspect = 1.89f

@Composable
internal fun ProfileIdCard(
    username: String,
    photo: Any?,
    cardNumber: String,
    joinedText: String,
    modifier: Modifier = Modifier,
    silver: Boolean = false,
    showIdCardOf: Boolean = silver,
    /** 0..1 across the card; where the silver highlight sits (the Hello page moves it with the tilt). */
    sheen: () -> Float = { 0.5f },
    onPhotoClick: (() -> Unit)? = null,
) {
    val name = username.trim().ifEmpty { "{username}" }
    val shape = RoundedCornerShape(22.dp)
    val bodyText = if (silver) Color(0xFF111114) else Color.White
    val mutedText = if (silver) Color(0xFF3A3A40) else Color(0xFFB5B5B5)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(IdCardAspect)
            .clip(shape)
            .then(if (silver) Modifier.silverSurface(sheen) else Modifier.background(Color(0xFF2F2F2F))),
    ) {
        // everything scales with the card, so it reads the same on a narrow phone and a tablet
        val unit = maxWidth / 332f
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = unit * 22f, top = unit * 20f, end = unit * 12f, bottom = unit * 18f),
            ) {
                Text(
                    text = "PROFILE USR#$cardNumber",
                    color = mutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = (unit.value * 11f).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(unit * 96f)
                            .clip(CircleShape)
                            .background(if (silver) Color(0xFF5A5A60) else Color(0xFF4F4F4F))
                            .then(if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo != null) {
                            AsyncImage(
                                model = photo,
                                contentDescription = "Profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (onPhotoClick != null) {
                            Icon(
                                painterResource(R.drawable.add),
                                contentDescription = "Add a profile picture",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(unit * 34f),
                            )
                        }
                    }
                    Spacer(Modifier.width(unit * 18f))
                    Column(modifier = Modifier.weight(1f)) {
                        if (showIdCardOf) {
                            Text(
                                "ID CARD OF",
                                color = mutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (unit.value * 10f).sp,
                            )
                        }
                        Text(
                            text = name,
                            color = bodyText,
                            fontSize = (unit.value * 21f).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = joinedText,
                            color = bodyText.copy(alpha = 0.85f),
                            fontSize = (unit.value * 10.5f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            MarqueeStrip(
                text = "${name}`s CARD",
                color = if (silver) Color(0xFF2B2B30).copy(alpha = 0.75f) else Color(0xFF8A8A8A),
                background = if (silver) Color.Black.copy(alpha = 0.18f) else Color(0xFF1B1B1B),
                modifier = Modifier
                    .width(unit * 40f)
                    .fillMaxHeight(),
                fontSizeSp = unit.value * 17f,
            )
        }
    }
}

/** Vertical text, rotated a quarter turn, sliding downward without end. Drawn, never recomposed. */
@Composable
private fun MarqueeStrip(
    text: String,
    color: Color,
    background: Color,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = remember(color, fontSizeSp) {
        TextStyle(color = color, fontSize = fontSizeSp.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
    val layout = remember(text, style) { measurer.measure("$text   ·   ", style) }
    val transition = rememberInfiniteTransition(label = "idCardMarquee")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 5200, easing = LinearEasing), RepeatMode.Restart),
        label = "idCardMarqueeProgress",
    )
    Canvas(modifier = modifier.background(background)) {
        val step = layout.size.width.toFloat().coerceAtLeast(1f)
        // rotated 90°: the text's x-axis runs down the strip, so moving it +x scrolls it downward
        rotate(90f, pivot = Offset.Zero) {
            translate(left = 0f, top = -size.width / 2f - layout.size.height / 2f) {
                var x = -step + progress * step
                while (x < size.height) {
                    drawText(layout, topLeft = Offset(x, 0f))
                    x += step
                }
            }
        }
    }
}

/** Brushed silver with a soft diagonal highlight that sits at [sheen] (0..1 across the card). */
private fun Modifier.silverSurface(sheen: () -> Float): Modifier = drawWithCache {
    val base = Brush.linearGradient(
        0f to Color(0xFFBFC1C6),
        0.35f to Color(0xFFE9EAEE),
        0.55f to Color(0xFFA9ABB2),
        0.8f to Color(0xFFD7D9DE),
        1f to Color(0xFF9C9EA5),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    // fine horizontal grain, what makes it read as metal rather than grey plastic
    val lines = (size.height / 3f).toInt()
    onDrawBehind {
        drawRect(base)
        for (i in 0 until lines) {
            val y = i * 3f
            drawLine(Color.White.copy(alpha = if (i % 2 == 0) 0.05f else 0.025f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val center = sheen().coerceIn(-0.2f, 1.2f) * size.width
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.55f),
                1f to Color.Transparent,
                start = Offset(center - size.width * 0.35f, 0f),
                end = Offset(center + size.width * 0.15f, size.height),
            ),
            blendMode = BlendMode.Screen,
        )
    }
}
