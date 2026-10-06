// ExplorePage.kt

package com.music.innertube.pages

import com.music.innertube.models.AlbumItem
import kotlinx.serialization.Serializable

@Serializable
data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
