// SimilarRecommendation.kt

package com.example.musicfy.models

import com.music.innertube.models.YTItem
import com.example.musicfy.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
