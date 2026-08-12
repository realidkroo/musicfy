// castconnectionhandler kt
// this thing is for cast connection handler

package com.example.musicfy.playback

import android.content.Context
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// stub castconnectionhandler for f droid builds cast functionality is not available without google play services stable the compiler can t infer stability across stateflow properties so without this annotation every composable that takes a castconnectionhandler parameter playerslider playercontrols is forced to fully recompose whenever its caller recomposes instead of skipping even though nothing it reads all via collectasstate actually changed issyncingfromcast is only ever read from musicservice never from a composable so this holds
@Stable
class CastConnectionHandler(
    context: Context,
    scope: CoroutineScope,
    musicService: MusicService
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting
    
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting
    
    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName
    
    private val _castPosition = MutableStateFlow(0L)
    val castPosition: StateFlow<Long> = _castPosition
    
    private val _castDuration = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = _castDuration
    
    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying
    
    private val _castIsBuffering = MutableStateFlow(false)
    val castIsBuffering: StateFlow<Boolean> = _castIsBuffering
    
    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume
    
    var isSyncingFromCast: Boolean = false
        private set
    
    fun initialize(): Boolean = false
    fun disconnect() {}
    fun loadCurrentMedia() {}
    fun loadMedia(metadata: com.example.musicfy.models.MediaMetadata) {}
    fun play() {}
    fun pause() {}
    fun seekTo(position: Long) {}
    fun setVolume(volume: Float) {}
    fun skipToNext() {}
    fun skipToPrevious() {}
    fun navigateToMediaIfInQueue(mediaId: String): Boolean = false
    fun release() {}
}
