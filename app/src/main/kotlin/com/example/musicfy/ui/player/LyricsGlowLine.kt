// LyricsGlowLine.kt
// Per-line renderer for the lyrics page, matching Monochrome's ".synced-line" treatment
// (styles.css: "Synced lyrics styling with Apple Music animations") — dim/blurred/scaled-down
// for lines away from playback, sharp/bold/glowing for the active one, all cross-fading on a
// 600ms curve. Also renders word-level "moving" karaoke fill (a left-to-right color sweep
// timed off LyricsEntry.words) on the active line when the provider supplied word timestamps,
// and an optional smaller romanized/translated sub-line beneath the main text.

package com.example.musicfy.ui.player

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.lyrics.LyricsEntry

enum class LyricsLineState { ACTIVE, UPCOMING, PAST, DEFAULT }

@Composable
fun LyricsGlowLine(
    entry: LyricsEntry,
    state: LyricsLineState,
    positionMs: Long,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLine: String? = null,
) {
    val targetAlpha = when (state) {
        LyricsLineState.ACTIVE -> 1f
        LyricsLineState.UPCOMING -> 0.7f
        LyricsLineState.PAST -> 0.3f
        LyricsLineState.DEFAULT -> 0.5f
    }
    val targetScale = when (state) {
        LyricsLineState.ACTIVE -> 1.06f
        LyricsLineState.UPCOMING -> 0.98f
        LyricsLineState.PAST -> 0.93f
        LyricsLineState.DEFAULT -> 0.95f
    }
    val targetBlur = when (state) {
        LyricsLineState.ACTIVE -> 0f
        LyricsLineState.UPCOMING -> 0.8f
        LyricsLineState.PAST -> 2f
        LyricsLineState.DEFAULT -> 1.5f
    }

    val animSpec = tween<Float>(durationMillis = 600)
    val alpha by animateFloatAsState(targetAlpha, animSpec, label = "lyricsLineAlpha")
    val scale by animateFloatAsState(targetScale, animSpec, label = "lyricsLineScale")
    val blur by animateFloatAsState(targetBlur, animSpec, label = "lyricsLineBlur")

    val baseColor = LocalContentColor.current
    val textColor = if (state == LyricsLineState.ACTIVE) Color.White else baseColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 24.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.05f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blur, blur, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            }
    ) {
        val words = entry.words
        if (state == LyricsLineState.ACTIVE && !words.isNullOrEmpty()) {
            KaraokeSweepText(
                text = entry.text,
                words = words,
                positionMs = positionMs,
                baseColor = textColor.copy(alpha = 0.45f),
                highlightColor = accentColor,
            )
        } else {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    shadow = if (state == LyricsLineState.ACTIVE) {
                        androidx.compose.ui.graphics.Shadow(
                            color = accentColor.copy(alpha = 0.45f),
                            offset = Offset.Zero,
                            blurRadius = 24f,
                        )
                    } else null,
                ),
                fontWeight = if (state == LyricsLineState.ACTIVE) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 3,
            )
        }

        if (!subLine.isNullOrBlank()) {
            Text(
                text = subLine,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Left-to-right color sweep across the whole line, timed by how far playback is through the
 * line's word span (first word's start to last word's end) — an approximation of true
 * per-word highlighting (which would need per-word text-layout measurement) that still reads
 * as the "progressive, moving" karaoke fill the line's timestamps describe.
 */
@Composable
private fun KaraokeSweepText(
    text: String,
    words: List<com.example.musicfy.lyrics.WordTimestamp>,
    positionMs: Long,
    baseColor: Color,
    highlightColor: Color,
) {
    val lineStartSec = words.first().startTime
    val lineEndSec = words.last().endTime
    val positionSec = positionMs / 1000.0
    val rawFraction = if (lineEndSec > lineStartSec) {
        ((positionSec - lineStartSec) / (lineEndSec - lineStartSec))
    } else 0.0
    val targetFraction = rawFraction.coerceIn(0.0, 1.0).toFloat()
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 150),
        label = "karaokeSweep",
    )

    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 22.sp,
        shadow = androidx.compose.ui.graphics.Shadow(
            color = highlightColor.copy(alpha = 0.45f),
            offset = Offset.Zero,
            blurRadius = 24f,
        ),
    )

    Box {
        Text(
            text = text,
            style = style,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = baseColor,
            overflow = TextOverflow.Ellipsis,
            maxLines = 3,
        )
        if (fraction > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtMost(1f))
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = text,
                    style = style,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = highlightColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 3,
                )
            }
        }
    }
}
