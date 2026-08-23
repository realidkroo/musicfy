// YTPlayerUtils.kt

package com.example.musicfy.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.example.musicfy.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.utils.cipher.CipherDeobfuscator
import com.example.musicfy.utils.YTPlayerUtils.MAIN_CLIENT
import com.example.musicfy.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.example.musicfy.utils.YTPlayerUtils.validateStatus
import com.example.musicfy.utils.potoken.PoTokenGenerator
import com.example.musicfy.utils.potoken.PoTokenResult
import com.example.musicfy.utils.sabr.EjsNTransformSolver
import com.example.musicfy.utils.PlaybackLogLevel
import com.example.musicfy.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(ioe, "Proxy connection failed for URI: $uri")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /** True once the BotGuard engine holds a usable session token. Surfaced in diagnostics. */
    val hasPoToken: Boolean
        get() = poTokenGenerator.hasSessionToken

    /**
     * Boot the BotGuard engine before the first track needs it. Safe to call more than once.
     */
    suspend fun preWarmPoToken() {
        val sessionId = (if (YouTube.cookie != null) YouTube.dataSyncId else null)
            ?: YouTube.visitorData
            ?: return
        poTokenGenerator.preWarm(sessionId)
    }

    /**
     * WEB_REMIX is the only client that yields a *complete* stream, because it is the one
     * that carries a PoToken (useWebPoTokens = true), which gets appended to the stream url
     * as `pot=`. Measured against googlevideo: an untokened url serves roughly the first
     * 786 KB and answers 403 for any range starting past that - about 32 seconds of audio.
     * IOS returns tidy non-ciphered urls and looks like it works, which is exactly the trap;
     * it has no PoToken so it is only useful as a partial-playback fallback.
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Ordered so the clients that can carry a stream PoToken come first - they are the only
     * ones that can serve a whole track for music content. The rest follow because they still
     * work for ordinary videos, but any stream they produce is validated for truncation before
     * it is accepted, so a crippled url can never be handed to the player.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        WEB_REMIX,
        WEB,
        WEB_CREATOR,
        IPADOS,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_VR_1_65_10,
        ANDROID_VR_NO_AUTH,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_CREATOR,
        MOBILE,
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> {
        val firstAttempt = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)

        if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager)
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return retryResult
        }

        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return firstAttempt
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")

        println("[PLAYBACK_DEBUG] playerResponseForPlayback called: videoId=$videoId, playlistId=$playlistId")

        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.poTokenFor(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
        }

        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        var metadataResponse: PlayerResponse? = null
        if (isLoggedIn) {
            Timber.tag(logTag).d("Fetching metadata from METADATA_CLIENT (WEB_REMIX) for authenticated tracking")
            try {

                var metaPoToken: PoTokenResult? = null
                val metaSessionId = YouTube.dataSyncId
                if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                    try {
                        metaPoToken = poTokenGenerator.poTokenFor(videoId, metaSessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Metadata PoToken generation failed")
                    }
                }
                metadataResponse = YouTube.player(
                    videoId, playlistId, METADATA_CLIENT,
                    signatureTimestamp.timestamp, metaPoToken?.playerRequestPoToken
                ).getOrNull()
                Timber.tag(logTag).d("Metadata response obtained: ${metadataResponse?.playabilityStatus?.status}")
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "Failed to fetch metadata from METADATA_CLIENT")
            }
        }

        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        if (mainPlayerResponse == null) {
            throw Exception("Failed to get player response")
        }

        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        val truncatedClients = mutableListOf<String>()
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        val currentStatus = mainPlayerResponse.playabilityStatus.status
        var isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Log.i(TAG, "Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        val startIndex = when {
            isPrivateTrack -> 1
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {

            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {

                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {

                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {

                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    try {
                        poToken = poTokenGenerator.poTokenFor(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null

                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName)

                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                val responseToUse = streamPlayerResponse

                val clientLabel =
                    if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName

                val formatCandidates = selectAudioFormatCandidates(
                    responseToUse,
                    audioQuality,
                    connectivityManager.isActiveNetworkMetered,
                )

                if (formatCandidates.isEmpty()) {
                    Timber.tag(logTag).d("No suitable format found for client: $clientLabel")
                    continue
                }

                // Walk the candidates so a format we cannot deobfuscate falls back to the
                // next best one instead of throwing away an otherwise healthy client.
                for (candidate in formatCandidates) {
                    val candidateUrl =
                        findUrlOrNull(candidate, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                    if (candidateUrl != null) {
                        format = candidate
                        streamUrl = candidateUrl
                        break
                    }
                    Timber.tag(logTag).d("Could not resolve URL for ${candidate.mimeType}@${candidate.bitrate}, trying next candidate")
                }

                if (format == null || streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for any candidate format on client: $clientLabel")
                    continue
                }

                Timber.tag(logTag).d("Format selected: ${format.mimeType}, bitrate: ${format.bitrate} ($clientLabel)")

                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (currentClient.useWebPoTokens) {
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                // Privately owned uploads are not subject to the PoToken window and sometimes
                // reject probe requests, so they stay exempt. Every other stream is validated,
                // including the last candidate - accepting it blindly is how a truncated url
                // used to reach the player.
                if (isPrivatelyOwned) {
                    Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId, private=true")
                    break
                }

                val validation = validateStatus(streamUrl!!, format.contentLength)
                if (validation == StreamValidation.OK) {

                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    PlaybackLogManager.log(
                        PlaybackLogLevel.INFO,
                        "Stream validated",
                        "client=${currentClient.clientName} itag=${format.itag} " +
                            "pot=${poToken?.streamingDataPoToken?.length ?: 0} " +
                            "bytes=${format.contentLength ?: 0}",
                    )

                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")
                    if (validation == StreamValidation.TRUNCATED) {
                        truncatedClients += currentClient.clientName
                    }

                    if (currentClient.useWebPoTokens) {
                        var nTransformWorked = false

                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            if (nTransformed != streamUrl) {
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(nTransformed, format.contentLength) == StreamValidation.OK) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                        }

                        if (nTransformWorked) break
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")

                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            if (truncatedClients.isNotEmpty()) {
                Timber.tag(logTag).e("All streams truncated: ${truncatedClients.joinToString()}")
                PlaybackLogManager.log(
                    PlaybackLogLevel.ERROR,
                    "All streams truncated",
                    "clients=${truncatedClients.joinToString()} poToken=${poToken != null}",
                )
                throw TruncatedStreamException(truncatedClients.toList(), poToken != null)
            }
            Timber.tag(logTag).e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")

        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }

    private val VIDEO_BACKGROUND_CLIENTS: Array<YouTubeClient> = arrayOf(
        MAIN_CLIENT,
        WEB_REMIX,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_VR_1_61_48,
        WEB,
    )

    suspend fun resolveVideoStreamUrl(videoId: String): Result<String> = runCatching {
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        for (client in VIDEO_BACKGROUND_CLIENTS) {
            val response = try {
                YouTube.player(videoId, null, client, signatureTimestamp.timestamp, null).getOrNull()
            } catch (e: Exception) {
                Timber.tag(logTag).d("resolveVideoStreamUrl: client ${client.clientName} threw: ${e.message}")
                null
            } ?: continue

            if (response.playabilityStatus.status != "OK") {
                Timber.tag(logTag).d("resolveVideoStreamUrl: client ${client.clientName} not OK: ${response.playabilityStatus.status}")
                continue
            }

            val format = response.streamingData?.adaptiveFormats
                ?.filter { !it.isAudio && it.height != null }
                ?.minByOrNull { kotlin.math.abs((it.height ?: 0) - 720) }
                ?: response.streamingData?.formats?.firstOrNull { !it.isAudio }

            if (format == null) {
                Timber.tag(logTag).d("resolveVideoStreamUrl: client ${client.clientName} had no video format")
                continue
            }

            val url = try {
                findUrlOrNull(format, videoId, response, skipNewPipe = false)
            } catch (e: Exception) {
                Timber.tag(logTag).d("resolveVideoStreamUrl: client ${client.clientName} URL resolution threw: ${e.message}")
                null
            }

            if (url != null) {
                Timber.tag(logTag).d("resolveVideoStreamUrl: resolved via ${client.clientName}")
                return@runCatching url
            }
        }

        throw Exception("Could not resolve a playable video stream for videoId=$videoId after trying ${VIDEO_BACKGROUND_CLIENTS.size} clients")
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? =
        selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            connectivityManager.isActiveNetworkMetered,
        ).firstOrNull()

    /**
     * Returns every usable audio format ordered best-first for [audioQuality].
     *
     * Callers walk this list so that a format whose URL cannot be resolved (ciphered
     * without a working deobfuscator, for example) falls back to the next best one
     * instead of discarding the whole client.
     */
    internal fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.bitrate > 0 }
            ?.filter { !it.url.isNullOrEmpty() || !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }
            .orEmpty()

        if (audioFormats.isEmpty()) {
            Timber.tag(logTag).d("No suitable audio format found")
            return emptyList()
        }

        // Prefer the original (non auto-dubbed) track when the response offers both.
        val originalFormats = audioFormats.filter { it.isOriginal }
        val usableFormats = originalFormats.ifEmpty { audioFormats }

        val effectiveQuality = when (audioQuality) {
            AudioQuality.AUTO -> if (networkMetered) AudioQuality.MEDIUM else AudioQuality.HIGH
            // Lossless and Atmos tiers are served by the Monochrome backend; when
            // YouTube is the source we still want its best available stream.
            AudioQuality.LOSSLESS, AudioQuality.HI_RES_LOSSLESS, AudioQuality.DOLBY_ATMOS -> AudioQuality.HIGH
            else -> audioQuality
        }

        // YouTube's audio ladder is roughly: ~50k (itag 139/249), ~70k (250),
        // ~128k AAC (140), ~160k Opus (251). The MEDIUM ceiling sits just under the
        // Opus rung so it lands on 128k AAC while HIGH keeps the best stream.
        val targetBitrateBps = when (effectiveQuality) {
            AudioQuality.LOW -> 70_000
            AudioQuality.MEDIUM -> 135_000
            else -> null // HIGH: take the best available
        }

        val preferHigher = compareByDescending<PlayerResponse.StreamingData.Format> { !it.url.isNullOrEmpty() }
            .thenByDescending { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget = compareByDescending<PlayerResponse.StreamingData.Format> { !it.url.isNullOrEmpty() }
            .thenBy { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates = if (targetBitrateBps == null) {
            usableFormats.sortedWith(preferHigher)
        } else {
            // Best stream at or below the target, then the cheapest one above it.
            usableFormats.filter { it.bitrate <= targetBitrateBps }.sortedWith(preferHigher) +
                usableFormats.filter { it.bitrate > targetBitrateBps }.sortedWith(preferLowerAboveTarget)
        }

        Timber.tag(logTag).d(
            "Format candidates ($effectiveQuality): " + candidates.take(8).joinToString {
                "${it.mimeType}@${it.bitrate}${if (it.url.isNullOrEmpty()) " (cipher)" else ""}"
            }
        )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? =
        Regex("""codecs="([^"]+)"""").find(mimeType)
            ?.groupValues?.getOrNull(1)
            ?.split(",")?.firstOrNull()?.trim()

    private fun codecRank(codec: String?): Int = when {
        codec.isNullOrBlank() -> 0
        codec.contains("opus", ignoreCase = true) -> 3
        codec.contains("mp4a", ignoreCase = true) -> 2
        else -> 1
    }

    /**
     * Checks a resolved stream URL is actually fetchable.
     *
     * Uses a one-byte ranged GET rather than HEAD: googlevideo answers HEAD with 403
     * on URLs issued by the iOS/iPadOS clients even when the URL is perfectly good,
     * which previously caused every working stream to be thrown away.
     */
    /**
     * Largest offset an *untokened* stream url will serve. Measured against googlevideo:
     * `bytes=0-786431` returns 206, `bytes=0-1048575` returns 403, and any range starting past
     * the window returns 403 even on a freshly issued url. At 128 kbps that window is about
     * 32 seconds of audio, which is what a crippled url sounds like.
     */
    private const val UNTOKENED_WINDOW_BYTES = 786_432L

    /** Offset probed to prove a url will serve beyond the untokened window. */
    private const val TRUNCATION_PROBE_OFFSET = 1_048_576L

    private fun rangeStatus(url: String, range: String): Int? {
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .get()
                .url(url)
                .header("Range", range)
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            httpClient.newCall(requestBuilder.build()).execute().use {
                return it.code
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return null
    }

    /**
     * A url is only usable if it serves the *whole* track. Probing byte 0 is not enough:
     * an untokened url answers that happily and then 403s the moment playback crosses
     * [UNTOKENED_WINDOW_BYTES], which is exactly how a missing PoToken has repeatedly
     * disguised itself as a playback bug. When we know the track is longer than the window,
     * probe past it as well.
     */
    /** Why a candidate stream url was accepted or turned down. */
    internal enum class StreamValidation { OK, REJECTED, TRUNCATED }

    /**
     * Thrown when every client produced a url that only serves YouTube's untokened preview
     * window. Playing it would give the listener about 32 seconds and look like a broken app,
     * so we fail instead and say why.
     */
    class TruncatedStreamException(
        val clients: List<String>,
        val hadPoToken: Boolean,
    ) : Exception(
        if (hadPoToken) {
            "YouTube rejected this device's PoToken, so it will only serve about 32 seconds per track."
        } else {
            "No PoToken could be generated, so YouTube will only serve about 32 seconds per track."
        },
    )

    private fun validateStatus(url: String, contentLength: Long? = null): StreamValidation {
        Timber.tag(logTag).d("Validating stream URL status")
        val headCode = rangeStatus(url, "bytes=0-0") ?: return StreamValidation.REJECTED
        if (headCode != 200 && headCode != 206) {
            Timber.tag(logTag).d("Stream URL validation result: Failed ($headCode)")
            return StreamValidation.REJECTED
        }

        if (contentLength == null || contentLength <= UNTOKENED_WINDOW_BYTES) {
            // Short enough to fit inside the window, so there is nothing past it to prove.
            Timber.tag(logTag).d("Stream URL validation result: Success ($headCode)")
            return StreamValidation.OK
        }

        val probeOffset = minOf(TRUNCATION_PROBE_OFFSET, contentLength - 1)
        val probeCode = rangeStatus(url, "bytes=$probeOffset-$probeOffset")
        // A null code means the probe itself failed (network, timeout). That is not evidence
        // of truncation, and rejecting on it would drop good streams on a flaky connection.
        val servesFullTrack = probeCode == null || probeCode == 200 || probeCode == 206
        if (!servesFullTrack) {
            Timber.tag(logTag).w("Stream is truncated - offset $probeOffset returned $probeCode (no usable PoToken)")
            PlaybackLogManager.log(
                PlaybackLogLevel.WARNING,
                "Truncated stream rejected",
                "offset=$probeOffset code=$probeCode",
            )
            return StreamValidation.TRUNCATED
        }
        return StreamValidation.OK
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
