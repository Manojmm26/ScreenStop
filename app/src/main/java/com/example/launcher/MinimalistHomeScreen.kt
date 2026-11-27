package com.example.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GuardDefaults
import com.example.data.GuardSettings
import com.example.data.InstalledAppInfo
import com.example.ui.GuardUiState
import com.example.ui.GuardViewModel
import com.example.ui.takeover.DetoxBlockedScreen
import com.example.ui.takeover.FrictionConfirmDialog
import com.example.ui.takeover.PendingFrictionAction
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HairlineSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SignalVermilionDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MinimalistHomeScreen(
    viewModel: GuardViewModel,
    onLaunchApp: (String) -> Unit,
    onOpenScreenStop: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAllAppsDrawer by remember { mutableStateOf(false) }
    var drawerSearch by remember { mutableStateOf("") }
    var showHiddenInDrawer by remember { mutableStateOf(false) }
    var selectedAppForMenu by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var renamingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var blockedAppAttempt by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var blockedAttemptIsDeepWork by remember { mutableStateOf(false) }
    var showDeepWorkDialog by remember { mutableStateOf(false) }
    var showEditFocusDialog by remember { mutableStateOf(false) }
    var pendingEndDeepWork by remember { mutableStateOf<PendingFrictionAction.EndDeepWorkEarly?>(null) }

    val homePagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Pressing Back inside the All Apps drawer returns to Home; on Focus Note returns to Home Apps.
    BackHandler(enabled = showAllAppsDrawer) {
        showAllAppsDrawer = false
        drawerSearch = ""
    }
    BackHandler(enabled = !showAllAppsDrawer && homePagerState.currentPage != 0) {
        scope.launch { homePagerState.animateScrollToPage(0) }
    }

    fun handleTapApp(app: InstalledAppInfo) {
        val currentNow = System.currentTimeMillis()
        when {
            app.isDetoxLocked(currentNow) -> {
                blockedAttemptIsDeepWork = false
                blockedAppAttempt = app
            }

            app.isRestrictedByDeepWork(uiState.settings, currentNow) -> {
                blockedAttemptIsDeepWork = true
                blockedAppAttempt = app
            }

            else -> onLaunchApp(app.packageName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedContent(
            targetState = showAllAppsDrawer,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "launcherHomeDrawer",
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) { inDrawer ->
            if (!inDrawer) {
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = homePagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("launcher_home_pager")
                    ) { page ->
                        when (page) {
                            0 -> MinimalistHomePane(
                                uiState = uiState,
                                nowMillis = nowMillis,
                                onTapApp = ::handleTapApp,
                                onLongPressApp = { selectedAppForMenu = it },
                                onOpenDeepWork = { showDeepWorkDialog = true },
                                onOpenAllApps = { showAllAppsDrawer = true },
                                onOpenScreenStop = onOpenScreenStop
                            )

                            else -> MinimalistFocusNotePane(
                                focusMantra = uiState.settings.homeFocusMantra,
                                nowMillis = nowMillis,
                                onEditClick = { showEditFocusDialog = true },
                                onGoHomePage = {
                                    scope.launch { homePagerState.animateScrollToPage(0) }
                                }
                            )
                        }
                    }

                    HomePagerIndicator(
                        currentPage = homePagerState.currentPage,
                        onSelectPage = { targetPage ->
                            scope.launch { homePagerState.animateScrollToPage(targetPage) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(bottom = 8.dp)
                    )
                }
            } else {
                MinimalistAppDrawerPane(
                    apps = uiState.installedApps,
                    settings = uiState.settings,
                    nowMillis = nowMillis,
                    searchQuery = drawerSearch,
                    showHidden = showHiddenInDrawer,
                    onSearchChange = { drawerSearch = it },
                    onToggleShowHidden = { showHiddenInDrawer = !showHiddenInDrawer },
                    onOpenDeepWork = { showDeepWorkDialog = true },
                    onBackHome = {
                        showAllAppsDrawer = false
                        drawerSearch = ""
                    },
                    onTapApp = ::handleTapApp,
                    onLongPressApp = { selectedAppForMenu = it }
                )
            }
        }

        // Immediate in-launcher Detox Lock or Deep Work block screen if user taps a restricted app
        val blocked = blockedAppAttempt
        val blockedUntil = if (blockedAttemptIsDeepWork) {
            uiState.settings.deepWorkUntilMillis
        } else {
            blocked?.blockedUntilMillis ?: 0L
        }
        if (blocked != null && blockedUntil > nowMillis) {
            DetoxBlockedScreen(
                appLabel = blocked.displayLabel,
                blockedUntilMillis = blockedUntil,
                isDeepWork = blockedAttemptIsDeepWork,
                categoryLabel = if (blockedAttemptIsDeepWork) blocked.category?.displayName else null,
                onReturnHome = {
                    blockedAppAttempt = null
                    blockedAttemptIsDeepWork = false
                }
            )
        }
    }

    if (showDeepWorkDialog) {
        if (uiState.settings.isDeepWorkActive(nowMillis)) {
            DeepWorkActiveDialog(
                settings = uiState.settings,
                hasDndAccess = uiState.permissions.hasDndAccess,
                nowMillis = nowMillis,
                onExtend15m = { viewModel.extendDeepWork(15) },
                onRequestEndEarly = {
                    showDeepWorkDialog = false
                    pendingEndDeepWork = PendingFrictionAction.EndDeepWorkEarly(
                        challengeWords = GuardDefaults.pickRandomChallengeWords(2, emptyList())
                    )
                },
                onOpenDndSettings = { viewModel.openDndPolicyAccessSettings() },
                onDismiss = { showDeepWorkDialog = false }
            )
        } else {
            DeepWorkStartDialog(
                settings = uiState.settings,
                installedApps = uiState.installedApps,
                hasDndAccess = uiState.permissions.hasDndAccess,
                nowMillis = nowMillis,
                onOpenDndSettings = { viewModel.openDndPolicyAccessSettings() },
                onDismiss = { showDeepWorkDialog = false },
                onStart = { untilMillis, categories, strictAll ->
                    showDeepWorkDialog = false
                    viewModel.startDeepWork(
                        untilMillis = untilMillis,
                        categories = categories,
                        strictAllNonExempt = strictAll
                    )
                }
            )
        }
    }

    pendingEndDeepWork?.let { action ->
        FrictionConfirmDialog(
            action = action,
            onDismiss = { pendingEndDeepWork = null },
            onConfirmed = {
                pendingEndDeepWork = null
                viewModel.endDeepWorkEarly()
            }
        )
    }

    if (showEditFocusDialog) {
        EditFocusMantraDialog(
            initialText = uiState.settings.homeFocusMantra,
            onDismiss = { showEditFocusDialog = false },
            onSave = { updated ->
                viewModel.setHomeFocusMantra(updated)
                showEditFocusDialog = false
            }
        )
    }

    selectedAppForMenu?.let { app ->
        LauncherAppQuickActionDialog(
            app = app,
            onDismiss = { selectedAppForMenu = null },
            onOpen = {
                selectedAppForMenu = null
                handleTapApp(app)
            },
            onTogglePin = {
                selectedAppForMenu = null
                viewModel.toggleLauncherFavorite(app.packageName)
            },
            onToggleHide = {
                selectedAppForMenu = null
                viewModel.toggleLauncherHidden(app.packageName)
            },
            onRename = {
                selectedAppForMenu = null
                renamingApp = app
            },
            onDetoxLock2h = {
                selectedAppForMenu = null
                viewModel.setDetoxLock(app.packageName, 2L * 60L * 60_000L, "2h")
            }
        )
    }

    renamingApp?.let { app ->
        RenameAppDialog(
            app = app,
            onDismiss = { renamingApp = null },
            onSave = { newLabel ->
                viewModel.setLauncherCustomLabel(app.packageName, newLabel)
                renamingApp = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalistHomePane(
    uiState: GuardUiState,
    nowMillis: Long,
    onTapApp: (InstalledAppInfo) -> Unit,
    onLongPressApp: (InstalledAppInfo) -> Unit,
    onOpenDeepWork: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenScreenStop: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE · MMM dd", Locale.getDefault()) }
    val timeText = timeFormat.format(Date(nowMillis))
    val dateText = dateFormat.format(Date(nowMillis)).uppercase(Locale.getDefault())

    val appByPackage = remember(uiState.installedApps) {
        uiState.installedApps.associateBy { it.packageName }
    }
    val favorites = remember(uiState.settings.launcherFavoritePackages, appByPackage) {
        uiState.settings.launcherFavoritePackages.mapNotNull { appByPackage[it] }
    }
    val activeDetoxCount = uiState.settings.activeBlockedApps(nowMillis).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Clock + Minimal Guard Pill
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = timeText,
                style = TextStyle(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 60.sp,
                    letterSpacing = (-2.5).sp,
                    fontFeatureSettings = "tnum"
                ),
                color = AlabasterInk,
                modifier = Modifier.testTag("launcher_clock_text")
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = ZincSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (uiState.settings.showGuardStatusOnHome) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onOpenScreenStop,
                    color = CarbonSurface,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.settings.isGuardEnabled) {
                            SignalVermilion.copy(alpha = 0.45f)
                        } else {
                            HairlineBorder
                        }
                    ),
                    modifier = Modifier.testTag("launcher_guard_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.settings.isGuardEnabled) SignalVermilion else ZincMuted
                                )
                        )
                        val guardSummary = if (uiState.settings.isGuardEnabled) {
                            "ARMED · ${GuardDefaults.formatDuration(uiState.runtime.elapsedSeconds)} / ${
                                GuardDefaults.formatDuration(uiState.runtime.targetSeconds)
                            }"
                        } else {
                            "GUARD OFF"
                        }
                        val detoxSuffix = if (activeDetoxCount > 0) " · $activeDetoxCount LOCKED" else ""
                        Text(
                            text = guardSummary + detoxSuffix,
                            style = MaterialTheme.typography.labelSmall,
                            color = AlabasterInk
                        )
                    }
                }
            }
        }

        // Center Text-Only Pinned Favorites
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.launcher_home_empty_favorites),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZincMuted
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launcher_favorites_list")
                ) {
                    items(favorites, key = { it.packageName }) { app ->
                        val isLocked = app.isDetoxLocked(nowMillis)
                        val isDeepWorkRestricted = !isLocked && app.isRestrictedByDeepWork(uiState.settings, nowMillis)
                        val isBlocked = isLocked || isDeepWorkRestricted
                        val remaining = (app.blockedUntilMillis ?: 0L) - nowMillis
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { onTapApp(app) },
                                    onLongClick = { onLongPressApp(app) }
                                )
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                                .testTag("launcher_fav_${app.packageName}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.displayLabel,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = if (isBlocked) ZincMuted else AlabasterInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (app.shouldShowDailyUsage || isBlocked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.shouldShowDailyUsage) {
                                        val usageTint = when {
                                            app.todayUsageMinutes >= 60 -> SignalVermilion
                                            app.todayUsageMinutes >= 30 -> WarningAmber
                                            else -> ZincSecondary
                                        }
                                        val usageBg = when {
                                            app.todayUsageMinutes >= 60 -> SignalVermilionDim
                                            app.todayUsageMinutes >= 30 -> WarningAmber.copy(alpha = 0.14f)
                                            else -> CarbonSurface
                                        }
                                        val usageBorder = when {
                                            app.todayUsageMinutes >= 60 -> SignalVermilion.copy(alpha = 0.5f)
                                            app.todayUsageMinutes >= 30 -> WarningAmber.copy(alpha = 0.45f)
                                            else -> HairlineBorder
                                        }
                                        Surface(
                                            color = usageBg,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, usageBorder),
                                            modifier = Modifier.testTag("launcher_fav_usage_${app.packageName}")
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.launcher_usage_minutes,
                                                    app.todayUsageMinutes
                                                ),
                                                style = TextStyle(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = usageTint,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (isLocked) {
                                        Surface(
                                            color = SignalVermilionDim,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = GuardDefaults.formatRemainingLock(remaining),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalVermilion,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else if (isDeepWorkRestricted) {
                                        Surface(
                                            color = SignalVermilionDim,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.deep_work_pill_tag),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalVermilion,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Global Deep Work Bar + Minimal Action Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DeepWorkLaunchBar(
                settings = uiState.settings,
                nowMillis = nowMillis,
                onClick = onOpenDeepWork
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenScreenStop,
                    color = CarbonSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.testTag("launcher_open_screenstop")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = AlabasterInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.launcher_home_guard_btn),
                            style = MaterialTheme.typography.labelLarge,
                            color = AlabasterInk
                        )
                    }
                }

                Surface(
                    onClick = onOpenAllApps,
                    color = AlabasterInk,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("launcher_open_all_apps")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.launcher_home_all_apps),
                            style = MaterialTheme.typography.labelLarge,
                            color = ObsidianCanvas
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = ObsidianCanvas,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalistAppDrawerPane(
    apps: List<InstalledAppInfo>,
    settings: GuardSettings,
    nowMillis: Long,
    searchQuery: String,
    showHidden: Boolean,
    onSearchChange: (String) -> Unit,
    onToggleShowHidden: () -> Unit,
    onOpenDeepWork: () -> Unit,
    onBackHome: () -> Unit,
    onTapApp: (InstalledAppInfo) -> Unit,
    onLongPressApp: (InstalledAppInfo) -> Unit
) {
    val hiddenCount = remember(apps) { apps.count { it.isHiddenInLauncher } }
    val visibleApps = remember(apps, searchQuery, showHidden) {
        val q = searchQuery.trim()
        apps.filter { app ->
            val hiddenAllowed = showHidden || !app.isHiddenInLauncher || q.isNotEmpty()
            val matchesQuery = q.isEmpty() ||
                app.displayLabel.contains(q, ignoreCase = true) ||
                app.label.contains(q, ignoreCase = true)
            hiddenAllowed && matchesQuery
        }.sortedBy { it.displayLabel.lowercase() }
    }

    var focusedWheelApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackHome,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("launcher_drawer_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.launcher_drawer_back_cd),
                        tint = AlabasterInk
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.launcher_home_all_apps),
                    style = MaterialTheme.typography.headlineMedium,
                    color = AlabasterInk
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hiddenCount > 0) {
                    TextButton(
                        onClick = onToggleShowHidden,
                        modifier = Modifier.testTag("launcher_toggle_hidden")
                    ) {
                        Text(
                            text = if (showHidden) {
                                stringResource(R.string.launcher_drawer_hide_hidden)
                            } else {
                                stringResource(R.string.launcher_drawer_show_hidden, hiddenCount)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ZincSecondary
                        )
                    }
                }

                DeepWorkCompactChip(
                    settings = settings,
                    nowMillis = nowMillis,
                    onClick = onOpenDeepWork
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        MinimalistWheelAppPicker(
            apps = visibleApps,
            settings = settings,
            nowMillis = nowMillis,
            onFocusedAppChanged = { focusedWheelApp = it },
            onLaunchApp = onTapApp,
            onLongPressApp = onLongPressApp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom-docked one-handed search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.launcher_drawer_search_hint),
                    color = ZincMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = ZincSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.apps_clear_search_cd),
                            tint = ZincSecondary
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    val target = focusedWheelApp ?: visibleApps.firstOrNull()
                    if (target != null) {
                        onTapApp(target)
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlabasterInk,
                unfocusedBorderColor = HairlineBorder,
                focusedContainerColor = CarbonSurface,
                unfocusedContainerColor = CarbonSurface,
                cursorColor = AlabasterInk,
                focusedTextColor = AlabasterInk,
                unfocusedTextColor = AlabasterInk
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("launcher_drawer_search")
        )
    }
}

@Composable
private fun LauncherAppQuickActionDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit,
    onRename: () -> Unit,
    onDetoxLock2h: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterInk,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(text = app.displayLabel, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickActionItem(stringResource(R.string.launcher_quick_open), AlabasterInk, onOpen)
                QuickActionItem(
                    text = if (app.isPinnedFavorite) {
                        stringResource(R.string.launcher_quick_unpin)
                    } else {
                        stringResource(R.string.launcher_quick_pin)
                    },
                    color = AlabasterInk,
                    onClick = onTogglePin
                )
                QuickActionItem(stringResource(R.string.launcher_quick_rename), AlabasterInk, onRename)
                QuickActionItem(
                    text = if (app.isHiddenInLauncher) {
                        stringResource(R.string.launcher_quick_unhide)
                    } else {
                        stringResource(R.string.launcher_quick_hide)
                    },
                    color = ZincSecondary,
                    onClick = onToggleHide
                )
                QuickActionItem(
                    text = stringResource(R.string.launcher_quick_detox_2h),
                    color = SignalVermilion,
                    onClick = onDetoxLock2h
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.messages_cancel), color = ZincSecondary)
            }
        }
    )
}

@Composable
private fun QuickActionItem(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SlateContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
        )
    }
}

@Composable
fun RenameAppDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(app.packageName) { mutableStateOf(app.customLabel.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterInk,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = stringResource(R.string.launcher_rename_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 40) draft = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.launcher_rename_hint),
                        color = ZincMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlabasterInk,
                    unfocusedBorderColor = HairlineBorder,
                    focusedContainerColor = ObsidianCanvas,
                    unfocusedContainerColor = ObsidianCanvas,
                    cursorColor = AlabasterInk,
                    focusedTextColor = AlabasterInk,
                    unfocusedTextColor = AlabasterInk
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlabasterInk,
                    contentColor = ObsidianCanvas
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.messages_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.messages_cancel), color = ZincSecondary)
            }
        }
    )
}
