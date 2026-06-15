// ImageUploadResponse.kt
// the file functioned as image upload response

package com.music.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class ImageUploadResponse(
    val encryptedBlobId: String
)