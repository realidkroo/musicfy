// LyricsToolSheets.kt
// The three lyrics tools reachable from the player menu when it is opened on the lyrics page:
// an editor, a provider picker, and translation settings.
//
// These live behind `fromLyrics` in PlayerActionMenu rather than being always-on player actions,
// because every one of them acts on lyrics that are on screen. "Refetch lyrics" from a player with
// no lyrics view open is an action whose entire result is invisible.

package com.example.musicfy.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.LyricsProviderOrderKey
import com.example.musicfy.constants.TranslateLanguageKey
import com.example.musicfy.constants.TranslateLyricsKey
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.lyrics.LyricsProviderRegistry
import com.example.musicfy.lyrics.LyricsTranslationHelper
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.LyricsMenuViewModel

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

/**
 * Line-by-line lyrics editor.
 *
 * Timestamps are shown in plain `m:ss` rather than the raw `[00:12.34]` tag they are stored as —
 * the bracketed form is a serialisation detail, and reading a screen of them to find the line you
 * want to fix is needlessly hard. The original millisecond value is kept alongside so saving
 * round-trips exactly; only the text is editable.
 *
 * Saving writes plain per-line LRC. Any word-level timings the provider supplied are dropped,
 * because there is no way to re-derive them from edited text — the edited song keeps line-level
 * sync and loses the karaoke sweep. That is a real trade and the reason the editor stores under
 * its own provider name, so a later refetch can replace it wholesale.
 */
@Composable
fun LyricsEditorSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsMenuViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    val parsed = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            LyricsUtils.parseLyrics(raw)
        }
    }

    // Editable copy, seeded once per song so typing is not undone by the database flow re-emitting.
    val drafts = remember(parsed) { mutableStateListOf<String>().apply { addAll(parsed.map { it.text }) } }

    MenuSheetSurface(onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Edit lyrics",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (parsed.isNotEmpty()) {
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                val id = mediaMetadata?.id
                                if (id != null) {
                                    viewModel.saveLyrics(
                                        songId = id,
                                        lyrics = buildLrc(parsed.map { it.time }, drafts),
                                    )
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (parsed.isEmpty()) {
                Text(
                    text = "No lyrics to edit for this song.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MenuRowSurface)
                        .padding(14.dp)
                ) {
                    itemsIndexed(parsed, key = { index, _ -> index }) { index, entry ->
                        Column(modifier = Modifier.padding(bottom = 14.dp)) {
                            Text(
                                text = formatTimestamp(entry.time),
                                color = Color(0xFF8A8A8A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = drafts.getOrElse(index) { entry.text },
                                onValueChange = { if (index < drafts.size) drafts[index] = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `83_400` -> `"1:23"`. The stored form is a bracketed LRC tag; this is the readable one. */
internal fun formatTimestamp(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Reassembles edited text against its original timings as plain per-line LRC. */
internal fun buildLrc(times: List<Long>, texts: List<String>): String = buildString {
    for (index in times.indices) {
        val text = texts.getOrNull(index).orEmpty()
        if (text.isBlank()) continue
        val ms = times[index].coerceAtLeast(0L)
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000
        val centis = (ms % 1000) / 10
        append('[')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append('.')
        append(centis.toString().padStart(2, '0'))
        append(']')
        append(text.trim())
        append('\n')
    }
}

// ---------------------------------------------------------------------------------------------
// Provider picker
// ---------------------------------------------------------------------------------------------

/**
 * Picks which source to prefer for this song, then refetches.
 *
 * Choosing a provider moves it to the front of the saved order rather than pinning it as the only
 * source: providers routinely have no entry for a given track, and a hard pin would leave the song
 * with no lyrics at all instead of falling through to the next one.
 */
@Composable
fun LyricsProviderSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: LyricsMenuViewModel = hiltViewModel()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val (order, setOrder) = rememberPreference(LyricsProviderOrderKey, defaultValue = "")

    val names = remember(order) {
        LyricsProviderRegistry.deserializeProviderOrder(order)
    }
    val active = lyricsEntity?.provider

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Lyrics Provider",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // A left-aligned list, not centred pills. These are named options being chosen from,
            // so they read down a common left edge like every other menu in the app; centred pills
            // made a list of eight providers into eight floating buttons with no scan line.
            names.forEach { name ->
                val isActive = name == active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MenuRowSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            setOrder(
                                LyricsProviderRegistry.serializeProviderOrder(
                                    listOf(name) + names.filter { it != name }
                                )
                            )
                            mediaMetadata?.let { viewModel.refetchLyrics(it, lyricsEntity) }
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = LyricsProviderRegistry.getDisplayName(name),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The source actually in use is marked, rather than the whole row changing
                    // colour — a filled row reads as "selected control", but this list is a set of
                    // actions where one happens to be current.
                    if (isActive) {
                        Text(
                            text = "In use",
                            color = Color(0xFF9A9A9A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Translation settings
// ---------------------------------------------------------------------------------------------

/**
 * Every language Google Translate accepts.
 *
 * Kept as a full list rather than a handful of popular picks: which language someone wants their
 * lyrics in is not something a shortlist can guess, and a missing language is a dead end with no
 * workaround. Presented in its own overlay because a list this long cannot sit inline.
 */
private val TranslationTargets = listOf(
    "af" to "Afrikaans", "sq" to "Albanian", "am" to "Amharic", "ar" to "Arabic",
    "hy" to "Armenian", "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian",
    "bn" to "Bengali", "bs" to "Bosnian", "bg" to "Bulgarian", "ca" to "Catalan",
    "ceb" to "Cebuano", "ny" to "Chichewa", "zh-CN" to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)", "co" to "Corsican", "hr" to "Croatian",
    "cs" to "Czech", "da" to "Danish", "nl" to "Dutch", "en" to "English",
    "eo" to "Esperanto", "et" to "Estonian", "tl" to "Filipino", "fi" to "Finnish",
    "fr" to "French", "fy" to "Frisian", "gl" to "Galician", "ka" to "Georgian",
    "de" to "German", "el" to "Greek", "gu" to "Gujarati", "ht" to "Haitian Creole",
    "ha" to "Hausa", "haw" to "Hawaiian", "iw" to "Hebrew", "hi" to "Hindi",
    "hmn" to "Hmong", "hu" to "Hungarian", "is" to "Icelandic", "ig" to "Igbo",
    "id" to "Indonesian", "ga" to "Irish", "it" to "Italian", "ja" to "Japanese",
    "jw" to "Javanese", "kn" to "Kannada", "kk" to "Kazakh", "km" to "Khmer",
    "ko" to "Korean", "ku" to "Kurdish", "ky" to "Kyrgyz", "lo" to "Lao",
    "la" to "Latin", "lv" to "Latvian", "lt" to "Lithuanian", "lb" to "Luxembourgish",
    "mk" to "Macedonian", "mg" to "Malagasy", "ms" to "Malay", "ml" to "Malayalam",
    "mt" to "Maltese", "mi" to "Maori", "mr" to "Marathi", "mn" to "Mongolian",
    "my" to "Myanmar", "ne" to "Nepali", "no" to "Norwegian", "ps" to "Pashto",
    "fa" to "Persian", "pl" to "Polish", "pt" to "Portuguese", "pa" to "Punjabi",
    "ro" to "Romanian", "ru" to "Russian", "sm" to "Samoan", "gd" to "Scots Gaelic",
    "sr" to "Serbian", "st" to "Sesotho", "sn" to "Shona", "sd" to "Sindhi",
    "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian", "so" to "Somali",
    "es" to "Spanish", "su" to "Sundanese", "sw" to "Swahili", "sv" to "Swedish",
    "tg" to "Tajik", "ta" to "Tamil", "te" to "Telugu", "th" to "Thai",
    "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu", "uz" to "Uzbek",
    "vi" to "Vietnamese", "cy" to "Welsh", "xh" to "Xhosa", "yi" to "Yiddish",
    "yo" to "Yoruba", "zu" to "Zulu",
)

@Composable
fun LyricsTranslationSheet(onDismiss: () -> Unit) {
    val (target, setTarget) = rememberPreference(TranslateLanguageKey, defaultValue = "")
    val (sessionWide, setSessionWide) = rememberPreference(TranslateLyricsKey, defaultValue = false)
    var picking by remember { mutableStateOf(false) }

    val status by LyricsTranslationHelper.status.collectAsState()
    val hasTranslations by LyricsTranslationHelper.hasActiveTranslations.collectAsState()
    val isTranslating = status is LyricsTranslationHelper.TranslationStatus.Translating

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Text(text = "Lyrics translation", color = Color(0xFF9A9A9A), fontSize = 12.sp)
            Text(
                text = "Translate from",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // The source is whatever the lyrics are already in — detected, not chosen. Offering a
            // picker here would only let the user tell the translator something wrong.
            PillButton(label = "Source", enabled = false, onClick = {})

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "To", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(
                label = TranslationTargets.firstOrNull { it.first == target }?.second ?: "unselected",
                onClick = { picking = true },
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "And", color = Color(0xFF9A9A9A), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(MenuRowSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Translate this whole session",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = sessionWide, onCheckedChange = setSessionWide)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // The action lives here rather than on the lyrics page: this is where the target
            // language is chosen, so pressing "translate" is only meaningful once you have set it.
            // Disabled until a target exists, since translating into nothing is not an action.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (target.isBlank() || isTranslating) {
                            MenuRowSurface
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        }
                    )
                    .clickable(
                        enabled = target.isNotBlank() && !isTranslating,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (hasTranslations) {
                            LyricsTranslationHelper.triggerClearTranslations()
                        } else {
                            LyricsTranslationHelper.triggerManualTranslation()
                        }
                        // Close so the result is actually visible — the lyrics are behind this
                        // sheet, and the loading dots appear on the lines themselves.
                        onDismiss()
                    }
            ) {
                Text(
                    text = when {
                        isTranslating -> "Translating…"
                        hasTranslations -> "Show original"
                        target.isBlank() -> "Pick a language first"
                        else -> "Translate!"
                    },
                    color = Color.White.copy(alpha = if (target.isBlank() || isTranslating) 0.45f else 1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (picking) {
        LanguagePickerSheet(
            selected = target,
            onPick = {
                setTarget(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * The full language list, in its own overlay on top of the translation sheet.
 *
 * Filterable, because a hundred-odd entries is more than anyone wants to scroll through to reach
 * "Vietnamese".
 */
@Composable
private fun LanguagePickerSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) {
            TranslationTargets
        } else {
            TranslationTargets.filter { it.second.contains(query, ignoreCase = true) }
        }
    }

    MenuSheetSurface(onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Text(
                text = "Translate to",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MenuRowSurface)
                    .padding(horizontal = 16.dp)
            ) {
                if (query.isEmpty()) {
                    Text(text = "Search language", color = Color(0xFF8A8A8A), fontSize = 13.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(filtered, key = { _, item -> item.first }) { _, (code, label) ->
                    val isSelected = code == selected
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.20f) else MenuRowSurface
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(code) }
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) Color.White.copy(alpha = 0.20f) else MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
