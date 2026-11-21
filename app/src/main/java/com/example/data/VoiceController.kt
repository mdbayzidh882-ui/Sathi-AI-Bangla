package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceEngineState(
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val liveSessionActive: Boolean = false,
    val partialTranscript: String = "",
    val ttsReady: Boolean = false,
    val speechErrorMessage: String? = null
)

class VoiceController(
    context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceEngineState())
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeLanguage: SathiLanguage = SathiLanguage.BN
    private var preferOfflineRecognition: Boolean = false
    private var liveAutoListenAfterTts: Boolean = false
    private var consecutiveLiveSilenceCount: Int = 0
    private var lastPartialTranscript: String = ""

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                        consecutiveLiveSilenceCount = 0
                        if (_state.value.liveSessionActive && liveAutoListenAfterTts) {
                            liveAutoListenAfterTts = false
                            mainHandler.postDelayed({
                                if (_state.value.liveSessionActive && !_state.value.listening && !_state.value.speaking) {
                                    startListeningInternal(activeLanguage, preferOfflineRecognition)
                                }
                            }, 350L)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(speaking = false)
                    }
                })
                _state.value = _state.value.copy(ttsReady = true)
            } else {
                Log.w(TAG, "TextToSpeech initialization returned status=$status")
            }
        }
    }

    fun startLiveSession(language: SathiLanguage, preferOffline: Boolean = false) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline
        consecutiveLiveSilenceCount = 0
        _state.value = _state.value.copy(liveSessionActive = true, speechErrorMessage = null)
        startListeningInternal(language, preferOffline)
    }

    fun stopLiveSession() {
        liveAutoListenAfterTts = false
        consecutiveLiveSilenceCount = 0
        lastPartialTranscript = ""
        _state.value = _state.value.copy(liveSessionActive = false)
        stopListening()
        stopSpeaking()
    }

    fun setLiveSessionEnabled(enabled: Boolean) {
        if (!enabled) {
            liveAutoListenAfterTts = false
        }
        _state.value = _state.value.copy(liveSessionActive = enabled)
    }

    fun startListening(language: SathiLanguage, preferOffline: Boolean = false) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline
        consecutiveLiveSilenceCount = 0
        startListeningInternal(language, preferOffline)
    }

    private fun startListeningInternal(language: SathiLanguage, preferOffline: Boolean) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline
        mainHandler.post {
            liveAutoListenAfterTts = false
            lastPartialTranscript = ""
            stopSpeakingInternal()
            _state.value = _state.value.copy(speechErrorMessage = null, partialTranscript = "")

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = _state.value.copy(
                    listening = false,
                    liveSessionActive = false,
                    speechErrorMessage = if (language == SathiLanguage.BN) {
                        "এই ডিভাইসে ভয়েস রেকগনিশন পাওয়া যায়নি। নিচে লিখে কথা বলতে পারেন।"
                    } else {
                        "Speech recognition is not available on this device. You can type below."
                    }
                )
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.value = _state.value.copy(listening = true, speechErrorMessage = null)
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _state.value = _state.value.copy(listening = false)
                        }

                        override fun onError(error: Int) {
                            val rescued = lastPartialTranscript.trim()
                            lastPartialTranscript = ""
                            val isTimeoutOrNoMatch = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                            if (isTimeoutOrNoMatch && rescued.length >= 2) {
                                consecutiveLiveSilenceCount = 0
                                _state.value = _state.value.copy(listening = false, partialTranscript = rescued)
                                onFinalTranscript(rescued)
                                return
                            }

                            if (_state.value.liveSessionActive && isTimeoutOrNoMatch && consecutiveLiveSilenceCount < 8) {
                                consecutiveLiveSilenceCount++
                                _state.value = _state.value.copy(listening = false, speechErrorMessage = null)
                                mainHandler.postDelayed({
                                    if (_state.value.liveSessionActive && !_state.value.speaking) {
                                        startListeningInternal(activeLanguage, preferOfflineRecognition)
                                    }
                                }, 320L)
                                return
                            }

                            val msg = mapSpeechError(error, activeLanguage)
                            _state.value = _state.value.copy(
                                listening = false,
                                liveSessionActive = false,
                                speechErrorMessage = msg
                            )
                        }

                        override fun onResults(results: Bundle?) {
                            consecutiveLiveSilenceCount = 0
                            lastPartialTranscript = ""
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim().orEmpty()
                            _state.value = _state.value.copy(
                                listening = false,
                                partialTranscript = spoken
                            )
                            if (spoken.isNotEmpty()) {
                                onFinalTranscript(spoken)
                            } else if (_state.value.liveSessionActive) {
                                mainHandler.postDelayed({
                                    if (_state.value.liveSessionActive && !_state.value.speaking) {
                                        startListeningInternal(activeLanguage, preferOfflineRecognition)
                                    }
                                }, 320L)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                lastPartialTranscript = partial
                                _state.value = _state.value.copy(partialTranscript = partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val localeTag = if (language == SathiLanguage.BN) "bn-BD" else "en-US"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
                    if (preferOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }
                speechRecognizer?.startListening(intent)
                _state.value = _state.value.copy(listening = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                _state.value = _state.value.copy(
                    listening = false,
                    liveSessionActive = false,
                    speechErrorMessage = if (language == SathiLanguage.BN) {
                        "মাইক্রোফোন চালু করতে সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "Could not start microphone. Please try again."
                    }
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _state.value = _state.value.copy(listening = false)
        }
    }

    fun interruptAndListen(language: SathiLanguage, preferOffline: Boolean = false) {
        stopSpeaking()
        startListening(language, preferOffline)
    }

    fun speak(
        text: String,
        language: SathiLanguage,
        voiceProfile: SathiVoiceProfile,
        mood: SathiMood,
        autoResumeLiveAfter: Boolean = _state.value.liveSessionActive
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val engine = tts ?: return

        mainHandler.post {
            try {
                liveAutoListenAfterTts = autoResumeLiveAfter && _state.value.liveSessionActive

                val hasBanglaChars = cleanText.any { it in '\u0980'..'\u09FF' }
                val targetLocale = if (language == SathiLanguage.BN || hasBanglaChars) {
                    Locale("bn", "BD")
                } else {
                    Locale.US
                }

                val langResult = engine.setLanguage(targetLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale("bn", "IN"))
                }

                val matchedDistinctVoice = selectSyntheticVoice(engine, targetLocale, voiceProfile)

                val basePitch = if (voiceProfile == SathiVoiceProfile.FEMALE) {
                    if (matchedDistinctVoice) 1.14f else 1.28f
                } else {
                    if (matchedDistinctVoice) 0.85f else 0.78f
                }
                val moodPitchDelta = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 0.05f
                    SathiMood.COMFORTING -> -0.04f
                    SathiMood.SERIOUS -> -0.02f
                    SathiMood.WARM -> 0.0f
                }
                val moodRate = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 1.03f
                    SathiMood.COMFORTING -> 0.90f
                    SathiMood.SERIOUS -> 0.96f
                    SathiMood.WARM -> 0.99f
                }

                engine.setPitch((basePitch + moodPitchDelta).coerceIn(0.68f, 1.48f))
                engine.setSpeechRate(moodRate)

                val utteranceId = UUID.randomUUID().toString()
                _state.value = _state.value.copy(speaking = true)
                engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
                liveAutoListenAfterTts = false
                _state.value = _state.value.copy(speaking = false)
            }
        }
    }

    fun stopSpeaking() {
        liveAutoListenAfterTts = false
        mainHandler.post { stopSpeakingInternal() }
    }

    private fun stopSpeakingInternal() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(speaking = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(speechErrorMessage = null)
    }

    fun isBengaliTtsAvailable(): Boolean {
        val engine = tts ?: return false
        return try {
            val res = engine.isLanguageAvailable(Locale("bn", "BD"))
            res >= TextToSpeech.LANG_AVAILABLE ||
                engine.isLanguageAvailable(Locale("bn", "IN")) >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        liveAutoListenAfterTts = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {
            }
        }
    }

    private fun selectSyntheticVoice(
        engine: TextToSpeech,
        locale: Locale,
        profile: SathiVoiceProfile
    ): Boolean {
        return try {
            val voices = engine.voices ?: return false
            val matchingLocale = voices
                .filter { v -> v.locale.language.equals(locale.language, ignoreCase = true) }
                .sortedBy { it.name }
            if (matchingLocale.isEmpty()) return false

            val femaleTokens = listOf(
                "female", "woman", "ban", "bnf", "bin", "kalpana", "tanisha",
                "-a-", "-c-", "-e-", "-g-", "x-sfg", "wavenet-a", "wavenet-c", "standard-a", "standard-c"
            )
            val maleTokens = listOf(
                "male", "man", "bfu", "bnm", "rishi", "bashkar", "pradeep",
                "-b-", "-d-", "-f-", "wavenet-b", "wavenet-d", "standard-b", "standard-d"
            )

            val explicitFemale = matchingLocale.firstOrNull { v ->
                val n = v.name.lowercase()
                femaleTokens.any { n.contains(it) } && maleTokens.none { n.contains(it) }
            }
            val explicitMale = matchingLocale.firstOrNull { v ->
                val n = v.name.lowercase()
                maleTokens.any { n.contains(it) }
            }

            val chosen: Voice? = if (profile == SathiVoiceProfile.FEMALE) {
                explicitFemale ?: matchingLocale.firstOrNull { it != explicitMale }
            } else {
                explicitMale ?: matchingLocale.lastOrNull { it != explicitFemale }
            }

            if (chosen != null) {
                engine.voice = chosen
                (profile == SathiVoiceProfile.FEMALE && explicitFemale != null) ||
                    (profile == SathiVoiceProfile.MALE && explicitMale != null)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun mapSpeechError(error: Int, language: SathiLanguage): String? {
        return when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (language == SathiLanguage.BN) "কথা স্পষ্ট শোনা যায়নি, আবার বলুন বা মাইক্রোফোন চাপুন।"
                else "I didn't catch that clearly. Please speak again or tap the mic."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (language == SathiLanguage.BN) "ভয়েস চিনতে নেটওয়ার্ক সমস্যা হয়েছে, নিচে লিখে কথা বলতে পারেন।"
                else "Network issue while listening. You can type your message below."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                if (language == SathiLanguage.BN) "কথা শোনার জন্য মাইক্রোফোন অনুমতি প্রয়োজন।"
                else "Microphone permission is needed to listen."
            SpeechRecognizer.ERROR_CLIENT -> null
            else ->
                if (language == SathiLanguage.BN) "মাইক্রোফোনে সাময়িক সমস্যা হয়েছে, আবার চেষ্টা করুন।"
                else "Microphone had a brief issue. Please tap again."
        }
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}
