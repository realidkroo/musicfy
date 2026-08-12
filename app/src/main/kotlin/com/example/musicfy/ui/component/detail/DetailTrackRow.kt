// DetailTrackRow.kt

package com.example.musicfy.ui.component.detail

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicfy.R
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.ui.component.ItemThumbnail

@Composable
fun DetailTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,

    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(48.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = null,
                    )
                }
            }
        }
        if (showDivider) {

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
