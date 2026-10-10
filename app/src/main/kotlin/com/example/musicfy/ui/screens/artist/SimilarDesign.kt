// SimilarDesign.kt
//
// "similar to <artist>" and Home's Artist list share one page: a blurred band from an artist's
// photo with the title on it, START OF THE LINE, then everything grouped by kind: the artists,
// the playlists they're on, their songs, their newest records.

package com.example.musicfy.ui.screens.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.example.musicfy.LocalDetailAccentColor
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.BoneColor
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.HomeCardHighlight
import com.example.musicfy.ui.component.HomeCardSpacing
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.HomeRowHeight
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.detail.FeaturedArtistUi
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistInset
import com.example.musicfy.ui.component.detail.PlaylistSectionTitle
import com.example.musicfy.ui.component.detail.PlaylistTracking
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.ReceiptCenterStyle
import com.example.musicfy.ui.component.detail.TrackSubtitleStyle
import com.example.musicfy.ui.component.detail.TrackTitleStyle
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.theme.rememberCoverThemeColor
import com.example.musicfy.ui.utils.SnapLayoutInfoProvider
import com.example.musicfy.ui.utils.SoftBlurTransformation
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.SimilarGroups
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem

private val SmallLineStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    letterSpacing = PlaylistTracking,
)

private val BigLineStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    lineHeight = 46.sp,
    letterSpacing = (-0.07).em,
)

private val TallCardShape = RoundedCornerShape(20.dp)

/**
 * the page both screens are. [groups] is null while the first answer is on its way, and
 * [deepLoading] holds bones where the songs and records will land.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SimilarPage(
    navController: NavController,
    bandImageUrl: String?,
    smallLine: String,
    bigLine: String,
    groups: SimilarGroups?,
    deepLoading: Boolean,
    artistsTitle: String,
    playlistsTitle: String = "Featured Playlist",
    songsTitle: String = "Featured songs",
    albumsTitle: String = "New from them",
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val profilePic by rememberPreference(ProfilePicUriKey, defaultValue = "")

    val themeColor by rememberCoverThemeColor(bandImageUrl)
    val background = animateColorAsState(
        targetValue = themeColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(600),
        label = "similarTheme",
    )
    val accent = LocalDetailAccentColor.current
    SideEffect { accent.value = themeColor }
    DisposableEffect(Unit) { onDispose { accent.value = null } }

    val bandHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    val bandHeightPx = with(LocalDensity.current) { bandHeight.toPx() }
    val listState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val collapsed by remember(listState, bandHeightPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > bandHeightPx * 0.7f
        }
    }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed) {
        morph.animateTo(
            targetValue = if (collapsed) 1f else 0f,
            animationSpec = spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    val glassState = remember { GlassState() }

    fun open(item: YTItem) {
        when (item) {
            is SongItem -> if (item.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
            }
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    fun showMenu(item: YTItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    onImportedPlaylist = { navController.navigate("local_playlist/$it") },
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(background.value) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { morph.value > 0.01f })
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = maxWidth
                LazyColumn(
                    state = listState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "band", contentType = "band") {
                        SimilarTitleBand(
                            imageUrl = bandImageUrl,
                            smallLine = smallLine,
                            bigLine = bigLine,
                            height = bandHeight,
                            background = { background.value },
                        )
                    }
                    item(key = "start_of_line") {
                        StartOfTheLine(modifier = Modifier.padding(top = 6.dp))
                    }

                    if (groups == null) {
                        item(key = "loading") {
                            Column(modifier = Modifier.padding(top = 20.dp)) { PlaylistTrackBones(rows = 6) }
                        }
                        return@LazyColumn
                    }

                    if (groups.artists.isNotEmpty()) {
                        item(key = "artists") {
                            FeaturedArtistsRow(
                                artists = groups.artists.map {
                                    FeaturedArtistUi(id = it.id, name = it.title, thumbnailUrl = it.thumbnail?.resize(300, 300))
                                },
                                onArtistClick = { artist -> navController.navigate("artist/${artist.id}") },
                                title = artistsTitle,
                                showChevron = false,
                                modifier = Modifier
                                    .padding(top = 22.dp)
                                    .revealOnAppear(key = "artists", seenState = revealSeen),
                            )
                        }
                    }

                    if (groups.playlists.isNotEmpty()) {
                        item(key = "playlists_title") {
                            PlaylistSectionTitle(
                                title = playlistsTitle,
                                showChevron = false,
                                modifier = Modifier.padding(top = 30.dp, bottom = 9.dp),
                            )
                        }
                        item(key = "playlists") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = PlaylistInset),
                                horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                                modifier = Modifier.revealOnAppear(key = "playlists", seenState = revealSeen),
                            ) {
                                items(groups.playlists, key = { it.id }) { playlist ->
                                    TallPlaylistCard(
                                        playlist = playlist,
                                        onClick = { open(playlist) },
                                        onLongClick = { showMenu(playlist) },
                                    )
                                }
                            }
                        }
                    }

                    if (groups.songs.isNotEmpty() || deepLoading) {
                        item(key = "songs_title") {
                            PlaylistSectionTitle(
                                title = songsTitle,
                                showChevron = false,
                                modifier = Modifier.padding(top = 30.dp, bottom = 8.dp),
                            )
                        }
                        item(key = "songs") {
                            if (groups.songs.isEmpty()) {
                                PlaylistTrackBones(rows = 3)
                            } else {
                                PagedSongGrid(
                                    songs = groups.songs,
                                    screenWidth = screenWidth,
                                    activeId = mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    onClick = ::open,
                                    onMenu = ::showMenu,
                                    modifier = Modifier.revealOnAppear(key = "songs", seenState = revealSeen),
                                )
                            }
                        }
                    }

                    if (groups.albums.isNotEmpty()) {
                        item(key = "albums_title") {
                            PlaylistSectionTitle(
                                title = albumsTitle,
                                showChevron = false,
                                modifier = Modifier.padding(top = 30.dp, bottom = 8.dp),
                            )
                        }
                        item(key = "albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = PlaylistInset),
                                horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                                modifier = Modifier.revealOnAppear(key = "albums", seenState = revealSeen),
                            ) {
                                items(groups.albums, key = { it.id }) { album ->
                                    HomeCoverCard(
                                        title = album.title,
                                        subtitle = listOfNotNull(
                                            album.artists?.joinToString { it.name }?.ifBlank { null },
                                            album.year?.toString(),
                                        ).joinToString(" · ").ifBlank { null },
                                        thumbnailUrl = album.thumbnail,
                                        sharedElementKey = "album-${album.id}",
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.combinedClickable(
                                            interactionSource = null,
                                            indication = HomeCardHighlight,
                                            onClick = { open(album) },
                                            onLongClick = { showMenu(album) },
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (!deepLoading && groups.artists.isEmpty() && groups.playlists.isEmpty() &&
                        groups.songs.isEmpty() && groups.albums.isEmpty()
                    ) {
                        item(key = "empty") {
                            Text(
                                text = "Nothing to show here yet",
                                style = TrackSubtitleStyle.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = PlaylistInset, vertical = 28.dp),
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(50.dp))
                    }
                }
            }
        }

        ArtistTopBar(
            glassState = glassState,
            morph = { morph.value },
            title = bigLine,
            background = background,
            profileImage = profilePic.ifBlank { null },
            onBackClick = { navController.navigateUp() },
            onBackLongClick = { navController.backToMain() },
            onProfileClick = { navController.navigateToTab("settings") },
        )
    }
}

/**
 * the photo, small and heavily softened (a 64px copy blurred once, so it looks the same on every
 * Android version and costs nothing to draw), fading into the page under the two title lines
 */
@Composable
private fun SimilarTitleBand(
    imageUrl: String?,
    smallLine: String,
    bigLine: String,
    height: Dp,
    background: () -> Color,
) {
    val context = LocalContext.current
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(256)
            .transformations(SoftBlurTransformation(width = 64, radius = 3))
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val h = size.height
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                        endY = h * 0.35f,
                    )
                    onDrawBehind {
                        val bg = background()
                        drawRect(top)
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.6f to bg.copy(alpha = 0.72f),
                                1f to bg,
                                startY = h * 0.38f,
                                endY = h,
                            ),
                            topLeft = Offset(0f, h * 0.38f),
                        )
                    }
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = PlaylistInset - 3.dp, end = PlaylistInset, bottom = 14.dp)
        ) {
            Text(text = smallLine, style = SmallLineStyle, color = Color.White, maxLines = 1)
            Text(
                text = bigLine,
                style = BigLineStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** the receipt rule at the top: the page's END OF LINE, the other way round */
@Composable
internal fun StartOfTheLine(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset)
    ) {
        ReceiptLine()
        Text(
            text = "START OF THE LINE",
            style = ReceiptCenterStyle,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        ReceiptLine()
    }
}

@Composable
private fun ReceiptLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.24f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.75.dp.toPx(),
                )
            }
    )
}

/** a tall card: the cover fills it, and the name sits in a dark fade at the foot with a chevron */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TallPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 200.dp, height = 245.dp)
            .containerTransformSource(key = "playlist-${playlist.id}", cornerRadius = 20.dp, coverUrl = playlist.thumbnail)
            .clip(TallCardShape)
            .background(BoneColor)
            .combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        AsyncImage(
            model = playlist.thumbnail?.resize(544, 544),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        0.78f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, bottom = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = TrackTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(playlist.author?.name, playlist.songCountText).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = TrackSubtitleStyle,
                        color = Color.White.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Home's paged rows: columns of three songs, the next column peeking in at the edge */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedSongGrid(
    songs: List<SongItem>,
    screenWidth: Dp,
    activeId: String?,
    isPlaying: Boolean,
    onClick: (SongItem) -> Unit,
    onMenu: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = minOf(3, songs.size).coerceAtLeast(1)
    val gridState = rememberLazyGridState()
    val snap = remember(gridState) {
        SnapLayoutInfoProvider(lazyGridState = gridState, positionInLayout = { _, _ -> 0f })
    }
    val columnWidth = if (screenWidth * 0.475f >= 320.dp) screenWidth * 0.475f else screenWidth * 0.76f
    LazyHorizontalGrid(
        state = gridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snap),
        contentPadding = PaddingValues(horizontal = PlaylistInset),
        horizontalArrangement = Arrangement.spacedBy(39.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(HomeRowHeight * rows + 6.dp * (rows - 1)),
    ) {
        items(items = songs, key = { it.id }) { song ->
            Box(modifier = Modifier.width(columnWidth)) {
                HomeTrackRow(
                    thumbnailUrl = song.thumbnail,
                    title = song.title,
                    subtitle = song.artists.joinToString { it.name },
                    isActive = song.id == activeId,
                    isPlaying = isPlaying,
                    onClick = { onClick(song) },
                    onLongClick = { onMenu(song) },
                    onMoreClick = { onMenu(song) },
                )
            }
        }
    }
}
