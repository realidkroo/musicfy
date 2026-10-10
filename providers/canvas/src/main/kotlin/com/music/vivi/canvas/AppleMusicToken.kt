// AppleMusicToken.kt

package com.example.musicfy.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * the developer token Apple's web player signs its catalog calls with. it's baked into the web
 * player's main script and rotated every few months, so a copy hardcoded in the app goes stale (the
 * last one expired on 17 June 2026 and took the artist backgrounds with it). this reads the current
 * one out of music.apple.com once per run and keeps it until shortly before it expires.
 */
object AppleMusicToken {

    // read from the web player on 10 Oct 2026, good until 10 Dec 2026. only used when the page
    // can't be reached; the live one replaces it as soon as it can be read
    private const val FALLBACK =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzkwODk0NTM2LCJleHAiOjE3OTY5NDI1MzYsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".VPhwMsGxew0uPgpC1HvhCyQmaWNI5AmJsCxSRT3NFlbF79Gtfdg2O1r-QZv6Gbj7lzYtCSxsmcVDbWA9ujHsHA"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"

    private val mutex = Mutex()
    @Volatile private var cached: String? = null
    @Volatile private var expiresAtMs: Long = 0L
    // after a failed read, wait a while before trying the page again instead of on every call
    @Volatile private var retryAfterMs: Long = 0L

    suspend fun get(client: HttpClient): String {
        val now = System.currentTimeMillis()
        cached?.takeIf { now < expiresAtMs - 60_000 }?.let { return it }
        return mutex.withLock {
            cached?.takeIf { System.currentTimeMillis() < expiresAtMs - 60_000 }?.let { return@withLock it }
            if (System.currentTimeMillis() < retryAfterMs) return@withLock cached ?: FALLBACK
            val fresh = runCatching { read(client) }.getOrNull()
            if (fresh != null) {
                cached = fresh.first
                expiresAtMs = fresh.second
                fresh.first
            } else {
                retryAfterMs = System.currentTimeMillis() + 10 * 60_000
                cached ?: FALLBACK
            }
        }
    }

    /** a 401 means the token was pulled early; forget it so the next call reads a new one */
    fun invalidate() {
        cached = null
        expiresAtMs = 0L
        retryAfterMs = 0L
    }

    private suspend fun read(client: HttpClient): Pair<String, Long>? {
        val html = client.get("https://music.apple.com/us/browse") {
            header("User-Agent", USER_AGENT)
        }.body<String>()
        val scripts = Regex("""/assets/index(?:-legacy)?[~-][a-zA-Z0-9_-]+\.js""")
            .findAll(html).map { it.value }.distinct().toList()
        val now = System.currentTimeMillis()
        for (path in scripts) {
            val script = client.get("https://music.apple.com$path") {
                header("User-Agent", USER_AGENT)
            }.body<String>()
            // the bundle carries a few tokens; the web player's own is issued by AMPWebPlay
            val candidates = Regex("""eyJ[a-zA-Z0-9_-]+\.eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")
                .findAll(script).map { it.value }.toList()
            val decoded = candidates.mapNotNull { token ->
                val payload = runCatching {
                    String(java.util.Base64.getUrlDecoder().decode(token.split(".")[1]), Charsets.UTF_8)
                }.getOrNull() ?: return@mapNotNull null
                val exp = Regex("\"exp\":(\\d+)").find(payload)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@mapNotNull null
                if (exp * 1000 <= now) return@mapNotNull null
                Triple(token, exp * 1000, payload.contains("AMPWebPlay"))
            }
            val best = decoded.firstOrNull { it.third } ?: decoded.firstOrNull()
            if (best != null) return best.first to best.second
        }
        return null
    }
}
