package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerStyle
import kotlin.math.absoluteValue

/**
 * The zoomed-out player style picker.
 *
 * Reached by long-pressing the player: the player itself scales away behind this while the styles
 * come forward, so the gesture reads as stepping back from the player rather than opening an
 * unrelated screen.
 *
 * Cards are deliberately plain rather than live player previews - rendering four working players
 * at once costs far more than it communicates, and the selection is confirmed by the name under
 * the header anyway.
 */
@Composable
fun PlayerStyleSelector(
    selected: PlayerStyle,
    artworkUrl: String?,
    onSelect: (PlayerStyle) -> Unit,
    onEdit: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val styles = remember { PlayerStyle.entries.toList() }
    val pagerState = rememberPagerState(
        initialPage = styles.indexOf(selected).coerceAtLeast(0),
        pageCount = { styles.size },
    )

    // Settling on a page *is* the selection, so the header name and the player behind stay in step
    // with what the user is looking at.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            styles.getOrNull(page)?.let(onSelect)
        }
    }

    val current = styles.getOrElse(pagerState.settledPage) { selected }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val entry by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "styleSelectorEntry",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Swipe down anywhere to leave, so the picker can be dismissed without aiming for
                // the back button.
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (travelled > 140f) onDismiss() },
                    onVerticalDrag = { _, amount -> travelled += amount },
                )
            }
            .graphicsLayer {
                // Settles in from slightly oversized, so it reads as pulling back from the player
                // rather than a panel appearing on top of it.
                alpha = entry
                val scale = 1.08f - 0.08f * entry
                scaleX = scale
                scaleY = scale
            }
            .background(Color(0xFF121212)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Styles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = current.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 40.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val style = styles[page]
                // Neighbouring cards sit back a little so the focused one reads as the selection.
                val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .graphicsLayer {
                            val scale = 1f - 0.06f * distance
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.35f * distance
                        }
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onEdit(style) },
                        ),
                ) {
                    StylePreview(style = style, artworkUrl = artworkUrl)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                styles.forEachIndexed { index, _ ->
                    val active = index == pagerState.currentPage
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else 0.3f,
                        animationSpec = tween(200),
                        label = "styleDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = dotAlpha)),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { onEdit(current) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "edit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * A scaled-down impression of what the style looks like, using the current cover.
 *
 * Not a live player - four of those composing at once costs far more than it communicates - but
 * enough shape and real artwork to tell the styles apart at a glance.
 */
@Composable
private fun StylePreview(style: PlayerStyle, artworkUrl: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        when (style) {
            PlayerStyle.SIMPLE, PlayerStyle.SIMPLE_COMPACT -> {
                // The simple styles are defined by the progress-filled background, so the preview
                // shows that split rather than a cover.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.38f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.height(10.dp))
                if (style == PlayerStyle.SIMPLE_COMPACT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(artworkUrl, 34.dp, 10.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            PreviewBar(width = 84.dp)
                            Spacer(Modifier.height(5.dp))
                            PreviewBar(width = 54.dp, alpha = 0.28f)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                PreviewBar(width = 96.dp, height = 20.dp)
                Spacer(Modifier.height(6.dp))
                PreviewBar(width = 96.dp, height = 20.dp, alpha = 0.3f)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { Dot() }
                }
            }

            PlayerStyle.LYRICS_ABSTRACT -> {
                Artwork(artworkUrl, null, 22.dp, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(14.dp))
                PreviewBar(width = 132.dp, height = 16.dp)
                Spacer(Modifier.height(7.dp))
                PreviewBar(width = 96.dp, height = 13.dp, alpha = 0.28f)
                Spacer(Modifier.height(20.dp))
            }

            PlayerStyle.DEFAULT -> {
                Artwork(artworkUrl, null, 18.dp, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(14.dp))
                PreviewBar(width = 120.dp)
                Spacer(Modifier.height(6.dp))
                PreviewBar(width = 80.dp, alpha = 0.28f)
                Spacer(Modifier.height(12.dp))
                PreviewBar(width = 999.dp, height = 5.dp, alpha = 0.3f)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    repeat(3) { Dot(size = 16.dp) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Artwork(url: String?, size: androidx.compose.ui.unit.Dp?, corner: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val shaped = modifier
        .then(if (size != null) Modifier.size(size) else Modifier)
        .clip(RoundedCornerShape(corner))
        .background(Color.White.copy(alpha = 0.12f))
    if (url.isNullOrBlank()) {
        Box(modifier = shaped)
    } else {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = shaped,
        )
    }
}

@Composable
private fun PreviewBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp = 11.dp,
    alpha: Float = 0.55f,
) {
    Box(
        modifier = Modifier
            .then(if (width.value >= 999f) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = alpha)),
    )
}

@Composable
private fun Dot(size: androidx.compose.ui.unit.Dp = 13.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f)),
    )
}
