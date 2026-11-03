package com.surudefteri.app.count

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.surudefteri.app.CrashLog
import com.surudefteri.app.data.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * Çekilmiş bir videodan sayım. Canlıdan daha doğrudur: hassas model kullanılır ve
 * saniyede 10 kare incelenir (telefon yetişmek zorunda değil).
 */
object VideoCounter {

    data class Progress(
        val percent: Int,
        val count: Int,
        val frame: Bitmap?,
        val tracks: List<TrackView>,
    )

    data class Result(val count: Int, val seen: Int, val durationMs: Long)

    suspend fun count(
        context: Context,
        uri: Uri,
        direction: Direction,
        linePosition: Float,
        includeSimilar: Boolean,
        onProgress: (Progress) -> Unit,
    ): Result? = withContext(Dispatchers.Default) {
        val detector = SheepDetector.create(context, accurate = true, includeSimilar = includeSimilar)
            ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0) return@withContext null
            val counter = LineCounter(direction.horizontal, direction.forward, linePosition)

            val stepMs = 100L // saniyede 10 kare
            var t = 0L
            var lastPercent = -1
            while (t <= durationMs) {
                coroutineContext.ensureActive()
                val frame = grab(retriever, t)
                if (frame == null) {
                    t += stepMs
                    continue
                }
                val small = scaleDown(frame, MAX_SIDE)
                val boxes = detector.detect(small, t)
                val tracks = counter.update(boxes)
                val percent = ((t * 100) / durationMs).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(Progress(percent, counter.count, small, tracks))
                } else if (small !== frame) {
                    small.recycle()
                }
                if (small !== frame) frame.recycle()
                t += stepMs
            }
            onProgress(Progress(100, counter.count, null, emptyList()))
            Result(counter.count, counter.seen, durationMs)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            CrashLog.record(context, "Videodan sayım hatası", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            detector.close()
        }
    }

    /**
     * Kareyi doğrudan küçültülmüş olarak alır. 4K videoda tam boy kare ~33 MB tutar ve
     * art arda alınınca belleği bitirir; küçük alınca hem hızlı hem güvenli.
     */
    private fun grab(r: MediaMetadataRetriever, timeMs: Long): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, MAX_SIDE, MAX_SIDE)
            } else {
                r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        } catch (e: Exception) {
            null
        }

    private const val MAX_SIDE = 960

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }
}
