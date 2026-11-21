package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sathiDataStore: DataStore<Preferences> by preferencesDataStore(name = "sathi_prefs")

enum class OfflinePackState {
    NOT_INSTALLED,
    DOWNLOADING,
    PAUSED,
    INSTALLED
}

enum class OfflinePackTier(val sizeLabelBn: String, val sizeLabelEn: String, val sizeMb: Int) {
    STANDARD("পূর্ণ জ্ঞান প্যাক (১৬টি বিষয়)", "Full Knowledge Pack (16 Topics)", 16),
    LITE("হালকা প্যাক (৮টি বিষয় • পুরোনো ফোন)", "Lite Pack (8 Topics • Old Phones)", 8)
}

data class LocalSettingsState(
    val offlinePackState: OfflinePackState = OfflinePackState.NOT_INSTALLED,
    val offlinePackTier: OfflinePackTier = OfflinePackTier.STANDARD,
    val offlineDownloadProgress: Float = 0f,
    val wifiOnlyDownload: Boolean = true,
    val offlineModeEnabled: Boolean = false,
    val failedCodeAttempts: Int = 0,
    val codeLockoutUntilMs: Long = 0L,
    val preferredLanguageFallback: String = "bn",
    val preferredVoiceFallback: String = "female",
    val customGeminiApiKey: String = "",
    val autoVoiceEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false
)

class SathiPreferences(private val context: Context) {
    private object Keys {
        val OFFLINE_PACK_STATE = stringPreferencesKey("offline_pack_state")
        val OFFLINE_PACK_TIER = stringPreferencesKey("offline_pack_tier")
        val OFFLINE_DOWNLOAD_PROGRESS = floatPreferencesKey("offline_download_progress")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val OFFLINE_MODE_ENABLED = booleanPreferencesKey("offline_mode_enabled")
        val FAILED_CODE_ATTEMPTS = intPreferencesKey("failed_code_attempts")
        val CODE_LOCKOUT_UNTIL_MS = longPreferencesKey("code_lockout_until_ms")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val PREFERRED_VOICE = stringPreferencesKey("preferred_voice")
        val CUSTOM_GEMINI_API_KEY = stringPreferencesKey("custom_gemini_api_key")
        val AUTO_VOICE_ENABLED = booleanPreferencesKey("auto_voice_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
    }

    val settingsFlow: Flow<LocalSettingsState> = context.sathiDataStore.data.map { prefs ->
        LocalSettingsState(
            offlinePackState = enumValues<OfflinePackState>().firstOrNull {
                it.name == prefs[Keys.OFFLINE_PACK_STATE]
            } ?: OfflinePackState.NOT_INSTALLED,
            offlinePackTier = enumValues<OfflinePackTier>().firstOrNull {
                it.name == prefs[Keys.OFFLINE_PACK_TIER]
            } ?: OfflinePackTier.STANDARD,
            offlineDownloadProgress = prefs[Keys.OFFLINE_DOWNLOAD_PROGRESS] ?: 0f,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: true,
            offlineModeEnabled = prefs[Keys.OFFLINE_MODE_ENABLED] ?: false,
            failedCodeAttempts = prefs[Keys.FAILED_CODE_ATTEMPTS] ?: 0,
            codeLockoutUntilMs = prefs[Keys.CODE_LOCKOUT_UNTIL_MS] ?: 0L,
            preferredLanguageFallback = prefs[Keys.PREFERRED_LANGUAGE] ?: "bn",
            preferredVoiceFallback = prefs[Keys.PREFERRED_VOICE] ?: "female",
            customGeminiApiKey = prefs[Keys.CUSTOM_GEMINI_API_KEY] ?: "",
            autoVoiceEnabled = prefs[Keys.AUTO_VOICE_ENABLED] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false
        )
    }

    suspend fun setOfflinePackState(state: OfflinePackState, progress: Float) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_PACK_STATE] = state.name
            prefs[Keys.OFFLINE_DOWNLOAD_PROGRESS] = progress.coerceIn(0f, 1f)
            if (state != OfflinePackState.INSTALLED) {
                prefs[Keys.OFFLINE_MODE_ENABLED] = false
            }
        }
    }

    suspend fun setOfflinePackTier(tier: OfflinePackTier) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_PACK_TIER] = tier.name
        }
    }

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY_DOWNLOAD] = wifiOnly
        }
    }

    suspend fun setOfflineModeEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_MODE_ENABLED] = enabled
        }
    }

    suspend fun recordCodeFailure(): Long {
        var lockoutUntil = 0L
        context.sathiDataStore.edit { prefs ->
            val attempts = (prefs[Keys.FAILED_CODE_ATTEMPTS] ?: 0) + 1
            prefs[Keys.FAILED_CODE_ATTEMPTS] = attempts
            if (attempts >= 5) {
                lockoutUntil = System.currentTimeMillis() + 5 * 60_000L
                prefs[Keys.CODE_LOCKOUT_UNTIL_MS] = lockoutUntil
                prefs[Keys.FAILED_CODE_ATTEMPTS] = 0
            }
        }
        return lockoutUntil
    }

    suspend fun resetCodeFailures() {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.FAILED_CODE_ATTEMPTS] = 0
            prefs[Keys.CODE_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun saveLanguageAndVoice(language: String, voice: String) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_LANGUAGE] = language
            prefs[Keys.PREFERRED_VOICE] = voice
        }
    }

    suspend fun saveCustomGeminiApiKey(apiKey: String) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setAutoVoiceEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_VOICE_ENABLED] = enabled
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.sathiDataStore.edit { it.clear() }
    }
}
