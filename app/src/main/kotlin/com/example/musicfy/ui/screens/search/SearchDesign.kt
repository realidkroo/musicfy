// SearchDesign.kt
// Shared building blocks for the rebuilt search experience (landing page, active/typing states,
// results page, genre page).
//
// Deliberately contains NO Material 3 components: no Scaffold, SearchBar, TopAppBar, Tab/TabRow,
// Card, Chip, IconButton, Surface, or the M3-expressive progress indicators. Everything here is
// foundation (Box/Row/Column/BasicTextField/LazyColumn) plus explicit painting. `Text` and `Icon`
// are kept purely as glyph/vector renderers with explicitly-specified styles — they carry no
// component chrome of their own, and the whole app already draws its type through them.
//
// The scroll-collapse behaviour every search surface shares lives here too (see
// [rememberCollapseProgress] and [SearchGlassTopBar]): the big page title fades/blurs out while the
// search field slides up into a progressively-blurred top bar, and reverses on scroll back. All of
// it is driven from the draw phase off a provider lambda, so a scroll never recomposes the page.

package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.utils.resize

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Metrics
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** Page gutter. Matches the mockups' 24dp inset on every search surface. */
val SearchHorizontalPadding = 24.dp

/** Height of the search field pill. */
val SearchFieldHeight = 48.dp

/** Profile avatar in the top bar's trailing slot. */
val AvatarSize = 36.dp

/** Vertical room the big page title occupies above the field while expanded. */
val SearchTitleBlockHeight = 62.dp

/**
 * Clearance between the status bar and the first thing the bar draws.
 *
 * The collapsed bar previously started at the status-bar inset exactly, which put the search field
 * hard against the clock and the notch cutout. This is applied both to the bar's own content and
 * to the content padding derived from it, so expanded and collapsed keep the same breathing room.
 */
val SearchTopClearance = 18.dp

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Palette
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The search surfaces are drawn against a near-black page in every theme (as designed), so these
 * are explicit rather than pulled from the M3 scheme — the scheme's container tones drift with the
 * dynamic-color source and were what made the field and the tiles read as two different greys.
 */
object SearchColors {
    val Field = Color(0xFF141416)
    val FieldFocused = Color(0xFF1E1E21)
    val Tile = Color(0xFF121214)
    val TileHigh = Color(0xFF1C1C1F)
    val Divider = Color(0xFF232326)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)

    /**
     * Black, not a dark grey, in both theme modes. The near-black the surfaces used to sit on
     * read as washed-out next to the (genuinely black) nav bar and mini player, so the page now
     * matches them and the tiles are what provide the lift.
     */
    fun page(pureBlack: Boolean): Color = Color.Black
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Scroll collapse
// ─────────────────────────────────────────────────────────────────────────────────────────────

/** The collapse curve. cubic-bezier(0.57, 0.53, 0, 1) — slow out of the gate, long glide home. */
val SearchCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

/** How long the bar takes to travel between its two states, in either direction. */
const val SearchCollapseDurationMs = 900

/**
 * 0 when the bar is expanded, 1 when collapsed — the single value every top-bar transition on
 * these screens is driven from.
 *
 * This is an ANIMATION between two latched states, not a direct read of the scroll offset. Mapping
 * it straight onto `firstVisibleItemScrollOffset` (as it did) meant the bar could only move as far
 * as the finger did: a short flick left it stranded part-way, and the moment the first item scrolled
 * out of view the value jumped from wherever it was to 1 in a single frame — the "instant cut".
 * Latching to a boolean and animating on [SearchCollapseEasing] means the bar always plays the whole
 * curve, at the same speed, however the list was thrown.
 *
 * The threshold has a dead band (collapse past 24dp, expand again only under 6dp) so a scroll that
 * hovers around the trigger point cannot flip the bar back and forth.
 *
 * Still read through a provider lambda by callers: `.value` inside a graphicsLayer/draw block keeps
 * the whole thing in the draw phase, so the 900ms of motion costs no recomposition — only the two
 * boundary flips do.
 */
@Composable
fun rememberCollapseProgress(listState: LazyListState): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val latch = remember(listState) { booleanArrayOf(false) }
    val collapsed by remember(listState, enterPx, exitPx) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val next = when {
                listState.firstVisibleItemIndex > 0 -> true
                offset > enterPx -> true
                offset < exitPx -> false
                else -> latch[0]
            }
            latch[0] = next
            next
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "searchCollapse",
    )
}

/**
 * Total height the expanded top bar occupies, i.e. the top contentPadding the scrolling content
 * needs so its first row starts below the bar.
 *
 * Fixed at the EXPANDED height and never animated: changing content padding mid-scroll re-lays out
 * the whole list every frame and fights the scroll position. The bar collapses by moving its own
 * children (draw phase only) while the content simply scrolls up underneath it.
 */
@Composable
fun searchTopBarHeight(withTitle: Boolean = true, extra: Dp = 0.dp): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + SearchTopClearance +
        (if (withTitle) SearchTitleBlockHeight else 0.dp) +
        SearchFieldHeight + extra + 32.dp
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The shared collapsing top bar: progressive blur over whatever the page captured into
 * [glassState], a darkening gradient beneath it, the big page title, and the search field.
 *
 * Both effects fade in with [progressProvider] rather than being always-on — an idle, unscrolled
 * page pays for neither the capture-blur passes nor the gradient overdraw.
 *
 * The caller's scrolling content must be wrapped in `Modifier.glassRoot(glassState)` and declared
 * BEFORE this composable in the same Box, so the blur reads a capture of the content rather than
 * of itself.
 */
@Composable
fun SearchGlassTopBar(
    glassState: GlassState,
    progressProvider: () -> Float,
    pureBlack: Boolean,
    title: String?,
    /**
     * False while the list is being flung or dragged.
     *
     * The blur's input is the captured content of the whole scrolling page, so every step
     * re-rasterises that content through a Gaussian: the page is drawn once for real and twice
     * more for the blur, every frame. Measured at 30.7ms of GPU per frame while scrolling, against
     * an 8.3ms budget — it is the single dominant cost on this screen, and no amount of trimming
     * the tiles underneath can offset it.
     *
     * Nobody can resolve a blurred backdrop mid-fling, so it is simply not drawn then; the gradient
     * scrim below (a single rect, essentially free) keeps the text legible in the meantime, and the
     * blur fades back the moment the list settles.
     */
    blurActive: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** Optional second row under the field (the results page's category selector). */
    below: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val titleTravelPx = with(density) { SearchTitleBlockHeight.toPx() }
    // Lane the collapsed field gives up to the avatar, and the distance the avatar itself travels
    // from the title row's centre to the field's. Both resolved once, not per frame.
    val avatarReservePx = with(density) { (AvatarSize + 12.dp).toPx() }
    val avatarDropPx = with(density) { (SearchFieldHeight / 2 - SearchTitleBlockHeight / 2).toPx() }

    // Cached per quantised radius: RenderEffect is immutable, so building one per frame of the
    // title's blur-out would allocate ~60 objects a second for a 300ms transition.
    val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }

    Box(modifier = modifier.fillMaxWidth()) {
        // Mount/unmount on a boolean so this only recomposes at the boundary; everything that
        // varies continuously is read inside draw-phase lambdas below.
        val showGlass by remember { derivedStateOf { progressProvider() > 0.01f } }
        if (showGlass && blurActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = progressProvider().coerceIn(0f, 1f) }
            ) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 42f * progressProvider().coerceIn(0f, 1f) },
                    foundationColor = pageColor,
                    direction = BlurDirection.BottomToTop,
                    // Every step re-rasterises the captured content through its own Gaussian, so
                    // this is a direct multiplier on GPU cost: measured at 30.7ms of GPU per frame
                    // while scrolling (the whole 120Hz budget is 8.3ms). Two layers still read as
                    // a graduated blur against the gradient beneath; three did not earn the third
                    // full-screen pass.
                    steps = 2,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (showGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        onDrawBehind {
                            val p = progressProvider().coerceIn(0f, 1f)
                            // Carries a little more weight while the blur is off, so the handover
                            // in and out of a fling is not a visible step.
                            val boost = if (blurActive) 1f else 1.12f
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to pageColor.copy(alpha = (p * 0.95f * boost).coerceAtMost(1f)),
                                    0.45f to pageColor.copy(alpha = (p * 0.72f * boost).coerceAtMost(1f)),
                                    0.78f to pageColor.copy(alpha = (p * 0.34f * boost).coerceAtMost(1f)),
                                    1f to Color.Transparent,
                                )
                            )
                        }
                    }
            )
        }

        Column(modifier = Modifier.padding(top = statusBar + SearchTopClearance)) {
            if (title != null) {
                // Collapses in place: rises by its own height, shrinks slightly toward its left
                // edge, and blurs out — the same wordmark treatment the home top bar uses, so the
                // two screens read as one system.
                Box(
                    modifier = Modifier
                        .height(SearchTitleBlockHeight)
                        .fillMaxWidth()
                        .padding(horizontal = SearchHorizontalPadding)
                        .graphicsLayer {
                            val p = progressProvider().coerceIn(0f, 1f)
                            alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                            translationY = -p * titleTravelPx
                            val scale = 1f - p * 0.18f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val quantised = ((p * 14f) / 3f).toInt() * 3
                                renderEffect = if (quantised > 0) {
                                    blurCache.getOrPut(quantised) {
                                        android.graphics.RenderEffect.createBlurEffect(
                                            quantised.toFloat(),
                                            quantised.toFloat(),
                                            android.graphics.Shader.TileMode.CLAMP,
                                        ).asComposeRenderEffect()
                                    }
                                } else {
                                    null
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                            ),
                            color = SearchColors.Primary,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Rides up into the space the title vacated.
            //
            // The field's WIDTH is interpolated in the layout phase rather than by animating a
            // padding: it has to give up room for the avatar as that avatar drops down beside it,
            // and a padding/weight change would re-measure this subtree on every scroll frame.
            // Measuring against a progress-derived width is the same layout-phase technique the
            // player's morph uses, and costs one measure pass with no recomposition.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (title != null) {
                            translationY = -progressProvider().coerceIn(0f, 1f) * titleTravelPx
                        }
                    },
            ) {
              Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
                Box(
                    modifier = if (trailing == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.layout { measurable, constraints ->
                            // Only reserve the avatar's lane once collapsed; expanded, the avatar
                            // is up on the title row and the field owns the full width.
                            val reserve = if (title == null) {
                                avatarReservePx
                            } else {
                                avatarReservePx * progressProvider().coerceIn(0f, 1f)
                            }
                            val width = (constraints.maxWidth - reserve.toInt()).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                        }
                    },
                ) {
                    field()
                }
              }
              if (below != null) {
                Spacer(modifier = Modifier.height(16.dp))
                below()
              }
            }
        }

        // Travels from the title row's centre line down to the field's, so it reads as one avatar
        // settling beside the search box rather than two of them swapping over.
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBar + SearchTopClearance, end = SearchHorizontalPadding)
                    .height(if (title == null) SearchFieldHeight else SearchTitleBlockHeight)
                    .graphicsLayer {
                        if (title != null) {
                            translationY = progressProvider().coerceIn(0f, 1f) * avatarDropPx
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

/** Avatar/profile button used in the top bar's trailing slot. */
@Composable
fun SearchAvatar(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SearchColors.TileHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl.resize(128, 128),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Search field
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The search input. A [BasicTextField] in a hand-drawn pill — no `SearchBar`, so it neither
 * expands into a full-screen M3 container nor brings that component's own scrim, back arrow and
 * animation curve with it. This screen owns all of that itself.
 */
@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnlyClick: (() -> Unit)? = null,
    /**
     * Applied to the text field itself, not to the pill around it. A FocusRequester on the pill
     * silently fails (a Row is not focusable), which is what would leave the caret placed but no
     * keyboard raised when the screen opened the field programmatically.
     */
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .clip(RoundedCornerShape(50))
            .background(if (focused) SearchColors.FieldFocused else SearchColors.Field)
            .then(
                if (readOnlyClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = readOnlyClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = SearchColors.Placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        TextStyle(color = SearchColors.Primary, fontSize = 15.sp)
                    ),
                    cursorBrush = SolidColor(SearchColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(value.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (onFocusChanged != null) {
                                Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                            } else {
                                Modifier
                            }
                        ),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Section label with the hairline rule under it. Replaces `NavigationTitle`, which draws M3
 * typography tokens plus an optional trailing chevron button.
 */
@Composable
fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    ruleAbove: Boolean = true,
    ruleBelow: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
        if (ruleAbove) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SearchColors.Divider)
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (large) 22.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = SearchColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(if (ruleBelow) 12.dp else 16.dp))
        if (ruleBelow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SearchColors.Divider)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Mood / genre tile
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * A mood or genre tile: the label on the left, and two of that category's featured playlist covers
 * stacked at the right edge as layered, differently-rotated cards — the "folder" shape in the
 * mockups. The back card is turned further and sits lower so the pair reads as a stack that has
 * been fanned open, not as two images side by side.
 *
 * The tile's own colour is derived from the category's own stripe colour (YouTube ships one per
 * mood/genre), so the tile is tinted like the genre page it opens rather than being a flat grey
 * box. Both covers are optional — with none, the stack simply renders as empty card shapes, which
 * is what shows while the featured playlists are still loading.
 */
@Composable
fun MoodTile(
    title: String,
    stripeColor: Long,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = remember(stripeColor) { moodTint(stripeColor) }
    // Set from the title's own layout below, so the fade is driven by what actually happened to
    // the text rather than by guessing at a character count.
    var titleWraps by remember(title) { mutableStateOf(false) }
    // Pre-blended once per tile rather than composited as two layers at draw time.
    val tileBrush = remember(tint) {
        Brush.linearGradient(
            colors = listOf(
                blendOver(tint.copy(alpha = 0.55f), SearchColors.Tile),
                blendOver(tint.copy(alpha = 0.16f), SearchColors.Tile),
            )
        )
    }

    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            // One fill, not two. These were a gradient AND a translucent solid stacked on top of
            // it — two full-rect blends per tile, times a dozen tiles, every scrolled frame.
            .background(brush = tileBrush)
            .clickable(onClick = onClick),
    ) {
        // Drawn before the label so the text always sits on top of the artwork, and faded to
        // nothing at its left edge so a long title that wraps to two lines runs out over clean
        // tile colour instead of over the covers. DstIn against a horizontal ramp, which needs its
        // own offscreen layer to composite against.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(width = 84.dp, height = 76.dp)
                // A gradient of the tile's OWN colour painted over the covers' left edge, rather
                // than a DstIn alpha mask. Identical result against an opaque tile, but DstIn
                // needs its own offscreen layer per tile — a dozen of those per scrolled frame is
                // exactly the kind of cost this grid cannot carry. This is one extra rect blend.
                // Fades the ARTWORK'S OWN ALPHA to zero, rather than painting tile-coloured
                // gradient over it.
                //
                // A colour scrim cannot work here: the tile underneath is itself a gradient, so a
                // single flat colour only matches it at one x position and shows a hard vertical
                // seam everywhere else — which is what the "cut, not continuous" edge was. DstIn
                // multiplies the artwork's alpha instead, so whatever the tile is doing behind it
                // simply shows through, and there is nothing to mismatch.
                //
                // The offscreen layer this needs is why it is gated on titleWraps: only the handful
                // of categories with names long enough to take a second line pay for it.
                .then(
                    if (titleWraps) {
                        Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                    } else {
                        Modifier
                    }
                )
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0.00f to Color.Transparent,
                        0.42f to Color.Black.copy(alpha = 0.55f),
                        0.70f to Color.Black,
                    )
                    onDrawWithContent {
                        drawContent()
                        if (titleWraps) drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                },
        ) {
            // Pulled apart along the diagonal and turned to clearly different angles — stacked
            // tighter, the back card only showed as a sliver and the pair read as one crooked
            // square rather than as a fanned stack.
            LayeredCover(
                url = covers.getOrNull(1),
                rotation = -24f,
                offsetX = (-2).dp,
                offsetY = 2.dp,
                size = 44.dp,
                // Fully opaque on purpose: a graphicsLayer with alpha < 1 AND clip = true cannot
                // be drawn in place and gets its own offscreen buffer. The back card reads as
                // recessed from the overlap and the rotation alone.
                alpha = 1f,
            )
            LayeredCover(
                url = covers.getOrNull(0),
                rotation = -6f,
                offsetX = 28.dp,
                offsetY = 13.dp,
                size = 48.dp,
                alpha = 1f,
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                // Tightened: at the default line height a wrapped two-line title reads as two
                // separate labels rather than one that happens to run on.
                lineHeight = 17.sp,
            ),
            color = SearchColors.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                val wraps = result.lineCount > 1
                if (wraps != titleWraps) titleWraps = wraps
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, end = 70.dp),
        )
    }
}

/** One card in a [MoodTile]'s fanned stack. */
@Composable
private fun BoxScope.LayeredCover(
    url: String?,
    rotation: Float,
    offsetX: Dp,
    offsetY: Dp,
    size: Dp,
    alpha: Float,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                translationX = with(density) { offsetX.toPx() }
                translationY = with(density) { offsetY.toPx() }
                this.alpha = alpha
                // No shadowElevation. Two of these per tile and a dozen tiles on screen meant
                // ~24 shadow-casting layers, each forcing its own RenderNode plus a shadow pass,
                // on a grid that is constantly being scrolled. The cards already read as stacked
                // from the rotation and the overlap; the shadow was costing far more than it said.
                shape = RoundedCornerShape(8.dp)
                clip = true
            }
            .background(SearchColors.TileHigh),
    ) {
        if (url != null) {
            AsyncImage(
                // 128px covers a 44-48dp card at this density; 256 was decoding four times the
                // pixels for every tile in a grid the user scrolls through constantly.
                model = url.resize(128, 128),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * YouTube's stripe colour is a fully-opaque ARGB long, and many of them are near-black or
 * fluorescent. Pulled toward a mid-tone so every tile is legibly tinted without any of them
 * blowing out against white text.
 */
private fun moodTint(stripeColor: Long): Color {
    val argb = stripeColor.toInt()
    val r = ((argb shr 16) and 0xFF)
    val g = ((argb shr 8) and 0xFF)
    val b = (argb and 0xFF)
    if (r == 0 && g == 0 && b == 0) return Color(0xFF3A3A44)
    val lift = { c: Int -> (c + (170 - c) * 0.45f).toInt().coerceIn(40, 220) }
    return Color(lift(r), lift(g), lift(b))
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Category selector
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The All / Music / Artist / Video / Album / Playlist selector on the results page.
 *
 * A plain scrollable row of pills — not `FilterChip`/`TabRow`, so there is no M3 indicator,
 * ripple-shape or selected-container tokens involved; selection is just a fill and a text colour.
 */
@Composable
fun SearchCategoryRow(
    categories: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SearchHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories) { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White else SearchColors.Tile)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (selected) Color.Black else SearchColors.Primary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * One suggestion / history row: leading glyph, label, and an optional trailing control.
 *
 * [pill] is what the mockups' suggestion list uses — a filled rounded row — while the history list
 * and the result list draw flat on the page.
 */
@Composable
fun SearchSimpleRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    pill: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(if (pill) 14.dp else 0.dp))
            .then(if (pill) Modifier.background(SearchColors.Tile) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (pill) 16.dp else 0.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = SearchColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/** The horizontal "…" overflow affordance. Horizontal, not the vertical kebab. */
@Composable
fun SearchOverflowDots(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.more_horiz),
            contentDescription = null,
            tint = SearchColors.Secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Thin rule used between result rows. */
@Composable
fun SearchRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .height(1.dp)
            .background(SearchColors.Divider)
    )
}

/** Square artwork slot with the app's standard corner treatment. */
@Composable
fun SearchArtwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    corner: Dp = 6.dp,
) {
    // Ask the CDN for roughly what we are actually going to draw, rounded up to a coarse grid.
    //
    // These were fixed 512px requests for rows barely 46dp (~138px) tall — a ~14x overdraw in
    // pixels per thumbnail. The decodes themselves are wasted bandwidth, but the real damage is to
    // the memory cache: oversized bitmaps evict each other, so scrolling back up finds nothing
    // cached and every thumbnail decodes again. The grid (rather than the exact px) keeps the
    // number of distinct cache keys small so different call sites can share entries.
    val density = LocalDensity.current
    val requestPx = remember(size, density) {
        val px = with(density) { size.roundToPx() }
        ((px + 127) / 128) * 128
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(corner))
            .background(SearchColors.TileHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(requestPx, requestPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = SearchColors.Secondary,
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

/**
 * Loading indicator. Three breathing dots rather than `CircularWavyProgressIndicator` — that one
 * is M3-expressive, and this matches the loading treatment the lyrics page already uses.
 */
@Composable
fun SearchLoadingDots(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "searchLoading")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "searchLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Hairline outline for the card surfaces.
 *
 * Deliberately at the bottom of what renders: 1px (not 1dp — a dp line is 3px on this density and
 * reads as a drawn frame) at 5% white, which separates the card from a black page without becoming
 * an edge you actually look at.
 */
fun Modifier.searchCardBorder(radius: Dp = 16.dp): Modifier = this.border(
    width = androidx.compose.ui.unit.Dp.Hairline,
    color = Color.White.copy(alpha = 0.05f),
    shape = RoundedCornerShape(radius),
)

/** Composites [top] over [bottom] once, so the two never have to be blended at draw time. */
private fun blendOver(top: Color, bottom: Color): Color {
    val a = top.alpha
    return Color(
        red = top.red * a + bottom.red * (1f - a),
        green = top.green * a + bottom.green * (1f - a),
        blue = top.blue * a + bottom.blue * (1f - a),
        alpha = 1f,
    )
}
