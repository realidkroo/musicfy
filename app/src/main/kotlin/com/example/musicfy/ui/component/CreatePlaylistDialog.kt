// CreatePlaylistDialog.kt

package com.example.musicfy.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.YtSyncNewPlaylistsKey
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.extensions.isSyncEnabled
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.logging.Logger

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()
    val syncNewPlaylists by rememberPreference(YtSyncNewPlaylistsKey, false)
    var syncedPlaylist by remember(syncNewPlaylists, isSignedIn) {
        mutableStateOf(allowSyncing && syncNewPlaylists && isSignedIn)
    }

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                val browseId = if (syncedPlaylist && isSignedIn) {
                    // a failed YouTube create still leaves the user a local playlist
                    runCatching { YouTube.createPlaylist(playlistName) }.getOrNull()
                } else if (syncedPlaylist) {
                    Logger.getLogger("CreatePlaylistDialog").warning("Not signed in")
                    return@launch
                } else null

                val playlistEntity = PlaylistEntity(
                    name = playlistName,
                    browseId = browseId,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                )

                database.query {
                    insert(playlistEntity)
                }

                withContext(Dispatchers.Main) {
                    onPlaylistCreated?.invoke(playlistEntity.id)
                }
            }
        },
        extraContent = {
            if (allowSyncing) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sync_playlist),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.allows_for_sync_witch_youtube),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSwitch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                if (syncedPlaylist) {
                                    // switching off is always allowed
                                    syncedPlaylist = false
                                    return@AppSwitch
                                }
                                val isYtmSyncEnabled = context.isSyncEnabled()
                                if (!isSignedIn && !syncedPlaylist) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.not_logged_in_youtube),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (!isYtmSyncEnabled) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.sync_disabled),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    syncedPlaylist = !syncedPlaylist
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
