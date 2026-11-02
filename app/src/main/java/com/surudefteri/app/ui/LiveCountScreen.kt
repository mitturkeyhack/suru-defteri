package com.surudefteri.app.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surudefteri.app.App
import com.surudefteri.app.count.LineCounter
import com.surudefteri.app.count.SheepDetector
import com.surudefteri.app.count.TrackView
import com.surudefteri.app.data.CountRecord
import com.surudefteri.app.data.Direction
import com.surudefteri.app.ui.theme.Meadow
import com.surudefteri.app.ui.theme.Wheat
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Canlı kamera ile sayım: telefon sabit durur, koyunlar çizgiyi geçtikçe sayılır. */
@Composable
fun LiveCountScreen(expected: Int, onBack: () -> Unit) {
    CameraPermissionGate { LiveCountContent(expected, onBack) }
}

@Composable
private fun LiveCountContent(expected: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val app = App.instance
    val savedDirection by app.prefs.direction.collectAsStateWithLifecycle()
    val includeSimilar by app.prefs.includeSimilar.collectAsStateWithLifecycle()

    var direction by remember { mutableStateOf(savedDirection) }
    var linePosition by remember { mutableFloatStateOf(0.5f) }
    var count by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<TrackView>>(emptyList()) }
    var aspect by remember { mutableFloatStateOf(3f / 4f) }
    var fps by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    // Sayaç, yön değişince sıfırdan kurulur.
    val counter = remember(direction) { LineCounter(direction.horizontal, direction.forward, linePosition) }
    counter.linePosition = linePosition

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(direction, includeSimilar) {
        val executor = Executors.newSingleThreadExecutor()
        val detector = SheepDetector.create(context, accurate = false, includeSimilar = includeSimilar)
        if (detector == null) error = "Koyun tanıma modeli yüklenemedi."
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var lastFrame = 0L
        var lastTimestamp = 0L

        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            if (detector != null) {
                analysis.setAnalyzer(executor) { proxy ->
                    try {
                        val rotation = proxy.imageInfo.rotationDegrees
                        val raw = proxy.toBitmap()
                        val bitmap = rotate(raw, rotation)
                        val now = SystemClock.uptimeMillis()
                        val ts = if (now <= lastTimestamp) lastTimestamp + 1 else now
                        lastTimestamp = ts
                        val boxes = detector.detect(bitmap, ts)
                        val result = counter.update(boxes)
                        val a = bitmap.width.toFloat() / bitmap.height
                        val f = if (lastFrame > 0) 1000f / (now - lastFrame).coerceAtLeast(1) else 0f
                        lastFrame = now
                        ContextCompat.getMainExecutor(context).execute {
                            tracks = result
                            count = counter.count
                            aspect = a
                            fps = if (fps == 0f) f else fps * 0.8f + f * 0.2f
                        }
                    } catch (e: Exception) {
                        // tek kare hatası: sonraki kareyle devam
                    } finally {
                        proxy.close()
                    }
                }
            }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                error = "Kamera açılamadı."
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.execute { detector?.close() }
            executor.shutdown()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Üst çubuk
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
            Text("Canlı sayım", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val all = Direction.entries
                direction = all[(all.indexOf(direction) + 1) % all.size]
                app.prefs.setDirection(direction)
                count = 0
            }) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Wheat)
                Text("  ${direction.label}", color = Wheat)
            }
        }

        // Kamera + çizim
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .aspectRatio(aspect)
                    .pointerInput(direction) {
                        detectDragGestures { change, _ ->
                            val p = if (direction.horizontal) change.position.x / size.width else change.position.y / size.height
                            linePosition = p.coerceIn(0.15f, 0.85f)
                        }
                    },
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                CountOverlay(tracks, direction, linePosition, Modifier.fillMaxSize())
            }
            error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            Text(
                "Çizgiyi parmağınla kaydırabilirsin · ${"%.0f".format(fps)} kare/sn",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Alt panel
        Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Meadow, lineHeight = 64.sp)
                    if (expected > 0) {
                        Text(
                            " / $expected",
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = { counter.adjust(-1); count = counter.count }) { Text("−1", fontSize = 18.sp) }
                    Spacer(Modifier.padding(4.dp))
                    FilledTonalButton(onClick = { counter.adjust(+1); count = counter.count }) { Text("+1", fontSize = 18.sp) }
                }
                if (expected > 0) {
                    LinearProgressIndicator(
                        progress = { (count.toFloat() / expected).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Meadow,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { counter.reset(); count = 0; tracks = emptyList() },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Text(" Sıfırla")
                    }
                    Button(
                        onClick = { showResult = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Bitir ve kaydet") }
                }
            }
        }
    }

    if (showResult) {
        CountResultDialog(
            counted = count,
            expected = expected,
            onDismiss = { showResult = false },
            onSave = {
                scope.launch {
                    app.db.dao().insertCount(CountRecord(counted = count, expected = expected, method = "canli"))
                    showResult = false
                    onBack()
                }
            },
        )
    }
}

/** Sayım sonucu: tam mı, eksik mi? */
@Composable
fun CountResultDialog(counted: Int, expected: Int, onDismiss: () -> Unit, onSave: () -> Unit) {
    val diff = expected - counted
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                when {
                    expected <= 0 -> "$counted koyun sayıldı"
                    diff == 0 -> "Tam! $counted / $expected"
                    diff > 0 -> "$diff eksik"
                    else -> "${-diff} fazla sayıldı"
                }
            )
        },
        text = {
            Text(
                when {
                    expected <= 0 -> "Beklenen sürü sayısını ana ekrandan girersen eksik/fazla hesaplanır."
                    diff == 0 -> "Sürünün tamamı sayıldı."
                    diff > 0 -> "$counted koyun sayıldı, $expected bekleniyordu. Koyunlar üst üste geçtiyse sayı eksik çıkabilir; emin değilsen tekrar say."
                    else -> "$counted sayıldı, $expected bekleniyordu. Geri dönen ya da iki kez geçen koyun olmuş olabilir."
                }
            )
        },
        confirmButton = { Button(onClick = onSave) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Devam et") } },
    )
}

private fun rotate(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
