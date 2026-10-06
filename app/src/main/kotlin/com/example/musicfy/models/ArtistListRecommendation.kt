// ArtistListRecommendation.kt

package com.example.musicfy.models

import com.music.innertube.models.YTItem

data class ArtistGroup(
    val artistName: String,
    val artistId: String?,
    val artistThumbnailUrl: String?,
    val items: List<YTItem>,
)
