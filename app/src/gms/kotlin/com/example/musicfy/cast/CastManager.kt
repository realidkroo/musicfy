// castmanager kt
// this thing is part of cast manager

package com.example.musicfy.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

// manages google cast integration for the music player handles switching between local exoplayer and remote castplayer
class CastManager(
    private val context: Context
) : SessionAvailabilityListener, CastStateListener {

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castState = MutableStateFlow(CastState.NO_DEVICES_AVAILABLE)
    val castState: StateFlow<Int> = _castState.asStateFlow()

    private var onCastSessionStarted: ((CastPlayer) -> Unit)? = null
    private var onCastSessionEnded: (() -> Unit)? = null

    // initialize the cast context should be called when the activity is created this is safe to call even if google play services is not available
    @Suppress("DEPRECATION")
    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.addCastStateListener(this)
            
            // using deprecated constructor and setsessionavailabilitylistener as the new
            // castplayer builder api requires a local player which we don t use in this architecture
            castPlayer = CastPlayer(castContext!!)
            castPlayer?.setSessionAvailabilityListener(this)
            
            _castState.value = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE
            
            Timber.d("CastManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CastManager - Cast may not be available on this device")
            castContext = null
            castPlayer = null
        }
    }

    // set callbacks for cast session events
    fun setSessionCallbacks(
        onStarted: (CastPlayer) -> Unit,
        onEnded: () -> Unit
    ) {
        onCastSessionStarted = onStarted
        onCastSessionEnded = onEnded
    }

    // get the castplayer instance if available
    fun getCastPlayer(): CastPlayer? = castPlayer

    // check if casting is currently active
    @Suppress("DEPRECATION")
    fun isCastSessionAvailable(): Boolean = castPlayer?.isCastSessionAvailable == true

    // get the current playback position from the cast player
    fun getCurrentPosition(): Long = castPlayer?.currentPosition ?: 0

    // get whether the cast player is currently playing
    fun isPlaying(): Boolean = castPlayer?.isPlaying == true

    // load media items into the cast player
    fun loadMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0
    ) {
        castPlayer?.let { player ->
            player.setMediaItems(mediaItems, startIndex, startPositionMs)
            player.prepare()
            player.play()
        }
    }

    // add a listener to the cast player
    fun addListener(listener: Player.Listener) {
        castPlayer?.addListener(listener)
    }

    // remove a listener from the cast player
    fun removeListener(listener: Player.Listener) {
        castPlayer?.removeListener(listener)
    }

    override fun onCastStateChanged(state: Int) {
        _castState.value = state
        Timber.d("Cast state changed: $state")
    }

    override fun onCastSessionAvailable() {
        _isCasting.value = true
        castPlayer?.let { player ->
            onCastSessionStarted?.invoke(player)
        }
        Timber.d("Cast session available")
    }

    override fun onCastSessionUnavailable() {
        _isCasting.value = false
        onCastSessionEnded?.invoke()
        Timber.d("Cast session unavailable")
    }

    // release resources should be called when the service is destroyed
    @Suppress("DEPRECATION")
    fun release() {
        castContext?.removeCastStateListener(this)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castContext = null
    }

    companion object {
        // check if cast is available on this device
        fun isCastAvailable(context: Context): Boolean {
            return try {
                CastContext.getSharedInstance(context)
                true
            } catch (e: Exception) {
                Timber.d("Cast not available: ${e.message}")
                false
            }
        }
    }
}
