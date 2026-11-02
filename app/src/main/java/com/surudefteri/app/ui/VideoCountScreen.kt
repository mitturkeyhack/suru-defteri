package com.surudefteri.app.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surudefteri.app.App
import com.surudefteri.app.count.TrackView
import com.surudefteri.app.count.VideoCounter
import com.surudefteri.app.data.CountRecord
import com.surudefteri.app.data.Direction
import com.surudefteri.app.ui.theme.Meadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Çekilmiş videodan sayım. Daha doğru (hassas model, kare kare inceleme) ama birkaç dakika sürebilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCountScreen(expected: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = App.instance
    val savedDirection by app.prefs.direction.collectAsStateWithLifecycle()
    val includeSimilar by app.prefs.includeSimilar.collectAsStateWithLifecycle()

    var uri by remember { mutableStateOf<Uri?>(null) }
    var firstFrame by remember { mutableStateOf<Bitmap?>(null) }
    var direction by remember { mutableStateOf(savedDirection) }
    var linePosition by remember { mutableFloatStateOf(0.5f) }

    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var percent by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var tracks by remember { mutableStateOf<List<TrackView>>(emptyList()) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            uri = picked
            done = false
            count = 0
            error = null
        }
    }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        firstFrame = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, u)
                r.getFrameAtTime(0)
            } catch (e: Exception) {
                null
            } finally {
                r.release()
            }
        }
    }

    fun start() {
        val u = uri ?: return
        running = true
        done = false
        error = null
        app.prefs.setDirection(direction)
        job = scope.launch {
            val result = VideoCounter.count(context, u, direction, linePosition, includeSimilar) { p ->
                scope.launch {
                    percent = p.percent
                    count = p.count
                    if (p.frame != null) {
                        frame = p.frame
                        tracks = p.tracks
                    }
                }
            }
            running = false
            if (result == null) {
                error = "Video işlenemedi. Başka bir video dene."
            } else {
                count = result.count
                done = true
                showResult = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Videodan say") },
                navigationIcon = {
                    IconButton(onClick = {
                        job?.cancel()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (uri == null) {
                Text(
                    "Koyunların bir geçitten geçtiği videoyu seç. Telefon çekim sırasında sabit durmuşsa sonuç çok daha doğru olur.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                    Text("  Video seç")
                }
                Tips()
                return@Column
            }

            // Önizleme: ilk kare (ya da işlenen kare) + çizgi
            val shown = frame ?: firstFrame
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (shown != null) {
                    Box(
                        Modifier
                            .aspectRatio(shown.width.toFloat() / shown.height)
                            .pointerInput(direction, running) {
                                if (running) return@pointerInput
                                detectDragGestures { change, _ ->
                                    val p = if (direction.horizontal) change.position.x / size.width else change.position.y / size.height
                                    linePosition = p.coerceIn(0.15f, 0.85f)
                                }
                            },
                    ) {
                        Image(
                            shown.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                        CountOverlay(if (running || done) tracks else emptyList(), direction, linePosition, Modifier.fillMaxSize())
                    }
                }
            }

            if (!running && !done) {
                Text("Koyunlar hangi yöne yürüyor?", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Direction.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { d ->
                                FilterChip(
                                    selected = d == direction,
                                    onClick = { direction = d },
                                    label = { Text(d.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Text(
                    "Sarı çizgiyi koyunların kesin geçtiği yere sürükle (genelde geçidin ortası).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Başka video") }
                    Button(onClick = { start() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                        Text("Saymaya başla")
                    }
                }
            }

            if (running || done) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Meadow, lineHeight = 56.sp)
                    if (expected > 0) {
                        Text(" / $expected", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                if (running) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Meadow,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(
                        "Video inceleniyor · %$percent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { job?.cancel(); running = false }, shape = RoundedCornerShape(14.dp)) {
                        Text("Durdur")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { done = false; frame = null; tracks = emptyList() }, shape = RoundedCornerShape(14.dp)) {
                            Text("Tekrar say")
                        }
                        Button(onClick = { showResult = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                            Text("Kaydet")
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
                    app.db.dao().insertCount(CountRecord(counted = count, expected = expected, method = "video"))
                    showResult = false
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun Tips() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Doğru sayım için", style = MaterialTheme.typography.titleSmall)
        listOf(
            "Koyunlar dar bir geçitten, mümkünse tek ya da ikişer geçsin.",
            "Telefonu sabitle: bir direğe, taşa ya da sehpaya daya.",
            "Geçidi yandan ya da hafif yukarıdan çek; koyunlar kadrajdan baştan sona geçsin.",
            "Gün ışığında çek; karanlıkta doğruluk düşer.",
        ).forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .background(Meadow, RoundedCornerShape(50))
                )
                Text(tip, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            }
        }
    }
}
