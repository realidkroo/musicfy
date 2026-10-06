// ListeningGenres.kt

package com.example.musicfy.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * works out which YouTube Music genres someone actually listens to. YouTube never says what genre
 * a song is, but iTunes does say it for an artist, so each of your most played artists is looked
 * up there (once per session) and its genre is matched onto YouTube's own genre tiles.
 */
object ListeningGenres {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // artist name -> iTunes genre; an empty string remembers a miss, so it isn't asked about again
    private val artistGenres = ConcurrentHashMap<String, String>()

    /** iTunes' main genre for [artistName], like "Hip-Hop/Rap", "K-Pop" or "Alternative". blocking. */
    fun itunesGenre(artistName: String): String? {
        val name = artistName.trim()
        val key = name.lowercase(Locale.ROOT)
        if (key.isEmpty()) return null
        artistGenres[key]?.let { return it.ifEmpty { null } }

        val genre = runCatching {
            val encoded = URLEncoder.encode(name, Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=music&entity=musicArtist&limit=5")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val results = JSONObject(body).optJSONArray("results") ?: return@use null
                val artists = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
                // the same name if there is one, otherwise trust the top hit
                val match = artists.firstOrNull { it.optString("artistName").trim().equals(name, ignoreCase = true) }
                    ?: artists.firstOrNull()
                match?.optString("primaryGenreName")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

        artistGenres[key] = genre.orEmpty()
        return genre
    }

    /**
     * the YouTube genre tile an iTunes genre belongs to. YouTube's tiles come in the user's language,
     * so they're matched on stems that read the same in most languages ("pop", "hip", "r&b",
     * "electr"/"elektr"...), an exact title first so "Pop" never lands on "K-Pop".
     */
    fun <T> match(itunesGenre: String, youtubeGenres: List<T>, title: (T) -> String): T? {
        val family = familyOf(itunesGenre) ?: return null
        return youtubeGenres.firstOrNull { family.matchesExactly(title(it)) }
            ?: youtubeGenres.firstOrNull { family.matches(title(it)) }
    }

    /** how many of [titles] read as genres, to tell YouTube's genre grid from its moods grid */
    fun genreScore(titles: List<String>): Int = titles.count(::isGenre)

    /** false for the grid's odd ones out, like "Decades" or "Family" */
    fun isGenre(title: String): Boolean = GenreFamily.entries.any { it.matches(title) }

    private fun familyOf(itunesGenre: String): GenreFamily? {
        val genre = itunesGenre.lowercase(Locale.ROOT)
        return GenreFamily.entries.firstOrNull { family -> family.itunes.any { genre.contains(it) } }
    }
}

/**
 * genre families, most specific first: K-Pop is checked before Pop, Latin before Pop ("Pop Latino"),
 * Alternative before Rock and Pop ("Indie Pop", "Indie Rock"), Metal before Rock.
 */
private enum class GenreFamily(val itunes: List<String>, val youtube: List<String>) {
    KPop(listOf("k-pop"), listOf("k-pop", "kpop")),
    JPop(listOf("j-pop", "anime"), listOf("j-pop", "jpop")),
    Mandopop(listOf("mandopop", "cantopop", "c-pop", "chinese"), listOf("mandopop", "cantopop")),
    HipHop(listOf("hip-hop", "hip hop", "rap"), listOf("hip-hop", "hip hop", "hiphop", "rap")),
    RnB(listOf("r&b", "soul", "funk"), listOf("r&b", "soul")),
    Electronic(
        listOf("electronic", "dance", "house", "techno", "trance", "edm", "dubstep"),
        listOf("dance", "electr", "elektr", "dansa", "électr", "eletr"),
    ),
    Metal(listOf("metal"), listOf("metal")),
    Latin(listOf("latin", "reggaeton", "salsa", "bachata"), listOf("latin")),
    Alternative(listOf("alternative", "indie"), listOf("indie", "altern")),
    Rock(listOf("rock", "punk", "grunge"), listOf("rock")),
    Jazz(listOf("jazz"), listOf("jazz")),
    Classical(listOf("classical", "opera"), listOf("classical", "klasik", "clásic", "classiq", "klassik", "clássic", "classic")),
    Country(listOf("country", "americana"), listOf("country")),
    Reggae(listOf("reggae", "dancehall", "caribbean"), listOf("reggae")),
    Blues(listOf("blues"), listOf("blues")),
    Folk(listOf("folk", "singer/songwriter", "acoustic"), listOf("folk", "akustik", "acoustic", "acústic")),
    Christian(listOf("christian", "gospel"), listOf("christian", "gospel", "kristen", "rohani", "cristian", "chrétien")),
    Soundtrack(listOf("soundtrack"), listOf("soundtrack", "musical")),
    African(listOf("afro", "african", "amapiano"), listOf("afric", "afrika", "afro")),
    Arabic(listOf("arabic"), listOf("arab")),
    Indian(listOf("bollywood", "indian", "hindi", "punjabi", "tamil"), listOf("india")),
    Brazilian(listOf("brazil", "mpb", "sertanejo", "samba", "bossa"), listOf("brazil", "brasil")),
    Dangdut(listOf("dangdut"), listOf("dangdut")),
    Pop(listOf("pop"), listOf("pop"));

    fun matches(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return youtube.any { t.contains(it) }
    }

    fun matchesExactly(title: String): Boolean {
        val t = title.trim().lowercase(Locale.ROOT)
        return youtube.any { t == it }
    }
}
