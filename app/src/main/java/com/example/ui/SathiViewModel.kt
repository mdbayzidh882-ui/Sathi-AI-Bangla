package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.BitmapUtils
import com.example.core.LocalSettingsState
import com.example.core.NetworkMonitor
import com.example.core.OfflinePackState
import com.example.core.OfflinePackTier
import com.example.core.SathiPreferences
import com.example.core.SathiThemeMode
import com.example.data.ActionOutcome
import com.example.data.DeviceActionExecutor
import com.example.data.GeneratedImageOutcome
import com.example.data.OfflineCompanionEngine
import com.example.data.OfflinePackManager
import com.example.data.PlaceholderSathiImageGenRepository
import com.example.data.SathiAiService
import com.example.data.SathiImageGenRepository
import com.example.data.SathiLocalRepository
import com.example.data.VoiceController
import com.example.data.VoiceEngineState
import com.example.data.WebSearchService
import com.example.model.ActivityLog
import com.example.model.AppConfig
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.CodeType
import com.example.model.MemoryCategory
import com.example.model.PendingConfirmation
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.PremiumCode
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import com.example.model.UserEmotion
import com.example.model.UserMemory
import com.example.model.UserProfile
import com.example.model.WebSource
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiScreenShareService
import com.example.overlay.SathiWakeWordService
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiBannerState {
    data object None : UiBannerState
    data class Info(val message: String) : UiBannerState
    data class Error(val message: String) : UiBannerState
}

sealed interface GoogleKeyCheckState {
    data object Idle : GoogleKeyCheckState
    data object Verifying : GoogleKeyCheckState
    data class Connected(val message: String) : GoogleKeyCheckState
    data class Failed(val message: String) : GoogleKeyCheckState
}

class SathiViewModel(
    private val currentUserId: String,
    private val userEmail: String,
    private val displayName: String,
    private val repository: SathiLocalRepository,
    private val aiService: SathiAiService,
    private val webSearchService: WebSearchService,
    private val offlineEngine: OfflineCompanionEngine,
    private val offlinePackManager: OfflinePackManager,
    private val voiceController: VoiceController,
    private val deviceActions: DeviceActionExecutor,
    private val preferences: SathiPreferences,
    private val networkMonitor: NetworkMonitor,
    private val imageGenRepository: SathiImageGenRepository = PlaceholderSathiImageGenRepository()
) : ViewModel() {

    val voiceState: StateFlow<VoiceEngineState> = voiceController.state

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val memories: StateFlow<List<UserMemory>> = repository.observeMemories(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Memories stream error", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val globalConfig: StateFlow<AppConfig> = repository.observeGlobalConfig()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(AppConfig())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    val localSettings: StateFlow<LocalSettingsState> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalSettingsState())

    val chatSessions: StateFlow<List<ChatSession>> = repository.observeChatSessions(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingMessageId = MutableStateFlow<String?>(null)
    val streamingMessageId: StateFlow<String?> = _streamingMessageId.asStateFlow()

    private val _streamingCharLimit = MutableStateFlow(Int.MAX_VALUE)
    val streamingCharLimit: StateFlow<Int> = _streamingCharLimit.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _activeMood = MutableStateFlow(SathiMood.WARM)
    val activeMood: StateFlow<SathiMood> = _activeMood.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _bannerState = MutableStateFlow<UiBannerState>(UiBannerState.None)
    val bannerState: StateFlow<UiBannerState> = _bannerState.asStateFlow()

    private val _googleKeyCheckState = MutableStateFlow<GoogleKeyCheckState>(GoogleKeyCheckState.Idle)
    val googleKeyCheckState: StateFlow<GoogleKeyCheckState> = _googleKeyCheckState.asStateFlow()

    private val _studioLoading = MutableStateFlow(false)
    val studioLoading: StateFlow<Boolean> = _studioLoading.asStateFlow()

    private val _studioResult = MutableStateFlow("")
    val studioResult: StateFlow<String> = _studioResult.asStateFlow()

    private val _morningGreeting = MutableStateFlow("")
    val morningGreeting: StateFlow<String> = _morningGreeting.asStateFlow()

    private val _isOwnerAdmin = MutableStateFlow(false)
    val isOwnerAdmin: StateFlow<Boolean> = _isOwnerAdmin.asStateFlow()

    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked.asStateFlow()

    private val _adminCodes = MutableStateFlow<List<PremiumCode>>(emptyList())
    val adminCodes: StateFlow<List<PremiumCode>> = _adminCodes.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    private val _adminLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val adminLogs: StateFlow<List<ActivityLog>> = _adminLogs.asStateFlow()

    private val _selectedUserEmotion = MutableStateFlow<UserEmotion?>(null)
    val selectedUserEmotion: StateFlow<UserEmotion?> = _selectedUserEmotion.asStateFlow()

    private val _adminLiveMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val adminLiveMessages: StateFlow<List<ChatMessage>> = _adminLiveMessages.asStateFlow()

    private val _adminAllSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val adminAllSessions: StateFlow<List<ChatSession>> = _adminAllSessions.asStateFlow()

    private val _offlineDownloadStatus = MutableStateFlow("")
    val offlineDownloadStatus: StateFlow<String> = _offlineDownloadStatus.asStateFlow()

    private val _generatedImagePreview = MutableStateFlow<GeneratedImageOutcome?>(null)
    val generatedImagePreview: StateFlow<GeneratedImageOutcome?> = _generatedImagePreview.asStateFlow()

    private var offlineDownloadJob: Job? = null
    private var adminObserverJob: Job? = null
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            launch {
                preferences.settingsFlow.collect { settings ->
                    voiceController.setAutoVoiceEnabled(settings.autoVoiceEnabled)
                }
            }
            repository.ensureUserProfile(currentUserId, displayName, userEmail)
            _isOwnerAdmin.value = repository.isUserAdminDocPresent()
            val existingSessions = repository.observeChatSessions(currentUserId).first()
            if (existingSessions.isNotEmpty()) {
                val latest = existingSessions.first()
                _activeSessionId.value = latest.id
                val loaded = repository.getMessagesForSessionOnce(latest.id)
                if (loaded.isNotEmpty()) {
                    _messages.value = loaded
                } else {
                    seedWelcomeMessage()
                }
            } else {
                seedWelcomeMessage()
            }
        }
    }

    private fun seedWelcomeMessage() {
        val name = displayName.ifBlank { "বন্ধু" }
        _streamingMessageId.value = null
        _messages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = "আসসালামু আলাইকুম $name! আমি সাথী এআই (Sathi AI), আপনার ব্যক্তিগত এআই বন্ধু। নিচের গোল মাইক্রোফোন বাটনে ট্যাপ করে বাংলা বা ইংরেজিতে কথা বলুন।",
                mood = SathiMood.WARM
            )
        )
    }

    fun startNewChat() {
        streamingJob?.cancel()
        _streamingMessageId.value = null
        _activeSessionId.value = null
        _messages.value = emptyList()
        seedWelcomeMessage()
    }

    fun selectChatSession(sessionId: String) {
        viewModelScope.launch {
            streamingJob?.cancel()
            _streamingMessageId.value = null
            _activeSessionId.value = sessionId
            val loaded = repository.getMessagesForSessionOnce(sessionId)
            if (loaded.isNotEmpty()) {
                _messages.value = loaded
            } else {
                seedWelcomeMessage()
            }
        }
    }

    fun togglePinChatSession(session: ChatSession) {
        viewModelScope.launch {
            repository.togglePinChatSession(session.id, !session.isPinned)
        }
    }

    fun deleteChatSession(session: ChatSession) {
        viewModelScope.launch {
            repository.deleteChatSession(session.id)
            if (_activeSessionId.value == session.id) {
                startNewChat()
            }
        }
    }

    fun reactToMessage(messageId: String, reaction: Int) {
        val currentList = _messages.value
        val existing = currentList.firstOrNull { it.id == messageId } ?: return
        val nextReaction = if (existing.reaction == reaction) 0 else reaction
        _messages.value = currentList.map { msg ->
            if (msg.id == messageId) msg.copy(reaction = nextReaction) else msg
        }
        viewModelScope.launch {
            if (_activeSessionId.value != null) {
                repository.updateMessageReaction(messageId, nextReaction)
            }
        }
    }

    fun toggleLiveMute() {
        voiceController.toggleMute()
    }

    fun setAutoVoiceEnabled(enabled: Boolean) {
        voiceController.setAutoVoiceEnabled(enabled)
        viewModelScope.launch {
            preferences.setAutoVoiceEnabled(enabled)
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setWakeWordEnabled(enabled)
        }
    }

    fun interruptSpeakingAndListen() {
        voiceController.interruptAndListen(currentLanguage(), isUsingOfflineMode())
    }

    fun onWakeWordTriggered() {
        val lang = currentLanguage()
        val offline = isUsingOfflineMode()
        voiceController.startListeningAfterWake(lang, offline)
        _bannerState.value = UiBannerState.Info(
            if (lang == SathiLanguage.BN) "সাথী জেগে উঠেছে! বলুন, আমি শুনছি..."
            else "Sathi is awake and listening!"
        )
    }

    fun selectUserEmotion(emotion: UserEmotion) {
        _selectedUserEmotion.value = emotion
        _activeMood.value = emotion.sathiMood
        val lang = currentLanguage()
        val voice = currentVoice()
        val name = userProfile.value?.userName?.ifBlank { displayName } ?: displayName.ifBlank { "বন্ধু" }
        val reply = if (lang == SathiLanguage.BN) {
            when (emotion) {
                UserEmotion.HAPPY -> "${emotion.emoji} আলহামদুলিল্লাহ $name! আপনাকে হাসিখুশি দেখে আমারও ভীষণ ভালো লাগছে। আজ আনন্দটা কীভাবে উদযাপন করবেন?"
                UserEmotion.SAD -> "${emotion.emoji} $name, বুঝতে পারছি আপনার মনটা একটু খারাপ। মন খুলে আমাকে বলুন কী হয়েছে—আমি সবসময় আপনার পাশেই আছি।"
                UserEmotion.ANGRY -> "${emotion.emoji} একটু থেমে গভীর শ্বাস নিন $name। আপনার বিরক্তি বা রাগের কথা আমাকে বললে মনটা হালকা লাগবে।"
                UserEmotion.ANXIOUS -> "${emotion.emoji} একদম দুশ্চিন্তা করবেন না $name। যেকোনো কঠিন সমস্যা আমরা ধাপে ধাপে একসাথে সমাধান করব।"
                UserEmotion.TIRED -> "${emotion.emoji} আজ অনেক পরিশ্রম হয়েছে তাই না $name? একটু বিশ্রাম নিন আর হালকা পানি পান করুন, আমি পাশে আছি।"
                UserEmotion.LOVING -> "${emotion.emoji} আপনার সুন্দর মনের জন্য অনেক ভালোবাসা $name! আজ আপনার জন্য কী করতে পারি?"
            }
        } else {
            when (emotion) {
                UserEmotion.HAPPY -> "${emotion.emoji} Seeing you happy makes my day brighter, $name! What good news brought this smile?"
                UserEmotion.SAD -> "${emotion.emoji} I'm right here beside you, $name. You don't have to carry sadness alone—tell me what's on your heart."
                UserEmotion.ANGRY -> "${emotion.emoji} Pause for a slow breath, $name. Feel free to vent to me—no judgment, only care."
                UserEmotion.ANXIOUS -> "${emotion.emoji} Let's take it one calm step at a time, $name. Tell me what's worrying you."
                UserEmotion.TIRED -> "${emotion.emoji} You sound really tired, $name. Rest your eyes for a bit—I'm keeping watch with you."
                UserEmotion.LOVING -> "${emotion.emoji} So much warmth and gratitude right back to you, $name! How can I help today?"
            }
        }
        val aiMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = reply,
            mood = emotion.sathiMood
        )
        _messages.value = _messages.value + aiMsg
        viewModelScope.launch {
            val sid = ensureActiveSessionWithTitle("${emotion.emoji} ${emotion.labelBn}")
            repository.upsertChatMessage(currentUserId, sid, aiMsg)
        }
        startStreamingReveal(aiMsg.id, reply)
        voiceController.speak(reply, lang, voice, emotion.sathiMood)
    }

    fun setThemeMode(mode: SathiThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun toggleDayNightTheme() {
        val next = when (localSettings.value.themeMode) {
            SathiThemeMode.NIGHT -> SathiThemeMode.DAY
            else -> SathiThemeMode.NIGHT
        }
        setThemeMode(next)
    }

    fun saveCustomGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            val cleaned = SathiAiService.sanitizeApiKeyInput(apiKey)
            preferences.saveCustomGeminiApiKey(cleaned, verified = false)
            if (cleaned.isBlank()) {
                _googleKeyCheckState.value = GoogleKeyCheckState.Idle
            }
            val lang = currentLanguage()
            _bannerState.value = UiBannerState.Info(
                if (cleaned.isBlank()) {
                    if (lang == SathiLanguage.BN) "Google সার্চ চাবি মুছে ফেলা হয়েছে।" else "Google Search key cleared."
                } else {
                    if (lang == SathiLanguage.BN) "Google সার্চ চাবি সুরক্ষিতভাবে সংরক্ষিত হয়েছে!" else "Google Search key saved securely!"
                }
            )
        }
    }

    fun saveAndVerifyGoogleApiKey(rawKey: String) {
        viewModelScope.launch {
            val cleaned = SathiAiService.sanitizeApiKeyInput(rawKey)
            val lang = currentLanguage()
            if (cleaned.isBlank()) {
                preferences.saveCustomGeminiApiKey("", verified = false)
                _googleKeyCheckState.value = GoogleKeyCheckState.Idle
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) "Google চাবি মুছে ফেলা হয়েছে।" else "Google key cleared."
                )
                return@launch
            }
            preferences.saveCustomGeminiApiKey(cleaned, verified = false)
            _googleKeyCheckState.value = GoogleKeyCheckState.Verifying
            val check = aiService.verifyGoogleApiKey(cleaned, lang)
            check.onSuccess { msg ->
                preferences.saveCustomGeminiApiKey(cleaned, verified = true)
                preferences.setOfflinePackTier(OfflinePackTier.DEEP)
                preferences.setAutoVoiceEnabled(true)
                voiceController.setAutoVoiceEnabled(true)
                _googleKeyCheckState.value = GoogleKeyCheckState.Connected(msg)
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) {
                        "$msg — স্মার্ট জ্ঞান ভান্ডার (সর্বোচ্চ ২ জিবি) স্বয়ংক্রিয়ভাবে ডাউনলোড শুরু হচ্ছে..."
                    } else {
                        "$msg — Automatically syncing Smart Knowledge Brain (up to 2 GB)..."
                    }
                )
                startAutoKnowledgeBrainSync(cleaned)
            }.onFailure { err ->
                preferences.saveCustomGeminiApiKey(cleaned, verified = false)
                val msg = err.localizedMessage ?: if (lang == SathiLanguage.BN) "চাবি যাচাই করা যায়নি।" else "Key verification failed."
                _googleKeyCheckState.value = GoogleKeyCheckState.Failed(msg)
            }
        }
    }

    private suspend fun ensureActiveSessionWithTitle(firstPrompt: String): String {
        val existing = _activeSessionId.value
        if (!existing.isNullOrBlank()) return existing
        val cleanTitle = firstPrompt.trim().replace(Regex("\\s+"), " ").take(42)
            .ifBlank { if (currentLanguage() == SathiLanguage.BN) "নতুন কথোপকথন" else "New Chat" }
        val session = repository.createChatSession(currentUserId, cleanTitle)
        _activeSessionId.value = session.id
        return session.id
    }

    private fun startStreamingReveal(messageId: String, fullText: String) {
        streamingJob?.cancel()
        if (fullText.length <= 24) {
            _streamingMessageId.value = null
            return
        }
        _streamingMessageId.value = messageId
        _streamingCharLimit.value = 1
        streamingJob = viewModelScope.launch {
            val step = (fullText.length / 28).coerceIn(3, 14)
            var cursor = step
            while (cursor < fullText.length) {
                _streamingCharLimit.value = cursor
                delay(24L)
                cursor += step
            }
            _streamingCharLimit.value = Int.MAX_VALUE
            _streamingMessageId.value = null
        }
    }

    fun currentLanguage(): SathiLanguage =
        SathiLanguage.fromCode(
            userProfile.value?.preferredLanguage ?: localSettings.value.preferredLanguageFallback
        )

    fun currentVoice(): SathiVoiceProfile =
        SathiVoiceProfile.fromCode(
            userProfile.value?.preferredVoice ?: localSettings.value.preferredVoiceFallback
        )

    fun isDeviceNetworkOnline(): Boolean = networkMonitor.isOnline()

    fun isUsingOfflineMode(): Boolean {
        val settings = localSettings.value
        val online = networkMonitor.isOnline()
        return settings.offlineModeEnabled || (!online && settings.offlinePackState == OfflinePackState.INSTALLED)
    }

    fun onMicButtonTapped() {
        val vs = voiceState.value
        val lang = currentLanguage()
        val offline = isUsingOfflineMode()
        when {
            vs.speaking -> {
                // Instant Barge-in / Interruption while keeping Live Session flowing
                voiceController.interruptAndListen(lang, offline)
            }
            vs.listening -> {
                voiceController.stopListening()
            }
            else -> {
                // One tap enters Gemini Live-style continuous voice mode
                voiceController.startLiveSession(lang, offline)
            }
        }
    }

    fun stopLiveVoiceSession() {
        voiceController.stopLiveSession()
    }

    fun analyzeHomePhoto(bitmap: Bitmap, question: String) {
        val lang = currentLanguage()
        val voice = currentVoice()
        val apiKey = localSettings.value.customGeminiApiKey
        val promptLabel = question.trim().ifBlank {
            if (lang == SathiLanguage.BN) "এই ছবিটি বিশ্লেষণ করো" else "Analyze this photo"
        }
        val thumb = Bitmap.createScaledBitmap(
            bitmap,
            320,
            (320f * bitmap.height / bitmap.width.coerceAtLeast(1)).toInt().coerceAtLeast(160),
            true
        )
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = promptLabel,
            mediaThumbnail = thumb,
            mediaBadge = if (lang == SathiLanguage.BN) "ছবি" else "Photo"
        )
        _messages.value = _messages.value + userMsg
        _isThinking.value = true
        viewModelScope.launch {
            try {
                val sid = ensureActiveSessionWithTitle(promptLabel)
                repository.upsertChatMessage(currentUserId, sid, userMsg)
                val res = aiService.analyzeImage(bitmap, question, lang, isScreenCapture = false, apiKey = apiKey)
                val reply = res.getOrElse {
                    if (lang == SathiLanguage.BN) "ছবিটি বিশ্লেষণ করা যায়নি।" else "Could not analyze photo."
                }
                val aiMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = reply,
                    mood = SathiMood.WARM
                )
                _messages.value = _messages.value + aiMsg
                repository.upsertChatMessage(currentUserId, sid, aiMsg)
                startStreamingReveal(aiMsg.id, reply)
                voiceController.speak(reply, lang, voice, SathiMood.WARM)
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun analyzeSharedScreen(question: String = "") {
        val lang = currentLanguage()
        val voice = currentVoice()
        val apiKey = localSettings.value.customGeminiApiKey
        val promptLabel = question.trim().ifBlank {
            if (lang == SathiLanguage.BN) "আমার শেয়ার করা স্ক্রিনটি দেখো" else "Look at my shared screen"
        }
        _isThinking.value = true
        viewModelScope.launch {
            try {
                val frame = SathiScreenShareService.captureLatestFrame()
                val screenContext = SathiScreenReaderService.formatScreenContextSummary()
                if (frame == null && screenContext.isBlank()) {
                    val errText = if (lang == SathiLanguage.BN) {
                        "এখন স্ক্রিনের তথ্য পাওয়া যায়নি। '+' বাটন থেকে স্ক্রিন শেয়ার অথবা সেটিংস থেকে স্ক্রিন রিডার চালু করুন।"
                    } else {
                        "Could not capture screen data yet. Enable Screen Share via + or Screen Reader in Settings."
                    }
                    appendSathiErrorReply(errText, lang, voice, speakReply = true)
                    return@launch
                }

                val thumb = frame?.let {
                    Bitmap.createScaledBitmap(
                        it,
                        240,
                        (240f * it.height / it.width.coerceAtLeast(1)).toInt().coerceAtLeast(160),
                        true
                    )
                }
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = promptLabel,
                    mediaThumbnail = thumb,
                    mediaBadge = if (lang == SathiLanguage.BN) "লাইভ স্ক্রিন" else "Live Screen"
                )
                _messages.value = _messages.value + userMsg
                val sid = ensureActiveSessionWithTitle(promptLabel)
                repository.upsertChatMessage(currentUserId, sid, userMsg)

                val reply = if (frame != null) {
                    aiService.analyzeImage(
                        bitmap = frame,
                        question = question,
                        language = lang,
                        isScreenCapture = true,
                        apiKey = apiKey,
                        screenTextContext = screenContext
                    ).getOrElse {
                        if (lang == SathiLanguage.BN) "স্ক্রিনটি বিশ্লেষণ করা যায়নি।" else "Could not analyze screen."
                    }
                } else {
                    if (lang == SathiLanguage.BN) {
                        "আমি আপনার ফোনের লাইভ স্ক্রিন থেকে যা দেখছি:\n\n$screenContext"
                    } else {
                        "Here is what I see on your live phone screen:\n\n$screenContext"
                    }
                }
                val aiMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = reply,
                    mood = SathiMood.WARM
                )
                _messages.value = _messages.value + aiMsg
                repository.upsertChatMessage(currentUserId, sid, aiMsg)
                startStreamingReveal(aiMsg.id, reply)
                voiceController.speak(reply, lang, voice, SathiMood.WARM)
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun analyzeHomeVideo(context: Context, uri: Uri, question: String) {
        val lang = currentLanguage()
        val voice = currentVoice()
        val promptLabel = question.trim().ifBlank {
            if (lang == SathiLanguage.BN) "এই ভিডিওটি বিশ্লেষণ করো" else "Analyze this video"
        }
        _isThinking.value = true
        viewModelScope.launch {
            try {
                val res = aiService.analyzeVideo(context, uri, question, lang)
                res.onSuccess { outcome ->
                    _messages.value = _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = true,
                        text = promptLabel,
                        mediaThumbnail = outcome.thumbnail,
                        mediaBadge = if (lang == SathiLanguage.BN) "ভিডিও • ${outcome.durationLabel}" else "Video • ${outcome.durationLabel}"
                    )
                    _messages.value = _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = outcome.summaryText,
                        mood = SathiMood.SERIOUS
                    )
                    voiceController.speak(outcome.summaryText, lang, voice, SathiMood.WARM)
                }.onFailure {
                    _bannerState.value = UiBannerState.Error(
                        if (lang == SathiLanguage.BN) "ভিডিওটি পড়া যায়নি, অনুগ্রহ করে অন্য একটি ভিডিও চেষ্টা করুন।"
                        else "Could not inspect this video file. Please try another clip."
                    )
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun sendUserMessage(rawText: String, speakReply: Boolean = true) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        val lowerPre = text.lowercase()
        if (lowerPre.startsWith("থামো") || lowerPre.startsWith("চুপ") || lowerPre.startsWith("stop")) {
            voiceController.stopSpeaking()
        }
        if (SathiWakeWordService.matchesTripleSathi(text)) {
            onWakeWordTriggered()
            return
        }
        if (_isThinking.value) return

        val profile = userProfile.value
        if (profile?.banned == true) {
            val lang = currentLanguage()
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "দুঃখিত, আপনার অ্যাকাউন্টটি অ্যাডমিন দ্বারা স্থগিত করা হয়েছে।"
                else "Your account has been suspended by the administrator."
            )
            return
        }

        val lang = currentLanguage()
        val voice = currentVoice()
        val userName = profile?.userName?.ifBlank { displayName } ?: displayName.ifBlank { "বন্ধু" }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = text
        )
        _messages.value = _messages.value + userMsg
        _isThinking.value = true
        _bannerState.value = UiBannerState.None

        viewModelScope.launch {
            try {
                val sid = ensureActiveSessionWithTitle(text)
                repository.upsertChatMessage(currentUserId, sid, userMsg)

                val settings = localSettings.value
                val online = networkMonitor.isOnline()

                if (settings.offlineModeEnabled || !online) {
                    if (settings.offlinePackState == OfflinePackState.INSTALLED) {
                        val offlineTurn = offlineEngine.respondOffline(text, lang, userName)
                        handleTurnOutput(offlineTurn, emptyList(), isOffline = true, speakReply = speakReply, lang = lang, voice = voice, sessionId = sid)
                    } else if (!online) {
                        val msg = if (lang == SathiLanguage.BN) {
                            "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। অফলাইন চ্যাট ব্যবহার করতে সেটিংস থেকে অফলাইন প্যাক ডাউনলোড করুন অথবা ওয়াই-ফাই/ডাটা চালু করুন।"
                        } else {
                            "No internet connection. Enable Wi-Fi/data or download the Offline Pack in Settings."
                        }
                        appendSathiErrorReply(msg, lang, voice, speakReply)
                    } else {
                        val offlineTurn = offlineEngine.respondOffline(text, lang, userName)
                        handleTurnOutput(offlineTurn, emptyList(), isOffline = true, speakReply = speakReply, lang = lang, voice = voice, sessionId = sid)
                    }
                    return@launch
                }

                // If live Screen Share or Accessibility Screen Reader is active and user asks about screen, include full screen data
                val lowerText = text.lowercase()
                val screenContext = SathiScreenReaderService.formatScreenContextSummary()
                if ((SathiScreenShareService.isSharing.value || screenContext.isNotBlank()) &&
                    (lowerText.contains("স্ক্রিন") || lowerText.contains("screen") ||
                        lowerText.contains("এটা কী") || lowerText.contains("এখানে কী") ||
                        lowerText.contains("পড়ে শোনা") || lowerText.contains("পড়ে শোনা") ||
                        lowerText.contains("কোন অ্যাপ"))
                ) {
                    val frame = SathiScreenShareService.captureLatestFrame()
                    if (frame != null) {
                        val analysis = aiService.analyzeImage(
                            bitmap = frame,
                            question = text,
                            language = lang,
                            isScreenCapture = true,
                            apiKey = settings.customGeminiApiKey,
                            screenTextContext = screenContext
                        ).getOrElse {
                            if (lang == SathiLanguage.BN) "স্ক্রিনটি পড়া যায়নি।" else "Could not read screen."
                        }
                        val aiMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = analysis,
                            mood = SathiMood.WARM
                        )
                        _messages.value = _messages.value + aiMsg
                        repository.upsertChatMessage(currentUserId, sid, aiMsg)
                        startStreamingReveal(aiMsg.id, analysis)
                        if (speakReply) {
                            voiceController.speak(analysis, lang, voice, SathiMood.WARM)
                        }
                        return@launch
                    } else if (screenContext.isNotBlank()) {
                        val reply = if (lang == SathiLanguage.BN) {
                            "বর্তমান স্ক্রিনে যা দেখা যাচ্ছে:\n\n$screenContext"
                        } else {
                            "Currently visible on your phone screen:\n\n$screenContext"
                        }
                        val aiMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = reply,
                            mood = SathiMood.WARM
                        )
                        _messages.value = _messages.value + aiMsg
                        repository.upsertChatMessage(currentUserId, sid, aiMsg)
                        startStreamingReveal(aiMsg.id, reply)
                        if (speakReply) {
                            voiceController.speak(reply, lang, voice, SathiMood.WARM)
                        }
                        return@launch
                    }
                }

                // Online Companion AI Engine
                val allowMemory = (profile?.hasPremiumAccess() == true) || globalConfig.value.longTermMemoryForFree
                val recentHistory = _messages.value.takeLast(8).map { it.isUser to it.text }
                val activeEmotion = _selectedUserEmotion.value

                val initialResult = aiService.generateCompanionReply(
                    userMessage = text,
                    language = lang,
                    userName = userName,
                    memories = memories.value,
                    recentHistory = recentHistory,
                    webSources = emptyList(),
                    allowLongTermMemory = allowMemory,
                    apiKey = settings.customGeminiApiKey,
                    userEmotion = activeEmotion
                )

                val turn = initialResult.getOrElse { err ->
                    val friendly = mapFriendlyAiError(err, lang)
                    appendSathiErrorReply(friendly, lang, voice, speakReply)
                    return@launch
                }

                // If AI requests live web search, fetch real HTTPS sources and summarize
                if (turn.actionType == PhoneActionType.WEB_SEARCH) {
                    val searchQuery = turn.actionParam.ifBlank { text }
                    val sources = webSearchService.searchWeb(searchQuery, lang)
                    if (sources.isNotEmpty()) {
                        val groundedResult = aiService.generateCompanionReply(
                            userMessage = text,
                            language = lang,
                            userName = userName,
                            memories = memories.value,
                            recentHistory = recentHistory,
                            webSources = sources,
                            allowLongTermMemory = allowMemory,
                            apiKey = settings.customGeminiApiKey,
                            userEmotion = activeEmotion
                        ).getOrElse { turn }
                        handleTurnOutput(groundedResult, sources, isOffline = false, speakReply = speakReply, lang = lang, voice = voice, sessionId = sid)
                    } else {
                        val fallbackTurn = turn.copy(
                            replyText = if (lang == SathiLanguage.BN) {
                                "দুঃখিত, এই বিষয়ে ইন্টারনেটে সঠিক তথ্য পাওয়া যায়নি। নির্দিষ্ট বিষয় বা শহরের নাম লিখে আবার চেষ্টা করুন।"
                            } else {
                                "Sorry, I couldn't find reliable online results for that. Please try with a more specific topic."
                            },
                            actionType = PhoneActionType.NONE
                        )
                        handleTurnOutput(fallbackTurn, emptyList(), isOffline = false, speakReply = speakReply, lang = lang, voice = voice, sessionId = sid)
                    }
                } else {
                    handleTurnOutput(turn, turn.groundedSources, isOffline = false, speakReply = speakReply, lang = lang, voice = voice, sessionId = sid)
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun handleTurnOutput(
        turn: com.example.data.SathiTurnResult,
        sources: List<WebSource>,
        isOffline: Boolean,
        speakReply: Boolean,
        lang: SathiLanguage,
        voice: SathiVoiceProfile,
        sessionId: String
    ) {
        _activeMood.value = turn.mood

        // Save memory if premium/enabled and detected
        val canSaveMemory = (userProfile.value?.hasPremiumAccess() == true) || globalConfig.value.longTermMemoryForFree
        if (turn.shouldSaveMemory && canSaveMemory && turn.memoryContent.isNotBlank()) {
            repository.addMemory(
                uid = currentUserId,
                category = turn.memoryCategory,
                title = turn.memoryTitle.ifBlank { "মনে রাখা তথ্য" },
                content = turn.memoryContent
            )
        }

        // Execute phone action if requested
        var actionLabel: String? = null
        var generatedThumbnail: Bitmap? = null
        var generatedBadge: String? = null
        var finalReplyText = turn.replyText

        if (turn.actionType == PhoneActionType.GENERATE_IMAGE) {
            val outcome = imageGenRepository.generateImage(
                prompt = turn.actionParam.ifBlank { "Artwork" },
                language = lang
            ).getOrNull()
            if (outcome != null) {
                _generatedImagePreview.value = outcome
                generatedThumbnail = outcome.bitmap
                generatedBadge = if (lang == SathiLanguage.BN) {
                    if (outcome.isPlaceholderPreview) "নমুনা প্রিভিউ • ডাউনলোডযোগ্য" else "সাথী এআই (Sathi AI) • তৈরি ছবি"
                } else {
                    if (outcome.isPlaceholderPreview) "Sample Preview • Downloadable" else "Sathi AI • Generated Image"
                }
                actionLabel = outcome.requiresFirebaseNotice
                finalReplyText = outcome.caption
            }
        } else if (turn.actionType != PhoneActionType.NONE && turn.actionType != PhoneActionType.WEB_SEARCH) {
            when (val outcome = deviceActions.executeAction(turn.actionType, turn.actionParam, turn.secondaryParam, lang, turn.recipientParam)) {
                is ActionOutcome.Executed -> {
                    if (outcome.label.isNotBlank()) actionLabel = outcome.label
                }
                is ActionOutcome.NeedsConfirmation -> {
                    _pendingConfirmation.value = outcome.confirmation
                }
                is ActionOutcome.Failed -> {
                    _bannerState.value = UiBannerState.Error(outcome.message)
                }
            }
        }

        val replyMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = finalReplyText,
            mood = turn.mood,
            sources = sources,
            mediaThumbnail = generatedThumbnail,
            mediaBadge = generatedBadge,
            actionLabel = actionLabel,
            isOfflineReply = isOffline,
            sessionId = sessionId
        )
        _messages.value = _messages.value + replyMsg
        repository.upsertChatMessage(currentUserId, sessionId, replyMsg)
        startStreamingReveal(replyMsg.id, finalReplyText)
        if (speakReply) {
            voiceController.speak(finalReplyText, lang, voice, turn.mood)
        }
    }

    fun generateImageFromPrompt(
        prompt: String,
        addToChat: Boolean = true,
        referenceBitmap: Bitmap? = null
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() && referenceBitmap == null) return
        val effectivePrompt = cleanPrompt.ifBlank { "Artistic portrait variation" }
        val lang = currentLanguage()
        val voice = currentVoice()
        viewModelScope.launch {
            if (addToChat && referenceBitmap == null) {
                sendUserMessage(
                    if (lang == SathiLanguage.BN) "ছবি তৈরি করো: $effectivePrompt" else "Create image: $effectivePrompt",
                    speakReply = true
                )
            } else if (addToChat && referenceBitmap != null) {
                val safeRef = BitmapUtils.ensureSoftwareBitmap(referenceBitmap)
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = if (lang == SathiLanguage.BN) "উদাহরণ ছবি দেখে তৈরি করো: $effectivePrompt" else "Generate from example: $effectivePrompt",
                    mediaThumbnail = safeRef,
                    mediaBadge = if (lang == SathiLanguage.BN) "উদাহরণ ছবি" else "Reference"
                )
                _messages.value = _messages.value + userMsg
                _isThinking.value = true
                try {
                    val sid = ensureActiveSessionWithTitle(effectivePrompt)
                    repository.upsertChatMessage(currentUserId, sid, userMsg)
                    val res = imageGenRepository.generateImage(effectivePrompt, lang, safeRef)
                    res.onSuccess { outcome ->
                        _generatedImagePreview.value = outcome
                        val aiMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            isUser = false,
                            text = outcome.caption,
                            mood = SathiMood.WARM,
                            mediaThumbnail = outcome.bitmap,
                            mediaBadge = if (lang == SathiLanguage.BN) {
                                if (outcome.isPlaceholderPreview) "উদাহরণভিত্তিক নমুনা • ডাউনলোডযোগ্য" else "সাথী এআই • উদাহরণভিত্তিক ছবি"
                            } else {
                                if (outcome.isPlaceholderPreview) "Reference Preview • Downloadable" else "Sathi AI • Reference Art"
                            },
                            actionLabel = outcome.requiresFirebaseNotice,
                            sessionId = sid
                        )
                        _messages.value = _messages.value + aiMsg
                        repository.upsertChatMessage(currentUserId, sid, aiMsg)
                        startStreamingReveal(aiMsg.id, outcome.caption)
                        voiceController.speak(outcome.caption, lang, voice, SathiMood.WARM)
                    }.onFailure { err ->
                        val errText = err.localizedMessage ?: if (lang == SathiLanguage.BN) "ছবি তৈরি করা যায়নি।" else "Could not generate image."
                        appendSathiErrorReply(errText, lang, voice, speakReply = true)
                    }
                } finally {
                    _isThinking.value = false
                }
            } else {
                _studioLoading.value = true
                val safeRef = referenceBitmap?.let { BitmapUtils.ensureSoftwareBitmap(it) }
                val res = imageGenRepository.generateImage(effectivePrompt, lang, safeRef)
                _studioLoading.value = false
                res.onSuccess { outcome ->
                    _generatedImagePreview.value = outcome
                    _studioResult.value = outcome.caption
                    voiceController.speak(outcome.caption, lang, voice, SathiMood.WARM, autoResumeLiveAfter = false)
                }.onFailure { err ->
                    _bannerState.value = UiBannerState.Error(
                        err.localizedMessage ?: if (lang == SathiLanguage.BN) "ছবি তৈরি করা যায়নি।" else "Could not generate image."
                    )
                }
            }
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, titlePrefix: String = "SathiAI") {
        val lang = currentLanguage()
        val res = BitmapUtils.saveBitmapToGallery(context, bitmap, titlePrefix)
        res.onSuccess {
            _bannerState.value = UiBannerState.Info(
                if (lang == SathiLanguage.BN) "ছবিটি ফোনের গ্যালারিতে (Pictures/SathiAI) ডাউনলোড হয়েছে ✓"
                else "Image saved to gallery (Pictures/SathiAI) ✓"
            )
        }.onFailure { err ->
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "গ্যালারিতে সংরক্ষণ করা যায়নি: ${err.localizedMessage.orEmpty()}"
                else "Failed to save image: ${err.localizedMessage.orEmpty()}"
            )
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String = "Sathi AI") {
        val lang = currentLanguage()
        val res = BitmapUtils.shareImageToApps(context, bitmap, title)
        res.onFailure {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "ছবি শেয়ার করা যায়নি।" else "Could not share image."
            )
        }
    }

    private fun appendSathiErrorReply(
        message: String,
        lang: SathiLanguage,
        voice: SathiVoiceProfile,
        speakReply: Boolean
    ) {
        _messages.value = _messages.value + ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = message,
            mood = SathiMood.COMFORTING
        )
        _bannerState.value = UiBannerState.Error(message)
        if (speakReply) {
            voiceController.speak(message, lang, voice, SathiMood.COMFORTING)
        }
    }

    fun confirmPendingAction(
        editedTarget: String? = null,
        editedMessage: String? = null,
        editedRecipient: String? = null
    ) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val lang = currentLanguage()
        val resolvedTarget = editedTarget?.trim()?.ifBlank { pending.target } ?: pending.target
        val resolvedMessage = editedMessage?.trim()?.ifBlank { pending.messageBody } ?: pending.messageBody
        val resolvedRecipient = editedRecipient?.trim() ?: pending.recipientName

        val outcome = when (pending.type) {
            PhoneActionType.CALL_CONFIRM -> deviceActions.executeConfirmedCall(resolvedTarget, lang)
            PhoneActionType.SMS_CONFIRM -> deviceActions.executeConfirmedSms(resolvedTarget, resolvedMessage, lang)
            PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> deviceActions.executeConfirmedSocialMessage(
                platform = pending.extraParam.ifBlank { resolvedTarget },
                messageBody = resolvedMessage,
                language = lang,
                recipientName = resolvedRecipient
            )
            else -> ActionOutcome.Executed("")
        }
        when (outcome) {
            is ActionOutcome.Executed -> if (outcome.label.isNotBlank()) {
                _bannerState.value = UiBannerState.Info(outcome.label)
            }
            is ActionOutcome.Failed -> _bannerState.value = UiBannerState.Error(outcome.message)
            is ActionOutcome.NeedsConfirmation -> {}
        }
    }

    fun dismissPendingAction() {
        _pendingConfirmation.value = null
    }

    fun speakMessage(text: String, mood: SathiMood = SathiMood.WARM) {
        voiceController.speak(text, currentLanguage(), currentVoice(), mood)
    }

    fun previewVoiceSample(profile: SathiVoiceProfile) {
        val lang = currentLanguage()
        val sample = if (lang == SathiLanguage.BN) {
            if (profile == SathiVoiceProfile.FEMALE) {
                "আসসালামু আলাইকুম! আমি সাথী, আপনার মিষ্টি মেয়ে কণ্ঠের সাথী। কেমন আছেন আজ?"
            } else {
                "আসসালামু আলাইকুম! আমি সাথী, আপনার মার্জিত ছেলে কণ্ঠের সাথী। আজ আপনাকে কীভাবে সাহায্য করতে পারি?"
            }
        } else {
            if (profile == SathiVoiceProfile.FEMALE) {
                "Assalamu Alaikum! I am Sathi in the sweet female voice. I am always here with you."
            } else {
                "Assalamu Alaikum! I am Sathi in the warm male voice. How can I help you today?"
            }
        }
        voiceController.speak(sample, lang, profile, SathiMood.WARM, autoResumeLiveAfter = false)
    }

    fun updateLanguageAndVoice(language: SathiLanguage, voice: SathiVoiceProfile, userName: String? = null) {
        viewModelScope.launch {
            preferences.saveLanguageAndVoice(language.code, voice.code)
            val resolvedName = userName ?: userProfile.value?.userName ?: displayName
            repository.updateUserPreferences(currentUserId, resolvedName, language.code, voice.code)
        }
    }

    // --- Memory CRUD ---

    fun addMemory(category: MemoryCategory, title: String, content: String) {
        viewModelScope.launch {
            val res = repository.addMemory(currentUserId, category, title, content)
            if (res.isFailure) {
                _bannerState.value = UiBannerState.Error(
                    if (currentLanguage() == SathiLanguage.BN) "স্মৃতি সংরক্ষণ করা যায়নি।"
                    else "Could not save memory."
                )
            }
        }
    }

    fun updateMemory(memoryId: String, category: MemoryCategory, title: String, content: String) {
        viewModelScope.launch {
            val res = repository.updateMemory(currentUserId, memoryId, category, title, content)
            if (res.isFailure) {
                _bannerState.value = UiBannerState.Error(
                    if (currentLanguage() == SathiLanguage.BN) "স্মৃতি হালনাগাদ করা যায়নি।"
                    else "Could not update memory."
                )
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, memoryId)
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            repository.deleteAllUserData(currentUserId)
            preferences.clearAll()
            _activeSessionId.value = null
            _messages.value = emptyList()
            seedWelcomeMessage()
            val lang = currentLanguage()
            _bannerState.value = UiBannerState.Info(
                if (lang == SathiLanguage.BN) "আপনার সব স্মৃতি ও ডেটা মুছে ফেলা হয়েছে।"
                else "All your memories and local data have been deleted."
            )
        }
    }

    // --- Premium Studio Tools ---

    fun generateMorningGreeting() {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) {
            _morningGreeting.value = if (lang == SathiLanguage.BN) {
                "শুভ সকাল! নতুন দিনের আলোয় আপনার মন থাকুক প্রশান্ত ও আনন্দময়।"
            } else {
                "Good morning! May today bring peace, clarity, and gentle joy."
            }
            return
        }
        viewModelScope.launch {
            _studioLoading.value = true
            val memorySummary = memories.value.take(3).joinToString(", ") { "${it.title}: ${it.content}" }
            val prompt = "Write a short, uplifting, heartfelt 2-sentence morning greeting for ${userProfile.value?.userName ?: displayName}."
            val res = aiService.runStudioTool(
                instruction = prompt,
                input = memorySummary.ifBlank { "New fresh day" },
                language = lang,
                apiKey = localSettings.value.customGeminiApiKey
            )
            _studioLoading.value = false
            res.onSuccess {
                _morningGreeting.value = it
                voiceController.speak(it, lang, currentVoice(), SathiMood.HAPPY)
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    fun analyzeCameraBitmap(bitmap: Bitmap, question: String) {
        val lang = currentLanguage()
        viewModelScope.launch {
            _studioLoading.value = true
            val res = aiService.analyzeImage(
                bitmap = bitmap,
                question = question,
                language = lang,
                isScreenCapture = false,
                apiKey = localSettings.value.customGeminiApiKey
            )
            _studioLoading.value = false
            res.onSuccess {
                _studioResult.value = it
                voiceController.speak(it, lang, currentVoice(), SathiMood.WARM, autoResumeLiveAfter = false)
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    fun analyzeStudioVideo(context: Context, uri: Uri, question: String) {
        val lang = currentLanguage()
        viewModelScope.launch {
            _studioLoading.value = true
            val res = aiService.analyzeVideo(context, uri, question, lang)
            _studioLoading.value = false
            res.onSuccess { outcome ->
                _studioResult.value = outcome.summaryText
                voiceController.speak(outcome.summaryText, lang, currentVoice(), SathiMood.WARM, autoResumeLiveAfter = false)
            }.onFailure {
                _bannerState.value = UiBannerState.Error(
                    if (lang == SathiLanguage.BN) "ভিডিও বিশ্লেষণ করা যায়নি।"
                    else "Could not analyze video."
                )
            }
        }
    }

    fun runPremiumStudioTool(instruction: String, input: String) {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "এই এআই টুলটির জন্য ইন্টারনেট প্রয়োজন।"
                else "Internet connection is required for this AI tool."
            )
            return
        }
        viewModelScope.launch {
            _studioLoading.value = true
            val res = aiService.runStudioTool(
                instruction = instruction,
                input = input,
                language = lang,
                apiKey = localSettings.value.customGeminiApiKey
            )
            _studioLoading.value = false
            res.onSuccess {
                _studioResult.value = it
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    // --- Plans & Code Redemption ---

    fun redeemCode(rawCode: String) {
        val lang = currentLanguage()
        val now = System.currentTimeMillis()
        val lockoutUntil = localSettings.value.codeLockoutUntilMs
        if (now < lockoutUntil) {
            val minsLeft = ((lockoutUntil - now) / 60_000L) + 1
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "অনেকবার ভুল কোড দিয়েছেন। অনুগ্রহ করে $minsLeft মিনিট পর চেষ্টা করুন।"
                else "Too many wrong attempts. Please try again in $minsLeft minute(s)."
            )
            return
        }

        viewModelScope.launch {
            val res = repository.redeemPremiumCode(currentUserId, rawCode)
            res.onSuccess { planTier ->
                preferences.resetCodeFailures()
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) "অভিনন্দন! আপনার প্ল্যান এখন: ${planTier.labelBn}"
                    else "Success! Your plan is now: ${planTier.labelEn}"
                )
            }.onFailure {
                val lockedUntil = preferences.recordCodeFailure()
                val msg = if (lockedUntil > 0L) {
                    if (lang == SathiLanguage.BN) "ভুল কোড! নিরাপত্তার জন্য ৫ মিনিটের জন্য কোড যাচাই স্থগিত করা হয়েছে।"
                    else "Invalid code. Verification locked for 5 minutes for security."
                } else {
                    if (lang == SathiLanguage.BN) "কোডটি সঠিক নয় বা ইতিমধ্যে ব্যবহৃত/বাতিল হয়েছে।"
                    else "That code is invalid, already used, or revoked."
                }
                _bannerState.value = UiBannerState.Error(msg)
            }
        }
    }

    // --- Offline Pack Manager ---

    fun selectOfflinePackTier(tier: OfflinePackTier) {
        viewModelScope.launch { preferences.setOfflinePackTier(tier) }
    }

    fun setWifiOnlyDownload(wifiOnly: Boolean) {
        viewModelScope.launch { preferences.setWifiOnlyDownload(wifiOnly) }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setOfflineModeEnabled(enabled) }
    }

    fun updateOfflinePack() {
        startOrResumeOfflineDownload(forceRefresh = true)
    }

    fun downloadCustomOfflineTopic(topic: String) {
        val lang = currentLanguage()
        val clean = topic.trim()
        if (clean.length < 2) return
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "নতুন বিষয় অফলাইনে যোগ করতে ইন্টারনেট প্রয়োজন।"
                else "Internet is needed to fetch a custom topic for offline use."
            )
            return
        }
        viewModelScope.launch {
            _offlineDownloadStatus.value = if (lang == SathiLanguage.BN) "খুঁজে সংরক্ষণ করা হচ্ছে: $clean" else "Saving topic: $clean"
            val res = offlinePackManager.downloadCustomOfflineTopic(clean, lang)
            _offlineDownloadStatus.value = ""
            res.onSuccess { entry ->
                preferences.setOfflinePackState(OfflinePackState.INSTALLED, 1f)
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) {
                        "\"${entry.topic}\" অফলাইন প্যাকে যোগ হয়েছে! এখন ইন্টারনেট ছাড়াও এটি জানতে পারবেন।"
                    } else {
                        "Added \"${entry.topic}\" to your offline knowledge pack!"
                    }
                )
            }.onFailure {
                _bannerState.value = UiBannerState.Error(
                    if (lang == SathiLanguage.BN) "\"$clean\" সম্পর্কে তথ্য খুঁজে পাওয়া যায়নি।"
                    else "Could not find an article for \"$clean\"."
                )
            }
        }
    }

    fun startAutoKnowledgeBrainSync(apiKey: String = localSettings.value.customGeminiApiKey) {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) return
        offlineDownloadJob?.cancel()
        offlineDownloadJob = viewModelScope.launch {
            preferences.setOfflinePackState(OfflinePackState.DOWNLOADING, 0.04f)
            val result = offlinePackManager.syncSmartKnowledgeBrainWithGemini(
                apiKey = apiKey,
                aiService = aiService
            ) { progress, statusBn, statusEn ->
                _offlineDownloadStatus.value = if (lang == SathiLanguage.BN) statusBn else statusEn
                preferences.setOfflinePackState(
                    if (progress >= 1f) OfflinePackState.INSTALLED else OfflinePackState.DOWNLOADING,
                    progress
                )
            }
            if (result.isSuccess) {
                preferences.setOfflinePackState(OfflinePackState.INSTALLED, 1f)
                _offlineDownloadStatus.value = ""
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) {
                        "স্মার্ট জ্ঞান ভান্ডার (${offlinePackManager.getInstalledTopicCount()}টি বিষয় • ${offlinePackManager.getInstalledSizeSummary()} / 2.00 GB) সক্রিয়!"
                    } else {
                        "Smart Knowledge Brain (${offlinePackManager.getInstalledTopicCount()} topics • ${offlinePackManager.getInstalledSizeSummary()} / 2.00 GB) ready!"
                    }
                )
            } else {
                preferences.setOfflinePackState(OfflinePackState.PAUSED, localSettings.value.offlineDownloadProgress)
                _offlineDownloadStatus.value = ""
            }
        }
    }

    fun startOrResumeOfflineDownload(forceRefresh: Boolean = false) {
        val lang = currentLanguage()
        val settings = localSettings.value
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "অফলাইন প্যাক ডাউনলোড করতে ইন্টারনেট প্রয়োজন।"
                else "Internet connection is needed to download the Offline Pack."
            )
            return
        }
        if (settings.wifiOnlyDownload && !networkMonitor.isWifiConnected()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "ওয়াই-ফাই ছাড়া ডাউনলোড বন্ধ আছে। ওয়াই-ফাই চালু করুন অথবা সেটিংস থেকে 'শুধু ওয়াই-ফাই' অপশনটি বন্ধ করুন।"
                else "Wi-Fi Only is enabled. Connect to Wi-Fi or turn off Wi-Fi Only to download."
            )
            return
        }

        offlineDownloadJob?.cancel()
        offlineDownloadJob = viewModelScope.launch {
            preferences.setOfflinePackState(OfflinePackState.DOWNLOADING, settings.offlineDownloadProgress)
            val result: Result<*> = if (settings.offlinePackTier == OfflinePackTier.DEEP) {
                offlinePackManager.syncSmartKnowledgeBrainWithGemini(
                    apiKey = settings.customGeminiApiKey,
                    aiService = aiService
                ) { progress, statusBn, statusEn ->
                    _offlineDownloadStatus.value = if (lang == SathiLanguage.BN) statusBn else statusEn
                    preferences.setOfflinePackState(
                        if (progress >= 1f) OfflinePackState.INSTALLED else OfflinePackState.DOWNLOADING,
                        progress
                    )
                }
            } else {
                offlinePackManager.downloadRealOfflinePack(
                    tier = settings.offlinePackTier,
                    forceRefresh = forceRefresh
                ) { progress, statusBn, statusEn ->
                    _offlineDownloadStatus.value = if (lang == SathiLanguage.BN) statusBn else statusEn
                    preferences.setOfflinePackState(
                        if (progress >= 1f) OfflinePackState.INSTALLED else OfflinePackState.DOWNLOADING,
                        progress
                    )
                }
            }
            if (result.isSuccess) {
                preferences.setOfflinePackState(OfflinePackState.INSTALLED, 1f)
                _offlineDownloadStatus.value = ""
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) "অফলাইন প্যাক (${offlinePackManager.getInstalledTopicCount()}টি বিষয়) ডাউনলোড/আপডেট সম্পন্ন হয়েছে!"
                    else "Offline Pack (${offlinePackManager.getInstalledTopicCount()} topics) downloaded/updated!"
                )
            } else {
                preferences.setOfflinePackState(OfflinePackState.PAUSED, localSettings.value.offlineDownloadProgress)
                _bannerState.value = UiBannerState.Error(
                    if (lang == SathiLanguage.BN) "ডাউনলোডে সাময়িক সমস্যা হয়েছে, 'পুনরায় শুরু' চাপুন।"
                    else "Download interrupted. Tap Resume to continue."
                )
            }
        }
    }

    fun pauseOfflineDownload() {
        offlineDownloadJob?.cancel()
        viewModelScope.launch {
            preferences.setOfflinePackState(
                OfflinePackState.PAUSED,
                localSettings.value.offlineDownloadProgress
            )
        }
    }

    fun deleteOfflinePack() {
        offlineDownloadJob?.cancel()
        offlinePackManager.deletePackOnDisk()
        _offlineDownloadStatus.value = ""
        viewModelScope.launch {
            preferences.setOfflinePackState(OfflinePackState.NOT_INSTALLED, 0f)
            preferences.setOfflineModeEnabled(false)
        }
    }

    fun getOfflinePackStorageSummary(): String =
        offlinePackManager.getStorageBudgetLabel(currentLanguage())

    fun getOfflineBrainBudgetSummary(language: SathiLanguage): String =
        offlinePackManager.getStorageBudgetLabel(language)

    fun installOfflineBengaliTts() {
        offlinePackManager.openSystemTtsVoiceInstaller()
    }

    fun openOfflineSpeechRecognitionSettings() {
        voiceController.openOfflineSpeechRecognitionSettings()
    }

    // --- Admin Panel ---

    fun unlockAdminSession() {
        if (!_isOwnerAdmin.value) return
        _adminUnlocked.value = true
        adminObserverJob?.cancel()
        adminObserverJob = viewModelScope.launch {
            launch {
                repository.observeAllCodes()
                    .catch { emit(emptyList()) }
                    .collect { _adminCodes.value = it }
            }
            launch {
                repository.observeAllUsers()
                    .catch { emit(emptyList()) }
                    .collect { _adminUsers.value = it }
            }
            launch {
                repository.observeActivityLogs()
                    .catch { emit(emptyList()) }
                    .collect { _adminLogs.value = it }
            }
            launch {
                repository.observeLiveMessagesForAdmin()
                    .catch { emit(emptyList()) }
                    .collect { _adminLiveMessages.value = it }
            }
            launch {
                repository.observeAllSessionsForAdmin()
                    .catch { emit(emptyList()) }
                    .collect { _adminAllSessions.value = it }
            }
        }
    }

    fun lockAdminSession() {
        _adminUnlocked.value = false
        adminObserverJob?.cancel()
    }

    fun adminGenerateCode(type: CodeType, note: String) {
        viewModelScope.launch {
            val res = repository.generatePremiumCode(currentUserId, userEmail, type, note)
            res.onSuccess { code ->
                _bannerState.value = UiBannerState.Info("Generated Code: $code")
            }.onFailure {
                _bannerState.value = UiBannerState.Error("Failed to generate code.")
            }
        }
    }

    fun adminRevokeCode(codeId: String) {
        viewModelScope.launch {
            repository.revokePremiumCode(currentUserId, userEmail, codeId)
        }
    }

    fun adminUpdateUser(targetUid: String, plan: PlanTier, banned: Boolean) {
        viewModelScope.launch {
            repository.adminUpdateUser(currentUserId, userEmail, targetUid, plan, banned)
        }
    }

    fun adminSaveGlobalConfig(config: AppConfig) {
        viewModelScope.launch {
            val res = repository.adminUpdateGlobalConfig(currentUserId, userEmail, config)
            if (res.isSuccess) {
                _bannerState.value = UiBannerState.Info(
                    if (currentLanguage() == SathiLanguage.BN) "গ্লোবাল সেটিংস সংরক্ষিত হয়েছে।"
                    else "Global settings saved."
                )
            }
        }
    }

    fun clearBanner() {
        _bannerState.value = UiBannerState.None
        voiceController.clearError()
    }

    private fun mapFriendlyAiError(e: Throwable, language: SathiLanguage): String {
        val msg = e.localizedMessage.orEmpty().lowercase()
        return when {
            msg.contains("timeout") ->
                if (language == SathiLanguage.BN) "নেটওয়ার্ক ধীরগতির হওয়ায় উত্তর আসতে দেরি হচ্ছে। আবার চেষ্টা করুন।"
                else "The connection is slow and timed out. Please try again."
            msg.contains("network") || msg.contains("unavailable") || !networkMonitor.isOnline() ->
                if (language == SathiLanguage.BN) "ইন্টারনেট সংযোগ বিচ্ছিন্ন রয়েছে। অনুগ্রহ করে সংযোগ পরীক্ষা করুন।"
                else "No internet connection. Please check your network."
            else ->
                if (language == SathiLanguage.BN) "দুঃখিত, এই মুহূর্তে উত্তর তৈরি করা যায়নি। একটু পর আবার চেষ্টা করুন।"
                else "Sorry, I couldn't complete that response right now. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
        streamingJob?.cancel()
        offlineDownloadJob?.cancel()
        adminObserverJob?.cancel()
    }

    companion object {
        private const val TAG = "SathiViewModel"
    }
}
