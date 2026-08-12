// randomizegriditemkt
// what is this for you ask its for randomize grid item ofc

package com.example.musicfy.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.musicfy.constants.ThumbnailCornerRadius

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // when isloading is true multiplier goes to 0 (moving dots to center)
    // when isloading is false multiplier goes to 1 (moving dots to corners)
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset"
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "loadingAlpha"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // die dots (5-pattern)
        val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
        val dotSize = 14.dp
        val padding = 24.dp

        // using a single center alignment and offsetting from center ensures they
        // collapse to center correctly

        // top left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // top right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = -padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // bottom left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // bottom right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = padding * dotOffsetMultiplier, y = padding * dotOffsetMultiplier)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        
        // loading indicator overlay
        Box(modifier = Modifier.alpha(loadingAlpha)) {
            LoadingIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
