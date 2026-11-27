package com.example.comm

import com.example.data.SathiAiService
import com.example.model.SathiLanguage
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IncomingCommType(val labelBn: String, val labelEn: String) {
    CALL("ইনকামিং কল", "Incoming Call"),
    MESSAGE("ইনকামিং মেসেজ", "Incoming Message")
}

data class IncomingCommRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: IncomingCommType,
    val appName: String,
    val packageName: String,
    val sender: String,
    val receivedText: String,
    val sathiDialogueReply: String,
    val coreInquirySummary: String,
    val autoRepliedViaSystem: Boolean = false,
    val receivedAtMs: Long = System.currentTimeMillis()
)

/**
 * Coordinates Sathi AI's three-step incoming communication workflow:
 * 1. Receive incoming call or message
 * 2. Engage in polite Bengali dialogue on Boss's behalf
 * 3. Summarize the core inquiry/issue concisely in Bengali for the user ("বস")
 */
object IncomingCommunicationManager {
    private val _summaries = MutableStateFlow<List<IncomingCommRecord>>(emptyList())
    val summaries: StateFlow<List<IncomingCommRecord>> = _summaries.asStateFlow()

    private val _latestVoiceBriefing = MutableStateFlow<IncomingCommRecord?>(null)
    val latestVoiceBriefing: StateFlow<IncomingCommRecord?> = _latestVoiceBriefing.asStateFlow()

    private val recentDedupeKeys = mutableMapOf<String, Long>()
    private val recentBotReplies = mutableMapOf<String, Long>()

    private val messagingPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.instagram.android",
        "org.telegram.messenger"
    )

    private val callPackages = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca"
    )

    fun isMessagingPackage(pkg: String): Boolean {
        val lower = pkg.lowercase().trim()
        return lower in messagingPackages ||
            lower.contains("messaging") ||
            lower.contains("mms") ||
            lower.contains("whatsapp") ||
            lower.contains("telegram")
    }

    fun isCallPackage(pkg: String): Boolean {
        val lower = pkg.lowercase().trim()
        return lower in callPackages || lower.contains("dialer") || lower.contains("incallui")
    }

    fun isSelfEchoReply(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return true
        val now = System.currentTimeMillis()
        recentBotReplies.entries.removeAll { now - it.value > 60_000L }
        if (recentBotReplies.containsKey(clean)) return true
        return clean.contains("বসের ব্যক্তিগত সহকারী সাথী") ||
            clean.contains("আমি সাথী (বসের") ||
            clean.contains("মূল বিষয়টি একটু")
    }

    fun markBotReplySent(replyText: String) {
        val clean = replyText.trim()
        if (clean.isNotEmpty()) {
            recentBotReplies[clean] = System.currentTimeMillis()
        }
    }

    fun buildPoliteDialogueReply(
        sender: String,
        incomingText: String,
        type: IncomingCommType,
        language: SathiLanguage = SathiLanguage.BN
    ): String {
        val cleanSender = sender.trim().ifBlank { if (language == SathiLanguage.BN) "আপনি" else "there" }
        val lower = incomingText.lowercase()
        val alreadyHasDetail = incomingText.trim().length >= 18 &&
            (lower.contains("?") || lower.contains("দরকার") || lower.contains("কথা") ||
                lower.contains("টাকা") || lower.contains("আস") || lower.contains("মিটিং") ||
                lower.contains("সময়") || lower.contains("call") || lower.contains("need") ||
                lower.contains("urgent") || lower.contains("জরুরি"))

        if (language == SathiLanguage.EN) {
            return when {
                type == IncomingCommType.CALL ->
                    "Assalamu Alaikum, this is Sathi, Boss's personal assistant. Please tell me the main reason for your call so I can brief Boss right away."
                alreadyHasDetail ->
                    "Assalamu Alaikum $cleanSender, I am Sathi, Boss's assistant. I received your message and am summarizing the core matter for Boss now. Let me know if anything is urgent."
                else ->
                    "Assalamu Alaikum $cleanSender, I am Sathi, Boss's personal assistant. Could you briefly share your main inquiry so I can pass it to Boss?"
            }
        }

        return when {
            type == IncomingCommType.CALL ->
                "আসসালামু আলাইকুম, আমি বসের ব্যক্তিগত সহকারী সাথী বলছি। আপনার কলের মূল বিষয়টি অনুগ্রহ করে সংক্ষেপে বলুন, আমি এখনই বসকে জানিয়ে দিচ্ছি।"
            alreadyHasDetail ->
                "আসসালামু আলাইকুম $cleanSender, আমি বসের ব্যক্তিগত সহকারী সাথী বলছি। আপনার বার্তাটি পেয়েছি এবং মূল বিষয়টি বসকে জানাচ্ছি। জরুরি কোনো তথ্য থাকলে এখানেই লিখে দিন।"
            else ->
                "আসসালামু আলাইকুম $cleanSender, আমি বসের ব্যক্তিগত সহকারী সাথী বলছি। অনুগ্রহ করে আপনার প্রয়োজন বা মূল বিষয়টি সংক্ষেপে বলুন, আমি বসকে জানিয়ে দেব।"
        }
    }

    fun extractCoreInquirySummary(
        sender: String,
        incomingText: String,
        appName: String,
        type: IncomingCommType,
        language: SathiLanguage = SathiLanguage.BN
    ): String {
        val cleanSender = sender.trim().ifBlank { "অজানা প্রেরক" }
        val cleanApp = appName.trim().ifBlank { if (type == IncomingCommType.CALL) "ফোন" else "মেসেজ" }
        val cleanMsg = incomingText.replace(Regex("\\s+"), " ").trim()
        val lower = cleanMsg.lowercase()

        val topicHintBn = when {
            lower.contains("টাকা") || lower.contains("বিকাশ") || lower.contains("নগদ") ||
                lower.contains("পেমেন্ট") || lower.contains("taka") || lower.contains("payment") ||
                lower.contains("bkash") || lower.contains("nagad") -> "আর্থিক লেনদেন বা পেমেন্ট সংক্রান্ত বিষয়"
            lower.contains("জরুরি") || lower.contains("বিপদ") || lower.contains("তাড়াতাড়ি") ||
                lower.contains("তাড়াতাড়ি") || lower.contains("urgent") || lower.contains("emergency") ->
                "জরুরি যোগাযোগের প্রয়োজন"
            lower.contains("কোথায়") || lower.contains("কোথায়") || lower.contains("আসবে") ||
                lower.contains("আসো") || lower.contains("দেখা") || lower.contains("মিটিং") ||
                lower.contains("লোকেশন") || lower.contains("location") || lower.contains("meeting") ->
                "অবস্থান জানা বা সাক্ষাৎ/সময় নির্ধারণ"
            lower.contains("কাজ") || lower.contains("অফিস") || lower.contains("প্রজেক্ট") ||
                lower.contains("অ্যাসাইনমেন্ট") || lower.contains("ক্লাস") || lower.contains("পরীক্ষা") ->
                "কাজ, পড়াশোনা বা অফিস সংক্রান্ত আলোচনা"
            lower.contains("ডাক্তার") || lower.contains("হাসপাতাল") || lower.contains("অসুস্থ") ||
                lower.contains("ওষুধ") ->
                "স্বাস্থ্য বা চিকিৎসা সংক্রান্ত বিষয়"
            lower.contains("ফোন ধর") || lower.contains("কল দি") || lower.contains("কল ব্যাক") ||
                type == IncomingCommType.CALL ->
                "সরাসরি কথা বলার অনুরোধ"
            else -> "সাধারণ খোঁজখবর বা বার্তা"
        }

        if (language == SathiLanguage.EN) {
            return if (cleanMsg.isBlank()) {
                "$cleanSender reached out via $cleanApp ($topicHintBn)."
            } else {
                "$cleanSender ($cleanApp) — Core issue: \"${cleanMsg.take(140)}\""
            }
        }

        return if (cleanMsg.isBlank() || type == IncomingCommType.CALL) {
            "$cleanApp-এ $cleanSender-এর কল রিসিভ করা হয়েছে। মূল বিষয়: $topicHintBn।"
        } else {
            "$cleanApp-এ $cleanSender জানিয়েছেন ($topicHintBn): \"${cleanMsg.take(140)}\""
        }
    }

    suspend fun handleIncomingWithOptionalAi(
        type: IncomingCommType,
        appName: String,
        packageName: String,
        sender: String,
        incomingText: String,
        apiKey: String,
        language: SathiLanguage,
        aiService: SathiAiService = SathiAiService(),
        autoRepliedViaSystem: Boolean = false
    ): IncomingCommRecord? {
        val cleanSender = sender.trim().ifBlank { "অজানা ব্যক্তি" }
        val cleanText = incomingText.trim()
        if (type == IncomingCommType.MESSAGE && isSelfEchoReply(cleanText)) {
            return null
        }

        val now = System.currentTimeMillis()
        val dedupeKey = "${type.name}|$packageName|$cleanSender|${cleanText.take(60)}"
        val prevAt = recentDedupeKeys[dedupeKey]
        if (prevAt != null && now - prevAt < 15_000L) {
            return null
        }
        recentDedupeKeys.entries.removeAll { now - it.value > 60_000L }
        recentDedupeKeys[dedupeKey] = now

        val fallbackDialogue = buildPoliteDialogueReply(cleanSender, cleanText, type, language)
        var finalDialogue = fallbackDialogue
        var finalSummary = extractCoreInquirySummary(cleanSender, cleanText, appName, type, language)

        if (apiKey.isNotBlank() && cleanText.isNotBlank()) {
            val prompt = buildString {
                append("একটি ${type.labelBn} এসেছে ($appName) '$cleanSender'-এর কাছ থেকে: \"$cleanText\"।\n")
                append("১) 'DIALOGUE:' লাইনে বসের সহকারী হিসেবে প্রেরককে বিনয়ী ও সংক্ষিপ্ত বাংলা উত্তর দাও যেন মূল প্রয়োজনটি স্পষ্ট হয়।\n")
                append("২) 'SUMMARY:' লাইনে বসকে জানানোর জন্য শুধুমাত্র বাংলায় ১-২ বাক্যে মূল বিষয়টি সংক্ষেপে লেখো।")
            }
            val aiTurn = aiService.generateCompanionReply(
                userMessage = prompt,
                language = SathiLanguage.BN,
                userName = "বস",
                memories = emptyList(),
                recentHistory = emptyList(),
                allowLongTermMemory = false,
                apiKey = apiKey
            ).getOrNull()

            val aiText = aiTurn?.replyText.orEmpty()
            if (aiText.isNotBlank()) {
                val dlg = Regex("DIALOGUE:\\s*(.+?)(?=\\nSUMMARY:|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(aiText)?.groupValues?.getOrNull(1)?.trim()
                val sum = Regex("SUMMARY:\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(aiText)?.groupValues?.getOrNull(1)?.trim()
                if (!dlg.isNullOrBlank()) finalDialogue = dlg.take(260)
                if (!sum.isNullOrBlank()) finalSummary = sum.take(260)
            }
        }

        val record = IncomingCommRecord(
            type = type,
            appName = appName,
            packageName = packageName,
            sender = cleanSender,
            receivedText = cleanText,
            sathiDialogueReply = finalDialogue,
            coreInquirySummary = finalSummary,
            autoRepliedViaSystem = autoRepliedViaSystem,
            receivedAtMs = now
        )
        _summaries.value = (listOf(record) + _summaries.value).take(30)
        _latestVoiceBriefing.value = record
        return record
    }

    fun recordQuickIncomingSync(
        type: IncomingCommType,
        appName: String,
        packageName: String,
        sender: String,
        incomingText: String,
        language: SathiLanguage = SathiLanguage.BN,
        autoRepliedViaSystem: Boolean = false
    ): IncomingCommRecord? {
        val cleanSender = sender.trim().ifBlank { "অজানা ব্যক্তি" }
        val cleanText = incomingText.trim()
        if (type == IncomingCommType.MESSAGE && isSelfEchoReply(cleanText)) {
            return null
        }
        val now = System.currentTimeMillis()
        val dedupeKey = "${type.name}|$packageName|$cleanSender|${cleanText.take(60)}"
        val prevAt = recentDedupeKeys[dedupeKey]
        if (prevAt != null && now - prevAt < 15_000L) {
            return null
        }
        recentDedupeKeys[dedupeKey] = now

        val dialogue = buildPoliteDialogueReply(cleanSender, cleanText, type, language)
        val summary = extractCoreInquirySummary(cleanSender, cleanText, appName, type, language)
        val record = IncomingCommRecord(
            type = type,
            appName = appName,
            packageName = packageName,
            sender = cleanSender,
            receivedText = cleanText,
            sathiDialogueReply = dialogue,
            coreInquirySummary = summary,
            autoRepliedViaSystem = autoRepliedViaSystem,
            receivedAtMs = now
        )
        _summaries.value = (listOf(record) + _summaries.value).take(30)
        _latestVoiceBriefing.value = record
        return record
    }

    fun formatBossVoiceBriefing(record: IncomingCommRecord): String {
        return "বস, ${record.coreInquirySummary}"
    }

    fun dismissLatestBriefing() {
        _latestVoiceBriefing.value = null
    }

    fun clearSummaries() {
        _summaries.value = emptyList()
        _latestVoiceBriefing.value = null
    }
}
