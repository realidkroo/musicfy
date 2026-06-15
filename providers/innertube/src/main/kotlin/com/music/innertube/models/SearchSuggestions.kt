// SearchSuggestions.kt
// this thing is for search suggestions

package com.music.innertube.models

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
