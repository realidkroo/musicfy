// MusicfySettingsScreen.kt

package com.example.musicfy.ui.screens.settings

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.R
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.SettingsHighlight
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.screens.search.SearchAvatar
import com.example.musicfy.ui.screens.search.SearchField
import com.example.musicfy.ui.screens.search.SearchGlassTopBar
import com.example.musicfy.ui.screens.search.rememberScrollCollapseProgress
import com.example.musicfy.ui.screens.search.searchTopBarHeight
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
    SettingsEntry("Offline mode", "General", "general_settings", "play downloaded cache offline"),
    SettingsEntry("Local song auto metadata", "General", "general_settings", "match tags id3 covers lyrics"),

    // Experimental
    SettingsEntry("Import & sync", "Experimental", "experimental_settings", "import spotify apple music deezer tidal tunemymusic csv youtube sync export backup transfer"),
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Advanced audio settings", "Experimental", "experimental_settings", "monochrome lossless hi-res atmos"),
    SettingsEntry("Cipher", "Experimental", "experimental_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "experimental_settings", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),
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
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isSearching = isSearchFocused || query.text.isNotBlank()

    BackHandler(enabled = isSearching) {
        query = TextFieldValue()
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val recommendedItem = remember { RecommendedOptions.random() }

    val results = remember(query.text) {
        val q = query.text.trim()
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

    // Same latch-and-settle collapse as Library/Search: crossing the threshold always finishes
    // the morph, even after the user stops scrolling mid-gesture.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    // Animated fade for content when search bar is tapped
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Scrollable content. glassRoot wraps only this box - never a sibling that also reads
        // glassState to draw the blur, such as SearchGlassTopBar below - or that sibling's draw
        // gets captured into its own backdrop RenderNode, which is a direct self-reference and
        // crashes RenderThread with a native stack overflow (see SearchScreen.kt for the same
        // split: glassRoot on the content box only, the glass top bar as a separate sibling).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress && collapseProgress > 0f }),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(searchTopBarHeight()))

            // Search results view when user types
            AnimatedVisibility(
                visible = query.text.isNotBlank(),
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
                            text = "Nothing matches \"${query.text}\".",
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
        }

        // Top bar: Musicfy mark + "Settings" title slide away on scroll (progressive glass blur
        // behind the gradient, matching Library/Search) while the search field glides up to sit
        // beside the profile avatar.
        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = { collapseProgress },
            pureBlack = true,
            title = "Musicfy",
            titleContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = "Musicfy",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            },
            trailing = {
                SearchAvatar(
                    imageUrl = profilePicUri.ifBlank { null },
                    onClick = { navController.navigate("settings") },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = {},
                placeholder = "Search any settings",
                focused = isSearchFocused,
                onFocusChanged = { isSearchFocused = it },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = TextFieldValue() },
                        )
                    }
                } else {
                    null
                },
            )
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
