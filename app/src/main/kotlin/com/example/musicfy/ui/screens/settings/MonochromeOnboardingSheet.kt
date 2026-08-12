// monochromeonboardingsheetkt
// shown once the moment the turn on monochrome backend switch flips on
// backend is then runs a real connectivity probe see
// preference is actually committed the switch only ends up enabled if the

package com.example.musicfy.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.playback.custom.MonochromeConnectivityResult
import com.example.musicfy.playback.custom.testMonochromeConnectivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

private const val MONOCHROME_WEBSITE_URL = "https://monochrome.tf"
private const val MONOCHROME_GITHUB_URL = "https://github.com/monochrome-music/monochrome"

private sealed interface MonochromeSheetState {
    data object Info : MonochromeSheetState
    data object Testing : MonochromeSheetState
    data object Success : MonochromeSheetState
    data class Failed(val reason: String) : MonochromeSheetState
    data object TurnstileNeeded : MonochromeSheetState
}

@Composable
fun MonochromeOnboardingContent(
    onEnabled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MonochromeSheetState>(MonochromeSheetState.Info) }
    val isTesting = state is MonochromeSheetState.Testing
    val isSuccess = state is MonochromeSheetState.Success

    fun runTest() {
        state = MonochromeSheetState.Testing
        coroutineScope.launch {
            // whatever goes wrong a thrown exception not just a returned failure
            // this must always resolve out of testing otherwise the spinner just spins
            // and the sheet never closes no matter what actually failed
            val result = try {
                testMonochromeConnectivity(context)
            } catch (e: Exception) {
                Timber.tag("MonochromeOnboarding").e(e, "Connectivity test threw")
                MonochromeConnectivityResult.Unreachable(e.javaClass.simpleName)
            }
            when (result) {
                is MonochromeConnectivityResult.Success -> {
                    onEnabled()
                    state = MonochromeSheetState.Success
                    delay(900)
                    onDismiss()
                }
                is MonochromeConnectivityResult.Unreachable -> {
                    state = MonochromeSheetState.Failed(result.reason)
                }
                is MonochromeConnectivityResult.TurnstileNeeded -> {
                    state = MonochromeSheetState.TurnstileNeeded
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Color(0xFF161616)),
    ) {
        // drag handle same treatment as betanoticescreen for the same this is a
        // affordance now that this popup reuses that container s zoom out motion
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .width(64.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.5f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 24.dp)
        ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.monochrome_logo),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        val title: String
        val description: String
        val code: String?
        when (val s = state) {
            MonochromeSheetState.Info -> {
                title = "Powered by Monochrome"
                description = "[this currently doenst work. this currently doenst work.this currently doenst work.this currently doenst work.] Thanks to the Monochrome dev, you can play FLAC music on your device. Please support the dev and their incredible passion for this project! You can also directly use Monochrome in your browser — visit their website and their GitHub for more!"
                code = null
            }
            MonochromeSheetState.Testing -> {
                title = "Testing playback & available server"
                description = "This will take a while."
                code = null
            }
            MonochromeSheetState.Success -> {
                title = "You're all set!"
                description = "Monochrome backend is enabled — enjoy Hi-Res FLAC playback."
                code = null
            }
            is MonochromeSheetState.Failed -> {
                title = "Failed to fetch :("
                description = "Sorry, either Monochrome's servers are down/blocked, or the app needs an update. Try checking for an update, or please visit their website instead."
                code = s.reason
            }
            MonochromeSheetState.TurnstileNeeded -> {
                title = "Something wrong"
                description = "You might need to fill the Turnstile challenge, or use the Monochrome website instead. Sorry!"
                code = "turnstile-token-needed"
            }
        }

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color(0xFFB3B3B3),
        )
        if (code != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Code — $code",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF777777),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        MonochromeLinkCard(
            title = "Monochrome Music",
            description = "Stream & download Hi-Res FLACs for free — monochrome.tf",
            icon = R.drawable.monochrome_logo,
            iconIsRaster = true,
            onClick = { uriHandler.openUri(MONOCHROME_WEBSITE_URL) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        MonochromeLinkCard(
            title = "Repo GitHub source",
            description = "Open-source, privacy-respecting, ad-free — github.com",
            icon = R.drawable.github,
            iconIsRaster = false,
            onClick = { uriHandler.openUri(MONOCHROME_GITHUB_URL) },
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { if (!isTesting && !isSuccess) runTest() },
            enabled = !isTesting && !isSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF2E2E2E),
                disabledContentColor = Color.White,
            ),
        ) {
            when {
                isTesting -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                isSuccess -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                else -> Text(text = "Continue", fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

@Composable
private fun MonochromeLinkCard(
    title: String,
    description: String,
    icon: Int,
    iconIsRaster: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconIsRaster) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.chevron_right_px),
            contentDescription = null,
            tint = Color(0xFF666666),
            modifier = Modifier.size(18.dp),
        )
    }
}
