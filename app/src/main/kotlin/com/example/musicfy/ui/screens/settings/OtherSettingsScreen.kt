// OtherSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.ui.component.SubSettingsScaffold

/**
 * Home for settings that do not belong to a themed page.
 */
@Composable
fun OtherSettingsScreen(navController: NavController) {
    SubSettingsScaffold(
        title = "Other settings",
        onBack = { navController.navigateUp() },
    ) {
        Spacer(Modifier.height(120.dp))
    }
}
