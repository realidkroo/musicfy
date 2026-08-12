// uriutilskt
// the file functioned as uri utils

package com.example.musicfy.ui.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import com.example.musicfy.R

// safely opens a uri using the provided urihandler if the uri cannot be handled
fun UriHandler.safeOpenUri(context: Context, uri: String) {
    if (uri.isBlank()) return
    
    runCatching {
        openUri(uri)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.error_no_stream).replace("stream", "app"), // fallback if specific string not found
            Toast.LENGTH_SHORT
        ).show()
    }
}
