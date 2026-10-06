// PlayerCustomizeSettingsScreen.kt

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
