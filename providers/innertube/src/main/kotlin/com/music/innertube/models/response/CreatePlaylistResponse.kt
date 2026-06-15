// CreatePlaylistResponse.kt
// what is this for you ask its for create playlist response ofc

package com.music.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistResponse(
    val playlistId: String
)
