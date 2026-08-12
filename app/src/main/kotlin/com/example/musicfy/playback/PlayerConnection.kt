// playerconnectionkt
// this thing is part of player connection

package com.example.musicfy.playback

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.extensions.currentMetadata
import com.example.musicfy.extensions.getCurrentQueueIndex
import com.example.musicfy.extensions.getQueueWindows
import com.example.musicfy.extensions.metadata
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.playback.MusicService.MusicBinder
import com.example.musicfy.playback.queues.Queue
import com.example.musicfy.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

// @stable: playerconnection is passed as a parameter into nearly every
// (songinfo actionbuttons playerslider playercontrols ) without this compose
// infer stability across its context/coroutinescope/exoplayer-typed
// of those composables is forced to fully recompose whenever the root player
// recomposes for any reason — even when nothing they actually read changed
// observed state lives on the exposed stateflow properties which are already
// collectasstate() everywhere so this annotation doesn't change what gets
@Stable
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    val context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    val scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
        private const val PLAYER_INIT_TIMEOUT_MS = 5000L // 5 second timeout for player initialization
    }

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady
    
    // safe player accessor checks readiness & handles errors should be used by all
    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    // public accessor for player throws if player not ready callers should check
    val player: ExoPlayer
        get() = getPlayerSafe()

    // tracks whether player initialization completed successfully
    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    
    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")
        
        // initialize with player state or safe defaults if player not ready
        val initialState = try {
            val initialPlayer = getPlayerSafe()
            Triple(initialPlayer.playbackState, initialPlayer.playWhenReady, 
                   initialPlayer.playWhenReady && initialPlayer.playbackState != STATE_ENDED)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }
        
        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        isPlaying = combine(playbackState, playWhenReady) { state, ready ->
            ready && state != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third
        )
        
        // track service readiness changes in background
        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }
        
        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }
    
    // effective playing state considers cast when active
    val isEffectivelyPlaying = combine(
        isPlaying,
        service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
        service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false)
    ) { localPlaying, isCasting, castPlaying ->
        if (isCasting) castPlaying else localPlaying
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playbackState != STATE_ENDED && player.playWhenReady
    )
    
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val queueItems: kotlinx.coroutines.flow.StateFlow<List<com.example.musicfy.ui.player.models.QueueItemData>> = queueWindows.map { windows ->
        windows.map { window ->
            val mediaItem = window.mediaItem
            com.example.musicfy.ui.player.models.QueueItemData(
                uid = window.uid.hashCode(),
                mediaId = mediaItem.mediaId,
                title = mediaItem.mediaMetadata.title?.toString() ?: "",
                artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                artworkUri = mediaItem.mediaMetadata.artworkUri,
                duration = window.durationMs
            )
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, emptyList())

    // single 15hz ticker for position/duration replacing the per-screen polling loops
    val progressState: kotlinx.coroutines.flow.StateFlow<com.example.musicfy.ui.player.models.ProgressState> =
        kotlinx.coroutines.flow.callbackFlow {
            while (true) {
                try {
                    val p = attachedPlayer
                    if (p != null) {
                        val position = p.currentPosition.coerceAtLeast(0L)
                        val duration = p.duration.coerceAtLeast(0L)
                        trySend(
                            com.example.musicfy.ui.player.models.ProgressState(
                                position = position,
                                duration = duration,
                                percentage = if (duration > 0L) position.toFloat() / duration else 0f,
                            )
                        )
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(66L)
            }
        }.distinctUntilChanged().stateIn(
            scope,
            SharingStarted.Lazily,
            com.example.musicfy.ui.player.models.ProgressState(),
        )

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted

    val waitingForNetworkConnection = service.waitingForNetworkConnection
    


    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {
            // observe player changes (eg crossfade swap)
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            
            // initial setup if flow hasn't emitted yet but service is ready
            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            // propagate the error so mainactivity can retry
            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        
        // refresh all state from new player
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    fun playQueue(queue: Queue) {
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun startRadioSeamlessly() {

        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {

        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {

        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    // toggle play/pause - handles cast when active
    fun togglePlayPause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }
    
    // start playback - handles cast when active
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }
    
    // pause playback - handles cast when active
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    // seek to position - handles cast when active
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {
            // when casting use cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            player.seekToNext()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            // when casting use cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            // logic to mimic standard seektoprevious behavior but with explicit callbacks
            // if we are more than 3 seconds in just restart the song
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                // otherwise go to previous media item
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        super.onTracksChanged(tracks)
        val format = player.audioFormat ?: return
        val mediaItem = player.currentMediaItem ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val isLocal = database.song(mediaItem.mediaId).first()?.song?.isLocal == true
                if (isLocal) {
                    database.query {
                        upsert(
                            com.example.musicfy.db.entities.FormatEntity(
                                id = mediaItem.mediaId,
                                itag = -1,
                                mimeType = format.sampleMimeType?.split(";")?.get(0) ?: "audio/flac",
                                codecs = format.codecs ?: "",
                                bitrate = format.bitrate,
                                sampleRate = format.sampleRate,
                                contentLength = 0L,
                                loudnessDb = null,
                                perceptualLoudnessDb = null,
                                playbackUrl = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error updating local format entity")
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        val artworkData = mediaMetadata.artworkData ?: return
        val mediaItem = player.currentMediaItem ?: return
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songEntity = database.song(mediaItem.mediaId).first() ?: return@launch
                if (songEntity.song.isLocal && songEntity.song.thumbnailUrl == null) {
                    val artworkFile = java.io.File(context.cacheDir, "artwork_${mediaItem.mediaId.hashCode()}.jpg")
                    if (!artworkFile.exists()) {
                        artworkFile.writeBytes(artworkData)
                    }
                    val newThumbnailUrl = android.net.Uri.fromFile(artworkFile).toString()
                    database.query {
                        upsert(songEntity.song.copy(thumbnailUrl = newThumbnailUrl))
                    }
                    Timber.tag(TAG).d("Extracted local artwork to $newThumbnailUrl")
                    
                    // update current mediametadata if it matches
                    val currentMetadata = this@PlayerConnection.mediaMetadata.value
                    if (currentMetadata?.id == mediaItem.mediaId) {
                        this@PlayerConnection.mediaMetadata.value = currentMetadata.copy(thumbnailUrl = newThumbnailUrl)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error extracting local artwork")
            }
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    val uiState: com.example.musicfy.ui.player.PlayerUiState by lazy { com.example.musicfy.ui.player.PlayerUiState(this) }

    fun dispose() {
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}