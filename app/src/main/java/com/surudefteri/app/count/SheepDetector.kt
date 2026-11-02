package com.surudefteri.app.count

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Telefonda çalışan koyun tanıyıcı (Google MediaPipe, EfficientDet).
 * İnternet gerekmez: model uygulamanın içinde gelir.
 *
 * İki model var: hızlı (canlı kamera için) ve hassas (videodan sayım için, varsa).
 */
class SheepDetector private constructor(private val detector: ObjectDetector) : AutoCloseable {

    /** Bir karedeki koyunları 0..1 koordinatlarla döner. Zaman damgaları artan olmalı. */
    fun detect(source: Bitmap, timestampMs: Long): List<Box> {
        // MediaPipe ARGB_8888 bekler; video karelerinin bazıları RGB_565 gelebilir.
        val bitmap = if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
        val image = BitmapImageBuilder(bitmap).build()
        val result = detector.detectForVideo(image, timestampMs)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return result.detections().mapNotNull { d ->
            val r = d.boundingBox()
            val score = d.categories().firstOrNull()?.score() ?: 0f
            val box = Box(r.left / w, r.top / h, r.right / w, r.bottom / h, score)
            // Çok küçük (uzakta, yanlış) ya da tüm görüntüyü kaplayan kutuları at.
            if (box.width < 0.02f || box.height < 0.02f || box.width * box.height > 0.8f) null else box
        }.let { suppressOverlaps(it) }
    }

    override fun close() = detector.close()

    companion object {
        private const val TAG = "Suru/Detector"
        const val FAST_MODEL = "detector_fast.tflite"
        const val ACCURATE_MODEL = "detector_accurate.tflite"

        fun hasModel(context: Context, name: String): Boolean =
            try {
                context.assets.open(name).close()
                true
            } catch (e: Exception) {
                false
            }

        /**
         * @param accurate true: hassas model (yavaş, daha doğru). Yoksa hızlı modele düşer.
         * @param includeSimilar true: model koyunu bazen "inek" sanıyor; onları da koyun say.
         */
        fun create(context: Context, accurate: Boolean, includeSimilar: Boolean): SheepDetector? {
            val model = if (accurate && hasModel(context, ACCURATE_MODEL)) ACCURATE_MODEL else FAST_MODEL
            if (!hasModel(context, model)) {
                Log.e(TAG, "Model bulunamadı: $model")
                return null
            }
            return try {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(model).build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setMaxResults(40)
                    .setScoreThreshold(if (accurate) 0.30f else 0.35f)
                    .setCategoryAllowlist(if (includeSimilar) listOf("sheep", "cow") else listOf("sheep"))
                    .build()
                SheepDetector(ObjectDetector.createFromOptions(context, options))
            } catch (e: Exception) {
                Log.e(TAG, "Tanıyıcı başlatılamadı", e)
                null
            }
        }

        /** Aynı koyuna iki farklı sınıftan (koyun/inek) gelen çakışan kutuları teke indir. */
        private fun suppressOverlaps(boxes: List<Box>): List<Box> {
            val sorted = boxes.sortedByDescending { it.score }
            val kept = mutableListOf<Box>()
            for (b in sorted) {
                if (kept.none { it.iou(b) > 0.6f }) kept += b
            }
            return kept
        }
    }
}
