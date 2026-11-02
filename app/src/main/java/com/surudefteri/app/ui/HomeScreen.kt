package com.surudefteri.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surudefteri.app.App
import com.surudefteri.app.data.CountRecord
import com.surudefteri.app.data.Direction
import com.surudefteri.app.ui.theme.Alert
import com.surudefteri.app.ui.theme.Meadow
import com.surudefteri.app.ui.theme.MeadowDark
import com.surudefteri.app.ui.theme.MeadowSoft
import com.surudefteri.app.ui.theme.Wheat
import com.surudefteri.app.ui.theme.WheatSoft
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    expected: Int,
    onLiveCount: () -> Unit,
    onVideoCount: () -> Unit,
    onSheepList: () -> Unit,
    onScan: () -> Unit,
) {
    val app = App.instance
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val activeCount by remember { dao.activeCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val counts by remember { dao.counts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var showSettings by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CountRecord?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sürü Defteri", style = MaterialTheme.typography.headlineMedium)
                    Text("İnternetsiz sürü takibi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, contentDescription = "Ayarlar") }
            }
        }

        item { HerdCard(expected, activeCount, counts.firstOrNull(), onEditExpected = { showSettings = true }) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction(
                    icon = Icons.Rounded.Videocam,
                    title = "Canlı say",
                    subtitle = "Kamerayı geçide tut",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onLiveCount,
                )
                BigAction(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "Videodan say",
                    subtitle = "Daha hassas",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onVideoCount,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction(Icons.Rounded.Pets, "Koyunlar", if (activeCount > 0) "$activeCount kayıt" else "Kayıt yok", Modifier.weight(1f), onSheepList)
                SmallAction(Icons.Rounded.QrCodeScanner, "Küpe okut", "Kartı aç / ekle", Modifier.weight(1f), onScan)
            }
        }

        if (counts.isNotEmpty()) {
            item {
                Text(
                    "SAYIM GEÇMİŞİ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(counts.take(30), key = { it.id }) { record ->
                Box(Modifier.combinedClickable(onClick = {}, onLongClick = { deleteTarget = record })) {
                    CountRow(record)
                }
            }
        }
    }

    if (showSettings) SettingsDialog(expected = app.prefs.expected.value, hasCards = activeCount > 0) { showSettings = false }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Bu sayım silinsin mi?") },
            text = { Text("${record.counted} / ${record.expected} · ${formatDate(record.time)}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.deleteCount(record.id) }
                    deleteTarget = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun HerdCard(expected: Int, activeCount: Int, last: CountRecord?, onEditExpected: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Meadow, MeadowDark)), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SÜRÜ", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (expected > 0) "$expected" else "—",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 56.sp,
                )
                Text(
                    "  koyun",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (expected <= 0) {
                Text(
                    "Sürü sayısını gir",
                    style = MaterialTheme.typography.labelLarge,
                    color = Wheat,
                    modifier = Modifier.combinedClickableCompat(onEditExpected),
                )
            } else {
                Text(
                    if (activeCount > 0) "Kartlardaki sürüdeki koyun sayısı" else "Elle girilen sürü sayısı",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            if (last != null) {
                val diff = last.expected - last.counted
                Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Son sayım · ${relativeDays(last.time)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                            Text(
                                "${last.counted} / ${last.expected}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                        }
                        when {
                            last.expected <= 0 -> Unit
                            diff == 0 -> Pill("Tam ✓", WheatSoft, MeadowDark)
                            diff > 0 -> Pill("$diff eksik", Color.White, Alert)
                            else -> Pill("${-diff} fazla", Color.White, MeadowDark)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

@Composable
private fun BigAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (primary) Wheat else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(128.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(if (primary) Color.White.copy(alpha = 0.55f) else MeadowSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MeadowDark)
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = Meadow)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CountRow(record: CountRecord) {
    val diff = record.expected - record.counted
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (record.expected > 0) "${record.counted} / ${record.expected}" else "${record.counted} koyun",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatDate(record.time) + " · " + if (record.method == "video") "videodan" else "canlı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                record.expected <= 0 -> Unit
                diff == 0 -> Pill("Tam", MeadowSoft, MeadowDark)
                diff > 0 -> Pill("$diff eksik", Color(0xFFFBE3E0), Alert)
                else -> Pill("${-diff} fazla", WheatSoft, MeadowDark)
            }
        }
    }
}

@Composable
private fun SettingsDialog(expected: Int, hasCards: Boolean, onDismiss: () -> Unit) {
    val prefs = App.instance.prefs
    var value by remember { mutableStateOf(if (expected > 0) expected.toString() else "") }
    val direction by prefs.direction.collectAsStateWithLifecycle()
    val similar by prefs.includeSimilar.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Ayarlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit).take(5) },
                    label = { Text("Sürüdeki koyun sayısı") },
                    supportingText = {
                        Text(if (hasCards) "Koyun kartı varken kartlardaki sayı kullanılır." else "Sayımda \"kaç eksik\" bunun üzerinden hesaplanır.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("Varsayılan yön", style = MaterialTheme.typography.titleSmall)
                Direction.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { d ->
                            FilterChip(selected = d == direction, onClick = { prefs.setDirection(d) }, label = { Text(d.label) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Benzer hayvanları da say", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Model bazen koyunu \"inek\" sanıyor. Sürüde inek yoksa açık kalsın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = similar, onCheckedChange = { prefs.setIncludeSimilar(it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.setExpected(value.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
