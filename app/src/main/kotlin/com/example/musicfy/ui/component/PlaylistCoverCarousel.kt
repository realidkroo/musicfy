// PlaylistCoverCarousel.kt

package com.example.musicfy.ui.component

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.playlistcover.CoverChoice
import com.example.musicfy.playlistcover.PlaylistCoverGenerator
import com.example.musicfy.playlistcover.PlaylistCoverStyle
import com.example.musicfy.playlistcover.PlaylistCovers
import com.example.musicfy.ui.player.menu.MenuRowSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** Edge of the covers drawn for the carousel: enough for the biggest card, small enough to redraw per keystroke. */
private const val PreviewPixels = 480

/** How long the title has to stay put before the covers are redrawn with it. */
private const val TitleSettleMs = 250L

/**
 * The cover picker of the create and edit sheets: a row of cards, the focused one centred and the
 * next one peeking in from the edge - the four generated styles, drawn live with the title typed so
 * far and the colours of [artworkUrl] (the playlist's top song), then a card for a picture of the
 * user's own.
 *
 * Whatever card rests in the middle is the choice, reported through [onChoice]:
 *  - a [CoverChoice.Generated] for a style card,
 *  - a [CoverChoice.Picked] for the picture card once a picture has been picked,
 *  - null when there is nothing to change - [keepAllowed] (editing a playlist that already has a
 *    cover) and the carousel not touched yet, or the picture card with no picture picked.
 *
 * @param startPage the card to open on: a style's ordinal, or the number of styles for the picture.
 * @param existingImage a picture already set as the cover, shown on the picture card.
 */
@Composable
internal fun PlaylistCoverCarousel(
    title: String,
    seed: String,
    artworkUrl: String?,
    startPage: Int,
    existingImage: String?,
    keepAllowed: Boolean,
    onChoice: (CoverChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The picture card comes first, at the far left, with the generated styles after it. The
    // carousel still opens on the card asked for ([startPage] is a style's ordinal, or the number
    // of styles for the picture), so a new playlist starts on its default style, not on the picker.
    val styles = PlaylistCoverStyle.entries
    val imagePage = 0
    val pageCount = styles.size + 1
    val firstPage = (if (startPage >= styles.size) imagePage else startPage + 1).coerceIn(0, pageCount - 1)

    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    val pickImage: () -> Unit = {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val pagerState = rememberPagerState(initialPage = firstPage) { pageCount }
    LaunchedEffect(picked) {
        if (picked != null) pagerState.animateScrollToPage(imagePage)
    }

    // With [keepAllowed] the cover stays as it is until the user so much as touches the carousel -
    // opening the sheet and pressing Done must not swap their cover for whichever card is showing.
    var touched by remember { mutableStateOf(!keepAllowed) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.first { it }
        touched = true
    }

    val currentOnChoice by rememberUpdatedState(onChoice)
    LaunchedEffect(pagerState) {
        snapshotFlow { Triple(pagerState.settledPage, picked, touched) }.collect { (page, uri, hasTouched) ->
            currentOnChoice(
                when {
                    !hasTouched && uri == null -> null
                    page != imagePage -> CoverChoice.Generated(styles[page - 1])
                    uri != null -> CoverChoice.Picked(uri)
                    else -> null
                }
            )
        }
    }

    val covers by rememberStyleCovers(title = title, seed = seed, artworkUrl = artworkUrl)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // About three fifths of the width, so the next card shows past the focused one on any
            // screen - and never so big it pushes the fields off a short one.
            val card = (maxWidth * 0.62f).coerceIn(150.dp, 260.dp)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(card),
                pageSpacing = 14.dp,
                contentPadding = PaddingValues(horizontal = (maxWidth - card) / 2),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                CoverCard(
                    page = page,
                    size = card,
                    pagerState = pagerState,
                    cover = styles.getOrNull(page - 1)?.let { covers[it] },
                    pickedImage = picked,
                    existingImage = existingImage,
                    onClick = {
                        when {
                            pagerState.currentPage != page ->
                                scope.launch { pagerState.animateScrollToPage(page) }
                            page == imagePage -> pickImage()
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val alpha by animateFloatAsState(
                    targetValue = if (index == pagerState.currentPage) 1f else 0.3f,
                    animationSpec = tween(200),
                    label = "coverDot",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha)),
                )
            }
        }
    }
}

@Composable
private fun CoverCard(
    page: Int,
    size: Dp,
    pagerState: androidx.compose.foundation.pager.PagerState,
    cover: ImageBitmap?,
    pickedImage: Uri?,
    existingImage: String?,
    onClick: () -> Unit,
) {
    val isImageCard = page == 0
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                // The cards beside the focused one sit back a little. Read here, in the layer, so
                // scrolling moves them without recomposing anything.
                val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                val scale = 1f - 0.08f * distance
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.4f * distance
            }
            .clip(RoundedCornerShape(28.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (!isImageCard) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val model = pickedImage ?: existingImage
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The mock's circle: the way in to the photo picker.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (model != null) 0.45f else 0.6f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.insert_photo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.13f),
                )
            }
        }
    }
}

/**
 * Every style's cover for the playlist as it is now, redrawn off the main thread when the title
 * settles or the artwork arrives - and never blank in between: the last set stays until the next
 * one is ready.
 */
@Composable
private fun rememberStyleCovers(
    title: String,
    seed: String,
    artworkUrl: String?,
): State<Map<PlaylistCoverStyle, ImageBitmap>> {
    val context = LocalContext.current.applicationContext

    // Four covers per keystroke would be wasted work: wait for a pause in typing.
    var settledTitle by remember { mutableStateOf(title) }
    LaunchedEffect(title) {
        delay(TitleSettleMs)
        settledTitle = title
    }

    val artwork = produceState<Bitmap?>(initialValue = null, artworkUrl) {
        value = PlaylistCovers.loadArtwork(context, artworkUrl)
    }

    return produceState(
        initialValue = emptyMap<PlaylistCoverStyle, ImageBitmap>(),
        settledTitle,
        seed,
        artwork.value,
    ) {
        val loaded = artwork.value
        value = withContext(Dispatchers.Default) {
            val palette = PlaylistCovers.paletteFor(loaded, seed)
            PlaylistCoverStyle.entries.associateWith { style ->
                PlaylistCoverGenerator.render(style, settledTitle, palette, loaded, PreviewPixels).asImageBitmap()
            }
        }
    }
}
