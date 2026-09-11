// OtherSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold

/**
 * Home for the settings that do not belong to a themed page - including the destructive ones,
 * which is why they live behind an extra tap rather than on the profile page.
 */
@Composable
fun OtherSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset app data?") },
            text = {
                Text(
                    "This wipes all local data — your library, downloads, playlists, and settings — " +
                        "and closes the app. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
                    activityManager?.clearApplicationUserData()
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    SubSettingsScaffold(
        title = "Other settings",
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Reset app data") },
                    highlightKey = "Reset app data",
                    descriptionText = "Wipe data and close app",
                    icon = painterResource(R.drawable.delete_history),
                    iconShape = CircleShape,
                    isHighlighted = true,
                    onClick = { showResetConfirm = true },
                ),
            ),
        )

        Spacer(Modifier.height(120.dp))
    }
}
