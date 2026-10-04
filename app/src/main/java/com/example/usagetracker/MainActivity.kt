package com.example.usagetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.navigation.UsageTrackerApp
import com.example.usagetracker.ui.theme.UsageTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { CategoryRepository(applicationContext).ensureSeeded() }
        setContent {
            UsageTrackerTheme {
                UsageTrackerApp()
            }
        }
    }
}
