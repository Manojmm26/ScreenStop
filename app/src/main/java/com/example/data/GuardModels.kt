package com.example.data

import kotlin.math.roundToInt
import kotlin.random.Random

enum class GuardStatus {
    DISARMED,
    MONITORING,
    PAUSED_EXCEPTION,
    PAUSED_IN_APP,
    SCREEN_OFF,
    TAKEOVER_ACTIVE,
    DETOX_BLOCKED,
    AWAITING_SESSION_PROMPT
}

enum class DistractingCategory(
    val storageKey: String,
    val displayName: String
) {
    SOCIAL("social", "Social"),
    VIDEO("video", "Video"),
    GAMES("games", "Games"),
    NEWS("news", "News"),
    SHOPPING("shopping", "Shopping");

    companion object {
        fun fromStorageKey(key: String): DistractingCategory? =
            entries.firstOrNull { it.storageKey == key }
    }
}

data class GuardSettings(
    val isGuardEnabled: Boolean = false,
    val intervalMinutes: Int = GuardDefaults.DEFAULT_INTERVAL_MINUTES,
    val escalatingFrictionEnabled: Boolean = true,
    val exceptionPackages: Set<String> = emptySet(),
    val blockedAppsUntilMillis: Map<String, Long> = emptyMap(),
    val promptedPackages: Set<String> = emptySet(),
    val reminderMessages: List<String> = GuardDefaults.DEFAULT_MESSAGES,
    val launcherFavoritePackages: List<String> = emptyList(),
    val launcherHiddenPackages: Set<String> = emptySet(),
    val launcherRenamedApps: Map<String, String> = emptyMap(),
    val showGuardStatusOnHome: Boolean = true,
    val homeFocusMantra: String = GuardDefaults.DEFAULT_HOME_FOCUS_MANTRA,
    val deepWorkUntilMillis: Long = 0L,
    val deepWorkCategories: Set<DistractingCategory> = GuardDefaults.DEFAULT_DEEP_WORK_CATEGORIES,
    val deepWorkStrictAllNonExempt: Boolean = false,
    val deepWorkSavedInterruptionFilter: Int = -1
) {
    fun activeBlockedApps(nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        return blockedAppsUntilMillis.filterValues { it > nowMillis }
    }

    fun isBlocked(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = blockedAppsUntilMillis[packageName] ?: return false
        return until > nowMillis
    }

    fun isDeepWorkActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return deepWorkUntilMillis > nowMillis
    }

    fun isRestrictedByDeepWork(
        packageName: String,
        category: DistractingCategory?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isDeepWorkActive(nowMillis)) return false
        if (packageName in exceptionPackages) return false
        if (deepWorkStrictAllNonExempt) return true
        return category != null && category in deepWorkCategories
    }

    fun hasActiveEnforcement(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isGuardEnabled ||
            activeBlockedApps(nowMillis).isNotEmpty() ||
            promptedPackages.isNotEmpty() ||
            isDeepWorkActive(nowMillis)
    }
}

data class TakeoverSession(
    val message: String,
    val challengeWords: List<String>,
    val escalationLevel: Int = 1,
    val consecutiveOverrides: Int = 0,
    val appLabel: String? = null,
    val isPreview: Boolean = false,
    val triggeredAtMillis: Long = System.currentTimeMillis()
) {
    val expectedPhrase: String
        get() = challengeWords.joinToString(" ")
}

sealed interface OverlayContent {
    data class Takeover(val session: TakeoverSession) : OverlayContent

    data class DetoxBlocked(
        val packageName: String,
        val appLabel: String,
        val blockedUntilMillis: Long,
        val isDeepWork: Boolean = false,
        val categoryLabel: String? = null
    ) : OverlayContent

    data class HowLongPrompt(
        val packageName: String,
        val appLabel: String,
        val isPreview: Boolean = false
    ) : OverlayContent
}

data class GuardRuntimeSnapshot(
    val status: GuardStatus = GuardStatus.DISARMED,
    val elapsedSeconds: Int = 0,
    val targetSeconds: Int = GuardDefaults.DEFAULT_INTERVAL_MINUTES * 60,
    val consecutiveOverrides: Int = 0,
    val currentForegroundPackage: String? = null,
    val activeSessionPackage: String? = null,
    val activeSessionLabel: String? = null,
    val activeTakeover: TakeoverSession? = null
)

data class PermissionSnapshot(
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val hasDndAccess: Boolean = false
) {
    val canArmGuard: Boolean
        get() = hasOverlayPermission && hasUsageStatsPermission

    val allGranted: Boolean
        get() = hasOverlayPermission && hasUsageStatsPermission && hasNotificationPermission
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val customLabel: String? = null,
    val isException: Boolean = false,
    val blockedUntilMillis: Long? = null,
    val isPrompted: Boolean = false,
    val isPinnedFavorite: Boolean = false,
    val isHiddenInLauncher: Boolean = false,
    val todayUsageMinutes: Int = 0,
    val category: DistractingCategory? = null
) {
    val displayLabel: String
        get() = customLabel?.takeIf { it.isNotBlank() } ?: label

    val shouldShowDailyUsage: Boolean
        get() = todayUsageMinutes >= GuardDefaults.DAILY_USAGE_BADGE_THRESHOLD_MINUTES

    fun isDetoxLocked(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return (blockedUntilMillis ?: 0L) > nowMillis
    }

    fun isRestrictedByDeepWork(
        settings: GuardSettings,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return settings.isRestrictedByDeepWork(
            packageName = packageName,
            category = category,
            nowMillis = nowMillis
        )
    }
}

data class DetoxDurationPreset(
    val label: String,
    val durationMillis: Long
)

object GuardDefaults {
    const val DEFAULT_INTERVAL_MINUTES = 3
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 60
    const val MIN_ESCALATED_INTERVAL_SECONDS = 30
    const val DAILY_USAGE_BADGE_THRESHOLD_MINUTES = 5
    const val DEFAULT_HOME_FOCUS_MANTRA = "Protect your attention.\nDo the one thing that matters most today."

    val PRESET_INTERVALS = listOf(1, 3, 5, 10, 15)
    val SESSION_PROMPT_OPTIONS_MINUTES = listOf(1, 3, 5, 10, 15)

    val DETOX_DURATION_PRESETS = listOf(
        DetoxDurationPreset("30m", 30L * 60_000L),
        DetoxDurationPreset("2h", 2L * 60L * 60_000L),
        DetoxDurationPreset("6h", 6L * 60L * 60_000L),
        DetoxDurationPreset("24h", 24L * 60L * 60_000L),
        DetoxDurationPreset("3d", 3L * 24L * 60L * 60_000L)
    )

    val DEEP_WORK_DURATION_PRESETS = listOf(
        DetoxDurationPreset("25m", 25L * 60_000L),
        DetoxDurationPreset("45m", 45L * 60_000L),
        DetoxDurationPreset("1h", 60L * 60_000L),
        DetoxDurationPreset("2h", 2L * 60L * 60_000L),
        DetoxDurationPreset("4h", 4L * 60L * 60_000L)
    )

    val DEFAULT_DEEP_WORK_CATEGORIES = setOf(
        DistractingCategory.SOCIAL,
        DistractingCategory.VIDEO,
        DistractingCategory.GAMES,
        DistractingCategory.NEWS
    )

    val DEFAULT_MESSAGES = listOf(
        "Put the phone down. Nothing on this screen is worth your focus right now.",
        "You already checked. Lock the screen and get back to what actually matters.",
        "Time is up. Do not let three minutes turn into thirty.",
        "Close the screen. You know you should not be scrolling right now.",
        "Stop feeding the habit. Lock your phone and step away.",
        "Ask yourself why you picked this up. If there is no real reason, lock it now.",
        "Your attention is being spent without your permission. Take it back.",
        "Enough. Put the phone face down and move on with your day.",
        "Every extra minute here costs you later. Lock the screen.",
        "Break the loop right now. Press the power button."
    )

    val CHALLENGE_WORDS = listOf(
        "DISCIPLINE",
        "CONSCIOUS",
        "DELIBERATE",
        "INTENTION",
        "STEADFAST",
        "OVERRIDE",
        "PRESENCE",
        "AWARENESS",
        "UNPLUGGED",
        "MINDFUL",
        "RESTRAINT",
        "WILLPOWER",
        "AUTONOMY",
        "DISENGAGE",
        "FOCUSED",
        "OFFLINE"
    )

    fun challengeWordCountForOverrides(
        consecutiveOverrides: Int,
        escalatingEnabled: Boolean
    ): Int {
        if (!escalatingEnabled || consecutiveOverrides <= 0) return 1
        return when (consecutiveOverrides) {
            1 -> 2
            else -> 3
        }
    }

    fun effectiveIntervalSeconds(
        baseMinutes: Int,
        consecutiveOverrides: Int,
        escalatingEnabled: Boolean
    ): Int {
        val baseSeconds = baseMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES) * 60
        if (!escalatingEnabled || consecutiveOverrides <= 0) return baseSeconds
        val multiplier = when (consecutiveOverrides) {
            1 -> 0.67f
            2 -> 0.50f
            else -> 0.34f
        }
        return (baseSeconds * multiplier).roundToInt().coerceAtLeast(MIN_ESCALATED_INTERVAL_SECONDS)
    }

    fun pickRandomMessage(messages: List<String>, previous: String?): String {
        val pool = messages.filter { it.isNotBlank() }.ifEmpty { DEFAULT_MESSAGES }
        if (pool.size == 1) return pool.first()
        val candidates = pool.filterNot { it == previous }.ifEmpty { pool }
        return candidates[Random.nextInt(candidates.size)]
    }

    fun pickRandomChallengeWords(count: Int, previousWords: List<String>): List<String> {
        val safeCount = count.coerceIn(1, 3)
        val freshPool = CHALLENGE_WORDS.filterNot { it in previousWords }.shuffled()
        val fallbackPool = CHALLENGE_WORDS.shuffled()
        val chosen = ArrayList<String>(safeCount)
        for (word in freshPool) {
            if (chosen.size == safeCount) break
            chosen.add(word)
        }
        for (word in fallbackPool) {
            if (chosen.size == safeCount) break
            if (word !in chosen) chosen.add(word)
        }
        return chosen
    }

    fun formatDuration(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val minutes = clamped / 60
        val seconds = clamped % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun formatRemainingLock(remainingMillis: Long): String {
        val clampedSeconds = (remainingMillis / 1_000L).coerceAtLeast(0L)
        val days = clampedSeconds / 86_400L
        val hours = (clampedSeconds % 86_400L) / 3_600L
        val minutes = (clampedSeconds % 3_600L) / 60L
        val seconds = clampedSeconds % 60L
        return when {
            days > 0L -> "${days}d ${hours}h"
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
