package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class DeviceAppEntry(
    val packageName: String,
    val label: String
)

object InstalledAppsLoader {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadLaunchableApps(context: Context): List<DeviceAppEntry> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val ownPackage = context.packageName
            val seen = HashSet<String>()
            resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackage || !seen.add(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                    .ifEmpty { pkg }
                DeviceAppEntry(packageName = pkg, label = label)
            }.sortedBy { it.label.lowercase() }
        }

    suspend fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, sizePx = 96)
                val imageBitmap = bitmap.asImageBitmap()
                iconCache[packageName] = imageBitmap
                imageBitmap
            }.getOrNull()
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
