package com.example.usagetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.notifications.Notifications
import com.example.usagetracker.navigation.UsageTrackerApp
import com.example.usagetracker.ui.theme.UsageTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var showEndOfDay by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showEndOfDay = savedInstanceState == null && intent.hasEndOfDayFlag()
        lifecycleScope.launch { CategoryRepository(applicationContext).ensureSeeded() }
        setContent {
            UsageTrackerTheme {
                UsageTrackerApp(
                    showEndOfDay = showEndOfDay,
                    onEndOfDayHandled = { showEndOfDay = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasEndOfDayFlag()) showEndOfDay = true
    }

    private fun Intent.hasEndOfDayFlag() = getBooleanExtra(Notifications.EXTRA_SHOW_END_OF_DAY, false)
}
