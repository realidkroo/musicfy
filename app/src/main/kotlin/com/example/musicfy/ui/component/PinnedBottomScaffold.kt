package com.example.musicfy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Scrollable content with an action pinned to the bottom.
 *
 * Screens that used `Spacer(Modifier.weight(1f))` to push a button down look right at the default
 * display size and lose the button entirely at larger ones - the spacer collapses to zero and the
 * button is laid out past the bottom edge with no way to reach it. Here the content scrolls and
 * the action stays put.
 *
 * The scrim only appears when the content actually overflows, so a screen that fits shows a clean
 * background and only a scrolling one gets the fade under its button.
 */
@Composable
fun PinnedBottomScaffold(
    modifier: Modifier = Modifier,
    scrimColor: Color = Color(0xFF161616),
    scrimExtraHeight: Dp = 32.dp,
    bottomBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

    // maxValue is 0 until the content is taller than the viewport, which is exactly the condition
    // the scrim should track.
    val isScrollable = scrollState.maxValue > 0

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            content()
            // Keeps the tail of the content clear of the pinned action.
            Spacer(Modifier.height(bottomBarHeight))
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned {
                    val measured = with(density) { it.size.height.toDp() }
                    if ((measured - bottomBarHeight).value.absoluteValue > 0.5f) {
                        bottomBarHeight = measured
                    }
                },
        ) {
            if (isScrollable) {
                // matchParentSize, never fillMaxSize: fillMaxSize resolves against the *outer*
                // Box's constraints, which stretched this bar to the full screen height. A
                // full-height bar aligned to the bottom draws its content at the top - and the
                // measured height fed straight back into the spacer below, so it also flickered.
                // matchParentSize takes its size from the bar and does not influence it.
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to scrimColor.copy(alpha = 0.85f),
                                1f to scrimColor,
                            )
                        )
                )
            }
            bottomBar()
        }
    }
}
