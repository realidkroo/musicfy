// DonatePrompt.kt
//
// Opens the Donate sheet on its own, sparingly:
//  - once for a new user, right after their 3rd song has actually been listened to;
//  - after that, twice per 9-day window, at random moments. Each window picks its two times up
//    front, and a prompt fires on the first listened-to song after one of them comes due.
//
// A "listened-to" song is one that has played for ListenThresholdMs straight, so skipping through
// a queue doesn't count. Prompts never open over another sheet and never while the app is in the
// background - a due prompt waits until the app is on screen and nothing else is showing.

package com.example.musicfy.ui.screens.donate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.DonateFirstPromptShownKey
import com.example.musicfy.constants.DonatePromptSlotsKey
import com.example.musicfy.constants.DonatePromptWindowStartKey
import com.example.musicfy.constants.DonateSongsPlayedKey
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.LocalZoomOutOverlayState
import com.example.musicfy.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.random.Random

private const val ListenThresholdMs = 30_000L
private const val SongsBeforeFirstPrompt = 3
private const val PromptsPerWindow = 2
private const val WindowMs = 9L * 24 * 60 * 60 * 1000

/** Nothing comes due in the first few hours of a window, so two prompts can't land back to back. */
private const val WindowLeadInMs = 6L * 60 * 60 * 1000

/**
 * Place once, inside PopupSheetHost's content. [enabled] = false (setup wizard or beta notice
 * still up) holds everything - songs aren't counted toward the first prompt until it's true.
 */
@Composable
fun DonatePromptScheduler(enabled: Boolean) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sheet = LocalZoomOutOverlayState.current
    val otherSheets = listOf(LocalMenuState.current, LocalBottomSheetPageState.current)
    val isEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(playerConnection) {
        val dataStore = context.dataStore
        var lastCountedId: String? = null

        combine(playerConnection.mediaMetadata, playerConnection.isPlaying) { metadata, playing ->
            metadata?.id.takeIf { playing }
        }.collectLatest { playingId ->
            // collectLatest restarts this on pause or track change, so only an uninterrupted
            // stretch of playback counts as a listen.
            if (playingId == null || playingId == lastCountedId) return@collectLatest
            delay(ListenThresholdMs)
            lastCountedId = playingId
            if (!isEnabled) return@collectLatest

            if (!onSongListened(dataStore, System.currentTimeMillis())) return@collectLatest

            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { !sheet.isVisible && otherSheets.none { it.isVisible } }.first { it }
            sheet.showDonateSheet()
        }
    }
}

/** Records one listened-to song; returns true if the Donate sheet should open now. */
private suspend fun onSongListened(
    dataStore: androidx.datastore.core.DataStore<Preferences>,
    now: Long,
): Boolean {
    var show = false
    dataStore.edit { prefs ->
        if (prefs[DonateFirstPromptShownKey] != true) {
            val played = (prefs[DonateSongsPlayedKey] ?: 0) + 1
            prefs[DonateSongsPlayedKey] = played
            if (played >= SongsBeforeFirstPrompt) {
                prefs[DonateFirstPromptShownKey] = true
                startWindow(prefs, now)
                show = true
            }
            return@edit
        }

        val windowStart = prefs[DonatePromptWindowStartKey] ?: 0L
        if (windowStart == 0L || now >= windowStart + WindowMs) {
            // Slots left over from a window that ended unused are dropped, not carried over.
            startWindow(prefs, now)
            return@edit
        }

        val slots = prefs[DonatePromptSlotsKey].orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }
        val (due, pending) = slots.partition { it <= now }
        if (due.isNotEmpty()) {
            // Several overdue at once (app unused for days) still means one prompt, not a burst.
            prefs[DonatePromptSlotsKey] = pending.joinToString(",")
            show = true
        }
    }
    return show
}

private fun startWindow(prefs: androidx.datastore.preferences.core.MutablePreferences, now: Long) {
    prefs[DonatePromptWindowStartKey] = now
    prefs[DonatePromptSlotsKey] = List(PromptsPerWindow) {
        now + Random.nextLong(WindowLeadInMs, WindowMs)
    }.sorted().joinToString(",")
}
