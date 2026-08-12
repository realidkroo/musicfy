// CastManager.kt

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

    @Suppress("DEPRECATION")
    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.addCastStateListener(this)

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

    fun setSessionCallbacks(
        onStarted: (CastPlayer) -> Unit,
        onEnded: () -> Unit
    ) {
        onCastSessionStarted = onStarted
        onCastSessionEnded = onEnded
    }

    fun getCastPlayer(): CastPlayer? = castPlayer

    @Suppress("DEPRECATION")
    fun isCastSessionAvailable(): Boolean = castPlayer?.isCastSessionAvailable == true

    fun getCurrentPosition(): Long = castPlayer?.currentPosition ?: 0

    fun isPlaying(): Boolean = castPlayer?.isPlaying == true

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

    fun addListener(listener: Player.Listener) {
        castPlayer?.addListener(listener)
    }

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

    @Suppress("DEPRECATION")
    fun release() {
        castContext?.removeCastStateListener(this)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castContext = null
    }

    companion object {

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
