// playereditoverlaykt
// the layer a long press on the artwork drops you into: "select the part
// with an outline around each customizable region of the player (concept

// it draws outlines and nothing else — the real player stays fully visible
// it which is the whole point of picking a part by pointing at it rather

package com.example.musicfy.ui.player.customize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.R

// which part of the player an outline refers to only [cover] currently opens
enum class PlayerEditTarget {
    COVER,

    // the title/artist + progress + transport block
    CONTROLS,

    // the lyrics/queue card deck at the bottom
    BOTTOM_CARD,
}

// which layer of the customization flow the player is currently showing
enum class PlayerEditPhase {
    // normal playback the overwhelmingly common case
    NONE,

    // the blur-and-hold beat right after the long press see playerenteringeditoverlay
    ENTERING,

    // the "select the part you want to edit" outlines
    SELECTING,

    // the full "currently editing" page
    CUSTOMIZING,
}

// until it has been laid out have not been laid out yet in which case that
@Composable
fun PlayerEditOverlay(
    coverRect: Rect?,
    controlsRect: Rect?,
    bottomCardRect: Rect?,
    onSelect: (PlayerEditTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    // owned as an animatable rather than animatefloatasstate because this layer
    // animate out as well as in choosing a part used to swap straight to the
    // on the same frame; now the scrim the header and every outline dissolve
    // the selection is only reported once they are gone — so the two screens
    // of cutting
    val appearAnim = remember { Animatable(0f) }
    val appear = appearAnim.value
    var chosen by remember { mutableStateOf<PlayerEditTarget?>(null) }

    LaunchedEffect(Unit) {
        appearAnim.animateTo(1f, tween(360, easing = EditOverlayEasing))
    }
    LaunchedEffect(chosen) {
        val target = chosen ?: return@LaunchedEffect
        appearAnim.animateTo(0f, tween(300, easing = EditOverlayEasing))
        onSelect(target)
    }
    val select: (PlayerEditTarget) -> Unit = { target -> if (chosen == null) chosen = target }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // swallows everything the outlines below didn't want before the bottom
            // detectdraggestures — which lives on an ancestor — can see it without this a
            // swipe down or left while choosing a part collapses or dismisses the whole
            // instead of doing nothing

            // deliberately the main pass not initial: main travels descendant → ancestor
            // the outline hit targets and the dismiss-tap below (both inner to this
            // still get first refusal and only the leftovers are eaten here consuming on
            // initial pass would take the events on the way *down* and break those taps
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                    }
                }
            }
            // tapping anywhere that is not an outline leaves edit mode declared before
            // outlines below so they sit on top of it and win the tap
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .background(Color.Black.copy(alpha = 0.06f * appear))
    ) {
        val bounds = Rect(
            left = 0f,
            top = 0f,
            right = constraints.maxWidth.toFloat(),
            bottom = constraints.maxHeight.toFloat(),
        )

        EditOverlayBackButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = appear },
        )

        Text(
            text = "Select the part that you want to edit",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 30.dp, start = 76.dp, end = 76.dp)
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * -16f
                },
        )

        // the artwork runs under the status bar and under this layer's own header so
        // outline is held below both rather than being drawn across the clock and
        // button
        val headerBottomPx = with(LocalDensity.current) {
            (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HeaderClearance).toPx()
        }

        if (coverRect != null) {
            // "side to side": the artwork reaches both screen edges so it has no
            // boundary of its own inside the viewport and a closed box would sit on top
            val coverIsFullBleed = coverRect.left <= bounds.left + 1f &&
                coverRect.right >= bounds.right - 1f
            EditTargetOutline(
                rect = coverRect,
                bounds = bounds,
                appear = { appear },
                topLimitPx = headerBottomPx,
                sideRailsOnly = coverIsFullBleed,
                // the cover has no bottom edge of its own — it bleeds straight into the
                // controls a hard line across the middle of the artwork reads as a seam so
                // the stroke ramps to fully transparent over its lower portion instead
                fadeBottom = true,
            ) { select(PlayerEditTarget.COVER) }
        }
        if (controlsRect != null) {
            EditTargetOutline(rect = controlsRect, bounds = bounds, appear = { appear }) {
                select(PlayerEditTarget.CONTROLS)
            }
        }
        if (bottomCardRect != null) {
            EditTargetOutline(
                rect = bottomCardRect,
                bounds = bounds,
                appear = { appear },
                // the deck is anchored flush to the screen edge and bleeds past it by design
                // its outline is allowed to run off the bottom too clamping it inside the
                // viewport would draw a closing line across the card that isn't there
                clampBottom = false,
            ) { select(PlayerEditTarget.BOTTOM_CARD) }
        }
    }
}

// back affordance matching subsettingsscaffold's circular button and its 20dp page padding
@Composable
fun EditOverlayBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 20.dp, top = 12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back_ios),
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

// one tappable outline the rect arrives in pixels (that is what
@Composable
private fun EditTargetOutline(
    rect: Rect,
    bounds: Rect,
    appear: () -> Float,
    // floor for the top edge so an outline can be kept clear of the header chrome
    topLimitPx: Float = 0f,
    // ramps the stroke to fully transparent across the lower part of the box
    fadeBottom: Boolean = false,
    // when false the box may run past the bottom of the screen instead of closing above it
    clampBottom: Boolean = true,
    // draws the two vertical rails only with no top or bottom stroke a cover that
    sideRailsOnly: Boolean = false,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    // a tight outline sits right on the content; a little breathing room makes
    // selectable region and gives a usable touch target
    val padPx = with(density) { OutlinePadding.toPx() }
    val marginPx = with(density) { OutlineScreenMargin.toPx() }

    val left = (rect.left - padPx).coerceAtLeast(bounds.left + marginPx)
    val top = (rect.top - padPx).coerceAtLeast(maxOf(bounds.top + marginPx, topLimitPx))
    val right = (rect.right + padPx).coerceAtMost(bounds.right - marginPx)
    val bottom = if (clampBottom) {
        (rect.bottom + padPx).coerceAtMost(bounds.bottom - marginPx)
    } else {
        rect.bottom + padPx
    }
    if (right <= left || bottom <= top) return

    val width = with(density) { (right - left).toDp() }
    val height = with(density) { (bottom - top).toDp() }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .offset { IntOffset(left.toInt(), top.toInt()) }
            .graphicsLayer { alpha = appear() }
            // drawn rather than modifierborder because border only takes a solid colour
            // the cover's stroke has to fade out down its length
            .drawWithCache {
                val strokePx = OutlineStroke.toPx()
                val radiusPx = OutlineCorner.toPx()
                val brush = if (fadeBottom) {
                    Brush.verticalGradient(
                        0f to OutlineColor,
                        FadeStart to OutlineColor,
                        1f to Color.Transparent,
                    )
                } else {
                    SolidColor(OutlineColor)
                }
                onDrawWithContent {
                    drawContent()
                    if (sideRailsOnly) {
                        val x0 = strokePx / 2f
                        val x1 = size.width - strokePx / 2f
                        // rounded caps so each rail ends softly instead of stopping on a blunt
                        // edge that would read as the corner of the box we just removed
                        drawLine(
                            brush = brush,
                            start = Offset(x0, 0f),
                            end = Offset(x0, size.height),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            brush = brush,
                            start = Offset(x1, 0f),
                            end = Offset(x1, size.height),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    } else {
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(strokePx / 2f, strokePx / 2f),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = CornerRadius(radiusPx, radiusPx),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    )
}

private val EditOverlayEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val OutlinePadding = 10.dp
private val OutlineScreenMargin = 12.dp
private val OutlineStroke = 6.dp
private val OutlineCorner = 30.dp
private val OutlineColor = Color.White.copy(alpha = 0.6f)

// where the cover outline's stroke starts ramping away as a fraction of its height
private const val FadeStart = 0.55f

// vertical room reserved under the status bar for the back button and the header line
private val HeaderClearance = 76.dp
