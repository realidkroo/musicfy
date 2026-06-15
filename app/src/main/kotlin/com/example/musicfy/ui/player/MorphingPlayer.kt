package com.example.musicfy.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.example.musicfy.R
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.extensions.togglePlayPause
import androidx.media3.common.Player

@Composable
fun MorphingSharedElements(
    progressProvider: () -> Float,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playbackState: @Player.State Int,
    maxWidth: androidx.compose.ui.unit.Dp, // Static screenWidth
    maxHeight: androidx.compose.ui.unit.Dp, // Static screenHeight
    collapsedBound: androidx.compose.ui.unit.Dp,
    horizontalOffsetProvider: () -> Float,
    isAppleMusic: Boolean,
    useNewPlayerDesign: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current

    // Target boundaries for MiniPlayer (pill at bottom)
    val miniHeight = 64.dp
    
    // Stable, static collapsed coordinates relative to the top-left of the capsule container (y = 0)
    val miniArtSize = 48.dp
    val miniArtX = 12.dp
    val miniArtY = (miniHeight - miniArtSize) / 2 // 8.dp

    val miniTextX = miniArtX + miniArtSize + 12.dp // 72.dp
    val miniTextY = 14.dp

    val miniPlaySize = 36.dp
    val miniPlayX = maxWidth - 48.dp - 92.dp // Starts at maxWidth - 140.dp
    val miniPlayY = (miniHeight - miniPlaySize) / 2 // 14.dp

    val miniSkipSize = 36.dp
    val miniSkipX = maxWidth - 48.dp - 52.dp // Starts at maxWidth - 100.dp
    val miniSkipY = (miniHeight - miniSkipSize) / 2 // 14.dp

    // Target boundaries for Full Player
    val fullWidth = if (isAppleMusic && !useNewPlayerDesign) maxWidth else maxWidth - (PlayerHorizontalPadding * 2)
    val fullArtHeight = if (isAppleMusic && !useNewPlayerDesign) maxHeight * 0.65f else fullWidth
    val fullArtX = if (isAppleMusic && !useNewPlayerDesign) 0.dp else PlayerHorizontalPadding
    val fullArtY = if (isAppleMusic && !useNewPlayerDesign) 0.dp else miniArtY

    val fullTextX = PlayerHorizontalPadding
    val fullTextY = if (isAppleMusic && !useNewPlayerDesign) fullArtHeight + 24.dp else miniTextY
    
    val fullPlayX = (maxWidth / 2) - 18.dp // Center of screen - half of mini button size (36.dp / 2 = 18.dp)
    val fullPlayY = maxHeight - 200.dp // Approx position above bottom sheet navigation

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val progress = progressProvider()
        val miniTop = this.maxHeight - miniHeight
        val artWidth = lerp(miniArtSize, fullWidth, progress)
        val artHeight = lerp(miniArtSize, fullArtHeight, progress)
        val artX = lerp(miniArtX, fullArtX, progress)
        val artY = lerp(miniTop + miniArtY, fullArtY, progress)
        val textX = lerp(miniTextX, fullTextX, progress)
        val textY = lerp(miniTop + miniTextY, fullTextY, progress)
        val playX = lerp(miniPlayX, fullPlayX, progress)
        val playY = lerp(miniTop + miniPlayY, fullPlayY, progress)
        val skipX = lerp(miniSkipX, fullPlayX + 80.dp, progress)
        val skipY = lerp(miniTop + miniSkipY, fullPlayY, progress)
        val horizontalOffset = with(density) { horizontalOffsetProvider().toDp() }
        val miniTextAlpha = (1f - (progress / 0.22f)).coerceIn(0f, 1f)

        Box(Modifier.fillMaxSize()) {
            // 1. Album Art
            if (mediaMetadata?.thumbnailUrl != null) {
                Box(
                    modifier = Modifier
                        .offset(x = artX + horizontalOffset, y = artY)
                        .size(artWidth, artHeight)
                        .graphicsLayer {
                            val artCornerRadius = when {
                                isAppleMusic && !useNewPlayerDesign -> lerp(16.dp, 0.dp, progress)
                                isAppleMusic && useNewPlayerDesign -> lerp(16.dp, 8.dp, progress)
                                else -> ThumbnailCornerRadius
                            }
                            clip = true
                            shape = RoundedCornerShape(artCornerRadius)
                        }
                        .drawWithContent {
                            drawContent()
                            val progress = progressProvider()
                            if (isAppleMusic && !useNewPlayerDesign && progress > 0f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.Black,
                                            0.75f to Color.Black,
                                            0.92f to Color.Black.copy(alpha = 1f - progress * 0.6f),
                                            1.00f to Color.Black.copy(alpha = 1f - progress)
                                        )
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaMetadata.thumbnailUrl)
                            .size(1200, 1200)
                            .allowHardware(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. Title and Subtitle
            if (mediaMetadata != null) {
                Column(
                    modifier = Modifier
                        .offset(x = textX, y = textY)
                        .width(maxWidth - miniTextX - 112.dp)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            transformOrigin = TransformOrigin(0f, 0f) // Scale from top-left
                            alpha = miniTextAlpha
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.00f to Color.Transparent,
                                        0.08f to Color.Black,
                                        0.88f to Color.Black,
                                        1.00f to Color.Transparent
                                    )
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = mediaMetadata.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    
                    if (mediaMetadata.artists.isNotEmpty()) {
                        Text(
                            text = mediaMetadata.artists.joinToString { it.name },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }

            // 3. Play/Pause Button
            Box(
                modifier = Modifier
                    .offset(x = playX, y = playY)
                    .requiredSize(miniPlaySize)
                    .graphicsLayer {
                        val playScale = 1f + (progress * 1.0f) // Scale from 36.dp to 72.dp
                        scaleX = playScale
                        scaleY = playScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f) // Scale from center
                        
                        alpha = (1f - (progress / 0.5f)).coerceIn(0f, 1f)
                    }
                    .clickable { playerConnection?.togglePlayPause() }
            ) {
                Icon(
                    painter = painterResource(
                        if (playbackState == Player.STATE_ENDED) R.drawable.replay
                        else if (isPlaying) R.drawable.pause 
                        else R.drawable.play
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp).align(Alignment.Center)
                )
            }

            // 4. Skip Next Button
            Box(
                modifier = Modifier
                    .offset(x = skipX, y = skipY)
                    .requiredSize(miniSkipSize)
                    .graphicsLayer {
                        val playScale = 1f + (progress * 1.0f)
                        scaleX = playScale
                        scaleY = playScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f) // Scale from center
                        
                        alpha = (1f - (progress / 0.5f)).coerceIn(0f, 1f)
                    }
                    .clickable { playerConnection?.player?.seekToNext() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp).align(Alignment.Center)
                )
            }
        }
    }
}
