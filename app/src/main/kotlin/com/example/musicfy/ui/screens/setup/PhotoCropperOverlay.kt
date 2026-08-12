package com.example.musicfy.ui.screens.setup

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity

private val IMAGE_INSET = 20.dp
private val IMAGE_CORNER = 28.dp

@Composable
fun PhotoCropperContainer(
    uri: Uri?,
    onDone: (Uri) -> Unit,
    onCancel: () -> Unit,
    onSelectNewImage: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val insetPx = with(LocalDensity.current) { IMAGE_INSET.toPx() }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var androidBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val overlayProgress = remember { Animatable(0f) }

    LaunchedEffect(uri) {
        if (uri != null) {
            overlayProgress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 220f))
            withContext(Dispatchers.IO) {
                try {
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    val maxDim = 2048
                    val finalBmp = if (bmp.width > maxDim || bmp.height > maxDim) {
                        val ratio = (maxDim.toFloat() / maxOf(bmp.width, bmp.height))
                        val newWidth = (bmp.width * ratio).toInt()
                        val newHeight = (bmp.height * ratio).toInt()
                        Bitmap.createScaledBitmap(bmp, newWidth, newHeight, true)
                    } else {
                        bmp
                    }

                    androidBitmap = finalBmp
                    bitmap = finalBmp.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            overlayProgress.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 220f))
            bitmap = null
            androidBitmap = null
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropCenter by remember { mutableStateOf(Offset.Zero) }
    var cropRadius by remember { mutableStateOf(0f) }
    val minRadius = 100f

    LaunchedEffect(containerSize) {
        if (containerSize.width > 0 && cropRadius == 0f) {
            cropCenter = Offset(containerSize.width / 2f, containerSize.height / 2f)

            cropRadius = (minOf(containerSize.width, containerSize.height) / 2f - insetPx)
                .coerceAtLeast(minRadius) * 0.92f
        }
    }

    val effectiveProgress = overlayProgress.value

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.08f * effectiveProgress
                    scaleX = scale
                    scaleY = scale

                    clip = true
                    val radius = (32f * effectiveProgress).coerceAtLeast(0f)
                    shape = RoundedCornerShape(radius.dp.toPx())
                }
        ) {
            content()
        }

        BackHandler(enabled = effectiveProgress > 0.01f) { onCancel() }

        if (effectiveProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = effectiveProgress.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.7f * effectiveProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {  }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val inverseProgress = 1f - effectiveProgress
                        translationY = size.height * inverseProgress
                    }
            ) {

        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF121212))
            ) {

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .width(64.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp)
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->

                                val maxRadius = minOf(
                                    containerSize.width / 2f - insetPx,
                                    containerSize.height / 2f - insetPx
                                ).coerceAtLeast(minRadius)
                                val newRadius = (cropRadius * zoom).coerceIn(minRadius, maxRadius)

                                val newCenter = cropCenter + pan

                                fun clamp(value: Float, extent: Int): Float {
                                    val lo = insetPx + newRadius
                                    val hi = extent - insetPx - newRadius
                                    return if (lo >= hi) extent / 2f else value.coerceIn(lo, hi)
                                }

                                val clampedX = clamp(newCenter.x, containerSize.width)
                                val clampedY = clamp(newCenter.y, containerSize.height)

                                cropCenter = Offset(clampedX, clampedY)
                                cropRadius = newRadius
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        val bmp = bitmap!!

                        val inset = IMAGE_INSET.toPx()
                        val availW = size.width - inset * 2f
                        val availH = size.height - inset * 2f
                        val scale = minOf(availW / bmp.width, availH / bmp.height)

                        val scaledW = bmp.width * scale
                        val scaledH = bmp.height * scale

                        val offsetX = inset + (availW - scaledW) / 2f
                        val offsetY = inset + (availH - scaledH) / 2f

                        clipPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(offsetX, offsetY, offsetX + scaledW, offsetY + scaledH),
                                        cornerRadius = CornerRadius(IMAGE_CORNER.toPx(), IMAGE_CORNER.toPx())
                                    )
                                )
                            }
                        ) {
                            drawImage(
                                image = bmp,
                                dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                dstSize = IntSize(scaledW.toInt(), scaledH.toInt())
                            )
                        }

                    drawPath(
                        path = Path().apply {
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addOval(Rect(cropCenter, cropRadius))
                            fillType = PathFillType.EvenOdd
                        },
                        color = Color.Black.copy(alpha = 0.7f)
                    )

                    drawCircle(
                        color = Color.White,
                        radius = cropRadius,
                        center = cropCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Text(
            text = "Adjust your photo profile",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        Button(
            onClick = onSelectNewImage,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Select new image", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val origBmp = androidBitmap ?: return@launch

                    val availW = containerSize.width - insetPx * 2f
                    val availH = containerSize.height - insetPx * 2f
                    val scale = minOf(availW / origBmp.width, availH / origBmp.height)

                    val scaledW = origBmp.width * scale
                    val scaledH = origBmp.height * scale

                    val offsetX = insetPx + (availW - scaledW) / 2f
                    val offsetY = insetPx + (availH - scaledH) / 2f

                    val normalizedCenterX = (cropCenter.x - offsetX) / scale
                    val normalizedCenterY = (cropCenter.y - offsetY) / scale
                    val normalizedRadius = cropRadius / scale

                    val left = (normalizedCenterX - normalizedRadius).toInt().coerceAtLeast(0)
                    val top = (normalizedCenterY - normalizedRadius).toInt().coerceAtLeast(0)
                    var cropSize = (normalizedRadius * 2).toInt()

                    if (left + cropSize > origBmp.width) cropSize = origBmp.width - left
                    if (top + cropSize > origBmp.height) cropSize = origBmp.height - top

                    val croppedBmp = Bitmap.createBitmap(origBmp, left, top, cropSize, cropSize)

                    val file = File(context.cacheDir, "cropped_profile_${UUID.randomUUID()}.jpg")
                    val out = FileOutputStream(file)
                    croppedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()

                    withContext(Dispatchers.Main) {
                        onDone(Uri.fromFile(file))
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        }
    }
        }
    }
}
}
