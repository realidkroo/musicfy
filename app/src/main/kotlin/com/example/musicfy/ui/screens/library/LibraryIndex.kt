// LibraryIndex.kt
//
// The index down the right edge of the Library's A-Z lists, and the sections it jumps between.
//
// Latin titles file under A-Z and anything without a letter under '#', as before. Titles in other
// scripts used to be filed under their own first character - a Japanese library got dozens of
// one-song sections the rail had no letter for. They now get sections that make sense for their
// script (kana rows for Japanese, initial consonants for Korean, pinyin initials for Chinese, the
// first letter for alphabets like Cyrillic), and the rail folds each script into one short tag at
// its foot - JP, KR, CN - that opens out into those sections when the finger reaches it.

package com.example.musicfy.ui.screens.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------------

/** How a section's title is written, in the order the scripts follow one another down the list. */
enum class IndexScript(val tag: String) {
    LATIN(""),
    SYMBOL("#"),
    JAPANESE("JP"),
    KOREAN("KR"),
    CHINESE("CN"),
    CYRILLIC("RU"),
    GREEK("GR"),
    ARABIC("AR"),
    HEBREW("HE"),
    THAI("TH"),
    INDIC("IN"),
}

/**
 * One section of an indexed list. [rank] orders sections within their script; [label] is what the
 * section's header and its spot on the rail say.
 */
data class IndexSection(
    val script: IndexScript,
    val rank: Int,
    val label: String,
) {
    /** Stable across recompositions and searches: what the rail and the list agree on. */
    val key: String get() = "${script.name}/$rank"
}

private val SymbolSection = IndexSection(IndexScript.SYMBOL, 0, "#")

/** Kana rows, in gojūon order, then kanji for titles that start with one. */
private val KanaRowLabels = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "漢")
private const val KanjiRow = 10

/** Initial consonants as they head a Korean index (doubled ones file with their single). */
private val HangulInitialLabels = listOf("가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하")

/** For each of Unicode's 19 syllable initials, its place in [HangulInitialLabels]. */
private val HangulInitialToRow = intArrayOf(0, 0, 1, 2, 2, 3, 4, 5, 5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13)

/** Compatibility jamo (ㄱ...ㅎ, as typed on their own) mapped to the same rows. */
private val JamoToRow: Map<Char, Int> = mapOf(
    'ㄱ' to 0, 'ㄲ' to 0, 'ㄴ' to 1, 'ㄷ' to 2, 'ㄸ' to 2, 'ㄹ' to 3, 'ㅁ' to 4, 'ㅂ' to 5, 'ㅃ' to 5,
    'ㅅ' to 6, 'ㅆ' to 6, 'ㅇ' to 7, 'ㅈ' to 8, 'ㅉ' to 8, 'ㅊ' to 9, 'ㅋ' to 10, 'ㅌ' to 11, 'ㅍ' to 12, 'ㅎ' to 13,
)

/**
 * Chinese titles go by the pinyin of their first character, which only ICU knows. Built the first
 * time a Chinese title shows up, never for a library without one.
 */
private val chineseIndex by lazy {
    runCatching {
        android.icu.text.AlphabeticIndex<Any>(android.icu.util.ULocale.SIMPLIFIED_CHINESE).buildImmutableIndex()
    }.getOrNull()
}

private fun isKana(c: Char): Boolean = c in '぀'..'ヿ' || c in 'ㇰ'..'ㇿ' || c in 'ｦ'..'ﾝ'

/** The hiragana row a kana belongs to; katakana is read as its hiragana. */
private fun kanaRow(c: Char): Int {
    var h = c
    if (h in 'ァ'..'ヶ') h = h - 0x60
    return when (h) {
        in 'ぁ'..'お' -> 0 // ぁ-お
        in 'か'..'ご' -> 1 // か-ご
        in 'さ'..'ぞ' -> 2 // さ-ぞ
        in 'た'..'ど' -> 3 // た-ど
        in 'な'..'の' -> 4 // な-の
        in 'は'..'ぽ' -> 5 // は-ぽ
        in 'ま'..'も' -> 6 // ま-も
        in 'ゃ'..'よ' -> 7 // ゃ-よ
        in 'ら'..'ろ' -> 8 // ら-ろ
        in 'ゎ'..'ん' -> 9 // ゎ-ん
        'ゕ', 'ゖ' -> 1 // ゕ ゖ
        else -> 0 // ゔ, the long-vowel mark, small katakana extensions
    }
}

/** The section a title files under. */
fun indexSectionOf(title: String): IndexSection {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return SymbolSection
    val firstCodePoint = trimmed.codePointAt(0)
    // NFKC folds full-width Latin (Ａ) and half-width kana (ｶ) into the forms everything else uses.
    val folded = Normalizer.normalize(String(Character.toChars(firstCodePoint)), Normalizer.Form.NFKC)
    if (folded.isEmpty()) return SymbolSection
    val cp = folded.codePointAt(0)
    val script = runCatching { Character.UnicodeScript.of(cp) }.getOrNull() ?: return SymbolSection
    return when (script) {
        Character.UnicodeScript.LATIN -> {
            // É files under E: the letter without its accent.
            val base = Normalizer.normalize(folded, Normalizer.Form.NFD).first().uppercaseChar()
            if (base in 'A'..'Z') IndexSection(IndexScript.LATIN, base - 'A', base.toString()) else SymbolSection
        }
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> {
            val row = kanaRow(folded[0])
            IndexSection(IndexScript.JAPANESE, row, KanaRowLabels[row])
        }
        Character.UnicodeScript.HANGUL -> {
            val c = folded[0]
            val row = if (c in '가'..'힣') HangulInitialToRow[(c - '가') / 588] else JamoToRow[c] ?: 0
            IndexSection(IndexScript.KOREAN, row, HangulInitialLabels[row])
        }
        Character.UnicodeScript.HAN -> {
            // Kanji and hanzi share code points; kana anywhere in the title says it's Japanese.
            if (trimmed.any(::isKana)) {
                IndexSection(IndexScript.JAPANESE, KanjiRow, KanaRowLabels[KanjiRow])
            } else {
                val index = chineseIndex
                val bucket = index?.getBucketIndex(folded) ?: 0
                val pinyin = index?.getBucket(bucket)?.label.orEmpty()
                // Labelled for now by its pinyin initial; the list relabels each section with the
                // first character actually in it (see [indexSections]).
                IndexSection(IndexScript.CHINESE, bucket, pinyin.ifBlank { folded })
            }
        }
        Character.UnicodeScript.CYRILLIC -> alphabetSection(IndexScript.CYRILLIC, folded)
        Character.UnicodeScript.GREEK -> alphabetSection(IndexScript.GREEK, folded)
        Character.UnicodeScript.ARABIC -> alphabetSection(IndexScript.ARABIC, folded)
        Character.UnicodeScript.HEBREW -> alphabetSection(IndexScript.HEBREW, folded)
        Character.UnicodeScript.THAI -> alphabetSection(IndexScript.THAI, folded)
        Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI, Character.UnicodeScript.TAMIL,
        Character.UnicodeScript.TELUGU, Character.UnicodeScript.KANNADA, Character.UnicodeScript.MALAYALAM,
        Character.UnicodeScript.GUJARATI, Character.UnicodeScript.GURMUKHI, Character.UnicodeScript.ORIYA,
        -> alphabetSection(IndexScript.INDIC, folded)
        else -> SymbolSection
    }
}

private fun alphabetSection(script: IndexScript, folded: String): IndexSection {
    val letter = folded.first().uppercaseChar()
    return IndexSection(script, letter.code, letter.toString())
}

private val sectionOrder = compareBy<IndexSection>({ it.script.ordinal }, { it.rank })

/**
 * [items] grouped into their sections, in index order: A-Z, '#', then each other script. Within a
 * section, by title. Chinese sections take the first character of their first title as their label,
 * so the rail shows a character from the user's own music rather than a pinyin letter that would
 * read as a second A-Z.
 */
fun <T> indexSections(items: List<T>, nameOf: (T) -> String): List<Pair<IndexSection, List<T>>> {
    val grouped = items
        .map { it to indexSectionOf(nameOf(it)) }
        .groupBy({ it.second }, { it.first })
    return grouped.entries
        .sortedWith { a, b -> sectionOrder.compare(a.key, b.key) }
        .map { (section, entries) ->
            val sorted = entries.sortedBy { nameOf(it).trim().lowercase() }
            val labelled = if (section.script == IndexScript.CHINESE) {
                section.copy(label = nameOf(sorted.first()).trim().take(1))
            } else {
                section
            }
            labelled to sorted
        }
}

/** [items] in the order an indexed list shows them - for queueing what the user is looking at. */
fun <T> sortForLibraryIndex(items: List<T>, nameOf: (T) -> String): List<T> =
    indexSections(items, nameOf).flatMap { it.second }

// ---------------------------------------------------------------------------------------------
// The rail
// ---------------------------------------------------------------------------------------------

/** One spot on the rail: a letter, '#', or a script's tag with the sections it opens into. */
private sealed interface RailSlot {
    data class Single(val key: String, val label: String) : RailSlot
    data class Group(val script: IndexScript, val children: List<Single>) : RailSlot
}

/** Where everything on the rail sits right now, in px from its top, and how visible it is. */
private class RailPositions(val centers: FloatArray, val alphas: FloatArray, val keys: Array<String?>)

private val RailLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.sp,
    letterSpacing = (-0.02).em,
)

private val RailTagStyle = RailLabelStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.em)

/** How far the letters around the finger are pushed apart, relative to their distance from it. */
private const val FisheyeSpread = 1.25f

/** How much bigger the letter under the finger gets (1.45 = two and a half times). */
private const val FisheyeGrow = 1.45f

/**
 * The A-Z index, with the other scripts folded in at its foot.
 *
 * Scrubbing it no longer pops a letter up in a bubble: the letters around the finger swell and swing
 * out to the left, the nearest the largest and the rest easing back into line with distance, and the
 * edge of the screen darkens behind them so they read over anything. A script tag (JP, KR, CN...)
 * opens out into its sections when the finger reaches it, and folds back up once the finger lifts.
 *
 * One custom down/move/up loop: a tap and the first frame of a drag are the same path, and nothing
 * else on the strip competes for the touch.
 */
@Composable
fun LibraryIndexRail(
    sections: List<IndexSection>,
    onSectionSelected: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSectionSelected)

    val available = remember(sections) { sections.map { it.key }.toSet() }
    val slots = remember(sections) {
        buildList<RailSlot> {
            ('A'..'Z').forEachIndexed { index, letter ->
                add(RailSlot.Single(IndexSection(IndexScript.LATIN, index, letter.toString()).key, letter.lowercase()))
            }
            add(RailSlot.Single(SymbolSection.key, "#"))
            sections
                .filter { it.script != IndexScript.LATIN && it.script != IndexScript.SYMBOL }
                .groupBy { it.script }
                .entries
                .sortedBy { it.key.ordinal }
                .forEach { (script, scriptSections) ->
                    add(RailSlot.Group(script, scriptSections.map { RailSlot.Single(it.key, it.label) }))
                }
        }
    }
    val groups = remember(slots) { slots.filterIsInstance<RailSlot.Group>() }
    // Every key on the rail in order, for falling through to the nearest section that has songs.
    val railOrder = remember(slots) {
        slots.flatMap { slot ->
            when (slot) {
                is RailSlot.Single -> listOf(slot.key)
                is RailSlot.Group -> slot.children.map { it.key }
            }
        }
    }
    val expansions = remember(groups) { groups.associate { it.script to Animatable(0f) } }

    var railHeight by remember { mutableFloatStateOf(0f) }
    var touchY by remember { mutableFloatStateOf(0f) }
    val presence = remember { Animatable(0f) }
    var lastKey by remember { mutableStateOf<String?>(null) }

    // Every label's place, from the open/closed state of the groups. Read where it's used (layout
    // and each label's layer), so opening a group moves things without recomposing anything.
    val positions by remember(slots) {
        derivedStateOf {
            val labelCount = slots.sumOf { if (it is RailSlot.Group) 1 + it.children.size else 1 }
            val centers = FloatArray(labelCount)
            val alphas = FloatArray(labelCount)
            val keys = arrayOfNulls<String>(labelCount)
            var total = 0f
            slots.forEach { slot ->
                total += if (slot is RailSlot.Group) {
                    val e = expansions[slot.script]?.value ?: 0f
                    1f + (slot.children.size - 1).coerceAtLeast(0) * e
                } else {
                    1f
                }
            }
            val unit = if (total > 0f) railHeight / total else 0f
            var cursor = 0f
            var i = 0
            slots.forEach { slot ->
                when (slot) {
                    is RailSlot.Single -> {
                        centers[i] = (cursor + 0.5f) * unit
                        alphas[i] = 1f
                        keys[i] = slot.key
                        i++
                        cursor += 1f
                    }
                    is RailSlot.Group -> {
                        val e = expansions[slot.script]?.value ?: 0f
                        val n = slot.children.size
                        val width = 1f + (n - 1).coerceAtLeast(0) * e
                        val tagCenter = cursor + width / 2f
                        centers[i] = tagCenter * unit
                        alphas[i] = 1f - e
                        keys[i] = null
                        i++
                        slot.children.forEachIndexed { j, child ->
                            val own = cursor + (j + 0.5f) * width / n
                            centers[i] = (tagCenter + (own - tagCenter) * e) * unit
                            alphas[i] = e
                            keys[i] = child.key
                            i++
                        }
                        cursor += width
                    }
                }
            }
            RailPositions(centers, alphas, keys)
        }
    }

    /** The section under [y], and the group it's in if any. Uses the rail as it is laid out now. */
    fun hit(y: Float): Pair<String?, IndexScript?> {
        if (railHeight <= 0f) return null to null
        var total = 0f
        slots.forEach { slot ->
            total += if (slot is RailSlot.Group) 1f + (slot.children.size - 1).coerceAtLeast(0) * (expansions[slot.script]?.value ?: 0f) else 1f
        }
        val t = (y / railHeight).coerceIn(0f, 0.9999f) * total
        var cursor = 0f
        for (slot in slots) {
            when (slot) {
                is RailSlot.Single -> {
                    if (t < cursor + 1f) return slot.key to null
                    cursor += 1f
                }
                is RailSlot.Group -> {
                    val e = expansions[slot.script]?.value ?: 0f
                    val n = slot.children.size
                    val width = 1f + (n - 1).coerceAtLeast(0) * e
                    if (t < cursor + width) {
                        val child = if (e < 0.5f) 0 else ((t - cursor) / (width / n)).toInt().coerceIn(0, n - 1)
                        return slot.children[child].key to slot.script
                    }
                    cursor += width
                }
            }
        }
        return (slots.lastOrNull() as? RailSlot.Single)?.key to null
    }

    /** [key], or the nearest section below it that has something in it (then above, if none). */
    fun resolve(key: String): String? {
        if (key in available) return key
        val at = railOrder.indexOf(key)
        if (at < 0) return null
        return railOrder.drop(at + 1).firstOrNull { it in available }
            ?: railOrder.take(at).lastOrNull { it in available }
    }

    fun handle(y: Float) {
        touchY = y
        val (key, group) = hit(y)
        if (group != null) {
            // Reaching a script's tag opens it (and closes any other). It stays open until the
            // finger lifts, so the rail never reshuffles under a finger moving back up.
            expansions.forEach { (script, anim) ->
                val target = if (script == group) 1f else 0f
                if (anim.targetValue != target) {
                    scope.launch { anim.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }
                }
            }
        }
        val resolved = key?.let(::resolve)
        if (resolved != null && resolved != lastKey) {
            lastKey = resolved
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSelected(resolved)
        }
        if (presence.targetValue != 1f) scope.launch { presence.animateTo(1f, tween(160)) }
    }

    fun release() {
        lastKey = null
        scope.launch { presence.animateTo(0f, tween(320, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) }
        scope.launch {
            delay(260)
            expansions.values.forEach { anim ->
                launch { anim.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)) }
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        // The screen's edge darkens behind the swollen letters, so they read over any artwork.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(150.dp)
                .graphicsLayer { alpha = presence.value }
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    )
                ),
        )

        val density = LocalDensity.current
        val sigmaPx = with(density) { 34.dp.toPx() }
        val swingPx = with(density) { 34.dp.toPx() }

        Layout(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.7f)
                .width(26.dp)
                .onSizeChanged { railHeight = it.height.toFloat() }
                .pointerInput(slots) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        handle(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            handle(change.position.y)
                            change.consume()
                        }
                        release()
                    }
                },
            content = {
                var index = 0
                slots.forEach { slot ->
                    val labels = when (slot) {
                        is RailSlot.Single -> listOf(slot.label to (slot.key in available))
                        is RailSlot.Group -> listOf(slot.script.tag to true) +
                            slot.children.map { it.label to (it.key in available) }
                    }
                    labels.forEachIndexed { j, (label, has) ->
                        val labelIndex = index++
                        val isTag = slot is RailSlot.Group && j == 0
                        Text(
                            text = label,
                            style = if (isTag) RailTagStyle else RailLabelStyle,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer {
                                val p = positions
                                val center = p.centers.getOrElse(labelIndex) { 0f }
                                val d = center - touchY
                                val falloff = exp(-(d / sigmaPx) * (d / sigmaPx)) * presence.value
                                // Pushed apart around the finger (still in order), swollen, swung left.
                                translationY = FisheyeSpread * d * falloff
                                translationX = -swingPx * falloff
                                val s = 1f + FisheyeGrow * falloff
                                scaleX = s
                                scaleY = s
                                val rest = if (has) 0.62f else 0.22f
                                alpha = (rest + (1f - rest) * falloff) * p.alphas.getOrElse(labelIndex) { 1f }
                            },
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val loose = Constraints()
            val placeables = measurables.map { it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val p = positions
                placeables.forEachIndexed { i, placeable ->
                    val center = p.centers.getOrElse(i) { 0f }
                    placeable.place(
                        x = (constraints.maxWidth - placeable.width) / 2,
                        y = (center - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }
    }
}
