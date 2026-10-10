// ArtistSubpageKit.kt
//
// The pages under an artist (top songs, albums, singles, videos, playlists, the artists around
// them) are playlist pages with the artist's banner as their cover. These are the parts they share:
// the title line with the grid / list toggles, the two-up grid, and its square and video cells.

package com.example.musicfy.ui.screens.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.ui.component.BoneColor
import com.example.musicfy.ui.component.HomeCardHighlight
import com.example.musicfy.ui.component.ItemThumbnail
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.detail.PillContent
import com.example.musicfy.ui.component.detail.PillFill
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistStatsStyle
import com.example.musicfy.ui.component.detail.PlaylistTitleStyle
import com.example.musicfy.ui.component.detail.TrackSubtitleStyle
import com.example.musicfy.ui.component.detail.TrackTitleStyle
import com.example.musicfy.ui.component.detail.playlistCoverHeight
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.ArtistPageCache

private val CellShape = RoundedCornerShape(18.dp)
internal val SubpageGridGap = 14.dp

/** the artist's name and banner still, for a page under them: this run's artist page, else the library */
@Composable
internal fun rememberArtistBanner(artistId: String?): Pair<String?, String?> {
    val cached = artistId?.let { ArtistPageCache.header(it) }
    if (cached != null) return cached.name to (cached.bannerUrl ?: cached.avatarUrl)
    val database = LocalDatabase.current
    val stored by remember(artistId) {
        artistId?.let { database.artist(it) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    return stored?.artist?.name to stored?.artist?.thumbnailUrl?.resize(1200, 1200)
}

/**
 * the title at the foot of the cover with the grid / list toggles at the end of its line, for the
 * pages that have no Play button: albums, singles, videos, playlists
 */
@Composable
internal fun ArtistSubpageHeader(
    title: String,
    subtitle: String?,
    isGrid: Boolean?,
    onGridChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = playlistCoverHeight() * 0.975f),
    ) {
        Column(modifier = Modifier.padding(start = PlaylistInset + 1.dp, end = PlaylistInset - 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = PlaylistTitleStyle,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isGrid != null) {
                    Spacer(Modifier.width(10.dp))
                    LayoutToggle(icon = R.drawable.grid_view, selected = isGrid, description = "Grid") { onGridChange(true) }
                    Spacer(Modifier.width(6.dp))
                    LayoutToggle(icon = R.drawable.list, selected = !isGrid, description = "List") { onGridChange(false) }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = PlaylistStatsStyle,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LayoutToggle(icon: Int, selected: Boolean, description: String, onClick: () -> Unit) {
    val fill by animateColorAsState(
        if (selected) PillFill else Color.White.copy(alpha = 0.16f),
        tween(200),
        label = "layoutToggle",
    )
    val tint by animateColorAsState(if (selected) PillContent else Color.White, tween(200), label = "layoutToggleTint")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .drawBehind { drawCircle(fill) }
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** two cells side by side, the second left empty on an odd last row so both keep their width */
@Composable
internal fun <T> SubpageGridRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SubpageGridGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset - 2.dp),
    ) {
        items.forEach { item ->
            Box(modifier = Modifier.weight(1f)) { cell(item) }
        }
        repeat(2 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** a big rounded square (a circle for an artist) and two lines under it */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubpageSquareCell(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    /** an album or playlist's page grows out of this square, as from Home */
    sharedElementKey: String? = null,
) {
    val shape = if (round) CircleShape else CellShape
    Column(
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .containerTransformSource(key = sharedElementKey, cornerRadius = 18.dp, coverUrl = thumbnailUrl)
                .clip(shape)
                .background(BoneColor)
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl?.resize(544, 544),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = TrackTitleStyle.copy(fontSize = 14.sp),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (round) TextAlign.Center else TextAlign.Start,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = TrackSubtitleStyle,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** a video's 16:9 frame with its title under it, for the two-up video grid */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubpageVideoCell(
    title: String,
    subtitle: String?,
    videoId: String,
    fallbackThumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var useFallback by remember(videoId) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(BoneColor)
        ) {
            AsyncImage(
                model = if (useFallback) fallbackThumbnailUrl else "https://i.ytimg.com/vi/$videoId/hq720.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { if (!useFallback) useFallback = true },
                modifier = Modifier.fillMaxSize(),
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.45f))))
                )
                com.example.musicfy.ui.component.PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = TrackTitleStyle.copy(fontSize = 13.sp),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = TrackSubtitleStyle,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}
