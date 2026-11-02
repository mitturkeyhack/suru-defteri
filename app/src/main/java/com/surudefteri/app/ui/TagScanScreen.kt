package com.surudefteri.app.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.surudefteri.app.App
import com.surudefteri.app.data.Sheep
import com.surudefteri.app.tags.TagReader
import com.surudefteri.app.ui.theme.Wheat
import java.util.concurrent.Executors

/**
 * Küpe okutma: kamerayı küpeye tut, numara okununca kayıtlı koyunun kartı ya da yeni kayıt açılır.
 * @param onResult (kayıtlı koyunun id'si ya da null, okunan/elle girilen küpe no)
 */
@Composable
fun TagScanScreen(onBack: () -> Unit, onResult: (Long?, String) -> Unit) {
    CameraPermissionGate { TagScanContent(onBack, onResult) }
}

@Composable
private fun TagScanContent(onBack: () -> Unit, onResult: (Long?, String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dao = App.instance.db.dao()

    var seen by remember { mutableStateOf<String?>(null) }
    var stable by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<Sheep?>(null) }
    var manual by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val reader = TagReader(
            onStable = { tag -> main.execute { if (stable == null) stable = TagReader.normalize(tag) } },
            onSeen = { tag -> main.execute { seen = tag } },
        )
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, reader) }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
            }
        }, main)
        onDispose {
            provider?.unbindAll()
            reader.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(stable) {
        val tag = stable ?: return@LaunchedEffect
        existing = dao.sheepByTag(tag)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Nişan çerçevesi
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .height(140.dp)
                .border(3.dp, Wheat, RoundedCornerShape(20.dp))
        )

        Row(
            Modifier
                .statusBarsPadding()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White) }
            Text("Küpe okut", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val tag = stable
                if (tag == null) {
                    Text("Küpeyi çerçevenin içine al, numara kendiliğinden okunur.", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        seen?.let { "Okunuyor: $it" } ?: "Numara aranıyor…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { manual = true }) { Text("Numarayı elle yaz") }
                } else {
                    Text(tag, fontSize = 30.sp, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium)
                    val found = existing
                    Text(
                        if (found != null) "Bu koyun kayıtlı" + (found.name.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                        else "Bu küpe kayıtlı değil",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { stable = null; existing = null }, shape = RoundedCornerShape(14.dp)) {
                            Text("Tekrar okut")
                        }
                        Button(
                            onClick = { onResult(found?.id, tag) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(if (found != null) "Kartını aç" else "Yeni koyun ekle") }
                    }
                }
            }
        }
    }

    if (manual) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Küpe numarası") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(20) },
                    singleLine = true,
                    placeholder = { Text("Örn. TR031234567") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(enabled = TagReader.normalize(value).length >= 2, onClick = {
                    manual = false
                    stable = TagReader.normalize(value)
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Vazgeç") } },
        )
    }
}
