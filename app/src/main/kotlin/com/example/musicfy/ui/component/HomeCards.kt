// HomeCards.kt

package com.example.musicfy.ui.component

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalGridItemSize
import com.example.musicfy.R
import com.example.musicfy.constants.GridItemSize
import com.example.musicfy.ui.theme.InterFontFamily
import coil3.compose.AsyncImage

// spacing and type straight from the Figma home frames (13:11 / 14:212)
val HomeContentInset = 36.dp
val HomeCardSpacing = 15.dp

private val HomeTracking = (-0.06).em

val HomeLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 19.sp,
    letterSpacing = HomeTracking,
)

val HomeTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = HomeTracking,
)

private val HomeCardTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)

private val HomeCardSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 15.sp,
    letterSpacing = (-0.02).em,
)

private val HomeRankStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    letterSpacing = HomeTracking,
)

private val SectionLabelColor = Color.White.copy(alpha = 0.69f)
private val HomeRowShape = RoundedCornerShape(14.dp)
val HomeRowHeight = 54.dp

// pressing anything on Home shows a soft rounded box behind it instead of a square ripple
private val SectionTapMargin = 12.dp
private val SectionTitleHighlight = RoundedHighlight(cornerRadius = 100.dp)
private val TrackRowHighlight = RoundedHighlight(cornerRadius = 22.dp, spillX = 8.dp)

/** for the cover cards: a little bigger than the card all round, cornered to match */
val HomeCardHighlight = RoundedHighlight(cornerRadius = HomeCardCornerRadius + 6.dp, spillX = 7.dp, spillY = 7.dp)

/** square cover size on Home: the Figma 137dp card, or the compact size if the user picked small grids */
@Composable
fun homeCardSize(): Dp =
    if (LocalGridItemSize.current == GridItemSize.BIG) 137.dp else 104.dp

/**
 * "Recently Played >" style header. the whole line is the tap target, so pressing it lights a
 * pill from one side of the screen to the other; the dots play the whole section on their own.
 */
@Composable
fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** No longer drawn: the three dots beside the title are gone. Kept so callers needn't change. */
    @Suppress("UNUSED_PARAMETER") onPlayAllClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SectionTapMargin, end = SectionTapMargin, bottom = 6.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = SectionTitleHighlight,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            // the text still starts on the 36dp inset, inside the wider tap area
            .padding(
                start = HomeContentInset - SectionTapMargin,
                end = HomeContentInset - SectionTapMargin,
                top = 4.dp,
                bottom = 4.dp,
            ),
    ) {
        if (thumbnail != null) {
            thumbnail()
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            if (label != null) {
                Text(
                    text = label,
                    style = HomeCardSubtitleStyle,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = HomeLabelStyle,
                color = SectionLabelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            SectionChevron()
        }
    }
}

@Composable
private fun SectionChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.size(18.dp),
    )
}

/** square (or round, for artists) cover with two lines under it. no outline, on purpose. */
@Composable
fun HomeCoverCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    sharedElementKey: String? = null,
    badges: @Composable RowScope.() -> Unit = {},
    cover: (@Composable (Dp) -> Unit)? = null,
) {
    val size = homeCardSize()
    val shape = if (circular) CircleShape else RoundedCornerShape(HomeCardCornerRadius)

    Column(modifier = modifier.width(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .homeSharedElement(sharedElementKey, coverUrl = thumbnailUrl)
                .clip(shape)
                .background(BoneColor),
        ) {
            if (cover != null) {
                cover(size)
            } else {
                ItemThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = shape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = HomeCardTitleStyle,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badges()
                Text(
                    text = subtitle,
                    style = HomeCardSubtitleStyle,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// the Figma "Concert" card (20:195) is 314 x 247 on a 402 wide frame
const val HomeVideoCardAspect = 314f / 247f

private val HomeVideoTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.03).em,
)

private val HomeVideoSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
)

// dark at the top where the text sits, clear by the middle so the frame itself shows
private val HomeVideoScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.62f),
    0.24f to Color.Black.copy(alpha = 0.34f),
    0.52f to Color.Transparent,
)

/**
 * the wide banner for Live Shows and Music Videos: the video's frame fills the card and the title
 * sits over its top-left corner. the 1280px still goes on first; a video without one falls back to
 * the smaller still YouTube listed it with.
 */
@Composable
fun HomeVideoCard(
    title: String,
    subtitle: String?,
    videoId: String,
    fallbackThumbnailUrl: String?,
    width: Dp,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HomeCardCornerRadius)
    var useFallback by remember(videoId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(width)
            .height(width / HomeVideoCardAspect)
            .clip(shape)
            .background(BoneColor),
    ) {
        AsyncImage(
            model = if (useFallback) fallbackThumbnailUrl else "https://i.ytimg.com/vi/$videoId/hq720.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { if (!useFallback) useFallback = true },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeVideoScrim),
        )
        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp)) {
            Text(
                text = title,
                style = HomeVideoTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    style = HomeVideoSubtitleStyle,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * the "list" and "Rank" rows: 54dp cover, two lines, a small more button. [rank] adds the big number.
 * No [onMoreClick], no button: the Library's lists use these rows and open the menu on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    /** The cover's outline: Home's rounded square, or a circle for an artist. */
    coverShape: Shape = HomeRowShape,
    /** Applied to the cover itself, e.g. to open a playlist growing out of it. */
    coverModifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(HomeRowHeight)
            .combinedClickable(
                interactionSource = null,
                indication = TrackRowHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = HomeRankStyle,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.width(37.dp),
            )
        }
        Box(
            modifier = coverModifier
                .size(HomeRowHeight)
                .clip(coverShape)
                .background(BoneColor),
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = coverShape,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(
                text = title,
                style = HomeCardTitleStyle.copy(fontSize = 15.sp),
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = HomeCardSubtitleStyle,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMoreClick != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onMoreClick),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = "More",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** a section that hasn't arrived yet: header bar plus a row of cards, all bones */
@Composable
fun HomeSectionBones(modifier: Modifier = Modifier) {
    val size = homeCardSize()
    Column(modifier = modifier.fillMaxWidth()) {
        Bone(
            width = 128.dp,
            height = 15.dp,
            modifier = Modifier.padding(start = HomeContentInset),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = HomeContentInset),
        ) {
            repeat(3) {
                Column {
                    Bone(width = size, height = size, shape = RoundedCornerShape(HomeCardCornerRadius))
                    Spacer(Modifier.height(8.dp))
                    Bone(width = size * 0.62f, height = 15.dp)
                    Spacer(Modifier.height(3.dp))
                    Bone(width = size * 0.28f, height = 11.dp)
                }
            }
        }
    }
}
