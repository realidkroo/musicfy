// homescreenkt
// this thing is part of home screen

package com.example.musicfy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.homeSharedElement
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.TransformOrigin

import androidx.compose.ui.text.font.FontWeight
import com.example.musicfy.ui.component.PlaylistGridItem

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
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
import com.example.musicfy.constants.GridItemSize
import com.example.musicfy.constants.GridItemsSizeKey
import com.example.musicfy.constants.GridThumbnailHeight
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.ListItemHeight
import com.example.musicfy.constants.ListThumbnailSize
import com.example.musicfy.constants.RandomizeHomeOrderKey
import com.example.musicfy.constants.SmallGridThumbnailHeight
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil
import com.example.musicfy.LocalIsPlayerExpanded
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.LocalPlayerConnection
import androidx.media3.exoplayer.offline.Download
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.playback.queues.LocalAlbumRadio
import com.example.musicfy.playback.queues.YouTubeAlbumRadio
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.R
import com.example.musicfy.ui.component.AlbumGradient
import com.example.musicfy.ui.component.AlbumGridItem
import com.example.musicfy.ui.component.ArtistGridItem
import com.example.musicfy.ui.component.ArtistListCard
import com.example.musicfy.ui.component.ChipsRow
import com.example.musicfy.ui.component.HeroCarousel
import com.example.musicfy.ui.component.HideOnScrollFAB
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.NavigationTitle
import com.example.musicfy.ui.component.PlaylistListItem
import com.example.musicfy.ui.component.RandomizeGridItem
import com.example.musicfy.ui.component.shimmer.GridItemPlaceHolder
import com.example.musicfy.ui.component.shimmer.ShimmerHost
import com.example.musicfy.ui.component.shimmer.TextPlaceholder
import com.example.musicfy.ui.component.SongGridItem
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.component.SpeedDialGridItem
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.menu.AlbumMenu
import com.example.musicfy.ui.menu.ArtistMenu
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.YouTubeAlbumMenu
import com.example.musicfy.ui.menu.YouTubeArtistMenu
import com.example.musicfy.ui.menu.YouTubePlaylistMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.ui.utils.SnapLayoutInfoProvider
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.utils.listItemShape
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.CommunityPlaylistItem
import com.example.musicfy.viewmodels.HomeViewModel
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableIntStateOf
import com.example.musicfy.viewmodels.DailyDiscoverItem


// display order is built explicitly in homesections below (not derived from
// declarations) — this is just identity + the stable key each section
sealed class HomeSection(val id: String) {
    data object RecentlyPlayed : HomeSection("recently_played")
    data object MostPlayed : HomeSection("most_played")
    data object History : HomeSection("history")
    data object DontForgetTheseSongs : HomeSection("dont_forget_these_songs")
    data object AccountPlaylists : HomeSection("account_playlists")
    data object DailyDiscover : HomeSection("daily_discover")
    data object SpeedDial : HomeSection("speed_dial")
    data object FromTheCommunity : HomeSection("from_the_community")
    data object ArtistList : HomeSection("artist_list")
    data object AllTimeHits : HomeSection("all_time_hits")
    // keyed by the section's own title (not just index) — the server doesn't
    // guarantee section order/content stays identical between fetches and an
    // index-based key made a reload silently swap whatever content was in a given
    // visual slot reading as the whole feed "resetting"
    // id includes index not just title youtube regularly returns two sections
    // title (and continuations append more of them) which made this id collide —
    // spacer key and both section item keys down with it and crashing lazycolumn
    // "key  was already used" index is the section's position in the home page
    // unique by construction
    data class HomePageSection(val index: Int, val title: String) : HomeSection("home_page_section_${index}_$title")
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
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DailyDiscoverCard(
    dailyDiscover: com.example.musicfy.viewmodels.DailyDiscoverItem,
    onClick: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    var playCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(dailyDiscover.recommendation.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            playCount = database.getLifetimePlayCount(dailyDiscover.recommendation.id).firstOrNull() ?: 0
        }
    }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val song = dailyDiscover.recommendation as? SongItem
    val playsString = stringResource(R.string.plays)

    Card(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (song != null) {
                        menuState.show {
                            YouTubeSongMenu(
                                song = song,
                                navController = navController,
                                onDismiss = { menuState.dismiss() }
                            )
                        }
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(dailyDiscover.recommendation.thumbnail?.resize(1200, 1200))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )

            if (maxWidth > 200.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = dailyDiscover.recommendation.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = buildString {
                                append((dailyDiscover.recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: "")
                                if (playCount > 0) {
                                    append(" • $playCount $playsString")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    val messages = listOf(
                        R.string.daily_discover_sounds_like,
                        R.string.daily_discover_because_you_listen_to,
                        R.string.daily_discover_similar_to,
                        R.string.daily_discover_based_on,
                        R.string.daily_discover_for_fans_of
                    )
                    val messageRes = remember(dailyDiscover.seed.id) {
                        messages[kotlin.math.abs(dailyDiscover.seed.id.hashCode()) % messages.size]
                    }

                    Text(
                        text = stringResource(messageRes, "${dailyDiscover.seed.title} • ${dailyDiscover.seed.artists.joinToString(", ") { it.name }}"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
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
            .clip(RoundedCornerShape(16.dp))
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
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayedSongsForHome by viewModel.mostPlayedSongsForHome.collectAsState()
    val recentHistorySongs by viewModel.recentHistorySongs.collectAsState()
    val artistListItems by viewModel.artistListItems.collectAsState()
    val allTimeHits by viewModel.allTimeHits.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val lastPlayedSong by viewModel.lastPlayedSong.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()

    // same emptiness test herocarousel uses to swap itself for onboardinghero:
    // being onboarded the "musicfy"/"home" titles stay out of the way
    val isFreshSetup = mediaMetadata == null &&
        lastPlayedSong == null &&
        dailyDiscover.isNullOrEmpty() &&
        keepListening.orEmpty().filterIsInstance<Song>().isEmpty()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isRandomizing by viewModel.isRandomizing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val profilePicUri by rememberPreference(ProfilePicUriKey, "")
    val randomizeHomeOrder = false

    // the setup flow saves the user's chosen photo as an absolute local path
    // everywhere on home; the linked music-account avatar remains a fallback
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

    // top-bar profile dropdown `profilemenuprogress` (not just the open/closed
    // profilemenuoverlay's morph reads every frame and it's kept alive (mounted)
    // whole close animation too — see showprofilemenuoverlay below — so
    // reverse of expanding instead of an abrupt disappearance
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
                navController.navigate("settings")
            },
            ProfileMenuItem(icon = R.drawable.account, label = "Switch Profile") {},
            ProfileMenuItem(icon = R.drawable.logout, label = "Log Out and reset") {},
        )
    }
    BackHandler(enabled = profileMenuOpen) { profileMenuOpen = false }

    val scope = rememberCoroutineScope()
    // track randomization job
    var randomizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val lazylistState = rememberLazyListState()

    val configuration = LocalConfiguration.current
    val carouselHeightDp = (configuration.screenHeightDp * 0.55f).dp
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
    // keep these callbacks stable their reads occur inside graphics layers which
    // compose update the transform without recomposing the carousel during every
    val scrollOffsetProvider = remember { { firstItemScrollOffset } }
    val heroScrollProgressProvider = remember { { heroScrollProgress } }

    val gridItemSize = com.example.musicfy.LocalGridItemSize.current
    val currentGridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }


    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }
    val allDownloads by LocalDownloadUtil.current.downloads.collectAsState()

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                downloadState = allDownloads[it.id]?.state,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                sharedElementKey = "album-${it.id}",
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("artist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> PlaylistGridItem(
                playlist = it,
                // "liked" navigates to auto_playlist/liked which now has its own real
                // hero header (the collage) — same "playlist-<id>" key scheme applies
                sharedElementKey = "playlist-${it.id}",
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == "liked") {
                                navController.navigate("auto_playlist/liked")
                            } else {
                                navController.navigate("local_playlist/${it.id}")
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
            )
        }
    }

    // compact card for horizontally-scrolling preview rows (recently played) -
    // same currentgridheight (driven by the user's grid-size preference) speed
    // youtubegriditem cards resolve to so the two rows read as one consistent
    // instead of recently played looking a different size from its neighbor row
    val compactLocalItemCard: @Composable (LocalItem) -> Unit = { localItem ->
        val subtitle = when (localItem) {
            is Song -> localItem.artists.joinToString(", ") { it.name }
            is Album -> localItem.artists.joinToString(", ") { it.name }
            is Playlist -> localItem.playlist.name
            is Artist -> ""
        }
        val (onClick, onLongClick) = when (localItem) {
            is Song -> Pair<() -> Unit, () -> Unit>(
                {
                    if (localItem.id == mediaMetadata?.id) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection.playQueue(YouTubeQueue.radio(localItem.toMediaMetadata()))
                    }
                },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        SongMenu(originalSong = localItem, navController = navController, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Album -> Pair<() -> Unit, () -> Unit>(
                { navController.navigate("album/${localItem.id}") },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(originalAlbum = localItem, navController = navController, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Playlist -> Pair<() -> Unit, () -> Unit>(
                {
                    if (localItem.id == "liked") {
                        navController.navigate("auto_playlist/liked")
                    } else {
                        navController.navigate("local_playlist/${localItem.id}")
                    }
                },
                { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
            is Artist -> Pair<() -> Unit, () -> Unit>(
                { navController.navigate("artist/${localItem.id}") },
                { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
        }

        // same key scheme as albumgriditem/playlistgriditem/ytgriditem above
        val sharedElementKey = when (localItem) {
            is Album -> "album-${localItem.id}"
            is Playlist -> "playlist-${localItem.id}"
            else -> null
        }

        Column(
            modifier = Modifier
                .width(currentGridHeight)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            val shape = if (localItem is Artist) CircleShape else RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .size(currentGridHeight)
                    .homeSharedElement(sharedElementKey)
                    .clip(shape)
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), shape)
            ) {
                AlbumGradient(thumbnailUrl = localItem.thumbnailUrl, modifier = Modifier.size(currentGridHeight))
                localItem.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url.resize(320, 320),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(currentGridHeight)
                            .clip(if (localItem is Artist) CircleShape else RoundedCornerShape(14.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = localItem.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            downloadState = allDownloads[item.id]?.state,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            // local/online/liked playlist screens all share the same "playlist-<id>"
            // key scheme so one key format covers whichever destination this
            // actually lands on
            sharedElementKey = when {
                item is AlbumItem -> "album-${item.id}"
                item is PlaylistItem -> "playlist-${item.id}"
                else -> null
            },
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(
                                        videoId = item.id
                                    ), item.toMediaMetadata()
                                )
                            )

                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> {
                                if (item.id == "liked") {
                                    navController.navigate("auto_playlist/liked")
                                } else if (item.author?.name == "Local Playlist") {
                                    navController.navigate("local_playlist/${item.id}")
                                } else {
                                    navController.navigate("online_playlist/${item.id}")
                                }
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
                )
        )
    }

    // explicit sequential order (not a weight map to sort by) — requested order
    // recently played most played from the community jump back in (speed dial)
    // history artist list [account playlists / don't forget these songs — kept
    // just not called out explicitly] one yt home section all time hits then the
    // rest of the yt home sections a weight-based sort can't cleanly interleave
    // "exactly one yt section then a named section then the rest of the yt
    // sections" without fragile fractional weights so this just builds the list
    // the order it should render once
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

    // home-wide tight tracking: every card here (songlistitem griditems etc)
    // materialthemetypography so overriding it just for this screen's
    // applies consistently everywhere in home without touching the app-wide type
    // by search/library/settings/player
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
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = quickPicksLazyGridState,
                    positionInLayout = { _, _ -> 0f }
                )
            }
            val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { _, _ -> 0f }
                )
            }

            // wrap lazycolumn to allow fixed top bar overlay
            val homeGlassState = remember { GlassState() }
            // separate capture from homeglassstate above (which only ever wraps the
            // for the unrelated scroll-driven hero blur): this one wraps this whole box
            // list content and the top bar chrome — so profilemenuoverlay below (a true
            // outside this box added after it closes) can blur a real capture of
            // that's actually behind it keeping the consumer outside the wrapped subtree
            // same precaution seamblurkt takes relative to morphingcover's glassroot: a
            // nested *inside* the wrapped subtree would end up capturing (and blurring)
            val profileMenuGlassState = remember { GlassState() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(profileMenuGlassState, isActive = { profileMenuProgress.value > 0f })
            ) {
                val backgroundColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface
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
                item {
                    HeroCarousel(
                        keepListening = keepListening,
                        lastPlayedSong = mediaMetadata ?: lastPlayedSong,
                        dailyDiscover = dailyDiscover,
                        playerConnection = playerConnection,
                        navController = navController,
                        scrollOffsetProvider = scrollOffsetProvider,
                        heroScrollProgressProvider = heroScrollProgressProvider
                    )
                }

                if (isLoading && homePage?.chips.isNullOrEmpty() && speedDialItems.isEmpty() && keepListening.isNullOrEmpty()) {
                    item(key = "chips_shimmer") {
                        ShimmerHost {
                            LazyRow(
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                items(5) {
                                    TextPlaceholder(
                                        height = 30.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.width(72.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                homeSections.forEach { section ->
                    when (section) {
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "speed_dial_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.speed_dial),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("section_detail/speed_dial") }
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        modifier = Modifier,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(
                                            items = items,
                                            key = { it.id }
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.RecentlyPlayed -> {
                            recentlyPlayed?.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "recently_played_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.recently_played),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("section_detail/recently_played") }
                                    )
                                }

                                item(key = "recently_played_list") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(
                                            items = items,
                                            key = { it.id }
                                        ) { item ->
                                            compactLocalItemCard(item)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.MostPlayed -> {
                            mostPlayedSongsForHome?.takeIf { it.isNotEmpty() }?.let { mostPlayed ->
                                item(key = "most_played_title") {
                                    val mostPlayedTitle = stringResource(R.string.vivi_quick_picks)
                                    NavigationTitle(
                                        title = mostPlayedTitle,
                                        modifier = Modifier.padding(horizontal = 12.dp),
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

                                item(key = "most_played_list") {
                                    val rows = min(4, mostPlayed.size)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * rows)
                                    ) {
                                        LazyHorizontalGrid(
                                            state = quickPicksLazyGridState,
                                            rows = GridCells.Fixed(rows),
                                            flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * rows)

                                        ) {
                                            itemsIndexed(
                                                items = mostPlayed,
                                                key = { _, it -> it.id }
                                            ) { index, song ->
                                                // leading rank column number left-aligned within it
                                                // (not centered) so the digit itself hugs the row's
                                                // left margin instead of floating with a visible gap
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.width(horizontalLazyGridItemWidth)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.width(24.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    SongListItem(
                                                        song = song,
                                                        downloadState = allDownloads[song.id]?.state,
                                                        showInLibraryIcon = true,
                                                        isActive = song.id == mediaMetadata?.id,
                                                        isPlaying = isPlaying,
                                                        isSwipeable = false,
                                                        shape = RectangleShape,
                                                        backgroundColor = Color.Transparent,
                                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .combinedClickable(
                                                                onClick = {
                                                                    if (song.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue.radio(
                                                                                song.toMediaMetadata()
                                                                            )
                                                                        )
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    menuState.show {
                                                                        SongMenu(
                                                                            originalSong = song,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                        // subtle trailing-edge gradient cue that more content follows
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(32.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(Color.Transparent, backgroundColor.copy(alpha = 0.5f))
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.History -> {
                            recentHistorySongs?.takeIf { it.isNotEmpty() }?.let { history ->
                                item(key = "history_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.vivi_on_heavy_rotation),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("history") }
                                    )
                                }

                                item(key = "history_list") {
                                    val rows = min(4, history.size)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * rows)
                                    ) {
                                        val historyLazyGridState = rememberLazyGridState()
                                        val historySnapLayoutInfoProvider = remember(historyLazyGridState) {
                                            SnapLayoutInfoProvider(
                                                lazyGridState = historyLazyGridState,
                                                positionInLayout = { _, _ -> 0f }
                                            )
                                        }
                                        LazyHorizontalGrid(
                                            state = historyLazyGridState,
                                            rows = GridCells.Fixed(rows),
                                            flingBehavior = rememberSnapFlingBehavior(historySnapLayoutInfoProvider),
                                            contentPadding = PaddingValues(start = 12.dp, end = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * rows)
                                        ) {
                                            itemsIndexed(
                                                items = history,
                                                // duplicates are expected (same song played repeatedly) so
                                                // key on position rather than song id
                                                key = { index, item -> "history_${index}_${item.id}" }
                                            ) { _, song ->
                                                // flush with the grid's own 24dp contentpadding —
                                                // no leading rank column here (that's most
                                                // played's thing) so nothing should indent this
                                                // past where every other row's content starts
                                                SongListItem(
                                                    song = song,
                                                    downloadState = allDownloads[song.id]?.state,
                                                    showInLibraryIcon = true,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    shape = RectangleShape,
                                                    backgroundColor = Color.Transparent,
                                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                                    modifier = Modifier
                                                        .width(horizontalLazyGridItemWidth)
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (song.id == mediaMetadata?.id) {
                                                                    playerConnection.togglePlayPause()
                                                                } else {
                                                                    playerConnection.playQueue(
                                                                        YouTubeQueue.radio(song.toMediaMetadata())
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    SongMenu(
                                                                        originalSong = song,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss
                                                                    )
                                                                }
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                        // subtle trailing-edge gradient cue that more content follows
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(32.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(Color.Transparent, backgroundColor.copy(alpha = 0.5f))
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "community_playlists_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.from_the_community),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("section_detail/from_the_community") }
                                    )
                                }

                                item(key = "community_playlists_content") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(playlists) { item ->
                                            CommunityPlaylistCard(
                                                item = item,
                                                onClick = {
                                                    navController.navigate("online_playlist/${item.playlist.id}")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.ArtistList -> {
                            artistListItems?.takeIf { it.isNotEmpty() }?.let { items ->
                                item(key = "artist_list_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.artist_list),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("artist_list_detail") }
                                    )
                                }

                                item(key = "artist_list_content") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(
                                            items = items,
                                            key = { "${it.artistId}_${it.artistName}" }
                                        ) { group ->
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
                        }
                        HomeSection.AllTimeHits -> {
                            allTimeHits?.takeIf { it.isNotEmpty() }?.let { hits ->
                                item(key = "all_time_hits_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.all_time_hits),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = { navController.navigate("section_detail/all_time_hits") }
                                    )
                                }

                                item(key = "all_time_hits_list") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(
                                            items = hits,
                                            key = { it.id }
                                        ) { item ->
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
                        }
                        HomeSection.DailyDiscover -> {
                            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
                                // added a tittle new update
                                item(key = "daily_discover_title") {
                                    val title = stringResource(R.string.your_daily_discover)
                                    NavigationTitle(
                                        title = title,
                                        onPlayAllClick = {
                                            val queueItems = discoverList.mapNotNull {
                                                (it.recommendation as? SongItem)?.toMediaMetadata()
                                            }

                                            if (queueItems.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = title,
                                                        items = queueItems.map { it.toMediaItem() }
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                                item(key = "daily_discover_content") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val carouselState = rememberCarouselState { discoverList.size }
                                        HorizontalMultiBrowseCarousel(
                                            state = carouselState,
                                            preferredItemWidth = 320.dp,
                                            itemSpacing = 16.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(320.dp)
                                        ) { i ->
                                            val item = discoverList[i]
                                            DailyDiscoverCard(
                                                dailyDiscover = item,
                                                onClick = {
                                                    val song = item.recommendation as? SongItem
                                                    val mediaMetadata = song?.toMediaMetadata()
                                                    if (mediaMetadata != null) {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                mediaMetadata
                                                            )
                                                        )
                                                    }
                                                },
                                                navController = navController,
                                                modifier = Modifier.clip(MaterialTheme.shapes.extraLarge)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.AccountPlaylists -> {
                            if (!localPlaylists.isNullOrEmpty() || !accountPlaylists.isNullOrEmpty()) {
                                item(key = "account_playlists_title") {
                                    Box {
                                        var showProfileMenu by remember { mutableStateOf(false) }
                                        NavigationTitle(
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
                                                            .size(ListThumbnailSize)
                                                            .clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.person),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(ListThumbnailSize)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showProfileMenu = true
                                            },
                                            modifier = Modifier.padding(horizontal = 12.dp)
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
                                                    navController.navigate("settings")
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

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        modifier = Modifier,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        localPlaylists?.let { items ->
                                            items(
                                                items = items,
                                                key = { it.id },
                                            ) { item ->
                                                localGridItem(item)
                                            }
                                        }
                                        accountPlaylists?.let { items ->
                                            items(
                                                items = items,
                                                key = { it.id },
                                            ) { item ->
                                                ytGridItem(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.DontForgetTheseSongs -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                item(key = "forgotten_favorites_title") {
                                    val forgottenFavoritesTitle = stringResource(R.string.dont_forget_these_songs)
                                    NavigationTitle(
                                        title = forgottenFavoritesTitle,
                                        modifier = Modifier.padding(horizontal = 12.dp),
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

                                item(key = "forgotten_favorites_list") {
                                    // take min in case list size is less than 4
                                    val rows = min(4, forgottenFavorites.size)
                                    LazyHorizontalGrid(
                                        state = forgottenFavoritesLazyGridState,
                                        rows = GridCells.Fixed(rows),
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        flingBehavior = rememberSnapFlingBehavior(
                                            forgottenFavoritesSnapLayoutInfoProvider
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * rows)
                                            
                                    ) {
                                        itemsIndexed(
                                            items = forgottenFavorites,
                                            key = { _, it -> it.id }
                                        ) { index, originalSong ->
                                            val song = originalSong

                                            // same leading-column width as most played's rank number
                                            // (just empty here) so thumbnails line up across sections
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.width(horizontalLazyGridItemWidth)
                                            ) {
                                            Spacer(modifier = Modifier.width(24.dp))
                                            SongListItem(
                                                song = song,
                                                downloadState = allDownloads[song.id]?.state,
                                                showInLibraryIcon = true,
                                                isActive = song.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                shape = RectangleShape,
                                                backgroundColor = Color.Transparent,
                                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue.radio(
                                                                        song.toMediaMetadata()
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss
                                                                )
                                                            }
                                                        }
                                                    )
                                            )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is HomeSection.HomePageSection -> {
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                // check if section contains songs for play all functionality
                                val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                val hasPlayableSongs = sectionSongs.isNotEmpty()

                                item(key = "home_section_title_${section.id}") {
                                    NavigationTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                                            {
                                                val shape =
                                                    if (sectionData.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                                        ThumbnailCornerRadius
                                                    )
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(ListThumbnailSize)
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
                                        onPlayAllClick = if (hasPlayableSongs) {
                                            {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = sectionData.title,
                                                        items = sectionSongs.map { it.toMediaMetadata().toMediaItem() }
                                                    )
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }

                                // always a horizontal row of cover-forward grid cards - the old
                                // songs-only branch rendered gray list rows here which looked
                                // out of place among the rest of home's card-based sections
                                item(key = "home_section_list_${section.id}") {
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(
                                            items = sectionData.items,
                                            key = { it.id }
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }

                    }

                    item(key = "section_spacer_${section.id}") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                if ((isLoading && speedDialItems.isEmpty() && keepListening.isNullOrEmpty()) || (homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true)) {
                    item(key = "loading_shimmer") {
                        ShimmerHost(
                            modifier = Modifier
                        ) {
                            repeat(2) {
                                TextPlaceholder(
                                    height = 36.dp,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .width(250.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(4) {
                                        GridItemPlaceHolder()
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            } // end of lazycolumn
            } // end of compositionlocalprovider

            // fixed top bar overlay
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ─── true progressive blur using glasskit (rendernode capture) ───
                // matches the weatherify settings screen approach exactly
                // gated by a derivedstateof boolean (like isscrolled below) so this only
                // recomposes at the mount/unmount boundary not on every scroll pixel the
                // continuously-varying reads inside stay deferred to the draw phase via
                // heroscrollprogressprovider() so the fade still animates smoothly
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
                            // three quality layers keep the progressive glass gradient while
                            // reducing full-screen offscreen blur passes during scrolling
                            steps = 3,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // tinted background overlay that fades with scroll built in the draw phase
                    // (ondrawbehind) rather than via `background(brush)` — since this box is
                    // now only recomposed at the showherooverlay boundary baking the gradient's
                    // alpha stops at composition time would freeze the fade instead of animating
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
                            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp,
                            bottom = 16.dp,
                            start = 24.dp,
                            end = 24.dp
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

                    Box(modifier = Modifier.graphicsLayer {
                        val scale = 1f - (topBarProgress * 0.2f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }, contentAlignment = Alignment.CenterStart) {
                        // onboarding owns the top of the screen on a clean setup so the wordmark
                        // stays hidden until the user scrolls past the hero (where "home" takes over)
                        val wordmarkAlpha by animateFloatAsState(
                            targetValue = if (isFreshSetup) 0f else 1f,
                            animationSpec = tween(durationMillis = 400),
                            label = "wordmarkAlpha"
                        )

                        // "musicfy" text
                        Text(
                            text = "Musicfy",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Normal),
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                alpha = (1f - topBarProgress) * wordmarkAlpha
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val rawBlur = topBarProgress * 15f
                                    val blurRadius = (rawBlur / 3f).toInt() * 3
                                    if (blurRadius > 0) {
                                        renderEffect = blurCache.getOrPut(blurRadius) {
                                            android.graphics.RenderEffect.createBlurEffect(
                                                blurRadius.toFloat(), blurRadius.toFloat(), android.graphics.Shader.TileMode.CLAMP
                                            ).asComposeRenderEffect()
                                        }
                                    } else {
                                        renderEffect = null
                                    }
                                }
                            }
                        )
                        // "home" text
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Normal),
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                alpha = topBarProgress
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val rawBlur = (1f - topBarProgress) * 15f
                                    val blurRadius = (rawBlur / 3f).toInt() * 3
                                    if (blurRadius > 0) {
                                        renderEffect = blurCache.getOrPut(blurRadius) {
                                            android.graphics.RenderEffect.createBlurEffect(
                                                blurRadius.toFloat(), blurRadius.toFloat(), android.graphics.Shader.TileMode.CLAMP
                                            ).asComposeRenderEffect()
                                        }
                                    } else {
                                        renderEffect = null
                                    }
                                }
                            }
                        )
                    }

                    // profile pill — glasskit frosted glass (no haze glow) also the trigger for
                    // profilemenuoverlay: its own bounds (whatever shape they currently are —
                    // bare circle when unscrolled full pill once scrolled) are what that overlay
                    // morphs from via ongloballypositioned below
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
                        // frosted glass background for the pill
                        if (topBarProgress > 0.01f) {
                            GlassPillBackground(
                                state = homeGlassState,
                                blurRadius = {
                                    val sheetProgress = playerBottomSheetState?.progress ?: 0f
                                    24f * (1f - sheetProgress.coerceIn(0f, 1f))
                                },
                                tint = Color.Black.copy(alpha = topBarProgress * 0.2f),
                                foundationColor = backgroundColor,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer { alpha = topBarProgress }
                            )
                            // subtle border
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = topBarProgress * 0.2f),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = (topBarProgress * 12).dp,
                                end = (topBarProgress * 4).dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                        ) {
                            if (topBarProgress > 0.01f) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back_ios),
                                    contentDescription = "Back",
                                    tint = Color.White.copy(alpha = topBarProgress),
                                    modifier = Modifier
                                        .size((topBarProgress * 18).dp)
                                        .padding(end = (topBarProgress * 4).dp)
                                )
                            }

                            // hidden (alpha -> 0) almost as soon as the menu starts opening —
                            // profilemenuoverlay's own floating avatar takes over from exactly
                            // this position so the handoff between the two reads as one image
                            // continuing to move rather than a visible swap ramped 6x progress
                            // instead of 1:1 so it's fully gone well before the floating copy has
                            // travelled far enough for a gap between them to be visible
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
        } // end of box wrapping lazycolumn

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


