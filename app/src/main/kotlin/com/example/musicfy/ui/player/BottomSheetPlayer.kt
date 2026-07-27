// BottomSheetPlayer.kt
// v0 minimal orchestrator, replacing the ~2,700-line BottomSheetPlayer that used to live in
// Player.kt (now at /old-player/Player.kt). Same call signature as before, so MainActivity.kt
// needed no changes. Only collects the two scoped flows it actually needs (trackInfo,
// transportState) instead of ~17 flows at root.

package com.example.musicfy.ui.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.ui.component.BottomSheet
import com.example.musicfy.ui.component.BottomSheetState

@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val trackInfo by playerConnection.uiState.trackInfo.collectAsState()
    val transportState by playerConnection.uiState.transportState.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        BottomSheet(
            state = state,
            modifier = modifier,
            isPillTransition = true,
            pureBlack = pureBlack,
            background = {},
            sharedContent = {
                val progressProvider = remember(state) { { state.progress.coerceIn(0f, 1f) } }
                val horizontalOffsetProvider = remember(state) { { state.horizontalOffset } }
                MorphingCover(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    trackInfo = trackInfo,
                    isPlaying = transportState.isPlaying,
                    playbackState = transportState.playbackState,
                    maxWidth = screenWidth,
                    maxHeight = screenHeight,
                    pureBlack = pureBlack,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            onDismiss = {
                playerConnection.service.clearAutomix()
                playerConnection.player.stop()
                playerConnection.player.clearMediaItems()
            },
            collapsedContent = {},
        ) {
            PlayerControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (screenHeight * 0.19f) - 37.dp)
            )
        }
    }
}
