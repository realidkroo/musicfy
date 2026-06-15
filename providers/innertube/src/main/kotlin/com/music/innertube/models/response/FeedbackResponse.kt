// FeedbackResponse.kt
// this thing is part of feedback response

package com.music.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse(
    val feedbackResponses: List<Status>,
) {
    @Serializable
    data class Status(
        val isProcessed: Boolean,
    )
}
