// LibraryImport.kt
//
// What the Library shows about importing: the banner at the top that opens the import sheet (see
// LibraryImportFlow.kt) - and turns into a progress bar while an import runs - and the import
// sources' icons, for their category cards.

package com.example.musicfy.ui.screens.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R
import com.example.musicfy.importer.ImportProgress
import com.example.musicfy.importer.ImportSource
import com.example.musicfy.importer.account.AccountService
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.musicfy.ui.screens.search.SearchColors
import com.example.musicfy.ui.screens.setup.onboarding.ProviderSquircle
import com.example.musicfy.ui.screens.setup.onboarding.SquircleShape
import kotlin.math.ceil

/** The services the banner flows past - the ones an account can be imported from. */
private val BannerServices = listOf(
    AccountService.SPOTIFY,
    AccountService.APPLE_MUSIC,
    AccountService.TIDAL,
    AccountService.YOUTUBE_MUSIC,
)

private val BannerShape = RoundedCornerShape(24.dp)

/** How long the marquee takes to carry one service's icon past - the whole set is four of these. */
private const val MarqueeMsPerIcon = 2600

/** The account service a source stands for, where it has an app icon of its own to show. */
internal fun ImportSource.service(): AccountService? = when (this) {
    ImportSource.SPOTIFY -> AccountService.SPOTIFY
    ImportSource.APPLE_MUSIC -> AccountService.APPLE_MUSIC
    ImportSource.TIDAL -> AccountService.TIDAL
    ImportSource.YOUTUBE_MUSIC -> AccountService.YOUTUBE_MUSIC
    ImportSource.DEEZER, ImportSource.OTHER -> null
}

/** A source's app icon, or - for one with no icon of ours - a neutral squircle with a note in it. */
@Composable
internal fun ImportSourceIcon(source: ImportSource, size: Dp, modifier: Modifier = Modifier) {
    val service = source.service()
    if (service != null) {
        ProviderSquircle(service, size = size, modifier = modifier)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(SquircleShape)
                .background(SearchColors.TileHigh),
        ) {
            Icon(
                painter = painterResource(R.drawable.library_music),
                contentDescription = source.label,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/**
 * The banner right under the search bar. Idle, it invites an import - the services' icons flowing
 * past, "Sync and add your music from your favourite music provider" - and opens the import sheet.
 * While an import runs it is replaced by its progress.
 */
@Composable
fun LibraryImportBanner(
    progress: ImportProgress,
    onOpenSheet: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = progress.isRunning,
        label = "libraryImportBanner",
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) { running ->
        if (running) {
            ImportProgressBanner(progress = progress, onClick = onOpenProgress)
        } else {
            ImportPromoBanner(onClick = onOpenSheet)
        }
    }
}

@Composable
private fun ImportPromoBanner(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(SearchColors.Tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 22.dp),
    ) {
        ProviderMarquee()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Sync and add your music from your favourite music provider",
            color = SearchColors.Primary,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.4).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

/**
 * The services' icons in a row that carries on past both edges of the card, moving left for ever.
 * One set of icons is its own period, so the row is several sets wide and slides one set's width
 * before starting over - with no way to see where it did.
 *
 * Moved in a graphics layer from an infinite transition read there, so it costs a redraw a frame
 * and no recomposition; it stops with the banner, which leaves composition as it scrolls away.
 */
@Composable
private fun ProviderMarquee(modifier: Modifier = Modifier) {
    val tile = 56.dp
    val gap = 14.dp
    val setWidth = (tile + gap) * BannerServices.size
    val setPx = with(LocalDensity.current) { setWidth.toPx() }

    val travel = rememberInfiniteTransition(label = "providerMarquee").animateFloat(
        initialValue = 0f,
        targetValue = setPx,
        animationSpec = infiniteRepeatable(tween(MarqueeMsPerIcon * BannerServices.size, easing = LinearEasing)),
        label = "providerMarqueeTravel",
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(tile).clipToBounds()) {
        // Enough sets to cover the card plus the one that slides out of view.
        val sets = ceil(maxWidth / setWidth).toInt() + 1
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -travel.value },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(sets) {
                BannerServices.forEach { service ->
                    ProviderSquircle(service, size = tile)
                }
            }
        }
    }
}

/** The banner while an import runs: a white bar and what it is doing. */
@Composable
private fun ImportProgressBanner(progress: ImportProgress, onClick: () -> Unit) {
    val total = progress.totalTracks
    val done = minOf(progress.processedTracks, total)
    val fraction = animateFloatAsState(
        targetValue = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(350),
        label = "libraryImportFraction",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BannerShape)
            .background(SearchColors.Tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        // Drawn from the animated value in the draw phase: the bar moves without recomposing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .drawBehind {
                    drawRoundRect(
                        color = Color.White,
                        size = Size(size.width * fraction.value.coerceAtLeast(0.02f), size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = when {
                total <= 0 -> "Getting your tracks ready…"
                progress.source != null -> "Adding $done of $total tracks from ${progress.source.label}"
                else -> "Adding $done of $total tracks"
            },
            color = SearchColors.Primary,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.4).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
