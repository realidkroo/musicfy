// artistlistdetailscreenkt
// "see all" destination for the unified artist list home section - doesn't
// sectiondetailscreen's flat song-list template since it renders compound

package com.example.musicfy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.models.toMediaMetadata
import com.example.musicfy.playback.queues.YouTubeQueue
import com.example.musicfy.ui.component.ArtistListCard
import com.example.musicfy.viewmodels.HomeViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistListDetailScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
) {
    val groups by homeViewModel.artistListItems.collectAsState()
    val playerConnection = LocalPlayerConnection.current

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
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.artist_list),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = groups.orEmpty(),
                key = { "${it.artistId}_${it.artistName}" }
            ) { group ->
                ArtistListCard(
                    group = group,
                    modifier = Modifier.width(320.dp),
                    onClick = {
                        group.artistId?.let { navController.navigate("artist/$it") }
                    },
                    onItemClick = { item ->
                        when (item) {
                            is SongItem -> playerConnection?.playQueue(
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
