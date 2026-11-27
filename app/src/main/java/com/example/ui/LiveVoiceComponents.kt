package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.BitmapUtils
import com.example.core.SathiAnimationStyle
import com.example.core.SathiOrbStyle
import com.example.data.SathiAiService
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.overlay.SathiFloatingService
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiScreenShareService
import com.example.ui.theme.SathiIdleOrbRose
import com.example.ui.theme.SathiListeningOrbPink
import com.example.ui.theme.SathiSpeakingOrbTeal
import kotlin.math.PI
import kotlin.math.sin

private class CameraFacingPreviewContract : ActivityResultContract<Boolean, Bitmap?>() {
    override fun createIntent(context: Context, input: Boolean): Intent {
        val facing = if (input) 1 else 0
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING", facing)
            putExtra("android.intent.extras.LENS_FACING_FRONT", facing)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", input)
        }
    }

    @Suppress("DEPRECATION")
    override fun parseResult(resultCode: Int, intent: Intent?): Bitmap? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return intent.extras?.get("data") as? Bitmap
    }
}

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
    buttonTestTag: String? = null,
    orbStyle: SathiOrbStyle = SathiOrbStyle.ORBIT_BLUE,
    reduceMotion: Boolean = false,
    animationStyle: SathiAnimationStyle = SathiAnimationStyle.SMOOTH
) {
    val targetOrbColor = when {
        muted -> MaterialTheme.colorScheme.outline
        speaking -> SathiSpeakingOrbTeal
        listening -> SathiListeningOrbPink
        liveActive -> SathiListeningOrbPink.copy(alpha = 0.88f)
        else -> Color(orbStyle.primaryColorHex)
    }
    val orbColor by animateColorAsState(
        targetValue = targetOrbColor,
        animationSpec = tween(durationMillis = if (reduceMotion) 120 else 320),
        label = "voice_orb_color"
    )

    val isDancing = (!muted && listening) || speaking || thinking
    val infiniteTransition = rememberInfiniteTransition(label = "dancing_orb_anim")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    reduceMotion -> 4600
                    isDancing -> 950
                    else -> 2400
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "top_wave_phase"
    )

    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            reduceMotion -> 1.03f
            speaking -> 1.32f
            listening && !muted -> 1.26f
            liveActive -> 1.15f
            else -> 1.07f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reduceMotion) 2600 else if (speaking) 480 else if (listening) 560 else 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )

    val orbBounce by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            reduceMotion -> 1.0f
            isDancing -> 1.09f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 360 else if (listening) 440 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_bounce"
    )

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isDancing) 1.0f else 0.35f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isDancing) 0.95f else 0.50f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isDancing) 1.0f else 0.72f,
        animationSpec = infiniteRepeatable(tween(230, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isDancing) 0.9f else 0.50f,
        animationSpec = infiniteRepeatable(tween(310, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isDancing) 0.98f else 0.35f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bar5"
    )

    val containerSize = orbSize * 1.32f
    val topWaveHeight = (orbSize * 0.24f).coerceAtLeast(14.dp)
    val previewOrbState = when {
        thinking -> OrbPreviewState.THINKING
        speaking -> OrbPreviewState.SPEAKING
        listening && !muted -> OrbPreviewState.LISTENING
        else -> OrbPreviewState.IDLE
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberSathiInteractiveScale(pressed, animationStyle, reduceMotion)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Glowing animated ribbon/wave ALWAYS floating above the mouth/mic orb (idle & active)
        Canvas(
            modifier = Modifier
                .width(orbSize * 1.15f)
                .height(topWaveHeight)
                .testTag("orb_top_wave_animation")
        ) {
            val midY = size.height * 0.56f
            val ampFactor = if (reduceMotion) 0.14f else if (isDancing) 0.38f else if (muted) 0.10f else 0.24f
            val amp = size.height * ampFactor
            val path1 = Path()
            val path2 = Path()
            val steps = 28
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = t * size.width
                val env = sin(t * PI).toFloat()
                val y1 = midY - sin((t * 3.0 * PI + wavePhase)).toFloat() * amp * env
                val y2 = midY - sin((t * 4.0 * PI - wavePhase * 1.25f)).toFloat() * (amp * 0.68f) * env
                if (i == 0) {
                    path1.moveTo(x, y1)
                    path2.moveTo(x, y2)
                } else {
                    path1.lineTo(x, y1)
                    path2.lineTo(x, y2)
                }
            }
            drawPath(
                path = path2,
                color = Color(orbStyle.accentColorHex).copy(alpha = if (isDancing) 0.85f else 0.50f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = path1,
                color = orbColor.copy(alpha = if (isDancing) 0.95f else 0.72f),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
            val dotFactors = listOf(bar2, bar3, bar4)
            dotFactors.forEachIndexed { idx, factor ->
                val dx = size.width * (0.34f + idx * 0.16f)
                val dy = size.height * (0.28f - factor * 0.14f)
                drawCircle(
                    color = if (idx == 1) Color(0xFFFFD54F) else orbColor,
                    radius = (if (isDancing) 2.6.dp else 1.9.dp).toPx(),
                    center = Offset(dx, dy)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(containerSize)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseRadius = size.minDimension * 0.34f
                drawCircle(
                    color = orbColor.copy(alpha = if (isDancing) 0.28f else 0.15f),
                    radius = baseRadius * ringPulse * 1.24f
                )
                drawCircle(
                    color = orbColor.copy(alpha = if (isDancing) 0.56f else 0.34f),
                    radius = baseRadius * ringPulse,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            val btnModifier = Modifier
                .size(orbSize)
                .graphicsLayer {
                    scaleX = orbBounce * pressScale
                    scaleY = orbBounce * pressScale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClickLabel = "Tap to start voice conversation",
                    onClick = onClick
                )
                .let { base ->
                    if (!buttonTestTag.isNullOrBlank()) base.testTag(buttonTestTag) else base
                }

            Box(
                contentAlignment = Alignment.Center,
                modifier = btnModifier
            ) {
                SathiStyledOrbCanvas(
                    style = orbStyle,
                    orbState = previewOrbState,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize()
                )

                when {
                    thinking -> CircularProgressIndicator(
                        modifier = Modifier.size(orbSize * 0.42f),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    muted -> Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Microphone muted",
                        tint = Color.White,
                        modifier = Modifier.size(orbSize * 0.44f)
                    )
                    speaking || listening -> {
                        val heights = listOf(bar1, bar2, bar3, bar4, bar5)
                        val maxBarHeight = orbSize * 0.40f
                        val barWidth = (orbSize * 0.064f).coerceAtLeast(2.5.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((orbSize * 0.042f).coerceAtLeast(2.dp))
                        ) {
                            heights.forEach { factor ->
                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .height((maxBarHeight * factor).coerceAtLeast(6.dp))
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.92f))
                                )
                            }
                        }
                    }
                    else -> Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to start voice conversation",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(orbSize * 0.36f)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveVoiceChatScreen(
    viewModel: SathiViewModel,
    onOpenAdminPanel: () -> Unit = {},
    onMinimizeToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val googleKeyCheckState by viewModel.googleKeyCheckState.collectAsStateWithLifecycle()
    val isOwnerAdmin by viewModel.isOwnerAdmin.collectAsStateWithLifecycle()
    val screenSharing by SathiScreenShareService.isSharing.collectAsStateWithLifecycle()
    val screenReaderActive by SathiScreenReaderService.isConnected.collectAsStateWithLifecycle()
    val gameModePaused by SathiScreenReaderService.isGameModePaused.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val isOffline = viewModel.isUsingOfflineMode()
    val isOnline = viewModel.isDeviceNetworkOnline()
    val lastAssistantMsg = messages.lastOrNull { !it.isUser }
    val lastUserMsg = messages.lastOrNull { it.isUser }

    var useFrontCamera by remember { mutableStateOf(false) }
    var showQuickKeyDialog by remember { mutableStateOf(false) }
    var quickKeyInput by remember { mutableStateOf(localSettings.customGeminiApiKey) }
    var showQuickKeyText by remember { mutableStateOf(false) }
    var keyPasteNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localSettings.customGeminiApiKey) {
        if (localSettings.customGeminiApiKey.isNotBlank() && quickKeyInput.isBlank()) {
            quickKeyInput = localSettings.customGeminiApiKey
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            val msg = if (language == SathiLanguage.BN) {
                "বস, লাইভ ভয়েস চ্যাটের জন্য মাইক্রোফোন অনুমতি দিন।"
            } else {
                "Boss, please allow microphone permission for Live Voice Chat."
            }
            viewModel.speakMessage(msg, SathiMood.WARM)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        CameraFacingPreviewContract()
    ) { bmp ->
        if (bmp != null) {
            val q = if (language == SathiLanguage.BN) "এই ছবিতে কী দেখছ তা বলো" else "Describe what you see"
            viewModel.analyzeHomePhoto(BitmapUtils.ensureSoftwareBitmap(bmp), q)
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(useFrontCamera)
        }
    }

    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            SathiScreenShareService.startService(context, result.resultCode, data)
            if (Settings.canDrawOverlays(context) && !SathiFloatingService.isRunning.value) {
                toggleFloatingOverlay(context, currentlyRunning = false) {}
            }
            val msg = if (language == SathiLanguage.BN) {
                "বস, লাইভ স্ক্রিন শেয়ার চালু হয়েছে! যেকোনো অ্যাপে গিয়ে 'স্ক্রিন দেখো' বলুন বা চাপুন।"
            } else {
                "Boss, Live Screen Share is active! Switch to any app and tap Inspect Screen."
            }
            viewModel.speakMessage(msg, SathiMood.HAPPY)
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

    if (showQuickKeyDialog) {
        AlertDialog(
            onDismissRequest = { showQuickKeyDialog = false },
            icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "Google API Key ও অ্যাডমিন" else "Google API Key & Admin")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "বস, আপনার Google AI চাবি পেস্ট করে যাচাই করলে সাথী সরাসরি জেমিনির মতো সম্পূর্ণ শক্তি পাবে।"
                        } else {
                            "Boss, paste your Google AI API Key to unlock full Gemini voice, vision & search."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = quickKeyInput,
                        onValueChange = {
                            quickKeyInput = SathiAiService.sanitizeApiKeyInput(it)
                            keyPasteNotice = null
                        },
                        label = { Text("Google API Key (AIza...)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false
                        ),
                        visualTransformation = if (showQuickKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showQuickKeyText = !showQuickKeyText }) {
                                Icon(
                                    imageVector = if (showQuickKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showQuickKeyText) "Hide key" else "Show key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val fromCompose = composeClipboard.getText()?.text.orEmpty()
                                val fromSys = runCatching {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                }.getOrDefault("")
                                val cleaned = SathiAiService.sanitizeApiKeyInput(fromCompose.ifBlank { fromSys })
                                if (cleaned.isNotBlank()) {
                                    quickKeyInput = cleaned
                                    keyPasteNotice = if (language == SathiLanguage.BN) "চাবি পেস্ট হয়েছে ✓" else "Key pasted ✓"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == SathiLanguage.BN) "পেস্ট" else "Paste")
                        }
                        if (isOwnerAdmin) {
                            OutlinedButton(
                                onClick = {
                                    showQuickKeyDialog = false
                                    onOpenAdminPanel()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == SathiLanguage.BN) "অ্যাডমিন" else "Admin")
                            }
                        }
                    }
                    if (!keyPasteNotice.isNullOrBlank()) {
                        Text(keyPasteNotice.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    when (val ks = googleKeyCheckState) {
                        is GoogleKeyCheckState.Connected -> Text("✓ ${ks.message}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        is GoogleKeyCheckState.Failed -> Text(ks.message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveAndVerifyGoogleApiKey(quickKeyInput)
                    showQuickKeyDialog = false
                }) {
                    Text(if (language == SathiLanguage.BN) "সংরক্ষণ ও চালু" else "Save & Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickKeyDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বন্ধ" else "Close")
                }
            }
        )
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
                        text = if (language == SathiLanguage.BN) "Sathi AI • লাইভ ভয়েস ও ক্যামেরা" else "Sathi AI • Live",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "সবসময় চলমান ওয়েভ ও অটো ভয়েস লুপ (বস মোড)"
                        } else {
                            "Continuous live voice, camera & screen assistant"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { showQuickKeyDialog = true },
                        color = if (localSettings.googleKeyVerified || localSettings.customGeminiApiKey.isNotBlank()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("live_admin_key_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Google Key and Admin", modifier = Modifier.size(16.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) "চাবি" else "Key",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
                                    if (language == SathiLanguage.BN) "অফলাইন" else "Offline"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (gameModePaused) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null)
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                "গেম মোড শনাক্ত: খেলার সময় ভাসমান বাবল অটো-পজ রয়েছে"
                            } else {
                                "Game Mode detected: Floating orb auto-paused while gaming"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEA0B101E)),
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00E5FF), Color(0xFFFFD54F), Color(0xFFE91E63))
                    )
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF0A1124), Color(0xFF131D3B))
                            )
                        )
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
                        orbSize = 140.dp,
                        buttonTestTag = "live_screen_orb_button",
                        orbStyle = localSettings.orbStyle,
                        reduceMotion = localSettings.reduceMotion || localSettings.batterySaver,
                        animationStyle = localSettings.animationStyle
                    )

                    val statusLabel = when {
                        isThinking -> if (language == SathiLanguage.BN) "Thinking • ভাবছি..." else "Thinking"
                        voiceState.speaking -> if (language == SathiLanguage.BN) "Speaking • সাথী বলছে" else "Speaking"
                        voiceState.muted -> if (language == SathiLanguage.BN) "Muted • মিউট" else "Muted"
                        voiceState.listening -> if (language == SathiLanguage.BN) "Listening • শুনছি বস" else "Listening"
                        else -> if (language == SathiLanguage.BN) "Live Voice Ready • অরবে ট্যাপ করুন" else "Tap orb to talk"
                    }

                    Surface(
                        color = when {
                            voiceState.speaking -> Color(0x332DD4BF)
                            voiceState.listening -> Color(0x33F472B6)
                            isThinking -> Color(0x33FBBF24)
                            else -> Color(0x2838BDF8)
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0x44FFFFFF))
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                voiceState.speaking -> SathiSpeakingOrbTeal
                                voiceState.listening -> SathiListeningOrbPink
                                else -> Color(0xFFE3F2FD)
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Nova-style Floating Dark-Glass Transcript Card (YOU & SATHI live lines)
                    Surface(
                        color = Color(0x880B132B),
                        border = BorderStroke(1.dp, Color(0x3338BDF8)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_transcript_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val youText = voiceState.partialTranscript.ifBlank {
                                lastUserMsg?.text.orEmpty()
                            }.ifBlank {
                                if (language == SathiLanguage.BN) "আপনার কথা এখানে লাইভ দেখা যাবে..." else "Speak naturally — your words appear here..."
                            }
                            Text(
                                text = if (language == SathiLanguage.BN) "আপনি (YOU)" else "YOU",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7DD3FC)
                            )
                            Text(
                                text = youText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Surface(
                                color = Color(0x22FFFFFF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                            ) {}

                            val sathiText = when {
                                isThinking -> if (language == SathiLanguage.BN) "বস, তোমার জন্য উত্তর সাজাচ্ছি..." else "Thinking, Boss..."
                                lastAssistantMsg != null -> lastAssistantMsg.text
                                else -> if (language == SathiLanguage.BN) "বলো বস, আমি শুনছি!" else "Speak Boss, I'm listening!"
                            }
                            Text(
                                text = if (language == SathiLanguage.BN) "সাথী (SATHI)" else "SATHI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SathiSpeakingOrbTeal
                            )
                            Text(
                                text = sathiText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF1F5F9),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bottom 3-button Live Controls: Mic Toggle, Minimize ↓, Red Close ✕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                if (voiceState.liveSessionActive) {
                                    viewModel.toggleLiveMute()
                                } else {
                                    handleOrbTap()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (voiceState.muted) Color(0xFF334155) else Color(0xFF0EA5E9),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("live_mic_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState.muted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (voiceState.muted) "Unmute microphone" else "Mute or start microphone"
                            )
                        }

                        FilledIconButton(
                            onClick = onMinimizeToHome,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("live_minimize_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (language == SathiLanguage.BN) "হোমে ফিরে যান" else "Minimize to Home"
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (voiceState.speaking) {
                                    viewModel.interruptSpeakingAndListen()
                                } else {
                                    viewModel.stopLiveVoiceSession()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("live_close_button")
                        ) {
                            Icon(
                                imageVector = if (voiceState.speaking) Icons.Default.Stop else Icons.Default.Close,
                                contentDescription = if (language == SathiLanguage.BN) "লাইভ বন্ধ করুন" else "Close live session"
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
                    }

                    // Live Camera (Front/Back) & Live Screen Share controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("live_camera_switch_button")
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch front or back camera", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (language == SathiLanguage.BN) {
                                    if (useFrontCamera) "ফ্রন্ট" else "ব্যাক"
                                } else {
                                    if (useFrontCamera) "Front" else "Back"
                                },
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(useFrontCamera)
                                else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("live_camera_capture_button")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == SathiLanguage.BN) "ক্যামেরা দেখান" else "Live Camera", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                if (screenSharing) {
                                    SathiScreenShareService.stopService(context)
                                } else {
                                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    if (mgr != null) {
                                        screenShareLauncher.launch(mgr.createScreenCaptureIntent())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (screenSharing) MaterialTheme.colorScheme.error else Color(0xFF00897B)
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("live_screen_share_button")
                        ) {
                            Icon(
                                imageVector = if (screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (language == SathiLanguage.BN) {
                                    if (screenSharing) "শেয়ার বন্ধ" else "স্ক্রিন শেয়ার"
                                } else {
                                    if (screenSharing) "Stop Share" else "Screen Share"
                                },
                                maxLines = 1
                            )
                        }
                    }

                    if (screenSharing || screenReaderActive) {
                        OutlinedButton(
                            onClick = { viewModel.analyzeSharedScreen("") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF80DEEA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("live_inspect_screen_button")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (language == SathiLanguage.BN) "এখন স্ক্রিন দেখো ও বলো" else "Inspect Current Screen Now"
                            )
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
                                text = if (language == SathiLanguage.BN) "Sathi AI-এর সর্বশেষ উত্তর" else "Latest from Sathi AI",
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
