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
     *  1. **Original script.** A Latin result sitting next to a non-Latin one is a translation or
     *     a romanisation of it. Neither "matches the song title's script" nor "whatever most
     *     providers returned" works here — both were tried and both pick the English release of a
     *     Japanese song, because a popular Japanese track has more English renderings available
     *     than Japanese ones. See the comment at [hasNativeScript].
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

        data class Candidate(
            val providerName: String,
            val lyrics: String,
            val order: Int,
            val sync: LyricsUtils.SyncKind,
            val script: LyricsUtils.Script,
        )

        val candidates = ArrayList<Candidate>(providers.size)
        for ((order, provider) in providers.withIndex()) {
            if (!provider.isEnabled(context)) continue
            val lyrics = try {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration,
                    mediaMetadata.album?.title,
                ).onFailure { reportException(it) }.getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                null
            } ?: continue

            if (lyrics.isBlank() || lyrics == LYRICS_NOT_FOUND) continue
            val sync = LyricsUtils.syncKind(lyrics)
            if (sync == LyricsUtils.SyncKind.NONE) continue

            candidates += Candidate(
                providerName = provider.name,
                lyrics = lyrics,
                order = order,
                sync = sync,
                script = LyricsUtils.lyricsScript(lyrics),
            )
        }

        if (candidates.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        // Prefer the original script over a Latin one whenever both come back.
        //
        // Verified against the live APIs rather than assumed. Asking YouLyPlus for YOASOBI's
        // "Idol" returns Apple Music's official ENGLISH release (metadata.language == "en"), and
        // LrcLib returns an English translation too — while QQ Music returns the actual Japanese.
        // So the English versions are the majority, which is why counting votes picks the wrong
        // one: a popular Japanese song has *more* English renderings available than Japanese ones.
        //
        // The asymmetry that does hold: these APIs serve original lyrics plus, sometimes, English
        // translations and romanisations. They do not translate English songs into Japanese or
        // Korean. So a non-Latin candidate is essentially always the original, and a Latin one
        // alongside it is a translation or a transliteration — both of which are derivatives the
        // user did not ask for.
        val hasNativeScript = candidates.any {
            it.script != LyricsUtils.Script.LATIN && it.script != LyricsUtils.Script.UNKNOWN
        }

        val best = candidates.maxByOrNull { c ->
            val scriptScore = when {
                // Everything came back Latin — nothing to prefer, stay neutral.
                !hasNativeScript -> 1
                c.script == LyricsUtils.Script.UNKNOWN -> 1
                c.script != LyricsUtils.Script.LATIN -> 2
                else -> 0
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
                (providers.size - c.order)
        }!!

        val result = LyricsWithProvider(best.lyrics, best.providerName)
        bestCache.put(mediaMetadata.id, result)
        return result
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
