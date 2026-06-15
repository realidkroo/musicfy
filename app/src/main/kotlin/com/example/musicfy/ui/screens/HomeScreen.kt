// HomeScreen.kt
// this thing is part of home screen

package com.example.musicfy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.ImageRequest
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import com.music.innertube.YouTube
import com.example.musicfy.constants.GridItemSize
import com.example.musicfy.constants.GridItemsSizeKey
import com.example.musicfy.constants.GridThumbnailHeight
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.ListItemHeight
import com.example.musicfy.constants.ListThumbnailSize
import com.example.musicfy.constants.RandomizeHomeOrderKey
import com.example.musicfy.constants.SmallGridThumbnailHeight
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSongMap
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
import com.example.musicfy.ui.component.AlbumGridItem
import com.example.musicfy.ui.component.ArtistGridItem
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


sealed class HomeSection(val id: String, val baseWeight: Int) {
    data object KeepListening : HomeSection("keep_listening", 100)
    data object YourLibrary : HomeSection("your_library", 95)
    data object AccountPlaylists : HomeSection("account_playlists", 90)
    data object DailyDiscover : HomeSection("daily_discover", 80)
    data object SpeedDial : HomeSection("speed_dial", 50)
    data object QuickPicks : HomeSection("quick_picks", 40)
    data object ForgottenFavorites : HomeSection("forgotten_favorites", 30)
    data object FromTheCommunity : HomeSection("from_the_community", 20)
    data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 10)
    data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 10)

}

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    var isBookmarked by remember { mutableStateOf(false) }
    LaunchedEffect(item.playlist.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.playlistByBrowseId(item.playlist.id).firstOrNull()?.let { dbPlaylist ->
                isBookmarked = dbPlaylist.playlist.bookmarkedAt != null
            }
        }
    }

    Card(
        modifier = modifier
            .width(320.dp)
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(28.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2x2 Grid of thumbnails
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = item.songs.getOrNull(0)?.thumbnail?.resize(256, 256),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = item.songs.getOrNull(1)?.thumbnail?.resize(256, 256),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = item.songs.getOrNull(2)?.thumbnail?.resize(256, 256),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                            AsyncImage(
                                model = item.songs.getOrNull(3)?.thumbnail?.resize(256, 256),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.playlist.author?.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                item.songs.take(3).forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { onSongClick(song) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = song.thumbnail.resize(256, 256),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                IconButton(
                    onClick = {
                        item.playlist.playEndpoint?.let {
                            playerConnection?.playQueue(YouTubeQueue(it))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        item.playlist.radioEndpoint?.let {
                            playerConnection?.playQueue(YouTubeQueue(it))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val existingPlaylist = database.playlistByBrowseId(item.playlist.id).firstOrNull()
                            if (existingPlaylist?.playlist == null) {
                                database.transaction {
                                    val playlistEntity = PlaylistEntity(
                                        name = item.playlist.title,
                                        browseId = item.playlist.id,
                                        thumbnailUrl = item.playlist.thumbnail,
                                        remoteSongCount = item.playlist.songCountText?.split(" ")?.firstOrNull()?.toIntOrNull(),
                                        playEndpointParams = item.playlist.playEndpoint?.params,
                                        shuffleEndpointParams = item.playlist.shuffleEndpoint?.params,
                                        radioEndpointParams = item.playlist.radioEndpoint?.params
                                    ).toggleLike()
                                    insert(playlistEntity)
                                    scope.launch(Dispatchers.IO) {
                                        item.songs.ifEmpty {
                                            YouTube.playlist(item.playlist.id).completed()
                                                .getOrNull()?.songs.orEmpty()
                                        }.map { it.toMediaMetadata() }
                                            .onEach(::insert)
                                            .mapIndexed { index, song ->
                                                PlaylistSongMap(
                                                    songId = song.id,
                                                    playlistId = playlistEntity.id,
                                                    position = index,
                                                    setVideoId = song.setVideoId
                                                )
                                            }
                                            .forEach(::insert)
                                    }
                                }
                                isBookmarked = true
                            } else {
                                database.transaction {
                                    update(existingPlaylist.playlist.toggleLike())
                                }
                                isBookmarked = existingPlaylist.playlist.bookmarkedAt == null
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(if (isBookmarked) R.drawable.library_add_check else R.drawable.library_add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
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


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val lastPlayedSong by viewModel.lastPlayedSong.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isRandomizing by viewModel.isRandomizing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val randomizeHomeOrder = false



    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    // Track randomization job
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
            // if a chip is selected, go back to the normal homepage first
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

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            downloadState = allDownloads[item.id]?.state,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
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
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                )
        )
    }

    val homeSections by remember {
        derivedStateOf {
            val list = mutableListOf<HomeSection>()

            if (speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
            if (keepListening?.isNotEmpty() == true) list.add(HomeSection.KeepListening)
            if (communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
            if (dailyDiscover?.isNotEmpty() == true) list.add(HomeSection.DailyDiscover)
            list.add(HomeSection.YourLibrary)
            if (accountPlaylists?.isNotEmpty() == true || localPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
            if (forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.ForgottenFavorites)

            similarRecommendations?.indices?.forEach { i ->
                list.add(HomeSection.SimilarRecommendation(i))
            }

            homePage?.sections?.indices?.forEach { i ->
                list.add(HomeSection.HomePageSection(i))
            }

            val defaultOrder = mapOf(
                HomeSection.SpeedDial to 100, // Recently Played
                HomeSection.QuickPicks to 90, // Most Played
                HomeSection.KeepListening to 80, // History
                HomeSection.YourLibrary to 70, // Your Library
                HomeSection.AccountPlaylists to 60, // Your Playlists
                HomeSection.DailyDiscover to 50,
                HomeSection.ForgottenFavorites to 40,
                HomeSection.FromTheCommunity to 30,
            )

            list.sortedByDescending { section ->
                when(section) {
                    is HomeSection.SimilarRecommendation -> 30 - section.index
                    is HomeSection.HomePageSection -> 20 - section.index
                    else -> defaultOrder[section] ?: 0
                }
            }
        }
    }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

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
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }
            val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }

            // Wrap LazyColumn to allow fixed Top Bar overlay
            Box(modifier = Modifier.fillMaxSize()) {
                val backgroundColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface
                LazyColumn(
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
                        scrollOffsetProvider = { firstItemScrollOffset },
                        heroScrollProgressProvider = { heroScrollProgress }
                    )
                }

                if (isLoading && homePage?.chips.isNullOrEmpty() && speedDialItems.isEmpty() && keepListening.isNullOrEmpty()) {
                    item(key = "chips_shimmer") {
                        ShimmerHost {
                            LazyRow(
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        modifier = Modifier
                                    )
                                }

                                item(key = "speed_dial_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        HomeSection.QuickPicks -> {
                            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                                item(key = "quick_picks_title") {
                                    val quickPicksTitle = stringResource(R.string.vivi_quick_picks)
                                    NavigationTitle(
                                        title = quickPicksTitle,
                                        modifier = Modifier,
                                        onPlayAllClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = quickPicksTitle,
                                                    items = quickPicks.mapNotNull { if (it is Song) it.toMediaItem() else null }
                                                )
                                            )
                                        }
                                    )
                                }

                                item(key = "quick_picks_list") {
                                    LazyHorizontalGrid(
                                        state = quickPicksLazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * 4)
                                            
                                    ) {
                                        itemsIndexed(
                                            items = quickPicks,
                                            key = { _, it -> it.id }
                                        ) { index, originalItem ->
                                            when (originalItem) {
                                                is Song -> {
                                                    val song = originalItem

                                                    SongListItem(
                                                        song = song,
                                                        downloadState = allDownloads[song.id]?.state,
                                                        showInLibraryIcon = true,
                                                        isActive = song!!.id == mediaMetadata?.id,
                                                        isPlaying = isPlaying,
                                                        isSwipeable = false,
                                                        shape = RectangleShape,
                                                        backgroundColor = Color.Transparent,
                                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                                        trailingContent = {
                                                            IconButton(
                                                                onClick = {
                                                                    menuState.show {
                                                                        SongMenu(
                                                                            originalSong = song!!,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss
                                                                        )
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.more_vert),
                                                                    contentDescription = null
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .width(horizontalLazyGridItemWidth)
                                                            .combinedClickable(
                                                                onClick = {
                                                                    if (song!!.id == mediaMetadata?.id) {
                                                                        playerConnection.togglePlayPause()
                                                                    } else {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue.radio(
                                                                                song!!.toMediaMetadata()
                                                                            )
                                                                        )
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    menuState.show {
                                                                        SongMenu(
                                                                            originalSong = song!!,
                                                                            navController = navController,
                                                                            onDismiss = menuState::dismiss
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                    )
                                                }
                                                is com.example.musicfy.db.entities.Playlist -> {
                                                    PlaylistListItem(
                                                        playlist = originalItem,
                                                        shape = RectangleShape,
                                                        backgroundColor = Color.Transparent,
                                                        modifier = Modifier
                                                            .width(horizontalLazyGridItemWidth)
                                                            .clickable {
                                                                navController.navigate("local_playlist/${originalItem.id}")
                                                            }
                                                    )
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                item(key = "community_playlists_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.from_the_community),
                                        modifier = Modifier
                                    )
                                }

                                item(key = "community_playlists_content") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                    ) {
                                        items(playlists) { item ->
                                            CommunityPlaylistCard(
                                                item = item,
                                                onClick = {
                                                    navController.navigate("online_playlist/${item.playlist.id.removePrefix("VL")}")
                                                },
                                                onSongClick = { song ->
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                            song.toMediaMetadata()
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.DailyDiscover -> {
                            dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
                                //added a tittle new update
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
                                            .padding(horizontal = 16.dp),
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
                        HomeSection.KeepListening -> {
                            keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                                item(key = "keep_listening_title") {
                                    NavigationTitle(
                                        title = stringResource(R.string.vivi_on_heavy_rotation),
                                        modifier = Modifier
                                    )
                                }

                                item(key = "keep_listening_list") {
                                    val rows = 1
                                    LazyHorizontalGrid(
                                        state = rememberLazyGridState(),
                                        rows = GridCells.Fixed(rows),
                                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((currentGridHeight + with(LocalDensity.current) {
                                                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                                        MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                                            }) * rows)
                                            
                                    ) {
                                        items(
                                            items = keepListening,
                                            key = { it.id }
                                        ) {
                                            localGridItem(it)
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.YourLibrary -> {
                            item(key = "your_library_title") {
                                NavigationTitle(
                                    title = stringResource(R.string.your_library),
                                    modifier = Modifier
                                )
                            }
                            item(key = "your_library_list") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                    modifier = Modifier,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        LibraryCard(
                                            icon = R.drawable.favorite,
                                            label = stringResource(R.string.liked_songs),
                                            onClick = { navController.navigate("auto_playlist/liked") }
                                        )
                                    }
                                    item {
                                        LibraryCard(
                                            icon = R.drawable.download,
                                            label = stringResource(R.string.downloaded),
                                            onClick = { navController.navigate("auto_playlist/downloaded") }
                                        )
                                    }
                                    item {
                                        LibraryCard(
                                            icon = R.drawable.favorite_border,
                                            label = stringResource(R.string.uploaded),
                                            onClick = { navController.navigate("auto_playlist/uploaded") }
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.AccountPlaylists -> {
                            if (!localPlaylists.isNullOrEmpty() || !accountPlaylists.isNullOrEmpty()) {
                                item(key = "account_playlists_title") {
                                    NavigationTitle(
                                        label = "Your Playlists",
                                        title = accountName,
                                        thumbnail = {
                                            if (url != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(url)
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .diskCacheKey(url)
                                                        .crossfade(false)
                                                        .build(),
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
                                            navController.navigate("account")
                                        },
                                        modifier = Modifier
                                    )
                                }

                                item(key = "account_playlists_list") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        HomeSection.ForgottenFavorites -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                item(key = "forgotten_favorites_title") {
                                    val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
                                    NavigationTitle(
                                        title = forgottenFavoritesTitle,
                                        modifier = Modifier,
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
                                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
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

                                            SongListItem(
                                                song = song,
                                                downloadState = allDownloads[song.id]?.state,
                                                showInLibraryIcon = true,
                                                isActive = song!!.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                shape = listItemShape(index = index % rows, count = rows),
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss
                                                                )
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(horizontalLazyGridItemWidth)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (song!!.id == mediaMetadata?.id) {
                                                                playerConnection.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue.radio(
                                                                        song!!.toMediaMetadata()
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                SongMenu(
                                                                    originalSong = song!!,
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
                        is HomeSection.SimilarRecommendation -> {
                            val recommendation = similarRecommendations?.getOrNull(section.index)
                            recommendation?.let {
                                item(key = "similar_to_title_${section.index}") {
                                    NavigationTitle(
                                        label = stringResource(R.string.similar_to),
                                        title = recommendation.title.title,
                                        thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                                            {
                                                val shape =
                                                    if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
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
                                        onClick = {
                                            when (recommendation.title) {
                                                is Song -> navController.navigate("album/${recommendation.title.album!!.id}")
                                                is Album -> navController.navigate("album/${recommendation.title.id}")
                                                is Artist -> navController.navigate("artist/${recommendation.title.id}")
                                                is Playlist -> {}
                                            }
                                        },
                                        modifier = Modifier
                                    )
                                }

                                item(key = "similar_to_list_${section.index}") {
                                    LazyRow(
                                        contentPadding = WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                        modifier = Modifier
                                    ) {
                                        items(
                                            items = recommendation.items,
                                            key = { it.id }
                                        ) { item ->
                                            ytGridItem(item)
                                        }
                                    }
                                }
                            }
                        }
                        is HomeSection.HomePageSection -> {
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                // Check if section contains songs for Play All functionality
                                val sectionSongs = sectionData.items.filterIsInstance<SongItem>()
                                val hasPlayableSongs = sectionSongs.isNotEmpty()
                                // Check if this section contains ONLY songs (like Quick picks, Trending songs)
                                val isSongsOnlySection = sectionData.items.isNotEmpty() &&
                                        sectionData.items.all { it is SongItem }

                                item(key = "home_section_title_${section.index}") {
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
                                        modifier = Modifier
                                    )
                                }

                                if (isSongsOnlySection) {
                                    // Render songs as a horizontal scrollable list (like Quick picks in YouTube Music)
                                    item(key = "home_section_list_${section.index}") {
                                        LazyHorizontalGrid(
                                            state = rememberLazyGridState(),
                                            rows = GridCells.Fixed(4),
                                            contentPadding = WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(ListItemHeight * 4)
                                                
                                        ) {
                                            itemsIndexed(
                                                items = sectionSongs,
                                                key = { _, it -> it.id }
                                            ) { index, song ->
                                                YouTubeListItem(
                                                    item = song,
                                                    isActive = song.id == mediaMetadata?.id,
                                                    isPlaying = isPlaying,
                                                    isSwipeable = false,
                                                    shape = listItemShape(index = index % 4, count = 4),
                                                    trailingContent = {
                                                        IconButton(
                                                            onClick = {
                                                                menuState.show {
                                                                    YouTubeSongMenu(
                                                                        song = song,
                                                                        navController = navController,
                                                                        onDismiss = menuState::dismiss
                                                                    )
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.more_vert),
                                                                contentDescription = null
                                                            )
                                                        }
                                                    },
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
                                                                    YouTubeSongMenu(
                                                                        song = song,
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
                                } else {
                                    // Render mixed content as horizontal grid items (albums, playlists, artists, etc.)
                                    item(key = "home_section_list_${section.index}") {
                                        LazyRow(
                                            contentPadding = WindowInsets.systemBars
                                                .only(WindowInsetsSides.Horizontal)
                                                .asPaddingValues(),
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
                                    contentPadding = WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
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
            } // End of LazyColumn

            // Fixed Top Bar Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(
                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        bottom = 16.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.graphicsLayer {
                        val scale = 1f - (heroScrollProgress * 0.2f)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }, contentAlignment = Alignment.CenterStart) {
                        // "Musicfy" Text
                        Text(
                            text = "Musicfy",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - heroScrollProgress
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val rawBlur = heroScrollProgress * 15f
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
                        // "Home" Text
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                alpha = heroScrollProgress
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val rawBlur = (1f - heroScrollProgress) * 15f
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

                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .graphicsLayer {
                                    val scale = 1f - (heroScrollProgress * 0.2f)
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                }
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    val scale = 1f - (heroScrollProgress * 0.2f)
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                }
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = "Profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } // End of Box wrapping LazyColumn
    }
}
}
@Composable
fun LibraryCard(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .width(160.dp)
            .height(80.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
