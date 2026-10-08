// AppleMusicAccount.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.array
import com.example.musicfy.importer.long
import com.example.musicfy.importer.obj
import com.example.musicfy.importer.path
import com.example.musicfy.importer.string
import kotlinx.serialization.json.JsonObject

private const val HOST = "https://amp-api.music.apple.com"
private const val SERVICE = "Apple Music"
private const val LIBRARY_SONGS_ID = "apple:library-songs"

/** Names iOS gives the auto playlist of loved songs; those import as Liked songs. */
private val FAVORITES_NAMES = setOf("favorite songs", "favourite songs")

/**
 * Needs two tokens from the web player: the developer token (sent as the bearer) and the
 * music-user token that only exists once the user signs in.
 */
object AppleMusicConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        val userToken = credentials.appleUserToken ?: return null
        val developerTokens = credentials.untriedBearers("apple.com").ifEmpty {
            listOfNotNull(credentials.newestBearer("apple.com"))
        }
        for (developerToken in developerTokens) {
            val pairKey = "$developerToken|$userToken"
            if (credentials.isTried(pairKey)) continue
            val headers = appleHeaders(developerToken, userToken)
            val response = AccountHttp.get("$HOST/v1/me/storefront", headers, SERVICE)
            when (response.status) {
                200 -> return AppleMusicLibrary(headers)
                429 -> throw AccountRetryLater(response.retryAfterSeconds?.coerceIn(2, 30) ?: 5)
                else -> credentials.markTried(pairKey)
            }
        }
        return null
    }

    fun appleHeaders(developerToken: String, userToken: String) = mapOf(
        "Authorization" to "Bearer $developerToken",
        "Media-User-Token" to userToken,
        "Origin" to "https://music.apple.com",
        "Referer" to "https://music.apple.com/",
        "Accept" to "application/json",
    )
}

/** Apple artwork URLs are templates: {w}x{h}, and sometimes {c} (crop) and {f} (format). */
private fun artworkUrl(template: String): String =
    template.replace("{w}", "300").replace("{h}", "300").replace("{c}", "bb").replace("{f}", "jpg")

private class AppleMusicLibrary(private val headers: Map<String, String>) : AccountLibrary {
    override val accountName: String? = null

    override suspend fun playlists(): List<RemotePlaylist> {
        val result = mutableListOf<RemotePlaylist>()
        forEachPage("$HOST/v1/me/library/playlists?limit=100") { item ->
            val id = item.string("id") ?: return@forEachPage
            val attributes = item["attributes"].obj()
            val name = attributes?.string("name") ?: "Untitled playlist"
            val isFavorites = name.lowercase() in FAVORITES_NAMES
            result += RemotePlaylist(
                id = id,
                name = name,
                trackCount = null,
                subtitle = if (isFavorites) "Imports as Liked songs" else null,
                isLiked = isFavorites,
                coverUrl = attributes?.get("artwork").obj()?.string("url")?.let(::artworkUrl),
            )
        }
        // loved songs first, like the other services list Liked songs first
        result.sortBy { if (it.isLiked) 0 else 1 }
        result += RemotePlaylist(
            id = LIBRARY_SONGS_ID,
            name = "Apple Music library",
            trackCount = null,
            subtitle = "Every song in your library, as one playlist",
        )
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val url = if (playlist.id == LIBRARY_SONGS_ID) "$HOST/v1/me/library/songs?limit=100"
        else "$HOST/v1/me/library/playlists/${playlist.id}/tracks?limit=100"
        val tracks = mutableListOf<ImportedTrack>()
        forEachPage(url) { item ->
            val attributes = item["attributes"].obj() ?: return@forEachPage
            val title = attributes.string("name") ?: return@forEachPage
            tracks += ImportedTrack(
                title = title,
                artist = attributes.string("artistName").orEmpty(),
                album = attributes.string("albumName"),
                durationMs = attributes.long("durationInMillis"),
            )
        }
        return tracks
    }

    /** Apple pages with a relative "next"; an empty playlist answers 404 instead of an empty list. */
    private suspend fun forEachPage(firstUrl: String, onItem: (JsonObject) -> Unit) {
        var next: String? = firstUrl
        var pages = 0
        while (next != null && pages++ < MAX_PAGES) {
            val page = AccountHttp.getJson(next, headers, SERVICE, notFoundAsNull = true) ?: return
            page["data"].array().forEach { it.obj()?.let(onItem) }
            next = (page.path("next") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?.takeIf { it.startsWith("/v1/") }
                ?.let { path -> HOST + path + if ("limit=" in path) "" else (if ('?' in path) "&" else "?") + "limit=100" }
        }
    }

    private companion object {
        const val MAX_PAGES = 400
    }
}
