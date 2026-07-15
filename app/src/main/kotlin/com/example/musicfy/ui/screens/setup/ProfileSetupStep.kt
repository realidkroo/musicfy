package com.example.musicfy.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun ProfileSetupStep(
    username: String,
    onUsernameChange: (String) -> Unit,
    profilePicUri: Uri?,
    onProfilePicChange: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onProfilePicChange(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(48.dp)) // Moved up slightly
        
        Text(
            text = "Firstly",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = (-2).sp // Changed letter spacing
        )
        
        Spacer(modifier = Modifier.height(4.dp)) // Reduced spacing
        
        Text(
            text = "Set your username and profile picture, its for\nwhen you want to listen with your friend.",
            fontSize = 16.sp,
            color = Color(0xFFB3B3B3),
            lineHeight = 22.sp,
            letterSpacing = (-0.5).sp
        )
        
        Spacer(modifier = Modifier.height(40.dp)) // Adjusted
        
        // Profile Picture Placeholder (visuals drawn by SetupWizardScreen overlay for smooth morphing)
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .clickable {
                    launcher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "username",
            fontSize = 14.sp, // Made smaller
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            singleLine = true,
            shape = RoundedCornerShape(percent = 50), // Rounder
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp), // Smaller
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2C2C2C),
                unfocusedContainerColor = Color(0xFF2C2C2C),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
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
                text = "Your data will never be on the musicfy server", // Changed text
                fontSize = 12.sp,
                color = Color(0xFFD0D0D0),
                fontWeight = FontWeight.Normal, // Not bold
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
