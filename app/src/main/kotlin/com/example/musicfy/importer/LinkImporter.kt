// LinkImporter.kt

package com.example.musicfy.importer

import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class LinkSource(val label: String, val example: String) {
    SPOTIFY("Spotify", "https://open.spotify.com/playlist/…"),
    APPLE_MUSIC("Apple Music", "https://music.apple.com/us/playlist/…"),
    DEEZER("Deezer", "https://www.deezer.com/playlist/…"),
    YOUTUBE("YouTube Music", "https://music.youtube.com/playlist?list=…"),
}

class LinkImportException(message: String) : Exception(message)

/**
 * Reads a public playlist or album from a share link, without logging in. Each source reads the
 * same data its own web page shows a logged-out visitor, so private playlists aren't reachable —
 * those go through TuneMyMusic.
 */
object LinkImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val SPOTIFY_REGEX = Regex("(?:open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{22})")
    private val APPLE_REGEX = Regex("music\\.apple\\.com/[a-z]{2}/(playlist|album)/(?:[^/?#]+/)?(pl\\.[A-Za-z0-9-]+|\\d+)")
    private val DEEZER_REGEX = Regex("deezer\\.com/(?:[a-z]{2}/)?(playlist|album)/(\\d+)")
    private val YT_LIST_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")
    private val YT_BROWSE_REGEX = Regex("music\\.youtube\\.com/(?:browse|playlist)/((?:VL|MPREb_)?[A-Za-z0-9_-]+)")
    private val SHORT_LINK_HOSTS = listOf("spotify.link", "spotify.app.link", "deezer.page.link", "link.deezer.com")

    /** Works out which service a pasted link belongs to, or null if it isn't one we read. */
    fun detect(link: String): LinkSource? {
        val text = link.trim()
        return when {
            text.contains("spotify") -> LinkSource.SPOTIFY
            text.contains("music.apple.com") -> LinkSource.APPLE_MUSIC
            text.contains("deezer") -> LinkSource.DEEZER
            text.contains("youtube.com") || text.contains("youtu.be") -> LinkSource.YOUTUBE
            else -> null
        }
    }

    suspend fun fetch(link: String): ParsedImport {
        val text = extractUrl(link.trim()) ?: throw LinkImportException("That doesn't look like a link.")
        val resolved = if (SHORT_LINK_HOSTS.any { text.contains(it) }) resolveShortLink(text) else text
        return when (detect(resolved) ?: detect(text)) {
            LinkSource.SPOTIFY -> fetchSpotify(resolved)
            LinkSource.APPLE_MUSIC -> fetchApple(resolved)
            LinkSource.DEEZER -> fetchDeezer(resolved)
            LinkSource.YOUTUBE -> fetchYouTube(resolved)
            null -> throw LinkImportException("Paste a Spotify, Apple Music, Deezer or YouTube Music link.")
        }
    }

    /** Share sheets often paste "Check out this playlist! https://…" — take just the URL. */
    private fun extractUrl(text: String): String? {
        if (text.startsWith("spotify:")) return text
        return Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
    }

    /** Short links redirect to the real page; some go through an HTML page that holds the real URL. */
    private suspend fun resolveShortLink(url: String): String {
        val response = get(url)
        val finalUrl = response.call.request.url.toString()
        if (listOf(SPOTIFY_REGEX, DEEZER_REGEX, YT_LIST_REGEX).any { it.containsMatchIn(finalUrl) }) return finalUrl
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        SPOTIFY_REGEX.find(body)?.let { return "https://open.spotify.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        DEEZER_REGEX.find(body)?.let { return "https://www.deezer.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        return finalUrl
    }

    private suspend fun get(url: String): HttpResponse = try {
        client.get(url) {
            header("User-Agent", USER_AGENT)
            header("Accept-Language", "en-US,en;q=0.9")
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw LinkImportException("Couldn't reach the server — check your connection.")
    }

    private suspend fun getOk(url: String, what: String): String {
        val response = get(url)
        when (response.status.value) {
            in 200..299 -> return response.bodyAsText()
            404 -> throw LinkImportException("$what wasn't found. Is it public?")
            429 -> throw LinkImportException("$what is rate-limiting us. Try again in a minute.")
            else -> throw LinkImportException("$what answered with an error (${response.status.value}).")
        }
    }

    // ---- Spotify: the embed player page carries the track list for logged-out visitors ----

    private suspend fun fetchSpotify(url: String): ParsedImport {
        val match = SPOTIFY_REGEX.find(url)
            ?: throw LinkImportException("Paste a Spotify playlist or album link.")
        val (type, id) = match.destructured
        return parseSpotifyEmbed(getOk("https://open.spotify.com/embed/$type/$id", "Spotify"), type)
    }

    internal fun parseSpotifyEmbed(html: String, type: String): ParsedImport {
        val data = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Spotify changed its page — use TuneMyMusic for now.")
        val entity = json.parseToJsonElement(data)
            .path("props", "pageProps", "state", "data", "entity") as? JsonObject
            ?: throw LinkImportException("That Spotify $type is private or doesn't exist.")

        val name = entity.string("name") ?: entity.string("title") ?: "Spotify $type"
        val albumName = if (type == "album") name else null
        val tracks = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.string("title") ?: return@mapNotNull null
            ImportedTrack(
                title = title,
                artist = obj.string("subtitle").orEmpty().replace(' ', ' '),
                album = albumName,
                durationMs = obj.long("duration"),
            )
        }
        if (tracks.isEmpty()) throw LinkImportException("That Spotify $type has no songs we can read.")

        val warnings = buildList {
            if (type == "playlist" && tracks.size >= SPOTIFY_EMBED_LIMIT) {
                add("Spotify only shares the first $SPOTIFY_EMBED_LIMIT songs of a playlist through links. For the whole playlist, use the TuneMyMusic CSV option.")
            }
        }
        return ParsedImport(likedSongs = emptyList(), playlists = mapOf(name to tracks), warnings = warnings)
    }

    private const val SPOTIFY_EMBED_LIMIT = 100

    // ---- Apple Music: the web page embeds its own data as serialized-server-data ----

    private suspend fun fetchApple(url: String): ParsedImport {
        val match = APPLE_REGEX.find(url)
            ?: throw LinkImportException("Paste an Apple Music playlist or album link.")
        val type = match.groupValues[1]
        return parseApplePage(getOk(url.substringBefore('#'), "Apple Music"), type)
    }

    internal fun parseApplePage(html: String, type: String): ParsedImport {
        val data = Regex("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Apple Music changed its page — use TuneMyMusic for now.")
        val root = json.parseToJsonElement(data)
        val page = (root.path("data") as? JsonArray)?.firstOrNull()?.path("data")
            ?: (root as? JsonArray)?.firstOrNull()?.path("data")
            ?: throw LinkImportException("That Apple Music $type is private or doesn't exist.")
        val sections = (page.path("sections") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        val header = sections.firstOrNull { it.string("itemKind") == "containerDetailHeaderLockup" }
            ?.let { (it["items"] as? JsonArray)?.firstOrNull() as? JsonObject }
        val name = header?.string("title") ?: "Apple Music $type"
        val albumArtist = header?.let { (it["subtitleLinks"] as? JsonArray)?.firstOrNull()?.path("title") as? JsonPrimitive }?.contentOrNull
        val declaredCount = header?.long("trackCount")?.toInt()

        val tracks = sections.filter { it.string("itemKind") == "trackLockup" }
            .flatMap { (it["items"] as? JsonArray).orEmpty() }
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val title = obj.string("title") ?: return@mapNotNull null
                val artists = (obj["subtitleLinks"] as? JsonArray).orEmpty()
                    .mapNotNull { (it.path("title") as? JsonPrimitive)?.contentOrNull }
                ImportedTrack(
                    title = title,
                    artist = obj.string("artistName") ?: artists.joinToString(", ").ifEmpty { albumArtist.orEmpty() },
                    album = if (type == "album") name
                    else (obj["tertiaryLinks"] as? JsonArray)?.firstOrNull()?.let { (it.path("title") as? JsonPrimitive)?.contentOrNull },
                    durationMs = obj.long("duration"),
                )
            }
        if (tracks.isEmpty()) throw LinkImportException("That Apple Music $type has no songs we can read.")

        val warnings = buildList {
            if (declaredCount != null && declaredCount > tracks.size) {
                add("Apple Music's page only listed ${tracks.size} of $declaredCount songs. For the whole playlist, use the TuneMyMusic CSV option.")
            }
        }
        return ParsedImport(likedSongs = emptyList(), playlists = mapOf(name to tracks), warnings = warnings)
    }

    // ---- Deezer: public API, no key needed ----

    private suspend fun fetchDeezer(url: String): ParsedImport {
        val match = DEEZER_REGEX.find(url)
            ?: throw LinkImportException("Paste a Deezer playlist or album link.")
        val (type, id) = match.destructured
        val info = deezerJson("https://api.deezer.com/$type/$id")
        val name = info.string("title") ?: "Deezer $type"
        val albumName = if (type == "album") name else null

        val tracks = mutableListOf<ImportedTrack>()
        var next: String? = "https://api.deezer.com/$type/$id/tracks?limit=100"
        var pages = 0
        while (next != null && pages < 100) {
            val page = deezerJson(next)
            (page["data"] as? JsonArray).orEmpty().forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val title = obj.string("title") ?: return@forEach
                tracks += ImportedTrack(
                    title = title,
                    artist = obj["artist"]?.let { (it.path("name") as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                    album = albumName ?: obj["album"]?.let { (it.path("title") as? JsonPrimitive)?.contentOrNull },
                    durationMs = obj.long("duration")?.times(1000),
                )
            }
            next = page.string("next")?.takeIf { it.startsWith("https://api.deezer.com/") }
            pages++
        }
        if (tracks.isEmpty()) throw LinkImportException("That Deezer $type has no songs we can read.")
        return ParsedImport(likedSongs = emptyList(), playlists = mapOf(name to tracks))
    }

    private suspend fun deezerJson(url: String): JsonObject {
        val obj = json.parseToJsonElement(getOk(url, "Deezer")) as? JsonObject
            ?: throw LinkImportException("Deezer sent something we couldn't read.")
        obj["error"]?.let { error ->
            val message = (error.path("message") as? JsonPrimitive)?.contentOrNull
            throw LinkImportException(if (message?.contains("no data", true) == true) "That Deezer playlist is private or doesn't exist." else "Deezer: ${message ?: "unknown error"}")
        }
        return obj
    }

    // ---- YouTube / YouTube Music: our own client, ids are exact so nothing needs matching ----

    private suspend fun fetchYouTube(url: String): ParsedImport {
        val browseId = YT_BROWSE_REGEX.find(url)?.groupValues?.get(1)
        val listId = YT_LIST_REGEX.find(url)?.groupValues?.get(1)
        return when {
            browseId != null && browseId.startsWith("MPREb_") -> fetchYouTubeAlbum(browseId)
            listId != null -> fetchYouTubePlaylist(listId)
            browseId != null -> fetchYouTubePlaylist(browseId.removePrefix("VL"))
            else -> throw LinkImportException("Paste a YouTube playlist or album link.")
        }
    }

    suspend fun fetchYouTubePlaylist(playlistId: String): ParsedImport {
        if (playlistId.startsWith("RD")) throw LinkImportException("That's a YouTube mix, which changes every time — save it as a playlist first.")
        val page = YouTube.playlist(playlistId).completed().getOrElse {
            throw LinkImportException("Couldn't open that YouTube playlist. Is it public or unlisted?")
        }
        val tracks = page.songs.map { it.toImportedTrack() }
        if (tracks.isEmpty()) throw LinkImportException("That YouTube playlist is empty.")
        return ParsedImport(likedSongs = emptyList(), playlists = mapOf(page.playlist.title to tracks))
    }

    private suspend fun fetchYouTubeAlbum(browseId: String): ParsedImport {
        val page = YouTube.album(browseId).getOrElse {
            throw LinkImportException("Couldn't open that YouTube Music album.")
        }
        val tracks = page.songs.map { it.toImportedTrack() }
        if (tracks.isEmpty()) throw LinkImportException("That album has no songs.")
        return ParsedImport(likedSongs = emptyList(), playlists = mapOf(page.album.title to tracks))
    }
}
