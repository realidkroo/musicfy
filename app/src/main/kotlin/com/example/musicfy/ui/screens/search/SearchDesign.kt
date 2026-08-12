// searchdesignkt
// shared building blocks for the rebuilt search experience (landing page
// results page genre page)

// deliberately contains no material 3 components: no scaffold searchbar
// card chip iconbutton surface or the m3-expressive progress indicators
// foundation (box/row/column/basictextfield/lazycolumn) plus explicit
// are kept purely as glyph/vector renderers with explicitly-specified styles
// component chrome of their own and the whole app already draws its type

// the scroll-collapse behaviour every search surface shares lives here too
// [remembercollapseprogress] and [searchglasstopbar]): the big page title
// search field slides up into a progressively-blurred top bar and reverses
// it is driven from the draw phase off a provider lambda so a scroll never

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

// ───────────────────────────────────────────────────────────────────────────
// metrics
// ───────────────────────────────────────────────────────────────────────────

// page gutter matches the mockups' 24dp inset on every search surface
val SearchHorizontalPadding = 24.dp

// height of the search field pill
val SearchFieldHeight = 48.dp

// profile avatar in the top bar's trailing slot
val AvatarSize = 36.dp

// vertical room the big page title occupies above the field while expanded
val SearchTitleBlockHeight = 62.dp

// clearance between the status bar and the first thing the bar draws the
val SearchTopClearance = 18.dp

// ───────────────────────────────────────────────────────────────────────────
// palette
// ───────────────────────────────────────────────────────────────────────────

// the search surfaces are drawn against a near-black page in every theme (as
object SearchColors {
    val Field = Color(0xFF141416)
    val FieldFocused = Color(0xFF1E1E21)
    val Tile = Color(0xFF121214)
    val TileHigh = Color(0xFF1C1C1F)
    val Divider = Color(0xFF232326)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Placeholder = Color(0xFF77777D)

    // black not a dark grey in both theme modes the near-black the surfaces used to
    fun page(pureBlack: Boolean): Color = Color.Black
}

// ───────────────────────────────────────────────────────────────────────────
// scroll collapse
// ───────────────────────────────────────────────────────────────────────────

// the collapse curve cubic-bezier(057 053 0 1) — slow out of the gate long glide home
val SearchCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)

// how long the bar takes to travel between its two states in either direction
const val SearchCollapseDurationMs = 900

// 0 when the bar is expanded 1 when collapsed — the single value every top-bar
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

// total height the expanded top bar occupies ie the top contentpadding the
@Composable
fun searchTopBarHeight(withTitle: Boolean = true, extra: Dp = 0.dp): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + SearchTopClearance +
        (if (withTitle) SearchTitleBlockHeight else 0.dp) +
        SearchFieldHeight + extra + 32.dp
}

// ───────────────────────────────────────────────────────────────────────────
// top bar
// ───────────────────────────────────────────────────────────────────────────

// the shared collapsing top bar: progressive blur over whatever the page captured
@Composable
fun SearchGlassTopBar(
    glassState: GlassState,
    progressProvider: () -> Float,
    pureBlack: Boolean,
    title: String?,
    // false while the list is being flung or dragged the blur's input is the captured
    blurActive: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    // optional second row under the field (the results page's category selector)
    below: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageColor = SearchColors.page(pureBlack)
    val density = LocalDensity.current
    val titleTravelPx = with(density) { SearchTitleBlockHeight.toPx() }
    // lane the collapsed field gives up to the avatar and the distance the
    // from the title row's centre to the field's both resolved once not per frame
    val avatarReservePx = with(density) { (AvatarSize + 12.dp).toPx() }
    val avatarDropPx = with(density) { (SearchFieldHeight / 2 - SearchTitleBlockHeight / 2).toPx() }

    // cached per quantised radius: rendereffect is immutable so building one per
    // title's blur-out would allocate ~60 objects a second for a 300ms transition
    val blurCache = remember { mutableMapOf<Int, androidx.compose.ui.graphics.RenderEffect>() }

    Box(modifier = modifier.fillMaxWidth()) {
        // mount/unmount on a boolean so this only recomposes at the boundary;
        // varies continuously is read inside draw-phase lambdas below
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
                    // every step re-rasterises the captured content through its own gaussian so
                    // this is a direct multiplier on gpu cost: measured at 307ms of gpu per frame
                    // while scrolling (the whole 120hz budget is 83ms) two layers still read as
                    // a graduated blur against the gradient beneath; three did not earn the third
                    // full-screen pass
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
                            // carries a little more weight while the blur is off so the handover
                            // in and out of a fling is not a visible step
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
                // collapses in place: rises by its own height shrinks slightly toward its
                // edge and blurs out — the same wordmark treatment the home top bar uses so
                // two screens read as one system
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

            // rides up into the space the title vacated

            // the field's width is interpolated in the layout phase rather than by
            // padding: it has to give up room for the avatar as that avatar drops down
            // and a padding/weight change would re-measure this subtree on every scroll
            // measuring against a progress-derived width is the same layout-phase
            // player's morph uses and costs one measure pass with no recomposition
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
                            // only reserve the avatar's lane once collapsed; expanded the avatar
                            // is up on the title row and the field owns the full width
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

        // travels from the title row's centre line down to the field's so it reads
        // settling beside the search box rather than two of them swapping over
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

// avatar/profile button used in the top bar's trailing slot
@Composable
fun SearchAvatar(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val model = remember(imageUrl) {
        val url = imageUrl?.trim().orEmpty()
        when {
            url.isEmpty() -> null
            url.startsWith("content://") || url.startsWith("file://") || url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "file://$url"
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SearchColors.TileHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Profile",
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

// ───────────────────────────────────────────────────────────────────────────
// search field
// ───────────────────────────────────────────────────────────────────────────

// the search input a [basictextfield] in a hand-drawn pill — no `searchbar` so it
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
    // applied to the text field itself not to the pill around it a focusrequester on
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

// ───────────────────────────────────────────────────────────────────────────
// section header
// ───────────────────────────────────────────────────────────────────────────

// section label with the hairline rule under it replaces `navigationtitle` which
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

// ───────────────────────────────────────────────────────────────────────────
// mood / genre tile
// ───────────────────────────────────────────────────────────────────────────

// a mood or genre tile: the label on the left and two of that category's featured
@Composable
fun MoodTile(
    title: String,
    stripeColor: Long,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = remember(stripeColor) { moodTint(stripeColor) }
    // set from the title's own layout below so the fade is driven by what
    // the text rather than by guessing at a character count
    var titleWraps by remember(title) { mutableStateOf(false) }
    // pre-blended once per tile rather than composited as two layers at draw time
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
            // one fill not two these were a gradient and a translucent solid stacked on
            // it — two full-rect blends per tile times a dozen tiles every scrolled frame
            .background(brush = tileBrush)
            .clickable(onClick = onClick),
    ) {
        // drawn before the label so the text always sits on top of the artwork and
        // nothing at its left edge so a long title that wraps to two lines runs out
        // tile colour instead of over the covers dstin against a horizontal ramp
        // own offscreen layer to composite against
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .size(width = 84.dp, height = 76.dp)
                // a gradient of the tile's own colour painted over the covers' left edge
                // than a dstin alpha mask identical result against an opaque tile but dstin
                // needs its own offscreen layer per tile — a dozen of those per scrolled
                // exactly the kind of cost this grid cannot carry this is one extra rect
                // fades the artwork's own alpha to zero rather than painting tile-coloured
                // gradient over it

                // a colour scrim cannot work here: the tile underneath is itself a gradient
                // single flat colour only matches it at one x position and shows a hard
                // seam everywhere else — which is what the "cut not continuous" edge was
                // multiplies the artwork's alpha instead so whatever the tile is doing
                // simply shows through and there is nothing to mismatch

                // the offscreen layer this needs is why it is gated on titlewraps: only the
                // of categories with names long enough to take a second line pay for it
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
            // pulled apart along the diagonal and turned to clearly different angles —
            // tighter the back card only showed as a sliver and the pair read as one
            // square rather than as a fanned stack
            LayeredCover(
                url = covers.getOrNull(1),
                rotation = -24f,
                offsetX = (-2).dp,
                offsetY = 2.dp,
                size = 44.dp,
                // fully opaque on purpose: a graphicslayer with alpha < 1 and clip = true
                // be drawn in place and gets its own offscreen buffer the back card reads as
                // recessed from the overlap and the rotation alone
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
                // tightened: at the default line height a wrapped two-line title reads as two
                // separate labels rather than one that happens to run on
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

// one card in a [moodtile]'s fanned stack
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
                // no shadowelevation two of these per tile and a dozen tiles on screen meant
                // ~24 shadow-casting layers each forcing its own rendernode plus a shadow
                // on a grid that is constantly being scrolled the cards already read as
                // from the rotation and the overlap; the shadow was costing far more than it
                shape = RoundedCornerShape(8.dp)
                clip = true
            }
            .background(SearchColors.TileHigh),
    ) {
        if (url != null) {
            AsyncImage(
                // 128px covers a 44-48dp card at this density; 256 was decoding four times
                // pixels for every tile in a grid the user scrolls through constantly
                model = url.resize(128, 128),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// youtube's stripe colour is a fully-opaque argb long and many of them are
private fun moodTint(stripeColor: Long): Color {
    val argb = stripeColor.toInt()
    val r = ((argb shr 16) and 0xFF)
    val g = ((argb shr 8) and 0xFF)
    val b = (argb and 0xFF)
    if (r == 0 && g == 0 && b == 0) return Color(0xFF3A3A44)
    val lift = { c: Int -> (c + (170 - c) * 0.45f).toInt().coerceIn(40, 220) }
    return Color(lift(r), lift(g), lift(b))
}

// ───────────────────────────────────────────────────────────────────────────
// category selector
// ───────────────────────────────────────────────────────────────────────────

// the all / music / artist / video / album / playlist selector on the results
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

// ───────────────────────────────────────────────────────────────────────────
// rows
// ───────────────────────────────────────────────────────────────────────────

// one suggestion / history row: leading glyph label and an optional trailing
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

// the horizontal "…" overflow affordance horizontal not the vertical kebab
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

// thin rule used between result rows
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

// square artwork slot with the app's standard corner treatment
@Composable
fun SearchArtwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    corner: Dp = 6.dp,
) {
    // ask the cdn for roughly what we are actually going to draw rounded up to a

    // these were fixed 512px requests for rows barely 46dp (~138px) tall — a
    // pixels per thumbnail the decodes themselves are wasted bandwidth but the
    // the memory cache: oversized bitmaps evict each other so scrolling back up
    // cached and every thumbnail decodes again the grid (rather than the exact
    // number of distinct cache keys small so different call sites can share
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

// loading indicator three breathing dots rather than
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

// hairline outline for the card surfaces deliberately at the bottom of what
fun Modifier.searchCardBorder(radius: Dp = 16.dp): Modifier = this.border(
    width = androidx.compose.ui.unit.Dp.Hairline,
    color = Color.White.copy(alpha = 0.05f),
    shape = RoundedCornerShape(radius),
)

// composites [top] over [bottom] once so the two never have to be blended at draw time
private fun blendOver(top: Color, bottom: Color): Color {
    val a = top.alpha
    return Color(
        red = top.red * a + bottom.red * (1f - a),
        green = top.green * a + bottom.green * (1f - a),
        blue = top.blue * a + bottom.blue * (1f - a),
        alpha = 1f,
    )
}
