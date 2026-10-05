package com.example.usagetracker.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.usagetracker.ui.screens.CategoriesScreen
import com.example.usagetracker.ui.screens.DetectionStatusScreen
import com.example.usagetracker.ui.screens.EndOfDayScreen
import com.example.usagetracker.ui.screens.FocusTasksScreen
import com.example.usagetracker.ui.screens.HomeScreen
import com.example.usagetracker.ui.screens.ImportPreviewScreen
import com.example.usagetracker.ui.screens.ImportViewModel
import com.example.usagetracker.ui.screens.PrivacyScreen
import com.example.usagetracker.ui.screens.SettingsScreen
import com.example.usagetracker.ui.screens.TemplatesScreen

/** Reached from Settings, so it is not a bottom-bar tab. */
private const val CATEGORIES_ROUTE = "categories"

private const val PRIVACY_ROUTE = "privacy"

private const val DETECTION_STATUS_ROUTE = "detection_status"

/** Reached from Focus Tasks after picking a file. */
private const val IMPORT_PREVIEW_ROUTE = "import_preview"

private const val TEMPLATES_ROUTE = "import_templates"

/** Opened from the end-of-day notification, so it is not a bottom-bar tab. */
private const val END_OF_DAY_ROUTE = "end_of_day"

/** Back arrow above a screen reached from Settings or Focus Tasks (system back also works). */
@Composable
private fun SubScreen(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
fun UsageTrackerApp(showEndOfDay: Boolean = false, onEndOfDayHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateToTab(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Activity-scoped (created outside the NavHost) so Focus Tasks and the preview share one import.
    val importViewModel: ImportViewModel = viewModel()
    val importResult by importViewModel.finished.collectAsStateWithLifecycle()
    val pickImportFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importViewModel.load(uri)
            navController.navigate(IMPORT_PREVIEW_ROUTE) { launchSingleTop = true }
        }
    }

    LaunchedEffect(showEndOfDay) {
        if (showEndOfDay) {
            navController.navigate(END_OF_DAY_ROUTE) { launchSingleTop = true }
            onEndOfDayHandled()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            // Re-tapping the current tab returns to its root (e.g. Categories -> Settings).
                            if (selected) navController.popBackStack(destination.route, inclusive = false)
                            else navigateToTab(destination)
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) { HomeScreen(
                    onOpenSettings = { navigateToTab(Destination.Settings) },
                    onOpenFocus = { navigateToTab(Destination.FocusTasks) },
                ) }
            composable(Destination.FocusTasks.route) {
                FocusTasksScreen(
                    onImport = { pickImportFile.launch(ImportViewModel.MIME_TYPES) },
                    onOpenTemplates = { navController.navigate(TEMPLATES_ROUTE) },
                    importResult = importResult,
                    onImportResultShown = importViewModel::consumeFinished,
                    onUndoImport = importViewModel::undo,
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                    onOpenDetectionStatus = { navController.navigate(DETECTION_STATUS_ROUTE) },
                )
            }
            composable(IMPORT_PREVIEW_ROUTE) {
                ImportPreviewScreen(
                    viewModel = importViewModel,
                    onBack = { navController.popBackStack() },
                    onImported = { navController.popBackStack() },
                )
            }
            composable(TEMPLATES_ROUTE) { SubScreen(onBack = { navController.popBackStack() }) { TemplatesScreen() } }
            composable(PRIVACY_ROUTE) { SubScreen(onBack = { navController.popBackStack() }) { PrivacyScreen() } }
            composable(DETECTION_STATUS_ROUTE) { SubScreen(onBack = { navController.popBackStack() }) { DetectionStatusScreen() } }
            composable(CATEGORIES_ROUTE) { SubScreen(onBack = { navController.popBackStack() }) { CategoriesScreen() } }
            composable(END_OF_DAY_ROUTE) { EndOfDayScreen(onDone = { navController.popBackStack() }) }
        }
    }
}
