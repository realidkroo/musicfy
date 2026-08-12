// artistlistrecommendationkt
// this thing is for the unified artist list home section one card per
// collapsing every similar to x artist seed into a single row of grouped

package com.example.musicfy.models

import com.music.innertube.models.YTItem

data class ArtistGroup(
    val artistName: String,
    val artistId: String?,
    val artistThumbnailUrl: String?,
    val items: List<YTItem>,
)
