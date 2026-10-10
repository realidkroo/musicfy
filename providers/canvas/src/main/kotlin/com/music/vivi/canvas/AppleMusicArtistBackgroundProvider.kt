// AppleMusicArtistBackgroundProvider.kt

package com.example.musicfy.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * what Apple Music's editors made for an artist: the looping motion banner, its still frame, the
 * artist's name drawn as a logo, the newest release and the bio. image urls are mzstatic templates
 * with a {w}x{h} slot; size them with [appleArtworkUrl].
 */
data class AppleArtistEditorial(
    val appleId: String,
    val name: String,
    /** 16:9 HLS loop, the one music.apple.com plays behind the header */
    val motionVideoUrl: String?,
    /** 1:1 HLS loop, for when the wide one is missing */
    val motionSquareUrl: String?,
    /** the still frame of the square loop (else the wide one), to show at once and while it loads */
    val previewFrameUrl: String?,
    /** a still editorial banner, when there is no motion */
    val bannerUrl: String?,
    /** the artist's name as a logo: white on transparent, trimmed */
    val logoUrl: String?,
    /** the logo's width / height */
    val logoAspect: Float?,
    val bio: String?,
    val latestRelease: AppleRelease?,
)

data class AppleRelease(
    val name: String,
    /** yyyy-MM-dd */
    val releaseDate: String?,
    val artworkUrl: String?,
    val isSingle: Boolean,
    val trackCount: Int?,
)

/** a sized url from an mzstatic {w}x{h} template; [format] "png"/"webp" keeps transparency */
fun appleArtworkUrl(template: String, width: Int, height: Int, format: String = "jpg"): String =
    template
        .replace("{w}", width.toString())
        .replace("{h}", height.toString())
        .replace("{f}", format)
        .replace(Regex("""(\d+x\d+(?:bb|sr|cc)(?:-\d+)?)\.(jpg|png|webp)$"""), "$1.$format")

object AppleMusicArtistBackgroundProvider {

    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val editorial: AppleArtistEditorial?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24
    // a miss is kept for less time: a network blip shouldn't hide an artist's banner all day
    private const val MISS_TTL_MS = 1000L * 60 * 10

    /** the motion banner's video url, as before; [getArtistEditorial] has the rest */
    suspend fun getByArtistName(
        artistName: String,
        storefront: String = "us",
    ): String? = getArtistEditorial(artistName, storefront)?.let { it.motionVideoUrl ?: it.motionSquareUrl }

    /** already looked up this run, without touching the network */
    fun cached(artistName: String, storefront: String = "us"): AppleArtistEditorial? =
        cache[cacheKey("artist", artistName, storefront)]
            ?.takeIf { it.expiresAtMs > System.currentTimeMillis() }
            ?.editorial

    suspend fun getArtistEditorial(
        artistName: String,
        storefront: String = "us",
    ): AppleArtistEditorial? {
        if (artistName.isBlank()) return null
        val key = cacheKey("artist", artistName, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.editorial }

        val result = searchAndFetch(artistName, storefront)
        cache[key] = CacheEntry(
            result,
            System.currentTimeMillis() + if (result != null) CACHE_TTL_MS else MISS_TTL_MS,
        )
        return result
    }

    private suspend fun authorizedGet(url: String, params: Map<String, String>): JsonObject? {
        repeat(2) { attempt ->
            val token = AppleMusicToken.get(client)
            val response = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                params.forEach { (k, v) -> parameter(k, v) }
            }
            if (response.status == HttpStatusCode.Unauthorized && attempt == 0) {
                AppleMusicToken.invalidate()
                return@repeat
            }
            if (response.status != HttpStatusCode.OK) return null
            return response.body<JsonObject>()
        }
        return null
    }

    private suspend fun searchAndFetch(
        artistName: String,
        storefront: String,
    ): AppleArtistEditorial? = runCatching {
        val root = authorizedGet(
            "$AMP_BASE_URL/v1/catalog/$storefront/search",
            mapOf("term" to artistName, "types" to "artists", "limit" to "5"),
        ) ?: return@runCatching null
        val results = root["results"]?.jsonObject?.get("artists")?.jsonObject?.get("data")?.jsonArray
            ?: return@runCatching null

        val wanted = normalize(artistName)
        val scored = results.mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val got = normalize(name)
            val score = when {
                got == wanted -> 10
                got.contains(wanted) || wanted.contains(got) -> 5
                else -> return@mapNotNull null
            }
            score to obj
        }.sortedByDescending { it.first }

        // an exact name first; a looser match only if nothing exact had any artwork
        for ((score, obj) in scored) {
            if (score < 5) continue
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val fetched = fetchArtist(id, storefront)
            if (fetched != null && (fetched.motionVideoUrl != null || fetched.logoUrl != null ||
                    fetched.previewFrameUrl != null || fetched.bannerUrl != null || score == 10)
            ) {
                return@runCatching fetched
            }
        }
        null
    }.getOrNull()

    private suspend fun fetchArtist(
        artistId: String,
        storefront: String,
    ): AppleArtistEditorial? = runCatching {
        val root = authorizedGet(
            "$AMP_BASE_URL/v1/catalog/$storefront/artists/$artistId",
            mapOf(
                "extend" to "editorialVideo,editorialArtwork,artistBio",
                "views" to "latest-release",
            ),
        ) ?: return@runCatching null
        val artist = root["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
        val attributes = artist["attributes"]?.jsonObject ?: return@runCatching null

        val video = attributes["editorialVideo"]?.jsonObject
        val artwork = attributes["editorialArtwork"]?.jsonObject

        fun motion(vararg keys: String): JsonObject? =
            keys.firstNotNullOfOrNull { key -> video?.get(key)?.jsonObject?.takeIf { it["video"] != null } }

        val wide = motion("motionArtistWide16x9", "motionArtistFullscreen16x9", "motionDetailRaw")
        val square = motion("motionArtistSquare1x1", "motionDetailSquare", "motionSquareVideo1x1")
        val wideUrl = wide?.get("video")?.jsonPrimitive?.contentOrNull
            ?: extractAnyVideo(video)
            ?: extractAnyVideo(artwork)
        val squareUrl = square?.get("video")?.jsonPrimitive?.contentOrNull
        // a phone's banner is taller than it is wide, so the square loop's frame crops far less
        val preview = (square ?: wide)?.get("previewFrame")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

        val logo = artwork?.get("musicContentColorLogoTrimmed")?.jsonObject
            ?: artwork?.get("musicContentLogoTrimmed")?.jsonObject
        val logoWidth = logo?.get("width")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
        val logoHeight = logo?.get("height")?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

        val banner = listOf("bannerUber", "storeFlowcase", "subscriptionHero", "originalFlowcaseBrick")
            .firstNotNullOfOrNull { key -> artwork?.get(key)?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull }

        val latest = artist["views"]?.jsonObject?.get("latest-release")?.jsonObject
            ?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.get("attributes")?.jsonObject
            ?.let { release ->
                val name = release["name"]?.jsonPrimitive?.contentOrNull ?: return@let null
                AppleRelease(
                    // "Kita Kesana - Single": the kind is in isSingle, and YouTube's title has no suffix
                    name = name.replace(Regex("""\s+-\s+(Single|EP)$"""), ""),
                    releaseDate = release["releaseDate"]?.jsonPrimitive?.contentOrNull,
                    artworkUrl = release["artwork"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                    isSingle = release["isSingle"]?.jsonPrimitive?.contentOrNull == "true",
                    trackCount = release["trackCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                )
            }

        AppleArtistEditorial(
            appleId = artistId,
            name = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            motionVideoUrl = wideUrl,
            motionSquareUrl = squareUrl,
            previewFrameUrl = preview,
            bannerUrl = banner,
            logoUrl = logo?.get("url")?.jsonPrimitive?.contentOrNull,
            logoAspect = if (logoWidth != null && logoHeight != null && logoHeight > 0f) logoWidth / logoHeight else null,
            bio = attributes["artistBio"]?.jsonPrimitive?.contentOrNull?.let(::plainText),
            latestRelease = latest,
        )
    }.getOrNull()

    // Apple's bios are small bits of HTML: line breaks, the odd <i>, entities
    private fun plainText(html: String): String? =
        html.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { null }

    private fun extractAnyVideo(editorial: JsonObject?): String? {
        editorial ?: return null
        for ((_, value) in editorial) {
            val url = (value as? JsonObject)?.get("video")?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    // "twenty one pilots" and "Twenty One Pilots", "Beyoncé" and "Beyonce", "AC/DC" and "ACDC"
    private fun normalize(name: String): String =
        java.text.Normalizer.normalize(name.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }
}
