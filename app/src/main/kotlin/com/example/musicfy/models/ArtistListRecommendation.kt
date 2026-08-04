// ArtistListRecommendation.kt
// this thing is for the unified "Artist List" home section - one card per artist,
// collapsing every "similar to X artist" seed into a single row of grouped cards.

package com.example.musicfy.models

import com.music.innertube.models.YTItem

data class ArtistGroup(
    val artistName: String,
    val artistId: String?,
    val artistThumbnailUrl: String?,
    val items: List<YTItem>,
)
