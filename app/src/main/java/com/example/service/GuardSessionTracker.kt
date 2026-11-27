package com.example.service

import com.example.data.GuardDefaults

data class ActiveAppSession(
    val packageName: String,
    val appLabel: String,
    val targetSeconds: Int,
    val elapsedMillis: Long = 0L
) {
    val elapsedSeconds: Int
        get() = (elapsedMillis / 1_000L).toInt().coerceAtLeast(0)

    val isExpired: Boolean
        get() = elapsedSeconds >= targetSeconds
}

/**
 * Pure-Kotlin monotonic timer and persistent per-app session tracker.
 * Per-app "How Long?" sessions stay alive across brief app switches and reset only on screen lock
 * or when enforcement stops.
 */
class GuardSessionTracker {

    private val lock = Any()
    private val perAppSessions = LinkedHashMap<String, ActiveAppSession>()

    private var globalElapsedMillis: Long = 0L
    private var consecutiveOverrides: Int = 0
    private var lastTickElapsedRealtimeMs: Long? = null

    fun consumeDeltaMillis(nowElapsedRealtimeMs: Long, maxSingleStepMs: Long = 10_000L): Long =
        synchronized(lock) {
            val previous = lastTickElapsedRealtimeMs
            lastTickElapsedRealtimeMs = nowElapsedRealtimeMs
            if (previous == null) return@synchronized 0L
            val rawDelta = nowElapsedRealtimeMs - previous
            if (rawDelta <= 0L) 0L else rawDelta.coerceAtMost(maxSingleStepMs)
        }

    fun resetTickClock() = synchronized(lock) {
        lastTickElapsedRealtimeMs = null
    }

    fun getGlobalElapsedSeconds(): Int = synchronized(lock) {
        (globalElapsedMillis / 1_000L).toInt().coerceAtLeast(0)
    }

    fun getConsecutiveOverrides(): Int = synchronized(lock) {
        consecutiveOverrides
    }

    fun getSession(packageName: String): ActiveAppSession? = synchronized(lock) {
        perAppSessions[packageName]
    }

    fun startOrReplaceSession(
        packageName: String,
        appLabel: String,
        targetSeconds: Int
    ): ActiveAppSession = synchronized(lock) {
        val session = ActiveAppSession(
            packageName = packageName,
            appLabel = appLabel,
            targetSeconds = targetSeconds.coerceAtLeast(GuardDefaults.MIN_ESCALATED_INTERVAL_SECONDS),
            elapsedMillis = 0L
        )
        perAppSessions[packageName] = session
        session
    }

    fun advanceSession(packageName: String, deltaMillis: Long): ActiveAppSession? = synchronized(lock) {
        val current = perAppSessions[packageName] ?: return@synchronized null
        val updated = current.copy(elapsedMillis = current.elapsedMillis + deltaMillis.coerceAtLeast(0L))
        perAppSessions[packageName] = updated
        updated
    }

    fun advanceGlobal(deltaMillis: Long): Int = synchronized(lock) {
        globalElapsedMillis += deltaMillis.coerceAtLeast(0L)
        (globalElapsedMillis / 1_000L).toInt().coerceAtLeast(0)
    }

    /**
     * Called when the user confirms a Takeover challenge word to keep using their phone.
     * Increments consecutive overrides (if escalating friction is enabled) and arms the next
     * escalated target for either the active per-app session or the global timer.
     */
    fun recordTakeoverOverride(
        forSessionPackage: String?,
        forSessionLabel: String?,
        baseIntervalMinutes: Int,
        escalatingEnabled: Boolean
    ) = synchronized(lock) {
        if (escalatingEnabled) {
            consecutiveOverrides += 1
        }
        val nextTargetSeconds = GuardDefaults.effectiveIntervalSeconds(
            baseMinutes = baseIntervalMinutes,
            consecutiveOverrides = consecutiveOverrides,
            escalatingEnabled = escalatingEnabled
        )
        if (!forSessionPackage.isNullOrBlank()) {
            perAppSessions[forSessionPackage] = ActiveAppSession(
                packageName = forSessionPackage,
                appLabel = forSessionLabel ?: forSessionPackage,
                targetSeconds = nextTargetSeconds,
                elapsedMillis = 0L
            )
        } else {
            globalElapsedMillis = 0L
        }
    }

    fun resetOnScreenOff() = synchronized(lock) {
        globalElapsedMillis = 0L
        consecutiveOverrides = 0
        perAppSessions.clear()
        lastTickElapsedRealtimeMs = null
    }
}
