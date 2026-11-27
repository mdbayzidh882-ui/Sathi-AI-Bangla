package com.example.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.LocalSettingsState
import com.example.model.SathiLanguage
import com.example.overlay.SathiScreenReaderService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhoneControlAndSafetyScreen(
    localSettings: LocalSettingsState,
    language: SathiLanguage,
    screenReaderActive: Boolean,
    onRequestBasicPermissions: () -> Unit,
    onSelectScrollLimit: (Int) -> Unit,
    onToggleStopOnUserTouch: (Boolean) -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onAddSosContact: (String, String) -> Unit,
    onRemoveSosContact: (String) -> Unit,
    onSaveSosMessage: (String) -> Unit,
    onToggleSosLocation: (Boolean) -> Unit,
    onTriggerSosNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var sosName by remember { mutableStateOf("") }
    var sosPhone by remember { mutableStateOf("") }
    var sosMsgInput by remember(localSettings.sosHelpMessage) {
        mutableStateOf(localSettings.sosHelpMessage)
    }

    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val overlayGranted = Settings.canDrawOverlays(context)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("phone_control_safety_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("phone_safety_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (language == SathiLanguage.BN) "পিছনে" else "Back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == SathiLanguage.BN) "ফোন কন্ট্রোল ও নিরাপত্তা" else "Phone control & safety",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "অনুমতি, স্ক্রল সীমা, অ্যাপ লক ও জরুরি SOS"
                        } else {
                            "Permissions, scroll limit, app lock & emergency SOS"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Permissions section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "অনুমতি সমূহ (Permissions)" else "Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    PermissionStatusRow(
                        label = if (language == SathiLanguage.BN) "মাইক্রোফোন (ভয়েস)" else "Microphone",
                        granted = micGranted,
                        language = language
                    )
                    PermissionStatusRow(
                        label = if (language == SathiLanguage.BN) "নোটিফিকেশন" else "Notifications",
                        granted = notifGranted,
                        language = language
                    )
                    PermissionStatusRow(
                        label = if (language == SathiLanguage.BN) "ফ্লোটিং বাবল (Overlay)" else "Display over apps",
                        granted = overlayGranted,
                        language = language
                    )
                    PermissionStatusRow(
                        label = if (language == SathiLanguage.BN) "কন্ট্যাক্টস (বন্ধু খোঁজা)" else "Contacts",
                        granted = contactsGranted,
                        language = language
                    )
                    PermissionStatusRow(
                        label = if (language == SathiLanguage.BN) "স্ক্রিন অটো-ট্যাপ (Accessibility)" else "Accessibility Control",
                        granted = screenReaderActive,
                        language = language
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestBasicPermissions,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("allow_all_basics_button")
                        ) {
                            Text(if (language == SathiLanguage.BN) "সব বেসিক চালু করুন" else "Allow all basics")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == SathiLanguage.BN) "Accessibility" else "Accessibility")
                        }
                    }
                }
            }
        }

        // Phone control & App lock
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "ফোন কন্ট্রোল ও নিরাপত্তা" else "Phone control & privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "অটো স্ক্রল সীমা (Scroll limit): একবারে কত ধাপ স্ক্রল করবে"
                        } else {
                            "Scroll limit: max steps per automated scroll"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 20).forEach { limit ->
                            FilterChip(
                                selected = localSettings.scrollLimit == limit,
                                onClick = { onSelectScrollLimit(limit) },
                                label = { Text("$limit") },
                                modifier = Modifier.testTag("scroll_limit_chip_$limit")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) "টাচ করলেই থামবে (Stops when you touch)" else "Stops when you touch",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "আপনি স্ক্রিন স্পর্শ করলে সাথীর চলমান অটো-অ্যাকশন সাথে সাথে থেমে যাবে"
                                } else {
                                    "Cancels pending automated taps/scrolls the instant you touch the screen"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localSettings.stopOnUserTouch,
                            onCheckedChange = onToggleStopOnUserTouch,
                            modifier = Modifier.testTag("stop_on_user_touch_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (language == SathiLanguage.BN) "অ্যাপ লক (App lock)" else "App lock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (language == SathiLanguage.BN) {
                                        "সাথী খোলার সময় ফোনের পিন বা বায়োমেট্রিক চাইবে"
                                    } else {
                                        "Require device PIN or biometrics when opening Sathi"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = localSettings.appLockEnabled,
                            onCheckedChange = onToggleAppLock,
                            modifier = Modifier.testTag("app_lock_switch")
                        )
                    }
                }
            }
        }

        // Emergency SOS section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "জরুরি SOS (সর্বোচ্চ ৫ জন পরিচিতি)" else "Emergency SOS (Up to 5 contacts)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    localSettings.sosContacts.forEach { contact ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { onRemoveSosContact(contact.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove contact", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (localSettings.sosContacts.size < 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sosName,
                                onValueChange = { sosName = it },
                                label = { Text(if (language == SathiLanguage.BN) "নাম" else "Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sos_name_input")
                            )
                            OutlinedTextField(
                                value = sosPhone,
                                onValueChange = { sosPhone = it },
                                label = { Text(if (language == SathiLanguage.BN) "নম্বর" else "Phone") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sos_phone_input")
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (sosPhone.isNotBlank()) {
                                    onAddSosContact(sosName, sosPhone)
                                    sosName = ""
                                    sosPhone = ""
                                }
                            },
                            enabled = sosPhone.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_sos_contact_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == SathiLanguage.BN) "SOS পরিচিতি যোগ করুন" else "Add SOS contact")
                        }
                    }

                    OutlinedTextField(
                        value = sosMsgInput,
                        onValueChange = { sosMsgInput = it },
                        label = { Text(if (language == SathiLanguage.BN) "জরুরি বার্তা (Help message)" else "Emergency help message") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sos_message_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveSosMessage(sosMsgInput) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_sos_message_button")
                        ) {
                            Text(if (language == SathiLanguage.BN) "বার্তা সেভ করুন" else "Save message")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) "লোকেশন তথ্য যুক্ত করুন" else "Include location hint",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "জরুরি বার্তায় আপনার সর্বশেষ জানা অবস্থান উল্লেখ থাকবে"
                                } else {
                                    "Appends a prompt to share your current pin location in SMS"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localSettings.sosIncludeLocation,
                            onCheckedChange = onToggleSosLocation,
                            modifier = Modifier.testTag("sos_location_switch")
                        )
                    }

                    Button(
                        onClick = onTriggerSosNow,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("send_sos_now_button")
                    ) {
                        Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == SathiLanguage.BN) "এখনই SOS পাঠান / 999 ডায়াল" else "Send SOS Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    language: SathiLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Surface(
            color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (granted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = if (granted) {
                        if (language == SathiLanguage.BN) "চালু" else "Allowed"
                    } else {
                        if (language == SathiLanguage.BN) "প্রয়োজন" else "Need"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DataAndBackupScreen(
    localSettings: LocalSettingsState,
    language: SathiLanguage,
    onExportBackupJson: () -> String,
    onImportBackupJson: (String) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onRedetectModels: () -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    BackHandler(onBack = onBack)

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreInput by remember { mutableStateOf("") }
    var modelReport by remember { mutableStateOf("") }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.Restore, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "ব্যাকআপ রিস্টোর করুন" else "Restore Backup")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "আগের এক্সপোর্ট করা Sathi AI ব্যাকআপ JSON এখানে পেস্ট করুন:"
                        } else {
                            "Paste your exported Sathi AI JSON backup below:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restoreInput,
                        onValueChange = { restoreInput = it },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_backup_json_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val fromCompose = clipboard.getText()?.text.orEmpty()
                            val fromSys = runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            }.getOrDefault("")
                            val clip = fromCompose.ifBlank { fromSys }.trim()
                            if (clip.isNotBlank()) restoreInput = clip
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == SathiLanguage.BN) "ক্লিপবোর্ড থেকে পেস্ট" else "Paste from clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportBackupJson(restoreInput)
                        showRestoreDialog = false
                    },
                    enabled = restoreInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text(if (language == SathiLanguage.BN) "রিস্টোর করুন" else "Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("data_backup_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("data_backup_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (language == SathiLanguage.BN) "পিছনে" else "Back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == SathiLanguage.BN) "ডেটা ও ব্যাকআপ" else "Data & backup",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "ব্যাকআপ, রিস্টোর ও এআই মডেল ডায়াগনস্টিকস"
                        } else {
                            "Backup, restore & AI model diagnostics"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "ব্যাকআপ ও রিস্টোর" else "Backup & restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val lastBackupText = if (localSettings.lastBackupTimeMs > 0L) {
                        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        fmt.format(Date(localSettings.lastBackupTimeMs))
                    } else {
                        if (language == SathiLanguage.BN) "এখনো করা হয়নি" else "Never"
                    }
                    Text(
                        text = if (language == SathiLanguage.BN) "সর্বশেষ ব্যাকআপ: $lastBackupText" else "Last backup: $lastBackupText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val json = onExportBackupJson()
                            clipboard.setText(AnnotatedString(json))
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Sathi AI Backup")
                                putExtra(Intent.EXTRA_TEXT, json)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "Export Sathi AI Backup")) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_backup_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == SathiLanguage.BN) "ব্যাকআপ কপি ও শেয়ার করুন" else "Export & Share Backup")
                    }

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_backup_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (language == SathiLanguage.BN) "ব্যাকআপ থেকে রিস্টোর করুন" else "Import / Restore Backup")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == SathiLanguage.BN) "অটো ব্যাকআপ (Auto backup)" else "Auto backup",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "কাজ বা সেটিংস বদলালে স্বয়ংক্রিয়ভাবে ব্যাকআপ সময় হালনাগাদ হবে"
                                } else {
                                    "Automatically snapshots settings & tasks when updated"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localSettings.autoBackupEnabled,
                            onCheckedChange = onToggleAutoBackup,
                            modifier = Modifier.testTag("auto_backup_switch")
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) "উন্নত ও এআই মডেল (Advanced)" else "Advanced",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { modelReport = onRedetectModels() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("redetect_ai_models_button")
                    ) {
                        Text(if (language == SathiLanguage.BN) "এআই মডেল পুনরায় শনাক্ত করুন" else "Re-detect AI models")
                    }
                    if (modelReport.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modelReport,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Sathi AI • v2.0 (Nova Parity Edition) • Created by Mohammad Bayzid Hasan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
