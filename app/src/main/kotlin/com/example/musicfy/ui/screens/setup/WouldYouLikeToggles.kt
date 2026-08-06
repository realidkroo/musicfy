package com.example.musicfy.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.constants.DisableAiFilterKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.OfflineModeKey
import com.example.musicfy.utils.rememberPreference

@Composable
fun WouldYouLikeToggles() {
    val (disableAiFilter, onDisableAiFilterChange) = rememberPreference(DisableAiFilterKey, defaultValue = false)
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF707070))
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Would you like to",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(20.dp))

        ToggleOptionRow(
            title = "Disable AI filter",
            description = "youtube may recommend AI music, this filter reduces it",
            checked = disableAiFilter,
            onCheckedChange = onDisableAiFilterChange
        )
        Spacer(modifier = Modifier.height(10.dp))
        ToggleOptionRow(
            title = "Disable Blur",
            description = "The app may get laggy when blur is enabled",
            checked = disableBlur,
            onCheckedChange = onDisableBlurChange
        )
        Spacer(modifier = Modifier.height(10.dp))
        ToggleOptionRow(
            title = "Offline mode",
            description = "well, offline mode.",
            checked = offlineMode,
            onCheckedChange = onOfflineModeChange
        )
    }
}

@Composable
private fun ToggleOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, fontSize = 13.sp, color = Color(0xFF999999), lineHeight = 17.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6C5CE7),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFF3A3A3A),
            )
        )
    }
}
