// SwipeActions.kt

package com.example.musicfy.ui.component

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.example.musicfy.LocalDatabase
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.models.toMediaMetadata
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sign

/** one side of a swipeable row: what it shows while you drag, and what it does once you let go past the line */
@Immutable
class SwipeAction(
    @DrawableRes val icon: Int,
    val color: Color,
    val label: String,
    val onTrigger: () -> Unit,
)

/** sliding left queues a song (green), sliding right saves it or takes it out of your playlist (purple) */
val SwipeQueueColor = Color(0xFF34C759)
val SwipeSaveColor = Color(0xFF9B72F2)

private val SwipePillIdle = Color.White.copy(alpha = 0.12f)

// a hold has to be this long before it counts as a long press inside a swipeable row, so pressing
// down and then sliding never opens selection or the menu by accident
private const val SwipeHoldMillis = 650L

// past the line the row only follows a third of the finger, so it feels like it's straining
private const val SwipeResistance = 0.35f

// library and playlist writes outlive the row: the pill that started them is gone a moment later
private val SwipeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * a row you can drag sideways, like Frame 117: the row slides on a dark rounded slab, and the
 * action for that side pops out of the edge with a little spring. past the line it fills with the
 * action's colour (with a tick you can feel), and letting go there fires it while the row springs back.
 *
 * [start] is revealed on the left by sliding right, [end] on the right by sliding left. both are
 * only composed while their side is showing, so an action can look things up without every row
 * in a list paying for it.
 */
@Composable
fun SwipeActionsBox(
    modifier: Modifier = Modifier,
    start: (@Composable () -> SwipeAction)? = null,
    end: (@Composable () -> SwipeAction)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    // what's drawn, and where the finger really is (they part ways past the line)
    var offset by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    // 1 = the start side is armed, -1 = the end side, 0 = neither
    var armed by remember { mutableIntStateOf(0) }
    var fired by remember { mutableIntStateOf(0) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val startAction = remember { mutableStateOf<SwipeAction?>(null) }
    val endAction = remember { mutableStateOf<SwipeAction?>(null) }

    val threshold = with(density) { (rowSize.width * 0.26f).coerceIn(84.dp.toPx(), 150.dp.toPx()) }
    val deadSideLimit = with(density) { 22.dp.toPx() }

    fun resisted(raw: Float): Float {
        val allowed = if (raw > 0f) start != null else end != null
        val distance = abs(raw)
        val shown = when {
            // a side with nothing on it only gives a little, so the row doesn't feel stuck
            !allowed -> minOf(distance * 0.2f, deadSideLimit)
            distance <= threshold -> distance
            else -> threshold + (distance - threshold) * SwipeResistance
        }
        return sign(raw) * shown
    }

    fun unresisted(shown: Float): Float {
        val distance = abs(shown)
        return if (distance <= threshold) shown else sign(shown) * (threshold + (distance - threshold) / SwipeResistance)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            offset = 0f
            travel = 0f
            armed = 0
        }
    }

    val dragState = rememberDraggableState { delta ->
        travel += delta
        offset = resisted(travel)
        val side = when {
            offset >= threshold && start != null -> 1
            offset <= -threshold && end != null -> -1
            // a little hysteresis, so hovering right on the line doesn't buzz
            armed != 0 && abs(offset) >= threshold * 0.92f -> armed
            else -> 0
        }
        if (side != armed) {
            armed = side
            haptic.performHapticFeedback(
                if (side != 0) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick
            )
        }
    }

    val baseConfiguration = LocalViewConfiguration.current
    val longHold = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long
                get() = maxOf(baseConfiguration.longPressTimeoutMillis, SwipeHoldMillis)
        }
    }

    val showStart by remember { derivedStateOf { offset > 0.5f } }
    val showEnd by remember { derivedStateOf { offset < -0.5f } }

    // provided around the whole row, so a click handler passed in through [modifier] waits
    // the longer hold too, not just the ones inside [content]
    CompositionLocalProvider(LocalViewConfiguration provides longHold) {
        Box(
            modifier = modifier
                .onSizeChanged { rowSize = it }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = {
                        settle[0]?.cancel()
                        fired = 0
                        travel = unresisted(offset)
                    },
                    onDragStopped = { velocity ->
                        val side = armed
                        val action = when (side) {
                            1 -> startAction.value
                            -1 -> endAction.value
                            else -> null
                        }
                        if (action != null) {
                            fired = side
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            action.onTrigger()
                        }
                        armed = 0
                        settle[0] = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = 0f,
                                initialVelocity = velocity.coerceIn(-3000f, 3000f) * 0.35f,
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                            ) { value, _ ->
                                offset = value
                                travel = value
                            }
                            fired = 0
                        }
                    },
                ),
        ) {
            if (showStart && start != null) {
                val action = start()
                SideEffect { startAction.value = action }
                SwipePill(
                    action = action,
                    atStart = true,
                    offset = { offset },
                    active = armed == 1 || fired == 1,
                    fired = fired == 1,
                    rowHeight = rowSize.height,
                )
            }
            if (showEnd && end != null) {
                val action = end()
                SideEffect { endAction.value = action }
                SwipePill(
                    action = action,
                    atStart = false,
                    offset = { offset },
                    active = armed == -1 || fired == -1,
                    fired = fired == -1,
                    rowHeight = rowSize.height,
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = offset }
                    .drawBehind {
                        // the row rides on its own rounded slab while it's moving, side to side
                        val strength = (abs(offset) / 20.dp.toPx()).coerceIn(0f, 1f)
                        if (strength > 0f) {
                            val inset = 10.dp.toPx()
                            drawRoundRect(
                                color = HighlightColor.copy(alpha = HighlightColor.alpha * strength),
                                topLeft = Offset(inset, 0f),
                                size = Size(size.width - inset * 2f, size.height),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                    },
            ) {
                content()
            }
        }
    }
}

/** the action's pill, pinned to its edge: pops out with a spring, fills with colour once armed */
@Composable
private fun BoxScope.SwipePill(
    action: SwipeAction,
    atStart: Boolean,
    offset: () -> Float,
    active: Boolean,
    fired: Boolean,
    rowHeight: Int,
) {
    val density = LocalDensity.current
    val popDistance = with(density) { 18.dp.toPx() }
    val shown by remember { derivedStateOf { abs(offset()) > popDistance } }
    val pop = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "swipePillPop",
    )
    val fill by animateColorAsState(
        targetValue = if (active) action.color else SwipePillIdle,
        animationSpec = spring(stiffness = 700f),
        label = "swipePillFill",
    )
    val bump = remember { Animatable(1f) }
    LaunchedEffect(active, fired) {
        if (active) {
            bump.animateTo(if (fired) 1.38f else 1.28f, tween(85))
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 560f))
        }
    }

    // proportions from Frame 117: 1.3 x 0.6 of the row's height, a sixth of it in from the edge
    val heightDp = with(density) { rowHeight.toDp() }
    val pillHeight = (heightDp * 0.6f).coerceIn(30.dp, 40.dp)
    val pillWidth = (heightDp * 1.3f).coerceIn(64.dp, 84.dp)
    val edgeInset = (heightDp * 0.16f).coerceIn(8.dp, 12.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = edgeInset)
            .size(width = pillWidth, height = pillHeight)
            .graphicsLayer {
                // the spring overshoots a little past full size, so it lands with some give
                val p = pop.value
                scaleX = p
                scaleY = p
                alpha = p.coerceIn(0f, 1f)
                translationX = (1f - p) * 16.dp.toPx() * if (atStart) -1f else 1f
            }
            .background(fill, RoundedCornerShape(50)),
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = action.label,
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = bump.value
                    scaleY = bump.value
                },
        )
    }
}

/** sliding left on a song: straight onto the end of the queue */
@Composable
fun queueSwipeAction(mediaItem: () -> MediaItem): SwipeAction {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    return SwipeAction(
        icon = R.drawable.queue_music,
        color = SwipeQueueColor,
        label = stringResource(R.string.add_to_queue),
    ) {
        playerConnection?.addToQueue(mediaItem())
        Toast.makeText(context, R.string.added_to_queue, Toast.LENGTH_SHORT).show()
    }
}

/** sliding right on a song that's already in the database: into the library, or back out of it */
@Composable
fun librarySwipeAction(song: SongEntity): SwipeAction {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val inLibrary = song.inLibrary != null
    return SwipeAction(
        icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
        color = SwipeSaveColor,
        label = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
    ) {
        // the same as the song menu's library item: the feedback token, then the local flag
        // (which also tells YouTube)
        val token = if (inLibrary) song.libraryRemoveToken else song.libraryAddToken
        token?.let { SwipeScope.launch { YouTube.feedback(listOf(it)) } }
        database.query { update(song.toggleLibrary()) }
        Toast.makeText(
            context,
            if (inLibrary) R.string.removed_from_library else R.string.added_to_library,
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** the same for a song straight from YouTube, which may not be in the database yet */
@Composable
fun librarySwipeAction(song: SongItem): SwipeAction {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val inLibrary by remember(song.id) {
        database.song(song.id).map { it?.song?.inLibrary != null }.distinctUntilChanged()
    }.collectAsState(initial = false)
    return SwipeAction(
        icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
        color = SwipeSaveColor,
        label = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
    ) {
        // mirrors the YouTube song menu
        val adding = !inLibrary
        SwipeScope.launch { YouTube.toggleSongLibrary(song.id, adding) }
        if (adding) {
            database.transaction {
                insert(song.toMediaMetadata())
                inLibrary(song.id, LocalDateTime.now())
                addLibraryTokens(song.id, song.libraryAddToken, song.libraryRemoveToken)
            }
        } else {
            database.query { inLibrary(song.id, null) }
        }
        Toast.makeText(
            context,
            if (adding) R.string.added_to_library else R.string.removed_from_library,
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** sliding right inside a playlist you made: take the song out of it */
@Composable
fun removeFromPlaylistSwipeAction(onRemove: () -> Unit): SwipeAction =
    SwipeAction(
        icon = R.drawable.remove,
        color = SwipeSaveColor,
        label = stringResource(R.string.remove_from_playlist),
        onTrigger = onRemove,
    )
