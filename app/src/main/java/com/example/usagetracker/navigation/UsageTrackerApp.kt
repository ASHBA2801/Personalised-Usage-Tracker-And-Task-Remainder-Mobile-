package com.example.usagetracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.usagetracker.ui.screens.CategoriesScreen
import com.example.usagetracker.ui.screens.FocusTasksScreen
import com.example.usagetracker.ui.screens.HomeScreen
import com.example.usagetracker.ui.screens.SettingsScreen

/** Reached from Settings, so it is not a bottom-bar tab. */
private const val CATEGORIES_ROUTE = "categories"

@Composable
fun UsageTrackerApp() {
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
                SettingsScreen(onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) })
            }
            composable(CATEGORIES_ROUTE) { CategoriesScreen() }
        }
    }
}
