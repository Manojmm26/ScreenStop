package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class DailyUsageEventType {
    RESUMED,
    PAUSED_OR_STOPPED,
    SCREEN_OFF_OR_LOCKED
}

data class DailyUsageEvent(
    val packageName: String,
    val className: String,
    val type: DailyUsageEventType,
    val timestampMillis: Long
)

object DailyAppUsageLoader {

    suspend fun loadTodayUsageMinutes(context: Context): Map<String, Int> =
        withContext(Dispatchers.IO) {
            if (!PermissionChecker.hasUsageStatsPermission(context)) {
                return@withContext emptyMap()
            }
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext emptyMap()

            val now = System.currentTimeMillis()
            val startOfDay = startOfLocalDayMillis(now)

            val events = readTodayEvents(manager, startOfDay, now)
            if (events.isNotEmpty()) {
                return@withContext aggregateTodayMinutesFromEvents(
                    events = events,
                    nowMillis = now,
                    ownPackageName = context.packageName
                )
            }

            // Fallback if no granular UsageEvents are returned by the ROM
            readFallbackUsageStats(manager, startOfDay, now, context.packageName)
        }

    fun startOfLocalDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Computes exact foreground usage in whole minutes per package from local midnight events.
     * Handles multi-activity apps cleanly and closes open sessions on screen-off or app switches.
     */
    fun aggregateTodayMinutesFromEvents(
        events: List<DailyUsageEvent>,
        nowMillis: Long,
        ownPackageName: String
    ): Map<String, Int> {
        val activeComponentsByPkg = HashMap<String, MutableSet<String>>()
        val sessionStartByPkg = HashMap<String, Long>()
        val totalsMillisByPkg = HashMap<String, Long>()

        fun closePackageSession(pkg: String, endMillis: Long) {
            val start = sessionStartByPkg.remove(pkg) ?: return
            activeComponentsByPkg.remove(pkg)
            val delta = (endMillis - start).coerceAtLeast(0L)
            // Guard against corrupt timestamps or multi-hour orphaned sessions without pause events.
            val clampedDelta = delta.coerceAtMost(4L * 60L * 60_000L)
            if (clampedDelta > 0L) {
                totalsMillisByPkg[pkg] = (totalsMillisByPkg[pkg] ?: 0L) + clampedDelta
            }
        }

        fun closeAllSessions(endMillis: Long) {
            val openPackages = sessionStartByPkg.keys.toList()
            for (pkg in openPackages) {
                closePackageSession(pkg, endMillis)
            }
        }

        for (event in events) {
            when (event.type) {
                DailyUsageEventType.SCREEN_OFF_OR_LOCKED -> {
                    closeAllSessions(event.timestampMillis)
                }

                DailyUsageEventType.RESUMED -> {
                    val pkg = event.packageName
                    if (pkg.isBlank() || pkg == "com.android.systemui") continue

                    // Close any other foreground package that didn't emit a pause event first.
                    val otherOpen = sessionStartByPkg.keys.filter { it != pkg }
                    for (other in otherOpen) {
                        closePackageSession(other, event.timestampMillis)
                    }

                    if (pkg == ownPackageName) continue

                    val components = activeComponentsByPkg.getOrPut(pkg) { LinkedHashSet() }
                    if (pkg !in sessionStartByPkg) {
                        sessionStartByPkg[pkg] = event.timestampMillis
                    }
                    components.add(event.className)
                }

                DailyUsageEventType.PAUSED_OR_STOPPED -> {
                    val pkg = event.packageName
                    val components = activeComponentsByPkg[pkg] ?: continue
                    if (event.className.isBlank()) {
                        components.clear()
                    } else {
                        components.remove(event.className)
                    }
                    if (components.isEmpty()) {
                        closePackageSession(pkg, event.timestampMillis)
                    }
                }
            }
        }

        // If a package is still open at query time (within 30m), close it up to nowMillis.
        val openAtEnd = sessionStartByPkg.keys.toList()
        for (pkg in openAtEnd) {
            val start = sessionStartByPkg[pkg] ?: continue
            if (nowMillis - start in 1L..(30L * 60_000L)) {
                closePackageSession(pkg, nowMillis)
            }
        }

        return buildMap {
            totalsMillisByPkg.forEach { (pkg, totalMs) ->
                val minutes = (totalMs / 60_000L).toInt()
                if (minutes > 0) {
                    put(pkg, minutes)
                }
            }
        }
    }

    private fun readTodayEvents(
        manager: UsageStatsManager,
        startOfDayMillis: Long,
        nowMillis: Long
    ): List<DailyUsageEvent> {
        return try {
            val usageEvents = manager.queryEvents(startOfDayMillis, nowMillis)
            val event = UsageEvents.Event()
            val result = ArrayList<DailyUsageEvent>()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: ""
                val cls = event.className ?: ""

                val mappedType = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ->
                        DailyUsageEventType.RESUMED

                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        @Suppress("DEPRECATION")
                        (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) ->
                        DailyUsageEventType.RESUMED

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                            event.eventType == UsageEvents.Event.ACTIVITY_STOPPED) ->
                        DailyUsageEventType.PAUSED_OR_STOPPED

                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        @Suppress("DEPRECATION")
                        (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) ->
                        DailyUsageEventType.PAUSED_OR_STOPPED

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                            event.eventType == UsageEvents.Event.KEYGUARD_SHOWN) ->
                        DailyUsageEventType.SCREEN_OFF_OR_LOCKED

                    else -> null
                }

                if (mappedType != null) {
                    result.add(
                        DailyUsageEvent(
                            packageName = pkg,
                            className = cls,
                            type = mappedType,
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

    private fun readFallbackUsageStats(
        manager: UsageStatsManager,
        startOfDayMillis: Long,
        nowMillis: Long,
        ownPackageName: String
    ): Map<String, Int> {
        return try {
            val aggregated = manager.queryAndAggregateUsageStats(startOfDayMillis, nowMillis)
                ?: return emptyMap()
            buildMap {
                aggregated.forEach { (pkg, stats) ->
                    if (pkg.isNotBlank() && pkg != ownPackageName && pkg != "com.android.systemui") {
                        val minutes = (stats.totalTimeInForeground / 60_000L).toInt()
                        if (minutes > 0) {
                            put(pkg, minutes)
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
