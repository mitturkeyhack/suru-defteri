package com.surudefteri.app.count

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Tek bir karede bulunan koyun kutusu (0..1 arası, görüntüye göre). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float = 1f) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun iou(o: Box): Float {
        val ix = max(0f, minOf(right, o.right) - maxOf(left, o.left))
        val iy = max(0f, minOf(bottom, o.bottom) - maxOf(top, o.top))
        val inter = ix * iy
        val union = width * height + o.width * o.height - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/** Ekrana çizmek için takip edilen koyun. */
data class TrackView(val id: Int, val box: Box, val counted: Boolean)

/**
 * Koyunları kareden kareye takip edip sayım çizgisini geçenleri sayar.
 *
 * - Her koyuna bir takip numarası verilir; bir sonraki karede en yakın / en çok örtüşen kutuyla eşleşir.
 * - Hız tahmini yapılır, böylece birkaç kare kaçırılsa bile koyun kaybolmaz.
 * - Çizgiyi ileri yönde geçen her koyun +1; geri dönen koyun -1 (iki kez sayılmaz).
 * - Çizginin iki yanında ince bir "tampon" bölge vardır: çizgi üzerinde titreyen koyun tekrar tekrar sayılmaz.
 *
 * Android'den bağımsızdır; birim testleriyle doğrulanır.
 */
class LineCounter(
    /** true: koyunlar yatay yürüyor (dikey çizgi), false: dikey yürüyor (yatay çizgi). */
    private val horizontal: Boolean,
    /** +1: soldan sağa / yukarıdan aşağı ileri; -1: tersi. */
    private val forward: Int,
    /** Çizginin yeri (0..1). */
    var linePosition: Float = 0.5f,
    private val band: Float = 0.03f,
    private val maxMissedFrames: Int = 10,
) {
    private class Track(
        val id: Int,
        var box: Box,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var missed: Int = 0,
        var side: Int = 0,
        var counted: Boolean = false,
        var hits: Int = 1,
    )

    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    var count: Int = 0
        private set

    /** Toplam görülen farklı koyun (bilgi amaçlı). */
    var seen: Int = 0
        private set

    fun reset() {
        tracks.clear()
        count = 0
        seen = 0
        nextId = 1
    }

    /** Elle düzeltme (+1 / -1). */
    fun adjust(delta: Int) {
        count = (count + delta).coerceAtLeast(0)
    }

    /** Yeni bir karenin tespitlerini işler; ekrana çizilecek takipleri döner. */
    fun update(detections: List<Box>): List<TrackView> {
        // 1) Mevcut takiplerin konumunu hızlarına göre tahmin et.
        val predicted = tracks.map { t ->
            Box(t.box.left + t.vx, t.box.top + t.vy, t.box.right + t.vx, t.box.bottom + t.vy)
        }

        // 2) Eşleştir: önce çok örtüşenler, sonra yakın olanlar (açgözlü, en iyi eşleşmeden başlayarak).
        data class Pair3(val ti: Int, val di: Int, val cost: Float)
        val candidates = mutableListOf<Pair3>()
        for ((ti, p) in predicted.withIndex()) {
            for ((di, d) in detections.withIndex()) {
                val iou = p.iou(d)
                val dist = hypot(p.cx - d.cx, p.cy - d.cy)
                val size = max(max(p.width, p.height), max(d.width, d.height))
                val gate = size * 0.9f
                if (iou > 0.1f || dist < gate) {
                    // Düşük maliyet daha iyi.
                    val cost = (1f - iou) + dist / max(size, 1e-3f)
                    candidates += Pair3(ti, di, cost)
                }
            }
        }
        candidates.sortBy { it.cost }
        val usedT = BooleanArray(tracks.size)
        val usedD = BooleanArray(detections.size)
        for (c in candidates) {
            if (usedT[c.ti] || usedD[c.di]) continue
            usedT[c.ti] = true
            usedD[c.di] = true
            val t = tracks[c.ti]
            val d = detections[c.di]
            // Hızı yumuşatarak güncelle.
            t.vx = 0.6f * (d.cx - t.box.cx) + 0.4f * t.vx
            t.vy = 0.6f * (d.cy - t.box.cy) + 0.4f * t.vy
            t.box = d
            t.missed = 0
            t.hits++
            checkCrossing(t)
        }

        // 3) Eşleşmeyen takipler: kayıp sayacını artır, tahmini konumla ilerlet.
        for ((ti, t) in tracks.withIndex()) {
            if (usedT[ti]) continue
            t.missed++
            t.box = predicted[ti]
            t.vx *= 0.8f
            t.vy *= 0.8f
        }
        tracks.removeAll { it.missed > maxMissedFrames }

        // 4) Eşleşmeyen tespitler: yeni koyun.
        for ((di, d) in detections.withIndex()) {
            if (usedD[di]) continue
            val t = Track(nextId++, d)
            t.side = sideOf(d)
            tracks += t
            seen++
        }

        return tracks.filter { it.missed == 0 }.map { TrackView(it.id, it.box, it.counted) }
    }

    private fun axis(b: Box): Float = if (horizontal) b.cx else b.cy

    /** -1: başlangıç tarafı, +1: varış tarafı, 0: çizgi üzerinde (tampon bölge). */
    private fun sideOf(b: Box): Int {
        val pos = axis(b)
        val raw = when {
            pos < linePosition - band -> -1
            pos > linePosition + band -> 1
            else -> 0
        }
        return raw * forward
    }

    private fun checkCrossing(t: Track) {
        val s = sideOf(t.box)
        if (s == 0) return // tampon bölgede: önceki tarafı koru
        val previous = t.side
        t.side = s
        if (previous == -1 && s == 1 && !t.counted) {
            count++
            t.counted = true
        } else if (previous == 1 && s == -1 && t.counted) {
            // Koyun geri döndü.
            count = (count - 1).coerceAtLeast(0)
            t.counted = false
        } else if (previous == 0 && s == 1 && !t.counted && t.hits >= 3 && abs(velocityAlongAxis(t)) > 0f &&
            velocityAlongAxis(t) * forward > 0
        ) {
            // Takip çizginin üstünde başladıysa ve ileri yönde hareket ederek çıktıysa yine say.
            count++
            t.counted = true
        }
    }

    private fun velocityAlongAxis(t: Track): Float = if (horizontal) t.vx else t.vy
}
