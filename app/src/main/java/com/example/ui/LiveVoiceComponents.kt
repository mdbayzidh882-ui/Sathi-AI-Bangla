package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.ui.theme.SathiIdleOrbRose
import com.example.ui.theme.SathiListeningOrbPink
import com.example.ui.theme.SathiSpeakingOrbTeal

@Composable
fun DancingVoiceOrb(
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    muted: Boolean,
    liveActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 64.dp,
    buttonTestTag: String? = null
) {
    val targetOrbColor = when {
        muted -> MaterialTheme.colorScheme.outline
        speaking -> SathiSpeakingOrbTeal
        listening -> SathiListeningOrbPink
        liveActive -> SathiListeningOrbPink.copy(alpha = 0.88f)
        else -> SathiIdleOrbRose
    }
    val orbColor by animateColorAsState(
        targetValue = targetOrbColor,
        animationSpec = tween(durationMillis = 320),
        label = "voice_orb_color"
    )

    val isDancing = (!muted && listening) || speaking || thinking
    val infiniteTransition = rememberInfiniteTransition(label = "dancing_orb_anim")

    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            speaking -> 1.32f
            listening && !muted -> 1.26f
            liveActive -> 1.15f
            else -> 1.06f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 480 else if (listening) 560 else 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )

    val orbBounce by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isDancing) 1.09f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 360 else 440,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_bounce"
    )

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isDancing) 1.0f else 0.3f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isDancing) 0.95f else 0.45f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isDancing) 1.0f else 0.65f,
        animationSpec = infiniteRepeatable(tween(230, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isDancing) 0.9f else 0.45f,
        animationSpec = infiniteRepeatable(tween(310, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isDancing) 0.98f else 0.3f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar5"
    )

    val containerSize = orbSize * 1.38f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(containerSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension * 0.34f
            drawCircle(
                color = orbColor.copy(alpha = if (isDancing) 0.26f else 0.14f),
                radius = baseRadius * ringPulse * 1.24f
            )
            drawCircle(
                color = orbColor.copy(alpha = if (isDancing) 0.52f else 0.30f),
                radius = baseRadius * ringPulse,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        val btnModifier = Modifier
            .size(orbSize)
            .graphicsLayer {
                scaleX = orbBounce
                scaleY = orbBounce
            }
            .let { base ->
                if (!buttonTestTag.isNullOrBlank()) base.testTag(buttonTestTag) else base
            }

        FilledIconButton(
            onClick = onClick,
            modifier = btnModifier,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = orbColor,
                contentColor = Color.White
            )
        ) {
            when {
                thinking -> CircularProgressIndicator(
                    modifier = Modifier.size(orbSize * 0.42f),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                muted -> Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "Microphone muted",
                    modifier = Modifier.size(orbSize * 0.46f)
                )
                speaking || listening -> {
                    val heights = listOf(bar1, bar2, bar3, bar4, bar5)
                    val maxBarHeight = orbSize * 0.44f
                    val barWidth = (orbSize * 0.068f).coerceAtLeast(3.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((orbSize * 0.045f).coerceAtLeast(2.5.dp))
                    ) {
                        heights.forEach { factor ->
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height((maxBarHeight * factor).coerceAtLeast(6.dp))
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                            )
                        }
                    }
                }
                else -> Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to start voice conversation",
                    modifier = Modifier.size(orbSize * 0.48f)
                )
            }
        }
    }
}

@Composable
fun LiveVoiceChatScreen(
    viewModel: SathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val isOffline = viewModel.isUsingOfflineMode()
    val isOnline = viewModel.isDeviceNetworkOnline()
    val lastAssistantMsg = messages.lastOrNull { !it.isUser }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            val msg = if (language == SathiLanguage.BN) {
                "লাইভ ভয়েস চ্যাটের জন্য মাইক্রোফোন অনুমতি দিন।"
            } else {
                "Please allow microphone permission for Live Voice Chat."
            }
            viewModel.speakMessage(msg, SathiMood.WARM)
        }
    }

    val handleOrbTap = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val liveQuickPrompts = if (language == SathiLanguage.BN) {
        listOf(
            "তোমার নাম কী?",
            "তোমাকে কে বানিয়েছে?",
            "আজকের খবর বলো",
            "তুমি কি গুগল সার্চ করতে পারো?",
            "এখন কয়টা বাজে?",
            "একটি ইসলামিক গল্প শোনাও"
        )
    } else {
        listOf(
            "What is your name?",
            "Who created you?",
            "Tell me today's news",
            "Can you search Google?",
            "What time is it?",
            "Tell me a short story"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("live_voice_chat_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == SathiLanguage.BN) "সাথী এআই • লাইভ ভয়েস চ্যাট" else "Sathi AI • Live Voice Chat",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "কথা বলার সময় গোলাপি ও সাথী বলার সময় টিল (Teal) রঙে নাচবে"
                        } else {
                            "Dances in pink while listening & glowing teal when Sathi speaks"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (isOnline && !isOffline) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline && !isOffline) Icons.Default.Wifi else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isOnline && !isOffline) {
                                if (language == SathiLanguage.BN) "অনলাইন" else "Online"
                            } else {
                                if (language == SathiLanguage.BN) "অফলাইন ভয়েস" else "Offline Voice"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DancingVoiceOrb(
                        listening = voiceState.listening,
                        speaking = voiceState.speaking,
                        thinking = isThinking,
                        muted = voiceState.muted,
                        liveActive = voiceState.liveSessionActive,
                        onClick = handleOrbTap,
                        orbSize = 112.dp,
                        buttonTestTag = "live_screen_orb_button"
                    )

                    val orbStatusText = when {
                        isThinking -> if (language == SathiLanguage.BN) "সাথী উত্তর তৈরি করছে..." else "Sathi is thinking..."
                        voiceState.speaking -> if (language == SathiLanguage.BN) "সাথী এআই কথা বলছে..." else "Sathi AI is speaking..."
                        voiceState.muted -> if (language == SathiLanguage.BN) "মাইক মিউট করা আছে" else "Mic is muted"
                        voiceState.listening -> if (language == SathiLanguage.BN) "আমি মন দিয়ে শুনছি, বলুন..." else "Listening closely, speak now..."
                        else -> if (language == SathiLanguage.BN) "কথা বলতে মাঝের গোল বাটনে চাপ দিন" else "Tap the round orb to talk live"
                    }

                    Text(
                        text = orbStatusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            voiceState.speaking -> SathiSpeakingOrbTeal
                            voiceState.listening -> SathiListeningOrbPink
                            else -> MaterialTheme.colorScheme.primary
                        },
                        textAlign = TextAlign.Center
                    )

                    if (voiceState.partialTranscript.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎙️ ${voiceState.partialTranscript}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = localSettings.autoVoiceEnabled,
                            onClick = { viewModel.setAutoVoiceEnabled(!localSettings.autoVoiceEnabled) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            leadingIcon = {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(
                                    if (language == SathiLanguage.BN) {
                                        if (localSettings.autoVoiceEnabled) "অটো ON" else "অটো OFF"
                                    } else {
                                        if (localSettings.autoVoiceEnabled) "Auto ON" else "Auto OFF"
                                    }
                                )
                            }
                        )

                        OutlinedButton(
                            onClick = { viewModel.toggleLiveMute() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (voiceState.muted) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (language == SathiLanguage.BN) {
                                    if (voiceState.muted) "আনমিউট" else "মিউট"
                                } else {
                                    if (voiceState.muted) "Unmute" else "Mute"
                                }
                            )
                        }

                        if (voiceState.speaking) {
                            Button(
                                onClick = { viewModel.interruptSpeakingAndListen() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == SathiLanguage.BN) "থামো" else "Stop")
                            }
                        } else if (voiceState.liveSessionActive) {
                            FilledIconButton(
                                onClick = { viewModel.stopLiveVoiceSession() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "End live voice session")
                            }
                        }
                    }
                }
            }
        }

        if (lastAssistantMsg != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == SathiLanguage.BN) "সাথী এআই-এর সর্বশেষ উত্তর" else "Latest from Sathi AI",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { viewModel.speakMessage(lastAssistantMsg.text, lastAssistantMsg.mood) }
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Replay voice",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = lastAssistantMsg.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (language == SathiLanguage.BN) {
                        "অফলাইন ও দ্রুত জিজ্ঞাসার তালিকা (ট্যাপ করলেই উত্তর বলবে):"
                    } else {
                        "Offline & Quick Voice Questions:"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(liveQuickPrompts) { prompt ->
                        AssistChip(
                            onClick = { viewModel.sendUserMessage(prompt, speakReply = true) },
                            label = { Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
    }
}
