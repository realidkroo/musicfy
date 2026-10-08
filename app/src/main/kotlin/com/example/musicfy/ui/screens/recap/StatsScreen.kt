// StatsScreen.kt
//
// Stats: everything the play history says about one month or one year. A ruler of months (each
// tick a day, as tall as that day's listening) picks the period; "Re'cap >" opens the Show by /
// year / month picker. Cards open into the detail behind them: a line of every day, the top ten,
// the genre split, the week and the clock. Nothing here is sent anywhere.

package com.example.musicfy.ui.screens.recap

import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.musicfy.ui.component.BlurEffectCache
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalDatabase
import com.example.musicfy.R
import com.example.musicfy.constants.ProfilePicUriKey
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.HomeContentInset
import com.example.musicfy.ui.component.LocalBottomSheetPageState
import com.example.musicfy.ui.component.PopupSheetHandle
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.resize
import com.example.musicfy.ui.utils.stableSystemBars
import com.example.musicfy.utils.rememberPreference
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val StatsCard = Color(0xFF0F0F0F)
private val ChipBg = Color(0xFF2A2A2A)

private fun statsText(size: Float, weight: FontWeight = FontWeight.Bold, color: Color = StatsInk, tracking: Float = -0.02f * size) =
    TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

private fun monthShort(month: Int) = Month.of(month).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
private fun monthFull(month: Int) = Month.of(month).getDisplayName(JavaTextStyle.FULL, Locale.getDefault())

@Composable
fun StatsScreen(navController: NavController) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val sheets = LocalBottomSheetPageState.current
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val today = remember { LocalDate.now() }

    var kind by rememberSaveable { mutableStateOf(StatsRange.Kind.Month) }
    var year by rememberSaveable { mutableStateOf(today.year) }
    var month by rememberSaveable { mutableStateOf(today.monthValue) }
    val range = StatsRange(kind, year, if (kind == StatsRange.Kind.Month) month else 1)

    var firstDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) {
        firstDay = RecapRepository.firstPlayDate(database) ?: today
    }

    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    LaunchedEffect(range) { stats = RecapRepository.load(database, range) }
    var genres by remember { mutableStateOf<GenreState>(GenreState.Loading) }
    LaunchedEffect(stats) {
        val s = stats ?: return@LaunchedEffect
        genres = GenreState.Loading
        genres = RecapGenres.shares(context, s.topArtists)?.let { GenreState.Ready(it) } ?: GenreState.Unavailable
    }

    val start = firstDay ?: today
    val periods = remember(kind, start) {
        if (kind == StatsRange.Kind.Month) {
            val first = YearMonth.from(start)
            val last = YearMonth.from(today)
            generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }
                .map { StatsRange(StatsRange.Kind.Month, it.year, it.monthValue) }.toList()
        } else {
            (start.year..today.year).map { StatsRange(StatsRange.Kind.Year, it) }
        }
    }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val allOpen = CardKeys.all { expanded[it] == true }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val collapse by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f) } }

    fun openPicker() {
        var handle: PopupSheetHandle? = null
        handle = sheets.showWithHandle {
            StatsPicker(
                kind = kind,
                year = year,
                month = month,
                firstDay = start,
                today = today,
                onDone = { k, y, m ->
                    kind = k
                    year = y
                    month = m
                    handle?.dismiss()
                },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxSize().glassRoot(glassState, isActive = { collapse > 0f })) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topInset + 82.dp, bottom = 200.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .padding(horizontal = HomeContentInset)
                            .graphicsLayer { alpha = 1f - collapse },
                    ) {
                        Text("Stats", style = statsText(44f, tracking = -1.6f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$year Re'cap  >",
                            style = statsText(22f, color = StatsInkMuted, tracking = -0.6f),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = ::openPicker),
                        )
                    }
                }
                item(key = "ruler") {
                    val available = remember(periods) { periods.toSet() }
                    StatsRuler(
                        selected = range,
                        available = available,
                        today = today,
                        onSelect = { p ->
                            year = p.year
                            if (p.kind == StatsRange.Kind.Month) month = p.month
                        },
                    )
                }
                item(key = "expandAll") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = HomeContentInset, top = 4.dp, bottom = 14.dp)
                            .clip(CircleShape)
                            .background(ChipBg)
                            .clickable { val open = !allOpen; CardKeys.forEach { expanded[it] = open } }
                            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    ) {
                        Text(if (allOpen) "Collapse all" else "Expand all", style = statsText(13f, FontWeight.SemiBold, tracking = 0f))
                        Spacer(Modifier.width(4.dp))
                        Chevron(open = allOpen, size = 18)
                    }
                }

                val s = stats
                if (s == null) {
                    item(key = "loading") { Spacer(Modifier.height(400.dp)) }
                } else if (s.isEmpty) {
                    item(key = "empty") {
                        StatsCardBox {
                            Text(
                                "Nothing played in ${if (kind == StatsRange.Kind.Month) "${monthFull(s.range.month)} ${s.range.year}" else "${s.range.year}"} yet.",
                                style = statsText(17f),
                            )
                            Text("Play a few songs and this fills itself in.", style = statsText(13f, FontWeight.Medium, StatsInkMuted, 0f), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    val thisWhat = if (kind == StatsRange.Kind.Month) "This month" else "This year"
                    item(key = "total") { TotalCard(s, thisWhat, expanded) }
                    item(key = "genre") { GenreCard(genres, expanded) }
                    s.topArtists.firstOrNull()?.let { top -> item(key = "artist") { ArtistCard(s, top, expanded) } }
                    s.topSongs.firstOrNull()?.let { top -> item(key = "song") { SongCard(s, top, expanded) } }
                    item(key = "often") {
                        Text(
                            "Let's see how often you like to listen to music",
                            style = statsText(16f),
                            modifier = Modifier.padding(start = HomeContentInset, end = HomeContentInset, top = 18.dp, bottom = 12.dp),
                        )
                    }
                    item(key = "weekday") { WeekdayCard(s, thisWhat, expanded) }
                    item(key = "hour") { HourCard(s, expanded) }
                    item(key = "privacy") {
                        Text(
                            "Everything here is collected by your usage on this app and WILL NEVER BE SENT to some random server! " +
                                "(Genres are looked up by artist name only.)",
                            style = statsText(11f, FontWeight.SemiBold, StatsInkMuted, 0f),
                            modifier = Modifier.padding(horizontal = HomeContentInset + 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // glass bar: a sibling of the glassRoot box, never inside it
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 84.dp)
                .graphicsLayer { alpha = collapse },
        ) {
            if (collapse > 0.01f) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 50f * collapse },
                    foundationColor = Color.Black.copy(alpha = 0.5f),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        }

        // top row: the logo, which turns into "Stats" once the title scrolls away (one element
        // morphing, like Home's), and the avatar
        val titleGone by remember { derivedStateOf { collapse > 0.5f } }
        val morph by animateFloatAsState(
            targetValue = if (titleGone) 1f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "statsTopBar",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HomeContentInset, end = HomeContentInset, top = topInset + 32.dp),
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(R.drawable.ic_musicfy_mark),
                    contentDescription = "Musicfy",
                    tint = Color.White,
                    modifier = Modifier
                        .size(width = 31.dp, height = 34.dp)
                        .graphicsLayer {
                            alpha = 1f - morph
                            val k = 1f - morph * 0.25f
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            renderEffect = BlurEffectCache.get(morph * 15f)
                        },
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = morph
                            renderEffect = BlurEffectCache.get((1f - morph) * 15f)
                        }
                        .clickable(enabled = morph > 0.5f, onClick = ::openPicker),
                ) {
                    Text("Stats", style = statsText(28f, tracking = -1f))
                    Spacer(Modifier.width(6.dp))
                    Text("$year Re'cap", style = statsText(15f, color = StatsInkMuted, tracking = -0.3f), modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            AsyncImage(
                model = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" },
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.person),
                error = painterResource(R.drawable.person),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A)),
            )
        }
    }
}

private val CardKeys = listOf("total", "genre", "artist", "song", "weekday", "hour")

internal sealed interface GenreState {
    data object Loading : GenreState
    data object Unavailable : GenreState
    data class Ready(val shares: List<GenreShare>) : GenreState
}

// ---------------------------------------------------------------------------------------------
// Cards

@Composable
private fun StatsCardBox(
    expandKey: String? = null,
    expanded: MutableMap<String, Boolean>? = null,
    detail: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = expandKey != null && expanded?.get(expandKey) == true
    Column(
        modifier = Modifier
            .padding(horizontal = HomeContentInset, vertical = 7.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StatsCard)
            .then(if (expandKey != null && expanded != null) Modifier.clickable { expanded[expandKey] = !open } else Modifier)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Box {
            Column(modifier = Modifier.padding(end = if (detail != null) 30.dp else 0.dp)) { content() }
            if (detail != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ChipBg),
                    contentAlignment = Alignment.Center,
                ) { Chevron(open = open, size = 18) }
            }
        }
        if (detail != null) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(420, easing = Emphasized)) + fadeIn(tween(300, 120)),
                exit = shrinkVertically(tween(320, easing = Emphasized)) + fadeOut(tween(160)),
            ) {
                Column(modifier = Modifier.padding(top = 18.dp, end = 4.dp)) { detail() }
            }
        }
    }
}

@Composable
private fun Chevron(open: Boolean, size: Int) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(320, easing = Emphasized), label = "chevron")
    Icon(
        painter = painterResource(R.drawable.expand_more),
        contentDescription = if (open) "Collapse" else "Expand",
        tint = Color.White,
        modifier = Modifier.size(size.dp).graphicsLayer { rotationZ = turn },
    )
}

/** Plays the odometer each time [key] changes. */
@Composable
private fun rememberRoll(key: Any?): () -> Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(1400, easing = Emphasized)) }
    return { a.value }
}

@Composable
private fun TotalCard(s: ListeningStats, thisWhat: String, expanded: MutableMap<String, Boolean>) {
    val minutes = s.totalMs / 60_000L
    val roll = rememberRoll(s)
    val big = statsText(52f, tracking = -2.4f)
    val unit = statsText(22f, tracking = -0.5f)
    StatsCardBox(
        expandKey = "total",
        expanded = expanded,
        detail = {
            Text(
                if (s.range.kind == StatsRange.Kind.Month) "Every day of ${monthFull(s.range.month)}" else "Every month of ${s.range.year}",
                style = statsText(13f, FontWeight.SemiBold, StatsInkMuted, 0f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (s.range.kind == StatsRange.Kind.Month) {
                val days = s.bucketMs.size
                ListeningLine(
                    values = s.bucketMs,
                    labelAt = { i -> if (i == 0 || (i + 1) % 7 == 0) "${i + 1}" else null },
                    nameOf = { i -> "${monthShort(s.range.month)} ${i + 1}" },
                )
                if (days == 0) Unit
            } else {
                ListeningLine(
                    values = s.bucketMs,
                    labelAt = { i -> if (i % 3 == 0) monthShort(i + 1) else null },
                    nameOf = { i -> monthFull(i + 1) },
                )
            }
        },
    ) {
        Text("$thisWhat, you've been listening for", style = statsText(15f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            OdometerText("${minutes / 60}", big, progress = roll)
            Text("h", style = unit, modifier = Modifier.padding(bottom = 8.dp))
            Text(":", style = big)
            OdometerText((minutes % 60).toString().padStart(2, '0'), big, progress = roll)
            Text("m", style = unit, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3A3A)))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Fact("${s.songCount}", if (s.songCount == 1) "Song" else "Songs")
            Fact("${s.artistCount}", if (s.artistCount == 1) "Artist" else "Artists")
            Fact("${s.albumCount}", if (s.albumCount == 1) "Album" else "Albums")
            Fact("${s.plays}", if (s.plays == 1) "play" else "plays")
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9D9D9)))
        Spacer(Modifier.width(5.dp))
        Text("$value $label", style = statsText(13f, FontWeight.SemiBold, StatsInkSecondary, 0f), maxLines = 1)
    }
}

@Composable
private fun GenreCard(state: GenreState, expanded: MutableMap<String, Boolean>) {
    val shares = (state as? GenreState.Ready)?.shares
    StatsCardBox(
        expandKey = if (shares != null) "genre" else null,
        expanded = expanded,
        detail = shares?.let {
            {
                RankList(
                    rows = it.mapIndexed { i, g ->
                        RankRow(
                            title = g.name,
                            detail = "${(g.fraction * 100).toInt()}% · ${shortDuration(g.playTimeMs)}",
                            value = g.playTimeMs,
                            swatch = GenreColors.getOrElse(i) { GenreOther },
                            noImage = true,
                        )
                    },
                    circle = true,
                )
            }
        },
    ) {
        Text("Dominant Genre", style = statsText(15f))
        when (state) {
            GenreState.Loading -> Text("Sorting your genres…", style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f), modifier = Modifier.padding(vertical = 30.dp))
            GenreState.Unavailable -> Text(
                "Couldn't name your genres right now - this needs a connection the first time.",
                style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f),
                modifier = Modifier.padding(vertical = 18.dp),
            )
            is GenreState.Ready -> {
                val top = state.shares.first()
                Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    GenreDonut(state.shares)
                    Box(Modifier.padding(horizontal = 14.dp).width(34.dp).height(1.dp).background(StatsInkSecondary))
                    Column {
                        Text("${(top.fraction * 100).toInt()}%", style = statsText(22f))
                        Text(top.name, style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // identity never by colour alone: the top five are named under the ring
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    donutSlices(state.shares).take(5).forEachIndexed { i, (_, color) ->
                        val name = state.shares.getOrNull(i)?.name ?: return@forEachIndexed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(4.dp))
                            Text(name, style = statsText(11f, FontWeight.Medium, StatsInkMuted, 0f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(s: ListeningStats, top: RecapArtist, expanded: MutableMap<String, Boolean>) {
    StatsCardBox(
        expandKey = "artist",
        expanded = expanded,
        detail = {
            RankList(
                rows = s.topArtists.take(10).map { a ->
                    RankRow(a.name, "${shortDuration(a.playTimeMs)} · ${a.plays} plays", a.playTimeMs, a.thumbnailUrl)
                },
                circle = true,
            )
        },
    ) {
        Text("You really love listening to this artist", style = statsText(15f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            AsyncImage(
                model = top.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${shortDuration(top.playTimeMs)} playtime and ${top.plays} plays from ${top.name}",
                    style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SongCard(s: ListeningStats, top: RecapSong, expanded: MutableMap<String, Boolean>) {
    StatsCardBox(
        expandKey = "song",
        expanded = expanded,
        detail = {
            RankList(
                rows = s.topSongs.take(10).map { song ->
                    RankRow(song.title, "${song.artistName} · ${song.plays} plays · ${shortDuration(song.playTimeMs)}", song.plays.toLong(), song.thumbnailUrl)
                },
                circle = false,
            )
        },
    ) {
        Text("And without noticing, this song is your #1", style = statsText(15f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            AsyncImage(
                model = top.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${top.title} from ${top.artistName}", style = statsText(20f, tracking = -0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${shortDuration(top.playTimeMs)} playtime and ${top.plays} plays",
                    style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f),
                )
            }
        }
    }
}

@Composable
private fun WeekdayCard(s: ListeningStats, thisWhat: String, expanded: MutableMap<String, Boolean>) {
    val best = s.weekdayMs.indices.maxByOrNull { s.weekdayMs[it] } ?: 0
    val day = DayOfWeek.of(best + 1)
    StatsCardBox(
        expandKey = "weekday",
        expanded = expanded,
        detail = {
            WeekdayBars(
                values = s.weekdayMs,
                labels = DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) },
            )
        },
    ) {
        Text("$thisWhat, you like to listen to music every", style = statsText(13f, FontWeight.SemiBold))
        Text(day.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()), style = statsText(32f, tracking = -1.2f), modifier = Modifier.padding(vertical = 2.dp))
        Text("totaling ${formatHoursDecimal(s.weekdayMs[best])} hours", style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f))
    }
}

@Composable
private fun HourCard(s: ListeningStats, expanded: MutableMap<String, Boolean>) {
    val best = s.hourMs.indices.maxByOrNull { s.hourMs[it] } ?: 0
    StatsCardBox(
        expandKey = "hour",
        expanded = expanded,
        detail = {
            ListeningLine(
                values = s.hourMs,
                labelAt = { h -> if (h % 6 == 0) hourLabel(h) else null },
                nameOf = { h -> hourLabel(h) },
            )
        },
    ) {
        Text("And you love to listen in the ${timeOfDayName(best)} exactly around", style = statsText(13f, FontWeight.SemiBold))
        Text(hourLabel(best), style = statsText(32f, tracking = -1.2f), modifier = Modifier.padding(vertical = 2.dp))
        Text("totaling ${formatHoursDecimal(s.hourMs[best])} hours at this time!", style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f))
    }
}

private fun hourLabel(h: Int): String = java.time.LocalTime.of(h, 0)
    .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

// ---------------------------------------------------------------------------------------------
// The picker sheet

@Composable
private fun ColumnScope.StatsPicker(
    kind: StatsRange.Kind,
    year: Int,
    month: Int,
    firstDay: LocalDate,
    today: LocalDate,
    onDone: (StatsRange.Kind, Int, Int) -> Unit,
) {
    var k by remember { mutableStateOf(kind) }
    var y by remember { mutableStateOf(year) }
    var m by remember { mutableStateOf(month) }
    val years = (firstDay.year..today.year).toList()
    val months = remember(y) {
        val from = if (y == firstDay.year) firstDay.monthValue else 1
        val to = if (y == today.year) today.monthValue else 12
        (from..to).toList()
    }
    if (m !in months) m = months.last()

    Column(modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
        Text("Show by", style = statsText(36f, tracking = -1.4f))
        Spacer(Modifier.height(10.dp))
        listOf(StatsRange.Kind.Month to "Month", StatsRange.Kind.Year to "Year").forEach { (value, label) ->
            val on = k == value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Color(0xFF4A4A4A) else Color(0xFF2A2A2A))
                    .then(if (on) Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), RoundedCornerShape(14.dp)) else Modifier)
                    .clickable { k = value }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFD9D9D9)), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF2A2A2A)))
                }
                Spacer(Modifier.width(14.dp))
                Text(label, style = statsText(18f), modifier = Modifier.weight(1f))
                if (on) Text("Selected", style = statsText(13f, FontWeight.SemiBold, StatsInkMuted, 0f))
            }
        }
        Box(Modifier.padding(vertical = 18.dp).fillMaxWidth().height(1.dp).background(Color(0xFF555555)))
        Text("What year?", style = statsText(36f, tracking = -1.4f))
        Wheel(items = years, selected = y, label = { "$it" }, onSelected = { y = it })
        AnimatedVisibility(visible = k == StatsRange.Kind.Month) {
            Column {
                Text("What month?", style = statsText(36f, tracking = -1.4f), modifier = Modifier.padding(top = 10.dp))
                Wheel(
                    items = months,
                    selected = m,
                    label = { mm -> monthFull(mm) + if (y == today.year && mm == today.monthValue) " (this month)" else "" },
                    onSelected = { m = it },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF666666))
                .clickable { onDone(k, y, m) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", style = statsText(20f))
        }
    }
}

/** A snapping wheel of [items]; the middle row, inside the outlined pill, is the choice. */
@Composable
private fun <T> Wheel(items: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    val rowH = 40.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(selected).coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val density = LocalDensity.current
    val rowPx = with(density) { rowH.toPx() }
    val centre by remember(items) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            (first + if (state.firstVisibleItemScrollOffset > rowPx / 2f) 1 else 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(centre, items) { items.getOrNull(centre)?.let { if (it != selected) onSelected(it) } }

    Box(modifier = Modifier.fillMaxWidth().height(rowH * 5).padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(rowH)
                .border(BorderStroke(1.dp, Color(0xFF8A8A8A)), CircleShape),
        )
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowH * 2),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items) { i, item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowH)
                        .graphicsLayer {
                            val info = state.layoutInfo
                            val visible = info.visibleItemsInfo.firstOrNull { it.index == i }
                            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            val d = if (visible != null) kotlin.math.abs(visible.offset + visible.size / 2f - mid) / (rowPx * 2.5f) else 1f
                            alpha = (1f - d).coerceIn(0.25f, 1f)
                            val k = 1f - 0.12f * d.coerceIn(0f, 1f)
                            scaleX = k
                            scaleY = k
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(item), style = statsText(15f, if (i == centre) FontWeight.Bold else FontWeight.SemiBold))
                }
            }
        }
    }
}
