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
    }

    fun getCurrentForegroundPackage(): String? {
        if (!PermissionChecker.hasUsageStatsPermission(context)) {
            return lastKnownPackage
        }
        val manager = usageStatsManager ?: return lastKnownPackage
        val now = System.currentTimeMillis()
        // Query recent window first; if no fresh events (user staying in one app), keep lastKnownPackage
        val lookbackMs = if (lastKnownPackage == null) 5 * 60_000L else 15_000L

        return try {
            val events = manager.queryEvents(now - lookbackMs, now)
            val event = UsageEvents.Event()
            var detectedPackage: String? = null
            var latestTimestamp = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isForegroundEvent && event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    detectedPackage = event.packageName
                }
            }

            if (!detectedPackage.isNullOrBlank()) {
                lastKnownPackage = detectedPackage
            }
            lastKnownPackage
        } catch (_: Exception) {
            lastKnownPackage
        }
    }
}
