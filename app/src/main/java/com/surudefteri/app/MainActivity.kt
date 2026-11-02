package com.surudefteri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.surudefteri.app.ui.AppRoot
import com.surudefteri.app.ui.theme.SuruTheme
import com.surudefteri.app.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SuruTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        Updater.checkIfDue(this)
    }
}
