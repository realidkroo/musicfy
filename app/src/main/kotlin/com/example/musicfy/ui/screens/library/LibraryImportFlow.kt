// LibraryImportFlow.kt
//
// "Sync and add your music" from the Library: the onboarding's own import pages - pick a provider,
// sign in on its page in a browser sheet inside this sheet, choose what to bring in, and for
// YouTube Music whether to keep it in sync - in one sheet over the Library, instead of sending the
// user off to the Experimental import screens. The pages themselves are the onboarding's, unchanged;
// only the steps around them live here. The import runs in the background once started, and the
// Library's banner turns into its progress bar.

package com.example.musicfy.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.musicfy.importer.account.AccountImportState
import com.example.musicfy.importer.account.AccountService
import com.example.musicfy.ui.component.PopupSheetHandle
import com.example.musicfy.ui.component.PopupSheetState
import com.example.musicfy.ui.screens.settings.importsync.CaptureWebContent
import com.example.musicfy.ui.screens.settings.importsync.YouTubeLoginWebContent
import com.example.musicfy.ui.screens.settings.importsync.clearWebSession
import com.example.musicfy.ui.screens.settings.importsync.onSignInTick
import com.example.musicfy.ui.screens.setup.onboarding.BehindScale
import com.example.musicfy.ui.screens.setup.onboarding.BrowserSpring
import com.example.musicfy.ui.screens.setup.onboarding.Onb
import com.example.musicfy.ui.screens.setup.onboarding.OnboardingBrowserSheet
import com.example.musicfy.ui.screens.setup.onboarding.ProviderListPage
import com.example.musicfy.ui.screens.setup.onboarding.ProviderSetupPage
import com.example.musicfy.ui.screens.setup.onboarding.SyncOptionsPage
import com.example.musicfy.ui.screens.setup.onboarding.WhatToSyncPage
import com.example.musicfy.viewmodels.OnboardingViewModel
import com.example.musicfy.viewmodels.YouTubeSyncChoices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class ImportStep { Providers, ProviderSetup, WhatToSync, SyncOptions }

/**
 * The popup sheet's own handle row sits above the content. The onboarding pages leave room for a
 * handle of their own at their top, so they're lifted under this one instead of stacking a second
 * gap below it.
 */
private val SheetHandleRow = 32.dp

/** Opens the import sheet over whatever is showing: the onboarding's import pages, in one sheet. */
fun PopupSheetState.showLibraryImportSheet() {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(fullBleed = true, surface = Onb.Page) {
        LibraryImportFlow(onClose = { sheet?.dismiss() })
    }
}

@Composable
private fun LibraryImportFlow(onClose: () -> Unit) {
    val vm: OnboardingViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()

    val history = remember { mutableStateListOf(ImportStep.Providers) }
    var forward by remember { mutableStateOf(true) }
    val step = history.last()

    fun go(next: ImportStep, replaceCurrent: Boolean = false) {
        forward = true
        if (replaceCurrent && history.size > 1) history.removeAt(history.lastIndex)
        history.add(next)
    }

    val session = vm.session
    val stateFlow: StateFlow<AccountImportState?> = remember(session) { session?.state ?: MutableStateFlow(null) }
    val sessionState by stateFlow.collectAsState()
    val readyFlow = remember(session) { session?.ready ?: MutableStateFlow(null) }
    val readyImport by readyFlow.collectAsState()
    var syncChoices by remember { mutableStateOf(YouTubeSyncChoices()) }

    // ---- the browser sheet, inside this one ----
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

    fun finish() {
        closeBrowser()
        onClose()
    }

    fun back() {
        if (history.size <= 1) {
            onClose()
            return
        }
        forward = false
        val leaving = history.removeAt(history.lastIndex)
        // leaving the sign-in or its playlists returns to choosing a provider, signed out of this one
        if (leaving == ImportStep.ProviderSetup || leaving == ImportStep.WhatToSync) {
            closeBrowser()
            vm.closeProvider()
        }
    }

    // the sheet going away, however it went, ends any sign-in it started
    DisposableEffect(Unit) {
        onDispose { vm.closeProvider() }
    }

    // the provider page opens its browser after a beat, once the user has read what's about to happen
    LaunchedEffect(step, session) {
        if (step != ImportStep.ProviderSetup || session == null) return@LaunchedEffect
        delay(900)
        val state = session.state.value
        if (state is AccountImportState.SigningIn || state is AccountImportState.NeedsYouTubeSignIn) openBrowser()
    }

    // signed in and the library is read: close the browser and show what can be brought in
    LaunchedEffect(sessionState, step) {
        if (step == ImportStep.ProviderSetup && sessionState is AccountImportState.Choosing) {
            closeBrowser()
            delay(250)
            go(ImportStep.WhatToSync, replaceCurrent = true)
        }
    }

    // the picked playlists are read: the importer takes them from here and the sheet is done
    LaunchedEffect(readyImport) {
        val parsed = readyImport ?: return@LaunchedEffect
        vm.startImport(parsed)
        session?.consumeReady()
        finish()
    }

    // back walks the pages; on the first one the sheet's own back handler closes it
    BackHandler(enabled = !browserWanted && history.size > 1) { back() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .layout { measurable, constraints ->
                // up under the popup sheet's handle row: see SheetHandleRow
                val lift = SheetHandleRow.roundToPx()
                val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + lift else constraints.maxHeight
                val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + lift, maxHeight = maxHeight))
                layout(placeable.width, (placeable.height - lift).coerceAtLeast(0)) {
                    placeable.place(0, -lift)
                }
            },
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
                        shape = RoundedCornerShape(28.dp)
                        clip = true
                    }
                },
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (forward) {
                        slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(320)) togetherWith
                            slideOutHorizontally(tween(380)) { -it / 4 } + fadeOut(tween(220))
                    } else {
                        slideInHorizontally(tween(380)) { -it / 3 } + fadeIn(tween(320)) togetherWith
                            slideOutHorizontally(tween(380)) { it / 4 } + fadeOut(tween(220))
                    }.using(SizeTransform(clip = false))
                },
                label = "libraryImportStep",
                modifier = Modifier.fillMaxSize(),
            ) { current ->
                when (current) {
                    ImportStep.Providers -> ProviderListPage(
                        onPick = { service ->
                            vm.startProvider(service)
                            go(ImportStep.ProviderSetup)
                        },
                        onBack = ::back,
                    )

                    ImportStep.ProviderSetup -> {
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

                    ImportStep.WhatToSync -> WhatToSyncPage(
                        service = session?.service ?: AccountService.SPOTIFY,
                        state = sessionState,
                        onToggle = { session?.toggle(it) },
                        onSelectAll = { session?.selectAll() },
                        onSelectNone = { session?.selectNone() },
                        onConfirm = {
                            // YouTube Music can also stay in sync; the others just import
                            if (session?.service == AccountService.YOUTUBE_MUSIC) go(ImportStep.SyncOptions) else session?.readSelected()
                        },
                        onCancelReading = { session?.cancelReading() },
                        onBack = ::back,
                    )

                    ImportStep.SyncOptions -> SyncOptionsPage(
                        choices = syncChoices,
                        onChange = { syncChoices = it },
                        reading = sessionState as? AccountImportState.Reading,
                        onSync = {
                            // synced playlists come in through sync itself, linked rather than copied
                            val selected = (sessionState as? AccountImportState.Choosing)?.selected.orEmpty()
                            vm.startYouTubeSync(selected, likedId = "LM", choices = syncChoices)
                            finish()
                        },
                        onJustImport = { session?.readSelected() },
                        onBack = { if (sessionState is AccountImportState.Reading) session?.cancelReading() else back() },
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
    }
}
