/**
 * musicfy Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.example.musicfy.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.musicfy.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.example.musicfy.LocalHazeState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } && 
           currentRoute.startsWith("$screenRoute/")
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    
    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }
            
            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }
            
            // Long press detection using InteractionSource
            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, isSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }
            
            NavigationRailItem(
                selected = isSelected,
                onClick = { 
                    if (!isSearchItem) {
                        onItemClick(screen, isSelected)
                    }
                    // For search item, click is handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = coil3.compose.rememberAsyncImagePainter(model = iconRes),
                        contentDescription = stringResource(screen.titleId),
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val playerConnection = com.example.musicfy.LocalPlayerConnection.current
    val currentSong by playerConnection?.service?.currentMediaMetadata?.collectAsState(initial = null) ?: androidx.compose.runtime.mutableStateOf(null)

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        
        val hazeState = LocalHazeState.current
        
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .let {
                    if (hazeState != null) {
                        it.hazeEffect(
                            state = hazeState, 
                            style = HazeStyle(
                                backgroundColor = containerColor,
                                tint = HazeTint(containerColor.copy(alpha = 0.65f)),
                                blurRadius = 24.dp
                            )
                        )
                    } else {
                        it.background(containerColor.copy(alpha = 0.85f))
                    }
                }
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
        ) {

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationItems.forEach { screen ->
                    val isSelected = remember(currentRoute, screen.route) {
                        isRouteSelected(currentRoute, screen.route, navigationItems)
                    }
                    val iconRes = remember(isSelected, screen) {
                        if (isSelected) screen.iconIdActive else screen.iconIdInactive
                    }
                    
                    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "nav_item_scale"
                    )

                    val targetBackgroundColor = if (isSelected) {
                        Color.White.copy(alpha = 0.2f)
                    } else Color.Transparent

                    val animatedBackgroundColor by animateColorAsState(
                        targetValue = targetBackgroundColor,
                        animationSpec = tween(200),
                        label = "nav_item_bg_color"
                    )

                    val targetIconTint = if (isSelected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.7f)
                    }

                    val animatedIconTint by animateColorAsState(
                        targetValue = targetIconTint,
                        animationSpec = tween(200),
                        label = "nav_item_icon_tint"
                    )
                    
                    // Long press detection using InteractionSource
                    if (isSearchItem) {
                        LaunchedEffect(interactionSource) {
                            var isLongClick = false
                            interactionSource.interactions.collectLatest { interaction ->
                                when (interaction) {
                                    is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                                        isLongClick = false
                                        kotlinx.coroutines.delay(viewConfiguration.longPressTimeoutMillis)
                                        isLongClick = true
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onSearchLongClick.invoke()
                                    }
                                    is androidx.compose.foundation.interaction.PressInteraction.Release -> {
                                        if (!isLongClick) {
                                            onItemClick(screen, isSelected)
                                        }
                                    }
                                    is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                                        isLongClick = false
                                    }
                                }
                            }
                        }
                    }
                    
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .scale(scale)
                            .clip(RoundedCornerShape(32.dp))
                            .background(animatedBackgroundColor)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null, // remove ripple to reveal color smoothly
                                onClick = {
                                    if (!isSearchItem) {
                                        onItemClick(screen, isSelected)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = iconRes,
                            animationSpec = tween(200),
                            label = "nav_icon_crossfade"
                        ) { targetIconRes ->
                            Icon(
                                painter = coil3.compose.rememberAsyncImagePainter(model = targetIconRes),
                                contentDescription = stringResource(screen.titleId),
                                modifier = Modifier.size(if (screen == Screens.Home || screen == Screens.Search) 22.dp else 24.dp),
                                tint = animatedIconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
