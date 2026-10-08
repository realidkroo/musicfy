// TopToast.kt
//
// Feedback for an action ("Added to Queue", "Removed from playlist - Undo", "Stream failed") as a
// dark pill that drops in from the top of the screen. TopToaster is a process-wide singleton so it
// can be called from anywhere - a menu, a view model, the playback service - and TopToastHost, put
// once above the app, draws whatever it holds. A new toast replaces the one showing; a swipe up
// puts one away early.

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.R
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.stableSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

@androidx.compose.runtime.Immutable
data class TopToast(
    val id: Long,
    val text: String,
    val isError: Boolean,
    /** A cover to show in the rounded square; without one the square carries [iconRes]. */
    val thumbnail: String?,
    val iconRes: Int,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMs: Long,
)

object TopToaster {
    private val ids = AtomicLong(0)
    private val state = MutableStateFlow<TopToast?>(null)
    val current: StateFlow<TopToast?> = state.asStateFlow()

    /**
     * Shows [text] with a cover (or [iconRes] when there is none). With an [actionLabel] the pill
     * carries "-> label", which runs [onAction] and closes it; those stay up a little longer.
     */
    fun show(
        text: String,
        thumbnail: String? = null,
        iconRes: Int = R.drawable.queue_music,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = if (actionLabel != null) 5000L else 2600L,
    ) {
        state.value = TopToast(ids.incrementAndGet(), text, false, thumbnail, iconRes, actionLabel, onAction, durationMs)
    }

    fun error(text: String, durationMs: Long = 4000L) {
        state.value = TopToast(ids.incrementAndGet(), text, true, null, R.drawable.close, null, null, durationMs)
    }

    fun dismiss(id: Long) {
        state.value = state.value?.takeIf { it.id != id }
    }
}

private val ToastCard = Color(0xFF1E1E1E)
private val ToastSquare = Color(0xFFD9D9D9)
private val ToastError = Color(0xFFEE5A52)
private val ToastEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val ToastTitle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    letterSpacing = (-0.6).sp,
    color = Color.White,
)

/** Draws the current toast over everything in its parent; touches elsewhere pass straight through. */
@Composable
fun TopToastHost(modifier: Modifier = Modifier) {
    val toast by TopToaster.current.collectAsState()
    val top = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = toast,
            contentKey = { it?.id },
            transitionSpec = {
                (
                    slideInVertically(spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMedium)) { -it - 40 } +
                        fadeIn(tween(160)) +
                        scaleIn(tween(320, easing = ToastEase), initialScale = 0.92f)
                    ) togetherWith (
                    slideOutVertically(tween(260, easing = ToastEase)) { -it - 40 } + fadeOut(tween(200))
                    ) using SizeTransform(clip = false) { _, _ -> tween(1) }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = top + 8.dp, start = 14.dp, end = 14.dp),
            label = "topToast",
        ) { t ->
            if (t != null) ToastPill(t)
        }
    }
}

@Composable
private fun ToastPill(toast: TopToast) {
    val scope = rememberCoroutineScope()
    val lift = remember(toast.id) { Animatable(0f) }
    var held by remember(toast.id) { mutableStateOf(false) }
    LaunchedEffect(toast.id, held) {
        if (held) return@LaunchedEffect
        delay(toast.durationMs)
        TopToaster.dismiss(toast.id)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = lift.value }
            .shadow(14.dp, RoundedCornerShape(30.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(30.dp))
            .background(ToastCard)
            .pointerInput(toast.id) {
                // dragging up closes it; it follows the finger only that way
                detectVerticalDragGestures(
                    onDragStart = { held = true },
                    onDragEnd = {
                        held = false
                        if (lift.value < -28.dp.toPx()) TopToaster.dismiss(toast.id)
                        else scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onDragCancel = {
                        held = false
                        scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                ) { _, amount -> scope.launch { lift.snapTo((lift.value + amount).coerceAtMost(0f)) } }
            }
            .defaultMinSize(minHeight = 68.dp)
            .padding(start = 12.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
    ) {
        if (toast.isError) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(ToastError),
            ) {
                Icon(painterResource(R.drawable.close), null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(ToastSquare),
            ) {
                if (toast.thumbnail != null) {
                    AsyncImage(
                        model = toast.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(painterResource(toast.iconRes), null, tint = Color(0xFF232323), modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = toast.text,
            style = ToastTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (toast.actionLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        TopToaster.dismiss(toast.id)
                        toast.onAction?.invoke()
                    }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Icon(painterResource(R.drawable.arrow_forward), null, tint = Color(0xFFE6E6E6), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    toast.actionLabel,
                    style = TextStyle(fontFamily = InterFontFamily, fontSize = 15.sp, color = Color(0xFFE6E6E6)),
                    maxLines = 1,
                )
            }
        } else if (!toast.isError) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 12.dp).size(26.dp).clip(CircleShape).background(ToastSquare),
            ) {
                Icon(painterResource(R.drawable.check), null, tint = Color(0xFF232323), modifier = Modifier.size(16.dp))
            }
        }
    }
}
