// DevSpaceData.kt
//
// What the Developer Space shows, straight from GitHub: issue and release counts, the commits since
// this build was made, and the latest issues. The page asks for a fresh copy every time it opens;
// the Musicfy page's card borrows whatever is already here. Anything GitHub refuses (rate limit,
// offline) leaves the last good numbers in place and sets [DevSpaceRepository.error].

package com.example.musicfy.ui.screens.settings.devspace

import com.example.musicfy.core.updater.GithubOwner
import com.example.musicfy.core.updater.GithubRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter

data class DevIssue(
    val number: Int,
    val title: String,
    val open: Boolean,
    val labels: List<String>,
    val url: String,
    val createdAt: Instant,
) {
    /** A "bug" label, or the word in the title - reporters don't always label. */
    val isBug: Boolean = labels.any { it.contains("bug", true) } || title.contains("bug", true)

    val isFeature: Boolean = !isBug && (
        labels.any { it.contains("enhancement", true) || it.contains("feature", true) } ||
            title.contains("feature", true) || title.contains("request", true)
        )
}

data class DevSpaceStats(
    val openTotal: Int = 0,
    val closedTotal: Int = 0,
    val newIssues: Int? = null,
    val newResolved: Int? = null,
    val commits: Int? = null,
    val releases: Int? = null,
    val issues: List<DevIssue> = emptyList(),
    val fetchedAt: Long = 0L,
) {
    val reportedTotal: Int get() = openTotal + closedTotal
}

object DevSpaceRepository {
    private val stats = MutableStateFlow<DevSpaceStats?>(null)
    private val loadingFlag = MutableStateFlow(false)
    private val errorFlag = MutableStateFlow<String?>(null)
    private val mutex = Mutex()

    val current: StateFlow<DevSpaceStats?> = stats.asStateFlow()
    val loading: StateFlow<Boolean> = loadingFlag.asStateFlow()
    val error: StateFlow<String?> = errorFlag.asStateFlow()

    private const val Api = "https://api.github.com"
    private const val Repo = "$GithubOwner/$GithubRepo"

    /** Fetches everything again; with [force] false, a copy under [maxAgeMs] old is left alone. */
    suspend fun refresh(sinceMillis: Long, force: Boolean, maxAgeMs: Long = 60_000L) {
        val known = stats.value
        if (!force && known != null && System.currentTimeMillis() - known.fetchedAt < maxAgeMs) return
        // two openers at once (the page and its card): a forced one waits its turn, a lazy one leaves it
        if (force) mutex.lock() else if (!mutex.tryLock()) return
        loadingFlag.value = true
        try {
            val since = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sinceMillis))
            val old = stats.value ?: DevSpaceStats()
            var failure: String? = null
            fun <T> guarded(block: () -> T): T? = try {
                block()
            } catch (e: RateLimited) {
                failure = "GitHub's rate limit is reached, try again in a few minutes"
                null
            } catch (e: Exception) {
                failure = failure ?: "Couldn't reach GitHub"
                null
            }

            val fresh = withContext(Dispatchers.IO) {
                coroutineScope {
                    val open = async { guarded { searchCount("is:open") } }
                    val closed = async { guarded { searchCount("is:closed") } }
                    val newIssues = async { guarded { searchCount("created:>=$since") } }
                    val newResolved = async { guarded { searchCount("is:closed closed:>=$since") } }
                    val commits = async { guarded { commitsSince(since) } }
                    val releases = async { guarded { releasesSince(sinceMillis) } }
                    val issues = async { guarded { latestIssues() } }
                    DevSpaceStats(
                        openTotal = open.await() ?: old.openTotal,
                        closedTotal = closed.await() ?: old.closedTotal,
                        newIssues = newIssues.await() ?: old.newIssues,
                        newResolved = newResolved.await() ?: old.newResolved,
                        commits = commits.await() ?: old.commits,
                        releases = releases.await() ?: old.releases,
                        issues = issues.await() ?: old.issues,
                        fetchedAt = System.currentTimeMillis(),
                    )
                }
            }
            stats.value = fresh
            errorFlag.value = failure
        } finally {
            loadingFlag.value = false
            mutex.unlock()
        }
    }

    private class RateLimited : Exception()

    private fun get(path: String): Pair<String, String?> {
        val c = URL(Api + path).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Musicfy")
        try {
            val code = c.responseCode
            if (code == 403 || code == 429) throw RateLimited()
            if (code !in 200..299) error("GitHub answered $code")
            return c.inputStream.bufferedReader().use { it.readText() } to c.getHeaderField("Link")
        } finally {
            c.disconnect()
        }
    }

    /** Issues (never pull requests) matching [qualifiers], counted by GitHub. */
    private fun searchCount(qualifiers: String): Int {
        val q = URLEncoder.encode("repo:$Repo is:issue $qualifiers", "UTF-8")
        return JSONObject(get("/search/issues?q=$q&per_page=1").first).getInt("total_count")
    }

    /** One commit per page, so the last page's number is the count. */
    private fun commitsSince(since: String): Int {
        val (body, link) = get("/repos/$Repo/commits?since=$since&per_page=1")
        val last = link?.let { Regex("[?&]page=(\\d+)>; rel=\"last\"").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return last ?: JSONArray(body).length()
    }

    private fun releasesSince(sinceMillis: Long): Int {
        val arr = JSONArray(get("/repos/$Repo/releases?per_page=100").first)
        var n = 0
        for (i in 0 until arr.length()) {
            val at = arr.getJSONObject(i).optString("published_at").takeIf { it.isNotBlank() } ?: continue
            if (Instant.parse(at).toEpochMilli() >= sinceMillis) n++
        }
        return n
    }

    private fun latestIssues(): List<DevIssue> {
        val arr = JSONArray(get("/repos/$Repo/issues?state=all&per_page=60&sort=created&direction=desc").first)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("pull_request")) continue
                val labels = o.optJSONArray("labels")?.let { l -> (0 until l.length()).map { l.getJSONObject(it).optString("name") } }.orEmpty()
                add(
                    DevIssue(
                        number = o.getInt("number"),
                        title = o.getString("title"),
                        open = o.getString("state") == "open",
                        labels = labels,
                        url = o.getString("html_url"),
                        createdAt = Instant.parse(o.getString("created_at")),
                    ),
                )
            }
        }
    }
}
