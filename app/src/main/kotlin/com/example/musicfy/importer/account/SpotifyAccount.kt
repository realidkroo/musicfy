// SpotifyAccount.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.array
import com.example.musicfy.importer.long
import com.example.musicfy.importer.obj
import com.example.musicfy.importer.string

private const val API = "https://api.spotify.com/v1"
private const val SERVICE = "Spotify"
private const val LIKED_ID = "spotify:liked"

/** The web player's token works with the Web API; the logged-out token fails /me and is skipped. */
object SpotifyConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        for (token in credentials.untriedBearers("spotify.com")) {
            val response = AccountHttp.get("$API/me", mapOf("Authorization" to "Bearer $token"), SERVICE)
            when (response.status) {
                200 -> {
                    val me = AccountHttp.parse(response.body)
                    // the anonymous web-player token has no user behind it
                    if (me?.string("id") == null) {
                        credentials.markTried(token)
                        continue
                    }
                    return SpotifyLibrary(
                        token = token,
                        accountName = me.string("display_name") ?: me.string("id"),
                        accountPhotoUrl = me["images"].array().firstOrNull()?.obj()?.string("url"),
                    )
                }
                429 -> throw AccountRetryLater(response.retryAfterSeconds?.coerceIn(2, 30) ?: 5)
                else -> credentials.markTried(token)
            }
        }
        return null
    }
}

private class SpotifyLibrary(
    token: String,
    override val accountName: String?,
    override val accountPhotoUrl: String?,
) : AccountLibrary {
    private val headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")

    override suspend fun playlists(): List<RemotePlaylist> {
        val result = mutableListOf<RemotePlaylist>()
        val likedTotal = AccountHttp.getJson("$API/me/tracks?limit=1", headers, SERVICE)?.long("total")?.toInt()
        result += RemotePlaylist(LIKED_ID, "Liked Songs", likedTotal, isLiked = true)

        var next: String? = "$API/me/playlists?limit=50"
        var pages = 0
        while (next != null && pages++ < MAX_PAGES) {
            val page = AccountHttp.getJson(next, headers, SERVICE) ?: break
            page["items"].array().forEach { element ->
                val playlist = element.obj() ?: return@forEach
                val id = playlist.string("id") ?: return@forEach
                // the track count moved between "tracks" and "items" across API versions
                val count = (playlist["tracks"].obj() ?: playlist["items"].obj())?.long("total")?.toInt()
                result += RemotePlaylist(
                    id = id,
                    name = playlist.string("name") ?: "Untitled playlist",
                    trackCount = count,
                    subtitle = playlist["owner"].obj()?.string("display_name")?.let { "by $it" },
                    coverUrl = playlist["images"].array().firstOrNull()?.obj()?.string("url"),
                )
            }
            next = page.string("next")?.takeIf { it.startsWith(API) }
        }
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val tracks = mutableListOf<ImportedTrack>()
        var next: String? = if (playlist.isLiked) "$API/me/tracks?limit=50" else "$API/playlists/${playlist.id}/tracks?limit=100"
        var triedItemsPath = playlist.isLiked
        var pages = 0
        while (next != null && pages++ < MAX_PAGES) {
            val page = AccountHttp.getJson(next, headers, SERVICE, notFoundAsNull = !triedItemsPath)
            if (page == null) {
                // newer API versions name the endpoint /items
                triedItemsPath = true
                next = "$API/playlists/${playlist.id}/items?limit=100"
                continue
            }
            triedItemsPath = true
            page["items"].array().forEach { element ->
                val entry = element.obj() ?: return@forEach
                val track = entry["track"].obj() ?: entry["item"].obj() ?: return@forEach
                // podcast episodes saved into playlists aren't music
                if (track.string("type") == "episode") return@forEach
                val title = track.string("name") ?: return@forEach
                tracks += ImportedTrack(
                    title = title,
                    artist = track["artists"].array().mapNotNull { it.obj()?.string("name") }.joinToString(", "),
                    album = track["album"].obj()?.string("name"),
                    durationMs = track.long("duration_ms"),
                )
            }
            next = page.string("next")?.takeIf { it.startsWith(API) }
        }
        return tracks
    }

    private companion object {
        const val MAX_PAGES = 400
    }
}
