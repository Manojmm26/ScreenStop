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

    private val resolver = ForegroundEventResolver(ownPackageName = context.packageName)

    fun isScreenUnlockedAndInteractive(): Boolean {
        val interactive = powerManager?.isInteractive ?: true
        val locked = keyguardManager?.isKeyguardLocked ?: false
        return interactive && !locked
    }

    fun clearCachedPackage() {
        resolver.clearTransientState()
        AppForegroundTracker.clearRecentLauncherLaunch()
    }

    fun markReturnedHomeFrom(packageName: String) {
        val now = System.currentTimeMillis()
        AppForegroundTracker.clearRecentLauncherLaunch()
        resolver.markReturnedHomeFrom(packageName, now)
    }

    fun getCurrentForegroundPackage(): String? {
        val now = System.currentTimeMillis()

        // Direct in-process truth for ScreenStop's own activities so we never rely on stale UsageEvents.
        if (AppForegroundTracker.isMainActivityVisible || AppForegroundTracker.isOverlayActivityVisible) {
            return context.packageName
        }

        if (AppForegroundTracker.isLauncherActivityVisible) {
            resolver.noteOwnHomeVisible(now)
            return context.packageName
        }

        val recentLaunch = AppForegroundTracker.peekRecentLauncherLaunch(nowMillis = now)
        if (!PermissionChecker.hasUsageStatsPermission(context)) {
            return resolver.resolve(
                events = emptyList(),
                nowEpochMillis = now,
                isOwnUiVisible = false,
                recentLauncherLaunch = recentLaunch
            )
        }

        val manager = usageStatsManager ?: return resolver.resolve(
            events = emptyList(),
            nowEpochMillis = now,
            isOwnUiVisible = false,
            recentLauncherLaunch = recentLaunch
        )

        val lastProcessed = resolver.getLastProcessedTimestamp()
        val startMillis = if (lastProcessed > 0L) {
            (lastProcessed - 2_000L).coerceAtLeast(now - 5L * 60_000L)
        } else {
            now - 15L * 60_000L
        }

        val rawEvents = queryUsageActivityEvents(manager, startMillis, now)
        val firstPass = resolver.resolve(
            events = rawEvents,
            nowEpochMillis = now,
            isOwnUiVisible = false,
            recentLauncherLaunch = recentLaunch
        )
        if (!firstPass.isNullOrBlank()) {
            return firstPass
        }

        // Cold-start fallback when no UsageEvents exist in the lookback window.
        val fallback = queryLatestFromUsageStats(manager, now - 10L * 60_000L, now)
        return resolver.resolve(
            events = emptyList(),
            nowEpochMillis = now,
            isOwnUiVisible = false,
            recentLauncherLaunch = recentLaunch,
            fallbackUsageStatsPackage = fallback?.first,
            fallbackUsageStatsLastUsedMillis = fallback?.second ?: 0L
        )
    }

    private fun queryUsageActivityEvents(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): List<UsageActivityEvent> {
        return try {
            val events = manager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            val result = ArrayList<UsageActivityEvent>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val cls = event.className ?: ""

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
                    isResumed -> result.add(
                        UsageActivityEvent(
                            packageName = pkg,
                            className = cls,
                            type = UsageActivityEventType.RESUMED,
                            timestampMillis = event.timeStamp
                        )
                    )

                    isPausedOrStopped -> result.add(
                        UsageActivityEvent(
                            packageName = pkg,
                            className = cls,
                            type = UsageActivityEventType.PAUSED_OR_STOPPED,
                            timestampMillis = event.timeStamp
                        )
                    )
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun queryLatestFromUsageStats(
        manager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Pair<String, Long>? {
        return try {
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startMillis, endMillis)
                ?: return null
            val latest = stats
                .asSequence()
                .filter {
                    it.lastTimeUsed > startMillis &&
                        it.packageName.isNotBlank() &&
                        it.packageName != "com.android.systemui" &&
                        it.packageName != context.packageName
                }
                .maxByOrNull { it.lastTimeUsed }
                ?: return null
            latest.packageName to latest.lastTimeUsed
        } catch (_: Throwable) {
            null
        }
    }
}
