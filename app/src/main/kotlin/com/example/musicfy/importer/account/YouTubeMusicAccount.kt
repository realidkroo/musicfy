// YouTubeMusicAccount.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.toImportedTrack
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.utils.completed

private const val LIKED_ID = "LM"

/** Uses the app's own YouTube sign-in; songs keep their exact ids, so nothing is searched. */
class YouTubeMusicLibrary(
    override val accountName: String?,
    override val accountPhotoUrl: String? = null,
) : AccountLibrary {

    override suspend fun playlists(): List<RemotePlaylist> {
        val page = YouTube.library("FEmusic_liked_playlists").completed().getOrElse {
            throw AccountImportException("Couldn't read your YouTube Music playlists. Check that you're signed in.")
        }
        val result = mutableListOf(RemotePlaylist(LIKED_ID, "Liked music", null, isLiked = true))
        page.items.filterIsInstance<PlaylistItem>()
            // LM is listed above; SE is the "Episodes for later" podcast list
            .filterNot { it.id == LIKED_ID || it.id == "SE" }
            .forEach { playlist ->
                result += RemotePlaylist(
                    id = playlist.id,
                    name = playlist.title,
                    trackCount = playlist.songCountText?.let { Regex("""\d[\d,.]*""").find(it)?.value?.filter(Char::isDigit)?.toIntOrNull() },
                    subtitle = playlist.author?.name,
                    coverUrl = playlist.thumbnail,
                )
            }
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val page = YouTube.playlist(playlist.id).completed().getOrElse {
            throw AccountImportException("Couldn't open \"${playlist.name}\" on YouTube Music.")
        }
        return page.songs.map { it.toImportedTrack() }
    }
}
