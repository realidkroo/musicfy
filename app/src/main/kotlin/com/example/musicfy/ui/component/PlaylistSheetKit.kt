// PlaylistSheetKit.kt

package com.example.musicfy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.ui.player.menu.MenuRowSurface

/*
 * The small pieces the playlist sheets are built from, taken from the reference mocks: a labelled
 * field, a pill button, a search field, a section label, and a row with a square thumbnail. All on
 * the sheet's own greys (see docs/popup-sheet.skill.md) and sized in dp with sp text that is free
 * to grow: nothing here has a fixed height, only a minimum, so a large font scale pushes the
 * layout out instead of clipping it.
 */

/** Smallest height of a field or button: comfortable to hit, and the compact 46dp the sheets use. */
private val ControlMinHeight = 46.dp

@Composable
internal fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier,
    )
}

/** A rounded field on [MenuRowSurface]: placeholder when empty, [leading] before the text. */
@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlMinHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** The search field at the top of a sheet: a search glyph in a circle, then the query. */
@Composable
internal fun SheetSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        leading = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp),
                )
            }
        },
    )
}

/** The sheet's one main action: a pill a step lighter than the rows, dimmed while [enabled] is false. */
@Composable
internal fun SheetPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ControlMinHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(percent = 50))
            .background(lerp(MenuRowSurface, Color.White, 0.22f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A thin rule between a sheet's header or search and what follows - the mocks' divider. */
@Composable
internal fun SheetDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

/**
 * A row on [MenuRowSurface]: [leading] (an icon in a circle, usually), the [title] and an optional
 * second line, then [trailing]. The same look as the player menu's rows, taller only when the text is.
 */
@Composable
internal fun SheetOptionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** The round white badge with a dark glyph that every row of the sheets starts with. */
@Composable
internal fun SheetIconBadge(icon: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f)),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * Lets this overflow the sheet's side padding by [amount] on both sides, back out to the screen
 * edges, so something scrolling sideways (the cover carousel) runs off the screen instead of
 * stopping short of it. The sheet clips at its own edge, not at its padding.
 */
internal fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra * 2))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra, 0)
    }
}

/**
 * The top of an item's sheet, as in the song-menu mock: a rounded square cover, the title and a
 * second line under it, and a round button at the end ([trailing]). Wraps to a taller row, never
 * clips, when the text is large.
 */
@Composable
internal fun SheetHeader(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = thumbnailUrl?.resize(240, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MenuRowSurface),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** The round button of a [SheetHeader]: [icon] in white on a faint disc, brighter while [active]. */
@Composable
internal fun SheetHeaderButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (active) 0.26f else 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A tall card for a headline action - the icon badge on top, the label beneath - two to a row, as
 * in the playlist-menu mock ("Download", "Edit playlist").
 */
@Composable
internal fun SheetActionCard(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        SheetIconBadge(icon)
        Spacer(Modifier.height(30.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Looks like [SheetSearchField] but is a button: for a sheet whose search happens on the screen
 * behind it (the playlist's own list), where typing here would have nothing to filter.
 */
@Composable
internal fun SheetSearchLauncher(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ControlMinHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = placeholder,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
