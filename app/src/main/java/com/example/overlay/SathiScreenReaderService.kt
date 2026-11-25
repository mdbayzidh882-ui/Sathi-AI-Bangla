package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        _isConnected.value = false
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
                val composePresent = findComposeField(root) != null
                if (composePresent && hasMatchingChatHeader(root, flow)) {
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
                                pendingSocialFlow = flow.copy(lastActionTimeMs = now)
                            }
                            return
                        }
                        // Search query already entered; wait for matching friend row or clear if search-only
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

            // Step 2: Type the message text into the chat compose box (never auto-clicks Send)
            val activeFlow = pendingSocialFlow ?: return
            if (activeFlow.messageText.isBlank()) {
                pendingSocialFlow = null
                return
            }

            val composeNode = findComposeField(root) ?: return
            val currentText = composeNode.text?.toString()?.trim().orEmpty()
            val hintText = composeNode.hintText?.toString()?.trim().orEmpty()
            if (currentText.isNotEmpty() && currentText != hintText) {
                return
            }
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    activeFlow.messageText
                )
            }
            if (composeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                pendingSocialFlow = null
            }
        } catch (_: Exception) {
        }
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
                text == "search" || viewId.contains("menuitem_search") ||
                viewId.contains("search_bar") || viewId.contains("search_button")
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
            if (pkg == packageName) return // Don't overwrite external app context with Sathi's own UI if switching briefly

            val lines = LinkedHashSet<String>()
            collectVisibleText(root, lines, depth = 0)
            val appName = resolveAppLabel(pkg)
            _latestSnapshot.value = ScreenAccessibilitySnapshot(
                activePackage = pkg,
                activeAppName = appName,
                visibleTextLines = lines.take(45),
                updatedAtMs = System.currentTimeMillis()
            )
        } catch (_: Exception) {
        }
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
        val recipientOpenedOrSearched: Boolean = false,
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
        private var pendingSocialFlow: StagedSocialFlow? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _latestSnapshot = MutableStateFlow(ScreenAccessibilitySnapshot())
        val latestSnapshot: StateFlow<ScreenAccessibilitySnapshot> = _latestSnapshot.asStateFlow()

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
            messageText: String
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
                recipientOpenedOrSearched = false,
                lastActionTimeMs = 0L,
                expiresAtMs = System.currentTimeMillis() + 25_000L
            )
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
