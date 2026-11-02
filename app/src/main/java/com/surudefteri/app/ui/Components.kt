package com.surudefteri.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.surudefteri.app.count.TrackView
import com.surudefteri.app.data.Direction
import com.surudefteri.app.ui.theme.Wheat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kamera izni yoksa ister; varsa içeriği gösterir. */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) {
        content()
    } else {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Sayım ve küpe okuma için kamera izni gerekiyor. Görüntüler telefondan dışarı çıkmaz.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(14.dp)) {
                    Text("İzin ver")
                }
            }
        }
    }
}

/**
 * Kamera/video görüntüsünün üstüne koyun kutularını ve sayım çizgisini çizer.
 * Sayılan koyunlar yeşil, henüz sayılmayanlar beyaz çerçeveli.
 */
@Composable
fun CountOverlay(
    tracks: List<TrackView>,
    direction: Direction,
    linePosition: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        tracks.forEach { t ->
            val color = if (t.counted) Color(0xFF4ADE80) else Color.White
            drawRect(
                color = color,
                topLeft = Offset(t.box.left * w, t.box.top * h),
                size = Size(t.box.width * w, t.box.height * h),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
        val stroke = 4.dp.toPx()
        if (direction.horizontal) {
            val x = linePosition * w
            drawLine(Wheat, Offset(x, 0f), Offset(x, h), stroke, pathEffect = dash)
            // Yön oku
            val y = h * 0.08f
            val dir = direction.forward
            drawLine(Wheat, Offset(x - 40f * dir, y), Offset(x + 40f * dir, y), stroke)
            drawLine(Wheat, Offset(x + 40f * dir, y), Offset(x + 22f * dir, y - 16f), stroke)
            drawLine(Wheat, Offset(x + 40f * dir, y), Offset(x + 22f * dir, y + 16f), stroke)
        } else {
            val y = linePosition * h
            drawLine(Wheat, Offset(0f, y), Offset(w, y), stroke, pathEffect = dash)
            val x = w * 0.08f
            val dir = direction.forward
            drawLine(Wheat, Offset(x, y - 40f * dir), Offset(x, y + 40f * dir), stroke)
            drawLine(Wheat, Offset(x, y + 40f * dir), Offset(x - 16f, y + 22f * dir), stroke)
            drawLine(Wheat, Offset(x, y + 40f * dir), Offset(x + 16f, y + 22f * dir), stroke)
        }
    }
}

@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

fun formatDate(time: Long): String = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("tr")).format(Date(time))

fun relativeDays(time: Long): String {
    val days = ((System.currentTimeMillis() - time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "bugün"
        days == 1 -> "dün"
        else -> "$days gün önce"
    }
}
