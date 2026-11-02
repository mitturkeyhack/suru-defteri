package com.surudefteri.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.surudefteri.app.data.Prefs
import com.surudefteri.app.data.SuruDb
import com.surudefteri.app.update.Updater

class App : Application() {
    lateinit var db: SuruDb
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = SuruDb.build(this)
        prefs = Prefs(this)
        Updater.schedule(this)
    }

    companion object {
        lateinit var instance: App
            private set

        val isInForeground: Boolean
            get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}
