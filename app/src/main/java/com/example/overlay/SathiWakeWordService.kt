package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground microphone service listening for the wake phrase "সাথী সাথী সাথী" / "Sathi Sathi Sathi"
 * both offline (via EXTRA_PREFER_OFFLINE) and online.
 */
class SathiWakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var running = false
    private var lastTriggerMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sathi Wake Word",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, SathiWakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("সাথী ওয়েক-ওয়ার্ড চালু আছে")
            .setContentText("অফলাইন বা অনলাইনে \"সাথী সাথী সাথী\" বললেই সাথী জেগে উঠবে")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "বন্ধ করুন", stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun listenForWakePhrase() {
        if (!running || pausedForActiveVoice) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer unavailable for wake word")
            stopSelf()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (matchesTripleSathi(text)) {
                            onWakeDetected()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        if (matches.any { matchesTripleSathi(it) }) {
                            onWakeDetected()
                        } else {
                            scheduleNextCycle(450L)
                        }
                    }

                    override fun onError(error: Int) {
                        val delayMs = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                stopSelf()
                                return
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1100L
                            else -> 650L
                        }
                        scheduleNextCycle(delayMs)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Wake listener cycle error", e)
            scheduleNextCycle(1500L)
        }
    }

    private fun scheduleNextCycle(delayMs: Long) {
        if (!running || pausedForActiveVoice) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ listenForWakePhrase() }, delayMs)
    }

    private fun pauseRecognizerImmediately() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_FOR_VOICE) {
            pauseRecognizerImmediately()
            return START_STICKY
        }
        if (intent?.action == ACTION_RESUME_FROM_VOICE) {
            if (running && !pausedForActiveVoice) {
                scheduleNextCycle(450L)
            }
            return START_STICKY
        }

        if (!running) {
            running = true
            _isRunning.value = true
            instance = this
            startForegroundSafely()
            if (!pausedForActiveVoice) {
                mainHandler.postDelayed({ listenForWakePhrase() }, 350L)
            }
        }
        return START_STICKY
    }

    private fun onWakeDetected() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 3500L) return
        lastTriggerMs = now

        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        } catch (_: Exception) {
        }

        _wakeTriggerCount.value = _wakeTriggerCount.value + 1

        try {
            val wakeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_WAKE_TRIGGERED, true)
            }
            startActivity(wakeIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not foreground activity directly on wake", e)
        }

        // Pause wake recognizer briefly so MainActivity VoiceController can take the mic cleanly
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        scheduleNextCycle(6500L)
    }

    override fun onDestroy() {
        running = false
        _isRunning.value = false
        if (instance == this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SathiWakeWordService"
        private const val CHANNEL_ID = "sathi_wake_word_channel"
        private const val NOTIFICATION_ID = 7863
        const val ACTION_STOP = "com.example.overlay.WAKE_STOP"
        const val ACTION_PAUSE_FOR_VOICE = "com.example.overlay.WAKE_PAUSE_FOR_VOICE"
        const val ACTION_RESUME_FROM_VOICE = "com.example.overlay.WAKE_RESUME_FROM_VOICE"
        const val EXTRA_WAKE_TRIGGERED = "extra_wake_triggered"

        @Volatile
        private var instance: SathiWakeWordService? = null

        @Volatile
        private var pausedForActiveVoice: Boolean = false

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _wakeTriggerCount = MutableStateFlow(0)
        val wakeTriggerCount: StateFlow<Int> = _wakeTriggerCount.asStateFlow()

        @Volatile
        private var wasActiveBeforeGame: Boolean = false

        fun setPausedForActiveVoice(context: Context, paused: Boolean) {
            if (pausedForActiveVoice == paused) return
            pausedForActiveVoice = paused
            if (!_isRunning.value) return
            val svc = instance
            if (svc != null) {
                svc.mainHandler.post {
                    if (paused) {
                        svc.pauseRecognizerImmediately()
                    } else if (svc.running && !pausedForActiveVoice) {
                        svc.scheduleNextCycle(500L)
                    }
                }
            } else {
                val action = if (paused) ACTION_PAUSE_FOR_VOICE else ACTION_RESUME_FROM_VOICE
                runCatching {
                    context.startService(Intent(context, SathiWakeWordService::class.java).setAction(action))
                }
            }
        }

        fun handleGameModeChanged(context: Context, pausedForGame: Boolean) {
            if (pausedForGame) {
                if (_isRunning.value) {
                    wasActiveBeforeGame = true
                    runCatching { stopService(context) }
                }
            } else if (wasActiveBeforeGame) {
                wasActiveBeforeGame = false
                runCatching { startService(context) }
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, SathiWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.startService(
                Intent(context, SathiWakeWordService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun matchesTripleSathi(transcript: String): Boolean {
            val lower = transcript.lowercase().trim()
            if (lower.isBlank()) return false
            val wakeVariants = listOf(
                "সাথী", "সাথি", "সাতি", "সাতী", "সathi",
                "sathi", "shathi", "saathi", "shati", "sathy"
            )
            val tokens = lower.split(Regex("[\\s,।!?]+")).filter { it.isNotBlank() }
            val count = tokens.count { token -> wakeVariants.any { token.contains(it) } }
            return count >= 3 ||
                lower.contains("সাথী সাথী সাথী") ||
                lower.contains("সাথি সাথি সাথি") ||
                lower.contains("sathi sathi sathi")
        }
    }
}
