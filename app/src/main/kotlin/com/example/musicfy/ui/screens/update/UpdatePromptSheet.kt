// UpdatePromptSheet.kt
// The "there's an update" prompt that greets you on Home.
//
// Same content and the same install flow as UpdateDetailSheet — changelog, what will be installed,
// install / open-the-web — but framed as an invitation rather than a page you navigated to: a big
// app mark at the top, the user's name in the heading, and a third button that puts it off for a
// day. Deliberately a clone rather than a shared composable with a flag: the two differ in enough
// small ways (icon size, heading, snooze, which detent they open at) that a parameterised version
// would be mostly branches, and the detail sheet is reachable from Settings where a "remind me
// later" makes no sense at all.

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.BuildConfig
import com.example.musicfy.R
import com.example.musicfy.core.updater.GithubRelease
import com.example.musicfy.core.updater.downloadApk
import com.example.musicfy.core.updater.formatBytes
import com.example.musicfy.core.updater.installApk
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.player.menu.MenuSheetSurface
import kotlinx.coroutines.launch

private val CardSurface = MenuRowSurface

/**
 * @param userName shown in the heading. Blank falls back to a name-less greeting rather than
 *   printing an empty gap or the word "null".
 * @param onSnooze put it off for a day — the caller records the timestamp.
 */
@Composable
fun UpdatePromptSheet(
    release: GithubRelease,
    userName: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReveal: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        wrapHeight = true,
        fullDetent = 0.92f,
        // Same reasoning as the detail sheet: the download lands in the cache and hands off to the
        // system installer, so letting the sheet go mid-flight would strand it.
        dismissEnabled = !downloading,
        revealProvider = onReveal,
    ) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp)
                .animateContentSize()
        ) {
            // The big mark. This is the one real difference from the detail sheet's 22dp inline
            // icon — here it is the first thing you see, so it carries the whole framing.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (userName.isBlank()) "Update available!" else "Update available, $userName!",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "What changed on ${release.version}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
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
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
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

            Spacer(modifier = Modifier.height(22.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress.floatValue,
                animationSpec = tween(durationMillis = 120),
                label = "promptDownloadProgress",
            )
            PromptButton(
                enabled = !downloading && release.apkUrl != null,
                progress = animatedProgress,
                label = when {
                    downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                    release.apkUrl == null -> "No APK in this release"
                    else -> "Install here"
                },
                dimmed = release.apkUrl == null,
                onClick = {
                    downloading = true
                    error = null
                    progress.floatValue = 0f
                    scope.launch {
                        val result = downloadApk(context, release) { progress.floatValue = it }
                        downloading = false
                        result.fold(
                            onSuccess = { installApk(context, it) },
                            onFailure = { error = it.message ?: "Download failed" },
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = "Open the web",
                dimmed = downloading,
                onClick = { context.openReleaseUrl(release.htmlUrl) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = "no thanks remind me in 24 hour",
                dimmed = downloading,
                onClick = onSnooze,
            )
        }
    }
}

/**
 * The prompt's button shape. [progress] fills it from the left, which is how the install button
 * shows download progress without needing a separate bar.
 */
@Composable
private fun PromptButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    progress: Float = 0f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(Color(0xFF444444))
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun android.content.Context.openReleaseUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
