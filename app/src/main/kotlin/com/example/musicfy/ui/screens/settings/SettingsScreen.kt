// SettingsScreen.kt

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.BuildConfig
import com.example.musicfy.R
import com.example.musicfy.constants.InnerTubeCookieKey
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.constants.UsernameKey
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.SettingsGroup
import com.example.musicfy.ui.component.SettingsItem
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.utils.rememberPreference
import kotlinx.coroutines.launch
import java.time.LocalTime

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

    val updateReveal = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

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

    val scrollProgressProvider = { (scrollState.value / 120f).coerceIn(0f, 1f) }
    val backgroundColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface

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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { scrollProgressProvider() > 0f })
                    .verticalScroll(scrollState)

                    .padding(
                        top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 24.dp
                    )
            ) {
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = (1f - scrollProgressProvider() * 2f).coerceIn(0f, 1f) }
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (profilePicUri.isNotBlank()) {
                        AsyncImage(
                            model = profilePicUri.takeIf { it.contains("://") } ?: "file://$profilePicUri",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, ${accountName.ifBlank { "there" }}!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { showWip() }
                    ) {
                        Text(
                            text = "view profile",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Musicfy-it",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        title = { Text("Play music on my other device") },
                        description = { Text("mdevice integrations") },
                        icon = painterResource(R.drawable.cast),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { restrictedFeatureName = "Play music on my other device" }
                    ),
                    SettingsItem(
                        title = { Text("Party mode") },
                        description = { Text("make a party room with your fren") },
                        icon = painterResource(R.drawable.group),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { restrictedFeatureName = "Party mode" }
                    ),
                    SettingsItem(
                        title = { Text("Import data") },
                        description = { Text("Import data from other music profider/backup") },
                        icon = painterResource(R.drawable.restore),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { restrictedFeatureName = "Import data" }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        title = { Text("${BuildConfig.VERSION_NAME}") },

                        description = {
                            Text(
                                if (updateState is com.example.musicfy.core.updater.UpdateState.Available) {
                                    com.example.musicfy.ui.screens.update.UpdateHeadline
                                } else {
                                    "Made with <3 by roo! this app is still on DEV stage."
                                }
                            )
                        },

                        icon = painterResource(R.drawable.ic_musicfy_mark),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { showUpdateSheet = true }
                    ),
                    SettingsItem(
                        title = { Text("General") },
                        icon = painterResource(R.drawable.settings),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { restrictedFeatureName = "General settings" }
                    ),
                    SettingsItem(
                        title = { Text("Appearance") },
                        icon = painterResource(R.drawable.contrast),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { navController.navigate("appearance_settings") }
                    ),
                    SettingsItem(
                        title = { Text("Playback") },
                        icon = painterResource(R.drawable.play),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { navController.navigate("playback_settings") }
                    ),
                    SettingsItem(
                        title = { Text("Experimental") },
                        icon = painterResource(R.drawable.biotech),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { navController.navigate("experimental_settings") }
                    ),
                    SettingsItem(
                        title = { Text("Reset app data") },
                        description = { Text("Wipe data and close app") },
                        icon = painterResource(R.drawable.delete_history),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        isHighlighted = true,
                        onClick = { showResetConfirm = true }
                    )
                )
            )

            Spacer(modifier = Modifier.height(180.dp))
            }

            val showTopBlur by remember { derivedStateOf { scrollProgressProvider() > 0.01f } }
            if (showTopBlur) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = scrollProgressProvider() }
                ) {
                    ProgressiveGlassBackground(
                        state = glassState,
                        maxBlurRadius = { 40f * scrollProgressProvider() },
                        foundationColor = Color.Transparent,
                        direction = BlurDirection.BottomToTop,
                        steps = 3,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        backgroundColor.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (profilePicUri.isNotBlank()) {
                                AsyncImage(
                                    model = profilePicUri.takeIf { it.contains("://") } ?: "file://$profilePicUri",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${accountName.ifBlank { "User" }} on musicfy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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
