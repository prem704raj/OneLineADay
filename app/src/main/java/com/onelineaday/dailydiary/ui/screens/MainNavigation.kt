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
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import java.time.LocalDate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        titleRes = R.string.today,
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Outlined.Edit
    )
    
    data object Timeline : Screen(
        route = "timeline",
        titleRes = R.string.timeline,
        selectedIcon = Icons.Filled.ViewTimeline,
        unselectedIcon = Icons.Outlined.ViewTimeline
    )
    
    data object Stats : Screen(
        route = "stats",
        titleRes = R.string.journey,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights
    )
    
    data object Calendar : Screen(
        route = "calendar",
        titleRes = R.string.calendar,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth
    )
    
    data object Settings : Screen(
        route = "settings",
        titleRes = R.string.settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

val screens = listOf(
    Screen.Home,
    Screen.Timeline,
    Screen.Calendar,
    Screen.Stats,
    Screen.Settings
)

@Composable
fun MainNavigation(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    currentAppTheme: com.onelineaday.dailydiary.ui.theme.AppTheme = com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT,
    onThemeChange: (com.onelineaday.dailydiary.ui.theme.AppTheme) -> Unit = {},
    pendingNavDestination: String? = null,
    onClearPendingNavDestination: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val viewModel: JournalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    LaunchedEffect(pendingNavDestination) {
        if (!pendingNavDestination.isNullOrBlank()) {
            navController.navigate(pendingNavDestination)
            onClearPendingNavDestination()
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        val shakeDetector = com.onelineaday.dailydiary.utils.ShakeDetector {
            if (viewModel.lastDeletedEntry != null) {
                viewModel.undoDelete()
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                shakeDetector.register(context)
                viewModel.refreshToday()
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

    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.refreshToday()

            val now = java.time.ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val waitMillis = java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)

            kotlinx.coroutines.delay(waitMillis + 500L)
        }
    }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPremium by com.onelineaday.dailydiary.PremiumManager.isPremium.collectAsState()
    val canRequestAds by com.onelineaday.dailydiary.ads.AdsConsentManager.canRequestAds.collectAsState()
    
    var viewingDetailEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var editingDetailEntry by remember { mutableStateOf<JournalEntry?>(null) }

    if (viewingDetailEntry != null) {
        editingDetailEntry?.let { entry ->
            EditEntryDialog(
                entry = entry,
                viewModel = viewModel,
                onDismiss = { editingDetailEntry = null },
                onSave = { newContent, newMood, newPhotoUri, newMediaUris, newTags ->
                    viewModel.updateEntry(
                        entry = entry,
                        newContent = newContent,
                        newMood = newMood,
                        newPhotoUri = newPhotoUri,
                        newMediaUris = newMediaUris,
                        newAudioUri = entry.audioUri,
                        newTags = newTags
                    )
                    viewingDetailEntry = entry.copy(
                        content = newContent,
                        mood = newMood,
                        photoUri = newPhotoUri,
                        mediaUris = newMediaUris,
                        tags = newTags
                    )
                    editingDetailEntry = null
                }
            )
        }

        EntryDetailScreen(
            entry = viewingDetailEntry!!,
            onBack = { viewingDetailEntry = null },
            onEdit = { editingDetailEntry = viewingDetailEntry },
            onDelete = {
                viewModel.deleteEntry(viewingDetailEntry!!)
                viewingDetailEntry = null
            },
            onTogglePin = {
                val entry = viewingDetailEntry!!
                val updated = entry.copy(isPinned = !entry.isPinned)
                viewModel.togglePin(entry)
                viewingDetailEntry = updated
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Prevent applying the top system inset twice. Each screen manages its own top bar / status bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
        bottomBar = {
            Column {
                if (!isPremium && canRequestAds && currentDestination?.route != "privacy_policy") {
                    com.onelineaday.dailydiary.ads.BannerAdView()
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { 
                        it.route == screen.route 
                    } == true
                    
                        val title = stringResource(screen.titleRes)
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
                                    contentDescription = title
                                )
                            },
                            label = {
                                Text(
                                    text = title,
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
    }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(
                route = Screen.Home.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry },
                    onNavigateToOnThisDay = { navController.navigate("on_this_day") }
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
                route = Screen.Calendar.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry },
                    onNavigateToYearInPixels = { navController.navigate("year_in_pixels") },
                    onNavigateToWrite = { date ->
                        viewModel.setSelectedDate(date)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            
            composable(
                route = Screen.Stats.route,
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                StatsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateToYearInPixels = { navController.navigate("year_in_pixels") },
                    onNavigateToLifePatterns = { navController.navigate("life_patterns") },
                    onNavigateToTimeCapsules = { navController.navigate("time_capsules") },
                    onNavigateToZenGarden = { navController.navigate("zen_garden") },
                    onNavigateToConstellation = { navController.navigate("constellation") },
                    onNavigateToMonthlyReel = { navController.navigate("monthly_reel") }
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
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
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

            composable(
                route = "on_this_day?date={date}",
                arguments = listOf(
                    navArgument("date") {
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) { backStackEntry ->
                val dateArg = backStackEntry.arguments?.getString("date")
                val initialDate = dateArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                OnThisDayScreen(
                    viewModel = viewModel,
                    initialDate = initialDate,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry }
                )
            }

            composable(
                route = "year_in_pixels",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                YearInPixelsScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry },
                    onNavigateToWrite = { date ->
                        viewModel.setSelectedDate(date)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(
                route = "life_patterns",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                LifePatternsScreen(
                    entries = uiState.entries,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "time_capsules",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                TimeCapsulesScreen(
                    journalId = uiState.activeJournalId ?: "default",
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "zen_garden",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                ZenGardenScreen(
                    currentStreak = uiState.currentStreak,
                    longestStreak = uiState.longestStreak,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "constellation",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                ConstellationScreen(
                    entries = uiState.entries,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry }
                )
            }

            composable(
                route = "monthly_reel",
                enterTransition = { fadeIn() + slideInHorizontally() },
                exitTransition = { fadeOut() + slideOutHorizontally() }
            ) {
                MonthlyReelScreen(
                    entries = uiState.entries,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { entry -> viewingDetailEntry = entry }
                )
            }
        }
    }
}