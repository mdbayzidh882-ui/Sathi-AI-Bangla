package com.example.data

import android.content.Context
import android.content.Intent
import android.os.StatFs
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
    private val shardsDir: File = File(packDir, "shards")

    @Volatile
    private var cachedEntries: Map<String, OfflineKnowledgeEntry>? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private data class RemoteWikiTopic(
        val lang: String,
        val pageTitle: String,
        val keywords: List<String>
    )

    // 155+ deep Bengali Wikipedia topics covering all 64 Bangladesh districts, history, science, tech, health, Islam, countries & sports
    private val deepTopics = listOf(
        // Country & National Heritage
        RemoteWikiTopic("bn", "বাংলাদেশ", listOf("বাংলাদেশ", "bangladesh", "দেশ", "রাজধানী")),
        RemoteWikiTopic("bn", "বাংলাদেশের_ইতিহাস", listOf("ইতিহাস", "bangladesh history")),
        RemoteWikiTopic("bn", "বাংলাদেশের_স্বাধীনতা_যুদ্ধ", listOf("মুক্তিযুদ্ধ", "১৯৭১", "liberation war", "স্বাধীনতা")),
        RemoteWikiTopic("bn", "বাংলা_ভাষা_আন্দোলন", listOf("ভাষা আন্দোলন", "একুশে ফেব্রুয়ারি", "১৯৫২", "শহীদ দিবস")),
        RemoteWikiTopic("bn", "বাংলাদেশের_সংবিধান", listOf("সংবিধান", "constitution")),
        RemoteWikiTopic("bn", "বাংলাদেশের_অর্থনীতি", listOf("অর্থনীতি", "বাণিজ্য", "economy", "টাকা")),
        RemoteWikiTopic("bn", "সুন্দরবন", listOf("সুন্দরবন", "sundarbans", "বাঘ", "ম্যানগ্রোভ")),
        RemoteWikiTopic("bn", "পদ্মা_সেতু", listOf("পদ্মা সেতু", "padma bridge", "পদ্মা")),
        RemoteWikiTopic("bn", "কক্সবাজার", listOf("কক্সবাজার", "coxs bazar", "সমুদ্র সৈকত")),
        RemoteWikiTopic("bn", "সেন্টমার্টিন_দ্বীপ", listOf("সেন্টমার্টিন", "saint martin")),
        RemoteWikiTopic("bn", "কুয়াকাটা", listOf("কুয়াকাটা", "kuakata")),
        RemoteWikiTopic("bn", "বঙ্গোপসাগর", listOf("বঙ্গোপসাগর", "bay of bengal")),
        RemoteWikiTopic("bn", "পদ্মা_নদী", listOf("পদ্মা নদী", "padma river")),
        RemoteWikiTopic("bn", "যমুনা_নদী_(বাংলাদেশ)", listOf("যমুনা নদী", "jamuna river")),
        RemoteWikiTopic("bn", "মেঘনা_নদী", listOf("মেঘনা নদী", "meghna river")),
        // All 64 Districts of Bangladesh
        RemoteWikiTopic("bn", "ঢাকা", listOf("ঢাকা", "dhaka", "রাজধানী")),
        RemoteWikiTopic("bn", "খুলনা", listOf("খুলনা", "khulna")),
        RemoteWikiTopic("bn", "চট্টগ্রাম", listOf("চট্টগ্রাম", "chattogram", "chittagong")),
        RemoteWikiTopic("bn", "সিলেট", listOf("সিলেট", "sylhet")),
        RemoteWikiTopic("bn", "রাজশাহী", listOf("রাজশাহী", "rajshahi")),
        RemoteWikiTopic("bn", "বরিশাল", listOf("বরিশাল", "barishal")),
        RemoteWikiTopic("bn", "রংপুর", listOf("রংপুর", "rangpur")),
        RemoteWikiTopic("bn", "ময়মনসিংহ", listOf("ময়মনসিংহ", "mymensingh")),
        RemoteWikiTopic("bn", "গাজীপুর_জেলা", listOf("গাজীপুর", "gazipur")),
        RemoteWikiTopic("bn", "নারায়ণগঞ্জ_জেলা", listOf("নারায়ণগঞ্জ", "narayanganj")),
        RemoteWikiTopic("bn", "টাঙ্গাইল_জেলা", listOf("টাঙ্গাইল", "tangail")),
        RemoteWikiTopic("bn", "নরসিংদী_জেলা", listOf("নরসিংদী", "narsingdi")),
        RemoteWikiTopic("bn", "মানিকগঞ্জ_জেলা", listOf("মানিকগঞ্জ", "manikganj")),
        RemoteWikiTopic("bn", "মুন্সীগঞ্জ_জেলা", listOf("মুন্সীগঞ্জ", "munshiganj")),
        RemoteWikiTopic("bn", "ফরিদপুর_জেলা", listOf("ফরিদপুর", "faridpur")),
        RemoteWikiTopic("bn", "গোপালগঞ্জ_জেলা", listOf("গোপালগঞ্জ", "gopalganj")),
        RemoteWikiTopic("bn", "মাদারীপুর_জেলা", listOf("মাদারীপুর", "madaripur")),
        RemoteWikiTopic("bn", "রাজবাড়ী_জেলা", listOf("রাজবাড়ী", "rajbari")),
        RemoteWikiTopic("bn", "শরীয়তপুর_জেলা", listOf("শরীয়তপুর", "shariatpur")),
        RemoteWikiTopic("bn", "কিশোরগঞ্জ_জেলা", listOf("কিশোরগঞ্জ", "kishoreganj")),
        RemoteWikiTopic("bn", "কুমিল্লা_জেলা", listOf("কুমিল্লা", "cumilla", "comilla")),
        RemoteWikiTopic("bn", "ব্রাহ্মণবাড়িয়া_জেলা", listOf("ব্রাহ্মণবাড়িয়া", "brahmanbaria")),
        RemoteWikiTopic("bn", "চাঁদপুর_জেলা", listOf("চাঁদপুর", "chandpur")),
        RemoteWikiTopic("bn", "নোয়াখালী_জেলা", listOf("নোয়াখালী", "noakhali")),
        RemoteWikiTopic("bn", "ফেনী_জেলা", listOf("ফেনী", "feni")),
        RemoteWikiTopic("bn", "লক্ষ্মীপুর_জেলা", listOf("লক্ষ্মীপুর", "lakshmipur")),
        RemoteWikiTopic("bn", "রাঙ্গামাটি_জেলা", listOf("রাঙ্গামাটি", "rangamati")),
        RemoteWikiTopic("bn", "বান্দরবান_জেলা", listOf("বান্দরবান", "bandarban")),
        RemoteWikiTopic("bn", "খাগড়াছড়ি_জেলা", listOf("খাগড়াছড়ি", "khagrachhari")),
        RemoteWikiTopic("bn", "বগুড়া_জেলা", listOf("বগুড়া", "bogura")),
        RemoteWikiTopic("bn", "পাবনা_জেলা", listOf("পাবনা", "pabna")),
        RemoteWikiTopic("bn", "সিরাজগঞ্জ_জেলা", listOf("সিরাজগঞ্জ", "sirajganj")),
        RemoteWikiTopic("bn", "নাটোর_জেলা", listOf("নাটোর", "natore")),
        RemoteWikiTopic("bn", "নওগাঁ_জেলা", listOf("নওগাঁ", "naogaon")),
        RemoteWikiTopic("bn", "চাঁপাইনবাবগঞ্জ_জেলা", listOf("চাঁপাইনবাবগঞ্জ", "chapainawabganj")),
        RemoteWikiTopic("bn", "জয়পুরহাট_জেলা", listOf("জয়পুরহাট", "joypurhat")),
        RemoteWikiTopic("bn", "যশোর_জেলা", listOf("যশোর", "jashore", "jessore")),
        RemoteWikiTopic("bn", "সাতক্ষীরা_জেলা", listOf("সাতক্ষীরা", "satkhira")),
        RemoteWikiTopic("bn", "বাগেরহাট_জেলা", listOf("বাগেরহাট", "bagerhat")),
        RemoteWikiTopic("bn", "নড়াইল_জেলা", listOf("নড়াইল", "narail")),
        RemoteWikiTopic("bn", "কুষ্টিয়া_জেলা", listOf("কুষ্টিয়া", "kushtia")),
        RemoteWikiTopic("bn", "ঝিনাইদহ_জেলা", listOf("ঝিনাইদহ", "jhenaidah")),
        RemoteWikiTopic("bn", "মাগুরা_জেলা", listOf("মাগুরা", "magura")),
        RemoteWikiTopic("bn", "মেহেরপুর_জেলা", listOf("মেহেরপুর", "meherpur")),
        RemoteWikiTopic("bn", "চুয়াডাঙ্গা_জেলা", listOf("চুয়াডাঙ্গা", "chuadanga")),
        RemoteWikiTopic("bn", "পটুয়াখালী_জেলা", listOf("পটুয়াখালী", "patuakhali")),
        RemoteWikiTopic("bn", "ভোলা_জেলা", listOf("ভোলা", "bhola")),
        RemoteWikiTopic("bn", "বরগুনা_জেলা", listOf("বরগুনা", "barguna")),
        RemoteWikiTopic("bn", "পিরোজপুর_জেলা", listOf("পিরোজপুর", "pirojpur")),
        RemoteWikiTopic("bn", "ঝালকাঠি_জেলা", listOf("ঝালকাঠি", "jhalokathi")),
        RemoteWikiTopic("bn", "সুনামগঞ্জ_জেলা", listOf("সুনামগঞ্জ", "sunamganj")),
        RemoteWikiTopic("bn", "হবিগঞ্জ_জেলা", listOf("হবিগঞ্জ", "habiganj")),
        RemoteWikiTopic("bn", "মৌলভীবাজার_জেলা", listOf("মৌলভীবাজার", "moulvibazar")),
        RemoteWikiTopic("bn", "দিনাজপুর_জেলা", listOf("দিনাজপুর", "dinajpur")),
        RemoteWikiTopic("bn", "ঠাকুরগাঁও_জেলা", listOf("ঠাকুরগাঁও", "thakurgaon")),
        RemoteWikiTopic("bn", "পঞ্চগড়_জেলা", listOf("পঞ্চগড়", "panchagarh")),
        RemoteWikiTopic("bn", "নীলফামারী_জেলা", listOf("নীলফামারী", "nilphamari")),
        RemoteWikiTopic("bn", "লালমনিরহাট_জেলা", listOf("লালমনিরহাট", "lalmonirhat")),
        RemoteWikiTopic("bn", "কুড়িগ্রাম_জেলা", listOf("কুড়িগ্রাম", "kurigram")),
        RemoteWikiTopic("bn", "গাইবান্ধা_জেলা", listOf("গাইবান্ধা", "gaibandha")),
        RemoteWikiTopic("bn", "জামালপুর_জেলা", listOf("জামালপুর", "jamalpur")),
        RemoteWikiTopic("bn", "শেরপুর_জেলা", listOf("শেরপুর", "sherpur")),
        RemoteWikiTopic("bn", "নেত্রকোণা_জেলা", listOf("নেত্রকোনা", "netrokona")),
        // Literature, Culture & Great Minds
        RemoteWikiTopic("bn", "বাংলা_ভাষা", listOf("বাংলা ভাষা", "bengali language")),
        RemoteWikiTopic("bn", "বাংলা_সাহিত্য", listOf("বাংলা সাহিত্য", "সাহিত্য", "bengali literature")),
        RemoteWikiTopic("bn", "রবীন্দ্রনাথ_ঠাকুর", listOf("রবীন্দ্রনাথ", "tagore", "গীতাঞ্জলি")),
        RemoteWikiTopic("bn", "কাজী_নজরুল_ইসলাম", listOf("নজরুল", "nazrul", "বিদ্রোহী কবি")),
        RemoteWikiTopic("bn", "বেগম_রোকেয়া", listOf("বেগম রোকেয়া", "রোকেয়া", "rokeya")),
        RemoteWikiTopic("bn", "জগদীশ_চন্দ্র_বসু", listOf("জগদীশ চন্দ্র বসু", "বসু", "বিজ্ঞানী")),
        RemoteWikiTopic("bn", "সত্যজিৎ_রায়", listOf("সত্যজিৎ রায়", "satyajit ray")),
        RemoteWikiTopic("bn", "জসিমউদ্দীন", listOf("জসিমউদ্দীন", "পল্লীকবি")),
        RemoteWikiTopic("bn", "জীবনানন্দ_দাশ", listOf("জীবনানন্দ দাশ", "বনলতা সেন")),
        RemoteWikiTopic("bn", "হুমায়ূন_আহমেদ", listOf("হুমায়ূন আহমেদ", "হিমু")),
        RemoteWikiTopic("bn", "ফজলুর_রহমান_খান", listOf("এফ আর খান", "স্থপতি")),
        RemoteWikiTopic("bn", "আলবার্ট_আইনস্টাইন", listOf("আইনস্টাইন", "einstein")),
        RemoteWikiTopic("bn", "আইজ্যাক_নিউটন", listOf("নিউটন", "newton")),
        // Technology, AI & Programming
        RemoteWikiTopic("bn", "কৃত্রিম_বুদ্ধিমত্তা", listOf("এআই", "কৃত্রিম বুদ্ধিমত্তা", "artificial intelligence", "ai")),
        RemoteWikiTopic("bn", "কম্পিউটার", listOf("কম্পিউটার", "computer")),
        RemoteWikiTopic("bn", "ইন্টারনেট", listOf("ইন্টারনেট", "internet", "ওয়েব")),
        RemoteWikiTopic("bn", "স্মার্টফোন", listOf("স্মার্টফোন", "মোবাইল", "smartphone")),
        RemoteWikiTopic("bn", "অ্যান্ড্রয়েড", listOf("অ্যান্ড্রয়েড", "android")),
        RemoteWikiTopic("bn", "পাইথন_(প্রোগ্রামিং_ভাষা)", listOf("পাইথন", "python")),
        RemoteWikiTopic("bn", "জাভা_(প্রোগ্রামিং_ভাষা)", listOf("জাভা", "java")),
        RemoteWikiTopic("bn", "এইচটিএমএল", listOf("html", "ওয়েব ডিজাইন")),
        RemoteWikiTopic("bn", "রোবোটিক্স", listOf("রোবট", "robotics")),
        // Science, Space & Nature
        RemoteWikiTopic("bn", "সৌরজগৎ", listOf("সৌরজগৎ", "পৃথিবী", "সূর্য", "চাঁদ", "গ্রহ", "solar system")),
        RemoteWikiTopic("bn", "পৃথিবী", listOf("পৃথিবী", "earth", "বিশ্ব")),
        RemoteWikiTopic("bn", "সূর্য", listOf("সূর্য", "sun", "নক্ষত্র")),
        RemoteWikiTopic("bn", "চাঁদ", listOf("চাঁদ", "চন্দ্র", "moon")),
        RemoteWikiTopic("bn", "মঙ্গল_গ্রহ", listOf("মঙ্গল গ্রহ", "mars")),
        RemoteWikiTopic("bn", "পানি", listOf("পানি", "জল", "water", "h2o")),
        RemoteWikiTopic("bn", "অক্সিজেন", listOf("অক্সিজেন", "oxygen", "বাতাস")),
        RemoteWikiTopic("bn", "বিদ্যুৎ", listOf("বিদ্যুৎ", "তড়িৎ", "electricity")),
        RemoteWikiTopic("bn", "মাধ্যাকর্ষণ", listOf("মাধ্যাকর্ষণ", "অভিকর্ষ", "gravity")),
        RemoteWikiTopic("bn", "সালোকসংশ্লেষণ", listOf("সালোকসংশ্লেষণ", "গাছ", "photosynthesis")),
        RemoteWikiTopic("bn", "আলো", listOf("আলো", "light")),
        RemoteWikiTopic("bn", "জলবায়ু_পরিবর্তন", listOf("জলবায়ু পরিবর্তন", "climate change")),
        RemoteWikiTopic("bn", "পাহাড়", listOf("পাহাড়", "পর্বত", "এভারেস্ট", "mountain")),
        RemoteWikiTopic("bn", "বৃষ্টি", listOf("বৃষ্টি", "বর্ষা", "মেঘ", "rain")),
        // Health & Medicine
        RemoteWikiTopic("bn", "স্বাস্থ্য", listOf("স্বাস্থ্য", "রোগ", "শরীর", "health")),
        RemoteWikiTopic("bn", "হৃৎপিণ্ড", listOf("হৃৎপিণ্ড", "হার্ট", "heart")),
        RemoteWikiTopic("bn", "মানব_মস্তিষ্ক", listOf("মস্তিষ্ক", "ব্রেইন", "brain")),
        RemoteWikiTopic("bn", "ডায়াবেটিস", listOf("ডায়াবেটিস", "ডায়াবেটিস", "diabetes")),
        RemoteWikiTopic("bn", "ডেঙ্গু_জ্বর", listOf("ডেঙ্গু", "মশা", "জ্বর", "dengue")),
        RemoteWikiTopic("bn", "ভিটামিন", listOf("ভিটামিন", "পুষ্টি", "খাবার", "vitamin")),
        RemoteWikiTopic("bn", "উচ্চ_রক্তচাপ", listOf("রক্তচাপ", "প্রেশার", "blood pressure")),
        RemoteWikiTopic("bn", "প্রাথমিক_চিকিৎসা", listOf("প্রাথমিক চিকিৎসা", "first aid")),
        // Islam & Faith
        RemoteWikiTopic("bn", "ইসলাম", listOf("ইসলাম", "ধর্ম", "islam")),
        RemoteWikiTopic("bn", "কুরআন", listOf("কুরআন", "কোরআন", "quran")),
        RemoteWikiTopic("bn", "হাদিস", listOf("হাদিস", "hadith")),
        RemoteWikiTopic("bn", "মুহাম্মদ", listOf("নবী", "হযরত মুহাম্মদ", "রাসূল")),
        RemoteWikiTopic("bn", "নামাজ", listOf("নামাজ", "সালাত", "salah")),
        RemoteWikiTopic("bn", "রমজান", listOf("রমজান", "রোজা", "সেহরি", "ইফতার", "ramadan")),
        RemoteWikiTopic("bn", "যাকাত", listOf("যাকাত", "জাকাত", "zakat")),
        RemoteWikiTopic("bn", "হজ্জ", listOf("হজ", "হজ্জ", "hajj")),
        RemoteWikiTopic("bn", "মক্কা", listOf("মক্কা", "কাবা", "makkah")),
        RemoteWikiTopic("bn", "মদিনা", listOf("মদিনা", "madinah")),
        // World Countries
        RemoteWikiTopic("bn", "ভারত", listOf("ভারত", "india")),
        RemoteWikiTopic("bn", "সৌদি_আরব", listOf("সৌদি আরব", "saudi arabia")),
        RemoteWikiTopic("bn", "মালয়েশিয়া", listOf("মালয়েশিয়া", "malaysia")),
        RemoteWikiTopic("bn", "জাপান", listOf("জাপান", "japan")),
        RemoteWikiTopic("bn", "চীন", listOf("চীন", "china")),
        RemoteWikiTopic("bn", "মার্কিন_যুক্তরাষ্ট্র", listOf("যুক্তরাষ্ট্র", "আমেরিকা", "usa")),
        RemoteWikiTopic("bn", "যুক্তরাজ্য", listOf("যুক্তরাজ্য", "ইংল্যান্ড", "uk")),
        RemoteWikiTopic("bn", "তুরস্ক", listOf("তুরস্ক", "turkey")),
        RemoteWikiTopic("bn", "পাকিস্তান", listOf("পাকিস্তান", "pakistan")),
        RemoteWikiTopic("bn", "কানাডা", listOf("কানাডা", "canada")),
        RemoteWikiTopic("bn", "অস্ট্রেলিয়া", listOf("অস্ট্রেলিয়া", "australia")),
        RemoteWikiTopic("bn", "মিশর", listOf("মিশর", "egypt")),
        // Sports & Agriculture
        RemoteWikiTopic("bn", "ক্রিকেট", listOf("ক্রিকেট", "cricket", "ব্যাট", "বল")),
        RemoteWikiTopic("bn", "বাংলাদেশ_জাতীয়_ক্রিকেট_দল", listOf("বাংলাদেশ ক্রিকেট", "টাইগার")),
        RemoteWikiTopic("bn", "ফুটবল", listOf("ফুটবল", "football", "বিশ্বকাপ")),
        RemoteWikiTopic("bn", "কৃষি", listOf("কৃষি", "চাষাবাদ", "ফসল", "ধান", "agriculture")),
        RemoteWikiTopic("bn", "ধান", listOf("ধান", "চাল", "rice")),
        RemoteWikiTopic("bn", "পাট", listOf("পাট", "সোনালী আঁশ", "jute"))
    )

    private val standardTopics = deepTopics.take(65)
    private val liteTopics = deepTopics.take(22)

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

            // Step 1: Write rich curated daily & Bangladesh Q&A database
            seedCuratedCoreEntries(existingEntries)
            saveEntries(manifestFile, existingEntries.values.toList())

            val topics = when (tier) {
                OfflinePackTier.DEEP -> deepTopics
                OfflinePackTier.STANDARD -> standardTopics
                OfflinePackTier.LITE -> liteTopics
            }
            val toFetch = if (shouldRefreshAll) {
                topics
            } else {
                topics.filter { t ->
                    val key = t.pageTitle.replace('_', ' ')
                    !existingEntries.containsKey(t.pageTitle) && !existingEntries.containsKey(key)
                }
            }

            if (toFetch.isNotEmpty()) {
                val batchSize = 12
                val batches = toFetch.chunked(batchSize)
                batches.forEachIndexed { batchIdx, batch ->
                    currentCoroutineContext().ensureActive()
                    val firstLabel = batch.first().pageTitle.replace('_', ' ')
                    val progress = ((batchIdx + 0.3f) / batches.size.coerceAtLeast(1)).coerceIn(0.05f, 0.94f)
                    onProgress(
                        progress,
                        "অফলাইন ডাটাবেজ ডাউনলোড হচ্ছে: $firstLabel... (ধাপ ${batchIdx + 1}/${batches.size})",
                        "Downloading offline encyclopedia: $firstLabel... (${batchIdx + 1}/${batches.size})"
                    )

                    val fetchedBatch = fetchWikipediaBatchExtracts(batch)
                    if (fetchedBatch.isNotEmpty()) {
                        fetchedBatch.forEach { (key, entry) ->
                            existingEntries[key] = entry
                        }
                    } else {
                        // Individual fallback if batch fails
                        batch.forEach { topic ->
                            currentCoroutineContext().ensureActive()
                            fetchWikipediaSummary(topic)?.let { fetched ->
                                existingEntries[topic.pageTitle] = fetched
                            }
                        }
                    }
                    saveEntries(manifestFile, existingEntries.values.toList())
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
                fetchWikipediaSearchMultiExtracts("bn", cleanQuery, limit = 1).firstOrNull()
                    ?: fetchWikipediaSearchMultiExtracts("en", cleanQuery, limit = 1).firstOrNull()
            } else {
                fetchWikipediaSearchMultiExtracts("en", cleanQuery, limit = 1).firstOrNull()
                    ?: fetchWikipediaSearchMultiExtracts("bn", cleanQuery, limit = 1).firstOrNull()
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

    suspend fun syncSmartKnowledgeBrainWithGemini(
        apiKey: String,
        aiService: SathiAiService,
        maxBudgetBytes: Long = MAX_BRAIN_STORAGE_BYTES,
        onProgress: suspend (progress: Float, statusTextBn: String, statusTextEn: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!packDir.exists()) packDir.mkdirs()
            if (!shardsDir.exists()) shardsDir.mkdirs()

            // Stage 1: Base deep offline encyclopedia (all 64 districts, core science/history/Islam/tech)
            val baseResult = downloadRealOfflinePack(
                tier = OfflinePackTier.DEEP,
                forceRefresh = false
            ) { p, bn, en ->
                onProgress((p * 0.45f).coerceIn(0.02f, 0.45f), bn, en)
            }
            if (baseResult.isFailure) {
                return@withContext Result.failure(
                    baseResult.exceptionOrNull() ?: IllegalStateException("Base offline sync failed")
                )
            }

            val existing = loadEntriesMap().toMutableMap()
            var addedCount = 0

            // Stage 2: Expanded multi-domain Wikipedia knowledge packs (stored in real JSON shards within 2 GB cap)
            val domainPacks = getExtendedDomainQueries()
            domainPacks.forEachIndexed { idx, (langCode, query, kws) ->
                currentCoroutineContext().ensureActive()
                if (getTotalInstalledBytes() >= maxBudgetBytes || !hasSafeStorageHeadroom()) {
                    onProgress(
                        1f,
                        "স্মার্ট জ্ঞান ভান্ডার প্রস্তুত (সীমা ২ জিবি সংরক্ষিত)",
                        "Smart Knowledge Brain ready (2 GB budget respected)"
                    )
                    return@withContext Result.success(existing.size)
                }

                val progress = 0.46f + (0.32f * (idx + 1).toFloat() / domainPacks.size.coerceAtLeast(1))
                onProgress(
                    progress.coerceIn(0.46f, 0.78f),
                    "গভীর জ্ঞান ভান্ডার সংগ্রহ হচ্ছে: $query (${idx + 1}/${domainPacks.size})",
                    "Syncing Smart Brain domain: $query (${idx + 1}/${domainPacks.size})"
                )

                val keyNorm = query.replace(' ', '_')
                if (!existing.containsKey(keyNorm) && !existing.values.any { it.topic.equals(query, ignoreCase = true) }) {
                    val fetchedList = fetchWikipediaSearchMultiExtracts(langCode, query, limit = 4)
                    val shardItems = mutableListOf<OfflineKnowledgeEntry>()
                    fetchedList.forEach { item ->
                        val itemKey = item.topic.replace(' ', '_')
                        if (!existing.containsKey(itemKey)) {
                            val enriched = item.copy(keywords = (item.keywords + kws + query).distinct())
                            existing[itemKey] = enriched
                            shardItems.add(enriched)
                            addedCount++
                        }
                    }
                    if (shardItems.isNotEmpty()) {
                        val shardFile = File(shardsDir, "domain_shard_${idx + 1}.json")
                        saveEntries(shardFile, shardItems)
                    }
                }
            }

            // Stage 3: Gemini-Synthesized Master Bilingual Reference Shards (when valid API key provided)
            val cleanKey = SathiAiService.sanitizeApiKeyInput(apiKey)
            if (cleanKey.isNotBlank()) {
                val brainModules = getGeminiBrainCurriculum()
                val geminiShardEntries = mutableListOf<OfflineKnowledgeEntry>()
                brainModules.forEachIndexed { idx, module ->
                    currentCoroutineContext().ensureActive()
                    if (getTotalInstalledBytes() >= maxBudgetBytes || !hasSafeStorageHeadroom()) {
                        return@forEachIndexed
                    }
                    val modKey = module.title.replace(' ', '_')
                    if (existing.containsKey(modKey)) return@forEachIndexed

                    val progress = 0.79f + (0.19f * (idx + 1).toFloat() / brainModules.size.coerceAtLeast(1))
                    onProgress(
                        progress.coerceIn(0.79f, 0.98f),
                        "জেমিনি মেধা সংরক্ষণ হচ্ছে: ${module.title}...",
                        "Distilling Gemini intelligence: ${module.title}..."
                    )

                    val distilled = aiService.fetchGeminiKnowledgeEntry(
                        apiKey = cleanKey,
                        title = module.title,
                        keywords = module.keywords,
                        prompt = module.instruction
                    )
                    if (distilled != null) {
                        existing[modKey] = distilled
                        geminiShardEntries.add(distilled)
                        addedCount++
                        saveEntries(File(shardsDir, "gemini_brain_shard.json"), geminiShardEntries)
                    }
                }
            }

            cachedEntries = existing.toMap()
            writeMetaFile(OfflinePackTier.DEEP.name, existing.size)
            onProgress(
                1f,
                "স্মার্ট জ্ঞান ভান্ডার সক্রিয় (${existing.size} বিষয় • ${getInstalledSizeSummary()} / 2.00 GB)",
                "Smart Knowledge Brain Active (${existing.size} topics • ${getInstalledSizeSummary()} / 2.00 GB)"
            )
            Result.success(existing.size)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deletePackOnDisk() {
        try {
            cachedEntries = null
            if (packDir.exists()) {
                packDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun getTotalInstalledBytes(): Long {
        if (!packDir.exists()) return 0L
        return packDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getRemainingBudgetBytes(maxBudgetBytes: Long = MAX_BRAIN_STORAGE_BYTES): Long =
        (maxBudgetBytes - getTotalInstalledBytes()).coerceAtLeast(0L)

    fun hasSafeStorageHeadroom(): Boolean {
        return try {
            val target = if (packDir.exists()) packDir else appContext.filesDir
            val stat = StatFs(target.absolutePath)
            stat.availableBytes > MIN_FREE_DISK_SAFETY_BYTES
        } catch (_: Exception) {
            true
        }
    }

    fun getInstalledSizeSummary(): String = formatBytes(getTotalInstalledBytes())

    fun getStorageBudgetLabel(language: SathiLanguage): String {
        val used = getInstalledSizeSummary()
        val count = getInstalledTopicCount()
        return if (language == SathiLanguage.BN) {
            "$used / 2.00 GB সর্বোচ্চ • $count টি সংরক্ষিত বিষয়"
        } else {
            "$used / 2.00 GB max • $count saved topics"
        }
    }

    fun getInstalledTopicCount(): Int = loadEntriesMap().size

    private fun formatBytes(totalBytes: Long): String {
        if (totalBytes <= 0L) return "0 KB"
        return when {
            totalBytes >= 1024L * 1024L * 1024L ->
                String.format("%.2f GB", totalBytes / (1024f * 1024f * 1024f))
            totalBytes >= 1024L * 1024L ->
                String.format("%.2f MB", totalBytes / (1024f * 1024f))
            else ->
                "${(totalBytes / 1024L).coerceAtLeast(1L)} KB"
        }
    }

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
        }.take(1800)

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

    private fun fetchWikipediaBatchExtracts(batch: List<RemoteWikiTopic>): Map<String, OfflineKnowledgeEntry> {
        val out = mutableMapOf<String, OfflineKnowledgeEntry>()
        if (batch.isEmpty()) return out
        val lang = batch.first().lang
        val titlesParam = batch.joinToString("|") { it.pageTitle }
        return try {
            val encodedTitles = URLEncoder.encode(titlesParam, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php" +
                "?action=query&titles=$encodedTitles&prop=extracts&explaintext=1&exchars=5500&redirects=1&format=json&utf8=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/2.5 (https://github.com/mdbayzidh882-ui/Sathi-AI-Bangla)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return out
                val body = resp.body?.string() ?: return out
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return out
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.optJSONObject(keys.next()) ?: continue
                    val title = page.optString("title").trim()
                    val extract = page.optString("extract").replace(Regex("\\s+"), " ").trim()
                    if (title.isBlank() || extract.length < 40) continue
                    val matchingTopic = batch.firstOrNull {
                        it.pageTitle.replace('_', ' ').equals(title, ignoreCase = true) ||
                            extract.contains(it.keywords.firstOrNull().orEmpty(), ignoreCase = true)
                    }
                    val kw = matchingTopic?.keywords ?: listOf(title)
                    val mapKey = (matchingTopic?.pageTitle ?: title).replace(' ', '_')
                    out[mapKey] = OfflineKnowledgeEntry(
                        topic = title,
                        keywords = (kw + title).distinct(),
                        summaryBn = extract.take(5500),
                        summaryEn = extract.take(5500)
                    )
                }
                out
            }
        } catch (_: Exception) {
            out
        }
    }

    private fun fetchWikipediaSummary(topic: RemoteWikiTopic): OfflineKnowledgeEntry? {
        return try {
            val encodedTitle = URLEncoder.encode(topic.pageTitle, "UTF-8").replace("+", "%20")
            val url = "https://${topic.lang}.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/2.5 (https://github.com/mdbayzidh882-ui/Sathi-AI-Bangla)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val extract = json.optString("extract").trim()
                if (extract.isBlank()) return null
                val title = json.optString("title").ifBlank { topic.pageTitle.replace('_', ' ') }
                val summary = extract.take(2500)
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

    private fun fetchWikipediaSearchMultiExtracts(
        lang: String,
        query: String,
        limit: Int = 4
    ): List<OfflineKnowledgeEntry> {
        val list = mutableListOf<OfflineKnowledgeEntry>()
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=$limit&prop=extracts&explaintext=1&exchars=4500&redirects=1&format=json&utf8=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SathiAndroidApp/2.5 (https://github.com/mdbayzidh882-ui/Sathi-AI-Bangla)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return list
                val body = resp.body?.string() ?: return list
                val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return list
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val pageObj = pages.optJSONObject(keys.next()) ?: continue
                    val title = pageObj.optString("title").trim()
                    val extract = pageObj.optString("extract").replace(Regex("\\s+"), " ").trim().take(4500)
                    if (title.isNotBlank() && extract.length >= 40) {
                        list.add(
                            OfflineKnowledgeEntry(
                                topic = title,
                                keywords = listOf(query, title),
                                summaryBn = extract,
                                summaryEn = extract
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            list
        }
    }

    private fun loadEntriesMap(): Map<String, OfflineKnowledgeEntry> {
        cachedEntries?.let { return it }
        if (!packDir.exists()) return emptyMap()
        val combined = linkedMapOf<String, OfflineKnowledgeEntry>()
        val mainFile = File(packDir, "knowledge_pack.json")
        if (mainFile.exists()) {
            readEntriesFile(mainFile).forEach { (k, v) -> combined[k] = v }
        }
        if (shardsDir.exists()) {
            shardsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { shard ->
                    readEntriesFile(shard).forEach { (k, v) -> combined[k] = v }
                }
        }
        val frozen = combined.toMap()
        cachedEntries = frozen
        return frozen
    }

    private fun readEntriesFile(file: File): Map<String, OfflineKnowledgeEntry> {
        return try {
            val arr = JSONArray(file.readText())
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val topic = o.optString("topic")
                    if (topic.isBlank()) continue
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
        file.parentFile?.mkdirs()
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
        cachedEntries = null
    }

    private data class BrainCurriculumItem(
        val title: String,
        val keywords: List<String>,
        val instruction: String
    )

    private fun getExtendedDomainQueries(): List<Triple<String, String, List<String>>> = listOf(
        Triple("bn", "কোয়ান্টাম বলবিজ্ঞান", listOf("কোয়ান্টাম", "quantum", "পদার্থবিজ্ঞান")),
        Triple("bn", "জীববিজ্ঞান ও ডিএনএ", listOf("জীববিজ্ঞান", "ডিএনএ", "dna", "biology")),
        Triple("bn", "রসায়ন ও পর্যায় সারণি", listOf("রসায়ন", "chemistry", "পর্যায় সারণি")),
        Triple("bn", "গণিত ও ক্যালকুলাস", listOf("গণিত", "ক্যালকুলাস", "math", "algebra")),
        Triple("bn", "কম্পিউটার প্রোগ্রামিং ও অ্যালগরিদম", listOf("অ্যালগরিদম", "প্রোগ্রামিং", "coding", "algorithm")),
        Triple("bn", "কৃষি বিজ্ঞান ও আধুনিক চাষাবাদ", listOf("আধুনিক কৃষি", "ফসল রোগ", "সার")),
        Triple("bn", "ইসলামের ইতিহাস ও খিলাফত", listOf("ইসলামের ইতিহাস", "খিলাফত", "সাহাবী")),
        Triple("bn", "বিশ্বের মহাদেশ ও মহাসাগর", listOf("মহাদেশ", "মহাসাগর", "continent", "ocean")),
        Triple("bn", "অর্থনীতি ও ব্যাংকিং ব্যবস্থা", listOf("ব্যাংক", "অর্থনীতি", "মুদ্রাস্ফীতি", "finance")),
        Triple("en", "Artificial intelligence and machine learning", listOf("machine learning", "neural network", "llm")),
        Triple("en", "Human anatomy and nutrition", listOf("anatomy", "nutrition", "organs")),
        Triple("en", "Software engineering and databases", listOf("software engineering", "database", "kotlin"))
    )

    private fun getGeminiBrainCurriculum(): List<BrainCurriculumItem> = listOf(
        BrainCurriculumItem(
            title = "দৈনন্দিন প্রোগ্রামিং ও কোডিং মাস্টার গাইড",
            keywords = listOf("কোডিং", "প্রোগ্রামিং", "kotlin", "python", "html", "javascript", "sql"),
            instruction = "Provide a dense, practical reference guide covering Python, Kotlin, JavaScript, and HTML/SQL fundamentals with clean syntax examples."
        ),
        BrainCurriculumItem(
            title = "আধুনিক বিজ্ঞান, মহাকাশ ও পদার্থবিজ্ঞান সারসংক্ষেপ",
            keywords = listOf("পদার্থবিজ্ঞান", "মহাকাশ", "ব্ল্যাক হোল", "physics", "space", "relativity"),
            instruction = "Summarize core laws of physics, relativity, thermodynamics, electricity, and space exploration clearly."
        ),
        BrainCurriculumItem(
            title = "রোগ প্রতিরোধ, পুষ্টি ও প্রাথমিক চিকিৎসা হ্যান্ডবুক",
            keywords = listOf("চিকিৎসা", "পুষ্টি", "প্রাথমিক চিকিৎসা", "অ্যান্টিবায়োটিক", "first aid", "nutrition"),
            instruction = "Provide a structured practical reference for balanced nutrition, hydration, fever/wound first-aid, and when to seek urgent medical care."
        ),
        BrainCurriculumItem(
            title = "বাংলাদেশ ও বিশ্ব সাধারণ জ্ঞান কমপেন্ডিয়াম",
            keywords = listOf("সাধারণ জ্ঞান", "বিসিএস", "ইতিহাস", "ভৌগোলিক", "general knowledge", "bcs"),
            instruction = "Compile high-value Bangladesh & world general knowledge facts useful for students and competitive exams."
        ),
        BrainCurriculumItem(
            title = "ব্যবসা, ফ্রিল্যান্সিং ও ক্যারিয়ার পরিকল্পনা গাইড",
            keywords = listOf("ফ্রিল্যান্সিং", "ব্যবসা", "ক্যারিয়ার", "সিভি", "cv", "business", "career"),
            instruction = "Summarize practical steps for resume writing, interview preparation, small business budgeting, and online freelancing safety."
        ),
        BrainCurriculumItem(
            title = "ইসলামিক জীবনবিধান, সূরা ও গুরুত্বপূর্ণ দোয়া",
            keywords = listOf("ইসলামিক জ্ঞান", "সূরা", "দোয়া", "তাসবিহ", "নামাজ শিক্ষা", "islamic guide"),
            instruction = "Provide an authentic, respectful summary of core Islamic prayers, daily duas with Bengali meaning, and Ramadan/Zakat essentials."
        )
    )

    private fun seedCuratedCoreEntries(map: MutableMap<String, OfflineKnowledgeEntry>) {
        val core = listOf(
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের বিভাগ ও ৬৪ জেলা",
                keywords = listOf("বিভাগ", "জেলা", "কতটি জেলা", "divisions", "districts of bangladesh", "৬৪ জেলা"),
                summaryBn = "বাংলাদেশে মোট ৮টি বিভাগ ও ৬৪টি জেলা রয়েছে:\n• ঢাকা বিভাগ (১৩ জেলা)\n• চট্টগ্রাম বিভাগ (১১ জেলা)\n• রাজশাহী বিভাগ (৮ জেলা)\n• খুলনা বিভাগ (১০ জেলা)\n• বরিশাল বিভাগ (৬ জেলা)\n• সিলেট বিভাগ (৪ জেলা)\n• রংপুর বিভাগ (৮ জেলা)\n• ময়মনসিংহ বিভাগ (৪ জেলা)।",
                summaryEn = "Bangladesh has 8 divisions (Dhaka, Chattogram, Rajshahi, Khulna, Barishal, Sylhet, Rangpur, Mymensingh) and 64 districts."
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
                summaryBn = "ইসলামের মূল ভিত্তি ৫টি: কালিমা (তাওহীদ ও রিসালাত), সালাত (৫ ওয়াক্ত নামাজ), জাকাত, সিয়াম (রমজানের রোজা) ও হজ।\nকাজের শুরুতে 'বিসমিল্লাহির রাহমানির রাহিম', শেষে 'আলহামদুলিল্লাহ' এবং বিপদে 'হাসবুনাল্লাহু ওয়া নি’মাল ওয়াকিল' পড়তে পারেন।",
                summaryEn = "The 5 pillars of Islam are Shahadah (faith), Salah (5 daily prayers), Zakah (charity), Sawm (fasting in Ramadan), and Hajj."
            ),
            OfflineKnowledgeEntry(
                topic = "প্রাথমিক স্বাস্থ্য ও জ্বর-সর্দির যত্ন",
                keywords = listOf("স্বাস্থ্য পরামর্শ", "ডায়রিয়া", "ওরস্যালাইন", "প্রাথমিক চিকিৎসা", "first aid", "fever"),
                summaryBn = "সুস্থ থাকতে প্রতিদিন ২.৫-৩ লিটার বিশুদ্ধ পানি পান করুন ও ৭-৮ ঘণ্টা ঘুমান। সাধারণ জ্বরে মাথায় জলপট্টি দিন ও পর্যাপ্ত তরল পান করুন। ডায়রিয়া হলে খাবার স্যালাইন (ORS) খান। তীব্র জ্বর বা শ্বাসকষ্টে দ্রুত চিকিৎসকের পরামর্শ নিন।",
                summaryEn = "Drink clean water daily and sleep 7-8 hours. For mild fever keep hydrated and rest; for dehydration use Oral Rehydration Solution (ORS)."
            ),
            OfflineKnowledgeEntry(
                topic = "দৈনন্দিন ইংরেজি শেখা (Spoken English)",
                keywords = listOf("ইংরেজি", "spoken english", "ইংরেজি শেখা", "english phrase"),
                summaryBn = "প্রয়োজনীয় ইংরেজি বাক্য:\n• How are you doing? — আপনি কেমন আছেন?\n• Could you please help me? — আপনি কি আমাকে সাহায্য করবেন?\n• Nice to meet you! — আপনার সাথে দেখা হয়ে ভালো লাগল!\n• Let's stay in touch. — চলো যোগাযোগ রাখি।",
                summaryEn = "Useful everyday phrases: 'Could you please help me?', 'I appreciate your kindness.', 'Let us stay in touch.'"
            ),
            OfflineKnowledgeEntry(
                topic = "একক ও পরিমাপ রূপান্তর গাইড",
                keywords = listOf("মিটার", "ফুট", "কেজি", "কিলোমিটার", "মাইল", "গ্রাম", "একক", "পরিমাপ", "unit", "conversion"),
                summaryBn = "সহজ হিসাব মনে রাখুন:\n• ১ কিলোমিটার = ১০০০ মিটার ≈ ০.৬২ মাইল\n• ১ কেজি = ১০০০ গ্রাম ≈ ২.২০ পাউন্ড\n• ১ ফুট = ১২ ইঞ্চি\n• ১ মিটার ≈ ৩.২৮ ফুট\n• ১ জিবি (GB) = ১০২৪ এমবি (MB)।",
                summaryEn = "Quick conversions: 1 km = 1000 m (~0.62 miles), 1 kg = 1000 g (~2.2 lbs), 1 ft = 12 in, 1 GB = 1024 MB."
            ),
            OfflineKnowledgeEntry(
                topic = "বাংলাদেশের ঋতু ও কৃষিকাজ",
                keywords = listOf("ঋতু", "বৈশাখ", "বর্ষা", "শীত", "গ্রীষ্ম", "ষড়ঋতু", "ফসল"),
                summaryBn = "বাংলাদেশ ষড়ঋতুর দেশ: গ্রীষ্ম, বর্ষা, শরৎ, হেমন্ত, শীত ও বসন্ত। বাংলাদেশের প্রধান ফসলের মধ্যে রয়েছে ধান (আউশ, আমন, বোরো), পাট (সোনালী আঁশ), গম, আলু, চা ও নানা রকম মৌসুমি ফল।",
                summaryEn = "Bangladesh has 6 traditional seasons (Summer, Monsoon, Autumn, Late Autumn, Winter, Spring). Major crops include rice, jute, wheat, potato, and tea."
            ),
            OfflineKnowledgeEntry(
                topic = "অনলাইন নিরাপত্তা ও প্রতারণা থেকে সতর্কতা",
                keywords = listOf("নিরাপত্তা", "প্রতারণা", "হ্যাকিং", "ওটিপি", "otp", "পাসওয়ার্ড", "বিকাশ", "cyber safety", "scam"),
                summaryBn = "অনলাইনে নিরাপদ থাকতে:\n১. কখনোই নিজের বিকাশ/নগদ/ব্যাংকের পিন বা OTP কোড কাউকে দেবেন না।\n২. অচেনা লিংকে ক্লিক করে লটারি বা পুরস্কারের লোভে তথ্য দেবেন না।\n৩. শক্তিশালী পাসওয়ার্ড ব্যবহার করুন।",
                summaryEn = "Stay cyber-safe: never share your banking PIN or OTP codes, avoid suspicious prize links, and use strong unique passwords."
            ),
            OfflineKnowledgeEntry(
                topic = "স্মার্টফোন ও ব্যাটারি ভালো রাখার উপায়",
                keywords = listOf("ব্যাটারি", "চার্জ", "স্টোরেজ", "ফোন গরম", "ফোন স্লো", "battery", "phone tips"),
                summaryBn = "ফোন সুস্থ রাখতে ২০%-৮০% চার্জের মধ্যে রাখার চেষ্টা করুন, আসল চার্জার ব্যবহার করুন, এবং মাঝে মাঝে অপ্রয়োজনীয় ক্যাশ ও ডুপ্লিকেট ফাইল মুছে ফেলুন।",
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
        private const val PACK_SCHEMA_VERSION = 4
        const val MAX_BRAIN_STORAGE_BYTES: Long = 2L * 1024L * 1024L * 1024L
        const val MIN_FREE_DISK_SAFETY_BYTES: Long = 512L * 1024L * 1024L
    }
}
