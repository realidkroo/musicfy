// WipRestrictedContent.kt
// The "Sorry, work in progress" popup shown in place of a handful of screens/sections that
// aren't ready yet — reuses the same ZoomOutPopupContainer motion and dark-card styling as the
// Monochrome onboarding sheet and BetaNoticeScreen, so all three read as one visual system.

package com.example.musicfy.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

@Composable
fun WipRestrictedContent(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Color(0xFF161616)),
    ) {
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
            Box(modifier = Modifier.size(72.dp)) {
                Image(
                    painter = painterResource(R.drawable.wip_restricted_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
                // "AWFUL" badge — matches the reference mockup's mood-down sticker treatment.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-6).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF7C5CFC))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_downward),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "AWFUL",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Sorry",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Work in progress — right now it's restricted :(",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color(0xFFB3B3B3),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "But don't worry! You can see how this screen looks right now by compiling the main branch yourself!",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color(0xFFB3B3B3),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "err — 403 forbidden",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF777777),
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text(text = "Got it!", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Global so every restricted entry point shares one popup instance via [LocalZoomOutOverlayState]. */
val LocalShowWipRestricted = compositionLocalOf<() -> Unit> {
    error("No WIP-restricted trigger provided")
}
