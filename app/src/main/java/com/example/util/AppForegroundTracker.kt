package com.example.util

data class RecentLauncherLaunch(
    val packageName: String,
    val launchedAtMillis: Long
)

object AppForegroundTracker {
    @Volatile
    var isMainActivityVisible: Boolean = false
        private set

    @Volatile
    var isLauncherActivityVisible: Boolean = false
        private set

    @Volatile
    var isOverlayActivityVisible: Boolean = false
        private set

    @Volatile
    private var recentLaunch: RecentLauncherLaunch? = null

    fun setMainActivityVisible(visible: Boolean) {
        isMainActivityVisible = visible
    }

    fun setLauncherActivityVisible(visible: Boolean) {
        isLauncherActivityVisible = visible
    }

    fun setOverlayActivityVisible(visible: Boolean) {
        isOverlayActivityVisible = visible
    }

    fun recordAppLaunchedFromLauncher(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        recentLaunch = RecentLauncherLaunch(packageName, nowMillis)
    }

    fun peekRecentLauncherLaunch(
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 3_000L
    ): RecentLauncherLaunch? {
        val current = recentLaunch ?: return null
        return if (nowMillis - current.launchedAtMillis <= maxAgeMillis) current else null
    }

    fun clearRecentLauncherLaunch() {
        recentLaunch = null
    }

    val isOwnUiVisible: Boolean
        get() = isMainActivityVisible || isLauncherActivityVisible || isOverlayActivityVisible
}
