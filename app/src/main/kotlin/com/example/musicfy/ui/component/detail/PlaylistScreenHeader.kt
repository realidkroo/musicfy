package com.example.musicfy.ui.component.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.R
import com.example.musicfy.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlaylistScreenHeader(
    thumbnailUrl: String?,
    title: String,
    userName: String,
    description: String?,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onMoreClick: () -> Unit,
    onColorExtracted: (Color) -> Unit = {},
    onCoverPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    // from detailcollapsestate headercontentalpha fades this header s own text+
    // gradient away phase a morphprogress > 0 means the top bar s morph
    // taken over and this cover should hard cut to invisible phase b without
    // cut the two would be visible at once reading as a ghost duplicate rather
    // one continuous image handing off
    headerContentAlpha: Float = 1f,
    morphProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val isDarkTheme = backgroundColor.luminance() < 0.5f

    var extractedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(thumbnailUrl, isDarkTheme) {
        extractedColor = null
        if (thumbnailUrl == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(8)
                            .resizeBitmapArea(100 * 100)
                            .generate()
                    }
                    val colors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColorInt,
                    )
                    extractedColor = colors.firstOrNull()?.let {
                        PlayerColorExtractor.darkenIfTooLight(it, isDarkTheme = isDarkTheme)
                    }
                    extractedColor?.let { onColorExtracted(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = extractedColor ?: backgroundColor,
        animationSpec = tween(durationMillis = 600),
        label = "PlaylistHeaderColor",
    )

    // content title action row sits on animatedcolor near the bottom of the
    // pick black vs white based on what actually ended up there instead of
    val contentColor = if (animatedColor.luminance() > 0.5f) Color.Black else Color.White

    val headerHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    val coverHandedOff = morphProgress > 0.001f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
        // no background color here on purpose once the cover hands off to the top
        // bar s morphing copy and the gradient overlay has faded with it a flat
        // background here would sit exposed behind the now empty header instead of
        // the screen s own accent tinted background that s the dark bg behind the
        // cover this used to show letting it stay transparent means whatever s
        // painted behind this whole screen the accent color gradient shows through
        // consistently instead of a mismatched flat color
    ) {
        // fullscreen cover image forced to fill container
        if (!thumbnailUrl.isNullOrEmpty()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer { alpha = if (coverHandedOff) 0f else 1f }
                    .onGloballyPositioned {
                        val pos = it.positionInWindow()
                        onCoverPositioned(
                            androidx.compose.ui.geometry.Rect(
                                pos.x, pos.y, pos.x + it.size.width, pos.y + it.size.height
                            )
                        )
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (coverHandedOff) 0f else 1f }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        // gradient overlay fades with the header content phase a not the cover
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = headerContentAlpha }
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.25f),
                        0.2f to Color.Transparent,
                        0.6f to Color.Transparent,
                        0.85f to animatedColor.copy(alpha = 0.7f),
                        1.0f to animatedColor,
                    )
                )
        )

        // header content positioned at bottom of the cover content is bottom aligned
        // within this fixed height header so the gap between the action row and the
        // track list right below it is controlled by this bottom padding not a
        // trailing spacer which would just get pushed against the box s own bottom
        // edge instead of creating room before the list that follows
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .graphicsLayer { alpha = headerContentAlpha }
        ) {
            // hidden entirely rather than shown blank as a placeholder callers pass an
            // empty string when there s no meaningful creator to name auto generated
            // playlists no signed in account etc per the per screen rules
            if (userName.isNotBlank()) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // the n songs • duration line was removed per feedback kept as an
            // unused param on the composable s public signature since callers still
            // compute it for other purposes but it no longer renders here

            Spacer(modifier = Modifier.height(20.dp))

            // action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // play button pill symmetric horizontal padding centers the icon+
                // text instead of the previous 36 58 split and the pill s height comes
                // from action_row_height so it lines up exactly with the circles below
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ACTION_ROW_HEIGHT)
                        .clip(RoundedCornerShape(50))
                        .background(contentColor.copy(alpha = 0.25f))
                        .clickable { onPlayClick() }
                        .padding(horizontal = 42.dp)
                ) {
                    AnimatedContent(targetState = isPlaying, label = "PlayPause") { playing ->
                        Icon(
                            painter = painterResource(if (playing) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play),
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // shuffle button circle same height as the pill above
                Box(
                    modifier = Modifier
                        .size(ACTION_ROW_HEIGHT)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f))
                        .clickable { onShuffleClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = "Shuffle",
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // options button circle same height as the pill above
                Box(
                    modifier = Modifier
                        .size(ACTION_ROW_HEIGHT)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f))
                        .clickable { onMoreClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Options",
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// shared height for the play pill and the two circular buttons beside it so
// line up exactly instead of the circles being taller than the pill
private val ACTION_ROW_HEIGHT = 44.dp
