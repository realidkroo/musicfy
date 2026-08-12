package com.example.musicfy.playback.custom

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import android.util.Base64
import androidx.media3.common.MimeTypes
import java.io.IOException

@UnstableApi
class DynamicResolvingMediaSource(
    private val originalMediaItem: MediaItem,
    private val mediaSourceFactoryProvider: (androidx.media3.exoplayer.drm.DrmSessionManager?) -> MediaSource.Factory,
    private val fetcherAction: suspend (String) -> CustomStreamResult?,
    private val onSourceResolved: (mediaId: String, source: String) -> Unit = { _, _ -> }
) : CompositeMediaSource<Void>() {

    private var innerMediaSource: MediaSource? = null
    private var timeline: Timeline? = null
    private var fetchError: IOException? = null
    private val fetchScope = CoroutineScope(Dispatchers.IO + Job())

    override fun prepareSourceInternal(mediaTransferListener: androidx.media3.datasource.TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        
        fetchScope.launch {
            val mediaId = originalMediaItem.mediaId
            val result = try {
                fetcherAction(mediaId)
            } catch (e: androidx.media3.common.PlaybackException) {
                // if a forced backend throws playbackexception propagate it so exoplayer
                // instead of silently falling back to youtube music
                fetchError = java.io.IOException(e)
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch stream for mediaId: $mediaId")
                fetchError = if (e is java.io.IOException) e else java.io.IOException(e)
                null
            }

            onSourceResolved(mediaId, result?.source ?: "YouTube Music")

            if (result == null) {
                if (fetchError == null) {
                    // fetcheraction declined to handle this item without erroring (eg custom
                    // backends disabled) — fall back to playing the original item (youtube)
                    // normally instead of leaving the source unprepared forever
                    withContext(Dispatchers.Main) {
                        val source = mediaSourceFactoryProvider(null).createMediaSource(originalMediaItem)
                        innerMediaSource = source
                        prepareChildSource(null, source)
                    }
                }
                // if fetcherror is set don't prepare — exoplayer will poll
                // maybethrowsourceinforefresherror() and correctly propagate the error
                return@launch
            }

            val builder = originalMediaItem.buildUpon().setUri(Uri.parse(result.streamUrl))
            
            if (result.isDash || result.streamUrl.endsWith(".mpd")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            if (!result.decryptionKey.isNullOrBlank()) {
                // setup clearkey drm
                val drmCallback = object : MediaDrmCallback {
                    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray {
                        return ByteArray(0)
                    }

                    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
                        try {
                            val requestStr = String(request.data, Charsets.UTF_8)
                            val requestJson = JSONObject(requestStr)
                            val kids = requestJson.getJSONArray("kids")
                            val kid = kids.getString(0)

                            val keyBytes = hexStringToByteArray(result.decryptionKey)
                            val kBase64Url = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                            val responseJson = JSONObject().apply {
                                put("keys", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("kty", "oct")
                                        put("k", kBase64Url)
                                        put("kid", kid)
                                    })
                                })
                                put("type", "temporary")
                            }
                            return responseJson.toString().toByteArray(Charsets.UTF_8)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to generate ClearKey response")
                            throw RuntimeException(e)
                        }
                    }
                }

                val drmManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)

                val localFactory = mediaSourceFactoryProvider(drmManager)
                
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build()
                )
                
                val finalMediaItem = builder.build()
                withContext(Dispatchers.Main) {
                    val source = localFactory.createMediaSource(finalMediaItem)
                    innerMediaSource = source
                    prepareChildSource(null, source)
                }
            } else {
                val finalMediaItem = builder.build()
                withContext(Dispatchers.Main) {
                    val source = mediaSourceFactoryProvider(null).createMediaSource(finalMediaItem)
                    innerMediaSource = source
                    prepareChildSource(null, source)
                }
            }
        }
    }

    private suspend fun createAndPrepareInnerSource(mediaItem: MediaItem, mediaTransferListener: androidx.media3.datasource.TransferListener?) {
        withContext(Dispatchers.Main) {
            val source = mediaSourceFactoryProvider(null).createMediaSource(mediaItem)
            innerMediaSource = source
            prepareChildSource(null, source)
        }
    }

    override fun getMediaItem(): MediaItem = originalMediaItem

    override fun maybeThrowSourceInfoRefreshError() {
        super.maybeThrowSourceInfoRefreshError()
        fetchError?.let { throw it }
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        if (innerMediaSource == null) {
            throw IllegalStateException("createPeriod called before innerMediaSource was prepared")
        }
        return innerMediaSource!!.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        innerMediaSource?.releasePeriod(mediaPeriod)
    }

    override fun onChildSourceInfoRefreshed(id: Void?, mediaSource: MediaSource, timeline: Timeline) {
        this.timeline = timeline
        refreshSourceInfo(timeline)
    }

    override fun releaseSourceInternal() {
        super.releaseSourceInternal()
        fetchScope.cancel()
        innerMediaSource = null
        timeline = null
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
