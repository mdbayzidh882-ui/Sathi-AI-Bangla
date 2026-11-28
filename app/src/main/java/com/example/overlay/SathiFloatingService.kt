package com.example.overlay

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.comm.IncomingCommunicationManager
import com.example.core.SathiOrbStyle
import com.example.core.SathiPreferences
import com.example.data.ActionOutcome
import com.example.data.DeviceActionExecutor
import com.example.data.SathiAiService
import com.example.data.VoiceController
import com.example.data.WebSearchService
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import com.example.ui.drawSathiOrbOnAndroidCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SathiFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: FloatingVoiceOrbView? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiService = SathiAiService()
    private val webSearchService = WebSearchService()
    private lateinit var deviceActions: DeviceActionExecutor
    private lateinit var preferences: SathiPreferences
    private var voiceController: VoiceController? = null

    private var cachedApiKey: String = ""
    private var cachedSystemPrompt: String = SathiPreferences.DEFAULT_BOSS_SYSTEM_PROMPT
    private var cachedAutoExecute: Boolean = true
    private var cachedLanguage: SathiLanguage = SathiLanguage.BN
    private var cachedVoice: SathiVoiceProfile = SathiVoiceProfile.FEMALE

    private var chatHistoryContainer: LinearLayout? = null
    private var chatScrollView: ScrollView? = null
    private var statusTextView: TextView? = null
    private var headerOrbView: FloatingVoiceOrbView? = null
    private var panelWaveView: FloatingVoiceWaveBarView? = null
    private var isPausedForGame: Boolean = false
    private var wasLiveBeforeGame: Boolean = false
    private var cachedAutoVoice: Boolean = true
    private var cachedOrbStyle: SathiOrbStyle = SathiOrbStyle.ORBIT_BLUE
    private var cachedReduceMotion: Boolean = false
    private var autoLaunchedByAppExit: Boolean = false
    private var dockedInsideApp: Boolean = false
    private var lastSpokenBriefingId: String? = null
    private val recentFloatingHistory = mutableListOf<Pair<Boolean, String>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        preferences = SathiPreferences(this)
        deviceActions = DeviceActionExecutor(this)
        voiceController = VoiceController(this) { spoken ->
            handleFloatingQuery(spoken)
        }

        serviceScope.launch {
            launch {
                preferences.settingsFlow.collect { s ->
                    cachedApiKey = s.customGeminiApiKey
                    cachedSystemPrompt = s.customSystemPrompt
                    cachedAutoExecute = s.autoExecuteActions
                    cachedAutoVoice = s.autoVoiceEnabled
                    voiceController?.setAutoVoiceEnabled(s.autoVoiceEnabled)
                    voiceController?.setStartEndSoundsEnabled(s.startEndSoundsEnabled)
                    cachedLanguage = SathiLanguage.fromCode(s.preferredLanguageFallback)
                    cachedVoice = SathiVoiceProfile.fromCode(s.preferredVoiceFallback)
                    cachedOrbStyle = s.orbStyle
                    cachedReduceMotion = s.reduceMotion || s.batterySaver
                    bubbleView?.setOrbAppearance(
                        style = cachedOrbStyle,
                        reduceMotion = cachedReduceMotion
                    )
                    headerOrbView?.setOrbAppearance(
                        style = cachedOrbStyle,
                        reduceMotion = cachedReduceMotion
                    )
                }
            }
            launch {
                voiceController?.state?.collect { vs ->
                    bubbleView?.setVoiceState(
                        listening = vs.listening,
                        speaking = vs.speaking
                    )
                    headerOrbView?.setVoiceState(
                        listening = vs.listening,
                        speaking = vs.speaking
                    )
                    panelWaveView?.setVoiceState(
                        listening = vs.listening,
                        speaking = vs.speaking
                    )
                    if (vs.micConflictDetected) {
                        statusTextView?.text = "🎙️ অন্য অ্যাপ মাইক্রোফোন ব্যবহার করছে — অপেক্ষা করছি..."
                    } else if (vs.listening) {
                        statusTextView?.text = "শুনছি বস... বলুন"
                    } else if (vs.speaking) {
                        statusTextView?.text = "সাথী বলছে..."
                    } else {
                        val mm = SathiScreenReaderService.multimediaState.value
                        if (mm.isMonitoring) {
                            statusTextView?.text = "👁️ ${mm.appLabel} মনিটর চালু — প্রশ্ন করুন"
                        }
                    }
                }
            }
            launch {
                SathiScreenReaderService.isGameModePaused.collect { paused ->
                    if (paused != isPausedForGame) {
                        applyGamePauseState(paused)
                    }
                }
            }
            launch {
                SathiScreenReaderService.multimediaState.collect { mm ->
                    if (!isPausedForGame && mm.isMonitoring) {
                        val vs = voiceController?.state?.value
                        if (vs?.listening != true && vs?.speaking != true) {
                            statusTextView?.text = "👁️ ${mm.appLabel} মনিটর চালু — সাহায্য করতে প্রস্তুত"
                        }
                    }
                }
            }
            launch {
                IncomingCommunicationManager.latestVoiceBriefing.collect { record ->
                    if (record != null && record.id != lastSpokenBriefingId && !isPausedForGame) {
                        lastSpokenBriefingId = record.id
                        val briefing = IncomingCommunicationManager.formatBossVoiceBriefing(record)
                        appendFloatingChatLine(isUser = false, briefing)
                        statusTextView?.text = "📩 ${record.appName}: মূল বিষয়"
                        voiceController?.speak(briefing, cachedLanguage, cachedVoice, SathiMood.WARM)
                    }
                }
            }
        }

        setupFloatingBubble()
        _isRunning.value = true
    }

    private fun applyGamePauseState(pause: Boolean) {
        if (pause) {
            isPausedForGame = true
            val vs = voiceController?.state?.value
            wasLiveBeforeGame = vs?.liveSessionActive == true
            voiceController?.stopLiveSession()
            bubbleView?.visibility = View.GONE
            panelView?.visibility = View.GONE
        } else {
            isPausedForGame = false
            if (!dockedInsideApp) {
                bubbleView?.visibility = View.VISIBLE
                panelView?.visibility = View.VISIBLE
            }
            if (wasLiveBeforeGame && cachedAutoVoice) {
                wasLiveBeforeGame = false
                voiceController?.startLiveSession(cachedLanguage)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubbleView == null) {
            setupFloatingBubble()
        }
        _isRunning.value = true
        when (intent?.action) {
            ACTION_GAME_PAUSE -> {
                applyGamePauseState(true)
            }
            ACTION_GAME_RESUME -> {
                applyGamePauseState(false)
            }
            ACTION_SHOW_VIRTUAL_WINDOW -> {
                autoLaunchedByAppExit = true
                dockedInsideApp = false
                if (!isPausedForGame) {
                    bubbleView?.visibility = View.VISIBLE
                    if (panelView == null) {
                        showMiniPanel()
                    } else {
                        panelView?.visibility = View.VISIBLE
                    }
                }
            }
            ACTION_DOCK_ON_APP_OPEN -> {
                voiceController?.stopLiveSession()
                if (autoLaunchedByAppExit) {
                    dockedInsideApp = true
                    hideMiniPanel()
                    bubbleView?.visibility = View.GONE
                }
            }
            else -> {
                autoLaunchedByAppExit = false
                dockedInsideApp = false
                if (!isPausedForGame) {
                    bubbleView?.visibility = View.VISIBLE
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sathi Floating Companion",
                    NotificationManager.IMPORTANCE_LOW
                )
                nm.createNotificationChannel(channel)
            }

            val openAppIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sathi AI ভাসমান অর্‌ব চালু আছে")
                .setContentText("বস, যেকোনো স্ক্রিনে ভাসমান বাবলে ট্যাপ করে কথা বলুন")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openAppIntent)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasMic = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val fgsType = if (hasMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } catch (_: SecurityException) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground safely", e)
        }
    }

    private fun overlayLayoutType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun setupFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val orbSize = dp(76)
        val orb = FloatingVoiceOrbView(this)

        val params = WindowManager.LayoutParams(
            orbSize,
            orbSize,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(220)
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = (initialX + dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(dp(40))
                    try {
                        windowManager?.updateViewLayout(orb, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleMiniPanel()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleParams = params
        bubbleView = orb
        try {
            windowManager?.addView(orb, params)
            if (isPausedForGame) {
                orb.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach floating bubble", e)
            stopSelf()
        }
    }

    private fun toggleMiniPanel() {
        if (panelView != null) {
            hideMiniPanel()
        } else {
            showMiniPanel()
        }
    }

    private fun showMiniPanel() {
        val wm = windowManager ?: return
        if (panelView != null) {
            panelView?.visibility = View.VISIBLE
            return
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F004142B"), Color.parseColor("#F00A2246"))
            ).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.parseColor("#7738BDF8"))
            }
            elevation = dp(18).toFloat()
        }

        val maxPanelWidth = minOf(dp(336), (resources.displayMetrics.widthPixels * 0.92f).toInt())
        val pParams = WindowManager.LayoutParams(
            maxPanelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(48)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val miniOrb = FloatingVoiceOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(8)
            }
            setOrbAppearance(cachedOrbStyle, cachedReduceMotion)
            val vs = voiceController?.state?.value
            setVoiceState(vs?.listening == true, vs?.speaking == true)
        }
        headerOrbView = miniOrb

        val titleTv = TextView(this).apply {
            text = "✦ Sathi AI • ভার্চুয়াল উইন্ডো"
            setTextColor(Color.parseColor("#7DD3FC"))
            textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minBtn = Button(this).apply {
            text = "▁"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { hideMiniPanel() }
        }
        headerRow.addView(miniOrb)
        headerRow.addView(titleTv)
        headerRow.addView(minBtn)

        var dragStartX = 0
        var dragStartY = 0
        var dragTouchX = 0f
        var dragTouchY = 0f
        headerRow.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = pParams.x
                    dragStartY = pParams.y
                    dragTouchX = ev.rawX
                    dragTouchY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    pParams.x = dragStartX + (ev.rawX - dragTouchX).toInt()
                    pParams.y = (dragStartY - (ev.rawY - dragTouchY).toInt()).coerceAtLeast(dp(12))
                    runCatching { wm.updateViewLayout(card, pParams) }
                    true
                }
                else -> false
            }
        }
        card.addView(headerRow)

        val statusTv = TextView(this).apply {
            val mm = SathiScreenReaderService.multimediaState.value
            text = if (mm.isMonitoring) {
                "👁️ ${mm.appLabel} মনিটর চালু — যেকোনো কিছু জিজ্ঞাসা করুন"
            } else {
                "ভাসমান ভার্চুয়াল উইন্ডো চালু • টেনে সরানো যাবে"
            }
            setTextColor(Color.parseColor("#A5C7F2"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(4))
        }
        statusTextView = statusTv
        card.addView(statusTv)

        val waveBar = FloatingVoiceWaveBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
            ).apply {
                bottomMargin = dp(6)
            }
            val vs = voiceController?.state?.value
            setVoiceState(vs?.listening == true, vs?.speaking == true)
        }
        panelWaveView = waveBar
        card.addView(waveBar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(164))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#99040D1C"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#4438BDF8"))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val historyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(historyLayout)
        chatScrollView = scroll
        chatHistoryContainer = historyLayout
        card.addView(scroll)
        appendFloatingChatLine(
            isUser = false,
            "অনেকক্ষণ পর তোমার মিষ্টি আওয়াজ শুনলাম বস! আমি সাথী ভার্চুয়াল উইন্ডোতে প্রস্তুত—বলো কী সাহায্য করব?"
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        val inputEt = EditText(this).apply {
            hint = "বস, এখানে লিখুন..."
            setHintTextColor(Color.parseColor("#7C91B6"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B311192E"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#5538D9FF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendBtn = Button(this).apply {
            text = "পাঠান"
            setTextColor(Color.parseColor("#061326"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5FE1FF"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                val query = inputEt.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    inputEt.setText("")
                    handleFloatingQuery(query)
                }
            }
        }
        inputRow.addView(inputEt)
        inputRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        inputRow.addView(sendBtn)
        card.addView(inputRow)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val micBtn = Button(this).apply {
            text = "🎙️ লাইভ"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D83B8A"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                statusTv.text = "লাইভ শুনছি বস... বলুন"
                voiceController?.startLiveSession(cachedLanguage)
            }
        }
        val lookScreenBtn = Button(this).apply {
            text = "👁️ স্ক্রিন"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1D4E89"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                inspectCurrentSharedScreen("")
            }
        }
        val openAppBtn = Button(this).apply {
            text = "অ্যাপ"
            setTextColor(Color.parseColor("#DCEBFF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1D2A4A"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener {
                hideMiniPanel()
                startActivity(
                    Intent(this@SathiFloatingService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FFB4C4"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#421E36"))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { stopSelf() }
        }

        actionsRow.addView(micBtn)
        actionsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(5), 1) })
        actionsRow.addView(lookScreenBtn)
        actionsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(5), 1) })
        actionsRow.addView(openAppBtn)
        actionsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(5), 1) })
        actionsRow.addView(closeBtn)
        card.addView(actionsRow)

        panelParams = pParams
        panelView = card
        try {
            wm.addView(card, pParams)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach floating panel", e)
            panelView = null
        }
    }

    private fun appendFloatingChatLine(isUser: Boolean, text: String) {
        val container = chatHistoryContainer ?: return
        val bubble = TextView(this).apply {
            this.text = if (isUser) "বস: $text" else "Sathi: $text"
            setTextColor(if (isUser) Color.parseColor("#061326") else Color.WHITE)
            textSize = 13f
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                setColor(if (isUser) Color.parseColor("#67E8F9") else Color.parseColor("#CC1B2845"))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        container.addView(bubble)
        chatScrollView?.post { chatScrollView?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun inspectCurrentSharedScreen(question: String) {
        val accessibilityContext = SathiScreenReaderService.formatScreenContextSummary()
        if (!SathiScreenShareService.isSharing.value && accessibilityContext.isBlank()) {
            val guide = "বস, প্রথমে Sathi AI অ্যাপের '+' বাটন থেকে 'ফোন স্ক্রিন শেয়ার করুন' বা 'স্ক্রিন ডাটা রিডার' চালু করুন, তারপর এখানে '👁️ স্ক্রিন' চাপুন।"
            appendFloatingChatLine(isUser = false, guide)
            statusTextView?.text = "স্ক্রিন শেয়ার চালু নেই"
            voiceController?.speak(guide, cachedLanguage, cachedVoice, SathiMood.WARM)
            return
        }
        statusTextView?.text = "বস, স্ক্রিন ও সব ডেটা পড়ছি..."
        serviceScope.launch {
            try {
                val frame = SathiScreenShareService.captureLatestFrame()
                val fullQuestion = buildString {
                    if (question.isNotBlank()) append(question)
                    if (accessibilityContext.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(accessibilityContext)
                    }
                }
                val analysis = if (frame != null) {
                    aiService.analyzeImage(
                        bitmap = frame,
                        question = fullQuestion,
                        language = cachedLanguage,
                        isScreenCapture = true,
                        apiKey = cachedApiKey,
                        screenTextContext = accessibilityContext
                    ).getOrElse { err ->
                        accessibilityContext.ifBlank {
                            err.localizedMessage ?: "বস, স্ক্রিন বিশ্লেষণ করা যায়নি।"
                        }
                    }
                } else {
                    accessibilityContext.ifBlank { "স্ক্রিন ফ্রেম এখনো তৈরি হয়নি বস।" }
                }
                appendFloatingChatLine(isUser = false, analysis)
                statusTextView?.text = "স্ক্রিন বিশ্লেষণ সম্পন্ন"
                voiceController?.speak(analysis, cachedLanguage, cachedVoice, SathiMood.WARM)
            } catch (e: Exception) {
                Log.e(TAG, "Screen inspection failed", e)
                val errMsg = "বস, স্ক্রিন পড়া যায়নি: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"
                appendFloatingChatLine(isUser = false, errMsg)
                statusTextView?.text = "স্ক্রিন পড়ায় সমস্যা হয়েছে"
            }
        }
    }

    private fun handleFloatingQuery(query: String) {
        appendFloatingChatLine(isUser = true, query)
        val lower = query.lowercase()
        val mmState = SathiScreenReaderService.multimediaState.value
        val screenContext = SathiScreenReaderService.formatScreenContextSummary()
        val explicitScreenQuery = lower.contains("স্ক্রিন") || lower.contains("screen") ||
            lower.contains("এটা কী") || lower.contains("এখানে কী")
        if ((SathiScreenShareService.isSharing.value || SathiScreenReaderService.isConnected.value) &&
            explicitScreenQuery
        ) {
            inspectCurrentSharedScreen(query)
            return
        }

        statusTextView?.text = "বস, ভাবছি..."
        serviceScope.launch {
            try {
                val enrichedMessage = if (mmState.isMonitoring && screenContext.isNotBlank()) {
                    "$query\n\n[${mmState.appLabel} স্ক্রিন তথ্য:\n${screenContext.take(700)}]"
                } else {
                    query
                }
                val historySnapshot = recentFloatingHistory.toList()
                val result = aiService.generateCompanionReply(
                    userMessage = enrichedMessage,
                    language = cachedLanguage,
                    userName = "বস",
                    memories = emptyList(),
                    recentHistory = historySnapshot,
                    webSources = emptyList(),
                    allowLongTermMemory = false,
                    apiKey = cachedApiKey,
                    customSystemPrompt = cachedSystemPrompt
                )
                val turn = result.getOrElse { err ->
                    val errText = err.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "দুঃখিত বস, উত্তর তৈরি করা যায়নি। ইন্টারনেট বা Google API Key পরীক্ষা করুন।"
                    appendFloatingChatLine(isUser = false, errText)
                    statusTextView?.text = "উত্তর আনা যায়নি"
                    voiceController?.speak(errText, cachedLanguage, cachedVoice, SathiMood.COMFORTING)
                    return@launch
                }
                val finalTurn = if (turn.actionType == PhoneActionType.WEB_SEARCH) {
                    val sources = webSearchService.searchWeb(turn.actionParam.ifBlank { query }, cachedLanguage)
                    if (sources.isNotEmpty()) {
                        aiService.generateCompanionReply(
                            userMessage = enrichedMessage,
                            language = cachedLanguage,
                            userName = "বস",
                            memories = emptyList(),
                            recentHistory = historySnapshot,
                            webSources = sources,
                            allowLongTermMemory = false,
                            apiKey = cachedApiKey,
                            customSystemPrompt = cachedSystemPrompt
                        ).getOrElse { turn }
                    } else {
                        turn.copy(
                            replyText = "দুঃখিত বস, এই বিষয়ে ইন্টারনেটে সঠিক তথ্য পাওয়া যায়নি।",
                            actionType = PhoneActionType.NONE
                        )
                    }
                } else turn

                var finalReply = finalTurn.replyText
                if (finalTurn.actionType == PhoneActionType.SOCIAL_MESSAGE_CONFIRM) {
                    val wantsSendNow = (cachedAutoExecute && finalTurn.secondaryParam.isNotBlank()) ||
                        SathiAiService.wantsImmediateSend(query)
                    val outcome = deviceActions.executeConfirmedSocialMessage(
                        platform = finalTurn.actionParam,
                        messageBody = finalTurn.secondaryParam,
                        language = cachedLanguage,
                        recipientName = finalTurn.recipientParam,
                        sendAfterType = wantsSendNow
                    )
                    if (outcome is ActionOutcome.Executed && outcome.label.isNotBlank()) {
                        finalReply = "জি বস, ${outcome.label}"
                    }
                } else if (finalTurn.actionType != PhoneActionType.NONE &&
                    finalTurn.actionType != PhoneActionType.WEB_SEARCH &&
                    finalTurn.actionType != PhoneActionType.CALL_CONFIRM &&
                    finalTurn.actionType != PhoneActionType.SMS_CONFIRM
                ) {
                    val outcome = deviceActions.executeAction(
                        finalTurn.actionType,
                        finalTurn.actionParam,
                        finalTurn.secondaryParam,
                        cachedLanguage,
                        finalTurn.recipientParam
                    )
                    if (outcome is ActionOutcome.Failed) {
                        finalReply = outcome.message
                    }
                } else if (cachedAutoExecute && finalTurn.actionType == PhoneActionType.SMS_CONFIRM &&
                    (finalTurn.actionParam.isNotBlank() || finalTurn.recipientParam.isNotBlank()) &&
                    finalTurn.secondaryParam.isNotBlank()
                ) {
                    deviceActions.executeConfirmedSms(
                        target = finalTurn.actionParam,
                        body = finalTurn.secondaryParam,
                        language = cachedLanguage,
                        recipientName = finalTurn.recipientParam
                    )
                }

                recentFloatingHistory.add(true to query)
                recentFloatingHistory.add(false to finalReply)
                while (recentFloatingHistory.size > 10) {
                    recentFloatingHistory.removeAt(0)
                }

                appendFloatingChatLine(isUser = false, finalReply)
                statusTextView?.text = "উত্তর প্রস্তুত বস"
                voiceController?.speak(
                    finalReply,
                    cachedLanguage,
                    cachedVoice,
                    finalTurn.mood
                )
            } catch (e: Exception) {
                Log.e(TAG, "Floating query failed", e)
                val errText = "দুঃখিত বস, উত্তর আনতে সমস্যা হয়েছে (${e.localizedMessage ?: "আবার বলুন"})।"
                appendFloatingChatLine(isUser = false, errText)
                statusTextView?.text = "সমস্যা হয়েছে — আবার চেষ্টা করুন"
                voiceController?.speak(errText, cachedLanguage, cachedVoice, SathiMood.COMFORTING)
            }
        }
    }

    private fun hideMiniPanel() {
        headerOrbView?.stopAnim()
        headerOrbView = null
        panelWaveView?.stopAnim()
        panelWaveView = null
        panelView?.let { v ->
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
        }
        panelView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        hideMiniPanel()
        bubbleView?.let { v ->
            v.stopAnim()
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        voiceController?.release()
        serviceScope.cancel()
    }

    /**
     * Dark translucent virtual floating orb with continuously pulsing outer wave rings
     * and glowing voice wave arc inside/around the orb.
     */
    private class FloatingVoiceOrbView(context: Context) : View(context) {
        private var phase: Float = 0f
        private var isListening: Boolean = false
        private var isSpeaking: Boolean = false
        private var orbStyle: SathiOrbStyle = SathiOrbStyle.ORBIT_BLUE
        private var reduceMotion: Boolean = false

        private val animator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            animator.start()
        }

        fun setOrbAppearance(style: SathiOrbStyle, reduceMotion: Boolean) {
            this.orbStyle = style
            this.reduceMotion = reduceMotion
            animator.duration = if (reduceMotion) 5200L else 2200L
            invalidate()
        }

        fun setVoiceState(listening: Boolean, speaking: Boolean) {
            isListening = listening
            isSpeaking = speaking
            invalidate()
        }

        fun stopAnim() {
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val maxR = minOf(w, h) / 2f
            drawSathiOrbOnAndroidCanvas(
                canvas = canvas,
                cx = cx,
                cy = cy,
                maxR = maxR,
                phase = phase,
                style = orbStyle,
                isListening = isListening,
                isSpeaking = isSpeaking,
                reduceMotion = reduceMotion
            )
        }
    }

    /**
     * Full-width animated speech waveform strip shown inside the floating window during speech.
     */
    private class FloatingVoiceWaveBarView(context: Context) : View(context) {
        private var phase: Float = 0f
        private var isListening: Boolean = false
        private var isSpeaking: Boolean = false
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val animator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            animator.start()
        }

        fun setVoiceState(listening: Boolean, speaking: Boolean) {
            isListening = listening
            isSpeaking = speaking
            invalidate()
        }

        fun stopAnim() {
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val barCount = 18
            val spacing = w / (barCount + 1)
            val centerY = h / 2f
            val active = isSpeaking || isListening
            barPaint.color = when {
                isSpeaking -> Color.parseColor("#00E5FF")
                isListening -> Color.parseColor("#FF4081")
                else -> Color.parseColor("#5538D9FF")
            }
            for (i in 1..barCount) {
                val norm = sin((i.toFloat() / (barCount + 1)) * PI).toFloat()
                val wave = 0.5f + 0.5f * sin(phase * 2.4f + i * 0.65f)
                val halfH = h * (if (active) 0.14f + 0.32f * wave * norm else 0.08f + 0.10f * wave * norm)
                val cx = i * spacing
                val halfW = (spacing * 0.24f).coerceAtLeast(2f)
                canvas.drawRoundRect(
                    cx - halfW,
                    centerY - halfH,
                    cx + halfW,
                    centerY + halfH,
                    halfW,
                    halfW,
                    barPaint
                )
            }
        }
    }

    companion object {
        private const val TAG = "SathiFloatingService"
        private const val CHANNEL_ID = "sathi_overlay_channel"
        private const val NOTIFICATION_ID = 7861
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_GAME_PAUSE = "com.example.overlay.ACTION_GAME_PAUSE"
        const val ACTION_GAME_RESUME = "com.example.overlay.ACTION_GAME_RESUME"
        const val ACTION_SHOW_VIRTUAL_WINDOW = "com.example.overlay.ACTION_SHOW_VIRTUAL_WINDOW"
        const val ACTION_DOCK_ON_APP_OPEN = "com.example.overlay.ACTION_DOCK_ON_APP_OPEN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
