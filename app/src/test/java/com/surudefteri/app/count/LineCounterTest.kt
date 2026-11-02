package com.surudefteri.app.count

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LineCounterTest {

    private fun box(cx: Float, cy: Float, w: Float = 0.12f, h: Float = 0.15f) =
        Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    /** Tek sıra yürüyen sürü; karelerin bir kısmında tespit kaçırılıyor. */
    private fun runSingleFile(
        sheep: Int,
        speed: Float,
        spacing: Float,
        dropout: Double,
        counter: LineCounter,
        leftToRight: Boolean = true,
        seed: Int = 7,
    ): Int {
        val rnd = Random(seed)
        val frames = ((1.4f + sheep * spacing) / speed).toInt() + 20
        for (f in 0 until frames) {
            val dets = mutableListOf<Box>()
            for (i in 0 until sheep) {
                val start = -0.2f - i * spacing
                var x = start + f * speed
                if (!leftToRight) x = 1f - x
                if (x < -0.05f || x > 1.05f) continue
                if (rnd.nextDouble() < dropout) continue
                val y = 0.5f + (rnd.nextFloat() - 0.5f) * 0.04f // hafif titreme
                dets += box(x + (rnd.nextFloat() - 0.5f) * 0.01f, y)
            }
            counter.update(dets)
        }
        return counter.count
    }

    @Test
    fun singleFile_allCounted() {
        val c = LineCounter(horizontal = true, forward = +1)
        assertEquals(30, runSingleFile(30, speed = 0.04f, spacing = 0.22f, dropout = 0.0, counter = c))
    }

    @Test
    fun singleFile_withMissedDetections() {
        val c = LineCounter(horizontal = true, forward = +1)
        assertEquals(30, runSingleFile(30, speed = 0.04f, spacing = 0.22f, dropout = 0.25, counter = c))
    }

    @Test
    fun rightToLeft() {
        val c = LineCounter(horizontal = true, forward = -1)
        assertEquals(12, runSingleFile(12, speed = 0.05f, spacing = 0.25f, dropout = 0.1, counter = c, leftToRight = false))
    }

    @Test
    fun wrongDirection_notCounted() {
        val c = LineCounter(horizontal = true, forward = +1)
        assertEquals(0, runSingleFile(5, speed = 0.05f, spacing = 0.3f, dropout = 0.0, counter = c, leftToRight = false))
    }

    @Test
    fun turnsBack_isUncounted() {
        val c = LineCounter(horizontal = true, forward = +1)
        // Çizgiyi geçer, sonra geri döner.
        val path = (0..20).map { 0.2f + it * 0.04f } + (0..20).map { 1.0f - it * 0.04f }
        for (x in path) c.update(listOf(box(x, 0.5f)))
        assertEquals(0, c.count)
    }

    @Test
    fun jitterOnLine_countedOnce() {
        val c = LineCounter(horizontal = true, forward = +1)
        val path = (0..10).map { 0.2f + it * 0.03f } +
            List(30) { i -> if (i % 2 == 0) 0.52f else 0.48f } +
            (0..10).map { 0.55f + it * 0.04f }
        for (x in path) c.update(listOf(box(x, 0.5f)))
        assertEquals(1, c.count)
    }

    @Test
    fun twoSideBySide() {
        val c = LineCounter(horizontal = true, forward = +1)
        for (f in 0..40) {
            val x = 0.05f + f * 0.03f
            c.update(listOf(box(x, 0.3f), box(x + 0.02f, 0.7f)))
        }
        assertEquals(2, c.count)
    }

    @Test
    fun verticalMovement() {
        val c = LineCounter(horizontal = false, forward = +1)
        for (f in 0..40) {
            val y = 0.05f + f * 0.03f
            c.update(listOf(box(0.4f, y), box(0.6f, y - 0.3f)))
        }
        assertEquals(2, c.count)
    }

    @Test
    fun manualAdjust() {
        val c = LineCounter(horizontal = true, forward = +1)
        c.adjust(+2)
        c.adjust(-1)
        c.adjust(-5)
        assertEquals(0, c.count)
    }
}
