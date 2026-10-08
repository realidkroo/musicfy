// PlaylistForm.kt

package com.example.musicfy.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.musicfy.playlistcover.CoverChoice

private const val MaxNameLength = 80
private const val MaxDescriptionLength = 300

/** How far the carousel overflows the sheet's side padding - the sheet pads its content by 20dp. */
private val SheetSidePadding = 20.dp

/**
 * What the create and edit sheets are editing. Plain state held outside the composition: the sheet
 * body and its pinned button bar are composed separately by the popup host, and both read it.
 */
@Stable
internal class PlaylistFormState(name: String, description: String) {
    var name by mutableStateOf(name)
    var description by mutableStateOf(description)

    /** The cover picked in the carousel; null while there is nothing to change. */
    var choice by mutableStateOf<CoverChoice?>(null)

    /** Whether a new playlist is created on YouTube Music as well. */
    var sync by mutableStateOf(false)

    /** Set while the work runs after Create/Done - the button is off and the sheet can't be closed. */
    var busy by mutableStateOf(false)
}

/**
 * The body of the create and edit sheets, as in the reference mocks: the cover carousel, then
 * "Playlist title" and "Playlist desc". Scrolls when the screen is short or the text large; the
 * sheet's own button bar stays pinned underneath.
 *
 * @param seed what a playlist with no top song yet takes its colours from.
 * @param extra anything the caller wants below the fields (the create sheet's sync switch).
 */
@Composable
internal fun PlaylistFormContent(
    form: PlaylistFormState,
    seed: String,
    artworkUrl: String?,
    startPage: Int,
    existingImage: String?,
    keepAllowed: Boolean,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        PlaylistCoverCarousel(
            title = form.name.ifBlank { "My playlist" },
            seed = seed,
            artworkUrl = artworkUrl,
            startPage = startPage,
            existingImage = existingImage,
            keepAllowed = keepAllowed,
            onChoice = { form.choice = it },
            modifier = Modifier.bleedHorizontally(SheetSidePadding),
        )

        Spacer(Modifier.height(22.dp))

        SheetLabel("Playlist title")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = form.name,
            onValueChange = { form.name = it.take(MaxNameLength) },
            placeholder = "Name your playlist",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(18.dp))

        SheetLabel("Playlist desc")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = form.description,
            onValueChange = { form.description = it.take(MaxDescriptionLength) },
            placeholder = "What is it about?",
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        if (extra != null) {
            Spacer(Modifier.height(18.dp))
            extra()
        }
    }
}
