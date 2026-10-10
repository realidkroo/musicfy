// TidalAccount.kt

package com.example.musicfy.importer.account

import android.util.Base64
import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.array
import com.example.musicfy.importer.dateMs
import com.example.musicfy.importer.idString
import com.example.musicfy.importer.long
import com.example.musicfy.importer.obj
import com.example.musicfy.importer.string
import kotlinx.serialization.json.JsonObject

private const val API = "https://api.tidal.com/v1"
private const val SERVICE = "Tidal"
private const val LIKED_ID = "tidal:favorites"

/** The web player's token, plus the user id and country every library call needs. */
object TidalConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        for (token in credentials.untriedBearers("tidal.com")) {
            val auth = mapOf("Authorization" to "Bearer $token")
            val response = AccountHttp.get("$API/sessions", auth, SERVICE)
            when (response.status) {
                200 -> {
                    val session = AccountHttp.parse(response.body)
                    val userId = session?.idString("userId")
                    val country = session?.string("countryCode")
                    if (userId != null && country != null) return TidalLibrary(auth, userId, country)
                    credentials.markTried(token)
                }
                429 -> throw AccountRetryLater(response.retryAfterSeconds?.coerceIn(2, 30) ?: 5)
                else -> {
                    // some tokens are refused by /sessions but carry the user in the token itself
                    jwtUser(token)?.let { (userId, country) ->
                        val probe = AccountHttp.get("$API/users/$userId/playlists?limit=1&countryCode=$country", auth, SERVICE)
                        if (probe.status == 200) return TidalLibrary(auth, userId, country)
                    }
                    credentials.markTried(token)
                }
            }
        }
        return null
    }

    private fun jwtUser(token: String): Pair<String, String>? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return null
        val claims = AccountHttp.parse(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            ?: return null
        val userId = claims.idString("uid") ?: return null
        val country = claims.string("cc") ?: return null
        userId to country
    }.getOrNull()
}

private class TidalLibrary(
    private val headers: Map<String, String>,
    private val userId: String,
    private val country: String,
) : AccountLibrary {
    override val accountName: String? = null

    override suspend fun playlists(): List<RemotePlaylist> {
        val likedTotal = AccountHttp.getJson("$API/users/$userId/favorites/tracks?limit=1&countryCode=$country", headers, SERVICE)
            ?.long("totalNumberOfItems")?.toInt()
        val result = mutableListOf(RemotePlaylist(LIKED_ID, "My Tracks", likedTotal, subtitle = "Your favorite tracks", isLiked = true))
        val seen = HashSet<String>()

        forEachPage("$API/users/$userId/playlists?countryCode=$country", pageSize = 50) { item ->
            item.toPlaylist(subtitle = null)?.takeIf { seen.add(it.id) }?.let { result += it }
        }
        forEachPage("$API/users/$userId/favorites/playlists?countryCode=$country", pageSize = 50) { entry ->
            entry["item"].obj()?.toPlaylist(subtitle = "Saved playlist")?.takeIf { seen.add(it.id) }?.let { result += it }
        }
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val url = if (playlist.isLiked) "$API/users/$userId/favorites/tracks?countryCode=$country&order=DATE&orderDirection=DESC"
        else "$API/playlists/${playlist.id}/items?countryCode=$country"
        val tracks = mutableListOf<ImportedTrack>()
        forEachPage(url, pageSize = 100) { entry ->
            // playlists can hold music videos too; only tracks have an album
            val type = entry.string("type")
            if (type != null && type != "track") return@forEachPage
            val track = entry["item"].obj() ?: return@forEachPage
            val title = track.string("title") ?: return@forEachPage
            val version = track.string("version")
            tracks += ImportedTrack(
                title = if (version != null && !title.contains(version, ignoreCase = true)) "$title ($version)" else title,
                artist = track["artists"].array().mapNotNull { it.obj()?.string("name") }.joinToString(", ")
                    .ifEmpty { track["artist"].obj()?.string("name").orEmpty() },
                album = track["album"].obj()?.string("title"),
                durationMs = track.long("duration")?.times(1000),
                // favorites say when they were added; playlist entries don't
                addedAtMs = dateMs(entry.string("created")),
            )
        }
        return tracks
    }

    private fun JsonObject.toPlaylist(subtitle: String?): RemotePlaylist? {
        val id = string("uuid") ?: return null
        // Tidal image ids are UUIDs whose dashes become path separators
        val cover = string("squareImage")?.let { "https://resources.tidal.com/images/${it.replace('-', '/')}/640x640.jpg" }
            ?: string("image")?.let { "https://resources.tidal.com/images/${it.replace('-', '/')}/750x500.jpg" }
        return RemotePlaylist(
            id = id,
            name = string("title") ?: "Untitled playlist",
            trackCount = long("numberOfTracks")?.toInt(),
            subtitle = subtitle,
            coverUrl = cover,
        )
    }

    /** Offset paging until the reported total, or an empty page. */
    private suspend fun forEachPage(baseUrl: String, pageSize: Int, onItem: (JsonObject) -> Unit) {
        var offset = 0
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = AccountHttp.getJson("$baseUrl&limit=$pageSize&offset=$offset", headers, SERVICE, notFoundAsNull = true) ?: return
            val items = page["items"].array()
            items.forEach { it.obj()?.let(onItem) }
            offset += items.size
            val total = page.long("totalNumberOfItems")
            if (items.isEmpty() || (total != null && offset >= total)) return
        }
    }

    private companion object {
        const val MAX_PAGES = 400
    }
}
