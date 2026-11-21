package com.example.data

import com.example.model.MemoryCategory
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood

class OfflineCompanionEngine(
    private val packManager: OfflinePackManager? = null
) {
    private var storyCursor = 0
    private var chatCursor = 0

    fun respondOffline(
        input: String,
        language: SathiLanguage,
        userName: String
    ): SathiTurnResult {
        val lower = input.lowercase().trim()

        if (lower.startsWith("থামো") || lower == "থাম" || lower.startsWith("stop")) {
            val reply = if (language == SathiLanguage.BN) {
                "জি, আমি থেমে গেলাম $userName! বলুন এখন কী শুনতে বা করতে চান?"
            } else {
                "Okay, I stopped! What would you like me to do next?"
            }
            return turn(reply, SathiMood.WARM)
        }

        // 1. Creator rule
        if (lower.contains("বানিয়ে") || lower.contains("বানাইছে") || lower.contains("তৈরি") ||
            lower.contains("who made") || lower.contains("created you") || lower.contains("creator")
        ) {
            return turn(
                reply = "আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান। অফলাইনেও আমি আপনার সাথে আছি!",
                mood = SathiMood.WARM
            )
        }

        // 2. Identity & Name
        if (lower.contains("তোমার নাম") || lower.contains("আপনার নাম") || lower.contains("what is your name") ||
            lower.contains("your name") || lower == "তুমি কে" || lower == "তুমি কে?" ||
            lower == "আপনি কে" || lower.contains("who are you")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম! আমার নাম সাথী (Sathi), আপনার ব্যক্তিগত এআই বন্ধু। আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।"
            } else {
                "Assalamu Alaikum! My name is Sathi, your personal AI companion created by Mohammad Bayzid Hasan."
            }
            return turn(reply, SathiMood.HAPPY)
        }

        // 3. Time / Date / Day (works accurately 100% offline!)
        if (SathiAiService.isTimeOrDateQuery(lower)) {
            return turn(SathiAiService.formatCurrentTimeAndDate(language), SathiMood.WARM)
        }

        // 4. Web search politely declined offline
        if (lower.contains("search") || lower.contains("খবর") || lower.contains("news") ||
            lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
            lower.contains("score") || lower.contains("স্কোর") || lower.contains("দাম") || lower.contains("youtube")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আমি এখন অফলাইন মোডে আছি, তাই লাইভ আবহাওয়া, খবর বা ইউটিউব দেখতে ইন্টারনেট সংযোগ প্রয়োজন। তবে গল্প, সাধারণ জ্ঞান, সময়-তারিখ, অ্যালার্ম, টাইমার, ফ্ল্যাশলাইট বা কল এখনই কাজ করবে।"
            } else {
                "I'm in Offline Mode, so live weather, news, and YouTube need internet. However, I can tell stories, share the time/date, answer saved offline facts, or run alarms, timers, flashlight, and calls!"
            }
            return turn(reply, SathiMood.WARM)
        }

        // 4. Flashlight ON / OFF
        if (lower.contains("flashlight on") || lower.contains("torch on") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("চালু") || lower.contains("জ্বালাও") || lower.contains("অন")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, আমি অফলাইনেই ফ্ল্যাশলাইট চালু করছি।"
            else "Sure, turning on the flashlight offline."
            return turn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_ON)
        }

        if (lower.contains("flashlight off") || lower.contains("torch off") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("বন্ধ") || lower.contains("অফ")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, ফ্ল্যাশলাইট বন্ধ করছি।"
            else "Turning off the flashlight."
            return turn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_OFF)
        }

        // 5. Alarm / Timer / Call
        if (lower.contains("alarm") || lower.contains("অ্যালার্ম")) {
            val timeMatch = Regex("(\\d{1,2})[:.](\\d{2})").find(lower)
            val hourOnly = Regex("(\\d{1,2})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val param = timeMatch?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" }
                ?: hourOnly?.let { "${it.coerceIn(0, 23).toString().padStart(2, '0')}:00" }
                ?: "07:00"
            val reply = if (language == SathiLanguage.BN) {
                "আমি অফলাইনে আপনার জন্য $param সময়ে অ্যালার্ম সেট করছি।"
            } else {
                "Setting an offline alarm for $param."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.SET_ALARM, param, "Sathi Alarm")
        }

        if (lower.contains("timer") || lower.contains("টাইমার")) {
            val num = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("sec") || lower.contains("সেকেন্ড")) num else num * 60
            val reply = if (language == SathiLanguage.BN) {
                "আমি অফলাইনে $seconds সেকেন্ডের টাইমার চালু করছি।"
            } else {
                "Starting an offline timer for $seconds seconds."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.SET_TIMER, seconds.toString(), "Sathi Timer")
        }

        if (lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর")) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{4,})").find(input)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "অফলাইন কল করার আগে আপনার অনুমতি নিচ্ছি। নিচের বক্সে নিশ্চিত করুন।"
            } else {
                "Please confirm below before I open the dialer offline."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.CALL_CONFIRM, digits)
        }

        // 5b. Open social or phone apps offline
        val offlineAppTarget = when {
            lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস") -> "whatsapp"
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") -> "messenger"
            lower.contains("facebook") || lower.contains("ফেসবুক") -> "facebook"
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") -> "instagram"
            lower.contains("tiktok") || lower.contains("টিকটক") -> "tiktok"
            lower.contains("youtube") || lower.contains("ইউটিউব") -> "youtube"
            lower.contains("setting") || lower.contains("সেটিং") -> "settings"
            else -> null
        }
        if (offlineAppTarget != null &&
            (lower.contains("খোলো") || lower.contains("ঢোকো") || lower.contains("চালাও") || lower.contains("open"))
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "জি, ফোনে $offlineAppTarget অ্যাপটি খুলে দিচ্ছি।"
            } else {
                "Opening $offlineAppTarget for you."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.OPEN_APP, offlineAppTarget)
        }

        // 6. Stories offline!
        if (lower.contains("গল্প") || lower.contains("কাহিনী") || lower.contains("story")) {
            val story = SathiAiService.nextStory(language, storyCursor++, input)
            return turn(story, SathiMood.HAPPY)
        }

        // 7. Quick Math offline
        SathiAiService.tryEvaluateMath(input, language)?.let { mathReply ->
            return turn(mathReply, SathiMood.SERIOUS)
        }

        // 8. Check Real Downloaded Offline Knowledge Pack on disk!
        packManager?.searchOfflineKnowledge(input, language)?.let { packHit ->
            return turn(packHit, SathiMood.SERIOUS)
        }

        // 10. Sad / Comforting mood
        if (lower.contains("sad") || lower.contains("মন খারাপ") || lower.contains("কষ্ট") ||
            lower.contains("একা") || lower.contains("lonely") || lower.contains("tired") || lower.contains("ক্লান্ত")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "$userName, মন খারাপ করবেন না। আমি আপনার সাথী—সবসময় আপনার পাশেই আছি। গভীর শ্বাস নিন, কঠিন সময় পেরিয়ে আলো আসবেই।"
            } else {
                "$userName, please don't feel down. Even offline, I am right here with you. Take a slow breath — brighter moments are ahead."
            }
            return turn(reply, SathiMood.COMFORTING)
        }

        // 11. Happy / Greeting
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("হ্যালো") ||
            lower.contains("সালাম") || lower.contains("আসসালামু") || lower.contains("কেমন আছ") || lower.contains("good morning")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম $userName! আমি আপনার অফলাইন সাথী। আলহামদুলিল্লাহ ভালো আছি — আপনি কেমন আছেন?"
            } else {
                "Assalamu Alaikum $userName! I am Sathi in offline mode. I'm doing great — how are you feeling today?"
            }
            return turn(reply, SathiMood.HAPPY)
        }

        // Varied, natural offline replies (never repeats the exact same robotic line!)
        val variedBn = listOf(
            "আমি মন দিয়ে শুনছি, $userName। আপনি চাইলে আমাকে একটি গল্প শোনাতে বলতে পারেন, সহজ অঙ্ক কষতে দিতে পারেন, বা অ্যালার্ম-ফ্ল্যাশলাইট চালাতে বলতে পারেন।",
            "দারুণ কথা বলেছেন, $userName! অফলাইন মোডেও আমি আপনার সাথে আছি—গল্প, সময় দেখা বা যেকোনো ফোন কাজের জন্য আমাকে বলুন।",
            "জি $userName, আমি বুঝেছি। আপনার কি একটু গল্প শুনতে ভালো লাগবে, নাকি ডাউনলোড করা তথ্য বা সময় জানতে চান?"
        )
        val variedEn = listOf(
            "I hear you, $userName! Even offline, you can ask me for a story, quick math, time, alarms, timers, or saved knowledge.",
            "Thanks for sharing that, $userName. Would you like a story, or help with an alarm, timer, or flashlight?",
            "I'm right here beside you offline, $userName. Ask me anything from your downloaded pack or ask for a fresh story!"
        )
        val idx = chatCursor++ % variedBn.size
        val reply = if (language == SathiLanguage.BN) variedBn[idx] else variedEn[idx]
        return turn(reply, SathiMood.WARM)
    }

    private fun turn(
        reply: String,
        mood: SathiMood,
        action: PhoneActionType = PhoneActionType.NONE,
        param: String = "",
        secondary: String = ""
    ) = SathiTurnResult(
        replyText = reply,
        mood = mood,
        actionType = action,
        actionParam = param,
        secondaryParam = secondary,
        shouldSaveMemory = false,
        memoryCategory = MemoryCategory.NOTE,
        memoryTitle = "",
        memoryContent = ""
    )
}
