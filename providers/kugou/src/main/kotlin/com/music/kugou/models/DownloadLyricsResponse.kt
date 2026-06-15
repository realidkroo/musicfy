// DownloadLyricsResponse.kt
// this thing is for download lyrics response

package com.music.kugou.models

import kotlinx.serialization.Serializable

@Serializable
data class DownloadLyricsResponse(
    val content: String,
)
