package com.example.util

enum class UsageActivityEventType {
    RESUMED,
    PAUSED_OR_STOPPED
}

data class UsageActivityEvent(
    val packageName: String,
    val className: String,
    val type: UsageActivityEventType,
    val timestampMillis: Long
)

/**
 * Pure-Kotlin state machine that resolves the current foreground package from UsageEvents
 * without dropping multi-activity apps (e.g., X transitioning MainActivity -> DetailActivity -> MainActivity STOPPED)
 * and without re-triggering dismissed apps after "Never Mind — Go Home" or Detox Lock dismissal.
 */
class ForegroundEventResolver(
    private val ownPackageName: String,
    private val homeSettleWindowMillis: Long = 1_500L
) {
    private data class ComponentKey(
        val packageName: String,
        val className: String
    )

    private val lock = Any()
    private val activeActivities = LinkedHashMap<ComponentKey, Long>()
    private val dismissedWatermarks = HashMap<String, Long>()

    private var lastKnownPackage: String? = null
    private var lastProcessedEventTimestamp: Long = 0L

    private var pendingHomeSettlePackage: String? = null
    private var pendingHomeSettleStartedEpoch: Long = 0L
    private var pendingHomeSettleUntilEpoch: Long = 0L

    fun getLastProcessedTimestamp(): Long = synchronized(lock) { lastProcessedEventTimestamp }

    fun markReturnedHomeFrom(packageName: String, nowEpochMillis: Long) = synchronized(lock) {
        if (packageName.isBlank()) return@synchronized
        val existingWatermark = dismissedWatermarks[packageName] ?: 0L
        dismissedWatermarks[packageName] = maxOf(existingWatermark, nowEpochMillis)
        pendingHomeSettlePackage = packageName
        pendingHomeSettleStartedEpoch = nowEpochMillis
        pendingHomeSettleUntilEpoch = nowEpochMillis + homeSettleWindowMillis

        // Remove any currently active entries for the dismissed package so it cannot resurface.
        val iterator = activeActivities.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.packageName == packageName) {
                iterator.remove()
            }
        }
        if (lastKnownPackage == packageName) {
            lastKnownPackage = null
        }
    }

    fun noteOwnHomeVisible(nowEpochMillis: Long) = synchronized(lock) {
        val pendingPkg = pendingHomeSettlePackage
        if (pendingPkg != null) {
            dismissedWatermarks[pendingPkg] = maxOf(dismissedWatermarks[pendingPkg] ?: 0L, nowEpochMillis)
            pendingHomeSettlePackage = null
            pendingHomeSettleStartedEpoch = 0L
            pendingHomeSettleUntilEpoch = 0L
        }
        lastKnownPackage = ownPackageName
    }

    fun clearTransientState() = synchronized(lock) {
        activeActivities.clear()
        lastKnownPackage = null
        lastProcessedEventTimestamp = 0L
        pendingHomeSettlePackage = null
        pendingHomeSettleStartedEpoch = 0L
        pendingHomeSettleUntilEpoch = 0L
    }

    fun resolve(
        events: List<UsageActivityEvent>,
        nowEpochMillis: Long,
        isOwnUiVisible: Boolean,
        recentLauncherLaunch: RecentLauncherLaunch? = null,
        fallbackUsageStatsPackage: String? = null,
        fallbackUsageStatsLastUsedMillis: Long = 0L
    ): String? = synchronized(lock) {
        // Expire settled window once enough time has elapsed.
        if (pendingHomeSettlePackage != null && nowEpochMillis > pendingHomeSettleUntilEpoch) {
            pendingHomeSettlePackage = null
            pendingHomeSettleStartedEpoch = 0L
            pendingHomeSettleUntilEpoch = 0L
        }

        // If user explicitly tapped an app inside MinimalistLauncherActivity after any dismissal,
        // accept it immediately while UsageStatsManager flushes.
        if (recentLauncherLaunch != null && !isOwnUiVisible) {
            val launchPkg = recentLauncherLaunch.packageName
            val watermark = dismissedWatermarks[launchPkg] ?: 0L
            if (!shouldIgnorePackage(launchPkg, isOwnUiVisible) &&
                recentLauncherLaunch.launchedAtMillis > watermark
            ) {
                if (pendingHomeSettlePackage == launchPkg) {
                    pendingHomeSettlePackage = null
                }
                dismissedWatermarks.remove(launchPkg)
                lastKnownPackage = launchPkg
                return@synchronized launchPkg
            }
        }

        var latestValidResumedPkg: String? = null
        var latestValidResumedTime = 0L

        for (event in events) {
            if (event.timestampMillis > lastProcessedEventTimestamp) {
                lastProcessedEventTimestamp = event.timestampMillis
            }

            val pkg = event.packageName
            if (shouldIgnorePackage(pkg, isOwnUiVisible)) continue

            val key = ComponentKey(pkg, event.className)
            when (event.type) {
                UsageActivityEventType.RESUMED -> {
                    val pendingPkg = pendingHomeSettlePackage
                    if (pendingPkg != null) {
                        if (pkg == pendingPkg && event.timestampMillis <= pendingHomeSettleUntilEpoch) {
                            // Stray bounce from the dismissed app while Home is coming to front.
                            continue
                        }
                        if (pkg != pendingPkg && event.timestampMillis >= pendingHomeSettleStartedEpoch) {
                            // Home or another app visibly settled on screen.
                            dismissedWatermarks[pendingPkg] = maxOf(
                                dismissedWatermarks[pendingPkg] ?: 0L,
                                event.timestampMillis
                            )
                            pendingHomeSettlePackage = null
                            pendingHomeSettleStartedEpoch = 0L
                            pendingHomeSettleUntilEpoch = 0L
                        }
                    }

                    val watermark = dismissedWatermarks[pkg] ?: 0L
                    if (event.timestampMillis <= watermark) {
                        // Historical event from before the user tapped "Never Mind — Go Home".
                        continue
                    }

                    // Genuine fresh resume of this app after any dismissal.
                    if (watermark > 0L) {
                        dismissedWatermarks.remove(pkg)
                    }

                    activeActivities[key] = event.timestampMillis
                    if (event.timestampMillis >= latestValidResumedTime) {
                        latestValidResumedTime = event.timestampMillis
                        latestValidResumedPkg = pkg
                    }
                }

                UsageActivityEventType.PAUSED_OR_STOPPED -> {
                    val existing = activeActivities[key]
                    if (existing != null && event.timestampMillis >= existing) {
                        activeActivities.remove(key)
                    } else if (event.className.isBlank()) {
                        // Fallback for events lacking className: remove only entries older than this stop.
                        val iter = activeActivities.entries.iterator()
                        while (iter.hasNext()) {
                            val entry = iter.next()
                            if (entry.key.packageName == pkg && event.timestampMillis >= entry.value) {
                                iter.remove()
                            }
                        }
                    }
                }
            }
        }

        // Prune very old entries (> 30 minutes) so stale orphaned activities never accumulate.
        val pruneBefore = nowEpochMillis - 30L * 60_000L
        val iter = activeActivities.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val pkg = entry.key.packageName
            val watermark = dismissedWatermarks[pkg] ?: 0L
            if (entry.value < pruneBefore ||
                entry.value <= watermark ||
                shouldIgnorePackage(pkg, isOwnUiVisible)
            ) {
                iter.remove()
            }
        }

        // Priority 1: Highest-timestamp currently resumed component.
        val topActive = activeActivities.maxByOrNull { it.value }?.key?.packageName
        if (!topActive.isNullOrBlank()) {
            lastKnownPackage = topActive
            return@synchronized topActive
        }

        // Priority 2: Most recently resumed valid package from this batch.
        if (!latestValidResumedPkg.isNullOrBlank()) {
            lastKnownPackage = latestValidResumedPkg
            return@synchronized latestValidResumedPkg
        }

        // Priority 3: Sticky lastKnownPackage (e.g., user stayed on one feed screen in X for 10+ minutes).
        val cached = sanitizeCachedPackage(lastKnownPackage, isOwnUiVisible, nowEpochMillis)
        if (cached != null) {
            return@synchronized cached
        }

        // Priority 4: Fallback UsageStats snapshot (respecting dismissal watermarks).
        if (!fallbackUsageStatsPackage.isNullOrBlank() &&
            !shouldIgnorePackage(fallbackUsageStatsPackage, isOwnUiVisible)
        ) {
            val pendingPkg = pendingHomeSettlePackage
            val isSuppressedBySettle = pendingPkg == fallbackUsageStatsPackage &&
                nowEpochMillis <= pendingHomeSettleUntilEpoch
            val watermark = dismissedWatermarks[fallbackUsageStatsPackage] ?: 0L
            if (!isSuppressedBySettle && fallbackUsageStatsLastUsedMillis > watermark) {
                lastKnownPackage = fallbackUsageStatsPackage
                return@synchronized fallbackUsageStatsPackage
            }
        }

        return@synchronized null
    }

    private fun shouldIgnorePackage(pkg: String, isOwnUiVisible: Boolean): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == "com.android.systemui") return true
        if (pkg == ownPackageName && !isOwnUiVisible) return true
        return false
    }

    private fun sanitizeCachedPackage(
        pkg: String?,
        isOwnUiVisible: Boolean,
        nowEpochMillis: Long
    ): String? {
        if (pkg.isNullOrBlank()) return null
        if (shouldIgnorePackage(pkg, isOwnUiVisible)) return null
        if (pendingHomeSettlePackage == pkg && nowEpochMillis <= pendingHomeSettleUntilEpoch) {
            return null
        }
        return pkg
    }
}
