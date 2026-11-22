package com.example.data

import com.example.model.MemoryCategory
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood

class OfflineCompanionEngine(
    private val packManager: OfflinePackManager? = null
) {
    private var storyCursor = 0
    private var jokeCursor = 0
    private var chatCursor = 0

    fun respondOffline(
        input: String,
        language: SathiLanguage,
        userName: String
    ): SathiTurnResult {
        val text = input.trim()
        val lower = text.lowercase()

        if (lower.startsWith("থামো") || lower == "থাম" || lower.startsWith("stop")) {
            val reply = if (language == SathiLanguage.BN) {
                "জি, আমি থেমে গেলাম $userName! বলুন এখন কী শুনতে বা করতে চান?"
            } else {
                "Okay, I stopped! What would you like me to do next?"
            }
            return turn(reply, SathiMood.WARM)
        }

        // 1. Creator rule (scoped so 'ছবি তৈরি' etc. are not misrouted)
        if (lower.contains("বানিয়ে") || lower.contains("বানিয়ে") || lower.contains("বানাইছে") ||
            lower.contains("নির্মাতা") ||
            lower.contains("কে তৈরি") || lower.contains("কার তৈরি") || lower.contains("তোমাকে তৈরি") || lower.contains("তোমারে তৈরি") ||
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

        // 4. Verified built-in facts, Downloaded Knowledge Pack, Unit Converter & Math
        SathiAiService.tryEvaluateMath(text, language)?.let { mathReply ->
            return turn(mathReply, SathiMood.SERIOUS)
        }
        SathiAiService.tryConvertUnits(text, language)?.let { unitReply ->
            return turn(unitReply, SathiMood.SERIOUS)
        }
        SathiAiService.matchVerifiedBuiltInFact(lower, language)?.let { factReply ->
            return turn(factReply, SathiMood.SERIOUS)
        }

        // Check offline pack BEFORE declining live search so stored encyclopedia facts always answer offline!
        packManager?.searchOfflineKnowledge(text, language)?.let { packHit ->
            return turn(packHit, SathiMood.SERIOUS)
        }

        // 5. Image generation preview routing (shows clear Sample Preview card even offline)
        SathiAiService.detectImageGenerationPrompt(text, lower)?.let { imgPrompt ->
            val reply = if (language == SathiLanguage.BN) {
                "\"$imgPrompt\"-এর জন্য নমুনা ছবি প্রিভিউ তৈরি করছি..."
            } else {
                "Creating a sample preview image for \"$imgPrompt\"..."
            }
            return turn(reply, SathiMood.HAPPY, PhoneActionType.GENERATE_IMAGE, imgPrompt)
        }

        // 6. Social Apps & Friend WhatsApp/Messenger SMS / Comment / Reply offline
        SathiAiService.detectSocialActionOrOpen(text, lower, language)?.let { socialTurn ->
            return socialTurn
        }

        // Open YouTube, Settings, or other phone apps offline
        if (lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.contains("খোলো") || lower.contains("খুলো") || lower.contains("চালু করো") ||
            lower.contains("ঢোকো") || lower.contains("ঢোকাও") || lower.contains("ঢুকো")
        ) {
            val offlineAppTarget = text
                .replace(Regex("(open|launch)", RegexOption.IGNORE_CASE), "")
                .replace("খোলো", "")
                .replace("খুলো", "")
                .replace("চালু করো", "")
                .replace("ঢোকো", "")
                .replace("ঢোকাও", "")
                .replace("ঢুকো", "")
                .replace("অ্যাপ", "")
                .trim()
            if (offlineAppTarget.isNotBlank()) {
                val reply = if (language == SathiLanguage.BN) {
                    "জি, ফোনে $offlineAppTarget অ্যাপটি খুলে দিচ্ছি।"
                } else {
                    "Opening $offlineAppTarget for you."
                }
                return turn(reply, SathiMood.WARM, PhoneActionType.OPEN_APP, offlineAppTarget)
            }
        }

        // 7. Flashlight ON / OFF & Volume
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

        if (lower.contains("volume up") || (lower.contains("ভলিউম") && (lower.contains("বাড়া") || lower.contains("বাড়া") || lower.contains("বেশি")))) {
            val reply = if (language == SathiLanguage.BN) "জি, অফলাইনে ভলিউম বাড়িয়ে দিচ্ছি।" else "Increasing media volume offline."
            return turn(reply, SathiMood.WARM, PhoneActionType.VOLUME_UP)
        }
        if (lower.contains("volume down") || (lower.contains("ভলিউম") && (lower.contains("কমা") || lower.contains("কম")))) {
            val reply = if (language == SathiLanguage.BN) "জি, অফলাইনে ভলিউম কমিয়ে দিচ্ছি।" else "Lowering media volume offline."
            return turn(reply, SathiMood.WARM, PhoneActionType.VOLUME_DOWN)
        }

        // 8. Alarm / Timer / Call
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
            val digits = Regex("(\\+?\\d[\\d\\s-]{4,})").find(text)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "অফলাইন কল করার আগে আপনার অনুমতি নিচ্ছি। নিচের বক্সে নিশ্চিত করুন।"
            } else {
                "Please confirm below before I open the dialer offline."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.CALL_CONFIRM, digits)
        }

        // 9. Stories, Jokes, Poems offline!
        if (lower.contains("গল্প") || lower.contains("কাহিনী") || lower.contains("কাহিনি") || lower.contains("story")) {
            val story = SathiAiService.nextStory(language, storyCursor++, text)
            return turn(story, SathiMood.HAPPY)
        }
        if (lower.contains("কৌতুক") || lower.contains("জোক") || lower.contains("joke") || lower.contains("ধাঁধা")) {
            return turn(SathiAiService.nextJoke(language, jokeCursor++), SathiMood.PLAYFUL)
        }
        if (lower.contains("কবিতা") || lower.contains("poem")) {
            return turn(SathiAiService.nextPoem(language, jokeCursor++), SathiMood.WARM)
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
                "আসসালামু আলাইকুম $userName! আমি আপনার অফলাইন সাথী। আলহামদুলিল্লাহ ভালো আছি — গল্প, কৌতুক, একক রূপান্তর, সাধারণ জ্ঞান বা ফোন কন্ট্রোলের জন্য আমাকে বলুন!"
            } else {
                "Assalamu Alaikum $userName! I am Sathi in offline mode. Ask me for stories, jokes, unit conversions, saved knowledge, or phone actions!"
            }
            return turn(reply, SathiMood.HAPPY)
        }

        // 12. Live web queries gracefully explained offline + suggest custom offline download
        if (SathiAiService.shouldTriggerWebSearch(text, lower)) {
            val savedPreview = packManager?.getAvailableTopicsPreview(5).orEmpty()
            val previewHintBn = if (savedPreview.isNotEmpty()) {
                "\n\nবর্তমান অফলাইন প্যাকে সংরক্ষিত কিছু বিষয়: ${savedPreview.joinToString(", ")}। চাইলে সেটিংস থেকে যেকোনো নতুন বিষয়ও অফলাইনে ডাউনলোড করে রাখতে পারবেন!"
            } else {
                "\n\nসেটিংস → 'অফলাইন প্যাক আপডেট' দিলে আরও অনেক বিষয় অফলাইনেই জানতে পারবেন।"
            }
            val reply = if (language == SathiLanguage.BN) {
                "আমি এখন অফলাইন মোডে আছি, তাই লাইভ ওয়েব সার্চের জন্য ইন্টারনেট প্রয়োজন।$previewHintBn"
            } else {
                "I'm currently in Offline Mode, so live web search needs internet. You can download custom topics or update the Offline Pack in Settings to answer even more offline!"
            }
            return turn(reply, SathiMood.WARM)
        }

        // Varied, informative offline replies
        val variedBn = listOf(
            "আমি মন দিয়ে শুনছি, $userName। অফলাইনে আমাকে গল্প, কবিতা, কৌতুক, একক রূপান্তর (যেমন: ৫ কেজি কত গ্রাম), সাধারণ জ্ঞান বা অ্যালার্ম-ফ্ল্যাশলাইট চালাতে বলতে পারেন।",
            "দারুণ কথা বলেছেন, $userName! আপনি চাইলে সেটিংস থেকে আপনার পছন্দের যেকোনো বিষয় অফলাইন প্যাকে যুক্ত করতে পারবেন—তাহলে ইন্টারনেট ছাড়াও আমি আরও বেশি বলতে পারব।",
            "জি $userName, আমি বুঝেছি। আপনার কি একটু মজার গল্প শুনতে ভালো লাগবে, নাকি ডাউনলোড করা তথ্য বা সময় জানতে চান?"
        )
        val variedEn = listOf(
            "I hear you, $userName! Even offline, you can ask for stories, poems, jokes, unit conversions, quick math, alarms, or saved knowledge.",
            "Thanks for sharing that, $userName. You can add custom topics in Settings -> Offline Pack so I know even more without internet!",
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
