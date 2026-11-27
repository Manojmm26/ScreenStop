package com.example.util

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.PermissionSnapshot

object PermissionChecker {

    fun checkAll(context: Context): PermissionSnapshot {
        return PermissionSnapshot(
            hasOverlayPermission = hasOverlayPermission(context),
            hasUsageStatsPermission = hasUsageStatsPermission(context),
            hasNotificationPermission = hasNotificationPermission(context)
        )
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        if (mode == AppOpsManager.MODE_ALLOWED) return true
        if (mode == AppOpsManager.MODE_DEFAULT) {
            val hasNormalPerm = context.checkCallingOrSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasNormalPerm) return true
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60L * 60_000L,
                now
            )
            return !stats.isNullOrEmpty()
        }
        return false
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isDefaultHomeLauncher(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun openDefaultHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun createHomeIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
    }

    fun launchHomeScreen(context: Context) {
        runCatching { context.startActivity(createHomeIntent()) }
    }

    fun resolveAppLabel(
        context: Context,
        packageName: String,
        customLabels: Map<String, String> = emptyMap()
    ): String {
        customLabels[packageName]?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse { packageName }
    }

    fun openOverlaySettings(context: Context) {
        val packageIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            context.startActivity(packageIntent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val packageIntent = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            context.startActivity(packageIntent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }
}
