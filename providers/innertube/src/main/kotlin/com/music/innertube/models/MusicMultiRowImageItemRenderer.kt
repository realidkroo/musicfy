// MusicMultiRowImageItemRenderer.kt
// the file functioned as music multi row image item renderer

package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicMultiRowImageItemRenderer(
    val title: Runs,
    val subtitle: Runs,
    val thumbnail: ThumbnailRenderer,
    val onTap: NavigationEndpoint,
)
