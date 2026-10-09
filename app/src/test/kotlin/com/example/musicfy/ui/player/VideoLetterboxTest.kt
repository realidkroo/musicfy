package com.example.musicfy.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLetterboxTest {
    private val size = 144

    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** A frame with [top]/[bottom] rows and [left]/[right] columns of black around [picture]. */
    private fun frame(
        top: Int = 0,
        bottom: Int = 0,
        left: Int = 0,
        right: Int = 0,
        barLevel: Int = 0,
        picture: (x: Int, y: Int) -> Int = { _, _ -> 128 },
    ): IntArray = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        val inBar = y < top || y >= size - bottom || x < left || x >= size - right
        gray(if (inBar) barLevel else picture(x, y))
    }

    private fun insetOf(reading: AxisReading): Float = (reading as AxisReading.Inset).inset

    @Test
    fun `finds top and bottom bars and crops one line past them`() {
        val reading = VideoLetterbox.analyze(frame(top = 20, bottom = 20), size, size)

        assertEquals(21f / size, insetOf(reading.vertical), 0.0001f)
        assertEquals(0f, insetOf(reading.horizontal), 0.0001f)
    }

    @Test
    fun `finds side bars`() {
        val reading = VideoLetterbox.analyze(frame(left = 18, right = 18), size, size)

        assertEquals(19f / size, insetOf(reading.horizontal), 0.0001f)
        assertEquals(0f, insetOf(reading.vertical), 0.0001f)
    }

    @Test
    fun `finds bars on all four sides`() {
        val reading = VideoLetterbox.analyze(frame(top = 12, bottom = 12, left = 10, right = 10), size, size)

        assertEquals(13f / size, insetOf(reading.vertical), 0.0001f)
        assertEquals(11f / size, insetOf(reading.horizontal), 0.0001f)
    }

    @Test
    fun `full frame picture has no bars`() {
        val reading = VideoLetterbox.analyze(frame(), size, size)

        assertEquals(0f, insetOf(reading.vertical), 0.0001f)
        assertEquals(0f, insetOf(reading.horizontal), 0.0001f)
    }

    @Test
    fun `dark grey bars still count`() {
        val reading = VideoLetterbox.analyze(frame(top = 20, bottom = 20, barLevel = 12), size, size)

        assertEquals(21f / size, insetOf(reading.vertical), 0.0001f)
    }

    @Test
    fun `a black frame says nothing`() {
        val reading = VideoLetterbox.analyze(IntArray(size * size) { gray(0) }, size, size)

        assertEquals(AxisReading.Unknown, reading.vertical)
        assertEquals(AxisReading.Unknown, reading.horizontal)
    }

    @Test
    fun `an empty texture says nothing`() {
        val reading = VideoLetterbox.analyze(IntArray(size * size), size, size)

        assertEquals(AxisReading.Unknown, reading.vertical)
    }

    @Test
    fun `a dark scene fading into the bars says nothing`() {
        // Black bars, then picture that only brightens gradually: a night scene, not a hard edge.
        val pixels = frame(top = 20, bottom = 20) { _, y ->
            val fromEdge = minOf(y - 20, size - 21 - y)
            (fromEdge * 2).coerceIn(0, 90)
        }
        val reading = VideoLetterbox.analyze(pixels, size, size)

        assertEquals(AxisReading.Unknown, reading.vertical)
    }

    @Test
    fun `dark sky above bright ground is not a letterbox`() {
        val pixels = frame { _, y -> if (y < 40) 0 else 150 }
        val reading = VideoLetterbox.analyze(pixels, size, size)

        assertEquals(0f, insetOf(reading.vertical), 0.0001f)
    }

    @Test
    fun `lopsided black at both edges is not trusted`() {
        val reading = VideoLetterbox.analyze(frame(top = 40, bottom = 8), size, size)

        assertEquals(AxisReading.Unknown, reading.vertical)
    }

    @Test
    fun `a channel logo inside the bottom bar does not cut the bar short`() {
        val pixels = frame(top = 20, bottom = 20)
        for (y in size - 12 until size - 6) {
            for (x in size - 20 until size - 4) pixels[y * size + x] = gray(255)
        }
        val reading = VideoLetterbox.analyze(pixels, size, size)

        assertEquals(21f / size, insetOf(reading.vertical), 0.0001f)
    }

    @Test
    fun `thin edge lines are ignored`() {
        val reading = VideoLetterbox.analyze(frame(top = 1, bottom = 1), size, size)

        assertEquals(0f, insetOf(reading.vertical), 0.0001f)
    }

    @Test
    fun `bars too tall to be real are not trusted`() {
        val reading = VideoLetterbox.analyze(frame(top = 64, bottom = 64), size, size)

        assertEquals(AxisReading.Unknown, reading.vertical)
    }

    @Test
    fun `stabilizer waits for agreeing samples before zooming in`() {
        val stabilizer = LetterboxStabilizer()
        val bars = AxisReading.Inset(0.12f)

        assertFalse(stabilizer.offer(bars, 0))
        assertFalse(stabilizer.offer(bars, 300))
        assertTrue(stabilizer.offer(bars, 600))
        assertEquals(0.12f, stabilizer.inset, 0.0001f)
    }

    @Test
    fun `stabilizer zooms out once bars are gone for two samples`() {
        val stabilizer = LetterboxStabilizer()
        val bars = AxisReading.Inset(0.12f)
        val none = AxisReading.Inset(0f)
        repeat(3) { stabilizer.offer(bars, it * 300L) }

        assertFalse(stabilizer.offer(none, 900))
        assertTrue(stabilizer.offer(none, 1200))
        assertEquals(0f, stabilizer.inset, 0.0001f)
    }

    @Test
    fun `one odd frame never moves the zoom`() {
        val stabilizer = LetterboxStabilizer()
        val bars = AxisReading.Inset(0.12f)
        repeat(3) { stabilizer.offer(bars, it * 300L) }

        assertFalse(stabilizer.offer(AxisReading.Inset(0f), 900))
        assertFalse(stabilizer.offer(bars, 1200))
        assertFalse(stabilizer.offer(AxisReading.Inset(0f), 1500))
        assertEquals(0.12f, stabilizer.inset, 0.0001f)
    }

    @Test
    fun `unknown readings hold the zoom`() {
        val stabilizer = LetterboxStabilizer()
        val bars = AxisReading.Inset(0.12f)
        repeat(3) { stabilizer.offer(bars, it * 300L) }

        repeat(10) { assertFalse(stabilizer.offer(AxisReading.Unknown, 900L + it * 300L)) }
        assertEquals(0.12f, stabilizer.inset, 0.0001f)
    }

    @Test
    fun `old samples expire across a long unknown stretch`() {
        val stabilizer = LetterboxStabilizer()
        val bars = AxisReading.Inset(0.12f)
        stabilizer.offer(bars, 0)
        stabilizer.offer(bars, 300)

        // A long fade, then one more bar sample: the two from before the fade no longer count.
        assertFalse(stabilizer.offer(bars, 10_000))
        assertEquals(0f, stabilizer.inset, 0.0001f)
    }

    @Test
    fun `jitter within tolerance does not move the zoom`() {
        val stabilizer = LetterboxStabilizer()
        repeat(3) { stabilizer.offer(AxisReading.Inset(0.12f), it * 300L) }

        repeat(3) { assertFalse(stabilizer.offer(AxisReading.Inset(0.125f), 900L + it * 300L)) }
    }

    @Test
    fun `no bars gives a plain centre crop`() {
        val t = videoCropTransform(1000f, 1000f, 16f / 9f, 0f, 0f)

        // A 16:9 video in a square view fills the height and overflows the sides equally.
        assertEquals(16f / 9f, t.scaleX, 0.0001f)
        assertEquals(1f, t.scaleY, 0.0001f)
        assertEquals((1000f - 1000f * 16f / 9f) / 2f, t.translationX, 0.01f)
        assertEquals(0f, t.translationY, 0.01f)
    }

    @Test
    fun `top and bottom bars zoom the picture to fill the view`() {
        val view = 1000f
        val inset = 0.12f
        val t = videoCropTransform(view, view, 16f / 9f, 0f, inset)

        // The picture between the bars must exactly cover the view's height...
        val frameHeight = view * t.scaleY
        val pictureTop = t.translationY + frameHeight * inset
        val pictureBottom = t.translationY + frameHeight * (1f - inset)
        assertEquals(0f, pictureTop, 0.01f)
        assertEquals(view, pictureBottom, 0.01f)
        // ...and keep the video's real aspect.
        assertEquals(16f / 9f, (view * t.scaleX) / frameHeight, 0.0001f)
    }

    @Test
    fun `side bars already cropped away do not zoom further`() {
        // A 4:3 picture pillarboxed in a 16:9 frame, shown in a square: the crop already hides them.
        val plain = videoCropTransform(1000f, 1000f, 16f / 9f, 0f, 0f)
        val withBars = videoCropTransform(1000f, 1000f, 16f / 9f, 0.125f, 0f)

        assertEquals(plain.scaleX, withBars.scaleX, 0.0001f)
        assertEquals(plain.scaleY, withBars.scaleY, 0.0001f)
    }

    @Test
    fun `an empty view is left alone`() {
        assertEquals(VideoTransform.Identity, videoCropTransform(0f, 0f, 16f / 9f, 0.1f, 0.1f))
    }
}
