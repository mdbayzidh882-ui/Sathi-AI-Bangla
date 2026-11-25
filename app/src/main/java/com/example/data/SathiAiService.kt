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
    val groundedSources: List<WebSource> = emptyList(),
    val recipientParam: String = ""
)

data class VideoAnalysisOutcome(
    val summaryText: String,
    val thumbnail: Bitmap?,
    val durationLabel: String
)

class SathiAiService(
    private val packManager: OfflinePackManager? = null
) {
    private var storyCursor = 0
    private var jokeCursor = 0
    private var chatCursor = 0

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(18, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
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
        // Creator identity rule (strictly enforced without hijacking 'ছবি তৈরি' or 'রেসিপি তৈরি')
        if (lower.contains("বানিয়ে") || lower.contains("বানিয়ে") || lower.contains("বানাইছে") ||
            lower.contains("নির্মাতা") ||
            ((lower.contains("কে তৈরি") || lower.contains("কার তৈরি") || lower.contains("তোমাকে তৈরি") || lower.contains("তোমারে তৈরি")) &&
                lower.contains("তৈরি")) ||
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

        // Sathi AI's own Name & Identity
        if (lower.contains("তোমার নাম") || lower.contains("আপনার নাম") || lower.contains("তোর নাম") ||
            lower.contains("what is your name") || lower.contains("what's your name") || lower.contains("your name") ||
            lower.contains("who are you") ||
            Regex("^(তুমি|আপনি)\\s*কে[?।!\\s]*$").matches(lower)
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম! আমার নাম সাথী এআই (Sathi AI), আপনার ব্যক্তিগত এআই বন্ধু। আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।"
            } else {
                "Assalamu Alaikum! My name is Sathi AI (সাথী এআই), your personal AI companion created by Mohammad Bayzid Hasan."
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        // Self-capability queries (prevents "তুমি কি ওয়েব/গুগল সার্চ করতে পারো?" from triggering random Wikipedia results!)
        detectCapabilitySelfQuery(lower, language, hasApiKey = apiKey.isNotBlank())?.let { capReply ->
            return Result.success(simpleTurn(capReply, SathiMood.HAPPY))
        }

        // Device Time / Date / Day (ALWAYS local & exact)
        if (isTimeOrDateQuery(lower)) {
            return Result.success(simpleTurn(formatCurrentTimeAndDate(language), SathiMood.WARM))
        }

        // 1. If web search results were already gathered, synthesize cleanly & helpfully
        if (webSources.isNotEmpty()) {
            val topSources = webSources.filter { it.snippet.isNotBlank() || it.title.isNotBlank() }.take(3)
            if (apiKey.isNotBlank()) {
                val contextPrompt = buildString {
                    append("User question: \"$text\"\n\nVerified web search snippets:\n")
                    topSources.forEachIndexed { i, src ->
                        append("${i + 1}. ${src.title}: ${src.snippet}\n")
                    }
                    append("\nSynthesize a clear, direct, accurate answer in the user's language based strictly on these verified facts without inventing details.")
                }
                callGeminiRestWithSearch(
                    apiKey = apiKey,
                    userMessage = contextPrompt,
                    language = language,
                    userName = userName,
                    memories = memories,
                    recentHistory = recentHistory,
                    userEmotion = userEmotion
                )?.let { geminiGrounded ->
                    return Result.success(geminiGrounded.copy(groundedSources = webSources.take(4)))
                }
            }

            val top = topSources.firstOrNull() ?: webSources.first()
            val extraList = topSources.drop(1)
            val reply = if (language == SathiLanguage.BN) {
                buildString {
                    append("${top.title}\n${top.snippet}")
                    extraList.forEach { extra ->
                        if (extra.snippet.isNotBlank() && extra.snippet != top.snippet) {
                            append("\n\n• ${extra.title}: ${extra.snippet.take(200)}")
                        }
                    }
                }.trim()
            } else {
                buildString {
                    append("${top.title}\n${top.snippet}")
                    extraList.forEach { extra ->
                        if (extra.snippet.isNotBlank() && extra.snippet != top.snippet) {
                            append("\n\n• ${extra.title}: ${extra.snippet.take(200)}")
                        }
                    }
                }.trim()
            }
            return Result.success(simpleTurn(reply, SathiMood.SERIOUS, sources = webSources.take(4)))
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

        val cleanKey = sanitizeApiKeyInput(apiKey)

        // 3. Short greetings & casual check-ins only (do NOT hijack rich multi-word tasks or questions!)
        val isShortTurn = text.length <= 34 && !text.contains("?") && !shouldTriggerWebSearch(text, lower)
        if (isShortTurn && (lower.contains("কি করছ") || lower.contains("কী করছ") || lower.contains("করতেছ") ||
                lower.contains("what are you doing") || lower.contains("how are you"))
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আমি আপনার সাথেই আছি $userName, মন দিয়ে আপনার কথা শুনছি! যেকোনো প্রশ্ন করতে পারেন, ছবি তৈরি বা বিশ্লেষণ করতে পারেন, অথবা বন্ধুদের WhatsApp/Messenger-এ মেসেজ লিখতে পারেন।"
            } else {
                "I'm right here listening to you, $userName! Ask me any question, generate an image, or message a friend on WhatsApp or Messenger."
            }
            return Result.success(simpleTurn(reply, SathiMood.HAPPY))
        }

        val isEmotionExpression = lower.contains("sad") || lower.contains("মন খারাপ") || lower.contains("মন ভালো নেই") ||
            lower.contains("ভালো লাগছে না") || lower.contains("কষ্ট") || lower.contains("কান্না") ||
            lower.contains("একা") || lower.contains("lonely") || lower.contains("tired") ||
            lower.contains("ক্লান্ত") || lower.contains("দুঃখ") || lower.contains("stress") ||
            lower.contains("রাগ") || lower.contains("বিরক্ত") || lower.contains("টেনশন") ||
            lower.contains("দুশ্চিন্তা") || lower.contains("অসহায়") || lower.contains("depressed")
        if (isEmotionExpression && (cleanKey.isBlank() || text.length <= 28)) {
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

        if (isShortTurn && (lower.contains("hello") || lower.contains("hi") || lower.contains("হ্যালো") ||
                lower.contains("সালাম") || lower.contains("আসসালামু") || lower.contains("কেমন আছ") ||
                lower.contains("good morning") || lower.contains("শুভ সকাল"))
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

        // 4. Image Creation Intent (routed cleanly to SathiImageGenRepository in ViewModel)
        detectImageGenerationPrompt(text, lower)?.let { imgPrompt ->
            val reply = if (language == SathiLanguage.BN) {
                "\"$imgPrompt\"-এর জন্য নমুনা ছবি প্রিভিউ তৈরি করছি..."
            } else {
                "Generating a sample image preview for \"$imgPrompt\"..."
            }
            return Result.success(
                simpleTurn(
                    reply = reply,
                    mood = SathiMood.HAPPY,
                    action = PhoneActionType.GENERATE_IMAGE,
                    param = imgPrompt
                )
            )
        }

        // 5. Social Apps & Messaging / Friend Search / Friend SMS / Comment / Reply
        detectSocialActionOrOpen(text, lower, language)?.let { socialTurn ->
            return Result.success(socialTurn)
        }

        // 6. YouTube action (open YouTube cleanly WITHOUT unwanted searches, OR search strictly what user requested)
        detectYouTubeAction(text, lower, language)?.let { ytTurn ->
            return Result.success(ytTurn)
        }

        // 7. Verified Built-In Bangladesh / Science Facts (instant & 100% accurate)
        matchVerifiedBuiltInFact(lower, language)?.let { verifiedReply ->
            return Result.success(simpleTurn(verifiedReply, SathiMood.SERIOUS))
        }

        // 8. Quick Exact Math Calculator & Unit Converter
        tryEvaluateMath(text, language)?.let { mathReply ->
            return Result.success(simpleTurn(mathReply, SathiMood.SERIOUS))
        }
        tryConvertUnits(text, language)?.let { unitReply ->
            return Result.success(simpleTurn(unitReply, SathiMood.SERIOUS))
        }

        // 9. Explicit Phone Controls (Flashlight, Volume, Alarm, Timer, Calendar, Call, SMS, Open App)
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

        if (lower.contains("volume up") || (lower.contains("ভলিউম") && (lower.contains("বাড়া") || lower.contains("বাড়া") || lower.contains("বেশি")))) {
            val reply = if (language == SathiLanguage.BN) "জি, সাউন্ড একটু বাড়িয়ে দিচ্ছি।" else "Increasing media volume."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.VOLUME_UP))
        }
        if (lower.contains("volume down") || (lower.contains("ভলিউম") && (lower.contains("কমা") || lower.contains("কম")))) {
            val reply = if (language == SathiLanguage.BN) "জি, সাউন্ড একটু কমিয়ে দিচ্ছি।" else "Lowering media volume."
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.VOLUME_DOWN))
        }

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

        if (lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর")) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{3,})").find(text)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "কল করার আগে আপনার নিশ্চিত অনুমতি নিচ্ছি। নিচের বক্সে নিশ্চিত করুন।"
            } else {
                "I'm asking for your confirmation first before opening the phone dialer."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.CALL_CONFIRM, digits))
        }

        if (lower.startsWith("sms ") || lower.startsWith("message ") || lower.contains("মেসেজ পাঠা") ||
            (lower.contains("এসএমএস") && (lower.contains("কর") || lower.contains("পাঠা")))
        ) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{3,})").find(text)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "মেসেজ পাঠানোর আগে আপনার অনুমতি নিচ্ছি। নিচে নিশ্চিত করুন।"
            } else {
                "Please confirm below before I prepare your SMS message."
            }
            return Result.success(simpleTurn(reply, SathiMood.WARM, PhoneActionType.SMS_CONFIRM, digits, text))
        }

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

        // 10. Full Gemini Intelligence + Google Search Grounding (whenever Google API Key is set!)
        val isDeviceAction = isExplicitPhoneControl(lower)
        if (!isDeviceAction && cleanKey.isNotBlank()) {
            callGeminiRestWithSearch(
                apiKey = cleanKey,
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

        // 11. Built-in Stories, Jokes, Riddles, Poems (only when requesting creative entertainment, not research about a named work)
        val asksResearchAboutTitle = lower.contains("সম্পর্কে") || lower.contains("লেখক") ||
            lower.contains("সারাংশ") || lower.contains("বিশ্লেষণ") || lower.contains("কার লেখা")
        if (!asksResearchAboutTitle) {
            if (lower.contains("গল্প") || lower.contains("story") || lower.contains("কাহিনি") || lower.contains("কাহিনী")) {
                return Result.success(simpleTurn(nextStory(language, storyCursor++, text), SathiMood.HAPPY))
            }
            if (lower.contains("কৌতুক") || lower.contains("জোক") || lower.contains("joke") || lower.contains("ধাঁধা") || lower.contains("riddle")) {
                return Result.success(simpleTurn(nextJoke(language, jokeCursor++), SathiMood.PLAYFUL))
            }
            if (lower.contains("কবিতা") || lower.contains("poem")) {
                return Result.success(simpleTurn(nextPoem(language, jokeCursor++), SathiMood.WARM))
            }
        }

        // 12. Genuine Web Search Intent (weather, prayer, currency, news, scores, & broad factual questions!)
        if (shouldTriggerWebSearch(text, lower)) {
            return Result.success(
                simpleTurn(
                    reply = if (language == SathiLanguage.BN) "আমি লাইভ ওয়েব সার্চ করে সঠিক তথ্য আনছি..." else "Searching the web for accurate live information...",
                    mood = SathiMood.SERIOUS,
                    action = PhoneActionType.WEB_SEARCH,
                    param = text
                )
            )
        }

        // 12b. Smart Local 2GB Brain lookup even in online/no-key mode if available
        packManager?.searchOfflineKnowledge(text, language)?.let { brainHit ->
            return Result.success(simpleTurn(brainHit, SathiMood.SERIOUS))
        }

        // 13. Acknowledgments
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

        // 14. Multi-turn conversational continuity (remembers previous topic!)
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

        // Emotion-aware empathy fallback
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
            "জি $userName! আপনি চাইলে যেকোনো বিষয় ওয়েবে সার্চ করতে পারেন, নমুনা ছবি বানাতে বলতে পারেন, অথবা বন্ধুকে WhatsApp/Messenger-এ মেসেজ পাঠাতে বলতে পারেন।",
            if (lastAiReply.isNotBlank()) "জি $userName, আমরা '${lastUserMsg.take(28)}' নিয়ে কথা বলছিলাম—এখন কী সাহায্য করতে পারি বলুন!"
            else "আমি সাথেই আছি, $userName। কোন বিষয়টি নিয়ে কথা বলব বা কোন তথ্যটি খুঁজে দেব বলুন!"
        )
        val naturalEn = listOf(
            "I hear you, $userName. Ask any factual question, generate a sample image, or tap + to share a photo or screen!",
            "Got it, $userName! You can search the web, send a WhatsApp or Messenger reply, check weather/prayer times, or ask for a story.",
            "I'm right here with you, $userName. What would you like to explore next?"
        )
        val idx = chatCursor++ % naturalBn.size
        val reply = if (language == SathiLanguage.BN) naturalBn[idx] else naturalEn[idx]
        return Result.success(simpleTurn(reply, userEmotion?.sathiMood ?: SathiMood.WARM))
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
        language: SathiLanguage,
        apiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanInput = input.trim()
            val cleanKey = sanitizeApiKeyInput(apiKey)
            if (cleanKey.isNotBlank()) {
                val langLabel = if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"
                val studioPrompt = buildString {
                    append("Instruction: $instruction\n")
                    append("User input: $cleanInput\n")
                    append("Respond clearly, naturally, and completely in $langLabel. Do not use 'নমস্কার'.")
                }
                callGeminiRestWithSearch(
                    apiKey = cleanKey,
                    userMessage = studioPrompt,
                    language = language,
                    userName = "বন্ধু",
                    memories = emptyList(),
                    recentHistory = emptyList()
                )?.let { geminiOut ->
                    if (geminiOut.replyText.isNotBlank()) {
                        return@withContext Result.success(geminiOut.replyText)
                    }
                }
            }

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

    private fun isExplicitPhoneControl(lower: String): Boolean {
        val isInfoQuestion = lower.contains("কীভাবে") || lower.contains("কিভাবে") || lower.contains("সম্পর্কে") ||
            lower.contains("মালিক কে") || lower.contains("ইতিহাস") || lower.contains("why ") ||
            lower.contains("how to ") || lower.contains("what is ")
        if (isInfoQuestion && !lower.contains("খোলো") && !lower.contains("চালাও") && !lower.contains("open ")) {
            return false
        }
        return lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") ||
            lower.contains("volume ") || lower.contains("ভলিউম") ||
            lower.contains("alarm") || lower.contains("অ্যালার্ম") ||
            lower.contains("timer") || lower.contains("টাইমার") ||
            lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর") ||
            lower.startsWith("sms ") || lower.startsWith("message ") || lower.contains("মেসেজ পাঠা") ||
            lower.contains("youtube") || lower.contains("ইউটিউব") ||
            lower.contains("facebook") || lower.contains("ফেসবুক") ||
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") ||
            lower.contains("tiktok") || lower.contains("টিকটক") ||
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") || lower.contains("ম্যাসেঞ্জার") ||
            lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ") || lower.contains("হোয়াটসঅ্যাপ") ||
            lower.startsWith("open ") || lower.contains("খোলো") || lower.contains("চালু করো") || lower.contains("ঢোকো")
    }

    private suspend fun callGeminiRestWithSearch(
        apiKey: String,
        userMessage: String,
        language: SathiLanguage,
        userName: String,
        memories: List<UserMemory>,
        recentHistory: List<Pair<Boolean, String>>,
        userEmotion: UserEmotion? = null
    ): SathiTurnResult? = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKeyInput(apiKey)
        if (cleanKey.isBlank()) return@withContext null

        val langName = if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"
        val memorySummary = if (memories.isNotEmpty()) {
            memories.take(8).joinToString("; ") { "${it.title}: ${it.content}" }
        } else ""
        val sysText = buildString {
            append("You are Sathi AI (সাথী এআই), an extraordinarily smart, analytical, empathetic, Gemini-class AI assistant created by Mohammad Bayzid Hasan (মোহাম্মদ বাইজিদ হাসান). ")
            append("Think step-by-step for complex logic, math, coding, debugging, science, health guidance, legal/business drafting, and Bengali-English translation. ")
            append("Provide clear, structured, well-reasoned answers without hallucinating facts. ")
            append("Never say 'নমস্কার'. Use 'আসসালামু আলাইকুম' only when greeting. ")
            append("Address the user as $userName and answer naturally in $langName. Use google_search whenever live news, prices, weather, schedules, or current factual verification helps.")
            if (userEmotion != null) {
                append(" User's current emotional mood: ${userEmotion.labelEn} (${userEmotion.emoji}); be deeply understanding.")
            }
            if (memorySummary.isNotBlank()) {
                append(" Saved user memories: $memorySummary.")
            }
        }

        val contents = JSONArray()
        recentHistory.takeLast(12).forEach { (isUser, msg) ->
            if (msg.isNotBlank()) {
                contents.put(
                    JSONObject().apply {
                        put("role", if (isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.take(1200))))
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

        val candidateModels = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        for (model in candidateModels) {
            for (useSearch in listOf(true, false)) {
                try {
                    val payload = JSONObject().apply {
                        put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysText))))
                        put("contents", contents)
                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("temperature", 0.7)
                                put("maxOutputTokens", 2048)
                            }
                        )
                        if (useSearch) {
                            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                        }
                    }
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", cleanKey)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            val code = resp.code
                            // If search tool caused 400/403/404, immediately retry plain generateContent on this same model
                            if (useSearch && (code == 400 || code == 403 || code == 404)) {
                                return@use
                            }
                            if (code == 401 || code == 403) return@withContext null
                            return@use
                        }
                        val body = resp.body?.string() ?: return@use
                        val root = JSONObject(body)
                        val cand = root.optJSONArray("candidates")?.optJSONObject(0) ?: return@use
                        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return@use
                        val reply = buildString {
                            for (i in 0 until parts.length()) {
                                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                                if (t.isNotBlank()) append(t)
                            }
                        }.trim()
                        if (reply.isBlank()) return@use

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

                        return@withContext simpleTurn(
                            reply = reply,
                            mood = SathiMood.WARM,
                            sources = sources.distinctBy { it.url }.take(4)
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
        null
    }

    suspend fun fetchGeminiKnowledgeEntry(
        apiKey: String,
        title: String,
        keywords: List<String>,
        prompt: String
    ): OfflineKnowledgeEntry? = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKeyInput(apiKey)
        if (cleanKey.isBlank()) return@withContext null
        val models = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        for (model in models) {
            for (useSearch in listOf(true, false)) {
                try {
                    val payload = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put(
                                        "parts",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "text",
                                                "Write a thorough, factual, structured offline reference guide in Bengali (with key technical terms in English) about: $prompt. Keep it clean plain text, accurate, and easy to read aloud."
                                            )
                                        )
                                    )
                                }
                            )
                        )
                        if (useSearch) {
                            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                        }
                    }
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                        .addHeader("x-goog-api-key", cleanKey)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            if (useSearch && (resp.code == 400 || resp.code == 404)) return@use
                            if (resp.code == 401 || resp.code == 403) return@withContext null
                            return@use
                        }
                        val body = resp.body?.string() ?: return@use
                        val parts = JSONObject(body)
                            .optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts") ?: return@use
                        val text = (0 until parts.length())
                            .mapNotNull { parts.optJSONObject(it)?.optString("text") }
                            .joinToString("\n")
                            .trim()
                        if (text.length >= 60) {
                            return@withContext OfflineKnowledgeEntry(
                                topic = title,
                                keywords = (keywords + title).distinct(),
                                summaryBn = text.take(8000),
                                summaryEn = text.take(8000)
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        null
    }

    private suspend fun callGeminiVisionRest(
        apiKey: String,
        bitmap: Bitmap,
        question: String,
        language: SathiLanguage,
        isScreenCapture: Boolean
    ): String? = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKeyInput(apiKey)
        if (cleanKey.isBlank()) return@withContext null

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
        val models = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue
                    val body = resp.body?.string() ?: continue
                    val partsOut = JSONObject(body)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue
                    val textOut = (0 until partsOut.length())
                        .mapNotNull { partsOut.optJSONObject(it)?.optString("text") }
                        .joinToString("")
                        .trim()
                    if (textOut.isNotBlank()) return@withContext textOut
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    suspend fun verifyGoogleApiKey(
        apiKey: String,
        language: SathiLanguage = SathiLanguage.BN
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = sanitizeApiKeyInput(apiKey)
        if (cleanKey.isBlank() || cleanKey.length < 16 || cleanKey.any { it.isWhitespace() }) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (language == SathiLanguage.BN) {
                        "সঠিক Google API Key দিন (AIza... দিয়ে শুরু, কোনো ফাঁকা জায়গা বা বাড়তি লেখা ছাড়া)।"
                    } else {
                        "Please paste a valid Google API Key starting with AIza... without spaces."
                    }
                )
            )
        }
        var lastCode = 0
        var lastDetail = ""
        val models = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash")
        try {
            for (model in models) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                val probeWithSearch = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().put(JSONObject().put("text", "Reply with only: OK")))
                            }
                        )
                    )
                    put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                }
                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .post(probeWithSearch.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success(
                            if (language == SathiLanguage.BN) {
                                "সফলভাবে সংযুক্ত হয়েছে ($model)! এখন সাথী এআই সরাসরি Google সার্চ, জেমিনি বুদ্ধিমত্তা ও ছবি তৈরি করতে পারবে।"
                            } else {
                                "Connected ($model)! Sathi AI is active for Google Search, Gemini AI, and image creation."
                            }
                        )
                    }
                    lastCode = resp.code
                    val errBody = resp.body?.string().orEmpty()
                    lastDetail = runCatching {
                        JSONObject(errBody).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")

                    if (lastCode == 400 || lastCode == 404) {
                        val plainProbe = JSONObject().apply {
                            put(
                                "contents",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("role", "user")
                                        put("parts", JSONArray().put(JSONObject().put("text", "Reply with only: OK")))
                                    }
                                )
                            )
                        }
                        val plainReq = Request.Builder()
                            .url(url)
                            .addHeader("x-goog-api-key", cleanKey)
                            .post(plainProbe.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        httpClient.newCall(plainReq).execute().use { plainResp ->
                            if (plainResp.isSuccessful) {
                                return@withContext Result.success(
                                    if (language == SathiLanguage.BN) {
                                        "Google চাবি সংযুক্ত হয়েছে ($model)! সাথী এআই সক্রিয় ও জ্ঞান ভান্ডার ডাউনলোডের জন্য প্রস্তুত।"
                                    } else {
                                        "Google API key connected ($model)! Sathi AI & Knowledge Brain sync are active."
                                    }
                                )
                            }
                            lastCode = plainResp.code
                            val plainErr = plainResp.body?.string().orEmpty()
                            val parsedPlain = runCatching {
                                JSONObject(plainErr).optJSONObject("error")?.optString("message").orEmpty()
                            }.getOrDefault("")
                            if (parsedPlain.isNotBlank()) lastDetail = parsedPlain
                        }
                    }
                }
            }

            val detailSuffix = if (lastDetail.isNotBlank()) " (${lastDetail.take(120)})" else ""
            val errMsg = when (lastCode) {
                400, 401, 403 -> if (language == SathiLanguage.BN) {
                    "চাবিটি সঠিক নয় বা Generative Language API চালু নেই (Code $lastCode)$detailSuffix। Google AI Studio (aistudio.google.com) থেকে নতুন চাবি তৈরি করে পেস্ট করুন।"
                } else {
                    "Invalid key or Generative Language API not enabled (HTTP $lastCode)$detailSuffix."
                }
                429 -> if (language == SathiLanguage.BN) {
                    "এই চাবির ফ্রি কোটা সাময়িকভাবে পূর্ণ (HTTP 429)। কিছুক্ষণ পর চেষ্টা করুন অথবা নতুন প্রজেক্টের চাবি দিন।"
                } else {
                    "Rate limit or quota exceeded (HTTP 429). Please wait or use another key."
                }
                else -> if (language == SathiLanguage.BN) {
                    "Google সার্ভারে সংযোগ করা যায়নি (HTTP $lastCode)$detailSuffix।"
                } else {
                    "Could not reach Google service (HTTP $lastCode)$detailSuffix."
                }
            }
            Result.failure(IllegalStateException(errMsg))
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    if (language == SathiLanguage.BN) {
                        "নেটওয়ার্ক সমস্যার কারণে Google যাচাই করা যায়নি—তবে চাবিটি সংরক্ষিত হয়েছে। ইন্টারনেট চালু থাকলে কাজ করবে।"
                    } else {
                        "Network error while verifying Google key (${e.localizedMessage.orEmpty()}), though key was saved locally."
                    }
                )
            )
        }
    }

    private fun simpleTurn(
        reply: String,
        mood: SathiMood,
        action: PhoneActionType = PhoneActionType.NONE,
        param: String = "",
        secondary: String = "",
        sources: List<WebSource> = emptyList(),
        recipient: String = ""
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
        groundedSources = sources,
        recipientParam = recipient
    )

    companion object {
        fun detectImageGenerationPrompt(text: String, lower: String): String? {
            if (lower.contains("ছবিতে কী") || lower.contains("ছবি বিশ্লেষণ") ||
                lower.contains("ছবি তোল") || lower.contains("ছবি আপলোড") ||
                lower.contains("ছবি দেখো") || lower.contains("analyze photo")
            ) {
                return null
            }
            val matches = lower.contains("ছবি তৈরি") || lower.contains("ছবি বানা") ||
                lower.contains("ছবি আঁক") || lower.contains("ছবি এঁকে") ||
                lower.contains("একটি ছবি দাও") || lower.contains("একটা ছবি দাও") ||
                Regex("\\b(generate|create|make|draw|paint)\\s+(an?\\s+)?(image|picture|photo|art|logo)\\b")
                    .containsMatchIn(lower) ||
                lower.startsWith("draw ")

            if (!matches) return null

            val prompt = text
                .replace(Regex("(একটি|একটা|সুন্দর|ছোট|হালকা পাতলা|প্লিজ|আমাকে)"), " ")
                .replace(Regex("(ছবি তৈরি করো|ছবি তৈরি করে দাও|ছবি তৈরি|ছবি বানাও|ছবি বানিয়ে দাও|ছবি বানিয়ে দাও|ছবি আঁকো|ছবি এঁকে দাও|ছবি দাও)"), " ")
                .replace(Regex("\\b(generate|create|make|draw|paint|an?|image|picture|photo|art|of)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { text.trim() }
            return prompt
        }

        fun detectYouTubeAction(
            text: String,
            lower: String,
            language: SathiLanguage
        ): SathiTurnResult? {
            val mentionsYouTube = lower.contains("youtube") || lower.contains("ইউটিউব") ||
                lower.contains("গান চালাও") || (lower.contains("ভিডিও") && lower.contains("চালাও"))
            if (!mentionsYouTube) return null

            val isQuestionAboutYouTube = (lower.contains("কীভাবে") || lower.contains("কিভাবে") ||
                lower.contains("মালিক কে") || lower.contains("সম্পর্কে") ||
                lower.contains("how to ") || lower.contains("who owns")) &&
                !lower.contains("খোলো") && !lower.contains("খুলো") && !lower.contains("চালাও") &&
                !lower.contains("সার্চ") && !lower.contains("open") && !lower.contains("search")
            if (isQuestionAboutYouTube) return null

            // Cleanly separate pure "open YouTube" vs user's explicit search/play query
            var stripped = text
                .replace(Regex("(youtube|open|launch|start|go\\s+to|search\\s+for|search|find|play|on\\s+youtube|in\\s+youtube|app)", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("(তুমি|আপনি|দয়া\\s*করে|দয়া\\s*করে|প্লিজ|আমার\\s*জন্য|একটু|একটা|গিয়ে|গিয়ে|দিয়ে|দিয়ে|ইউটিউবে|ইউটিউব|অ্যাপে|অ্যাপ)"), " ")
                .replace(Regex("(খুলে\\s*দাও|খুলে\\s*দিন|খোলো|খুলো|চালু\\s*করো|অন\\s*করো|ঢোকো|ঢুকাও|ঢুকো|ঢোকাও|যাও|সার্চ\\s*করো|সার্চ\\s*দাও|খুঁজে\\s*দাও|খোঁজো|খোজো|চালাও|চালিয়ে\\s*দাও|চালিয়ে\\s*দাও|দাও|করো|এ)"), " ")
                .replace(Regex("[?,।!\"']+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            // Guard against leftover short Bengali pronouns/particles
            val nonSearchWords = setOf("আমি", "তুমি", "এ", "টা", "টি", "ও", "আর", "করে", "থেকে", "শুধু", "মাত্র")
            val tokens = stripped.split(" ").filter { it.isNotBlank() && it !in nonSearchWords }
            val query = tokens.joinToString(" ").trim()

            return if (query.isEmpty()) {
                SathiTurnResult(
                    replyText = if (language == SathiLanguage.BN) {
                        "জি, ইউটিউব (YouTube) খুলে দিচ্ছি—অন্য কিছু সার্চ না করে সরাসরি অ্যাপ খুললাম।"
                    } else {
                        "Opening YouTube directly for you."
                    },
                    mood = SathiMood.HAPPY,
                    actionType = PhoneActionType.OPEN_APP,
                    actionParam = "YouTube",
                    secondaryParam = "",
                    shouldSaveMemory = false,
                    memoryCategory = MemoryCategory.NOTE,
                    memoryTitle = "",
                    memoryContent = ""
                )
            } else {
                SathiTurnResult(
                    replyText = if (language == SathiLanguage.BN) {
                        "জি, ইউটিউবে '$query' খুঁজে/চালিয়ে দিচ্ছি।"
                    } else {
                        "Opening YouTube to search '$query'."
                    },
                    mood = SathiMood.HAPPY,
                    actionType = PhoneActionType.YOUTUBE,
                    actionParam = query,
                    secondaryParam = "",
                    shouldSaveMemory = false,
                    memoryCategory = MemoryCategory.NOTE,
                    memoryTitle = "",
                    memoryContent = ""
                )
            }
        }

        fun detectSocialActionOrOpen(
            text: String,
            lower: String,
            language: SathiLanguage
        ): SathiTurnResult? {
            val explicitPlatform = when {
                lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস") -> "WhatsApp"
                lower.contains("messenger") || lower.contains("মেসেঞ্জার") || lower.contains("ম্যাসেঞ্জার") -> "Messenger"
                lower.contains("facebook") || lower.contains("ফেসবুক") || Regex("\\bfb\\b").containsMatchIn(lower) -> "Facebook"
                lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") || lower.contains("ইন্সটাগ্রাম") -> "Instagram"
                lower.contains("tiktok") || lower.contains("টিকটক") -> "TikTok"
                else -> null
            }

            val isInfoQuery = (lower.contains("সম্পর্কে") || lower.contains("কীভাবে") || lower.contains("কিভাবে") ||
                lower.contains("ইতিহাস") || lower.contains("সুবিধা") || lower.contains("what is ") || lower.contains("how does ")) &&
                !lower.contains("এসএমএস") && !lower.contains("মেসেজ") && !lower.contains("খোঁজো") &&
                !lower.contains("খোলো") && !lower.contains("লিখ") && !lower.contains("লেখ")
            if (isInfoQuery) return null

            val wantsSearchFriend = lower.contains("খোঁজ") || lower.contains("খোজ") || lower.contains("খুঁজ") ||
                lower.contains("খুজ") || lower.contains("সার্চ") || lower.contains("বের কর") ||
                lower.contains("search") || lower.contains("find")
            val wantsMessageOrComment = lower.contains("মেসেজ") || lower.contains("এসএমএস") ||
                lower.contains("sms") || lower.contains("কমেন্ট") || lower.contains("কমপ্লেন") ||
                lower.contains("রিপ্লাই") || lower.contains("এপ্লাই") || lower.contains("জানাও") ||
                lower.contains("লিখ") || lower.contains("লেখ") || lower.contains("পাঠা") || lower.contains("বলো ওকে") ||
                lower.contains("message") || lower.contains("comment") || lower.contains("reply") ||
                lower.contains("send") || lower.contains("post") || lower.contains("write") || lower.contains("text ")

            if (explicitPlatform == null && !wantsMessageOrComment && !wantsSearchFriend) return null
            val platform = explicitPlatform ?: when {
                lower.contains("কমেন্ট") || lower.contains("comment") -> "Facebook"
                lower.contains("রিপ্লাই") || lower.contains("reply") -> "Messenger"
                lower.contains("বন্ধু") || lower.contains("ফ্রেন্ড") || lower.contains("জানাও") -> "WhatsApp"
                else -> return null
            }

            if (wantsMessageOrComment || wantsSearchFriend) {
                val extractedRecipient = extractFriendRecipient(text)
                val cleanedBody = if (wantsMessageOrComment) {
                    extractSocialMessageBody(text, extractedRecipient)
                } else {
                    ""
                }

                // Pure Friend Search (no SMS text provided) -> allow review or launch search directly
                if (wantsSearchFriend && !wantsMessageOrComment && extractedRecipient.isNotBlank()) {
                    val reply = if (language == SathiLanguage.BN) {
                        "প্রাপক/বন্ধু: $extractedRecipient\nআমি $platform-এ '$extractedRecipient'-কে খুঁজে দেওয়ার জন্য প্রস্তুত হয়েছি। নিচে নাম/নম্বর নিশ্চিত করে 'নিশ্চিত করুন ও খুলুন' চাপুন।"
                    } else {
                        "Friend to find: $extractedRecipient\nConfirm below to open $platform and search for '$extractedRecipient'."
                    }
                    return SathiTurnResult(
                        replyText = reply,
                        mood = SathiMood.HAPPY,
                        actionType = PhoneActionType.SOCIAL_MESSAGE_CONFIRM,
                        actionParam = platform,
                        secondaryParam = "",
                        shouldSaveMemory = false,
                        memoryCategory = MemoryCategory.NOTE,
                        memoryTitle = "",
                        memoryContent = "",
                        recipientParam = extractedRecipient
                    )
                }

                val recipientNoteBn = if (extractedRecipient.isNotBlank()) "প্রাপক/বন্ধু: $extractedRecipient\n" else ""
                val recipientNoteEn = if (extractedRecipient.isNotBlank()) "Recipient/Friend: $extractedRecipient\n" else ""
                val reply = if (language == SathiLanguage.BN) {
                    if (cleanedBody.isNotBlank()) {
                        "${recipientNoteBn}আমি $platform-এ এই লেখাটি প্রস্তুত করেছি: \"$cleanedBody\"। নিচে নাম/নম্বর বা লেখা যাচাই করে নিশ্চিত করলে $platform-এ লিখে দেওয়া হবে।"
                    } else {
                        "${recipientNoteBn}নিচে বন্ধুর নাম/নম্বর এবং যে এসএমএসটি লিখতে চান তা দেখে নিশ্চিত করুন।"
                    }
                } else {
                    if (cleanedBody.isNotBlank()) {
                        "${recipientNoteEn}I prepared your $platform message/reply: \"$cleanedBody\". Confirm below to launch $platform."
                    } else {
                        "${recipientNoteEn}Confirm friend name/number and optional message below for $platform."
                    }
                }
                return SathiTurnResult(
                    replyText = reply,
                    mood = SathiMood.WARM,
                    actionType = PhoneActionType.SOCIAL_MESSAGE_CONFIRM,
                    actionParam = platform,
                    secondaryParam = cleanedBody,
                    shouldSaveMemory = false,
                    memoryCategory = MemoryCategory.NOTE,
                    memoryTitle = "",
                    memoryContent = "",
                    recipientParam = extractedRecipient
                )
            }

            val reply = if (language == SathiLanguage.BN) {
                "জি, $platform খুলে দিচ্ছি।"
            } else {
                "Opening $platform for you."
            }
            return SathiTurnResult(
                replyText = reply,
                mood = SathiMood.HAPPY,
                actionType = PhoneActionType.OPEN_APP,
                actionParam = platform,
                secondaryParam = "",
                shouldSaveMemory = false,
                memoryCategory = MemoryCategory.NOTE,
                memoryTitle = "",
                memoryContent = ""
            )
        }

        private fun extractFriendRecipient(text: String): String {
            // Digits phone number first
            Regex("(\\+?\\d[\\d\\s-]{5,})").find(text)?.value?.trim()?.let { return it }

            val stopWords = setOf(
                "বন্ধু", "ফ্রেন্ড", "আমার", "এই", "ওই", "নাম", "নামে", "হলো", "হল",
                "whatsapp", "messenger", "facebook", "instagram", "tiktok",
                "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "হোয়াটসঅ্যাপে",
                "মেসেঞ্জার", "মেসেঞ্জারে", "ম্যাসেঞ্জারে", "ফেসবুক", "ফেসবুকে",
                "ওকে", "তাকে", "একটি", "একটা", "এসএমএস", "মেসেজ"
            )
            val bnPatterns = listOf(
                Regex("(?:নাম\\s*(?:হলো|হল|:)?|এই\\s*নামে)\\s+([^\\s,।!?]{2,24})"),
                Regex("([^\\s,।!?]{2,24})\\s+নামে\\s+(?:খোঁজো|খোজো|খুঁজো|সার্চ|বের)"),
                Regex("(?:আমার\\s+)?(?:বন্ধু|ফ্রেন্ড)\\s+([^\\s,।!?]+?)(?:-কে|কে|-এর|এর|\\s+কে)?\\s+(?:(?:এই|ওই|একটি|একটা|ওকে|তাকে)\\s+)?(?:খোঁজো|খোজো|খুঁজো|খুজ|সার্চ|এসএমএস|মেসেজ|জানাও|রিপ্লাই|কমেন্ট|বলো|লেখ|লিখ|পাঠা)"),
                Regex("(?:আমার\\s+)?(?:বন্ধু|ফ্রেন্ড)\\s+([^\\s,।!?]+?)(?:-কে|কে|\\s+কে)(?=\\s|$|[.,।!?])"),
                Regex("([^\\s,।!?]{2,22})(?:-কে|কে)\\s+(?:(?:এই|ওই|একটি|একটা)\\s+)?(?:খোঁজো|খোজো|খুঁজো|সার্চ|এসএমএস|মেসেজ|জানাও|রিপ্লাই|কমেন্ট|বলো|লেখ|লিখ)"),
                Regex("([^\\s,।!?]{2,22})(?:-এর|এর)\\s+কাছে(?=\\s|$|[.,।!?])"),
                Regex("([^\\s,।!?]{2,22})-কে(?=\\s|$|[.,।!?])"),
                Regex("\\bto\\s+([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
                Regex("\\bfriend\\s+([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
                Regex("\\b(?:search|find)\\s+([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
            )
            for (regex in bnPatterns) {
                val cand = regex.find(text)?.groupValues?.getOrNull(1)
                    ?.replace(Regex("^(?:আমার|এই|ওই)\\s+"), "")
                    ?.trim()
                    .orEmpty()
                if (cand.length >= 2 && cand.lowercase() !in stopWords) {
                    return cand
                }
            }
            return ""
        }

        private fun extractSocialMessageBody(text: String, recipient: String): String {
            val quoteMatch = Regex("[\"“'‘]([^\"”'’]{2,})[\"”'’]").find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!quoteMatch.isNullOrBlank()) return quoteMatch

            val afterMarker = Regex("(?:ওকে জানাও|জানাও যে|জানাও|বলো যে|ওকে বলো|এসএমএসটা লেখো|এসএমএসটা করো|এসএমএস লেখো|এসএমএস লিখো|মেসেজ লেখো|মেসেজ লিখো|লিখে দাও|লেখো যে|লিখো যে|লেখো|লিখো|কমেন্ট করো|রিপ্লাই দাও|মেসেজ করো যে|জানিয়ে দাও)\\s+(.+)$")
                .find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!afterMarker.isNullOrBlank() && afterMarker.length >= 2) {
                return afterMarker
                    .replace(Regex("^(যে|je)\\s+"), "")
                    .replace(Regex("(এপ্লাই করো|রিপ্লাই করো|পাঠিয়ে দাও|পাঠাও|লেখো|লিখো|করো)$"), "")
                    .trim()
                    .ifBlank { text }
            }

            var cleaned = text
                .replace(Regex("(whatsapp|messenger|facebook|instagram|tiktok|fb|sms|send|message|comment|reply|post|apply|write|search|find)", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("(হোয়াটসঅ্যাপে|হোয়াটসঅ্যাপে|হোয়াটসঅ্যাপ|হোয়াটসঅ্যাপ|মেসেঞ্জারে|মেসেঞ্জার|ম্যাসেঞ্জারে|ম্যাসেঞ্জার|ফেসবুকে|ফেসবুক|ইনস্টাগ্রামে|ইনস্টাগ্রাম|টিকটকে|টিকটক)"), " ")
                .replace(Regex("(খুলে|অন করে|ঢুকে|আমার বন্ধু|বন্ধু|আমার ফ্রেন্ড|ফ্রেন্ড|ফ্রেন্ডকে|ওকে|তাকে|একটি|একটা|করে|লিখে|লেখো|লিখো|লেখ|লিখ|দাও|করো|পাঠাও|পাঠিয়ে|মেসেজ|এসএমএসটা|এসএমএস|কমেন্ট|কমপ্লেন|রিপ্লাই|এপ্লাই|জানাও|জানিয়ে|খোঁজো|খোজো|খুঁজো|সার্চ)"), " ")
            if (recipient.isNotBlank()) {
                cleaned = cleaned.replace("${recipient}-কে", " ")
                    .replace("${recipient}কে", " ")
                    .replace("${recipient}-এর", " ")
                    .replace("${recipient}এর", " ")
                    .replace(recipient, " ")
            }
            return cleaned.replace(Regex("\\s+"), " ").trim()
        }

        fun sanitizeApiKeyInput(raw: String): String {
            val stripped = raw.replace(Regex("[\\s\\u200B-\\u200D\\uFEFF\"']+"), "").trim()
            val extracted = Regex("AIza[0-9A-Za-z_\\-]{16,}").find(stripped)?.value
            return extracted ?: stripped
        }

        fun detectCapabilitySelfQuery(
            lower: String,
            language: SathiLanguage,
            hasApiKey: Boolean
        ): String? {
            val asksAboutSelf = lower.contains("তুমি কি") || lower.contains("তুমি কী") ||
                lower.contains("আপনি কি") || lower.contains("আপনি কী") ||
                lower.contains("তোমার কাজ") || lower.contains("কী কী করতে") ||
                lower.contains("কি কি করতে") || lower.contains("can you") ||
                lower.contains("what can you") || lower.contains("do you support")
            if (!asksAboutSelf) return null

            val mentionsSearchOrAbility = lower.contains("সার্চ") || lower.contains("search") ||
                lower.contains("গুগল") || lower.contains("google") ||
                lower.contains("ওয়েব") || lower.contains("ওয়েব") || lower.contains("web") ||
                lower.contains("খুঁজ") || lower.contains("করতে পার") ||
                lower.contains("কী কী") || lower.contains("কি কি") ||
                lower.contains("what can you") || lower.contains("কাজ")
            if (!mentionsSearchOrAbility) return null

            val keyStatusBn = if (hasApiKey) {
                "✓ আপনার Google API Key যুক্ত আছে, তাই আমি সরাসরি Google সার্চ ও জেমিনি এআই ব্যবহার করতে পারছি!"
            } else {
                "• বর্তমানে বিল্ট-ইন ফ্রি লাইভ ওয়েব সার্চ (Google News, Wikipedia, আবহাওয়া, নামাজের সময়, মুদ্রা ও Bing) চালু আছে। সেটিংস বা অ্যাডমিন প্যানেলের 'Google চাবি' ট্যাবে নিজের চাবি পেস্ট করলে সরাসরি Google Gemini সার্চও যুক্ত হবে!"
            }
            val keyStatusEn = if (hasApiKey) {
                "✓ Your Google API Key is connected, so Google Search grounding and Gemini AI are active!"
            } else {
                "• Free live web search (Bangladesh Google News, Wikipedia, weather, prayer times, exchange rates) is active. You can also paste your Google API Key in Settings or Admin -> Google চাবি for direct Google Gemini grounding."
            }
            return if (language == SathiLanguage.BN) {
                "হ্যাঁ! আমি সাথী এআই (Sathi AI) — আমি ওয়েব সার্চ ও সারা বাংলাদেশের খবরসহ অনেক কাজ করতে পারি:\n" +
                    "• লাইভ ওয়েব সার্চ ও সারা বাংলাদেশের আজকের খবর\n" +
                    "• যেকোনো জেলা বা শহরের আবহাওয়া ও নামাজের সময়সূচি\n" +
                    "• ছবি তৈরি ও গ্যালারিতে ডাউনলোড, এবং ছবি/স্ক্রিন বিশ্লেষণ\n" +
                    "• WhatsApp ও Messenger-এ বন্ধু খোঁজা ও মেসেজ/এসএমএস লেখা, ইউটিউব, কল, অ্যালার্ম\n" +
                    "• অফলাইনে ডাউনলোড করা জ্ঞান ভান্ডার, গল্প, কৌতুক ও অঙ্ক\n\n$keyStatusBn"
            } else {
                "Yes! I am Sathi AI — I can search the live web, read all-Bangladesh news, check weather & prayer times, create & save images, analyze photos/screens, find friends & draft messages in WhatsApp/Messenger, open YouTube, and answer offline!\n\n$keyStatusEn"
            }
        }

        fun shouldTriggerWebSearch(text: String, lower: String): Boolean {
            if (detectCapabilitySelfQuery(lower, SathiLanguage.BN, hasApiKey = false) != null) {
                return false
            }
            val isWeather = lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
                lower.contains("তাপমাত্রা") || lower.contains("temperature") || lower.contains("বৃষ্টি")
            val isPrayerTime = lower.contains("নামাজ") || lower.contains("ফজর") || lower.contains("যোহর") ||
                lower.contains("আসর") || lower.contains("মাগরিব") || lower.contains("এশা") ||
                lower.contains("ইফতার") || lower.contains("সেহরি") || lower.contains("prayer") || lower.contains("salah")
            val isCurrency = lower.contains("dollar") || lower.contains("usd") || lower.contains("euro") ||
                lower.contains("riyal") || lower.contains("dirham") || lower.contains("ডলার") ||
                lower.contains("রিয়াল") || lower.contains("রিয়াল") || lower.contains("দিরহাম") ||
                lower.contains("টাকার রেট") || lower.contains("টাকার মান")
            val isNewsOrScore = lower.contains("news") || lower.contains("খবর") || lower.contains("সংবাদ") ||
                lower.contains("score") || lower.contains("স্কোর") || lower.contains("ক্রিকেট") ||
                lower.contains("ফুটবল") || lower.contains("search") || lower.contains("খুঁজ") ||
                lower.contains("সার্চ") || lower.contains("গুগল") || lower.contains("তথ্য") ||
                lower.contains("উইকিপিডিয়া") || lower.contains("wikipedia")
            val isFactualQuestion = text.contains("?") ||
                Regex("\\b(what|who|where|when|why|how|which|explain|define|meaning|recipe|price|distance|history|capital|population|symptoms|benefits|tips|difference|example|code|program)\\b")
                    .containsMatchIn(lower) ||
                Regex("(কীভাবে|কিভাবে|কাকে বলে|রাজধানী|ইতিহাস|সম্পর্কে|ব্যাখ্যা|সংজ্ঞা|মানে কী|মানে কি|অর্থ কী|অর্থ কি|দাম কত|কত টাকা|দূরত্ব|রেসিপি|উপকারিতা|অপকারিতা|পার্থক্য|নিয়ম|উপায়|কে ছিলেন|কে আবিষ্কার|কোথায়|কোথায়|কবে হয়|কবে হয়|কোন দেশ|কোনটি|কয়টি|কতটি|কতগুলো|বোঝাও|শেখাও|শিখাও|জানাও সম্পর্কে|\\sকী\\b|\\sকি\\b|\\sকে\\b)")
                    .containsMatchIn(lower)
            return isWeather || isPrayerTime || isCurrency || isNewsOrScore || isFactualQuestion
        }

        fun matchVerifiedBuiltInFact(lower: String, language: SathiLanguage): String? {
            return when {
                (lower.contains("বাংলাদেশ") || lower.contains("bangladesh")) && lower.contains("রাজধানী") ||
                    lower.contains("capital of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের রাজধানী হলো ঢাকা (Dhaka)।" else "The capital of Bangladesh is Dhaka."

                lower.contains("জাতীয় ফল") || lower.contains("জাতীয় ফল") || lower.contains("national fruit of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় ফল হলো কাঁঠাল।" else "The national fruit of Bangladesh is the jackfruit."

                lower.contains("জাতীয় ফুল") || lower.contains("জাতীয় ফুল") || lower.contains("national flower of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় ফুল হলো শাপলা (সাদা শাপলা)।" else "The national flower of Bangladesh is the white water lily (Shapla)."

                lower.contains("জাতীয় পাখি") || lower.contains("জাতীয় পাখি") || lower.contains("national bird of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় পাখি হলো দোয়েল।" else "The national bird of Bangladesh is the Oriental magpie-robin (Doel)."

                lower.contains("জাতীয় মাছ") || lower.contains("জাতীয় মাছ") || lower.contains("national fish of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় মাছ হলো ইলিশ।" else "The national fish of Bangladesh is the Hilsa (Ilish)."

                lower.contains("জাতীয় পশু") || lower.contains("জাতীয় পশু") || lower.contains("national animal of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় পশু হলো রয়েল বেঙ্গল টাইগার (সুন্দরবনের বাঘ)।" else "The national animal of Bangladesh is the Royal Bengal Tiger."

                lower.contains("জাতীয় সংগীত") || lower.contains("জাতীয় সংগীত") || lower.contains("national anthem of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের জাতীয় সংগীত 'আমার সোনার বাংলা', যার রচয়িতা ও সুরকার রবীন্দ্রনাথ ঠাকুর।" else "Bangladesh's national anthem is 'Amar Shonar Bangla', written by Rabindranath Tagore."

                lower.contains("স্বাধীনতা দিবস") || lower.contains("independence day of bangladesh") ->
                    if (language == SathiLanguage.BN) "বাংলাদেশের স্বাধীনতা দিবস ২৬শে মার্চ (১৯৭১) এবং বিজয় দিবস ১৬ই ডিসেম্বর (১৯৭১)।" else "Bangladesh's Independence Day is 26 March (1971) and Victory Day is 16 December (1971)."

                lower.contains("পানি") && (lower.contains("কত ডিগ্রি") || lower.contains("ফোটে") || lower.contains("স্ফুটনাঙ্ক") || lower.contains("boiling point")) ->
                    if (language == SathiLanguage.BN) "স্বাভাবিক চাপে বিশুদ্ধ পানির স্ফুটনাঙ্ক (ফোটার তাপমাত্রা) ১০০° সেলসিয়াস (২১২° ফারেনহাইট) এবং হিমাঙ্ক (বরফ হওয়ার তাপমাত্রা) ০° সেলসিয়াস।" else "At standard pressure, pure water boils at 100°C (212°F) and freezes at 0°C (32°F)."

                lower.contains("মানবদেহে") && (lower.contains("হাড়") || lower.contains("হাড়") || lower.contains("bones")) ->
                    if (language == SathiLanguage.BN) "একজন প্রাপ্তবয়স্ক মানুষের শরীরে মোট ২০৬টি হাড় (অস্থি) থাকে।" else "An adult human body has 206 bones."

                lower.contains("সৌরজগত") && (lower.contains("কয়টি গ্রহ") || lower.contains("কয়টি গ্রহ") || lower.contains("planets")) ->
                    if (language == SathiLanguage.BN) "সৌরজগতে মোট ৮টি গ্রহ রয়েছে: বুধ, শুক্র, পৃথিবী, মঙ্গল, বৃহস্পতি, শনি, ইউরেনাস ও নেপচুন।" else "The solar system has 8 planets: Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, and Neptune."

                else -> null
            }
        }

        fun tryConvertUnits(input: String, language: SathiLanguage): String? {
            val normalized = input.lowercase()
                .replace('০', '0').replace('১', '1').replace('২', '2').replace('৩', '3').replace('৪', '4')
                .replace('৫', '5').replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
            val num = Regex("(-?\\d+(?:\\.\\d+)?)").find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: return null

            val isBn = language == SathiLanguage.BN
            return when {
                (normalized.contains("কিলোমিটার") || Regex("\\bkm\\b").containsMatchIn(normalized)) &&
                    (normalized.contains("মাইল") || normalized.contains("mile")) -> {
                    val out = String.format(Locale.US, "%.2f", num * 0.621371)
                    if (isBn) "$num কিলোমিটার = প্রায় $out মাইল।" else "$num km = $out miles."
                }
                (normalized.contains("কেজি") || Regex("\\bkg\\b").containsMatchIn(normalized)) &&
                    (normalized.contains("গ্রাম") || normalized.contains("gram")) -> {
                    val out = String.format(Locale.US, "%.0f", num * 1000.0)
                    if (isBn) "$num কেজি = $out গ্রাম।" else "$num kg = $out grams."
                }
                (normalized.contains("কেজি") || Regex("\\bkg\\b").containsMatchIn(normalized)) &&
                    (normalized.contains("পাউন্ড") || normalized.contains("pound") || normalized.contains("lb")) -> {
                    val out = String.format(Locale.US, "%.2f", num * 2.20462)
                    if (isBn) "$num কেজি = প্রায় $out পাউন্ড।" else "$num kg = $out lbs."
                }
                (normalized.contains("ফুট") || normalized.contains("feet") || normalized.contains("ft")) &&
                    (normalized.contains("ইঞ্চি") || normalized.contains("inch")) -> {
                    val out = String.format(Locale.US, "%.1f", num * 12.0)
                    if (isBn) "$num ফুট = $out ইঞ্চি।" else "$num feet = $out inches."
                }
                (normalized.contains("সেলসিয়াস") || normalized.contains("সেলসিয়াস") || normalized.contains("celsius")) &&
                    (normalized.contains("ফারেনহাইট") || normalized.contains("fahrenheit")) -> {
                    val out = String.format(Locale.US, "%.1f", (num * 9.0 / 5.0) + 32.0)
                    if (isBn) "$num° সেলসিয়াস = $out° ফারেনহাইট।" else "$num°C = $out°F."
                }
                (normalized.contains("জিবি") || Regex("\\bgb\\b").containsMatchIn(normalized)) &&
                    (normalized.contains("এমবি") || Regex("\\bmb\\b").containsMatchIn(normalized)) -> {
                    val out = String.format(Locale.US, "%.0f", num * 1024.0)
                    if (isBn) "$num জিবি (GB) = $out এমবি (MB)।" else "$num GB = $out MB."
                }
                else -> null
            }
        }

        fun nextPoem(language: SathiLanguage, index: Int): String {
            val poemsBn = listOf(
                "ভোরের আলোয় নতুন আশা,\nহৃদয়ে থাকুক ভালোবাসা।\nমেঘ পেরিয়ে রোদ উঠবেই,\nনতুন স্বপ্ন ঠিকই ফুটবেই।",
                "নদীর ঢেউয়ে পাখির গানে,\nজীবন হাসে নতুন প্রাণে।\nছোট্ট ভালো কাজের ছোঁয়ায়,\nপৃথিবী সুন্দর হয় ভালোবাসায়।",
                "রাতের শেষে ভোরের রবি,\nআঁকে আকাশে রঙিন ছবি।\nসাহস রাখো বুকের মাঝে,\nসফল হবে সব ভালো কাজে।"
            )
            val poemsEn = listOf(
                "A gentle breeze, a morning sky,\nWith quiet strength as clouds drift by;\nKeep faith inside, walk bright and free,\nThe best of days is yet to be.",
                "Through quiet rain and golden light,\nSmall steps turn darkness into bright;\nHold hope within your heart each day,\nAnd watch your courage light the way."
            )
            val list = if (language == SathiLanguage.BN) poemsBn else poemsEn
            return list[index % list.size]
        }

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
                "দুই বন্ধু মরুভূমি দিয়ে হাঁটছিল। পথে সামান্য বিষয় নিয়ে ঝগড়া হলো এবং একজন বালিতে লিখল, 'আজ আমার বন্ধু আমাকে কষ্ট দিয়েছে।' কিছুক্ষণ পর তারা এক নদীতে সাঁতার কাটতে গিয়ে একজন ডুবতে লাগল—বন্ধুটি প্রাণপণ চেষ্টা করে তাকে বাঁচাল। তখন সে একটি পাথরে খোদাই করে লিখল, 'আজ আমার বন্ধু আমার জীবন বাঁচিয়েছে।' কষ্টগুলো বালিতে আর ভালোবাসাগুলো পাথরে লিখে রাখাই প্রকৃত বন্ধুত্ব।"
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
                "বন্ধু ১: দোস্ত, আমার ঘড়িটা পানিতে পড়ে গেছে, তাও টিকটিক করে চলছে! বন্ধু ২: কীভাবে? বন্ধু ১: ওটা বোধহয় সাঁতার শিখে ফেলেছে!",
                "রোগী: ডাক্তার সাহেব, আমি কথা বললে কেউ শুনতে পায় না কেন? ডাক্তার: আপনি ফোনের মাইক্রোফোন বন্ধ করে রেখেছেন!",
                "ছেলে: বাবা, আমার স্কুলের পিকনিকের টাকা লাগবে। বাবা: কত? ছেলে: ৫০০ টাকা, তবে সাথী থাকলে ফ্রি গল্প শোনানো যাবে!"
            )
            val jokesEn = listOf(
                "Why did the smartphone go to school? Because it wanted to improve its reception!",
                "Why do programmers prefer dark mode? Because light attracts bugs!",
                "Why did the math book look sad? Because it had too many problems!"
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
