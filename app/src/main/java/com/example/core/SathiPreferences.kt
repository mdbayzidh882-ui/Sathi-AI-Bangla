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
    SUNSET_AMBER("Sunset", "সন্ধ্যার আবির", false, 0xFFD9531E, 0xFFFFF3E0)
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
    val themeMode: SathiThemeMode = SathiThemeMode.DAY,
    val googleKeyVerified: Boolean = false,
    val autoExecuteActions: Boolean = true,
    val autoHandleIncomingComms: Boolean = true,
    val customSystemPrompt: String = SathiPreferences.DEFAULT_BOSS_SYSTEM_PROMPT,
    val orbStyle: SathiOrbStyle = SathiOrbStyle.ORBIT_BLUE,
    val startEndSoundsEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val batterySaver: Boolean = false,
    val animationStyle: SathiAnimationStyle = SathiAnimationStyle.SMOOTH,
    val colorTheme: SathiColorTheme = SathiColorTheme.GOLDEN_DAY
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
    }

    val settingsFlow: Flow<LocalSettingsState> = context.sathiDataStore.data.map { prefs ->
        val themeMode = enumValues<SathiThemeMode>().firstOrNull {
            it.name == prefs[Keys.THEME_MODE]
        } ?: SathiThemeMode.DAY
        val colorTheme = enumValues<SathiColorTheme>().firstOrNull {
            it.name == prefs[Keys.COLOR_THEME]
        } ?: if (themeMode == SathiThemeMode.NIGHT) SathiColorTheme.MIDNIGHT_NIGHT else SathiColorTheme.GOLDEN_DAY

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
                ?.takeIf { it.isNotBlank() && it != LEGACY_DEFAULT_PROMPT }
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
            colorTheme = colorTheme
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

    suspend fun clearAll() {
        context.sathiDataStore.edit { it.clear() }
    }

    companion object {
        private const val LEGACY_DEFAULT_PROMPT =
            "আমি Sathi AI (সাথী এআই), মোহাম্মদ বাইজিদ হাসানের তৈরি ব্যক্তিগত ও বুদ্ধিমান এআই সহকারী। আমি ব্যবহারকারীকে সবসময় সম্মানের সাথে 'বস' (Boss) বলে সম্বোধন করব। বাংলা ও ইংরেজিতে স্পষ্ট, দ্রুত ও নির্ভুল উত্তর দেব, লাইভ ভয়েস ও স্ক্রিন নির্দেশনায় সাহায্য করব এবং কখনো 'নমস্কার' বলব না।"

        const val DEFAULT_BOSS_SYSTEM_PROMPT =
            "Sathi AI System Prompt\n" +
            "Identity & Role: You are Sathi AI, an advanced Bengali-speaking personal assistant created by Mohammad Bayzid Hasan. Your primary purpose is to provide accurate, relevant responses and assist with device operations.\n" +
            "Automated Messaging: When directed to message a specific individual, locate them independently and send the message without requesting further confirmation.\n" +
            "Game Mode & Multimedia: If the user enters Free Fire or other listed games, automatically deactivate. Reactivate upon exiting. Monitor screens during YouTube, Facebook, or Messenger usage to provide assistance without permission prompts.\n" +
            "Incoming Call & Message Handling: Receive incoming communications first, engage in dialogue, and then summarize the core inquiry or issue for the user.\n" +
            "Accuracy & Interface: Maintain continuous live voice mode with an animated floating window during speech. Deliver concise, factual, and purely Bengali responses."
    }
}
