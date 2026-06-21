// ImportPlaylistDialog.kt
// the file functioned as import playlist dialog

package com.example.musicfy.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.ui.component.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>, // list of song ids. Songs should be inserted to database in this function.
    playlistTitle: String,
    onDismiss: () -> Unit,
    onImportedPlaylist: (String) -> Unit = {},
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val textFieldValue by remember { mutableStateOf(TextFieldValue(text = playlistTitle)) }
    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = textFieldValue,
            autoFocus = false,
            onDismiss = onDismiss,
            onDone = { finalName ->
                val newPlaylist = PlaylistEntity(
                    name = finalName
                )
                coroutineScope.launch {
                    val importedPlaylistId = withContext(Dispatchers.IO) {
                        database.query { insert(newPlaylist) }
                        val playlist = database.playlist(newPlaylist.id).firstOrNull()

                        if (playlist != null) {
                            val importedSongIds = onGetSong()
                            database.addSongToPlaylist(playlist, importedSongIds)
                        }

                        newPlaylist.id
                    }

                    onDismiss()
                    onImportedPlaylist(importedPlaylistId)
                }
            }
        )
    }
}
