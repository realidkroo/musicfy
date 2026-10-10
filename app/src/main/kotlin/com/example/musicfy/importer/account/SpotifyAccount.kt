// SpotifyAccount.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.array
import com.example.musicfy.importer.dateMs
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
private const val LOG_TAG = "SpotifyImport"

/**
 * Reads the library the way Spotify's web player does (see [SpotifyWebPlayer]). The Web API is the
 * fallback for when the web player's queries never turn up: it rate-limits the web player's token
 * hard enough to fail a whole import, so it waits [WEB_API_AFTER_MS] for the web player first. The
 * logged-out token fails both and is skipped.
 */
object SpotifyConnector : AccountConnector {
    private const val WEB_API_AFTER_MS = 30_000L

    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        SpotifyWebPlayer.connect(credentials)?.let { return it }
        val newest = credentials.newestBearer("spotify.com") ?: return null
        val waited = credentials.bearerAgeMs(newest)
        if (waited < WEB_API_AFTER_MS) throw AccountRetryLater(((WEB_API_AFTER_MS - waited) / 1000).coerceIn(2, 10))

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
                    Timber.tag(LOG_TAG).i("connected through the Web API")
                    return SpotifyLibrary(
                        token = token,
                        accountName = me.string("display_name") ?: me.string("id"),
                        accountPhotoUrl = me["images"].array().firstOrNull()?.obj()?.string("url"),
                    )
                }
                429 -> {
                    Timber.tag(LOG_TAG).d("Web API is rate-limiting this token")
                    throw AccountRetryLater(response.retryAfterSeconds?.coerceIn(2, 30) ?: 5)
                }
                else -> {
                    Timber.tag(LOG_TAG).d("Web API refused a token (%d)", response.status)
                    credentials.markTried(token)
                }
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
                    coverUrl = largestImage(playlist["images"].array()),
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
                    addedAtMs = dateMs(entry.string("added_at")),
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

/** The biggest of a list of `{url, width, height}` images. */
private fun largestImage(sources: List<JsonElement>): String? =
    sources.mapNotNull { it.obj() }
        .maxByOrNull { it.long("width") ?: 0 }
        ?.string("url")

/**
 * Spotify's web player asks its own GraphQL endpoint ("pathfinder") for the library, by query name
 * and hash. The sign-in page reports those queries two ways (see the capture hook and
 * `SpotifyFindQueriesJs`): the ones it is seen sending, with their real variables, and the ones read
 * out of the web player's script, which get [defaultVariables]. That second way matters: Liked
 * Songs comes out of the web player's own cache, so its query is never sent on its own.
 */
private object SpotifyWebPlayer {
    private val LIBRARY_QUERY = Regex("""^libraryV(\d+)$""")

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
        // newest first, skipping any that turned out to have no account behind them
        for (token in credentials.bearers("spotify.com")) {
            val key = "web-player|$token"
            if (credentials.isTried(key)) continue
            val client = WebPlayerClient(token, credentials.sessionHeaders())
            val firstPage = try {
                client.run(library, libraryVariables(library, offset = 0))
            } catch (e: AccountImportException) {
                Timber.tag(LOG_TAG).w("library query failed: %s", e.message)
                if (!e.signedOut) return null
                credentials.markTried(key)
                continue
            }
            // the logged-out player has no "me"
            if (firstPage.path("data", "me").obj() == null) {
                Timber.tag(LOG_TAG).d("a token with no account behind it; trying the next")
                credentials.markTried(key)
                continue
            }

            val playlists = LinkedHashMap<String, RemotePlaylist>()
            collectPlaylists(firstPage, null, playlists, LikedCount())
            val liked = likedQuery(queries)
            val playlist = playlistQuery(queries)
            val stillComing = liked == null || (playlists.isNotEmpty() && playlist == null)
            if (stillComing && System.currentTimeMillis() - library.seenAtMs < WAIT_FOR_QUERIES_MS) {
                Timber.tag(LOG_TAG).d("signed in; waiting for the %s query", if (liked == null) "Liked Songs" else "playlist")
                throw AccountRetryLater(2)
            }
            Timber.tag(LOG_TAG).i(
                "connected through the web player: %s, liked by %s, playlists by %s",
                library.name, liked?.name ?: "the Web API", playlist?.name ?: "the Web API",
            )
            return SpotifyWebPlayerLibrary(credentials, client, webApi = SpotifyLibrary(token, null, null))
        }
        Timber.tag(LOG_TAG).d("no signed-in token yet")
        return null
    }

    /**
     * What the web player itself sends, for a query read out of its script. Pages are set per call
     * (see [libraryVariables] and [trackVariables]); a query that needs a variable we don't send
     * answers with an error naming it.
     */
    private fun defaultVariables(name: String): JsonObject = when {
        LIBRARY_QUERY.matches(name) -> buildJsonObject {
            put("order", JsonNull)
            put("textFilter", "")
            put(
                "features",
                JsonArray(listOf("LIKED_SONGS", "YOUR_EPISODES_V2", "PRERELEASES", "PRERELEASES_V2", "CLIPS", "EVENTS").map(::JsonPrimitive)),
            )
            put("limit", 50)
            put("offset", 0)
            put("flatten", true)
            put("expandedFolders", JsonArray(emptyList()))
            put("folderUri", JsonNull)
            put("includeFoldersWhenFlattening", true)
        }
        name == "fetchPlaylist" -> buildJsonObject {
            put("offset", 0)
            put("limit", 100)
            put("enableWatchFeedEntrypoint", false)
        }
        name == "fetchPlaylistContents" -> buildJsonObject {
            put("offset", 0)
            put("limit", 100)
        }
        else -> buildJsonObject {
            put("offset", 0)
            put("limit", 50)
        }
    }

    private fun variablesOf(query: WebPlayerQuery): JsonObject =
        if (query.guessed) defaultVariables(query.name) else query.variables

    fun libraryVariables(query: WebPlayerQuery, offset: Int): JsonObject = variablesOf(query).withChanges(
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
        val paging = variablesOf(query).withChanges(
            "offset" to JsonPrimitive(offset),
            "limit" to JsonPrimitive(pageSize(query)),
            onlyIfPresent = emptySet(),
        )
        return if (uri == null) paging else paging.withChanges("uri" to JsonPrimitive(uri), onlyIfPresent = emptySet())
    }

    fun pageSize(query: WebPlayerQuery): Int = variablesOf(query).long("limit")?.toInt()?.coerceIn(10, 100) ?: 50

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
                if (uri == "spotify:collection:tracks") element.long("count")?.let { liked.value = it.toInt() }
                if (uri != null && uri.startsWith("spotify:playlist:") && name != null) {
                    val id = uri.removePrefix("spotify:playlist:")
                    out.getOrPut(id) {
                        RemotePlaylist(
                            id = id,
                            name = name,
                            trackCount = (element.path("content").obj() ?: element.path("tracks").obj())?.long("totalCount")?.toInt(),
                            subtitle = element.path("ownerV2", "data").obj()?.string("name")?.let { "by $it" },
                            coverUrl = largestImage(element.path("images", "items").array().firstOrNull()?.path("sources").array()),
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
     * The tracks of a page's list, each with when it was added: an item is
     * `{addedAt: {isoString}, track | itemV2: {data: Track}}`.
     */
    fun tracksOf(items: JsonArray): List<ImportedTrack> = items.flatMap { item ->
        val found = mutableListOf<ImportedTrack>()
        collectTracks(item, null, found)
        val addedAt = dateMs(item.path("addedAt").obj()?.string("isoString"))
        found.map { it.copy(addedAtMs = addedAt) }
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
                    Timber.tag(LOG_TAG).w("%s answered without data: %s", query.name, response.body.take(300))
                    throw AccountImportException("Spotify sent something we couldn't read.")
                }
                response.status == 429 || response.status == 502 || response.status == 503 -> {
                    val waitSeconds = (response.retryAfterSeconds ?: (2L shl attempt)).coerceIn(1, 30)
                    Timber.tag(LOG_TAG).d("%s answered %d, waiting %ds", query.name, response.status, waitSeconds)
                    delay(waitSeconds * 1000)
                }
                response.status == 401 -> throw AccountImportException("Spotify signed you out. Sign in again.", signedOut = true)
                else -> {
                    Timber.tag(LOG_TAG).w("%s answered %d: %s", query.name, response.status, response.body.take(300))
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
        Timber.tag(LOG_TAG).d("library: %d playlists, %s liked songs", playlists.size, liked.value)
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
            val list = SpotifyWebPlayer.pageList(page, *listPath)
            val found = if (list != null) {
                SpotifyWebPlayer.tracksOf(list.items)
            } else {
                mutableListOf<ImportedTrack>().also { SpotifyWebPlayer.collectTracks(page["data"] ?: page, null, it) }
            }
            // without a known shape, a page the same as the last one means the offset was ignored
            if (list == null && found.isNotEmpty() && found == previousFound) break
            if (list == null || list.items != previous?.items) tracks += found
            // podcast episodes are items too, so a page can add no tracks and not be the last
            if (SpotifyWebPlayer.isLastPage(list, previous, offset, found.size)) break
            previous = list
            previousFound = found
            offset += list?.size ?: SpotifyWebPlayer.pageSize(query)
        }
        Timber.tag(LOG_TAG).d("\"%s\": %d tracks by %s", playlist.name, tracks.size, query.name)
        return tracks
    }

    private companion object {
        const val MAX_PAGES = 400
    }
}
