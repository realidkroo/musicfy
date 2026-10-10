// ArtistDesign.kt
//
// The artist page's own pieces: the banner (Apple's motion over YouTube's photo, with Home's blur
// bands, riding up and zooming like a playlist cover), the round photo and name or name logo, the
// Play / like / Subscribe row, the release card that slides over to the bio, and the top bar.

package com.example.musicfy.ui.screens.artist

import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicfy.R
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.component.BoneColor
import com.example.musicfy.ui.component.DownscaledBlur
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.ScrollAwayCover
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.detail.CircleAction
import com.example.musicfy.ui.component.detail.PillContent
import com.example.musicfy.ui.component.detail.PillFill
import com.example.musicfy.ui.component.detail.PlayPill
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistStatsStyle
import com.example.musicfy.ui.component.detail.PlaylistTracking
import com.example.musicfy.ui.component.detail.SectionTitleStyle
import com.example.musicfy.ui.component.detail.TrackShape
import com.example.musicfy.ui.component.detail.TrackSubtitleStyle
import com.example.musicfy.ui.component.detail.TrackTitleStyle
import com.example.musicfy.ui.component.pressBounce
import com.example.musicfy.ui.player.CanvasArtworkPlayer
import com.example.musicfy.ui.screens.search.SearchAvatar
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.viewmodels.ArtistRelease

// the round photo sits this far down the banner, and the page's header starts there
internal const val ArtistAvatarAt = 0.45f
internal val ArtistAvatarSize = 96.dp

internal val ArtistNameStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = PlaylistTracking,
)

private val ArtistBarTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.04).em,
)

private val CardLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = (-0.02).em,
)

internal val AboutTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.04).em,
)

internal val AboutBodyStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.01).em,
)

private val ArtistCardShape = RoundedCornerShape(22.dp)

/**
 * the banner pinned behind the list. the YouTube photo is there from the first frame, Apple's
 * still fades in over it and the loop over that, so nothing ever pops. the media is recorded once
 * and drawn again, blurred, into a band at the top and one at the bottom, as Home's hero does, and
 * the whole frame rides up, zooms and softens away with the scroll like a playlist's cover.
 */
@Composable
internal fun ArtistBanner(
    photoUrl: String?,
    stillUrl: String?,
    motionUrl: String?,
    playMotion: Boolean,
    edgeBlur: Boolean,
    height: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
) {
    val context = LocalContext.current
    val mediaLayer = rememberGraphicsLayer()
    val photoRequest = remember(photoUrl) {
        ImageRequest.Builder(context).data(photoUrl).crossfade(250).build()
    }
    var stillShown by remember(stillUrl) { mutableStateOf(false) }
    val stillAlpha by animateFloatAsState(
        targetValue = if (stillShown) 1f else 0f,
        animationSpec = tween(450),
        label = "artistStill",
    )

    ScrollAwayCover(
        progress = scrollProgress,
        follow = -0.8f,
        // the loop is still once the page moves, so the soft copy blurs a still frame
        softFrom = 0.02f,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (edgeBlur) {
                            Modifier.drawWithContent {
                                mediaLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(mediaLayer)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (stillUrl != null && stillUrl != photoUrl) {
                    AsyncImage(
                        model = stillUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { stillShown = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = stillAlpha
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            },
                    )
                }
                if (motionUrl != null) {
                    CanvasArtworkPlayer(
                        primaryUrl = motionUrl,
                        fallbackUrl = null,
                        isPlaying = playMotion,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (edgeBlur) {
                BannerBlurBand(mediaLayer, atTop = true, heightFraction = 0.4f, radius = 22f)
                BannerBlurBand(mediaLayer, atTop = false, heightFraction = 0.55f, radius = 30f)
            }
            BannerScrims(background)
        }
    }
}

@Composable
private fun BoxScope.BannerBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
        }
    }
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
    }
}

/** a little dim for the white text, darker under the status bar, and the foot sinking into the page */
@Composable
private fun BoxScope.BannerScrims(background: State<Color>) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val h = size.height
                val top = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    endY = h * 0.3f,
                )
                onDrawBehind {
                    val bg = background.value
                    drawRect(Color.Black.copy(alpha = 0.14f))
                    drawRect(top, size = Size(size.width, h * 0.3f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to bg.copy(alpha = 0.55f),
                            0.82f to bg.copy(alpha = 0.92f),
                            1f to bg,
                            startY = h * 0.42f,
                            endY = h,
                        ),
                        topLeft = Offset(0f, h * 0.42f),
                        size = Size(size.width, h * 0.58f),
                    )
                }
            }
    )
}

/** the round YouTube photo, a bone until it loads */
@Composable
internal fun ArtistAvatar(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(ArtistAvatarSize)
            .clip(CircleShape)
            .background(BoneColor),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * the artist's name, or Apple's drawing of it when there is one. the logo is fitted inside 260dp
 * wide and 72dp tall at its own proportions, so a long wordmark and a squarer monogram both read
 * at about the size the name would; the name stays until the logo has actually loaded.
 */
@Composable
internal fun ArtistName(
    name: String,
    logoUrl: String?,
    logoAspect: Float?,
    modifier: Modifier = Modifier,
) {
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    val logoAlpha by animateFloatAsState(if (logoLoaded) 1f else 0f, tween(350), label = "artistLogo")
    Box(modifier = modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))) {
        if (logoUrl != null) {
            val aspect = (logoAspect ?: 3f).coerceIn(0.6f, 12f)
            val width = min(260.dp, 72.dp * aspect)
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onSuccess = { logoLoaded = true },
                modifier = Modifier
                    .size(width = width, height = width / aspect)
                    .graphicsLayer { alpha = logoAlpha },
            )
        }
        if (!logoLoaded) {
            Text(
                text = name,
                style = ArtistNameStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "12.3M subscribers · 21.4M monthly audience" */
internal fun artistStats(subscribers: String?, monthly: String?): String =
    listOfNotNull(
        subscribers?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            if (text.any { it.isLetter() && it !in "KMBkmb" }) text else "$text subscribers"
        },
        monthly?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            if (text.count { it.isLetter() } > 2) text else "$text monthly listeners"
        },
    ).joinToString(" · ")

/**
 * Play and the heart. signed in to YouTube, a Subscribe pill joins them and Play gives up its
 * label to make room: the pill shrinks to a round button and the new one grows in beside it.
 */
@Composable
internal fun ArtistActions(
    isPlaying: Boolean,
    liked: Boolean,
    subscribed: Boolean?,
    showSubscribe: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (showSubscribe) {
            CircleAction(
                icon = if (isPlaying) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlay,
            )
        } else {
            PlayPill(isPlaying = isPlaying, onClick = onPlay)
        }
        Spacer(Modifier.width(if (showSubscribe) 4.dp else 9.dp))
        CircleAction(
            icon = if (liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
            contentDescription = if (liked) "Liked" else "Like",
            onClick = onLike,
        )
        AnimatedVisibility(
            visible = showSubscribe,
            enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) + fadeIn(tween(160)),
            exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120)),
        ) {
            Row {
                Spacer(Modifier.width(9.dp))
                SubscribePill(subscribed = subscribed == true, onClick = onSubscribe)
            }
        }
    }
}

@Composable
private fun SubscribePill(subscribed: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val fill by androidx.compose.animation.animateColorAsState(
        if (subscribed) Color.White.copy(alpha = 0.16f) else PillFill,
        tween(220),
        label = "subscribeFill",
    )
    val content = if (subscribed) Color.White else PillContent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .drawBehind { drawRect(fill) }
            .border(1.dp, Color.White.copy(alpha = if (subscribed) 0.22f else 0f), RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(start = 14.dp, end = 17.dp)
    ) {
        Icon(
            painter = painterResource(if (subscribed) R.drawable.subscribed else R.drawable.subscribe),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (subscribed) "Subscribed" else "Subscribe",
            style = TrackTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = content,
        )
    }
}

/**
 * the release card and, a swipe to the right, the bio. the next card peeks in at the edge so it's
 * plain there's more; a tap on the bio card goes down to the full text at the foot of the page.
 */
@Composable
internal fun ArtistReleasePager(
    release: ArtistRelease?,
    bio: String?,
    artistName: String,
    releaseSaved: Boolean,
    isReleasePlaying: Boolean,
    onReleaseClick: () -> Unit,
    onReleasePlay: () -> Unit,
    onReleaseSave: () -> Unit,
    onBioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = buildList {
        if (release != null) add(0)
        if (!bio.isNullOrBlank()) add(1)
    }
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState { pages.size }
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(start = PlaylistInset - 3.dp, end = if (pages.size > 1) 44.dp else PlaylistInset - 3.dp),
        pageSpacing = 12.dp,
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth(),
    ) { index ->
        when (pages[index]) {
            0 -> ReleaseCard(
                release = release!!,
                saved = releaseSaved,
                isPlaying = isReleasePlaying,
                onClick = onReleaseClick,
                onPlay = onReleasePlay,
                onSave = onReleaseSave,
            )
            else -> BioCard(name = artistName, bio = bio.orEmpty(), onClick = onBioClick)
        }
    }
}

@Composable
private fun ArtistCard(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(ArtistCardShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), ArtistCardShape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        content = content,
    )
}

@Composable
private fun ReleaseCard(
    release: ArtistRelease,
    saved: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
) {
    ArtistCard(onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = release.label,
                    style = CardLabelStyle,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (release.dateLabel != null) {
                    Text(
                        text = release.dateLabel,
                        style = PlaylistStatsStyle,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        // the album opens growing out of this cover, as from Home
                        .containerTransformSource(
                            key = release.album?.let { "album-${it.id}" },
                            cornerRadius = 9.dp,
                            coverUrl = release.thumbnailUrl,
                        )
                        .clip(TrackShape)
                        .background(BoneColor)
                ) {
                    AsyncImage(
                        model = release.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 11.dp)
                ) {
                    Text(
                        text = release.title,
                        style = TrackTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = release.subtitle,
                        style = TrackSubtitleStyle,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleAction(
                    icon = if (isPlaying) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play,
                    contentDescription = "Play",
                    onClick = onPlay,
                )
                if (release.album != null) {
                    Spacer(Modifier.width(4.dp))
                    CircleAction(
                        icon = if (saved) R.drawable.library_add_check else R.drawable.add,
                        contentDescription = if (saved) "In your library" else "Add to library",
                        onClick = onSave,
                    )
                }
            }
        }
    }
}

@Composable
private fun BioCard(name: String, bio: String, onClick: () -> Unit) {
    ArtistCard(onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "About $name",
                style = CardLabelStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = bio,
                style = AboutBodyStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = Color.White.copy(alpha = 0.74f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Top songs ••• >": the line opens the shelf's own page, the dots a small menu of what the old
 * header buttons did (shuffle, radio, the link)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = PlaylistInset - 4.dp, end = PlaylistInset),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(text = title, style = SectionTitleStyle, color = Color.White, maxLines = 1)
            if (menu == null && onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (menu != null) {
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = "More",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Color(0xFF1E1F23),
                ) {
                    menu { menuOpen = false }
                }
            }
            if (onClick != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClick)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = "See all",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArtistMenuItem(text: String, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = text, style = TrackTitleStyle, color = Color.White) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}

/**
 * the bar over the page. at rest it's the back button and your avatar on the banner; once the
 * name has gone under it, it frosts over and the name settles in beside the back button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    title: String,
    background: State<Color>,
    profileImage: String?,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val density = LocalDensity.current
    val showChrome by remember { derivedStateOf { morph() > 0.01f } }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showChrome) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * morph().coerceIn(0f, 1f) },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 96.dp)
                    .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp)
                .height(47.dp)
                .padding(start = 26.dp, end = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        val m = morph().coerceIn(0f, 1f)
                        drawCircle(Color.Black.copy(alpha = 0.28f + 0.1f * m))
                    }
                    .combinedClickable(onClick = onBackClick, onLongClick = onBackLongClick)
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .offset(x = 2.dp),
                )
            }
            Text(
                text = title,
                style = ArtistBarTitleStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp)
                    .graphicsLayer {
                        val m = morph()
                        alpha = m.coerceIn(0f, 1f)
                        translationY = with(density) { 10.dp.toPx() } * (1f - m)
                    },
            )
            SearchAvatar(imageUrl = profileImage, onClick = onProfileClick, size = 32.dp)
        }
    }
}

/** stand-ins for the header while YouTube answers */
@Composable
internal fun ArtistHeaderBones(modifier: Modifier = Modifier) {
    com.example.musicfy.ui.component.BonesHost(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = PlaylistInset - 4.dp)) {
            com.example.musicfy.ui.component.Bone(width = ArtistAvatarSize, height = ArtistAvatarSize, shape = CircleShape)
            Spacer(Modifier.height(16.dp))
            com.example.musicfy.ui.component.Bone(width = 190.dp, height = 30.dp)
            Spacer(Modifier.height(8.dp))
            com.example.musicfy.ui.component.Bone(width = 150.dp, height = 10.dp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                com.example.musicfy.ui.component.Bone(width = 92.dp, height = 33.dp, shape = RoundedCornerShape(50))
                com.example.musicfy.ui.component.Bone(width = 33.dp, height = 33.dp, shape = CircleShape)
            }
        }
    }
}
