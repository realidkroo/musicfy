// settingsgroupkt
// what is this for you ask its for material3settings group ofc

// two looks live here classic is the original one card per row treatment and
// main settingsscreen renders grouped is the drill down sub settings look a
// card with no dividers where any option whose sub options are currently
// into a nested pill together with them so the parent child relationship is
// divider or an indent

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class SettingsGroupStyle {
    // original look one card per row hairline gaps used by the main settings page
    Classic,

    // sub settings look one continuous card no dividers nested pills for sub options
    Grouped,
}

// a material 3 expressive style settings group component
@Composable
fun SettingsGroup(
    title: String? = null,
    items: List<SettingsItem>,
    style: SettingsGroupStyle = SettingsGroupStyle.Classic,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // section title
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }

        when (style) {
            SettingsGroupStyle.Classic -> ClassicItems(items)
            SettingsGroupStyle.Grouped -> GroupedItems(items)
        }
    }
}

@Composable
private fun ClassicItems(items: List<SettingsItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp) // no separator
    ) {
        val visibleItems = items.filter { it.isVisible }
        items.forEach { item ->
            AnimatedVisibility(
                visible = item.isVisible,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                // concept style fully rounded separate cards
                val shape = RoundedCornerShape(20.dp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1C1E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    SettingsItemRow(item = item, style = SettingsGroupStyle.Classic)
                }
            }
        }
    }
}

// a parent option together with the sub options that belong to it
private class ItemCluster(
    val parent: SettingsItem,
    val subs: MutableList<SettingsItem> = mutableListOf(),
)

// issuboption items attach to the most recent non sub item above them which is
private fun clusterItems(items: List<SettingsItem>): List<ItemCluster> {
    val clusters = mutableListOf<ItemCluster>()
    items.forEach { item ->
        if (item.isSubOption && clusters.isNotEmpty()) {
            clusters.last().subs += item
        } else {
            clusters += ItemCluster(item)
        }
    }
    return clusters
}

@Composable
private fun GroupedItems(items: List<SettingsItem>) {
    val clusters = clusterItems(items)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            clusters.forEach { cluster ->
                AnimatedVisibility(
                    visible = cluster.parent.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    ClusterRows(cluster)
                }
            }
        }
    }
}

@Composable
private fun ClusterRows(cluster: ItemCluster) {
    val hasVisibleSubs = cluster.subs.any { it.isVisible }

    // animated rather than branched so toggling a parent doesn t pop a
    // and out the pill fades and insets in step with the sub rows expanding
    val pillAlpha by animateFloatAsState(
        targetValue = if (hasVisibleSubs) 1f else 0f,
        animationSpec = tween(300),
        label = "pillAlpha"
    )
    val pillInset by animateDpAsState(
        targetValue = if (hasVisibleSubs) 6.dp else 0.dp,
        animationSpec = tween(300),
        label = "pillInset"
    )

    val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f * pillAlpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pillInset, vertical = pillInset / 2)
            .clip(RoundedCornerShape(22.dp))
            .background(pillColor)
    ) {
        Column {
            SettingsItemRow(item = cluster.parent, style = SettingsGroupStyle.Grouped)
            cluster.subs.forEach { sub ->
                AnimatedVisibility(
                    visible = sub.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    SettingsItemRow(item = sub, style = SettingsGroupStyle.Grouped)
                }
            }
        }
    }
}

// individual settings item row with material 3 styling
@Composable
private fun SettingsItemRow(
    item: SettingsItem,
    style: SettingsGroupStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // icon with background
        item.icon?.let { icon ->
            Box(
                modifier = Modifier
                    .size(32.dp) // the circle in the concept is a bit smaller
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        if (item.tintIcon) {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = if (!item.enabled)
                                    Color(0xFF1C1C1E).copy(alpha = 0.38f)
                                else
                                    Color(0xFF1C1C1E),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Image(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = if (!item.enabled)
                                Color(0xFF1C1C1E).copy(alpha = 0.38f)
                            else
                                Color(0xFF1C1C1E),
                            modifier = Modifier.size(16.dp) // slightly smaller icon to fit in the 32dp circle
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // title content grouped sub settings rows read smaller and greyer than
            // full strength white the main settings page keeps its original weight
            val titleBaseStyle = if (style == SettingsGroupStyle.Grouped) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            }
            val titleColor = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                style == SettingsGroupStyle.Grouped -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            ProvideTextStyle(titleBaseStyle.copy(color = titleColor)) {
                item.title()
            }

            // description descriptiontext is the preferred form it is capped to a
            // here so a long string can never grow the row to two or three lines
            val descColor = if (!item.enabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            if (item.descriptionText != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = item.descriptionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = descColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.labelMedium.copy(color = descColor)
                    ) {
                        // attempt to wrap in something that limits lines or rely on desc to do it
                        // we will just provide the smaller text style
                        desc()
                    }
                }
            }
        }

        // trailing content
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

// data class for material 3 settings item
data class SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    // single line subtitle preferred over description takes precedence when both are set
    val descriptionText: String? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val isSubOption: Boolean = false,
    val onClick: (() -> Unit)? = null
)
