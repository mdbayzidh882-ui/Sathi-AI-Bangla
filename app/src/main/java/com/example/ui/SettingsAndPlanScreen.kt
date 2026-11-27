package com.example.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.comm.SathiNotificationListenerService
import com.example.core.OfflinePackState
import com.example.core.OfflinePackTier
import com.example.core.SathiPreferences
import com.example.core.SathiThemeMode
import com.example.data.SathiAiService
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import com.example.overlay.SathiFloatingService
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiWakeWordService
import com.example.ui.theme.SathiListeningOrbPink
import com.example.ui.theme.SathiSpeakingOrbTeal

@Composable
fun SettingsAndPlanScreen(
    viewModel: SathiViewModel,
    onOpenAdminPanel: () -> Unit,
    initialOpenLookAndFeel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val googleKeyCheckState by viewModel.googleKeyCheckState.collectAsStateWithLifecycle()
    val isOwnerAdmin by viewModel.isOwnerAdmin.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.offlineDownloadStatus.collectAsStateWithLifecycle()
    val floatingRunning by SathiFloatingService.isRunning.collectAsStateWithLifecycle()
    val wakeRunning by SathiWakeWordService.isRunning.collectAsStateWithLifecycle()
    val screenReaderActive by SathiScreenReaderService.isConnected.collectAsStateWithLifecycle()
    val incomingSummaries by viewModel.incomingSummaries.collectAsStateWithLifecycle()
    val notifListenerConnected by SathiNotificationListenerService.isConnected.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val voice = SathiVoiceProfile.fromCode(profile?.preferredVoice)

    var nameInput by remember(profile?.userName) {
        mutableStateOf(profile?.userName.orEmpty())
    }
    var geminiKeyInput by remember { mutableStateOf(localSettings.customGeminiApiKey) }
    var showGeminiKey by remember { mutableStateOf(true) }
    var keyPasteNotice by remember { mutableStateOf<String?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var customTopicInput by remember { mutableStateOf("") }
    var systemPromptInput by remember(localSettings.customSystemPrompt) {
        mutableStateOf(localSettings.customSystemPrompt)
    }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showLookAndFeel by remember(initialOpenLookAndFeel) { mutableStateOf(initialOpenLookAndFeel) }
    var openOrbPickerDirectly by remember { mutableStateOf(false) }
    var showPhoneSafety by remember { mutableStateOf(false) }
    var showDataBackup by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }

    val basicPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    if (showLookAndFeel) {
        LookAndFeelScreen(
            localSettings = localSettings,
            language = language,
            onSelectOrbStyle = { viewModel.setOrbStyle(it) },
            onToggleStartEndSounds = { viewModel.setStartEndSoundsEnabled(it) },
            onToggleReduceMotion = { viewModel.setReduceMotion(it) },
            onSelectAnimationStyle = { viewModel.setAnimationStyle(it) },
            onSelectColorTheme = { viewModel.setColorTheme(it) },
            onBack = {
                showLookAndFeel = false
                openOrbPickerDirectly = false
            },
            initialOpenOrbPicker = openOrbPickerDirectly,
            modifier = modifier
        )
        return
    }

    if (showPhoneSafety) {
        PhoneControlAndSafetyScreen(
            localSettings = localSettings,
            language = language,
            screenReaderActive = screenReaderActive,
            onRequestBasicPermissions = {
                val list = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                basicPermsLauncher.launch(list.toTypedArray())
            },
            onSelectScrollLimit = { viewModel.setScrollLimit(it) },
            onToggleStopOnUserTouch = { viewModel.setStopOnUserTouch(it) },
            onToggleAppLock = { viewModel.setAppLockEnabled(it) },
            onAddSosContact = { n, p -> viewModel.addSosContact(n, p) },
            onRemoveSosContact = { viewModel.removeSosContact(it) },
            onSaveSosMessage = { viewModel.saveSosMessage(it) },
            onToggleSosLocation = { viewModel.setSosIncludeLocation(it) },
            onTriggerSosNow = { viewModel.triggerEmergencySos() },
            onBack = { showPhoneSafety = false },
            modifier = modifier
        )
        return
    }

    if (showDataBackup) {
        DataAndBackupScreen(
            localSettings = localSettings,
            language = language,
            onExportBackupJson = { viewModel.buildBackupJson() },
            onImportBackupJson = { viewModel.importBackupJson(it) },
            onToggleAutoBackup = { viewModel.setAutoBackupEnabled(it) },
            onRedetectModels = { viewModel.redetectAiModels() },
            onBack = { showDataBackup = false },
            modifier = modifier
        )
        return
    }

    LaunchedEffect(localSettings.customGeminiApiKey) {
        if (localSettings.customGeminiApiKey.isNotBlank() && geminiKeyInput.isBlank()) {
            geminiKeyInput = localSettings.customGeminiApiKey
        }
    }

    val wakeMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
            SathiWakeWordService.startService(context)
        }
    }

    val settingsVoiceOrbMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            val msg = if (language == SathiLanguage.BN) {
                "ভয়েস চ্যাটের জন্য মাইক্রোফোন অনুমতি দিন।"
            } else {
                "Allow microphone permission for Voice Chat."
            }
            viewModel.speakMessage(msg, SathiMood.WARM)
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "স্ক্রিন ডাটা রিডার (Accessibility)" else "Screen Data Reader")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "স্ক্রিন শেয়ার চলাকালে চলমান অ্যাপ ও স্ক্রিনের লেখা সম্পূর্ণভাবে বুঝে উত্তর দিতে সাথী আপনার অনুমতিতে Accessibility সেবা ব্যবহার করে। কোনো পাসওয়ার্ড পড়া বা সংরক্ষণ করা হয় না।"
                    } else {
                        "When Screen Share is active, Sathi uses Android Accessibility only to read active app names and visible on-screen text with your consent. Passwords are excluded."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showAccessibilityDialog = false
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }) {
                    Text(if (language == SathiLanguage.BN) "Accessibility খুলুন" else "Open Accessibility")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            icon = { Icon(Icons.Default.BubbleChart, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "ভাসমান সাথী বাবল অনুমতি" else "Overlay Permission Needed")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "অ্যাপের বাইরেও পাশে গোল বাবল দেখাতে 'Display over other apps' অনুমতি চালু করুন।"
                    } else {
                        "Please allow 'Display over other apps' so Sathi's floating orb works outside the app."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showOverlayDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }) {
                    Text(if (language == SathiLanguage.BN) "সেটিংস খুলুন" else "Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Default.Policy, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "গোপনীয়তা নীতি (Privacy Policy)" else "Privacy Policy")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "১. আপনার স্মৃতি ও সেটিংস কেবল আপনার ফোনেই সুরক্ষিত থাকে, এবং ওয়েব সার্চ ও অনুবাদ এনক্রিপ্টেড HTTPS সংযোগে চলে।\n" +
                            "২. অ্যাপের ভেতরে কোনো API Key বা বাধ্যতামূলক লগইন রাখা হয়নি।\n" +
                            "৩. মাইক্রোফোন ও ক্যামেরা কেবল আপনার স্পষ্ট অনুমতি ও ব্যবহারের সময় চালু হয়।\n" +
                            "৪. কল বা মেসেজ করার আগে সবসময় আপনার অনুমতি নেওয়া হয়।\n" +
                            "৫. আপনি যেকোনো সময় 'স্মৃতি' পর্দা থেকে নিজের সব ডেটা মুছে ফেলতে পারেন।"
                    } else {
                        "1. Your memories and settings stay stored locally on your device, and web search/translation use encrypted HTTPS.\n" +
                            "2. Zero API keys and no mandatory login are used in the app.\n" +
                            "3. Microphone and camera are used only with your permission when you tap.\n" +
                            "4. Calls and messages always ask your confirmation first.\n" +
                            "5. You can delete all your memories and local data anytime from Memory."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বুঝেছি" else "Close")
                }
            }
        )
    }

    val qLower = settingsQuery.trim().lowercase()
    fun matches(vararg keys: String): Boolean =
        qLower.isBlank() || keys.any { it.lowercase().contains(qLower) }

    val micReady = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val notifReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val overlayReady = Settings.canDrawOverlays(context)
    val readinessCount = listOf(micReady, notifReady, overlayReady, screenReaderActive).count { it }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (language == SathiLanguage.BN) "সেটিংস ও প্ল্যান" else "Settings & Plans",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = settingsQuery,
                    onValueChange = { settingsQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = {
                        Text(if (language == SathiLanguage.BN) "সেটিংস খুঁজুন (যেমন: SOS, থিম, ব্যাকআপ)..." else "Search settings...")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_input")
                )
            }
        }

        // Setup readiness + Quick Access rows (Phone control & safety, Data & backup, Notification)
        if (matches("setup", "readiness", "phone", "safety", "sos", "lock", "permission", "backup", "restore", "notification", "নিরাপত্তা", "ব্যাকআপ", "ফোন", "নোটিফিকেশন")) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPhoneSafety = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (language == SathiLanguage.BN) {
                                            "সেটআপ প্রস্তুতি ($readinessCount / 4 প্রস্তুত)"
                                        } else {
                                            "Setup readiness ($readinessCount of 4 ready)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (language == SathiLanguage.BN) {
                                            "মাইক, নোটিফিকেশন, বাবল ও স্ক্রিন রিডার একসাথে দেখুন"
                                        } else {
                                            "Inspect microphone, notifications, overlay & accessibility"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }

                        // Phone control & safety row
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .testTag("open_phone_safety_button")
                                .clickable { showPhoneSafety = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (language == SathiLanguage.BN) "ফোন কন্ট্রোল ও নিরাপত্তা (SOS ও লক)" else "Phone control & safety",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (language == SathiLanguage.BN) {
                                            "অনুমতি, স্ক্রল সীমা (${localSettings.scrollLimit}), অ্যাপ লক ও SOS (${localSettings.sosContacts.size}/5)"
                                        } else {
                                            "Permissions, scroll limit (${localSettings.scrollLimit}), app lock & SOS"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }

                        // Data & backup row
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .testTag("open_data_backup_button")
                                .clickable { showDataBackup = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (language == SathiLanguage.BN) "ডেটা ও ব্যাকআপ" else "Data & backup",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (language == SathiLanguage.BN) {
                                            "ব্যাকআপ এক্সপোর্ট/রিস্টোর ও এআই মডেল ডায়াগনস্টিকস"
                                        } else {
                                            "Backup, restore & re-detect AI models"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }

                        // Persistent Sathi notification toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == SathiLanguage.BN) "সাথী দ্রুত নোটিফিকেশন" else "Sathi notification",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (language == SathiLanguage.BN) {
                                        "স্ট্যাটাস বারে এক ট্যাপে সাথীকে ডাকার শর্টকাট রাখুন"
                                    } else {
                                        "Keep a persistent quick-launch tile in your notification shade"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localSettings.persistentNotificationEnabled,
                                onCheckedChange = { viewModel.setPersistentNotificationEnabled(it) },
                                modifier = Modifier.testTag("sathi_notification_switch")
                            )
                        }
                    }
                }
            }
        }

        // 0. Appearance & Language (Look & feel, Orb style, Theme, Battery saver)
        if (matches("theme", "look", "orb", "color", "language", "battery", "day", "night", "থিম", "অর্ব", "ভাষা", "ব্যাটারি")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (language == SathiLanguage.BN) "থিম ও লুক (Appearance & language)" else "Appearance & language",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Look & feel entry row
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 60.dp)
                            .testTag("open_look_and_feel_button")
                            .clickable {
                                openOrbPickerDirectly = false
                                showLookAndFeel = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == SathiLanguage.BN) "লুক ও থিমস (Look & feel)" else "Look & feel",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (language == SathiLanguage.BN) {
                                        "অর্ব স্টাইল, মোশন, অ্যানিমেশন, থিম"
                                    } else {
                                        "Orb style, motion, theme"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (language == SathiLanguage.BN) localSettings.orbStyle.titleBn else localSettings.orbStyle.titleEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Language quick row
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable {
                                val nextLang = if (language == SathiLanguage.BN) SathiLanguage.EN else SathiLanguage.BN
                                viewModel.updateLanguageAndVoice(nextLang, voice, nameInput)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == SathiLanguage.BN) "ভাষা (Language)" else "Language",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (language == SathiLanguage.BN) "অ্যাপ ও কথোপকথনের ভাষা" else "App language",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (language == SathiLanguage.BN) "বাংলা" else "English",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Battery saver toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clickable { viewModel.setBatterySaver(!localSettings.batterySaver) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) "ব্যাটারি সেভার (Battery saver)" else "Battery saver",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "চার্জ বাঁচাতে অ্যানিমেশন শান্ত রাখুন"
                                } else {
                                    "Calmer animations to save power"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localSettings.batterySaver,
                            onCheckedChange = { viewModel.setBatterySaver(it) },
                            modifier = Modifier.testTag("battery_saver_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (localSettings.themeMode == SathiThemeMode.NIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "দ্রুত দিন / রাত ও থিম" else "Quick Day / Night & Theme",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SathiThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = localSettings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(if (language == SathiLanguage.BN) mode.labelBn else mode.labelEn) },
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
        }

        // 1. Profile, Language & Synthetic Voice Choice + Sample Preview
        if (matches("profile", "name", "voice", "language", "নাম", "কণ্ঠ", "ভয়েস")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "নাম, ভাষা ও কণ্ঠস্বর" else "Profile, Language & Voice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(if (language == SathiLanguage.BN) "আপনার নাম" else "Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SathiLanguage.entries.forEach { langOption ->
                            FilterChip(
                                selected = language == langOption,
                                onClick = { viewModel.updateLanguageAndVoice(langOption, voice, nameInput) },
                                label = { Text(if (language == SathiLanguage.BN) langOption.labelBn else langOption.labelEn) }
                            )
                        }
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "কণ্ঠস্বর নির্বাচন (লাইসেন্সকৃত ও সিন্থেটিক কণ্ঠ):"
                        } else {
                            "Voice Choice (Licensed & Synthetic Voices):"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )

                    SathiVoiceProfile.entries.forEach { voiceOption ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilterChip(
                                selected = voice == voiceOption,
                                onClick = { viewModel.updateLanguageAndVoice(language, voiceOption, nameInput) },
                                label = {
                                    Text(if (language == SathiLanguage.BN) voiceOption.labelBn else voiceOption.labelEn)
                                }
                            )
                            TextButton(
                                onClick = { viewModel.previewVoiceSample(voiceOption) },
                                modifier = Modifier.testTag("preview_voice_${voiceOption.code}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == SathiLanguage.BN) "নমুনা শুনুন" else "Preview")
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DancingVoiceOrb(
                                listening = voiceState.listening,
                                speaking = voiceState.speaking,
                                thinking = isThinking,
                                muted = voiceState.muted,
                                liveActive = voiceState.liveSessionActive,
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.onMicButtonTapped()
                                    } else {
                                        settingsVoiceOrbMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                orbSize = 58.dp,
                                buttonTestTag = "settings_voice_chat_orb",
                                orbStyle = localSettings.orbStyle,
                                reduceMotion = localSettings.reduceMotion || localSettings.batterySaver,
                                animationStyle = localSettings.animationStyle
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == SathiLanguage.BN) "ভয়েস চ্যাট (হ্যান্ডস-ফ্রি গোল বাটন)" else "Voice Chat (Hands-Free Orb)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        voiceState.speaking -> if (language == SathiLanguage.BN) "সাথী বলছে — কথা বললেই থামবে" else "Sathi speaking (talk to interrupt)"
                                        voiceState.listening -> if (language == SathiLanguage.BN) "শুনছে — গোলাপি রঙে নাচছে..." else "Listening (Pink dance)"
                                        else -> if (language == SathiLanguage.BN) "একবার ট্যাপ করুন — অটোমেটিক কথা বলবে ও শুনবে" else "Tap once — hands-free turns automatically"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        voiceState.speaking -> SathiSpeakingOrbTeal
                                        voiceState.listening -> SathiListeningOrbPink
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.updateLanguageAndVoice(language, voice, nameInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (language == SathiLanguage.BN) "নাম ও সেটিংস সংরক্ষণ করুন" else "Save Profile & Voice")
                    }
                }
            }
        }
        }

        // 2. Floating Gemini-Style Overlay Bubble
        if (matches("floating", "bubble", "overlay", "ভাসমান", "বাবল")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BubbleChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "ভাসমান সাথী বাবল (Floating Window)" else "Floating Gemini-Style Orb",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "অ্যাপ থেকে বের হয়ে গেলেও ফোনের যেকোনো স্ক্রিনের পাশে সাথীর গোল বাবল থাকবে—ট্যাপ করলেই ছোট ভাসমান উইন্ডো খুলে কথা বলা যাবে।"
                        } else {
                            "Keeps a draggable Sathi orb on the edge of your screen over other apps, just like a quick floating assistant."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            toggleFloatingOverlay(
                                context = context,
                                currentlyRunning = floatingRunning,
                                onNeedPermission = { showOverlayDialog = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_floating_orb_button")
                    ) {
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (floatingRunning) "ভাসমান বাবল বন্ধ করুন" else "ভাসমান বাবল চালু করুন"
                            } else {
                                if (floatingRunning) "Turn Off Floating Orb" else "Enable Floating Orb"
                            }
                        )
                    }
                }
            }
        }
        }

        // 2a. Offline/Online Triple-Sathi Wake Word & Full Screen Data Reader
        if (matches("wake", "screen", "accessibility", "ওয়েক", "স্ক্রিন")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "ওয়েক-ওয়ার্ড ও সম্পূর্ণ স্ক্রিন ডাটা"
                            } else {
                                "Wake Word & Screen Reader"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "\"সাথী সাথী সাথী\" ওয়েক-ওয়ার্ড (অফলাইন/অনলাইন)"
                                } else {
                                    "\"Sathi Sathi Sathi\" Wake Word (Offline & Online)"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "তিনবার সাথী বললেই ফোনে অ্যাপ সামনে এসে কথা শোনা শুরু করবে"
                                } else {
                                    "Say Sathi three times to wake the app and start listening"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wakeRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.setWakeWordEnabled(true)
                                        SathiWakeWordService.startService(context)
                                    } else {
                                        wakeMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.setWakeWordEnabled(false)
                                    SathiWakeWordService.stopService(context)
                                }
                            },
                            modifier = Modifier.testTag("wake_word_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = { showAccessibilityDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (screenReaderActive) "স্ক্রিন ডাটা রিডার: চালু আছে ✓" else "স্ক্রিন ডাটা রিডার চালু করুন (Accessibility)"
                            } else {
                                if (screenReaderActive) "Screen Data Reader: Active ✓" else "Enable Screen Data Reader"
                            }
                        )
                    }
                }
            }
        }
        }

        // 2a-2. Boss Mode: Direct Auto-Execution & Editable System Prompt
        if (matches("auto", "action", "prompt", "boss", "whatsapp", "messenger", "প্রম্পট", "বস")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "অটো অ্যাকশন ও এআই আচরণ (বস মোড)"
                            } else {
                                "Auto Actions & AI System Prompt"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "অটো WhatsApp / Messenger ও অ্যাকশন"
                                } else {
                                    "Auto WhatsApp / Messenger Execution"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "চালু থাকলে পপআপ ছাড়াই সরাসরি অ্যাপ ওপেন ও মেসেজ টাইপ হবে"
                                } else {
                                    "Directly opens & executes social messages without confirmation popup"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localSettings.autoExecuteActions,
                            onCheckedChange = { viewModel.setAutoExecuteActions(it) },
                            modifier = Modifier.testTag("auto_execute_actions_switch")
                        )
                    }

                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = { systemPromptInput = it },
                        label = {
                            Text(
                                if (language == SathiLanguage.BN) "Sathi AI সিস্টেম প্রম্পট (System Prompt)"
                                else "Sathi AI System Prompt"
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_system_prompt_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                systemPromptInput = SathiPreferences.DEFAULT_BOSS_SYSTEM_PROMPT
                                viewModel.saveCustomSystemPrompt(SathiPreferences.DEFAULT_BOSS_SYSTEM_PROMPT)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == SathiLanguage.BN) "ডিফল্ট রিসেট" else "Reset Default")
                        }
                        Button(
                            onClick = { viewModel.saveCustomSystemPrompt(systemPromptInput) },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("settings_save_prompt_button")
                        ) {
                            Text(if (language == SathiLanguage.BN) "সংরক্ষণ" else "Save")
                        }
                    }
                }
            }
        }
        }

        // 2a-3. Incoming Call & Message Handling Assistant + Boss Summaries
        if (matches("incoming", "call", "message", "summary", "কল", "মেসেজ")) {
        item {
            IncomingCommAssistantCard(
                autoHandleEnabled = localSettings.autoHandleIncomingComms,
                listenerConnected = notifListenerConnected,
                summaries = incomingSummaries,
                language = language,
                onToggleAutoHandle = { viewModel.setAutoHandleIncomingComms(it) },
                onClearSummaries = { viewModel.clearIncomingSummaries() }
            )
        }
        }

        // 2b. Optional Gemini Cloud AI Key (Zero-Setup works without it; paste key for full Google Search + Vision)
        if (matches("gemini", "api", "key", "google", "search", "চাবি", "জেমিনি")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "গুগল জেমিনি সার্চ ও এআই ব্রেইন (ঐচ্ছিক)"
                            } else {
                                "Google Gemini Search & AI Brain (Optional)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "Google API Key সেটআপ ও যাচাই করলেই সাথী এআই সরাসরি জেমিনির মতো সম্পূর্ণ বুদ্ধিমত্তা, Google সার্চ, উদাহরণ থেকে ছবি তৈরি ও সর্বোচ্চ ২ জিবি স্মার্ট জ্ঞান ভান্ডার স্বয়ংক্রিয়ভাবে চালু করবে।"
                        } else {
                            "Verifying your Google API Key automatically activates full Gemini intelligence, Google Search, reference image generation, and syncs the Smart Knowledge Brain (up to 2 GB cap)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { raw ->
                            geminiKeyInput = SathiAiService.sanitizeApiKeyInput(raw)
                            keyPasteNotice = null
                        },
                        label = {
                            Text(if (language == SathiLanguage.BN) "Google API Key (AIza...)" else "Google API Key (AIza...)")
                        },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showGeminiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("optional_gemini_key_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val fromCompose = composeClipboard.getText()?.text.orEmpty()
                                val fromSystem = runCatching {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                }.getOrDefault("")
                                val cleaned = SathiAiService.sanitizeApiKeyInput(fromCompose.ifBlank { fromSystem })
                                if (cleaned.isBlank()) {
                                    keyPasteNotice = if (language == SathiLanguage.BN) "ক্লিপবোর্ডে কোনো চাবি কপি করা নেই।" else "Clipboard is empty."
                                } else {
                                    geminiKeyInput = cleaned
                                    keyPasteNotice = if (language == SathiLanguage.BN) "চাবি পেস্ট হয়েছে! এখন সংরক্ষণ ও যাচাই চাপুন।" else "Key pasted! Tap Save & Verify."
                                }
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("paste_gemini_key_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == SathiLanguage.BN) "পেস্ট" else "Paste")
                        }
                        Button(
                            onClick = {
                                val cleaned = SathiAiService.sanitizeApiKeyInput(geminiKeyInput)
                                geminiKeyInput = cleaned
                                keyPasteNotice = null
                                viewModel.saveAndVerifyGoogleApiKey(cleaned)
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("save_gemini_key_button")
                        ) {
                            Text(if (language == SathiLanguage.BN) "সংরক্ষণ ও যাচাই" else "Save & Verify")
                        }
                        if (localSettings.customGeminiApiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    geminiKeyInput = ""
                                    keyPasteNotice = null
                                    viewModel.saveAndVerifyGoogleApiKey("")
                                }
                            ) {
                                Text(if (language == SathiLanguage.BN) "মুছুন" else "Clear")
                            }
                        }
                    }
                    if (!keyPasteNotice.isNullOrBlank()) {
                        Text(
                            text = keyPasteNotice.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    when (googleKeyCheckState) {
                        is GoogleKeyCheckState.Connected -> {
                            Text(
                                text = "✓ ${(googleKeyCheckState as GoogleKeyCheckState.Connected).message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is GoogleKeyCheckState.Failed -> {
                            Text(
                                text = (googleKeyCheckState as GoogleKeyCheckState.Failed).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
        }

        // 3. Plan & Signed Code Verification
        if (matches("plan", "code", "premium", "owner", "প্ল্যান", "কোড")) {
        item {
            val plan = profile?.effectivePlan() ?: PlanTier.FREE
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "বর্তমান প্ল্যান: ${plan.labelBn}"
                            } else {
                                "Current Plan: ${plan.labelEn}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "ফ্রি: আনলিমিটেড ভয়েস চ্যাট, ওয়েব সার্চ, ইউটিউব, ফোন অ্যাকশন ও অফলাইন প্যাক।\nপ্রিমিয়াম / অনার: ক্যামেরা ও ভিডিও বিশ্লেষণ, দীর্ঘমেয়াদি মেমোরি, দিনের পরিকল্পনা, সারসংক্ষেপ, অনুবাদ ও সকালের শুভেচ্ছা।"
                        } else {
                            "Free: Unlimited voice chat, web search, YouTube, phone actions & Offline Pack.\nPremium / Owner: Camera & video understanding, long-term memory, day planning, summaries, translation & morning greeting."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        label = {
                            Text(if (language == SathiLanguage.BN) "অ্যাডমিন থেকে প্রাপ্ত কোড লিখুন" else "Enter Activation or Owner Code")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redeem_code_input")
                    )

                    Button(
                        onClick = {
                            viewModel.redeemCode(codeInput)
                            codeInput = ""
                        },
                        enabled = codeInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redeem_code_button")
                    ) {
                        Text(if (language == SathiLanguage.BN) "কোড যাচাই ও চালু করুন" else "Verify & Redeem Code")
                    }
                }
            }
        }
        }

        // 4. Real Offline Pack Manager + Android Offline Voice Setup
        if (matches("offline", "pack", "tts", "speech", "অফলাইন", "প্যাক")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "স্মার্ট জ্ঞান ভান্ডার ও অফলাইন প্যাক (সর্বোচ্চ ২ জিবি)" else "Smart Knowledge Brain (Up to 2 GB)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "Google চাবি সেট করলেই সাথী প্রয়োজনমতো Google ও এনসাইক্লোপিডিয়া থেকে তথ্য নিয়ে সর্বোচ্চ ২ জিবি পর্যন্ত নিজের মেধা সংরক্ষণ করে যাতে নেট ছাড়াও কথা ও উত্তর দিতে পারে।"
                        } else {
                            "Automatically collects and stores structured encyclopedic & Gemini-distilled knowledge on-device up to a strict 2 GB budget for rich offline voice answers."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = viewModel.getOfflineBrainBudgetSummary(language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflinePackTier.entries.forEach { tier ->
                            FilterChip(
                                selected = localSettings.offlinePackTier == tier,
                                onClick = { viewModel.selectOfflinePackTier(tier) },
                                enabled = localSettings.offlinePackState == OfflinePackState.NOT_INSTALLED,
                                label = {
                                    Text(if (language == SathiLanguage.BN) tier.sizeLabelBn else tier.sizeLabelEn)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == SathiLanguage.BN) "শুধু ওয়াই-ফাই দিয়ে ডাউনলোড" else "Download on Wi-Fi Only",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = localSettings.wifiOnlyDownload,
                            onCheckedChange = { viewModel.setWifiOnlyDownload(it) }
                        )
                    }

                    if (localSettings.offlinePackState == OfflinePackState.DOWNLOADING ||
                        localSettings.offlinePackState == OfflinePackState.PAUSED
                    ) {
                        val pct = (localSettings.offlineDownloadProgress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { localSettings.offlineDownloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (downloadStatus.isNotBlank()) "$downloadStatus ($pct%)"
                            else if (language == SathiLanguage.BN) "অগ্রগতি: $pct%" else "Progress: $pct%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (localSettings.offlinePackState) {
                            OfflinePackState.NOT_INSTALLED -> {
                                Button(
                                    onClick = { viewModel.startOrResumeOfflineDownload() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_offline_pack_button")
                                ) {
                                    Text(
                                        if (language == SathiLanguage.BN) {
                                            "অফলাইন প্যাক ডাউনলোড করুন"
                                        } else {
                                            "Download Offline Pack"
                                        }
                                    )
                                }
                            }
                            OfflinePackState.DOWNLOADING -> {
                                OutlinedButton(
                                    onClick = { viewModel.pauseOfflineDownload() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == SathiLanguage.BN) "বিরতি" else "Pause")
                                }
                                IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete pack", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OfflinePackState.PAUSED -> {
                                Button(
                                    onClick = { viewModel.startOrResumeOfflineDownload() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == SathiLanguage.BN) "পুনরায় শুরু" else "Resume")
                                }
                                IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete pack", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            OfflinePackState.INSTALLED -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (language == SathiLanguage.BN) "অফলাইন মোড চালু রাখুন" else "Use Offline Mode",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = viewModel.getOfflinePackStorageSummary(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = localSettings.offlineModeEnabled,
                                                onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
                                            )
                                            IconButton(onClick = { viewModel.deleteOfflinePack() }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete offline pack", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.updateOfflinePack() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("update_offline_pack_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (language == SathiLanguage.BN) "আপডেট ও নতুন তথ্য নিন (Update Pack)"
                                            else "Update & Expand Offline Pack"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customTopicInput,
                                            onValueChange = { customTopicInput = it },
                                            label = {
                                                Text(
                                                    if (language == SathiLanguage.BN) "নতুন বিষয় অফলাইনে যোগ করুন"
                                                    else "Add custom offline topic"
                                                )
                                            },
                                            placeholder = {
                                                Text(if (language == SathiLanguage.BN) "যেমন: জাপান, কম্পিউটার..." else "e.g., Japan, Physics")
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("custom_offline_topic_input")
                                        )
                                        Button(
                                            onClick = {
                                                val topic = customTopicInput.trim()
                                                if (topic.isNotEmpty()) {
                                                    customTopicInput = ""
                                                    viewModel.downloadCustomOfflineTopic(topic)
                                                }
                                            },
                                            enabled = customTopicInput.isNotBlank(),
                                            modifier = Modifier.testTag("custom_offline_topic_button")
                                        ) {
                                            Text(if (language == SathiLanguage.BN) "যোগ" else "Add")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.installOfflineBengaliTts() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (language == SathiLanguage.BN) "অফলাইন বাংলা কণ্ঠ (TTS Voice Data) ইনস্টল করুন"
                            else "Install Offline Bengali TTS Voice"
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.openOfflineSpeechRecognitionSettings() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_speech_settings_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (language == SathiLanguage.BN) "অফলাইন ভয়েস রেকগনিশন (Speech Pack) ডাউনলোড করুন"
                            else "Download Offline Voice Recognition Pack"
                        )
                    }
                }
            }
        }
        }

        // 5. Owner-Only Admin Panel Entry
        if (isOwnerAdmin && matches("admin", "অ্যাডমিন")) {
            item {
                Button(
                    onClick = onOpenAdminPanel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_admin_panel_button")
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (language == SathiLanguage.BN) "অ্যাডমিন ড্যাশবোর্ড (শুধুমাত্র মালিক)"
                        else "Owner Admin Dashboard"
                    )
                }
            }
        }

        // 6. Privacy Policy
        if (matches("privacy", "policy", "গোপনীয়তা")) {
        item {
            OutlinedButton(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_policy_button")
            ) {
                Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (language == SathiLanguage.BN) "গোপনীয়তা নীতি" else "Privacy Policy")
            }
        }
        }
    }
}
