// deviceoutputsheetkt
// "change device output" (concept screen 2): the two volumes that actually
// things then everything you could route playback to

// - device volume  — the android stream_music level shared with the hardware
// - music volume   — this player's own gain applied on top turning the app
// touching what every other app on the phone plays at is the point

package com.example.musicfy.ui.player.menu

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R

@Composable
fun DeviceOutputSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    val deviceVolume = remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }
    val musicVolume = remember {
        mutableFloatStateOf(playerConnection?.player?.volume ?: 1f)
    }

    // outputs the system will actually route to queried once when the sheet
    // callback would be nicer but a sheet is short-lived enough that a snapshot
    val devices = remember {
        runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in RoutableTypes }
                .map { it.productName?.toString().orEmpty().ifBlank { it.type.deviceLabel() } to it.type }
                .distinctBy { it.first }
        }.getOrDefault(emptyList())
    }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.66f, fullDetent = 0.92f) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            SectionLabel("Change playback")

            VolumeCard(
                icon = R.drawable.volume_up,
                title = android.os.Build.MODEL ?: "this device",
                value = deviceVolume.floatValue,
                onValueChange = { next ->
                    deviceVolume.floatValue = next
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (next * maxVolume).toInt(),
                        0,
                    )
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            VolumeCard(
                icon = R.drawable.music_note,
                title = "Music volume",
                value = musicVolume.floatValue,
                onValueChange = { next ->
                    musicVolume.floatValue = next
                    playerConnection?.player?.volume = next
                },
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel("available devices")

            if (devices.isEmpty()) {
                DeviceRow(label = "No other outputs found", enabled = false, onClick = {})
            } else {
                devices.forEach { (label, type) ->
                    DeviceRow(label = label, icon = type.deviceIcon(), onClick = {})
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            DeviceRow(
                label = "go to bluetooth settings",
                icon = R.drawable.bluetooth,
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
    }
}

// output types worth offering deliberately not every constant the platform
private val RoutableTypes = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_HDMI,
)

private fun Int.deviceLabel(): String = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth device"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    else -> "Output"
}

private fun Int.deviceIcon(): Int = when (this) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.drawable.bluetooth
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.drawable.volume_up
    else -> R.drawable.cast
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF8A8A8A),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun VolumeCard(
    icon: Int,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_down),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            LineSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    label: String,
    onClick: () -> Unit,
    icon: Int = R.drawable.cast,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        IconDot(icon, alpha)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconDot(icon: Int, alpha: Float = 1f) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f * alpha))
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp),
        )
    }
}
