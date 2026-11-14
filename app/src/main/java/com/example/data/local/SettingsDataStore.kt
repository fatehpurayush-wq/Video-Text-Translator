package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CaptionStyleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "vtt_settings")

data class UserSettings(
    val defaultSourceLang: String = "auto",
    val defaultTargetLang: String = "hi",
    val frameSamplingIntervalMs: Long = 1000L, // 400ms (accurate) .. 2500ms (fast)
    val minTextLength: Int = 3,
    val wifiOnlyDownloads: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.OBSIDIAN,
    val recentTargetLangs: List<String> = listOf("hi", "en", "mr", "ta", "te"),
    val defaultCaptionStyle: CaptionStyleConfig = CaptionStyleConfig(),
    val showLiveOverlayDefault: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val DEFAULT_SOURCE_LANG = stringPreferencesKey("default_source_lang")
        val DEFAULT_TARGET_LANG = stringPreferencesKey("default_target_lang")
        val FRAME_INTERVAL_MS = longPreferencesKey("frame_interval_ms")
        val MIN_TEXT_LENGTH = intPreferencesKey("min_text_length")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RECENT_TARGETS = stringPreferencesKey("recent_targets")
        val CAPTION_STYLE = stringPreferencesKey("caption_style")
        val LIVE_OVERLAY = booleanPreferencesKey("live_overlay")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.OBSIDIAN.name
        val theme = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.OBSIDIAN)
        val recentStr = prefs[Keys.RECENT_TARGETS] ?: "hi,en,mr,ta,te"
        val recent = recentStr.split(",").filter { it.isNotBlank() }.distinct()
        val captionJson = prefs[Keys.CAPTION_STYLE]
        val captionStyle = if (captionJson != null) {
            runCatching { json.decodeFromString<CaptionStyleConfig>(captionJson) }
                .getOrDefault(CaptionStyleConfig())
        } else {
            CaptionStyleConfig()
        }

        UserSettings(
            defaultSourceLang = prefs[Keys.DEFAULT_SOURCE_LANG] ?: "auto",
            defaultTargetLang = prefs[Keys.DEFAULT_TARGET_LANG] ?: "hi",
            frameSamplingIntervalMs = prefs[Keys.FRAME_INTERVAL_MS] ?: 1000L,
            minTextLength = prefs[Keys.MIN_TEXT_LENGTH] ?: 3,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: true,
            themeMode = theme,
            recentTargetLangs = recent.ifEmpty { listOf("hi", "en") },
            defaultCaptionStyle = captionStyle,
            showLiveOverlayDefault = prefs[Keys.LIVE_OVERLAY] ?: true
        )
    }

    suspend fun setDefaultSourceLang(code: String) {
        context.dataStore.edit { it[Keys.DEFAULT_SOURCE_LANG] = code }
    }

    suspend fun setDefaultTargetLang(code: String) {
        context.dataStore.edit { it[Keys.DEFAULT_TARGET_LANG] = code }
        recordRecentTargetLang(code)
    }

    suspend fun setFrameSamplingIntervalMs(intervalMs: Long) {
        context.dataStore.edit { it[Keys.FRAME_INTERVAL_MS] = intervalMs.coerceIn(350L, 2500L) }
    }

    suspend fun setMinTextLength(length: Int) {
        context.dataStore.edit { it[Keys.MIN_TEXT_LENGTH] = length.coerceIn(1, 15) }
    }

    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultCaptionStyle(style: CaptionStyleConfig) {
        context.dataStore.edit { it[Keys.CAPTION_STYLE] = json.encodeToString(style) }
    }

    suspend fun setShowLiveOverlayDefault(show: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_OVERLAY] = show }
    }

    suspend fun recordRecentTargetLang(code: String) {
        if (code == "auto") return
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.RECENT_TARGETS] ?: "hi,en,mr,ta,te")
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableList()
            current.removeAll { it == code }
            current.add(0, code)
            prefs[Keys.RECENT_TARGETS] = current.take(7).joinToString(",")
        }
    }
}
