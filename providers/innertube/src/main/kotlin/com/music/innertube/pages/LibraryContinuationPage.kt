// LibraryContinuationPage.kt
// this thing is for library continuation page

package com.music.innertube.pages

import com.music.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
