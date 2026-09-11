package com.example.musicfy.ui.player.styles

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.lyrics.LyricsUtils

/**
 * "Lyrics abstract": a rounded cover with the current and next lyric lines beneath it, set at a
 * size that reacts to how much there is to say.
 *
 * Short lines are set large and long ones small, so a two-word line lands like a title while a
 * wordy one still fits. That is the whole idea of the style - the type does the expressing, which
 * is why there is no other chrome on screen.
 */
@Composable
fun LyricsAbstractStyle(
    surface: Color,
    accent: Color,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val progress by playerConnection.uiState.progressState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    val lines = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) emptyList() else LyricsUtils.parseLyrics(raw)
    }

    val index by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, progress.position) }
    }

    val currentText = lines.getOrNull(index)?.text.orEmpty()
    val nextText = lines.getOrNull(index + 1)?.text.orEmpty()

    // Gradient sweep across the active line. A plain animated brush rather than an AGSL shader, so
    // the style looks the same on every supported version instead of falling flat below API 33.
    val flow = rememberInfiniteTransition(label = "abstractFlow")
    val phase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "abstractFlowPhase",
    )

    val sweep = remember(phase, accent) {
        Brush.linearGradient(
            colors = listOf(
                Color.White,
                accent,
                Color.White,
            ),
            start = androidx.compose.ui.geometry.Offset(phase * 1400f - 700f, 0f),
            end = androidx.compose.ui.geometry.Offset(phase * 1400f + 700f, 400f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onOpenMenu),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(com.example.musicfy.R.drawable.more_vert),
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(36.dp))
                    // Backed so the cover still occupies its space while artwork loads, or when a
                    // track has none - an AsyncImage with a null model draws nothing at all, which
                    // is why the style looked like bare text on black.
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.weight(1f))

            // Lyrics only - the title is not a substitute. When a track has none, the style says
            // so rather than quietly turning into a title card.
            if (lines.isEmpty()) {
                Text(
                    text = "No lyrics for this track",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.35f),
                )
            } else {
                // Keyed on the line index so each new line zooms up and fades in as it arrives,
                // rather than the text swapping in place.
                androidx.compose.animation.AnimatedContent(
                    targetState = index to currentText,
                    transitionSpec = {
                        (
                            androidx.compose.animation.fadeIn(tween(420)) +
                                androidx.compose.animation.scaleIn(
                                    initialScale = 0.86f,
                                    animationSpec = tween(520),
                                )
                            ) togetherWith androidx.compose.animation.fadeOut(tween(260))
                    },
                    label = "abstractLine",
                ) { (_, text) ->
                    AbstractLyricLine(text = text, brush = sweep, glow = true)
                }
            }

            Spacer(Modifier.height(14.dp))

            if (nextText.isNotBlank()) {
                Text(
                    text = nextText,
                    style = TextStyle(
                        fontSize = sizeFor(nextText).times(0.55f),
                        lineHeight = sizeFor(nextText).times(0.65f),
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.35f),
                    maxLines = 3,
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * The active line, drawn twice: once blurred underneath as the glow, once sharp on top.
 *
 * Cheaper and more portable than a shader-based bloom, and it degrades to just the sharp copy on
 * devices where blur is unavailable.
 */
@Composable
private fun AbstractLyricLine(text: String, brush: Brush, glow: Boolean) {
    val size = sizeFor(text)
    val style = TextStyle(
        fontSize = size,
        lineHeight = size * 1.1f,
        fontWeight = FontWeight.Bold,
        brush = brush,
    )

    val annotated = remember(text, size) { abstractLine(text, size) }

    Box {
        if (glow && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Text(
                text = annotated,
                style = style,
                maxLines = 3,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = 0.55f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .blur(18.dp),
            )
        }
        Text(text = annotated, style = style, maxLines = 3)
    }
}

/**
 * Type size from line length: short lines get set big, long ones shrink to fit.
 *
 * Stepped rather than continuous so consecutive lines of similar length settle at the same size
 * instead of jittering by a point or two between every lyric.
 */
private fun sizeFor(text: String) = when (text.length) {
    in 0..10 -> 64.sp
    in 11..20 -> 52.sp
    in 21..32 -> 42.sp
    in 33..48 -> 32.sp
    else -> 26.sp
}

/**
 * Builds the line as mixed type: each word sized against its own length, within the range the line
 * as a whole allows.
 *
 * This is what makes the style abstract rather than just large - a short word swells and a long one
 * settles back, so the line has rhythm instead of one flat size.
 */
private fun abstractLine(text: String, base: androidx.compose.ui.unit.TextUnit): AnnotatedString =
    buildAnnotatedString {
        val words = text.split(" ").filter { it.isNotBlank() }
        words.forEachIndexed { i, word ->
            val scale = when (word.length) {
                in 0..2 -> 1.25f
                in 3..4 -> 1.12f
                in 5..7 -> 1f
                in 8..10 -> 0.88f
                else -> 0.78f
            }
            withStyle(
                SpanStyle(
                    fontSize = base * scale,
                    fontWeight = if (scale >= 1.12f) FontWeight.Black else FontWeight.Bold,
                )
            ) {
                append(word)
            }
            if (i != words.lastIndex) append(" ")
        }
    }
