package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.guardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screenstop_settings"
)

class GuardSettingsRepository(private val context: Context) {

    private object Keys {
        val IS_GUARD_ENABLED = booleanPreferencesKey("is_guard_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val ESCALATING_FRICTION = booleanPreferencesKey("escalating_friction_enabled")
        val EXCEPTION_PACKAGES = stringSetPreferencesKey("exception_packages")
        val PROMPTED_PACKAGES = stringSetPreferencesKey("prompted_packages")
        val BLOCKED_APPS_JSON = stringPreferencesKey("blocked_apps_until_json")
        val REMINDER_MESSAGES_JSON = stringPreferencesKey("reminder_messages_json")
        val LAUNCHER_FAVORITES_JSON = stringPreferencesKey("launcher_favorites_json")
        val LAUNCHER_HIDDEN_PACKAGES = stringSetPreferencesKey("launcher_hidden_packages")
        val LAUNCHER_RENAMED_JSON = stringPreferencesKey("launcher_renamed_json")
        val SHOW_GUARD_ON_HOME = booleanPreferencesKey("show_guard_status_on_home")
    }

    val settingsFlow: Flow<GuardSettings> = context.guardDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val enabled = prefs[Keys.IS_GUARD_ENABLED] ?: false
            val interval = (prefs[Keys.INTERVAL_MINUTES] ?: GuardDefaults.DEFAULT_INTERVAL_MINUTES)
                .coerceIn(GuardDefaults.MIN_INTERVAL_MINUTES, GuardDefaults.MAX_INTERVAL_MINUTES)
            val escalating = prefs[Keys.ESCALATING_FRICTION] ?: true
            val exceptions = prefs[Keys.EXCEPTION_PACKAGES] ?: emptySet()
            val prompted = prefs[Keys.PROMPTED_PACKAGES] ?: emptySet()
            val blockedMap = decodeLongMap(prefs[Keys.BLOCKED_APPS_JSON])
            val messages = decodeStringList(prefs[Keys.REMINDER_MESSAGES_JSON])
                .ifEmpty { GuardDefaults.DEFAULT_MESSAGES }
            val favorites = decodeStringList(prefs[Keys.LAUNCHER_FAVORITES_JSON])
            val hidden = prefs[Keys.LAUNCHER_HIDDEN_PACKAGES] ?: emptySet()
            val renamed = decodeStringMap(prefs[Keys.LAUNCHER_RENAMED_JSON])
            val showGuardOnHome = prefs[Keys.SHOW_GUARD_ON_HOME] ?: true

            GuardSettings(
                isGuardEnabled = enabled,
                intervalMinutes = interval,
                escalatingFrictionEnabled = escalating,
                exceptionPackages = exceptions,
                blockedAppsUntilMillis = blockedMap,
                promptedPackages = prompted,
                reminderMessages = messages,
                launcherFavoritePackages = favorites,
                launcherHiddenPackages = hidden,
                launcherRenamedApps = renamed,
                showGuardStatusOnHome = showGuardOnHome
            )
        }

    suspend fun getCurrentSettings(): GuardSettings = settingsFlow.first()

    suspend fun setGuardEnabled(enabled: Boolean) {
        context.guardDataStore.edit { prefs ->
            prefs[Keys.IS_GUARD_ENABLED] = enabled
        }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(
            GuardDefaults.MIN_INTERVAL_MINUTES,
            GuardDefaults.MAX_INTERVAL_MINUTES
        )
        context.guardDataStore.edit { prefs ->
            prefs[Keys.INTERVAL_MINUTES] = clamped
        }
    }

    suspend fun setEscalatingFrictionEnabled(enabled: Boolean) {
        context.guardDataStore.edit { prefs ->
            prefs[Keys.ESCALATING_FRICTION] = enabled
        }
    }

    suspend fun toggleExceptionPackage(packageName: String) {
        context.guardDataStore.edit { prefs ->
            val current = prefs[Keys.EXCEPTION_PACKAGES] ?: emptySet()
            val adding = packageName !in current
            prefs[Keys.EXCEPTION_PACKAGES] = if (adding) current + packageName else current - packageName
            if (adding) {
                // An exception app shouldn't simultaneously prompt "How long?"
                val prompted = prefs[Keys.PROMPTED_PACKAGES] ?: emptySet()
                if (packageName in prompted) {
                    prefs[Keys.PROMPTED_PACKAGES] = prompted - packageName
                }
            }
        }
    }

    suspend fun togglePromptedPackage(packageName: String) {
        context.guardDataStore.edit { prefs ->
            val current = prefs[Keys.PROMPTED_PACKAGES] ?: emptySet()
            val adding = packageName !in current
            prefs[Keys.PROMPTED_PACKAGES] = if (adding) current + packageName else current - packageName
            if (adding) {
                // Prompted apps participate in session limits rather than exceptions
                val exceptions = prefs[Keys.EXCEPTION_PACKAGES] ?: emptySet()
                if (packageName in exceptions) {
                    prefs[Keys.EXCEPTION_PACKAGES] = exceptions - packageName
                }
            }
        }
    }

    suspend fun setDetoxLock(packageName: String, durationMillis: Long) {
        val unlockAt = System.currentTimeMillis() + durationMillis
        context.guardDataStore.edit { prefs ->
            val map = decodeLongMap(prefs[Keys.BLOCKED_APPS_JSON]).toMutableMap()
            val now = System.currentTimeMillis()
            map.entries.removeAll { it.value <= now }
            map[packageName] = unlockAt
            prefs[Keys.BLOCKED_APPS_JSON] = encodeLongMap(map)
        }
    }

    suspend fun clearDetoxLock(packageName: String) {
        context.guardDataStore.edit { prefs ->
            val map = decodeLongMap(prefs[Keys.BLOCKED_APPS_JSON]).toMutableMap()
            map.remove(packageName)
            prefs[Keys.BLOCKED_APPS_JSON] = encodeLongMap(map)
        }
    }

    suspend fun pruneExpiredDetoxLocks() {
        val now = System.currentTimeMillis()
        context.guardDataStore.edit { prefs ->
            val map = decodeLongMap(prefs[Keys.BLOCKED_APPS_JSON])
            val active = map.filterValues { it > now }
            if (active.size != map.size) {
                prefs[Keys.BLOCKED_APPS_JSON] = encodeLongMap(active)
            }
        }
    }

    suspend fun toggleLauncherFavorite(packageName: String): Boolean {
        var addedOrRemoved = false
        context.guardDataStore.edit { prefs ->
            val current = decodeStringList(prefs[Keys.LAUNCHER_FAVORITES_JSON]).toMutableList()
            if (packageName in current) {
                current.remove(packageName)
                prefs[Keys.LAUNCHER_FAVORITES_JSON] = encodeStringList(current)
                addedOrRemoved = true
            } else if (current.size < GuardDefaults.MAX_LAUNCHER_FAVORITES) {
                current.add(packageName)
                prefs[Keys.LAUNCHER_FAVORITES_JSON] = encodeStringList(current)
                // Ensure pinned favorite isn't hidden
                val hidden = prefs[Keys.LAUNCHER_HIDDEN_PACKAGES] ?: emptySet()
                if (packageName in hidden) {
                    prefs[Keys.LAUNCHER_HIDDEN_PACKAGES] = hidden - packageName
                }
                addedOrRemoved = true
            }
        }
        return addedOrRemoved
    }

    suspend fun toggleLauncherHidden(packageName: String) {
        context.guardDataStore.edit { prefs ->
            val hidden = prefs[Keys.LAUNCHER_HIDDEN_PACKAGES] ?: emptySet()
            val hiding = packageName !in hidden
            prefs[Keys.LAUNCHER_HIDDEN_PACKAGES] = if (hiding) hidden + packageName else hidden - packageName
            if (hiding) {
                val favorites = decodeStringList(prefs[Keys.LAUNCHER_FAVORITES_JSON]).toMutableList()
                if (favorites.remove(packageName)) {
                    prefs[Keys.LAUNCHER_FAVORITES_JSON] = encodeStringList(favorites)
                }
            }
        }
    }

    suspend fun setLauncherCustomLabel(packageName: String, customLabel: String) {
        val trimmed = customLabel.trim()
        context.guardDataStore.edit { prefs ->
            val map = decodeStringMap(prefs[Keys.LAUNCHER_RENAMED_JSON]).toMutableMap()
            if (trimmed.isEmpty()) {
                map.remove(packageName)
            } else {
                map[packageName] = trimmed
            }
            prefs[Keys.LAUNCHER_RENAMED_JSON] = encodeStringMap(map)
        }
    }

    suspend fun setShowGuardStatusOnHome(show: Boolean) {
        context.guardDataStore.edit { prefs ->
            prefs[Keys.SHOW_GUARD_ON_HOME] = show
        }
    }

    suspend fun addMessage(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        context.guardDataStore.edit { prefs ->
            val current = decodeStringList(prefs[Keys.REMINDER_MESSAGES_JSON])
                .ifEmpty { GuardDefaults.DEFAULT_MESSAGES }
                .toMutableList()
            current.add(0, trimmed)
            prefs[Keys.REMINDER_MESSAGES_JSON] = encodeStringList(current)
        }
        return true
    }

    suspend fun updateMessage(index: Int, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        var updated = false
        context.guardDataStore.edit { prefs ->
            val current = decodeStringList(prefs[Keys.REMINDER_MESSAGES_JSON])
                .ifEmpty { GuardDefaults.DEFAULT_MESSAGES }
                .toMutableList()
            if (index in current.indices) {
                current[index] = trimmed
                prefs[Keys.REMINDER_MESSAGES_JSON] = encodeStringList(current)
                updated = true
            }
        }
        return updated
    }

    suspend fun deleteMessage(index: Int): Boolean {
        var deleted = false
        context.guardDataStore.edit { prefs ->
            val current = decodeStringList(prefs[Keys.REMINDER_MESSAGES_JSON])
                .ifEmpty { GuardDefaults.DEFAULT_MESSAGES }
                .toMutableList()
            if (current.size > 1 && index in current.indices) {
                current.removeAt(index)
                prefs[Keys.REMINDER_MESSAGES_JSON] = encodeStringList(current)
                deleted = true
            }
        }
        return deleted
    }

    suspend fun resetDefaultMessages() {
        context.guardDataStore.edit { prefs ->
            prefs[Keys.REMINDER_MESSAGES_JSON] = encodeStringList(GuardDefaults.DEFAULT_MESSAGES)
        }
    }

    private fun encodeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun decodeStringList(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optString(i, "").trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeLongMap(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeLongMap(rawJson: String?): Map<String, Long> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(rawJson)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optLong(key, 0L)
                    if (value > 0L) put(key, value)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeStringMap(rawJson: String?): Map<String, String> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(rawJson)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optString(key, "").trim()
                    if (value.isNotEmpty()) put(key, value)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
