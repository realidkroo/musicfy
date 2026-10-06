// PoTokenResult.kt

package com.example.musicfy.utils.potoken

/**
 * The two tokens produced by one BotGuard minting cycle.
 *
 * The pairing is not intuitive and musicfy had it backwards: the **session**-bound token belongs
 * in the player request, and the **video**-bound token is the one googlevideo wants on the stream
 * url as `pot=`. Metrolist and ArchiveTune - both of which stream full-length audio - agree on
 * this, so the field names describe where each token goes rather than what it was minted from.
 */
class PoTokenResult(
    /** Minted from the session id. Sent in the player request's `serviceIntegrityDimensions`. */
    val playerRequestPoToken: String,
    /** Minted from the video id. Appended to the stream url as `pot=`. */
    val streamingDataPoToken: String,
)
