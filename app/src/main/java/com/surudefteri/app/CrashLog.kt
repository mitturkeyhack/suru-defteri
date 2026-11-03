package com.surudefteri.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Çökme ve hata raporu. Uygulama kapanırsa bir sonraki açılışta ekranda gösterilir;
 * kullanıcı kopyalayıp geliştiriciye gönderebilir. Hiçbir yere kendiliğinden gönderilmez.
 */
object CrashLog {
    private const val FILE = "son_hata.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(app, "Uygulama çöktü (${thread.name})", error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        // Yerel koddaki (C++) çökmeler Java tarafında yakalanamaz; Android'in kaydından okunur.
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                readSystemExitInfo(app)
            } catch (_: Throwable) {
            }
        }
    }

    fun record(context: Context, title: String, error: Throwable?) {
        val sw = StringWriter()
        error?.printStackTrace(PrintWriter(sw))
        write(context, "$title\n${header(context)}\n\n$sw")
    }

    /** Kaydedilmiş rapor (yoksa null). */
    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun write(context: Context, text: String) {
        File(context.filesDir, FILE).writeText(text.take(20_000))
    }

    private fun header(context: Context): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date())
        return "Sürüm $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · $time"
    }

    private fun readSystemExitInfo(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences("crash", Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("last_exit", 0L)
        val info = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .firstOrNull { it.timestamp > lastSeen } ?: return
        prefs.edit().putLong("last_exit", info.timestamp).apply()
        val reason = when (info.reason) {
            ApplicationExitInfo.REASON_CRASH -> "Java çökmesi"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Yerel kod çökmesi"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Bellek yetmedi"
            ApplicationExitInfo.REASON_ANR -> "Uygulama yanıt vermedi"
            else -> return // normal kapanışlar rapor edilmez
        }
        // Java çökmesi zaten kendi işleyicimizle kaydedildi; üstüne yazmayalım.
        if (info.reason == ApplicationExitInfo.REASON_CRASH && read(context) != null) return
        val time = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date(info.timestamp))
        val trace = try {
            info.traceInputStream?.use { stream ->
                // Yerel çökme kaydı ikili biçimde; içindeki okunabilir satırları al.
                val bytes = stream.readBytes()
                String(bytes, Charsets.ISO_8859_1)
                    .split(Regex("[^\\x20-\\x7E]+"))
                    .filter { it.length >= 6 }
                    .take(120)
                    .joinToString("\n")
            }
        } catch (_: Exception) {
            null
        }
        write(context, "$reason · $time\n${header(context)}\n${info.description.orEmpty()}\n\n${trace.orEmpty()}")
    }
}
