// PlaylistContinuationPage.kt
// this thing is part of playlist continuation page

package com.music.innertube.pages

import com.music.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
