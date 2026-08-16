// Menu.kt
//
// The row style shared by every item menu — song, album, artist, playlist, and their YouTube
// variants.
//
// Despite the `Material3` names (kept so the ~10 menus that call this don't all need editing),
// nothing here is Material 3 any more: these rows now render in the same language as the player's
// own action sheet — flat dark pills, an icon in a white disc, one line of text. The player sheet
// and the item menus used to be two visibly different designs reachable from the same screen, and
// the fastest way to make every menu match is to change the one component they all go through
// rather than to restyle each menu by hand.
//
// Two deliberate omissions relative to the old M3 version:
//
//   * `description` is accepted but never rendered. Menus are a list of verbs; a second
//     explanatory line under each one doubles the height of the sheet and slows down the scan.
//   * `cardColors` is accepted but ignored, so one menu can't quietly diverge from the rest.
//
// Both fields stay on the data class purely so existing call sites keep compiling; they are inert.

package com.example.musicfy.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.player.menu.MenuRowSurface

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Material3MenuItemRow(item = item)
        }
    }
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData
) {
    val enabled = item.onClick != null
    val contentAlpha = if (enabled) 1f else 0.35f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { item.onClick?.invoke() },
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        item.icon?.let { icon ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f * contentAlpha)),
            ) {
                // The disc is white, so anything drawn on it has to be dark. Menus pass a bare
                // Icon with no tint, which picks up LocalContentColor — overriding it here means
                // none of them need to know they are being drawn on a light background. A menu
                // that sets its own tint (the red heart) still wins, which is intended.
                Box(modifier = Modifier.size(13.dp)) {
                    CompositionLocalProvider(
                        LocalContentColor provides Color.Black.copy(alpha = 0.75f),
                    ) {
                        icon()
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = contentAlpha),
                )
            ) {
                item.title()
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    /** Accepted for source compatibility, never rendered. See the file header. */
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    /** Accepted for source compatibility, never applied. See the file header. */
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null
)
