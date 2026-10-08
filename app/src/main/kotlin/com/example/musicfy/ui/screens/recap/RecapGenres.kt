// RecapGenres.kt
//
// Nothing in the library knows a song's genre - YouTube Music never says. The Stats page borrows
// what Home's recommender already does (ListeningGenres): iTunes' primary genre for each *artist*,
// asked with the artist's name only, never with anything about what or how much was played. Known
// answers are kept on disk so each artist is asked about once, and shares are weighted locally by
// listening time.

package com.example.musicfy.ui.screens.recap

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.musicfy.utils.ListeningGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Immutable
data class GenreShare(val name: String, val fraction: Float, val playTimeMs: Long)

internal object RecapGenres {
    private const val CacheFileName = "recap_artist_genres.json"

    /** New lookups per call; the rest wait for the next visit (the API allows ~20 a minute). */
    private const val MaxLookupsPerCall = 12

    private val mutex = Mutex()

    /** Normalised artist name -> iTunes genre. Only answers are kept; a miss is asked again next session. */
    private var cache: MutableMap<String, String>? = null

    /** Genre shares of [artists]' listening time, biggest first; null when none could be named. */
    suspend fun shares(context: Context, artists: List<RecapArtist>): List<GenreShare>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val known = cache ?: readCache(context).also { cache = it }
            var asked = 0
            var dirty = false
            for (artist in artists) {
                val key = keyOf(artist.name)
                if (key.isEmpty() || key in known) continue
                if (asked >= MaxLookupsPerCall) break
                if (asked > 0) delay(220)
                asked++
                val genre = ListeningGenres.itunesGenre(artist.name) ?: continue
                known[key] = genre
                dirty = true
            }
            if (dirty) writeCache(context, known)

            val totals = LinkedHashMap<String, Long>()
            for (artist in artists) {
                val genre = known[keyOf(artist.name)] ?: continue
                totals[genre] = (totals[genre] ?: 0L) + artist.playTimeMs
            }
            val sum = totals.values.sum().takeIf { it > 0 } ?: return@withLock null
            totals.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { GenreShare(it.key, it.value.toFloat() / sum, it.value) }
        }
    }

    private fun keyOf(name: String) = name.trim().lowercase()

    private fun readCache(context: Context): MutableMap<String, String> = try {
        val file = File(context.filesDir, CacheFileName)
        if (!file.exists()) {
            HashMap()
        } else {
            val json = JSONObject(file.readText())
            HashMap<String, String>().apply { json.keys().forEach { k -> json.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) } } }
        }
    } catch (_: Exception) {
        HashMap()
    }

    private fun writeCache(context: Context, map: Map<String, String>) {
        runCatching {
            val json = JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            File(context.filesDir, CacheFileName).writeText(json.toString())
        }
    }
}
