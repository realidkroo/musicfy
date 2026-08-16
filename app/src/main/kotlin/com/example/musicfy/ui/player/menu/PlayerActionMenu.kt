// PlayerActionMenu.kt

package com.example.musicfy.ui.player.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.ui.utils.resize

/**
 * @param fromLyrics true when the menu was opened from the lyrics page. The lyrics tools are only
 *   meaningful there — offering "edit lyrics" or "refetch lyrics" from the player, where there may
 *   not even be lyrics on screen, is an action with no visible consequence.
 */
@Composable
fun PlayerActionMenu(
    onDismiss: () -> Unit,
    onEditPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    fromLyrics: Boolean = false,

    onReveal: ((Float) -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    var showSleepTimer by remember { mutableStateOf(false) }
    var showAudioDevices by remember { mutableStateOf(false) }
    var showPlaybackSpeed by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showLyricsProvider by remember { mutableStateOf(false) }
    var showLyricsTranslation by remember { mutableStateOf(false) }

    // Only touched by the lyrics section. hiltViewModel() itself is cheap, but the flows are
    // collected lazily so a menu opened from the player does no lyrics work at all.
    val lyricsMenuViewModel: com.example.musicfy.viewmodels.LyricsMenuViewModel = hiltViewModel()
    val lyricsMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        revealProvider = onReveal,
    ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(trackInfo.thumbnailUrl?.resize(240, 240))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackInfo.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append("by ")
                                append(trackInfo.artist.ifBlank { "unknown" })
                                if (trackInfo.album.isNotBlank()) {
                                    append(" on ")
                                    append(trackInfo.album)
                                }
                            },
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (fromLyrics) {
                    MenuDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuCard(
                            icon = R.drawable.translate,
                            title = "Ai translation",
                            // Greyed out until the AI translation settings are filled in — an
                            // enabled button that silently does nothing is worse than a disabled
                            // one that says so.
                            enabled = false,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        MenuCard(
                            icon = R.drawable.edit,
                            title = "Edit lyrics",
                            onClick = { showLyricsEditor = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Refetch removed — picking a provider already re-fetches, so a separate
                    // button that re-ran the same query with unchanged settings had no visible
                    // outcome.
                    MenuRow(
                        icon = R.drawable.lyrics,
                        title = "Lyrics provider",
                        onClick = { showLyricsProvider = true },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MenuRow(
                        icon = R.drawable.translate,
                        title = "lyrics translation",
                        onClick = { showLyricsTranslation = true },
                    )
                }

                MenuDivider()

                DeviceVolumeRow()
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.cast,
                    title = "Change device output",
                    onClick = { showAudioDevices = true },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SleepTimerRow(onClick = { showSleepTimer = true })
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.speed,
                    title = "Playback speed and tempo",
                    onClick = { showPlaybackSpeed = true },
                )

                MenuDivider()

                MenuRow(
                    icon = R.drawable.playlist_add,
                    title = "add to playlist",
                    enabled = false,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                    title = if (trackInfo.liked) "remove from liked" else "add to liked",
                    onClick = { playerConnection.toggleLike() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.library_add,
                    title = "add to library",
                    enabled = false,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.download,
                    title = "download music",
                    enabled = false,
                    onClick = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.share,
                    title = "share song",
                    enabled = false,
                    onClick = {},
                )

                MenuDivider()

                MenuRow(
                    icon = R.drawable.edit,
                    title = "edit player",
                    onClick = {
                        onDismiss()
                        onEditPlayer()
                    },
                )
            }
    }

    if (showSleepTimer) {
        SleepTimerSheet(onDismiss = { showSleepTimer = false })
    }
    if (showAudioDevices) {
        DeviceOutputSheet(onDismiss = { showAudioDevices = false })
    }
    if (showPlaybackSpeed) {
        PlaybackSpeedSheet(onDismiss = { showPlaybackSpeed = false })
    }
    if (showLyricsEditor) {
        LyricsEditorSheet(onDismiss = { showLyricsEditor = false })
    }
    if (showLyricsProvider) {
        LyricsProviderSheet(onDismiss = { showLyricsProvider = false })
    }
    if (showLyricsTranslation) {
        LyricsTranslationSheet(onDismiss = { showLyricsTranslation = false })
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

/**
 * The tall two-up card used for the headline lyrics actions: icon on its own row, label beneath.
 * Same surface and disabled treatment as [MenuRow], just a different shape.
 */
@Composable
private fun MenuCard(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f * contentAlpha))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = contentAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f * contentAlpha))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = contentAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
private fun SleepTimerRow(onClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val sleepTimer = playerConnection.service.sleepTimer

    var remainingLabel by remember { mutableStateOf("") }
    LaunchedEffect(sleepTimer.triggerTime, sleepTimer.pauseWhenSongEnd) {
        while (true) {
            remainingLabel = when {
                sleepTimer.pauseWhenSongEnd -> "ends with song"
                sleepTimer.triggerTime > 0L -> {
                    val left = sleepTimer.triggerTime - System.currentTimeMillis()
                    if (left <= 0L) "" else formatRemaining(left)
                }
                else -> ""
            }
            if (remainingLabel.isEmpty() || sleepTimer.pauseWhenSongEnd) break
            kotlinx.coroutines.delay(1000)
        }
    }

    MenuRow(
        icon = R.drawable.sleep_timer,
        title = "Sleep timer",
        onClick = onClick,
        trailing = {
            if (remainingLabel.isNotEmpty()) {
                Text(
                    text = "$remainingLabel remaining",
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                )
            }
        },
    )
}

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
private fun DeviceVolumeRow() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableStateOf(
            audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.volume_up),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = android.os.Build.MODEL ?: "this device",
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
                value = volume,
                onValueChange = { next ->
                    volume = next
                    audioManager.setStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        (next * maxVolume).toInt(),
                        0,
                    )
                },
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
