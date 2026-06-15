// SearchBody.kt
// this thing is for search body

package com.music.innertube.models.body

import com.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)
