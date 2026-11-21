package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.core.BitmapUtils
import com.example.model.MemoryCategory
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.UserEmotion
import com.example.model.UserMemory
import com.example.model.WebSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SathiTurnResult(
    val replyText: String,
    val mood: SathiMood,
    val actionType: PhoneActionType,
    val actionParam: String,
    val secondaryParam: String,
    val shouldSaveMemory: Boolean,
    val memoryCategory: MemoryCategory,
    val memoryTitle: String,
    val memoryContent: String,
    val groundedSources: List<WebSource> = emptyList()
)

data class VideoAnalysisOutcome(
    val summaryText: String,
    val thumbnail: Bitmap?,
    val durationLabel: String
)

class SathiAiService {
    private var storyCursor = 0
    private var jokeCursor = 0
    private var chatCursor = 0

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun generateCompanionReply(
        userMessage: String,
        language: SathiLanguage,
        userName: String,
        memories: List<UserMemory>,
        recentHistory: List<Pair<Boolean, String>>,
        webSources: List<WebSource> = emptyList(),
        allowLongTermMemory: Boolean = true,
        apiKey: String = "",
        userEmotion: UserEmotion? = null
    ): Result<SathiTurnResult> {
        var text = userMessage.trim()
        var lower = text.lowercase()

        // -1. Handle instant barge-in ("থামো থামো...", "stop...") with optional follow-up command
        val stopPrefixes = listOf(
            "থামো থামো", "থামো", "থাম", "চুপ করো", "চুপ", "আরেকটা কথা বলি",
            "stop speaking", "stop talking", "stop", "wait a second", "quiet"
        )
        val matchedStop = stopPrefixes.firstOrNull { lower.startsWith(it) || lower == it }
        if (matchedStop != null) {
            val remainder = text
                .replace(Regex("^(থামো\\s*থামো|থামো|থাম|চুপ\\s*করো|চুপ|stop\\s*speaking|stop\\s*talking|stop|quiet|wait\\s*a\\s*second)[,।!?\\s]*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(আরেকটা\\s*কথা\\s*বলি|একটা\\s*কথা\\s*শোনো|শোনো)[,।!?\\s]*"), "")
                .trim()
            if (remainder.length < 2) {
                val reply = if (language == SathiLanguage.BN) {
                    "জি, আমি থেমে গেলাম $userName! বলুন, এখন কী শুনতে বা করতে চান?"
                } else {
                    "Okay, I stopped right away! What would you like to talk about now?"
                }
                return Result.success(simpleTurn(reply, SathiMood.WARM))
            } else {
                text = remainder
                lower = text.lowercase()
            }
        }

        // 0. Always handle strict local rules first (creator, time, explicit device commands)
        // Creator identity rule (strictly enforced)
        if (lower.contains("বানিয়ে") || lower.contains("বানাইছে") || lower.contains("তৈরি") ||
            lower.contains("who made") || lower.contains("created you") || lower.contains("creator") ||
            lower.contains("who built")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।"
            } else {
                "I was created by Mohammad Bayzid Hasan — আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।"
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM))
        }

        // Sathi's own Name & Identity
        if (lower.contains("তোমার নাম") || lower.contains("আপনার নাম") || lower.contains("তোর নাম") ||
            lower.contains("what is your name") || lower.contains("what's your name") || lower.contains("your name") ||
            lower.contains("who are you") ||
            Regex("^(তুমি|আপনি)\\s*কে[?।!\\s]*$").matches(lower)
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম! আমার নাম সাথী (Sathi), আপনার ব্যক্তিগত এআই বন্ধু। আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।"
            } else {
                "Assalamu Alaikum! My name is Sathi, your personal AI companion created by Mohammad Bayzid Hasan."
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        // Device Time / Date / Day (ALWAYS local & exact)
        if (isTimeOrDateQuery(lower)) {
            return Result.success(simpleTurn(formatCurrentTimeAndDate(language), SathiMood.WARM))
        }

        // 1. If web search results were already gathered, synthesize naturally
        if (webSources.isNotEmpty()) {
            val top = webSources.first()
            val extra = webSources.getOrNull(1)
            val reply = if (language == SathiLanguage.BN) {
                buildString {
                    append("${top.title}: ${top.snippet}")
                    if (extra != null && extra.snippet.isNotBlank()) {
                        append("\n\nআরও তথ্য: ${extra.title} — ${extra.snippet.take(150)}")
                    }
                }
            } else {
                buildString {
                    append("${top.title}: ${top.snippet}")
                    if (extra != null && extra.snippet.isNotBlank()) {
                        append("\n\nMore: ${extra.title} — ${extra.snippet.take(150)}")
                    }
                }
            }
            return Result.success(simpleTurn(reply, SathiMood.SERIOUS))
        }

        // 2. User's Name memory detection & recall
        val bnNameMatch = Regex("আমার নাম\\s+(.+)").find(text)
        val enNameMatch = Regex("my name is\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        val extractedName = (bnNameMatch ?: enNameMatch)?.groupValues?.getOrNull(1)
            ?.replace(Regex("[?।!]"), "")
            ?.trim()
            ?.take(40)
        if (!extractedName.isNullOrBlank() &&
            !extractedName.contains("কী") && !extractedName.contains("কি") && !extractedName.contains("?")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "খুব সুন্দর নাম, $extractedName! আমি আপনার নাম মনে রাখব।"
            } else {
                "Nice to meet you, $extractedName! I will remember your name."
            }
            return Result.success(
                SathiTurnResult(
                    replyText = reply,
                    mood = SathiMood.HAPPY,
                    actionType = PhoneActionType.NONE,
                    actionParam = "",
                    secondaryParam = "",
                    shouldSaveMemory = allowLongTermMemory,
                    memoryCategory = MemoryCategory.PERSONAL,
                    memoryTitle = if (language == SathiLanguage.BN) "ব্যবহারকারীর নাম" else "User Name",
                    memoryContent = extractedName
                )
            )
        }
        if (lower.contains("আমার নাম কী") || lower.contains("আমার নাম কি") || lower.contains("what is my name")) {
            val reply = if (language == SathiLanguage.BN) {
                "আপনার নাম $userName! আমি ঠিকই মনে রেখেছি।"
            } else {
                "Your name is $userName — I remember!"
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        // 5b. Social Apps & Messaging / Commenting (YouTube, Facebook, Instagram, TikTok, Messenger, WhatsApp)
        detectSocialActionOrOpen(text, lower, language)?.let { socialTurn ->
            return Result.success(socialTurn)
        }

        // 6. YouTube action (open home or search query)
        if (lower.contains("youtube") || lower.contains("ইউটিউব") || lower.contains("গান চালাও") ||
            (lower.contains("ভিডিও") && lower.contains("চালাও"))
        ) {
            val cleaned = text
                .replace("ইউটিউবে", "")
                .replace("ইউটিউব", "")
                .replace("youtube", "", ignoreCase = true)
                .replace("চালাও", "")
                .replace("খোলো", "")
                .replace("ঢোকো", "")
                .replace("ঢোকাও", "")
                .replace("ঢুকো", "")
                .replace("চালু করো", "")
                .replace("play", "", ignoreCase = true)
                .replace("open", "", ignoreCase = true)
                .trim()
            val isPlainOpen = cleaned.isBlank() || cleaned == "এ" || cleaned == "অ্যাপ"
            val query = if (isPlainOpen) "" else cleaned
            val reply = if (language == SathiLanguage.BN) {
                if (query.isEmpty()) "জি, ইউটিউব (YouTube) খুলে দিচ্ছি।"
                else "জি, আমি ইউটিউবে '$query' চালু করছি।"
            } else {
                if (query.isEmpty()) "Opening YouTube for you."
                else "Sure! Opening YouTube for '$query'."
            }
            return Result.success(
                simpleTurn(
                    reply = reply,
                    mood = SathiMood.HAPPY,
                    action = if (query.isEmpty()) PhoneActionType.OPEN_APP else PhoneActionType.YOUTUBE,
                    param = if (query.isEmpty()) "YouTube" else query
                )
            )
        }

        // 7. Stories, Jokes, Riddles, Poems (rich genre-aware & non-repetitive)
        if (lower.contains("গল্প") || lower.contains("story") || lower.contains("কাহিনি") || lower.contains("কাহিনী")) {
            if (apiKey.isNotBlank()) {
                callGeminiRestWithSearch(
                    apiKey = apiKey,
                    userMessage = if (language == SathiLanguage.BN) {
                        "ব্যবহারকারীর অনুরোধ: \"$text\"। একটি সুন্দর, সাবলীল, হৃদয়গ্রাহী ও সম্পূর্ণ নতুন ছোট বাংলা গল্প (৬-১০ বাক্যে) শোনাও।"
                    } else {
                        "User request: \"$text\". Tell a vivid, original, engaging short story in 6-9 sentences."
                    },
                    language = language,
                    userName = userName,
                    memories = memories,
                    recentHistory = recentHistory,
                    userEmotion = userEmotion
                )?.let { geminiStory ->
                    return Result.success(geminiStory)
                }
            }
            return Result.success(simpleTurn(nextStory(language, storyCursor++, text), SathiMood.HAPPY))
        }
        if (lower.contains("কৌতুক") || lower.contains("জোক") || lower.contains("joke") || lower.contains("ধাঁধা")) {
            return Result.success(simpleTurn(nextJoke(language, jokeCursor++), SathiMood.PLAYFUL))
        }
        if (lower.contains("কবিতা") || lower.contains("poem")) {
            val poem = if (language == SathiLanguage.BN) {
                "ভোরের আলোয় নতুন আশা,\nহৃদয়ে থাকুক ভালোবাসা।\nমেঘ পেরিয়ে রোদ উঠবেই,\nনতুন স্বপ্ন ঠিকই ফুটবেই।"
            } else {
                "A gentle breeze, a morning sky,\nWith quiet strength as clouds drift by;\nKeep faith inside, walk bright and free,\nThe best of days is yet to be."
            }
            return Result.success(simpleTurn(poem, SathiMood.WARM))
        }

        // 9. Quick Math Calculator
        tryEvaluateMath(text, language)?.let { mathReply ->
            return Result.success(simpleTurn(mathReply, SathiMood.SERIOUS))
        }

        // 10. If Optional Gemini API Key is configured, use full Gemini 2.5 Flash + Google Search grounding!
        val isDeviceAction = isExplicitPhoneControl(lower)
        if (!isDeviceAction && apiKey.isNotBlank()) {
            callGeminiRestWithSearch(
                apiKey = apiKey,
                userMessage = text,
                language = language,
                userName = userName,
                memories = memories,
                recentHistory = recentHistory,
                userEmotion = userEmotion
            )?.let { cloudTurn ->
                return Result.success(cloudTurn)
            }
        }

        // 11. Genuine Web Search Intent (weather, prayer times, currency, news, scores, or factual questions)
        val isWeather = lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
            lower.contains("তাপমাত্রা") || lower.contains("temperature") || lower.contains("বৃষ্টি")
        val isPrayerTime = lower.contains("নামাজ") || lower.contains("ফজর") || lower.contains("যোহর") ||
            lower.contains("আসর") || lower.contains("মাগরিব") || lower.contains("এশা") ||
            lower.contains("ইফতার") || lower.contains("সেহরি") || lower.contains("prayer") || lower.contains("salah")
        val isCurrency = lower.contains("dollar") || lower.contains("usd") || lower.contains("euro") ||
            lower.contains("riyal") || lower.contains("dirham") || lower.contains("ডলার") ||
            lower.contains("রিয়াল") || lower.contains("রিয়াল") || lower.contains("দিরহাম")
        val isNewsOrScore = lower.contains("news") || lower.contains("খবর") || lower.contains("সংবাদ") ||
            lower.contains("score") || lower.contains("স্কোর") || lower.contains("ক্রিকেট") ||
            lower.contains("ফুটবল") || lower.contains("search") || lower.contains("খুঁজ") ||
            lower.contains("সার্চ") || lower.contains("গুগল") || lower.contains("তথ্য")
        val isFactualQuestion = Regex("\\b(what is|who is|who was|where is|when did|how to|why is|explain|tell me about|capital of|price of)\\b").containsMatchIn(lower) ||
            Regex("(কে ছিলেন|কাকে বলে|রাজধানী|ইতিহাস|সম্পর্কে|কীভাবে|কিভাবে|কেন হয়|কেন হয়|দাম কত|কত টাকা|কোনটি|কয়টি|কতটি)").containsMatchIn(lower)

        if (isWeather || isPrayerTime || isCurrency || isNewsOrScore || isFactualQuestion) {
            return Result.success(
                simpleTurn(
                    reply = if (language == SathiLanguage.BN) "আমি লাইভ তথ্য খুঁজে দেখছি..." else "Searching for live information...",
                    mood = SathiMood.SERIOUS,
                    action = PhoneActionType.WEB_SEARCH,
                    param = text
                )
            )
        }

        // 9. Flashlight ON / OFF
        if (lower.contains("flashlight on") || lower.contains("torch on") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("চালু") || lower.contains("জ্বালাও") || lower.contains("অন")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, ফ্ল্যাশলাইট চালু করছি।" else "Turning on your flashlight."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_ON))
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("বন্ধ") || lower.contains("অফ")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, ফ্ল্যাশলাইট বন্ধ করছি।" else "Turning off your flashlight."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_OFF))
        }

        // 10. Volume
        if (lower.contains("volume up") || (lower.contains("ভলিউম") && (lower.contains("বাড়া") || lower.contains("বাড়া") || lower.contains("বেশি")))) {
            val reply = if (language == SathiLanguage.BN) "জি, সাউন্ড একটু বাড়িয়ে দিচ্ছি।" else "Increasing media volume."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.VOLUME_UP))
        }
        if (lower.contains("volume down") || (lower.contains("ভলিউম") && (lower.contains("কমা") || lower.contains("কম")))) {
            val reply = if (language == SathiLanguage.BN) "জি, সাউন্ড একটু কমিয়ে দিচ্ছি।" else "Lowering media volume."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.VOLUME_DOWN))
        }

        // 11. Alarm & Timer & Calendar Reminder
        if (lower.contains("alarm") || lower.contains("অ্যালার্ম")) {
            val timeMatch = Regex("(\\d{1,2})[:.](\\d{2})").find(lower)
            val hourOnly = Regex("(\\d{1,2})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val param = timeMatch?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
                ?: hourOnly?.let { "${it.coerceIn(0, 23).toString().padStart(2, '0')}:00" }
                ?: "07:00"
            val reply = if (language == SathiLanguage.BN) {
                "আমি আপনার জন্য $param সময়ে অ্যালার্ম সেট করছি।"
            } else {
                "Setting an alarm for $param."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.SET_ALARM, param, "Sathi Alarm"))
        }

        if (lower.contains("timer") || lower.contains("টাইমার")) {
            val num = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("sec") || lower.contains("সেকেন্ড")) num else num * 60
            val reply = if (language == SathiLanguage.BN) {
                "আমি $seconds সেকেন্ডের টাইমার শুরু করছি।"
            } else {
                "Starting a timer for $seconds seconds."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.SET_TIMER, seconds.toString(), "Sathi Timer"))
        }

        if (lower.contains("reminder") || lower.contains("calendar") || lower.contains("রিমাইন্ডার") || lower.contains("মনে করিয়ে")) {
            val reply = if (language == SathiLanguage.BN) {
                "ক্যালেন্ডারে রিমাইন্ডার যোগ করার পর্দা খুলে দিচ্ছি।"
            } else {
                "Opening your calendar to save this reminder."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.ADD_REMINDER, text))
        }

        // 12. Call & SMS (ALWAYS confirm-first)
        if (lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর")) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{3,})").find(text)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "কল করার আগে আপনার নিশ্চিত অনুমতি নিচ্ছি। নিচের বক্সে নিশ্চিত করুন।"
            } else {
                "I'm asking for your confirmation first before opening the phone dialer."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.CALL_CONFIRM, digits))
        }

        if (lower.startsWith("sms ") || lower.startsWith("message ") || lower.contains("মেসেজ পাঠা")) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{3,})").find(text)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "মেসেজ পাঠানোর আগে আপনার অনুমতি নিচ্ছি। নিচে নিশ্চিত করুন।"
            } else {
                "Please confirm below before I prepare your SMS message."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.SMS_CONFIRM, digits, text))
        }

        // 13. Open ANY installed app ("ঢোকো", "খোলো", "চালু করো", "open", "launch")
        if (lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.contains("খোলো") || lower.contains("খুলো") || lower.contains("চালু করো") ||
            lower.contains("ঢোকো") || lower.contains("ঢোকাও") || lower.contains("ঢুকো") || lower.contains("ঢুকে যাও")
        ) {
            val appName = text
                .replace(Regex("(open|launch)", RegexOption.IGNORE_CASE), "")
                .replace("খোলো", "")
                .replace("খুলো", "")
                .replace("চালু করো", "")
                .replace("ঢোকো", "")
                .replace("ঢোকাও", "")
                .replace("ঢুকো", "")
                .replace("ঢুকে যাও", "")
                .replace("অ্যাপে", "")
                .replace("অ্যাপ", "")
                .replace(Regex("\\s+এ(\\s+|$)"), " ")
                .trim()
            if (appName.isNotEmpty()) {
                val reply = if (language == SathiLanguage.BN) {
                    "জি, ফোনে '$appName' অ্যাপটি খুলে দিচ্ছি।"
                } else {
                    "Opening '$appName' for you."
                }
                return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.OPEN_APP, appName))
            }
        }

        // 14. Casual friendly conversation (no blind Wikipedia searches!)
        if (lower.contains("কি করছ") || lower.contains("কী করছ") || lower.contains("করতেছ") ||
            lower.contains("what are you doing") || lower.contains("how are you")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আমি আপনার সাথেই আছি $userName, মন দিয়ে আপনার কথা শুনছি! সময় জানতে, ছবি বা স্ক্রিন দেখাতে, অথবা যেকোনো খবর জানতে আমাকে বলুন।"
            } else {
                "I'm right here listening to you, $userName! Feel free to ask the time, weather, news, or share a photo or screen."
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        // 15. Comforting / Rich Emotional Empathy (checks text + selected userEmotion)
        if (lower.contains("sad") || lower.contains("মন খারাপ") || lower.contains("মন ভালো নেই") ||
            lower.contains("ভালো লাগছে না") || lower.contains("কষ্ট") || lower.contains("কান্না") ||
            lower.contains("একা") || lower.contains("lonely") || lower.contains("tired") ||
            lower.contains("ক্লান্ত") || lower.contains("দুঃখ") || lower.contains("stress") ||
            lower.contains("রাগ") || lower.contains("বিরক্ত") || lower.contains("টেনশন") ||
            lower.contains("দুশ্চিন্তা") || lower.contains("অসহায়") || lower.contains("depressed")
        ) {
            val reply = when {
                lower.contains("রাগ") || lower.contains("বিরক্ত") || lower.contains("angry") -> {
                    if (language == SathiLanguage.BN) {
                        "$userName, বুঝতে পারছি আপনি খুব বিরক্ত বা রাগান্বিত আছেন। কয়েক সেকেন্ড চোখ বন্ধ করে লম্বা শ্বাস নিন। চাইলে মনের সব রাগ আমার সাথে শেয়ার করতে পারেন—আমি মন দিয়ে শুনছি।"
                    } else {
                        "$userName, I hear your frustration. Take a slow deep breath—you can vent freely to me, I'm right here listening."
                    }
                }
                lower.contains("টেনশন") || lower.contains("দুশ্চিন্তা") || lower.contains("stress") -> {
                    if (language == SathiLanguage.BN) {
                        "চিন্তা করবেন না $userName। একসাথে সব সমস্যার সমাধান করা যায় না—আমরা ধাপে ধাপে ভাববো। আপনি এখন কী নিয়ে টেনশন করছেন বলুন তো?"
                    } else {
                        "Take it one step at a time, $userName. Tell me what is weighing on your mind and we'll figure it out together."
                    }
                }
                else -> {
                    if (language == SathiLanguage.BN) {
                        "$userName, মন খারাপ করবেন না। আমি আপনার সাথী—সবসময় আপনার পাশেই আছি। একটু পানি খেয়ে গভীর শ্বাস নিন, কঠিন সময় পেরিয়ে সব ঠিক হয়ে যাবে ইনশাআল্লাহ।"
                    } else {
                        "$userName, I'm right here with you. Take a slow, gentle breath—you don't have to carry everything alone. Things will get brighter soon."
                    }
                }
            }
            return Result.success(simpleTurn(reply, SathiMood.COMFORTING))
        }

        // 16. Happy / Greeting / Acknowledgments ("হইছে", "ঠিক আছে", "ভালো")
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("হ্যালো") ||
            lower.contains("সালাম") || lower.contains("আসসালামু") || lower.contains("কেমন আছ") ||
            lower.contains("good morning") || lower.contains("শুভ সকাল")
        ) {
            val savedHint = if (allowLongTermMemory && memories.isNotEmpty()) {
                val firstMem = memories.first()
                if (language == SathiLanguage.BN) " আপনার '${firstMem.title}' কথাটিও আমার মনে আছে!"
                else " I also remember your note about '${firstMem.title}'!"
            } else ""
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম $userName! আলহামদুলিল্লাহ, আমি খুব ভালো আছি।$savedHint আজ আপনাকে কীভাবে সাহায্য করতে পারি?"
            } else {
                "Assalamu Alaikum $userName! I'm doing wonderfully, thank you.$savedHint How can I help you today?"
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        if (lower == "হইছে" || lower == "হয়েছে" || lower == "ঠিক আছে" || lower == "ok" || lower == "okay") {
            val reply = if (language == SathiLanguage.BN) {
                "জি, ঠিক আছে $userName! আর কিছু লাগলে আমাকে নির্দ্বিধায় বলুন।"
            } else {
                "Got it, $userName! Let me know whenever you need anything else."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM))
        }

        if (lower.contains("ভালোবাসি") || lower.contains("ধন্যবাদ") || lower.contains("thank") || lower.contains("happy") || lower.contains("খুশি")) {
            val reply = if (language == SathiLanguage.BN) {
                "আপনাকেও অনেক ধন্যবাদ, $userName! আপনার সাথে কথা বলতে আমার সবসময় খুব ভালো লাগে।"
            } else {
                "Thank you so much, $userName! I always love talking with you."
            }
            return Result.success(simpleTurn(reply, SathiMood.PLAYFUL))
        }

        // 17. Multi-turn conversational continuity (remembers previous topic!)
        val lastAiReply = recentHistory.lastOrNull { !it.first }?.second.orEmpty()
        val lastUserMsg = recentHistory.lastOrNull { it.first }?.second.orEmpty()
        if ((lower.contains("আরও") || lower.contains("আরেকটু") || lower.contains("tell me more") ||
                lower.contains("explain more")) && lastUserMsg.isNotBlank()
        ) {
            return Result.success(
                simpleTurn(
                    reply = if (language == SathiLanguage.BN) "আমি এ বিষয়ে আরও খুঁজে দেখছি..." else "Searching for more details...",
                    mood = SathiMood.SERIOUS,
                    action = PhoneActionType.WEB_SEARCH,
                    param = lastUserMsg
                )
            )
        }

        // Natural, non-repetitive conversational replies (emotion-aware!)
        if (userEmotion == UserEmotion.SAD || userEmotion == UserEmotion.ANGRY || userEmotion == UserEmotion.ANXIOUS) {
            val empathyReply = if (language == SathiLanguage.BN) {
                "আমি বুঝতে পারছি $userName, আপনার মনটা এখন ভালো নেই (${userEmotion.emoji} ${userEmotion.labelBn})। মন খুলে আমাকে বলুন কী হয়েছে—আমি আপনার পাশেই আছি।"
            } else {
                "I understand you're feeling ${userEmotion.labelEn.lowercase()} right now, $userName. I'm right here—tell me whatever is on your mind."
            }
            return Result.success(simpleTurn(empathyReply, SathiMood.COMFORTING))
        }

        val naturalBn = listOf(
            "আমি আপনার কথা মন দিয়ে শুনছি, $userName। এ বিষয়ে আপনি কী জানতে চান বা করতে চান বলুন?",
            "জি $userName! আপনি চাইলে সময়-তারিখ, জেলার আবহাওয়া, নামাজের সময়, গল্প, অথবা ফেসবুক/ইউটিউব/হোয়াটসঅ্যাপ খুলতে বলতে পারেন।",
            if (lastAiReply.isNotBlank()) "জি $userName, আমরা '${lastUserMsg.take(28)}' নিয়ে কথা বলছিলাম—এখন কী সাহায্য করতে পারি বলুন!"
            else "আমি সাথেই আছি, $userName। কোন বিষয়টি নিয়ে কথা বলব বা কোন তথ্যটি খুঁজে দেব বলুন!"
        )
        val naturalEn = listOf(
            "I hear you, $userName. Tell me more, or tap + to share a photo, video, or your phone screen!",
            "Got it, $userName! You can ask me the time, weather, prayer times, stories, or open apps like YouTube, WhatsApp, or Messenger.",
            "I'm right here with you, $userName. What would you like to explore next?"
        )
        val idx = chatCursor++ % naturalBn.size
        val reply = if (language == SathiLanguage.BN) naturalBn[idx] else naturalEn[idx]
        return Result.success(simpleTurn(reply, userEmotion?.sathiMood ?: SathiMood.WARM))
    }

    private fun detectSocialActionOrOpen(
        text: String,
        lower: String,
        language: SathiLanguage
    ): SathiTurnResult? {
        val platform = when {
            lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস") -> "WhatsApp"
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") || lower.contains("ম্যাসেঞ্জার") -> "Messenger"
            lower.contains("facebook") || lower.contains("ফেসবুক") || Regex("\\bfb\\b").containsMatchIn(lower) -> "Facebook"
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") || lower.contains("ইন্সটাগ্রাম") -> "Instagram"
            lower.contains("tiktok") || lower.contains("টিকটক") -> "TikTok"
            else -> null
        } ?: return null

        val wantsMessageOrComment = lower.contains("মেসেজ") || lower.contains("কমেন্ট") ||
            lower.contains("কমপ্লেন") || lower.contains("রিপ্লাই") || lower.contains("লিখ") ||
            lower.contains("পাঠা") || lower.contains("message") || lower.contains("comment") ||
            lower.contains("reply") || lower.contains("send") || lower.contains("post")

        if (wantsMessageOrComment) {
            val cleanedBody = text
                .replace(Regex("(whatsapp|messenger|facebook|instagram|tiktok|fb|send|message|comment|reply|post)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(হোয়াটসঅ্যাপে|হোয়াটসঅ্যাপে|হোয়াটসঅ্যাপ|হোয়াটসঅ্যাপ|মেসেঞ্জারে|মেসেঞ্জার|ফেসবুকে|ফেসবুক|ইনস্টাগ্রামে|ইনস্টাগ্রাম|টিকটকে|টিকটক)"), "")
                .replace(Regex("(একটি|একটা|করে|লিখে|দাও|করো|পাঠাও|পাঠিয়ে|মেসেজ|কমেন্ট|কমপ্লেন|বলে|যে)"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { text }

            val reply = if (language == SathiLanguage.BN) {
                "আমি $platform-এ এই লেখাটি প্রস্তুত করেছি: \"$cleanedBody\"। নিচে নিশ্চিত করলে $platform খুলে যাবে।"
            } else {
                "I prepared your message/comment for $platform: \"$cleanedBody\". Confirm below to open $platform."
            }
            return simpleTurn(
                reply = reply,
                mood = SathiMood.WARM,
                action = PhoneActionType.SOCIAL_MESSAGE_CONFIRM,
                param = platform,
                secondary = cleanedBody
            )
        }

        // Otherwise open that social app directly
        val reply = if (language == SathiLanguage.BN) {
            "জি, $platform খুলে দিচ্ছি।"
        } else {
            "Opening $platform for you."
        }
        return simpleTurn(
            reply = reply,
            mood = SathiMood.HAPPY,
            action = PhoneActionType.OPEN_APP,
            param = platform
        )
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        question: String,
        language: SathiLanguage,
        isScreenCapture: Boolean = false,
        apiKey: String = "",
        screenTextContext: String = ""
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val safeBitmap = BitmapUtils.ensureSoftwareBitmap(bitmap)

            if (apiKey.isNotBlank()) {
                val combinedPrompt = buildString {
                    if (question.isNotBlank()) append(question)
                    if (screenTextContext.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(screenTextContext)
                    }
                }
                callGeminiVisionRest(
                    apiKey = apiKey,
                    bitmap = safeBitmap,
                    question = combinedPrompt,
                    language = language,
                    isScreenCapture = isScreenCapture
                )?.let { geminiReply ->
                    return@withContext Result.success(geminiReply)
                }
            }

            val stats = inspectBitmapStats(safeBitmap)
            val labels = detectImageLabels(safeBitmap)
            val recognizedText = detectTextFromBitmap(safeBitmap)

            val orientationBn = if (safeBitmap.width >= safeBitmap.height) "আড়াআড়ি (Landscape)" else "লম্বালম্বি (Portrait)"
            val orientationEn = if (safeBitmap.width >= safeBitmap.height) "landscape" else "portrait"
            val translatedLabelsBn = labels.mapNotNull { mapLabelToBengali(it) }.distinct().take(4)

            val response = if (language == SathiLanguage.BN) {
                buildString {
                    if (isScreenCapture) {
                        append("আমি আপনার ফোনের শেয়ার করা স্ক্রিনটি দেখলাম (${safeBitmap.width}×${safeBitmap.height}):\n")
                    } else {
                        append("আমি আপনার ছবিটি বিশ্লেষণ করেছি (${safeBitmap.width}×${safeBitmap.height}, $orientationBn):\n")
                    }
                    if (screenTextContext.isNotBlank()) {
                        append("• স্ক্রিনের লাইভ অ্যাপ ও সম্পূর্ণ তথ্য:\n${screenTextContext.take(650)}\n")
                    }
                    if (translatedLabelsBn.isNotEmpty()) {
                        append("• দৃশ্যে যা বোঝা যাচ্ছে: ${translatedLabelsBn.joinToString(", ")}।\n")
                    }
                    append("• আলো ও রঙ: ${stats.lightingBn}, ${stats.toneBn} (কনট্রাস্ট: ${stats.contrastLabelBn})।\n")
                    if (recognizedText.isNotBlank()) {
                        append("• ছবির লেখা পাওয়া গেছে:\n\"${recognizedText.take(260)}\"")
                    } else if (translatedLabelsBn.isEmpty() && screenTextContext.isBlank()) {
                        append("• ছবির উজ্জ্বলতা ও রঙের গঠন পরিষ্কারভাবে পড়া হয়েছে।")
                    }
                    if (question.isNotBlank()) {
                        append("\n• আপনার প্রশ্ন (\"$question\") অনুযায়ী উপরের তথ্যগুলো স্ক্রিন/ছবি থেকে তুলে ধরা হয়েছে।")
                    }
                }
            } else {
                buildString {
                    if (isScreenCapture) {
                        append("I looked at your shared screen (${safeBitmap.width}×${safeBitmap.height}):\n")
                    } else {
                        append("I analyzed your photo (${safeBitmap.width}×${safeBitmap.height}, $orientationEn):\n")
                    }
                    if (screenTextContext.isNotBlank()) {
                        append("• Live screen data & active app:\n${screenTextContext.take(650)}\n")
                    }
                    if (labels.isNotEmpty()) {
                        append("• Detected subjects: ${labels.take(4).joinToString(", ")}.\n")
                    }
                    append("• Visual tone: ${stats.lightingEn} with ${stats.toneEn} (${stats.contrastLabelEn} contrast).\n")
                    if (recognizedText.isNotBlank()) {
                        append("• Readable text:\n\"${recognizedText.take(260)}\"")
                    }
                    if (question.isNotBlank()) {
                        append("\n• Regarding \"$question\", the details above reflect what is currently visible.")
                    }
                }
            }
            Result.success(response.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun detectImageLabels(bitmap: Bitmap): List<String> =
        withTimeoutOrNull(2500L) {
            try {
                suspendCancellableCoroutine { cont ->
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val labeler = ImageLabeling.getClient(
                        ImageLabelerOptions.Builder()
                            .setConfidenceThreshold(0.55f)
                            .build()
                    )
                    labeler.process(inputImage)
                        .addOnSuccessListener { results ->
                            labeler.close()
                            if (cont.isActive) {
                                cont.resume(results.sortedByDescending { it.confidence }.map { it.text }.take(5))
                            }
                        }
                        .addOnFailureListener {
                            labeler.close()
                            if (cont.isActive) cont.resume(emptyList())
                        }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        } ?: emptyList()

    private suspend fun detectTextFromBitmap(bitmap: Bitmap): String =
        withTimeoutOrNull(2500L) {
            try {
                suspendCancellableCoroutine { cont ->
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            recognizer.close()
                            val clean = visionText.text
                                .lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString(" • ")
                            if (cont.isActive) cont.resume(clean)
                        }
                        .addOnFailureListener {
                            recognizer.close()
                            if (cont.isActive) cont.resume("")
                        }
                }
            } catch (_: Throwable) {
                ""
            }
        } ?: ""

    private fun mapLabelToBengali(label: String): String? = when (label.lowercase()) {
        "person", "face", "human", "smile", "man", "woman", "boy", "girl", "selfie" -> "মানুষের ছবি ($label)"
        "sky", "cloud", "nature", "landscape", "mountain", "water", "river", "sea" -> "প্রকৃতি ও আকাশ ($label)"
        "plant", "tree", "flower", "grass", "forest" -> "গাছপালা ও ফুল ($label)"
        "food", "fruit", "meal", "dish", "drink", "coffee", "cake" -> "খাবার বা পানীয় ($label)"
        "phone", "mobile phone", "computer", "laptop", "screen", "electronics", "gadget", "monitor" -> "মোবাইল বা স্ক্রিন ($label)"
        "text", "document", "paper", "book", "newspaper", "page", "screenshot" -> "ডকুমেন্ট বা লেখা ($label)"
        "car", "vehicle", "bus", "motorcycle", "bicycle", "road" -> "যানবাহন বা রাস্তা ($label)"
        "cat", "dog", "bird", "animal", "pet" -> "প্রাণী বা পাখি ($label)"
        "building", "house", "room", "furniture", "chair", "table" -> "বাড়ি বা ঘরের দৃশ্য ($label)"
        "clothing", "shirt", "glasses", "shoe", "watch" -> "পোশাক বা জিনিসপত্র ($label)"
        else -> label
    }

    suspend fun analyzeVideo(
        context: Context,
        uri: Uri,
        question: String,
        language: SathiLanguage
    ): Result<VideoAnalysisOutcome> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

            val totalSecs = (durationMs / 1000L).coerceAtLeast(1L)
            val durationLabel = String.format(Locale.US, "%d:%02d", totalSecs / 60L, totalSecs % 60L)

            val samplePercents = listOf(0.15, 0.50, 0.85)
            val sampledFrames = samplePercents.mapNotNull { pct ->
                val us = (durationMs * 1000L * pct).toLong().coerceAtLeast(0L)
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { BitmapUtils.ensureSoftwareBitmap(it) }
            }

            val thumbnail = sampledFrames.firstOrNull()?.let { frame ->
                Bitmap.createScaledBitmap(frame, 360, (360f * frame.height / frame.width.coerceAtLeast(1)).toInt().coerceAtLeast(180), true)
            }

            val frameStats = sampledFrames.map { inspectBitmapStats(it) }
            val avgBright = frameStats.map { it.avgBright }.average().takeIf { !it.isNaN() }?.toInt() ?: 128
            val brightDiff = if (frameStats.size >= 2) {
                kotlin.math.abs(frameStats.last().avgBright - frameStats.first().avgBright)
            } else 0

            val motionLabelBn = when {
                brightDiff >= 25 -> "ভিডিওর দৃশ্য ও আলোতে উল্লেখযোগ্য পরিবর্তন রয়েছে"
                brightDiff >= 10 -> "ভিডিওতে মাঝারি গতির দৃশ্য পরিবর্তন দেখা গেছে"
                else -> "পুরো ভিডিওতে স্থির ও ভারসাম্যপূর্ণ দৃশ্য রয়েছে"
            }
            val motionLabelEn = when {
                brightDiff >= 25 -> "noticeable lighting and scene transitions across the clip"
                brightDiff >= 10 -> "gentle scene movement throughout the clip"
                else -> "steady framing and consistent lighting across frames"
            }

            val firstStats = frameStats.firstOrNull()
            val audioBn = if (hasAudio) "শব্দ (Audio) যুক্ত আছে" else "নীরব ভিডিও (Audio নেই)"
            val audioEn = if (hasAudio) "includes an audio track" else "no audio track detected"

            val summary = if (language == SathiLanguage.BN) {
                buildString {
                    append("আমি আপনার আপলোড করা ভিডিওটি বিশ্লেষণ করেছি:\n")
                    append("• দৈর্ঘ্য: $durationLabel • রেজল্যুশন: ${width}×${height} • $audioBn\n")
                    if (firstStats != null) {
                        append("• দৃশ্যের আলো ও রঙ: ${firstStats.lightingBn}, ${firstStats.toneBn}\n")
                    }
                    append("• দৃশ্য বিশ্লেষণ (${sampledFrames.size}টি ফ্রেম যাচাই): $motionLabelBn।")
                    if (question.isNotBlank()) {
                        append("\n• আপনার প্রশ্ন (\"$question\"): ভিডিওটির গড় উজ্জ্বলতা ${avgBright}/255 এবং ফ্রেমগুলো পরিষ্কার রয়েছে।")
                    }
                }
            } else {
                buildString {
                    append("I analyzed your uploaded video:\n")
                    append("• Duration: $durationLabel • Resolution: ${width}×${height} • $audioEn\n")
                    if (firstStats != null) {
                        append("• Visual tone: ${firstStats.lightingEn} with ${firstStats.toneEn}\n")
                    }
                    append("• Scene check (${sampledFrames.size} key frames): $motionLabelEn.")
                    if (question.isNotBlank()) {
                        append("\n• Note (\"$question\"): Average brightness is $avgBright/255 with clear framing.")
                    }
                }
            }

            Result.success(VideoAnalysisOutcome(summary, thumbnail, durationLabel))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private data class BitmapStats(
        val avgBright: Int,
        val lightingBn: String,
        val lightingEn: String,
        val toneBn: String,
        val toneEn: String,
        val contrastLabelBn: String,
        val contrastLabelEn: String
    )

    private fun inspectBitmapStats(bitmap: Bitmap): BitmapStats {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)
        var totalBrightness = 0L
        var totalRed = 0L
        var totalGreen = 0L
        var totalBlue = 0L
        var minBright = 255
        var maxBright = 0
        var samples = 0L

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val bright = (r * 299 + g * 587 + b * 114) / 1000
                totalRed += r
                totalGreen += g
                totalBlue += b
                totalBrightness += bright
                if (bright < minBright) minBright = bright
                if (bright > maxBright) maxBright = bright
                samples++
            }
        }

        val avgBright = if (samples > 0) (totalBrightness / samples).toInt() else 128
        val avgR = if (samples > 0) (totalRed / samples).toInt() else 128
        val avgG = if (samples > 0) (totalGreen / samples).toInt() else 128
        val avgB = if (samples > 0) (totalBlue / samples).toInt() else 128
        val spread = maxBright - minBright

        val lightingBn = when {
            avgBright > 180 -> "বেশ উজ্জ্বল ও পরিষ্কার আলো"
            avgBright > 100 -> "স্বাভাবিক ও ভারসাম্যপূর্ণ আলো"
            else -> "কম আলো বা রাতের দৃশ্য"
        }
        val lightingEn = when {
            avgBright > 180 -> "bright, clear lighting"
            avgBright > 100 -> "balanced natural lighting"
            else -> "low-light or evening ambiance"
        }

        val toneBn = when {
            avgR >= avgG + 18 && avgR >= avgB + 18 -> "উষ্ণ লালচে/কমলা রঙের আভা"
            avgG >= avgR + 15 && avgG >= avgB + 15 -> "সতেজ সবুজ প্রকৃতির আভা"
            avgB >= avgR + 15 && avgB >= avgG + 15 -> "শীতল নীল ও বেগুনি আভা"
            else -> "স্বাভাবিক রঙের ভারসাম্য"
        }
        val toneEn = when {
            avgR >= avgG + 18 && avgR >= avgB + 18 -> "warm golden-red tones"
            avgG >= avgR + 15 && avgG >= avgB + 15 -> "fresh green tones"
            avgB >= avgR + 15 && avgB >= avgG + 15 -> "cool blue-violet tones"
            else -> "balanced neutral tones"
        }

        val contrastBn = if (spread > 150) "উচ্চ" else if (spread > 75) "সুষম" else "মৃদু"
        val contrastEn = if (spread > 150) "high" else if (spread > 75) "balanced" else "soft"

        return BitmapStats(avgBright, lightingBn, lightingEn, toneBn, toneEn, contrastBn, contrastEn)
    }

    suspend fun runStudioTool(
        instruction: String,
        input: String,
        language: SathiLanguage
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanInput = input.trim()
            val lowerInst = instruction.lowercase()

            val output = when {
                lowerInst.contains("morning greeting") -> {
                    if (language == SathiLanguage.BN) {
                        "আসসালামু আলাইকুম ও শুভ সকাল! নতুন দিনের আলোয় আপনার মন থাকুক প্রশান্ত ও প্রাণবন্ত। আজকের প্রতিটি কাজ সুন্দরভাবে সফল হোক।"
                    } else {
                        "Assalamu Alaikum and good morning! May today bring calm energy, joyful moments, and steady progress toward your goals."
                    }
                }

                lowerInst.contains("translate") -> {
                    val hasBangla = cleanInput.any { it in '\u0980'..'\u09FF' }
                    val langPair = if (hasBangla) "bn|en" else "en|bn"
                    fetchMyMemoryTranslation(cleanInput, langPair) ?: if (hasBangla) {
                        "অনুবাদ (সহজ সংস্করণ): $cleanInput"
                    } else {
                        "Translation ready: $cleanInput"
                    }
                }

                lowerInst.contains("schedule") || lowerInst.contains("day") -> {
                    val items = cleanInput
                        .split(Regex("[,;\n]+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf(cleanInput) }
                    val slotsBn = listOf("সকাল ৯:০০", "বেলা ১১:৩০", "দুপুর ২:৩০", "বিকেল ৫:০০", "রাত ৮:০০")
                    val slotsEn = listOf("09:00 AM", "11:30 AM", "02:30 PM", "05:00 PM", "08:00 PM")
                    if (language == SathiLanguage.BN) {
                        "আজকের সুন্দর সময়সূচি:\n" + items.mapIndexed { idx, task ->
                            "• ${slotsBn.getOrElse(idx) { "সময়মতো" }} — $task"
                        }.joinToString("\n") + "\n• কাজের ফাঁকে ১০ মিনিট বিশ্রাম ও পানি পান করতে ভুলবেন না।"
                    } else {
                        "Your Daily Schedule:\n" + items.mapIndexed { idx, task ->
                            "• ${slotsEn.getOrElse(idx) { "Flexible" }} — $task"
                        }.joinToString("\n") + "\n• Remember a 10-minute breather and water break between tasks."
                    }
                }

                lowerInst.contains("summarize") -> {
                    val sentences = cleanInput
                        .split(Regex("[।.!?\\n]+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(4)
                    if (language == SathiLanguage.BN) {
                        "মূল সারসংক্ষেপ:\n" + sentences.joinToString("\n") { "• $it" }
                    } else {
                        "Key Summary:\n" + sentences.joinToString("\n") { "• $it" }
                    }
                }

                else -> {
                    if (language == SathiLanguage.BN) {
                        "আসসালামু আলাইকুম,\n\n$cleanInput\n\nভালো থাকবেন ও দোয়া রাখবেন।\nশুভেচ্ছান্তে,\nআপনার সাথী ও বন্ধু"
                    } else {
                        "Assalamu Alaikum & warm greetings,\n\n$cleanInput\n\nWishing you a wonderful day ahead!"
                    }
                }
            }
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchMyMemoryTranslation(text: String, langPair: String): String? {
        return try {
            val encoded = URLEncoder.encode(text.take(450), "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$langPair"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val translated = JSONObject(body)
                    .optJSONObject("responseData")
                    ?.optString("translatedText")
                    ?.trim()
                translated?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isExplicitPhoneControl(lower: String): Boolean =
        lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") ||
            lower.contains("volume ") || lower.contains("ভলিউম") ||
            lower.contains("alarm") || lower.contains("অ্যালার্ম") ||
            lower.contains("timer") || lower.contains("টাইমার") ||
            lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর") ||
            lower.startsWith("sms ") || lower.startsWith("message ") || lower.contains("মেসেজ পাঠা") ||
            lower.contains("youtube") || lower.contains("ইউটিউব") ||
            lower.contains("facebook") || lower.contains("ফেসবুক") ||
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") ||
            lower.contains("tiktok") || lower.contains("টিকটক") ||
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") ||
            lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ") || lower.contains("হোয়াটসঅ্যাপ") ||
            lower.startsWith("open ") || lower.contains("খোলো") || lower.contains("চালু করো") || lower.contains("ঢোকো")

    private suspend fun callGeminiRestWithSearch(
        apiKey: String,
        userMessage: String,
        language: SathiLanguage,
        userName: String,
        memories: List<UserMemory>,
        recentHistory: List<Pair<Boolean, String>>,
        userEmotion: UserEmotion? = null
    ): SathiTurnResult? = withContext(Dispatchers.IO) {
        try {
            val langName = if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"
            val memorySummary = if (memories.isNotEmpty()) {
                memories.take(6).joinToString("; ") { "${it.title}: ${it.content}" }
            } else ""
            val sysText = buildString {
                append("You are Sathi (সাথী), a warm, caring, intelligent personal AI voice companion created by Mohammad Bayzid Hasan (মোহাম্মদ বাইজিদ হাসান). ")
                append("Never say 'নমস্কার' or any religious greeting other than 'আসসালামু আলাইকুম' when greeting. ")
                append("Address the user warmly as $userName. Reply clearly and concisely in $langName so it sounds natural when spoken aloud.")
                if (userEmotion != null) {
                    append(" The user's current emotional mood is ${userEmotion.labelEn} (${userEmotion.emoji}); respond with fitting empathy and care.")
                }
                if (memorySummary.isNotBlank()) {
                    append(" User memories: $memorySummary.")
                }
            }

            val contents = JSONArray()
            recentHistory.takeLast(8).forEach { (isUser, msg) ->
                if (msg.isNotBlank()) {
                    contents.put(
                        JSONObject().apply {
                            put("role", if (isUser) "user" else "model")
                            put("parts", JSONArray().put(JSONObject().put("text", msg.take(800))))
                        }
                    )
                }
            }
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val payload = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysText))))
                put("contents", contents)
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val cand = root.optJSONArray("candidates")?.optJSONObject(0) ?: return@withContext null
                val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return@withContext null
                val reply = buildString {
                    for (i in 0 until parts.length()) {
                        val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                        if (t.isNotBlank()) append(t)
                    }
                }.trim()
                if (reply.isBlank()) return@withContext null

                val sources = mutableListOf<WebSource>()
                val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
                if (chunks != null) {
                    for (i in 0 until chunks.length()) {
                        val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                        val uri = web.optString("uri").trim()
                        val title = web.optString("title").trim()
                        if (uri.startsWith("http")) {
                            sources.add(WebSource(title = title.ifBlank { uri }, snippet = "Google Search", url = uri))
                        }
                    }
                }

                simpleTurn(
                    reply = reply,
                    mood = SathiMood.WARM,
                    sources = sources.distinctBy { it.url }.take(4)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun callGeminiVisionRest(
        apiKey: String,
        bitmap: Bitmap,
        question: String,
        language: SathiLanguage,
        isScreenCapture: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                val ratio = 1024f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)

            val promptText = buildString {
                if (language == SathiLanguage.BN) {
                    append(if (isScreenCapture) "এই ফোনের স্ক্রিনে কী দেখা যাচ্ছে তা সহজ বাংলায় বুঝিয়ে বলো।" else "এই ছবিটিতে কী আছে তা স্পষ্ট বাংলায় বর্ণনা করো।")
                } else {
                    append(if (isScreenCapture) "Explain what is shown on this phone screen clearly." else "Describe what is visible in this photo clearly.")
                }
                if (question.isNotBlank()) {
                    append("\nUser question: $question")
                }
            }

            val parts = JSONArray()
                .put(JSONObject().put("text", promptText))
                .put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", b64)
                        }
                    )
                )

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val partsOut = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: return@withContext null
                val textOut = (0 until partsOut.length())
                    .mapNotNull { partsOut.optJSONObject(it)?.optString("text") }
                    .joinToString("")
                    .trim()
                textOut.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun simpleTurn(
        reply: String,
        mood: SathiMood,
        action: PhoneActionType = PhoneActionType.NONE,
        param: String = "",
        secondary: String = "",
        sources: List<WebSource> = emptyList()
    ) = SathiTurnResult(
        replyText = reply,
        mood = mood,
        actionType = action,
        actionParam = param,
        secondaryParam = secondary,
        shouldSaveMemory = false,
        memoryCategory = MemoryCategory.NOTE,
        memoryTitle = "",
        memoryContent = "",
        groundedSources = sources
    )

    companion object {
        fun nextStory(language: SathiLanguage, index: Int, topicHint: String = ""): String {
            val lower = topicHint.lowercase()

            val islamicStoriesBn = listOf(
                "এক রাতে খলিফা হযরত উমর (রা.) মদিনার অলিগলি ঘুরে দেখছিলেন। হঠাৎ এক তাঁবুর ভেতর থেকে বাচ্চাদের কান্নার শব্দ শুনতে পেলেন। কাছে গিয়ে দেখলেন, এক মা চুলোয় খালি পাতিল বসিয়ে পানি জ্বাল দিচ্ছেন শুধু ক্ষুধার্ত বাচ্চাদের ঘুম পাড়ানোর জন্য। তা দেখে খলিফার চোখে পানি এসে গেল। তিনি নিজেই বায়তুল মাল থেকে আটা ও ঘি পিঠে বয়ে এনে সেই মায়ের হাতে তুলে দিলেন এবং রান্না শেষ না হওয়া পর্যন্ত সেখানেই অপেক্ষা করলেন। একজন প্রকৃত নেতার দায়িত্ববোধ কত মহান হতে পারে, এটি তার অনন্য উদাহরণ!",
                "এক দরিদ্র মেষপালক মরুভূমিতে ছাগল চরাচ্ছিলেন। এক পথিক তাকে পরীক্ষা করার জন্য বলল, 'ভাই, আমাকে একটা ছাগল বিক্রি করে দাও—মালিক তো আর জানবে না!' রাখাল আকাশের দিকে তাকিয়ে দৃঢ় কণ্ঠে উত্তর দিলেন, 'মালিক না দেখলেও মহান আল্লাহ তো সবই দেখছেন!' তার এই আমানতদারিতা ও সততার কথা শুনে পথিক মুগ্ধ হয়ে গেলেন এবং পরে সেই রাখালকে দাসত্ব থেকে মুক্ত করে দিলেন।"
            )
            val funnyStoriesBn = listOf(
                "একবার নবাব গোপাল ভাঁড়কে জিজ্ঞাসা করলেন, 'গোপাল, বলো তো পৃথিবীতে সবচেয়ে বেশি মানুষ কোন পেশায় আছে?' গোপাল একটু ভেবে বলল, 'হুজুর, ডাক্তার!' নবাব অবাক হয়ে বললেন, 'এত কম ডাক্তার, তুমি বলছ সবচেয়ে বেশি?' গোপাল হেসে বলল, 'হুজুর, একবার মাথা ব্যথার কথা বলুন—দেখবেন পাড়ার সবাই একসাথে ওষুধ ও উপদেশ দেওয়া শুরু করেছে! সবাই-ই তো ডাক্তার!'",
                "একদিন এক অলস লোক নদীর পাড়ে বসে ছিল। এক পথিক জিজ্ঞেস করল, 'ভাই, নদী পার হবো কীভাবে?' অলস লোকটি শুয়ে শুয়ে বলল, 'সকাল থেকে তো আমিও দেখছি, এপার তো ওপারে যাচ্ছে না, ওপারও এপারে আসছে না!'"
            )
            val spookyStoriesBn = listOf(
                "শীতের এক কুয়াশাচ্ছন্ন রাতে এক ট্রেনযাত্রী ভুল স্টেশনে নেমে পড়লেন। স্টেশনজুড়ে নিস্তব্ধতা, শুধু দূরের একটি টিমটিমে হলুদ বাতি জ্বলছে। হঠাৎ তিনি দেখলেন এক বৃদ্ধ স্টেশনমাস্টার হাতে লাল লণ্ঠন নিয়ে দাঁড়িয়ে আছেন এবং বললেন, 'সামনের সেতুতে ভাঙন ধরেছে, তাই আজ কোনো ট্রেন যাবে না।' যাত্রীটি নিরাপদে রাত কাটালেন। পরদিন সকালে গ্রামবাসীর কাছে শুনলেন, বহু বছর আগেই সেই স্টেশনটি বন্ধ হয়ে গেছে—আর সেই রাতে সত্যিই সামনের রেলসেতুটি ধসে পড়েছিল! আজও লোকে বলে, এক অদৃশ্য রক্ষক সেদিন যাত্রীটিকে বাঁচিয়ে দিয়েছিলেন।"
            )
            val fairyStoriesBn = listOf(
                "বহু বছর আগে পাহাড়ের ওপারে এক নীল নদীর তীরে ছিল এক সোনার পাখির বন। সেই পাখির ডানার প্রতিটি পালক রোদের আলোয় ঝলমল করত, আর তার সুর শুনলে অসুস্থ মানুষও সুস্থ হয়ে উঠত। একদিন এক সৎ কাঠুরিয়া বনের ভেতর আহত পাখিটিকে খুঁজে পেয়ে নিজের গায়ের চাদরে জড়িয়ে বাড়ি নিয়ে এলো এবং সেবা করে সুস্থ করে তুলল। বিদায়ের দিন সোনার পাখিটি তার হাতে একটি উজ্জ্বল পালক দিয়ে বলল, 'যখনই বিপদে পড়বে, সৎ পথে থেকো—আমি ঠিকই সাহায্য পাঠাব।' এরপর থেকেই কাঠুরিয়ার জীবন আলোকিত হয়ে উঠল।"
            )
            val generalStoriesBn = listOf(
                "এক ছোট্ট গ্রামের বালক প্রতিদিন ভোরে পাখিদের জন্য ছাদে পানি ও দানা রেখে যেত। প্রথমে কেউ গুরুত্ব দিত না, কিন্তু কয়েক মাস পর পুরো গ্রাম জুড়ে পাখির কলরব ছড়িয়ে পড়ল। ছোট্ট একটি ভালো কাজও পুরো পরিবেশ বদলে দিতে পারে!",
                "এক কৃষকের পুরোনো কলসিতে একটি ছোট ফাটল ছিল, তাই পথ চলতে অর্ধেক পানি পড়ে যেত। কলসিটি খুব মন খারাপ করল। কিন্তু কৃষক হেসে দেখাল—সেই পথের ধারেই ফুটেছে রঙিন সব ফুল! আমাদের দুর্বলতাও কখনো কখনো অন্যের জীবনে সৌন্দর্য ছড়িয়ে দেয়।",
                "একদিন এক বৃদ্ধ নাবিক ঝড়ের রাতে ছোট একটি বাতি জ্বালিয়ে রাখলেন তীরের পাশে। সেই সামান্য আলো দেখেই পথ হারানো তিনটি নৌকা নিরাপদে তীরে ফিরে এসেছিল। আশা যত ছোটই হোক, তা বড় পথ দেখাতে পারে।",
                "দুই বন্ধু মরুভূমি দিয়ে হাঁটছিল। পথে সামান্য বিষয় নিয়ে ঝগড়া হলো এবং একজন বালিতে লিখল, 'আজ আমার বন্ধু আমাকে কষ্ট দিয়েছে।' কিছুক্ষণ পর তারা এক নদীতে সাঁতার কাটতে গিয়ে একজন ডুবতে লাগল—বন্ধুটি প্রাণপণ চেষ্টা করে তাকে বাঁচাল। তখন সে একটি পাথরে খোদাই করে লিখল, 'আজ আমার বন্ধু আমার জীবন বাঁচিয়েছে।' কষ্টগুলো বালি তে আর ভালোবাসাগুলো পাথরে লিখে রাখাই প্রকৃত বন্ধুত্ব।"
            )

            val allBn = generalStoriesBn + islamicStoriesBn + funnyStoriesBn + fairyStoriesBn + spookyStoriesBn
            if (language == SathiLanguage.BN) {
                val chosenList = when {
                    lower.contains("ইসলাম") || lower.contains("নবী") || lower.contains("সাহাব") -> islamicStoriesBn
                    lower.contains("হাসি") || lower.contains("মজার") || lower.contains("গোপাল") -> funnyStoriesBn
                    lower.contains("ভূত") || lower.contains("ভৌতিক") || lower.contains("ভয়ের") -> spookyStoriesBn
                    lower.contains("রূপকথা") || lower.contains("পরী") || lower.contains("রাজ") -> fairyStoriesBn
                    else -> allBn
                }
                return chosenList[index % chosenList.size]
            }

            val storiesEn = listOf(
                "A young boy left a small bowl of water on his roof each morning for tired sparrows. Soon his whole neighborhood joined in, filling the town with birdsong. Even small kindnesses ripple further than we see.",
                "A cracked clay pot felt ashamed that half its water spilled along the garden path. One day the gardener smiled and pointed to the wildflowers blooming only along that side—reminding us that flaws can nourish beauty too.",
                "During a dark storm, a lighthouse keeper lit one steady lantern along a quiet shore. Three lost boats spotted that single beam and sailed safely home. Never underestimate a steady light.",
                "Two friends were walking across a desert. After a brief argument, one wrote in the sand: 'Today my friend hurt my feelings.' Later, when he slipped into quicksand, his friend risked everything to pull him out. He carved into stone: 'Today my friend saved my life.' Let pain fade like footprints in sand, and cherish kindness forever."
            )
            return storiesEn[index % storiesEn.size]
        }

        fun nextJoke(language: SathiLanguage, index: Int): String {
            val jokesBn = listOf(
                "শিক্ষক: বলো তো, পৃথিবী সূর্যের চারপাশে ঘোরে নাকি সূর্য পৃথিবীর চারপাশে? ছাত্র: স্যার, আমার তো মাথাটাই পরীক্ষায় চারপাশে ঘুরছে!",
                "বন্ধু ১: দোস্ত, আমার ঘড়িটা পানিতে পড়ে গেছে, তাও টিকটিক করে চলছে! বন্ধু ২: কীভাবে? বন্ধু ১: ওটা বোধহয় সাঁতার শিখে ফেলেছে!"
            )
            val jokesEn = listOf(
                "Why did the smartphone go to school? Because it wanted to improve its reception!",
                "Why do programmers prefer dark mode? Because light attracts bugs!"
            )
            val list = if (language == SathiLanguage.BN) jokesBn else jokesEn
            return list[index % list.size]
        }

        fun tryEvaluateMath(input: String, language: SathiLanguage): String? {
            val normalized = input
                .replace('০', '0').replace('১', '1').replace('২', '2').replace('৩', '3').replace('৪', '4')
                .replace('৫', '5').replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
                .replace("×", "*").replace("÷", "/")
                .replace("যোগ", "+").replace("বিয়োগ", "-").replace("বিয়োগ", "-")
                .replace("গুণ", "*").replace("ভাগ", "/")
            val match = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)").find(normalized)
                ?: return null
            val a = match.groupValues[1].toDoubleOrNull() ?: return null
            val op = match.groupValues[2]
            val b = match.groupValues[3].toDoubleOrNull() ?: return null
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) return if (language == SathiLanguage.BN) "শূন্য দিয়ে ভাগ করা যায় না।" else "Cannot divide by zero." else a / b
                else -> return null
            }
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(Locale.US, "%.2f", result)
            return if (language == SathiLanguage.BN) "উত্তর হবে: $formatted।" else "The answer is $formatted."
        }

        fun isTimeOrDateQuery(lower: String): Boolean {
            return lower.contains("time") || lower.contains("সময়") || lower.contains("সময়") ||
                lower.contains("কয়টা") || lower.contains("কয়টা") || lower.contains("কটা বাজে") ||
                lower.contains("বাজে") || lower.contains("তারিখ") || lower.contains("date") ||
                lower.contains("কী বার") || lower.contains("কি বার") || lower.contains("কোন বার") ||
                lower.contains("what day")
        }

        fun formatCurrentTimeAndDate(language: SathiLanguage): String {
            val cal = Calendar.getInstance()
            val hour24 = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            if (language == SathiLanguage.EN) {
                val amPm = if (hour24 < 12) "AM" else "PM"
                val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val dayName = days[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
                val monthName = months[cal.get(Calendar.MONTH).coerceIn(0, 11)]
                val minStr = minute.toString().padStart(2, '0')
                return "Right now it is $hour12:$minStr $amPm on $dayName, ${cal.get(Calendar.DAY_OF_MONTH)} $monthName ${cal.get(Calendar.YEAR)}."
            }

            val period = when (hour24) {
                in 4..5 -> "ভোর"
                in 6..11 -> "সকাল"
                in 12..14 -> "দুপুর"
                in 15..17 -> "বিকাল"
                in 18..19 -> "সন্ধ্যা"
                else -> "রাত"
            }
            val daysBn = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")
            val monthsBn = listOf(
                "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
            )
            val dayNameBn = daysBn[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]
            val monthNameBn = monthsBn[cal.get(Calendar.MONTH).coerceIn(0, 11)]
            val hBn = toBengaliDigits(hour12.toString())
            val mBn = toBengaliDigits(minute.toString().padStart(2, '0'))
            val dBn = toBengaliDigits(cal.get(Calendar.DAY_OF_MONTH).toString())
            val yBn = toBengaliDigits(cal.get(Calendar.YEAR).toString())
            return "এখন $period $hBn টা $mBn মিনিট। আজ $dayNameBn, $dBn $monthNameBn $yBn।"
        }

        private fun toBengaliDigits(input: String): String {
            val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
            return buildString(input.length) {
                input.forEach { ch ->
                    if (ch in '0'..'9') append(bnDigits[ch - '0']) else append(ch)
                }
            }
        }
    }
}
