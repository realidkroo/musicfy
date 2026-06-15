// ArtistItemsContinuationPage.kt
// this thing is part of artist items continuation page

package com.music.innertube.pages

import com.music.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
