// ArtistListCard.kt

package com.example.musicfy.ui.component

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.ui.theme.PlayerColorExtractor
import com.music.innertube.models.YTItem

@Composable
fun ArtistListCard(
    group: ArtistGroup,
    onClick: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(group) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val thumbnails = mutableListOf<String>()
                group.artistThumbnailUrl?.let { thumbnails.add(it) }
                thumbnails.addAll(group.items.take(2).mapNotNull { it.thumbnail })

                val colorsList = mutableListOf<Color>()
                for (thumb in thumbnails) {
                    val request = ImageRequest.Builder(context)
                        .data(thumb)
                        .size(100, 100)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val bitmap = result.image?.toBitmap()

                    if (bitmap != null) {
                        val palette = Palette.from(bitmap)
                            .maximumColorCount(4)
                            .resizeBitmapArea(100 * 100)
                            .generate()
                        val colors = PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = android.graphics.Color.DKGRAY
                        )
                        colorsList.addAll(colors.take(2))
                    }
                }
                if (colorsList.isNotEmpty()) {
                    extractedColors = colorsList.distinct().take(3)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
    ) {
        val premiumDarkColors = listOf(
            listOf(Color(0xFF2B1B38), Color(0xFF151521), Color.Black),
            listOf(Color(0xFF1B2838), Color(0xFF0F1521), Color.Black),
            listOf(Color(0xFF381B24), Color(0xFF1F0F15), Color.Black),
            listOf(Color(0xFF1B382D), Color(0xFF0F1F17), Color.Black),
            listOf(Color(0xFF2D2B55), Color(0xFF151521), Color.Black)
        )
        val fallbackColors = premiumDarkColors[(group.artistName.hashCode() and 0x7FFFFFFF) % premiumDarkColors.size]

        val color1 by animateColorAsState(
            targetValue = extractedColors.getOrNull(0)?.let {
                Color(androidx.core.graphics.ColorUtils.blendARGB(it.toArgb(), android.graphics.Color.BLACK, 0.65f))
            } ?: fallbackColors[0],
            animationSpec = tween(800), label = ""
        )
        val color2 by animateColorAsState(
            targetValue = extractedColors.getOrNull(1)?.let {
                Color(androidx.core.graphics.ColorUtils.blendARGB(it.toArgb(), android.graphics.Color.BLACK, 0.75f))
            } ?: fallbackColors[1],
            animationSpec = tween(800), label = ""
        )
        val color3 by animateColorAsState(
            targetValue = extractedColors.getOrNull(2)?.let {
                Color(androidx.core.graphics.ColorUtils.blendARGB(it.toArgb(), android.graphics.Color.BLACK, 0.85f))
            } ?: fallbackColors[2],
            animationSpec = tween(800), label = ""
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(color1, color2, color3)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (group.artistThumbnailUrl != null) {
                        AsyncImage(
                            model = group.artistThumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape),
                        )
                    } else {
                        AlbumGradient(thumbnailUrl = null, modifier = Modifier.size(52.dp).clip(CircleShape))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Similar to",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Text(
                        text = group.artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 24.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
            ) {
                group.items.take(3).forEach { item ->
                    Box(
                        modifier = Modifier
                            .requiredSize(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AlbumGradient(thumbnailUrl = item.thumbnail, modifier = Modifier.fillMaxSize())
                        item.thumbnail?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .offset(y = 20.dp)
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
            ) {
                group.items.drop(3).take(2).forEach { item ->
                    Box(
                        modifier = Modifier
                            .requiredSize(160.dp)
                            .clip(CircleShape)
                    ) {
                        AlbumGradient(thumbnailUrl = item.thumbnail, modifier = Modifier.fillMaxSize())
                        item.thumbnail?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
