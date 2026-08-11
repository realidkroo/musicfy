// GenreDescriptions.kt
// Blurbs for the mood/genre pages.
//
// YouTube's browse response carries a title and nothing else — no summary, no history, no "what is
// this" — so the page had only a generated one-liner under its heading. These are written per
// category: what the thing actually is, where it came from, and what it sounds like.
//
// Matching is on the category's own title, case- and punctuation-insensitive, with a prefix pass
// afterwards so regional variants of a heading ("Hip-Hop & Rap", "Hip hop") still land on the right
// entry. Anything genuinely unknown falls back to a plain descriptive line rather than to silence.

package com.example.musicfy.ui.screens.search

private val Descriptions: Map<String, String> = mapOf(
    // ── Moods & moments ──────────────────────────────────────────────────────────────────────
    "chill" to "Low-tempo, low-pressure listening — downtempo electronica, soft indie, quiet " +
        "soul and lo-fi beats. The idea grew out of 90s club chill-out rooms, where DJs kept a " +
        "second, slower room running beside the dancefloor, and it has been shorthand for " +
        "unhurried music ever since.",
    "commute" to "Music built for the trip in between: steady tempos, strong hooks and songs " +
        "that survive traffic noise and a pair of cheap earbuds. Leans on pop, hip-hop and " +
        "familiar catalogue rather than anything that needs close attention.",
    "energize" to "High-tempo, high-drive tracks for when you need lifting — dance, pop-punk, " +
        "big-room electronic and up-tempo hip-hop. Chosen for momentum: fast tempos, bright " +
        "production and choruses that arrive early.",
    "feel good" to "Warm, major-key, unironically happy music, drawn from soul, funk, disco and " +
        "bright pop across every decade. Less about a genre than about a guarantee — nothing " +
        "here is going to bring the mood down.",
    "focus" to "Music engineered to stay in the background: ambient, modern classical, " +
        "instrumental hip-hop and minimal electronica. Largely wordless on purpose, since " +
        "lyrics compete with reading and writing for the same part of your attention.",
    "gaming" to "Long-session listening for play — electronic, synthwave, metal, phonk and " +
        "orchestral game scores. Built to hold up over hours without either dropping the " +
        "intensity or demanding you follow it.",
    "party" to "Dancefloor music: house, hip-hop, Latin, Afrobeats and pop that everyone " +
        "already knows the words to. Sequenced for a room rather than for headphones — " +
        "familiar, loud and relentless.",
    "romance" to "Slow songs about wanting someone, from classic soul and quiet storm through " +
        "to contemporary R&B and singer-songwriter ballads. The lineage runs from 50s doo-wop " +
        "and Motown straight into modern bedroom pop.",
    "sad" to "Music for sitting with it rather than shaking it off — ballads, breakup songs, " +
        "slowcore, torch songs and melancholy indie. Minor keys, slow tempos and lyrics that " +
        "do not resolve.",
    "sleep" to "Quiet, slow and mostly beatless: ambient drones, piano, soft strings and " +
        "nature-adjacent textures. Descends from Brian Eno's Music for Airports (1978) and the " +
        "idea that music can be as ignorable as it is interesting.",
    "workout" to "Tempo-driven music for training — typically 120–150 BPM, where most people's " +
        "running cadence sits. Hip-hop, EDM, hard rock and drill, picked for a steady pulse and " +
        "a constant push.",

    // ── Genres ───────────────────────────────────────────────────────────────────────────────
    "african" to "The music of a continent, not a single sound: Afrobeats from Lagos and Accra, " +
        "amapiano from South Africa, highlife, soukous, and Afrobeat proper — the horn-driven, " +
        "politically charged style Fela Kuti built in 1970s Nigeria.",
    "arabic" to "From the orchestral tarab of Umm Kulthum and Abdel Halim Hafez to modern " +
        "Khaleeji pop and Egyptian mahraganat. Built on maqam — a modal system with intervals " +
        "smaller than the Western semitone, which is why it can sound unplaceable at first.",
    "blues" to "Born in the Mississippi Delta around 1900 out of work songs, field hollers and " +
        "spirituals. Its twelve-bar form, blue notes and call-and-response are the direct " +
        "foundation of jazz, R&B, rock and roll and almost everything after.",
    "bollywood & indian" to "Film music as the mainstream: playback singers, huge arrangements " +
        "and songs written for the screen, alongside classical Hindustani and Carnatic " +
        "traditions, bhangra, and independent Indian pop.",
    "country" to "American vernacular music out of the rural South, tracing to 1920s string " +
        "bands and the 1927 Bristol sessions that recorded Jimmie Rodgers and the Carter " +
        "Family. Narrative lyrics, plain language, and a straight line from honky-tonk to " +
        "today's stadium country.",
    "dance & electronic" to "Music made from machines for rooms full of people — Chicago house " +
        "and Detroit techno in the 80s, then rave, trance, garage, dubstep and everything the " +
        "festival circuit has produced since.",
    "decades" to "The catalogue by era. Each decade collects what actually charted and what " +
        "outlasted it — useful both as nostalgia and as a way of hearing how quickly the " +
        "mainstream moves.",
    "family" to "Music for listening together with children in the room: soundtracks, sing-" +
        "alongs and pop chosen for being genuinely all-ages rather than merely inoffensive.",
    "folk & acoustic" to "Songs carried by voice and unamplified instruments, from traditional " +
        "ballads through the 60s revival — Dylan, Baez, Nick Drake — to contemporary " +
        "singer-songwriters. Words first, arrangement second.",
    "hip hop" to "From block parties in the 1970s Bronx, where DJ Kool Herc looped the breaks " +
        "off funk records, into the dominant popular music on earth. Four elements — MCing, " +
        "DJing, breaking and graffiti — of which the first two became the record industry.",
    "indie & alternative" to "What sat outside the majors, then became its own mainstream: " +
        "post-punk, college rock, 90s alternative, and the 2000s indie wave. Defined originally " +
        "by who released it, and later by a sound.",
    "j-pop" to "Japanese popular music as a self-contained ecosystem — idol groups, anime and " +
        "drama tie-ins, city pop's 80s revival, and a domestic market big enough that it has " +
        "never needed to sound like anywhere else.",
    "jazz" to "New Orleans, early 1900s: blues and ragtime meeting brass-band instrumentation. " +
        "Improvisation is the point, and the tradition runs through swing, bebop, modal, free " +
        "and fusion without ever settling.",
    "k-pop" to "South Korean pop built on an intensive training and A&R system, fusing " +
        "Western pop, hip-hop and R&B with heavy choreography and visual production. A global " +
        "export industry since roughly 2010.",
    "latin" to "Reggaeton, salsa, bachata, cumbia, regional Mexican and Latin pop — Caribbean, " +
        "South American and Mexican traditions that now sit permanently in the global charts.",
    "metal" to "Heavier, louder and faster than the blues rock it came from: Black Sabbath in " +
        "1970 downtuning into something genuinely menacing, then splintering into thrash, " +
        "death, black, doom and metalcore.",
    "pop" to "Whatever the mainstream currently is. Defined by intent rather than instruments — " +
        "written for maximum reach, built on hooks, and constantly absorbing whatever is " +
        "working in other genres.",
    "r&b & soul" to "Rhythm and blues out of the 1940s, then soul as gospel technique applied " +
        "to secular songs — Motown, Stax, Philadelphia. Contemporary R&B keeps the vocal " +
        "tradition over slower, more electronic production.",
    "reggae" to "Jamaica, late 1960s: ska and rocksteady slowed down, the emphasis moved to the " +
        "offbeat, the bass pushed to the front. Also the birthplace of dub, and through it, of " +
        "remix culture itself.",
    "rock" to "The electric guitar as the centre of popular music, from 50s rock and roll " +
        "through the British Invasion, hard rock, punk, and alternative. Broad enough to be " +
        "almost meaningless and specific enough to be unmistakable.",
    "soundtracks & musicals" to "Music written to serve something else — film scores, stage " +
        "musicals and game soundtracks. Composed to picture and to plot, which is why it works " +
        "differently on its own.",
)

/**
 * A written description for a mood or genre page, or a plain generated line if the category is not
 * one we have a blurb for.
 */
fun genreDescription(title: String): String {
    if (title.isBlank()) return ""
    val key = title.lowercase()
        .replace("&amp;", "&")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    Descriptions[key]?.let { return it }

    // "Hip-Hop & Rap" against "hip hop", "Country & Americana" against "country", and so on.
    Descriptions.entries
        .firstOrNull { (candidate, _) -> key.startsWith(candidate) || candidate.startsWith(key) }
        ?.let { return it.value }

    return "Featured playlists, albums and artists in $title."
}
