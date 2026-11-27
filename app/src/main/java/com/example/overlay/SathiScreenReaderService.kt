package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.comm.IncomingCommType
import com.example.comm.IncomingCommunicationManager
import com.example.model.SathiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MultimediaAppType(val labelBn: String, val labelEn: String) {
    YOUTUBE("ইউটিউব", "YouTube"),
    FACEBOOK("ফেসবুক", "Facebook"),
    MESSENGER("মেসেঞ্জার", "Messenger")
}

data class MultimediaMonitorState(
    val isMonitoring: Boolean = false,
    val appType: MultimediaAppType? = null,
    val packageName: String = "",
    val appLabel: String = "",
    val summaryText: String = "",
    val updatedAtMs: Long = 0L
)

data class ScreenAccessibilitySnapshot(
    val activePackage: String = "",
    val activeAppName: String = "",
    val visibleTextLines: List<String> = emptyList(),
    val updatedAtMs: Long = 0L
)

class SathiScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnected.value = true
        instance = this
        captureWindowSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        captureWindowSnapshot()
        tryExecuteSocialFlow()
    }

    override fun onInterrupt() {
        // No persistent resources to release here
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        _isConnected.value = false
        _isGameModePaused.value = false
    }

    private fun tryExecuteSocialFlow() {
        val flow = pendingSocialFlow ?: return
        val now = System.currentTimeMillis()
        if (now > flow.expiresAtMs) {
            pendingSocialFlow = null
            return
        }
        if (now - flow.lastActionTimeMs < 450L) return

        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg == packageName) return
            if (flow.targetPackages.isNotEmpty() && pkg !in flow.targetPackages) return

            // Step 1: Locate or search for the friend first when recipientName is given
            if (flow.recipientName.isNotBlank() && !flow.recipientOpenedOrSearched) {
                val composeField = findComposeField(root)
                val composePresent = composeField != null
                val prefilledBody = composeField?.text?.toString()?.trim().orEmpty()
                val alreadyInTargetChat = composePresent && (
                    hasMatchingChatHeader(root, flow) ||
                        (flow.messageText.isNotBlank() && prefilledBody.contains(flow.messageText, ignoreCase = true))
                    )
                if (alreadyInTargetChat) {
                    // Already inside the friend's chat thread
                    pendingSocialFlow = flow.copy(recipientOpenedOrSearched = true)
                } else {
                    val friendRow = findClickableMatchingFriendRow(root, flow)
                    if (friendRow != null) {
                        if (performClickSafely(friendRow)) {
                            if (flow.messageText.isBlank()) {
                                pendingSocialFlow = null
                            } else {
                                pendingSocialFlow = flow.copy(
                                    recipientOpenedOrSearched = true,
                                    lastActionTimeMs = now
                                )
                            }
                            return
                        }
                    }

                    val searchEdit = findSearchField(root)
                    if (searchEdit != null) {
                        val curSearch = searchEdit.text?.toString()?.trim().orEmpty()
                        val searchQuery = flow.preferredSearchQuery()
                        val elapsedSinceSearch = if (flow.searchSubmittedAtMs > 0L) now - flow.searchSubmittedAtMs else 0L
                        val shouldRetryTranslit = !flow.triedTransliteration &&
                            flow.searchSubmittedAtMs > 0L &&
                            elapsedSinceSearch >= 1_100L &&
                            flow.transliteratedRecipient.length >= 2 &&
                            !flow.transliteratedRecipient.equals(searchQuery, ignoreCase = true)

                        if (shouldRetryTranslit) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    flow.transliteratedRecipient
                                )
                            }
                            if (searchEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                                pendingSocialFlow = flow.copy(
                                    triedTransliteration = true,
                                    searchSubmittedAtMs = now,
                                    lastActionTimeMs = now
                                )
                            }
                            return
                        }

                        if (!matchesFriend(curSearch, flow) &&
                            !curSearch.equals(searchQuery, ignoreCase = true)
                        ) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(
                                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                    searchQuery
                                )
                            }
                            if (searchEdit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                                pendingSocialFlow = flow.copy(
                                    searchSubmittedAtMs = now,
                                    lastActionTimeMs = now
                                )
                            }
                            return
                        }
                        if (flow.messageText.isBlank()) {
                            pendingSocialFlow = flow.copy(lastActionTimeMs = now)
                        }
                        return
                    }

                    val searchBtn = findClickableSearchButton(root)
                    if (searchBtn != null && performClickSafely(searchBtn)) {
                        pendingSocialFlow = flow.copy(lastActionTimeMs = now)
                        return
                    }
                    return
                }
            }

            // Step 2: Type the message text into the chat compose box
            val activeFlow = pendingSocialFlow ?: return
            if (activeFlow.messageText.isBlank()) {
                pendingSocialFlow = null
                return
            }

            val composeNode = findComposeField(root) ?: return
            val currentText = composeNode.text?.toString()?.trim().orEmpty()
            val hintText = composeNode.hintText?.toString()?.trim().orEmpty()
            if (currentText.isEmpty() || currentText == hintText) {
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        activeFlow.messageText
                    )
                }
                val typed = composeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!typed) return
            }

            if (activeFlow.sendAfterType) {
                val sendBtn = findMatchingActionButton(
                    root,
                    listOf("send", "পাঠান", "পাঠাও", "post", "পোস্ট")
                )
                if (sendBtn != null) {
                    performClickSafely(sendBtn)
                    pendingSocialFlow = null
                } else {
                    pendingSocialFlow = activeFlow.copy(lastActionTimeMs = now)
                }
            } else {
                pendingSocialFlow = null
            }
        } catch (_: Exception) {
        }
    }

    private fun executeDirectCommandOnWindow(command: String, textToType: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg == packageName) return false

            val lower = command.lowercase()
            if (textToType.isNotBlank() &&
                (lower == "type" || lower.contains("টাইটেল") || lower.contains("title") ||
                    lower.contains("ক্যাপশন") || lower.contains("caption") || lower.contains("লিখ") || lower.contains("লেখ"))
            ) {
                val targetField = findComposeField(root)
                if (targetField != null) {
                    val args = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                    }
                    return targetField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                return false
            }

            val tokens = when {
                lower.contains("send") || lower.contains("সেন্ড") || lower.contains("পাঠা") ->
                    listOf("send", "পাঠান", "পাঠাও")
                lower.contains("next") || lower.contains("নেক্সট") || lower.contains("পরবর্তী") ->
                    listOf("next", "নেক্সট", "পরবর্তী", "continue", "এগিয়ে")
                lower.contains("publish") || lower.contains("পাবলিশ") || lower.contains("upload") || lower.contains("আপলোড") ->
                    listOf("publish", "পাবলিশ", "upload", "আপলোড", "post", "পোস্ট", "share", "শেয়ার")
                lower.contains("post") || lower.contains("পোস্ট") ->
                    listOf("post", "পোস্ট", "publish", "পাবলিশ")
                else -> listOf(command.trim())
            }
            val btn = findMatchingActionButton(root, tokens) ?: return false
            performClickSafely(btn)
        } catch (_: Exception) {
            false
        }
    }

    private fun findMatchingActionButton(
        node: AccessibilityNodeInfo?,
        keywords: List<String>,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        if (node.isVisibleToUser && !node.isEditable) {
            val txt = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val matches = keywords.any { kw ->
                val k = kw.lowercase()
                txt == k || desc == k || txt.contains(k) || desc.contains(k) || viewId.contains(k)
            }
            if (matches) {
                if (node.isClickable) return node
                var parent = node.parent
                var steps = 0
                while (parent != null && steps < 4) {
                    if (parent.isClickable && !parent.isEditable) return parent
                    parent = parent.parent
                    steps++
                }
            }
        }
        for (i in 0 until node.childCount) {
            val found = findMatchingActionButton(node.getChild(i), keywords, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun isSearchField(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val hint = node.hintText?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        return viewId.contains("search") || viewId.contains("filter") ||
            hint.contains("search") || hint.contains("অনুসন্ধান") || hint.contains("খুঁজ") ||
            desc.contains("search") || desc.contains("অনুসন্ধান")
    }

    private fun isComposeField(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable && node.isVisibleToUser && !node.isPassword && !isSearchField(node)
    }

    private fun findSearchField(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        if (node.isEditable && node.isVisibleToUser && !node.isPassword && isSearchField(node)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findSearchField(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findComposeField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && isComposeField(focused)) {
            return focused
        }
        return findFirstComposeRecursive(root, 0)
    }

    private fun findFirstComposeRecursive(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        if (isComposeField(node)) return node
        for (i in 0 until node.childCount) {
            val found = findFirstComposeRecursive(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableSearchButton(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        if (node.isVisibleToUser && !node.isEditable) {
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val looksLikeSearch = desc.contains("search") || desc.contains("অনুসন্ধান") ||
                desc.contains("খুঁজ") || desc.contains("new chat") || desc.contains("নতুন চ্যাট") ||
                text == "search" || viewId.contains("menuitem_search") ||
                viewId.contains("search_bar") || viewId.contains("search_button") ||
                viewId.contains("fab")
            if (looksLikeSearch) {
                if (node.isClickable) return node
                var parent = node.parent
                var steps = 0
                while (parent != null && steps < 4) {
                    if (parent.isClickable) return parent
                    parent = parent.parent
                    steps++
                }
            }
        }
        for (i in 0 until node.childCount) {
            val found = findClickableSearchButton(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun matchesFriend(candidate: String, flow: StagedSocialFlow): Boolean {
        val clean = candidate.trim()
        if (clean.length < 2) return false
        val lower = clean.lowercase()
        if (flow.recipientName.isNotBlank() && lower.contains(flow.recipientName.lowercase())) return true
        if (flow.transliteratedRecipient.length >= 2 && lower.contains(flow.transliteratedRecipient.lowercase())) return true
        return com.example.data.DeviceActionExecutor.matchesContactName(clean, flow.recipientName)
    }

    private fun hasMatchingChatHeader(node: AccessibilityNodeInfo?, flow: StagedSocialFlow, depth: Int = 0): Boolean {
        if (node == null || depth > 12) return false
        if (!node.isEditable && node.isVisibleToUser) {
            val txt = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (matchesFriend(txt, flow) || matchesFriend(desc, flow)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (hasMatchingChatHeader(node.getChild(i), flow, depth + 1)) return true
        }
        return false
    }

    private fun findClickableMatchingFriendRow(node: AccessibilityNodeInfo?, flow: StagedSocialFlow, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 16) return null
        if (node.isVisibleToUser && !node.isEditable) {
            val txt = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (matchesFriend(txt, flow) || matchesFriend(desc, flow)) {
                if (node.isClickable) return node
                var parent = node.parent
                var steps = 0
                while (parent != null && steps < 5) {
                    if (parent.isClickable && !parent.isEditable) return parent
                    parent = parent.parent
                    steps++
                }
            }
        }
        for (i in 0 until node.childCount) {
            val found = findClickableMatchingFriendRow(node.getChild(i), flow, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun performClickSafely(node: AccessibilityNodeInfo): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var steps = 0
        while (parent != null && steps < 4) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
            steps++
        }
        return false
    }

    private fun captureWindowSnapshot() {
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString().orEmpty()

            // Detect entry/exit into Free Fire or other mobile games -> deactivate while gaming, reactivate on exit
            val nowGame = isPackageGame(pkg)
            if (nowGame != _isGameModePaused.value) {
                _isGameModePaused.value = nowGame
                SathiWakeWordService.handleGameModeChanged(this, nowGame)
                if (SathiFloatingService.isRunning.value) {
                    val action = if (nowGame) {
                        SathiFloatingService.ACTION_GAME_PAUSE
                    } else {
                        SathiFloatingService.ACTION_GAME_RESUME
                    }
                    runCatching {
                        startService(Intent(this, SathiFloatingService::class.java).setAction(action))
                    }
                }
            }

            if (pkg == packageName || nowGame) {
                if (nowGame) {
                    _multimediaState.value = MultimediaMonitorState(isMonitoring = false)
                }
                return
            }

            val lines = LinkedHashSet<String>()
            collectVisibleText(root, lines, depth = 0)
            val appName = resolveAppLabel(pkg)
            val snap = ScreenAccessibilitySnapshot(
                activePackage = pkg,
                activeAppName = appName,
                visibleTextLines = lines.take(45),
                updatedAtMs = System.currentTimeMillis()
            )
            _latestSnapshot.value = snap

            // Monitor multimedia screens (YouTube, Facebook, Messenger) automatically without permission prompts
            val mmType = classifyMultimediaPackage(pkg)
            if (mmType != null) {
                _multimediaState.value = MultimediaMonitorState(
                    isMonitoring = true,
                    appType = mmType,
                    packageName = pkg,
                    appLabel = mmType.labelEn,
                    summaryText = snap.visibleTextLines.joinToString(" | ").take(900),
                    updatedAtMs = snap.updatedAtMs
                )
            } else {
                _multimediaState.value = MultimediaMonitorState(isMonitoring = false)
            }

            // Detect incoming call screen as fallback if Dialer/WhatsApp/Messenger incoming call screen appears
            detectIncomingCallUi(root, pkg, appName, snap.visibleTextLines)
        } catch (_: Exception) {
        }
    }

    private fun detectIncomingCallUi(
        root: AccessibilityNodeInfo,
        pkg: String,
        appName: String,
        lines: List<String>
    ) {
        if (!IncomingCommunicationManager.isCallPackage(pkg)) return
        val combined = lines.joinToString(" ").lowercase()
        val looksIncoming = combined.contains("incoming call") ||
            combined.contains("কল আসছে") ||
            (combined.contains("answer") && combined.contains("decline")) ||
            (combined.contains("উত্তর") && combined.contains("বাতিল"))
        if (!looksIncoming) return

        val caller = lines.firstOrNull { line ->
            val l = line.lowercase()
            line.length in 2..48 &&
                !l.contains("incoming") && !l.contains("answer") && !l.contains("decline") &&
                !l.contains("swipe") && !l.contains("কল আসছে")
        }.orEmpty().ifBlank { appName }

        IncomingCommunicationManager.recordQuickIncomingSync(
            type = IncomingCommType.CALL,
            appName = appName,
            packageName = pkg,
            sender = caller,
            incomingText = lines.take(4).joinToString(" "),
            language = SathiLanguage.BN,
            autoRepliedViaSystem = false
        )
    }

    private fun isPackageGame(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == packageName) return false
        if (isKnownGamePackage(pkg)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val categoryGame = runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                info.category == ApplicationInfo.CATEGORY_GAME
            }.getOrDefault(false)
            if (categoryGame) return true
        }
        return false
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (node == null || depth > 14 || out.size >= 50) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() && text.length <= 260) {
            out.add(text)
        }
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc.length <= 200 && desc != text) {
            out.add(desc)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleText(child, out, depth + 1)
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        if (pkg.isBlank()) return ""
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private data class StagedSocialFlow(
        val targetPackages: Set<String>,
        val recipientName: String,
        val transliteratedRecipient: String,
        val messageText: String,
        val sendAfterType: Boolean = false,
        val recipientOpenedOrSearched: Boolean = false,
        val triedTransliteration: Boolean = false,
        val searchSubmittedAtMs: Long = 0L,
        val lastActionTimeMs: Long = 0L,
        val expiresAtMs: Long
    ) {
        fun preferredSearchQuery(): String {
            return if (recipientName.any { it in '\u0980'..'\u09FF' } && transliteratedRecipient.length >= 2) {
                recipientName
            } else {
                recipientName.ifBlank { transliteratedRecipient }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SathiScreenReaderService? = null

        @Volatile
        private var pendingSocialFlow: StagedSocialFlow? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _isGameModePaused = MutableStateFlow(false)
        val isGameModePaused: StateFlow<Boolean> = _isGameModePaused.asStateFlow()

        private val _multimediaState = MutableStateFlow(MultimediaMonitorState())
        val multimediaState: StateFlow<MultimediaMonitorState> = _multimediaState.asStateFlow()

        private val _latestSnapshot = MutableStateFlow(ScreenAccessibilitySnapshot())
        val latestSnapshot: StateFlow<ScreenAccessibilitySnapshot> = _latestSnapshot.asStateFlow()

        private val knownGamePackages = setOf(
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.tencent.ig",
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.mobile.legends",
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.supercell.clashofclans",
            "com.supercell.clashroyale",
            "com.supercell.brawlstars",
            "com.roblox.client",
            "com.mojang.minecraftpe",
            "com.innersloth.spacemafia",
            "com.kiloo.subwaysurf",
            "com.outfit7.talkingtomgoldrun",
            "com.miniclip.eightballpool",
            "com.ludo.king",
            "jp.konami.pesam",
            "com.ea.gp.fifamobile"
        )

        fun isKnownGamePackage(pkg: String): Boolean {
            val lower = pkg.lowercase().trim()
            if (lower.isEmpty()) return false
            if (lower in knownGamePackages) return true
            return lower.contains(".freefire") ||
                lower.contains(".pubg") ||
                lower.startsWith("com.gameloft.") ||
                lower.startsWith("com.ea.game.") ||
                lower.startsWith("com.mihoyo.") ||
                lower.startsWith("com.garena.") ||
                lower.startsWith("com.netease.")
        }

        fun classifyMultimediaPackage(pkg: String): MultimediaAppType? {
            return when (pkg.lowercase().trim()) {
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
                "com.google.android.youtube.tv" -> MultimediaAppType.YOUTUBE
                "com.facebook.katana",
                "com.facebook.lite" -> MultimediaAppType.FACEBOOK
                "com.facebook.orca",
                "com.facebook.mlite" -> MultimediaAppType.MESSENGER
                else -> null
            }
        }

        fun stagePendingAutoFill(targetPackages: List<String>, text: String) {
            stagePendingSocialFlow(
                targetPackages = targetPackages,
                recipientName = "",
                transliteratedRecipient = "",
                messageText = text
            )
        }

        fun stagePendingSocialFlow(
            targetPackages: List<String>,
            recipientName: String,
            transliteratedRecipient: String,
            messageText: String,
            sendAfterType: Boolean = false
        ) {
            val cleanRecipient = recipientName.trim()
            val cleanTranslit = transliteratedRecipient.trim()
            val cleanMsg = messageText.trim()
            if (cleanRecipient.isEmpty() && cleanMsg.isEmpty()) return
            pendingSocialFlow = StagedSocialFlow(
                targetPackages = targetPackages.toSet(),
                recipientName = cleanRecipient,
                transliteratedRecipient = cleanTranslit,
                messageText = cleanMsg,
                sendAfterType = sendAfterType,
                recipientOpenedOrSearched = false,
                lastActionTimeMs = 0L,
                expiresAtMs = System.currentTimeMillis() + 25_000L
            )
        }

        fun performDirectScreenCommand(command: String, textToType: String = ""): Boolean {
            val svc = instance ?: return false
            return svc.executeDirectCommandOnWindow(command, textToType)
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            if (_isConnected.value) return true
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                val cn = ComponentName(context, SathiScreenReaderService::class.java).flattenToString()
                enabled.contains(cn, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        fun formatScreenContextSummary(): String {
            val snap = _latestSnapshot.value
            if (snap.visibleTextLines.isEmpty() && snap.activeAppName.isBlank()) return ""
            return buildString {
                if (snap.activeAppName.isNotBlank()) {
                    append("চলমান অ্যাপ: ${snap.activeAppName}\n")
                }
                if (snap.visibleTextLines.isNotEmpty()) {
                    append("স্ক্রিনের সম্পূর্ণ লেখা/ডেটা:\n")
                    append(snap.visibleTextLines.joinToString(" | ").take(1200))
                }
            }.trim()
        }
    }
}
