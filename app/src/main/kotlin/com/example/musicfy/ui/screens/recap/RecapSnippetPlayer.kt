// RecapSnippetPlayer.kt
//
// The recap plays a bit of your top artist's song and your top song. It does that on its own small
// player, so the real queue is never touched: the recap pauses the main player when it opens and
// hands back to it when it closes.

package com.example.musicfy.ui.screens.recap

import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class RecapSnippetPlayer(private val context: Context, private val scope: CoroutineScope) {
    private var player: ExoPlayer? = null
    private var playingId: String? = null
    private var job: Job? = null
    private var released = false

    /** Starts [songId] from its beginning, fading in. Anything already playing fades out first. */
    fun play(songId: String, fadeInMs: Long = 1400L) {
        if (released || songId == playingId) return
        playingId = songId
        val previous = player
        player = null
        job?.cancel()
        previous?.let { retire(it, 450L) }
        job = scope.launch {
            val data = withContext(Dispatchers.IO) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                YTPlayerUtils.playerResponseForPlayback(songId, audioQuality = AudioQuality.AUTO, connectivityManager = cm).getOrNull()
            } ?: return@launch
            if (released || playingId != songId) return@launch
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(data.streamHeaders)
                .setAllowCrossProtocolRedirects(true)
            data.streamHeaders["User-Agent"]?.let { http.setUserAgent(it) }
            val exo = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .build()
                .apply {
                    // no focus request: the main player is already paused, and taking focus
                    // from it would stop it from resuming when the recap hands back
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                        false,
                    )
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_OFF
                    setMediaItem(MediaItem.fromUri(data.streamUrl))
                    prepare()
                    playWhenReady = true
                }
            player = exo
            withTimeoutOrNull(8_000L) {
                while (!exo.isPlaying) delay(40L)
            } ?: return@launch
            fade(exo, to = 1f, durationMs = fadeInMs)
        }
    }

    /** Fades whatever is playing out and lets it go. */
    fun stop(fadeOutMs: Long = 900L) {
        playingId = null
        job?.cancel()
        val current = player ?: return
        player = null
        retire(current, fadeOutMs)
    }

    // its own coroutine, so a play() right after can't cancel the fade and leak the player
    private fun retire(p: ExoPlayer, fadeOutMs: Long) {
        scope.launch {
            try {
                fade(p, to = 0f, durationMs = fadeOutMs)
            } finally {
                p.release()
            }
        }
    }

    fun release() {
        released = true
        job?.cancel()
        player?.release()
        player = null
    }

    private suspend fun fade(p: ExoPlayer, to: Float, durationMs: Long) {
        val from = p.volume
        val steps = (durationMs / 16L).coerceAtLeast(1L)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            // ears hear loudness logarithmically: a squared ramp sounds even
            val eased = if (to > from) t * t else 1f - (1f - t) * (1f - t)
            p.volume = from + (to - from) * eased
            delay(16L)
        }
        p.volume = to
    }
}
