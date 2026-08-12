// lyricshelperkt
// this thing is part of lyrics helper

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    // resolves the ordered list of lyrics providers from the user's saved priority
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        // migration path: place the old preferred provider first in the default order
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        // check network connectivity before making network requests
        // use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // if network check fails try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // still proceed but return not found to avoid hanging
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val providers = resolveLyricsProviders()
        val scope = CoroutineScope(SupervisorJob())
        val deferred = scope.async {
            var fallbackLyrics: LyricsWithProvider? = null

            for (provider in providers) {
                if (provider.isEnabled(context)) {
                    try {
                        val result = provider.getLyrics(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                        result.onSuccess { lyrics ->
                            // check if these lyrics have moving text patterns (eg word-by-word sync
                            val hasMovingLyrics = lyrics.contains("<") && lyrics.contains(">") && lyrics.contains(":")
                            val currentLyricsWithProvider = LyricsWithProvider(lyrics, provider.name)

                            // word-timed lyrics are only worth preferring if the words are
                            // actually words some providers time each syllable separately and
                            // space them apart which renders as "se men ta ra" those are passed
                            // over so the next provider gets a chance — but still kept as a
                            // last-resort fallback since split lyrics beat no lyrics
                            if (hasMovingLyrics && !LyricsUtils.isSyllableSplit(lyrics)) {
                                return@async currentLyricsWithProvider
                            } else if (fallbackLyrics == null) {
                                // save the first successful (but non-moving) lyrics as fallback
                                fallbackLyrics = currentLyricsWithProvider
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    } catch (e: Exception) {
                        // catch network-related exceptions like unresolvedaddressexception
                        reportException(e)
                    }
                }
            }
            return@async fallbackLyrics ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val result = deferred.await()
        scope.cancel()
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

        // check network connectivity before making network requests
        // use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // if network check fails try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // still try to proceed in case of false negative
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
                        // catch network-related exceptions like unresolvedaddressexception
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