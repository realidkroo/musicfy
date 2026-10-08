// DeveloperSpaceScreen.kt
//
// "Dev Spaces": the developer's page. This build (version, number, how old), what the repo did since the
// build was made, the repo's totals, its open issues with filters, the resolved ones, and - at the bottom
// - the Experimental settings. Every number is fetched from GitHub each time the page opens
// (DevSpaceRepository); the top morphs into a glass bar on scroll like Settings, Library and Stats.

package com.example.musicfy.ui.screens.settings.devspace

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.BuildConfig
import com.example.musicfy.LocalPlayerAwareWindowInsets
import com.example.musicfy.R
import com.example.musicfy.core.updater.parseFullVersion
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.OdometerNumber
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.screens.settings.CategoryBubble
import com.example.musicfy.ui.screens.update.AppIconImage
import com.example.musicfy.ui.theme.InterFontFamily
import com.example.musicfy.ui.utils.stableSystemBars
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// the same measures as the Settings home: 20dp page sides, 24dp cards, 12dp between them
private val SideInset = 20.dp
private val BarInset = 24.dp
private val CardCorner = 24.dp
private val CardPad = 18.dp

private val DevCard = Color(0xFF1C1C1E)
private val DevRow = Color(0xFF2C2C2E)
private val DevMuted = Color(0xFF8E8E93)
private val DevPill = Color(0xFF3A3A3C)
private val BugText = Color(0xFFFF8A80)
private val BugFill = Color(0xFF4A2424)
private const val IssueRowsShown = 6

private fun devText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    tracking: Float = -0.4f,
) = TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

/** A big figure's size in dp, so it follows the card's width rather than the font-size setting. */
@Composable
private fun figureSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

private enum class IssueFilter(val label: String) {
    All("All"), Bug("Bug"), Feature("Feature"), Other("Other");

    fun matches(issue: DevIssue) = when (this) {
        All -> true
        Bug -> issue.isBug
        Feature -> issue.isFeature
        Other -> !issue.isBug && !issue.isFeature
    }
}

@Composable
fun DeveloperSpaceScreen(navController: NavController) {
    val context = LocalContext.current
    val stats by DevSpaceRepository.current.collectAsState()
    val loading by DevSpaceRepository.loading.collectAsState()
    val error by DevSpaceRepository.error.collectAsState()
    var reload by remember { mutableIntStateOf(0) }

    // every open (and every "try again") asks GitHub again
    LaunchedEffect(reload) { DevSpaceRepository.refresh(BuildConfig.BUILD_TIME_MILLIS, force = true) }

    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }
    // Scroll-linked, not latched: the glass fades in over the first 56dp and the small title takes over
    // as the big one slides under the bar, so it follows the finger and settles wherever it stops.
    val density = LocalDensity.current
    val barRangePx = with(density) { 56.dp.toPx() }
    val titleStartPx = with(density) { 70.dp.toPx() }
    val titleRangePx = with(density) { 60.dp.toPx() }
    val barP by remember { derivedStateOf { FastOutSlowInEasing.transform((scrollState.value / barRangePx).coerceIn(0f, 1f)) } }
    val titleP by remember {
        derivedStateOf { FastOutSlowInEasing.transform(((scrollState.value - titleStartPx) / titleRangePx).coerceIn(0f, 1f)) }
    }
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    var filter by remember { mutableStateOf(IssueFilter.All) }
    var showAllOpen by remember { mutableStateOf(false) }
    var showAllResolved by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // glassRoot wraps the content only; the glass bar below is a sibling (see glassroot-rendernode-crash)
        Box(
            Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { barP > 0f }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = SideInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(topInset + 30.dp + 40.dp + 6.dp))

                // avatar + title scroll away with the page, fading as they pass under the bar
                Column(
                    modifier = Modifier
                        .padding(horizontal = BarInset - SideInset)
                        .graphicsLayer { alpha = 1f - titleP },
                ) {
                    // no blur on the avatar: a CLAMP blur smears the four points where the circle touches
                    // its layer's edge into a "+"; only the text is blurred
                    DevAvatar(Modifier.size(72.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dev Spaces",
                        style = devText(34f, tracking = -1.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { renderEffect = BlurEffectCache.get(titleP * 10f) },
                    )
                }
                Spacer(Modifier.height(4.dp))

                BuildCard()

                val s = stats
                val ready = s != null || !loading
                StatsCard(
                    title = "Repo’s stats",
                    subtitle = "By today, from when this build was made, there was",
                    figures = listOf(
                        s?.newIssues to "new reported issues",
                        s?.newResolved to "new resolved issue",
                        s?.commits to "commits",
                        s?.releases to "releases",
                    ),
                    settled = ready,
                )
                StatsCard(
                    title = "Repo’s stats",
                    subtitle = "in total",
                    figures = listOf(
                        s?.reportedTotal to "reported issues",
                        s?.closedTotal to "resolved issue",
                    ),
                    settled = ready,
                )

                val open = s?.issues.orEmpty().filter { it.open }
                val resolved = s?.issues.orEmpty().filter { !it.open }

                Panel {
                    Text("Repo Issue", style = devText(17f))
                    Spacer(Modifier.height(10.dp))
                    FilterChips(
                        selected = filter,
                        countOf = { f -> open.count { f.matches(it) } },
                        onSelect = { filter = it; showAllOpen = false; showAllResolved = false },
                    )
                    Spacer(Modifier.height(12.dp))
                    val shown = open.filter { filter.matches(it) }
                    when {
                        s == null && loading -> repeat(3) { SkeletonRow() }
                        s == null -> RetryNote(error ?: "Couldn't reach GitHub") { reload++ }
                        shown.isEmpty() -> Note(if (open.isEmpty()) "No open issues. Nice." else "No ${filter.label.lowercase()} issues open.")
                        else -> IssueList(shown, showAllOpen, context) { showAllOpen = true }
                    }
                    if (s != null && error != null) {
                        Spacer(Modifier.height(10.dp))
                        RetryNote("$error. Showing the last numbers.") { reload++ }
                    }
                }

                val shownResolved = resolved.filter { filter.matches(it) }
                if (s != null && shownResolved.isNotEmpty()) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Resolved", style = devText(17f), modifier = Modifier.weight(1f))
                            Text("${shownResolved.size}", style = devText(15f, FontWeight.SemiBold, DevMuted))
                        }
                        Spacer(Modifier.height(12.dp))
                        IssueList(shownResolved, showAllResolved, context) { showAllResolved = true }
                    }
                }

                Spacer(Modifier.height(6.dp))
                CategoryBubble(
                    title = "Experimental settings",
                    iconRes = R.drawable.biotech,
                    onClick = { navController.navigate("experimental_settings") },
                )

                Spacer(Modifier.height(maxOf(180.dp, bottomInset + 160.dp)))
            }
        }

        // glass bar, a sibling of the glassRoot box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 84.dp)
                .graphicsLayer { alpha = barP },
        ) {
            if (barP > 0.01f) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 50f * barP },
                    foundationColor = Color.Black.copy(alpha = 0.5f),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))),
            )
        }

        // back button, and the small avatar + title that slide in beside it once the big ones are gone
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BarInset - 4.dp, end = BarInset, top = topInset + 30.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF48484A))
                    .clickable { navController.navigateUp() },
            ) {
                Icon(painterResource(R.drawable.chevron_leftpx), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .graphicsLayer {
                        alpha = titleP
                        translationY = (1f - titleP) * 10.dp.toPx()
                    },
            ) {
                DevAvatar(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Dev Spaces",
                    style = devText(20f, tracking = -0.6f),
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { renderEffect = BlurEffectCache.get((1f - titleP) * 10f) },
                )
            }
        }
    }
}

/** The developer's picture, the one the About sheet shows. */
@Composable
internal fun DevAvatar(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.frame_51_3),
        contentDescription = "roo",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).background(Color(0xFFD9D9D9)),
    )
}

@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(DevCard)
            .padding(CardPad)
            .animateContentSize(tween(260, easing = FastOutSlowInEasing)),
        content = content,
    )
}

@Composable
private fun BuildCard() {
    val version = BuildConfig.VERSION_NAME
    val shortVersion = version.substringBefore(' ')
    val buildNumber = parseFullVersion(version).buildNumber
    val buildMs = BuildConfig.BUILD_TIME_MILLIS
    val buildDate = remember(buildMs) {
        if (buildMs <= 0L) null
        else Instant.ofEpochMilli(buildMs).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val days = buildDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0L) }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Musicfy $shortVersion", style = devText(18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append("Build")
                        if (buildNumber > 0) append(" #$buildNumber")
                        if (buildDate != null) append("  ").append(buildDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    },
                    style = devText(13f, FontWeight.Medium, DevMuted, -0.1f),
                    maxLines = 2,
                )
            }
        }
        if (days != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                when (days) {
                    0L -> "this build is from today"
                    1L -> "this build was 1 day old"
                    else -> "this build was $days days old"
                },
                style = devText(12f, FontWeight.Medium, DevMuted, -0.1f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    subtitle: String,
    figures: List<Pair<Int?, String>>,
    settled: Boolean,
) {
    Panel {
        Text(title, style = devText(17f))
        Text(subtitle, style = devText(13f, FontWeight.Medium, Color(0xFFE5E5EA), -0.1f))
        Spacer(Modifier.height(16.dp))
        // each column is as wide as the card allows; the figure shrinks if the number runs long
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val divider = 1.dp
            val cell = (maxWidth - divider * (figures.size - 1)) / figures.size
            val digits = figures.maxOf { (it.first ?: 0).toString().length }.coerceAtLeast(1)
            val figureDp = ((cell.value - 14f) / (digits * 0.6f)).coerceIn(22f, 40f)
            Row(verticalAlignment = Alignment.Top) {
                figures.forEachIndexed { i, (value, label) ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .width(divider)
                                .height((figureDp * 0.85f).dp)
                                .background(Color.White.copy(alpha = 0.35f)),
                        )
                    }
                    Column(Modifier.width(cell).padding(start = if (i == 0) 0.dp else 14.dp, end = 4.dp)) {
                        OdometerNumber(
                            value = value?.toString() ?: if (settled) "–" else "0",
                            fontSize = figureSp(figureDp),
                            letterSpacing = (-1.5).sp,
                        )
                        Text(label, style = devText(11f, FontWeight.Medium, Color(0xFFD1D1D6), 0f), maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: IssueFilter, countOf: (IssueFilter) -> Int, onSelect: (IssueFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        IssueFilter.entries.forEach { f ->
            val on = f == selected
            val n = countOf(f)
            Text(
                text = if (n > 0) "${f.label} $n" else f.label,
                style = devText(13f, FontWeight.SemiBold, if (on) Color.Black else Color.White, -0.2f),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) Color.White else DevPill)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun IssueList(issues: List<DevIssue>, showAll: Boolean, context: android.content.Context, onShowAll: () -> Unit) {
    val visible = if (showAll) issues else issues.take(IssueRowsShown)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { IssueRow(it) { openUrl(context, it.url) } }
        if (!showAll && issues.size > IssueRowsShown) {
            Text(
                "Show all ${issues.size}",
                style = devText(13f, FontWeight.SemiBold, Color.White, -0.2f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(DevPill)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun IssueRow(issue: DevIssue, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DevRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(issue.title, style = devText(15f, FontWeight.SemiBold, tracking = -0.2f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("#${issue.number} · ${ago(issue.createdAt)}", style = devText(12f, FontWeight.Medium, DevMuted, 0f), maxLines = 1)
        }
        val tag = if (issue.isBug) "bug" else issue.labels.firstOrNull()
        if (tag != null) {
            Spacer(Modifier.width(10.dp))
            val bug = issue.isBug
            Text(
                tag,
                style = devText(11f, FontWeight.SemiBold, if (bug) BugText else Color(0xFFD1D1D6), 0f),
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (bug) BugFill else DevPill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SkeletonRow() {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { alpha = pulse }
            .clip(RoundedCornerShape(18.dp))
            .background(DevRow),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = devText(14f, FontWeight.Medium, DevMuted, -0.2f), modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun RetryNote(text: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = devText(13f, FontWeight.Medium, DevMuted, -0.2f), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            "Try again",
            style = devText(13f, FontWeight.SemiBold, Color.Black, -0.2f),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

private fun ago(at: Instant): String {
    val days = ChronoUnit.DAYS.between(at, Instant.now())
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "${days}d ago"
        days < 365L -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** What the Musicfy page's Developer card shows; null until GitHub has answered once. */
data class DevCounts(val open: Int, val resolved: Int)

@Composable
fun rememberDevCounts(): DevCounts? {
    val stats by DevSpaceRepository.current.collectAsState()
    LaunchedEffect(Unit) { DevSpaceRepository.refresh(BuildConfig.BUILD_TIME_MILLIS, force = false) }
    return stats?.let { DevCounts(it.openTotal, it.closedTotal) }
}
