// DetailActionRow.kt

package com.example.musicfy.ui.component.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.example.musicfy.R

sealed class DetailSecondaryAction {

    data class AddToLibrary(val onClick: () -> Unit) : DetailSecondaryAction()

    data class DownloadAction(val state: Int?, val onClick: () -> Unit) : DetailSecondaryAction()
}

@Composable
fun DetailActionRow(
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    secondaryAction: DetailSecondaryAction,
    modifier: Modifier = Modifier,

    extraAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (isPlaying) R.string.pause else R.string.play),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        DetailCircleButton(
            icon = R.drawable.shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onShuffleClick,
        )

        when (secondaryAction) {
            is DetailSecondaryAction.AddToLibrary -> DetailCircleButton(
                icon = R.drawable.favorite_border,
                contentDescription = stringResource(R.string.save),
                onClick = secondaryAction.onClick,
            )
            is DetailSecondaryAction.DownloadAction -> when (secondaryAction.state) {
                Download.STATE_COMPLETED -> DetailCircleButton(
                    icon = R.drawable.offline,
                    contentDescription = null,
                    onClick = secondaryAction.onClick,
                )
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Surface(
                    onClick = secondaryAction.onClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> DetailCircleButton(
                    icon = R.drawable.download,
                    contentDescription = null,
                    onClick = secondaryAction.onClick,
                )
            }
        }

        extraAction?.invoke()
    }
}

@Composable
fun DetailCircleButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(48.dp),
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}
