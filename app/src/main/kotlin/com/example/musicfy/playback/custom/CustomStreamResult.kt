package com.example.musicfy.playback.custom

data class CustomStreamResult(
    val streamUrl: String,
    val decryptionKey: String? = null,
    val keyId: String? = null,
    val isDash: Boolean = false,
    val source: String? = null
)
