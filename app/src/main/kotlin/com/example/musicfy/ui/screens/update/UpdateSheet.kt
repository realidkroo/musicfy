// update sheet ui and detail sheet

package com.example.musicfy.ui.screens.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.musicfy.BuildConfig
import com.example.musicfy.R
import com.example.musicfy.core.updater.GithubProfileUrl
import com.example.musicfy.core.updater.GithubRelease
import com.example.musicfy.core.updater.GithubRepoUrl
import com.example.musicfy.core.updater.InstagramUrl
import com.example.musicfy.core.updater.UpdateState
import com.example.musicfy.core.updater.downloadApk
import com.example.musicfy.core.updater.apkFileFor
import com.example.musicfy.core.updater.getLatestRelease
import com.example.musicfy.core.updater.formatBytes
import com.example.musicfy.core.updater.installApk
import com.example.musicfy.core.updater.isDownloaded
import com.example.musicfy.core.updater.isNewerThanInstalled
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.player.menu.MenuSheetSurface
import kotlinx.coroutines.launch

private val CardSurface = MenuRowSurface
private val AccentGreen = Color(0xFF2E9E5B)

// update state state holder
@Composable
fun rememberUpdateState(): androidx.compose.runtime.State<UpdateState> {
    val state = remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(Unit) {
        val result = getLatestRelease()
        state.value = result.fold(
            onSuccess = { release ->
                when {
                    release == null -> UpdateState.UpToDate
                    isNewerThanInstalled(release.version) -> UpdateState.Available(release)
                    else -> UpdateState.UpToDate
                }
            },
            onFailure = { UpdateState.Failed(it.message ?: "Couldn't reach GitHub") },
        )
    }
    return state
}

@Composable
fun UpdateSheet(
    state: UpdateState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReveal: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    var detailRelease by remember { mutableStateOf<GithubRelease?>(null) }

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        halfDetent = 0.78f,
        fullDetent = 0.94f,
        revealProvider = onReveal,
    ) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Musicfy ${BuildConfig.VERSION_NAME} by roo",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // the one line that changes: this is the same copy the settings row shows
                text = when (state) {
                    is UpdateState.Available -> UpdateHeadline
                    UpdateState.Checking -> "Checking for updates…"
                    is UpdateState.Failed -> "Couldn't check for updates"
                    UpdateState.UpToDate -> "Latest version"
                },
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            // the update card only exists when there is one "latest version" is the whole
            // no-update state — nothing greyed out nothing to explain
            if (state is UpdateState.Available) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardSurface)
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_musicfy_mark),
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.release.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.release.body.lineSequence()
                                .firstOrNull { it.isNotBlank() }
                                ?.trim()
                                ?.removePrefix("#")
                                ?.trim()
                                .orEmpty()
                                .ifBlank { "No description" },
                            color = Color(0xFF9A9A9A),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentGreen)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { detailRelease = state.release },
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Update",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- dev card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.frame_51_3),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hello",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Im the main dev here",
                            color = Color(0xFF9A9A9A),
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinkRow(R.drawable.star, "Star the repo") { context.openUrl(GithubRepoUrl) }
                Spacer(modifier = Modifier.height(8.dp))
                LinkRow(R.drawable.github, "@realidkroo") { context.openUrl(GithubProfileUrl) }
                Spacer(modifier = Modifier.height(8.dp))
                LinkRow(R.drawable.link, "@realidkroo") { context.openUrl(InstagramUrl) }
                Spacer(modifier = Modifier.height(8.dp))
                // nothing to donate to yet so it reads as unavailable rather than being
                LinkRow(R.drawable.heart, "Donate me!", enabled = false) {}
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Got it!",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    detailRelease?.let { release ->
        UpdateDetailSheet(release = release, onDismiss = { detailRelease = null })
    }
}

// the line the settings row and the sheet both show when an update is waiting
const val UpdateHeadline = "Theres an update~!"

@Composable
private fun LinkRow(
    icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f * alpha))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// changelog what will be installed and the install button wrapheight is the
@Composable
private fun UpdateDetailSheet(release: GithubRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    // same reasoning as updatepromptsheet: keep a finished download and offer a
    // the installer could not be shown instead of fetching the whole apk a
    var needsPermission by remember { mutableStateOf(false) }
    val startInstall: (java.io.File) -> Unit = { file ->
        if (installApk(context, file)) {
            needsPermission = false
        } else {
            needsPermission = true
            error = "Allow \"Install unknown apps\" for musicfy, then tap Install here again."
        }
    }

    MenuSheetSurface(
        onDismiss = onDismiss,
        wrapHeight = true,
        fullDetent = 0.9f,
        // the download writes into the cache and hands off to the installer; letting
        // go mid-flight would strand it with no way back to the progress
        dismissEnabled = !downloading,
    ) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = release.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Changelog",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // verbatim release body no line cap — the sheet grows to fit it and the
                    // scroll above takes over once it hits the 90% ceiling
                    text = release.body.trim().ifBlank { "No changelog for this release." },
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "To Be Installed -",
                color = Color(0xFF8A8A8A),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(14.dp)
            ) {
                AppIconImage(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.apkName ?: "musicfy.apk",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${BuildConfig.APPLICATION_ID} · ${formatBytes(release.apkSizeBytes)}",
                        color = Color(0xFF9A9A9A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = Color(0xFFE0736B), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // install — the progress fills the button itself same as the beta notice's
            val animatedProgress by animateFloatAsState(
                targetValue = progress.floatValue,
                animationSpec = tween(durationMillis = 120),
                label = "downloadProgress",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .clickable(
                        enabled = !downloading && release.apkUrl != null,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            error = null
                            if (isDownloaded(context, release)) {
                                startInstall(apkFileFor(context, release))
                            } else {
                                downloading = true
                                progress.floatValue = 0f
                                scope.launch {
                                    val result = downloadApk(context, release) { progress.floatValue = it }
                                    downloading = false
                                    result.fold(
                                        onSuccess = { startInstall(it) },
                                        onFailure = { error = it.message ?: "Download failed" },
                                    )
                                }
                            }
                        },
                    )
            ) {
                Box(
                    modifier = Modifier
                        // see updatepromptsheet: without this the fill is centred by the parent's
                        // contentalignment and grows out of the middle instead of from the left
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .background(Color(0xFF444444))
                )
                Text(
                    text = when {
                        downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                        release.apkUrl == null -> "No APK in this release"
                        else -> "Install here"
                    },
                    color = Color.White.copy(alpha = if (release.apkUrl == null) 0.5f else 1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .clickable(
                        enabled = !downloading,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { context.openUrl(release.htmlUrl) },
                    )
            ) {
                Text(
                    text = "Open the web",
                    color = Color.White.copy(alpha = if (downloading) 0.5f else 1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}


// the app's own launcher icon as the system actually composites it deliberately
@Composable
internal fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        }.getOrNull()
    }

    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        // only reachable if the packagemanager lookup fails outright the brand mark
        // vector so this path can never re-trigger the crash above
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(CardSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_musicfy_mark),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}
