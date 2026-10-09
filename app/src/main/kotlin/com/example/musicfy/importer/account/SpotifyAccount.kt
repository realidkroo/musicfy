// SpotifyAccount.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.array
import com.example.musicfy.importer.long
import com.example.musicfy.importer.obj
import com.example.musicfy.importer.path
import com.example.musicfy.importer.string
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber

private const val API = "https://api.spotify.com/v1"
private const val SERVICE = "Spotify"
private const val LIKED_ID = "spotify:liked"

/**
 * Reads the library the way Spotify's web player does once its queries have been seen, and falls
 * back to the Web API until then. The Web API can refuse or rate-limit the web player's token, which
 * left sign-in waiting forever. The logged-out token fails both and is skipped.
 */
object SpotifyConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        SpotifyWebPlayer.connect(credentials)?.let { return it }
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

/**
 * Spotify's web player asks its own GraphQL endpoint ("pathfinder") for the library, with a query
 * name and hash it ships with. The sign-in page reports those queries (see the capture hook), and
 * the same ones are sent again here with the next offsets: the sidebar's library query for the
 * playlists, Liked Songs' query, and a playlist page's query, which the sign-in page opens once.
 */
private object SpotifyWebPlayer {
    private val LIBRARY_QUERY = Regex("""^libraryV(\d+)$""")
    private const val LIKED_URI = "spotify:collection:tracks"

    /** How long to wait for the Liked Songs and playlist queries once the library query is seen. */
    private const val WAIT_FOR_QUERIES_MS = 20_000L

    fun libraryQuery(queries: List<WebPlayerQuery>): WebPlayerQuery? =
        queries.filter { LIBRARY_QUERY.matches(it.name) }
            .maxByOrNull { LIBRARY_QUERY.find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }

    /** Liked Songs' query: "fetchLibraryTracks" at the time of writing. */
    fun likedQuery(queries: List<WebPlayerQuery>): WebPlayerQuery? =
        queries.firstOrNull { it.name == "fetchLibraryTracks" }
            ?: queries.firstOrNull { "LibraryTracks" in it.name || "LikedSongs" in it.name }

    /** The playlist page's query; "...Contents" pages through tracks, the plain one carries them too. */
    fun playlistQuery(queries: List<WebPlayerQuery>): WebPlayerQuery? =
        queries.firstOrNull { it.name == "fetchPlaylistContents" }
            ?: queries.firstOrNull { it.name == "fetchPlaylist" }
            ?: queries.firstOrNull { it.name.startsWith("fetchPlaylist") && "Metadata" !in it.name }

    suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        val queries = credentials.queries()
        val library = libraryQuery(queries) ?: return null
        val token = credentials.newestBearer("spotify.com") ?: return null
        val client = WebPlayerClient(token, credentials.sessionHeaders())
        val firstPage = try {
            client.run(library, libraryVariables(library, offset = 0))
        } catch (e: AccountImportException) {
            Timber.w("Spotify web player library query failed: %s", e.message)
            return null
        }
        // the logged-out player has no "me"
        if (firstPage.path("data", "me").obj() == null) return null

        val playlists = LinkedHashMap<String, RemotePlaylist>()
        collectPlaylists(firstPage, null, playlists, LikedCount())
        val stillComing = likedQuery(queries) == null || (playlists.isNotEmpty() && playlistQuery(queries) == null)
        if (stillComing && System.currentTimeMillis() - library.seenAtMs < WAIT_FOR_QUERIES_MS) throw AccountRetryLater(2)
        return SpotifyWebPlayerLibrary(credentials, client, webApi = SpotifyLibrary(token, null, null))
    }

    fun libraryVariables(query: WebPlayerQuery, offset: Int): JsonObject = query.variables.withChanges(
        "offset" to JsonPrimitive(offset),
        "limit" to JsonPrimitive(pageSize(query)),
        // every playlist, not only what the sidebar's filter or open folders show
        "filters" to JsonArray(emptyList()),
        "textFilter" to JsonPrimitive(""),
        "flatten" to JsonPrimitive(true),
        "expandedFolders" to JsonArray(emptyList()),
        "folderUri" to JsonNull,
        onlyIfPresent = setOf("filters", "textFilter", "flatten", "expandedFolders", "folderUri"),
    )

    /** [uri] is the playlist's; Liked Songs' query has none. */
    fun trackVariables(query: WebPlayerQuery, uri: String?, offset: Int): JsonObject {
        val paging = query.variables.withChanges(
            "offset" to JsonPrimitive(offset),
            "limit" to JsonPrimitive(pageSize(query)),
            onlyIfPresent = emptySet(),
        )
        return if (uri == null) paging else paging.withChanges("uri" to JsonPrimitive(uri), onlyIfPresent = emptySet())
    }

    fun pageSize(query: WebPlayerQuery): Int = query.variables.long("limit")?.toInt()?.coerceIn(10, 100) ?: 50

    /** Liked Songs' size, which the library lists as a pseudo-playlist. */
    class LikedCount(var value: Int? = null)

    /**
     * Every playlist in a library page, wherever it sits: Spotify wraps each one as
     * `{_uri, data: {__typename, uri?, name, ...}}`, and folders nest more of the same.
     */
    fun collectPlaylists(element: JsonElement, parentUri: String?, out: MutableMap<String, RemotePlaylist>, liked: LikedCount) {
        when (element) {
            is JsonArray -> element.forEach { collectPlaylists(it, null, out, liked) }
            is JsonObject -> {
                val uri = element.string("uri") ?: parentUri
                val name = element.string("name")
                if (uri == LIKED_URI) element.long("count")?.let { liked.value = it.toInt() }
                if (uri != null && uri.startsWith("spotify:playlist:") && name != null) {
                    val id = uri.removePrefix("spotify:playlist:")
                    out.getOrPut(id) {
                        RemotePlaylist(
                            id = id,
                            name = name,
                            trackCount = (element.path("content").obj() ?: element.path("tracks").obj())?.long("totalCount")?.toInt(),
                            subtitle = element.path("ownerV2", "data").obj()?.string("name")?.let { "by $it" },
                            coverUrl = element.path("images", "items").array().firstOrNull()
                                ?.path("sources")?.array()?.firstOrNull()?.obj()?.string("url"),
                        )
                    }
                    return
                }
                element.forEach { (key, value) ->
                    val childUri = if (key == "data") element.string("_uri") else null
                    collectPlaylists(value, childUri, out, liked)
                }
            }
            else -> Unit
        }
    }

    /** Every track in a page: `{__typename: "Track", name, artists, albumOfTrack, ...}`, at any depth. */
    fun collectTracks(element: JsonElement, parentUri: String?, out: MutableList<ImportedTrack>) {
        when (element) {
            is JsonArray -> element.forEach { collectTracks(it, null, out) }
            is JsonObject -> {
                val uri = element.string("uri") ?: parentUri
                val name = element.string("name")
                val isTrack = element.string("__typename") == "Track" || uri?.startsWith("spotify:track:") == true
                if (isTrack && name != null) {
                    out += ImportedTrack(
                        title = name,
                        artist = element.path("artists", "items").array()
                            .mapNotNull { it.path("profile")?.obj()?.string("name") ?: it.obj()?.string("name") }
                            .joinToString(", "),
                        album = element.path("albumOfTrack").obj()?.string("name"),
                        durationMs = (element.path("trackDuration") ?: element.path("duration"))?.obj()?.long("totalMilliseconds"),
                    )
                    return
                }
                element.forEach { (key, value) ->
                    val childUri = if (key == "data") element.string("_uri") else null
                    collectTracks(value, childUri, out)
                }
            }
            else -> Unit
        }
    }

    /**
     * Where a page's list sits when Spotify's responses look as expected; paging stops by it.
     * [items] tells a page apart from the one before, in case the offset was ignored.
     */
    class PageList(val size: Int, val total: Int?, val items: JsonArray)

    fun pageList(page: JsonObject, vararg path: String): PageList? {
        val list = page.path(*path).obj() ?: return null
        val items = list["items"] as? JsonArray ?: return null
        return PageList(items.size, list.long("totalCount")?.toInt(), items)
    }

    /**
     * True when nothing comes after this page: an empty or repeated page, or the total reached.
     * Without a known shape, [found] (what this page added) decides.
     */
    fun isLastPage(list: PageList?, previous: PageList?, offset: Int, found: Int): Boolean = when {
        list == null -> found == 0
        list.size == 0 || list.items == previous?.items -> true
        else -> list.total != null && offset + list.size >= list.total
    }

    private fun JsonObject.withChanges(vararg changes: Pair<String, JsonElement>, onlyIfPresent: Set<String>): JsonObject {
        val result = LinkedHashMap<String, JsonElement>(this)
        changes.forEach { (key, value) -> if (key !in onlyIfPresent || key in this) result[key] = value }
        return JsonObject(result)
    }
}

/** Sends a query the web player made, with its own headers, and reads the answer. */
private class WebPlayerClient(token: String, sessionHeaders: Map<String, String>) {
    private val headers = sessionHeaders + mapOf(
        "authorization" to "Bearer $token",
        "accept" to "application/json",
        "origin" to "https://open.spotify.com",
        "referer" to "https://open.spotify.com/",
        "user-agent" to DESKTOP_USER_AGENT,
    )

    suspend fun run(query: WebPlayerQuery, variables: JsonObject): JsonObject {
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", query.hash)
            }
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = if (query.post) {
                val body = buildJsonObject {
                    put("variables", variables)
                    put("operationName", query.name)
                    put("extensions", extensions)
                }
                AccountHttp.post(query.url, headers, body.toString(), SERVICE)
            } else {
                val url = query.url + "?operationName=" + encode(query.name) +
                    "&variables=" + encode(variables.toString()) + "&extensions=" + encode(extensions.toString())
                AccountHttp.get(url, headers, SERVICE)
            }
            when {
                response.status in 200..299 -> {
                    // a GraphQL error comes back as 200 with "errors" and no data
                    AccountHttp.parse(response.body)?.takeIf { it["data"].obj() != null }?.let { return it }
                    Timber.w("Spotify %s answered without data: %s", query.name, response.body.take(300))
                    throw AccountImportException("Spotify sent something we couldn't read.")
                }
                response.status == 429 || response.status == 502 || response.status == 503 -> {
                    val waitSeconds = (response.retryAfterSeconds ?: (2L shl attempt)).coerceIn(1, 30)
                    delay(waitSeconds * 1000)
                }
                response.status == 401 -> throw AccountImportException("Spotify signed you out. Sign in again.", signedOut = true)
                else -> {
                    Timber.w("Spotify %s answered %d: %s", query.name, response.status, response.body.take(300))
                    throw AccountImportException("Spotify answered with an error (${response.status}).")
                }
            }
        }
        throw AccountImportException("Spotify is busy right now. Try again in a minute.")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private class SpotifyWebPlayerLibrary(
    private val credentials: CapturedCredentials,
    private val client: WebPlayerClient,
    /** For a list whose query the sign-in page never saw. */
    private val webApi: SpotifyLibrary,
) : AccountLibrary {
    override val accountName: String? = null

    override suspend fun playlists(): List<RemotePlaylist> {
        val query = SpotifyWebPlayer.libraryQuery(credentials.queries()) ?: return webApi.playlists()
        val playlists = LinkedHashMap<String, RemotePlaylist>()
        val liked = SpotifyWebPlayer.LikedCount()
        var previous: SpotifyWebPlayer.PageList? = null
        var offset = 0
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = client.run(query, SpotifyWebPlayer.libraryVariables(query, offset))
            val before = playlists.size
            SpotifyWebPlayer.collectPlaylists(page, null, playlists, liked)
            // artists, albums and folders are items too, so a page can add no playlists and not be the last
            val list = SpotifyWebPlayer.pageList(page, "data", "me", query.name)
            if (SpotifyWebPlayer.isLastPage(list, previous, offset, found = playlists.size - before)) break
            previous = list
            offset += list?.size ?: SpotifyWebPlayer.pageSize(query)
        }
        return listOf(RemotePlaylist(LIKED_ID, "Liked Songs", liked.value, isLiked = true)) + playlists.values
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val queries = credentials.queries()
        val query = (if (playlist.isLiked) SpotifyWebPlayer.likedQuery(queries) else SpotifyWebPlayer.playlistQuery(queries))
            ?: return webApi.tracks(playlist)
        val uri = if (playlist.isLiked) null else "spotify:playlist:${playlist.id}"
        val listPath = if (playlist.isLiked) arrayOf("data", "me", "library", "tracks") else arrayOf("data", "playlistV2", "content")
        val tracks = mutableListOf<ImportedTrack>()
        var previous: SpotifyWebPlayer.PageList? = null
        var previousFound: List<ImportedTrack>? = null
        var offset = 0
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = client.run(query, SpotifyWebPlayer.trackVariables(query, uri, offset))
            val found = mutableListOf<ImportedTrack>()
            SpotifyWebPlayer.collectTracks(page["data"] ?: page, null, found)
            val list = SpotifyWebPlayer.pageList(page, *listPath)
            // without a known shape, a page the same as the last one means the offset was ignored
            if (list == null && found.isNotEmpty() && found == previousFound) break
            if (list == null || list.items != previous?.items) tracks += found
            // podcast episodes are items too, so a page can add no tracks and not be the last
            if (SpotifyWebPlayer.isLastPage(list, previous, offset, found.size)) break
            previous = list
            previousFound = found
            offset += list?.size ?: SpotifyWebPlayer.pageSize(query)
        }
        return tracks
    }

    private companion object {
        const val MAX_PAGES = 400
    }
}
