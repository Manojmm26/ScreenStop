package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.OverlayContent
import com.example.ui.screens.ExceptionsScreen
import com.example.ui.screens.GuardScreen
import com.example.ui.screens.LauncherHubScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.takeover.DetoxBlockedScreen
import com.example.ui.takeover.HowLongPromptScreen
import com.example.ui.takeover.TakeoverScreen
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.GraphiteHighest
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.ZincSecondary

private const val ROUTE_GUARD = "guard"
private const val ROUTE_APPS = "apps"
private const val ROUTE_LAUNCHER = "launcher"
private const val ROUTE_MESSAGES = "messages"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestination(
        route = ROUTE_GUARD,
        labelRes = R.string.nav_guard,
        selectedIcon = Icons.Filled.Shield,
        unselectedIcon = Icons.Outlined.Shield,
        testTag = "nav_guard"
    ),
    NavDestination(
        route = ROUTE_APPS,
        labelRes = R.string.nav_apps,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        testTag = "nav_apps"
    ),
    NavDestination(
        route = ROUTE_LAUNCHER,
        labelRes = R.string.nav_launcher,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_launcher"
    ),
    NavDestination(
        route = ROUTE_MESSAGES,
        labelRes = R.string.nav_messages,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_messages"
    )
)

@Composable
fun ScreenStopApp(
    viewModel: GuardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_GUARD
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.refreshPermissions() }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    fun navigateTo(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissions()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ObsidianCanvas)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = ObsidianCanvas,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = GraphiteHighest,
                            contentColor = AlabasterInk,
                            shape = MaterialTheme.shapes.small
                        )
                    }
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        ScreenStopBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = ::navigateTo
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        ScreenStopNavRail(
                            currentRoute = currentRoute,
                            onNavigate = ::navigateTo
                        )
                        VerticalDivider(color = HairlineBorder)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_GUARD,
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 640.dp)
                        ) {
                            composable(ROUTE_GUARD) {
                                GuardScreen(
                                    uiState = uiState,
                                    onToggleGuard = viewModel::toggleGuard,
                                    onSelectInterval = viewModel::setIntervalMinutes,
                                    onToggleEscalatingFriction = viewModel::setEscalatingFrictionEnabled,
                                    onRequestOverlayPermission = viewModel::openOverlaySettings,
                                    onRequestUsagePermission = viewModel::openUsageAccessSettings,
                                    onRequestNotificationPermission = ::requestNotifications,
                                    onTestTakeover = viewModel::triggerTestTakeover,
                                    onTestHowLongPrompt = viewModel::triggerTestHowLongPrompt,
                                    onNavigateToApps = { navigateTo(ROUTE_APPS) },
                                    onNavigateToMessages = { navigateTo(ROUTE_MESSAGES) }
                                )
                            }
                            composable(ROUTE_APPS) {
                                ExceptionsScreen(
                                    apps = uiState.installedApps,
                                    isLoading = uiState.isLoadingApps,
                                    searchQuery = uiState.appSearchQuery,
                                    hasUsagePermission = uiState.permissions.hasUsageStatsPermission,
                                    onSearchQueryChange = viewModel::updateAppSearchQuery,
                                    onToggleException = viewModel::toggleExceptionPackage,
                                    onTogglePrompted = viewModel::togglePromptedPackage,
                                    onSetDetoxLock = viewModel::setDetoxLock,
                                    onClearDetoxLock = viewModel::clearDetoxLock,
                                    onToggleLauncherFavorite = viewModel::toggleLauncherFavorite,
                                    onToggleLauncherHidden = viewModel::toggleLauncherHidden,
                                    onRenameApp = viewModel::setLauncherCustomLabel,
                                    onRequestUsagePermission = viewModel::openUsageAccessSettings
                                )
                            }
                            composable(ROUTE_LAUNCHER) {
                                LauncherHubScreen(
                                    uiState = uiState,
                                    onSetDefaultHome = viewModel::openDefaultHomeSettings,
                                    onOpenMinimalistHome = viewModel::openMinimalistHomeNow,
                                    onToggleShowGuardStatus = viewModel::setShowGuardStatusOnHome,
                                    onUnpinFavorite = viewModel::toggleLauncherFavorite,
                                    onUnhideApp = viewModel::toggleLauncherHidden
                                )
                            }
                            composable(ROUTE_MESSAGES) {
                                MessagesScreen(
                                    messages = uiState.settings.reminderMessages,
                                    onAddMessage = viewModel::addMessage,
                                    onUpdateMessage = viewModel::updateMessage,
                                    onDeleteMessage = viewModel::deleteMessage,
                                    onResetDefaults = viewModel::resetDefaultMessages
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-app full-screen overlay previews (when system overlay permission isn't granted yet)
        when (val preview = uiState.previewOverlay) {
            is OverlayContent.Takeover -> {
                TakeoverScreen(
                    session = preview.session,
                    onDismissConfirmed = viewModel::dismissPreviewOverlay
                )
            }

            is OverlayContent.DetoxBlocked -> {
                DetoxBlockedScreen(
                    appLabel = preview.appLabel,
                    blockedUntilMillis = preview.blockedUntilMillis,
                    onReturnHome = viewModel::dismissPreviewOverlay
                )
            }

            is OverlayContent.HowLongPrompt -> {
                HowLongPromptScreen(
                    appLabel = preview.appLabel,
                    onSelectDurationMinutes = { viewModel.dismissPreviewOverlay() },
                    onNeverMindGoHome = viewModel::dismissPreviewOverlay
                )
            }

            null -> Unit
        }
    }
}

@Composable
private fun ScreenStopBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    Box {
        HorizontalDivider(
            color = HairlineBorder,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        NavigationBar(
            containerColor = CarbonSurface,
            contentColor = AlabasterInk,
            windowInsets = WindowInsets.navigationBars
        ) {
            TopLevelDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(dest.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = stringResource(dest.labelRes)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(dest.labelRes),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianCanvas,
                        selectedTextColor = AlabasterInk,
                        indicatorColor = AlabasterInk,
                        unselectedIconColor = ZincSecondary,
                        unselectedTextColor = ZincSecondary
                    ),
                    modifier = Modifier.testTag(dest.testTag)
                )
            }
        }
    }
}

@Composable
private fun ScreenStopNavRail(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationRail(
        containerColor = CarbonSurface,
        contentColor = AlabasterInk
    ) {
        TopLevelDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = stringResource(dest.labelRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(dest.labelRes),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ObsidianCanvas,
                    selectedTextColor = AlabasterInk,
                    indicatorColor = AlabasterInk,
                    unselectedIconColor = ZincSecondary,
                    unselectedTextColor = ZincSecondary
                ),
                modifier = Modifier.testTag(dest.testTag)
            )
        }
    }
}
