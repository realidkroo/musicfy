// PlayerMenu.kt
// what is this for you ask its for player menu ofc

package com.example.musicfy.ui.menu

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.pluralStringResource
import com.example.musicfy.ui.component.ActionPromptDialog
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil

import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.ListItemHeight
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.ListDialog
import com.example.musicfy.ui.component.Material3MenuGroup
import com.example.musicfy.ui.component.Material3MenuItemData
import com.example.musicfy.ui.component.NewAction
import com.example.musicfy.ui.component.NewActionGrid
import com.example.musicfy.ui.component.VolumeSlider
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    
    // Cast state for volume control - safely access castConnectionHandler to prevent crashes
    val castHandler = remember(playerConnection) {
        try {
            playerConnection.service.castConnectionHandler
        } catch (e: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    


    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction {
                insert(mediaMetadata)
            }
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )



    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                    Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }
    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }

    if (showSleepTimerDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
            },
            onCancel = {
                showSleepTimerDialog = false
            },
            onReset = {
                sleepTimerValue = 30f // Default value
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        }
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            }
        )
    }

    if (isQueueTrigger != true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 6.dp),
        ) {
            // Show Cast indicator when casting
            if (isCasting && castDeviceName != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.cast),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.casting_to, castDeviceName ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            VolumeSlider(
                value = if (isCasting) castVolume else playerVolume.value,
                onValueChange = { volume ->
                    if (isCasting) {
                        castHandler?.setVolume(volume)
                    } else {
                        playerConnection.service.playerVolume.value = volume
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            val startingRadioText = stringResource(R.string.starting_radio)
            NewActionGrid(
                actions = listOfNotNull(
                    if (true) {
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.start_an_radio),
                            onClick = {
                                Toast.makeText(context, startingRadioText, Toast.LENGTH_SHORT).show()
                                playerConnection.startRadioSeamlessly()
                                onDismiss()
                            }
                        )
                    } else null,
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.add_to_an_playlist),
                        onClick = { showChoosePlaylistDialog = true }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            val intent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, null))
                            onDismiss()
                        }
                    )
                ),
                columns = 3,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
            )
        }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (artists.isNotEmpty()) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.view_artist)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.artists.joinToString { it.name },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    if (mediaMetadata.artists.size == 1) {
                                        navController.navigate("artist/${mediaMetadata.artists[0].id}")
                                        playerBottomSheetState.collapseSoft()
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                }
                            )
                        )
                    }
                    if (mediaMetadata.album != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.view_album)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.album.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    navController.navigate("album/${mediaMetadata.album.id}")
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            )
                        )
                    }
                    // Add to Library option
                    val isInLibrary = librarySong?.song?.inLibrary != null
                    add(
                        Material3MenuItemData(
                            title = { 
                                Text(
                                    text = stringResource(
                                        if (isInLibrary) R.string.remove_from_library
                                        else R.string.add_to_library
                                    )
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (isInLibrary) R.drawable.library_add_check
                                        else R.drawable.library_add
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                playerConnection.toggleLibrary()
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = stringResource(R.string.remove_download)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        mediaMetadata.id,
                                        false,
                                    )
                                }
                            )
                        }

                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        mediaMetadata.id,
                                        false,
                                    )
                                }
                            )
                        }

                        else -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_download)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    database.transaction {
                                        insert(mediaMetadata)
                                    }
                                    val downloadRequest =
                                        DownloadRequest
                                            .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                            .setCustomCacheKey(mediaMetadata.id)
                                            .setData(mediaMetadata.title.toByteArray())
                                            .build()
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        false,
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }



        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                onShowDetailsDialog()
                                onDismiss()
                            }
                        )
                    )

                    if (isQueueTrigger != true) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.equalizer)) },
                                description = { Text(text = stringResource(R.string.equalizer_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.equalizer),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    navController.navigate("equalizer")
                                    onDismiss()
                                }
                            )
                        )
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.advanced)) },
                                description = { Text(text = stringResource(R.string.advanced_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.tune),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    showPitchTempoDialog = true
                                }
                            )
                        )
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.sleep_timer)) },
                                description = { Text(text = if (sleepTimerEnabled) "Active" else "Disabled") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.sleep_timer),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                onClick = {
                                    if (sleepTimerEnabled) {
                                        playerConnection.service.sleepTimer.clear()
                                    } else {
                                        showSleepTimerDialog = true
                                    }
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var tempo by remember {
        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
    }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
    }
    val updatePlaybackParameters = {
        playerConnection.player.playbackParameters =
            PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                },
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column {
                ValueAdjuster(
                    icon = R.drawable.speed,
                    currentValue = tempo,
                    values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
                    onValueUpdate = {
                        tempo = it
                        updatePlaybackParameters()
                    },
                    valueText = { "${String.format("%.2f", it)}x" },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                ValueAdjuster(
                    icon = R.drawable.discover_tune,
                    currentValue = transposeValue,
                    values = (-12..12).toList(),
                    onValueUpdate = {
                        transposeValue = it
                        updatePlaybackParameters()
                    },
                    valueText = { "${if (it > 0) "+" else ""}$it" },
                )
            }
        },
    )
}

@Composable
fun <T> ValueAdjuster(
    @DrawableRes icon: Int,
    currentValue: T,
    values: List<T>,
    onValueUpdate: (T) -> Unit,
    valueText: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        IconButton(
            enabled = currentValue != values.first(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) - 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.remove),
                contentDescription = null,
            )
        }

        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp),
        )

        IconButton(
            enabled = currentValue != values.last(),
            onClick = {
                onValueUpdate(values[values.indexOf(currentValue) + 1])
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
            )
        }
    }
}
