// GetQueueResponse.kt
// what is this for you ask its for get queue response ofc

package com.music.innertube.models.response

import com.music.innertube.models.PlaylistPanelRenderer
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueResponse(
    val queueDatas: List<QueueData>,
) {
    @Serializable
    data class QueueData(
        val content: PlaylistPanelRenderer.Content,
    )
}
