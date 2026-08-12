package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun ProfileSetupStep(
    username: String,
    onUsernameChange: (String) -> Unit,
    profilePicUri: Uri?,
    onProfileTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(48.dp)) // moved up slightly
        
        Text(
            text = "Firstly",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = (-2).sp // changed letter spacing
        )
        
        Spacer(modifier = Modifier.height(4.dp)) // reduced spacing
        
        Text(
            text = "Set your username and profile picture, its for\nwhen you want to listen with your friend.",
            fontSize = 16.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 22.sp,
            letterSpacing = (-0.5).sp
        )
        
        Spacer(modifier = Modifier.height(40.dp)) // adjusted
        
        // profile picture placeholder (visuals drawn by setupwizardscreen overlay
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileTap)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "username",
            fontSize = 14.sp, // made smaller
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // a plain basictextfield instead of outlinedtextfield: the material field
        // for a label/placeholder so squeezing it into 48dp clipped the typed text
        BasicTextField(
            value = username,
            onValueChange = onUsernameChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF2C2C2C)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                }
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 120.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_info),
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Your data will never be on the musicfy server", // changed text
                fontSize = 12.sp,
                color = Color(0xFFD0D0D0),
                fontWeight = FontWeight.Normal, // not bold
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
