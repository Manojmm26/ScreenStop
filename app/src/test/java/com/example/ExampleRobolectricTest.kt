package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.GuardDefaults
import com.example.service.ScreenGuardService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches ScreenStop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ScreenStop", appName)
    }

    @Test
    fun `default interval and lock remaining formatting work accurately`() {
        assertEquals(3, GuardDefaults.DEFAULT_INTERVAL_MINUTES)
        assertEquals("00:00", GuardDefaults.formatDuration(0))
        assertEquals("03:00", GuardDefaults.formatDuration(180))
        assertEquals("2h 30m", GuardDefaults.formatRemainingLock((2 * 3600L + 30 * 60L) * 1000L))
        assertEquals("1d 2h", GuardDefaults.formatRemainingLock((26 * 3600L) * 1000L))
    }

    @Test
    fun `escalating friction increases challenge words and shortens interval`() {
        assertEquals(1, GuardDefaults.challengeWordCountForOverrides(0, escalatingEnabled = true))
        assertEquals(2, GuardDefaults.challengeWordCountForOverrides(1, escalatingEnabled = true))
        assertEquals(3, GuardDefaults.challengeWordCountForOverrides(2, escalatingEnabled = true))
        assertEquals(1, GuardDefaults.challengeWordCountForOverrides(3, escalatingEnabled = false))

        val base = GuardDefaults.effectiveIntervalSeconds(3, 0, escalatingEnabled = true)
        val level2 = GuardDefaults.effectiveIntervalSeconds(3, 1, escalatingEnabled = true)
        val level3 = GuardDefaults.effectiveIntervalSeconds(3, 2, escalatingEnabled = true)
        assertEquals(180, base)
        assertTrue(level2 < base)
        assertTrue(level3 < level2)
        assertTrue(level3 >= GuardDefaults.MIN_ESCALATED_INTERVAL_SECONDS)
    }

    @Test
    fun `multi-word challenge picker returns distinct words avoiding previous selection`() {
        val firstBatch = GuardDefaults.pickRandomChallengeWords(3, emptyList())
        assertEquals(3, firstBatch.toSet().size)

        val secondBatch = GuardDefaults.pickRandomChallengeWords(3, firstBatch)
        assertEquals(3, secondBatch.toSet().size)
        assertTrue(secondBatch.none { it in firstBatch })
    }

    @Test
    fun `ScreenGuardService starts foreground immediately on test takeover and how-long prompt`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            ScreenGuardService.ACTION_TEST_TAKEOVER,
            ScreenGuardService.ACTION_TEST_HOW_LONG
        ).forEach { action ->
            val controller = Robolectric.buildService(
                ScreenGuardService::class.java,
                Intent(context, ScreenGuardService::class.java).setAction(action)
            )
            val service = controller.create().startCommand(0, 1).get()
            val shadowService = Shadows.shadowOf(service)
            assertNotNull(shadowService.lastForegroundNotification)
            controller.destroy()
        }
    }
}
