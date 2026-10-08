// AccountModels.kt

package com.example.musicfy.importer.account

import com.example.musicfy.importer.ImportedTrack

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
 * Session details seen while the user signs in on the service's own web page: the bearer tokens
 * its web player sends, and Apple's music-user token. Each one is tried once against the API;
 * tokens from before sign-in (anonymous) simply fail that check.
 */
class CapturedCredentials {
    private val bearers = LinkedHashMap<String, String>()
    private val tried = HashSet<String>()

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
                    while (bearers.size > MAX_TOKENS) bearers.remove(bearers.keys.first())
                }
            }
            "media-user-token" -> if (trimmed.length >= 20) appleUserToken = trimmed
        }
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

    fun isTried(key: String): Boolean = synchronized(this) { key in tried }

    fun markTried(key: String) {
        synchronized(this) { tried += key }
    }

    fun clear() {
        synchronized(this) {
            bearers.clear()
            tried.clear()
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
