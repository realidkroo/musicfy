// DiscCover.kt

package com.example.musicfy.ui.player.customize

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size as CoilSize
import com.example.musicfy.constants.PlayerCoverStyle
import com.example.musicfy.ui.utils.resize
import kotlin.math.min

@Immutable
data class DiscGeometry(

    val diameter: Float,

    val centerX: Float,
    val centerY: Float,

    val art: Float,

    val spindle: Float,

    val plateOffsetX: Float,
    val plateOffsetY: Float,

    val plateWidth: Float,
    val plateHeight: Float,

    val plateRotation: Float,

    val albumCard: AlbumCard? = null,
) {
    @Immutable
    data class AlbumCard(val centerX: Float, val centerY: Float, val side: Float)
}

fun discGeometryFor(style: PlayerCoverStyle): DiscGeometry = when (style) {

    PlayerCoverStyle.DISC_SMALL_FULL -> DiscGeometry(
        diameter = 1f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.88f,
        spindle = 0.20f,
        plateOffsetX = -0.30f,
        plateOffsetY = -0.30f,
        plateWidth = 0.26f,
        plateHeight = 0.10f,
        plateRotation = -38f,
    )
    PlayerCoverStyle.DISC_SMALL_LABEL -> DiscGeometry(
        diameter = 1f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.44f,
        spindle = 0.13f,
        plateOffsetX = -0.29f,
        plateOffsetY = -0.22f,
        plateWidth = 0.28f,
        plateHeight = 0.11f,
        plateRotation = -60f,
    )
    PlayerCoverStyle.DISC_BIG_FULL -> DiscGeometry(
        diameter = 1.55f,
        centerX = 0.24f,
        centerY = 0.20f,
        art = 0.95f,
        spindle = 0.17f,
        plateOffsetX = 0.02f,
        plateOffsetY = 0.30f,
        plateWidth = 0.22f,
        plateHeight = 0.085f,
        plateRotation = 0f,
    )
    PlayerCoverStyle.DISC_BIG_LABEL -> DiscGeometry(
        diameter = 1.60f,
        centerX = 0.22f,
        centerY = 0.18f,
        art = 0.42f,
        spindle = 0.11f,
        plateOffsetX = 0.06f,
        plateOffsetY = 0.28f,
        plateWidth = 0.24f,
        plateHeight = 0.09f,
        plateRotation = -8f,
    )

    PlayerCoverStyle.DISC_ALBUM -> DiscGeometry(
        diameter = 0.86f,
        centerX = 0.40f,
        centerY = 0.5f,
        art = 0.40f,
        spindle = 0.12f,
        plateOffsetX = -0.28f,
        plateOffsetY = -0.22f,
        plateWidth = 0.28f,
        plateHeight = 0.11f,
        plateRotation = -60f,
        albumCard = DiscGeometry.AlbumCard(centerX = 0.66f, centerY = 0.5f, side = 0.68f),
    )

    PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> DiscGeometry(
        diameter = 0.86f,
        centerX = 0.5f,
        centerY = 0.5f,
        art = 0.88f,
        spindle = 0.20f,
        plateOffsetX = 0f,
        plateOffsetY = 0f,
        plateWidth = 0.26f,
        plateHeight = 0.10f,
        plateRotation = 0f,
    )
}

private val PlatterBlack = Color(0xFF121114)
private val PlatterEdge = Color(0xFF2A2830)

private fun grooveBrush(radius: Float): Brush {
    val stops = ArrayList<Pair<Float, Color>>(GrooveBands * 2 + 2)

    stops.add(0f to Color.Transparent)
    stops.add(GrooveInner to Color.Transparent)
    for (band in 0 until GrooveBands) {
        val t = GrooveInner + (1f - GrooveInner) * (band / GrooveBands.toFloat())
        val next = GrooveInner + (1f - GrooveInner) * ((band + 0.5f) / GrooveBands)
        stops.add(t to Color.White.copy(alpha = 0.035f))
        stops.add(next to Color.Black.copy(alpha = 0.30f))
    }
    stops.add(1f to Color.Black.copy(alpha = 0.22f))
    return Brush.radialGradient(
        colorStops = stops.toTypedArray(),
        center = Offset(radius, radius),
        radius = radius,
    )
}

private const val GrooveBands = 44
private const val GrooveInner = 0.34f

private fun sheenBrush(radius: Float): Brush = Brush.sweepGradient(
    0.00f to Color.White.copy(alpha = 0.00f),
    0.08f to Color.White.copy(alpha = 0.13f),
    0.16f to Color.White.copy(alpha = 0.00f),
    0.50f to Color.White.copy(alpha = 0.00f),
    0.58f to Color.White.copy(alpha = 0.09f),
    0.66f to Color.White.copy(alpha = 0.00f),
    1.00f to Color.White.copy(alpha = 0.00f),
    center = Offset(radius, radius),
)

@Composable
fun DiscCover(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    discName: String,
    realistic: Boolean,
    editMode: Boolean,
    rotationProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val geometry = remember(style) { discGeometryFor(style) }

    BoxWithConstraints(modifier = modifier) {
        val boxSide = min(maxWidth.value, maxHeight.value)
        val discSide = boxSide * geometry.diameter
        val discSidePx = with(density) { discSide.dp.toPx() }
        val radiusPx = discSidePx / 2f

        val discLeft = maxWidth.value * geometry.centerX - discSide / 2f
        val discTop = maxHeight.value * geometry.centerY - discSide / 2f
        val discLeftPx = with(density) { discLeft.dp.toPx() }
        val discTopPx = with(density) { discTop.dp.toPx() }

        val grooves = remember(radiusPx) { grooveBrush(radiusPx) }
        val sheen = remember(radiusPx) { sheenBrush(radiusPx) }

        val platterModifier = Modifier
            .requiredSize(discSide.dp)
            .offset { IntOffset(discLeftPx.toInt(), discTopPx.toInt()) }

        Box(
            modifier = platterModifier
                .graphicsLayer {
                    rotationZ = rotationProvider()

                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = PlatterBlack, radius = r, center = center)
                if (realistic) {
                    drawCircle(brush = grooves, radius = r, center = center)
                }
                drawCircle(
                    color = PlatterEdge,
                    radius = r - StrokeHairline.toPx() / 2f,
                    center = center,
                    style = Stroke(width = StrokeHairline.toPx()),
                )
            }

            if (artworkUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUrl.resize(1200, 1200))
                        .allowHardware(true)
                        .crossfade(300)
                        .size(CoilSize(1200, 1200))
                        .precision(Precision.INEXACT)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((discSide * geometry.art).dp)
                        .clip(androidx.compose.foundation.shape.CircleShape),
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val spindleR = r * geometry.spindle
                drawCircle(color = SpindleFill, radius = spindleR, center = center)
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = spindleR,
                    center = center,
                    style = Stroke(width = StrokeHairline.toPx()),
                )
            }

            DiscNamePlate(
                discName = discName,
                editMode = editMode,
                discSide = discSide,
                geometry = geometry,
            )
        }

        if (realistic) {
            Canvas(modifier = platterModifier) {
                drawCircle(
                    brush = sheen,
                    radius = size.minDimension / 2f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
        }

        val card = geometry.albumCard
        if (card != null && artworkUrl != null) {
            val side = boxSide * card.side
            val cardLeftPx = with(density) { (maxWidth.value * card.centerX - side / 2f).dp.toPx() }
            val cardTopPx = with(density) { (maxHeight.value * card.centerY - side / 2f).dp.toPx() }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl.resize(1200, 1200))
                    .allowHardware(true)
                    .crossfade(300)
                    .size(CoilSize(1200, 1200))
                    .precision(Precision.INEXACT)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .requiredSize(side.dp)
                    .offset { IntOffset(cardLeftPx.toInt(), cardTopPx.toInt()) }
                    .clip(RoundedCornerShape(side.dp * 0.06f)),
            )
        }
    }
}

private val SpindleFill = Color(0xFF3A383F)
private val StrokeHairline = 1.dp

@Composable
private fun BoxScope.DiscNamePlate(
    discName: String,
    editMode: Boolean,
    discSide: Float,
    geometry: DiscGeometry,
) {
    if (discName.isBlank() && !editMode) return

    val density = LocalDensity.current
    val plateW = discSide * geometry.plateWidth
    val plateH = discSide * geometry.plateHeight
    val offsetXPx = with(density) { (discSide * geometry.plateOffsetX).dp.toPx() }
    val offsetYPx = with(density) { (discSide * geometry.plateOffsetY).dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .size(width = plateW.dp, height = plateH.dp)
            .offset { IntOffset(offsetXPx.toInt(), offsetYPx.toInt()) }
            .graphicsLayer { rotationZ = geometry.plateRotation }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (discName.isBlank()) 0.35f else 0.22f),
                shape = RoundedCornerShape(percent = 40),
            ),
    ) {
        if (discName.isNotBlank()) {
            Text(
                text = discName,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = (plateH * 0.42f).sp,
                lineHeight = (plateH * 0.50f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun DiscTonearm(
    liftProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val lift = liftProvider().coerceIn(0f, 1f)

        val pivot = Offset(size.width * 1.02f, size.height * 0.22f)
        val armLength = size.width * 0.62f

        val angle = Math.toRadians((TonearmRestDegrees + (TonearmLiftDegrees - TonearmRestDegrees) * lift).toDouble())
        val head = Offset(
            x = pivot.x + (armLength * kotlin.math.cos(angle)).toFloat(),
            y = pivot.y + (armLength * kotlin.math.sin(angle)).toFloat(),
        )

        drawLine(
            color = ArmMetal,
            start = pivot,
            end = head,
            strokeWidth = size.width * 0.022f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        val headW = size.width * 0.20f
        val headH = size.width * 0.15f
        drawOval(
            color = HeadBlack,
            topLeft = Offset(head.x - headW / 2f, head.y - headH / 2f),
            size = Size(headW, headH),
        )
        drawCircle(color = HeadBlack, radius = size.width * 0.045f, center = pivot)
    }
}

private val ArmMetal = Color(0xFFCFCBD3)
private val HeadBlack = Color(0xFF17161A)
private const val TonearmRestDegrees = 168f
private const val TonearmLiftDegrees = 196f
