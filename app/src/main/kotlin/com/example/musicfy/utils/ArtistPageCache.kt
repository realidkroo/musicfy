// ArtistPageCache.kt

package com.example.musicfy.utils

import com.example.musicfy.models.ArtistGroup
import com.music.innertube.pages.ArtistPage
import java.util.concurrent.ConcurrentHashMap

/**
 * what an artist's page shows at the top: the round photo from YouTube Music, and the banner,
 * motion and name logo from Apple Music when Apple has them. image urls are ready to load.
 */
data class ArtistHeader(
    val id: String,
    val name: String,
    /** YouTube Music's photo; crop it square for the round avatar */
    val avatarUrl: String?,
    /** a still for the banner: Apple's motion frame or banner, else the YouTube photo */
    val bannerUrl: String?,
    /** Apple's looping banner video (HLS), when there is one */
    val motionUrl: String?,
    /** the artist's name as a white logo on transparent, when Apple has one */
    val logoUrl: String?,
    /** the logo's width / height */
    val logoAspect: Float?,
)

/**
 * the artist pages opened this run, so the pages under an artist (top songs, albums, videos,
 * similar artists) open on the same banner and shelves at once instead of fetching them again.
 */
object ArtistPageCache {
    private val pages = ConcurrentHashMap<String, ArtistPage>()
    private val headers = ConcurrentHashMap<String, ArtistHeader>()
    private val picks = ConcurrentHashMap<String, ArtistGroup>()

    fun page(artistId: String): ArtistPage? = pages[artistId]

    fun header(artistId: String): ArtistHeader? = headers[artistId]

    fun putPage(artistId: String, page: ArtistPage) {
        pages[artistId] = page
    }

    fun putHeader(header: ArtistHeader) {
        headers[header.id] = header
    }

    /** what a Home "Similar to" box showed for this artist, so its page leads with the same picks */
    fun picks(artistId: String): ArtistGroup? = picks[artistId]

    fun putPicks(artistId: String, group: ArtistGroup) {
        picks[artistId] = group
    }
}
