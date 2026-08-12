package com.example.musicfy.ui.player.models

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.Player

@Immutable
data class TransportState(
    val isPlaying: Boolean = false,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val playbackState: Int = Player.STATE_IDLE,
)

@Immutable
data class TrackInfo(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    // album title when the track has one used for the mini player pill s subtitle
    val album: String = "",
    val thumbnailUrl: String? = null,
    val liked: Boolean = false,
)

@Immutable
data class QueueItemData(
    val uid: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val duration: Long = 0L,
)

@Immutable
data class QueueState(
    val items: List<QueueItemData> = emptyList(),
    val currentIndex: Int = -1,
    val title: String = "",
)

@Immutable
data class ProgressState(
    val position: Long = 0L,
    val duration: Long = 0L,
    val percentage: Float = 0f,
)
