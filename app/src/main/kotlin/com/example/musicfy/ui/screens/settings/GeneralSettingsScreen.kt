// GeneralSettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.LocalSongAutoMetadataKey
import com.example.musicfy.constants.OfflineModeKey
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsGroupStyle
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.SubSettingsSearchBar
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.utils.rememberPreference

@Composable
fun GeneralSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    val (offlineMode, onOfflineModeChange) = rememberPreference(
        OfflineModeKey,
        defaultValue = false
    )
    val (localSongAutoMetadata, onLocalSongAutoMetadataChange) = rememberPreference(
        LocalSongAutoMetadataKey,
        defaultValue = true
    )

    SubSettingsScaffold(
        title = "General",
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        Text(
            text = "Generally app settings",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )

        // Card 1: Page options
        val pageItems = listOf(
            SettingsItem(
                title = { Text("Import Data") },
                highlightKey = "Import Data",
                descriptionText = "import musicfy data or other streaming apps",
                icon = painterResource(R.drawable.backup),
                iconShape = CircleShape,
                onClick = { navController.navigate("other_settings") },
            ),
            SettingsItem(
                title = { Text("Edit profile") },
                highlightKey = "Edit profile",
                descriptionText = "edit profilee",
                icon = painterResource(R.drawable.person),
                iconShape = CircleShape,
                onClick = { navController.navigate("settings") },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (pageItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = pageItems,
            )
            Spacer(Modifier.height(14.dp))
        }

        // Card 2: Toggle options
        val toggleItems = listOf(
            SettingsItem(
                title = { Text("Disable App Updates notice") },
                highlightKey = "Disable App Updates notice",
                descriptionText = "No popup will appear except if u open the updater",
                icon = painterResource(R.drawable.commit),
                iconShape = CircleShape,
                onClick = { },
                trailingContent = {
                    AppSwitch(
                        checked = false,
                        onCheckedChange = { },
                        enabled = false,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Offline mode") },
                highlightKey = "Offline mode",
                descriptionText = "play downloaded music only",
                icon = painterResource(R.drawable.download),
                iconShape = CircleShape,
                onClick = { onOfflineModeChange(!offlineMode) },
                trailingContent = {
                    AppSwitch(
                        checked = offlineMode,
                        onCheckedChange = onOfflineModeChange,
                    )
                },
            ),
            SettingsItem(
                title = { Text("Local song auto metadata") },
                highlightKey = "Local song auto metadata",
                descriptionText = "Automatically match local tracks with online metadata",
                icon = painterResource(R.drawable.cached),
                iconShape = CircleShape,
                onClick = { onLocalSongAutoMetadataChange(!localSongAutoMetadata) },
                trailingContent = {
                    AppSwitch(
                        checked = localSongAutoMetadata,
                        onCheckedChange = onLocalSongAutoMetadataChange,
                    )
                },
            ),
        ).filter {
            query.isBlank() || it.highlightKey?.contains(query, ignoreCase = true) == true ||
                it.descriptionText?.contains(query, ignoreCase = true) == true
        }

        if (toggleItems.isNotEmpty()) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = toggleItems,
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
