// PlaylistScreenMenus.kt

package com.example.musicfy.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.example.musicfy.R
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.PlaylistSong
import com.example.musicfy.ui.component.Material3MenuGroup
import com.example.musicfy.ui.component.Material3MenuItemData
import com.example.musicfy.ui.component.SheetActionCard
import com.example.musicfy.ui.component.SheetDivider
import com.example.musicfy.ui.component.SheetSearchLauncher

/**
 * The menu behind a playlist screen's "more" button, laid out as in the mock: a search that opens
 * the screen's own song search, Download and Edit playlist as two cards, then Play next, Sort by,
 * Edit order and Add to playlist - and the rest of the options as they were.
 *
 * Sort by and Edit order are the screen's sort dialog and its lock on reordering; they used to be
 * "Sort order" and "Lock/Unlock editing" further down the list.
 */
@Composable
fun LocalPlaylistMenu(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    context: Context,
    downloadState: Int,
    onEdit: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
    locked: Boolean = true,
    onToggleLock: () -> Unit = {},
    onShowSortDialog: () -> Unit = {},
    onSearch: () -> Unit = {},
    onPlayNext: () -> Unit = {},
) {
    val isYouTubePlaylist = playlist.playlist.browseId != null
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val topSong = remember(songs) {
        songs.minWithOrNull(compareBy({ it.map.position }, { it.map.id }))?.song
    }

    // Adds every song of this playlist to another one. Opens over this menu, which comes back
    // forward behind it.
    AddToPlaylistDialog(
        isVisible = showAddToPlaylist,
        onGetSong = { songs.map { it.song.id } },
        onDismiss = { showAddToPlaylist = false },
        topSongId = topSong?.id,
        topSongArtworkUrl = topSong?.song?.thumbnailUrl,
    )

    val downloadIcon = if (downloadState == Download.STATE_COMPLETED) R.drawable.offline else R.drawable.download
    val downloadTitle = when (downloadState) {
        Download.STATE_COMPLETED -> stringResource(R.string.remove_download)
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> stringResource(R.string.downloading)
        else -> stringResource(R.string.action_download)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The sheet only caps its height; a short screen or a large font scrolls instead of
            // cutting the last options off.
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
    ) {
        SheetSearchLauncher(
            placeholder = "Search any song on playlist",
            onClick = {
                onDismiss()
                onSearch()
            },
        )

        SheetDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetActionCard(
                icon = downloadIcon,
                title = downloadTitle,
                onClick = {
                    onDownload()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
            // Over this menu rather than closing it: Done comes back here.
            SheetActionCard(
                icon = R.drawable.edit,
                title = "Edit playlist",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Material3MenuGroup(
            items = listOfNotNull(
                Material3MenuItemData(
                    title = { Text("Play next") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.playlist_play),
                            contentDescription = null
                        )
                    },
                    onClick = if (songs.isNotEmpty()) {
                        {
                            onPlayNext()
                            onDismiss()
                        }
                    } else null,
                ),
                Material3MenuItemData(
                    title = { Text("Sort by") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.drag_handle),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onShowSortDialog()
                        onDismiss()
                    },
                ),
                if (playlist.playlist.isEditable) {
                    Material3MenuItemData(
                        title = { Text(if (locked) "Edit order" else "Lock order") },
                        icon = {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock_open else R.drawable.lock),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onToggleLock()
                            onDismiss()
                        },
                    )
                } else null,
                Material3MenuItemData(
                    title = { Text("Add to playlist") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.playlist_add),
                            contentDescription = null
                        )
                    },
                    onClick = if (songs.isNotEmpty()) {
                        { showAddToPlaylist = true }
                    } else null,
                ),
            )
        )

        Spacer(Modifier.height(12.dp))

        Material3MenuGroup(
            items = buildList {
                if (isYouTubePlaylist) {
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.action_sync)) },
                            description = { Text(stringResource(R.string.sync_playlist_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onSync()
                                onDismiss()
                            }
                        )
                    )
                }

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.add_to_queue)) },
                        description = { Text(stringResource(R.string.add_to_queue_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onQueue()
                            onDismiss()
                        }
                    )
                )

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.share)) },
                        description = { Text(stringResource(R.string.share_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            val shareText = if (isYouTubePlaylist) {
                                "https://music.youtube.com/playlist?list=${playlist.playlist.browseId}"
                            } else {
                                songs.joinToString("\n") { it.song.song.title }
                            }
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                            onDismiss()
                        }
                    )
                )

                add(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.delete)) },
                        description = { Text(stringResource(R.string.delete_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onDelete()
                            onDismiss()
                        }
                    )
                )
            }
        )
    }
}

@Composable
fun AutoPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            if (true) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}

@Composable
fun TopPlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            if (true) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}

@Composable
fun CachePlaylistMenu(
    downloadState: Int,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {

    val downloadMenuItem = when (downloadState) {
        Download.STATE_COMPLETED -> Material3MenuItemData(
            title = { Text(stringResource(R.string.remove_download)) },
            description = { Text(stringResource(R.string.remove_download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
            title = { Text(stringResource(R.string.downloading)) },
            description = { Text(stringResource(R.string.download_in_progress_desc)) },
            icon = {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
        else -> Material3MenuItemData(
            title = { Text(stringResource(R.string.action_download)) },
            description = { Text(stringResource(R.string.download_playlist_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null
                )
            },
            onClick = {
                onDownload()
                onDismiss()
            }
        )
    }

    Material3MenuGroup(
        items = listOfNotNull(
            if (true) {
                Material3MenuItemData(
                    title = { Text(stringResource(R.string.add_to_queue)) },
                    description = { Text(stringResource(R.string.add_to_queue_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onQueue()
                        onDismiss()
                    }
                )
            } else null,
            downloadMenuItem
        )
    )
}
