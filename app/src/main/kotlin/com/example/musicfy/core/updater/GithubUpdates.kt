// GithubUpdates.kt
// Update client for github.com/realidkroo/musicfy.

package com.example.musicfy.core.updater

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.musicfy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
 * Structured version representation for comparing SemVer + Build numbers.
 */
data class FullVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val buildNumber: Int = 0
) : Comparable<FullVersion> {
    override fun compareTo(other: FullVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return buildNumber.compareTo(other.buildNumber)
    }
}

/**
 * Parses full SemVer + build attempt out of text e.g. "musicfy 6.0.8 (944)" or "6.0.8 build#944".
 */
fun parseFullVersion(text: String): FullVersion {
    val semverRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
    val buildNumRegex = Regex("""(?:build#|\(#|#|\()(\d+)\)?""", RegexOption.IGNORE_CASE)

    val semverMatch = semverRegex.find(text)
    val major = semverMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minor = semverMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
    val patch = semverMatch?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0

    val buildMatch = buildNumRegex.find(text)
    val buildNumber = buildMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

    return FullVersion(major, minor, patch, buildNumber)
}

private const val CacheTtlMillis = 15L * 60L * 1000L // 15-minute TTL cache

private val cacheLock = Any()
private var cachedAt = 0L
private var cachedResult: Result<GithubRelease?>? = null
private var inFlight: kotlinx.coroutines.Deferred<Result<GithubRelease?>>? = null

private val updateScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
)

/**
 * Cached wrapper around [fetchLatestRelease].
 *
 * @param force skips the cache for explicit user check requests.
 */
suspend fun getLatestRelease(force: Boolean = false): Result<GithubRelease?> {
    val deferred = synchronized(cacheLock) {
        if (!force) {
            val cached = cachedResult
            if (cached != null && System.currentTimeMillis() - cachedAt < CacheTtlMillis) {
                return cached
            }
        }
        inFlight ?: updateScope.async { fetchLatestRelease() }.also { inFlight = it }
    }

    val result = deferred.await()
    synchronized(cacheLock) {
        if (inFlight === deferred) inFlight = null
        if (result.isSuccess) {
            cachedResult = result
            cachedAt = System.currentTimeMillis()
        }
    }
    return result
}

/**
 * Fetches all non-draft releases from GitHub and finds the release with the HIGHEST version.
 */
suspend fun fetchLatestRelease(): Result<GithubRelease?> = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(ReleasesApi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "musicfy-android")
        }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(json)

        var bestRelease: GithubRelease? = null
        var bestFullVersion: FullVersion? = null

        val appArch = runCatching { BuildConfig.ARCHITECTURE }.getOrDefault("universal")

        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue

            val tag = release.optString("tag_name").orEmpty()
            val title = release.optString("name").orEmpty().ifBlank { tag }
            val fullVer = parseFullVersion("$title $tag")

            if (fullVer.major == 0 && fullVer.minor == 0 && fullVer.patch == 0) continue

            val displayVersion = if (fullVer.buildNumber > 0) {
                "${fullVer.major}.${fullVer.minor}.${fullVer.patch} (${fullVer.buildNumber})"
            } else {
                "${fullVer.major}.${fullVer.minor}.${fullVer.patch}"
            }

            val assets = release.optJSONArray("assets")
            var selectedUrl: String? = null
            var selectedName: String? = null
            var selectedSize = 0L

            var universalUrl: String? = null
            var universalName: String? = null
            var universalSize = 0L

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

                    if (appArch.isNotBlank() && appArch != "universal" && name.contains(appArch, ignoreCase = true)) {
                        selectedUrl = url
                        selectedName = name
                        selectedSize = size
                    } else if (name.contains("universal", ignoreCase = true)) {
                        universalUrl = url
                        universalName = name
                        universalSize = size
                    } else if (fallbackUrl == null) {
                        fallbackUrl = url
                        fallbackName = name
                        fallbackSize = size
                    }
                }
            }

            val apkUrl = selectedUrl ?: universalUrl ?: fallbackUrl
            val apkName = selectedName ?: universalName ?: fallbackName
            val apkSize = when {
                selectedUrl != null -> selectedSize
                universalUrl != null -> universalSize
                else -> fallbackSize
            }

            val currentRelease = GithubRelease(
                title = title,
                tag = tag,
                version = displayVersion,
                body = release.optString("body").orEmpty(),
                htmlUrl = release.optString("html_url").orEmpty().ifBlank { "$GithubRepoUrl/releases" },
                apkUrl = apkUrl,
                apkName = apkName,
                apkSizeBytes = apkSize,
                publishedAt = release.optString("published_at").orEmpty(),
            )

            // Select the release with the HIGHEST version across all GitHub releases
            if (bestFullVersion == null || fullVer > bestFullVersion) {
                bestFullVersion = fullVer
                bestRelease = currentRelease
            }
        }

        bestRelease
    }
}

/** True when candidate version is strictly higher than currently installed version. */
fun isNewerThanInstalled(candidateVersionText: String): Boolean {
    val candidate = parseFullVersion(candidateVersionText)
    val installed = parseFullVersion(BuildConfig.VERSION_NAME)
    return candidate > installed
}

/**
 * Downloads the release APK into the cache, reporting progress 0..1.
 */
suspend fun downloadApk(
    context: Context,
    release: GithubRelease,
    onProgress: (Float) -> Unit,
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val url = release.apkUrl ?: error("This release has no APK attached")
        val target = apkFileFor(context, release)

        if (isDownloaded(context, release)) {
            onProgress(1f)
            return@runCatching target
        }
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "musicfy-android")
        }
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

fun apkFileFor(context: Context, release: GithubRelease): File =
    File(context.cacheDir, release.apkName ?: "musicfy-update.apk")

fun isDownloaded(context: Context, release: GithubRelease): Boolean {
    val f = apkFileFor(context, release)
    if (!f.exists() || f.length() <= 0L) return false
    return release.apkSizeBytes <= 0L || f.length() == release.apkSizeBytes
}

fun canInstallPackages(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

fun requestInstallPermission(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun installApk(context: Context, apk: File): Boolean {
    if (!canInstallPackages(context)) {
        requestInstallPermission(context)
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
