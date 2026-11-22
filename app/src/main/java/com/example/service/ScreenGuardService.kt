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
    private lateinit var overlayController: TakeoverOverlayController
    private lateinit var notificationManager: NotificationManager

    @Volatile
    private var latestSettings: GuardSettings = GuardSettings()

    @Volatile
    private var elapsedSeconds: Int = 0

    @Volatile
    private var consecutiveOverrides: Int = 0

    @Volatile
    private var currentStatus: GuardStatus = GuardStatus.MONITORING

    @Volatile
    private var isPreviewMode: Boolean = false

    @Volatile
    private var isOverlayActive: Boolean = false

    // Per-app "How Long?" session state
    @Volatile
    private var activeSessionPackage: String? = null

    @Volatile
    private var activeSessionLabel: String? = null

    @Volatile
    private var activeSessionTargetSeconds: Int? = null

    @Volatile
    private var lastObservedPackage: String? = null

    private var settingsJob: Job? = null
    private var monitorJob: Job? = null
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOffReset()
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    if (foregroundDetector.isScreenUnlockedAndInteractive() && !isOverlayActive) {
                        currentStatus = GuardStatus.MONITORING
                        publishRuntimeState(null)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = GuardSettingsRepository(applicationContext)
        foregroundDetector = ForegroundAppDetector(applicationContext)
        overlayController = TakeoverOverlayController(applicationContext)
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

            ACTION_DISARM -> {
                isPreviewMode = false
                serviceScope.launch {
                    repository.setGuardEnabled(false)
                    withContext(Dispatchers.Main) {
                        stopGuardAndSelf()
                    }
                }
                return START_NOT_STICKY
            }

            ACTION_TEST_TAKEOVER -> {
                isPreviewMode = true
                ensureSettingsObserver()
                serviceScope.launch {
                    latestSettings = repository.getCurrentSettings()
                    withContext(Dispatchers.Main) {
                        triggerTakeover(isPreview = true, appLabel = null)
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
        isOverlayActive = false
        overlayController.dismiss()
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
                if (!settings.isGuardEnabled && !isPreviewMode) {
                    withContext(Dispatchers.Main) {
                        stopGuardAndSelf()
                    }
                } else {
                    publishRuntimeState(GuardRuntimeState.snapshot.value.currentForegroundPackage)
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
        elapsedSeconds = 0
        consecutiveOverrides = 0
        activeSessionPackage = null
        activeSessionLabel = null
        activeSessionTargetSeconds = null
        lastObservedPackage = null
        isOverlayActive = false
        foregroundDetector.clearCachedPackage()
        if (overlayController.isShowing) {
            overlayController.dismiss()
        }
        if (isPreviewMode && !latestSettings.isGuardEnabled) {
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
        updateNotification()
    }

    private fun ensureMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            latestSettings = repository.getCurrentSettings()
            while (isActive) {
                delay(1_000L)
                tickOneSecond()
            }
        }
    }

    private suspend fun tickOneSecond() {
        val settings = latestSettings
        if (!settings.isGuardEnabled && !isPreviewMode) {
            withContext(Dispatchers.Main) { stopGuardAndSelf() }
            return
        }

        if (!foregroundDetector.isScreenUnlockedAndInteractive()) {
            if (elapsedSeconds != 0 || consecutiveOverrides != 0 || isOverlayActive || currentStatus != GuardStatus.SCREEN_OFF) {
                withContext(Dispatchers.Main) { handleScreenOffReset() }
            }
            return
        }

        if (isOverlayActive || overlayController.isShowing) {
            publishRuntimeState(GuardRuntimeState.snapshot.value.currentForegroundPackage)
            return
        }

        val foregroundPkg = foregroundDetector.getCurrentForegroundPackage()
        val now = System.currentTimeMillis()

        // If user switched away from an active per-app session, clear that session cleanly.
        if (activeSessionPackage != null && foregroundPkg != null && foregroundPkg != activeSessionPackage) {
            activeSessionPackage = null
            activeSessionLabel = null
            activeSessionTargetSeconds = null
        }

        // Priority 1: Timed Detox Lock — immediately hard-block without override.
        if (foregroundPkg != null && foregroundPkg != packageName) {
            val blockedUntil = settings.blockedAppsUntilMillis[foregroundPkg]
            if (blockedUntil != null && blockedUntil > now) {
                val appLabel = PermissionChecker.resolveAppLabel(
                    applicationContext,
                    foregroundPkg,
                    settings.launcherRenamedApps
                )
                withContext(Dispatchers.Main) {
                    triggerDetoxBlocked(foregroundPkg, appLabel, blockedUntil)
                }
                return
            }
        }

        // Priority 2: Per-App "How Long?" Prompt on entering a prompted app without a granted session.
        if (foregroundPkg != null &&
            foregroundPkg != packageName &&
            foregroundPkg in settings.promptedPackages &&
            activeSessionPackage != foregroundPkg &&
            lastObservedPackage != foregroundPkg
        ) {
            lastObservedPackage = foregroundPkg
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
        if (foregroundPkg != null) {
            lastObservedPackage = foregroundPkg
        }

        val targetSeconds = currentTargetSeconds(settings)

        // Priority 3: Exception Apps / ScreenStop self-pause / Standard & Escalated Usage Countdown
        when {
            foregroundPkg == packageName -> {
                currentStatus = GuardStatus.PAUSED_IN_APP
                publishRuntimeState(foregroundPkg)
            }

            foregroundPkg != null && foregroundPkg in settings.exceptionPackages -> {
                currentStatus = GuardStatus.PAUSED_EXCEPTION
                publishRuntimeState(foregroundPkg)
            }

            else -> {
                currentStatus = GuardStatus.MONITORING
                elapsedSeconds += 1
                if (elapsedSeconds >= targetSeconds) {
                    val sessionAppLabel = activeSessionLabel
                    withContext(Dispatchers.Main) {
                        triggerTakeover(isPreview = false, appLabel = sessionAppLabel)
                    }
                } else {
                    publishRuntimeState(foregroundPkg)
                }
            }
        }

        if (elapsedSeconds % 2 == 0 || currentStatus != GuardStatus.MONITORING) {
            updateNotification()
        }
    }

    private fun currentTargetSeconds(settings: GuardSettings): Int {
        val customSessionTarget = activeSessionTargetSeconds
        if (activeSessionPackage != null && customSessionTarget != null) {
            return customSessionTarget
        }
        return GuardDefaults.effectiveIntervalSeconds(
            baseMinutes = settings.intervalMinutes,
            consecutiveOverrides = consecutiveOverrides,
            escalatingEnabled = settings.escalatingFrictionEnabled
        )
    }

    private fun triggerDetoxBlocked(pkg: String, appLabel: String, blockedUntilMillis: Long) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) return
        isOverlayActive = true
        currentStatus = GuardStatus.DETOX_BLOCKED
        publishRuntimeState(pkg)
        updateNotification()

        overlayController.show(
            content = OverlayContent.DetoxBlocked(
                packageName = pkg,
                appLabel = appLabel,
                blockedUntilMillis = blockedUntilMillis
            ),
            onReturnHome = {
                isOverlayActive = false
                lastObservedPackage = null
                foregroundDetector.clearCachedPackage()
                PermissionChecker.launchHomeScreen(applicationContext)
                currentStatus = if (latestSettings.isGuardEnabled) GuardStatus.MONITORING else GuardStatus.DISARMED
                publishRuntimeState(null)
                updateNotification()
            },
            onShowFailed = {
                isOverlayActive = false
                currentStatus = GuardStatus.MONITORING
                publishRuntimeState(null)
            }
        )
    }

    private fun triggerHowLongPrompt(pkg: String, appLabel: String, isPreview: Boolean) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) {
            isPreviewMode = false
            isOverlayActive = false
            if (!latestSettings.isGuardEnabled) stopGuardAndSelf()
            return
        }
        isOverlayActive = true
        currentStatus = GuardStatus.AWAITING_SESSION_PROMPT
        publishRuntimeState(pkg)
        updateNotification()

        overlayController.show(
            content = OverlayContent.HowLongPrompt(
                packageName = pkg,
                appLabel = appLabel,
                isPreview = isPreview
            ),
            onDurationSelected = { chosenMinutes ->
                isOverlayActive = false
                isPreviewMode = false
                if (!isPreview) {
                    activeSessionPackage = pkg
                    activeSessionLabel = appLabel
                    activeSessionTargetSeconds = chosenMinutes * 60
                    elapsedSeconds = 0
                }
                currentStatus = if (latestSettings.isGuardEnabled) GuardStatus.MONITORING else GuardStatus.DISARMED
                publishRuntimeState(if (isPreview) null else pkg)
                if (!latestSettings.isGuardEnabled) {
                    stopGuardAndSelf()
                } else {
                    updateNotification()
                }
            },
            onReturnHome = {
                isOverlayActive = false
                isPreviewMode = false
                lastObservedPackage = null
                foregroundDetector.clearCachedPackage()
                if (!isPreview) {
                    PermissionChecker.launchHomeScreen(applicationContext)
                }
                currentStatus = if (latestSettings.isGuardEnabled) GuardStatus.MONITORING else GuardStatus.DISARMED
                publishRuntimeState(null)
                if (!latestSettings.isGuardEnabled) {
                    stopGuardAndSelf()
                } else {
                    updateNotification()
                }
            },
            onShowFailed = {
                isOverlayActive = false
                isPreviewMode = false
                if (!latestSettings.isGuardEnabled) {
                    stopGuardAndSelf()
                } else {
                    currentStatus = GuardStatus.MONITORING
                    publishRuntimeState(null)
                }
            }
        )
    }

    private fun triggerTakeover(isPreview: Boolean, appLabel: String?) {
        if (!PermissionChecker.hasOverlayPermission(applicationContext)) {
            isPreviewMode = false
            isOverlayActive = false
            elapsedSeconds = 0
            if (!latestSettings.isGuardEnabled) {
                stopGuardAndSelf()
            } else {
                publishRuntimeState(null)
            }
            return
        }

        val session = GuardRuntimeState.createSession(
            messages = latestSettings.reminderMessages,
            consecutiveOverrides = if (isPreview) consecutiveOverrides else consecutiveOverrides,
            escalatingEnabled = latestSettings.escalatingFrictionEnabled,
            appLabel = appLabel,
            isPreview = isPreview
        )
        isOverlayActive = true
        currentStatus = GuardStatus.TAKEOVER_ACTIVE
        val currentTarget = currentTargetSeconds(latestSettings)
        GuardRuntimeState.update {
            it.copy(
                status = GuardStatus.TAKEOVER_ACTIVE,
                elapsedSeconds = if (isPreview) elapsedSeconds else currentTarget,
                targetSeconds = currentTarget,
                consecutiveOverrides = consecutiveOverrides,
                activeTakeover = session
            )
        }
        updateNotification()

        overlayController.show(
            content = OverlayContent.Takeover(session),
            onTakeoverConfirmed = {
                isOverlayActive = false
                isPreviewMode = false
                if (!isPreview) {
                    elapsedSeconds = 0
                    if (latestSettings.escalatingFrictionEnabled) {
                        consecutiveOverrides += 1
                    }
                    // Reset per-app custom target after a takeover so escalating global friction takes over
                    activeSessionTargetSeconds = null
                }
                currentStatus = if (latestSettings.isGuardEnabled) {
                    GuardStatus.MONITORING
                } else {
                    GuardStatus.DISARMED
                }
                GuardRuntimeState.update {
                    it.copy(
                        status = currentStatus,
                        elapsedSeconds = elapsedSeconds,
                        targetSeconds = currentTargetSeconds(latestSettings),
                        consecutiveOverrides = consecutiveOverrides,
                        activeTakeover = null
                    )
                }
                if (!latestSettings.isGuardEnabled) {
                    stopGuardAndSelf()
                } else {
                    updateNotification()
                }
            },
            onShowFailed = {
                isOverlayActive = false
                isPreviewMode = false
                if (!latestSettings.isGuardEnabled) {
                    stopGuardAndSelf()
                } else {
                    currentStatus = GuardStatus.MONITORING
                    publishRuntimeState(null)
                    updateNotification()
                }
            }
        )
    }

    private fun publishRuntimeState(foregroundPkg: String?) {
        GuardRuntimeState.update {
            it.copy(
                status = currentStatus,
                elapsedSeconds = elapsedSeconds,
                targetSeconds = currentTargetSeconds(latestSettings),
                consecutiveOverrides = consecutiveOverrides,
                currentForegroundPackage = foregroundPkg,
                activeSessionPackage = activeSessionPackage,
                activeSessionLabel = activeSessionLabel
            )
        }
    }

    private fun stopGuardAndSelf() {
        monitorJob?.cancel()
        monitorJob = null
        isOverlayActive = false
        isPreviewMode = false
        overlayController.dismiss()
        elapsedSeconds = 0
        consecutiveOverrides = 0
        activeSessionPackage = null
        activeSessionLabel = null
        activeSessionTargetSeconds = null
        lastObservedPackage = null
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

    private fun updateNotification() {
        if (PermissionChecker.hasNotificationPermission(applicationContext)) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disarmIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenGuardService::class.java).setAction(ACTION_DISARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedFormatted = GuardDefaults.formatDuration(elapsedSeconds)
        val targetFormatted = GuardDefaults.formatDuration(currentTargetSeconds(latestSettings))
        val bodyText = when (currentStatus) {
            GuardStatus.PAUSED_EXCEPTION -> getString(R.string.notif_text_paused_exception, elapsedFormatted)
            GuardStatus.PAUSED_IN_APP -> getString(R.string.notif_text_paused_in_app, elapsedFormatted)
            GuardStatus.SCREEN_OFF -> getString(R.string.notif_text_screen_off)
            GuardStatus.TAKEOVER_ACTIVE -> getString(R.string.notif_text_takeover)
            GuardStatus.DETOX_BLOCKED -> getString(R.string.notif_text_detox)
            GuardStatus.AWAITING_SESSION_PROMPT -> getString(R.string.notif_text_prompt)
            else -> getString(
                R.string.notif_text_monitoring,
                targetFormatted,
                elapsedFormatted
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_screenstop)
            .setContentTitle(getString(R.string.notif_title_monitoring))
            .setContentText(bodyText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notif_action_disarm), disarmIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screenstop_guard_channel"
        private const val NOTIFICATION_ID = 4107

        const val ACTION_START = "com.example.action.START_GUARD"
        const val ACTION_STOP = "com.example.action.STOP_GUARD"
        const val ACTION_DISARM = "com.example.action.DISARM_GUARD"
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
