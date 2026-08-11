// MorphingCover.kt
// v0 minimal replacement for MorphingPlayer.kt: cover art + play/skip-next morph only.
// No title/artist text, no tiered low-res/high-res/canvas-artwork loading, no background-style
// branching. Old file kept for reference at /old-player/MorphingPlayer.kt.

package com.example.musicfy.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.example.musicfy.R
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.constants.ThumbnailCornerRadius
import com.example.musicfy.extensions.togglePlayPause
import com.example.musicfy.ui.player.models.TrackInfo
import com.example.musicfy.ui.utils.resize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import com.example.musicfy.canvas.CanvasArtwork
import com.example.musicfy.canvas.MonochromeApiCanvas
import com.example.musicfy.applecanvas.AppleMusicCanvasProvider
import com.example.musicfy.LocalGlassState
import com.example.musicfy.constants.CanvasThumbnailAnimationKey
import com.example.musicfy.constants.CanvasWifiOnlyKey
import com.example.musicfy.constants.DisableBlurKey
import com.example.musicfy.constants.PlayVideoBackgroundKey
import com.example.musicfy.constants.PlayerBackgroundStyle
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.player.customize.DiscCoverStack
import com.example.musicfy.ui.player.customize.PlayerBackgroundContent
import com.example.musicfy.ui.player.customize.CoverGradientBackdrop
import com.example.musicfy.ui.player.customize.coverArtBox
import com.example.musicfy.ui.player.customize.isDisc
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.press3D
import com.example.musicfy.utils.rememberPreference
import androidx.core.content.getSystemService

/**
 * Pre-computed static endpoints for the morphing animation. Calculated once, no allocations
 * per frame — same technique proven in the old MorphingPlayer.kt.
 */
@Stable
private class MorphEndpoints(
    val miniArtSize: Dp,
    val miniArtX: Dp,
    val miniArtY: Dp,
    val miniPlayX: Dp,
    val miniPlayY: Dp,
    val miniSkipX: Dp,
    val miniSkipY: Dp,
    val miniTextX: Dp,
    val miniTextWidth: Dp,
    val lyricsArtSize: Dp,
    val lyricsArtX: Dp,
    val lyricsArtY: Dp,
    val fullTextX: Dp,
    val fullTextY: Dp,
    val fullTextWidth: Dp,
    val fullWidth: Dp,
    /**
     * Width of the artwork box in the expanded player. Separate from [fullWidth] (which the
     * backdrop still uses) purely so a cover style can inset the artwork without also shrinking
     * the background behind it — [fullWidth] for every style except SQUARED.
     */
    val fullArtWidth: Dp,
    val fullArtHeight: Dp,
    val fullArtX: Dp,
    val fullArtY: Dp,
    val fullPlayX: Dp,
    val fullPlayY: Dp,
    val miniHeight: Dp,
    val fullHeight: Dp,
)

@Stable
private class MorphEndpointsPx(
    val miniArtSizePx: Float,
    val miniArtXPx: Float,
    val miniArtYPx: Float,
    val miniPlayXPx: Float,
    val miniPlayYPx: Float,
    val miniSkipXPx: Float,
    val miniSkipYPx: Float,
    val miniTextXPx: Float,
    val miniTextWidthPx: Float,
    val lyricsArtSizePx: Float,
    val lyricsArtXPx: Float,
    val lyricsArtYPx: Float,
    val fullTextXPx: Float,
    val fullTextYPx: Float,
    val fullTextWidthPx: Float,
    val fullWidthPx: Float,
    val fullArtWidthPx: Float,
    val fullArtHeightPx: Float,
    val fullArtXPx: Float,
    val fullArtYPx: Float,
    val fullPlayXPx: Float,
    val fullPlayYPx: Float,
    val miniHeightPx: Float,
    val fullHeightPx: Float,
)

private enum class MorphElement { ART, PLAY, SKIP, TEXT, BACKDROP }

// The progress value at which the full-player backdrop reaches full opacity and the pill's own
// blurred background reaches zero. Both the backdrop's and the pill's alpha are computed as
// exact complements of this same constant (see MorphingCover below), so at every progress value
// their combined coverage is ~1 — no gap, regardless of drag speed/direction.
private const val PILL_FADE_END = 0.15f

/** Corner radius of the shrunken cover once it's landed in the lyrics page's header. */
private val LyricsHeaderCornerRadius = 12.dp

/** Corner radius the SQUARED style keeps once fully expanded (concept screen 83). */
private val SquaredCoverCornerRadius = 22.dp



private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * How present the vinyl is, 0..1 — a draw-phase read, never a state subscription.
 *
 * 1 only in the fully open player. It dissolves out over the last third of the collapse (well
 * before the artwork reaches pill size, so you never see a shrunken platter) and again as the
 * lyrics page takes over, revealing the plain square artwork underneath in both directions.
 */
private fun discWeight(
    progressProvider: () -> Float,
    lyricsProgressProvider: () -> Float,
): Float {
    val expanded = ((progressProvider() - 0.55f) / 0.30f).coerceIn(0f, 1f)
    val lyrics = ((lyricsProgressProvider() - 0.10f) / 0.40f).coerceIn(0f, 1f)
    return expanded * (1f - lyrics)
}

/**
 * Positions a morphing element by reading progressProvider()/horizontalOffsetProvider() in the
 * layout phase (not composition) — this is what prevents recomposition of the whole morph tree
 * on every drag frame. Verified this behavior in the old MorphingPlayer.kt before reusing it.
 */
private fun Modifier.morphLayout(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    endpointsPx: MorphEndpointsPx,
    element: MorphElement,
    lyricsProgressProvider: () -> Float = { 0f },
) = this.layout { measurable, constraints ->
    val p = progressProvider()
    val hOffset = horizontalOffsetProvider()

    val (x, y, w, h) = when (element) {
        MorphElement.ART -> {
            val artW = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)

            // Second stage, layered on top of the pill->fullscreen morph: as the lyrics page
            // opens, that same rect continues shrinking into the header slot at the top-left.
            val lp = lyricsProgressProvider()
            if (lp <= 0f) {
                floatArrayOf(artX, artY, artW, artH)
            } else {
                // The header slot is the lyrics endpoint only for as long as the sheet is
                // actually expanded; `p` walks that endpoint back onto the mini-pill slot as the
                // sheet collapses. At p = 0 BOTH sides of the outer lerp are the pill, so the
                // cover has exactly ONE destination no matter what lp is doing.
                //
                // Without this, lp stays 1 for the whole of a swipe-down from the lyrics page
                // (nothing tells lyricsProgress the sheet is collapsing — see BottomSheet's
                // collapseSoft, which is the only thing that gesture calls), and at lp = 1 the
                // lerps below ignore `p` entirely: the cover stayed pinned at
                // statusBarTop + 28dp in the container's local space and simply rode the
                // container's translationY down, landing ~44dp below the pill's own artwork slot
                // and still 60dp wide instead of 48dp. That is the "ends up too low / doesn't
                // land on the mini pill".
                //
                // hOffset applies to both terms so the cover still tracks a horizontal
                // song-change swipe at p = 0 (state.horizontalOffset only ever moves while
                // collapsed, so this is a no-op on the lyrics page itself).
                val lyricsX = lerpF(endpointsPx.miniArtXPx, endpointsPx.lyricsArtXPx, p) + hOffset
                val lyricsY = lerpF(endpointsPx.miniArtYPx, endpointsPx.lyricsArtYPx, p)
                val lyricsSize = lerpF(endpointsPx.miniArtSizePx, endpointsPx.lyricsArtSizePx, p)
                floatArrayOf(
                    lerpF(artX, lyricsX, lp),
                    lerpF(artY, lyricsY, lp),
                    lerpF(artW, lyricsSize, lp),
                    lerpF(artH, lyricsSize, lp),
                )
            }
        }
        MorphElement.PLAY -> {
            val playX = lerpF(endpointsPx.miniPlayXPx, endpointsPx.fullPlayXPx, p)
            val playY = lerpF(endpointsPx.miniPlayYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(playX, playY, -1f, -1f)
        }
        MorphElement.SKIP -> {
            val skipX = lerpF(endpointsPx.miniSkipXPx, endpointsPx.fullPlayXPx + 80f, p)
            val skipY = lerpF(endpointsPx.miniSkipYPx, endpointsPx.fullPlayYPx, p)
            floatArrayOf(skipX, skipY, -1f, -1f)
        }
        MorphElement.TEXT -> {
            // Interpolates position *and* width, the same way ART does, so the label tracks the
            // cover's expansion rather than just fading in place. Height stays at the pill's own
            // height throughout — the text column centres itself inside that box, and the block
            // has faded out well before the full-player endpoint matters (SongInfoRow owns the
            // title once expanded, so these two must never be legible at the same time).
            val textX = lerpF(endpointsPx.miniTextXPx, endpointsPx.fullTextXPx, p) + hOffset
            val textY = lerpF(0f, endpointsPx.fullTextYPx, p)
            val textW = lerpF(endpointsPx.miniTextWidthPx, endpointsPx.fullTextWidthPx, p)
            floatArrayOf(textX, textY, textW, endpointsPx.miniHeightPx)
        }
        MorphElement.BACKDROP -> {
            // Actually grows from the pill bar's own bounds up to fullscreen — a real
            // layout-phase morph (position/size interpolation), not just a fade+scale of an
            // already-fullsize rectangle. Uses the explicit fullHeightPx endpoint (same
            // source as ART's own full-size target) rather than the incoming `constraints`,
            // since those aren't guaranteed to reflect the actual player height depending on
            // how bounds propagate through the modifier chain — this is why it wasn't visibly
            // growing before.
            val backdropH = lerpF(endpointsPx.miniHeightPx, endpointsPx.fullHeightPx, p)
            floatArrayOf(0f, 0f, endpointsPx.fullWidthPx, backdropH)
        }
    }

    val childConstraints = if (w > 0f && h > 0f) {
        Constraints.fixed(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    } else {
        constraints
    }

    val placeable = measurable.measure(childConstraints)
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.toInt(), y.toInt())
    }
}

/**
 * Cover art + play/skip-next, morphing from the mini-player pill to fullscreen.
 * v0: no title/artist morph, single-resolution image load, no background-style branching.
 */
@Composable
fun MorphingCover(
    progressProvider: () -> Float,
    horizontalOffsetProvider: () -> Float,
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    playbackState: Int,
    maxWidth: Dp,
    maxHeight: Dp,
    collapsedBound: Dp,
    pureBlack: Boolean,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    /**
     * 0 = normal player, 1 = lyrics page. Drives the cover shrinking to the small top-left slot
     * on the lyrics screen. This exists so there is exactly ONE cover image in the app: the
     * lyrics page used to draw its own 48dp thumbnail while this full-size one stayed mounted
     * behind it, which is why two covers were visible.
     */
    lyricsProgressProvider: () -> Float = { 0f },
    /**
     * How the artwork is presented. EDGE_TO_EDGE — the default — is the treatment this file has
     * always drawn, and takes exactly the same code path it always did; every other style is an
     * additional branch alongside it.
     */
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.EDGE_TO_EDGE,
    /**
     * Which backdrop to draw. COVER_GRADIENT — the default — is the blurred-artwork + warp
     * shader block below, left where it was; the other three come from PlayerBackgroundStyles.kt.
     */
    backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER_GRADIENT,
    /** Shows the disc's name plate outline even when the user hasn't set a name yet. */
    editMode: Boolean = false,
    /** Long press on the artwork while fully expanded — opens the edit overlay. */
    onLongPressCover: (() -> Unit)? = null,
    /**
     * Reports the artwork box's live rect in root coordinates, so the edit overlay can outline
     * wherever the cover actually is for the current style instead of re-deriving the endpoint
     * arithmetic above.
     */
    onArtBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Prefetch the next queue item's artwork as soon as the current track is known, so by the
    // time the song actually ends — or the user taps skip — the image is already in Coil's
    // cache and the crossfade above is effectively instant instead of loading over the network
    // while the old cover is held on screen.
    // Hoisted out of the prefetch block below so the disc styles' skip choreography can also read
    // currentIndex (it needs the direction of the move to know which way to slide the discs).
    val emptyQueue = remember { kotlinx.coroutines.flow.MutableStateFlow(com.example.musicfy.ui.player.models.QueueState()) }
    val queueState by (playerConnection?.uiState?.queueState ?: emptyQueue).collectAsState()

    if (playerConnection != null) {
        LaunchedEffect(queueState.currentIndex, queueState.items) {
            val nextUrl = queueState.items.getOrNull(queueState.currentIndex + 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)
                ?: return@LaunchedEffect
            // Size/precision must match the display request below, or the prefetch lands under a
            // different memory-cache key and the cover still decodes from disk on skip.
            val request = ImageRequest.Builder(context)
                .data(nextUrl)
                .size(CoilSize(1200, 1200))
                .precision(Precision.INEXACT)
                .build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    val endpoints = remember(maxWidth, maxHeight, statusBarTop, coverStyle, 2) {
        val miniHeight = 64.dp
        val miniArtSize = 48.dp
        val miniArtX = 36.dp
        val miniArtY = (miniHeight - miniArtSize) / 2

        val miniPlaySize = 36.dp
        val miniSkipSize = 36.dp
        val miniSkipX = maxWidth - 36.dp - miniSkipSize
        val miniSkipY = (miniHeight - miniSkipSize) / 2
        val miniPlayX = miniSkipX - 8.dp - miniPlaySize
        val miniPlayY = (miniHeight - miniPlaySize) / 2

        // Text occupies the gap between the artwork and the play button. Width is derived from
        // those two rather than hardcoded, so it can never run underneath the controls; the
        // right-edge fade below then softens wherever a long title actually reaches.
        val miniTextX = miniArtX + miniArtSize + 12.dp
        val miniTextWidth = (miniPlayX - 10.dp - miniTextX).coerceAtLeast(0.dp)

        // Expanded-player artwork rect, per style — shared with the customization page's preview
        // so the two can't disagree. See PlayerCoverLayout.kt.
        val artBox = coverArtBox(coverStyle, maxWidth, maxHeight, statusBarTop)

        MorphEndpoints(
            miniArtSize = miniArtSize,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            miniTextX = miniTextX,
            miniTextWidth = miniTextWidth,
            // Must match LyricsScreen's own header box exactly (36dp horizontal inset —
            // matching the 36dp the lyrics list/timestamp use, statusBarsPadding +
            // 28dp top, 60dp square) — that page no longer draws a thumbnail of its own, this
            // cover lands in the hole where it used to be.
            lyricsArtSize = 60.dp,
            lyricsArtX = 36.dp,
            lyricsArtY = statusBarTop + 28.dp,
            fullTextX = 24.dp,
            fullTextY = maxHeight * 0.63f + 24.dp,
            fullTextWidth = (maxWidth - 48.dp).coerceAtLeast(0.dp),
            fullWidth = maxWidth,
            fullArtWidth = artBox.width,
            fullArtHeight = artBox.height,
            fullArtX = artBox.x,
            fullArtY = artBox.y,
            fullPlayX = (maxWidth / 2) - 18.dp,
            fullPlayY = maxHeight - 200.dp,
            miniHeight = miniHeight,
            fullHeight = maxHeight,
        )
    }

    val endpointsPx = remember(endpoints, density) {
        with(density) {
            MorphEndpointsPx(
                miniArtSizePx = endpoints.miniArtSize.toPx(),
                miniArtXPx = endpoints.miniArtX.toPx(),
                miniArtYPx = endpoints.miniArtY.toPx(),
                miniPlayXPx = endpoints.miniPlayX.toPx(),
                miniPlayYPx = endpoints.miniPlayY.toPx(),
                miniSkipXPx = endpoints.miniSkipX.toPx(),
                miniSkipYPx = endpoints.miniSkipY.toPx(),
                miniTextXPx = endpoints.miniTextX.toPx(),
                miniTextWidthPx = endpoints.miniTextWidth.toPx(),
                lyricsArtSizePx = endpoints.lyricsArtSize.toPx(),
                lyricsArtXPx = endpoints.lyricsArtX.toPx(),
                lyricsArtYPx = endpoints.lyricsArtY.toPx(),
                fullTextXPx = endpoints.fullTextX.toPx(),
                fullTextYPx = endpoints.fullTextY.toPx(),
                fullTextWidthPx = endpoints.fullTextWidth.toPx(),
                fullWidthPx = endpoints.fullWidth.toPx(),
                fullArtWidthPx = endpoints.fullArtWidth.toPx(),
                fullArtHeightPx = endpoints.fullArtHeight.toPx(),
                fullArtXPx = endpoints.fullArtX.toPx(),
                fullArtYPx = endpoints.fullArtY.toPx(),
                fullPlayXPx = endpoints.fullPlayX.toPx(),
                fullPlayYPx = endpoints.fullPlayY.toPx(),
                miniHeightPx = endpoints.miniHeight.toPx(),
                fullHeightPx = endpoints.fullHeight.toPx(),
            )
        }
    }

    val miniPlaySize = 36.dp

    val isDiscStyle = coverStyle.isDisc

    // Both of these are derivedStateOf over a draw-phase progress value on purpose: they only
    // invalidate when the threshold is actually crossed, never once per drag frame. Same shape
    // as isPillMounted / showBackdrop / warpClockActive elsewhere in this file.
    val longPressEnabled by remember {
        derivedStateOf { progressProvider() > 0.9f && lyricsProgressProvider() < 0.1f }
    }
    // `!editMode` on both this and the warp clock below: while the editor is up the whole player
    // is sitting under a full-screen Gaussian blur, and every one of these animations invalidates
    // that blurred layer on every frame — which is exactly what stops HWUI caching it and why
    // entering edit mode measured 62% of frames slow on draw-command issue. Nothing animating
    // under a blur that heavy is legible, so freezing them costs nothing visually.
    //
    // Keyed on editMode: an unkeyed remember would capture the flag's first value and never see
    // it change.
    val discSpinActive by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.9f && lyricsProgressProvider() < 0.6f }
    }

    // Real collapsed-pill height, in px, as reported by BottomSheetState — used below to
    // counteract the sweep the outer BottomSheet container applies to this whole composable as
    // it drags between pill and fullscreen (see the comment on the pill background below).
    val collapsedBoundPx = with(density) { collapsedBound.toPx() }

    // Warp shader + its driving clock are created ONCE here, unconditionally, instead of inside
    // the conditionally-mounted backdrop block below. Recreating a RuntimeShader every time the
    // backdrop mounts means recompiling the AGSL program on the GPU right at the moment the user
    // starts swiping up — that compile hitch is what caused the swipe-up-only lag (swiping down
    // never showed it because the backdrop unmounts, not mounts, at the end of that gesture, and
    // teardown is cheap). Keeping the shader instance and clock alive permanently costs nothing
    // while the backdrop isn't drawn (nothing reads them), and "freezes" it in the sense that it
    // never needs to be torn down and rebuilt again.
    //
    // warpTimeState is read via `.value` only inside a graphicsLayer draw block below (draw
    // phase), never destructured with `by` at composable scope — that `by` pattern is what
    // silently forced a full recomposition of the entire backdrop subtree every single frame
    // while it was mounted, which was the other major contributor to the swipe lag.
    val warpShader = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.graphics.RuntimeShader(
                """
                uniform float2 resolution;
                uniform float time;
                uniform shader image;

                half4 main(float2 fragCoord) {
                    float2 uv = fragCoord / resolution;
                    float t = time * 0.5;

                    float2 warpOffset = float2(
                        sin(uv.y * 3.0 + t) * 0.05 + cos(uv.x * 2.0 - t * 0.6) * 0.04,
                        cos(uv.x * 3.0 + t * 0.7) * 0.05 + sin(uv.y * 2.5 + t * 0.9) * 0.04
                    );

                    float2 distortedCoord = fragCoord + warpOffset * resolution;
                    return image.eval(distortedCoord);
                }
                """.trimIndent()
            )
        } else {
            null
        }
    }
    // The clock driving the shader's `time` uniform is NOT kept unconditionally alive like the
    // shader object above — a rememberInfiniteTransition/animateFloat here does no GPU work, just
    // a coroutine that calls withInfiniteAnimationFrameNanos every vsync to update a float, so
    // Choreographer would otherwise keep getting woken up 60x/second for the entire lifetime of
    // this always-mounted composable, even while the player sits collapsed as a mini pill (the
    // overwhelming majority of real usage). Gating just the clock — never the RuntimeShader
    // object itself — on the same threshold that already gates the backdrop's composition below
    // stops that sustained wakeup cost while idle, without touching the AGSL recompile-avoidance
    // this comment block is about.
    // Also off once the lyrics page has taken over. The warp is a full-screen RuntimeShader
    // re-evaluated on an infinite frame clock; on the lyrics page it is almost entirely hidden
    // behind the text and its fades, so it was burning a shader pass every frame for nothing —
    // exactly the budget the karaoke sweep needs to hold 120Hz.
    val warpClockActive by remember(editMode) {
        derivedStateOf { !editMode && progressProvider() > 0.02f && lyricsProgressProvider() < 0.6f }
    }
    val warpTimeState = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(warpClockActive) {
        if (!warpClockActive) return@LaunchedEffect
        while (isActive) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameTimeNanos ->
                // Absolute frame time mod 100 (matching the original 100-unit / 100s cycle)
                // instead of delta-accumulation, so restarting this loop never drifts.
                warpTimeState.floatValue = ((frameTimeNanos / 1_000_000f) * (100f / 100_000f)).mod(100f)
            }
        }
    }

    // YouTube official-music-video background: resolved once here (rather than down in the
    // cover-art block) so both the fullscreen ambient backdrop further below AND the cover card
    // can share the same resolved video, lyrics-sync anchors, and captured live frames instead
    // of duplicating the search/resolve/decode work.
    val playVideoBackground by rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    var videoInfo by remember(trackInfo.mediaId) { mutableStateOf<OfficialMusicVideo?>(null) }
    LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist, playVideoBackground) {
        val mediaId = trackInfo.mediaId
        val titleStr = trackInfo.title
        val artistStr = trackInfo.artist
        if (!playVideoBackground || mediaId.isBlank() || titleStr.isBlank() || artistStr.isBlank()) {
            videoInfo = null
            return@LaunchedEffect
        }
        if (YouTubeVideoUrlCache.contains(mediaId)) {
            videoInfo = YouTubeVideoUrlCache.get(mediaId)
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) { findOfficialMusicVideo(titleStr, artistStr) }
        YouTubeVideoUrlCache.put(mediaId, resolved)
        videoInfo = resolved
    }

    // "Sync with lyrics" sub-option: pairs the song's own timed lyrics against the video's
    // YouTube transcript by line text, so the video seeks to wherever it's actually singing the
    // current line instead of just the same fraction of the way through. Falls back to plain
    // proportional sync (a null anchor list) whenever lyrics are missing or nothing matched.
    val lyricsSyncEnabled by rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = false)
    val currentLyrics by playerConnection?.currentLyrics?.collectAsState(initial = null)
        ?: androidx.compose.runtime.mutableStateOf(null)
    var lyricVideoAnchors by remember(trackInfo.mediaId) { mutableStateOf<List<LyricVideoAnchor>?>(null) }
    LaunchedEffect(videoInfo?.videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val mediaId = trackInfo.mediaId
        val videoId = videoInfo?.videoId
        val lyricsRaw = currentLyrics?.lyrics
            ?.takeIf { it.isNotBlank() && it != com.example.musicfy.db.entities.LyricsEntity.LYRICS_NOT_FOUND }
        if (!lyricsSyncEnabled || videoId == null || lyricsRaw == null) {
            lyricVideoAnchors = null
            return@LaunchedEffect
        }
        if (LyricVideoAnchorCache.contains(mediaId)) {
            lyricVideoAnchors = LyricVideoAnchorCache.get(mediaId)
            return@LaunchedEffect
        }
        val anchors = withContext(Dispatchers.IO) {
            runCatching { buildLyricVideoAnchors(videoId, lyricsRaw) }.getOrNull()
        }
        LyricVideoAnchorCache.put(mediaId, anchors)
        lyricVideoAnchors = anchors
    }

    // Captures the cover card's live video frames (see YouTubeVideoBackground's glassRoot wrap
    // below) so the fullscreen backdrop can redraw a blurred, mirrored copy of the SAME decoded
    // frames instead of decoding the video a second time.
    val videoGlassState = remember(trackInfo.mediaId) { GlassState() }

    // glassRoot captures this whole subtree's actual rendered content into a RenderNode, so
    // anything elsewhere reading the same GlassState (the seam blur, added in
    // BottomSheetPlayer.kt as a sibling outside this composable so there's no risk of it
    // capturing itself) can redraw a genuinely blurred version of whatever's really here — the
    // same mechanism HomeScreen.kt already uses for its scroll-driven top bar blur, reused here
    // instead of the earlier hand-rolled "duplicate + stretch + blur a copy of the cover" attempt
    // that kept showing smeared-photo artifacts and a visible hard edge.
    // Always record, never gate on a progress threshold — same fix applied earlier this session
    // to SubSettingsScaffold's glassRoot for the identical symptom ("blur sometimes just doesn't
    // appear"). Gating at > 0.80f left almost no margin before consumers like SeamBlur (fading in
    // from 0.85) or PlayerBottomCardStack actually need a valid display list; if recording hadn't
    // caught up yet on a given frame, they read a stale/empty node and rendered nothing. This
    // subtree is cheap enough to double-draw continuously — reliability wins over the saved cost.
    // ...with one exception: edit mode. The capture is a second full-screen draw of this whole
    // subtree every frame, and while the editor is up nothing can read anything useful from it —
    // in ENTERING the player is behind a full-screen blur, and in CUSTOMIZING the stage covers it
    // outright, with the card deck (the other consumer) already composed out. Measured entering
    // edit mode at 11.8ms of GPU per frame against an 8.3ms budget at 120Hz, with the render
    // thread stalled in swapBuffers; this is one of the passes making that up.
    // Only while the pill is what the user is actually looking at. Hysteresis-free on purpose:
    // this is a composition-time boolean that flips once per open/close, and press3D is inert
    // when false, so there is nothing to be gained from a dead band here.
    val isPillPressable by remember {
        derivedStateOf { progressProvider() < 0.02f && !editMode }
    }

    // Pill press feedback. Applied to the whole morph root rather than to the pill's background
    // Box, because the pill's artwork, label and transport buttons are SIBLINGS of that background
    // (each absolutely positioned by morphLayout) — tilting the background alone would slide the
    // glass out from under its own contents.
    //
    // The pivot is the pill's own centre, not this node's: the node is the full-screen morph
    // container, and a centred pivot would rotate the pill about a point most of a screen below
    // it. Only enabled while the pill is actually the thing on screen, so pressing anything in the
    // expanded player never tilts the player.
    val pillPressOrigin = remember(endpointsPx.fullHeightPx) {
        val h = endpointsPx.fullHeightPx
        TransformOrigin(0.5f, if (h > 0f) (endpointsPx.miniHeightPx / 2f) / h else 0f)
    }

    Box(
        modifier = modifier
            .glassRoot(glassState, isActive = { !editMode })
            .press3D(
                maxTilt = 6f,
                pressedScale = 0.97f,
                enabled = isPillPressable,
                origin = pillPressOrigin,
            )
    ) {
        // Backdrop blur for the pill — blurs whatever's actually behind the mini player (the
        // real Haze "source" registered in MainActivity), not a copy of the cover art. Its own
        // alpha is the exact complement of the full backdrop's alpha below (both ramp across the
        // same PILL_FADE_END window, driven directly by progress every frame, not by a
        // fixed-duration AnimatedVisibility fade) — so at any instant, in either drag direction,
        // pillAlpha + backdropAlpha == 1. A fixed-duration fade can only approximate that if the
        // gesture happens to move at the speed the animation was tuned for; at any other speed it
        // desyncs from progress and leaves a gap where neither layer is opaque — which is exactly
        // what showed up as "a transparent thing" on swipe-down.
        //
        // Mount/unmount still uses hysteresis (enter below 0.16, exit above 0.20), but purely as
        // a cost optimization (stop paying for Haze's capture+blur once fully expanded) — by then
        // alpha has already reached exactly 0 at progress 0.15, so the mount boundary can never
        // itself cause a visible seam the way the old fixed-duration fade could.
        //
        // translationY counter-sweep: this whole composable lives inside BottomSheet's single
        // "expanding clipping container", which is a FIXED (expandedBound-tall) box that
        // BottomSheet itself slides via `translationY = expandedBound - state.value` as you drag
        // — that's what makes the cover art and buttons convincingly grow from the pill up into
        // fullscreen. But it means anything anchored at this box's local top (which is where the
        // pill sits, at progress 0) rides that same slide the instant progress > 0, even before
        // it's grown into anything — a small, non-growing rectangle just sliding up the screen,
        // which read as "it just goes up instead of stay and fade" with a visible trailing edge
        // ("ugly outline"). Applying the exact inverse of BottomSheet's own sweep here cancels it
        // out, so this box stays pinned to the real on-screen collapsed-pill position the whole
        // time it's visible, and alpha is the only thing that changes.
        val navBackdropGlassState = LocalGlassState.current ?: remember { GlassState() }
        val pillMountedHolder = remember { booleanArrayOf(true) }
        val isPillMounted by remember {
            derivedStateOf {
                val progress = progressProvider()
                val next = when {
                    progress < 0.16f -> true
                    progress > 0.20f -> false
                    else -> pillMountedHolder[0]
                }
                pillMountedHolder[0] = next
                next
            }
        }
        if (isPillMounted) {
            val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        // Deliberately NOT faded against the backdrop's own ramp.
                        //
                        // The two alphas were exact complements, on the theory that they would
                        // sum to full coverage at every progress. Alpha compositing does not work
                        // that way: a 0.5 layer over a 0.5 layer resolves to 0.75, not 1, so for
                        // the whole length of that crossfade the pair was ~25% transparent at its
                        // worst and the home screen showed straight through the mini player. That
                        // is the "transparent thing" on open and close.
                        //
                        // The pill instead stays fully opaque for as long as it is mounted and the
                        // backdrop fades in ON TOP of it, so total coverage is 1 at every instant
                        // and in both directions. The backdrop reaches full opacity at
                        // PILL_FADE_END and this unmounts at 0.20, so the handover has margin
                        // rather than a seam. Shape is not a concern either: BottomSheet's own
                        // clip is still at the pill's inset and corner radius this early in the
                        // drag, so the wider backdrop is clipped to the pill's silhouette.
                        translationY = progressProvider().coerceIn(0f, 1f) * (endpointsPx.fullHeightPx - collapsedBoundPx)
                    }
            ) {
                GlassPillBackground(
                    state = navBackdropGlassState,
                    blurRadius = { 24f },
                    tint = containerColor.copy(alpha = 0.65f),
                    foundationColor = containerColor,
                    // CLAMP, not the default DECAL — this pill's own bounds ARE the intended
                    // blur extent (no fade-out), so edges need to read as fully blurred right up
                    // to the border instead of washing to transparent near it.
                    tileMode = android.graphics.Shader.TileMode.CLAMP,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Full-player background behind the cover card and every other element — the source
        // image is blurred ONCE at a fixed size (48x48, scaled up), then that already-blurred
        // result is just alpha-faded in place. The *layer itself* morphs: it grows from the pill
        // bar's own bounds up to fullscreen using the same layout-phase morphLayout technique as
        // the cover art below, so it's a real position/size interpolation (a morph), not a fixed
        // full-size rectangle that just fades/scales from its own center. Only composed past the
        // very start of the drag (progress > 0.02) — nothing while collapsed in the pill.
        //
        // The blur is baked into the 48x48 source bitmap itself via BackdropBlurTransformation
        // (a Coil Transformation, computed once per track off the main thread on a tiny bitmap),
        // NOT a live Modifier.blur() RenderEffect. The warp shader below keeps the background
        // visibly moving/warping every frame — that part is intentionally kept — but since it no
        // longer has a live Gaussian blur chained beneath it, the only per-frame GPU cost left is
        // the shader's own cheap coordinate distortion, not a full blur re-evaluation on top of it.
        // The artwork gate stays exactly as it was for the default backdrop; the two artwork-free
        // styles (flat fill, static gradient) additionally have nothing to wait for, so they draw
        // even on a track with no thumbnail.
        if (trackInfo.thumbnailUrl != null || backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {
            val showBackdrop by remember { derivedStateOf { progressProvider() > 0.02f } }
            if (showBackdrop) {
                val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                Box(
                    modifier = Modifier
                        .morphLayout(
                            progressProvider = progressProvider,
                            horizontalOffsetProvider = { 0f },
                            endpointsPx = endpointsPx,
                            element = MorphElement.BACKDROP,
                        )
                        .graphicsLayer {
                            // Exact complement of the pill's own alpha above — see the comment
                            // there for why this is a direct function of progress rather than a
                            // fixed-duration animation.
                            alpha = (progressProvider() / PILL_FADE_END).coerceIn(0f, 1f)
                            // Clip to this box's own (morphing) bounds — the child below is a
                            // FIXED full-player size and gets cropped down to whatever window
                            // is currently revealed, rather than being re-measured/re-blurred
                            // as that window grows.
                            clip = true
                        }
                        // Solid backing color painted first — if the blurred image's edges
                        // ever sample past their own bounds (blur naturally fades toward
                        // transparent right at the source edge), this is what shows through
                        // instead of whatever's actually behind the player.
                        .background(containerColor)
                ) {
                    // Fixed absolute size (always the full player's own maxWidth/maxHeight, never
                    // the parent's currently-morphing size) — requiredSize is what actually makes
                    // this stick: the parent Box above measures this content with
                    // Constraints.fixed(w, h) via morphLayout (that's the growing morph itself,
                    // and stays untouched), and a plain .size()/.fillMaxSize() would just get
                    // clamped back down to those incoming constraints. requiredSize explicitly
                    // overrides them, so this offscreen/shader layer is allocated once at a
                    // constant size and only its *contents* get redrawn every frame (cheap) —
                    // the parent's own clip = true is what reveals more or less of it as the drag
                    // progresses, not a resize of this content. Before this, the whole
                    // scale+RuntimeShader Offscreen layer was being resized (and its GPU backing
                    // buffer reallocated) on every single frame of the drag, in both directions —
                    // that was the remaining swipe-up/swipe-down lag.
                    // When the YouTube video background is on and resolved, the ambient backdrop
                    // becomes a blurred, mirrored copy of the video's own live frames instead of
                    // the usual blurred-cover-art + warp shader — see VideoBackdropBlur below.
                    if (backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {
                        // The three added backdrops. Deliberately a sibling branch rather than a
                        // rewrite of the block below: COVER_GRADIENT is the default and still
                        // runs through the original code, untouched.
                        PlayerBackgroundContent(
                            style = backgroundStyle,
                            thumbnailUrl = trackInfo.thumbnailUrl,
                            pureBlack = pureBlack,
                            modifier = Modifier.requiredSize(maxWidth, maxHeight)
                        )
                    } else if (playVideoBackground && videoInfo != null) {
                        VideoBackdropBlur(
                            glassState = videoGlassState,
                            modifier = Modifier.requiredSize(maxWidth, maxHeight)
                        )
                    } else {
                        // Shared with the customization page's preview so the two can never
                        // disagree — see CoverGradientBackdrop.kt. The shader instance and the
                        // clock stay hoisted here, for the AGSL-compile and idle-wakeup reasons
                        // documented above.
                        CoverGradientBackdrop(
                            thumbnailUrl = trackInfo.thumbnailUrl,
                            width = maxWidth,
                            height = maxHeight,
                            animate = warpClockActive,
                            shader = warpShader,
                            timeProvider = { warpTimeState.floatValue },
                        )
                    }
                }
            }
        }

        // Cover art
        // A disc still renders (black platter, empty label) with no artwork, so it isn't gated
        // on the thumbnail the way the plain image styles have to be.
        if (trackInfo.thumbnailUrl != null || isDiscStyle) {
            Box(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = horizontalOffsetProvider,
                        endpointsPx = endpointsPx,
                        element = MorphElement.ART,
                        lyricsProgressProvider = lyricsProgressProvider,
                    )
                    .graphicsLayer {
                        val p = progressProvider()
                        // A disc style's platter is deliberately larger than this box and hangs
                        // off its edges (concept screens 84/87), so clipping is the one thing
                        // that must not happen while the platter is what's on screen. Once it has
                        // dissolved into the square — collapsing to the pill, or opening the
                        // lyrics page — clipping has to come back or the square's rounded corners
                        // stop being rounded. Every non-disc style clips exactly as before.
                        clip = !isDiscStyle ||
                            discWeight(progressProvider, lyricsProgressProvider) < 0.5f
                        // Radius only ever accounted for the pill<->fullscreen stage (ending at a
                        // flat 0.dp once fully expanded), with no second stage for the lyrics
                        // shrink — so the small header cover was rendering perfectly square.
                        // lyricsProgress interpolates it back up to LyricsHeaderCornerRadius.
                        val expandedRadius = if (coverStyle == PlayerCoverStyle.SQUARED) {
                            SquaredCoverCornerRadius
                        } else {
                            0.dp
                        }
                        val base = lerp(ThumbnailCornerRadius, expandedRadius, p)
                        shape = RoundedCornerShape(lerp(base, LyricsHeaderCornerRadius, lyricsProgressProvider()))
                    }
                    .then(
                        // Only mounted once the player is genuinely open. Left permanently
                        // installed, this detector would consume the pointer-down on the mini
                        // pill's 48dp thumbnail, and the pill's own tap-to-expand — which uses
                        // the default requireUnconsumed = true — would stop firing there.
                        if (longPressEnabled && onLongPressCover != null) {
                            Modifier.pointerInput(onLongPressCover) {
                                detectTapGestures(onLongPress = { onLongPressCover() })
                            }
                        } else Modifier
                    )
                    .then(
                        if (onArtBoundsChanged != null) {
                            Modifier.onGloballyPositioned { onArtBoundsChanged(it.boundsInRoot()) }
                        } else Modifier
                    )
            ) {
                // The plain square artwork is the base layer for EVERY style, disc ones
                // included. A disc has no sensible 48dp form and none at all in the lyrics
                // header, so rather than shrinking a platter into the pill the disc dissolves
                // and reveals this square underneath — the pill and the lyrics header keep the
                // artwork they have always shown, and the transition is a morph rather than a
                // swap because both layers occupy the identical, already-morphing box.
                //
                // The two are an exact crossfade: a platter is a circle inside a square box, so
                // leaving this layer at full strength underneath one left the square artwork
                // showing in the corners around the disc — the same image visible twice at once.
                // The layer is only attached for disc styles, so every other style reaches this
                // artwork through precisely the modifier chain it always did.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isDiscStyle) {
                                Modifier.graphicsLayer {
                                    alpha = 1f - discWeight(progressProvider, lyricsProgressProvider)
                                }
                            } else Modifier
                        )
                ) {
                // crossfade: when trackInfo.thumbnailUrl changes (track change / manual skip),
                // Coil keeps showing the previous image and fades to the new one once it's
                // decoded, instead of clearing to blank while the new image loads. Combined
                // with the queue-ahead prefetch below, the next track's image is usually
                // already cached by the time this swaps, so the fade is effectively instant;
                // when it isn't cached (e.g. a manual skip before prefetch finished), this is
                // what keeps the old cover "held" until the new one is ready.
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(trackInfo.thumbnailUrl?.resize(1200, 1200))
                        .allowHardware(true)
                        .crossfade(300)
                        // Pin the decode size instead of letting Coil infer it from the layout
                        // node. This composable lives inside morphLayout(MorphElement.ART),
                        // which measures it with Constraints.fixed(miniArtSizePx) while the
                        // player is collapsed in the pill. Coil resolves its size once, so it
                        // was decoding a ~150px bitmap and then keeping it as the sheet grew —
                        // the full-screen cover was a mini-pill thumbnail scaled up ~7x.
                        // The URL already asks the CDN for 1200px; this makes the decode match.
                        .size(CoilSize(1200, 1200))
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Keyed by mediaId so switching tracks resets this to null immediately (same
                // recomposition as the track change, before the LaunchedEffect below even
                // runs) instead of holding onto the previous track's animated cover/state
                // while the new one is still being looked up.
                var canvasArtwork by remember(trackInfo.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                val canvasEnabled by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
                val canvasWifiOnly by rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
                LaunchedEffect(trackInfo.mediaId, trackInfo.title, trackInfo.artist, canvasEnabled, canvasWifiOnly) {
                    val mediaId = trackInfo.mediaId
                    val titleStr = trackInfo.title
                    val artistStr = trackInfo.artist
                    if (!canvasEnabled || mediaId.isBlank() || titleStr.isBlank() || artistStr.isBlank()) return@LaunchedEffect

                    val connectivityManager = context.getSystemService<android.net.ConnectivityManager>()
                    if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) return@LaunchedEffect

                    val cached = CanvasArtworkPlaybackCache.get(mediaId)
                    if (cached != null) {
                        canvasArtwork = cached
                        return@LaunchedEffect
                    }

                    val normTitle = normalizeCanvasSongTitle(titleStr)
                    val normArtist = normalizeCanvasArtistName(artistStr)
                    if (normTitle.length < 2 || normArtist.length < 2) return@LaunchedEffect

                    val res = withContext(Dispatchers.IO) {
                        MonochromeApiCanvas.getBySongArtist(normTitle, normArtist, null)
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                        ?: AppleMusicCanvasProvider.getBySongArtist(normTitle, normArtist, null, "us")
                            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    }
                    if (res != null) {
                        CanvasArtworkPlaybackCache.put(mediaId, res)
                        canvasArtwork = res
                    }
                }

                // Static cover shows for the whole drag; only once nearly fully expanded does
                // it crossfade to the animated canvas cover, and it fades back to static as
                // soon as collapsing starts — a fixed-duration crossfade, not tied directly to
                // drag progress, same reasoning as the pill blur above. Also gated live on
                // canvasEnabled so flipping the setting off hides an already-fetched clip
                // immediately instead of waiting for the next track change.
                val nearFullyExpanded by remember(canvasArtwork, canvasEnabled) {
                    derivedStateOf { canvasEnabled && canvasArtwork?.preferredAnimationUrl != null && progressProvider() > 0.92f }
                }
                AnimatedVisibility(
                    visible = !isDiscStyle && nearFullyExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                        fallbackUrl = null,
                        // Loops even while the music is paused (unlike the YouTube video
                        // background below, which pauses with playback) — but not while the editor
                        // is up, where it would just be decoding video frames to feed a
                        // full-screen blur. Paused rather than unmounted, so returning from the
                        // editor does not re-create the player and re-buffer the clip.
                        isPlaying = !editMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Optional YouTube official-music-video background (real ExoPlayer video, not a
                // lyrics video / user upload — see YouTubeVideoLookup.kt's Videos-tab search +
                // MUSIC_VIDEO_TYPE_OMV filter). Resolution + lyrics-sync anchors are computed once,
                // hoisted above (shared with the fullscreen backdrop's blurred/mirrored copy).
                // Shown here sharp and uncropped-ugly-free via the same crop-to-fill as the static
                // cover; YouTubeVideoBackground itself only fades in once ExoPlayer actually
                // renders a first frame, so there's no blank gap — it just looks like the static
                // cover until the video is ready.
                if (!isDiscStyle && playVideoBackground && videoInfo != null) {
                    YouTubeVideoBackground(
                        streamUrl = videoInfo?.streamUrl,
                        isPlaying = isPlaying,
                        positionMsProvider = { playerConnection?.player?.currentPosition ?: 0L },
                        lyricVideoAnchors = lyricVideoAnchors,
                        glassState = videoGlassState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                }

                if (isDiscStyle) {
                    DiscCoverStack(
                        style = coverStyle,
                        artworkUrl = trackInfo.thumbnailUrl,
                        mediaId = trackInfo.mediaId,
                        queueIndex = queueState.currentIndex,
                        isPlaying = isPlaying,
                        spinActive = discSpinActive,
                        editMode = editMode,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = discWeight(progressProvider, lyricsProgressProvider) },
                    )
                }
            }
        }

        // Title + artist/album — visible in the pill, morphing and fading on the same curve as
        // the controls below so it never overlaps SongInfoRow's copy in the expanded player.
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = horizontalOffsetProvider,
                    endpointsPx = endpointsPx,
                    element = MorphElement.TEXT,
                )
                .graphicsLayer {
                    alpha = (1f - (progressProvider() / 0.5f)).coerceIn(0f, 1f)
                    // Offscreen so the DstIn fade below composites against this layer's own
                    // pixels rather than punching a hole through the pill behind it.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    // Right edge ramps to zero opacity, so an over-long title dissolves instead
                    // of hard-clipping or showing an ellipsis.
                    val fade = Brush.horizontalGradient(
                        0f to Color.Black,
                        0.82f to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                }
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = trackInfo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    // No ellipsis and no wrapping: the gradient above is what terminates the
                    // line, so the glyphs must be allowed to run to the edge and be faded.
                    softWrap = false,
                )
                val subtitle = listOf(trackInfo.artist, trackInfo.album)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // Play/Pause — visible in the pill, fades out as the fullscreen controls fade in
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.PLAY,
                )
                .requiredSize(miniPlaySize)
                .graphicsLayer {
                    val p = progressProvider()
                    val playScale = 1f + (p * 1.0f)
                    scaleX = playScale
                    scaleY = playScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = (1f - (p / 0.5f)).coerceIn(0f, 1f)
                }
                .clickable { playerConnection?.togglePlayPause() }
        ) {
            Icon(
                painter = painterResource(
                    if (playbackState == Player.STATE_ENDED) R.drawable.replay
                    else if (isPlaying) R.drawable.ic_untitled_pause
                    else R.drawable.ic_untitled_play
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp).align(Alignment.Center)
            )
        }

        // Skip next — same fade behavior as Play
        Box(
            modifier = Modifier
                .morphLayout(
                    progressProvider = progressProvider,
                    horizontalOffsetProvider = { 0f },
                    endpointsPx = endpointsPx,
                    element = MorphElement.SKIP,
                )
                .requiredSize(miniPlaySize)
                .graphicsLayer {
                    val p = progressProvider()
                    val playScale = 1f + (p * 1.0f)
                    scaleX = playScale
                    scaleY = playScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = (1f - (p / 0.5f)).coerceIn(0f, 1f)
                }
                .clickable { playerConnection?.player?.seekToNext() }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_untitled_skip_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp).align(Alignment.Center)
            )
        }
    }
}

/**
 * The fullscreen ambient backdrop's video-based alternative to the usual cover-based
 * BackdropBlurTransformation + warp shader: a heavily blurred copy of the cover card's own live
 * video frames (captured via [glassState] — the same GlassKit RenderNode mechanism used
 * everywhere else in this app, reading whatever YouTubeVideoBackground's glassRoot wrap actually
 * rendered, not a second decode), plus a second vertically-mirrored copy so an aspect-mismatched
 * video reads as one continuous backdrop instead of a hard crop edge. Draws nothing (letting the
 * dark backdrop base behind it show through) whenever there's no captured frame yet, or Disable
 * Blur is on — that's the "precaution and transition" fallback.
 */
@Composable
private fun VideoBackdropBlur(
    glassState: GlassState,
    modifier: Modifier = Modifier,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    if (disableBlur) return

    Box(modifier = modifier) {
        // Normal copy, scaled to fully cover this box (crop, not letterbox) — drawRenderNode
        // paints at the node's own native captured size with no auto-scaling, so without this
        // the captured (cover-card-sized) content would sit as a small, misplaced blob inside
        // the much larger fullscreen box instead of filling it.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        90f, 90f, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
        ) {
            drawFillScaledVideoNode(glassState, flip = false)
        }

        // "Flip it down" — the same content, vertically mirrored but scaled/positioned to cover
        // this exact same box (not the whole node re-anchored by an unrelated center pivot),
        // so the two copies land in register instead of drifting apart.
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.6f
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        90f, 90f, android.graphics.Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
        ) {
            drawFillScaledVideoNode(glassState, flip = true)
        }
    }
}

/**
 * Draws the captured video RenderNode scaled (crop-to-fill, like ContentScale.Crop) and
 * centered to exactly cover this draw scope's own size. [flip] mirrors it vertically in place —
 * same footprint as the unflipped copy, not offset by an unrelated center pivot — so both copies
 * land in register with each other instead of drifting apart.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFillScaledVideoNode(
    glassState: GlassState,
    flip: Boolean,
) {
    val node = glassState.renderNode
    if (node == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || !node.hasDisplayList()) return
    val nodeWidth = node.width.toFloat()
    val nodeHeight = node.height.toFloat()
    if (nodeWidth <= 0f || nodeHeight <= 0f) return

    val fillScale = maxOf(size.width / nodeWidth, size.height / nodeHeight)
    val scaledWidth = nodeWidth * fillScale
    val scaledHeight = nodeHeight * fillScale
    val offsetX = (size.width - scaledWidth) / 2f
    val offsetY = (size.height - scaledHeight) / 2f

    withTransform({
        translate(left = offsetX, top = if (flip) offsetY + scaledHeight else offsetY)
        scale(scaleX = fillScale, scaleY = if (flip) -fillScale else fillScale, pivot = Offset.Zero)
    }) {
        drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
    }
}

@androidx.annotation.RequiresApi(33)
private fun Modifier.liquidWarpEffect(
    shader: android.graphics.RuntimeShader,
    time: () -> Float,
): Modifier = this.graphicsLayer {
    // Both reads happen here, in the draw phase — this is what keeps the warp's continuous
    // per-frame updates from ever recomposing the composable tree above it.
    shader.setFloatUniform("resolution", size.width, size.height)
    shader.setFloatUniform("time", time())
    renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
    clip = true
}
