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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
import com.example.musicfy.constants.YtVideoBackgroundLyricsSyncKey
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.glassRoot
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
    val fullWidth: Dp,
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
    val fullWidthPx: Float,
    val fullArtHeightPx: Float,
    val fullArtXPx: Float,
    val fullArtYPx: Float,
    val fullPlayXPx: Float,
    val fullPlayYPx: Float,
    val miniHeightPx: Float,
    val fullHeightPx: Float,
)

private enum class MorphElement { ART, PLAY, SKIP, BACKDROP }

// The progress value at which the full-player backdrop reaches full opacity and the pill's own
// blurred background reaches zero. Both the backdrop's and the pill's alpha are computed as
// exact complements of this same constant (see MorphingCover below), so at every progress value
// their combined coverage is ~1 — no gap, regardless of drag speed/direction.
private const val PILL_FADE_END = 0.15f

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

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
) = this.layout { measurable, constraints ->
    val p = progressProvider()
    val hOffset = horizontalOffsetProvider()

    val (x, y, w, h) = when (element) {
        MorphElement.ART -> {
            val artW = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullWidthPx, p)
            val artH = lerpF(endpointsPx.miniArtSizePx, endpointsPx.fullArtHeightPx, p)
            val artX = lerpF(endpointsPx.miniArtXPx, endpointsPx.fullArtXPx, p) + hOffset
            val artY = lerpF(endpointsPx.miniArtYPx, endpointsPx.fullArtYPx, p)
            floatArrayOf(artX, artY, artW, artH)
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
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val density = LocalDensity.current

    // Prefetch the next queue item's artwork as soon as the current track is known, so by the
    // time the song actually ends — or the user taps skip — the image is already in Coil's
    // cache and the crossfade above is effectively instant instead of loading over the network
    // while the old cover is held on screen.
    if (playerConnection != null) {
        val queueState by playerConnection.uiState.queueState.collectAsState()
        LaunchedEffect(queueState.currentIndex, queueState.items) {
            val nextUrl = queueState.items.getOrNull(queueState.currentIndex + 1)
                ?.artworkUri?.toString()?.resize(1200, 1200)
                ?: return@LaunchedEffect
            val request = ImageRequest.Builder(context).data(nextUrl).build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    val endpoints = remember(maxWidth, maxHeight) {
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

        MorphEndpoints(
            miniArtSize = miniArtSize,
            miniArtX = miniArtX,
            miniArtY = miniArtY,
            miniPlayX = miniPlayX,
            miniPlayY = miniPlayY,
            miniSkipX = miniSkipX,
            miniSkipY = miniSkipY,
            fullWidth = maxWidth,
            fullArtHeight = maxHeight * 0.63f,
            fullArtX = 0.dp,
            fullArtY = 0.dp,
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
                fullWidthPx = endpoints.fullWidth.toPx(),
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
    val warpClockActive by remember { derivedStateOf { progressProvider() > 0.02f } }
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
    Box(modifier = modifier.glassRoot(glassState, isActive = { progressProvider() > 0.80f })) {
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
                        alpha = (1f - (progressProvider() / PILL_FADE_END)).coerceIn(0f, 1f)
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
        if (trackInfo.thumbnailUrl != null) {
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
                    if (playVideoBackground && videoInfo != null) {
                        VideoBackdropBlur(
                            glassState = videoGlassState,
                            modifier = Modifier.requiredSize(maxWidth, maxHeight)
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(trackInfo.thumbnailUrl?.resize(48, 48))
                                .allowHardware(false)
                                .transformations(BackdropBlurTransformation(radiusPx = 4))
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(maxWidth, maxHeight)
                                .graphicsLayer {
                                    scaleX = 1.6f
                                    scaleY = 1.6f
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                }
                                .let {
                                    if (warpShader != null) {
                                        it.liquidWarpEffect(warpShader) { warpTimeState.floatValue }
                                    } else {
                                        it
                                    }
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .requiredSize(maxWidth, maxHeight)
                            .background(Color.Black.copy(alpha = 0.22f))
                    )
                }
            }
        }

        // Cover art
        if (trackInfo.thumbnailUrl != null) {
            Box(
                modifier = Modifier
                    .morphLayout(
                        progressProvider = progressProvider,
                        horizontalOffsetProvider = horizontalOffsetProvider,
                        endpointsPx = endpointsPx,
                        element = MorphElement.ART,
                    )
                    .graphicsLayer {
                        val p = progressProvider()
                        clip = true
                        shape = RoundedCornerShape(lerp(ThumbnailCornerRadius, 0.dp, p))
                    }
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
                    visible = nearFullyExpanded,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    CanvasArtworkPlayer(
                        primaryUrl = canvasArtwork?.preferredAnimationUrl,
                        fallbackUrl = null,
                        // Always true: the animated cover keeps looping even when the music is
                        // paused (unlike the YouTube video background below, which still pauses
                        // with playback).
                        isPlaying = true,
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
                if (playVideoBackground && videoInfo != null) {
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
