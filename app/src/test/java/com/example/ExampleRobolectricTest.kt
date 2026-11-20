package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineCompanionEngine
import com.example.data.SathiAiService
import com.example.data.SathiDatabase
import com.example.data.SathiLocalRepository
import com.example.model.CodeType
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.UserProfile
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
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app opens directly on home screen with Sathi title, mic button, and media plus button`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("সাথী • Sathi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attach_media_button").assertIsDisplayed()
    }

    @Test
    fun `app name is Sathi`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sathi", appName)
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
}
