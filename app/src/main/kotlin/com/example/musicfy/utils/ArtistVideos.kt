// ArtistVideos.kt

package com.example.musicfy.utils

import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ArtistSection
import kotlinx.serialization.Serializable

/** the kinds of video row YouTube's own Home feed sends */
enum class FeedVideoKind { Live, MusicVideos, Other }

/** a video on one of Home's banner rows, with its title tidied up for the card */
@Serializable
data class HomeVideo(
    val item: SongItem,
    val title: String,
    val subtitle: String,
)

/**
 * picks Home's "Live Shows" and "Music Videos for You" off the artist pages of the people you play
 * most. those pages carry a "Videos" and a "Live performances" shelf straight from the artist's
 * channel, which is far steadier than searching: a search for a name can come back as fan edits
 * and AI covers one day and the real videos the next.
 */
object ArtistVideos {

    class Shelves(val musicVideos: List<SongItem>, val liveShows: List<SongItem>)

    // audio-only tracks are ATV; podcasts have their own type
    private val VideoTypes = setOf("MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC")

    // the live shelf's title in the user's language ("Live performances", "Pertunjukan langsung"...).
    // \b only knows ascii letters, so the korean/japanese/chinese words sit outside it
    private val LiveShelfTitle = Regex(
        "\\b(live|langsung|en vivo|ao vivo|en directo|en direct|dal vivo|konser|concerts?)\\b|라이브|ライブ|现场|現場",
        RegexOption.IGNORE_CASE,
    )

    // "Music videos for you", "Video musik untuk Anda"... in the feed
    private val MusicVideoShelfTitle = Regex(
        "\\b(music videos?|videos?|vídeos?|vidéos?|videoclips?|clipes|musikvideos?|mvs?)\\b|뮤직\\s?비디오|ミュージックビデオ|音乐视频|音樂影片",
        RegexOption.IGNORE_CASE,
    )

    // the Videos shelf mixes in lyric videos and teasers; the row is for the real thing
    private val NotAMusicVideo = Regex(
        "\\b(lyrics?|lirik|audio|visuali[sz]er|teaser|trailer|behind the scenes|making of|shorts|dance practice)\\b",
        RegexOption.IGNORE_CASE,
    )

    // "(Official Music Video)", "[MV]", "(4K)"... the card already says it's a video
    private val VideoTags = Regex(
        "\\s*[(\\[][^)\\]]*\\b(official|mv|m/v|music video|video|4k|hd)\\b[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    /**
     * the shelf titles come in the user's language, so the shelves are found by what's in them: rows
     * of videos. "Top songs" is tracks with albums, so it never counts. the live one is the shelf
     * whose title reads "live", or else the second, since Videos always comes first.
     */
    fun shelves(sections: List<ArtistSection>): Shelves {
        val videoShelves = sections.filter { section ->
            val videos = section.items.count { it is SongItem && isVideo(it) && it.album == null }
            videos > 0 && videos * 4 >= section.items.size * 3
        }
        val live = videoShelves.firstOrNull { LiveShelfTitle.containsMatchIn(it.title) }
            ?: videoShelves.getOrNull(1)
        val videos = videoShelves.firstOrNull { it !== live }

        // the same title twice is two uploads of one performance
        val liveShows = live?.items.orEmpty().filterIsInstance<SongItem>().filter(::isVideo)
            .distinctBy { it.title.lowercase() }
        val liveIds = liveShows.mapTo(HashSet()) { it.id }
        val musicVideos = videos?.items.orEmpty().filterIsInstance<SongItem>()
            .filter { isVideo(it) && it.id !in liveIds && !NotAMusicVideo.containsMatchIn(it.title) }
            .distinctBy { it.title.lowercase() }
        return Shelves(musicVideos = musicVideos, liveShows = liveShows)
    }

    /**
     * what kind of video row a shelf from YouTube's Home feed is, or null when it isn't all videos.
     * "Live performances" and "Music videos for you" are the same rows Home already builds, so they
     * fold into those rather than showing twice.
     */
    fun feedShelfKind(title: String, items: List<YTItem>): FeedVideoKind? {
        val videos = items.count { it is SongItem && isVideo(it) }
        if (videos == 0 || videos * 4 < items.size * 3) return null
        return when {
            LiveShelfTitle.containsMatchIn(title) -> FeedVideoKind.Live
            MusicVideoShelfTitle.containsMatchIn(title) -> FeedVideoKind.MusicVideos
            else -> FeedVideoKind.Other
        }
    }

    /** a video from YouTube's feed, which can be by anyone, so it carries its own artist line */
    fun feedVideo(item: SongItem): HomeVideo {
        val artists = item.artists.joinToString(", ") { it.name }
        return HomeVideo(
            item = item,
            title = item.artists.firstOrNull()?.let { cleanTitle(item.title, it.name) } ?: item.title,
            subtitle = artists,
        )
    }

    /** Home's own picks first in each pair, then YouTube's, without repeats */
    fun merge(own: List<HomeVideo>?, feed: List<HomeVideo>, limit: Int = 16): List<HomeVideo>? =
        interleave(listOf(own.orEmpty(), feed), limit * 2)
            .distinctBy { it.item.id }
            .take(limit)
            .ifEmpty { null }

    fun homeVideo(item: SongItem, artist: String) = HomeVideo(
        item = item,
        title = cleanTitle(item.title, artist),
        subtitle = artist,
    )

    /** one from each artist in turn, so a single artist can't fill the whole row */
    fun <T> interleave(lists: List<List<T>>, limit: Int): List<T> = buildList {
        var round = 0
        while (size < limit && lists.any { round < it.size }) {
            lists.forEach { if (round < it.size && size < limit) add(it[round]) }
            round++
        }
    }

    private fun isVideo(item: SongItem) = item.musicVideoType in VideoTypes

    private fun cleanTitle(title: String, artist: String): String {
        // "NewJeans (뉴진스) - Title" / "HINDIA | Title": the subtitle already names them
        val leadingArtist = Regex(
            "^\\s*${Regex.escape(artist)}(\\s*[(\\[][^)\\]]*[)\\]])?\\s*[-–—|:]\\s*",
            RegexOption.IGNORE_CASE,
        )
        val cleaned = title
            .replace(leadingArtist, "")
            .replace(VideoTags, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '-', '–', '—', '|', ':')
        return cleaned.ifEmpty { title }
    }
}
