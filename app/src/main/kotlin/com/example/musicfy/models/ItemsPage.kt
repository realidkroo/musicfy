// ItemsPage.kt
// this thing is part of items page

package com.example.musicfy.models

import com.music.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
