package com.example.usagetracker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Filled.Home),
    FocusTasks("focus_tasks", "Focus Tasks", Icons.Filled.CheckCircle),
    Settings("settings", "Settings", Icons.Filled.Settings),
}
