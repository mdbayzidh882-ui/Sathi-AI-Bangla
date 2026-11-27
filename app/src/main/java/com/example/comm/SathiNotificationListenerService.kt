package com.example.comm

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.SathiPreferences
import com.example.data.SathiAiService
import com.example.model.SathiLanguage
import com.example.overlay.SathiScreenReaderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Listens for real incoming message and call notifications (when granted Notification Access),
 * engages in polite Bengali dialogue via notification RemoteInput / Call Answer action,
 * and summarizes the core inquiry for Boss via IncomingCommunicationManager.
 */
class SathiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: SathiPreferences
    private val aiService = SathiAiService()

    @Volatile
    private var autoHandleEnabled: Boolean = true
    @Volatile
    private var cachedApiKey: String = ""
    @Volatile
    private var cachedLanguage: SathiLanguage = SathiLanguage.BN

    override fun onCreate() {
        super.onCreate()
        preferences = SathiPreferences(this)
        scope.launch {
            preferences.settingsFlow.collect { s ->
                autoHandleEnabled = s.autoHandleIncomingComms
                cachedApiKey = s.customGeminiApiKey
                cachedLanguage = SathiLanguage.fromCode(s.preferredLanguageFallback)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBar = sbn ?: return
        val pkg = statusBar.packageName.orEmpty()
        if (pkg.isBlank() || pkg == packageName) return
        if (SathiScreenReaderService.isGameModePaused.value) return
        if (!autoHandleEnabled) return

        val notification = statusBar.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val contentText = bigText.ifBlank { bodyText }

        val isCall = notification.category == Notification.CATEGORY_CALL ||
            (IncomingCommunicationManager.isCallPackage(pkg) && hasCallAnswerAction(notification))
        val replyAction = findReplyAction(notification)
        val isMessage = !isCall && (replyAction != null || IncomingCommunicationManager.isMessagingPackage(pkg))

        if (!isCall && !isMessage) return
        if (isMessage && (contentText.isBlank() || title.isBlank())) return
        if (isMessage && IncomingCommunicationManager.isSelfEchoReply(contentText)) return

        val appLabel = resolveAppLabel(pkg)

        scope.launch {
            if (isCall) {
                val answerAction = findAnswerCallAction(notification)
                val answered = if (answerAction != null) {
                    runCatching {
                        answerAction.actionIntent.send()
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                IncomingCommunicationManager.handleIncomingWithOptionalAi(
                    type = IncomingCommType.CALL,
                    appName = appLabel,
                    packageName = pkg,
                    sender = title.ifBlank { "অজানা কলার" },
                    incomingText = contentText,
                    apiKey = cachedApiKey,
                    language = cachedLanguage,
                    aiService = aiService,
                    autoRepliedViaSystem = answered
                )
            } else {
                val quickDialogue = IncomingCommunicationManager.buildPoliteDialogueReply(
                    sender = title,
                    incomingText = contentText,
                    type = IncomingCommType.MESSAGE,
                    language = cachedLanguage
                )
                val record = IncomingCommunicationManager.handleIncomingWithOptionalAi(
                    type = IncomingCommType.MESSAGE,
                    appName = appLabel,
                    packageName = pkg,
                    sender = title,
                    incomingText = contentText,
                    apiKey = cachedApiKey,
                    language = cachedLanguage,
                    aiService = aiService,
                    autoRepliedViaSystem = replyAction != null
                )
                val dialogueToSend = record?.sathiDialogueReply ?: quickDialogue
                if (replyAction != null && dialogueToSend.isNotBlank()) {
                    sendRemoteInputReply(replyAction, dialogueToSend)
                }
            }
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        return actions.firstOrNull { action ->
            val inputs = action.remoteInputs
            inputs != null && inputs.isNotEmpty()
        }
    }

    private fun hasCallAnswerAction(notification: Notification): Boolean =
        findAnswerCallAction(notification) != null

    private fun findAnswerCallAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        val answerTokens = listOf("answer", "accept", "pick up", "উত্তর", "গ্রহণ", "রিসিভ")
        return actions.firstOrNull { action ->
            val title = action.title?.toString()?.lowercase().orEmpty()
            action.actionIntent != null && answerTokens.any { title.contains(it) }
        }
    }

    private fun sendRemoteInputReply(action: Notification.Action, replyText: String): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        val pendingIntent = action.actionIntent ?: return false
        return try {
            val fillIn = Intent()
            val bundle = Bundle()
            for (ri in remoteInputs) {
                bundle.putCharSequence(ri.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, bundle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
            }
            IncomingCommunicationManager.markBotReplySent(replyText)
            pendingIntent.send(this, 0, fillIn)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        scope.cancel()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun isNotificationAccessEnabled(context: Context): Boolean {
            if (_isConnected.value) return true
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ).orEmpty()
                val cn = ComponentName(context, SathiNotificationListenerService::class.java).flattenToString()
                flat.contains(cn, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }
}
