// PoTokenResult.kt

package com.example.musicfy.utils.potoken

/**
 * The tokens produced by one BotGuard minting cycle.
 *
 * Two bindings exist: a token minted from the **video id**, and one minted from the
 * **session id** (visitorData, or dataSyncId when signed in). Which one googlevideo accepts
 * on the stream url is not obvious - ArchiveTune, which streams full-length audio, sends the
 * video-bound token there and keeps the session-bound one only as a fallback. We do the same.
 */
class PoTokenResult(
    /** Video-bound. Sent in the player request's `serviceIntegrityDimensions`. */
    val playerRequestPoToken: String,
    /** Video-bound. Appended to the stream url as `pot=`. */
    val streamingDataPoToken: String,
    /** Session-bound. Retried on the stream url if the video-bound token is rejected. */
    val sessionPoToken: String?,
)
