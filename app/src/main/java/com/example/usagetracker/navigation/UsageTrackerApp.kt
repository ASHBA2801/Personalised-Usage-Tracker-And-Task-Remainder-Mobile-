package com.example.usagetracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.usagetracker.ui.screens.CategoriesScreen
import com.example.usagetracker.ui.screens.EndOfDayScreen
import com.example.usagetracker.ui.screens.FocusTasksScreen
import com.example.usagetracker.ui.screens.HomeScreen
import com.example.usagetracker.ui.screens.PrivacyScreen
import com.example.usagetracker.ui.screens.SettingsScreen

/** Reached from Settings, so it is not a bottom-bar tab. */
private const val CATEGORIES_ROUTE = "categories"

private const val PRIVACY_ROUTE = "privacy"

/** Opened from the end-of-day notification, so it is not a bottom-bar tab. */
private const val END_OF_DAY_ROUTE = "end_of_day"

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
                        onClick = { navigateToTab(destination) },
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
            composable(Destination.Home.route) { HomeScreen(onOpenSettings = { navigateToTab(Destination.Settings) }) }
            composable(Destination.FocusTasks.route) { FocusTasksScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                )
            }
            composable(PRIVACY_ROUTE) { PrivacyScreen() }
            composable(CATEGORIES_ROUTE) { CategoriesScreen() }
            composable(END_OF_DAY_ROUTE) { EndOfDayScreen(onDone = { navController.popBackStack() }) }
        }
    }
}
