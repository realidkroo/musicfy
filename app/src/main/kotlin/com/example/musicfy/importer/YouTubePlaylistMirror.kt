// YouTubePlaylistMirror.kt

package com.example.musicfy.importer

import com.example.musicfy.db.MusicDatabase
import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Creates a YouTube copy of a local playlist and links the two, so later edits reach both. */
object YouTubePlaylistMirror {

    /** True when the playlist exists on YouTube with all its (YouTube) songs added. */
    suspend fun mirror(database: MusicDatabase, playlistId: String, name: String): Boolean {
        val newBrowseId = runCatching { YouTube.createPlaylist(name) }.getOrNull() ?: return false
        val current = database.playlist(playlistId).first()?.playlist ?: return false
        database.update(current.copy(browseId = newBrowseId))

        val ids = database.playlistSongs(playlistId).first()
            .sortedBy { it.map.position }
            // songs from files on this phone have no YouTube video to add
            .filterNot { it.song.song.isLocal }
            .map { it.map.songId }
        if (ids.isEmpty()) return true
        if (YouTube.addVideosToPlaylist(newBrowseId, ids).isFailure) return false
        attachSetVideoIds(database, playlistId, newBrowseId)
        return true
    }

    /**
     * Removing a song from a YouTube playlist needs the per-entry setVideoId, which only exists once
     * YouTube has the songs. Fill them in without ever dropping a local entry.
     */
    private suspend fun attachSetVideoIds(database: MusicDatabase, playlistId: String, browseId: String) {
        delay(1500)
        val remote = YouTube.playlist(browseId).completed().getOrNull()?.songs ?: return
        val queues = remote.filter { it.setVideoId != null }
            .groupBy({ it.id }, { it.setVideoId!! })
            .mapValues { ArrayDeque(it.value) }
        if (queues.isEmpty()) return
        val maps = database.playlistSongs(playlistId).first().map { it.map }.sortedBy { it.position }
        database.withTransaction {
            maps.forEach { map ->
                if (map.setVideoId == null) {
                    queues[map.songId]?.removeFirstOrNull()?.let { update(map.copy(setVideoId = it)) }
                }
            }
        }
    }
}
