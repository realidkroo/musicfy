// AccountModels.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack
import com.example.musicfy.importer.obj
import com.example.musicfy.importer.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

enum class AccountService(
    val route: String,
    val label: String,
    /** Where sign-in starts; null for YouTube Music, which uses the app's own account. */
    val loginUrl: String?,
    /** Sites whose cookies and storage belong to this service, cleared by "Use a different account". */
    val webOrigins: List<String>,
    /** The web player that sends the session after sign-in; only it is watched, never the login pages. */
    val playerOrigins: Set<String> = emptySet(),
    /** Tidal's web player turns phones away, so it's opened as a desktop site. */
    val desktopSite: Boolean = false,
) {
    SPOTIFY(
        route = "spotify",
        label = "Spotify",
        loginUrl = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F",
        webOrigins = listOf("https://accounts.spotify.com", "https://open.spotify.com", "https://www.spotify.com"),
        playerOrigins = setOf("https://open.spotify.com"),
    ),
    APPLE_MUSIC(
        route = "apple_music",
        label = "Apple Music",
        loginUrl = "https://music.apple.com/us/library/playlists",
        webOrigins = listOf("https://music.apple.com", "https://idmsa.apple.com", "https://authorize.music.apple.com", "https://www.apple.com"),
        playerOrigins = setOf("https://music.apple.com"),
    ),
    TIDAL(
        route = "tidal",
        label = "Tidal",
        loginUrl = "https://listen.tidal.com/login",
        webOrigins = listOf("https://listen.tidal.com", "https://login.tidal.com", "https://tidal.com"),
        playerOrigins = setOf("https://listen.tidal.com"),
        desktopSite = true,
    ),
    YOUTUBE_MUSIC(
        route = "youtube_music",
        label = "YouTube Music",
        loginUrl = null,
        webOrigins = emptyList(),
    );

    companion object {
        fun fromRoute(route: String?): AccountService? = entries.firstOrNull { it.route == route }
    }
}

data class RemotePlaylist(
    val id: String,
    val name: String,
    val trackCount: Int?,
    val subtitle: String? = null,
    /** Goes into Liked songs instead of becoming a playlist. */
    val isLiked: Boolean = false,
    /**
     * The account's whole song library rather than a playlist: goes into the Library's Songs (and
     * Artists, Albums) as songs, not into a playlist named after the service.
     */
    val isLibrary: Boolean = false,
    val coverUrl: String? = null,
)

/** A signed-in account's library, read on demand. */
interface AccountLibrary {
    val accountName: String?
    /** The account's own picture, to start the Musicfy profile from. */
    val accountPhotoUrl: String? get() = null
    suspend fun playlists(): List<RemotePlaylist>
    suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack>
}

class AccountImportException(message: String, val signedOut: Boolean = false) : Exception(message)

/** The service asked us to slow down; try connecting again after [seconds]. */
class AccountRetryLater(val seconds: Long) : Exception()

/**
 * Desktop Chrome: Tidal's web player refuses phones, and Spotify's phone site has no library. Also
 * sent with the requests that read Spotify's library the way its web player does.
 */
internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

/**
 * The pseudo header under which the sign-in page reports a query Spotify's web player made, as JSON
 * {name, hash, variables, url, post}, so the app can send the same query for the rest of a list.
 */
const val WEB_PLAYER_QUERY_HEADER = "musicfy-web-player-query"

/** One query the web player was seen making, ready to be sent again with other variables. */
class WebPlayerQuery(
    val name: String,
    val hash: String,
    val variables: JsonObject,
    val url: String,
    val post: Boolean,
    /**
     * Read out of the web player's script rather than seen being sent, so [variables] are our own
     * defaults. A query the page actually sends replaces it.
     */
    val guessed: Boolean = false,
    val seenAtMs: Long = System.currentTimeMillis(),
)

/**
 * Session details seen while the user signs in on the service's own web page: the bearer tokens
 * its web player sends, and Apple's music-user token. Each one is tried once against the API;
 * tokens from before sign-in (anonymous) simply fail that check.
 */
class CapturedCredentials {
    private val bearers = LinkedHashMap<String, String>()
    /** When each token was first seen. */
    private val bearerSeenAt = HashMap<String, Long>()
    private val tried = HashSet<String>()
    private val sessionHeaders = HashMap<String, String>()
    private val queries = LinkedHashMap<String, WebPlayerQuery>()

    @Volatile var appleUserToken: String? = null
        private set

    fun addHeader(name: String, value: String, url: String) {
        val trimmed = value.trim()
        when (name.trim().lowercase()) {
            "authorization" -> {
                if (!trimmed.startsWith("Bearer ", ignoreCase = true)) return
                val token = trimmed.substring(7).trim()
                if (token.length < 20) return
                synchronized(this) {
                    bearers.remove(token)
                    bearers[token] = hostOf(url)
                    bearerSeenAt.getOrPut(token) { System.currentTimeMillis() }
                    while (bearers.size > MAX_TOKENS) {
                        val oldest = bearers.keys.first()
                        bearers.remove(oldest)
                        bearerSeenAt.remove(oldest)
                    }
                }
            }
            "media-user-token" -> if (trimmed.length >= 20) appleUserToken = trimmed
            "client-token", "app-platform", "spotify-app-version" ->
                if (trimmed.isNotEmpty()) synchronized(this) { sessionHeaders[name.trim().lowercase()] = trimmed }
            WEB_PLAYER_QUERY_HEADER -> parseQuery(trimmed)?.let { query ->
                synchronized(this) {
                    val known = queries[query.name]
                    // the first one sent wins; one sent beats one read out of the script
                    if (known == null || (known.guessed && !query.guessed)) queries[query.name] = query
                }
            }
        }
    }

    /** The web player's own client headers (client-token, app-platform, spotify-app-version). */
    fun sessionHeaders(): Map<String, String> = synchronized(this) { HashMap(sessionHeaders) }

    /** The first query seen under each name, in the order they were seen. */
    fun queries(): List<WebPlayerQuery> = synchronized(this) { queries.values.toList() }

    private fun parseQuery(raw: String): WebPlayerQuery? {
        val json = AccountHttp.parse(raw) ?: return null
        return WebPlayerQuery(
            name = json.string("name") ?: return null,
            hash = json.string("hash") ?: return null,
            variables = json["variables"].obj() ?: JsonObject(emptyMap()),
            url = json.string("url")?.takeIf { it.startsWith("https://") } ?: return null,
            post = (json["post"] as? JsonPrimitive)?.booleanOrNull ?: false,
            guessed = (json["guessed"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    /** Newest first; a token whose request had no readable host counts for every service. */
    fun untriedBearers(hostSuffix: String): List<String> = synchronized(this) {
        bearers.entries.filter { (token, host) -> token !in tried && (host.isEmpty() || host.endsWith(hostSuffix)) }
            .map { it.key }
            .asReversed()
    }

    fun newestBearer(hostSuffix: String): String? = synchronized(this) {
        bearers.entries.lastOrNull { (_, host) -> host.isEmpty() || host.endsWith(hostSuffix) }?.key
    }

    /** Every token seen for [hostSuffix], tried or not, newest first. */
    fun bearers(hostSuffix: String): List<String> = synchronized(this) {
        bearers.entries.filter { (_, host) -> host.isEmpty() || host.endsWith(hostSuffix) }.map { it.key }.asReversed()
    }

    /** How long ago [token] was first seen; 0 for one never seen. */
    fun bearerAgeMs(token: String): Long = synchronized(this) {
        bearerSeenAt[token]?.let { System.currentTimeMillis() - it } ?: 0
    }

    fun isTried(key: String): Boolean = synchronized(this) { key in tried }

    fun markTried(key: String) {
        synchronized(this) { tried += key }
    }

    fun clear() {
        synchronized(this) {
            bearers.clear()
            bearerSeenAt.clear()
            tried.clear()
            sessionHeaders.clear()
            queries.clear()
        }
        appleUserToken = null
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.lowercase().orEmpty()

    private companion object {
        const val MAX_TOKENS = 24
    }
}

/** Turns captured credentials into a library once they belong to a signed-in account. */
interface AccountConnector {
    /** Null while nothing captured works yet. */
    suspend fun connect(credentials: CapturedCredentials): AccountLibrary?
}
