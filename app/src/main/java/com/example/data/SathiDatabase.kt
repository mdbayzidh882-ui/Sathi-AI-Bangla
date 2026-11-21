package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val userName: String,
    val email: String,
    val plan: String,
    val redeemedCodeId: String,
    val planExpiresAt: Long,
    val banned: Boolean,
    val preferredLanguage: String,
    val preferredVoice: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "premium_codes")
data class PremiumCodeEntity(
    @PrimaryKey val id: String,
    val codeType: String,
    val status: String,
    val createdBy: String,
    val usedBy: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: String,
    val cameraEnabledForFree: Boolean,
    val longTermMemoryForFree: Boolean,
    val translationForFree: Boolean,
    val dayPlanningForFree: Boolean,
    val announcementText: String,
    val announcementActive: Boolean,
    val bannerAdEnabled: Boolean = true,
    val bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val updatedBy: String,
    val updatedAt: Long
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val actorUid: String,
    val actorEmail: String,
    val action: String,
    val target: String,
    val details: String,
    val createdAt: Long
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val isUser: Boolean,
    val text: String,
    val moodCode: String,
    val sourcesJson: String,
    val actionLabel: String?,
    val isOfflineReply: Boolean,
    val mediaBadge: String?,
    val reaction: Int,
    val timestampMs: Long
)

@Dao
interface SathiDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :uid LIMIT 1")
    fun observeUserProfile(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :uid LIMIT 1")
    suspend fun getUserProfileOnce(uid: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY updatedAt DESC")
    fun observeAllUsers(): Flow<List<UserProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(entity: UserProfileEntity)

    @Query(
        "UPDATE user_profiles SET userName = :name, preferredLanguage = :lang, preferredVoice = :voice, updatedAt = :updatedAt WHERE userId = :uid"
    )
    suspend fun updateUserPreferences(
        uid: String,
        name: String,
        lang: String,
        voice: String,
        updatedAt: Long
    )

    @Query(
        "UPDATE user_profiles SET plan = :plan, redeemedCodeId = :codeId, planExpiresAt = :expiresAt, updatedAt = :updatedAt WHERE userId = :uid"
    )
    suspend fun updateUserPlan(
        uid: String,
        plan: String,
        codeId: String,
        expiresAt: Long,
        updatedAt: Long
    )

    @Query(
        "UPDATE user_profiles SET plan = :plan, planExpiresAt = :expiresAt, banned = :banned, updatedAt = :updatedAt WHERE userId = :uid"
    )
    suspend fun adminUpdateUser(
        uid: String,
        plan: String,
        expiresAt: Long,
        banned: Boolean,
        updatedAt: Long
    )

    @Query("SELECT * FROM user_memories WHERE userId = :uid ORDER BY updatedAt DESC")
    fun observeMemories(uid: String): Flow<List<UserMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(entity: UserMemoryEntity)

    @Query(
        "UPDATE user_memories SET category = :category, title = :title, content = :content, updatedAt = :updatedAt WHERE id = :id AND userId = :uid"
    )
    suspend fun updateMemory(
        id: String,
        uid: String,
        category: String,
        title: String,
        content: String,
        updatedAt: Long
    )

    @Query("DELETE FROM user_memories WHERE id = :id AND userId = :uid")
    suspend fun deleteMemory(id: String, uid: String)

    @Query("DELETE FROM user_memories WHERE userId = :uid")
    suspend fun deleteAllMemoriesForUser(uid: String)

    @Query("SELECT * FROM app_config WHERE id = 'global' LIMIT 1")
    fun observeGlobalConfig(): Flow<AppConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlobalConfig(entity: AppConfigEntity)

    @Query("SELECT * FROM premium_codes ORDER BY createdAt DESC")
    fun observeAllCodes(): Flow<List<PremiumCodeEntity>>

    @Query("SELECT * FROM premium_codes WHERE id = :codeId LIMIT 1")
    suspend fun getCodeById(codeId: String): PremiumCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCode(entity: PremiumCodeEntity)

    @Query(
        "UPDATE premium_codes SET status = :status, usedBy = :usedBy, updatedAt = :updatedAt WHERE id = :codeId"
    )
    suspend fun updateCodeStatus(codeId: String, status: String, usedBy: String, updatedAt: Long)

    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT 50")
    fun observeActivityLogs(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(entity: ActivityLogEntity)

    // --- Chat Sessions & Messages (Gemini-style Sidebar History & Pinned Chats) ---
    @Query("SELECT * FROM chat_sessions WHERE userId = :uid ORDER BY isPinned DESC, updatedAt DESC")
    fun observeChatSessions(uid: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getChatSessionOnce(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatSession(entity: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameChatSession(sessionId: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchChatSession(sessionId: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateChatSessionPinned(sessionId: String, isPinned: Boolean, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteChatSession(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE userId = :uid")
    suspend fun deleteAllChatSessionsForUser(uid: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getMessagesForSessionOnce(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(entity: ChatMessageEntity)

    @Query("UPDATE chat_messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateMessageReaction(messageId: String, reaction: Int)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT * FROM chat_messages ORDER BY timestampMs DESC LIMIT 30")
    fun observeRecentMessagesForAdmin(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 30")
    fun observeAllChatSessionsForAdmin(): Flow<List<ChatSessionEntity>>

    @Query("DELETE FROM chat_messages WHERE userId = :uid")
    suspend fun deleteAllChatMessagesForUser(uid: String)
}

@Database(
    entities = [
        UserProfileEntity::class,
        UserMemoryEntity::class,
        PremiumCodeEntity::class,
        AppConfigEntity::class,
        ActivityLogEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SathiDatabase : RoomDatabase() {
    abstract fun sathiDao(): SathiDao

    companion object {
        @Volatile
        private var instance: SathiDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `userId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sessionId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `isUser` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `moodCode` TEXT NOT NULL,
                        `sourcesJson` TEXT NOT NULL,
                        `actionLabel` TEXT,
                        `isOfflineReply` INTEGER NOT NULL,
                        `mediaBadge` TEXT,
                        `reaction` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `app_config` ADD COLUMN `bannerAdEnabled` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `app_config` ADD COLUMN `bannerAdUnitId` TEXT NOT NULL DEFAULT 'ca-app-pub-3940256099942544/6300978111'"
                )
            }
        }

        fun getInstance(context: Context): SathiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SathiDatabase::class.java,
                    "sathi_local_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
