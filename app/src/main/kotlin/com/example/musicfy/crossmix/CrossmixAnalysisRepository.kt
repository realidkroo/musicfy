package com.example.musicfy.crossmix

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk-backed analysis cache. A failed decode is intentionally not cached: playback URLs can
 * expire, while a later request for the same media id may provide a usable source.
 */
class CrossmixAnalysisRepository(private val context: Context) {
    private val memory = ConcurrentHashMap<String, TrackAnalysis>()
    private val cacheDir = File(context.cacheDir, "crossmix-analysis").also { it.mkdirs() }

    suspend fun analyze(item: MediaItem): TrackAnalysis? {
        val uri = item.localConfiguration?.uri ?: return null
        val mediaId = item.mediaId.ifBlank { uri.toString() }
        memory[mediaId]?.let { return it }
        return withContext(Dispatchers.IO) {
            read(mediaId)?.also { memory[mediaId] = it } ?: run {
                val pcm = MediaCodecTrackDecoder.decode(context, uri) ?: return@run null
                val analysis = CrossmixAudioAnalyzer.analyzePcm16(pcm.bytes, pcm.sampleRate, pcm.channelCount)
                    ?: return@run null
                write(mediaId, analysis)
                memory[mediaId] = analysis
                analysis
            }
        }
    }

    private fun read(mediaId: String): TrackAnalysis? = runCatching {
        DataInputStream(BufferedInputStream(cacheFile(mediaId).inputStream())).use { input ->
            if (input.readInt() != CacheVersion) return null
            TrackAnalysis(
                bpm = input.readFloat().takeUnless { it.isNaN() },
                beatGridMs = input.readLongArray(),
                key = input.readUTF().ifBlank { null },
                keyConfidence = input.readFloat(),
                onsetMs = input.readLongArray(),
                leadingSilenceEndMs = input.readLong(),
                trailingSilenceStartMs = input.readLong(),
                quietPointsMs = input.readLongArray(),
            )
        }
    }.getOrNull()

    private fun write(mediaId: String, analysis: TrackAnalysis) = runCatching {
        DataOutputStream(BufferedOutputStream(cacheFile(mediaId).outputStream())).use { output ->
            output.writeInt(CacheVersion)
            output.writeFloat(analysis.bpm ?: Float.NaN)
            output.writeLongArray(analysis.beatGridMs)
            output.writeUTF(analysis.key.orEmpty())
            output.writeFloat(analysis.keyConfidence)
            output.writeLongArray(analysis.onsetMs)
            output.writeLong(analysis.leadingSilenceEndMs)
            output.writeLong(analysis.trailingSilenceStartMs)
            output.writeLongArray(analysis.quietPointsMs)
        }
    }

    private fun cacheFile(mediaId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(mediaId.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) })
    }

    private fun DataInputStream.readLongArray(): LongArray = LongArray(readInt().coerceIn(0, MaxCachedArray)) { readLong() }

    private fun DataOutputStream.writeLongArray(values: LongArray) {
        writeInt(values.size)
        values.forEach(::writeLong)
    }

    private companion object {
        const val CacheVersion = 1
        const val MaxCachedArray = 100_000
    }
}

private data class DecodedPcm(val bytes: ByteArray, val sampleRate: Int, val channelCount: Int)

/** Decodes an Android-supported local or HTTP media URI to PCM 16-bit for offline analysis. */
private object MediaCodecTrackDecoder {
    private const val TimeoutUs = 10_000L
    private const val MaxPcmBytes = 48 * 1024 * 1024

    fun decode(context: Context, uri: Uri): DecodedPcm? = runCatching {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, emptyMap())
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime).apply { configure(inputFormat, null, null, 0); start() }

            var outputRate = inputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
            var outputChannels = inputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
            val output = ByteAccumulator()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded && output.size < MaxPcmBytes) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TimeoutUs)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TimeoutUs)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        outputRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, outputRate)
                        outputChannels = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, outputChannels)
                        if (format.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT) != android.media.AudioFormat.ENCODING_PCM_16BIT) return null
                    }
                    in 0..Int.MAX_VALUE -> {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (bufferInfo.size > 0 && buffer != null) {
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            output.append(buffer, MaxPcmBytes)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            output.takeIf { outputRate > 0 && outputChannels > 0 && it.size > 0 }
                ?.let { DecodedPcm(it.toByteArray(), outputRate, outputChannels) }
        } finally {
            codec?.runCatching { stop() }
            codec?.release()
            extractor.release()
        }
    }.getOrNull()

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default

    private class ByteAccumulator {
        private var bytes = ByteArray(32_768)
        var size = 0
            private set

        fun append(buffer: java.nio.ByteBuffer, maximumSize: Int) {
            val count = minOf(buffer.remaining(), maximumSize - size)
            ensure(size + count)
            buffer.get(bytes, size, count)
            size += count
        }

        fun toByteArray(): ByteArray = bytes.copyOf(size)

        private fun ensure(required: Int) {
            if (required <= bytes.size) return
            bytes = bytes.copyOf((bytes.size * 2).coerceAtLeast(required))
        }
    }
}
