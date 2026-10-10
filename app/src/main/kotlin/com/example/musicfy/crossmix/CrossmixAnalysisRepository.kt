package com.example.musicfy.crossmix

import android.content.Context
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Analyses tracks for Crossmix and keeps the results on disk.
 *
 * The audio is read through the player's own data source: a YouTube song's media item only holds
 * its video id, which the player's resolving source turns into a stream URL (and serves from the
 * cache when it can). The old decoder handed that id straight to MediaExtractor, which can't open
 * it, so every streamed song came back unanalysed. Reading the next song this way also leaves its
 * opening in the player's cache, so it starts instantly.
 *
 * A failed decode is not cached: a stream URL that expired now may well work on the next try.
 */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class CrossmixAnalysisRepository(
    private val context: Context,
    private val dataSourceFactory: () -> DataSource.Factory,
) {
    private val memory = ConcurrentHashMap<String, TrackAnalysis>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysis?>>()
    private val cacheDir = File(context.cacheDir, "crossmix-analysis-v2").also { it.mkdirs() }

    // one quiet thread: analysis must never compete with decoding the song that's playing
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "crossmix-analysis").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * [wholeTrack]: the song that's playing, whose ending is mixed out of. otherwise only the
     * first [HeadMs] are read: the next song is only ever entered in its first two minutes.
     */
    suspend fun analyze(item: MediaItem, wholeTrack: Boolean): TrackAnalysis? {
        val uri = item.localConfiguration?.uri ?: return null
        val mediaId = item.mediaId.ifBlank { uri.toString() }
        usable(memory[mediaId], wholeTrack)?.let { return it }

        val key = "$mediaId|$wholeTrack"
        // started only once it's in the map, so a quick finish can't remove itself before it's
        // added and leave a stale (possibly failed) result behind for every later caller
        val job = inFlight.getOrPut(key) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    usable(read(mediaId), wholeTrack)?.also { memory[mediaId] = it }
                        ?: withTimeoutOrNull(if (wholeTrack) 60_000L else 40_000L) {
                            decodeAndAnalyze(uri, mediaId, wholeTrack)
                        }?.also { analysis ->
                            // a whole-track result answers head requests too; never overwrite it with less
                            if (memory[mediaId]?.complete != true || analysis.complete) {
                                memory[mediaId] = analysis
                                write(mediaId, analysis)
                            }
                        }
                } catch (_: Throwable) {
                    null
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        job.start()
        return job.await()
    }

    /** what's already known, without decoding anything */
    fun cached(mediaId: String): TrackAnalysis? = memory[mediaId]

    private fun usable(analysis: TrackAnalysis?, wholeTrack: Boolean): TrackAnalysis? =
        analysis?.takeIf { !wholeTrack || it.complete }

    private suspend fun decodeAndAnalyze(uri: Uri, mediaId: String, wholeTrack: Boolean): TrackAnalysis? {
        val analyzer = StreamingTrackAnalyzer()
        val limitUs = if (wholeTrack) MaxTrackMs * 1_000L else HeadMs * 1_000L
        val source = Media3MediaDataSource(dataSourceFactory(), uri, mediaId)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            var sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
            val resampler = MonoResampler()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var reachedEnd = false
            var lastPresentationUs = 0L
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TimeoutUs)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input != null) extractor.readSampleData(input, 0) else -1
                        if (size < 0 || extractor.sampleTime > limitUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            if (size < 0) reachedEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, TimeoutUs)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        val encoding = out.intOr(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                        if (encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) return null
                    }
                    outputIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 && sampleRate > 0 && channels > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            resampler.push(buffer.order(ByteOrder.nativeOrder()).asShortBuffer(), channels, sampleRate, analyzer)
                            lastPresentationUs = info.presentationTimeUs
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            val complete = reachedEnd || lastPresentationUs >= MaxTrackMs * 1_000L
            return analyzer.finish(complete = wholeTrack && complete)
        } finally {
            codec?.runCatching { stop() }
            codec?.runCatching { release() }
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
    }

    private fun read(mediaId: String): TrackAnalysis? = runCatching {
        val file = cacheFile(mediaId)
        if (!file.exists()) return null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != CacheVersion) return null
            TrackAnalysis(
                analyzedMs = input.readLong(),
                complete = input.readBoolean(),
                bpm = input.readFloat().takeUnless { it.isNaN() },
                bpmConfidence = input.readFloat(),
                beatsMs = input.readLongArray(),
                downbeatIndex = input.readInt(),
                key = input.readUTF().ifBlank { null },
                keyConfidence = input.readFloat(),
                leadingSilenceEndMs = input.readLong(),
                trailingSilenceStartMs = input.readLong(),
                energy = input.readFloatArray(),
                bass = input.readFloatArray(),
                brightness = input.readFloatArray(),
                vibe = Vibe(
                    energy = input.readFloat(),
                    danceability = input.readFloat(),
                    brightness = input.readFloat(),
                    bassWeight = input.readFloat(),
                    minor = input.readBoolean(),
                ),
            )
        }
    }.getOrNull()

    private fun write(mediaId: String, analysis: TrackAnalysis) = runCatching {
        val target = cacheFile(mediaId)
        val temp = File(target.parentFile, target.name + ".tmp")
        DataOutputStream(BufferedOutputStream(temp.outputStream())).use { out ->
            out.writeInt(CacheVersion)
            out.writeLong(analysis.analyzedMs)
            out.writeBoolean(analysis.complete)
            out.writeFloat(analysis.bpm ?: Float.NaN)
            out.writeFloat(analysis.bpmConfidence)
            out.writeLongArray(analysis.beatsMs)
            out.writeInt(analysis.downbeatIndex)
            out.writeUTF(analysis.key.orEmpty())
            out.writeFloat(analysis.keyConfidence)
            out.writeLong(analysis.leadingSilenceEndMs)
            out.writeLong(analysis.trailingSilenceStartMs)
            out.writeFloatArray(analysis.energy)
            out.writeFloatArray(analysis.bass)
            out.writeFloatArray(analysis.brightness)
            out.writeFloat(analysis.vibe.energy)
            out.writeFloat(analysis.vibe.danceability)
            out.writeFloat(analysis.vibe.brightness)
            out.writeFloat(analysis.vibe.bassWeight)
            out.writeBoolean(analysis.vibe.minor)
        }
        // written whole, then swapped in: a crash mid-write can't leave a half file to misread
        temp.renameTo(target)
    }

    private fun cacheFile(mediaId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(mediaId.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) })
    }

    private fun DataInputStream.readLongArray(): LongArray = LongArray(readInt().coerceIn(0, MaxCachedArray)) { readLong() }
    private fun DataInputStream.readFloatArray(): FloatArray = FloatArray(readInt().coerceIn(0, MaxCachedArray)) { readFloat() }

    private fun DataOutputStream.writeLongArray(values: LongArray) {
        writeInt(values.size)
        values.forEach(::writeLong)
    }

    private fun DataOutputStream.writeFloatArray(values: FloatArray) {
        writeInt(values.size)
        values.forEach(::writeFloat)
    }

    private fun MediaFormat.intOr(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default

    private companion object {
        const val CacheVersion = 2
        const val MaxCachedArray = 200_000
        const val TimeoutUs = 10_000L
        /** the next song: two minutes of possible entry points plus room for the blend after one */
        const val HeadMs = 150_000L
        const val MaxTrackMs = 15 * 60_000L
    }
}

/** downmixes PCM 16 to mono and resamples it to the analyser's rate, carrying phase between calls */
private class MonoResampler {
    private val out = FloatArray(8_192)
    private var count = 0
    private var position = 0.0
    private var previous = 0f

    fun push(pcm: java.nio.ShortBuffer, channels: Int, inputRate: Int, analyzer: StreamingTrackAnalyzer) {
        val step = inputRate.toDouble() / StreamingTrackAnalyzer.SampleRate
        val frames = pcm.remaining() / channels
        for (frame in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) sum += pcm.get() / 32_768f
            val current = sum / channels
            // emit every output sample that falls between the previous input sample and this one
            while (position <= 1.0) {
                out[count++] = previous + (current - previous) * position.toFloat()
                if (count == out.size) { analyzer.feed(out, count); count = 0 }
                position += step
            }
            position -= 1.0
            previous = current
        }
        if (count > 0) { analyzer.feed(out, count); count = 0 }
    }
}

/**
 * MediaExtractor reading through a Media3 data source: the player's resolving, caching source,
 * so a YouTube id becomes its stream and cached bytes are never fetched twice.
 */
@UnstableApi
private class Media3MediaDataSource(
    private val factory: DataSource.Factory,
    private val uri: Uri,
    private val key: String,
) : MediaDataSource() {
    private var source: DataSource? = null
    private var position = 0L
    private var length = C.LENGTH_UNSET.toLong()

    override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, size: Int): Int {
        if (size == 0) return 0
        if (length != C.LENGTH_UNSET.toLong() && offset >= length) return -1
        if (source == null || offset != position) reopen(offset)
        val current = source ?: return -1
        var total = 0
        while (total < size) {
            val read = current.read(buffer, bufferOffset + total, size - total)
            if (read == C.RESULT_END_OF_INPUT) break
            total += read
            position += read
        }
        return if (total == 0) -1 else total
    }

    override fun getSize(): Long {
        if (length == C.LENGTH_UNSET.toLong() && source == null) runCatching { reopen(0L) }
        return if (length == C.LENGTH_UNSET.toLong()) -1L else length
    }

    override fun close() {
        runCatching { source?.close() }
        source = null
    }

    private fun reopen(offset: Long) {
        close()
        val next = factory.createDataSource()
        val spec = DataSpec.Builder().setUri(uri).setKey(key).setPosition(offset).build()
        val opened = next.open(spec)
        if (opened != C.LENGTH_UNSET.toLong() && length == C.LENGTH_UNSET.toLong()) length = offset + opened
        source = next
        position = offset
    }
}
