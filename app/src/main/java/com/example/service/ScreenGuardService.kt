package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.GuardDefaults
import com.example.data.GuardRuntimeState
import com.example.data.GuardSettings
import com.example.data.GuardSettingsRepository
import com.example.data.GuardStatus
import com.example.data.OverlayContent
import com.example.ui.takeover.OverlayActivity
import com.example.util.AppCategoryClassifier
import com.example.util.AppForegroundTracker
import com.example.util.DeepWorkSilenceController
import com.example.util.ForegroundAppDetector
import com.example.util.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: GuardSettingsRepository
    private lateinit var foregroundDetector: ForegroundAppDetector
    private lateinit var notificationManager: NotificationManager

    private val sessionTracker = GuardSessionTracker()

    @Volatile
    private var latestSettings: GuardSettings = GuardSettings()

    @Volatile
    private var currentStatus: GuardStatus = GuardStatus.MONITORING

    @Volatile
    private var isPreviewMode: Boolean = false

    @Volatile
    private var isOverlayActive: Boolean = false

    @Volatile
    private var overlayRequestedAtElapsedMs: Long = 0L

    @Volatile
    private var activeOverlayPackage: String? = null

    @Volatile
    private var activeOverlayLabel: String? = null

    @Volatile
    private var lastNotificationUpdateElapsedMs: Long = 0L

    private var settingsJob: Job? = null
    private var monitorJob: Job? = null
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOffReset()
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    if (foregroundDetector.isScreenUnlockedAndInteractive() && !isOverlayActive) {
                        sessionTracker.resetTickClock()
                        currentStatus = if (latestSettings.isGuardEnabled) {
                            GuardStatus.MONITORING
                        } else {
                            GuardStatus.DISARMED
                        }
                        publishRuntimeState(foregroundPkg = null, displayedSession = null)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = GuardSettingsRepository(applicationContext)
        foregroundDetector = ForegroundAppDetector(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        startForegroundCompat(buildNotification())
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                isPreviewMode = false
                stopGuardAndSelf()
                return START_NOT_STICKY
            }

            ACTION_TEST_TAKEOVER -> {
                isPreviewMode = true
                ensureSettingsObserver()
                serviceScope.launch {
                    latestSettings = repository.getCurrentSettings()
                    withContext(Dispatchers.Main) {
                        triggerTakeover(
                            isPreview = true,
                            sessionPackage = null,
                            appLabel = null
                        )
                    }
                }
                return START_NOT_STICKY
            }

            ACTION_TEST_HOW_LONG -> {
                isPreviewMode = true
                ensureSettingsObserver()
                serviceScope.launch {
                    latestSettings = repository.getCurrentSettings()
                    withContext(Dispatchers.Main) {
                        triggerHowLongPrompt(
                            pkg = packageName,
                            appLabel = "Sample App",
                            isPreview = true
                        )
                    }
                }
                return START_NOT_STICKY
            }

            else -> {
                isPreviewMode = false
                ensureSettingsObserver()
                ensureMonitoringLoop()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        settingsJob?.cancel()
        monitorJob?.cancel()
        isPreviewMode = false
        clearActiveOverlayState(dismissCoordinator = true)
        sessionTracker.resetOnScreenOff()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            receiverRegistered = false
        }
        GuardRuntimeState.resetToDisarmed(latestSettings.intervalMinutes)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureSettingsObserver() {
        if (settingsJob?.isActive == true) return
        settingsJob = serviceScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                latestSettings = settings
                if (!settings.hasActiveEnforcement() && !isPreviewMode) {
                    withContext(Dispatchers.Main) {
                        stopGuardAndSelf()
                    }
                } else {
                    publishRuntimeState(
                        foregroundPkg = GuardRuntimeState.snapshot.value.currentForegroundPackage,
                        displayedSession = null
                    )
                    updateNotification(force = true)
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun handleScreenOffReset() {
        sessionTracker.resetOnScreenOff()
        clearActiveOverlayState(dismissCoordinator = true)
        foregroundDetector.clearCachedPackage()

        if (isPreviewMode && !latestSettings.hasActiveEnforcement()) {
            isPreviewMode = false
            stopGuardAndSelf()
            return
        }
        isPreviewMode = false
        currentStatus = GuardStatus.SCREEN_OFF
        GuardRuntimeState.update {
            it.copy(
                status = GuardStatus.SCREEN_OFF,
                elapsedSeconds = 0,
                targetSeconds = latestSettings.intervalMinutes * 60,
                consecutiveOverrides = 0,
                currentForegroundPackage = null,
                activeSessionPackage = null,
                activeSessionLabel = null,
                activeTakeover = null
            )
        }
        updateNotification(force = true)
    }

    private fun ensureMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        sessionTracker.resetTickClock()
        monitorJob = serviceScope.launch {
            latestSettings = repository.getCurrentSettings()
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                tick()
            }
        }
    }

    private suspend fun tick() {
        val settings = latestSettings
        val nowEpochMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()

        // Automatically finish Deep Work and restore previous DND state when the target time arrives.
        if (settings.deepWorkUntilMillis > 0L && nowEpochMs >= settings.deepWorkUntilMillis) {
            val savedFilter = repository.clearDeepWork()
            DeepWorkSilenceController.restoreSilence(applicationContext, savedFilter)
            latestSettings = settings.copy(
                deepWorkUntilMillis = 0L,
                deepWorkSavedInterruptionFilter = DeepWorkSilenceController.NO_SAVED_FILTER
            )
            updateNotification(force = true)
            if (!latestSettings.hasActiveEnforcement(nowEpochMs) && !isPreviewMode) {
                withContext(Dispatchers.Main) { stopGuardAndSelf() }
                return
            }
        }

        if (!latestSettings.hasActiveEnforcement(nowEpochMs) && !isPreviewMode) {
            withContext(Dispatchers.Main) { stopGuardAndSelf() }
            return
        }

        if (!foregroundDetector.isScreenUnlockedAndInteractive()) {
            if (sessionTracker.getGlobalElapsedSeconds() != 0 ||
                sessionTracker.getConsecutiveOverrides() != 0 ||
                isOverlayActive ||
                currentStatus != GuardStatus.SCREEN_OFF
            ) {
                withContext(Dispatchers.Main) { handleScreenOffReset() }
            }
            return
        }

        // Watchdog: if an overlay was requested or active, wait while it's genuinely visible;
        // recover automatically if the Activity failed to open or was unexpectedly dismissed.
        if (isOverlayActive) {
            val coordinatorHasRequest = OverlayCoordinator.activeRequest.value != null
            val overlayVisible = AppForegroundTracker.isOverlayActivityVisible
            val launchAgeMs = nowElapsedMs - overlayRequestedAtElapsedMs

            if (overlayVisible || (coordinatorHasRequest && launchAgeMs < OVERLAY_LAUNCH_WATCHDOG_MS)) {
                sessionTracker.resetTickClock()
                return
            }

            // Stale overlay state detected — self-heal so enforcement never freezes!
            clearActiveOverlayState(dismissCoordinator = true)
        }

        val deltaMillis = sessionTracker.consumeDeltaMillis(nowElapsedMs)
        val foregroundPkg = foregroundDetector.getCurrentForegroundPackage()

        // Priority 1: Timed Detox Lock & Deep Work Category Restriction — immediate hard-block.
        if (foregroundPkg != null && foregroundPkg != packageName) {
            val blockedUntil = settings.blockedAppsUntilMillis[foregroundPkg]
            if (blockedUntil != null && blockedUntil > nowEpochMs) {
                val appLabel = PermissionChecker.resolveAppLabel(
                    applicationContext,
                    foregroundPkg,
                    settings.launcherRenamedApps
                )
                withContext(Dispatchers.Main) {
                    triggerDetoxBlocked(
                        pkg = foregroundPkg,
                        appLabel = appLabel,
                        blockedUntilMillis = blockedUntil,
                        isDeepWork = false,
                        categoryLabel = null
                    )
                }
                return
            }

            if (settings.isDeepWorkActive(nowEpochMs)) {
                val category = AppCategoryClassifier.classifyPackage(applicationContext, foregroundPkg)
                if (settings.isRestrictedByDeepWork(foregroundPkg, category, nowEpochMs)) {
                    val appLabel = PermissionChecker.resolveAppLabel(
                        applicationContext,
                        foregroundPkg,
                        settings.launcherRenamedApps
                    )
                    withContext(Dispatchers.Main) {
                        triggerDetoxBlocked(
                            pkg = foregroundPkg,
                            appLabel = appLabel,
                            blockedUntilMillis = settings.deepWorkUntilMillis,
                            isDeepWork = true,
                            categoryLabel = category?.displayName
                        )
                    }
                    return
                }
            }
        }

        // Priority 2: Per-App "How Long?" Prompt & Persistent Per-App Session Countdown
        // (Works even when the main global Guard switch is OFF!)
        if (foregroundPkg != null &&
            foregroundPkg != packageName &&
            foregroundPkg in settings.promptedPackages
        ) {
            val existingSession = sessionTracker.getSession(foregroundPkg)
            if (existingSession == null) {
                val appLabel = PermissionChecker.resolveAppLabel(
                    applicationContext,
                    foregroundPkg,
                    settings.launcherRenamedApps
                )
                withContext(Dispatchers.Main) {
                    triggerHowLongPrompt(foregroundPkg, appLabel, isPreview = false)
                }
                return
            }

            // Resume & advance this app's persistent session timer using real monotonic time.
            val updatedSession = sessionTracker.advanceSession(foregroundPkg, deltaMillis) ?: existingSession
            currentStatus = GuardStatus.MONITORING
            if (updatedSession.isExpired) {
                withContext(Dispatchers.Main) {
                    triggerTakeover(
                        isPreview = false,
                        sessionPackage = updatedSession.packageName,
                        appLabel = updatedSession.appLabel
                    )
                }
            } else {
                publishRuntimeState(foregroundPkg = foregroundPkg, displayedSession = updatedSession)
                updateNotification(force = false, displayedSession = updatedSession)
            }
            return
        }

        // Priority 3: Global Usage Guard Countdown (only when master Usage Guard switch is ON)
        if (!settings.isGuardEnabled) {
            currentStatus = GuardStatus.DISARMED
            publishRuntimeState(foregroundPkg = foregroundPkg, displayedSession = null)
            updateNotification(force = false)
            return
        }

        when {
            foregroundPkg == packageName -> {
                currentStatus = GuardStatus.PAUSED_IN_APP
                publishRuntimeState(foregroundPkg = foregroundPkg, displayedSession = null)
                updateNotification(force = false)
            }

            foregroundPkg != null && foregroundPkg in settings.exceptionPackages -> {
                currentStatus = GuardStatus.PAUSED_EXCEPTION
                publishRuntimeState(foregroundPkg = foregroundPkg, displayedSession = null)
                updateNotification(force = false)
            }

            else -> {
                currentStatus = GuardStatus.MONITORING
                val elapsedSec = sessionTracker.advanceGlobal(deltaMillis)
                val targetSec = globalTargetSeconds(settings)
                if (elapsedSec >= targetSec) {
                    withContext(Dispatchers.Main) {
                        triggerTakeover(
                            isPreview = false,
                            sessionPackage = null,
                            appLabel = null
                        )
                    }
                } else {
                    publishRuntimeState(foregroundPkg = foregroundPkg, displayedSession = null)
                    updateNotification(force = false)
                }
            }
        }
    }

    private fun globalTargetSeconds(settings: GuardSettings): Int {
        return GuardDefaults.effectiveIntervalSeconds(
            baseMinutes = settings.intervalMinutes,
            consecutiveOverrides = sessionTracker.getConsecutiveOverrides(),
            escalatingEnabled = settings.escalatingFrictionEnabled
        )
    }

    private fun triggerDetoxBlocked(
        pkg: String,
        appLabel: String,
        blockedUntilMillis: Long,
        isDeepWork: Boolean = false,
        categoryLabel: String? = null
    ) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) return

        markOverlayLaunching(pkg, appLabel, GuardStatus.DETOX_BLOCKED)
        publishRuntimeState(foregroundPkg = pkg, displayedSession = null)
        updateNotification(force = true)

        val request = ActiveOverlayRequest(
            content = OverlayContent.DetoxBlocked(
                packageName = pkg,
                appLabel = appLabel,
                blockedUntilMillis = blockedUntilMillis,
                isDeepWork = isDeepWork,
                categoryLabel = categoryLabel
            ),
            launchHomeOnReturn = true,
            onReturnHome = {
                foregroundDetector.markReturnedHomeFrom(pkg)
                clearActiveOverlayState(dismissCoordinator = false)
                sessionTracker.resetTickClock()
                currentStatus = if (latestSettings.isGuardEnabled) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                publishRuntimeState(foregroundPkg = null, displayedSession = null)
                updateNotification(force = true)
            }
        )
        launchOverlayActivity(request)
    }

    private fun triggerHowLongPrompt(pkg: String, appLabel: String, isPreview: Boolean) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) {
            isPreviewMode = false
            clearActiveOverlayState(dismissCoordinator = true)
            if (!latestSettings.hasActiveEnforcement()) stopGuardAndSelf()
            return
        }

        markOverlayLaunching(pkg, appLabel, GuardStatus.AWAITING_SESSION_PROMPT)
        publishRuntimeState(foregroundPkg = pkg, displayedSession = null)
        updateNotification(force = true)

        val request = ActiveOverlayRequest(
            content = OverlayContent.HowLongPrompt(
                packageName = pkg,
                appLabel = appLabel,
                isPreview = isPreview
            ),
            launchHomeOnReturn = !isPreview,
            onDurationSelected = { chosenMinutes ->
                clearActiveOverlayState(dismissCoordinator = false)
                isPreviewMode = false
                sessionTracker.resetTickClock()
                val createdSession = if (!isPreview) {
                    sessionTracker.startOrReplaceSession(
                        packageName = pkg,
                        appLabel = appLabel,
                        targetSeconds = chosenMinutes * 60
                    )
                } else {
                    null
                }
                currentStatus = if (latestSettings.isGuardEnabled || createdSession != null) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                publishRuntimeState(
                    foregroundPkg = if (isPreview) null else pkg,
                    displayedSession = createdSession
                )
                if (!latestSettings.hasActiveEnforcement()) {
                    stopGuardAndSelf()
                } else {
                    updateNotification(force = true, displayedSession = createdSession)
                }
            },
            onReturnHome = {
                if (!isPreview) {
                    foregroundDetector.markReturnedHomeFrom(pkg)
                }
                clearActiveOverlayState(dismissCoordinator = false)
                isPreviewMode = false
                sessionTracker.resetTickClock()
                currentStatus = if (latestSettings.isGuardEnabled) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                publishRuntimeState(foregroundPkg = null, displayedSession = null)
                if (!latestSettings.hasActiveEnforcement()) {
                    stopGuardAndSelf()
                } else {
                    updateNotification(force = true)
                }
            }
        )
        launchOverlayActivity(request)
    }

    private fun triggerTakeover(
        isPreview: Boolean,
        sessionPackage: String?,
        appLabel: String?
    ) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) {
            isPreviewMode = false
            clearActiveOverlayState(dismissCoordinator = true)
            if (!latestSettings.hasActiveEnforcement()) {
                stopGuardAndSelf()
            } else {
                publishRuntimeState(foregroundPkg = null, displayedSession = null)
            }
            return
        }

        val overrides = sessionTracker.getConsecutiveOverrides()
        val session = GuardRuntimeState.createSession(
            messages = latestSettings.reminderMessages,
            consecutiveOverrides = overrides,
            escalatingEnabled = latestSettings.escalatingFrictionEnabled,
            appLabel = appLabel,
            isPreview = isPreview
        )

        markOverlayLaunching(sessionPackage, appLabel, GuardStatus.TAKEOVER_ACTIVE)
        val activeAppSession = sessionPackage?.let { sessionTracker.getSession(it) }
        val displayedTarget = activeAppSession?.targetSeconds ?: globalTargetSeconds(latestSettings)
        val displayedElapsed = if (isPreview) {
            sessionTracker.getGlobalElapsedSeconds()
        } else {
            displayedTarget
        }

        GuardRuntimeState.update {
            it.copy(
                status = GuardStatus.TAKEOVER_ACTIVE,
                elapsedSeconds = displayedElapsed,
                targetSeconds = displayedTarget,
                consecutiveOverrides = overrides,
                activeSessionPackage = activeAppSession?.packageName,
                activeSessionLabel = activeAppSession?.appLabel,
                activeTakeover = session
            )
        }
        updateNotification(force = true)

        val request = ActiveOverlayRequest(
            content = OverlayContent.Takeover(session),
            launchHomeOnReturn = false,
            onTakeoverConfirmed = {
                clearActiveOverlayState(dismissCoordinator = false)
                isPreviewMode = false
                sessionTracker.resetTickClock()
                if (!isPreview) {
                    sessionTracker.recordTakeoverOverride(
                        forSessionPackage = sessionPackage,
                        forSessionLabel = appLabel,
                        baseIntervalMinutes = latestSettings.intervalMinutes,
                        escalatingEnabled = latestSettings.escalatingFrictionEnabled
                    )
                }
                val nextSession = sessionPackage?.let { sessionTracker.getSession(it) }
                currentStatus = if (latestSettings.isGuardEnabled || nextSession != null) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                GuardRuntimeState.update {
                    it.copy(
                        status = currentStatus,
                        elapsedSeconds = nextSession?.elapsedSeconds ?: sessionTracker.getGlobalElapsedSeconds(),
                        targetSeconds = nextSession?.targetSeconds ?: globalTargetSeconds(latestSettings),
                        consecutiveOverrides = sessionTracker.getConsecutiveOverrides(),
                        activeSessionPackage = nextSession?.packageName,
                        activeSessionLabel = nextSession?.appLabel,
                        activeTakeover = null
                    )
                }
                if (!latestSettings.hasActiveEnforcement()) {
                    stopGuardAndSelf()
                } else {
                    updateNotification(force = true, displayedSession = nextSession)
                }
            },
            onAbandonedToBackground = {
                // User pressed Home without typing the challenge word.
                // Keep elapsed time intact so reopening a non-exempt app immediately re-enforces.
                clearActiveOverlayState(dismissCoordinator = false)
                isPreviewMode = false
                sessionTracker.resetTickClock()
                currentStatus = if (latestSettings.isGuardEnabled) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                GuardRuntimeState.update { it.copy(status = currentStatus, activeTakeover = null) }
                if (!latestSettings.hasActiveEnforcement()) {
                    stopGuardAndSelf()
                } else {
                    updateNotification(force = true)
                }
            }
        )
        launchOverlayActivity(request)
    }

    private fun markOverlayLaunching(pkg: String?, label: String?, status: GuardStatus) {
        isOverlayActive = true
        overlayRequestedAtElapsedMs = SystemClock.elapsedRealtime()
        activeOverlayPackage = pkg
        activeOverlayLabel = label
        currentStatus = status
    }

    private fun clearActiveOverlayState(dismissCoordinator: Boolean) {
        isOverlayActive = false
        overlayRequestedAtElapsedMs = 0L
        activeOverlayPackage = null
        activeOverlayLabel = null
        if (dismissCoordinator) {
            OverlayCoordinator.clear()
        }
    }

    private fun launchOverlayActivity(request: ActiveOverlayRequest) {
        OverlayCoordinator.publish(request)
        val intent = Intent(applicationContext, OverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            clearActiveOverlayState(dismissCoordinator = true)
            isPreviewMode = false
            currentStatus = if (latestSettings.isGuardEnabled) {
                GuardStatus.MONITORING
            } else {
                GuardStatus.DISARMED
            }
            GuardRuntimeState.update { it.copy(status = currentStatus, activeTakeover = null) }
            if (!latestSettings.hasActiveEnforcement()) {
                stopGuardAndSelf()
            }
        }
    }

    private fun publishRuntimeState(
        foregroundPkg: String?,
        displayedSession: ActiveAppSession?
    ) {
        val elapsed = displayedSession?.elapsedSeconds ?: sessionTracker.getGlobalElapsedSeconds()
        val target = displayedSession?.targetSeconds ?: globalTargetSeconds(latestSettings)
        GuardRuntimeState.update {
            it.copy(
                status = currentStatus,
                elapsedSeconds = elapsed,
                targetSeconds = target,
                consecutiveOverrides = sessionTracker.getConsecutiveOverrides(),
                currentForegroundPackage = foregroundPkg,
                activeSessionPackage = displayedSession?.packageName,
                activeSessionLabel = displayedSession?.appLabel,
                activeTakeover = if (currentStatus == GuardStatus.TAKEOVER_ACTIVE) it.activeTakeover else null
            )
        }
    }

    private fun stopGuardAndSelf() {
        monitorJob?.cancel()
        monitorJob = null
        isPreviewMode = false
        clearActiveOverlayState(dismissCoordinator = true)
        sessionTracker.resetOnScreenOff()
        foregroundDetector.clearCachedPackage()
        currentStatus = GuardStatus.DISARMED
        GuardRuntimeState.resetToDisarmed(latestSettings.intervalMinutes)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        force: Boolean = false,
        displayedSession: ActiveAppSession? = null
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && nowElapsed - lastNotificationUpdateElapsedMs < 2_000L) {
            return
        }
        lastNotificationUpdateElapsedMs = nowElapsed
        if (PermissionChecker.hasNotificationPermission(applicationContext)) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(displayedSession))
        }
    }

    private fun buildNotification(displayedSession: ActiveAppSession? = null): Notification {
        val activeOverlay = OverlayCoordinator.activeRequest.value
        val shouldRouteToOverlay = activeOverlay != null && (
            currentStatus == GuardStatus.TAKEOVER_ACTIVE ||
                currentStatus == GuardStatus.DETOX_BLOCKED
            )

        val tapTargetIntent = if (shouldRouteToOverlay) {
            Intent(this, OverlayActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        } else {
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            if (shouldRouteToOverlay) 2 else 0,
            tapTargetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settings = latestSettings
        val elapsedSeconds = displayedSession?.elapsedSeconds ?: sessionTracker.getGlobalElapsedSeconds()
        val targetSeconds = displayedSession?.targetSeconds ?: globalTargetSeconds(settings)
        val elapsedFormatted = GuardDefaults.formatDuration(elapsedSeconds)
        val targetFormatted = GuardDefaults.formatDuration(targetSeconds)

        val nowEpoch = System.currentTimeMillis()
        val deepWorkActive = settings.isDeepWorkActive(nowEpoch)
        val deepWorkRemaining = GuardDefaults.formatRemainingLock(settings.deepWorkUntilMillis - nowEpoch)

        val title = when {
            deepWorkActive -> getString(R.string.notif_title_deep_work)
            settings.isGuardEnabled -> getString(R.string.notif_title_monitoring)
            else -> getString(R.string.notif_title_rules_active)
        }

        val bodyText = when {
            currentStatus == GuardStatus.SCREEN_OFF && deepWorkActive ->
                getString(R.string.notif_text_deep_work, deepWorkRemaining)
            currentStatus == GuardStatus.SCREEN_OFF -> getString(R.string.notif_text_screen_off)
            currentStatus == GuardStatus.TAKEOVER_ACTIVE -> getString(R.string.notif_text_takeover)
            currentStatus == GuardStatus.DETOX_BLOCKED && deepWorkActive ->
                getString(R.string.notif_text_deep_work_blocked, deepWorkRemaining)
            currentStatus == GuardStatus.DETOX_BLOCKED -> getString(R.string.notif_text_detox)
            currentStatus == GuardStatus.AWAITING_SESSION_PROMPT -> getString(R.string.notif_text_prompt)
            deepWorkActive -> getString(R.string.notif_text_deep_work, deepWorkRemaining)
            displayedSession != null -> getString(
                R.string.notif_text_session_active,
                displayedSession.appLabel,
                elapsedFormatted,
                targetFormatted
            )
            !settings.isGuardEnabled -> getString(
                R.string.notif_text_rules_active,
                settings.activeBlockedApps().size,
                settings.promptedPackages.size
            )
            currentStatus == GuardStatus.PAUSED_EXCEPTION ->
                getString(R.string.notif_text_paused_exception, elapsedFormatted)
            currentStatus == GuardStatus.PAUSED_IN_APP ->
                getString(R.string.notif_text_paused_in_app, elapsedFormatted)
            else -> getString(
                R.string.notif_text_monitoring,
                targetFormatted,
                elapsedFormatted
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screenstop)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screenstop_guard_channel"
        private const val NOTIFICATION_ID = 4107
        private const val POLL_INTERVAL_MS = 500L
        private const val OVERLAY_LAUNCH_WATCHDOG_MS = 2_500L

        const val ACTION_START = "com.example.action.START_GUARD"
        const val ACTION_STOP = "com.example.action.STOP_GUARD"
        const val ACTION_TEST_TAKEOVER = "com.example.action.TEST_TAKEOVER"
        const val ACTION_TEST_HOW_LONG = "com.example.action.TEST_HOW_LONG"

        fun start(context: Context) {
            val intent = Intent(context, ScreenGuardService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenGuardService::class.java).setAction(ACTION_STOP)
            context.stopService(intent)
        }

        fun triggerSystemTestTakeover(context: Context) {
            val intent = Intent(context, ScreenGuardService::class.java).setAction(ACTION_TEST_TAKEOVER)
            ContextCompat.startForegroundService(context, intent)
        }

        fun triggerSystemTestHowLong(context: Context) {
            val intent = Intent(context, ScreenGuardService::class.java).setAction(ACTION_TEST_HOW_LONG)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
