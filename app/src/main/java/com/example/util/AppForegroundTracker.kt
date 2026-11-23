package com.example.util

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
    private var launchedPackage: String? = null

    @Volatile
    private var launchedAtMillis: Long = 0L

    fun setMainActivityVisible(visible: Boolean) {
        isMainActivityVisible = visible
    }

    fun setLauncherActivityVisible(visible: Boolean) {
        isLauncherActivityVisible = visible
    }

    fun setOverlayActivityVisible(visible: Boolean) {
        isOverlayActivityVisible = visible
    }

    fun recordAppLaunchedFromLauncher(packageName: String) {
        launchedPackage = packageName
        launchedAtMillis = System.currentTimeMillis()
    }

    fun peekRecentLauncherLaunch(maxAgeMillis: Long = 3_000L): String? {
        val pkg = launchedPackage ?: return null
        return if (System.currentTimeMillis() - launchedAtMillis <= maxAgeMillis) pkg else null
    }

    fun clearRecentLauncherLaunch() {
        launchedPackage = null
        launchedAtMillis = 0L
    }

    val isOwnUiVisible: Boolean
        get() = isMainActivityVisible || isLauncherActivityVisible || isOverlayActivityVisible
}
