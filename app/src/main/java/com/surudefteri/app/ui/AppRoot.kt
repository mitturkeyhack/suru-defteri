package com.surudefteri.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.surudefteri.app.CrashLog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.surudefteri.app.App

@Composable
fun AppRoot() {
    val app = App.instance
    val nav = rememberNavController()
    val activeCount by remember { app.db.dao().activeCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val manualExpected by app.prefs.expected.collectAsStateWithLifecycle()
    // Kartlarda koyun varsa sürü sayısı oradan, yoksa elle girilen sayı.
    val expected = if (activeCount > 0) activeCount else manualExpected

    val context = LocalContext.current
    var crashReport by remember { mutableStateOf(CrashLog.read(context)) }
    crashReport?.let { report ->
        CrashDialog(report) {
            CrashLog.clear(context)
            crashReport = null
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    expected = expected,
                    onLiveCount = { nav.navigate("live") },
                    onVideoCount = { nav.navigate("video") },
                    onSheepList = { nav.navigate("sheep") },
                    onScan = { nav.navigate("scan") },
                )
            }
            composable("live") { LiveCountScreen(expected) { nav.popBackStack() } }
            composable("video") { VideoCountScreen(expected) { nav.popBackStack() } }
            composable("sheep") {
                SheepListScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { id -> nav.navigate("edit/$id") },
                    onScan = { nav.navigate("scan") },
                )
            }
            composable("scan") {
                TagScanScreen(
                    onBack = { nav.popBackStack() },
                    onResult = { id, tag ->
                        nav.navigate(if (id != null) "edit/$id" else "edit/0?tag=$tag") {
                            popUpTo("scan") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                "edit/{id}?tag={tag}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("tag") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                SheepEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialTag = entry.arguments?.getString("tag").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}


/** Önceki çökmenin raporu: kullanıcı kopyalayıp geliştiriciye gönderebilir. */
@Composable
private fun CrashDialog(report: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Bir hata oluştu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Uygulama son kullanımda hata verdi. Aşağıdaki raporu kopyalayıp geliştiriciye gönderirsen sorun hızla düzeltilir.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(context, "Rapor kopyalandı", Toast.LENGTH_SHORT).show()
            }) { Text("Kopyala") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, "Raporu paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Paylaş") }
                TextButton(onClick = onClose) { Text("Kapat") }
            }
        },
    )
}
