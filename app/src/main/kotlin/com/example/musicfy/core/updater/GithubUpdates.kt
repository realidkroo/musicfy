// GithubUpdates.kt
// Update client for github.com/realidkroo/musicfy.
//
// Separate from musicfyupdater.kt, which targets the old musicfy-app org and carries a nightly
// workflow channel this repo doesn't have. Nothing here touches that file.
//
// ─────────────────────────────────────────────────────────────────────────────
// RELEASE FORMAT — what the repo has to publish for this to work
// ─────────────────────────────────────────────────────────────────────────────
//
//   Tag        dev                     rolling dev channel; always force-moved to the newest
//              v1.4.0                  stable, semver, "v" prefix
//
//   Title      musicfy 1.4.0 (881)     "musicfy <versionName> (<buildNumber>)"
//                                      versionName is what gets compared against BuildConfig;
//                                      the parenthesised build number is display only.
//
//   Body       markdown                shown verbatim as the changelog. First line is the
//                                      headline, the rest is detail.
//
//   Assets     musicfy-1.4.0-universal.apk      ← this is the one the updater installs
//              musicfy-1.4.0-arm64-v8a.apk
//              musicfy-1.4.0-armeabi-v7a.apk
//              musicfy-1.4.0-x86_64.apk
//              musicfy-1.4.0-x86.apk
//
//              i.e. musicfy-<versionName>-<abi>.apk. The updater looks for "universal" and
//              falls back to the first .apk asset if the repo only ships one.

package com.example.musicfy.core.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.musicfy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val GithubOwner = "realidkroo"
const val GithubRepo = "musicfy"

const val GithubProfileUrl = "https://github.com/$GithubOwner"
const val GithubRepoUrl = "https://github.com/$GithubOwner/$GithubRepo"
const val InstagramUrl = "https://www.instagram.com/realidkroo/"

private const val ReleasesApi =
    "https://api.github.com/repos/$GithubOwner/$GithubRepo/releases?per_page=20"

/** Which ABI's asset to install. Universal, because that is what the repo always ships. */
private const val PreferredAbi = "universal"

data class GithubRelease(
    /** Release title, e.g. "musicfy 1.4.0 (881)". */
    val title: String,
    val tag: String,
    /** Version parsed out of the title/tag, compared against BuildConfig.VERSION_NAME. */
    val version: String,
    /** Markdown body — the changelog. */
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Newest release that is actually newer than what's installed.
 *
 * "Newest" is the first entry the API returns — GitHub orders releases by creation date — after
 * skipping drafts. Prereleases are included: the dev channel publishes as one.
 */
suspend fun fetchLatestRelease(): Result<GithubRelease?> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(ReleasesApi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rejects requests with no User-Agent outright.
            setRequestProperty("User-Agent", "musicfy-android")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(json)

        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue

            val tag = release.optString("tag_name").orEmpty()
            val title = release.optString("name").orEmpty().ifBlank { tag }
            val version = parseVersion(title, tag) ?: continue

            val assets = release.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            var fallbackUrl: String? = null
            var fallbackName: String? = null
            var fallbackSize = 0L

            if (assets != null) {
                for (a in 0 until assets.length()) {
                    val asset = assets.getJSONObject(a)
                    val name = asset.optString("name").orEmpty()
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val url = asset.optString("browser_download_url").orEmpty()
                    val size = asset.optLong("size", 0L)
                    if (name.contains(PreferredAbi, ignoreCase = true)) {
                        apkUrl = url
                        apkName = name
                        apkSize = size
                    } else if (fallbackUrl == null) {
                        fallbackUrl = url
                        fallbackName = name
                        fallbackSize = size
                    }
                }
            }

            return@runCatching GithubRelease(
                title = title,
                tag = tag,
                version = version,
                body = release.optString("body").orEmpty(),
                htmlUrl = release.optString("html_url").orEmpty().ifBlank { "$GithubRepoUrl/releases" },
                apkUrl = apkUrl ?: fallbackUrl,
                apkName = apkName ?: fallbackName,
                apkSizeBytes = if (apkUrl != null) apkSize else fallbackSize,
                publishedAt = release.optString("published_at").orEmpty(),
            )
        }
        null
    }
}

/**
 * Pulls a version out of a release title or tag.
 *
 * Accepts "musicfy 1.4.0 (881)", "v1.4.0" and a bare "1.4.0". The rolling "dev" tag carries no
 * version of its own, so for that one the title is the only source.
 */
internal fun parseVersion(title: String, tag: String): String? {
    val pattern = Regex("""(\d+(?:\.\d+)+)""")
    return pattern.find(title)?.groupValues?.get(1)
        ?: pattern.find(tag)?.groupValues?.get(1)
}

/** True when [candidate] is a strictly higher version than what is installed. */
fun isNewerThanInstalled(candidate: String): Boolean =
    compareVersions(candidate, BuildConfig.VERSION_NAME) > 0

internal fun compareVersions(a: String, b: String): Int {
    // Trailing non-numeric junk ("1.4.0-beta") is dropped rather than making the whole compare
    // fail — a suffixed build of a higher version is still a higher version.
    val aParts = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val bParts = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(aParts.size, bParts.size)) {
        val diff = (aParts.getOrElse(i) { 0 }) - (bParts.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

/**
 * Downloads the release APK into the cache, reporting 0..1 as it goes.
 *
 * @return the downloaded file, or null if the release has no APK attached.
 */
suspend fun downloadApk(
    context: Context,
    release: GithubRelease,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val url = release.apkUrl ?: error("This release has no APK attached")
        val target = File(context.cacheDir, release.apkName ?: "musicfy-update.apk")
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "musicfy-android")
        }
        // Content-Length can be absent behind the redirect to the CDN; the release metadata's
        // size is the reliable one, so prefer it and only fall back to the header.
        val total = release.apkSizeBytes.takeIf { it > 0 }
            ?: connection.contentLengthLong.takeIf { it > 0 }
            ?: -1L

        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        target
    }
}

/** Hands the downloaded APK to the system installer. */
fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
