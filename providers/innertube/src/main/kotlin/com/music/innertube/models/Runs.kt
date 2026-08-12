// runs kt
// this thing is part of runs

package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null ||
        (getOrNull(0)?.getOrNull(0)?.text?.contains(regex = Regex("[&,]"))) != false
    ) {
        this
    } else {
        this.drop(1)
    }

fun List<Run>.oddElements() =
    filterIndexed { index, _ ->
        index % 2 == 0
    }

// the secondary text line on a list item is split on • and the first group is
// assumed to be the artist segment see splitbyseparator oddelements usage in the
// page kt parsers but some rows auto generated mixes rows with no real artist
// credit don t have an artist segment at all so that first group ends up being
// whatever was first instead e g a raw 3 45 duration string filtering those out
// where an artist list is built stops a duration from being stored displayed as if it
// were an artist s name
private val TIMESTAMP_TEXT_REGEX = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

fun String.looksLikeTimestamp(): Boolean = TIMESTAMP_TEXT_REGEX.matches(trim())
