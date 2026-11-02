package com.surudefteri.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.surudefteri.app.App
import com.surudefteri.app.data.Sheep
import com.surudefteri.app.tags.TagReader
import com.surudefteri.app.ui.theme.Meadow
import com.surudefteri.app.ui.theme.MeadowSoft
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

private fun statusLabel(status: String) = when (status) {
    Sheep.STATUS_SOLD -> "Satıldı"
    Sheep.STATUS_DEAD -> "Öldü"
    else -> "Sürüde"
}

private fun ageText(birthYear: Int?): String? {
    birthYear ?: return null
    val age = Calendar.getInstance().get(Calendar.YEAR) - birthYear
    return if (age <= 0) "bu yıl doğdu" else "$age yaşında"
}

/** Koyun listesi: arama + duruma göre süzme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheepListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onScan: () -> Unit) {
    val sheep by remember { App.instance.db.dao().allSheep() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Sheep.STATUS_ACTIVE) }

    val shown = sheep.filter { s ->
        (filter == "hepsi" || s.status == filter) &&
            (query.isBlank() || s.tag.contains(query.uppercase()) || s.name.contains(query, ignoreCase = true))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Koyunlar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                text = { Text("Küpe okut / ekle") },
                containerColor = Meadow,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Küpe no ya da isim ara") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Sheep.STATUS_ACTIVE to "Sürüde", Sheep.STATUS_SOLD to "Satılan", Sheep.STATUS_DEAD to "Ölen", "hepsi" to "Hepsi")
                        .forEach { (key, label) ->
                            FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                        }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (sheep.isEmpty()) "Henüz koyun kaydı yok. Sağ alttaki düğmeyle küpeyi okutarak ilk koyunu ekle."
                        else "Bu aramaya uyan koyun yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { s -> SheepRow(s) { onOpen(s.id) } }
        }
    }
}

@Composable
private fun SheepRow(sheep: Sheep, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheepAvatar(sheep.photoPath, 48)
            Column(Modifier.weight(1f)) {
                Text(sheep.tag, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                val details = listOfNotNull(
                    sheep.name.takeIf { it.isNotBlank() },
                    if (sheep.sex == Sheep.SEX_MALE) "Koç" else "Dişi",
                    ageText(sheep.birthYear),
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sheep.status != Sheep.STATUS_ACTIVE) {
                Pill(statusLabel(sheep.status), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SheepAvatar(path: String?, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MeadowSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("🐑", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Koyun kartı: ekle / düzenle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheepEditScreen(id: Long, initialTag: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = App.instance.db.dao()

    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Sheep?>(null) }
    var tag by remember { mutableStateOf(initialTag) }
    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sheep.SEX_FEMALE) }
    var birthYear by remember { mutableStateOf("") }
    var mother by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Sheep.STATUS_ACTIVE) }
    var photo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        dao.sheep(id)?.let { s ->
            original = s
            tag = s.tag
            name = s.name
            sex = s.sex
            birthYear = s.birthYear?.toString().orEmpty()
            mother = s.motherTag
            notes = s.notes
            status = s.status
            photo = s.photoPath
        }
        loaded = true
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPhoto
        if (ok && file != null && file.length() > 0) photo = file.absolutePath else file?.delete()
        pendingPhoto = null
    }

    fun shoot() {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "koyun_${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        takePicture.launch(uri)
    }

    fun save() {
        val cleanTag = TagReader.normalize(tag)
        if (cleanTag.isEmpty()) {
            error = "Küpe numarası gerekli"
            return
        }
        scope.launch {
            val clash = dao.sheepByTag(cleanTag)
            if (clash != null && clash.id != id) {
                error = "Bu küpe numarası başka bir koyunda kayıtlı"
                return@launch
            }
            val sheep = (original ?: Sheep(tag = cleanTag)).copy(
                tag = cleanTag,
                name = name.trim(),
                sex = sex,
                birthYear = birthYear.toIntOrNull(),
                motherTag = TagReader.normalize(mother),
                notes = notes.trim(),
                status = status,
                photoPath = photo,
            )
            if (id == 0L) dao.insert(sheep) else dao.update(sheep)
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Yeni koyun" else "Koyun kartı") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri") } },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.clickable { shoot() }) {
                    SheepAvatar(photo, 88)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Meadow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = "Fotoğraf çek", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.take(20); error = null },
                    label = { Text("Küpe no") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("İsim (isteğe bağlı)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Cinsiyet", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sex == Sheep.SEX_FEMALE, onClick = { sex = Sheep.SEX_FEMALE }, label = { Text("Dişi") })
                FilterChip(selected = sex == Sheep.SEX_MALE, onClick = { sex = Sheep.SEX_MALE }, label = { Text("Koç") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it.filter(Char::isDigit).take(4) },
                    label = { Text("Doğum yılı") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = mother,
                    onValueChange = { mother = it.take(20) },
                    label = { Text("Anne küpe no") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Durum", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Sheep.STATUS_ACTIVE, Sheep.STATUS_SOLD, Sheep.STATUS_DEAD).forEach { s ->
                    FilterChip(selected = status == s, onClick = { status = s }, label = { Text(statusLabel(s)) })
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notlar (aşı, hastalık, kuzu…)") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { save() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) { Text("Kaydet") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("Satılan ya da ölen koyunlar için silmek yerine durumunu değiştirmen önerilir; geçmiş korunur.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        original?.let { dao.delete(it) }
                        onBack()
                    }
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}
