// lyricsproviderregistrykt
// this thing is part of lyrics provider registry

package com.example.musicfy.lyrics

import com.example.musicfy.constants.PreferredLyricsProvider

// central registry for all lyrics providers maps provider names (used for
object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "YouLyPlus"       to YouLyPlusLyricsProvider,
        "Paxsenix"        to PaxSenixLyricsProvider,
        "BetterLyrics"    to BetterLyricsProvider,
        "SimpMusic"       to SimpMusicLyricsProvider,
        "LrcLib"          to LrcLibLyricsProvider,
        "Kugou"           to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic"    to YouTubeLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "YouLyPlus",
        "Paxsenix",
        "SimpMusic",
        "LrcLib",
        "Kugou",
        "YouTubeSubtitle",
        "YouTubeMusic",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    // maps a [preferredlyricsprovider] enum value to its registry name used for migration
    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB        -> "LrcLib"
        PreferredLyricsProvider.KUGOU         -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC     -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS     -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX      -> "Paxsenix"
    }

    // returns the human-readable display name for a registry provider key
    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Apple Music"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        else              -> name
    }
}
