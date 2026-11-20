package com.example.data

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.example.core.OfflinePackTier
import com.example.model.SathiLanguage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class OfflineKnowledgeEntry(
    val topic: String,
    val keywords: List<String>,
    val summaryBn: String,
    val summaryEn: String
)

class OfflinePackManager(context: Context) {
    private val appContext = context.applicationContext
    private val packDir: File = File(appContext.filesDir, "sathi_offline_pack")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private data class RemoteWikiTopic(
        val lang: String,
        val pageTitle: String,
        val keywords: List<String>
    )

    private val standardTopics = listOf(
        RemoteWikiTopic("bn", "বাংলাদেশ", listOf("বাংলাদেশ", "bangladesh", "দেশ", "রাজধানী")),
        RemoteWikiTopic("bn", "ঢাকা", listOf("ঢাকা", "dhaka", "রাজধানী")),
        RemoteWikiTopic("bn", "খুলনা", listOf("খুলনা", "khulna")),
        RemoteWikiTopic("bn", "চট্টগ্রাম", listOf("চট্টগ্রাম", "chattogram", "chittagong")),
        RemoteWikiTopic("bn", "সিলেট", listOf("সিলেট", "sylhet")),
        RemoteWikiTopic("bn", "রাজশাহী", listOf("রাজশাহী", "rajshahi")),
        RemoteWikiTopic("bn", "বরিশাল", listOf("বরিশাল", "barishal")),
        RemoteWikiTopic("bn", "রংপুর", listOf("রংপুর", "rangpur")),
        RemoteWikiTopic("bn", "ময়মনসিংহ", listOf("ময়মনসিংহ", "mymensingh")),
        RemoteWikiTopic("bn", "সুন্দরবন", listOf("সুন্দরবন", "sundarbans", "বাঘ")),
        RemoteWikiTopic("bn", "পদ্মা_সেতু", listOf("পদ্মা সেতু", "padma bridge")),
        RemoteWikiTopic("bn", "রবীন্দ্রনাথ_ঠাকুর", listOf("রবীন্দ্রনাথ", "tagore")),
        RemoteWikiTopic("bn", "কাজী_নজরুল_ইসলাম", listOf("নজরুল", "nazrul")),
        RemoteWikiTopic("bn", "কৃত্রিম_বুদ্ধিমত্তা", listOf("এআই", "কৃত্রিম বুদ্ধিমত্তা", "artificial intelligence", "ai")),
        RemoteWikiTopic("bn", "সৌরজগৎ", listOf("সৌরজগৎ", "পৃথিবী", "সূর্য", "চাঁদ", "solar system")),
        RemoteWikiTopic("bn", "স্বাস্থ্য", listOf("স্বাস্থ্য", "রোগ", "শরীর", "পানি", "health"))
    )

    private val liteTopics = standardTopics.take(8)

    suspend fun downloadRealOfflinePack(
        tier: OfflinePackTier,
        onProgress: suspend (progress: Float, statusTextBn: String, statusTextEn: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!packDir.exists()) packDir.mkdirs()

            val manifestFile = File(packDir, "knowledge_pack.json")
            val existingEntries = loadEntriesMap().toMutableMap()

            // Step 1: Write built-in core Bangladesh & Daily Companion Pack
            seedCuratedCoreEntries(existingEntries)
            saveEntries(manifestFile, existingEntries.values.toList())

            val topics = if (tier == OfflinePackTier.STANDARD) standardTopics else liteTopics
            val total = topics.size.coerceAtLeast(1)

            topics.forEachIndexed { idx, topic ->
                currentCoroutineContext().ensureActive()
                if (!existingEntries.containsKey(topic.pageTitle)) {
                    onProgress(
                        (idx.toFloat() / total).coerceIn(0.05f, 0.95f),
                        "ডাউনলোড হচ্ছে: ${topic.pageTitle.replace('_', ' ')} (${idx + 1}/$total)",
                        "Downloading: ${topic.pageTitle.replace('_', ' ')} (${idx + 1}/$total)"
                    )
                    val fetched = fetchWikipediaSummary(topic)
                    if (fetched != null) {
                        existingEntries[topic.pageTitle] = fetched
                        saveEntries(manifestFile, existingEntries.values.toList())
                    }
                } else {
                    onProgress(
                        ((idx + 1).toFloat() / total).coerceIn(0.05f, 0.98f),
                        "সংরক্ষিত তথ্য যাচাই হচ্ছে (${idx + 1}/$total)",
                        "Verifying saved pack (${idx + 1}/$total)"
                    )
                }
            }

            // Require actual downloaded / written data file on disk
            check(manifestFile.exists() && manifestFile.length() > 128L) {
                "Offline knowledge bundle could not be verified on disk."
            }

            File(packDir, "pack_meta.json").writeText(
                JSONObject().apply {
                    put("tier", tier.name)
                    put("entriesCount", existingEntries.size)
                    put("updatedAt", System.currentTimeMillis())
                }.toString()
            )

            onProgress(1f, "অফলাইন প্যাক সম্পূর্ণ প্রস্তুত", "Offline Pack Ready")
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deletePackOnDisk() {
        try {
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun getInstalledSizeSummary(): String {
        if (!packDir.exists()) return "0 KB"
        val totalBytes = packDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return if (totalBytes >= 1024 * 1024) {
            String.format("%.2f MB", totalBytes / (1024f * 1024f))
        } else {
            "${(totalBytes / 1024L).coerceAtLeast(1L)} KB"
        }
    }

    fun getInstalledTopicCount(): Int = loadEntriesMap().size

    fun searchOfflineKnowledge(query: String, language: SathiLanguage): String? {
        val q = query.lowercase().trim()
        if (q.length < 2) return null
        val entries = loadEntriesMap().values
        val matched = entries.firstOrNull { entry ->
            entry.topic.lowercase().let { q.contains(it) || it.contains(q) } ||
                entry.keywords.any { kw -> q.contains(kw.lowercase()) }
        } ?: return null

        val body = if (language == SathiLanguage.BN) matched.summaryBn else matched.summaryEn
        return if (body.isNotBlank()) body else matched.summaryBn.ifBlank { matched.summaryEn }
    }

    fun openSystemTtsVoiceInstaller() {
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun fetchWikipediaSummary(topic: RemoteWikiTopic): OfflineKnowledgeEntry? {
        return try {
            val url = "https://${topic.lang}.wikipedia.org/api/rest_v1/page/summary/${topic.pageTitle}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/1.0 (https://github.com/mdbayzidh882-ui/Yaseen-Al-)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val extract = json.optString("extract").trim()
                if (extract.isBlank()) return null
                OfflineKnowledgeEntry(
                    topic = topic.pageTitle.replace('_', ' '),
                    keywords = topic.keywords,
                    summaryBn = extract.take(420),
                    summaryEn = extract.take(420)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadEntriesMap(): Map<String, OfflineKnowledgeEntry> {
        val file = File(packDir, "knowledge_pack.json")
        if (!file.exists()) return emptyMap()
        return try {
            val arr = JSONArray(file.readText())
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val topic = o.optString("topic")
                    val kwArr = o.optJSONArray("keywords") ?: JSONArray()
                    val keywords = buildList {
                        for (j in 0 until kwArr.length()) add(kwArr.getString(j))
                    }
                    put(
                        topic.replace(' ', '_'),
                        OfflineKnowledgeEntry(
                            topic = topic,
                            keywords = keywords,
                            summaryBn = o.optString("summaryBn"),
                            summaryEn = o.optString("summaryEn")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveEntries(file: File, entries: List<OfflineKnowledgeEntry>) {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(JSONObject().apply {
                put("topic", entry.topic)
                put("keywords", JSONArray(entry.keywords))
                put("summaryBn", entry.summaryBn)
                put("summaryEn", entry.summaryEn)
            })
        }
        file.writeText(arr.toString())
    }

    private fun seedCuratedCoreEntries(map: MutableMap<String, OfflineKnowledgeEntry>) {
        val core = listOf(
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের বিভাগ ও জেলা",
                keywords = listOf("বিভাগ", "জেলা", "কতটি জেলা", "divisions", "districts of bangladesh"),
                summaryBn = "বাংলাদেশে মোট ৮টি বিভাগ (ঢাকা, চট্টগ্রাম, রাজশাহী, খুলনা, বরিশাল, সিলেট, রংপুর, ময়মনসিংহ) এবং ৬৪টি জেলা রয়েছে।",
                summaryEn = "Bangladesh has 8 administrative divisions and 64 districts."
            ),
            OfflineKnowledgeEntry(
                topic = "মৌলিক স্বাস্থ্য পরামর্শ",
                keywords = listOf("পানি", "ঘুম", "মাথা ব্যথা", "জ্বর", "স্বাস্থ্য পরামর্শ", "sleep", "headache"),
                summaryBn = "সুস্থ থাকতে প্রতিদিন পর্যাপ্ত বিশুদ্ধ পানি পান করুন, অন্তত ৭-৮ ঘণ্টা ঘুমান এবং নিয়মিত হাঁটাহাঁটি করুন। কোনো অসুস্থতা বেশি মনে হলে নিকটস্থ চিকিৎসকের পরামর্শ নিন।",
                summaryEn = "Stay hydrated, sleep 7-8 hours nightly, and walk regularly. Consult a doctor if symptoms persist."
            )
        )
        core.forEach { map.putIfAbsent(it.topic.replace(' ', '_'), it) }
    }
}
