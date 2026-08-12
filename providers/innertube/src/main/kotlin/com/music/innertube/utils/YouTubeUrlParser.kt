// youtubeurlparser kt
// the file functioned as you tube url parser

package com.music.innertube.utils

import com.music.innertube.models.WatchEndpoint

// utility class for parsing youtube and youtube music urls extracts video ids playlist ids and creates watchendpoints from urls
object YouTubeUrlParser {
    // represents the type of youtube link parsed
    sealed class ParsedUrl {
        abstract val id: String

        data class Video(
            override val id: String,
        ) : ParsedUrl()

        data class Artist(
            override val id: String,
        ) : ParsedUrl()
    }

    // pattern for matching youtube video urls
    private val VIDEO_URL_PATTERNS =
        listOf(
            Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
        )

    // pattern for matching youtube music artist urls
    private val ARTIST_URL_PATTERNS =
        listOf(
            Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
            Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/browse/(MPRE[a-zA-Z0-9_-]+)"""),
        )

    // checks if the given text is a youtube url
    fun isYouTubeUrl(text: String): Boolean = parse(text) != null

    // parses a youtube url and returns the parsed result
    fun parse(url: String): ParsedUrl? {
        val trimmedUrl = url.trim()
        println("[LINK_PARSE_DEBUG] Parsing URL: $trimmedUrl")

        // check for video urls
        for (pattern in VIDEO_URL_PATTERNS) {
            pattern.find(trimmedUrl)?.let { matchResult ->
                matchResult.groupValues.getOrNull(1)?.let { videoId ->
                    println("[LINK_PARSE_DEBUG] Detected Video ID: $videoId")
                    return ParsedUrl.Video(videoId)
                }
            }
        }

        // check for artist urls
        if (trimmedUrl.contains("music.youtube.com")) {
            for (pattern in ARTIST_URL_PATTERNS) {
                pattern.find(trimmedUrl)?.let { matchResult ->
                    matchResult.groupValues.getOrNull(1)?.let { artistId ->
                        println("[LINK_PARSE_DEBUG] Detected Artist ID: $artistId")
                        return ParsedUrl.Artist(artistId)
                    }
                }
            }
        }

        println("[LINK_PARSE_DEBUG] No match found or type restricted")
        return null
    }

    // extracts video id from a youtube url
    fun extractVideoId(url: String): String? = (parse(url) as? ParsedUrl.Video)?.id

    // creates a watchendpoint from a youtube video url
    fun createWatchEndpoint(url: String): WatchEndpoint? =
        extractVideoId(url)?.let { videoId ->
            WatchEndpoint(videoId = videoId)
        }
}
