package com.example

import com.example.data.DistractingCategory
import com.example.data.GuardSettings
import com.example.data.InstalledAppInfo
import com.example.service.GuardSessionTracker
import com.example.util.AppCategoryClassifier
import com.example.util.DailyAppUsageLoader
import com.example.util.DailyUsageEvent
import com.example.util.DailyUsageEventType
import com.example.util.ForegroundEventResolver
import com.example.util.RecentLauncherLaunch
import com.example.util.UsageActivityEvent
import com.example.util.UsageActivityEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStopEngineTest {

    private val ownPkg = "com.aistudio.screenstop.vqrkzm"
    private val xPkg = "com.twitter.android"
    private val homePkg = "com.google.android.apps.nexuslauncher"
    private val whatsappPkg = "com.whatsapp"

    @Test
    fun multiActivityTransitionInsideX_neverDropsForegroundApp() {
        val resolver = ForegroundEventResolver(ownPackageName = ownPkg)

        // 1. User opens X MainActivity while stock launcher was previously open.
        val batch1 = listOf(
            UsageActivityEvent(homePkg, "Launcher", UsageActivityEventType.RESUMED, 1_000L),
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.RESUMED, 5_000L)
        )
        assertEquals(
            xPkg,
            resolver.resolve(events = batch1, nowEpochMillis = 5_500L, isOwnUiVisible = false)
        )

        // 2. User taps a post inside X: MainActivity PAUSED -> TweetDetailActivity RESUMED -> MainActivity STOPPED.
        val batch2 = listOf(
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.PAUSED_OR_STOPPED, 10_000L),
            UsageActivityEvent(xPkg, "TweetDetailActivity", UsageActivityEventType.RESUMED, 10_050L),
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.PAUSED_OR_STOPPED, 10_500L)
        )
        assertEquals(
            xPkg,
            resolver.resolve(events = batch2, nowEpochMillis = 11_000L, isOwnUiVisible = false)
        )

        // 3. User stays reading the thread for 10 minutes with zero new UsageEvents.
        assertEquals(
            xPkg,
            resolver.resolve(
                events = emptyList(),
                nowEpochMillis = 11_000L + 10 * 60_000L,
                isOwnUiVisible = false
            )
        )
    }

    @Test
    fun neverMindGoHome_suppressesStaleAndBounceEvents_butAllowsGenuineReopen() {
        val resolver = ForegroundEventResolver(ownPackageName = ownPkg, homeSettleWindowMillis = 1_500L)

        // User opens X at t = 10_000
        val openEvents = listOf(
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.RESUMED, 10_000L)
        )
        assertEquals(
            xPkg,
            resolver.resolve(events = openEvents, nowEpochMillis = 10_200L, isOwnUiVisible = false)
        )

        // User taps "Never Mind — Go Home" at t = 12_000
        resolver.markReturnedHomeFrom(xPkg, nowEpochMillis = 12_000L)

        // Tick 1 (t = 12_300): Stale historical event (10_000) AND stray bounce event (12_150) must NOT re-trigger X!
        val staleAndBounceEvents = listOf(
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.RESUMED, 10_000L),
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.RESUMED, 12_150L)
        )
        assertNull(
            resolver.resolve(
                events = staleAndBounceEvents,
                nowEpochMillis = 12_300L,
                isOwnUiVisible = false,
                fallbackUsageStatsPackage = xPkg,
                fallbackUsageStatsLastUsedMillis = 10_000L
            )
        )

        // Tick 2 (t = 12_500): Stock Home launcher settles -> foreground is Home, NOT X.
        val homeSettledEvents = listOf(
            UsageActivityEvent(homePkg, "Launcher", UsageActivityEventType.RESUMED, 12_400L)
        )
        assertEquals(
            homePkg,
            resolver.resolve(events = homeSettledEvents, nowEpochMillis = 12_500L, isOwnUiVisible = false)
        )

        // Tick 3 (t = 13_200): User genuinely opens X again from Home -> MUST detect X immediately!
        val reopenEvents = listOf(
            UsageActivityEvent(xPkg, "MainActivity", UsageActivityEventType.RESUMED, 13_100L)
        )
        assertEquals(
            xPkg,
            resolver.resolve(events = reopenEvents, nowEpochMillis = 13_200L, isOwnUiVisible = false)
        )
    }

    @Test
    fun minimalistLauncherReopen_triggersPromptImmediatelyAfterGoHome() {
        val resolver = ForegroundEventResolver(ownPackageName = ownPkg)

        resolver.markReturnedHomeFrom(xPkg, nowEpochMillis = 20_000L)
        resolver.noteOwnHomeVisible(nowEpochMillis = 20_200L)

        // Old launch from before Go Home is ignored
        val oldLaunch = RecentLauncherLaunch(xPkg, launchedAtMillis = 19_500L)
        assertNull(
            resolver.resolve(
                events = emptyList(),
                nowEpochMillis = 20_300L,
                isOwnUiVisible = false,
                recentLauncherLaunch = oldLaunch
            )
        )

        // Fresh tap from MinimalistLauncherActivity after Go Home triggers immediately
        val freshLaunch = RecentLauncherLaunch(xPkg, launchedAtMillis = 20_800L)
        assertEquals(
            xPkg,
            resolver.resolve(
                events = emptyList(),
                nowEpochMillis = 20_900L,
                isOwnUiVisible = false,
                recentLauncherLaunch = freshLaunch
            )
        )
    }

    @Test
    fun perAppSessionPersistsAcrossAppSwitches_andResetsOnScreenOff() {
        val tracker = GuardSessionTracker()

        // User chooses 5 minutes (300s) for X
        tracker.startOrReplaceSession(xPkg, "X", targetSeconds = 300)

        // Monotonic clock advances 90 seconds inside X (even in irregular chunks)
        assertEquals(0L, tracker.consumeDeltaMillis(1_000L))
        val delta1 = tracker.consumeDeltaMillis(4_500L) // 3.5s
        assertEquals(3_500L, delta1)
        tracker.advanceSession(xPkg, 90_000L)

        val midSession = tracker.getSession(xPkg)
        assertNotNull(midSession)
        assertEquals(90, midSession!!.elapsedSeconds)
        assertFalse(midSession.isExpired)

        // User switches to WhatsApp -> global advances, X session stays paused at 90s
        tracker.advanceGlobal(25_000L)
        assertEquals(25, tracker.getGlobalElapsedSeconds())
        assertEquals(90, tracker.getSession(xPkg)!!.elapsedSeconds)

        // User returns to X -> remaining 210s completes and expires
        val finished = tracker.advanceSession(xPkg, 210_000L)!!
        assertEquals(300, finished.elapsedSeconds)
        assertTrue(finished.isExpired)

        // User confirms Takeover challenge word -> escalating friction activates and arms shorter follow-up
        tracker.recordTakeoverOverride(
            forSessionPackage = xPkg,
            forSessionLabel = "X",
            baseIntervalMinutes = 3,
            escalatingEnabled = true
        )
        assertEquals(1, tracker.getConsecutiveOverrides())
        val escalatedSession = tracker.getSession(xPkg)!!
        assertEquals(0, escalatedSession.elapsedSeconds)
        assertTrue(escalatedSession.targetSeconds < 180)

        // Screen locks -> everything resets cleanly
        tracker.resetOnScreenOff()
        assertNull(tracker.getSession(xPkg))
        assertEquals(0, tracker.getGlobalElapsedSeconds())
        assertEquals(0, tracker.getConsecutiveOverrides())
    }

    @Test
    fun hasActiveEnforcement_trueWhenOnlyDetoxOrPromptRulesActive() {
        val now = 100_000L
        val emptySettings = GuardSettings(isGuardEnabled = false)
        assertFalse(emptySettings.hasActiveEnforcement(now))

        val promptOnly = GuardSettings(
            isGuardEnabled = false,
            promptedPackages = setOf(xPkg)
        )
        assertTrue(promptOnly.hasActiveEnforcement(now))

        val detoxOnly = GuardSettings(
            isGuardEnabled = false,
            blockedAppsUntilMillis = mapOf(xPkg to now + 60_000L)
        )
        assertTrue(detoxOnly.hasActiveEnforcement(now))

        val expiredDetoxOnly = GuardSettings(
            isGuardEnabled = false,
            blockedAppsUntilMillis = mapOf(xPkg to now - 1_000L)
        )
        assertFalse(expiredDetoxOnly.hasActiveEnforcement(now))
    }

    @Test
    fun dailyUsageAggregation_countsLocalDayMinutesAndRespectsFiveMinuteThreshold() {
        val minute = 60_000L
        val events = listOf(
            // Own launcher open for 10 min -> must be excluded
            DailyUsageEvent(ownPkg, "MinimalistLauncherActivity", DailyUsageEventType.RESUMED, 0L),
            // X opens for 8 min across two activities without losing time, then screen locks
            DailyUsageEvent(xPkg, "MainActivity", DailyUsageEventType.RESUMED, 10 * minute),
            DailyUsageEvent(xPkg, "TweetDetailActivity", DailyUsageEventType.RESUMED, 14 * minute),
            DailyUsageEvent(xPkg, "MainActivity", DailyUsageEventType.PAUSED_OR_STOPPED, 14 * minute + 200L),
            DailyUsageEvent("", "", DailyUsageEventType.SCREEN_OFF_OR_LOCKED, 18 * minute),
            // WhatsApp opens for 4 min, then switches to X for 6 more min (total X = 14 min)
            DailyUsageEvent(whatsappPkg, "HomeActivity", DailyUsageEventType.RESUMED, 25 * minute),
            DailyUsageEvent(xPkg, "MainActivity", DailyUsageEventType.RESUMED, 29 * minute),
            DailyUsageEvent(xPkg, "MainActivity", DailyUsageEventType.PAUSED_OR_STOPPED, 35 * minute)
        )

        val totals = DailyAppUsageLoader.aggregateTodayMinutesFromEvents(
            events = events,
            nowMillis = 40 * minute,
            ownPackageName = ownPkg
        )

        assertNull(totals[ownPkg])
        assertEquals(14, totals[xPkg])
        assertEquals(4, totals[whatsappPkg])

        val xInfo = InstalledAppInfo(packageName = xPkg, label = "X", todayUsageMinutes = totals[xPkg] ?: 0)
        val waInfo = InstalledAppInfo(packageName = whatsappPkg, label = "WhatsApp", todayUsageMinutes = totals[whatsappPkg] ?: 0)
        val exactFiveInfo = InstalledAppInfo(packageName = "com.test.five", label = "Five", todayUsageMinutes = 5)

        assertTrue(xInfo.shouldShowDailyUsage)
        assertFalse(waInfo.shouldShowDailyUsage)
        assertTrue(exactFiveInfo.shouldShowDailyUsage)
    }

    @Test
    fun deepWork_classifiesDistractingCategories_andRespectsExceptionsAndExpiry() {
        assertEquals(
            DistractingCategory.SOCIAL,
            AppCategoryClassifier.classify("com.instagram.android", "Instagram")
        )
        assertEquals(
            DistractingCategory.VIDEO,
            AppCategoryClassifier.classify("com.google.android.youtube", "YouTube")
        )
        assertEquals(
            DistractingCategory.GAMES,
            AppCategoryClassifier.classify("com.supercell.clashroyale", "Clash Royale")
        )
        assertEquals(
            DistractingCategory.SHOPPING,
            AppCategoryClassifier.classify("com.amazon.mShop.android.shopping", "Amazon Shopping")
        )
        assertNull(
            AppCategoryClassifier.classify("com.google.android.calendar", "Calendar")
        )

        val now = 1_000_000L
        val settings = GuardSettings(
            isGuardEnabled = false,
            exceptionPackages = setOf(whatsappPkg),
            deepWorkUntilMillis = now + 30 * 60_000L,
            deepWorkCategories = setOf(DistractingCategory.SOCIAL, DistractingCategory.VIDEO),
            deepWorkStrictAllNonExempt = false
        )

        assertTrue(settings.isDeepWorkActive(now))
        assertTrue(settings.hasActiveEnforcement(now))

        // Non-exception Social app is restricted during Deep Work
        assertTrue(settings.isRestrictedByDeepWork(xPkg, DistractingCategory.SOCIAL, now))
        // Exception app is NEVER restricted even though WhatsApp is classified as Social
        assertFalse(settings.isRestrictedByDeepWork(whatsappPkg, DistractingCategory.SOCIAL, now))
        // Unselected category (Shopping) & non-distracting utility (Calendar) stay open
        assertFalse(settings.isRestrictedByDeepWork("com.amazon.mshop", DistractingCategory.SHOPPING, now))
        assertFalse(settings.isRestrictedByDeepWork("com.google.android.calendar", null, now))

        // Strict mode blocks all non-exception apps while preserving exceptions
        val strictSettings = settings.copy(deepWorkStrictAllNonExempt = true)
        assertTrue(strictSettings.isRestrictedByDeepWork("com.google.android.calendar", null, now))
        assertFalse(strictSettings.isRestrictedByDeepWork(whatsappPkg, DistractingCategory.SOCIAL, now))

        // After target end time, Deep Work expires automatically
        val afterExpiry = now + 31 * 60_000L
        assertFalse(settings.isDeepWorkActive(afterExpiry))
        assertFalse(settings.isRestrictedByDeepWork(xPkg, DistractingCategory.SOCIAL, afterExpiry))
        assertFalse(settings.hasActiveEnforcement(afterExpiry))
    }
}
