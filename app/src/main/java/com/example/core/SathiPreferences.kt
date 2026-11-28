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

enum class SathiThemeMode(val labelBn: String, val labelEn: String) {
    DAY("দিন", "Day"),
    NIGHT("রাত", "Night"),
    SYSTEM("অটো", "Auto")
}

enum class SathiOrbStyle(
    val titleEn: String,
    val titleBn: String,
    val subtitleEn: String,
    val subtitleBn: String,
    val primaryColorHex: Long,
    val accentColorHex: Long
) {
    ORBIT_BLUE("Orbit Blue", "অরবিট ব্লু", "Blue dots circling a core", "মূল গোলক ঘিরে ঘূর্ণায়মান নীল বিন্দু", 0xFF38BDF8, 0xFF0EA5E9),
    NEBULA("Nebula", "নেবুলা", "Violet particle sphere", "বেগুনি নক্ষত্রকণার উজ্জ্বল গোলক", 0xFFA855F7, 0xFF6366F1),
    PLASMA_RING("Plasma Ring", "প্লাজমা রিং", "Cyan rings and pink glow", "সায়ান বলয় ও গোলাপি প্লাজমা আভা", 0xFF22D3EE, 0xFFEC4899),
    PARTICLE_GLOBE("Particle Globe", "পার্টিকেল গ্লোব", "Icy spinning globe", "বরফ-নীল ঘূর্ণায়মান কণা গোলক", 0xFF7DD3FC, 0xFF3B82F6),
    ECLIPSE("Eclipse", "ইকলিপ্স", "Golden corona round the dark", "অন্ধকার কেন্দ্র ঘিরে সোনালি জ্যোতির্বলয়", 0xFFF59E0B, 0xFFFDE68A),
    RINGED_PLANET("Ringed Planet", "রিংড প্ল্যানেট", "Banded world with a ring", "চারপাশে বলয় ঘেরা গ্রহ", 0xFFEAB308, 0xFF38BDF8),
    WIREFRAME("Wireframe", "ওয়্যারফ্রেম", "Glowing grid globe", "উজ্জ্বল গ্রিড জালের গোলক", 0xFF06B6D4, 0xFF67E8F9),
    NORTHERN_GLOW("Northern Glow", "নর্দার্ন গ্লো", "Drifting green and violet light", "সবুজ ও বেগুনি মেরুজ্যোতি", 0xFF10B981, 0xFF8B5CF6),
    MAGMA("Magma", "ম্যাগমা", "Molten rock, slow and hot", "উত্তপ্ত গলিত লাভা আভা", 0xFFEF4444, 0xFFF97316),
    HALO("Halo", "হ্যালো", "A neon ring that breathes", "শ্বাস নেওয়া উজ্জ্বল নিওন বলয়", 0xFF38BDF8, 0xFF818CF8),
    STORM_CORE("Storm Core", "স্টর্ম কোর", "Lightning inside dark glass", "কালো কাঁচের ভেতর বিদ্যুৎ চমক", 0xFF60A5FA, 0xFFE0F2FE),
    VOICE_RING("Voice Ring", "ভয়েস রিং", "Dancing circular equalizer", "নাচতে থাকা গোল ভয়েস ইকুয়ালাইজার", 0xFF2DD4BF, 0xFF38BDF8),
    GLOW_FLIES("Glow Flies", "জোনাকি আভা", "Blinking lights in glass", "কাঁচের ভেতর মিটমিটে আলো", 0xFFA3E635, 0xFF22D3EE),
    SPIRAL("Spiral", "স্পাইরাল", "Four arms of light", "চার বাহুর ঘূর্ণায়মান আলোর ছটা", 0xFF2DD4BF, 0xFF818CF8),
    MERCURY("Mercury", "মার্কারি", "Liquid silver", "তরল রূপার ঝলকানি", 0xFFE2E8F0, 0xFF94A3B8),
    SUGAR_SWIRL("Sugar Swirl", "সুগার সোয়ার্ল", "Soft pink and mint marble", "কোমল গোলাপি ও মিন্ট মার্বেল", 0xFFF472B6, 0xFF5EEAD4),
    CODE_RAIN("Code Rain", "কোড রেইন", "Falling green code", "ঝরে পড়া সবুজ ডিজিটাল কোড", 0xFF22C55E, 0xFF86EFAC),
    WATCHER("Watcher", "ওয়াচার", "An eye that follows your voice", "আপনার কথার তালে চলা স্মার্ট চোখ", 0xFF38BDF8, 0xFFF43F5E),
    LUNAR("Lunar", "লুনার", "Craters and shadow", "চাঁদের গহ্বর ও মায়াবী ছায়া", 0xFFCBD5E1, 0xFF64748B),
    SOAP_BUBBLE("Soap Bubble", "সাবান বাবল", "Wobbling soap film", "রঙধনু আভার দুলতে থাকা বাবল", 0xFF67E8F9, 0xFFF472B6),
    CANDLELIGHT("Candlelight", "মোমবাতির আলো", "One calm point of light", "শান্ত স্নিগ্ধ আলোর শিখা", 0xFFFBBF24, 0xFFFB923C),
    AMBER_PULSE("Amber Pulse", "অ্যাম্বার পালস", "Warm glow with ripples", "উষ্ণ সোনালি ঢেউ ও কম্পন", 0xFFF59E0B, 0xFFFDE047),
    FROST("Frost", "ফ্রস্ট", "Soft frosted light", "স্নিগ্ধ তুষার-শীতল আলো", 0xFFBAE6FD, 0xFFE0F2FE),
    TARGET_LOCK("Target Lock", "টার্গেট লক", "Turning rings, gold heart", "ঘূর্ণায়মান বলয় ও সোনালি কেন্দ্র", 0xFF38BDF8, 0xFFFACC15),
    DEEP_SEA("Deep Sea", "ডিপ সি", "Blue water in motion", "গভীর সমুদ্রের চলমান নীল ঢেউ", 0xFF0284C7, 0xFF2DD4BF),
    SUNSET_GLASS("Sunset Glass", "সানসেট গ্লাস", "Orange and coral swirl", "কমলা ও প্রবাল রঙের গোধূলি", 0xFFF97316, 0xFFFB7185),
    NEON_PINK("Neon Pink", "নিওন পিংক", "Pink and violet pulse", "গোলাপি ও বেগুনি নিওন স্পন্দন", 0xFFEC4899, 0xFFA855F7)
}

enum class SathiAnimationStyle(val labelEn: String, val labelBn: String) {
    OFF("Off", "বন্ধ (Off)"),
    SMOOTH("Smooth", "মসৃণ (Smooth)"),
    BOUNCY("Bouncy", "বাউন্সি (Bouncy)"),
    ZOOM("Zoom", "জুম (Zoom)")
}

enum class SathiColorTheme(
    val labelEn: String,
    val labelBn: String,
    val isDarkPalette: Boolean,
    val previewPrimaryHex: Long,
    val previewBgHex: Long
) {
    GOLDEN_DAY("Golden Day", "সোনালি দিন", false, 0xFFB76E00, 0xFFFFF8DC),
    OCEAN("Ocean", "মহাসাগর (Ocean)", true, 0xFF38BDF8, 0xFF061B38),
    MIDNIGHT_NIGHT("Midnight", "মধ্যরাত (Night)", true, 0xFFFFD93D, 0xFF101319),
    AURORA_VIOLET("Aurora Violet", "অরোরা বেগুনি", true, 0xFFC084FC, 0xFF170D2E),
    EMERALD_FOREST("Emerald", "পান্না সবুজ", true, 0xFF34D399, 0xFF07211A),
    ROSE_CRIMSON("Rose Glow", "গোলাপি আভা", true, 0xFFFB7185, 0xFF260B18),
    SUNSET_AMBER("Sunset", "সন্ধ্যার আবির", false, 0xFFD9531E, 0xFFFFF3E0),
    AMOLED_BLACK("Amoled Black", "অ্যামোলেড ব্ল্যাক", true, 0xFF38BDF8, 0xFF000000),
    MINT("Mint", "মিন্ট সবুজ", false, 0xFF059669, 0xFFECFDF5),
    LIQUID_GLASS("Liquid Glass", "লিকুইড গ্লাস", true, 0xFF67E8F9, 0xFF0B1F3A),
    FROST("Frost", "ফ্রস্ট (Frost)", false, 0xFF0284C7, 0xFFF0F9FF),
    SOFT_LIGHT("Soft Light", "সফট লাইট", false, 0xFF6366F1, 0xFFF8FAFC),
    ICE_BLUE("Ice Blue", "আইস ব্লু", false, 0xFF0EA5E9, 0xFFEFF6FF)
}

enum class OfflinePackTier(val sizeLabelBn: String, val sizeLabelEn: String, val sizeMb: Int) {
    DEEP("স্মার্ট জ্ঞান ভান্ডার (সর্বোচ্চ ২ জিবি)", "Smart Knowledge Brain (Up to 2 GB)", 2048),
    STANDARD("পূর্ণ জ্ঞান প্যাক (৬০+ বিষয়)", "Full Knowledge Pack (60+ Topics)", 24),
    LITE("হালকা প্যাক (২০টি বিষয়)", "Lite Pack (20 Topics)", 8)
}

data class LocalSettingsState(
    val offlinePackState: OfflinePackState = OfflinePackState.NOT_INSTALLED,
    val offlinePackTier: OfflinePackTier = OfflinePackTier.DEEP,
    val offlineDownloadProgress: Float = 0f,
    val wifiOnlyDownload: Boolean = false,
    val offlineModeEnabled: Boolean = false,
    val failedCodeAttempts: Int = 0,
    val codeLockoutUntilMs: Long = 0L,
    val preferredLanguageFallback: String = "bn",
    val preferredVoiceFallback: String = "female",
    val customGeminiApiKey: String = "",
    val autoVoiceEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val themeMode: SathiThemeMode = SathiThemeMode.NIGHT,
    val googleKeyVerified: Boolean = false,
    val autoExecuteActions: Boolean = true,
    val autoHandleIncomingComms: Boolean = true,
    val customSystemPrompt: String = SathiPreferences.DEFAULT_BOSS_SYSTEM_PROMPT,
    val orbStyle: SathiOrbStyle = SathiOrbStyle.ORBIT_BLUE,
    val startEndSoundsEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val batterySaver: Boolean = false,
    val animationStyle: SathiAnimationStyle = SathiAnimationStyle.SMOOTH,
    val colorTheme: SathiColorTheme = SathiColorTheme.OCEAN,
    val scrollLimit: Int = 5,
    val stopOnUserTouch: Boolean = true,
    val appLockEnabled: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val autoVirtualWindowEnabled: Boolean = true,
    val sosContacts: List<com.example.model.SosContact> = emptyList(),
    val sosHelpMessage: String = SathiPreferences.DEFAULT_SOS_MESSAGE,
    val sosIncludeLocation: Boolean = false,
    val todayTasks: List<com.example.model.SathiTaskItem> = emptyList(),
    val dailyStreakCount: Int = 1,
    val lastActiveDayEpoch: Long = 0L,
    val autoBackupEnabled: Boolean = false,
    val lastBackupTimeMs: Long = 0L
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GOOGLE_KEY_VERIFIED = booleanPreferencesKey("google_key_verified")
        val AUTO_EXECUTE_ACTIONS = booleanPreferencesKey("auto_execute_actions")
        val AUTO_HANDLE_INCOMING_COMMS = booleanPreferencesKey("auto_handle_incoming_comms")
        val CUSTOM_SYSTEM_PROMPT = stringPreferencesKey("custom_system_prompt")
        val ORB_STYLE = stringPreferencesKey("orb_style")
        val START_END_SOUNDS = booleanPreferencesKey("start_end_sounds")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val ANIMATION_STYLE = stringPreferencesKey("animation_style")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val SCROLL_LIMIT = intPreferencesKey("scroll_limit")
        val STOP_ON_USER_TOUCH = booleanPreferencesKey("stop_on_user_touch")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PERSISTENT_NOTIFICATION_ENABLED = booleanPreferencesKey("persistent_notification_enabled")
        val AUTO_VIRTUAL_WINDOW_ENABLED = booleanPreferencesKey("auto_virtual_window_enabled")
        val SOS_CONTACTS_JSON = stringPreferencesKey("sos_contacts_json")
        val SOS_HELP_MESSAGE = stringPreferencesKey("sos_help_message")
        val SOS_INCLUDE_LOCATION = booleanPreferencesKey("sos_include_location")
        val TODAY_TASKS_JSON = stringPreferencesKey("today_tasks_json")
        val DAILY_STREAK_COUNT = intPreferencesKey("daily_streak_count")
        val LAST_ACTIVE_DAY_EPOCH = longPreferencesKey("last_active_day_epoch")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val LAST_BACKUP_TIME_MS = longPreferencesKey("last_backup_time_ms")
    }

    val settingsFlow: Flow<LocalSettingsState> = context.sathiDataStore.data.map { prefs ->
        val themeMode = enumValues<SathiThemeMode>().firstOrNull {
            it.name == prefs[Keys.THEME_MODE]
        } ?: SathiThemeMode.NIGHT
        val colorTheme = enumValues<SathiColorTheme>().firstOrNull {
            it.name == prefs[Keys.COLOR_THEME]
        } ?: if (themeMode == SathiThemeMode.DAY) SathiColorTheme.GOLDEN_DAY else SathiColorTheme.OCEAN

        LocalSettingsState(
            offlinePackState = enumValues<OfflinePackState>().firstOrNull {
                it.name == prefs[Keys.OFFLINE_PACK_STATE]
            } ?: OfflinePackState.NOT_INSTALLED,
            offlinePackTier = enumValues<OfflinePackTier>().firstOrNull {
                it.name == prefs[Keys.OFFLINE_PACK_TIER]
            } ?: OfflinePackTier.DEEP,
            offlineDownloadProgress = prefs[Keys.OFFLINE_DOWNLOAD_PROGRESS] ?: 0f,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: false,
            offlineModeEnabled = prefs[Keys.OFFLINE_MODE_ENABLED] ?: false,
            failedCodeAttempts = prefs[Keys.FAILED_CODE_ATTEMPTS] ?: 0,
            codeLockoutUntilMs = prefs[Keys.CODE_LOCKOUT_UNTIL_MS] ?: 0L,
            preferredLanguageFallback = prefs[Keys.PREFERRED_LANGUAGE] ?: "bn",
            preferredVoiceFallback = prefs[Keys.PREFERRED_VOICE] ?: "female",
            customGeminiApiKey = prefs[Keys.CUSTOM_GEMINI_API_KEY] ?: "",
            autoVoiceEnabled = prefs[Keys.AUTO_VOICE_ENABLED] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            themeMode = themeMode,
            googleKeyVerified = prefs[Keys.GOOGLE_KEY_VERIFIED] ?: false,
            autoExecuteActions = prefs[Keys.AUTO_EXECUTE_ACTIONS] ?: true,
            autoHandleIncomingComms = prefs[Keys.AUTO_HANDLE_INCOMING_COMMS] ?: true,
            customSystemPrompt = prefs[Keys.CUSTOM_SYSTEM_PROMPT]
                ?.takeIf { it.isNotBlank() && it != LEGACY_DEFAULT_PROMPT && it != LEGACY_DEFAULT_BOSS_PROMPT_V2 }
                ?: DEFAULT_BOSS_SYSTEM_PROMPT,
            orbStyle = enumValues<SathiOrbStyle>().firstOrNull {
                it.name == prefs[Keys.ORB_STYLE]
            } ?: SathiOrbStyle.ORBIT_BLUE,
            startEndSoundsEnabled = prefs[Keys.START_END_SOUNDS] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            batterySaver = prefs[Keys.BATTERY_SAVER] ?: false,
            animationStyle = enumValues<SathiAnimationStyle>().firstOrNull {
                it.name == prefs[Keys.ANIMATION_STYLE]
            } ?: SathiAnimationStyle.SMOOTH,
            colorTheme = colorTheme,
            scrollLimit = (prefs[Keys.SCROLL_LIMIT] ?: 5).coerceIn(3, 20),
            stopOnUserTouch = prefs[Keys.STOP_ON_USER_TOUCH] ?: true,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] ?: false,
            autoVirtualWindowEnabled = prefs[Keys.AUTO_VIRTUAL_WINDOW_ENABLED] ?: true,
            sosContacts = parseSosContacts(prefs[Keys.SOS_CONTACTS_JSON]),
            sosHelpMessage = prefs[Keys.SOS_HELP_MESSAGE]?.takeIf { it.isNotBlank() } ?: DEFAULT_SOS_MESSAGE,
            sosIncludeLocation = prefs[Keys.SOS_INCLUDE_LOCATION] ?: false,
            todayTasks = parseTodayTasks(prefs[Keys.TODAY_TASKS_JSON]),
            dailyStreakCount = (prefs[Keys.DAILY_STREAK_COUNT] ?: 1).coerceAtLeast(1),
            lastActiveDayEpoch = prefs[Keys.LAST_ACTIVE_DAY_EPOCH] ?: 0L,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            lastBackupTimeMs = prefs[Keys.LAST_BACKUP_TIME_MS] ?: 0L
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

    suspend fun saveCustomGeminiApiKey(apiKey: String, verified: Boolean = false) {
        context.sathiDataStore.edit { prefs ->
            val cleaned = apiKey.trim()
            prefs[Keys.CUSTOM_GEMINI_API_KEY] = cleaned
            prefs[Keys.GOOGLE_KEY_VERIFIED] = cleaned.isNotBlank() && verified
        }
    }

    suspend fun setThemeMode(mode: SathiThemeMode) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
            val existingTheme = enumValues<SathiColorTheme>().firstOrNull {
                it.name == prefs[Keys.COLOR_THEME]
            }
            when (mode) {
                SathiThemeMode.DAY -> {
                    if (existingTheme == null || existingTheme.isDarkPalette) {
                        prefs[Keys.COLOR_THEME] = SathiColorTheme.GOLDEN_DAY.name
                    }
                }
                SathiThemeMode.NIGHT -> {
                    if (existingTheme == null || !existingTheme.isDarkPalette) {
                        prefs[Keys.COLOR_THEME] = SathiColorTheme.MIDNIGHT_NIGHT.name
                    }
                }
                SathiThemeMode.SYSTEM -> Unit
            }
        }
    }

    suspend fun setOrbStyle(style: SathiOrbStyle) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.ORB_STYLE] = style.name
        }
    }

    suspend fun setStartEndSoundsEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.START_END_SOUNDS] = enabled
        }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.REDUCE_MOTION] = enabled
        }
    }

    suspend fun setBatterySaver(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.BATTERY_SAVER] = enabled
        }
    }

    suspend fun setAnimationStyle(style: SathiAnimationStyle) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.ANIMATION_STYLE] = style.name
        }
    }

    suspend fun setColorTheme(theme: SathiColorTheme) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.COLOR_THEME] = theme.name
            prefs[Keys.THEME_MODE] = if (theme.isDarkPalette) {
                SathiThemeMode.NIGHT.name
            } else {
                SathiThemeMode.DAY.name
            }
        }
    }

    suspend fun setAutoVoiceEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_EXECUTE_ACTIONS] = prefs[Keys.AUTO_EXECUTE_ACTIONS] ?: true
            prefs[Keys.AUTO_VOICE_ENABLED] = enabled
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED] = enabled
        }
    }

    suspend fun setAutoExecuteActions(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_EXECUTE_ACTIONS] = enabled
        }
    }

    suspend fun setAutoHandleIncomingComms(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_HANDLE_INCOMING_COMMS] = enabled
        }
    }

    suspend fun saveCustomSystemPrompt(prompt: String) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_SYSTEM_PROMPT] = prompt.trim().ifBlank { DEFAULT_BOSS_SYSTEM_PROMPT }
        }
    }

    suspend fun setScrollLimit(limit: Int) {
        val allowed = listOf(3, 5, 10, 20).minByOrNull { kotlin.math.abs(it - limit) } ?: 5
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.SCROLL_LIMIT] = allowed
        }
    }

    suspend fun setStopOnUserTouch(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.STOP_ON_USER_TOUCH] = enabled
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setAutoVirtualWindowEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_VIRTUAL_WINDOW_ENABLED] = enabled
        }
    }

    suspend fun saveSosContacts(contacts: List<com.example.model.SosContact>) {
        val capped = contacts.take(5)
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.SOS_CONTACTS_JSON] = encodeSosContacts(capped)
        }
    }

    suspend fun saveSosMessage(message: String) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.SOS_HELP_MESSAGE] = message.trim().ifBlank { DEFAULT_SOS_MESSAGE }
        }
    }

    suspend fun setSosIncludeLocation(include: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.SOS_INCLUDE_LOCATION] = include
        }
    }

    suspend fun saveTodayTasks(tasks: List<com.example.model.SathiTaskItem>) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.TODAY_TASKS_JSON] = encodeTodayTasks(tasks.take(30))
        }
    }

    suspend fun bumpDailyStreakIfNewDay(todayEpochDay: Long = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())) {
        context.sathiDataStore.edit { prefs ->
            val lastDay = prefs[Keys.LAST_ACTIVE_DAY_EPOCH] ?: 0L
            val currentStreak = (prefs[Keys.DAILY_STREAK_COUNT] ?: 1).coerceAtLeast(1)
            when {
                lastDay == 0L -> {
                    prefs[Keys.DAILY_STREAK_COUNT] = 1
                    prefs[Keys.LAST_ACTIVE_DAY_EPOCH] = todayEpochDay
                }
                todayEpochDay == lastDay + 1L -> {
                    prefs[Keys.DAILY_STREAK_COUNT] = currentStreak + 1
                    prefs[Keys.LAST_ACTIVE_DAY_EPOCH] = todayEpochDay
                }
                todayEpochDay > lastDay + 1L -> {
                    prefs[Keys.DAILY_STREAK_COUNT] = 1
                    prefs[Keys.LAST_ACTIVE_DAY_EPOCH] = todayEpochDay
                }
            }
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun recordBackupTimestamp(timestampMs: Long = System.currentTimeMillis()) {
        context.sathiDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIME_MS] = timestampMs
        }
    }

    fun exportBackupJson(settings: LocalSettingsState): String {
        val root = org.json.JSONObject()
        root.put("version", 2)
        root.put("exportedAtMs", System.currentTimeMillis())
        root.put("language", settings.preferredLanguageFallback)
        root.put("voice", settings.preferredVoiceFallback)
        root.put("orbStyle", settings.orbStyle.name)
        root.put("colorTheme", settings.colorTheme.name)
        root.put("animationStyle", settings.animationStyle.name)
        root.put("reduceMotion", settings.reduceMotion)
        root.put("batterySaver", settings.batterySaver)
        root.put("startEndSoundsEnabled", settings.startEndSoundsEnabled)
        root.put("autoVoiceEnabled", settings.autoVoiceEnabled)
        root.put("wakeWordEnabled", settings.wakeWordEnabled)
        root.put("autoExecuteActions", settings.autoExecuteActions)
        root.put("autoHandleIncomingComms", settings.autoHandleIncomingComms)
        root.put("scrollLimit", settings.scrollLimit)
        root.put("stopOnUserTouch", settings.stopOnUserTouch)
        root.put("appLockEnabled", settings.appLockEnabled)
        root.put("persistentNotificationEnabled", settings.persistentNotificationEnabled)
        root.put("autoVirtualWindowEnabled", settings.autoVirtualWindowEnabled)
        root.put("sosHelpMessage", settings.sosHelpMessage)
        root.put("sosIncludeLocation", settings.sosIncludeLocation)
        root.put("sosContacts", org.json.JSONArray(encodeSosContacts(settings.sosContacts)))
        root.put("todayTasks", org.json.JSONArray(encodeTodayTasks(settings.todayTasks)))
        root.put("dailyStreakCount", settings.dailyStreakCount)
        root.put("customSystemPrompt", settings.customSystemPrompt)
        return root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Result<Unit> {
        return runCatching {
            val root = org.json.JSONObject(rawJson.trim())
            context.sathiDataStore.edit { prefs ->
                root.optString("language").takeIf { it.isNotBlank() }?.let { prefs[Keys.PREFERRED_LANGUAGE] = it }
                root.optString("voice").takeIf { it.isNotBlank() }?.let { prefs[Keys.PREFERRED_VOICE] = it }
                enumValues<SathiOrbStyle>().firstOrNull { it.name == root.optString("orbStyle") }
                    ?.let { prefs[Keys.ORB_STYLE] = it.name }
                enumValues<SathiColorTheme>().firstOrNull { it.name == root.optString("colorTheme") }
                    ?.let {
                        prefs[Keys.COLOR_THEME] = it.name
                        prefs[Keys.THEME_MODE] = if (it.isDarkPalette) SathiThemeMode.NIGHT.name else SathiThemeMode.DAY.name
                    }
                enumValues<SathiAnimationStyle>().firstOrNull { it.name == root.optString("animationStyle") }
                    ?.let { prefs[Keys.ANIMATION_STYLE] = it.name }
                if (root.has("reduceMotion")) prefs[Keys.REDUCE_MOTION] = root.optBoolean("reduceMotion")
                if (root.has("batterySaver")) prefs[Keys.BATTERY_SAVER] = root.optBoolean("batterySaver")
                if (root.has("startEndSoundsEnabled")) prefs[Keys.START_END_SOUNDS] = root.optBoolean("startEndSoundsEnabled")
                if (root.has("autoVoiceEnabled")) prefs[Keys.AUTO_VOICE_ENABLED] = root.optBoolean("autoVoiceEnabled")
                if (root.has("wakeWordEnabled")) prefs[Keys.WAKE_WORD_ENABLED] = root.optBoolean("wakeWordEnabled")
                if (root.has("autoExecuteActions")) prefs[Keys.AUTO_EXECUTE_ACTIONS] = root.optBoolean("autoExecuteActions")
                if (root.has("autoHandleIncomingComms")) prefs[Keys.AUTO_HANDLE_INCOMING_COMMS] = root.optBoolean("autoHandleIncomingComms")
                if (root.has("scrollLimit")) prefs[Keys.SCROLL_LIMIT] = root.optInt("scrollLimit", 5).coerceIn(3, 20)
                if (root.has("stopOnUserTouch")) prefs[Keys.STOP_ON_USER_TOUCH] = root.optBoolean("stopOnUserTouch")
                if (root.has("appLockEnabled")) prefs[Keys.APP_LOCK_ENABLED] = root.optBoolean("appLockEnabled")
                if (root.has("persistentNotificationEnabled")) {
                    prefs[Keys.PERSISTENT_NOTIFICATION_ENABLED] = root.optBoolean("persistentNotificationEnabled")
                }
                if (root.has("autoVirtualWindowEnabled")) {
                    prefs[Keys.AUTO_VIRTUAL_WINDOW_ENABLED] = root.optBoolean("autoVirtualWindowEnabled")
                }
                root.optString("sosHelpMessage").takeIf { it.isNotBlank() }?.let { prefs[Keys.SOS_HELP_MESSAGE] = it }
                if (root.has("sosIncludeLocation")) prefs[Keys.SOS_INCLUDE_LOCATION] = root.optBoolean("sosIncludeLocation")
                root.optJSONArray("sosContacts")?.let { arr ->
                    val parsed = parseSosContacts(arr.toString()).take(5)
                    prefs[Keys.SOS_CONTACTS_JSON] = encodeSosContacts(parsed)
                }
                root.optJSONArray("todayTasks")?.let { arr ->
                    val parsed = parseTodayTasks(arr.toString()).take(30)
                    prefs[Keys.TODAY_TASKS_JSON] = encodeTodayTasks(parsed)
                }
                root.optString("customSystemPrompt").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.CUSTOM_SYSTEM_PROMPT] = it
                }
                prefs[Keys.LAST_BACKUP_TIME_MS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clearAll() {
        context.sathiDataStore.edit { it.clear() }
    }

    companion object {
        private const val LEGACY_DEFAULT_PROMPT =
            "আমি Sathi AI (সাথী এআই), মোহাম্মদ বাইজিদ হাসানের তৈরি ব্যক্তিগত ও বুদ্ধিমান এআই সহকারী। আমি ব্যবহারকারীকে সবসময় সম্মানের সাথে 'বস' (Boss) বলে সম্বোধন করব। বাংলা ও ইংরেজিতে স্পষ্ট, দ্রুত ও নির্ভুল উত্তর দেব, লাইভ ভয়েস ও স্ক্রিন নির্দেশনায় সাহায্য করব এবং কখনো 'নমস্কার' বলব না।"

        const val DEFAULT_SOS_MESSAGE =
            "জরুরি সাহায্য প্রয়োজন! আমি বিপদে আছি, অনুগ্রহ করে দ্রুত যোগাযোগ করুন (Sathi AI SOS)।"

        private const val LEGACY_DEFAULT_BOSS_PROMPT_V2 =
            "Sathi AI System Prompt\n" +
            "Identity & Role: You are Sathi AI, an advanced Bengali-speaking personal assistant created by Mohammad Bayzid Hasan. Your primary purpose is to provide accurate, relevant responses and assist with device operations.\n" +
            "Automated Messaging: When directed to message a specific individual, locate them independently and send the message without requesting further confirmation.\n" +
            "Game Mode & Multimedia: If the user enters Free Fire or other listed games, automatically deactivate. Reactivate upon exiting. Monitor screens during YouTube, Facebook, or Messenger usage to provide assistance without permission prompts.\n" +
            "Incoming Call & Message Handling: Receive incoming communications first, engage in dialogue, and then summarize the core inquiry or issue for the user.\n" +
            "Accuracy & Interface: Maintain continuous live voice mode with an animated floating window during speech. Deliver concise, factual, and purely Bengali responses."

        const val DEFAULT_BOSS_SYSTEM_PROMPT =
            "Sathi AI System Prompt\n" +
            "Identity & Role: You are Sathi AI, an advanced Bengali-speaking personal assistant created by Mohammad Bayzid Hasan. Your primary purpose is to provide accurate, relevant responses and assist with device operations.\n" +
            "Automated Messaging: When directed to message a specific individual, locate them independently and send the message without requesting further confirmation.\n" +
            "Game Mode & Multimedia: If the user enters Free Fire or other listed games, automatically deactivate. Reactivate upon exiting. Monitor screens during YouTube, Facebook, or Messenger usage to provide assistance without permission prompts.\n" +
            "Incoming Call & Message Handling: Receive incoming communications first, engage in dialogue, and then summarize the core inquiry or issue for the user.\n" +
            "Accuracy & Interface: Maintain continuous live voice mode with an animated floating window during speech. Deliver concise, factual, and purely Bengali responses.\n" +
            "Strict Directive: তুমি কঠোরভাবে ব্যবহারকারীর নির্দেশ অনুসরণ করবে। কোনো বিষয়ে অনুমান না করে বা অপ্রয়োজনীয় সার্চ না করে সরাসরি নির্ভুল ও প্রাসঙ্গিক উত্তর দেবে।"

        private fun encodeSosContacts(contacts: List<com.example.model.SosContact>): String {
            val arr = org.json.JSONArray()
            contacts.take(5).forEach { c ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                    }
                )
            }
            return arr.toString()
        }

        private fun parseSosContacts(raw: String?): List<com.example.model.SosContact> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length().coerceAtMost(5)) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val phone = obj.optString("phone").trim()
                        if (phone.isNotBlank()) {
                            add(
                                com.example.model.SosContact(
                                    id = obj.optString("id").ifBlank { "sos_$i" },
                                    name = obj.optString("name").ifBlank { phone },
                                    phone = phone
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        private fun encodeTodayTasks(tasks: List<com.example.model.SathiTaskItem>): String {
            val arr = org.json.JSONArray()
            tasks.take(30).forEach { t ->
                arr.put(
                    org.json.JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("completed", t.completed)
                        put("createdAtMs", t.createdAtMs)
                    }
                )
            }
            return arr.toString()
        }

        private fun parseTodayTasks(raw: String?): List<com.example.model.SathiTaskItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val title = obj.optString("title").trim()
                        if (title.isNotBlank()) {
                            add(
                                com.example.model.SathiTaskItem(
                                    id = obj.optString("id").ifBlank { "task_$i" },
                                    title = title,
                                    completed = obj.optBoolean("completed", false),
                                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
