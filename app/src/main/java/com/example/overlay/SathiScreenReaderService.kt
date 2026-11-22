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
        tryAutoFillPendingText()
    }

    override fun onInterrupt() {
        // No persistent resources to release here
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
    }

    private fun tryAutoFillPendingText() {
        val pending = pendingAutoFill ?: return
        if (System.currentTimeMillis() > pending.expiresAtMs) {
            pendingAutoFill = null
            return
        }
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg == packageName) return
            if (pending.targetPackages.isNotEmpty() && pkg !in pending.targetPackages) return

            val editableNode = findTargetEditableNode(root) ?: return
            val currentText = editableNode.text?.toString()?.trim().orEmpty()
            val hintText = editableNode.hintText?.toString()?.trim().orEmpty()
            if (currentText.isNotEmpty() && currentText != hintText) {
                return
            }
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    pending.text
                )
            }
            if (editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                pendingAutoFill = null
            }
        } catch (_: Exception) {
        }
    }

    private fun findTargetEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable && !focused.isPassword) {
            return focused
        }
        return findFirstEditable(root, 0)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 14) return null
        if (node.isEditable && node.isVisibleToUser && !node.isPassword) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstEditable(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
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

    private data class StagedAutoFill(
        val targetPackages: Set<String>,
        val text: String,
        val expiresAtMs: Long
    )

    companion object {
        @Volatile
        private var pendingAutoFill: StagedAutoFill? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _latestSnapshot = MutableStateFlow(ScreenAccessibilitySnapshot())
        val latestSnapshot: StateFlow<ScreenAccessibilitySnapshot> = _latestSnapshot.asStateFlow()

        fun stagePendingAutoFill(targetPackages: List<String>, text: String) {
            val clean = text.trim()
            if (clean.isEmpty()) return
            pendingAutoFill = StagedAutoFill(
                targetPackages = targetPackages.toSet(),
                text = clean,
                expiresAtMs = System.currentTimeMillis() + 30_000L
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
