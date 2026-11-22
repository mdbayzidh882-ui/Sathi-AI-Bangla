package com.example.data

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.example.core.OfflinePackTier
import com.example.model.SathiLanguage
import java.io.File
import java.net.URLEncoder
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
        RemoteWikiTopic("bn", "কক্সবাজার", listOf("কক্সবাজার", "coxs bazar", "সমুদ্র সৈকত")),
        RemoteWikiTopic("bn", "সুন্দরবন", listOf("সুন্দরবন", "sundarbans", "বাঘ", "ম্যানগ্রোভ")),
        RemoteWikiTopic("bn", "পদ্মা_সেতু", listOf("পদ্মা সেতু", "padma bridge", "পদ্মা")),
        RemoteWikiTopic("bn", "বাংলাদেশের_স্বাধীনতা_যুদ্ধ", listOf("মুক্তিযুদ্ধ", "১৯৭১", "liberation war", "স্বাধীনতা")),
        RemoteWikiTopic("bn", "বাংলা_ভাষা_আন্দোলন", listOf("ভাষা আন্দোলন", "একুশে ফেব্রুয়ারি", "১৯৫২", "শহীদ দিবস")),
        RemoteWikiTopic("bn", "বাংলা_সাহিত্য", listOf("বাংলা সাহিত্য", "সাহিত্য", "bengali literature")),
        RemoteWikiTopic("bn", "রবীন্দ্রনাথ_ঠাকুর", listOf("রবীন্দ্রনাথ", "tagore", "গীতাঞ্জলি")),
        RemoteWikiTopic("bn", "কাজী_নজরুল_ইসলাম", listOf("নজরুল", "nazrul", "বিদ্রোহী কবি")),
        RemoteWikiTopic("bn", "বেগম_রোকেয়া", listOf("বেগম রোকেয়া", "রোকেয়া", "rokeya")),
        RemoteWikiTopic("bn", "জগদীশ_চন্দ্র_বসু", listOf("জগদীশ চন্দ্র বসু", "বসু", "বিজ্ঞানী")),
        RemoteWikiTopic("bn", "সত্যজিৎ_রায়", listOf("সত্যজিৎ রায়", "satyajit ray")),
        RemoteWikiTopic("bn", "কৃত্রিম_বুদ্ধিমত্তা", listOf("এআই", "কৃত্রিম বুদ্ধিমত্তা", "artificial intelligence", "ai")),
        RemoteWikiTopic("bn", "কম্পিউটার", listOf("কম্পিউটার", "computer", "প্রোগ্রামিং")),
        RemoteWikiTopic("bn", "ইন্টারনেট", listOf("ইন্টারনেট", "internet", "ওয়েব", "ওয়েব")),
        RemoteWikiTopic("bn", "স্মার্টফোন", listOf("স্মার্টফোন", "মোবাইল", "smartphone")),
        RemoteWikiTopic("bn", "সৌরজগৎ", listOf("সৌরজগৎ", "পৃথিবী", "সূর্য", "চাঁদ", "গ্রহ", "solar system")),
        RemoteWikiTopic("bn", "পৃথিবী", listOf("পৃথিবী", "earth", "বিশ্ব")),
        RemoteWikiTopic("bn", "সূর্য", listOf("সূর্য", "sun", "নক্ষত্র")),
        RemoteWikiTopic("bn", "চাঁদ", listOf("চাঁদ", "চন্দ্র", "moon")),
        RemoteWikiTopic("bn", "পানি", listOf("পানি", "জল", "water", "h2o")),
        RemoteWikiTopic("bn", "অক্সিজেন", listOf("অক্সিজেন", "oxygen", "বাতাস")),
        RemoteWikiTopic("bn", "বিদ্যুৎ", listOf("বিদ্যুৎ", "তড়িৎ", "electricity")),
        RemoteWikiTopic("bn", "মাধ্যাকর্ষণ", listOf("মাধ্যাকর্ষণ", "অভিকর্ষ", "gravity")),
        RemoteWikiTopic("bn", "সালোকসংশ্লেষণ", listOf("সালোকসংশ্লেষণ", "গাছ", "photosynthesis")),
        RemoteWikiTopic("bn", "স্বাস্থ্য", listOf("স্বাস্থ্য", "রোগ", "শরীর", "পানি", "health")),
        RemoteWikiTopic("bn", "হৃৎপিণ্ড", listOf("হৃৎপিণ্ড", "হার্ট", "heart")),
        RemoteWikiTopic("bn", "মানব_মস্তিষ্ক", listOf("মস্তিষ্ক", "ব্রেইন", "brain")),
        RemoteWikiTopic("bn", "ডায়াবেটিস", listOf("ডায়াবেটিস", "ডায়াবেটিস", "diabetes", "রক্তের শর্করা")),
        RemoteWikiTopic("bn", "ডেঙ্গু_জ্বর", listOf("ডেঙ্গু", "মশা", "জ্বর", "dengue")),
        RemoteWikiTopic("bn", "ইসলাম", listOf("ইসলাম", "ধর্ম", "কুরআন", "islam")),
        RemoteWikiTopic("bn", "রমজান", listOf("রমজান", "রোজা", "সেহরি", "ইফতার", "ramadan")),
        RemoteWikiTopic("bn", "ক্রিকেট", listOf("ক্রিকেট", "cricket", "ব্যাট", "বল")),
        RemoteWikiTopic("bn", "ফুটবল", listOf("ফুটবল", "football", "বিশ্বকাপ")),
        RemoteWikiTopic("bn", "বাংলাদেশের_অর্থনীতি", listOf("অর্থনীতি", "বাণিজ্য", "economy", "টাকা")),
        RemoteWikiTopic("bn", "কৃষি", listOf("কৃষি", "চাষাবাদ", "ফসল", "ধান", "agriculture")),
        RemoteWikiTopic("bn", "পাহাড়", listOf("পাহাড়", "পর্বত", "এভারেস্ট", "mountain")),
        RemoteWikiTopic("bn", "বৃষ্টি", listOf("বৃষ্টি", "বর্ষা", "মেঘ", "rain")),
        RemoteWikiTopic("bn", "ভিটামিন", listOf("ভিটামিন", "পুষ্টি", "খাবার", "vitamin"))
    )

    private val liteTopics = standardTopics.take(20)

    suspend fun downloadRealOfflinePack(
        tier: OfflinePackTier,
        forceRefresh: Boolean = false,
        onProgress: suspend (progress: Float, statusTextBn: String, statusTextEn: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!packDir.exists()) packDir.mkdirs()

            val manifestFile = File(packDir, "knowledge_pack.json")
            val existingEntries = loadEntriesMap().toMutableMap()
            val shouldRefreshAll = forceRefresh || readInstalledSchemaVersion() < PACK_SCHEMA_VERSION

            // Step 1: Write & upgrade rich built-in core Bangladesh & Daily Companion Pack
            seedCuratedCoreEntries(existingEntries)
            saveEntries(manifestFile, existingEntries.values.toList())

            val topics = if (tier == OfflinePackTier.STANDARD) standardTopics else liteTopics
            val total = topics.size.coerceAtLeast(1)

            topics.forEachIndexed { idx, topic ->
                currentCoroutineContext().ensureActive()
                val key = topic.pageTitle.replace('_', ' ')
                val existsAlready = existingEntries.containsKey(topic.pageTitle) || existingEntries.containsKey(key)
                if (!existsAlready || shouldRefreshAll) {
                    onProgress(
                        (idx.toFloat() / total).coerceIn(0.05f, 0.95f),
                        "আপডেট ও ডাউনলোড হচ্ছে: $key (${idx + 1}/$total)",
                        "Updating & downloading: $key (${idx + 1}/$total)"
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

            check(manifestFile.exists() && manifestFile.length() > 128L) {
                "Offline knowledge bundle could not be verified on disk."
            }

            writeMetaFile(tier.name, existingEntries.size)

            onProgress(1f, "অফলাইন প্যাক সম্পূর্ণ আপডেটেড ও প্রস্তুত", "Offline Pack Updated & Ready")
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadCustomOfflineTopic(
        query: String,
        language: SathiLanguage
    ): Result<OfflineKnowledgeEntry> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.length < 2) {
                return@withContext Result.failure(IllegalArgumentException("Topic name too short"))
            }
            if (!packDir.exists()) packDir.mkdirs()

            val prefersBn = cleanQuery.any { it in '\u0980'..'\u09FF' } || language == SathiLanguage.BN
            val fetched = if (prefersBn) {
                fetchWikipediaSearchExtract("bn", cleanQuery)
                    ?: fetchWikipediaSearchExtract("en", cleanQuery)
            } else {
                fetchWikipediaSearchExtract("en", cleanQuery)
                    ?: fetchWikipediaSearchExtract("bn", cleanQuery)
            } ?: return@withContext Result.failure(IllegalStateException("Topic not found on Wikipedia"))

            val manifestFile = File(packDir, "knowledge_pack.json")
            val existing = loadEntriesMap().toMutableMap()
            val entryKey = fetched.topic.replace(' ', '_')
            val enrichedEntry = fetched.copy(
                keywords = (fetched.keywords + listOf(cleanQuery, fetched.topic)).distinct()
            )
            existing[entryKey] = enrichedEntry
            seedCuratedCoreEntries(existing)
            saveEntries(manifestFile, existing.values.toList())
            writeMetaFile("CUSTOM", existing.size)
            Result.success(enrichedEntry)
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
        val entries = loadEntriesMap().values.toList()
        if (entries.isEmpty()) return null

        val queryTokens = tokenize(q)
        val ranked = entries.map { entry ->
            var score = 0
            val topicLower = entry.topic.lowercase()
            if (q == topicLower || topicLower == q) score += 100
            if (q.contains(topicLower) || topicLower.contains(q)) score += 45
            entry.keywords.forEach { kw ->
                val kwL = kw.lowercase()
                if (q == kwL) score += 80
                else if (q.contains(kwL)) score += 32
                else if (kwL.length >= 3 && queryTokens.any { it.contains(kwL) || kwL.contains(it) }) score += 18
            }
            val summaryLower = (entry.summaryBn + " " + entry.summaryEn).lowercase()
            queryTokens.forEach { tok ->
                if (tok.length >= 3 && summaryLower.contains(tok)) score += 6
            }
            entry to score
        }.filter { it.second >= 18 }
            .sortedByDescending { it.second }

        val best = ranked.firstOrNull()?.first ?: return null
        val related = ranked.drop(1).take(2).map { it.first.topic }

        val mainBody = if (language == SathiLanguage.BN) {
            best.summaryBn.ifBlank { best.summaryEn }
        } else {
            best.summaryEn.ifBlank { best.summaryBn }
        }

        return buildString {
            append("${best.topic}:\n$mainBody")
            if (related.isNotEmpty()) {
                if (language == SathiLanguage.BN) {
                    append("\n\nসম্পর্কিত অফলাইন বিষয়: ${related.joinToString(", ")}")
                } else {
                    append("\n\nRelated offline topics: ${related.joinToString(", ")}")
                }
            }
        }
    }

    fun getAvailableTopicsPreview(limit: Int = 8): List<String> =
        loadEntriesMap().values.map { it.topic }.take(limit)

    fun openSystemTtsVoiceInstaller() {
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun tokenize(input: String): List<String> =
        input.split(Regex("[\\s,।?!.;:\"'()\\-]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

    private fun readInstalledSchemaVersion(): Int {
        return try {
            val metaFile = File(packDir, "pack_meta.json")
            if (!metaFile.exists()) return 0
            JSONObject(metaFile.readText()).optInt("schemaVersion", 1)
        } catch (_: Exception) {
            0
        }
    }

    private fun writeMetaFile(tierName: String, count: Int) {
        File(packDir, "pack_meta.json").writeText(
            JSONObject().apply {
                put("tier", tierName)
                put("schemaVersion", PACK_SCHEMA_VERSION)
                put("entriesCount", count)
                put("updatedAt", System.currentTimeMillis())
            }.toString()
        )
    }

    private fun fetchWikipediaSummary(topic: RemoteWikiTopic): OfflineKnowledgeEntry? {
        return try {
            val encodedTitle = URLEncoder.encode(topic.pageTitle, "UTF-8").replace("+", "%20")
            val url = "https://${topic.lang}.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/2.0 (https://github.com/mdbayzidh882-ui/Sathi-AI-Bangla)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val extract = json.optString("extract").trim()
                if (extract.isBlank()) return null
                val title = json.optString("title").ifBlank { topic.pageTitle.replace('_', ' ') }
                val summary = extract.take(750)
                OfflineKnowledgeEntry(
                    topic = title,
                    keywords = topic.keywords,
                    summaryBn = summary,
                    summaryEn = summary
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWikipediaSearchExtract(lang: String, query: String): OfflineKnowledgeEntry? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&exsentences=5&redirects=1&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/2.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val pageObj = pages.optJSONObject(keys.next()) ?: continue
                    val title = pageObj.optString("title").trim()
                    val extract = pageObj.optString("extract").trim().take(780)
                    if (title.isNotBlank() && extract.isNotBlank()) {
                        return OfflineKnowledgeEntry(
                            topic = title,
                            keywords = listOf(query, title),
                            summaryBn = extract,
                            summaryEn = extract
                        )
                    }
                }
                null
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
                keywords = listOf("বিভাগ", "জেলা", "কতটি জেলা", "divisions", "districts of bangladesh", "৬৪ জেলা"),
                summaryBn = "বাংলাদেশে মোট ৮টি বিভাগ (ঢাকা, চট্টগ্রাম, রাজশাহী, খুলনা, বরিশাল, সিলেট, রংপুর, ময়মনসিংহ) এবং ৬৪টি জেলা রয়েছে। সবচেয়ে বড় বিভাগ চট্টগ্রাম ও জনবহুল শহর ঢাকা।",
                summaryEn = "Bangladesh has 8 administrative divisions (Dhaka, Chattogram, Rajshahi, Khulna, Barishal, Sylhet, Rangpur, Mymensingh) and 64 districts."
            ),
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের জাতীয় প্রতীকসমূহ",
                keywords = listOf("জাতীয় প্রতীক", "জাতীয় প্রতীক", "জাতীয় ফল", "জাতীয় ফুল", "জাতীয় পাখি", "জাতীয় মাছ", "জাতীয় পশু", "পতাকা"),
                summaryBn = "বাংলাদেশের জাতীয় ফল কাঁঠাল, জাতীয় ফুল শাপলা, জাতীয় পাখি দোয়েল, জাতীয় মাছ ইলিশ, জাতীয় পশু রয়েল বেঙ্গল টাইগার, জাতীয় খেলা কাবাডি এবং জাতীয় সংগীত 'আমার সোনার বাংলা' (রবীন্দ্রনাথ ঠাকুর)।",
                summaryEn = "Bangladesh national symbols: Fruit = Jackfruit, Flower = Shapla (White Water Lily), Bird = Doel, Fish = Hilsa, Animal = Royal Bengal Tiger, Game = Kabaddi."
            ),
            OfflineKnowledgeEntry(
                topic = "জরুরি হেল্পলাইন নম্বর (বাংলাদেশ)",
                keywords = listOf("হেল্পলাইন", "জরুরি নম্বর", "পুলিশ", "অ্যাম্বুলেন্স", "ফায়ার সার্ভিস", "999", "333", "109", "emergency"),
                summaryBn = "বাংলাদেশে জরুরি সেবা নম্বর:\n• ৯৯৯ (999) — জাতীয় জরুরি সেবা (পুলিশ, ফায়ার সার্ভিস ও অ্যাম্বুলেন্স)\n• ৩৩৩ (333) — সরকারি তথ্য ও সেবা\n• ১০৯ (109) — নারী ও শিশু নির্যাতন প্রতিরোধ হেল্পলাইন\n• ১৬২৬৩ (16263) — স্বাস্থ্য বাতায়ন (২৪ ঘণ্টা ডাক্তারি পরামর্শ)\n• ১০৯০ (1090) — দুর্যোগ ও আবহাওয়ার আগাম বার্তা।",
                summaryEn = "Bangladesh emergency helplines: 999 (Police/Fire/Ambulance), 333 (Govt Info), 109 (Women & Child Helpline), 16263 (24/7 Health Call Center), 1090 (Weather alerts)."
            ),
            OfflineKnowledgeEntry(
                topic = "ইসলামের মূল ভিত্তি ও দৈনন্দিন দোয়া",
                keywords = listOf("ইসলামের ভিত্তি", "পাঁচ স্তম্ভ", "নামাজ", "রোজা", "দোয়া", "দোয়া", "কলেমা", "dua", "pillars of islam"),
                summaryBn = "ইসলামের মূল ভিত্তি ৫টি: কালিমা (তাওহীদ ও রিসালাত), সালাত (৫ ওয়াক্ত নামাজ), জাকাত, সিয়াম (রমজানের রোজা) ও হজ।\nকাজের শুরুতে 'বিসমিল্লাহির রাহমানির রাহিম', শেষে 'আলহামদুলিল্লাহ' এবং অসুস্থতা বা বিপদে 'হাসবুনাল্লাহু ওয়া নি’মাল ওয়াকিল' পড়তে পারেন।",
                summaryEn = "The 5 pillars of Islam are Shahadah (faith), Salah (5 daily prayers), Zakah (charity), Sawm (fasting in Ramadan), and Hajj. Begin good work with 'Bismillah' and give thanks with 'Alhamdulillah'."
            ),
            OfflineKnowledgeEntry(
                topic = "প্রাথমিক স্বাস্থ্য ও জ্বর-সর্দির যত্ন",
                keywords = listOf("পানি", "ঘুম", "মাথা ব্যথা", "জ্বর", "স্বাস্থ্য পরামর্শ", "ডায়রিয়া", "ওরস্যালাইন", "প্রাথমিক চিকিৎসা", "first aid", "fever", "sleep"),
                summaryBn = "সুস্থ থাকতে প্রতিদিন ২.৫-৩ লিটার বিশুদ্ধ পানি পান করুন ও ৭-৮ ঘণ্টা ঘুমান। সাধারণ জ্বরে মাথায় জলপট্টি দিন ও পর্যাপ্ত তরল পান করুন। ডায়রিয়া হলে প্রতিবার পাতলা পায়খানার পর খাবার স্যালাইন (ORS) খান। তীব্র জ্বর, শ্বাসকষ্ট বা ব্যথা না কমলে দ্রুত ডাক্তার দেখান।",
                summaryEn = "Drink clean water daily and sleep 7-8 hours. For mild fever keep hydrated and rest; for dehydration use Oral Rehydration Solution (ORS). Seek medical care if high fever or severe symptoms persist."
            ),
            OfflineKnowledgeEntry(
                topic = "দৈনন্দিন ইংরেজি শেখা (Spoken English)",
                keywords = listOf("ইংরেজি", "spoken english", "ইংরেজি শেখা", "english phrase", "ভালো ইংরেজি"),
                summaryBn = "প্রয়োজনীয় ইংরেজি বাক্য:\n• How are you doing? — আপনি কেমন আছেন?\n• Could you please help me? — আপনি কি আমাকে সাহায্য করবেন?\n• Nice to meet you! — আপনার সাথে দেখা হয়ে ভালো লাগল!\n• Let's stay in touch. — চলো যোগাযোগ রাখি।",
                summaryEn = "Useful everyday phrases: 'Could you please help me?', 'I appreciate your kindness.', 'Let us stay in touch.'"
            ),
            OfflineKnowledgeEntry(
                topic = "একক ও পরিমাপ রূপান্তর গাইড",
                keywords = listOf("মিটার", "ফুট", "কেজি", "কিলোমিটার", "মাইল", "গ্রাম", "একক", "পরিমাপ", "unit", "conversion"),
                summaryBn = "সহজ হিসাব মনে রাখুন:\n• ১ কিলোমিটার = ১০০০ মিটার ≈ ০.৬২ মাইল\n• ১ কেজি = ১০০০ গ্রাম ≈ ২.২০ পাউন্ড\n• ১ ফুট = ১২ ইঞ্চি\n• ১ মিটার ≈ ৩.২৮ ফুট\n• ১ জিবি (GB) = ১০২৪ এমবি (MB)। চাইলে আমাকে সরাসরি সংখ্যা লিখে কনভার্ট করতে বলতে পারেন!",
                summaryEn = "Quick conversions: 1 km = 1000 m (~0.62 miles), 1 kg = 1000 g (~2.2 lbs), 1 ft = 12 in, 1 GB = 1024 MB."
            ),
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের ঋতু ও কৃষিকাজ",
                keywords = listOf("ঋতু", "বৈশাখ", "বর্ষা", "শীত", "গ্রীষ্ম", "ষড়ঋতু", "ফসল", "ধান", "পাট"),
                summaryBn = "বাংলাদেশ ষড়ঋতুর দেশ: গ্রীষ্ম, বর্ষা, শরৎ, হেমন্ত, শীত ও বসন্ত। বাংলাদেশের প্রধান ফসলের মধ্যে রয়েছে ধান (আউশ, আমন, বোরো), পাট (সোনালী আঁশ), গম, আলু, চা ও নানা রকম মৌসুমি ফল।",
                summaryEn = "Bangladesh has 6 traditional seasons (Summer, Monsoon, Autumn, Late Autumn, Winter, Spring). Major crops include rice, jute, wheat, potato, and tea."
            ),
            OfflineKnowledgeEntry(
                topic = "অনলাইন নিরাপত্তা ও প্রতারণা থেকে সতর্কতা",
                keywords = listOf("নিরাপত্তা", "প্রতারণা", "হ্যাকিং", "ওটিপি", "otp", "পাসওয়ার্ড", "বিকাশ", "cyber safety", "scam"),
                summaryBn = "অনলাইনে নিরাপদ থাকতে:\n১. কখনোই নিজের বিকাশ/নগদ/ব্যাংকের পিন বা OTP কোড কাউকে দেবেন না।\n২. অচেনা লিংকে ক্লিক করে লটারি বা পুরস্কারের লোভে তথ্য দেবেন না।\n৩. বড় ও ছোট হাতের অক্ষর, সংখ্যা ও চিহ্ন মিলিয়ে শক্তিশালী পাসওয়ার্ড ব্যবহার করুন।",
                summaryEn = "Stay cyber-safe: never share your banking PIN or OTP codes, avoid suspicious prize links, and use strong unique passwords."
            ),
            OfflineKnowledgeEntry(
                topic = "স্মার্টফোন ও ব্যাটারি ভালো রাখার উপায়",
                keywords = listOf("ব্যাটারি", "চার্জ", "স্টোরেজ", "ফোন গরম", "ফোন স্লো", "battery", "phone tips"),
                summaryBn = "ফোন সুস্থ রাখতে ২০%-৮০% চার্জের মধ্যে রাখার চেষ্টা করুন, আসল চার্জার ব্যবহার করুন, এবং মাঝে মাঝে অপ্রয়োজনীয় ক্যাশ ও ডুপ্লিকেট ফাইল মুছে ফেলুন। গরম আবহাওয়ায় চার্জ দেওয়ার সময় ভারী গেম খেলা এড়িয়ে চলুন।",
                summaryEn = "Keep your phone battery healthy by staying roughly between 20%-80% charge, using an original charger, and clearing unused cache files."
            ),
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের স্বাধীনতা ও মুক্তিযুদ্ধ ১৯৭১",
                keywords = listOf("স্বাধীনতা দিবস", "বিজয় দিবস", "বিজয় দিবস", "২৬ মার্চ", "১৬ ডিসেম্বর", "১৯৭১", "মুক্তিযুদ্ধ"),
                summaryBn = "১৯৭১ সালের ২৬শে মার্চ বাংলাদেশের স্বাধীনতা ঘোষণা ও মুক্তিযুদ্ধ শুরু হয়। দীর্ঘ ৯ মাস রক্তক্ষয়ী সংগ্রামের পর ১৯৭১ সালের ১৬ই ডিসেম্বর বাংলাদেশ চূড়ান্ত বিজয় অর্জন করে ও স্বাধীন রাষ্ট্র হিসেবে আত্মপ্রকাশ করে।",
                summaryEn = "Bangladesh declared independence on 26 March 1971 and achieved Victory on 16 December 1971 after a 9-month Liberation War."
            ),
            OfflineKnowledgeEntry(
                topic = "পড়াশোনা ও চাকরির প্রস্তুতির টিপস",
                keywords = listOf("পড়াশোনা", "পড়াশোনা", "পরীক্ষা", "চাকরি", "মনোযোগ", "study", "interview", "exam"),
                summaryBn = "ভালো ফল ও মনোযোগের জন্য পোমোডোরো পদ্ধতি (২৫ মিনিট পড়া + ৫ মিনিট বিরতি) ব্যবহার করুন। প্রতিটি অধ্যায়ের মূল পয়েন্ট নিজের ভাষায় নোট করুন এবং পরীক্ষার আগে পুরোনো প্রশ্ন অনুশীলন করুন।",
                summaryEn = "Try the Pomodoro technique (25 min focus + 5 min break), summarize topics in your own words, and practice past questions."
            )
        )
        core.forEach { entry ->
            map[entry.topic.replace(' ', '_')] = entry
        }
    }

    companion object {
        private const val PACK_SCHEMA_VERSION = 2
    }
}

