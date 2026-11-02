package com.surudefteri.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.surudefteri.app.App

/** Arka planda (uygulama kapalıyken de) güncelleme kontrolü. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Updater.checkAndInstall(applicationContext, userInitiated = false)) Result.success() else Result.retry()
}

/** Android'in kurulum sonucunu bildirdiği yer. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESULT) return
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                Updater.onNeedsConfirm(context, version, confirm)
                if (App.isInForeground) {
                    try {
                        context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Log.w("Suru/Update", "Onay ekranı açılamadı", e)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                Log.w("Suru/Update", "Kurulum başarısız ($status)")
                Updater.onFailed(
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Güncelleme iptal edildi"
                    else "Güncelleme kurulamadı"
                )
            }
        }
    }

    companion object {
        const val ACTION_RESULT = "com.surudefteri.app.UPDATE_RESULT"
        const val EXTRA_VERSION = "version"
    }
}

/** Güncelleme kurulunca kullanıcıya haber verir. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updater.onUpdated(context)
    }
}
