package com.example.util

import android.app.NotificationManager
import android.content.Context

/**
 * Controls Android's system Do Not Disturb interruption filter during Deep Work sessions
 * so non-exception apps are instantly silenced and cleanly restored when the session ends.
 */
object DeepWorkSilenceController {

    const val NO_SAVED_FILTER = -1

    /**
     * Enables priority Do Not Disturb if notification policy access is granted.
     * Returns the previous interruption filter so it can be restored when Deep Work ends,
     * or [NO_SAVED_FILTER] if policy access is not granted or DND was already silencing.
     */
    fun enableSilence(context: Context): Int {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return NO_SAVED_FILTER
        if (!manager.isNotificationPolicyAccessGranted) {
            return NO_SAVED_FILTER
        }
        return runCatching {
            val previousFilter = manager.currentInterruptionFilter
            if (previousFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                previousFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                previousFilter
            } else {
                // User already had DND/Alarms/Total Silence enabled; leave it untouched and don't clobber on exit.
                NO_SAVED_FILTER
            }
        }.getOrDefault(NO_SAVED_FILTER)
    }

    /**
     * Restores the interruption filter that was active before Deep Work started.
     */
    fun restoreSilence(context: Context, savedFilter: Int) {
        if (savedFilter == NO_SAVED_FILTER) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        runCatching {
            manager.setInterruptionFilter(savedFilter)
        }
    }
}
