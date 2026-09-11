// SettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.BuildConfig
import com.example.musicfy.R
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.UsernameKey
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun randomGreetingWord(): String {
    val hour = LocalTime.now().hour
    val timeBased = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val variants = listOf(
        timeBased,
        "Hey",
        "Sup",
        "'Ello",
        "Konnichiwa",
        "Hallo",
        "Halo",
        "Ni hao",
        "Sawadee",
        "Salut",
    )
    return variants.random()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val updateState by com.example.musicfy.ui.screens.update.rememberUpdateState()
    var showUpdateSheet by remember { mutableStateOf(false) }

    val updateReveal = remember { mutableFloatStateOf(0f) }

    val hideAppChrome = com.example.musicfy.LocalHideAppChrome.current
    androidx.compose.runtime.DisposableEffect(showUpdateSheet) {
        hideAppChrome.value = showUpdateSheet
        onDispose { hideAppChrome.value = false }
    }

    val (localUsername) = rememberPreference(UsernameKey, "")
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    var liveAccountName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(innerTubeCookie) {
        if (innerTubeCookie.isBlank()) {
            liveAccountName = null
            return@LaunchedEffect
        }
        com.music.innertube.YouTube.cookie = innerTubeCookie
        com.music.innertube.YouTube.accountInfo()
            .onSuccess { info -> liveAccountName = info.name }
    }
    val accountName = localUsername.ifBlank { liveAccountName.orEmpty() }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val greeting = remember { randomGreetingWord() }

    val database = com.example.musicfy.LocalDatabase.current
    val totalListeningMs by database.totalListeningTimeMs().collectAsState(initial = 0L)

    fun showWip() {
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Work in progress — we haven't built this page yet")
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }
    var restrictedFeatureName by remember { mutableStateOf<String?>(null) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset app data?") },
            text = { Text("This wipes all local data — your library, downloads, playlists, and settings — and closes the app. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
                    activityManager?.clearApplicationUserData()
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Collapse range for smooth morphing: ~135dp
    val collapseThresholdPx = with(density) { 135.dp.toPx() }
    val rawScrollProgress by remember {
        derivedStateOf { (scrollState.value.toFloat() / collapseThresholdPx).coerceIn(0f, 1f) }
    }
    val easedProgress = FastOutSlowInEasing.transform(rawScrollProgress)

    // Expanded header height: statusBarTop + avatar(96) + space(16) + greeting(32) + space(20) = statusBarTop + 164.dp
    val headerExpandedSpacerHeight = statusBarTop + 164.dp
    val topBarHeight = statusBarTop + 86.dp

    Scaffold(
        modifier = Modifier.graphicsLayer {
            val r = updateReveal.floatValue
            if (r > 0.001f) {
                val scale = 1f - 0.08f * r
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(28.dp * r)
                clip = true
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Layer 1: Scrollable Page Content
            Column(
                modifier = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { rawScrollProgress > 0f })
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                // Spacer matching the expanded header bounds
                Spacer(modifier = Modifier.height(headerExpandedSpacerHeight))

                // Listening Time (Exact font size as Figma concept)
                ListeningTimeRow(totalListeningMs = totalListeningMs)

                // No separator line here - clean breathing space to Recap Card as in concept
                Spacer(modifier = Modifier.height(24.dp))

                // Modern Animated Recap Card
                RecapCard(onClick = { showWip() })

                Spacer(modifier = Modifier.height(28.dp))

                SectionHeading("App version")
                SettingsGroup(
                    items = listOf(
                        SettingsItem(
                            title = {
                                Text(
                                    text = BuildConfig.VERSION_NAME,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            description = {
                                Text(
                                    text = if (updateState is com.example.musicfy.core.updater.UpdateState.Available) {
                                        com.example.musicfy.ui.screens.update.UpdateHeadline
                                    } else {
                                        "Made with <3 by roo! this app is still on DEV stage."
                                    },
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            },
                            icon = painterResource(R.drawable.ic_musicfy_mark),
                            iconShape = CircleShape,
                            onClick = { showUpdateSheet = true }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeading("Interconnectivity")
                SettingsGroup(
                    items = listOf(
                        SettingsItem(
                            title = {
                                Text(
                                    text = "My own device",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            description = {
                                Text(
                                    text = "make your device as a remote, or the player, and stream your local music at original quality",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            },
                            icon = painterResource(R.drawable.cast),
                            iconShape = CircleShape,
                            onClick = { restrictedFeatureName = "My own device" }
                        ),
                        SettingsItem(
                            title = {
                                Text(
                                    text = "Party Mode",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            description = {
                                Text(
                                    text = "invite your friend to listen to the same song at same party session.",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            },
                            icon = painterResource(R.drawable.person),
                            iconShape = CircleShape,
                            onClick = { restrictedFeatureName = "Party Mode" }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeading("Settings")
                SettingsGroup(
                    items = listOf(
                        SettingsItem(
                            title = {
                                Text(
                                    text = "Open musicfy settings",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            },
                            icon = painterResource(R.drawable.settings),
                            iconShape = CircleShape,
                            onClick = { navController.navigate("musicfy_settings") }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(180.dp))
            }

            // Layer 2: Progressive Glass Top Bar with Dark Gradient
            if (rawScrollProgress > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarHeight)
                        .align(Alignment.TopCenter)
                ) {
                    ProgressiveGlassBackground(
                        state = glassState,
                        maxBlurRadius = { 55f * rawScrollProgress },
                        foundationColor = Color.Black.copy(alpha = 0.55f * easedProgress),
                        direction = BlurDirection.BottomToTop,
                        steps = 5,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (rawScrollProgress * 1.5f).coerceIn(0f, 1f) }
                    )

                    // Smooth, rich dark gradient dissolving naturally into the black page
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black,
                                        Color.Black.copy(alpha = 0.92f * easedProgress),
                                        Color.Black.copy(alpha = 0.60f * easedProgress),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // Layer 3: Coordinated Morphing Avatar & Greeting Header
            // Avatar smoothly scales from 96dp to 36dp and glides to top bar position
            val avatarSize = lerpDp(96.dp, 36.dp, easedProgress)
            val avatarX = 24.dp
            val avatarY = lerpDp(statusBarTop + 20.dp, statusBarTop + 16.dp, easedProgress)

            Box(
                modifier = Modifier
                    .offset(x = avatarX, y = avatarY)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                if (profilePicUri.isNotBlank()) {
                    AsyncImage(
                        model = profilePicUri.takeIf { it.contains("://") } ?: "file://$profilePicUri",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = accountName.firstOrNull()?.uppercase() ?: "M",
                        fontFamily = InterFontFamily,
                        fontSize = lerpDp(38.dp, 16.dp, easedProgress).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C2C2E)
                    )
                }
            }

            // Greeting text + Edit Profile pill
            val greetingX = lerpDp(24.dp, 72.dp, easedProgress)
            val greetingY = lerpDp(statusBarTop + 132.dp, statusBarTop + 22.dp, easedProgress)
            val textScale = lerpFloat(1.0f, 0.80f, easedProgress)
            val pillAlpha = (1f - rawScrollProgress * 3.5f).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .offset(x = greetingX, y = greetingY)
                    .padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$greeting, ${accountName.ifBlank { "there" }}!",
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = if (easedProgress > 0.5f) FontWeight.SemiBold else FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                )

                if (pillAlpha > 0.01f) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF202024),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f * pillAlpha)),
                        modifier = Modifier
                            .graphicsLayer { alpha = pillAlpha }
                            .clickable { showWip() }
                    ) {
                        Text(
                            text = "edit profile",
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUpdateSheet) {
        com.example.musicfy.ui.screens.update.UpdateSheet(
            state = updateState,
            onDismiss = {
                showUpdateSheet = false
                updateReveal.floatValue = 0f
            },
            onReveal = { updateReveal.floatValue = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (restrictedFeatureName != null) {
        com.example.musicfy.ui.component.RestrictionPopup(
            featureName = restrictedFeatureName!!,
            onDismiss = { restrictedFeatureName = null }
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        fontFamily = InterFontFamily,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = (-0.4).sp,
        color = Color.White,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/**
 * Total time the user has spent listening, split into hours and minutes.
 * Styled exactly to match the Figma concept with large bold numbers and monospace label.
 */
@Composable
private fun ListeningTimeRow(totalListeningMs: Long) {
    val totalMinutes = (totalListeningMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListeningTimeValue(value = hours.toString(), unit = "h")

        ListeningTimeSeparator()

        ListeningTimeValue(value = minutes.toString(), unit = "m")

        ListeningTimeSeparator()

        Text(
            text = "MUSICFY\nLISTENING TIME",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = (-0.2).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun ListeningTimeValue(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-2.0).sp,
            color = Color.White,
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = unit,
            fontFamily = InterFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun ListeningTimeSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.25f))
    )
}

/**
 * Modern Animated Recap Card featuring organic GPU-accelerated waving liquid gradient,
 * overlapping decorative album sleeves, and bold display typography.
 */
@Composable
private fun RecapCard(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "recapAnimation")

    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6800, easing = LinearEasing)
        ),
        label = "wavePhase1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9800, easing = LinearEasing)
        ),
        label = "wavePhase2"
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing)
        ),
        label = "shimmerOffset"
    )

    val wavePath1 = remember { Path() }
    val wavePath2 = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        // GPU Canvas with flowing harmonic waves and subtle specular sheen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Rich base warm gradient
            val baseBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE26D5C),
                    Color(0xFFEB765E),
                    Color(0xFFF3966D),
                    Color(0xFFD45844)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
            drawRect(brush = baseBrush)

            // 2. Wave Layer 1 (fluid primary harmonic curve)
            wavePath1.reset()
            wavePath1.moveTo(0f, h)
            wavePath1.lineTo(0f, h * 0.40f)
            val steps = 36
            val stepW = w / steps
            for (i in 1..steps) {
                val x = i * stepW
                val normX = x / w
                val y = h * 0.46f +
                    sin(normX * 2 * PI + wavePhase1).toFloat() * (h * 0.16f) +
                    cos(normX * 3.5 * PI + wavePhase2).toFloat() * (h * 0.08f)
                wavePath1.lineTo(x, y)
            }
            wavePath1.lineTo(w, h)
            wavePath1.close()

            val wave1Brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF9E7A).copy(alpha = 0.50f),
                    Color(0xFFDE4C34).copy(alpha = 0.35f)
                ),
                startY = h * 0.2f,
                endY = h
            )
            drawPath(path = wavePath1, brush = wave1Brush)

            // 3. Wave Layer 2 (complementary dynamic wave)
            wavePath2.reset()
            wavePath2.moveTo(0f, h)
            wavePath2.lineTo(0f, h * 0.58f)
            for (i in 1..steps) {
                val x = i * stepW
                val normX = x / w
                val y = h * 0.56f +
                    sin(normX * 3 * PI + wavePhase2).toFloat() * (h * 0.13f) -
                    cos(normX * 1.8 * PI + wavePhase1).toFloat() * (h * 0.09f)
                wavePath2.lineTo(x, y)
            }
            wavePath2.lineTo(w, h)
            wavePath2.close()

            val wave2Brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFC7A0).copy(alpha = 0.45f),
                    Color(0xFFE85B3F).copy(alpha = 0.20f)
                ),
                start = Offset(0f, h * 0.4f),
                end = Offset(w, h)
            )
            drawPath(path = wavePath2, brush = wave2Brush)

            // 4. Sweeping diagonal specular sheen
            val shimmerX = shimmerOffset * w
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                start = Offset(shimmerX - w * 0.25f, 0f),
                end = Offset(shimmerX + w * 0.25f, h)
            )
            drawRect(brush = shimmerBrush)
        }

        // Left decorative overlapping sleeve/album cards
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp, y = 8.dp)
        ) {
            // Back white/translucent card sleeve peeking
            Box(
                modifier = Modifier
                    .offset(x = 10.dp, y = (-4).dp)
                    .size(width = 66.dp, height = 78.dp)
                    .rotate(-14f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.40f))
            )

            // Front dark-slate album sleeve peeking
            Box(
                modifier = Modifier
                    .offset(x = 18.dp, y = 8.dp)
                    .size(width = 72.dp, height = 86.dp)
                    .rotate(-5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF6E7480))
            ) {
                // Mini inner vinyl groove accent peeking from corner
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2024))
                )
            }
        }

        // Right Text: "Your 26"re-cap is here"
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 102.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your 26\"re-cap is here",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                letterSpacing = (-0.5).sp,
                color = Color.White,
                lineHeight = 26.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        offset = Offset(0f, 2f),
                        blurRadius = 6f
                    )
                )
            )
        }

        // Elegant glass/card rim border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(1.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(Color.Transparent)
        )
    }
}
