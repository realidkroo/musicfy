// SettingsScreen.kt
// Rebuilt as a hub: profile greeting header, a few "coming later" Musicfy-it cards, a version
// row, then navigation rows into the now-split Appearance/Playback/Experimental screens.
// "General" was dropped per explicit request (not used for now); "Other settings" is now a
// single destructive "Reset app data" action instead of a section.

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

/**
 * Rotates between a time-of-day greeting and a handful of casual/multilingual ones so the
 * header doesn't say the exact same thing every time you open Settings. Re-rolled once per
 * visit (remembered for the composition, not on every recomposition).
 */
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
    // "Hey {name}" means the name set during onboarding (UsernameKey — what ProfileSetupStep
    // actually collects), not the YouTube account name — most users never sign into a YouTube
    // account at all here. A live-fetched signed-in account name is only used as a fallback if
    // no local name was ever set (e.g. setup was skipped somehow).
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
    // Scroll fades this in over the first ~120dp, same feel as Home's hero-scroll-driven top bar.
    val scrollProgressProvider = { (scrollState.value / 120f).coerceIn(0f, 1f) }
    val backgroundColor = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surface

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { scrollProgressProvider() > 0f })
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
            // Profile header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { showWip() }
                    ) {
                        Text(
                            text = "view profile",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showWip() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_less),
                        contentDescription = "More",
                        modifier = Modifier.size(22.dp)
                    )
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
                        description = { Text("Make your device a remote, or the player, and stream your local music at original quality") },
                        icon = painterResource(R.drawable.cast),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { showWip() }
                    ),
                    SettingsItem(
                        title = { Text("Party mode") },
                        description = { Text("Invite your friend to listen to the same song at same party session") },
                        icon = painterResource(R.drawable.group),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { showWip() }
                    ),
                    SettingsItem(
                        title = { Text("Import data") },
                        description = { Text("Import data from other music profiles/musicfy backup") },
                        icon = painterResource(R.drawable.restore),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        onClick = { showWip() }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.info), contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("${BuildConfig.VERSION_NAME} DEV", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Made with <3 by roo! this app is still in DEV stage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                        description = { Text("Wipes all local data and closes the app") },
                        icon = painterResource(R.drawable.delete_history),
                        iconShape = androidx.compose.foundation.shape.CircleShape,
                        isHighlighted = true,
                        onClick = { showResetConfirm = true }
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(180.dp)) // Ensures last item is not covered by mini player
            }

            // Progressive glass blur, GlassKit (not Haze) — fades in once you scroll past the
            // profile header, matching Home's top-bar treatment so it doesn't glow.
            val showTopBlur by remember { derivedStateOf { scrollProgressProvider() > 0.01f } }
            if (showTopBlur) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = scrollProgressProvider() }
                ) {
                    ProgressiveGlassBackground(
                        state = glassState,
                        maxBlurRadius = { 40f * scrollProgressProvider() },
                        foundationColor = backgroundColor,
                        direction = BlurDirection.BottomToTop,
                        steps = 3,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
