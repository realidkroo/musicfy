// OnboardingHero.kt
// The hero shown in place of HeroCarousel when there is nothing to carousel yet — a brand-new
// install with no last-played song, no Daily Discover and no listening history. Same cover-grid
// concept as the setup wizard's welcome screen, but permanently looping, tilted, and blurred.

package com.example.musicfy.ui.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.example.musicfy.R
import com.example.musicfy.ui.utils.resize
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TILT_DEGREES = 40f

/**
 * @param username shown in the greeting; blank is fine, the greeting just drops the name.
 * @param onGetStarted invoked by the "here" pill — wired to the search tab.
 */
@Composable
fun OnboardingHero(
    username: String,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var covers by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        covers = withContext(Dispatchers.IO) {
            listOf("top hits", "classic rock", "j-pop")
                .flatMap { query ->
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        ?.map { it.thumbnail.resize(544, 544) }
                        .orEmpty()
                }
                .distinct()
                .shuffled()
        }
    }

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        TiltedCoverWall(
            covers = covers,
            modifier = Modifier.fillMaxSize()
        )

        // Scrim so the copy stays readable over whatever art loads.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.45f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Black
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
        ) {
            Text(
                text = "Welcome",
                fontSize = 48.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-2).sp
            )
            Text(
                text = if (username.isBlank()) "to musicfy!" else "to musicfy, $username!",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-1).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Lets start by finding your fav music here!\nor importing media on library.",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB3B3B3)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1C))
                    .clickable(onClick = onGetStarted)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "here",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Cover art that fades up once it has actually decoded, instead of snapping in over the empty
 * placeholder tile the moment the network returns.
 */
@Composable
fun FadeInCover(
    url: String,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "cover_fade"
    )

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onState = { state -> if (state is AsyncImagePainter.State.Success) loaded = true },
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    )
}

/**
 * Three columns of cover art, tilted and drifting upward forever. The blur radius breathes rather
 * than sitting still so the wall reads as alive even while the drift is slow.
 */
@Composable
private fun TiltedCoverWall(
    covers: List<String>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "cover_wall")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val blurPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blur_pulse"
    )

    val tileSize = 150.dp
    val spacing = 12.dp
    val density = LocalDensity.current
    val stridePx = with(density) { (tileSize + spacing).toPx() }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = TILT_DEGREES
                // Overscale so the rotated wall still covers the corners of the hero.
                scaleX = 2.1f
                scaleY = 2.1f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radius = 14f + blurPulse * 10f
                    renderEffect = RenderEffect
                        .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (covers.isEmpty()) return@Box

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            repeat(3) { column ->
                // Each column is offset a little so the wall doesn't read as one rigid block, and
                // the loop is seamless because the strip repeats its own contents twice.
                val strip = remember(covers, column) {
                    val rotated = covers.drop(column * 2) + covers.take(column * 2)
                    (rotated + rotated).take(12)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val phase = (drift + column * 0.17f) % 1f
                            translationY = -phase * stridePx * 6f
                        }
                ) {
                    strip.forEach { url ->
                        Box(
                            modifier = Modifier
                                .padding(bottom = spacing)
                                .fillMaxWidth()
                                .height(tileSize)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A1A))
                        ) {
                            FadeInCover(url = url, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
