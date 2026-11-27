package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.comm.IncomingCommRecord
import com.example.comm.IncomingCommType
import com.example.comm.IncomingCommunicationManager
import com.example.comm.SathiNotificationListenerService
import com.example.model.SathiLanguage
import com.example.overlay.MultimediaMonitorState

@Composable
fun MultimediaMonitorBanner(
    state: MultimediaMonitorState,
    language: SathiLanguage,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = state.isMonitoring, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .testTag("multimedia_monitor_banner")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                val appTitle = if (language == SathiLanguage.BN) {
                    state.appType?.labelBn ?: state.appLabel
                } else {
                    state.appType?.labelEn ?: state.appLabel
                }
                Text(
                    text = if (language == SathiLanguage.BN) {
                        "মাল্টিমিডিয়া মনিটর সক্রিয় ($appTitle): প্রয়োজনে সরাসরি সাহায্য করতে প্রস্তুত"
                    } else {
                        "Multimedia Monitor active ($appTitle): Ready to assist seamlessly"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun IncomingSummaryAlertCard(
    briefing: IncomingCommRecord?,
    language: SathiLanguage,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = briefing != null, modifier = modifier) {
        if (briefing != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("incoming_briefing_alert_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (briefing.type == IncomingCommType.CALL) Icons.Default.Call else Icons.Default.MarkChatRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${briefing.type.labelBn} • ${briefing.appName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = IncomingCommunicationManager.formatBossVoiceBriefing(briefing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onSpeak(IncomingCommunicationManager.formatBossVoiceBriefing(briefing)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = if (language == SathiLanguage.BN) "শুনে নিন" else "Speak summary",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (language == SathiLanguage.BN) "বন্ধ করুন" else "Dismiss",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCommAssistantCard(
    autoHandleEnabled: Boolean,
    listenerConnected: Boolean,
    summaries: List<IncomingCommRecord>,
    language: SathiLanguage,
    onToggleAutoHandle: (Boolean) -> Unit,
    onClearSummaries: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accessEnabled = listenerConnected || SathiNotificationListenerService.isNotificationAccessEnabled(context)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == SathiLanguage.BN) {
                        "ইনকামিং কল ও মেসেজ সহকারী"
                    } else {
                        "Incoming Call & Message Assistant"
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
                            "অটো কল/মেসেজ গ্রহণ ও সারসংক্ষেপ"
                        } else {
                            "Auto-handle Calls & Messages"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "নতুন কল বা মেসেজে সাথী নিজে কথা বলে মূল বিষয়টি বসকে বাংলায় জানাবে"
                        } else {
                            "Receives incoming communications, engages politely, and summarizes the core inquiry for Boss"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoHandleEnabled,
                    onCheckedChange = onToggleAutoHandle,
                    modifier = Modifier.testTag("auto_incoming_comms_switch")
                )
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("open_notification_access_button")
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (language == SathiLanguage.BN) {
                        if (accessEnabled) "নোটিফিকেশন অ্যাক্সেস: চালু আছে ✓" else "নোটিফিকেশন অ্যাক্সেস চালু করুন"
                    } else {
                        if (accessEnabled) "Notification Access: Active ✓" else "Enable Notification Access"
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("incoming_comm_summaries_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "সাম্প্রতিক মূল বিষয়ের সারসংক্ষেপ (${summaries.size})"
                        } else {
                            "Recent Core Inquiry Summaries (${summaries.size})"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summaries.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSummaries,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("clear_incoming_summaries_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == SathiLanguage.BN) "মুছুন" else "Clear")
                        }
                    }
                }

                if (summaries.isEmpty()) {
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "এখনো কোনো ইনকামিং কল বা মেসেজের সারসংক্ষেপ জমা হয়নি।"
                        } else {
                            "No incoming call or message summaries recorded yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    summaries.take(5).forEachIndexed { idx, record ->
                        if (idx > 0) HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${record.type.labelBn} • ${record.sender} (${record.appName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = record.coreInquirySummary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (language == SathiLanguage.BN) {
                                    "সাথীর উত্তর: ${record.sathiDialogueReply}"
                                } else {
                                    "Sathi replied: ${record.sathiDialogueReply}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
