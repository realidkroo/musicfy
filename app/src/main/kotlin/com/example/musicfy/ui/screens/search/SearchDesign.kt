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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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

/** Vertical room the big page title occupies above the field while expanded. */
val SearchTitleBlockHeight = 62.dp

/**
 * Scroll distance over which the title collapses into the bar.
 *
 * Short on purpose: the title only has to travel its own height, and a long ramp makes the bar
 * feel like it is lagging behind the finger.
 */
private val CollapseTravel = 72.dp

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Palette
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The search surfaces are drawn against a near-black page in every theme (as designed), so these
 * are explicit rather than pulled from the M3 scheme — the scheme's container tones drift with the
 * dynamic-color source and were what made the field and the tiles read as two different greys.
 */
object SearchColors {
    val Field = Color(0xFF1B1B1D)
    val FieldFocused = Color(0xFF242427)
    val Tile = Color(0xFF19191B)
    val TileHigh = Color(0xFF232326)
    val Divider = Color(0xFF2A2A2D)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)

    fun page(pureBlack: Boolean): Color = if (pureBlack) Color.Black else Color(0xFF0A0A0B)
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Scroll collapse
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * 0 while the list is at rest, 1 once it has scrolled [CollapseTravel] — the single value every
 * top-bar transition on these screens is driven from.
 *
 * Returned as a [State] AND read through a provider lambda by callers: reading `.value` inside a
 * graphicsLayer/draw block keeps the whole collapse in the draw phase, so dragging the list never
 * recomposes the page. (Reading it in composition instead is what would put a full recompose of
 * the grid on every scroll frame.)
 */
@Composable
fun rememberCollapseProgress(listState: LazyListState): State<Float> {
    val travelPx = with(LocalDensity.current) { CollapseTravel.toPx() }
    return remember(listState, travelPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / travelPx).coerceIn(0f, 1f)
            }
        }
    }
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
fun searchTopBarHeight(withTitle: Boolean = true): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + (if (withTitle) SearchTitleBlockHeight else 0.dp) + SearchFieldHeight + 24.dp
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
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val titleTravelPx = with(density) { SearchTitleBlockHeight.toPx() }

    // Cached per quantised radius: RenderEffect is immutable, so building one per frame of the
    // title's blur-out would allocate ~60 objects a second for a 300ms transition.
    val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }

    Box(modifier = modifier.fillMaxWidth()) {
        // Mount/unmount on a boolean so this only recomposes at the boundary; everything that
        // varies continuously is read inside draw-phase lambdas below.
        val showGlass by remember { derivedStateOf { progressProvider() > 0.01f } }
        if (showGlass) {
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
                    steps = 3,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        onDrawBehind {
                            val p = progressProvider().coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to pageColor.copy(alpha = p * 0.95f),
                                    0.45f to pageColor.copy(alpha = p * 0.72f),
                                    0.78f to pageColor.copy(alpha = p * 0.34f),
                                    1f to Color.Transparent,
                                )
                            )
                        }
                    }
            )
        }

        Column(modifier = Modifier.padding(top = statusBar)) {
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

            // Rides up into the space the title vacated. The trailing slot (profile avatar) sits
            // beside it and only appears once collapsed, matching the mockup where the avatar
            // moves from next to the title to next to the field.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SearchHorizontalPadding)
                    .graphicsLayer {
                        if (title != null) {
                            translationY = -progressProvider().coerceIn(0f, 1f) * titleTravelPx
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) { field() }
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    trailing()
                }
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
                    modifier = Modifier.fillMaxWidth(),
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
    rule: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
        if (rule) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SearchColors.Divider)
            )
            Spacer(modifier = Modifier.height(14.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
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

    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.16f))
                )
            )
            .background(SearchColors.Tile.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
    ) {
        // Drawn before the label so the text always sits on top of the artwork.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(width = 84.dp, height = 76.dp),
        ) {
            LayeredCover(
                url = covers.getOrNull(1),
                rotation = -20f,
                offsetX = 4.dp,
                offsetY = 10.dp,
                size = 46.dp,
                alpha = 0.7f,
            )
            LayeredCover(
                url = covers.getOrNull(0),
                rotation = -8f,
                offsetX = 22.dp,
                offsetY = 2.dp,
                size = 50.dp,
                alpha = 1f,
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = SearchColors.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, end = 76.dp),
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
                shadowElevation = with(density) { 6.dp.toPx() }
                shape = RoundedCornerShape(8.dp)
                clip = true
            }
            .background(SearchColors.TileHigh),
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(256, 256),
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
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = SearchHorizontalPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.lazy.itemsIndexed(categories) { index, label ->
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
    Box(
        modifier = modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(corner))
            .background(SearchColors.TileHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(512, 512),
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

/** Border helper for the card surfaces the mockups outline rather than fill. */
fun Modifier.searchCardBorder(radius: Dp = 16.dp): Modifier =
    this.border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(radius))
