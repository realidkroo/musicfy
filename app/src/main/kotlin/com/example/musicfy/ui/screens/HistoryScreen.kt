package com.example.musicfy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.extensions.toMediaItem
import com.example.musicfy.playback.queues.ListQueue
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.menu.SongMenu
import com.example.musicfy.viewmodels.DateAgo
import com.example.musicfy.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val eventsMap by historyViewModel.events.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            eventsMap.forEach { (dateAgo, eventsList) ->
                item(key = "header_${dateAgo.hashCode()}") {
                    val headerText = when (dateAgo) {
                        DateAgo.Today -> stringResource(R.string.today)
                        DateAgo.Yesterday -> stringResource(R.string.yesterday)
                        DateAgo.ThisWeek -> stringResource(R.string.this_week)
                        DateAgo.LastWeek -> stringResource(R.string.last_week)
                        is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }

                itemsIndexed(
                    items = eventsList,
                    key = { _, event -> event.event.id }
                ) { index, eventWithSong ->
                    SongListItem(
                        song = eventWithSong.song,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val allSongs = eventsMap.values.flatten().map { it.song }
                                val mappedIndex = allSongs.indexOf(eventWithSong.song)
                                if (mappedIndex != -1) {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = "History",
                                            items = allSongs.map { it.toMediaItem() },
                                            startIndex = mappedIndex
                                        )
                                    )
                                }
                            },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(originalSong = eventWithSong.song, navController = navController, onDismiss = { menuState.dismiss() })
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
            }
        }
    }
}
