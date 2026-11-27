package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.DistractingCategory
import com.example.data.GuardDefaults
import com.example.data.GuardRuntimeSnapshot
import com.example.data.GuardRuntimeState
import com.example.data.GuardSettings
import com.example.data.GuardSettingsRepository
import com.example.data.InstalledAppInfo
import com.example.data.OverlayContent
import com.example.data.PermissionSnapshot
import com.example.launcher.MinimalistLauncherActivity
import com.example.service.ScreenGuardService
import com.example.util.DailyAppUsageLoader
import com.example.util.DeepWorkSilenceController
import com.example.util.DeviceAppEntry
import com.example.util.InstalledAppsLoader
import com.example.util.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuardUiState(
    val settings: GuardSettings = GuardSettings(),
    val runtime: GuardRuntimeSnapshot = GuardRuntimeSnapshot(),
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val isDefaultHomeLauncher: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val appSearchQuery: String = "",
    val previewOverlay: OverlayContent? = null,
    val snackbarMessage: String? = null
)

private data class LocalUiSlice(
    val permissions: PermissionSnapshot,
    val isDefaultHome: Boolean,
    val rawApps: List<DeviceAppEntry>,
    val isLoadingApps: Boolean,
    val todayUsageMinutes: Map<String, Int>,
    val searchQuery: String,
    val previewOverlay: OverlayContent?,
    val snackbarMessage: String?
)

class GuardViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = GuardSettingsRepository(appContext)

    private val permissionsFlow = MutableStateFlow(PermissionChecker.checkAll(appContext))
    private val isDefaultHomeFlow = MutableStateFlow(PermissionChecker.isDefaultHomeLauncher(appContext))
    private val rawAppsFlow = MutableStateFlow<List<DeviceAppEntry>>(emptyList())
    private val isLoadingAppsFlow = MutableStateFlow(true)
    private val todayUsageMinutesFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val appSearchQueryFlow = MutableStateFlow("")
    private val previewOverlayFlow = MutableStateFlow<OverlayContent?>(null)
    private val snackbarMessageFlow = MutableStateFlow<String?>(null)

    private val localPartA = combine(
        permissionsFlow,
        isDefaultHomeFlow,
        rawAppsFlow,
        isLoadingAppsFlow,
        todayUsageMinutesFlow
    ) { perms, isHome, apps, loading, usageMap ->
        LocalUiSlice(
            permissions = perms,
            isDefaultHome = isHome,
            rawApps = apps,
            isLoadingApps = loading,
            todayUsageMinutes = usageMap,
            searchQuery = "",
            previewOverlay = null,
            snackbarMessage = null
        )
    }

    private val localSliceFlow = combine(
        localPartA,
        appSearchQueryFlow,
        previewOverlayFlow,
        snackbarMessageFlow
    ) { base, query, preview, snack ->
        base.copy(
            searchQuery = query,
            previewOverlay = preview,
            snackbarMessage = snack
        )
    }

    val uiState: StateFlow<GuardUiState> = combine(
        repository.settingsFlow,
        GuardRuntimeState.snapshot,
        localSliceFlow
    ) { settings, runtime, local ->
        val now = System.currentTimeMillis()
        val favoritesSet = settings.launcherFavoritePackages.toSet()
        val mappedApps = local.rawApps.map { entry ->
            val blockedUntil = settings.blockedAppsUntilMillis[entry.packageName]?.takeIf { it > now }
            InstalledAppInfo(
                packageName = entry.packageName,
                label = entry.label,
                customLabel = settings.launcherRenamedApps[entry.packageName],
                isException = entry.packageName in settings.exceptionPackages,
                blockedUntilMillis = blockedUntil,
                isPrompted = entry.packageName in settings.promptedPackages,
                isPinnedFavorite = entry.packageName in favoritesSet,
                isHiddenInLauncher = entry.packageName in settings.launcherHiddenPackages,
                todayUsageMinutes = local.todayUsageMinutes[entry.packageName] ?: 0,
                category = entry.category
            )
        }
        val effectiveTarget = if (runtime.activeSessionPackage != null) {
            runtime.targetSeconds
        } else {
            GuardDefaults.effectiveIntervalSeconds(
                baseMinutes = settings.intervalMinutes,
                consecutiveOverrides = runtime.consecutiveOverrides,
                escalatingEnabled = settings.escalatingFrictionEnabled
            )
        }
        GuardUiState(
            settings = settings,
            runtime = runtime.copy(targetSeconds = effectiveTarget),
            permissions = local.permissions,
            isDefaultHomeLauncher = local.isDefaultHome,
            installedApps = mappedApps,
            isLoadingApps = local.isLoadingApps,
            appSearchQuery = local.searchQuery,
            previewOverlay = local.previewOverlay,
            snackbarMessage = local.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GuardUiState(
            permissions = permissionsFlow.value,
            isDefaultHomeLauncher = isDefaultHomeFlow.value
        )
    )

    init {
        refreshInstalledApps()
        refreshDailyUsage()
        syncServiceWithPersistedState()
    }

    fun refreshPermissions() {
        val fresh = PermissionChecker.checkAll(appContext)
        permissionsFlow.value = fresh
        isDefaultHomeFlow.value = PermissionChecker.isDefaultHomeLauncher(appContext)
        refreshDailyUsage()
        viewModelScope.launch {
            repository.pruneExpiredDetoxLocks()
            cleanupExpiredDeepWorkIfNeeded()
            val persisted = repository.getCurrentSettings()
            if (persisted.isGuardEnabled && !fresh.canArmGuard) {
                repository.setGuardEnabled(false)
                ScreenGuardService.stop(appContext)
            } else if (persisted.hasActiveEnforcement() && fresh.canArmGuard) {
                ScreenGuardService.start(appContext)
            } else if (!persisted.hasActiveEnforcement()) {
                ScreenGuardService.stop(appContext)
            }
        }
    }

    fun refreshDailyUsage() {
        viewModelScope.launch {
            todayUsageMinutesFlow.value = DailyAppUsageLoader.loadTodayUsageMinutes(appContext)
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            isLoadingAppsFlow.value = rawAppsFlow.value.isEmpty()
            rawAppsFlow.value = InstalledAppsLoader.loadLaunchableApps(appContext)
            isLoadingAppsFlow.value = false
        }
    }

    fun toggleGuard(enabled: Boolean) {
        val currentPermissions = PermissionChecker.checkAll(appContext)
        permissionsFlow.value = currentPermissions

        viewModelScope.launch {
            if (enabled) {
                if (!currentPermissions.canArmGuard) {
                    snackbarMessageFlow.value =
                        appContext.getString(R.string.guard_perm_missing_banner)
                    return@launch
                }
                repository.setGuardEnabled(true)
                ScreenGuardService.start(appContext)
            } else {
                repository.setGuardEnabled(false)
                syncEnforcementServiceLocked(currentPermissions)
            }
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch { repository.setIntervalMinutes(minutes) }
    }

    fun setEscalatingFrictionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEscalatingFrictionEnabled(enabled) }
    }

    fun triggerTestTakeover() {
        val state = uiState.value
        val hasOverlay = PermissionChecker.hasOverlayPermission(appContext)
        if (hasOverlay) {
            ScreenGuardService.triggerSystemTestTakeover(appContext)
        } else {
            val session = GuardRuntimeState.createSession(
                messages = state.settings.reminderMessages,
                consecutiveOverrides = state.runtime.consecutiveOverrides,
                escalatingEnabled = state.settings.escalatingFrictionEnabled,
                isPreview = true
            )
            previewOverlayFlow.value = OverlayContent.Takeover(session)
        }
    }

    fun triggerTestHowLongPrompt() {
        val hasOverlay = PermissionChecker.hasOverlayPermission(appContext)
        if (hasOverlay) {
            ScreenGuardService.triggerSystemTestHowLong(appContext)
        } else {
            previewOverlayFlow.value = OverlayContent.HowLongPrompt(
                packageName = appContext.packageName,
                appLabel = "Sample App",
                isPreview = true
            )
        }
    }

    fun dismissPreviewOverlay() {
        previewOverlayFlow.value = null
    }

    // App Rules: Exceptions, Detox Lock, How Long? Prompt
    fun toggleExceptionPackage(packageName: String) {
        viewModelScope.launch { repository.toggleExceptionPackage(packageName) }
    }

    fun togglePromptedPackage(packageName: String) {
        viewModelScope.launch {
            repository.togglePromptedPackage(packageName)
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            val persisted = repository.getCurrentSettings()
            if (packageName in persisted.promptedPackages && !perms.canArmGuard) {
                snackbarMessageFlow.value = appContext.getString(R.string.guard_perm_missing_banner)
            }
            syncEnforcementServiceLocked(perms, persisted)
        }
    }

    fun setDetoxLock(packageName: String, durationMillis: Long, durationLabel: String) {
        viewModelScope.launch {
            repository.setDetoxLock(packageName, durationMillis)
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            val persisted = repository.getCurrentSettings()
            val appLabel = PermissionChecker.resolveAppLabel(
                appContext,
                packageName,
                persisted.launcherRenamedApps
            )
            snackbarMessageFlow.value = if (perms.canArmGuard) {
                appContext.getString(
                    R.string.apps_detox_locked_snack,
                    appLabel,
                    durationLabel
                )
            } else {
                appContext.getString(R.string.guard_perm_missing_banner)
            }
            syncEnforcementServiceLocked(perms, persisted)
        }
    }

    fun clearDetoxLock(packageName: String) {
        viewModelScope.launch {
            repository.clearDetoxLock(packageName)
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            val persisted = repository.getCurrentSettings()
            val appLabel = PermissionChecker.resolveAppLabel(
                appContext,
                packageName,
                persisted.launcherRenamedApps
            )
            snackbarMessageFlow.value = appContext.getString(
                R.string.apps_detox_unlocked_snack,
                appLabel
            )
            syncEnforcementServiceLocked(perms, persisted)
        }
    }

    // Minimalist Launcher customization
    fun toggleLauncherFavorite(packageName: String) {
        viewModelScope.launch {
            repository.toggleLauncherFavorite(packageName)
        }
    }

    fun toggleLauncherHidden(packageName: String) {
        viewModelScope.launch { repository.toggleLauncherHidden(packageName) }
    }

    fun setLauncherCustomLabel(packageName: String, customLabel: String) {
        viewModelScope.launch { repository.setLauncherCustomLabel(packageName, customLabel) }
    }

    fun setShowGuardStatusOnHome(show: Boolean) {
        viewModelScope.launch { repository.setShowGuardStatusOnHome(show) }
    }

    fun setHomeFocusMantra(text: String) {
        viewModelScope.launch { repository.setHomeFocusMantra(text) }
    }

    fun openDefaultHomeSettings() {
        PermissionChecker.openDefaultHomeSettings(appContext)
    }

    fun openMinimalistHomeNow() {
        val intent = Intent(appContext, MinimalistLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    // Deep Work Mode
    fun startDeepWork(
        untilMillis: Long,
        categories: Set<DistractingCategory>,
        strictAllNonExempt: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val safeUntil = untilMillis.coerceAtLeast(now + 60_000L)
            val savedFilter = DeepWorkSilenceController.enableSilence(appContext)
            repository.startDeepWork(
                untilMillis = safeUntil,
                categories = categories,
                strictAllNonExempt = strictAllNonExempt,
                savedInterruptionFilter = savedFilter
            )
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            val persisted = repository.getCurrentSettings()
            syncEnforcementServiceLocked(perms, persisted)
        }
    }

    fun extendDeepWork(extraMinutes: Int) {
        viewModelScope.launch {
            repository.extendDeepWork(extraMinutes * 60_000L)
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            syncEnforcementServiceLocked(perms)
        }
    }

    fun endDeepWorkEarly() {
        viewModelScope.launch {
            val savedFilter = repository.clearDeepWork()
            DeepWorkSilenceController.restoreSilence(appContext, savedFilter)
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            syncEnforcementServiceLocked(perms)
        }
    }

    fun openDndPolicyAccessSettings() {
        PermissionChecker.openDndPolicyAccessSettings(appContext)
    }

    // Reminder Messages CRUD
    fun addMessage(text: String) {
        viewModelScope.launch {
            if (repository.addMessage(text)) {
                snackbarMessageFlow.value = appContext.getString(R.string.messages_added)
            }
        }
    }

    fun updateMessage(index: Int, text: String) {
        viewModelScope.launch {
            if (repository.updateMessage(index, text)) {
                snackbarMessageFlow.value = appContext.getString(R.string.messages_updated)
            }
        }
    }

    fun deleteMessage(index: Int) {
        viewModelScope.launch {
            val deleted = repository.deleteMessage(index)
            snackbarMessageFlow.value = if (deleted) {
                appContext.getString(R.string.messages_deleted)
            } else {
                appContext.getString(R.string.messages_last_guard)
            }
        }
    }

    fun resetDefaultMessages() {
        viewModelScope.launch {
            repository.resetDefaultMessages()
            snackbarMessageFlow.value = appContext.getString(R.string.messages_restored)
        }
    }

    fun updateAppSearchQuery(query: String) {
        appSearchQueryFlow.value = query
    }

    fun clearSnackbar() {
        snackbarMessageFlow.value = null
    }

    fun openOverlaySettings() {
        PermissionChecker.openOverlaySettings(appContext)
    }

    fun openUsageAccessSettings() {
        PermissionChecker.openUsageAccessSettings(appContext)
    }

    private fun syncServiceWithPersistedState() {
        viewModelScope.launch {
            repository.pruneExpiredDetoxLocks()
            cleanupExpiredDeepWorkIfNeeded()
            val persisted = repository.getCurrentSettings()
            val perms = PermissionChecker.checkAll(appContext)
            permissionsFlow.value = perms
            if (persisted.isGuardEnabled && !perms.canArmGuard) {
                repository.setGuardEnabled(false)
                ScreenGuardService.stop(appContext)
            } else {
                syncEnforcementServiceLocked(perms, persisted)
            }
        }
    }

    private suspend fun cleanupExpiredDeepWorkIfNeeded() {
        val persisted = repository.getCurrentSettings()
        if (persisted.deepWorkUntilMillis > 0L && !persisted.isDeepWorkActive()) {
            val savedFilter = repository.clearDeepWork()
            DeepWorkSilenceController.restoreSilence(appContext, savedFilter)
        }
    }

    private suspend fun syncEnforcementServiceLocked(
        perms: PermissionSnapshot,
        settings: GuardSettings? = null
    ) {
        val current = settings ?: repository.getCurrentSettings()
        if (current.hasActiveEnforcement() && perms.canArmGuard) {
            ScreenGuardService.start(appContext)
        } else {
            ScreenGuardService.stop(appContext)
        }
    }
}
