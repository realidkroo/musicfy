// player kt
// this thing is part of player

package com.example.musicfy.ui.player

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Indication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.produceState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import coil3.size.Size as CoilSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.musicfy.constants.MiniPlayerHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalDownloadUtil

import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.AppleMusicDarkChromeKey
import com.example.musicfy.constants.AudioQuality
import com.example.musicfy.constants.AudioQualityKey
import com.example.musicfy.constants.CropAlbumArtKey
import com.example.musicfy.constants.DarkModeKey
import com.example.musicfy.constants.HidePlayerThumbnailKey
import com.example.musicfy.constants.EnableLyricsThumbnailPlayPauseKey
import com.example.musicfy.constants.KeepScreenOn
import com.example.musicfy.constants.LyricsLineSpacingKey
import com.example.musicfy.constants.LyricsTextPositionKey
import com.example.musicfy.constants.LyricsTextSizeKey
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerBackgroundStyleKey
import com.example.musicfy.constants.PlayerButtonsStyle
import com.example.musicfy.constants.PlayerButtonsStyleKey
import com.example.musicfy.constants.PlayerHorizontalPadding
import com.example.musicfy.constants.QueuePeekHeight
import com.example.musicfy.constants.SliderStyle
import com.example.musicfy.constants.SliderStyleKey
import com.example.musicfy.constants.SquigglySliderKey
import com.example.musicfy.constants.SwipeLyricsKey
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.constants.ShowAudioQualityBadgeKey
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.extensions.SwipeGesture
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.extensions.toggleRepeatMode

import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.playback.ExoDownloadService
import com.example.musicfy.core.getConnectedBluetoothDeviceName
import com.example.musicfy.core.isBuds
import com.example.musicfy.core.isSpeaker
import com.example.musicfy.core.AudioDeviceBottomSheet
import com.example.musicfy.ui.component.BottomSheet
import com.example.musicfy.ui.component.BottomSheetState
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.LocalMenuState
import com.example.musicfy.ui.component.PlayerSliderTrack
import com.example.musicfy.ui.component.ResizableIconButton
import com.example.musicfy.ui.component.SquigglySlider
import com.example.musicfy.ui.component.WavySlider
import com.example.musicfy.ui.component.rememberBottomSheetState
import com.example.musicfy.ui.menu.OldPlayerMenu
import com.example.musicfy.ui.menu.PlayerMenu
import com.example.musicfy.ui.component.VolumeSlider
import com.example.musicfy.ui.screens.DarkMode
import com.example.musicfy.ui.screens.LyricsPosition
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.theme.PlayerColorExtractor
import com.example.musicfy.ui.theme.PlayerSliderColors
import com.example.musicfy.ui.utils.ShowMediaInfo
import com.example.musicfy.ui.utils.ShowOffsetDialog
import com.example.musicfy.ui.component.AudioFormatBadge
import com.example.musicfy.extensions.metadata
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils.parseLyrics
import com.example.musicfy.lyrics.LyricsUtils.findCurrentLineIndex
import com.example.musicfy.lyrics.WordTimestamp
import com.example.musicfy.utils.makeTimeString
import com.example.musicfy.utils.ArtistImageResolver
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.example.musicfy.ui.component.Icon as MIcon
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLoadControl
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.ui.player.CanvasArtworkPlaybackCache
import com.example.musicfy.ui.player.normalizeCanvasArtistName
import com.example.musicfy.ui.player.normalizeCanvasSongTitle

import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (context as android.app.Activity).window
    val insetsController = remember { androidx.core.view.WindowCompat.getInsetsController(window, view) }
    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        }
    }

    val useNewPlayerDesign = true
    val (showPlayerBottomCard) = rememberPreference(
        com.example.musicfy.constants.ShowPlayerBottomCardKey,
        defaultValue = true
    )
    val (showAudioQualityBadge) = rememberPreference(
        ShowAudioQualityBadgeKey,
        defaultValue = false
    )
    val (hideAudioQualityBadge) = rememberPreference(
        com.example.musicfy.constants.HideAudioQualityBadgeKey,
        defaultValue = false
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val (playerBackground, onPlayerBackgroundChange) = rememberEnumPreference(
        PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.APPLE_MUSIC
    )
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val enableCanvas by rememberPreference(CanvasThumbnailAnimationKey, true)

    val shouldUseDarkButtonColors = remember(playerBackground, useDarkTheme) {
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH -> true
            PlayerBackgroundStyle.DEFAULT -> useDarkTheme
        }
    }

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val swipeLyrics by rememberPreference(SwipeLyricsKey, false)
    val enableLyricsThumbnailPlayPause by rememberPreference(EnableLyricsThumbnailPlayPauseKey, false)
    val isKeepScreenOn by rememberPreference(KeepScreenOn, false)
    val keepScreenOn = isPlaying && isKeepScreenOn

    DisposableEffect(playerBackground, state.isExpanded, useDarkTheme, keepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH -> {
                    insetsController.isAppearanceLightStatusBars = false
                }
                PlayerBackgroundStyle.DEFAULT -> {
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            if (keepScreenOn && state.isExpanded)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }

    val playbackState by playerConnection.playbackState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val automix by playerConnection.service.automixItems.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val isMuted by playerConnection.isMuted.collectAsState()
    val playerVolume by playerConnection.service.playerVolume.collectAsState()

    val (audioQuality) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (appleMusicDarkChrome) = rememberPreference(
        AppleMusicDarkChromeKey,
        defaultValue = false
    )

    LaunchedEffect(currentSong?.song?.id) {
        val missingArtists = currentSong
            ?.artists
            ?.filter { it.thumbnailUrl.isNullOrBlank() && it.name.isNotBlank() }
            ?.take(2)
            .orEmpty()
        if (missingArtists.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            missingArtists.forEach { artist ->
                val thumbnailUrl = ArtistImageResolver.resolveThumbnail(artist) ?: return@forEach
                database.query {
                    update(
                        artist.copy(
                            thumbnailUrl = thumbnailUrl,
                            lastUpdateTime = java.time.LocalDateTime.now()
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        if (currentLyrics != null) return@LaunchedEffect

        delay(350)
        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.example.musicfy.di.LyricsHelperEntryPoint::class.java
                )
                val lyricsHelper = entryPoint.lyricsHelper()
                val fetchedLyricsWithProvider = lyricsHelper.getLyrics(metadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            metadata.id,
                            fetchedLyricsWithProvider.lyrics,
                            fetchedLyricsWithProvider.provider
                        )
                    )
                }
            } catch (_: Exception) {
                // keep the preview in loading state instead of flashing an unavailable label
            }
        }
    }
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val squigglySlider by rememberPreference(SquigglySliderKey, defaultValue = false)
    
    // listen together state reactive
    
    // cast state safely access castconnectionhandler to prevent crashes during service lifecycle changes
    val castHandler = remember(playerConnection) {
        try {
            playerConnection.service.castConnectionHandler
        } catch (e: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    
    // use cast state when casting otherwise local player
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    // use state objects for position duration to pass to miniplayer without causing recomposition
    // these states persist across playback state changes to ensure continuous progress updates
    val positionState = remember { mutableLongStateOf(0L) }
    val durationState = remember { mutableLongStateOf(0L) }
    
    // convenience accessors for local use
    var position by positionState
    var duration by durationState
    
    val effectivePosition by remember {
        derivedStateOf {
            if (isCasting) {
                castPosition
            } else {
                position
            }
        }
    }
    
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    // track when we last manually set position to avoid cast overwriting it
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }
    
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    val bluetoothDeviceName by produceState<String?>(initialValue = getConnectedBluetoothDeviceName(context)) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = getConnectedBluetoothDeviceName(context)
            }
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
                }
            }
        } else null

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
        
        context.registerReceiver(receiver, filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    val systemVolume by produceState(initialValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(receiver, filter)
        awaitDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (
            playerBackground == PlayerBackgroundStyle.GRADIENT ||
            playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED ||
            playerBackground == PlayerBackgroundStyle.APPLE_MUSIC
        ) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .size(100, 100)
                        .allowHardware(false)
                        .memoryCacheKey("gradient_${currentMetadata.id}")
                        .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(8)
                                    .resizeBitmapArea(100 * 100)
                                    .generate()
                            }
                            val extractedColors = if (playerBackground == PlayerBackgroundStyle.APPLE_MUSIC) {
                                PlayerColorExtractor.extractAppleMusicColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor
                                )
                            } else if (playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
                                listOfNotNull(
                                    palette.getVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getLightVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getDarkVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getMutedColor(fallbackColor).let { Color(it) },
                                    palette.getLightMutedColor(fallbackColor).let { Color(it) },
                                    palette.getDarkMutedColor(fallbackColor).let { Color(it) }
                                ).distinct()
                            } else {
                                PlayerColorExtractor.extractGradientColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor
                                )
                            }
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val useDarkAppleMusicChrome = remember(playerBackground, gradientColors, appleMusicDarkChrome) {
        if (!appleMusicDarkChrome || playerBackground != PlayerBackgroundStyle.APPLE_MUSIC || gradientColors.isEmpty()) {
            false
        } else {
            gradientColors
                .take(4)
                .map { (it.red * 0.299f) + (it.green * 0.587f) + (it.blue * 0.114f) }
                .average() > 0.62
        }
    }

    val TextBackgroundColor by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
            PlayerBackgroundStyle.APPLE_MUSIC -> if (useDarkAppleMusicChrome) Color(0xFF171717) else Color.White
            PlayerBackgroundStyle.LIVE_MESH -> Color.White
        },
        label = "TextBackgroundColor"
    )

    val icBackgroundColor by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            PlayerBackgroundStyle.GRADIENT -> Color.Black
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
            PlayerBackgroundStyle.APPLE_MUSIC -> if (useDarkAppleMusicChrome) Color.White else Color.Black
            PlayerBackgroundStyle.LIVE_MESH -> Color.Black
        },
        label = "icBackgroundColor"
    )

    var canvasArtwork by remember(mediaMetadata?.id) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasFetchInFlight by remember(mediaMetadata?.id) { mutableStateOf(false) }

    LaunchedEffect(mediaMetadata?.id, enableCanvas) {
        if (!enableCanvas) {
            canvasArtwork = null
            return@LaunchedEffect
        }
        val item = mediaMetadata ?: return@LaunchedEffect
        
        // use cached artwork if available
        CanvasArtworkPlaybackCache.get(item.id)?.let { cached ->
            canvasArtwork = cached
            return@LaunchedEffect
        }

        if (canvasFetchInFlight) return@LaunchedEffect
        canvasFetchInFlight = true
        
        withContext(Dispatchers.IO) {
            val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"
            val requestedTitle = item.title
            val requestedArtist = item.artists.joinToString { it.name }
            val requestedAlbum = item.album?.title ?: ""
            
            val s = normalizeCanvasSongTitle(requestedTitle)
            val a = normalizeCanvasArtistName(requestedArtist)
            
            val fetched = linkedSetOf(
                s to a,
                requestedTitle to a,
                s to requestedArtist,
                requestedTitle to requestedArtist,
            ).filter { (song, artist) -> song.isNotBlank() && artist.isNotBlank() }
                .firstNotNullOfOrNull { (song, artist) ->
                    if (requestedAlbum.isNotBlank()) {
                        AppleMusicCanvasProvider.getByAlbumArtist(
                            album = requestedAlbum,
                            artist = artist,
                            storefront = storefront
                        )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                            ?.let { return@firstNotNullOfOrNull it }
                    }

                    MonochromeApiCanvas.getBySongArtist(song, artist, requestedAlbum)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: AppleMusicCanvasProvider.getBySongArtist(song, artist, requestedAlbum, storefront)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                }

            val validated = fetched?.let { artwork ->
                val resultArtist = artwork.artist
                val artistMatches = if (resultArtist != null && requestedArtist.isNotBlank()) {
                    val normalizedResult = normalizeCanvasArtistName(resultArtist)
                    val normalizedRequested = normalizeCanvasArtistName(requestedArtist)
                    resultArtist.contains(requestedArtist, ignoreCase = true) ||
                        requestedArtist.contains(resultArtist, ignoreCase = true) ||
                        normalizedResult.contains(normalizedRequested, ignoreCase = true) ||
                        normalizedRequested.contains(normalizedResult, ignoreCase = true)
                } else true

                val resultAlbum = artwork.albumName
                val resultName = artwork.name
                val titleMatches = when {
                    resultAlbum != null && requestedAlbum.isNotBlank() -> {
                        val normalizedResultAlbum = normalizeCanvasSongTitle(resultAlbum)
                        val normalizedRequestedAlbum = normalizeCanvasSongTitle(requestedAlbum)
                        resultAlbum.contains(requestedAlbum, ignoreCase = true) ||
                            requestedAlbum.contains(resultAlbum, ignoreCase = true) ||
                            normalizedResultAlbum.contains(normalizedRequestedAlbum, ignoreCase = true) ||
                            normalizedRequestedAlbum.contains(normalizedResultAlbum, ignoreCase = true)
                    }
                    resultName != null && requestedTitle.isNotBlank() -> {
                        val normalizedResultName = normalizeCanvasSongTitle(resultName)
                        val normalizedRequestedTitle = normalizeCanvasSongTitle(requestedTitle)
                        val normalizedRequestedAlbum = if (requestedAlbum.isNotBlank()) normalizeCanvasSongTitle(requestedAlbum) else ""
                        resultName.contains(requestedTitle, ignoreCase = true) ||
                            requestedTitle.contains(resultName, ignoreCase = true) ||
                            normalizedResultName.contains(normalizedRequestedTitle, ignoreCase = true) ||
                            normalizedRequestedTitle.contains(normalizedResultName, ignoreCase = true) ||
                            (requestedAlbum.isNotBlank() && (
                                resultName.contains(requestedAlbum, ignoreCase = true) ||
                                    requestedAlbum.contains(resultName, ignoreCase = true) ||
                                    normalizedResultName.contains(normalizedRequestedAlbum, ignoreCase = true) ||
                                    normalizedRequestedAlbum.contains(normalizedResultName, ignoreCase = true)
                                ))
                    }
                    else -> true
                }

                if (artistMatches && titleMatches) artwork else null
            }

            withContext(Dispatchers.Main) {
                canvasArtwork = validated
                if (validated != null) {
                    CanvasArtworkPlaybackCache.put(item.id, validated)
                }
                canvasFetchInFlight = false
            }
        }
    }

    val (textButtonColor, iconButtonColor) = when {
        playerBackground == PlayerBackgroundStyle.BLUR || 
        playerBackground == PlayerBackgroundStyle.GRADIENT ||
        playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED ||
        playerBackground == PlayerBackgroundStyle.APPLE_MUSIC ||
        playerBackground == PlayerBackgroundStyle.LIVE_MESH -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(Color.White, Color.Black)
                PlayerButtonsStyle.PRIMARY -> Pair(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.onTertiary
                )
            }
        }
        else -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT ->
                    if (useDarkTheme) Pair(Color.White, Color.Black)
                    else Pair(Color.Black, Color.White)
                PlayerButtonsStyle.PRIMARY -> Pair(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }

    // separate colors for previous next buttons in primary tertiary modes
    val (sideButtonContainerColor, sideButtonContentColor) = when {
        playerBackground == PlayerBackgroundStyle.BLUR || 
        playerBackground == PlayerBackgroundStyle.GRADIENT -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    Color.White.copy(alpha = 0.2f), 
                    Color.White
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    Color.White.copy(alpha = 0.2f), 
                    Color.White
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        else -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurface
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }
    val inlineLyricsMorphProgress by animateFloatAsState(
        targetValue = if (showInlineLyrics) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 260f
        ),
        label = "inlineLyricsMorphProgress"
    )

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }

    val isPlayerLaunchSettled by remember {
        derivedStateOf { state.isExpanded && state.progress > 0.995f }
    }

    // cache parsed lyrics lines only re parses when lyrics entity changes
    val parsedLyricsLines = remember(currentLyrics, mediaMetadata?.id) {
        val lyricsText = currentLyrics
            ?.takeIf { it.id == mediaMetadata?.id }
            ?.lyrics
            ?.trim()
        if (lyricsText != null && lyricsText.startsWith("[")) {
            try { parseLyrics(lyricsText) } catch (_: Exception) { emptyList() }
        } else emptyList()
    }

    // position duration on demand providers to avoid root recomposition on every position tick
    val positionProvider = remember(playerConnection, isCasting, castPosition) {
        {
            if (isCasting) castPosition
            else try { playerConnection.player.currentPosition } catch (_: Exception) { 0L }
        }
    }
    val durationProvider = remember(playerConnection, isCasting, castDuration) {
        {
            if (isCasting) castDuration
            else try { playerConnection.player.duration.coerceAtLeast(0L) } catch (_: Exception) { 0L }
        }
    }

    var bottomCardLyricsPosition by remember(mediaMetadata?.id) {
        mutableLongStateOf(positionProvider())
    }
    LaunchedEffect(isPlayerLaunchSettled, isPlaying, mediaMetadata?.id) {
        if (isPlayerLaunchSettled && isPlaying) {
            while (isActive) {
                bottomCardLyricsPosition = positionProvider()
                delay(500)
            }
        }
    }

    // current lyrics line for the bottom card preview lightweight lookup per settled position
    val currentLyricsEntry = remember(parsedLyricsLines, bottomCardLyricsPosition) {
        if (parsedLyricsLines.isNotEmpty()) {
            val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
            val idx = findCurrentLineIndex(parsedLyricsLines, bottomCardLyricsPosition + lyricsOffset)
            if (idx >= 0 && idx < parsedLyricsLines.size) parsedLyricsLines[idx] else null
        } else null
    }
    val currentLyricsLine = currentLyricsEntry?.text?.repairPlayerLyricsSpacing()

    // next queue item for the bottom card preview
    val nextQueueMetadata = remember(currentWindowIndex, queueWindows) {
        val nextIdx = currentWindowIndex + 1
        if (nextIdx in queueWindows.indices) {
            queueWindows[nextIdx].mediaItem.metadata
        } else null
    }

    // only update position duration state on explicit playback state or song changes
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = try { playerConnection.player.currentPosition } catch (_: Exception) { 0L }
            duration = try { playerConnection.player.duration.coerceAtLeast(0L) } catch (_: Exception) { 0L }
        }
    }
    
    // when casting use cast position duration directly
    // but wait a bit after manual seeks to let cast catch up
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTime
            if (timeSinceManualSeek > 1500) {
                // only update from cast if we haven t manually seeked recently
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1
    )

    val bottomSheetBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC ->
            MaterialTheme.colorScheme.surfaceContainer
        PlayerBackgroundStyle.LIVE_MESH ->
            Color.Black
        else ->
            if (useBlackBackground) Color.Black
            else MaterialTheme.colorScheme.surfaceContainer
    }


    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        BottomSheet(
            state = state,
            modifier = modifier,
            isPillTransition = true,
            pureBlack = pureBlack,
        background = {
            PlayerBackgroundRenderer(
                playerBackground = playerBackground,
                bottomSheetBackgroundColor = bottomSheetBackgroundColor,
                gradientColors = gradientColors,
                state = state,
                useDarkTheme = useDarkTheme,
                mediaMetadata = mediaMetadata,
                canvasArtwork = canvasArtwork,
                enableCanvas = enableCanvas,
                isPlaying = isPlaying,
            )
        },
        sharedContent = {
            val progressProvider = remember(state) { { state.progress.coerceIn(0f, 1f) } }
            val horizontalOffsetProvider = remember(state) { { state.horizontalOffset } }

            Box(Modifier.fillMaxSize()) {
                MorphingSharedElements(
                    progressProvider = progressProvider,
                    mediaMetadata = mediaMetadata,
                    canvasArtwork = canvasArtwork,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    maxWidth = screenWidth,
                    maxHeight = screenHeight,
                    collapsedBound = state.collapsedBound,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    isAppleMusic = playerBackground == PlayerBackgroundStyle.APPLE_MUSIC,
                    useNewPlayerDesign = useNewPlayerDesign
                )

                if (useNewPlayerDesign) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = ((state.progress - 0.72f) / 0.2f).coerceIn(0f, 1f) }
                    ) {


                        mediaMetadata?.let { metadata ->
                            PressScaleIconButton(
                                icon = R.drawable.more_horiz,
                                tint = TextBackgroundColor,
                                iconSize = 28.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 48.dp, end = 22.dp)
                                    .size(42.dp),
                                onClick = {
                                    menuState.show {
                                        PlayerMenu(
                                            mediaMetadata = metadata,
                                            navController = navController,
                                            playerBottomSheetState = state,
                                            onShowDetailsDialog = {
                                                metadata.id.let {
                                                    bottomSheetPageState.show {
                                                        ShowMediaInfo(it)
                                                    }
                                                }
                                            },
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {},
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val newPlayerHeaderLift = if (useNewPlayerDesign) (-20).dp else 0.dp

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
                    .offset(y = newPlayerHeaderLift)
                    .alpha(if (showInlineLyrics) 0f else 1f),
            ) {
                SongInfo(
                    mediaMetadata = mediaMetadata,
                    showInlineLyrics = showInlineLyrics,
                    onDismissInlineLyrics = { showInlineLyrics = false },
                    hidePlayerThumbnail = hidePlayerThumbnail,
                    isFullScreen = isFullScreen,
                    enableLyricsThumbnailPlayPause = enableLyricsThumbnailPlayPause,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    state = state,
                    swipeLyrics = swipeLyrics,
                    textButtonColor = textButtonColor,
                    TextBackgroundColor = TextBackgroundColor,
                    hideAudioQualityBadge = hideAudioQualityBadge,
                    currentFormat = currentFormat,
                    audioQuality = audioQuality,
                    currentSong = currentSong,
                    playerConnection = playerConnection,
                    navController = navController,
                )

                Spacer(modifier = Modifier.width(12.dp))

                ActionButtons(
                    useNewPlayerDesign = useNewPlayerDesign,
                    showInlineLyrics = showInlineLyrics,
                    mediaMetadata = mediaMetadata,
                    currentSong = currentSong,
                    currentLyrics = currentLyrics,
                    repeatMode = repeatMode,
                    textButtonColor = textButtonColor,
                    TextBackgroundColor = TextBackgroundColor,
                    playerConnection = playerConnection,
                    navController = navController,
                    state = state,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { isFullScreen = !isFullScreen },
                )
            }

            Spacer(Modifier.height(if (useNewPlayerDesign) 12.dp else 8.dp))

            PlayerSlider(
                useNewPlayerDesign = useNewPlayerDesign,
                newPlayerHeaderLift = newPlayerHeaderLift,
                sliderPosition = sliderPosition,
                onSliderPositionChange = { sliderPosition = it },
                effectivePosition = effectivePosition,
                duration = duration,
                isCasting = isCasting,
                castHandler = castHandler,
                onManualSeek = { lastManualSeekTime = System.currentTimeMillis() },
                onPositionChange = { position = it },
                playerConnection = playerConnection,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                sliderStyle = sliderStyle,
                squigglySlider = squigglySlider,
                playerBackground = playerBackground,
                useDarkTheme = useDarkTheme,
                effectiveIsPlaying = effectiveIsPlaying,
                showAudioQualityBadge = showAudioQualityBadge,
                hideAudioQualityBadge = hideAudioQualityBadge,
                sleepTimerEnabled = sleepTimerEnabled,
                onShowSleepTimerDialog = { showSleepTimerDialog = true },
                mediaMetadata = mediaMetadata,
                navController = navController,
                state = state,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                audioQuality = audioQuality,
            )

            Spacer(Modifier.height(if (useNewPlayerDesign) 16.dp else 8.dp))

            PlayerControls(
                isFullScreen = isFullScreen,
                useNewPlayerDesign = useNewPlayerDesign,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                playerConnection = playerConnection,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                effectiveIsPlaying = effectiveIsPlaying,
                playbackState = playbackState,
                isCasting = isCasting,
                castIsPlaying = castIsPlaying,
                castHandler = castHandler,
                castVolume = castVolume,
                systemVolume = systemVolume,
                maxSystemVolume = maxSystemVolume,
                audioManager = audioManager,
                bluetoothDeviceName = bluetoothDeviceName,
            )
        }

        val lyricsSurfaceColor = when (playerBackground) {
            PlayerBackgroundStyle.APPLE_MUSIC,
            PlayerBackgroundStyle.GRADIENT,
            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                adaptiveLyricsCardColor(
                    colors = gradientColors,
                    fallback = MaterialTheme.colorScheme.surfaceContainerHighest,
                    preferLightCard = useDarkAppleMusicChrome
                )
            }
            PlayerBackgroundStyle.LIVE_MESH -> Color(0xFF303035)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // calculate vertical padding like outertune
                val density = LocalDensity.current
                val verticalPadding = max(
                    WindowInsets.systemBars.getTop(density),
                    WindowInsets.systemBars.getBottom(density)
                )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)
                
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets)
                        )
                        .padding(bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    ) {
                        // remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        Box(Modifier.fillMaxSize()) {
                            if (inlineLyricsMorphProgress < 0.99f) {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = (if (state.progress > 0.95f) ((state.progress - 0.95f) * 20f).coerceIn(0f, 1f) else 0f) *
                                                    (1f - inlineLyricsMorphProgress).coerceIn(0f, 1f)
                                        },
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                )
                            }
                            if (inlineLyricsMorphProgress > 0.01f) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showInlineLyrics,
                                    positionProvider = { effectivePosition },
                                    previewLine = currentLyricsLine,
                                    textColor = TextBackgroundColor,
                                    cardColor = lyricsSurfaceColor,
                                    morphProgress = inlineLyricsMorphProgress,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(if (showInlineLyrics) 0.65f else 1f, false)
                            .animateContentSize()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    ) {
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen || useNewPlayerDesign) 0.dp else queueSheetState.collapsedBound,
                    label = "bottomPadding"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = bottomPadding),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        // remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val p = inlineLyricsMorphProgress.coerceIn(0f, 1f)
                            Thumbnail(
                                sliderPositionProvider = sliderPositionProvider,
                                modifier = Modifier
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                                    .graphicsLayer {
                                        alpha = if (state.progress > 0.95f) ((state.progress - 0.95f) * 20f).coerceIn(0f, 1f) else 0f
                                    },
                                isPlayerExpanded = isExpandedProvider,
                                lyricsMorphProgress = p
                            )
                            if (inlineLyricsMorphProgress > 0.01f) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showInlineLyrics,
                                    positionProvider = { effectivePosition },
                                    previewLine = currentLyricsLine,
                                    textColor = TextBackgroundColor,
                                    cardColor = lyricsSurfaceColor,
                                    morphProgress = inlineLyricsMorphProgress,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    val showNewBottomLyricsCard = useNewPlayerDesign && !showInlineLyrics && showPlayerBottomCard

                    if (showNewBottomLyricsCard) {
                        PlayerBottomCards(
                            currentLyricsLine = currentLyricsLine,
                            currentLyricsEntry = currentLyricsEntry,
                            playbackPositionProvider = { effectivePosition },
                            nextQueueTitle = nextQueueMetadata?.title,
                            nextQueueArtist = nextQueueMetadata?.artists?.joinToString { it.name },
                            textColor = TextBackgroundColor,
                            cardColor = lyricsSurfaceColor,
                            onCardTap = {
                                showInlineLyrics = true
                            },
                            modifier = Modifier,
                            revealReady = isPlayerLaunchSettled,
                        )
                    }

                    Spacer(Modifier.height(if (useNewPlayerDesign) 0.dp else 8.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = !isFullScreen && showPlayerBottomCard && !(useNewPlayerDesign && !showInlineLyrics),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
            navController = navController,
            background =
            if (useBlackBackground) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surface // fixed the issue causing the queue ui not good surfacecontainer
            },
            onBackgroundColor = onBackgroundColor,
            TextBackgroundColor = TextBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            pureBlack = pureBlack,
            showInlineLyrics = showInlineLyrics,
            playerBackground = playerBackground,
            onToggleLyrics = {
                showInlineLyrics = !showInlineLyrics
            },
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    previewLine: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    morphProgress: Float = 1f,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.LEFT)
    val lyricsTextSize by rememberPreference(LyricsTextSizeKey, 24f)
    val lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.18f)
    val audioQuality by rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    val (hideAudioQualityBadge) = rememberPreference(
        com.example.musicfy.constants.HideAudioQualityBadgeKey,
        defaultValue = false
    )
    val listState = rememberLazyListState()
    var userTouchedAt by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    val nowPosition = positionProvider()
    val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
    val parsedLines = remember(lyrics) {
        lyrics
            ?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
            ?.let { runCatching { parseLyrics(it) }.getOrDefault(emptyList()) }
            .orEmpty()
    }
    val currentLineIndex = remember(parsedLines, nowPosition, lyricsOffset) {
        findCurrentLineIndex(parsedLines, nowPosition + lyricsOffset)
    }
    val canAutoCenter = showLyrics && parsedLines.isNotEmpty()

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.example.musicfy.di.LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    // handle error
                }
            }
        }
    }

    LaunchedEffect(currentLineIndex, canAutoCenter, userTouchedAt) {
        if (!canAutoCenter || currentLineIndex !in parsedLines.indices) return@LaunchedEffect
        val idleMs = System.currentTimeMillis() - userTouchedAt
        if (userTouchedAt != 0L && idleMs < 6000L) return@LaunchedEffect
        listState.animateScrollToItem(currentLineIndex, scrollOffset = -120)
    }

    LaunchedEffect(userTouchedAt) {
        if (userTouchedAt == 0L) return@LaunchedEffect
        controlsVisible = true
        delay(6000)
        controlsVisible = false
        if (currentLineIndex in parsedLines.indices) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -120)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(parsedLines) {
                detectTapGestures(
                    onPress = {
                        userTouchedAt = System.currentTimeMillis()
                        controlsVisible = true
                        tryAwaitRelease()
                        userTouchedAt = System.currentTimeMillis()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val progress = morphProgress.coerceIn(0f, 1f)
        val startTop = (maxHeight - 150.dp).coerceAtLeast(0.dp)
        val previewTitle = previewLine?.takeIf { it.isNotBlank() } ?: mediaMetadata?.title ?: ""
        val artworkSize = lerp(44.dp, 128.dp, progress)
        val identityTop = lerp(startTop + 34.dp, 24.dp, progress)
        val horizontalPadding = lerp(24.dp, 28.dp, progress)

        when {
            lyrics == null -> {
                ContainedLoadingIndicator(
                    modifier = Modifier.graphicsLayer {
                        alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    }
                )
            }
            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    }
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy((18 * lyricsLineSpacing).dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 34.dp)
                        .padding(top = 176.dp, bottom = 28.dp)
                        .graphicsLayer {
                            alpha = ((progress - 0.28f) / 0.72f).coerceIn(0f, 1f)
                            translationY = (1f - progress) * 26.dp.toPx()
                        }
                ) {
                    item { Spacer(Modifier.height(maxHeight * 0.16f)) }
                    itemsIndexed(parsedLines) { index, entry ->
                        PlayerSyncedLyricsLine(
                            entry = entry,
                            nextEntryTime = parsedLines.getOrNull(index + 1)?.time,
                            isActive = index == currentLineIndex,
                            distanceFromActive = if (currentLineIndex >= 0) kotlin.math.abs(index - currentLineIndex) else 6,
                            effectivePosition = nowPosition + lyricsOffset,
                            textColor = textColor,
                            lyricsPosition = lyricsTextPosition,
                            baseTextSize = lyricsTextSize,
                            lineSpacing = lyricsLineSpacing,
                            onClick = {
                                playerConnection.seekTo((entry.time - lyricsOffset).coerceAtLeast(0))
                                userTouchedAt = System.currentTimeMillis()
                            },
                        )
                    }
                    item {
                        LyricsSourceFooter(
                            provider = currentLyrics?.provider,
                            year = currentSong?.song?.year,
                            color = textColor,
                            modifier = Modifier.padding(top = 20.dp, bottom = 80.dp)
                        )
                    }
                }
            }
        }

        if (previewTitle.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = horizontalPadding)
                    .offset(y = identityTop)
                    .graphicsLayer {
                        alpha = (0.55f + progress * 0.45f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                // reserve exact layout space for single thumbnail animating from center to header
                Spacer(Modifier.size(artworkSize))
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = lerp(0.dp, 10.dp, progress))
                ) {
                    Text(
                        text = mediaMetadata?.title ?: previewTitle,
                        color = textColor,
                        fontSize = lerp(13.sp, 20.sp, progress),
                        lineHeight = lerp(15.sp, 23.sp, progress),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (mediaMetadata?.explicit == true) {
                            Text(
                                text = "E",
                                color = textColor.copy(alpha = 0.82f),
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .border(1.dp, textColor.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (!hideAudioQualityBadge) {
                            AudioFormatBadge(
                                format = currentFormat,
                                tint = Color.Unspecified,
                                height = 16.dp,
                                audioQuality = audioQuality,
                                fallbackId = currentSong?.id
                            )
                        }
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            color = textColor.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontFamily = InterFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && currentLineIndex !in parsedLines.indices && parsedLines.isNotEmpty(),
            enter = fadeIn(tween(180)) + slideInVertically { it / 4 },
            exit = fadeOut(tween(220)) + slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
        ) {
            Text(
                text = "Re-sync",
                color = textColor,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(textColor.copy(alpha = 0.16f))
                    .clickable {
                        userTouchedAt = 0L
                        if (currentLineIndex in parsedLines.indices) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(currentLineIndex, scrollOffset = -120)
                            }
                        }
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSyncedLyricsLine(
    entry: LyricsEntry,
    nextEntryTime: Long?,
    isActive: Boolean,
    distanceFromActive: Int,
    effectivePosition: Long,
    textColor: Color,
    lyricsPosition: LyricsPosition,
    baseTextSize: Float,
    lineSpacing: Float,
    onClick: () -> Unit,
) {
    val alignment = when {
        entry.agent == "v2" -> Alignment.CenterEnd
        entry.agent == "v1000" || entry.isBackground -> Alignment.Center
        entry.agent == "v1" -> Alignment.CenterStart
        lyricsPosition == LyricsPosition.RIGHT -> Alignment.CenterEnd
        lyricsPosition == LyricsPosition.CENTER -> Alignment.Center
        else -> Alignment.CenterStart
    }
    val textAlign = when (alignment) {
        Alignment.CenterEnd -> TextAlign.Right
        Alignment.Center -> TextAlign.Center
        else -> TextAlign.Left
    }
    val horizontalArrangement = when (alignment) {
        Alignment.CenterEnd -> Arrangement.End
        Alignment.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    val activeScale by animateFloatAsState(
        targetValue = if (isActive) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "playerLyricsLineScale"
    )
    val targetAlpha = when {
        isActive -> 1f
        distanceFromActive == 1 -> 0.58f
        distanceFromActive == 2 -> 0.32f
        else -> 0.16f
    }
    val alpha by animateFloatAsState(targetAlpha, tween(280), label = "playerLyricsLineAlpha")
    val lineColor = textColor.copy(alpha = alpha)
    val textSize = if (entry.isBackground) baseTextSize * 0.82f else baseTextSize
    val displayText = remember(entry.text) { entry.text.repairPlayerLyricsSpacing() }
    val words = remember(entry.text, displayText, entry.words) {
        val timed = entry.words.orEmpty()
        if (
            displayText == entry.text.trim() &&
            timed.joinToString(" ") { it.text }.trim() == entry.text.trim()
        ) timed else emptyList()
    }

    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
                transformOrigin = when (alignment) {
                    Alignment.CenterEnd -> TransformOrigin(1f, 0.5f)
                    Alignment.Center -> TransformOrigin(0.5f, 0.5f)
                    else -> TransformOrigin(0f, 0.5f)
                }
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        if (words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                words.forEachIndexed { index, word ->
                    PlayerLyricsWord(
                        word = word,
                        isLineActive = isActive,
                        effectivePosition = effectivePosition,
                        baseColor = textColor,
                        inactiveColor = lineColor,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * lineSpacing.coerceAtMost(1.28f)).sp,
                    )
                    if (index != words.lastIndex) {
                        Text(
                            text = " ",
                            fontSize = textSize.sp,
                            lineHeight = (textSize * lineSpacing.coerceAtMost(1.28f)).sp
                        )
                    }
                }
            }
        } else {
            val lineProgress = remember(effectivePosition, entry.time, nextEntryTime) {
                val end = nextEntryTime ?: (entry.time + 2800L)
                ((effectivePosition - entry.time).toFloat() / (end - entry.time).coerceAtLeast(1L)).coerceIn(0f, 1f)
            }
            Text(
                text = displayText,
                style = if (isActive) {
                    MaterialTheme.typography.bodyMedium.copy(
                        brush = Brush.horizontalGradient(
                            0f to textColor,
                            lineProgress.coerceAtLeast(0.02f) to textColor,
                            (lineProgress + 0.14f).coerceAtMost(1f) to textColor.copy(alpha = 0.42f),
                            1f to textColor.copy(alpha = 0.42f)
                        ),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * lineSpacing.coerceAtMost(1.28f)).sp,
                        textAlign = textAlign
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        color = lineColor,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textSize.sp,
                        lineHeight = (textSize * lineSpacing.coerceAtMost(1.28f)).sp,
                        textAlign = textAlign
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlayerLyricsWord(
    word: WordTimestamp,
    isLineActive: Boolean,
    effectivePosition: Long,
    baseColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val start = (word.startTime * 1000).toLong()
    val end = (word.endTime * 1000).toLong().coerceAtLeast(start + 40L)
    val progress = when {
        effectivePosition >= end -> 1f
        effectivePosition < start || !isLineActive -> 0f
        else -> ((effectivePosition - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    }
    Text(
        text = word.text,
        style = if (isLineActive) {
            MaterialTheme.typography.bodyMedium.copy(
                brush = Brush.horizontalGradient(
                    0f to baseColor,
                    progress.coerceAtLeast(0.01f) to baseColor,
                    (progress + 0.18f).coerceAtMost(1f) to baseColor.copy(alpha = 0.42f),
                    1f to baseColor.copy(alpha = 0.42f)
                ),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = lineHeight,
            )
        } else {
            MaterialTheme.typography.bodyMedium.copy(
                color = inactiveColor,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = lineHeight,
            )
        }
    )
}

@Composable
private fun LyricsSourceFooter(
    provider: String?,
    year: Int?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val source = provider?.takeIf { it.isNotBlank() } ?: "Unknown source"
    val details = listOfNotNull(
        "Lyrics from $source",
        year?.takeIf { it > 0 }?.toString(),
    ).joinToString(" / ")
    Text(
        text = details,
        color = color.copy(alpha = 0.48f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFamily = InterFontFamily,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}


@Composable
fun MoreActionsButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(it)
                                }
                            }
                        },
                        onDismiss = menuState::dismiss
                    )
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor)
        )
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(it)
                                }
                            }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
    ) {
        Image(
            painter = painterResource(R.drawable.more_horiz),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconButtonColor),
        )
    }
}

@Composable


private fun adaptiveLyricsCardColor(
    colors: List<Color>,
    fallback: Color,
    preferLightCard: Boolean,
): Color {
    val palette = colors.take(5).ifEmpty { listOf(fallback) }
    val average = Color(
        red = palette.map { it.red }.average().toFloat(),
        green = palette.map { it.green }.average().toFloat(),
        blue = palette.map { it.blue }.average().toFloat(),
        alpha = 1f
    )
    val luminance = average.red * 0.299f + average.green * 0.587f + average.blue * 0.114f
    val glassBase = when {
        preferLightCard -> average.towardsWhite(0.35f)
        luminance > 0.55f -> average.towardsWhite(0.1f)
        else -> average.towardsWhite(0.18f)
    }
    return glassBase.copy(alpha = 1f)
}

private fun String.repairPlayerLyricsSpacing(): String {
    val compacted = replace(Regex("[\\t\\u00A0]+"), " ").trim()
    if (compacted.isBlank()) return compacted
    if (!Regex(" {2,}").containsMatchIn(compacted)) {
        return compacted.replace(Regex(" +"), " ")
    }

    return compacted
        .split(Regex(" {2,}"))
        .joinToString(" ") { group ->
            val parts = group.trim().split(Regex(" +")).filter { it.isNotBlank() }
            if (parts.size > 1 && parts.all { part -> part.length <= 4 && part.any(Char::isLetter) }) {
                parts.joinToString("")
            } else {
                group.trim().replace(Regex(" +"), " ")
            }
        }
}

private fun Color.towardsBlack(amount: Float): Color =
    Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha
    )

private fun Color.towardsWhite(amount: Float): Color =
    Color(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
        alpha = alpha
    )

@Composable
private fun BackgroundVideoView(
    videoUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }
    
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(4096, 4096)
                .setForceHighestSupportedBitrate(true)
                .build()
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setTargetBufferBytes(20 * 1024 * 1024) // 20mb buffer for 4k
                    .build()
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = isPlaying
            }
    }

    val aspectRatioFrameLayout = remember {
        AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatioFrameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
                }
            }
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoUrl) {
        isVideoReady = false
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(if (videoUrl.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(800),
        label = "videoAlpha"
    )

    AndroidView(
        factory = { _ ->
            aspectRatioFrameLayout.apply {
                // ensure the view doesn t capture touches intended for other sections
                isEnabled = false
                isClickable = false
                isFocusable = false

                // ensure textureview is added only once
                if (childCount == 0) {
                    val textureView = TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    addView(textureView)
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        modifier = modifier.alpha(alpha)
    )
}

