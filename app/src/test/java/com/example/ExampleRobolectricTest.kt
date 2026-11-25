package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineCompanionEngine
import com.example.data.PlaceholderSathiImageGenRepository
import com.example.data.SathiAiService
import com.example.data.SathiDatabase
import com.example.data.SathiLocalRepository
import com.example.model.CodeType
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.UserEmotion
import com.example.model.UserProfile
import com.example.overlay.SathiWakeWordService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app opens directly on home screen with Sathi AI title, top quick questions, drawer, new chat, mic, auto-voice toggle, emotion bar, media plus button, and no ad section`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("সাথী এআই • Sathi AI").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_quick_questions_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_chat_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("center_ai_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("emotion_chip_sad").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_voice_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attach_media_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_banner_ad").assertDoesNotExist()
    }

    @Test
    fun `app name is Sathi AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("সাথী এআই", appName)
    }

    @Test
    fun `sathi ai service answers identity, math, and rotating stories without Hindu greetings`() = runBlocking {
        val ai = SathiAiService()

        val nameTurn = ai.generateCompanionReply(
            userMessage = "তোমার নাম কী?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, nameTurn.actionType)
        assertTrue(nameTurn.replyText.contains("সাথী"))
        assertTrue(nameTurn.replyText.contains("আসসালামু আলাইকুম"))
        assertFalse(nameTurn.replyText.contains("নমস্কার"))

        val whoTurn = ai.generateCompanionReply(
            userMessage = "তুমি কে?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, whoTurn.actionType)
        assertTrue(whoTurn.replyText.contains("মোহাম্মদ বাইজিদ হাসান"))

        val timeTurn = ai.generateCompanionReply(
            userMessage = "এখন কয়টা বাজে",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, timeTurn.actionType)
        assertTrue(timeTurn.replyText.contains("মিনিট"))
        assertFalse(timeTurn.replyText.contains("অভিনেত্রী"))

        val casualTurn = ai.generateCompanionReply(
            userMessage = "কি করতেছো এখন",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, casualTurn.actionType)

        val testBmp = android.graphics.Bitmap.createBitmap(120, 90, android.graphics.Bitmap.Config.ARGB_8888)
        testBmp.eraseColor(android.graphics.Color.rgb(40, 140, 220))
        val imgAnalysis = ai.analyzeImage(
            bitmap = testBmp,
            question = "এই ছবিতে কী আছে?",
            language = SathiLanguage.BN,
            isScreenCapture = false
        ).getOrThrow()
        assertTrue(imgAnalysis.contains("120×90"))

        val mathTurn = ai.generateCompanionReply(
            userMessage = "২৫ + ১৫ কত",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(mathTurn.replyText.contains("40"))

        val story1 = ai.generateCompanionReply(
            userMessage = "একটি গল্প শোনাও",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        val story2 = ai.generateCompanionReply(
            userMessage = "আরেকটা গল্প বলো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertNotEquals(story1.replyText, story2.replyText)
    }

    @Test
    fun `offline engine returns creator rule and offline actions without namaskar`() {
        val engine = OfflineCompanionEngine()

        val greetReply = engine.respondOffline("সালাম", SathiLanguage.BN, "Bayzid")
        assertTrue(greetReply.replyText.contains("আসসালামু আলাইকুম") || greetReply.replyText.contains("ওয়ালাইকুম"))
        assertFalse(greetReply.replyText.contains("নমস্কার"))

        val creatorReply = engine.respondOffline("তোমাকে কে বানিয়েছে?", SathiLanguage.BN, "Bayzid")
        assertTrue(creatorReply.replyText.contains("মোহাম্মদ বাইজিদ হাসান"))

        val searchReply = engine.respondOffline("আজকের খবর সার্চ করো", SathiLanguage.BN, "Bayzid")
        assertEquals(PhoneActionType.NONE, searchReply.actionType)
        assertTrue(searchReply.replyText.contains("অফলাইন"))

        val flashOn = engine.respondOffline("Turn flashlight on", SathiLanguage.EN, "Bayzid")
        assertEquals(PhoneActionType.FLASHLIGHT_ON, flashOn.actionType)

        val callReply = engine.respondOffline("Call 01700000000", SathiLanguage.EN, "Bayzid")
        assertEquals(PhoneActionType.CALL_CONFIRM, callReply.actionType)
    }

    @Test
    fun `signed premium code can be generated and redeemed even on another device database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, SathiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val db2 = Room.inMemoryDatabaseBuilder(context, SathiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val adminRepo = SathiLocalRepository(db1.sathiDao())
            val userRepoOnOtherDevice = SathiLocalRepository(db2.sathiDao())

            val code = adminRepo.generatePremiumCode(
                adminUid = "admin",
                adminEmail = SathiLocalRepository.OWNER_EMAIL,
                codeType = CodeType.ONE_MONTH,
                note = "Friend test"
            ).getOrThrow()
            assertTrue(code.startsWith("SATHI-1M-"))

            userRepoOnOtherDevice.ensureUserProfile("u_friend", "Friend", "f@test.com").getOrThrow()
            val tier = userRepoOnOtherDevice.redeemPremiumCode("u_friend", code).getOrThrow()
            assertEquals(PlanTier.PREMIUM, tier)

            val updatedProfile = userRepoOnOtherDevice.observeUserProfile("u_friend").first()
            assertEquals(PlanTier.PREMIUM, updatedProfile?.effectivePlan())
        } finally {
            db1.close()
            db2.close()
        }
    }

    @Test
    fun `expired premium reverts to free tier`() {
        val expired = UserProfile(
            userId = "u1",
            userName = "Test",
            plan = "premium",
            planExpiresAt = System.currentTimeMillis() - 10_000L
        )
        assertEquals(PlanTier.FREE, expired.effectivePlan())
        assertFalse(expired.hasPremiumAccess())

        val owner = UserProfile(userId = "u2", userName = "Bayzid", plan = "owner", planExpiresAt = 0L)
        assertEquals(PlanTier.OWNER, owner.effectivePlan())
        assertTrue(owner.hasPremiumAccess())
    }

    @Test
    fun `chat sessions persist, pin at top, and save message reactions`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SathiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = SathiLocalRepository(db.sathiDao())
            val s1 = repo.createChatSession("u1", "খুলনার আবহাওয়া")
            val s2 = repo.createChatSession("u1", "ইসলামের ইতিহাস")
            repo.togglePinChatSession(s1.id, true)

            val sessions = repo.observeChatSessions("u1").first()
            assertEquals(2, sessions.size)
            assertEquals(s1.id, sessions.first().id)
            assertTrue(sessions.first().isPinned)

            val aiMsg = com.example.model.ChatMessage(
                id = "m_1",
                isUser = false,
                text = "খুলনায় আজ রৌদ্রোজ্জ্বল আবহাওয়া।",
                sessionId = s1.id
            )
            repo.upsertChatMessage("u1", s1.id, aiMsg)
            repo.updateMessageReaction("m_1", 1)

            val loadedMsgs = repo.getMessagesForSessionOnce(s1.id)
            assertEquals(1, loadedMsgs.size)
            assertEquals(1, loadedMsgs.first().reaction)

            val ai = SathiAiService()
            val followUp = ai.generateCompanionReply(
                userMessage = "আরও বলো",
                language = SathiLanguage.BN,
                userName = "বাইজিদ",
                memories = emptyList(),
                recentHistory = listOf(true to "বাংলাদেশের অর্থনীতি কেমন?")
            ).getOrThrow()
            assertEquals(PhoneActionType.WEB_SEARCH, followUp.actionType)
            assertTrue(followUp.actionParam.contains("বাংলাদেশের অর্থনীতি"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `wake word, thamo barge-in, social apps, emotions, and genre stories work correctly`() = runBlocking {
        assertTrue(SathiWakeWordService.matchesTripleSathi("সাথী সাথী সাথী"))
        assertTrue(SathiWakeWordService.matchesTripleSathi("Sathi Sathi Sathi"))
        assertFalse(SathiWakeWordService.matchesTripleSathi("সাথী কেমন আছো"))

        val ai = SathiAiService()

        // Thamo barge-in only
        val stopOnly = ai.generateCompanionReply(
            userMessage = "থামো থামো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(stopOnly.replyText.contains("থেমে"))

        // Thamo + follow-up command
        val stopAndAsk = ai.generateCompanionReply(
            userMessage = "থামো থামো তোমার নাম কী?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(stopAndAsk.replyText.contains("সাথী"))

        // Social open & social comment/message confirm
        val fbOpen = ai.generateCompanionReply(
            userMessage = "ফেসবুকে ঢোকো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.OPEN_APP, fbOpen.actionType)
        assertEquals("Facebook", fbOpen.actionParam)

        val waMsg = ai.generateCompanionReply(
            userMessage = "WhatsApp এ একটা মেসেজ পাঠাও আমি আসছি",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, waMsg.actionType)

        // Emotion awareness
        val sadReply = ai.generateCompanionReply(
            userMessage = "আজ কিছুই ভালো লাগছে না",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList(),
            userEmotion = UserEmotion.SAD
        ).getOrThrow()
        assertTrue(sadReply.replyText.isNotBlank())

        // Genre-specific stories
        val islamicStory = ai.generateCompanionReply(
            userMessage = "একটি ইসলামিক গল্প বলো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(islamicStory.replyText.contains("উমর") || islamicStory.replyText.contains("আল্লাহ"))

        val ghostStory = ai.generateCompanionReply(
            userMessage = "একটি ভূতের গল্প বলো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(ghostStory.replyText.contains("স্টেশন"))
    }

    @Test
    fun `upgraded factual search, friend WhatsApp or Messenger SMS and reply, image preview, and expanded offline mode work accurately`() = runBlocking {
        val ai = SathiAiService()

        // 1. Accurate built-in verified quick fact (no wrong answer)
        val fruitTurn = ai.generateCompanionReply(
            userMessage = "বাংলাদেশের জাতীয় ফল কী?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertTrue(fruitTurn.replyText.contains("কাঁঠাল"))

        // 2. General factual question routes cleanly to WEB_SEARCH
        val searchTurn = ai.generateCompanionReply(
            userMessage = "সূর্য থেকে পৃথিবীর দূরত্ব কত?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.WEB_SEARCH, searchTurn.actionType)

        // 3. Lightweight image generation routes to GENERATE_IMAGE + Placeholder repo marks Sample Preview
        val imgTurn = ai.generateCompanionReply(
            userMessage = "একটি সুন্দর সূর্যাস্তের ছবি তৈরি করো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.GENERATE_IMAGE, imgTurn.actionType)
        assertTrue(imgTurn.actionParam.isNotBlank())

        val imgRepo = PlaceholderSathiImageGenRepository()
        val imgOutcome = imgRepo.generateImage(imgTurn.actionParam, SathiLanguage.BN).getOrThrow()
        assertTrue(imgOutcome.isPlaceholderPreview)
        assertTrue(imgOutcome.bitmap.width > 100)
        assertTrue(imgOutcome.requiresFirebaseNotice.contains("ডাউনলোড"))

        // 4. WhatsApp friend SMS & Messenger reply extraction
        val waFriendTurn = ai.generateCompanionReply(
            userMessage = "হোয়াটসঅ্যাপ খুলে আমার বন্ধু রহিমকে একটি এসএমএস করো ওকে জানাও আমি কাল আসব",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, waFriendTurn.actionType)
        assertEquals("WhatsApp", waFriendTurn.actionParam)
        assertEquals("রহিম", waFriendTurn.recipientParam)
        assertTrue(waFriendTurn.secondaryParam.contains("আমি কাল আসব"))

        val messengerReplyTurn = ai.generateCompanionReply(
            userMessage = "মেসেঞ্জার অন করে করিমকে রিপ্লাই দাও ঠিক আছে ভাই",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, messengerReplyTurn.actionType)
        assertEquals("Messenger", messengerReplyTurn.actionParam)
        assertEquals("করিম", messengerReplyTurn.recipientParam)
        assertTrue(messengerReplyTurn.secondaryParam.contains("ঠিক আছে"))

        // 5. Upgraded offline engine unit conversion, jokes, and social actions
        val offlineEngine = OfflineCompanionEngine()
        val unitTurn = offlineEngine.respondOffline("৫ কেজি কত গ্রাম", SathiLanguage.BN, "বাইজিদ")
        assertTrue(unitTurn.replyText.contains("5000"))

        val jokeTurn = offlineEngine.respondOffline("একটি মজার কৌতুক বলো", SathiLanguage.BN, "বাইজিদ")
        assertTrue(jokeTurn.replyText.isNotBlank())

        val offlineFriendWa = offlineEngine.respondOffline(
            "WhatsApp এ আমার বন্ধু হাসানকে মেসেজ পাঠাও কেমন আছো",
            SathiLanguage.BN,
            "বাইজিদ"
        )
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, offlineFriendWa.actionType)
        assertEquals("হাসান", offlineFriendWa.recipientParam)
    }

    @Test
    fun `banner ad section is disabled by default`() {
        val defaultConfig = com.example.model.AppConfig()
        assertFalse(defaultConfig.bannerAdEnabled)
        assertEquals(null, com.example.ui.sanitizeBannerHttpsUrl("javascript:alert(1)"))
        assertEquals(null, com.example.ui.sanitizeBannerHttpsUrl("file:///etc/passwd"))
        assertEquals(null, com.example.ui.sanitizeBannerHttpsUrl("https://127.0.0.1/test"))
    }

    @Test
    fun `admin google key verification validates input, sanitizes pasted keys, masks preview, and persists in preferences`() = runBlocking {
        val ai = SathiAiService()
        val blankResult = ai.verifyGoogleApiKey("   ", SathiLanguage.BN)
        assertTrue(blankResult.isFailure)

        assertEquals(
            "AIzaSyTestKey1234567890",
            SathiAiService.sanitizeApiKeyInput("  \"AIzaSyTestKey\n\t 1234567890\" \u200B ")
        )
        assertEquals("AIza••••••7890", com.example.ui.maskApiKeyPreview("AIzaSyDemoKey1234567890"))
        assertEquals("", com.example.ui.maskApiKeyPreview("   "))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.core.SathiPreferences(context)
        prefs.saveCustomGeminiApiKey("  AIzaTestSample1234567890  ")
        assertEquals("AIzaTestSample1234567890", prefs.settingsFlow.first().customGeminiApiKey)
        prefs.saveCustomGeminiApiKey("")
        assertEquals("", prefs.settingsFlow.first().customGeminiApiKey)
    }

    @Test
    fun `capability questions never trigger web search and web relevance blocks unrelated articles`() = runBlocking {
        val ai = SathiAiService()
        val cap1 = ai.generateCompanionReply(
            userMessage = "তুমি কি ওয়েব সার্চ করতে পারো?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, cap1.actionType)
        assertTrue(cap1.replyText.contains("সাথী এআই"))
        assertFalse(cap1.replyText.contains("চলচ্চিত্র"))

        val cap2 = ai.generateCompanionReply(
            userMessage = "তুমি কি গুগল সার্চ করতে পারো?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.NONE, cap2.actionType)

        // Ensure unrelated Bengali film result has 0 relevance score for a Google search capability query
        val ws = com.example.data.WebSearchService()
        val score = ws.scoreRelevance(
            source = com.example.model.WebSource(
                title = "বাংলাদেশের চলচ্চিত্র তালিকা",
                snippet = "১৯৫৬ সালে মুক্তিপ্রাপ্ত চলচ্চিত্র।",
                url = "https://bn.wikipedia.org/"
            ),
            tokens = listOf("কোয়ান্টাম", "কম্পিউটার")
        )
        assertEquals(0, score)

        // Ensure all-Bangladesh news helper uses whole Bangladesh, not Khulna only
        val offline = OfflineCompanionEngine()
        val offlineNews = offline.respondOffline("আজকের খবর বলো", SathiLanguage.BN, "বাইজিদ")
        assertEquals(PhoneActionType.NONE, offlineNews.actionType)
        assertTrue(offlineNews.replyText.contains("বাংলাদেশ"))
    }

    @Test
    fun `hands-free barge-in, day-night theme persistence, 2GB brain budget, reference image generation, and key extraction work`() = runBlocking {
        // 1. Voice barge-in RMS detector: triggers on sustained human speech above baseline, ignores low/short echo
        assertTrue(
            com.example.data.VoiceController.shouldTriggerVoiceBargeIn(
                recentWindowRms = listOf(2900.0, 3200.0, 3050.0),
                baselineRms = 750.0
            )
        )
        assertFalse(
            com.example.data.VoiceController.shouldTriggerVoiceBargeIn(
                recentWindowRms = listOf(800.0, 2900.0, 900.0),
                baselineRms = 750.0
            )
        )
        assertFalse(
            com.example.data.VoiceController.shouldTriggerVoiceBargeIn(
                recentWindowRms = listOf(1100.0, 1150.0, 1120.0),
                baselineRms = 750.0
            )
        )

        // 2. Day / Night theme preference defaults to DAY and switches cleanly
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.core.SathiPreferences(context)
        assertEquals(com.example.core.SathiThemeMode.DAY, prefs.settingsFlow.first().themeMode)
        prefs.setThemeMode(com.example.core.SathiThemeMode.NIGHT)
        assertEquals(com.example.core.SathiThemeMode.NIGHT, prefs.settingsFlow.first().themeMode)
        prefs.setThemeMode(com.example.core.SathiThemeMode.DAY)

        // 3. 2 GB Smart Brain budget & key sanitization
        val packManager = com.example.data.OfflinePackManager(context)
        assertEquals(2L * 1024L * 1024L * 1024L, com.example.data.OfflinePackManager.MAX_BRAIN_STORAGE_BYTES)
        assertTrue(packManager.getStorageBudgetLabel(SathiLanguage.BN).contains("2.00 GB"))
        assertEquals(
            "AIzaSyDemoExtractKey1234567890",
            SathiAiService.sanitizeApiKeyInput("API Key: AIzaSyDemoExtractKey1234567890 (copy)")
        )

        // 4. Reference-image guided generation returns valid downloadable bitmap
        val refBmp = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        refBmp.eraseColor(android.graphics.Color.rgb(255, 200, 40))
        val imgRepo = PlaceholderSathiImageGenRepository()
        val refOutcome = imgRepo.generateImage("Golden sunrise variant", SathiLanguage.BN, refBmp).getOrThrow()
        assertTrue(refOutcome.bitmap.width >= 512)
        assertTrue(refOutcome.caption.contains("উদাহরণ"))
    }

    @Test
    fun `exact youtube open vs explicit query, friend search on whatsapp and messenger, sms writing, and contact transliteration work cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ai = SathiAiService(com.example.data.OfflinePackManager(context))
        val offline = OfflineCompanionEngine()

        // 1. Plain YouTube open NEVER searches "Bangla music" or stray words like "তুমি"
        val ytOpenOnline = ai.generateCompanionReply(
            userMessage = "তুমি ইউটিউব খোলো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.OPEN_APP, ytOpenOnline.actionType)
        assertEquals("YouTube", ytOpenOnline.actionParam)

        val ytOpenOffline = offline.respondOffline("ইউটিউব অ্যাপ অন করো", SathiLanguage.BN, "বাইজিদ")
        assertEquals(PhoneActionType.OPEN_APP, ytOpenOffline.actionType)
        assertEquals("YouTube", ytOpenOffline.actionParam)

        // 2. Explicit YouTube search keeps strictly what user asked
        val ytSearch = ai.generateCompanionReply(
            userMessage = "ইউটিউবে বাংলা নাটক সার্চ করো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.YOUTUBE, ytSearch.actionType)
        assertEquals("বাংলা নাটক", ytSearch.actionParam)

        // 3. Info query mentioning greeting or app name is NOT hijacked as greeting or app open
        val infoTurn = ai.generateCompanionReply(
            userMessage = "হ্যালো, ইউটিউব থেকে কীভাবে আয় করে?",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.WEB_SEARCH, infoTurn.actionType)

        // 4. WhatsApp & Messenger friend search by name (without SMS body)
        val waSearchFriend = ai.generateCompanionReply(
            userMessage = "হোয়াটসঅ্যাপে আমার ফ্রেন্ড তানভীরকে খোঁজো",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, waSearchFriend.actionType)
        assertEquals("WhatsApp", waSearchFriend.actionParam)
        assertEquals("তানভীর", waSearchFriend.recipientParam)
        assertEquals("", waSearchFriend.secondaryParam)

        val msgSearchFriend = offline.respondOffline("মেসেঞ্জারে রহিমকে খুঁজো", SathiLanguage.BN, "বাইজিদ")
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, msgSearchFriend.actionType)
        assertEquals("Messenger", msgSearchFriend.actionParam)
        assertEquals("রহিম", msgSearchFriend.recipientParam)
        assertEquals("", msgSearchFriend.secondaryParam)

        // 5. Varied Bengali friend SMS writing phrasing
        val smsWriteTurn = ai.generateCompanionReply(
            userMessage = "মেসেঞ্জারে ফ্রেন্ড সাকিবকে এই এসএমএসটা করো যে কাল দেখা হবে",
            language = SathiLanguage.BN,
            userName = "বাইজিদ",
            memories = emptyList(),
            recentHistory = emptyList()
        ).getOrThrow()
        assertEquals(PhoneActionType.SOCIAL_MESSAGE_CONFIRM, smsWriteTurn.actionType)
        assertEquals("Messenger", smsWriteTurn.actionParam)
        assertEquals("সাকিব", smsWriteTurn.recipientParam)
        assertTrue(smsWriteTurn.secondaryParam.contains("কাল দেখা হবে"))

        // 6. Bengali-to-Latin contact transliteration matching
        assertTrue(com.example.data.DeviceActionExecutor.matchesContactName("MD Rahim Uddin", "রহিম"))
        assertTrue(com.example.data.DeviceActionExecutor.matchesContactName("Tanvir Hasan", "তানভীর"))
        assertTrue(com.example.data.DeviceActionExecutor.matchesContactName("Karim Ahmed", "করিম"))
        assertFalse(com.example.data.DeviceActionExecutor.matchesContactName("Jahidul Islam", "রহিম"))
    }
}
