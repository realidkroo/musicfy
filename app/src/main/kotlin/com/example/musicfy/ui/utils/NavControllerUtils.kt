// NavControllerUtils.kt
// this thing is for nav controller utils

package com.example.musicfy.ui.utils

import androidx.navigation.NavController
import com.example.musicfy.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
