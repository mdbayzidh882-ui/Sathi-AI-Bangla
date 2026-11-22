package com.example.data

import com.example.model.SathiLanguage
import com.example.model.WebSource
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class WebSearchService {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchWeb(query: String, language: SathiLanguage): List<WebSource> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            val lower = trimmed.lowercase()

            // 1. Live weather query -> Open-Meteo Geocoding + Forecast (all Bangladesh + world)
            if (isWeatherQuery(lower)) {
                val weather = fetchLiveWeatherWithGeocoding(trimmed, language)
                if (weather != null) {
                    return@withContext listOf(weather)
                }
            }

            // 2. Live Islamic Prayer Times (নামাজের সময়সূচি)
            if (isPrayerTimeQuery(lower)) {
                fetchPrayerTimesSource(trimmed, language)?.let { prayerSource ->
                    return@withContext listOf(prayerSource)
                }
            }

            // 3. Live currency exchange rates (USD/EUR/GBP/SAR/AED/MYR/SGD/INR to BDT)
            if (isCurrencyQuery(lower)) {
                fetchExchangeRateSource(language)?.let { rateSource ->
                    return@withContext listOf(rateSource)
                }
            }

            val results = mutableListOf<WebSource>()

            // 4. Live news / sports / scores -> Google News RSS for Bangladesh + Global
            if (isNewsOrScoreQuery(lower)) {
                results.addAll(fetchGoogleNewsRss(trimmed, language))
            }

            // 5. Multi-engine web search (Bing RSS + Wikipedia Extracts + Wikidata + DuckDuckGo JSON + News fallback)
            val cleanTopic = extractSearchTopic(trimmed).ifBlank { trimmed.take(90) }
            val normalizedStem = stripBengaliSuffixes(cleanTopic)
            val wikiLang = if (language == SathiLanguage.BN || cleanTopic.any { it in '\u0980'..'\u09FF' }) "bn" else "en"

            if (cleanTopic.length >= 2) {
                fetchDuckDuckGoSource(cleanTopic)?.let { results.add(it) }

                // Wikipedia REST exact + Generator Extracts (returns clean 4-sentence intro paragraphs!)
                results.addAll(fetchWikipediaExtracts(normalizedStem.ifBlank { cleanTopic }, wikiLang))
                if (normalizedStem != cleanTopic && results.size < 2) {
                    results.addAll(fetchWikipediaExtracts(cleanTopic, wikiLang))
                }

                // Broad live web results via Bing Search RSS (fast XML, zero JS/CAPTCHA block)
                if (results.size < 3) {
                    results.addAll(fetchBingSearchRss(trimmed, language))
                }

                // Wikidata entity facts for people, places, terms, concepts
                if (results.size < 3) {
                    results.addAll(fetchWikidataEntities(normalizedStem.ifBlank { cleanTopic }, wikiLang))
                }

                // English Wikipedia fallback if Bengali query has more detail in English
                if (results.size < 2 && wikiLang == "bn") {
                    results.addAll(fetchWikipediaExtracts(cleanTopic, "en"))
                }

                // Google News RSS final live web fallback
                if (results.isEmpty()) {
                    results.addAll(fetchGoogleNewsRss(cleanTopic, language))
                }
            }

            val tokens = normalizedStem.lowercase()
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }

            results
                .distinctBy { it.url.ifBlank { it.title } }
                .sortedByDescending { src -> scoreRelevance(src, tokens) }
                .take(4)
        }

    private fun scoreRelevance(source: WebSource, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 1
        val titleLower = source.title.lowercase()
        val bodyLower = source.snippet.lowercase()
        var score = 0
        tokens.forEach { t ->
            if (titleLower.contains(t)) score += 3
            if (bodyLower.contains(t)) score += 1
        }
        return score
    }

    private fun extractSearchTopic(raw: String): String {
        val stopWords = setOf(
            "সার্চ", "খুঁজো", "খুঁজে", "দাও", "বলো", "বলুন", "বলে", "জানাও", "জানতে", "চাই",
            "দেখো", "দেখাও", "গুগল", "গুগলে", "অনলাইন", "অনলাইনে", "পারো", "পারবে",
            "সম্পর্কে", "বিষয়ে", "বিষয়ে", "নিয়ে", "নিয়ে", "কি", "কী", "কে", "কেন", "কবে",
            "কোথায়", "কোথায়", "কীভাবে", "কিভাবে", "কোন", "কোনটি", "কয়টি", "কয়টি", "কত",
            "কতটি", "হলো", "হল", "হয়", "হয়", "একটু", "দয়া", "করে", "আমাকে", "আমাদের",
            "দিয়ে", "দিয়ে", "থেকে", "এর", "এবং", "ও", "বা",
            "search", "google", "find", "tell", "me", "about", "what", "who", "where",
            "when", "why", "how", "many", "much", "which", "is", "was", "are", "were",
            "do", "does", "did", "can", "you", "explain", "define", "meaning", "of",
            "the", "a", "an", "in", "on", "for", "to", "with", "please"
        )
        return raw
            .replace(Regex("[?,!।\"'()]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { token -> token.lowercase() !in stopWords }
            .joinToString(" ")
            .trim()
            .take(90)
    }

    private fun stripBengaliSuffixes(topic: String): String {
        val suffixes = listOf("গুলোর", "গুলো", "দেরকে", "দের", "য়ের", "য়ের", "ের", "তে", "য়ে", "য়ে")
        return topic.split(Regex("\\s+"))
            .joinToString(" ") { word ->
                if (word.length <= 3) word
                else {
                    val matched = suffixes.firstOrNull { word.endsWith(it) && word.length - it.length >= 2 }
                    if (matched != null) word.dropLast(matched.length) else word
                }
            }
            .trim()
    }

    private fun isWeatherQuery(lower: String): Boolean =
        lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
            lower.contains("temperature") || lower.contains("তাপমাত্রা") || lower.contains("বৃষ্টি") ||
            lower.contains("গরম") || lower.contains("শীত")

    private fun isCurrencyQuery(lower: String): Boolean =
        lower.contains("dollar") || lower.contains("usd") || lower.contains("bdt") ||
            lower.contains("euro") || lower.contains("riyal") || lower.contains("dirham") ||
            lower.contains("ringgit") || lower.contains("pound") || lower.contains("exchange rate") ||
            lower.contains("currency") || lower.contains("ডলার") || lower.contains("রিয়াল") ||
            lower.contains("রিয়াল") || lower.contains("দিরহাম") || lower.contains("ইউরো") ||
            lower.contains("পাউন্ড") || lower.contains("রিঙ্গিত") ||
            ((lower.contains("টাকা") || lower.contains("রেট")) &&
                (lower.contains("দাম") || lower.contains("কত") || lower.contains("rate")))

    private fun isPrayerTimeQuery(lower: String): Boolean =
        lower.contains("নামাজ") || lower.contains("ফজর") || lower.contains("যোহর") ||
            lower.contains("জোহর") || lower.contains("আসর") || lower.contains("মাগরিব") ||
            lower.contains("এশা") || lower.contains("ইফতার") || lower.contains("সেহরি") ||
            lower.contains("prayer time") || lower.contains("namaz") || lower.contains("salah")

    private fun isNewsOrScoreQuery(lower: String): Boolean =
        lower.contains("news") || lower.contains("খবর") || lower.contains("সংবাদ") ||
            lower.contains("score") || lower.contains("স্কোর") || lower.contains("খেলা") ||
            lower.contains("cricket") || lower.contains("ক্রিকেট") || lower.contains("football") ||
            lower.contains("ফুটবল") || lower.contains("আজকের") || lower.contains("today")

    private fun fetchPrayerTimesSource(query: String, language: SathiLanguage): WebSource? {
        return try {
            val resolved = resolveLocation(query)
            val cityEng = resolved.displayName.substringBefore("/").substringBefore(",").trim().ifBlank { "Dhaka" }
            val encodedCity = URLEncoder.encode(cityEng, "UTF-8")
            val url = "https://api.aladhan.com/v1/timingsByCity?city=$encodedCity&country=Bangladesh&method=1"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val timings = JSONObject(body).optJSONObject("data")?.optJSONObject("timings") ?: return null
                val fajr = timings.optString("Fajr")
                val dhuhr = timings.optString("Dhuhr")
                val asr = timings.optString("Asr")
                val maghrib = timings.optString("Maghrib")
                val isha = timings.optString("Isha")
                if (fajr.isBlank()) return null

                val snippet = if (language == SathiLanguage.BN) {
                    "ফজর: $fajr • যোহর: $dhuhr • আসর: $asr • মাগরিব: $maghrib • এশা: $isha"
                } else {
                    "Fajr: $fajr • Dhuhr: $dhuhr • Asr: $asr • Maghrib: $maghrib • Isha: $isha"
                }
                WebSource(
                    title = if (language == SathiLanguage.BN) "${resolved.displayName} — আজকের নামাজের সময়সূচি" else "${resolved.displayName} — Prayer Times",
                    snippet = snippet,
                    url = "https://aladhan.com/"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchExchangeRateSource(language: SathiLanguage): WebSource? {
        return try {
            val req = Request.Builder()
                .url("https://open.er-api.com/v6/latest/USD")
                .header("User-Agent", "SathiAndroidApp/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val rates = JSONObject(body).optJSONObject("rates") ?: return null
                val bdtPerUsd = rates.optDouble("BDT", Double.NaN)
                val eurPerUsd = rates.optDouble("EUR", Double.NaN)
                val gbpPerUsd = rates.optDouble("GBP", Double.NaN)
                val sarPerUsd = rates.optDouble("SAR", Double.NaN)
                val aedPerUsd = rates.optDouble("AED", Double.NaN)
                val myrPerUsd = rates.optDouble("MYR", Double.NaN)
                val inrPerUsd = rates.optDouble("INR", Double.NaN)
                if (bdtPerUsd.isNaN()) return null

                val bdtPerEur = if (!eurPerUsd.isNaN() && eurPerUsd > 0) bdtPerUsd / eurPerUsd else 0.0
                val bdtPerGbp = if (!gbpPerUsd.isNaN() && gbpPerUsd > 0) bdtPerUsd / gbpPerUsd else 0.0
                val bdtPerSar = if (!sarPerUsd.isNaN() && sarPerUsd > 0) bdtPerUsd / sarPerUsd else 0.0
                val bdtPerAed = if (!aedPerUsd.isNaN() && aedPerUsd > 0) bdtPerUsd / aedPerUsd else 0.0
                val bdtPerMyr = if (!myrPerUsd.isNaN() && myrPerUsd > 0) bdtPerUsd / myrPerUsd else 0.0
                val bdtPerInr = if (!inrPerUsd.isNaN() && inrPerUsd > 0) bdtPerUsd / inrPerUsd else 0.0

                val snippet = if (language == SathiLanguage.BN) {
                    buildString {
                        append("১ ডলার (USD) ≈ ${String.format("%.2f", bdtPerUsd)} টাকা")
                        if (bdtPerEur > 0) append(" • ১ ইউরো ≈ ${String.format("%.2f", bdtPerEur)} টাকা")
                        if (bdtPerGbp > 0) append(" • ১ পাউন্ড ≈ ${String.format("%.2f", bdtPerGbp)} টাকা")
                        if (bdtPerSar > 0) append(" • ১ রিয়াল ≈ ${String.format("%.2f", bdtPerSar)} টাকা")
                        if (bdtPerAed > 0) append(" • ১ দিরহাম ≈ ${String.format("%.2f", bdtPerAed)} টাকা")
                        if (bdtPerMyr > 0) append(" • ১ রিঙ্গিত ≈ ${String.format("%.2f", bdtPerMyr)} টাকা")
                        if (bdtPerInr > 0) append(" • ১ রুপি ≈ ${String.format("%.2f", bdtPerInr)} টাকা")
                    }
                } else {
                    buildString {
                        append("1 USD ≈ ${String.format("%.2f", bdtPerUsd)} BDT")
                        if (bdtPerEur > 0) append(" • 1 EUR ≈ ${String.format("%.2f", bdtPerEur)} BDT")
                        if (bdtPerGbp > 0) append(" • 1 GBP ≈ ${String.format("%.2f", bdtPerGbp)} BDT")
                        if (bdtPerSar > 0) append(" • 1 SAR ≈ ${String.format("%.2f", bdtPerSar)} BDT")
                        if (bdtPerAed > 0) append(" • 1 AED ≈ ${String.format("%.2f", bdtPerAed)} BDT")
                    }
                }
                WebSource(
                    title = if (language == SathiLanguage.BN) "লাইভ মুদ্রা বিনিময় হার (Exchange Rate)" else "Live Currency Exchange Rates",
                    snippet = snippet,
                    url = "https://www.exchangerate-api.com/"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLiveWeatherWithGeocoding(query: String, language: SathiLanguage): WebSource? {
        return try {
            val resolved = resolveLocation(query)
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${resolved.lat}&longitude=${resolved.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JSONObject(body)
                val current = root.optJSONObject("current") ?: return null
                val daily = root.optJSONObject("daily")
                val todayMax = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, Double.NaN) ?: Double.NaN
                val todayMin = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, Double.NaN) ?: Double.NaN
                val temp = current.optDouble("temperature_2m", Double.NaN)
                val feels = current.optDouble("apparent_temperature", temp)
                val humidity = current.optInt("relative_humidity_2m", -1)
                val wind = current.optDouble("wind_speed_10m", Double.NaN)
                val wCode = current.optInt("weather_code", 0)
                if (temp.isNaN()) return null

                val condBn = weatherCodeToBn(wCode)
                val condEn = weatherCodeToEn(wCode)
                val rangeBn = if (!todayMax.isNaN() && !todayMin.isNaN()) " (সর্বোচ্চ ${todayMax}°C / সর্বনিম্ন ${todayMin}°C)" else ""
                val rangeEn = if (!todayMax.isNaN() && !todayMin.isNaN()) " (High ${todayMax}°C / Low ${todayMin}°C)" else ""

                val title = if (language == SathiLanguage.BN) {
                    "${resolved.displayName} - লাইভ আবহাওয়া"
                } else {
                    "${resolved.displayName} Live Weather"
                }
                val snippet = if (language == SathiLanguage.BN) {
                    "অবস্থা: $condBn • তাপমাত্রা: ${temp}°C (অনুভূত ${feels}°C)$rangeBn, আর্দ্রতা: ${humidity}%, বাতাস: ${wind} km/h।"
                } else {
                    "Condition: $condEn • Temp: ${temp}°C (feels ${feels}°C)$rangeEn, Humidity: ${humidity}%, Wind: ${wind} km/h."
                }
                WebSource(
                    title = title,
                    snippet = snippet,
                    url = "https://open-meteo.com/"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class ResolvedCity(val displayName: String, val lat: Double, val lon: Double)

    private fun resolveLocation(query: String): ResolvedCity {
        val lower = query.lowercase()
        for ((pattern, englishName, coords) in bdDistrictEntries) {
            if (lower.contains(pattern)) {
                return ResolvedCity(englishName, coords.first, coords.second)
            }
        }
        val candidate = stripBengaliSuffixes(extractCityCandidate(query))
        if (candidate.isNotBlank()) {
            geocodeCity(candidate)?.let { return it }
        }
        return ResolvedCity("Dhaka / ঢাকা", 23.8103, 90.4125)
    }

    private fun extractCityCandidate(raw: String): String {
        return raw
            .replace(Regex("(weather|forecast|temperature|prayer|namaz|today|now|in|of|at)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(আবহাওয়া|আবহাওয়া|তাপমাত্রা|নামাজের|নামাজ|সময়সূচি|সময়|কেমন|আজকের|আজ|এখন|বৃষ্টি|হবে|কি|কী|বলো|জানাও|জেলায়|শহরে)"), " ")
            .replace(Regex("[?,!।]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(50)
    }

    private fun geocodeCity(name: String): ResolvedCity? {
        return try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val first = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
                val city = first.optString("name").trim()
                val country = first.optString("country").trim()
                val lat = first.optDouble("latitude", Double.NaN)
                val lon = first.optDouble("longitude", Double.NaN)
                if (city.isBlank() || lat.isNaN() || lon.isNaN()) return null
                val label = if (country.isNotBlank()) "$city, $country" else city
                ResolvedCity(label, lat, lon)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchBingSearchRss(query: String, language: SathiLanguage): List<WebSource> {
        return try {
            val encoded = URLEncoder.encode(query.take(110), "UTF-8")
            val setLang = if (language == SathiLanguage.BN) "bn-BD" else "en-US"
            val url = "https://www.bing.com/search?q=$encoded&format=rss&setlang=$setLang"
            val req = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                )
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val xml = resp.body?.string() ?: return emptyList()
                parseRssItems(xml, defaultFallbackLabel = "Bing Web Search").take(3)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchGoogleNewsRss(query: String, language: SathiLanguage): List<WebSource> {
        return try {
            val cleanQuery = query
                .replace(Regex("(আজকের|খবর|সংবাদ|জানাও|বলো|দাও|news|latest|today)", RegexOption.IGNORE_CASE), "")
                .trim()
            val url = if (cleanQuery.isBlank() || cleanQuery.length < 3) {
                if (language == SathiLanguage.BN) {
                    "https://news.google.com/rss?hl=bn&gl=BD&ceid=BD:bn"
                } else {
                    "https://news.google.com/rss?hl=en-BD&gl=BD&ceid=BD:en"
                }
            } else {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                if (language == SathiLanguage.BN) {
                    "https://news.google.com/rss/search?q=$encoded&hl=bn&gl=BD&ceid=BD:bn"
                } else {
                    "https://news.google.com/rss/search?q=$encoded&hl=en-BD&gl=BD&ceid=BD:en"
                }
            }

            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val xml = resp.body?.string() ?: return emptyList()
                parseRssItems(xml, defaultFallbackLabel = "Google News Bangladesh").take(4)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRssItems(xml: String, defaultFallbackLabel: String): List<WebSource> {
        val items = mutableListOf<WebSource>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(xml.reader())
            var inItem = false
            var currentTag = ""
            var title = ""
            var link = ""
            var description = ""
            var pubDate = ""
            var sourceName = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT && items.size < 4) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.orEmpty()
                        if (currentTag == "item") {
                            inItem = true
                            title = ""
                            link = ""
                            description = ""
                            pubDate = ""
                            sourceName = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty()
                            when (currentTag) {
                                "title" -> title += text
                                "link" -> link += text
                                "description" -> description += text
                                "pubDate" -> pubDate += text
                                "source" -> sourceName += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && inItem) {
                            val cleanTitle = stripHtml(title)
                            val cleanDesc = stripHtml(description)
                            if (cleanTitle.isNotBlank() && link.isNotBlank()) {
                                val meta = listOf(sourceName.trim(), pubDate.take(16).trim())
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                val chosenSnippet = when {
                                    cleanDesc.length >= 20 && !cleanDesc.equals(cleanTitle, ignoreCase = true) ->
                                        cleanDesc.take(280)
                                    meta.isNotBlank() -> meta
                                    else -> defaultFallbackLabel
                                }
                                items.add(
                                    WebSource(
                                        title = cleanTitle,
                                        snippet = chosenSnippet,
                                        url = link.trim()
                                    )
                                )
                            }
                            inItem = false
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return items
    }

    private fun fetchWikipediaExtracts(topic: String, langCode: String): List<WebSource> {
        return try {
            val cleaned = topic.replace(Regex("[?,!।]+"), " ").trim()
            if (cleaned.length < 2) return emptyList()

            // 1. Direct REST exact title summary first (highest accuracy, zero random hits)
            val exactSlug = URLEncoder.encode(cleaned.replace(" ", "_"), "UTF-8")
            val summaryUrl = "https://$langCode.wikipedia.org/api/rest_v1/page/summary/$exactSlug"
            val summaryReq = Request.Builder().url(summaryUrl).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(summaryReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val type = json.optString("type")
                    val title = json.optString("title").trim()
                    val extract = json.optString("extract").trim()
                    if (type != "disambiguation" && title.isNotBlank() && extract.length > 25) {
                        val pageUrl = "https://$langCode.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                        return listOf(WebSource(title = title, snippet = extract.take(420), url = pageUrl))
                    }
                }
            }

            // 2. Generator Search + Extracts API (returns clean full multi-sentence intro without HTML!)
            val topicTokens = cleaned.lowercase()
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val url = "https://$langCode.wikipedia.org/w/api.php" +
                "?action=query&generator=search&gsrsearch=$encoded&gsrlimit=4" +
                "&prop=extracts|info&exintro=1&explaintext=1&exsentences=4&redirects=1&inprop=url&format=json&utf8=1"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val pagesObj = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                    ?: return emptyList()

                buildList {
                    val keys = pagesObj.keys()
                    while (keys.hasNext()) {
                        val page = pagesObj.optJSONObject(keys.next()) ?: continue
                        val title = page.optString("title").trim()
                        val extract = page.optString("extract").replace(Regex("\\s+"), " ").trim()
                        val pageUrl = page.optString("fullurl").ifBlank {
                            "https://$langCode.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                        }
                        val combinedLower = "$title $extract".lowercase()
                        val isRelevant = topicTokens.any { token -> combinedLower.contains(token) }
                        if (title.isNotEmpty() && extract.length > 20 && isRelevant) {
                            add(WebSource(title = title, snippet = extract.take(420), url = pageUrl))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchWikidataEntities(topic: String, langCode: String): List<WebSource> {
        return try {
            val cleaned = topic.replace(Regex("[?,!।]+"), " ").trim()
            if (cleaned.length < 2) return emptyList()
            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val url = "https://www.wikidata.org/w/api.php" +
                "?action=wbsearchentities&search=$encoded&language=$langCode&uselang=$langCode&format=json&limit=3"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val searchArr = JSONObject(body).optJSONArray("search") ?: return emptyList()
                buildList {
                    for (i in 0 until searchArr.length()) {
                        val item = searchArr.optJSONObject(i) ?: continue
                        val label = item.optString("label").trim()
                        val desc = item.optString("description").trim()
                        val conceptUri = item.optString("concepturi").trim()
                        if (label.isNotBlank() && desc.isNotBlank()) {
                            add(
                                WebSource(
                                    title = label,
                                    snippet = desc,
                                    url = conceptUri.ifBlank { "https://www.wikidata.org/" }
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchDuckDuckGoSource(query: String): WebSource? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText").trim()
                val abstractUrl = json.optString("AbstractURL").trim()
                val heading = json.optString("Heading").trim()
                if (abstractText.isNotEmpty() && abstractUrl.isNotEmpty()) {
                    WebSource(
                        title = heading.ifBlank { query },
                        snippet = abstractText.take(360),
                        url = abstractUrl
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stripHtml(raw: String): String =
        decodeHtmlEntities(raw.replace(Regex("<[^>]*>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun decodeHtmlEntities(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

    private fun weatherCodeToBn(code: Int): String = when (code) {
        0 -> "পরিষ্কার আকাশ"
        1, 2 -> "হালকা মেঘলা"
        3 -> "মেঘলা আকাশ"
        45, 48 -> "কুয়াশা"
        51, 53, 55, 61, 63, 65 -> "বৃষ্টি"
        80, 81, 82 -> "দমকা বৃষ্টি"
        95, 96, 99 -> "বজ্রসহ বৃষ্টি"
        else -> "স্বাভাবিক আবহাওয়া"
    }

    private fun weatherCodeToEn(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55, 61, 63, 65 -> "Rain"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Moderate weather"
    }

    private data class BdDistrictEntry(val pattern: String, val displayName: String, val coords: Pair<Double, Double>)

    private val bdDistrictEntries = listOf(
        BdDistrictEntry("খুলনা", "Khulna / খুলনা", 22.8456 to 89.5403),
        BdDistrictEntry("khulna", "Khulna / খুলনা", 22.8456 to 89.5403),
        BdDistrictEntry("চট্টগ্রাম", "Chattogram / চট্টগ্রাম", 22.3569 to 91.7832),
        BdDistrictEntry("chattogram", "Chattogram / চট্টগ্রাম", 22.3569 to 91.7832),
        BdDistrictEntry("chittagong", "Chattogram / চট্টগ্রাম", 22.3569 to 91.7832),
        BdDistrictEntry("সিলেট", "Sylhet / সিলেট", 24.8949 to 91.8687),
        BdDistrictEntry("sylhet", "Sylhet / সিলেট", 24.8949 to 91.8687),
        BdDistrictEntry("রাজশাহী", "Rajshahi / রাজশাহী", 24.3745 to 88.6042),
        BdDistrictEntry("rajshahi", "Rajshahi / রাজশাহী", 24.3745 to 88.6042),
        BdDistrictEntry("বরিশাল", "Barishal / বরিশাল", 22.7010 to 90.3535),
        BdDistrictEntry("barishal", "Barishal / বরিশাল", 22.7010 to 90.3535),
        BdDistrictEntry("barisal", "Barishal / বরিশাল", 22.7010 to 90.3535),
        BdDistrictEntry("রংপুর", "Rangpur / রংপুর", 25.7439 to 89.2752),
        BdDistrictEntry("rangpur", "Rangpur / রংপুর", 25.7439 to 89.2752),
        BdDistrictEntry("ময়মনসিংহ", "Mymensingh / ময়মনসিংহ", 24.7471 to 90.4203),
        BdDistrictEntry("ময়মনসিংহ", "Mymensingh / ময়মনসিংহ", 24.7471 to 90.4203),
        BdDistrictEntry("mymensingh", "Mymensingh / ময়মনসিংহ", 24.7471 to 90.4203),
        BdDistrictEntry("কুমিল্লা", "Cumilla / কুমিল্লা", 23.4607 to 91.1809),
        BdDistrictEntry("cumilla", "Cumilla / কুমিল্লা", 23.4607 to 91.1809),
        BdDistrictEntry("comilla", "Cumilla / কুমিল্লা", 23.4607 to 91.1809),
        BdDistrictEntry("কক্সবাজার", "Cox's Bazar / কক্সবাজার", 21.4272 to 92.0058),
        BdDistrictEntry("cox", "Cox's Bazar / কক্সবাজার", 21.4272 to 92.0058),
        BdDistrictEntry("বগুড়া", "Bogura / বগুড়া", 24.8465 to 89.3773),
        BdDistrictEntry("বগুড়া", "Bogura / বগুড়া", 24.8465 to 89.3773),
        BdDistrictEntry("bogura", "Bogura / বগুড়া", 24.8465 to 89.3773),
        BdDistrictEntry("যশোর", "Jashore / যশোর", 23.1664 to 89.2081),
        BdDistrictEntry("jashore", "Jashore / যশোর", 23.1664 to 89.2081),
        BdDistrictEntry("jessore", "Jashore / যশোর", 23.1664 to 89.2081),
        BdDistrictEntry("সাতক্ষীরা", "Satkhira / সাতক্ষীরা", 22.7185 to 89.0705),
        BdDistrictEntry("satkhira", "Satkhira / সাতক্ষীরা", 22.7185 to 89.0705),
        BdDistrictEntry("বাগেরহাট", "Bagerhat / বাগেরহাট", 22.6516 to 89.7859),
        BdDistrictEntry("bagerhat", "Bagerhat / বাগেরহাট", 22.6516 to 89.7859),
        BdDistrictEntry("গাজীপুর", "Gazipur / গাজীপুর", 23.9999 to 90.4203),
        BdDistrictEntry("gazipur", "Gazipur / গাজীপুর", 23.9999 to 90.4203),
        BdDistrictEntry("নারায়ণগঞ্জ", "Narayanganj / নারায়ণগঞ্জ", 23.6238 to 90.5000),
        BdDistrictEntry("নারায়ণগঞ্জ", "Narayanganj / নারায়ণগঞ্জ", 23.6238 to 90.5000),
        BdDistrictEntry("narayanganj", "Narayanganj / নারায়ণগঞ্জ", 23.6238 to 90.5000),
        BdDistrictEntry("টাঙ্গাইল", "Tangail / টাঙ্গাইল", 24.2513 to 89.9167),
        BdDistrictEntry("tangail", "Tangail / টাঙ্গাইল", 24.2513 to 89.9167),
        BdDistrictEntry("ফরিদপুর", "Faridpur / ফরিদপুর", 23.6071 to 89.8429),
        BdDistrictEntry("faridpur", "Faridpur / ফরিদপুর", 23.6071 to 89.8429),
        BdDistrictEntry("নোয়াখালী", "Noakhali / নোয়াখালী", 22.8696 to 91.0994),
        BdDistrictEntry("নোয়াখালী", "Noakhali / নোয়াখালী", 22.8696 to 91.0994),
        BdDistrictEntry("noakhali", "Noakhali / নোয়াখালী", 22.8696 to 91.0994),
        BdDistrictEntry("দিনাজপুর", "Dinajpur / দিনাজপুর", 25.6279 to 88.6332),
        BdDistrictEntry("dinajpur", "Dinajpur / দিনাজপুর", 25.6279 to 88.6332),
        BdDistrictEntry("পাবনা", "Pabna / পাবনা", 24.0064 to 89.2372),
        BdDistrictEntry("pabna", "Pabna / পাবনা", 24.0064 to 89.2372),
        BdDistrictEntry("কুষ্টিয়া", "Kushtia / কুষ্টিয়া", 23.9013 to 89.1205),
        BdDistrictEntry("কুষ্টিয়া", "Kushtia / কুষ্টিয়া", 23.9013 to 89.1205),
        BdDistrictEntry("kushtia", "Kushtia / কুষ্টিয়া", 23.9013 to 89.1205),
        BdDistrictEntry("নড়াইল", "Narail / নড়াইল", 23.1725 to 89.5127),
        BdDistrictEntry("নড়াইল", "Narail / নড়াইল", 23.1725 to 89.5127),
        BdDistrictEntry("narail", "Narail / নড়াইল", 23.1725 to 89.5127),
        BdDistrictEntry("ফেনী", "Feni / ফেনী", 23.0159 to 91.3976),
        BdDistrictEntry("feni", "Feni / ফেনী", 23.0159 to 91.3976),
        BdDistrictEntry("কলকাতা", "Kolkata / কলকাতা", 22.5726 to 88.3639),
        BdDistrictEntry("kolkata", "Kolkata / কলকাতা", 22.5726 to 88.3639),
        BdDistrictEntry("ঢাকা", "Dhaka / ঢাকা", 23.8103 to 90.4125),
        BdDistrictEntry("dhaka", "Dhaka / ঢাকা", 23.8103 to 90.4125)
    )
}
