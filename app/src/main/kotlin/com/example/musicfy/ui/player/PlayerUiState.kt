package com.example.musicfy.ui.player

import androidx.compose.runtime.Stable
import com.example.musicfy.playback.PlayerConnection
import com.example.musicfy.ui.player.models.QueueState
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.player.models.TransportState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Stable
class PlayerUiState(connection: PlayerConnection) {
    private val scope = connection.scope

    val transportState: StateFlow<TransportState> = combine(
        connection.isEffectivelyPlaying,
        connection.shuffleModeEnabled,
        connection.repeatMode,
        connection.canSkipNext,
        connection.canSkipPrevious,
    ) { isPlaying, shuffleModeEnabled, repeatMode, canSkipNext, canSkipPrevious ->
        TransportState(
            isPlaying = isPlaying,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            canSkipNext = canSkipNext,
            canSkipPrevious = canSkipPrevious,
        )
    }.combine(connection.playbackState) { partial, playbackState ->
        partial.copy(playbackState = playbackState)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, TransportState())

    val trackInfo: StateFlow<TrackInfo> = combine(
        connection.mediaMetadata,
        connection.currentSong,
    ) { metadata, song ->
        TrackInfo(
            mediaId = metadata?.id.orEmpty(),
            title = metadata?.title.orEmpty(),
            artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
            album = metadata?.album?.title.orEmpty(),
            thumbnailUrl = metadata?.thumbnailUrl,
            liked = song?.song?.liked == true,
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, TrackInfo())

    val queueState: StateFlow<QueueState> = combine(
        connection.queueItems,
        connection.currentWindowIndex,
        connection.queueTitle,
    ) { items, currentIndex, title ->
        QueueState(items = items, currentIndex = currentIndex, title = title.orEmpty())
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, QueueState())

    val progressState = connection.progressState
}
