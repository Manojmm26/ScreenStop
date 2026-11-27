package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.DistractingCategory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Classifies installed apps into distracting categories for Deep Work mode.
 * Combines Android's official ApplicationInfo.category metadata (API 26+) with
 * deterministic package-name & label heuristics so major distracting apps are
 * reliably caught even when their APK omits android:appCategory.
 */
object AppCategoryClassifier {

    private val cache = ConcurrentHashMap<String, OptionalCategory>()

    private data class OptionalCategory(val category: DistractingCategory?)

    fun classifyPackage(context: Context, packageName: String): DistractingCategory? {
        cache[packageName]?.let { return it.category }

        val resolved = runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo)?.toString().orEmpty()
            classifyAppInfo(appInfo, label)
        }.getOrNull() ?: classify(
            packageName = packageName,
            label = "",
            androidCategory = null,
            isLegacyGame = false
        )

        cache[packageName] = OptionalCategory(resolved)
        return resolved
    }

    fun classifyAppInfo(appInfo: ApplicationInfo, label: String): DistractingCategory? {
        val androidCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo.category
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val isLegacyGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        val category = classify(
            packageName = appInfo.packageName,
            label = label,
            androidCategory = androidCategory,
            isLegacyGame = isLegacyGame
        )
        cache[appInfo.packageName] = OptionalCategory(category)
        return category
    }

    /**
     * Pure JVM-testable classification engine.
     */
    fun classify(
        packageName: String,
        label: String,
        androidCategory: Int? = null,
        isLegacyGame: Boolean = false
    ): DistractingCategory? {
        val pkg = packageName.lowercase(Locale.US)
        val lbl = label.lowercase(Locale.US)

        // 1. High-confidence known package heuristics first (handles apps that misreport category).
        if (matchesAny(pkg, SOCIAL_PACKAGES) || matchesAny(lbl, SOCIAL_LABEL_HINTS)) {
            return DistractingCategory.SOCIAL
        }
        if (matchesAny(pkg, VIDEO_PACKAGES) || matchesAny(lbl, VIDEO_LABEL_HINTS)) {
            return DistractingCategory.VIDEO
        }
        if (matchesAny(pkg, SHOPPING_PACKAGES) || matchesAny(lbl, SHOPPING_LABEL_HINTS)) {
            return DistractingCategory.SHOPPING
        }
        if (matchesAny(pkg, NEWS_PACKAGES) || matchesAny(lbl, NEWS_LABEL_HINTS)) {
            return DistractingCategory.NEWS
        }
        if (isLegacyGame || matchesAny(pkg, GAME_PACKAGES)) {
            return DistractingCategory.GAMES
        }

        // 2. Android ApplicationInfo.category (API 26+)
        if (androidCategory != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (androidCategory) {
                ApplicationInfo.CATEGORY_SOCIAL -> return DistractingCategory.SOCIAL
                ApplicationInfo.CATEGORY_VIDEO -> return DistractingCategory.VIDEO
                ApplicationInfo.CATEGORY_GAME -> return DistractingCategory.GAMES
                ApplicationInfo.CATEGORY_NEWS -> return DistractingCategory.NEWS
            }
        }

        return null
    }

    fun clearCache() {
        cache.clear()
    }

    private fun matchesAny(value: String, needles: List<String>): Boolean {
        if (value.isBlank()) return false
        return needles.any { value.contains(it) }
    }

    private val SOCIAL_PACKAGES = listOf(
        "com.instagram",
        "com.twitter",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.tiktok",
        "com.reddit",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.snapchat",
        "com.pinterest",
        "com.linkedin",
        "com.discord",
        "com.tumblr",
        "com.whatsapp",
        "org.telegram",
        "com.viber",
        "com.bsky",
        "xyz.blueskyweb",
        "com.threads",
        "com.instagram.barcelona",
        "com.bereal",
        "com.tinder",
        "com.bumble",
        "co.hinge",
        "com.quora",
        "com.vkontakte",
        "jp.naver.line"
    )

    private val SOCIAL_LABEL_HINTS = listOf(
        "instagram",
        "tiktok",
        "reddit",
        "facebook",
        "snapchat",
        "threads",
        "twitter",
        "discord",
        "pinterest",
        "tumblr",
        "bluesky",
        "mastodon"
    )

    private val VIDEO_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.netflix",
        "tv.twitch",
        "com.amazon.avod",
        "com.disney.disneyplus",
        "com.hulu",
        "com.hbo",
        "com.wbd.stream",
        "com.vimeo",
        "com.dailymotion",
        "in.startv.hotstar",
        "com.jio.media.ondemand",
        "com.crunchyroll",
        "com.peacocktv",
        "com.cbs.app",
        "com.plexapp",
        "com.kick"
    )

    private val VIDEO_LABEL_HINTS = listOf(
        "youtube",
        "netflix",
        "twitch",
        "disney+",
        "prime video",
        "hulu",
        "crunchyroll",
        "hotstar"
    )

    private val NEWS_PACKAGES = listOf(
        "com.google.android.apps.magazines",
        "flipboard.app",
        "com.nytimes",
        "com.washingtonpost",
        "com.cnn",
        "bbc.mobile.news",
        "com.foxnews",
        "com.guardian",
        "com.reuters",
        "com.bloomberg",
        "com.medium.reader",
        "com.substack",
        "com.inshorts",
        "com.apple.news",
        "com.yahoo.mobile.client.android.yahoo"
    )

    private val NEWS_LABEL_HINTS = listOf(
        "flipboard",
        "google news",
        "bbc news",
        "cnn",
        "inshorts",
        "substack"
    )

    private val SHOPPING_PACKAGES = listOf(
        "com.amazon.mshop",
        "com.ebay",
        "com.alibaba",
        "com.einnovation.temu",
        "com.zzkko",
        "com.flipkart",
        "com.myntra",
        "com.etsy",
        "com.shopify.arrive",
        "com.walmart",
        "com.target",
        "com.bestbuy",
        "com.meesho",
        "com.shopee",
        "com.lazada",
        "com.shein",
        "com.nike.omega",
        "com.zara"
    )

    private val SHOPPING_LABEL_HINTS = listOf(
        "amazon shopping",
        "ebay",
        "temu",
        "shein",
        "flipkart",
        "aliexpress",
        "shopee",
        "myntra"
    )

    private val GAME_PACKAGES = listOf(
        "com.supercell",
        "com.king.",
        "com.mojang",
        "com.roblox",
        "com.epicgames",
        "com.riotgames",
        "com.activision",
        "com.ea.game",
        "com.ea.gp",
        "com.gameloft",
        "com.nianticlabs",
        "com.mihoyo",
        "com.tencent.ig",
        "com.pubg",
        "com.dts.freefire",
        "com.innersloth",
        "com.kiloo",
        "com.miniclip",
        "com.zynga",
        "com.playrix",
        "com.rovio",
        "com.chess"
    )
}
