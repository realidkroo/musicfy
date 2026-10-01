// MusicfySettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.UsernameKey
import com.example.musicfy.ui.component.SettingsHighlight
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.utils.rememberPreference

private data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
) {
    val highlightKey: String get() = title
}

private val SettingsIndex = listOf(
    // Appearance
    SettingsEntry("Customize player", "Appearance", "appearance_settings", "player customise cover style"),
    SettingsEntry("Customize lyrics tab", "Appearance", "appearance_settings", "lyrics letter typography font"),
    SettingsEntry("Lyrics Shader", "Appearance", "appearance_settings", "lyrics glow wave letters bloom shader"),
    SettingsEntry("High quality bloom", "Appearance", "appearance_settings", "lyrics bloom quality extra soft pass"),
    SettingsEntry("Always enable Youtube background", "Appearance", "appearance_settings", "youtube video background canvas"),
    SettingsEntry("Enable animated canvas", "Appearance", "appearance_settings", "canvas animation spotify looping visual"),
    SettingsEntry("Blur", "Appearance", "appearance_settings", "blur glass performance lag strength"),
    SettingsEntry("Animation", "Appearance", "appearance_settings", "element blur animator transition animation"),

    // Audio
    SettingsEntry("CrossMix", "Audio", "crossmix_settings", "automix crossfade transition gapless"),
    SettingsEntry("Equalizer", "Audio", "equalizer", "eq bass treble 10 band"),
    SettingsEntry("Audio Quality", "Audio", "audio_settings", "bitrate stream high low lossless"),
    SettingsEntry("Skip Silence", "Audio", "audio_settings", "silence trim gap interval"),
    SettingsEntry("Audio Normalization", "Audio", "audio_settings", "loudness volume gain balance"),

    // Playback
    SettingsEntry("Keep playing even when closed", "Playback", "playback_settings", "background close stop playback"),
    SettingsEntry("Save Player's last state", "Playback", "playback_settings", "persistent queue restore track state"),

    // General
    SettingsEntry("Import Data", "General", "general_settings", "backup restore transfer library spotify"),
    SettingsEntry("Edit profile", "General", "general_settings", "username avatar account"),
    SettingsEntry("Reset app data", "General", "general_settings", "wipe delete erase clear"),
    SettingsEntry("Offline mode", "General", "general_settings", "play downloaded cache offline"),
    SettingsEntry("Local song auto metadata", "General", "general_settings", "match tags id3 covers lyrics"),

    // Experimental
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Advanced audio settings", "Experimental", "experimental_settings", "monochrome lossless hi-res atmos"),
    SettingsEntry("Cipher", "Experimental", "experimental_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "experimental_settings", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),

    // Other
    SettingsEntry("Reset app data", "Other settings", "other_settings", "wipe delete erase clear"),
)

private data class RecommendedSetting(
    val title: String,
    val breadcrumb: String,
    val iconRes: Int,
    val onClick: (NavController) -> Unit,
)

private val RecommendedOptions = listOf(
    RecommendedSetting(
        title = "Configure Blur settings",
        breadcrumb = "settings>appearance",
        iconRes = R.drawable.contrast,
        onClick = { nav ->
            SettingsHighlight.pending = "Blur"
            nav.navigate("appearance_settings")
        }
    ),
    RecommendedSetting(
        title = "Configure Crossfade settings",
        breadcrumb = "settings>audio",
        iconRes = R.drawable.linear_scale,
        onClick = { nav ->
            nav.navigate("crossmix_settings")
        }
    ),
    RecommendedSetting(
        title = "Configure Equalizer settings",
        breadcrumb = "settings>audio",
        iconRes = R.drawable.equalizer,
        onClick = { nav ->
            nav.navigate("equalizer")
        }
    )
)

private val BubbleColor = Color(0xFF1C1C1E)
private val IconBgColor = Color(0xFF333338)

@Composable
fun MusicfySettingsScreen(navController: NavController) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val (username) = rememberPreference(UsernameKey, "")

    var query by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isSearching = isSearchFocused || query.isNotBlank()

    BackHandler(enabled = isSearching) {
        query = ""
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val recommendedItem = remember { RecommendedOptions.random() }

    val results = remember(query) {
        val q = query.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            SettingsIndex.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.keywords.contains(q, ignoreCase = true) ||
                    it.page.contains(q, ignoreCase = true)
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarContentPaddingTop = statusBarTop + 14.dp
    val topBarHeight = topBarContentPaddingTop + 48.dp

    // Continuous scroll animation for top bar (animates to end without pausing halfway)
    val isScrolled = scrollState.value > with(density) { 14.dp.toPx() }
    val scrollProgress by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "scrollProgress"
    )

    // Animated fade for content when search bar is tapped
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Space for top bar with reduced spacing to Settings text
            Spacer(modifier = Modifier.height(topBarHeight + 2.dp))

            // Large "Settings" title
            Text(
                text = "Settings",
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.6).sp,
                    color = Color.White
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar (properly sized, no search icon, no outline, perfectly aligned)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(BubbleColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search any settings",
                                    fontFamily = InterFontFamily,
                                    fontSize = 16.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (query.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = "" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search results view when user types
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 20 },
                exit = fadeOut(tween(120, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = "Nothing matches \"$query\".",
                            fontFamily = InterFontFamily,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    } else {
                        results.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BubbleColor)
                                    .clickable {
                                        SettingsHighlight.pending = entry.highlightKey
                                        navController.navigate(entry.route)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(IconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.settings),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(11.dp))
                                    Column {
                                        Text(
                                            text = entry.title,
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = entry.page,
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Normal Settings Content (fades smoothly to black when search is focused)
            if (contentAlpha > 0.001f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    // Recommended Item Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BubbleColor)
                            .clickable { recommendedItem.onClick(navController) }
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = "Reccomended item",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(IconBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(recommendedItem.iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = recommendedItem.title,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = recommendedItem.breadcrumb,
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Separate Bubble Pills for Categories (No outlines, individually bubble-wrapped)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryBubble(
                            title = "General",
                            iconRes = R.drawable.settings,
                            onClick = { navController.navigate("general_settings") }
                        )

                        CategoryBubble(
                            title = "Appearance",
                            iconRes = R.drawable.contrast,
                            onClick = { navController.navigate("appearance_settings") }
                        )

                        CategoryBubble(
                            title = "Playback",
                            iconRes = R.drawable.play,
                            onClick = { navController.navigate("playback_settings") }
                        )

                        CategoryBubble(
                            title = "Audio",
                            iconRes = R.drawable.graphic_eq,
                            onClick = { navController.navigate("audio_settings") }
                        )

                        CategoryBubble(
                            title = "Experimental settings",
                            iconRes = R.drawable.biotech,
                            onClick = { navController.navigate("experimental_settings") }
                        )

                        CategoryBubble(
                            title = "Other Settings",
                            iconRes = R.drawable.settings,
                            onClick = { navController.navigate("other_settings") }
                        )
                    }
                }
            }

            val bottomInset = com.example.musicfy.LocalPlayerAwareWindowInsets.current.asPaddingValues()
            val bottomPadding = bottomInset.calculateBottomPadding()
            val bottomSpacerHeight = maxOf(340.dp, bottomPadding + 160.dp)

            // Headroom spacer for smooth scrolling past collapse threshold and bottom bar
            Spacer(modifier = Modifier.height(bottomSpacerHeight))
        }

        // Top Bar (Musicfy Mark + User Profile, with zero-overlap scroll animation)
        val topBarBgAlpha = (scrollProgress * 0.88f).coerceIn(0f, 0.95f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = topBarBgAlpha),
                            Color.Black.copy(alpha = topBarBgAlpha * 0.85f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarContentPaddingTop, start = 20.dp, end = 20.dp)
            ) {
                // Left side: Musicfy Mark morphing to "Settings" Text
                // Phase 1 (0f to 0.45f): Icon slides left and fades out
                val iconFraction = (scrollProgress / 0.45f).coerceIn(0f, 1f)
                val iconAlpha = 1f - iconFraction
                val iconTranslationX = with(density) { (-28.dp * iconFraction).toPx() }

                if (iconAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                alpha = iconAlpha
                                translationX = iconTranslationX
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { navController.navigateUp() }
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_musicfy_mark),
                            contentDescription = "Musicfy",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Phase 2 (0.45f to 0.9f): "Settings" title slides in and fades/unblurs (NO overlap!)
                val titleFraction = ((scrollProgress - 0.45f) / 0.45f).coerceIn(0f, 1f)
                val titleAlpha = titleFraction
                val titleTranslationX = with(density) { (20.dp * (1f - titleFraction)).toPx() }

                if (titleAlpha > 0.001f) {
                    val blurPx = (1f - titleFraction) * 12f
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                alpha = titleAlpha
                                translationX = titleTranslationX
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.2f) {
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                        blurPx,
                                        blurPx,
                                        android.graphics.Shader.TileMode.CLAMP
                                    ).asComposeRenderEffect()
                                }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { navController.navigateUp() }
                            )
                    ) {
                        Text(
                            text = "Settings",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }

                // Right side: User Profile Avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { navController.navigate("settings") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePicUri.isNotBlank()) {
                        AsyncImage(
                            model = profilePicUri.takeIf { it.contains("://") } ?: "file://$profilePicUri",
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = username.firstOrNull()?.uppercase() ?: "M",
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBubble(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(BubbleColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
    }
}
