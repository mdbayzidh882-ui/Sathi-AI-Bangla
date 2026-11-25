package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SathiLocalRepository
import com.example.model.CodeType
import com.example.model.PlanTier
import com.example.overlay.SathiFloatingService
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiScreenShareService
import com.example.overlay.SathiWakeWordService
import java.security.MessageDigest

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.SathiAiService

private enum class AdminTab(val label: String) {
    LIVE("Live Monitor"),
    GOOGLE_KEY("Google চাবি"),
    CODES("Codes"),
    USERS("Users"),
    FEATURES("Features & Ads"),
    LOGS("Activity Log")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: SathiViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isOwnerAdmin by viewModel.isOwnerAdmin.collectAsStateWithLifecycle()
    val adminUnlocked by viewModel.adminUnlocked.collectAsStateWithLifecycle()
    val codes by viewModel.adminCodes.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val logs by viewModel.adminLogs.collectAsStateWithLifecycle()
    val liveMessages by viewModel.adminLiveMessages.collectAsStateWithLifecycle()
    val allSessions by viewModel.adminAllSessions.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val googleKeyCheckState by viewModel.googleKeyCheckState.collectAsStateWithLifecycle()
    val floatingRunning by SathiFloatingService.isRunning.collectAsStateWithLifecycle()
    val screenSharing by SathiScreenShareService.isSharing.collectAsStateWithLifecycle()
    val screenReaderActive by SathiScreenReaderService.isConnected.collectAsStateWithLifecycle()
    val wakeRunning by SathiWakeWordService.isRunning.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.lockAdminSession()
        onBack()
    }

    if (!isOwnerAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Access Denied") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "অনুমতি নেই • Owner Only",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "এই অ্যাডমিন ড্যাশবোর্ড শুধুমাত্র নির্মাতা মোহাম্মদ বাইজিদ হাসানের জন্য সংরক্ষিত।",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sathi Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.lockAdminSession()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!adminUnlocked) {
            AdminOwnerLockGate(
                context = context,
                onUnlocked = { viewModel.unlockAdminSession() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            var selectedTab by remember { mutableIntStateOf(0) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    AdminTab.entries.forEachIndexed { idx, tab ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(tab.label) }
                        )
                    }
                }

                when (AdminTab.entries[selectedTab]) {
                    AdminTab.LIVE -> AdminLiveMonitorTab(
                        usersCount = users.size,
                        sessionsCount = allSessions.size,
                        liveMessages = liveMessages,
                        floatingRunning = floatingRunning,
                        screenSharing = screenSharing,
                        screenReaderActive = screenReaderActive,
                        wakeRunning = wakeRunning
                    )

                    AdminTab.GOOGLE_KEY -> AdminGoogleKeyTab(
                        savedKey = localSettings.customGeminiApiKey,
                        checkState = googleKeyCheckState,
                        onSaveAndVerify = { viewModel.saveAndVerifyGoogleApiKey(it) },
                        onClear = { viewModel.saveAndVerifyGoogleApiKey("") }
                    )

                    AdminTab.CODES -> AdminCodesTab(
                        codes = codes,
                        onGenerate = { type, note -> viewModel.adminGenerateCode(type, note) },
                        onRevoke = { id -> viewModel.adminRevokeCode(id) },
                        onCopy = { code ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Sathi Code", code))
                        }
                    )

                    AdminTab.USERS -> AdminUsersTab(
                        users = users,
                        onUpdateUser = { uid, plan, banned -> viewModel.adminUpdateUser(uid, plan, banned) }
                    )

                    AdminTab.FEATURES -> AdminFeaturesTab(
                        config = globalConfig,
                        onSave = { updated -> viewModel.adminSaveGlobalConfig(updated) }
                    )

                    AdminTab.LOGS -> AdminLogsTab(logs = logs)
                }
            }
        }
    }
}

@Composable
private fun AdminOwnerLockGate(
    context: Context,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adminPrefs = remember(context) {
        context.getSharedPreferences("sathi_admin_gate", Context.MODE_PRIVATE)
    }
    var storedHash by remember { mutableStateOf(adminPrefs.getString("pass_hash", null)) }
    var ownerKeyInput by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var resetMode by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isSettingUp = storedHash == null || resetMode

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isSettingUp) "মালিকের অ্যাডমিন পাসওয়ার্ড সেটআপ" else "অ্যাডমিন লগইন (শুধুমাত্র মালিক)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSettingUp) {
                "শুধুমাত্র আসল মালিকের গোপন Owner Key দিয়ে আপনার নিজস্ব পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর) সেট করুন। কোনো কোড স্ক্রিনে দেখানো হয় না।"
            } else {
                "অ্যাডমিন ড্যাশবোর্ড খুলতে আপনার অ্যাডমিন পাসওয়ার্ড লিখুন।"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isSettingUp) {
            OutlinedTextField(
                value = ownerKeyInput,
                onValueChange = { ownerKeyInput = it },
                label = { Text("Owner Secret Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_owner_key_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(if (isSettingUp) "New Admin Password (6+ chars)" else "Admin Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_password_input")
        )

        if (isSettingUp) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPassphrase,
                onValueChange = { confirmPassphrase = it },
                label = { Text("Confirm Admin Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_confirm_password_input")
            )
        }

        if (!errorMsg.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMsg.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (isSettingUp) {
                    if (ownerKeyInput.trim().uppercase() != SathiLocalRepository.OWNER_ADMIN_KEY) {
                        errorMsg = "গোপন Owner Key সঠিক নয়। শুধুমাত্র মালিক প্রবেশ করতে পারবেন।"
                    } else if (passphrase.length < 6) {
                        errorMsg = "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"
                    } else if (passphrase != confirmPassphrase) {
                        errorMsg = "দুটি পাসওয়ার্ড মেলেনি।"
                    } else {
                        val hash = sha256Hex(passphrase)
                        adminPrefs.edit().putString("pass_hash", hash).apply()
                        storedHash = hash
                        resetMode = false
                        errorMsg = null
                        onUnlocked()
                    }
                } else {
                    if (sha256Hex(passphrase) != storedHash) {
                        errorMsg = "অ্যাডমিন পাসওয়ার্ড সঠিক নয়।"
                    } else {
                        errorMsg = null
                        onUnlocked()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admin_unlock_button")
        ) {
            Text(if (isSettingUp) "সেভ করে অ্যাডমিন প্যানেল খুলুন" else "আনলক করুন")
        }

        if (storedHash != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                resetMode = !resetMode
                errorMsg = null
                passphrase = ""
                confirmPassphrase = ""
                ownerKeyInput = ""
            }) {
                Text(if (resetMode) "আগের পাসওয়ার্ড দিয়ে লগইন" else "পাসওয়ার্ড পরিবর্তন / রিসেট করুন")
            }
        }
    }
}

@Composable
private fun AdminCodesTab(
    codes: List<com.example.model.PremiumCode>,
    onGenerate: (CodeType, String) -> Unit,
    onRevoke: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf(CodeType.ONE_MONTH) }
    var note by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Generate Activation Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CodeType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.labelEn) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Optional Note (e.g. Friend Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onGenerate(selectedType, note)
                            note = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Random Code")
                    }
                }
            }
        }

        items(codes, key = { it.id }) { code ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(code.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${CodeType.fromCode(code.codeType).labelEn} • Status: ${code.status.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (code.note.isNotBlank()) {
                            Text(code.note, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = { onCopy(code.id) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                    }
                    if (code.status == "active") {
                        OutlinedButton(
                            onClick = { onRevoke(code.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Revoke")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<com.example.model.UserProfile>,
    onUpdateUser: (String, PlanTier, Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users, key = { it.id }) { user ->
            val currentPlan = PlanTier.fromCode(user.plan)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.userName.ifBlank { "User" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${user.email.ifBlank { user.userId }} • Plan: ${currentPlan.labelEn}" +
                            if (user.banned) " • BANNED" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (user.banned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val nextPlan = if (currentPlan == PlanTier.FREE) PlanTier.PREMIUM else PlanTier.FREE
                            onUpdateUser(user.userId, nextPlan, user.banned)
                        }) {
                            Text(if (currentPlan == PlanTier.FREE) "Grant Premium" else "Set Free")
                        }
                        OutlinedButton(
                            onClick = { onUpdateUser(user.userId, currentPlan, !user.banned) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (user.banned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                if (user.banned) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (user.banned) "Unban" else "Ban")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminFeaturesTab(
    config: com.example.model.AppConfig,
    onSave: (com.example.model.AppConfig) -> Unit
) {
    var cameraFree by remember(config) { mutableStateOf(config.cameraEnabledForFree) }
    var memoryFree by remember(config) { mutableStateOf(config.longTermMemoryForFree) }
    var translationFree by remember(config) { mutableStateOf(config.translationForFree) }
    var dayPlanFree by remember(config) { mutableStateOf(config.dayPlanningForFree) }
    var announcementActive by remember(config) { mutableStateOf(config.announcementActive) }
    var announcementText by remember(config) { mutableStateOf(config.announcementText) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Move Features Between Free & Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureToggleRow("Camera Vision for Free Users", cameraFree) { cameraFree = it }
                    FeatureToggleRow("Long-Term Memory for Free Users", memoryFree) { memoryFree = it }
                    FeatureToggleRow("Translation for Free Users", translationFree) { translationFree = it }
                    FeatureToggleRow("Day Planning for Free Users", dayPlanFree) { dayPlanFree = it }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Global Announcement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureToggleRow("Show Announcement on Home", announcementActive) { announcementActive = it }
                    OutlinedTextField(
                        value = announcementText,
                        onValueChange = { announcementText = it },
                        label = { Text("Announcement Message (Bengali / English)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSave(
                                config.copy(
                                    cameraEnabledForFree = cameraFree,
                                    longTermMemoryForFree = memoryFree,
                                    translationForFree = translationFree,
                                    dayPlanningForFree = dayPlanFree,
                                    announcementActive = announcementActive,
                                    announcementText = announcementText,
                                    bannerAdEnabled = false
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Features & Announcement")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminLiveMonitorTab(
    usersCount: Int,
    sessionsCount: Int,
    liveMessages: List<com.example.model.ChatMessage>,
    floatingRunning: Boolean,
    screenSharing: Boolean,
    screenReaderActive: Boolean,
    wakeRunning: Boolean
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "লাইভ অ্যাক্টিভিটি ও ইউজার স্ট্যাটাস",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "নিবন্ধিত ইউজার: $usersCount জন • চ্যাট সেশন: $sessionsCount টি • মোট লাইভ মেসেজ: ${liveMessages.size} টি",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "ভাসমান বাবল: ${if (floatingRunning) "চালু ✓" else "বন্ধ"} • স্ক্রিন শেয়ার: ${if (screenSharing) "চালু ✓" else "বন্ধ"} • স্ক্রিন রিডার: ${if (screenReaderActive) "চালু ✓" else "বন্ধ"} • ওয়েক-ওয়ার্ড: ${if (wakeRunning) "চালু ✓" else "বন্ধ"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "নোট: এটি সম্পূর্ণ প্রাইভেট লোকাল ডাটাবেজে চলে, তাই এই ফোনের সব চ্যাট ও সার্ভিস স্ট্যাটাস রিয়েল-টাইমে এখানে দেখা যাচ্ছে।",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            Text(
                text = "সাম্প্রতিক লাইভ কথোপকথন (Live Chat Feed)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(liveMessages, key = { it.id }) { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val senderLabel = if (msg.isUser) "👤 ইউজার" else "🤖 সাথী (${msg.mood.labelBn})"
                    Text(
                        text = senderLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AdminLogsTab(logs: List<com.example.model.ActivityLog>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs, key = { it.id }) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.target}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(log.details, style = MaterialTheme.typography.bodyMedium)
                    Text(log.actorEmail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest("sathi_salt_$input".toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun maskApiKeyPreview(key: String): String {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.length <= 8) return "••••••••"
    return "${trimmed.take(4)}••••••${trimmed.takeLast(4)}"
}

@Composable
private fun AdminGoogleKeyTab(
    savedKey: String,
    checkState: GoogleKeyCheckState,
    onSaveAndVerify: (String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current
    var keyInput by remember { mutableStateOf(savedKey) }
    var showKey by remember { mutableStateOf(true) }
    var pasteFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedKey) {
        if (savedKey.isNotBlank() && keyInput.isBlank()) {
            keyInput = savedKey
        }
    }

    val isConnected = savedKey.isNotBlank() && checkState !is GoogleKeyCheckState.Failed

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) {
                                "Google সার্চ ও AI চাবি সংযুক্ত আছে ✓"
                            } else {
                                "Google সার্চ ও AI চাবি (API Key)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (savedKey.isNotBlank()) {
                            "সংরক্ষিত চাবি: ${maskApiKeyPreview(savedKey)} — এখন সাথী সরাসরি Google Search দিয়ে উত্তর খুঁজতে পারবে এবং ছবিও বানাতে পারবে।"
                        } else {
                            "নিচে আপনার Google AI Studio-এর চাবি (API Key) কপি করে পেস্ট করুন। এতে কোনো Firebase সেটআপ ছাড়াই সাথী সরাসরি Google থেকে সার্চ করবে।"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "চাবি পেস্ট ও সংযোগ করুন",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { raw ->
                            keyInput = SathiAiService.sanitizeApiKeyInput(raw)
                            pasteFeedback = null
                        },
                        label = { Text("Google API Key (AIza...)") },
                        placeholder = { Text("এখানে চাবি পেস্ট করুন") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_google_key_input")
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
                                val rawClip = fromCompose.ifBlank { fromSystem }
                                val cleaned = SathiAiService.sanitizeApiKeyInput(rawClip)
                                if (cleaned.isBlank()) {
                                    pasteFeedback = "ক্লিপবোর্ডে কোনো লেখা কপি করা নেই।"
                                } else {
                                    keyInput = cleaned
                                    pasteFeedback = "চাবি পেস্ট হয়েছে (${maskApiKeyPreview(cleaned)}) — এখন 'সংরক্ষণ ও চালু করুন' চাপুন!"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_paste_google_key_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পেস্ট করুন")
                        }

                        Button(
                            enabled = checkState !is GoogleKeyCheckState.Verifying,
                            onClick = {
                                val cleaned = SathiAiService.sanitizeApiKeyInput(keyInput)
                                keyInput = cleaned
                                pasteFeedback = null
                                onSaveAndVerify(cleaned)
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("admin_save_google_key_button")
                        ) {
                            if (checkState is GoogleKeyCheckState.Verifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("যাচাই চলছে...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সংরক্ষণ ও চালু করুন")
                            }
                        }
                    }

                    if (savedKey.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                keyInput = ""
                                pasteFeedback = null
                                onClear()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_clear_google_key_button")
                        ) {
                            Text("সংরক্ষিত চাবি মুছুন")
                        }
                    }

                    if (!pasteFeedback.isNullOrBlank()) {
                        Text(
                            text = pasteFeedback.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    when (checkState) {
                        is GoogleKeyCheckState.Connected -> {
                            Text(
                                text = "✓ ${checkState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is GoogleKeyCheckState.Failed -> {
                            Text(
                                text = checkState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "কীভাবে Google থেকে চাবি নেবেন (সহজ ৩ ধাপ)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "১. নিচের 'চাবি তৈরি করুন' বাটনে চাপ দিয়ে ব্রাউজার খুলুন।\n" +
                            "২. আপনার Gmail দিয়ে লগইন করে 'Create API key' চেপে কোডটি Copy করুন।\n" +
                            "৩. এখানে ফিরে 'পেস্ট করুন' এবং 'সংরক্ষণ ও চালু করুন' বাটনে চাপ দিন — ব্যস, কোনো Firebase ছাড়াই Google সার্চ চালু!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("চাবি তৈরি করুন")
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Google AI Studio Key URL", "https://aistudio.google.com/apikey"))
                                pasteFeedback = "লিংক কপি হয়েছে: aistudio.google.com/apikey"
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("লিংক কপি")
                        }
                    }
                }
            }
        }
    }
}
