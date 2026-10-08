// PlaylistCovers.kt

package com.example.musicfy.playlistcover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Immutable
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.example.musicfy.db.MusicDatabase
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The generated playlist covers. Each one is drawn from the playlist's title and the colours of
 * its top song - the layouts follow the four reference frames: a diagonal gradient with the title
 * at the top, the same with the title at the bottom, two big circles of the song's colour over the
 * gradient, and the song's own artwork, blurred and darkened, behind a dark gradient.
 *
 * They are drawn into a real bitmap and stored as a file (see [PlaylistCoverStore]) rather than
 * shipped as artwork, so every playlist gets its own colours for the price of one small image.
 */
enum class PlaylistCoverStyle(val key: String, val displayName: String) {
    GRADIENT_TOP("gt", "Gradient"),
    GRADIENT_BOTTOM("gb", "Gradient, title below"),
    BLOOM("bl", "Bloom"),
    BACKDROP("bd", "Backdrop");

    companion object {
        fun fromKey(key: String?): PlaylistCoverStyle? = entries.firstOrNull { it.key == key }
    }
}

/** What a user picks for a cover: one of the generated styles, or a picture of their own. */
sealed interface CoverChoice {
    data class Generated(val style: PlaylistCoverStyle) : CoverChoice
    data class Picked(val uri: Uri) : CoverChoice
}

/**
 * The colours a cover is drawn in, all taken from one hue - the top song's. [light] to [deep] is
 * the gradient, [accent] the flat shapes, [dark] the base under a blurred backdrop.
 */
@Immutable
class CoverPalette(
    val light: Int,
    val mid: Int,
    val deep: Int,
    val accent: Int,
    val dark: Int,
) {
    companion object {
        /** Builds the whole set from one colour, keeping saturation where it still looks good flat. */
        fun fromColor(color: Int): CoverPalette {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            val hue = hsl[0]
            // Grey art stays grey, like the reference frames; anything else is held to a range
            // that is neither washed out nor garish across a whole cover.
            val sat = if (hsl[1] < 0.12f) hsl[1] else hsl[1].coerceIn(0.38f, 0.80f)

            fun tone(saturation: Float, lightness: Float): Int =
                ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness))

            return CoverPalette(
                light = tone(sat * 0.55f, 0.94f),
                mid = tone(sat * 0.75f, 0.76f),
                deep = tone(sat, 0.52f),
                accent = tone(sat, 0.44f),
                dark = tone(sat * 0.55f, 0.07f),
            )
        }

        /** No song to take colours from yet: a steady colour of the playlist's own. */
        fun fromSeed(seed: String): CoverPalette {
            val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
            return fromColor(ColorUtils.HSLToColor(floatArrayOf(hue, 0.62f, 0.5f)))
        }

        /** The most characteristic colour of [artwork]. Needs a software bitmap; not for the main thread. */
        fun fromArtwork(artwork: Bitmap): CoverPalette {
            val palette = Palette.from(artwork).maximumColorCount(16).resizeBitmapArea(96 * 96).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.mutedSwatch
            return fromColor(swatch?.rgb ?: AColor.GRAY)
        }
    }
}

object PlaylistCoverGenerator {
    /** Edge of a stored cover, in px. Plenty for the biggest place one is shown. */
    const val ExportSize = 720

    private val InkDark = 0xFF0B0B0C.toInt()
    private val InkLight = 0xFFFFFFFF.toInt()

    /** Pixels the artwork is shrunk to for the backdrop - the shrinking is most of the blur. */
    private const val BackdropPixels = 48

    /** Richer and a good deal darker, so white text always has something to sit on. */
    private val BackdropFilter = ColorMatrixColorFilter(
        ColorMatrix().apply {
            setSaturation(1.25f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.62f, 0f, 0f, 0f, 0f,
                        0f, 0.62f, 0f, 0f, 0f,
                        0f, 0f, 0.62f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }
    )

    /**
     * Draws a cover. Cheap - a few gradients and a line of text - but still not for the main
     * thread when it is called per keystroke. [artwork] is only used by [PlaylistCoverStyle.BACKDROP].
     */
    fun render(
        style: PlaylistCoverStyle,
        title: String,
        palette: CoverPalette,
        artwork: Bitmap?,
        size: Int = ExportSize,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()

        val ink = when (style) {
            PlaylistCoverStyle.GRADIENT_TOP, PlaylistCoverStyle.GRADIENT_BOTTOM -> {
                drawGradient(canvas, s, palette)
                InkDark
            }
            PlaylistCoverStyle.BLOOM -> {
                drawGradient(canvas, s, palette)
                drawBloom(canvas, s, palette)
                InkDark
            }
            PlaylistCoverStyle.BACKDROP -> {
                drawBackdrop(canvas, s, palette, artwork)
                InkLight
            }
        }
        drawTitle(canvas, title, s, atBottom = style != PlaylistCoverStyle.GRADIENT_TOP, ink = ink)
        return bitmap
    }

    private fun drawGradient(canvas: Canvas, s: Float, palette: CoverPalette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, s, s,
            intArrayOf(palette.light, palette.mid, palette.deep),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, s, s, paint)
    }

    /**
     * Two circles from the middle of either edge: where they overlap the middle is filled, and
     * the gradient shows through the notches above and below it.
     */
    private fun drawBloom(canvas: Canvas, s: Float, palette: CoverPalette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, s,
            ColorUtils.blendARGB(palette.accent, AColor.WHITE, 0.12f),
            ColorUtils.blendARGB(palette.accent, AColor.BLACK, 0.18f),
            Shader.TileMode.CLAMP,
        )
        val radius = s * 0.56f
        canvas.drawCircle(0f, s * 0.5f, radius, paint)
        canvas.drawCircle(s, s * 0.5f, radius, paint)
    }

    private fun drawBackdrop(canvas: Canvas, s: Float, palette: CoverPalette, artwork: Bitmap?) {
        canvas.drawColor(palette.dark)
        if (artwork != null) {
            val soft = softened(artwork)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            paint.colorFilter = BackdropFilter
            canvas.drawBitmap(soft, null, RectF(0f, 0f, s, s), paint)
            soft.recycle()
        } else {
            drawGlows(canvas, s, palette)
        }
        // The dark gradient: the picture shows at the top and gives way to near-black at the foot,
        // where the title sits.
        val shade = Paint()
        shade.shader = LinearGradient(
            0f, 0f, 0f, s,
            intArrayOf(0x33000000, 0x66000000, (0xE6 shl 24) or (palette.dark and 0xFFFFFF)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, s, s, shade)
    }

    /** Stand-in for a backdrop when there is no artwork: soft glows of the palette on the dark base. */
    private fun drawGlows(canvas: Canvas, s: Float, palette: CoverPalette) {
        val spots = listOf(
            Triple(0.20f, 0.25f, palette.accent),
            Triple(0.85f, 0.40f, palette.deep),
            Triple(0.45f, 0.95f, palette.mid),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((x, y, color) in spots) {
            paint.shader = RadialGradient(
                x * s, y * s, s * 0.8f,
                ColorUtils.setAlphaComponent(color, 200),
                ColorUtils.setAlphaComponent(color, 0),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, s, s, paint)
        }
    }

    /** [src] cropped to a square, shrunk to [BackdropPixels] and blurred - drawn back up, it is a haze. */
    private fun softened(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val crop = Rect(
            (src.width - side) / 2,
            (src.height - side) / 2,
            (src.width + side) / 2,
            (src.height + side) / 2,
        )
        val tiny = Bitmap.createBitmap(BackdropPixels, BackdropPixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tiny)
        canvas.drawColor(AColor.BLACK)
        canvas.drawBitmap(src, crop, Rect(0, 0, BackdropPixels, BackdropPixels), Paint(Paint.FILTER_BITMAP_FLAG))
        boxBlur(tiny, radius = 5, passes = 3)
        return tiny
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        var from = IntArray(w * h)
        var into = IntArray(w * h)
        bitmap.getPixels(from, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurPass(from, into, w, h, radius, horizontal = true)
            blurPass(into, from, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(from, 0, w, 0, 0, w, h)
    }

    /** One running-sum box blur along every row (or column), edges clamped. Opaque pixels only. */
    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = 2 * radius + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int = if (horizontal) line * w + i else i * w + line

            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = src[at(i.coerceIn(0, length - 1))]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                dst[at(i)] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val entering = src[at((i + radius + 1).coerceAtMost(length - 1))]
                val leaving = src[at((i - radius).coerceAtLeast(0))]
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    /** The title, at the top or bottom left: up to three lines, ellipsized, sized to the cover. */
    private fun drawTitle(canvas: Canvas, title: String, s: Float, atBottom: Boolean, ink: Int) {
        val text = title.trim()
        if (text.isEmpty()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = s * 0.108f
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, (s * 0.78f).toInt())
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 0.98f)
            .build()
        val x = s * 0.09f
        val y = if (atBottom) s * 0.91f - layout.height else s * 0.09f
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}

/**
 * Where covers live: one small image file per playlist, in the app's own storage. A generated
 * cover's file name says what it was made from (style, top song, title), so it can be recognised
 * later - and redrawn when the top song or the title has moved on - without a column for it in the
 * database. The playlist's `thumbnailUrl` is simply the file.
 */
object PlaylistCoverStore {
    private const val DIR = "playlist_covers"
    private const val GENERATED = "gen"
    private const val PICKED = "img"

    class Parsed(val style: PlaylistCoverStyle, val sourceKey: String, val titleKey: String)

    fun sourceKey(sourceId: String?): String = hashKey(sourceId ?: "-")

    fun titleKey(title: String): String = hashKey(title.trim())

    private fun hashKey(value: String): String = (value.hashCode().toLong() and 0xFFFFFFFFL).toString(16)

    private fun safe(id: String): String = id.replace('.', '-')

    /** True for any cover this class wrote - generated or a picked picture. */
    fun isManaged(thumbnailUrl: String?): Boolean = thumbnailUrl != null && thumbnailUrl.contains("/$DIR/")

    /** What a generated cover was made from, or null for anything else (a picked picture, a remote image). */
    fun parse(thumbnailUrl: String?): Parsed? {
        val url = thumbnailUrl ?: return null
        if (!isManaged(url)) return null
        // gen . playlistId . style . sourceKey . titleKey . timestamp . webp
        val parts = url.substringAfterLast('/').split('.')
        if (parts.size != 7 || parts[0] != GENERATED) return null
        val style = PlaylistCoverStyle.fromKey(parts[2]) ?: return null
        return Parsed(style, parts[3], parts[4])
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun newFile(context: Context, vararg parts: String): File =
        File(directory(context), (parts.toList() + listOf(System.currentTimeMillis().toString(), "webp")).joinToString("."))

    suspend fun saveGenerated(
        context: Context,
        playlistId: String,
        style: PlaylistCoverStyle,
        sourceId: String?,
        title: String,
        bitmap: Bitmap,
    ): String = withContext(Dispatchers.IO) {
        val file = newFile(context, GENERATED, safe(playlistId), style.key, sourceKey(sourceId), titleKey(title))
        write(bitmap, file)
        Uri.fromFile(file).toString()
    }

    /** A picture from the photo picker, cropped to a square and shrunk to [PlaylistCoverGenerator.ExportSize]. */
    suspend fun savePicked(context: Context, playlistId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val size = PlaylistCoverGenerator.ExportSize
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size, size)
                .scale(Scale.FILL)
                .allowHardware(false)
                .build()
            val decoded = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
            val square = centerCropSquare(decoded, size)
            val file = newFile(context, PICKED, safe(playlistId))
            write(square, file)
            Uri.fromFile(file).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Removes every cover file of [playlistId] except the one [keepUrl] points to. */
    fun deleteStale(context: Context, playlistId: String, keepUrl: String?) {
        val keep = keepUrl?.substringAfterLast('/')
        val id = safe(playlistId)
        directory(context).listFiles()?.forEach { file ->
            val mine = file.name.startsWith("$GENERATED.$id.") || file.name.startsWith("$PICKED.$id.")
            if (mine && file.name != keep) file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun write(bitmap: Bitmap, file: File) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        file.outputStream().buffered().use { out -> bitmap.compress(format, 92, out) }
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }
}

/** The pieces together: from a choice to a stored cover, and keeping a generated one current. */
object PlaylistCovers {
    /** A song's artwork as a software bitmap for the palette and the backdrop, or null if it won't load. */
    suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(url.resize(256, 256))
                .size(256, 256)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** The palette of [artwork], or - with none - a steady one for [seed]. Computes; keep it off the main thread. */
    fun paletteFor(artwork: Bitmap?, seed: String): CoverPalette =
        artwork?.let(CoverPalette::fromArtwork) ?: CoverPalette.fromSeed(seed)

    /**
     * Makes and stores the cover for [choice], returning what to put in the playlist's thumbnail.
     * [topSongId] and [artworkUrl] are the playlist's top song, which a generated cover takes its
     * colours from; both null for a playlist with no songs yet.
     */
    suspend fun create(
        context: Context,
        playlistId: String,
        title: String,
        choice: CoverChoice,
        topSongId: String?,
        artworkUrl: String?,
    ): String? = when (choice) {
        is CoverChoice.Picked -> PlaylistCoverStore.savePicked(context, playlistId, choice.uri)
        is CoverChoice.Generated -> {
            val artwork = loadArtwork(context, artworkUrl)
            val bitmap = withContext(Dispatchers.Default) {
                PlaylistCoverGenerator.render(
                    style = choice.style,
                    title = title,
                    palette = paletteFor(artwork, playlistId),
                    artwork = artwork,
                )
            }
            PlaylistCoverStore.saveGenerated(context, playlistId, choice.style, topSongId, title, bitmap)
        }
    }

    /**
     * Redraws a generated cover whose top song or title has changed since it was made. Anything
     * else - a picture of the user's, a remote playlist's own image, no cover - is left alone.
     */
    suspend fun refreshIfStale(
        context: Context,
        database: MusicDatabase,
        playlist: PlaylistEntity,
        topSongId: String?,
        artworkUrl: String?,
    ) {
        val made = PlaylistCoverStore.parse(playlist.thumbnailUrl) ?: return
        if (made.sourceKey == PlaylistCoverStore.sourceKey(topSongId) &&
            made.titleKey == PlaylistCoverStore.titleKey(playlist.name)
        ) return

        val url = create(
            context = context,
            playlistId = playlist.id,
            title = playlist.name,
            choice = CoverChoice.Generated(made.style),
            topSongId = topSongId,
            artworkUrl = artworkUrl,
        ) ?: return
        database.query { update(playlist.copy(thumbnailUrl = url)) }
        withContext(Dispatchers.IO) { PlaylistCoverStore.deleteStale(context, playlist.id, url) }
    }
}
