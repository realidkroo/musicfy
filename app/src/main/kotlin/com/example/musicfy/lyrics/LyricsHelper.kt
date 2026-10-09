// LyricsHelper.kt

package com.example.musicfy.lyrics

import android.content.Context
import android.util.LruCache
import com.example.musicfy.constants.LyricsProviderOrderKey
import com.example.musicfy.constants.PreferredLyricsProvider
import com.example.musicfy.constants.PreferredLyricsProviderKey
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.extensions.toEnum
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.utils.NetworkConnectivityObserver
import com.example.musicfy.utils.dataStore
import com.example.musicfy.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private val bestCache = LruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    /**
     * Picks the best lyrics for [mediaMetadata] out of the enabled providers, in preference order.
     *
     * Providers are tried in order and each result is *graded* rather than taken on faith. The old
     * acceptance test was `contains("<") && contains(">") && contains(":")`, which is true of any
     * LRC file with an emoticon in it, and which said nothing at all about what language came
     * back — so a Chinese translation of an English song scored the same as the real thing and won
     * on provider order alone. Grading compares three things, in this order of importance:
     *
     *  1. **Original script.** Decided from evidence about the song; see [pickBest].
     *  2. **Sync quality.** Word > line > plain. This is what "flowing lyrics" means.
     *  3. **Voice tags.** Duet attribution, which drives left/right layout.
     *  4. **Provider order.** Only breaks ties.
     *
     * All enabled providers are queried, since the script comparison needs the full candidate set.
     */
    /**
     * @param forceRefresh skip the in-memory result cache and re-query every provider. Required by
     *   "refetch lyrics": that path deletes the stored row and asks again, and without this it
     *   would be handed back the very result it was trying to discard.
     */
    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        forceRefresh: Boolean = false,
    ): LyricsWithProvider {
        if (forceRefresh) {
            bestCache.remove(mediaMetadata.id)
        } else {
            bestCache.get(mediaMetadata.id)?.let { return it }
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {

            true
        }

        if (!isNetworkAvailable) {

            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val providers = resolveLyricsProviders()
        val candidates = fetchCandidates(mediaMetadata, providers)
            .filter { it.sync != LyricsUtils.SyncKind.NONE }

        if (candidates.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        val best = pickBest(mediaMetadata, candidates, providers.size)
        val result = LyricsWithProvider(best.lyrics, best.providerName)
        bestCache.put(mediaMetadata.id, result)
        return result
    }

    /**
     * One provider's answer for [mediaMetadata], graded. Used by the source picker so it can show
     * which providers actually have lyrics for this song before the user picks one.
     */
    data class ProviderCandidate(
        val providerName: String,
        val lyrics: String,
        val order: Int,
        val sync: LyricsUtils.SyncKind,
        val script: LyricsUtils.Script,
    )

    /**
     * Asks every enabled provider at once. They used to be asked one after another, so a song
     * waited on the sum of eight network round trips (each with its own timeouts) before anything
     * was shown, and switching source felt like nothing happened.
     */
    suspend fun fetchCandidates(
        mediaMetadata: MediaMetadata,
        providers: List<LyricsProvider>? = null,
    ): List<ProviderCandidate> = coroutineScope {
        val ordered = providers ?: resolveLyricsProviders()
        ordered.mapIndexed { order, provider ->
            async { fetchCandidate(mediaMetadata, provider, order) }
        }.awaitAll().filterNotNull()
    }

    /** The enabled providers, in the user's preferred order. */
    suspend fun orderedProviders(): List<LyricsProvider> = resolveLyricsProviders()

    fun isProviderEnabled(provider: LyricsProvider): Boolean = provider.isEnabled(context)

    /** Asks one provider. Null when it is switched off, has nothing, fails or times out. */
    suspend fun fetchCandidate(
        mediaMetadata: MediaMetadata,
        provider: LyricsProvider,
        order: Int,
    ): ProviderCandidate? {
        if (!provider.isEnabled(context)) return null
        val lyrics = try {
            withTimeoutOrNull(ProviderTimeoutMs) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration,
                    mediaMetadata.album?.title,
                ).onFailure { reportException(it) }.getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            null
        }
        if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND) return null
        return ProviderCandidate(
            providerName = provider.name,
            lyrics = lyrics,
            order = order,
            sync = LyricsUtils.syncKind(lyrics),
            script = LyricsUtils.lyricsScript(lyrics),
        ).takeIf { it.sync != LyricsUtils.SyncKind.NONE }
    }

    /** Remembers a source the user picked by hand, so the next lookup returns it. */
    fun rememberChoice(mediaId: String, choice: LyricsWithProvider) {
        bestCache.put(mediaId, choice)
    }

    /**
     * Picks the lyrics most likely to be the song as sung.
     *
     * Language comes first, and it is decided from evidence about the SONG rather than from the
     * candidates alone:
     *
     *  1. A non-Latin title, artist or album ("夜に駆ける", "아이유") says what script the song is in.
     *  2. Otherwise, YouTube's own lyrics or captions for this exact video, when they are in a
     *     non-Latin script. They come from the video itself, not from a title search, so they
     *     cannot be a different song or a translation fetched by mistake.
     *  3. Otherwise the old rule, narrowed: a non-Latin result beats a Latin one (the Latin one
     *     being a translation or romanisation) only when it is Japanese or Korean, or when two
     *     providers agree on it. A lone Chinese result for a song with a Latin title is far more
     *     often a mismatched search hit than the original, and it used to win outright, which is
     *     how English and Indonesian songs ended up showing Chinese lyrics.
     *
     * Then sync quality (word > line > plain), then duet tags, then provider order.
     */
    private fun pickBest(
        mediaMetadata: MediaMetadata,
        candidates: List<ProviderCandidate>,
        providerCount: Int,
    ): ProviderCandidate {
        val metadataScript = LyricsUtils.dominantScript(
            listOfNotNull(
                mediaMetadata.title,
                mediaMetadata.artists.joinToString(" ") { it.name },
                mediaMetadata.album?.title,
            ).joinToString(" ")
        )
        val referenceScript = metadataScript.takeIf(::isNative)
            ?: candidates
                .filter { it.providerName in LanguageReferenceProviders }
                .map { it.script }
                .firstOrNull(::isNative)

        val trustedNative: Set<LyricsUtils.Script> = if (referenceScript != null) {
            emptySet()
        } else {
            candidates.map { it.script }
                .filter(::isNative)
                .groupingBy { it }
                .eachCount()
                .filter { (script, count) ->
                    count >= 2 || script == LyricsUtils.Script.KANA || script == LyricsUtils.Script.HANGUL
                }
                .keys
        }

        return candidates.maxByOrNull { c ->
            val scriptScore = when {
                referenceScript != null -> when {
                    c.script == referenceScript -> 3
                    sameLanguageFamily(c.script, referenceScript) -> 2
                    c.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                trustedNative.isNotEmpty() -> when {
                    c.script in trustedNative -> 2
                    c.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                // Nothing points at a non-Latin original: an untrusted non-Latin result is the
                // odd one out, not the preferred one.
                isNative(c.script) -> 0
                else -> 1
            }
            // Syllable-split payloads are word timings smeared across single letters; they
            // render as gibberish word-by-word, so demote them to the level of line-synced.
            val syncScore = if (c.sync == LyricsUtils.SyncKind.WORD && LyricsUtils.isSyllableSplit(c.lyrics)) {
                LyricsUtils.SyncKind.LINE.ordinal
            } else {
                c.sync.ordinal
            }
            // Duet/voice tagging is strictly extra information: it is what lets the screen put
            // one singer left and the answering singer right. Only BetterLyrics (from TTML's
            // ttm:agent) and Paxsenix emit it — YouLyPlus never does. Without this term an
            // agent-less result that ties on script and sync would win purely on provider order
            // and the song would silently render flush-left with no way to tell why.
            val duetScore = if (LyricsUtils.hasVoiceTags(c.lyrics)) 1 else 0

            scriptScore * ScriptWeight +
                syncScore * SyncWeight +
                duetScore * DuetWeight +
                (providerCount - c.order)
        }!!
    }

    private fun isNative(script: LyricsUtils.Script) =
        script != LyricsUtils.Script.LATIN && script != LyricsUtils.Script.UNKNOWN

    /** Kanji-only Japanese titles read as Han; Japanese lyrics read as kana. Same song either way. */
    private fun sameLanguageFamily(a: LyricsUtils.Script, b: LyricsUtils.Script): Boolean {
        val cjk = setOf(LyricsUtils.Script.HAN, LyricsUtils.Script.KANA)
        return a in cjk && b in cjk
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {

            true
        }

        if (!isNetworkAvailable) {

            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {

                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3

        /** One slow provider must not hold the whole lookup hostage. */
        private const val ProviderTimeoutMs = 15_000L

        /**
         * Providers whose answer comes from this exact video rather than a title search, so their
         * language is the song's language.
         */
        private val LanguageReferenceProviders = setOf("YouTubeMusic", "YouTubeSubtitle")

        // Spread far enough apart that a script match always outranks any amount of sync
        // quality, and sync quality always outranks provider order.
        private const val DuetWeight = 100
        private const val SyncWeight = 1_000
        private const val ScriptWeight = 10_000
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
