package com.example.musicfy.playback

/**
 * Decides how many bytes a single [androidx.media3.datasource.DataSpec] should cover.
 *
 * googlevideo rejects unbounded reads outright: measured against a live stream url, a request
 * with no Range header, or with an open-ended `Range: bytes=0-`, answers 403. Only a bounded
 * range is served, and only up to somewhere between 786 KB and 1 MB per request. So every
 * request we issue must carry an explicit, modest length - chunking is mandatory, not an
 * optimisation.
 *
 * Returns `null` only when there is nothing sensible left to ask for.
 */
internal fun resolveStreamChunkLength(
    requestedLength: Long,
    position: Long,
    knownContentLength: Long?,
    chunkLength: Long,
): Long? {
    if (chunkLength <= 0L || position < 0L) return null
    if (knownContentLength != null && position >= knownContentLength) return null

    val remainingLength = knownContentLength?.minus(position)?.takeIf { it > 0L }
    return listOfNotNull(
        chunkLength,
        requestedLength.takeIf { it > 0L },
        remainingLength,
    ).minOrNull()?.takeIf { it > 0L }
}
