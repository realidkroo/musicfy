// RecapStoryPages.kt
//
// What each recap page shows. Every page is a pure function of its PageAnim (enter/exit 0..1) and
// the shared StoryState; nothing here starts an animation of its own.

package com.example.musicfy.ui.screens.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.resize
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Shared bits

internal fun recapBold(size: Float, lineHeight: Float = size * 1.1f, tracking: Float = -0.035f * size, weight: FontWeight = FontWeight.Bold) =
    TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        color = Color.White,
    )

private val MonoHint = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = Color.White)

private fun Modifier.at(x: Float, y: Float): Modifier = offset { IntOffset(x.roundToInt(), y.roundToInt()) }

/** Line [index] of [count]: rises in (or slides in from [enterFromDp]) and slides out to [exitToDp]. */
private fun Modifier.lineMotion(
    anim: PageAnim,
    index: Int,
    count: Int,
    enterFromDp: Float = 0f,
    exitToDp: Float = -110f,
    riseDp: Float = 26f,
    drag: () -> Float = { 0f },
): Modifier = graphicsLayer {
    val e = easeOutCubic(stagger(anim.enter.value, index, count))
    val x = easeInCubic(stagger(anim.exit.value, index, count, length = 0.7f))
    alpha = e * (1f - x)
    translationX = (1f - e) * enterFromDp.dp.toPx() + x * exitToDp.dp.toPx() + drag()
    translationY = (1f - e) * riseDp.dp.toPx()
}

/** A page group that slides away to the left, fading as it goes, as [exit] rises. */
private fun Modifier.slideAway(exit: () -> Float): Modifier = graphicsLayer {
    val x = exit()
    if (x <= 0f) return@graphicsLayer
    translationX = -easeInCubic(x) * size.width * 0.6f
    alpha = 1f - easeInCubic(((x - 0.1f) / 0.9f).coerceIn(0f, 1f))
}

/** A page group that zooms out, blurs and fades as [exit] rises. */
private fun Modifier.zoomBlurOut(exit: () -> Float): Modifier = graphicsLayer {
    val x = exit()
    if (x <= 0f) return@graphicsLayer
    val e = easeInOutCubic(x)
    scaleX = 1f - 0.2f * e
    scaleY = 1f - 0.2f * e
    alpha = 1f - e
    renderEffect = BlurEffectCache.get(e * 70f)
}

@Composable
private fun visible(vararg anims: PageAnim): Boolean {
    val state = remember(*anims) { derivedStateOf { anims.any { it.enter.value > 0f && it.exit.value < 1f } } }
    return state.value
}

@Composable
private fun Picture(url: String?, size: Int, modifier: Modifier, shape: Shape) {
    AsyncImage(
        model = url?.resize(size, size),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(Color(0x33FFFFFF)),
        error = ColorPainter(Color(0x33FFFFFF)),
        modifier = modifier
            .clip(shape)
            .background(Color(0x22FFFFFF)),
    )
}

// ---------------------------------------------------------------------------------------------

@Composable
internal fun StoryPages(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val p = s.pages
    Box(modifier = Modifier.fillMaxSize()) {
        if (visible(p[P_HEY])) HeyPage(s, data)
        if (visible(p[P_JOURNEY])) JourneyPage(s, data, time)
        if (visible(p[P_MONTH])) MonthPage(s, data, time)
        if (visible(p[P_WEEKDAY])) WeekdayPage(s, data, time)
        if (data.heroArtist != null && visible(p[P_ARTISTS], p[P_ARTIST])) ArtistSequence(s, data, time)
        if (visible(p[P_SONGS], p[P_MELODY], p[P_SONG])) SongSequence(s, data, time)
        if (visible(p[P_RECEIPT])) ReceiptPage(s, data)
        if (visible(p[P_END])) EndPage(s, data, time)
    }
}

@Composable
private fun HeyPage(s: StoryState, data: RecapStoryData) {
    val geo = s.geo
    val anim = s.pages[P_HEY]
    val av = geo.avatar(P_HEY)
    Text(
        text = "hey, ${data.name}!",
        style = recapBold(26f),
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, av[1] + av[2] / 2f + geo.dp(34f))
            .padding(horizontal = 36.dp)
            .graphicsLayer {
                val e = easeOutCubic(anim.enter.value)
                val x = easeInCubic(anim.exit.value)
                alpha = e * (1f - x)
                translationY = (1f - e) * 22.dp.toPx() - x * 36.dp.toPx()
            },
    )
}

@Composable
private fun JourneyPage(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val anim = s.pages[P_JOURNEY]
    val av = geo.avatar(P_JOURNEY)
    val drag = { s.dragX }
    val n = 5
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, av[1] + av[2] / 2f + geo.dp(44f))
            .padding(horizontal = 36.dp),
    ) {
        Text("${data.name} you started your journey in Musicfy on", style = recapBold(22f), modifier = Modifier.lineMotion(anim, 0, n, drag = drag))
        Spacer(Modifier.height(4.dp))
        OdometerText(
            text = DateLong.format(data.joinedDate),
            style = recapBold(44f),
            progress = { stagger(anim.enter.value, 1, n) },
            modifier = Modifier.lineMotion(anim, 1, n, drag = drag),
        )
        Spacer(Modifier.height(20.dp))
        Text("and you have been listening to music in total time of", style = recapBold(22f), modifier = Modifier.lineMotion(anim, 2, n, drag = drag))
        Spacer(Modifier.height(4.dp))
        OdometerText(
            text = formatHoursMinutes(data.allTimeMs),
            style = recapBold(44f),
            progress = { stagger(anim.enter.value, 3, n) },
            modifier = Modifier.lineMotion(anim, 3, n, drag = drag),
        )
        Spacer(Modifier.height(22.dp))
        Text("so long, partners!", style = recapBold(22f), modifier = Modifier.lineMotion(anim, 4, n, drag = drag))
    }
    val hintY = geo.height - geo.dp(144f)
    Text(
        "TAP!",
        style = MonoHint,
        modifier = Modifier
            .at(geo.dp(52f), hintY - geo.dp(13f))
            .graphicsLayer {
                val e = ((anim.enter.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                alpha = e * (1f - anim.exit.value)
            },
    )
}

@Composable
private fun MonthPage(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val anim = s.pages[P_MONTH]
    val density = LocalDensity.current
    val drag = { s.dragX }
    val n = 4
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { geo.fy(702f).toDp() })
            .padding(horizontal = 30.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Text(
                "${data.monthName} is",
                style = recapBold(44f),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp).lineMotion(anim, 0, n, enterFromDp = 70f, drag = drag),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                OdometerText(
                    text = "${data.monthProgress}%",
                    style = recapBold(104f, lineHeight = 112f, tracking = -4f, weight = FontWeight.ExtraBold),
                    progress = { stagger(anim.enter.value, 1, n) },
                    modifier = Modifier.lineMotion(anim, 1, n, enterFromDp = 70f, drag = drag),
                )
            }
            Text("out of 100%", style = recapBold(15f), modifier = Modifier.padding(start = 6.dp).lineMotion(anim, 2, n, drag = drag))
            Spacer(Modifier.height(12.dp))
            MonthBar(
                fraction = data.monthProgress / 100f,
                progress = { easeInOutCubic(((anim.enter.value - 0.4f) / 0.6f).coerceIn(0f, 1f)) },
                time = time,
                modifier = Modifier.lineMotion(anim, 3, n, drag = drag),
            )
        }
    }
}

/** The month bar: a white track and a green fill that grows in, with a sheen that never stops moving. */
@Composable
private fun MonthBar(fraction: Float, progress: () -> Float, time: RecapTime, modifier: Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .drawWithCache {
                val r = CornerRadius(size.height / 2f)
                val fill = Brush.horizontalGradient(listOf(Color(0xFF46E68F), Color(0xFF88FF76), Color(0xFFD4FF73)))
                onDrawBehind {
                    drawRoundRect(Color.White, cornerRadius = r)
                    val f = fraction * progress()
                    if (f <= 0.001f) return@onDrawBehind
                    val w = maxOf(size.height, size.width * f)
                    val bar = Size(w, size.height)
                    drawRoundRect(fill, size = bar, cornerRadius = r)
                    val sweep = (((time.seconds * 0.55) % 1.5) - 0.25).toFloat() * w
                    drawRoundRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            start = Offset(sweep - size.height * 1.6f, 0f),
                            end = Offset(sweep + size.height * 0.4f, size.height),
                        ),
                        size = bar,
                        cornerRadius = r,
                    )
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = size.height * 0.6f),
                        size = bar,
                        cornerRadius = r,
                    )
                    // a soft glow at the leading edge while it's still growing
                    val growing = 1f - progress()
                    if (growing > 0.01f) {
                        drawCircle(Color.White, radius = size.height * 0.32f, center = Offset(w - size.height / 2f, size.height / 2f), alpha = 0.5f * growing)
                    }
                }
            },
    )
}

@Composable
private fun WeekdayPage(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val anim = s.pages[P_WEEKDAY]
    val av = geo.avatar(P_WEEKDAY)
    val drag = { s.dragX }
    val n = 5
    val day = DayOfWeek.of(data.favouriteDay + 1).displayName()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, av[1] + av[2] / 2f + geo.dp(22f))
            .padding(horizontal = 36.dp),
    ) {
        Text("and at this time,", style = recapBold(34f), modifier = Modifier.lineMotion(anim, 0, n, drag = drag))
        BasicText(
            text = day.uppercase(),
            style = recapBold(62f, lineHeight = 66f, tracking = -2.4f, weight = FontWeight.ExtraBold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 62.sp),
            modifier = Modifier.fillMaxWidth().lineMotion(anim, 1, n, drag = drag),
        )
        Text("is your favourite day to listen to music", style = recapBold(22f), modifier = Modifier.lineMotion(anim, 2, n, drag = drag))
    }
    val hoursMs = data.favouriteDayMs
    val amount = if (hoursMs >= 3_600_000L) "${hoursMs / 3_600_000L}" else "${(hoursMs / 60_000L).coerceAtLeast(1L)}"
    val unit = if (hoursMs >= 3_600_000L) if (hoursMs / 3_600_000L == 1L) " hour" else " hours" else " minutes"
    val more = if (data.runnerUpMs > 0L) ((data.favouriteDayMs - data.runnerUpMs) * 100f / data.runnerUpMs).roundToInt() else -1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, geo.fy(546f))
            .padding(horizontal = 36.dp),
    ) {
        val big = recapBold(36f)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.lineMotion(anim, 3, n, drag = drag)) {
            OdometerText(amount, big, progress = { stagger(anim.enter.value, 3, n) })
            Text(if (more >= 0) "$unit," else unit, style = big)
            if (more >= 0) {
                Spacer(Modifier.width(10.dp))
                OdometerText("$more%", big, progress = { stagger(anim.enter.value, 4, n) })
            }
        }
        Text(
            if (more >= 0) "more than your 2nd!" else "nothing else comes close!",
            style = big,
            modifier = Modifier.lineMotion(anim, 4, n, drag = drag),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Artists: the slot machine, then the reveal

@Composable
private fun ArtistSequence(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val spinAnim = s.pages[P_ARTISTS]
    val reveal = s.pages[P_ARTIST]
    val artists = data.artists
    val hero = data.heroArtist ?: return
    val n = artists.size.coerceAtLeast(1)
    val heroIndex = 3 * n
    val d = geo.dp(229f)
    val pitch = geo.dp(254f)
    val colCx = geo.width / 2f + d / 2f
    val heroEndD = geo.dp(132f)
    val heroEndCx = geo.dp(40f) + heroEndD / 2f
    val heroEndCy = geo.fy(381f) + heroEndD / 2f
    val density = LocalDensity.current
    val discDp = with(density) { d.toDp() }

    fun heroCenter(): FloatArray {
        val m = s.heroArtist.value
        val startCy = geo.height / 2f + (heroIndex - s.spin.value) * pitch
        return floatArrayOf(lerpF(colCx, heroEndCx, m), lerpF(startCy, heroEndCy, m), lerpF(d, heroEndD, m))
    }

    // the artist page leaves sliding left, every piece of it; the covers come in from the right
    Box(modifier = Modifier.fillMaxSize().slideAway { reveal.exit.value }) {
        if (visible(spinAnim)) {
            Text(
                "Again\nand\nagain",
                style = recapBold(48f, lineHeight = 46f),
                modifier = Modifier.at(geo.inset, geo.top + geo.dp(118f)).lineMotion(spinAnim, 0, 2, exitToDp = -140f),
            )
            Text(
                "this\nartist..",
                style = recapBold(62f, lineHeight = 58f),
                modifier = Modifier.at(geo.inset, geo.fy(578f)).lineMotion(spinAnim, 1, 2, exitToDp = -140f),
            )
            // the column, endlessly climbing; the hero slot is drawn on its own below
            val first by remember { derivedStateOf { floor(s.spin.value).toInt() - 3 } }
            for (j in first..first + 6) {
                if (j == heroIndex) continue
                key(j) {
                    val artist = artists[Math.floorMod(j, n)]
                    Picture(
                        url = artist.thumbnailUrl,
                        size = 544,
                        shape = CircleShape,
                        modifier = Modifier
                            .requiredSize(discDp)
                            .at(0f, 0f)
                            .graphicsLayer {
                                val e = easeOutCubic(spinAnim.enter.value)
                                val x = easeInCubic(spinAnim.exit.value)
                                translationX = colCx - d / 2f + (1f - e) * geo.dp(120f) + x * geo.width * 0.6f
                                translationY = geo.height / 2f + (j - s.spin.value) * pitch - d / 2f
                                alpha = e * (1f - x)
                            },
                    )
                }
            }
        }

        // the highlight: rings breaking out of the chosen circle
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val g = s.spinGlow.value
                    if (g <= 0f || g >= 1f || s.heroArtist.value > 0.6f) return@drawBehind
                    val c = heroCenter()
                    for (k in 0 until 3) {
                        val t = ((g - k * 0.16f) / 0.68f).coerceIn(0f, 1f)
                        if (t <= 0f || t >= 1f) continue
                        drawCircle(
                            Color.White,
                            radius = c[2] / 2f * (1f + 0.9f * easeOutCubic(t)),
                            center = Offset(c[0], c[1]),
                            style = Stroke(width = geo.dp(5f) * (1f - t) + 1f),
                            alpha = 0.7f * (1f - t),
                        )
                    }
                },
        )

        if (visible(reveal)) ArtistReveal(s, hero, reveal, heroEndCx, heroEndCy, heroEndD, time)

        // the hero itself
        Picture(
            url = hero.thumbnailUrl,
            size = 544,
            shape = CircleShape,
            modifier = Modifier
                .requiredSize(discDp)
                .at(0f, 0f)
                .graphicsLayer {
                    val c = heroCenter()
                    val pulse = 1f + 0.07f * sin(PI.toFloat() * s.spinGlow.value.coerceIn(0f, 0.5f) * 2f)
                    val k = c[2] / d * pulse
                    translationX = c[0] - d / 2f + (1f - easeOutCubic(spinAnim.enter.value)) * geo.dp(120f)
                    translationY = c[1] - d / 2f
                    scaleX = k
                    scaleY = k
                    alpha = easeOutCubic(spinAnim.enter.value)
                },
        )
    }
}

@Composable
private fun ArtistReveal(s: StoryState, hero: RecapArtist, anim: PageAnim, cx: Float, cy: Float, d: Float, time: RecapTime) {
    val geo = s.geo
    val drag = { s.dragX }
    val n = 6
    val density = LocalDensity.current
    val song = hero.topSong

    Column(modifier = Modifier.at(geo.inset, geo.top + geo.dp(92f)).padding(end = 36.dp)) {
        Text("Recognize\nthis artist?", style = recapBold(34f, lineHeight = 37f), modifier = Modifier.lineMotion(anim, 0, n, enterFromDp = 90f, drag = drag))
        Text(
            "that's your favorite this month!",
            style = recapBold(26f, lineHeight = 28f).copy(color = Color.White.copy(alpha = 0.72f)),
            modifier = Modifier.padding(top = 4.dp).lineMotion(anim, 1, n, enterFromDp = 90f, drag = drag),
        )
    }

    // the most played song slides out from behind the photo, tilted, with its label
    if (song != null) {
        val coverD = geo.dp(80f)
        val endCx = cx + geo.dp(96f)
        val endCy = cy - geo.dp(72f)
        Picture(
            url = song.thumbnailUrl,
            size = 240,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .requiredSize(with(density) { coverD.toDp() })
                .at(0f, 0f)
                .graphicsLayer {
                    val t = stagger(anim.enter.value, 2, n)
                    val e = easeOutCubic(t)
                    translationX = lerpF(cx, endCx, e) - coverD / 2f + drag()
                    translationY = lerpF(cy, endCy, e) - coverD / 2f
                    rotationZ = CoverTilt * e
                    val k = 0.85f + 0.15f * e
                    scaleX = k
                    scaleY = k
                    alpha = (t * 2.5f).coerceAtMost(1f)
                },
        )
        // the label sits off the cover's right edge, turned with it: the cover's own corner,
        // rotated the same way, is where it starts
        val turn = Math.toRadians(CoverTilt.toDouble())
        val ax = coverD / 2f + geo.dp(10f)
        val ay = -coverD / 2f + geo.dp(2f)
        val labelX = endCx + ax * cos(turn).toFloat() - ay * sin(turn).toFloat()
        val labelY = endCy + ax * sin(turn).toFloat() + ay * cos(turn).toFloat()
        Column(
            modifier = Modifier
                .at(labelX, labelY)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    rotationZ = CoverTilt
                    val e = easeOutCubic(stagger(anim.enter.value, 3, n))
                    alpha = e
                    // slides out along its own line, from the cover
                    val back = (1f - e) * geo.dp(24f)
                    translationX = -back * cos(turn).toFloat() + drag()
                    translationY = -back * sin(turn).toFloat()
                },
        ) {
            Text(song.title, style = recapBold(12f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
            Text("${song.plays} plays", style = recapBold(12f))
        }
    }

    val palette = remember(s.paletteTo) { s.paletteTo.textColors() }
    Column(modifier = Modifier.at(geo.inset, cy + d / 2f + geo.dp(10f)).padding(end = 36.dp)) {
        BasicText(
            text = hero.name,
            style = recapBold(64f, lineHeight = 66f, tracking = -2.6f),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 64.sp),
            modifier = Modifier
                .fillMaxWidth()
                .lineMotion(anim, 4, n, enterFromDp = 90f, drag = drag)
                .gradientToWhite(
                    colors = { palette },
                    whiteness = { ((anim.enter.value - 0.62f) / 0.38f).coerceIn(0f, 1f) },
                    time = time,
                ),
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.lineMotion(anim, 5, n, enterFromDp = 90f, drag = drag)) {
            OdometerText("${hero.plays}", recapBold(26f), progress = { stagger(anim.enter.value, 5, n) })
            Text("x Plays", style = recapBold(26f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Songs: the tilted grid, then "Hear the melody..", then the #1 song

private const val GridTilt = -30f

// the top song's cover on the artist page leans like the song grid that follows it
private const val CoverTilt = -30f
private const val GridCols = 2
private const val GridRows = 4

@Composable
private fun SongSequence(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val grid = s.pages[P_SONGS]
    val melody = s.pages[P_MELODY]
    val songPage = s.pages[P_SONG]
    val songs = data.songs
    val hero = data.heroSong ?: return
    val n = songs.size.coerceAtLeast(1)
    val density = LocalDensity.current
    val cover = geo.dp(163f)
    val colPitch = geo.dp(184f)
    val rowPitch = geo.dp(184f)
    val coverDp = with(density) { cover.toDp() }
    val driftPx = geo.dp(150f)
    val cx = geo.width / 2f
    val cy = geo.height / 2f
    val rad = Math.toRadians(GridTilt.toDouble())
    val cosT = cos(rad).toFloat()
    val sinT = sin(rad).toFloat()

    // grid space -> screen, about the screen centre
    fun columnEnter(c: Int): Float = easeOutCubic(stagger(grid.enter.value, c + GridCols, 2 * GridCols + 1, length = 0.7f))
    fun column(c: Int): Float {
        val dir = if (c % 2 == 0) -1f else 1f
        return dir * driftPx * (s.gridDrift.value - 1f) + (1f - columnEnter(c)) * dir * geo.height * 1.3f
    }
    // and the whole grid slides in from the right while the artist page slides off to the left
    fun enterX(c: Int): Float = (1f - columnEnter(c)) * geo.width * 0.9f

    val melodySize = geo.dp(212f)
    val melodyCx = geo.width - geo.dp(42f) - melodySize / 2f
    val melodyCy = geo.fy(229f) + melodySize / 2f
    val songCy = geo.fy(151f) + melodySize / 2f

    Box(modifier = Modifier.fillMaxSize().zoomBlurOut { songPage.exit.value }) {
        if (visible(grid)) {
            for (c in -GridCols..GridCols) for (r in -GridRows..GridRows) {
                if (c == 0 && r == 0) continue
                val slot = (c + GridCols) * (2 * GridRows + 1) + (r + GridRows)
                val song = songs[Math.floorMod(slot - (GridCols * (2 * GridRows + 1) + GridRows), n)]
                key(c, r) {
                    Picture(
                        url = song.thumbnailUrl,
                        size = 360,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .requiredSize(coverDp)
                            .at(0f, 0f)
                            .graphicsLayer {
                                val gx = c * colPitch
                                val gy = (r + if (c % 2 != 0) 0.5f else 0f) * rowPitch + column(c)
                                translationX = cx + gx * cosT - gy * sinT - cover / 2f + enterX(c)
                                translationY = cy + gx * sinT + gy * cosT - cover / 2f
                                rotationZ = GridTilt
                                // the pick: everything else shrinks away, farthest first
                                val dist = hypot(c.toFloat(), r.toFloat()) / 5f
                                val p = ((s.gridPick.value - (1f - dist) * 0.35f) / 0.65f).coerceIn(0f, 1f)
                                val k = 1f - 0.35f * easeInCubic(p)
                                scaleX = k
                                scaleY = k
                                alpha = 1f - p
                            },
                    )
                }
            }
            Column(modifier = Modifier.at(geo.inset, geo.fy(628f)).padding(end = 36.dp)) {
                Text("You might\nrecognize this!", style = recapBold(40f, lineHeight = 42f), modifier = Modifier.lineMotion(grid, 0, 2, enterFromDp = 120f))
                Spacer(Modifier.height(14.dp))
                Text("hmm, which one...", style = recapBold(24f), modifier = Modifier.lineMotion(grid, 1, 2, enterFromDp = 120f))
            }
        }

        if (visible(melody)) {
            Text(
                "Hear the\nmelody..",
                style = recapBold(34f, lineHeight = 37f),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .at(0f, geo.top + geo.dp(80f))
                    .padding(end = 42.dp)
                    .lineMotion(melody, 0, 2, enterFromDp = 90f, exitToDp = 60f),
            )
            Text(
                "${hero.title} by\n${hero.artistName}",
                style = recapBold(30f, lineHeight = 34f),
                textAlign = TextAlign.End,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .at(0f, melodyCy + melodySize / 2f + geo.dp(18f))
                    .padding(start = 60.dp, end = 42.dp)
                    .lineMotion(melody, 1, 2, enterFromDp = 90f, exitToDp = 60f),
            )
        }

        if (visible(songPage)) SongReveal(s, hero, songPage, melodyCx, songCy, melodySize, time)

        // the hero cover: picked from the grid, then the melody cover, then up to the song page
        Picture(
            url = hero.thumbnailUrl,
            size = 544,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .requiredSize(with(density) { melodySize.toDp() })
                .at(0f, 0f)
                .graphicsLayer {
                    val m = s.heroCover.value
                    val u = s.heroCoverUp.value
                    val gy = column(0)
                    val gridCx = cx - gy * sinT
                    val gridCy = cy + gy * cosT
                    val pulse = 1f + 0.1f * sin(PI.toFloat() * s.gridPick.value)
                    val size = lerpF(cover * pulse, melodySize, m)
                    val x = lerpF(gridCx, melodyCx, m)
                    val y = lerpF(lerpF(gridCy, melodyCy, m), songCy, u)
                    translationX = x - melodySize / 2f + enterX(0) * (1f - m)
                    translationY = y - melodySize / 2f
                    scaleX = size / melodySize
                    scaleY = size / melodySize
                    rotationZ = GridTilt * (1f - m)
                    alpha = easeOutCubic(stagger(grid.enter.value, GridCols, 2 * GridCols + 1, length = 0.7f))
                },
        )
    }
}

@Composable
private fun SongReveal(s: StoryState, hero: RecapSong, anim: PageAnim, coverCx: Float, coverCy: Float, coverSize: Float, time: RecapTime) {
    val geo = s.geo
    val density = LocalDensity.current
    val drag = { s.dragX }
    val n = 4
    val discD = geo.dp(160f)
    val discEndCx = geo.dp(47f) + discD / 2f
    val discEndCy = geo.fy(176f) + discD / 2f
    // the artist slides out from behind the cover (it's composed first, so it sits underneath)
    Picture(
        url = hero.artistThumbnailUrl ?: hero.thumbnailUrl,
        size = 400,
        shape = CircleShape,
        modifier = Modifier
            .requiredSize(with(density) { discD.toDp() })
            .at(0f, 0f)
            .graphicsLayer {
                val e = easeOutCubic(stagger(anim.enter.value, 0, n, length = 0.6f))
                translationX = lerpF(coverCx, discEndCx, e) - discD / 2f + drag()
                translationY = lerpF(coverCy, discEndCy, e) - discD / 2f
                val k = 0.75f + 0.25f * e
                scaleX = k
                scaleY = k
                alpha = (e * 3f).coerceAtMost(1f) * 0.9f
            },
    )
    Text(
        "${hero.title} by\n${hero.artistName}",
        style = recapBold(22f, lineHeight = 26f),
        textAlign = TextAlign.End,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, coverCy + coverSize / 2f + geo.dp(14f))
            .padding(start = 80.dp, end = 42.dp)
            .lineMotion(anim, 1, n, enterFromDp = -60f, drag = drag),
    )
    Column(modifier = Modifier.at(geo.dp(33f), geo.fy(456f)).padding(end = 36.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.lineMotion(anim, 2, n, drag = drag)) {
            OdometerText("${hero.plays}", recapBold(46f), progress = { stagger(anim.enter.value, 2, n) })
            Text(" Plays!", style = recapBold(46f))
        }
        Text(
            "Your favourite song\nthis month!!!",
            style = recapBold(26f, lineHeight = 28f).copy(color = Color.White.copy(alpha = 0.72f)),
            modifier = Modifier.padding(top = 2.dp).lineMotion(anim, 3, n, drag = drag),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The bill

private val Paper = Color(0xFFEEECE6)
private val Ink = Color(0xFF1B1B1B)
private val ReceiptMono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 17.sp, color = Ink, textAlign = TextAlign.Center)

/** Paper torn in a zigzag at the top and the bottom. */
private object ZigzagShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth = with(density) { 44.dp.toPx() }
        val depth = with(density) { 18.dp.toPx() }
        val teeth = (size.width / tooth).roundToInt().coerceAtLeast(3)
        val step = size.width / teeth
        val path = Path().apply {
            moveTo(0f, depth)
            for (i in 0 until teeth) {
                lineTo(i * step + step / 2f, 0f)
                lineTo((i + 1) * step, depth)
            }
            lineTo(size.width, size.height - depth)
            for (i in teeth downTo 1) {
                lineTo(i * step - step / 2f, size.height)
                lineTo((i - 1) * step, size.height - depth)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun ReceiptPage(s: StoryState, data: RecapStoryData) {
    val geo = s.geo
    val anim = s.pages[P_RECEIPT]
    val density = LocalDensity.current
    Text(
        "Let's see your bill",
        style = recapBold(30f),
        modifier = Modifier.at(geo.inset, geo.top + geo.dp(82f)).lineMotion(anim, 0, 2, riseDp = 20f, exitToDp = 0f),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, geo.fy(240f))
            .height(with(density) { (geo.height - geo.fy(240f)).toDp() })
            .graphicsLayer {
                val e = easeOutBack(anim.enter.value.coerceIn(0f, 1f), 0.9f)
                translationY = (1f - e) * size.height + easeInCubic(anim.exit.value) * size.height * 1.1f
            },
    ) {
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Receipt(data, Modifier.padding(start = 27.dp, end = 27.dp, top = 6.dp, bottom = 120.dp))
        }
    }
}

@Composable
private fun Receipt(data: RecapStoryData, modifier: Modifier) {
    val now = remember { LocalDateTime.now() }
    val dashes = "-".repeat(64)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Paper, ZigzagShape)
            .padding(horizontal = 26.dp, vertical = 40.dp),
    ) {
        ReceiptLine(dashes)
        ReceiptLine("MUSICFY")
        ReceiptLine("${data.monthName} recap")
        ReceiptLine("printed at ${now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}/${now.format(DateTimeFormatter.ofPattern("HH:mm"))}")
        ReceiptLine(dashes)
        Spacer(Modifier.height(18.dp))
        ReceiptHeading("Top Artist")
        data.artists.forEachIndexed { i, a ->
            ReceiptItem(a.thumbnailUrl, CircleShape, "${i + 1}x ${a.name}", "${formatHoursDecimal(a.playTimeMs)}h")
        }
        Spacer(Modifier.height(18.dp))
        ReceiptHeading("Top Music")
        data.songs.take(10).forEachIndexed { i, song ->
            ReceiptItem(song.thumbnailUrl, RoundedCornerShape(5.dp), "${i + 1}x ${song.title}", "${formatHoursDecimal(song.playTimeMs)}h")
        }
        Spacer(Modifier.height(36.dp))
        Text(
            "Total : ${formatHoursMinutes(data.stats.totalMs)} playtime",
            style = ReceiptMono.copy(textAlign = TextAlign.Start),
        )
        Text(
            "${data.stats.plays} plays · ${data.stats.songCount} songs · ${data.stats.artistCount} artists",
            style = ReceiptMono.copy(textAlign = TextAlign.Start, color = Ink.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.height(10.dp))
        ReceiptLine(dashes)
        ReceiptLine("END OF LINE")
        ReceiptLine(dashes)
    }
}

@Composable
private fun ReceiptLine(text: String) {
    Text(text, style = ReceiptMono, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ReceiptHeading(text: String) {
    Text(text, style = recapBold(18f).copy(color = Ink), modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun ReceiptItem(url: String?, shape: Shape, title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        AsyncImage(
            model = url?.resize(96, 96),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFFE53935)),
            error = ColorPainter(Color(0xFFE53935)),
            modifier = Modifier.size(18.dp).clip(shape),
        )
        Text(
            title,
            style = recapBold(13.5f, tracking = -0.2f).copy(color = Color(0xFF3A3A3A)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("- $value", style = recapBold(13.5f, tracking = -0.2f).copy(color = Color(0xFF3A3A3A)))
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun EndPage(s: StoryState, data: RecapStoryData, time: RecapTime) {
    val geo = s.geo
    val anim = s.pages[P_END]
    val cardBottom = geo.top + geo.dp(175f) + (geo.width - 2 * geo.inset) / com.example.musicfy.ui.screens.setup.onboarding.IdCardAspect
    Text(
        "There you go",
        style = recapBold(34f).copy(color = Color.White.copy(alpha = 0.72f)),
        modifier = Modifier.at(geo.inset, geo.top + geo.dp(112f)).lineMotion(anim, 0, 4, exitToDp = 0f),
    )
    Text(
        "That's your\n${data.monthName} recap!",
        style = recapBold(44f, lineHeight = 46f),
        modifier = Modifier.at(geo.inset, cardBottom + geo.dp(34f)).padding(end = 36.dp).lineMotion(anim, 1, 4, exitToDp = 0f),
    )
    Text(
        "TAP TO FINISH",
        style = MonoHint,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .at(0f, geo.fy(782f))
            .lineMotion(anim, 3, 4, exitToDp = 0f),
    )
}
