// HomeScreen.kt

package com.example.musicfy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.ProfileMenuItem
import com.example.musicfy.ui.component.ProfileMenuOverlay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.theme.InterFontFamily
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.TransformOrigin

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.ui.theme.PlayerColorExtractor
import androidx.compose.ui.graphics.toArgb
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.R
import com.example.musicfy.ui.component.ArtistListCard
import com.example.musicfy.ui.component.BonesHost
import com.example.musicfy.ui.component.HeroCarousel
import com.example.musicfy.ui.component.HeroHeightFraction
import com.example.musicfy.ui.component.HomeCardCornerRadius
import com.example.musicfy.ui.component.HomeCardSpacing
import com.example.musicfy.ui.component.HomeContentInset
import com.example.musicfy.ui.component.HomeCardHighlight
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.HomeRowHeight
import com.example.musicfy.ui.component.HomeSectionBones
import com.example.musicfy.ui.component.HomeSectionTitle
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.LikedSongsThumbnail
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.PlaylistThumbnail
import com.example.musicfy.ui.component.RevealSeenState
import com.example.musicfy.ui.component.containerTransformSource
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.menu.AlbumMenu
import com.example.musicfy.ui.menu.ArtistMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.utils.SnapLayoutInfoProvider
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.joinByBullet
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.CommunityPlaylistItem
import com.example.musicfy.viewmodels.HomeViewModel
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import com.example.musicfy.ui.component.navigateToTab

sealed class HomeSection(val id: String) {
    data object RecentlyPlayed : HomeSection("recently_played")
    data object MostPlayed : HomeSection("most_played")
    data object History : HomeSection("history")
    data object DontForgetTheseSongs : HomeSection("dont_forget_these_songs")
    data object AccountPlaylists : HomeSection("account_playlists")
    data object SpeedDial : HomeSection("speed_dial")
    data object FromTheCommunity : HomeSection("from_the_community")
    data object ArtistList : HomeSection("artist_list")
    data object AllTimeHits : HomeSection("all_time_hits")

    data class HomePageSection(val index: Int, val title: String) : HomeSection("home_page_section_${index}_$title")
}

/** a Home row that fades and slides up the first time it scrolls into view */
private fun LazyListScope.homeItem(
    key: String,
    revealSeen: RevealSeenState,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Box(modifier = Modifier.revealOnAppear(key, revealSeen)) {
            content()
        }
    }
}

/** a horizontally scrolling row of cards, lined up with the section title. cards cascade in once. */
@Composable
private fun <T> HomeCardRow(
    rowKey: String,
    items: List<T>,
    revealSeen: RevealSeenState,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            Box(
                modifier = Modifier.revealOnAppear(
                    key = "$rowKey/${key(item)}",
                    seenState = revealSeen,
                    delayMillis = min(index, 4) * 60,
                )
            ) {
                content(item)
            }
        }
    }
}

/** the paged "list" and "Rank" sections: columns of three song rows */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> HomeTrackGrid(
    items: List<T>,
    state: LazyGridState,
    rowSpacing: Dp,
    columnWidth: Dp,
    key: (Int, T) -> Any,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    val rows = min(3, items.size)
    val snapLayoutInfoProvider = remember(state) {
        SnapLayoutInfoProvider(lazyGridState = state, positionInLayout = { _, _ -> 0f })
    }
    LazyHorizontalGrid(
        state = state,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(39.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeRowHeight * rows + rowSpacing * (rows - 1)),
    ) {
        itemsIndexed(items = items, key = key) { index, item ->
            Box(modifier = Modifier.width(columnWidth)) {
                content(index, item)
            }
        }
    }
}

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var extractedColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(item.songs) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val thumbnails = item.songs.take(3).mapNotNull { it.thumbnail }
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

    Card(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .containerTransformSource("playlist-${item.playlist.id}", coverUrl = item.playlist.thumbnail)
            .clip(RoundedCornerShape(HomeCardCornerRadius)),
        shape = RoundedCornerShape(HomeCardCornerRadius),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val premiumDarkColors = listOf(
                listOf(Color(0xFF2B1B38), Color(0xFF151521), Color.Black),
                listOf(Color(0xFF1B2838), Color(0xFF0F1521), Color.Black),
                listOf(Color(0xFF381B24), Color(0xFF1F0F15), Color.Black),
                listOf(Color(0xFF1B382D), Color(0xFF0F1F17), Color.Black),
                listOf(Color(0xFF2D2B55), Color(0xFF151521), Color.Black)
            )
            val fallbackColors = premiumDarkColors[(item.playlist.id.hashCode() and 0x7FFFFFFF) % premiumDarkColors.size]

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = 35f
                            scaleX = 1.3f
                            scaleY = 1.3f
                            translationX = 40.dp.toPx()
                            translationY = -30.dp.toPx()
                        }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.songs.take(3).forEach { song ->
                                AsyncImage(
                                    model = song.thumbnail.resize(256, 256),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.songs.drop(3).take(2).forEach { song ->
                                AsyncImage(
                                    model = song.thumbnail.resize(256, 256),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 28.dp, end = 16.dp, top = 16.dp)
            ) {
                Text(
                    text = item.playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = item.playlist.songCountText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AllTimeHitsCard(
    item: YTItem,
    modifier: Modifier = Modifier,
) {
    val artistName = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: ""
        is ArtistItem -> item.title
        is PlaylistItem -> item.author?.name ?: ""
    }

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(HomeCardCornerRadius))
    ) {
        AsyncImage(
            model = item.thumbnail?.resize(320, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerBottomSheetState = com.example.musicfy.ui.component.LocalPlayerBottomSheetState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayedSongsForHome by viewModel.mostPlayedSongsForHome.collectAsState()
    val recentHistorySongs by viewModel.recentHistorySongs.collectAsState()
    val artistListItems by viewModel.artistListItems.collectAsState()
    val allTimeHits by viewModel.allTimeHits.collectAsState()

    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val lastPlayedSong by viewModel.lastPlayedSong.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    // keepListening stays null until the first local load lands, which also covers the moment
    // before load() has flipped isLoading on
    val isFirstLoad = isLoading || keepListening == null
    val revealSeen = rememberRevealSeenState()

    val isFreshSetup = mediaMetadata == null &&
        lastPlayedSong == null &&
        dailyDiscover.isNullOrEmpty() &&
        keepListening.orEmpty().filterIsInstance<Song>().isEmpty()

    // the gacha's reel: songs you play most, favourites you've drifted from, recommendations and
    // recent plays, all mixed together (only ones with a cover, so the reel never shows a blank)
    val gachaPool = remember(mostPlayedSongsForHome, forgottenFavorites, dailyDiscover, recentHistorySongs, keepListening) {
        buildList {
            mostPlayedSongsForHome?.forEach { add(it.toMediaMetadata()) }
            forgottenFavorites?.forEach { add(it.toMediaMetadata()) }
            dailyDiscover?.forEach { discover -> (discover.recommendation as? SongItem)?.let { add(it.toMediaMetadata()) } }
            recentHistorySongs?.forEach { add(it.toMediaMetadata()) }
            keepListening?.filterIsInstance<Song>()?.forEach { add(it.toMediaMetadata()) }
        }
            .filter { !it.thumbnailUrl.isNullOrEmpty() }
            .distinctBy { it.id }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val profilePicUri by rememberPreference(ProfilePicUriKey, "")

    val localProfileImageUrl = profilePicUri
        .takeIf { it.isNotBlank() }
        ?.let { if (it.contains("://")) it else "file://$it" }
    val profileImageUrl = localProfileImageUrl ?: accountImageUrl
    val context = LocalContext.current
    val profileImageRequest = remember(context, profileImageUrl) {
        profileImageUrl?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(imageUrl)
                .build()
        }
    }

    var profileMenuOpen by remember { mutableStateOf(false) }
    val profileMenuProgress = remember { Animatable(0f) }
    LaunchedEffect(profileMenuOpen) {
        if (profileMenuOpen) {
            profileMenuProgress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 340f))
        } else {
            profileMenuProgress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
    }
    val profileMenuProgressProvider = remember { { profileMenuProgress.value } }
    val showProfileMenuOverlay by remember { derivedStateOf { profileMenuOpen || profileMenuProgress.value > 0.0005f } }
    var profilePillBounds by remember { mutableStateOf<Rect?>(null) }
    var profileAvatarBounds by remember { mutableStateOf<Rect?>(null) }
    val profileMenuItems = remember {
        listOf(
            ProfileMenuItem(icon = R.drawable.settings, label = "Musicfy Settings") {
                navController.navigateToTab("settings")
            },
            ProfileMenuItem(icon = R.drawable.account, label = "Switch Profile") {},
            ProfileMenuItem(icon = R.drawable.logout, label = "Log Out and reset") {},
        )
    }
    BackHandler(enabled = profileMenuOpen) { profileMenuOpen = false }

    val scope = rememberCoroutineScope()

    val lazylistState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val carouselHeightDp = (configuration.screenHeightDp * HeroHeightFraction).dp
    val carouselHeightPx = with(LocalDensity.current) { carouselHeightDp.toPx() }

    val firstItemScrollOffset by remember {
        derivedStateOf {
            if (lazylistState.firstVisibleItemIndex == 0) {
                lazylistState.firstVisibleItemScrollOffset.toFloat()
            } else {
                carouselHeightPx
            }
        }
    }
    val heroScrollProgress by remember {
        derivedStateOf {
            (firstItemScrollOffset / carouselHeightPx).coerceIn(0f, 1f)
        }
    }

    val heroScrollProgressProvider = remember { { heroScrollProgress } }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        // keyed on the page token as well as the scroll position: a short feed never scrolls, so
        // when the fresh token replaced the stale cached one nothing asked for the next page and
        // Home stopped after a section or two
        snapshotFlow {
            val layoutInfo = lazylistState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            homePage?.continuation?.takeIf { lastVisibleIndex >= layoutInfo.totalItemsCount - 3 }
        }
            .distinctUntilChanged()
            .collect { continuation ->
                if (continuation != null) viewModel.loadMoreYouTubeItems(continuation)
            }
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    val allDownloads by LocalDownloadUtil.current.downloads.collectAsState()

    val playSong: (Song) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.togglePlayPause()
        } else {
            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        }
    }
    val showSongMenu: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
        }
    }
    val openLocalPlaylist: (String) -> Unit = { id ->
        if (id == "liked") navController.navigate("auto_playlist/liked") else navController.navigate("local_playlist/$id")
    }

    // every square card on Home that comes from the library
    val localItemCard: @Composable (LocalItem) -> Unit = { localItem ->
        val (onClick, onLongClick) = when (localItem) {
            is Song -> Pair<() -> Unit, () -> Unit>({ playSong(localItem) }, { showSongMenu(localItem) })
            is Album -> Pair<() -> Unit, () -> Unit>(
                { navController.navigate("album/${localItem.id}") },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(originalAlbum = localItem, navController = navController, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Artist -> Pair<() -> Unit, () -> Unit>(
                { navController.navigate("artist/${localItem.id}") },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        ArtistMenu(originalArtist = localItem, coroutineScope = scope, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Playlist -> Pair<() -> Unit, () -> Unit>(
                { openLocalPlaylist(localItem.id) },
                { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
        }
        val playlist = localItem as? Playlist
        HomeCoverCard(
            title = localItem.title,
            subtitle = when (localItem) {
                is Song -> localItem.artists.joinToString(", ") { it.name }
                is Album -> localItem.artists.joinToString(", ") { it.name }
                is Playlist -> pluralStringResource(R.plurals.n_song, localItem.songCount, localItem.songCount)
                is Artist -> null
            },
            // a library playlist's own thumbnailUrl is always null; its covers live in thumbnails
            thumbnailUrl = playlist?.thumbnails?.firstOrNull() ?: localItem.thumbnailUrl,
            circular = localItem is Artist,
            isActive = when (localItem) {
                is Song -> localItem.id == mediaMetadata?.id
                is Album -> localItem.id == mediaMetadata?.album?.id
                else -> false
            },
            isPlaying = isPlaying,
            sharedElementKey = when (localItem) {
                is Album -> "album-${localItem.id}"
                is Playlist -> "playlist-${localItem.id}"
                else -> null
            },
            cover = when {
                playlist == null -> null
                playlist.id == "liked" -> { size ->
                    LikedSongsThumbnail(size = size, shape = RoundedCornerShape(HomeCardCornerRadius))
                }
                else -> { size ->
                    PlaylistThumbnail(
                        thumbnails = playlist.thumbnails,
                        size = size,
                        shape = RoundedCornerShape(HomeCardCornerRadius),
                        placeHolder = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(size / 3)
                            )
                        },
                    )
                }
            },
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        )
    }

    // the same card for anything that came from YouTube
    val ytItemCard: @Composable (YTItem) -> Unit = { item ->
        HomeCoverCard(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> item.artists.joinToString(", ") { it.name }
                is AlbumItem -> joinByBullet(item.artists?.joinToString(", ") { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            thumbnailUrl = item.thumbnail,
            circular = item is ArtistItem,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            sharedElementKey = when (item) {
                is AlbumItem -> "album-${item.id}"
                is PlaylistItem -> "playlist-${item.id}"
                else -> null
            },
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = {
                    when (item) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
                        )
                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> when {
                            item.id == "liked" -> navController.navigate("auto_playlist/liked")
                            item.author?.name == "Local Playlist" -> navController.navigate("local_playlist/${item.id}")
                            else -> navController.navigate("online_playlist/${item.id}")
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is AlbumItem -> YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is ArtistItem -> YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss
                            )
                            is PlaylistItem -> YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss,
                                onImportedPlaylist = { playlistId ->
                                    navController.navigate("local_playlist/$playlistId")
                                }
                            )
                        }
                    }
                }
            ),
        )
    }

    val homeSections by remember {
        derivedStateOf {
            val list = mutableListOf<HomeSection>()

            if (recentlyPlayed?.isNotEmpty() == true) list.add(HomeSection.RecentlyPlayed)
            if (mostPlayedSongsForHome?.isNotEmpty() == true) list.add(HomeSection.MostPlayed)
            if (communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
            if (speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (recentHistorySongs?.isNotEmpty() == true) list.add(HomeSection.History)
            if (artistListItems?.isNotEmpty() == true) list.add(HomeSection.ArtistList)
            if (accountPlaylists?.isNotEmpty() == true || localPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
            if (forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.DontForgetTheseSongs)

            val homePageSections = homePage?.sections.orEmpty()
            homePageSections.firstOrNull()?.let { list.add(HomeSection.HomePageSection(0, it.title)) }
            if (allTimeHits?.isNotEmpty() == true) list.add(HomeSection.AllTimeHits)
            homePageSections.drop(1).forEachIndexed { i, section ->
                list.add(HomeSection.HomePageSection(i + 1, section.title))
            }

            list
        }
    }

    LaunchedEffect(mostPlayedSongsForHome) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    val baseTypography = MaterialTheme.typography
    val homeTypography = remember(baseTypography) {
        val tightenSp = 0.6f
        fun tighten(style: androidx.compose.ui.text.TextStyle) =
            style.copy(letterSpacing = (style.letterSpacing.value - tightenSp).sp)
        baseTypography.copy(
            titleLarge = tighten(baseTypography.titleLarge),
            titleMedium = tighten(baseTypography.titleMedium),
            titleSmall = tighten(baseTypography.titleSmall),
            bodyLarge = tighten(baseTypography.bodyLarge),
            bodyMedium = tighten(baseTypography.bodyMedium),
            bodySmall = tighten(baseTypography.bodySmall),
            labelLarge = tighten(baseTypography.labelLarge),
            labelMedium = tighten(baseTypography.labelMedium),
            labelSmall = tighten(baseTypography.labelSmall),
        )
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = homeTypography,
    ) {
    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            // a song column is 304 of 401 in the Figma, so the next one peeks in from the edge
            val trackColumnWidth = if (maxWidth * 0.475f >= 320.dp) maxWidth * 0.475f else maxWidth * 0.76f

            val homeGlassState = remember { GlassState() }

            val profileMenuGlassState = remember { GlassState() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(profileMenuGlassState, isActive = { profileMenuProgress.value > 0f })
            ) {
                val backgroundColor = Color.Black
                CompositionLocalProvider(
                    com.example.musicfy.ui.component.LocalGridItemPadding provides 0.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.glassRoot(
                            homeGlassState,
                            isActive = { heroScrollProgressProvider() > 0f }
                        ),
                        state = lazylistState,
                        contentPadding = PaddingValues(
                            bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                item(key = "hero") {
                    HeroCarousel(
                        keepListening = keepListening,
                        lastPlayedSong = mediaMetadata ?: lastPlayedSong,
                        dailyDiscover = dailyDiscover,
                        playerConnection = playerConnection,
                        navController = navController,
                        heroScrollProgressProvider = heroScrollProgressProvider,
                        isLoading = isFirstLoad,
                        gachaPool = gachaPool,
                    )
                }

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("speed_dial_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.speed_dial),
                                        onClick = { navController.navigate("section_detail/speed_dial") }
                                    )
                                }
                                homeItem("speed_dial_list", revealSeen) {
                                    HomeCardRow("speed_dial", items, revealSeen, key = { it.id }) { ytItemCard(it) }
                                }
                            }
                        }
                        HomeSection.RecentlyPlayed -> {
                            recentlyPlayed?.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("recently_played_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.recently_played),
                                        onClick = { navController.navigate("section_detail/recently_played") }
                                    )
                                }
                                homeItem("recently_played_list", revealSeen) {
                                    HomeCardRow("recently_played", items, revealSeen, key = { it.id }) { localItemCard(it) }
                                }
                            }
                        }
                        HomeSection.MostPlayed -> {
                            mostPlayedSongsForHome?.takeIf { it.isNotEmpty() }?.let { mostPlayed ->
                                homeItem("most_played_title", revealSeen) {
                                    val mostPlayedTitle = stringResource(R.string.vivi_quick_picks)
                                    HomeSectionTitle(
                                        title = mostPlayedTitle,
                                        onClick = { navController.navigate("section_detail/most_played") },
                                        onPlayAllClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = mostPlayedTitle,
                                                    items = mostPlayed.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    )
                                }
                                homeItem("most_played_list", revealSeen) {
                                    HomeTrackGrid(
                                        items = mostPlayed,
                                        state = quickPicksLazyGridState,
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                    ) { index, song ->
                                        HomeTrackRow(
                                            thumbnailUrl = song.song.thumbnailUrl,
                                            title = song.song.title,
                                            subtitle = song.artists.joinToString(", ") { it.name },
                                            isActive = song.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            rank = index + 1,
                                            onClick = { playSong(song) },
                                            onLongClick = { showSongMenu(song) },
                                            onMoreClick = { showSongMenu(song) },
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.History -> {
                            recentHistorySongs?.takeIf { it.isNotEmpty() }?.let { history ->
                                homeItem("history_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.vivi_on_heavy_rotation),
                                        onClick = { navController.navigate("history") }
                                    )
                                }
                                homeItem("history_list", revealSeen) {
                                    HomeTrackGrid(
                                        items = history,
                                        state = rememberLazyGridState(),
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { index, song -> "history_${index}_${song.id}" },
                                    ) { _, song ->
                                        HomeTrackRow(
                                            thumbnailUrl = song.song.thumbnailUrl,
                                            title = song.song.title,
                                            subtitle = song.artists.joinToString(", ") { it.name },
                                            isActive = song.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            onClick = { playSong(song) },
                                            onLongClick = { showSongMenu(song) },
                                            onMoreClick = { showSongMenu(song) },
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                homeItem("community_playlists_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.from_the_community),
                                        onClick = { navController.navigate("section_detail/from_the_community") }
                                    )
                                }
                                homeItem("community_playlists_content", revealSeen) {
                                    HomeCardRow("community_playlists", playlists, revealSeen, key = { it.playlist.id }) { item ->
                                        CommunityPlaylistCard(
                                            item = item,
                                            onClick = { navController.navigate("online_playlist/${item.playlist.id}") }
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.ArtistList -> {
                            artistListItems?.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("artist_list_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.artist_list),
                                        onClick = { navController.navigate("artist_list_detail") }
                                    )
                                }
                                homeItem("artist_list_content", revealSeen) {
                                    HomeCardRow("artist_list", items, revealSeen, key = { "${it.artistId}_${it.artistName}" }) { group ->
                                        ArtistListCard(
                                            group = group,
                                            onClick = {
                                                group.artistId?.let { navController.navigate("artist/$it") }
                                            },
                                            onItemClick = { item ->
                                                when (item) {
                                                    is SongItem -> playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                            item.toMediaMetadata()
                                                        )
                                                    )
                                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                    else -> {}
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.AllTimeHits -> {
                            allTimeHits?.takeIf { it.isNotEmpty() }?.let { hits ->
                                homeItem("all_time_hits_title", revealSeen) {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.all_time_hits),
                                        onClick = { navController.navigate("section_detail/all_time_hits") }
                                    )
                                }
                                homeItem("all_time_hits_list", revealSeen) {
                                    HomeCardRow("all_time_hits", hits, revealSeen, key = { it.id }) { item ->
                                        AllTimeHitsCard(
                                            item = item,
                                            modifier = Modifier
                                                .width(160.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        when (item) {
                                                            is SongItem -> playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                                    item.toMediaMetadata()
                                                                )
                                                            )
                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate("artist/${item.id}")
                                                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.AccountPlaylists -> {
                            if (!localPlaylists.isNullOrEmpty() || !accountPlaylists.isNullOrEmpty()) {
                                homeItem("account_playlists_title", revealSeen) {
                                    Box {
                                        var showProfileMenu by remember { mutableStateOf(false) }
                                        HomeSectionTitle(
                                            label = "Your Playlists",
                                            title = accountName,
                                            thumbnail = {
                                                if (profileImageUrl != null) {
                                                    AsyncImage(
                                                        model = profileImageRequest,
                                                        placeholder = painterResource(id = R.drawable.person),
                                                        error = painterResource(id = R.drawable.person),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.person),
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.69f),
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            },
                                            onClick = { showProfileMenu = true },
                                        )

                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showProfileMenu,
                                            onDismissRequest = { showProfileMenu = false }
                                        ) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(stringResource(R.string.account)) },
                                                onClick = {
                                                    showProfileMenu = false
                                                    navController.navigate("account")
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.person),
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings)) },
                                                onClick = {
                                                    showProfileMenu = false
                                                    navController.navigateToTab("settings")
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.settings),
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                homeItem("account_playlists_list", revealSeen) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = HomeContentInset),
                                        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing)
                                    ) {
                                        localPlaylists?.let { items ->
                                            items(items = items, key = { it.id }) { item ->
                                                localItemCard(item)
                                            }
                                        }
                                        accountPlaylists?.let { items ->
                                            items(items = items, key = { it.id }) { item ->
                                                ytItemCard(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.DontForgetTheseSongs -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                homeItem("forgotten_favorites_title", revealSeen) {
                                    val forgottenFavoritesTitle = stringResource(R.string.dont_forget_these_songs)
                                    HomeSectionTitle(
                                        title = forgottenFavoritesTitle,
                                        onClick = { navController.navigate("section_detail/forgotten_favorites") },
                                        onPlayAllClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = forgottenFavoritesTitle,
                                                    items = forgottenFavorites.map { it.toMediaItem() }
                                                )
                                            )
                                        }
                                    )
                                }
                                homeItem("forgotten_favorites_list", revealSeen) {
                                    HomeTrackGrid(
                                        items = forgottenFavorites,
                                        state = forgottenFavoritesLazyGridState,
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                    ) { _, song ->
                                        HomeTrackRow(
                                            thumbnailUrl = song.song.thumbnailUrl,
                                            title = song.song.title,
                                            subtitle = song.artists.joinToString(", ") { it.name },
                                            isActive = song.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            onClick = { playSong(song) },
                                            onLongClick = { showSongMenu(song) },
                                            onMoreClick = { showSongMenu(song) },
                                        )
                                    }
                                }
                            }
                        }
                        is HomeSection.HomePageSection -> {
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                val sectionSongs = sectionData.items.filterIsInstance<SongItem>()

                                homeItem("home_section_title_${section.id}", revealSeen) {
                                    HomeSectionTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                                            {
                                                val shape = if (sectionData.endpoint?.isArtistEndpoint == true) {
                                                    CircleShape
                                                } else {
                                                    RoundedCornerShape(ThumbnailCornerRadius)
                                                }
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(shape)
                                                )
                                            }
                                        },
                                        onClick = sectionData.endpoint?.let { endpoint ->
                                            {
                                                when {
                                                    endpoint.browseId == "FEmusic_moods_and_genres" ->
                                                        navController.navigate("mood_and_genres")
                                                    endpoint.params != null ->
                                                        navController.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
                                                    else ->
                                                        navController.navigate("browse/${endpoint.browseId}")
                                                }
                                            }
                                        },
                                        onPlayAllClick = if (sectionSongs.isNotEmpty()) {
                                            {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = sectionData.title,
                                                        items = sectionSongs.map { it.toMediaMetadata().toMediaItem() }
                                                    )
                                                )
                                            }
                                        } else null,
                                    )
                                }

                                homeItem("home_section_list_${section.id}", revealSeen) {
                                    HomeCardRow("home_section_${section.id}", sectionData.items, revealSeen, key = { it.id }) {
                                        ytItemCard(it)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "section_spacer_${section.id}") {
                        Spacer(modifier = Modifier.height(34.dp))
                    }
                }

                // only while something is really on its way; a page token alone used to leave
                // these bones up forever when the next page couldn't load
                if ((isFirstLoad && speedDialItems.isEmpty() && keepListening.isNullOrEmpty()) || isLoadingMore) {
                    item(key = "loading_bones") {
                        BonesHost(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = null,
                                fadeOutSpec = tween(220),
                            )
                        ) {
                            repeat(2) {
                                HomeSectionBones()
                                Spacer(modifier = Modifier.height(34.dp))
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                val showHeroOverlay by remember { derivedStateOf { heroScrollProgressProvider() > 0.01f } }
                if (showHeroOverlay) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = heroScrollProgressProvider().coerceIn(0f, 1f) }
                    ) {
                        ProgressiveGlassBackground(
                            state = homeGlassState,
                            maxBlurRadius = {
                                val sheetProgress = playerBottomSheetState?.progress ?: 0f
                                50f * heroScrollProgressProvider().coerceIn(0f, 1f) * (1f - sheetProgress.coerceIn(0f, 1f))
                            },
                            foundationColor = backgroundColor,
                            direction = BlurDirection.BottomToTop,

                            steps = 3,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithCache {
                                onDrawBehind {
                                    val p = heroScrollProgressProvider().coerceIn(0f, 1f)
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = p * 0.9f),
                                            0.3f to Color.Black.copy(alpha = p * 0.6f),
                                            0.6f to Color.Black.copy(alpha = p * 0.3f),
                                            1f to Color.Transparent
                                        )
                                    )
                                }
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 14.dp,
                            bottom = 16.dp,
                            start = HomeContentInset,
                            end = 30.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScrolled by remember { derivedStateOf { heroScrollProgress > 0.5f } }
                    val topBarProgress by animateFloatAsState(
                        targetValue = if (isScrolled) 1f else 0f,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                        label = "topBarProgress"
                    )

                    // the brand mark over the hero, swapped for a bold "Home" once the hero scrolls away
                    Box(contentAlignment = Alignment.CenterStart) {
                        val markAlpha by animateFloatAsState(
                            targetValue = if (isFreshSetup) 0f else 1f,
                            animationSpec = tween(durationMillis = 400),
                            label = "markAlpha"
                        )

                        Icon(
                            painter = painterResource(R.drawable.ic_musicfy_mark),
                            contentDescription = "Musicfy",
                            tint = Color.White,
                            modifier = Modifier
                                .size(width = 31.dp, height = 34.dp)
                                .graphicsLayer {
                                    alpha = (1f - topBarProgress) * markAlpha
                                    val scale = 1f - topBarProgress * 0.25f
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    renderEffect = BlurEffectCache.get(topBarProgress * 15f)
                                }
                        )

                        Text(
                            text = "Home",
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.06).em,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                alpha = topBarProgress
                                renderEffect = BlurEffectCache.get((1f - topBarProgress) * 15f)
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = 1f - (topBarProgress * 0.1f)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(1f, 0.5f)
                            }
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .onGloballyPositioned { profilePillBounds = it.boundsInRoot() }
                            .clickable { profileMenuOpen = true },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            val avatarBoundsModifier = Modifier
                                .onGloballyPositioned { profileAvatarBounds = it.boundsInRoot() }
                                .graphicsLayer { alpha = (1f - profileMenuProgress.value * 6f).coerceIn(0f, 1f) }
                            if (profileImageUrl != null) {
                                AsyncImage(
                                    model = profileImageRequest,
                                    placeholder = painterResource(R.drawable.person),
                                    error = painterResource(R.drawable.person),
                                    contentDescription = "Profile",
                                    modifier = avatarBoundsModifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = avatarBoundsModifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = "Profile",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            ProfileMenuOverlay(
                visible = showProfileMenuOverlay,
                progressProvider = profileMenuProgressProvider,
                onDismissRequest = { profileMenuOpen = false },
                glassState = profileMenuGlassState,
                triggerBoundsProvider = { profilePillBounds },
                avatarBoundsProvider = { profileAvatarBounds },
                accountName = accountName,
                accountSubtitle = "Tap here to view profile",
                profileImageRequest = profileImageRequest,
                onProfileClick = { profileMenuOpen = false },
                items = profileMenuItems,
            )

        }
    }
    }
}
