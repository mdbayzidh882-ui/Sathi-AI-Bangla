package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.DeviceActionExecutor
import com.example.data.SathiAiService
import com.example.data.VoiceController
import com.example.data.WebSearchService
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import kotlin.math.abs
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
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiService = SathiAiService()
    private val webSearchService = WebSearchService()
    private lateinit var deviceActions: DeviceActionExecutor
    private var voiceController: VoiceController? = null

    private var replyTextView: TextView? = null
    private var statusTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        deviceActions = DeviceActionExecutor(this)
        voiceController = VoiceController(this) { spoken ->
            handleFloatingQuery(spoken)
        }

        startForegroundSafely()
        setupFloatingBubble()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
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
            .setContentTitle("সাথী ভাসমান উইন্ডো চালু আছে")
            .setContentText("যেকোনো স্ক্রিনে সাথী বাবলে ট্যাপ করে কথা বলুন")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

        val orbSize = dp(58)
        val orb = TextView(this).apply {
            text = "✦ সাথী"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#9C7BFF"), Color.parseColor("#6D3BFF"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.parseColor("#E9DDFF"))
            }
            elevation = dp(10).toFloat()
        }

        val params = WindowManager.LayoutParams(
            orbSize,
            orbSize,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(240)
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#251942"), Color.parseColor("#18102B"))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#A78BFA"))
            }
            elevation = dp(14).toFloat()
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = "✦ সাথী লাইভ (Gemini Style)"
            setTextColor(Color.parseColor("#E9DDFF"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val minBtn = Button(this).apply {
            text = "—"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { hideMiniPanel() }
        }
        headerRow.addView(titleTv)
        headerRow.addView(minBtn)
        card.addView(headerRow)

        val statusTv = TextView(this).apply {
            text = "যেকোনো প্রশ্ন বলুন বা নিচে লিখুন..."
            setTextColor(Color.parseColor("#C4B5FD"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(6))
        }
        statusTextView = statusTv
        card.addView(statusTv)

        val replyTv = TextView(this).apply {
            text = "আসসালামু আলাইকুম! আমি সাথী—বলুন কীভাবে সাহায্য করতে পারি?"
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 5
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#332359"))
                cornerRadius = dp(14).toFloat()
            }
        }
        replyTextView = replyTv
        card.addView(replyTv)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
        }
        val inputEt = EditText(this).apply {
            hint = "এখানে লিখুন..."
            setHintTextColor(Color.parseColor("#A896C9"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1D1336"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#6E54A3"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendBtn = Button(this).apply {
            text = "পাঠান"
            setTextColor(Color.parseColor("#1B0D3D"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CBB6FF"))
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
            text = "🎙️ বলুন"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7B4DFF"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                statusTv.text = "শুনছি... বলুন"
                voiceController?.startListening(SathiLanguage.BN)
            }
        }
        val lookScreenBtn = Button(this).apply {
            text = "👁️ স্ক্রিন"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5B34C7"))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                inspectCurrentSharedScreen("")
            }
        }
        val openAppBtn = Button(this).apply {
            text = "অ্যাপ"
            setTextColor(Color.parseColor("#E9DDFF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3B2A66"))
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

        val pParams = WindowManager.LayoutParams(
            dp(336),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        panelParams = pParams
        panelView = card
        try {
            wm.addView(card, pParams)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach floating panel", e)
            panelView = null
        }
    }

    private fun inspectCurrentSharedScreen(question: String) {
        if (!SathiScreenShareService.isSharing.value) {
            val guide = "প্রথমে সাথী অ্যাপের '+' বাটন থেকে 'ফোন স্ক্রিন শেয়ার করুন' চালু করুন, তারপর এখানে '👁️ স্ক্রিন' চাপুন।"
            replyTextView?.text = guide
            statusTextView?.text = "স্ক্রিন শেয়ার চালু নেই"
            voiceController?.speak(guide, SathiLanguage.BN, SathiVoiceProfile.FEMALE, SathiMood.WARM, autoResumeLiveAfter = false)
            return
        }
        statusTextView?.text = "স্ক্রিন দেখছি..."
        serviceScope.launch {
            val frame = SathiScreenShareService.captureLatestFrame()
            if (frame == null) {
                statusTextView?.text = "ফ্রেম পাওয়া যায়নি"
                replyTextView?.text = "স্ক্রিন ফ্রেম এখনো তৈরি হয়নি, অনুগ্রহ করে আবার চেষ্টা করুন।"
                return@launch
            }
            val analysis = aiService.analyzeImage(frame, question, SathiLanguage.BN, isScreenCapture = true).getOrElse {
                "স্ক্রিন বিশ্লেষণ করা যায়নি।"
            }
            replyTextView?.text = analysis
            statusTextView?.text = "স্ক্রিন বিশ্লেষণ সম্পন্ন"
            voiceController?.speak(analysis, SathiLanguage.BN, SathiVoiceProfile.FEMALE, SathiMood.WARM, autoResumeLiveAfter = false)
        }
    }

    private fun handleFloatingQuery(query: String) {
        val lower = query.lowercase()
        if (SathiScreenShareService.isSharing.value &&
            (lower.contains("স্ক্রিন") || lower.contains("screen") ||
                lower.contains("এটা কী") || lower.contains("এখানে কী"))
        ) {
            inspectCurrentSharedScreen(query)
            return
        }

        statusTextView?.text = "সাথী ভাবছে..."
        serviceScope.launch {
            val result = aiService.generateCompanionReply(
                userMessage = query,
                language = SathiLanguage.BN,
                userName = "বন্ধু",
                memories = emptyList(),
                recentHistory = emptyList(),
                webSources = emptyList(),
                allowLongTermMemory = false
            )
            val turn = result.getOrNull() ?: return@launch
            val finalTurn = if (turn.actionType == PhoneActionType.WEB_SEARCH) {
                val sources = webSearchService.searchWeb(turn.actionParam.ifBlank { query }, SathiLanguage.BN)
                if (sources.isNotEmpty()) {
                    aiService.generateCompanionReply(
                        userMessage = query,
                        language = SathiLanguage.BN,
                        userName = "বন্ধু",
                        memories = emptyList(),
                        recentHistory = emptyList(),
                        webSources = sources,
                        allowLongTermMemory = false
                    ).getOrElse { turn }
                } else {
                    turn.copy(
                        replyText = "দুঃখিত, এই বিষয়ে ইন্টারনেটে সঠিক তথ্য পাওয়া যায়নি।",
                        actionType = PhoneActionType.NONE
                    )
                }
            } else turn

            if (finalTurn.actionType != PhoneActionType.NONE &&
                finalTurn.actionType != PhoneActionType.WEB_SEARCH &&
                finalTurn.actionType != PhoneActionType.CALL_CONFIRM &&
                finalTurn.actionType != PhoneActionType.SMS_CONFIRM
            ) {
                deviceActions.executeAction(finalTurn.actionType, finalTurn.actionParam, finalTurn.secondaryParam, SathiLanguage.BN)
            }

            replyTextView?.text = finalTurn.replyText
            statusTextView?.text = "উত্তর প্রস্তুত"
            voiceController?.speak(
                finalTurn.replyText,
                SathiLanguage.BN,
                SathiVoiceProfile.FEMALE,
                SathiMood.WARM,
                autoResumeLiveAfter = false
            )
        }
    }

    private fun hideMiniPanel() {
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
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        voiceController?.release()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "SathiFloatingService"
        private const val CHANNEL_ID = "sathi_overlay_channel"
        private const val NOTIFICATION_ID = 7861
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
