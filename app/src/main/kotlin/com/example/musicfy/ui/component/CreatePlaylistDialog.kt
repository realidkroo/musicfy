// CreatePlaylistDialog.kt

package com.example.musicfy.ui.component

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.innertube.YouTube
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.YtSyncNewPlaylistsKey
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.extensions.isSyncEnabled
import com.example.musicfy.playlistcover.CoverChoice
import com.example.musicfy.playlistcover.PlaylistCoverStyle
import com.example.musicfy.playlistcover.PlaylistCovers
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Creating a playlist, as a sheet (the "Create playlist" mock): a carousel of generated covers -
 * or a picture of the user's own - then the title and description. The name is kept for the old
 * dialog this replaces; it is shown through the app's popup system like every other sheet, and
 * [onDismiss] is called once it has closed, however it was closed.
 *
 * @param topSongId the song the playlist is being made for, if there is one. It becomes the top
 *   song, so the covers take their colours from its [topSongArtworkUrl]; without one they take a
 *   colour of their own and are redrawn from the first song that is added.
 */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
    topSongId: String? = null,
    topSongArtworkUrl: String? = null,
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val context = LocalContext.current
    val appContext = context.applicationContext

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()
    val syncNewPlaylists by rememberPreference(YtSyncNewPlaylistsKey, false)

    // Decided up front so the cover's file, its seeded colours and the playlist all agree on it.
    val playlistId = remember { PlaylistEntity.generatePlaylistId() }
    val form = remember { PlaylistFormState(initialTextFieldValue.orEmpty(), "") }
    LaunchedEffect(allowSyncing, syncNewPlaylists, isSignedIn) {
        form.sync = allowSyncing && syncNewPlaylists && isSignedIn
    }

    // The sheet's content is composed later, by the popup host, from lambdas made here - what can
    // change after that is read through these.
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnCreated by rememberUpdatedState(onPlaylistCreated)
    val signedIn by rememberUpdatedState(isSignedIn)

    DisposableEffect(menuState) {
        var closed = false
        // Set by the show() below, before anything here can run: the sheet is composed afterwards.
        var sheet: PopupSheetHandle? = null
        sheet = menuState.showWithHandle(
            buttonBar = {
                val scope = rememberCoroutineScope()
                SheetPillButton(
                    text = if (form.busy) "Creating…" else "Create playlist",
                    enabled = form.name.isNotBlank() && !form.busy,
                    onClick = {
                        form.busy = true
                        // Nothing may close the sheet under the work below.
                        sheet?.setLocked(true)
                        scope.launch(Dispatchers.IO) {
                            try {
                                val name = form.name.trim()
                                val browseId = when {
                                    form.sync && signedIn ->
                                        // a failed YouTube create still leaves the user a local playlist
                                        runCatching { YouTube.createPlaylist(name) }.getOrNull()
                                    form.sync -> {
                                        Timber.tag("CreatePlaylistDialog").w("Not signed in")
                                        form.busy = false
                                        sheet?.setLocked(false)
                                        return@launch
                                    }
                                    else -> null
                                }

                                val choice = form.choice ?: CoverChoice.Generated(PlaylistCoverStyle.GRADIENT_TOP)
                                val cover = PlaylistCovers.create(
                                    context = appContext,
                                    playlistId = playlistId,
                                    title = name,
                                    choice = choice,
                                    topSongId = topSongId,
                                    artworkUrl = topSongArtworkUrl,
                                )

                                val playlist = PlaylistEntity(
                                    id = playlistId,
                                    name = name,
                                    browseId = browseId,
                                    bookmarkedAt = LocalDateTime.now(),
                                    isEditable = true,
                                    thumbnailUrl = cover,
                                    description = form.description.trim().ifBlank { null },
                                )
                                database.query { insert(playlist) }

                                withContext(Dispatchers.Main) {
                                    currentOnCreated?.invoke(playlistId)
                                    sheet?.dismiss()
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.tag("CreatePlaylistDialog").e(e, "Creating the playlist failed")
                                form.busy = false
                                sheet?.setLocked(false)
                            }
                        }
                    },
                )
            },
            onClosed = {
                closed = true
                currentOnDismiss()
            },
        ) {
            PlaylistFormContent(
                form = form,
                seed = playlistId,
                artworkUrl = topSongArtworkUrl,
                startPage = 0,
                existingImage = null,
                keepAllowed = false,
                extra = if (allowSyncing) {
                    {
                        val ctx = LocalContext.current
                        SheetOptionRow(
                            title = stringResource(R.string.sync_playlist),
                            subtitle = stringResource(R.string.allows_for_sync_witch_youtube),
                            leading = { SheetIconBadge(R.drawable.sync) },
                            trailing = {
                                AppSwitch(
                                    checked = form.sync,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                if (form.sync) {
                                    // switching off is always allowed
                                    form.sync = false
                                } else if (!signedIn) {
                                    Toast.makeText(ctx, ctx.getString(R.string.not_logged_in_youtube), Toast.LENGTH_SHORT).show()
                                } else if (!ctx.isSyncEnabled()) {
                                    Toast.makeText(ctx, ctx.getString(R.string.sync_disabled), Toast.LENGTH_SHORT).show()
                                } else {
                                    form.sync = true
                                }
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
        // The caller took the dialog away with the sheet still up: take the sheet away with it.
        onDispose { if (!closed) sheet?.dismiss() }
    }
}
