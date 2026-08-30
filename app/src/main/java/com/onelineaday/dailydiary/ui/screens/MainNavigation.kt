package com.onelineaday.dailydiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import com.onelineaday.dailydiary.ads.InterstitialAdManager
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Today",
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Outlined.Edit
    )
    
    data object Timeline : Screen(
        route = "timeline",
        title = "Timeline",
        selectedIcon = Icons.Filled.ViewTimeline,
        unselectedIcon = Icons.Outlined.ViewTimeline
    )
    
    data object Stats : Screen(
        route = "stats",
        title = "Journey",
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object Gallery : Screen(
        route = "gallery",
        title = "Gallery",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary
    )
}

val screens = listOf(
    Screen.Home,
    Screen.Timeline,
    Screen.Stats,
    Screen.Settings
)

@Composable
fun MainNavigation(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    currentAppTheme: com.onelineaday.dailydiary.ui.theme.AppTheme = com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT,
    onThemeChange: (com.onelineaday.dailydiary.ui.theme.AppTheme) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val viewModel: JournalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val shakeDetector = com.onelineaday.dailydiary.utils.ShakeDetector {
            if (viewModel.lastDeletedEntry != null) {
                viewModel.undoDelete()
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                shakeDetector.register(context)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                shakeDetector.unregister(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            shakeDetector.unregister(context)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { 
                        it.route == screen.route 
                    } == true
                    
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
            
            composable(
                route = Screen.Timeline.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                TimelineScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
            
            composable(
                route = Screen.Stats.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                StatsScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
            
            composable(
                route = Screen.Settings.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    currentAppTheme = currentAppTheme,
                    onThemeChange = onThemeChange,
                    onNavigateToPrivacyPolicy = { navController.navigate("privacy_policy") }
                )
            }
            
            composable(
                route = "privacy_policy",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                PrivacyPolicyScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
