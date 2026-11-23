package com.example.util

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

class ForegroundAppDetector(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val keyguardManager: KeyguardManager? =
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    @Volatile
    private var lastKnownPackage: String? = null

    fun isScreenUnlockedAndInteractive(): Boolean {
        val interactive = powerManager?.isInteractive ?: true
        val locked = keyguardManager?.isKeyguardLocked ?: false
        return interactive && !locked
    }

    fun clearCachedPackage() {
        lastKnownPackage = null
        AppForegroundTracker.clearRecentLauncherLaunch()
    }

    fun getCurrentForegroundPackage(): String? {
        // Direct in-process truth for our own activities so we never rely on stale UsageEvents.
        if (AppForegroundTracker.isMainActivityVisible || AppForegroundTracker.isOverlayActivityVisible) {
            return context.packageName
        }

        // If user just tapped an app inside MinimalistLauncherActivity, report it right away
        // while UsageStatsManager catches up.
        val recentLaunch = AppForegroundTracker.peekRecentLauncherLaunch()
        if (!recentLaunch.isNullOrBlank() && !AppForegroundTracker.isLauncherActivityVisible) {
            lastKnownPackage = recentLaunch
            return recentLaunch
        }

        if (AppForegroundTracker.isLauncherActivityVisible) {
            lastKnownPackage = context.packageName
            return context.packageName
        }

        if (!PermissionChecker.hasUsageStatsPermission(context)) {
            return sanitizeExternalPackage(lastKnownPackage)
        }

        val manager = usageStatsManager ?: return sanitizeExternalPackage(lastKnownPackage)
        val now = System.currentTimeMillis()
        val lookbackMs = if (lastKnownPackage == null || lastKnownPackage == context.packageName) {
            15 * 60_000L
        } else {
            2 * 60_000L
        }

        val detectedFromEvents = queryActiveFromEvents(manager, now - lookbackMs, now)
        if (!detectedFromEvents.isNullOrBlank()) {
            lastKnownPackage = detectedFromEvents
            return detectedFromEvents
        }

        // If lastKnownPackage is still valid and not our own package (since our UI is not visible),
        // user has simply stayed in the same app without switching activities.
        val cached = sanitizeExternalPackage(lastKnownPackage)
        if (cached != null) {
            return cached
        }

        // Fallback to UsageStats ordered by lastTimeUsed when no recent events exist.
        val fallback = queryLatestFromUsageStats(manager, now - 10 * 60_000L, now)
        if (!fallback.isNullOrBlank()) {
            lastKnownPackage = fallback
            return fallback
        }

        return null
    }

    private fun queryActiveFromEvents(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): String? {
        return try {
            val events = manager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            val resumedTimestamps = LinkedHashMap<String, Long>()
            var latestResumedPkg: String? = null
            var latestResumedTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (shouldIgnorePackage(pkg)) continue

                val isResumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                val isPausedOrStopped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                        event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                }

                when {
                    isResumed -> {
                        resumedTimestamps[pkg] = event.timeStamp
                        if (event.timeStamp >= latestResumedTime) {
                            latestResumedTime = event.timeStamp
                            latestResumedPkg = pkg
                        }
                    }

                    isPausedOrStopped -> {
                        val resumedAt = resumedTimestamps[pkg]
                        if (resumedAt != null && event.timeStamp >= resumedAt) {
                            resumedTimestamps.remove(pkg)
                        }
                    }
                }
            }

            // Prefer an app that is still currently resumed (not yet paused/stopped).
            val currentlyResumed = resumedTimestamps.maxByOrNull { it.value }?.key
            currentlyResumed ?: latestResumedPkg
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryLatestFromUsageStats(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): String? {
        return try {
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startMillis, endMillis)
                ?: return null
            stats
                .asSequence()
                .filter { it.lastTimeUsed > startMillis && !shouldIgnorePackage(it.packageName) }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    private fun shouldIgnorePackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == "com.android.systemui") return true
        // Never report ScreenStop itself when none of ScreenStop's activities are visible!
        if (pkg == context.packageName && !AppForegroundTracker.isOwnUiVisible) return true
        return false
    }

    private fun sanitizeExternalPackage(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        if (pkg == "com.android.systemui") return null
        if (pkg == context.packageName && !AppForegroundTracker.isOwnUiVisible) return null
        return pkg
    }
}
