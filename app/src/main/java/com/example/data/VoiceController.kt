package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceEngineState(
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val liveSessionActive: Boolean = false,
    val autoVoiceEnabled: Boolean = true,
    val muted: Boolean = false,
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
    private var retriedOfflineAfterNetError: Boolean = false
    private var liveAutoListenAfterTts: Boolean = false
    private var consecutiveLiveSilenceCount: Int = 0
    private var busyRetryCount: Int = 0
    private var lastPartialTranscript: String = ""
    @Volatile
    private var startEndSoundsEnabled: Boolean = false

    // Real-time barge-in voice activity monitor while TTS speaks
    private val bargeInMonitorRunning = AtomicBoolean(false)
    @Volatile
    private var bargeInThread: Thread? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = true)
                        if (_state.value.liveSessionActive && _state.value.autoVoiceEnabled && !_state.value.muted) {
                            startBargeInMonitor()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        stopBargeInMonitor()
                        _state.value = _state.value.copy(speaking = false)
                        consecutiveLiveSilenceCount = 0
                        busyRetryCount = 0
                        scheduleAutoResumeAfterSpeech()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        stopBargeInMonitor()
                        _state.value = _state.value.copy(speaking = false)
                        scheduleAutoResumeAfterSpeech()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        stopBargeInMonitor()
                        _state.value = _state.value.copy(speaking = false)
                        scheduleAutoResumeAfterSpeech()
                    }
                })
                _state.value = _state.value.copy(ttsReady = true)
            } else {
                Log.w(TAG, "TextToSpeech initialization returned status=$status")
            }
        }
    }

    private fun scheduleAutoResumeAfterSpeech() {
        if (_state.value.liveSessionActive && _state.value.autoVoiceEnabled && liveAutoListenAfterTts && !_state.value.muted) {
            liveAutoListenAfterTts = false
            mainHandler.postDelayed({
                if (_state.value.liveSessionActive &&
                    _state.value.autoVoiceEnabled &&
                    !_state.value.muted &&
                    !_state.value.listening &&
                    !_state.value.speaking
                ) {
                    startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
                }
            }, 380L)
        }
    }

    fun resumeLiveListeningIfActive(language: SathiLanguage = activeLanguage, preferOffline: Boolean = preferOfflineRecognition) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline || !isNetworkConnected()
        mainHandler.postDelayed({
            if (_state.value.liveSessionActive &&
                _state.value.autoVoiceEnabled &&
                !_state.value.muted &&
                !_state.value.listening &&
                !_state.value.speaking
            ) {
                startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
            }
        }, 380L)
    }

    fun setAutoVoiceEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(autoVoiceEnabled = enabled)
        if (!enabled) {
            liveAutoListenAfterTts = false
            stopBargeInMonitor()
            _state.value = _state.value.copy(liveSessionActive = false)
        }
    }

    fun setStartEndSoundsEnabled(enabled: Boolean) {
        startEndSoundsEnabled = enabled
    }

    private fun playListeningCueSound(isStart: Boolean) {
        if (!startEndSoundsEnabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 42)
            val toneType = if (isStart) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
            tone.startTone(toneType, 110)
            mainHandler.postDelayed({
                runCatching { tone.release() }
            }, 180L)
        }
    }

    fun startListeningAfterWake(language: SathiLanguage, preferOffline: Boolean = false) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline || !isNetworkConnected()
        consecutiveLiveSilenceCount = 0
        busyRetryCount = 0
        retriedOfflineAfterNetError = false
        _state.value = _state.value.copy(
            liveSessionActive = _state.value.autoVoiceEnabled,
            muted = false,
            speechErrorMessage = null
        )
        startListeningInternal(language, preferOfflineRecognition)
    }

    fun startLiveSession(language: SathiLanguage, preferOffline: Boolean = false) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline || !isNetworkConnected()
        consecutiveLiveSilenceCount = 0
        busyRetryCount = 0
        retriedOfflineAfterNetError = false
        _state.value = _state.value.copy(
            liveSessionActive = _state.value.autoVoiceEnabled,
            muted = false,
            speechErrorMessage = null
        )
        startListeningInternal(language, preferOfflineRecognition)
    }

    fun stopLiveSession() {
        liveAutoListenAfterTts = false
        consecutiveLiveSilenceCount = 0
        busyRetryCount = 0
        lastPartialTranscript = ""
        stopBargeInMonitor()
        _state.value = _state.value.copy(liveSessionActive = false, muted = false)
        stopListening()
        stopSpeaking()
    }

    fun toggleMute() {
        setMuted(!_state.value.muted)
    }

    fun setMuted(muted: Boolean) {
        _state.value = _state.value.copy(muted = muted)
        if (muted) {
            liveAutoListenAfterTts = false
            stopBargeInMonitor()
            stopListening()
        } else if (_state.value.liveSessionActive && _state.value.autoVoiceEnabled && !_state.value.speaking && !_state.value.listening) {
            startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
        }
    }

    fun setLiveSessionEnabled(enabled: Boolean) {
        if (!enabled) {
            liveAutoListenAfterTts = false
            stopBargeInMonitor()
        }
        _state.value = _state.value.copy(liveSessionActive = enabled)
    }

    fun startListening(language: SathiLanguage, preferOffline: Boolean = false) {
        activeLanguage = language
        preferOfflineRecognition = preferOffline || !isNetworkConnected()
        consecutiveLiveSilenceCount = 0
        busyRetryCount = 0
        retriedOfflineAfterNetError = false
        startListeningInternal(language, preferOfflineRecognition)
    }

    private fun startListeningInternal(language: SathiLanguage, preferOffline: Boolean) {
        activeLanguage = language
        val useOffline = preferOffline || !isNetworkConnected()
        preferOfflineRecognition = useOffline
        mainHandler.post {
            liveAutoListenAfterTts = false
            lastPartialTranscript = ""
            stopBargeInMonitor()
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
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            busyRetryCount = 0
                            _state.value = _state.value.copy(listening = true, speechErrorMessage = null)
                            playListeningCueSound(isStart = true)
                        }

                        override fun onBeginningOfSpeech() {
                            consecutiveLiveSilenceCount = 0
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _state.value = _state.value.copy(listening = false)
                            playListeningCueSound(isStart = false)
                        }

                        override fun onError(error: Int) {
                            val rescued = lastPartialTranscript.trim()
                            lastPartialTranscript = ""
                            val isTimeoutOrNoMatch = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                            if (isTimeoutOrNoMatch && rescued.length >= 2) {
                                consecutiveLiveSilenceCount = 0
                                busyRetryCount = 0
                                _state.value = _state.value.copy(listening = false, partialTranscript = rescued)
                                onFinalTranscript(rescued)
                                return
                            }

                            // Automatic fallback retry with EXTRA_PREFER_OFFLINE when network speech fails
                            val isNetworkErr = error == SpeechRecognizer.ERROR_NETWORK ||
                                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                            if (isNetworkErr && !retriedOfflineAfterNetError) {
                                retriedOfflineAfterNetError = true
                                mainHandler.postDelayed({
                                    if (!_state.value.muted && !_state.value.speaking) {
                                        startListeningInternal(activeLanguage, true)
                                    }
                                }, 220L)
                                return
                            }

                            // Recover seamlessly if recognizer was temporarily busy
                            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && busyRetryCount < 6) {
                                busyRetryCount++
                                _state.value = _state.value.copy(listening = false, speechErrorMessage = null)
                                mainHandler.postDelayed({
                                    if (_state.value.liveSessionActive && !_state.value.muted && !_state.value.speaking) {
                                        startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
                                    }
                                }, 460L)
                                return
                            }

                            // Keep hands-free live session alive during silence/pauses/transient audio-focus glitches without requiring user to re-tap
                            val isRecoverableLiveError = isTimeoutOrNoMatch ||
                                error == SpeechRecognizer.ERROR_CLIENT ||
                                error == SpeechRecognizer.ERROR_AUDIO ||
                                error == SpeechRecognizer.ERROR_SERVER ||
                                error == SpeechRecognizer.ERROR_NETWORK ||
                                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                            if (_state.value.liveSessionActive &&
                                _state.value.autoVoiceEnabled &&
                                !_state.value.muted &&
                                isRecoverableLiveError
                            ) {
                                consecutiveLiveSilenceCount++
                                _state.value = _state.value.copy(listening = false, speechErrorMessage = null)
                                val delayMs = when {
                                    error == SpeechRecognizer.ERROR_AUDIO -> 520L
                                    consecutiveLiveSilenceCount < 10 -> 320L
                                    else -> 700L
                                }
                                mainHandler.postDelayed({
                                    if (_state.value.liveSessionActive &&
                                        _state.value.autoVoiceEnabled &&
                                        !_state.value.muted &&
                                        !_state.value.speaking &&
                                        !_state.value.listening
                                    ) {
                                        startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
                                    }
                                }, delayMs)
                                return
                            }

                            if (_state.value.muted) {
                                _state.value = _state.value.copy(listening = false, speechErrorMessage = null)
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
                            busyRetryCount = 0
                            retriedOfflineAfterNetError = false
                            lastPartialTranscript = ""
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim().orEmpty()
                            _state.value = _state.value.copy(
                                listening = false,
                                partialTranscript = spoken
                            )
                            if (spoken.isNotEmpty()) {
                                onFinalTranscript(spoken)
                            } else if (_state.value.liveSessionActive && _state.value.autoVoiceEnabled && !_state.value.muted) {
                                mainHandler.postDelayed({
                                    if (_state.value.liveSessionActive && !_state.value.muted && !_state.value.speaking) {
                                        startListeningInternal(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
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
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1150L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 350L)
                    if (useOffline) {
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

    /**
     * Monitors microphone input via AcousticEchoCanceler while Sathi is speaking.
     * As soon as sustained user speech is detected above the calibrated echo/noise baseline,
     * TTS pauses immediately and SpeechRecognizer starts listening automatically.
     */
    private fun startBargeInMonitor() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (!bargeInMonitorRunning.compareAndSet(false, true)) return

        val thread = Thread {
            var recorder: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            try {
                val sampleRate = 16000
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@Thread

                val frameSize = 1280 // 80ms @ 16kHz mono
                val bufferSize = maxOf(minBuf * 2, frameSize * 4)
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@Thread

                if (AcousticEchoCanceler.isAvailable()) {
                    aec = runCatching {
                        AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                    }.getOrNull()
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = runCatching {
                        NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
                    }.getOrNull()
                }

                recorder.startRecording()
                val pcm = ShortArray(frameSize)
                val startMs = System.currentTimeMillis()
                var baselineRms = 1200.0
                val calibrationSamples = mutableListOf<Double>()
                val recentWindows = ArrayDeque<Double>()

                while (bargeInMonitorRunning.get() &&
                    _state.value.speaking &&
                    _state.value.liveSessionActive &&
                    _state.value.autoVoiceEnabled &&
                    !_state.value.muted
                ) {
                    val read = recorder.read(pcm, 0, pcm.size)
                    if (read <= 0) {
                        Thread.sleep(40L)
                        continue
                    }

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = pcm[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read)
                    val elapsed = System.currentTimeMillis() - startMs

                    // Initial 550ms TTS ramp-up calibration window
                    if (elapsed < 550L) {
                        calibrationSamples.add(rms)
                        if (calibrationSamples.isNotEmpty()) {
                            baselineRms = (calibrationSamples.average() * 1.15).coerceIn(700.0, 5200.0)
                        }
                        continue
                    }

                    if (recentWindows.size >= 3) {
                        recentWindows.removeFirst()
                    }
                    recentWindows.addLast(rms)

                    if (shouldTriggerVoiceBargeIn(recentWindows.toList(), baselineRms)) {
                        bargeInMonitorRunning.set(false)
                        mainHandler.post {
                            if (_state.value.liveSessionActive && _state.value.autoVoiceEnabled && !_state.value.muted) {
                                interruptAndListen(activeLanguage, preferOfflineRecognition || !isNetworkConnected())
                            }
                        }
                        break
                    } else if (rms < baselineRms * 1.35) {
                        // Slowly track gentle changes in background echo level
                        baselineRms = (baselineRms * 0.90 + rms * 0.10).coerceIn(700.0, 5200.0)
                    }
                }
            } catch (_: Throwable) {
                // Non-fatal on emulators/environments without physical mic HAL
            } finally {
                bargeInMonitorRunning.set(false)
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }
        }.apply {
            name = "SathiBargeInMonitor"
            isDaemon = true
        }
        bargeInThread = thread
        thread.start()
    }

    private fun stopBargeInMonitor() {
        bargeInMonitorRunning.set(false)
        bargeInThread = null
    }

    fun stopListening() {
        mainHandler.post {
            val wasListening = _state.value.listening
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _state.value = _state.value.copy(listening = false)
            if (wasListening) {
                playListeningCueSound(isStart = false)
            }
        }
    }

    fun interruptAndListen(language: SathiLanguage, preferOffline: Boolean = false) {
        stopBargeInMonitor()
        stopSpeaking()
        consecutiveLiveSilenceCount = 0
        busyRetryCount = 0
        _state.value = _state.value.copy(
            liveSessionActive = _state.value.autoVoiceEnabled,
            muted = false,
            speechErrorMessage = null
        )
        startListening(language, preferOffline)
    }

    fun speak(
        text: String,
        language: SathiLanguage,
        voiceProfile: SathiVoiceProfile,
        mood: SathiMood,
        autoResumeLiveAfter: Boolean = _state.value.liveSessionActive && _state.value.autoVoiceEnabled
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val engine = tts
        if (engine == null || !_state.value.ttsReady) {
            if (autoResumeLiveAfter && _state.value.liveSessionActive && _state.value.autoVoiceEnabled && !_state.value.muted) {
                mainHandler.postDelayed({
                    if (_state.value.liveSessionActive && !_state.value.muted && !_state.value.listening && !_state.value.speaking) {
                        startListeningInternal(language, preferOfflineRecognition || !isNetworkConnected())
                    }
                }, 350L)
            }
            return
        }

        mainHandler.post {
            try {
                liveAutoListenAfterTts = autoResumeLiveAfter && _state.value.liveSessionActive && _state.value.autoVoiceEnabled

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

                val offlineOnly = preferOfflineRecognition || !isNetworkConnected()
                val matchedDistinctVoice = selectSyntheticVoice(engine, targetLocale, voiceProfile, offlineOnly)

                val basePitch = if (voiceProfile == SathiVoiceProfile.FEMALE) {
                    if (matchedDistinctVoice) 1.24f else 1.34f
                } else {
                    if (matchedDistinctVoice) 0.82f else 0.76f
                }
                val moodPitchDelta = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 0.04f
                    SathiMood.COMFORTING -> -0.03f
                    SathiMood.SERIOUS -> -0.01f
                    SathiMood.WARM -> 0.01f
                }
                val moodRate = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 1.01f
                    SathiMood.COMFORTING -> 0.92f
                    SathiMood.SERIOUS -> 0.96f
                    SathiMood.WARM -> 0.98f
                }

                engine.setPitch((basePitch + moodPitchDelta).coerceIn(0.68f, 1.48f))
                engine.setSpeechRate(moodRate)

                val utteranceId = UUID.randomUUID().toString()
                _state.value = _state.value.copy(speaking = true)
                val res = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    _state.value = _state.value.copy(speaking = false)
                    scheduleAutoResumeAfterSpeech()
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
                _state.value = _state.value.copy(speaking = false)
                scheduleAutoResumeAfterSpeech()
            }
        }
    }

    fun stopSpeaking() {
        liveAutoListenAfterTts = false
        stopBargeInMonitor()
        mainHandler.post { stopSpeakingInternal() }
    }

    private fun stopSpeakingInternal() {
        stopBargeInMonitor()
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

    fun openOfflineSpeechRecognitionSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent("com.google.android.googlequicksearchbox.action.PRIVACY_SETTINGS"),
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        liveAutoListenAfterTts = false
        stopBargeInMonitor()
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

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun selectSyntheticVoice(
        engine: TextToSpeech,
        locale: Locale,
        profile: SathiVoiceProfile,
        preferOffline: Boolean
    ): Boolean {
        return try {
            val voices = engine.voices ?: return false
            val allLocaleVoices = voices
                .filter { v -> v.locale.language.equals(locale.language, ignoreCase = true) }
                .sortedBy { it.name }
            if (allLocaleVoices.isEmpty()) return false

            val offlineVoices = allLocaleVoices.filter { !it.isNetworkConnectionRequired }
            val matchingLocale = if (preferOffline && offlineVoices.isNotEmpty()) offlineVoices else allLocaleVoices

            val femaleTokens = listOf(
                "female", "woman", "ban", "bnf", "bnd", "bin", "sfg", "kalpana", "tanisha",
                "kanta", "sabina", "f00", "-a-", "-c-", "-e-", "-g-", "x-sfg",
                "wavenet-a", "wavenet-c", "standard-a", "standard-c"
            )
            val maleTokens = listOf(
                "male", "man", "bfu", "bnm", "rishi", "bashkar", "pradeep", "tanmay",
                "m00", "-b-", "-d-", "-f-", "wavenet-b", "wavenet-d", "standard-b", "standard-d"
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
                explicitFemale
                    ?: matchingLocale.firstOrNull { it != explicitMale && !it.isNetworkConnectionRequired }
                    ?: matchingLocale.firstOrNull { it != explicitMale }
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
                if (language == SathiLanguage.BN) "কথা স্পষ্ট শোনা যায়নি, আবার বলুন বা গোল মাইক্রোফোন চাপুন।"
                else "I didn't catch that clearly. Please speak again or tap the mic."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (language == SathiLanguage.BN) "অফলাইনে কথা চিনতে সেটিংস থেকে বাংলা অফলাইন ভয়েস ইনস্টল করুন, অথবা নিচে লিখে বলুন।"
                else "Offline speech data is not installed yet on this device. Install it in Settings or type below."
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

        /**
         * Returns true only when 3 consecutive 80ms windows (~240ms) sustain RMS speech energy
         * clearly above the calibrated echo/ambient baseline, rejecting brief clicks or TTS leakage.
         */
        fun shouldTriggerVoiceBargeIn(recentWindowRms: List<Double>, baselineRms: Double): Boolean {
            if (recentWindowRms.size < 3) return false
            val threshold = maxOf(2150.0, baselineRms * 2.35)
            return recentWindowRms.takeLast(3).all { it >= threshold }
        }
    }
}
