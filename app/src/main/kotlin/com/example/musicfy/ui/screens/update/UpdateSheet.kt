// UpdateSheet.kt

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
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
import com.example.musicfy.ui.component.PopupSheetState
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.player.menu.MenuSurface
import com.example.musicfy.ui.screens.donate.showDonateSheet
import kotlinx.coroutines.launch

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

/** Shows the "app version" sheet as a PopupSheet - see docs/popup-sheet.skill.md. */
fun PopupSheetState.showUpdateSheet(updateState: UpdateState) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Got it!", onClick = popup::dismiss) }) {
        UpdateSheetContent(updateState = updateState, popup = popup)
    }
}

private fun PopupSheetState.showUpdateDetailSheet(release: GithubRelease) {
    val popup = this
    popup.show {
        UpdateDetailContent(
            release = release,
            // Pushed on top of the version sheet, so back just pops down to it again.
            onBack = popup::dismiss,
            // Locks this same sheet while a download is in flight. Re-showing the content locked
            // instead would push a second copy of it with fresh, not-downloading state.
            onLocked = popup::setLocked,
        )
    }
}

@Composable
private fun SheetPrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.UpdateSheetContent(updateState: UpdateState, popup: PopupSheetState) {
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Spacer(modifier = Modifier.height(4.dp))

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_musicfy_mark),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Musicfy ${BuildConfig.VERSION_NAME} by roo",
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = when (updateState) {
            is UpdateState.Available -> UpdateHeadline
            UpdateState.Checking -> "Checking for updates…"
            is UpdateState.Failed -> "Couldn't check for updates"
            UpdateState.UpToDate -> "Latest version"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )

    if (updateState is UpdateState.Available) {
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MenuRowSurface)
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MenuSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = updateState.release.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = updateState.release.body.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.removePrefix("#")
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "No description" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { popup.showUpdateDetailSheet(updateState.release) },
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Update",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
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
                Text(text = "Hello", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Im the main dev here",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        LinkRow(R.drawable.heart, "Donate me!", enabled = true) {
            // Same PopupSheetState, so this pushes Donate on top and this sheet recedes
            // behind it instead of closing. See docs/popup-sheet.skill.md.
            popup.showDonateSheet()
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    }
}

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
            .background(MenuSurface.copy(alpha = 0.6f))
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ColumnScope.UpdateDetailContent(
    release: GithubRelease,
    onBack: () -> Unit,
    onLocked: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    val startInstall: (java.io.File) -> Unit = { file ->
        if (!installApk(context, file)) {
            error = "Allow \"Install unknown apps\" for musicfy, then tap Install here again."
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_musicfy_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = release.title,
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
            .background(MenuRowSurface)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(text = "Changelog", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = release.body.trim().ifBlank { "No changelog for this release." },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "To Be Installed -",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(14.dp)
    ) {
        AppIconImage(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.apkName ?: "musicfy.apk",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${BuildConfig.APPLICATION_ID} · ${formatBytes(release.apkSizeBytes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    error?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(18.dp))

    val animatedProgress by animateFloatAsState(
        targetValue = progress.floatValue,
        animationSpec = tween(durationMillis = 120),
        label = "downloadProgress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
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
                        onLocked(true)
                        progress.floatValue = 0f
                        scope.launch {
                            val result = downloadApk(context, release) { progress.floatValue = it }
                            downloading = false
                            onLocked(false)
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
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        Text(
            text = when {
                downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                release.apkUrl == null -> "No APK in this release"
                else -> "Install here"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { context.openUrl(release.htmlUrl) },
            )
    ) {
        Text(text = "Open the web", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MenuSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_musicfy_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}
