package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.musicfy.constants.CrossmixMode
import com.example.musicfy.constants.CrossmixModeKey
import com.example.musicfy.constants.DisableAiFilterKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.importer.account.AccountImportState
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.ui.screens.settings.importsync.onSignInTick
import com.example.musicfy.ui.screens.settings.importsync.CaptureWebContent
import com.example.musicfy.ui.screens.settings.importsync.YouTubeLoginWebContent
import com.example.musicfy.ui.screens.settings.importsync.clearWebSession
import com.example.musicfy.ui.screens.setup.onboarding.BehindScale
import com.example.musicfy.ui.screens.setup.onboarding.BlurCapability
import com.example.musicfy.ui.screens.setup.onboarding.BlurWarningPage
import com.example.musicfy.ui.screens.setup.onboarding.BrowserSpring
import com.example.musicfy.ui.screens.setup.onboarding.BuildHomePage
import com.example.musicfy.ui.screens.setup.onboarding.CardInfo
import com.example.musicfy.ui.screens.setup.onboarding.DoneStep
import com.example.musicfy.ui.screens.setup.onboarding.HelloCardStep
import com.example.musicfy.ui.screens.setup.onboarding.ImportAskPage
import com.example.musicfy.ui.screens.setup.onboarding.ImportModePage
import com.example.musicfy.ui.screens.setup.onboarding.LastOnePage
import com.example.musicfy.ui.screens.setup.onboarding.MinArtists
import com.example.musicfy.ui.screens.setup.onboarding.Onb
import com.example.musicfy.ui.screens.setup.onboarding.OnbButton
import com.example.musicfy.ui.screens.setup.onboarding.OnboardingBrowserSheet
import com.example.musicfy.ui.screens.setup.onboarding.ProfilePage
import com.example.musicfy.ui.screens.setup.onboarding.ProviderListPage
import com.example.musicfy.ui.screens.setup.onboarding.ProviderSetupPage
import com.example.musicfy.ui.screens.setup.onboarding.SyncOptionsPage
import com.example.musicfy.ui.screens.setup.onboarding.WhatToSyncPage
import com.example.musicfy.utils.rememberEnumPreference
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.viewmodels.OnboardingViewModel
import com.example.musicfy.viewmodels.YouTubeSyncChoices
import com.music.innertube.models.ArtistItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class Step {
    Welcome, ImportAsk, ImportMode, Providers, ProviderSetup, WhatToSync, SyncOptions,
    Profile, Hello, BuildHome, BlurWarning, LastOne, Done,
}

/** Pages that play once and move on by themselves; back never returns to them. */
private val AutoSteps = setOf(Step.Hello, Step.Done)

/**
 * Onboarding. The welcome page is the original; everything after it follows the "edit page"
 * mocks: import (or sync) → provider → sign in, in a browser sheet inside this sheet → pick
 * playlists → profile card → Hello → (artists, if nothing was imported) → (blur warning, on slow
 * phones) → Last one → Done.
 */
@Composable
fun SetupWizardScreen(
    onComplete: (String, Uri?) -> Unit,
    onDrag: (Float) -> Unit,
    onDragRelease: () -> Unit,
) {
    val vm: OnboardingViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val history = remember { mutableStateListOf(Step.Welcome) }
    var forward by remember { mutableStateOf(true) }
    val step = history.last()

    LaunchedEffect(Unit) { vm.reset() }

    fun go(next: Step, replaceCurrent: Boolean = false) {
        forward = true
        if (replaceCurrent && history.size > 1) history.removeAt(history.lastIndex)
        history.add(next)
    }

    // ---- profile ----
    var username by rememberSaveable { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUncroppedUri by remember { mutableStateOf<Uri?>(null) }
    var lastPickedUri by remember { mutableStateOf<Uri?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    var isLeavingWelcome by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            lastPickedUri = picked
            selectedUncroppedUri = picked
        }
    }
    fun openPhotoPicker() = photoPicker.launch(
        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    // ---- import ----
    var backupError by remember { mutableStateOf<String?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val error = vm.importBackup(uri)
                backupError = error
                if (error == null) go(Step.Profile)
            }
        }
    }

    val session = vm.session
    val stateFlow: StateFlow<AccountImportState?> = remember(session) { session?.state ?: MutableStateFlow(null) }
    val sessionState by stateFlow.collectAsState()
    val readyFlow = remember(session) { session?.ready ?: MutableStateFlow(null) }
    val readyImport by readyFlow.collectAsState()
    var syncChoices by remember { mutableStateOf(YouTubeSyncChoices()) }

    // ---- the browser sheet ----
    val browserPresence = remember { Animatable(0f) }
    var browserShown by remember { mutableStateOf(false) }
    var browserWanted by remember { mutableStateOf(false) }

    fun openBrowser() {
        if (session == null || browserWanted) return
        browserWanted = true
        browserShown = true
        scope.launch { browserPresence.animateTo(1f, BrowserSpring) }
    }

    fun closeBrowser() {
        if (!browserWanted) return
        browserWanted = false
        scope.launch {
            browserPresence.animateTo(0f, BrowserSpring)
            if (!browserWanted) browserShown = false
        }
    }

    fun back() {
        if (history.size <= 1 || step in AutoSteps) return
        forward = false
        val leaving = history.removeAt(history.lastIndex)
        // leaving the sign-in or its playlists returns to choosing a provider, signed out of this one
        if (leaving == Step.ProviderSetup || leaving == Step.WhatToSync) {
            closeBrowser()
            vm.closeProvider()
        }
    }

    // the provider page opens its browser after a beat, once the user has read what's about to happen
    LaunchedEffect(step, session) {
        if (step != Step.ProviderSetup || session == null) return@LaunchedEffect
        delay(900)
        val state = session.state.value
        if (state is AccountImportState.SigningIn || state is AccountImportState.NeedsYouTubeSignIn) openBrowser()
    }

    // signed in and the library is read: close the browser and show the playlists
    LaunchedEffect(sessionState, step) {
        if (step == Step.ProviderSetup && sessionState is AccountImportState.Choosing) {
            closeBrowser()
            delay(250)
            go(Step.WhatToSync, replaceCurrent = true)
        }
    }

    // the picked playlists are read: the importer takes them from here, onboarding moves on
    LaunchedEffect(readyImport) {
        val parsed = readyImport ?: return@LaunchedEffect
        vm.startImport(parsed)
        session?.consumeReady()
        go(Step.Profile)
    }

    // the account's own name and picture start the profile, once
    LaunchedEffect(step) {
        if (step != Step.Profile || prefilled) return@LaunchedEffect
        prefilled = true
        if (username.isBlank()) vm.accountName()?.let { username = it }
        if (profilePicUri == null) {
            val photo = vm.accountPhoto()
            if (profilePicUri == null && photo != null) profilePicUri = photo
        }
    }

    // ---- artists ----
    var artistQuery by rememberSaveable { mutableStateOf("") }
    val pickedArtists = remember { mutableStateMapOf<String, ArtistItem>() }
    val suggestions by vm.artistSuggestions.collectAsState()
    val searchResults by vm.artistResults.collectAsState()
    LaunchedEffect(step) { if (step == Step.BuildHome) vm.loadArtistSuggestions() }
    LaunchedEffect(artistQuery) {
        delay(350)
        vm.searchArtists(artistQuery)
    }

    // ---- settings the last pages flip ----
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (crossmixMode, onCrossmixModeChange) = rememberEnumPreference(CrossmixModeKey, defaultValue = CrossmixMode.AUTO_CROSSFADE)
    val (disableAiFilter, onDisableAiFilterChange) = rememberPreference(DisableAiFilterKey, defaultValue = false)

    val card = CardInfo(username = username, photo = profilePicUri, cardNumber = vm.cardNumber, joinedText = vm.joinedText)

    fun afterHome(): Step = if (vm.blur?.verdict == BlurCapability.Verdict.Laggy) Step.BlurWarning else Step.LastOne

    BackHandler(enabled = !browserWanted) {
        if (selectedUncroppedUri != null) selectedUncroppedUri = null else back()
    }

    PhotoCropperContainer(
        uri = selectedUncroppedUri,
        onDone = { cropped ->
            profilePicUri = cropped
            selectedUncroppedUri = null
        },
        onCancel = { selectedUncroppedUri = null },
        onSelectNewImage = { openPhotoPicker() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Onb.Page),
        ) {
            // the page, receding like a covered sheet while the browser is open over it
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = browserPresence.value
                        val s = 1f - (1f - BehindScale) * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        if (p > 0f) {
                            shape = RoundedCornerShape(32.dp)
                            clip = true
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val auto = targetState in AutoSteps || initialState in AutoSteps
                        when {
                            auto -> (fadeIn(tween(420)) + scaleIn(tween(520), initialScale = 0.96f)) togetherWith fadeOut(tween(260))
                            forward -> slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { -it / 4 } + fadeOut(tween(220))
                            else -> slideInHorizontally(tween(380)) { -it / 3 } + fadeIn(tween(320)) togetherWith
                                slideOutHorizontally(tween(380)) { it / 4 } + fadeOut(tween(220))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "onboardingStep",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    when (current) {
                        // black like the welcome page itself, so no grey strip shows above it
                        Step.Welcome -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.fillMaxSize().padding(top = 56.dp)) { WelcomeStep(isHiding = isLeavingWelcome) }
                            OnbButton(
                                text = "Next",
                                onClick = {
                                    isLeavingWelcome = true
                                    scope.launch {
                                        delay(400)
                                        go(Step.ImportAsk)
                                        isLeavingWelcome = false
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 28.dp, vertical = 18.dp),
                            )
                        }

                        Step.ImportAsk -> ImportAskPage(
                            onYes = { go(Step.ImportMode) },
                            onSkip = { go(Step.Profile) },
                        )

                        Step.ImportMode -> ImportModePage(
                            onImport = { go(Step.Providers) },
                            onBackup = {
                                backupError = null
                                backupPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                            },
                            onBack = ::back,
                            backupError = backupError,
                        )

                        Step.Providers -> ProviderListPage(
                            onPick = { service ->
                                vm.startProvider(service)
                                go(Step.ProviderSetup)
                            },
                            onBack = ::back,
                        )

                        Step.ProviderSetup -> {
                            val service = session?.service ?: AccountService.SPOTIFY
                            ProviderSetupPage(
                                service = service,
                                state = sessionState,
                                browserOpen = browserWanted,
                                onOpenBrowser = ::openBrowser,
                                onRetry = {
                                    clearWebSession(service.webOrigins)
                                    session?.signInAgain()
                                    openBrowser()
                                },
                                onBack = ::back,
                            )
                        }

                        Step.WhatToSync -> WhatToSyncPage(
                            service = session?.service ?: AccountService.SPOTIFY,
                            state = sessionState,
                            onToggle = { session?.toggle(it) },
                            onSelectAll = { session?.selectAll() },
                            onSelectNone = { session?.selectNone() },
                            onConfirm = {
                                // YouTube Music can also stay in sync; the others just import
                                if (session?.service == AccountService.YOUTUBE_MUSIC) go(Step.SyncOptions) else session?.readSelected()
                            },
                            onCancelReading = { session?.cancelReading() },
                            onBack = ::back,
                        )

                        Step.SyncOptions -> SyncOptionsPage(
                            choices = syncChoices,
                            onChange = { syncChoices = it },
                            reading = sessionState as? AccountImportState.Reading,
                            onSync = {
                                // synced playlists come in through sync itself, linked rather than copied
                                val selected = (sessionState as? AccountImportState.Choosing)?.selected.orEmpty()
                                vm.startYouTubeSync(selected, likedId = "LM", choices = syncChoices)
                                go(Step.Profile)
                            },
                            onJustImport = { session?.readSelected() },
                            onBack = { if (sessionState is AccountImportState.Reading) session?.cancelReading() else back() },
                        )

                        Step.Profile -> ProfilePage(
                            username = username,
                            onUsernameChange = { username = it },
                            photo = profilePicUri,
                            onPhotoClick = {
                                val existing = lastPickedUri
                                if (existing != null) selectedUncroppedUri = existing else openPhotoPicker()
                            },
                            cardNumber = vm.cardNumber,
                            joinedText = vm.joinedText,
                            onNext = { if (username.isNotBlank()) go(Step.Hello) },
                        )

                        Step.Hello -> HelloCardStep(
                            card = card,
                            onProbe = vm::onBlurProbe,
                            onFinished = {
                                go(if (!vm.broughtMusicIn) Step.BuildHome else afterHome(), replaceCurrent = true)
                            },
                        )

                        Step.BuildHome -> BuildHomePage(
                            query = artistQuery,
                            onQueryChange = { artistQuery = it },
                            artists = (searchResults ?: suggestions).let { shown ->
                                // picked ones stay visible even when a search doesn't include them
                                (pickedArtists.values.filter { picked -> shown.none { it.id == picked.id } } + shown)
                            },
                            loading = suggestions.isEmpty() && searchResults == null,
                            selected = pickedArtists.keys,
                            onToggle = { artist ->
                                if (artist.id in pickedArtists) pickedArtists.remove(artist.id) else pickedArtists[artist.id] = artist
                            },
                            onNext = {
                                if (pickedArtists.size >= MinArtists) {
                                    vm.saveArtists(pickedArtists.values.toList())
                                    go(afterHome())
                                }
                            },
                        )

                        Step.BlurWarning -> BlurWarningPage(
                            disableBlur = disableBlur,
                            onDisableBlurChange = onDisableBlurChange,
                            detected = vm.blur?.summary,
                            onContinue = { go(Step.LastOne) },
                        )

                        Step.LastOne -> LastOnePage(
                            autoCrossfade = crossmixMode != CrossmixMode.OFF,
                            onAutoCrossfadeChange = { on -> onCrossmixModeChange(if (on) CrossmixMode.AUTO_CROSSFADE else CrossmixMode.OFF) },
                            aiArtistFilter = !disableAiFilter,
                            onAiArtistFilterChange = { on -> onDisableAiFilterChange(!on) },
                            onNext = { go(Step.Done) },
                        )

                        Step.Done -> DoneStep(
                            card = card,
                            onFinished = {
                                vm.saveCard()
                                vm.closeProvider()
                                onComplete(username.trim(), profilePicUri)
                            },
                        )
                    }
                }
            }

            if (browserShown && session != null) {
                // dims the page behind, and a tap on that strip closes the browser
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = browserPresence.value }
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::closeBrowser,
                        ),
                )
                OnboardingBrowserSheet(
                    service = session.service,
                    presence = browserPresence,
                    onClose = ::closeBrowser,
                ) { onUrlChange, onLoadingChange, modifier ->
                    if (session.service == AccountService.YOUTUBE_MUSIC) {
                        YouTubeLoginWebContent(
                            onSignedIn = {
                                closeBrowser()
                                session.refreshYouTube()
                            },
                            onExit = ::closeBrowser,
                            onUrlChange = onUrlChange,
                            onLoadingChange = onLoadingChange,
                            modifier = modifier,
                        )
                    } else {
                        CaptureWebContent(
                            startUrl = session.service.loginUrl.orEmpty(),
                            playerOrigins = session.service.playerOrigins,
                            desktopSite = session.service.desktopSite,
                            onHeader = session::onHeader,
                            onTick = { view -> session.service.onSignInTick(view, session::onApplePageProbe, session::onCookies) },
                            onExit = ::closeBrowser,
                            onUrlChange = onUrlChange,
                            onLoadingChange = onLoadingChange,
                            modifier = modifier,
                        )
                    }
                }
            }

            // the onboarding sheet's own handle; dragging it rubber-bands the whole sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = onDragRelease,
                            onDragCancel = onDragRelease,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
