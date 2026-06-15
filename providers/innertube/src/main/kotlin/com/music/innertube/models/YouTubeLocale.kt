// YouTubeLocale.kt
// the file functioned as you tube locale

package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String, // geolocation
    val hl: String, // host language
)
