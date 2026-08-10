package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.musicfy.constants.EnableMonochromeBackendKey
import com.example.musicfy.importer.ParsedImport
import com.example.musicfy.ui.screens.settings.MonochromeOnboardingContent
import com.example.musicfy.utils.rememberPreference
import com.example.musicfy.importer.parseTuneMyMusicCsv
import com.example.musicfy.viewmodels.SetupImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PAGE_WELCOME = 0
private const val PAGE_PROFILE = 1
private const val PAGE_GREETING = 2
private const val PAGE_SETUP_FURTHER = 3
private const val PAGE_IMPORT_PROVIDER = 4
private const val PAGE_TMM_INSTRUCTIONS = 5
private const val PAGE_SELECT_CSV = 6
private const val PAGE_REVIEW_IMPORT = 7
private const val PAGE_TOGGLES = 8
private const val PAGE_MONOCHROME_CHOICE = 9
private const val PAGE_MONOCHROME_INFO = 10
private const val PAGE_THANK_YOU = 11
private const val PAGE_COUNT = 12

@Composable
fun SetupWizardScreen(
    onComplete: (String, Uri?) -> Unit,
    onDrag: (Float) -> Unit,
    onDragRelease: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val importViewModel: SetupImportViewModel = hiltViewModel()

    var username by remember { mutableStateOf("") }
    var profilePicUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUncroppedUri by remember { mutableStateOf<Uri?>(null) }
    // Kept so tapping the avatar again reopens the cropper on the original photo instead of
    // forcing a re-pick.
    var lastPickedUri by remember { mutableStateOf<Uri?>(null) }
    var isLeavingWelcome by remember { mutableStateOf(false) }

    val (_, onEnableMonochromeBackendChange) = rememberPreference(
        EnableMonochromeBackendKey,
        defaultValue = false
    )

    var parsedImport by remember { mutableStateOf<ParsedImport?>(null) }
    var csvLoading by remember { mutableStateOf(false) }
    var csvError by remember { mutableStateOf<String?>(null) }

    fun goTo(page: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(page) }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked: Uri? ->
        if (picked != null) {
            lastPickedUri = picked
            selectedUncroppedUri = picked
        }
    }

    fun openPhotoPicker() {
        photoPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        csvLoading = true
        csvError = null
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Couldn't open that file")
                }.mapCatching { text -> parseTuneMyMusicCsv(text) }
            }
            csvLoading = false
            result.onSuccess { parsed ->
                if (parsed.totalSongs == 0) {
                    csvError = "Couldn't find any songs in that file — make sure it's the CSV exported from tunemymusic.com."
                } else {
                    parsedImport = parsed
                    goTo(PAGE_REVIEW_IMPORT)
                }
            }.onFailure {
                csvError = "Couldn't read that file. Make sure it's a valid .csv export."
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == PAGE_WELCOME) {
            isLeavingWelcome = false
        }
        if (pagerState.settledPage == PAGE_GREETING) {
            kotlinx.coroutines.delay(3000)
            pagerState.animateScrollToPage(PAGE_SETUP_FURTHER)
        }
    }

    BackHandler {
        if (selectedUncroppedUri != null) {
            selectedUncroppedUri = null
        } else if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    PhotoCropperContainer(
        uri = selectedUncroppedUri,
        onDone = { croppedUri ->
            profilePicUri = croppedUri
            selectedUncroppedUri = null
        },
        onCancel = {
            selectedUncroppedUri = null
        },
        onSelectNewImage = { openPhotoPicker() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = {
                            onDragRelease()
                        },
                        onDragCancel = {
                            onDragRelease()
                        }
                    )
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color(0xFF121212)) // Dark gray/black surface
        ) {
            // Pager Content (fill screen first, drawing behind overlays)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false, // Must use buttons to navigate
                modifier = Modifier.fillMaxSize()
            ) { page ->
                // Pass content padding to non-welcome pages so they don't overlap with the top drag handle
                val pageModifier = Modifier.fillMaxSize().padding(top = 56.dp)
                when (page) {
                    PAGE_WELCOME -> WelcomeStep(isHiding = isLeavingWelcome)
                    PAGE_PROFILE -> Box(pageModifier) {
                        ProfileSetupStep(
                            username = username,
                            onUsernameChange = { username = it },
                            profilePicUri = profilePicUri,
                            onProfileTap = {
                                // Already have a photo? Go straight back to the adjust frame; the
                                // cropper itself offers "Select new image".
                                val existing = lastPickedUri
                                if (existing != null) selectedUncroppedUri = existing else openPhotoPicker()
                            }
                        )
                    }
                    PAGE_GREETING -> Box(pageModifier) {
                        GreetingStep(username = username, profilePicUri = profilePicUri)
                    }
                    PAGE_SETUP_FURTHER -> Box(pageModifier) {
                        SetupFurtherStep(profilePicUri = profilePicUri)
                    }
                    PAGE_IMPORT_PROVIDER -> Box(pageModifier) {
                        ImportProviderStep()
                    }
                    PAGE_TMM_INSTRUCTIONS -> Box(pageModifier) {
                        TuneMyMusicInstructionsStep()
                    }
                    PAGE_SELECT_CSV -> Box(pageModifier) {
                        SelectCsvStep(
                            isLoading = csvLoading,
                            errorMessage = csvError,
                            onPickFile = { csvPickerLauncher.launch("text/*") }
                        )
                    }
                    PAGE_REVIEW_IMPORT -> Box(pageModifier) {
                        parsedImport?.let { ReviewImportStep(parsed = it) }
                    }
                    PAGE_TOGGLES -> Box(pageModifier) {
                        WouldYouLikeToggles()
                    }
                    PAGE_MONOCHROME_CHOICE -> Box(pageModifier) {
                        MonochromeChoiceStep()
                    }
                    PAGE_MONOCHROME_INFO -> MonochromeOnboardingContent(
                        onEnabled = { onEnableMonochromeBackendChange(true) },
                        onDismiss = { goTo(PAGE_THANK_YOU) }
                    )
                    PAGE_THANK_YOU -> Box(pageModifier) {
                        ThankYouStep()
                    }
                }
            }

            // Morphing Profile Picture Overlay
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                val screenWidth = maxWidth

                // Calculate the exact floating scroll position
                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction

                // Show overlay between page 0 (transitioning out) and page 3 (transitioning in)
                if (pageOffset > 0f && pageOffset < 3f) {
                    val progress = (pageOffset - 1f).coerceIn(0f, 1f)

                    val page1Y = 242.dp
                    val page1X = 32.dp // Fixed: left aligned to match hitbox
                    val page1Size = 140.dp

                    val page2Y = screenHeight - 333.dp
                    val page2X = 32.dp
                    val page2Size = 110.dp

                    val currentY = androidx.compose.ui.unit.lerp(page1Y, page2Y, progress)
                    val currentSize = androidx.compose.ui.unit.lerp(page1Size, page2Size, progress)

                    var currentX = androidx.compose.ui.unit.lerp(page1X, page2X, progress)

                    // Make it slide in with Page 1 when coming from Welcome screen
                    if (pageOffset < 1f) {
                        currentX += (screenWidth * (1f - pageOffset))
                    }
                    // Make it slide out with Page 2 when going to Setup Further screen
                    else if (pageOffset > 2f) {
                        currentX -= (screenWidth * (pageOffset - 2f))
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = currentX, y = currentY)
                            .size(currentSize)
                            .clip(CircleShape)
                            .background(Color(0xFF707070)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUri != null) {
                            coil3.compose.AsyncImage(
                                model = profilePicUri,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (progress == 0f) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Picture",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            // Drag Handle overlaid on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }

        // Bottom Action Bar overlaid on top
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            when (pagerState.currentPage) {
                PAGE_THANK_YOU -> WizardButton(text = "Done", onClick = { onComplete(username, profilePicUri) })

                PAGE_GREETING -> {
                    // Auto-advances via the LaunchedEffect above — no button needed.
                }

                PAGE_PROFILE -> WizardButton(
                    text = "Next",
                    enabled = username.isNotBlank(),
                    onClick = { if (username.isNotBlank()) goTo(PAGE_GREETING) }
                )

                PAGE_WELCOME -> WizardButton(
                    text = "Next",
                    onClick = {
                        isLeavingWelcome = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(400)
                            pagerState.animateScrollToPage(PAGE_PROFILE)
                        }
                    }
                )

                PAGE_SETUP_FURTHER -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardButton(text = "Continue", onClick = { goTo(PAGE_IMPORT_PROVIDER) })
                    WizardButton(text = "Skip", secondary = true, onClick = { goTo(PAGE_TOGGLES) })
                }

                PAGE_IMPORT_PROVIDER -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardButton(text = "Continue", onClick = { goTo(PAGE_TMM_INSTRUCTIONS) })
                    WizardButton(text = "Skip", secondary = true, onClick = { goTo(PAGE_TOGGLES) })
                }

                PAGE_TMM_INSTRUCTIONS -> WizardButton(text = "Next", onClick = { goTo(PAGE_SELECT_CSV) })

                PAGE_SELECT_CSV -> WizardButton(text = "Skip Wizard", secondary = true, onClick = { goTo(PAGE_TOGGLES) })

                PAGE_REVIEW_IMPORT -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val songCount = parsedImport?.totalSongs ?: 0
                    WizardButton(
                        text = "Import $songCount song${if (songCount == 1) "" else "s"}",
                        onClick = {
                            parsedImport?.let { importViewModel.startImport(it) }
                            goTo(PAGE_TOGGLES)
                        }
                    )
                    WizardButton(text = "Skip Wizard", secondary = true, onClick = { goTo(PAGE_TOGGLES) })
                }

                PAGE_TOGGLES -> WizardButton(text = "Continue", onClick = { goTo(PAGE_MONOCHROME_CHOICE) })

                PAGE_MONOCHROME_CHOICE -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardButton(text = "this thing does not work rn go tap the no button", secondary = true, onClick = { goTo(PAGE_MONOCHROME_INFO) })//work- yes button
                    WizardButton(text = "No (recommended)", highlighted = true, onClick = { goTo(PAGE_THANK_YOU) })
                }

                PAGE_MONOCHROME_INFO -> {
                    // The Monochrome page carries its own Continue button (it runs the probe).
                }
            }
        }
        }
    }
}

@Composable
private fun WizardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    highlighted: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                highlighted -> Color.White
                secondary -> Color(0xFF2A2A2A)
                else -> Color(0xFF333333)
            },
            disabledContainerColor = Color(0xFF222222)
        ),
        shape = CircleShape
    ) {
        Text(
            text,
            color = when {
                !enabled -> Color.Gray
                highlighted -> Color.Black
                else -> Color.White
            },
            fontWeight = FontWeight.Bold
        )
    }
}
