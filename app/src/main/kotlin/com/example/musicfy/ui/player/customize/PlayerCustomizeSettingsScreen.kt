// PlayerCustomizeSettingsScreen.kt
// Route wrapper for Settings → Appearance → Player customization.
//
// PlayerCustomizeScreen draws its own opaque page and its own preview, so reaching it from
// Settings needs nothing more than a destination and a back action — there is one editor
// implementation, not a player-hosted one and a standalone one.

package com.example.musicfy.ui.player.customize

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@Composable
fun PlayerCustomizeSettingsScreen(navController: NavController) {
    PlayerCustomizeScreen(
        onBack = { navController.navigateUp() },
        modifier = Modifier.fillMaxSize(),
    )
}
