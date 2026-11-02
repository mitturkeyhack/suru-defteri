package com.surudefteri.app.count

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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

            val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            val stepMs = 100L // saniyede 10 kare
            var t = 0L
            var lastPercent = -1
            while (t <= durationMs) {
                coroutineContext.ensureActive()
                val frame = grab(retriever, t, durationMs, frameCount)
                if (frame == null) {
                    t += stepMs
                    continue
                }
                val small = scaleDown(frame, 960)
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
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            detector.close()
        }
    }

    private fun grab(r: MediaMetadataRetriever, timeMs: Long, durationMs: Long, frameCount: Int?): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= 28 && frameCount != null && frameCount > 0) {
                val index = ((timeMs.toDouble() / durationMs) * (frameCount - 1)).toInt().coerceIn(0, frameCount - 1)
                r.getFrameAtIndex(index)
            } else {
                r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        } catch (e: Exception) {
            null
        }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    }
}
