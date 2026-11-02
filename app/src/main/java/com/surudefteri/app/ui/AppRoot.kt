package com.surudefteri.app.ui

import androidx.compose.foundation.layout.fillMaxSize
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
