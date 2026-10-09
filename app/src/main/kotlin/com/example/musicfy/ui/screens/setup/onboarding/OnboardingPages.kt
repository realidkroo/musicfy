// OnboardingPages.kt
//
// Every page after the welcome screen, as plain stateless composables. OnboardingFlow decides which
// one shows and wires them to the view model.

package com.example.musicfy.ui.screens.setup.onboarding

import com.example.musicfy.ui.component.rememberDeviceTilt
import androidx.compose.ui.unit.em
import com.example.musicfy.ui.theme.InterFontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.importer.account.AccountImportState
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.importer.account.RemotePlaylist
import com.example.musicfy.viewmodels.YouTubeSyncChoices
import com.music.innertube.models.ArtistItem

private const val GoBack = "hell nah, go back"

/** "Let's build your home" needs this many artists to have something to build from. */
internal const val MinArtists = 3

// ---- 1. Import? ----

@Composable
internal fun ImportAskPage(onYes: () -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.download)) },
        title = "Importing music",
        subtitle = "Do you want to import music from other platforms like Spotify, Apple Music, Tidal or SoundCloud, or sync your data with YouTube Music?",
        footer = {
            OnbButton("Yes!", onClick = onYes)
            OnbButton("Skip", onClick = onSkip, style = OnbButtonStyle.Secondary)
        },
    )
}

// ---- 2. Which kind ----

@Composable
internal fun ImportModePage(onImport: () -> Unit, onBackup: () -> Unit, onBack: () -> Unit, backupError: String?) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.download)) },
        title = "Importing music",
        subtitle = "what do you want?",
        footer = { OnbButton(GoBack, onClick = onBack, style = OnbButtonStyle.Secondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbChoiceCard(
                icon = painterResource(R.drawable.playlist_add),
                title = "Import",
                description = "import music, playlists and albums from supported providers like Apple Music, Spotify, Tidal and YouTube Music",
                onClick = onImport,
            )
            OnbChoiceCard(
                icon = painterResource(R.drawable.backup),
                title = "Import from backup",
                description = "if you by any chance have a Musicfy backup",
                onClick = onBackup,
            )
            if (backupError != null) {
                Text(backupError, color = Color(0xFFFF8A80), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---- 3. Provider ----

@Composable
internal fun ProviderListPage(onPick: (AccountService) -> Unit, onBack: () -> Unit) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.library_music)) },
        title = "Select provider",
        footer = { OnbButton(GoBack, onClick = onBack, style = OnbButtonStyle.Secondary) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                AccountService.SPOTIFY to "Liked Songs and playlists",
                AccountService.APPLE_MUSIC to "Library playlists and Favorite Songs",
                AccountService.YOUTUBE_MUSIC to "Liked music and playlists · also supports sync",
                AccountService.TIDAL to "My Tracks and your playlists",
            ).forEach { (service, description) ->
                OnbCard(modifier = Modifier.fillMaxWidth(), onClick = { onPick(service) }, shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderSquircle(service, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(description, color = Onb.Muted, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

// ---- 4. Setup (instructions; the browser sheet opens over this) ----

@Composable
internal fun ProviderSetupPage(
    service: AccountService,
    state: AccountImportState?,
    browserOpen: Boolean,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingPage(
        badge = { ProviderSquircle(service, size = 40.dp) },
        title = "${service.label} setup",
        subtitle = "You will log in to your account. We never see your password. A web page will open.",
        footer = {
            when (state) {
                is AccountImportState.Failed -> OnbButton(if (state.signedOut) "Sign in again" else "Try again", onClick = onRetry)
                is AccountImportState.Loading, is AccountImportState.Choosing -> Unit
                else -> if (!browserOpen) OnbButton("Open sign-in", onClick = onOpenBrowser)
            }
            OnbButton(GoBack, onClick = onBack, style = OnbButtonStyle.Secondary)
        },
    ) {
        when (state) {
            is AccountImportState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(state.label, color = Onb.Subtitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            is AccountImportState.Failed -> Text(state.message, color = Color(0xFFFF8A80), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            else -> Text(
                "Sign in on ${service.label}'s own page. Once you're in, the page closes and your playlists show up here.",
                color = Onb.Muted,
                fontSize = 14.sp,
            )
        }
    }
}

// ---- 5. What to sync / import ----

@Composable
internal fun WhatToSyncPage(
    service: AccountService,
    state: AccountImportState?,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onConfirm: () -> Unit,
    onCancelReading: () -> Unit,
    onBack: () -> Unit,
) {
    val choosing = state as? AccountImportState.Choosing
    val reading = state as? AccountImportState.Reading
    val selectedCount = choosing?.selected?.size ?: 0
    OnboardingPage(
        badge = { ProviderSquircle(service, size = 40.dp) },
        title = "What to import",
        subtitle = choosing?.accountName?.let { "Signed in as $it" },
        scrollable = false,
        footer = {
            if (reading != null) {
                OnbButton("Stop", onClick = onCancelReading, style = OnbButtonStyle.Secondary)
            } else {
                OnbButton(
                    text = when {
                        selectedCount == 0 -> "Choose what to import"
                        service == AccountService.YOUTUBE_MUSIC -> "Next"
                        else -> "Import $selectedCount"
                    },
                    enabled = selectedCount > 0,
                    onClick = onConfirm,
                )
                OnbButton(GoBack, onClick = onBack, style = OnbButtonStyle.Secondary)
            }
        },
    ) {
        if (reading != null) {
            Text("${reading.label} (${reading.done + 1} of ${reading.total})", color = Onb.Subtitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { reading.done.toFloat() / reading.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Onb.Card,
            )
            return@OnboardingPage
        }
        if (choosing == null) return@OnboardingPage
        val allSelected = selectedCount == choosing.playlists.size && selectedCount > 0
        Text(
            text = if (allSelected) "Select none" else "Select all",
            color = Onb.Subtitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = if (allSelected) onSelectNone else onSelectAll)
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth >= 480.dp) 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (choosing.playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Nothing in this account yet.", color = Onb.Muted, fontSize = 14.sp)
                    }
                }
                items(choosing.playlists, key = { it.id }) { playlist ->
                    CoverTile(playlist = playlist, selected = playlist.id in choosing.selected, onClick = { onToggle(playlist.id) })
                }
            }
        }
    }
}

@Composable
private fun CoverTile(playlist: RemotePlaylist, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = if (selected) 0.94f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Onb.Card)
                .then(if (selected) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier),
        ) {
            when {
                playlist.isLiked -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF5B5B66), Color(0xFF1F1F24)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.favorite), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                playlist.coverUrl != null -> AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        playlist.name.take(1).uppercase(),
                        color = Onb.Muted,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.check), contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val detail = listOfNotNull(playlist.trackCount?.let { "$it songs" }, playlist.subtitle).joinToString(" · ")
        if (detail.isNotEmpty()) Text(detail, color = Onb.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- 6. YouTube only: also keep it in sync? ----

@Composable
internal fun SyncOptionsPage(
    choices: YouTubeSyncChoices,
    onChange: (YouTubeSyncChoices) -> Unit,
    reading: AccountImportState.Reading?,
    onSync: () -> Unit,
    onJustImport: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingPage(
        badge = { ProviderSquircle(AccountService.YOUTUBE_MUSIC, size = 40.dp) },
        title = "Also keep in sync?",
        subtitle = "YouTube Music can stay in step with Musicfy: your playlists, likes and history, both ways.",
        footer = {
            if (reading != null) {
                Text(
                    "${reading.label} (${reading.done + 1} of ${reading.total})",
                    color = Onb.Subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                OnbButton("Import & sync", onClick = onSync)
                OnbButton("Just import", onClick = onJustImport, style = OnbButtonStyle.Secondary)
                OnbButton(GoBack, onClick = onBack, style = OnbButtonStyle.Secondary)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnbToggleCard(
                title = "Sync library on launch",
                description = "Likes, library and the playlists you picked, each time Musicfy opens",
                checked = choices.syncOnLaunch,
                onCheckedChange = { onChange(choices.copy(syncOnLaunch = it)) },
            )
            OnbToggleCard(
                title = "New playlists go to YouTube",
                description = "Playlists you make here are created on YouTube too",
                checked = choices.newPlaylistsToYouTube,
                onCheckedChange = { onChange(choices.copy(newPlaylistsToYouTube = it)) },
            )
            OnbToggleCard(
                title = "Push plays to YouTube history",
                description = "What you play here shows up in your YouTube history",
                checked = choices.reportPlays,
                onCheckedChange = { onChange(choices.copy(reportPlays = it)) },
            )
            OnbToggleCard(
                title = "Bring YouTube history here",
                description = "Plays from your other devices join Musicfy's history",
                checked = choices.copyHistory,
                onCheckedChange = { onChange(choices.copy(copyHistory = it)) },
            )
        }
    }
}

// ---- 7. Profile ----

@Composable
internal fun ProfilePage(
    username: String,
    onUsernameChange: (String) -> Unit,
    photo: Any?,
    onPhotoClick: () -> Unit,
    cardNumber: String,
    joinedText: String,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.person)) },
        title = "Let's create your profile!",
        footer = {
            Text(
                "Musicfy will never store your data online!",
                color = Onb.Faint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            OnbButton("Next", onClick = onNext, enabled = username.isNotBlank())
        },
    ) {
        Text("what's your username?", color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = username,
            onValueChange = { onUsernameChange(it.take(32)) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Onb.Field)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    if (username.isEmpty()) Text("username", color = Onb.Faint, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        Text("Set a profile picture here!", color = Onb.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val deviceTilt = rememberDeviceTilt()
        ProfileIdCard(
            username = username,
            photo = photo,
            cardNumber = cardNumber,
            joinedText = joinedText,
            onPhotoClick = onPhotoClick,
            modifier = Modifier.graphicsLayer {
                rotationX = deviceTilt.rotationX
                rotationY = deviceTilt.rotationY
                cameraDistance = 14f * density
            },
        )
    }
}

// ---- 8. Build your home (only when nothing was imported) ----

@Composable
internal fun BuildHomePage(
    query: String,
    onQueryChange: (String) -> Unit,
    artists: List<ArtistItem>,
    loading: Boolean,
    selected: Set<String>,
    onToggle: (ArtistItem) -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.home)) },
        title = "Let's build your home",
        subtitle = "Select any artist you really like",
        scrollable = false,
        footer = {
            OnbButton(
                text = when (selected.size) {
                    0 -> "you need to pick 3 artists"
                    1 -> "2 more"
                    2 -> "one more artist to pick"
                    else -> "next"
                },
                enabled = selected.size >= MinArtists,
                onClick = onNext,
            )
        },
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontFamily = InterFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Onb.Field)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        if (query.isEmpty()) Text("Search artists", color = Onb.Faint, fontSize = 15.sp)
                        inner()
                    }
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val columns = (maxWidth / 112.dp).toInt().coerceIn(3, 6)
            if (loading && artists.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).size(26.dp), color = Color.White, strokeWidth = 2.dp)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistBubble(artist = artist, selected = artist.id in selected, onClick = { onToggle(artist) })
                }
            }
        }
    }
}

@Composable
private fun ArtistBubble(artist: ArtistItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = if (selected) 0.92f else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(Onb.Card)
                .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.check), contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- 9. Blur warning (weak phones only) ----

/**
 * Fixed, not scrolling: the phone fills everything below the switch, down to the bottom edge, and
 * the button floats over it on a gradient and a progressive blur of the phone behind it. The blur
 * reads a recording of the content box; it sits beside that box, never inside it.
 */
@Composable
internal fun BlurWarningPage(
    disableBlur: Boolean,
    onDisableBlurChange: (Boolean) -> Unit,
    detected: String?,
    onContinue: () -> Unit,
) {
    val glass = remember { GlassState() }
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val side = rememberSidePadding(maxWidth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glass),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = Onb.MaxContentWidth)
                    .fillMaxSize()
                    .padding(top = Onb.TopClearance + 18.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = side)) {
                    PageBadge(painterResource(R.drawable.warning))
                    Spacer(Modifier.height(22.dp))
                    Text("Warning", color = Onb.Title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = OnbTitleTracking)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your phone may be too weak to run blurs. Do you want to disable blur? You can always change this in Settings.",
                        color = Onb.Subtitle,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = OnbSubtitleTracking,
                    )
                    Spacer(Modifier.height(22.dp))
                    OnbToggleCard(
                        title = "Disable Blur",
                        description = detected?.let { "Measured: $it" } ?: "",
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                        leading = { Box(Modifier.size(22.dp).clip(CircleShape).background(Onb.Badge)) },
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Image(
                    painter = painterResource(R.drawable.onboarding_phone_internals),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = side + 6.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp + navBar),
        ) {
            ProgressiveGlassBackground(
                state = glass,
                maxBlurRadius = { 30f },
                direction = BlurDirection.TopToBottom,
                steps = 3,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Onb.Page.copy(alpha = 0.7f), 1f to Onb.Page)),
            )
            OnbButton(
                text = if (disableBlur) "continue" else "no, continue",
                onClick = onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = Onb.MaxContentWidth)
                    .navigationBarsPadding()
                    .padding(horizontal = side, vertical = 18.dp),
            )
        }
    }
}

// ---- 10. Last one ----

@Composable
internal fun LastOnePage(
    autoCrossfade: Boolean,
    onAutoCrossfadeChange: (Boolean) -> Unit,
    aiArtistFilter: Boolean,
    onAiArtistFilterChange: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    OnboardingPage(
        badge = { PageBadge(painterResource(R.drawable.tune)) },
        title = "Last one",
        subtitle = "Disable anything that you don't like",
        footer = { OnbButton("next", onClick = onNext) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbToggleCard(
                title = "Auto Crossfade",
                description = "Automatically crossfades, analysing the pitch, tempo and more to pick the perfect moment",
                checked = autoCrossfade,
                onCheckedChange = onAutoCrossfadeChange,
                header = { CrossfadeBanner() },
            )
            OnbToggleCard(
                title = "AI Artist filter",
                description = "Turning this off may give you AI slop recommendations",
                checked = aiArtistFilter,
                onCheckedChange = onAiArtistFilterChange,
            )
        }
    }
}
