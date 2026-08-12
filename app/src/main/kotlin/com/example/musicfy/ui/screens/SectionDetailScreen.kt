package com.example.musicfy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.models.toMediaMetadata
import androidx.compose.ui.graphics.RectangleShape
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.AlbumListItem
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.PlaylistListItem
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.ui.menu.YouTubeSongMenu
import com.example.musicfy.viewmodels.HomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionDetailScreen(
    navController: NavController,
    sectionId: String,
    homeViewModel: HomeViewModel
) {
    val speedDialItems by homeViewModel.speedDialItems.collectAsState()
    val quickPicks by homeViewModel.quickPicks.collectAsState()
    val forgottenFavorites by homeViewModel.forgottenFavorites.collectAsState()
    val keepListening by homeViewModel.keepListening.collectAsState()
    val accountName by homeViewModel.accountName.collectAsState()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val mostPlayedSongsForHome by homeViewModel.mostPlayedSongsForHome.collectAsState()
    val communityPlaylists by homeViewModel.communityPlaylists.collectAsState()
    val allTimeHits by homeViewModel.allTimeHits.collectAsState()

    val title = when (sectionId) {
        "speed_dial" -> stringResource(R.string.speed_dial)
        "quick_picks" -> stringResource(R.string.quick_picks)
        "forgotten_favorites" -> stringResource(R.string.dont_forget_these_songs)
        "history" -> stringResource(R.string.vivi_on_heavy_rotation)
        "recently_played" -> stringResource(R.string.recently_played)
        "most_played" -> stringResource(R.string.vivi_quick_picks)
        "from_the_community" -> stringResource(R.string.from_the_community)
        "all_time_hits" -> stringResource(R.string.all_time_hits)
        else -> ""
    }

    val iconRes = when (sectionId) {
        "speed_dial" -> R.drawable.history
        "quick_picks" -> R.drawable.speed
        "forgotten_favorites" -> R.drawable.favorite
        "history" -> R.drawable.history
        "recently_played" -> R.drawable.history
        "most_played" -> R.drawable.speed
        "from_the_community" -> R.drawable.favorite
        "all_time_hits" -> R.drawable.speed
        else -> R.drawable.history
    }

    val items = remember(
        sectionId, speedDialItems, quickPicks, forgottenFavorites, keepListening,
        recentlyPlayed, mostPlayedSongsForHome, communityPlaylists,
        allTimeHits,
    ) {
        when (sectionId) {
            "speed_dial" -> speedDialItems.filterIsInstance<SongItem>()
            "quick_picks" -> quickPicks?.filterIsInstance<Song>() ?: emptyList()
            "forgotten_favorites" -> forgottenFavorites ?: emptyList()
            "history" -> keepListening?.filterIsInstance<Song>() ?: emptyList()
            "recently_played" -> recentlyPlayed ?: emptyList()
            "most_played" -> mostPlayedSongsForHome ?: emptyList()
            "from_the_community" -> communityPlaylists?.map { it.playlist } ?: emptyList()
            "all_time_hits" -> allTimeHits ?: emptyList()
            else -> emptyList()
        }
    }

    val totalDuration = remember(items) {
        items.sumOf {
            when (it) {
                is SongItem -> it.duration ?: 0
                is Song -> it.song.duration
                else -> 0
            }
        }.toLong()
    }

    val uniqueArtists = remember(items) {
        val artists = mutableMapOf<String, Artist>()
        items.forEach { item ->
            when (item) {
                is SongItem -> item.artists.forEach { artist -> if (artist.id != null) artists[artist.id!!] = artist }
                is Song -> item.artists.forEach { artist -> artists[artist.id] = Artist(name = artist.name, id = artist.id) }
            }
        }
        artists.values.toList()
    }

    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                    bottom = 8.dp
                )
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            itemsIndexed(items) { index, item ->
                when (item) {
                    is SongItem -> {
                        YouTubeListItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val ytItems = items.filterIsInstance<SongItem>()
                                    val mappedIndex = ytItems.indexOf(item)
                                    if (mappedIndex != -1) {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = title,
                                                items = ytItems.map { it.toMediaMetadata().toMediaItem() },
                                                startIndex = mappedIndex
                                            )
                                        )
                                    }
                                },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(song = item, navController = navController, onDismiss = { menuState.dismiss() })
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_horiz),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        )
                    }
                    is Song -> {
                        SongListItem(
                            song = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val songItems = items.filterIsInstance<Song>()
                                    val mappedIndex = songItems.indexOf(item)
                                    if (mappedIndex != -1) {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = title,
                                                items = songItems.map { it.toMediaItem() },
                                                startIndex = mappedIndex
                                            )
                                        )
                                    }
                                },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(originalSong = item, navController = navController, onDismiss = { menuState.dismiss() })
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_horiz),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        )
                    }
                    is Album -> {
                        AlbumListItem(
                            album = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("album/${item.id}") }
                        )
                    }
                    is com.example.musicfy.db.entities.Playlist -> {
                        PlaylistListItem(
                            playlist = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.id == "liked") {
                                        navController.navigate("auto_playlist/liked")
                                    } else {
                                        navController.navigate("local_playlist/${item.id}")
                                    }
                                }
                        )
                    }
                    is AlbumItem -> {
                        YouTubeListItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("album/${item.id}") }
                        )
                    }
                    is ArtistItem -> {
                        YouTubeListItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("artist/${item.id}") }
                        )
                    }
                    is PlaylistItem -> {
                        YouTubeListItem(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("online_playlist/${item.id}") }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Total : ${(totalDuration).formatAsDuration()} - ${items.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uniqueArtists.isNotEmpty() && sectionId != "history") {
                item {
                    Text(
                        text = "Featured Artist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uniqueArtists) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(80.dp)
                                    .clickable {
                                        artist.id?.let { artistId ->
                                            navController.navigate("artist/$artistId")
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                                    )

                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Long.formatAsDuration(): String {
    val seconds = this
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
}
