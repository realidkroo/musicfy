// DonateSheet.kt

package com.example.musicfy.ui.screens.donate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.ui.component.PopupSheetState
import com.example.musicfy.ui.player.menu.MenuRowSurface
import com.example.musicfy.ui.theme.InterFontFamily

const val KofiUrl = "https://ko-fi.com/realidkroo"
const val SocialBuzzUrl = "https://sociabuzz.com/realidkroo/tribe"
const val BuyMeACoffeeUrl = "https://buymeacoffee.com/realkeichii"

/** Shows Donate as a plain PopupSheet - see docs/popup-sheet.skill.md before copying this file. */
fun PopupSheetState.showDonateSheet() {
    show(buttonBar = { DonateDoneButton(onClick = ::dismiss) }) {
        DonateSheetContent()
    }
}

@Composable
private fun DonateDoneButton(onClick: () -> Unit) {
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
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Done",
            fontFamily = InterFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ColumnScope.DonateSheetContent() {
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Spacer(modifier = Modifier.height(12.dp))

    // Author Profile Picture
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Color(0xFF333333)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.frame_51_3),
            contentDescription = "roo profile picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = "Donate Me!",
        fontFamily = InterFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = "Or buy me a coffee! thanks for donating :3 your donation, no matter how small it is is very meaninfful for me, a solo developer",
        fontFamily = InterFontFamily,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(24.dp))

    DonateOptionRow(
        title = "Support me on KoFi!",
        onClick = { context.openBrowserUrl(KofiUrl) },
        iconContent = {
            Image(
                painter = painterResource(R.drawable.ic_kofi),
                contentDescription = "Ko-fi",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp)
            )
        }
    )

    Spacer(modifier = Modifier.height(10.dp))

    DonateOptionRow(
        title = "Support me with SocialBuzz!",
        subtitle = "reccomended for some SEA and LATAM countries",
        onClick = { context.openBrowserUrl(SocialBuzzUrl) },
        iconContent = {
            Icon(
                painter = painterResource(R.drawable.link),
                contentDescription = "SocialBuzz",
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }
    )

    Spacer(modifier = Modifier.height(10.dp))

    DonateOptionRow(
        title = "Buy me a coffee!",
        onClick = { context.openBrowserUrl(BuyMeACoffeeUrl) },
        iconContent = {
            Icon(
                painter = painterResource(R.drawable.ic_bmc_logo),
                contentDescription = "Buy me a coffee",
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    )

    Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DonateOptionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconContent: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small icon circle - fixed white, not themed: the brand marks inside (Ko-fi, BMC,
        // SocialBuzz) are only legible against a light badge regardless of app theme.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Context.openBrowserUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    } catch (_: Exception) {}
}
