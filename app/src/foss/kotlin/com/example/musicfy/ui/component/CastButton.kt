// castbutton kt
// what is this for you ask its for cast button ofc

package com.example.musicfy.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// stub castbutton for f droid builds does not render anything cast not available without gms
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    // no op cast not available in foss build
}
