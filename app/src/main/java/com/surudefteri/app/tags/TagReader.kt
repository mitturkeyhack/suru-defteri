package com.surudefteri.app.tags

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Küpedeki numarayı kameradan okur (ML Kit, model uygulamanın içinde — internetsiz).
 * Aynı numara üst üste birkaç karede okununca "kesin" sayılır; yanlış okumaları azaltır.
 */
class TagReader(private val onStable: (String) -> Unit, private val onSeen: (String?) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var last: String? = null
    private var streak = 0
    @Volatile
    private var busy = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || busy) {
            proxy.close()
            return
        }
        busy = true
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val tag = bestCandidate(text.text)
                onSeen(tag)
                if (tag != null && tag == last) {
                    streak++
                    if (streak >= STABLE_FRAMES) onStable(tag)
                } else {
                    last = tag
                    streak = if (tag != null) 1 else 0
                }
            }
            .addOnCompleteListener {
                busy = false
                proxy.close()
            }
    }

    fun close() = recognizer.close()

    companion object {
        private const val STABLE_FRAMES = 3

        /** Metindeki en olası küpe numarası: "TR" önekli ya da 4–15 haneli en uzun rakam dizisi. */
        fun bestCandidate(raw: String): String? {
            val upper = raw.uppercase()
                .replace('O', '0').replace('I', '1').replace('L', '1')
            val regex = Regex("""(TR)?[\s\-.]?((?:\d[\s\-.]?){4,15})""")
            return regex.findAll(upper)
                .map { m -> (m.groupValues[1] + m.groupValues[2]).filter { it.isLetterOrDigit() } }
                .filter { it.count(Char::isDigit) >= 4 }
                .maxByOrNull { it.length }
        }

        /** Kayıt için tek biçim: büyük harf, boşluksuz. */
        fun normalize(tag: String): String = tag.uppercase().filter { it.isLetterOrDigit() }
    }
}
