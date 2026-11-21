package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.BitmapUtils
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.UserEmotion
import com.example.overlay.SathiFloatingService
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiScreenShareService
import com.example.overlay.SathiWakeWordService
import com.example.ui.theme.MoodComfortGlow
import com.example.ui.theme.MoodHappyGlow
import com.example.ui.theme.MoodSeriousGlow
import com.example.ui.theme.MoodWarmGlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVoiceScreen(
    viewModel: SathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val streamingMessageId by viewModel.streamingMessageId.collectAsStateWithLifecycle()
    val streamingCharLimit by viewModel.streamingCharLimit.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val activeMood by viewModel.activeMood.collectAsStateWithLifecycle()
    val selectedEmotion by viewModel.selectedUserEmotion.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val banner by viewModel.bannerState.collectAsStateWithLifecycle()
    val morningGreeting by viewModel.morningGreeting.collectAsStateWithLifecycle()
    val floatingRunning by SathiFloatingService.isRunning.collectAsStateWithLifecycle()
    val screenSharing by SathiScreenShareService.isSharing.collectAsStateWithLifecycle()
    val wakeRunning by SathiWakeWordService.isRunning.collectAsStateWithLifecycle()
    val screenReaderActive by SathiScreenReaderService.isConnected.collectAsStateWithLifecycle()

    val language = SathiLanguage.fromCode(profile?.preferredLanguage)
    val isOffline = viewModel.isUsingOfflineMode()
    var typedInput by remember { mutableStateOf("") }
    var showMicRationale by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val screenShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            SathiScreenShareService.startService(context, result.resultCode, data)
            if (Settings.canDrawOverlays(context) && !floatingRunning) {
                toggleFloatingOverlay(context, currentlyRunning = false) {}
            }
            val msg = if (language == SathiLanguage.BN) {
                "স্ক্রিন শেয়ার চালু হয়েছে! এখন অন্য অ্যাপে গিয়ে বাবলের 'স্ক্রিন দেখো' চাপুন অথবা এখানে 'স্ক্রিন দেখো' চাপুন।"
            } else {
                "Screen Share is now active! Switch to any app and tap 'Look at Screen' in the floating bubble or right here."
            }
            viewModel.speakMessage(msg, SathiMood.HAPPY)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            val msg = if (language == SathiLanguage.BN) {
                "মাইক্রোফোন অনুমতি না দিলে কথা শোনা যাবে না। আপনি চাইলে নিচে লিখেও কথা বলতে পারেন।"
            } else {
                "Microphone permission was denied. You can still chat by typing below."
            }
            viewModel.speakMessage(msg, SathiMood.WARM)
        }
    }

    val wakeMicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setWakeWordEnabled(true)
            SathiWakeWordService.startService(context)
        }
    }

    val homeCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            val q = typedInput.trim()
            typedInput = ""
            viewModel.analyzeHomePhoto(BitmapUtils.ensureSoftwareBitmap(bmp), q)
        }
    }

    val homeCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            homeCameraLauncher.launch(null)
        }
    }

    val homePhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = BitmapUtils.decodeUriToSoftwareBitmap(context, uri)
            if (bmp != null) {
                val q = typedInput.trim()
                typedInput = ""
                viewModel.analyzeHomePhoto(bmp, q)
            } else {
                val errMsg = if (language == SathiLanguage.BN) {
                    "দুঃখিত, এই ছবিটি ফোনের গ্যালারি থেকে পড়া যায়নি।"
                } else {
                    "Sorry, could not decode that photo from gallery."
                }
                viewModel.speakMessage(errMsg, SathiMood.WARM)
            }
        }
    }

    val homeVideoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val q = typedInput.trim()
            typedInput = ""
            viewModel.analyzeHomeVideo(context, uri, q)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val handleMicTap = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onMicButtonTapped()
        } else {
            showMicRationale = true
        }
    }

    if (showMediaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMediaSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (language == SathiLanguage.BN) "ছবি বা ভিডিও বিশ্লেষণ করুন" else "Analyze Photo or Video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                ListItem(
                    headlineContent = {
                        Text(if (language == SathiLanguage.BN) "ছবি তুলুন (ক্যামেরা)" else "Take Photo (Camera)")
                    },
                    supportingContent = {
                        Text(if (language == SathiLanguage.BN) "লাইভ ছবি তুলে সাথীকে দেখান" else "Capture a photo for Sathi to inspect")
                    },
                    leadingContent = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMediaSheet = false
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) homeCameraLauncher.launch(null)
                            else homeCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                )
                ListItem(
                    headlineContent = {
                        Text(if (language == SathiLanguage.BN) "ছবি আপলোড করুন (গ্যালারি)" else "Upload Photo (Gallery)")
                    },
                    supportingContent = {
                        Text(if (language == SathiLanguage.BN) "ফোনের যেকোনো ছবি বিশ্লেষণ করুন" else "Pick an image from your gallery")
                    },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMediaSheet = false
                            homePhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                )
                ListItem(
                    headlineContent = {
                        Text(if (language == SathiLanguage.BN) "ভিডিও আপলোড করুন" else "Upload Video")
                    },
                    supportingContent = {
                        Text(if (language == SathiLanguage.BN) "ভিডিওর দৃশ্য, সময়, আলো ও রঙ বিশ্লেষণ করুন" else "Inspect scenes, brightness, duration & frames")
                    },
                    leadingContent = {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMediaSheet = false
                            homeVideoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (screenSharing) "স্ক্রিন শেয়ার বন্ধ করুন" else "ফোন স্ক্রিন শেয়ার করুন (Live)"
                            } else {
                                if (screenSharing) "Stop Screen Share" else "Share Phone Screen (Live)"
                            }
                        )
                    },
                    supportingContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                "সাথীকে আপনার ফোনের স্ক্রিন দেখান যেন যেকোনো কিছু পড়ে ও বুঝিয়ে দিতে পারে"
                            } else {
                                "Let Sathi see your screen to read text and explain what is shown"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (screenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                            contentDescription = null,
                            tint = if (screenSharing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("share_screen_option")
                        .clickable {
                            showMediaSheet = false
                            if (screenSharing) {
                                SathiScreenShareService.stopService(context)
                            } else {
                                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                if (mgr != null) {
                                    screenShareLauncher.launch(mgr.createScreenCaptureIntent())
                                }
                            }
                        }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (screenReaderActive) "স্ক্রিন ডাটা রিডার: চালু আছে ✓" else "সম্পূর্ণ স্ক্রিন ডাটা রিডার চালু করুন"
                            } else {
                                if (screenReaderActive) "Screen Data Reader: Active ✓" else "Enable Full Screen Data Reader"
                            }
                        )
                    },
                    supportingContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                "অন্য অ্যাপের লেখা ও নাম সরাসরি পড়ে সাথীকে জানাতে সাহায্য করে"
                            } else {
                                "Reads active app name and visible text cleanly during screen sharing"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMediaSheet = false
                            showAccessibilityDisclosure = true
                        }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (wakeRunning) "ওয়েক-ওয়ার্ড (\"সাথী সাথী সাথী\"): চালু ✓" else "\"সাথী সাথী সাথী\" ওয়েক-ওয়ার্ড চালু করুন"
                            } else {
                                if (wakeRunning) "Wake Word (\"Sathi x3\"): Active ✓" else "Enable \"Sathi Sathi Sathi\" Wake Word"
                            }
                        )
                    },
                    supportingContent = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                "অফলাইন বা অনলাইনে তিনবার সাথী ডাকলেই কথা শোনা শুরু করবে"
                            } else {
                                "Hands-free offline & online activation by saying Sathi three times"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showMediaSheet = false
                            if (wakeRunning) {
                                viewModel.setWakeWordEnabled(false)
                                SathiWakeWordService.stopService(context)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.setWakeWordEnabled(true)
                                    SathiWakeWordService.startService(context)
                                } else {
                                    wakeMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
            title = {
                Text(if (language == SathiLanguage.BN) "স্ক্রিন ডাটা রিডার (Accessibility)" else "Screen Data Reader Disclosure")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "আপনি যখন স্ক্রিন শেয়ার বা 'স্ক্রিন দেখো' ব্যবহার করবেন, তখন সাথী চলমান অ্যাপ ও স্ক্রিনের লেখা স্পষ্টভাবে পড়ার জন্য Android Accessibility সেবা ব্যবহার করে। পাসওয়ার্ড বা ব্যক্তিগত তথ্য কখনো সংরক্ষণ করা হয় না।"
                    } else {
                        "When you ask Sathi to read your screen, Sathi uses Android Accessibility only to read the active app title and visible on-screen text. Password fields and private data are excluded and never stored remotely."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showAccessibilityDisclosure = false
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }) {
                    Text(if (language == SathiLanguage.BN) "সেটিংস খুলুন" else "Open Accessibility")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
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
                Text(if (language == SathiLanguage.BN) "ভাসমান সাথী বাবল অনুমতি" else "Floating Overlay Permission")
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "অ্যাপ থেকে বের হয়ে গেলেও স্ক্রিনের পাশে সাথীর গোল বাবল রাখতে 'Display over other apps' অনুমতি চালু করুন।"
                    } else {
                        "Enable 'Display over other apps' so Sathi's floating Gemini-style orb stays ready on your screen."
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
                    Text(if (language == SathiLanguage.BN) "সেটিংসে যান" else "Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            icon = { Icon(Icons.Default.Mic, contentDescription = null) },
            title = {
                Text(
                    if (language == SathiLanguage.BN) "মাইক্রোফোন অনুমতি প্রয়োজন"
                    else "Microphone Permission Needed"
                )
            },
            text = {
                Text(
                    if (language == SathiLanguage.BN) {
                        "সাথী আপনার কথা শুনে লাইভ বাংলা ও ইংরেজিতে উত্তর দেওয়ার জন্য মাইক্রোফোন ব্যবহার করে।"
                    } else {
                        "Sathi needs microphone access to hear your voice and reply live in Bengali or English."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showMicRationale = false
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text(if (language == SathiLanguage.BN) "অনুমতি দিন" else "Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text(if (language == SathiLanguage.BN) "পরে দেব" else "Not Now")
                }
            }
        )
    }

    pendingConfirmation?.let { confirm ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            icon = {
                Icon(
                    imageVector = when (confirm.type) {
                        PhoneActionType.CALL_CONFIRM -> Icons.Default.Phone
                        PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> Icons.AutoMirrored.Filled.Send
                        else -> Icons.Default.Sms
                    },
                    contentDescription = null
                )
            },
            title = {
                Text(
                    if (language == SathiLanguage.BN) {
                        when (confirm.type) {
                            PhoneActionType.CALL_CONFIRM -> "কল করার অনুমতি দিন"
                            PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> "${confirm.target}-এ লেখা পাঠানোর অনুমতি"
                            else -> "মেসেজ পাঠানোর অনুমতি দিন"
                        }
                    } else {
                        when (confirm.type) {
                            PhoneActionType.CALL_CONFIRM -> "Confirm Phone Call"
                            PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> "Confirm ${confirm.target} Message"
                            else -> "Confirm Message"
                        }
                    }
                )
            },
            text = {
                val targetLabel = confirm.target.ifBlank {
                    if (language == SathiLanguage.BN) "(ফোন ডায়ালার)" else "(Phone Dialer)"
                }
                Text(
                    if (language == SathiLanguage.BN) {
                        when (confirm.type) {
                            PhoneActionType.SOCIAL_MESSAGE_CONFIRM ->
                                "অ্যাপ/প্ল্যাটফর্ম: $targetLabel\nপ্রস্তুত বার্তা/কমেন্ট:\n\"${confirm.messageBody}\"\n\nনিশ্চিত করলে $targetLabel খুলে যাবে ও লেখাটি কপি থাকবে।"
                            else ->
                                "নম্বর/প্রাপক: $targetLabel" +
                                    if (confirm.messageBody.isNotBlank()) "\nবার্তা: ${confirm.messageBody}" else ""
                        }
                    } else {
                        when (confirm.type) {
                            PhoneActionType.SOCIAL_MESSAGE_CONFIRM ->
                                "Platform: $targetLabel\nPrepared text:\n\"${confirm.messageBody}\""
                            else ->
                                "Recipient: $targetLabel" +
                                    if (confirm.messageBody.isNotBlank()) "\nMessage: ${confirm.messageBody}" else ""
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    modifier = Modifier.testTag("confirm_action_button")
                ) {
                    Text(if (language == SathiLanguage.BN) "নিশ্চিত করুন" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingAction() }) {
                    Text(if (language == SathiLanguage.BN) "বাতিল" else "Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SathiHistoryDrawerSheet(
                sessions = chatSessions,
                activeSessionId = activeSessionId,
                language = language,
                floatingRunning = floatingRunning,
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { session ->
                    viewModel.selectChatSession(session.id)
                    scope.launch { drawerState.close() }
                },
                onTogglePin = { session -> viewModel.togglePinChatSession(session) },
                onDeleteSession = { session -> viewModel.deleteChatSession(session) },
                onToggleFloating = {
                    scope.launch { drawerState.close() }
                    toggleFloatingOverlay(
                        context = context,
                        currentlyRunning = floatingRunning,
                        onNeedPermission = { showOverlayDialog = true }
                    )
                }
            )
        }
    ) {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Gemini-Style Header Bar: [≡ History]  Sathi Avatar & Title  [✏️ New Chat] [Float] [বাংলা/EN]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = if (language == SathiLanguage.BN) "কথোপকথন তালিকা খুলুন" else "Open conversation history"
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.img_sathi_companion),
                        contentDescription = "Sathi Avatar",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (language == SathiLanguage.BN) "সাথী • Sathi" else "Sathi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val plan = profile?.effectivePlan() ?: PlanTier.FREE
                        Text(
                            text = if (language == SathiLanguage.BN) plan.labelBn else plan.labelEn,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline Mode",
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .size(16.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.startNewChat() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = if (language == SathiLanguage.BN) "নতুন কথোপকথন" else "New chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            toggleFloatingOverlay(
                                context = context,
                                currentlyRunning = floatingRunning,
                                onNeedPermission = { showOverlayDialog = true }
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BubbleChart,
                            contentDescription = if (language == SathiLanguage.BN) "ভাসমান বাবল" else "Floating bubble",
                            tint = if (floatingRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AssistChip(
                        onClick = {
                            val next = if (language == SathiLanguage.BN) SathiLanguage.EN else SathiLanguage.BN
                            viewModel.updateLanguageAndVoice(next, viewModel.currentVoice())
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Language",
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(if (language == SathiLanguage.BN) "বাংলা" else "EN") }
                    )
                }
            }

        // Announcement Banner from Admin Panel
        AnimatedVisibility(visible = globalConfig.announcementActive && globalConfig.announcementText.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = "Announcement",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = globalConfig.announcementText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Error or Info Banner
        val activeError = voiceState.speechErrorMessage ?: (banner as? UiBannerState.Error)?.message
        val activeInfo = (banner as? UiBannerState.Info)?.message
        AnimatedVisibility(visible = !activeError.isNullOrBlank() || !activeInfo.isNullOrBlank()) {
            val isErr = !activeError.isNullOrBlank()
            Surface(
                color = if (isErr) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { viewModel.clearBanner() }
            ) {
                Text(
                    text = activeError ?: activeInfo.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Conversation List with Centered AI Logo in the Yellow-Blue middle zone
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CenterSathiAiLogo(
                isProminent = messages.size <= 1,
                isActive = voiceState.listening || voiceState.speaking || isThinking,
                language = language,
                availableHeight = maxHeight,
                modifier = Modifier.align(Alignment.Center)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (morningGreeting.isNotBlank()) {
                    item(key = "morning_greeting") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = morningGreeting,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val displayedText = if (msg.id == streamingMessageId && streamingCharLimit < msg.text.length) {
                        msg.text.take(streamingCharLimit)
                    } else {
                        msg.text
                    }
                    ChatBubbleCard(
                        message = msg,
                        displayedText = displayedText,
                        language = language,
                        onSpeak = { viewModel.speakMessage(msg.text, msg.mood) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(msg.text))
                        },
                        onReact = { reaction ->
                            viewModel.reactToMessage(msg.id, reaction)
                        },
                        onOpenUrl = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                if (voiceState.listening && voiceState.partialTranscript.isNotBlank()) {
                    item(key = "live_partial") {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎙️ ${voiceState.partialTranscript}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Emoji Mood Selector ("ও ইমোশনাল বোঝ" + ইমোজি দিয়ে অনুভূতি জানানো)
        SathiEmojiMoodBar(
            selectedEmotion = selectedEmotion,
            language = language,
            onSelectEmotion = { emotion -> viewModel.selectUserEmotion(emotion) }
        )

        // Quick Voice Prompts
        val suggestions = if (language == SathiLanguage.BN) {
            listOf(
                "তোমার নাম কী?",
                "খুলনার আবহাওয়া কেমন?",
                "ঢাকায় নামাজের সময়",
                "একটি ইসলামিক গল্প শোনাও",
                "ইউটিউবে ঢোকো"
            )
        } else {
            listOf(
                "What is your name?",
                "Weather in Khulna",
                "Dhaka prayer times",
                "Tell me a short story",
                "Open YouTube"
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { prompt ->
                AssistChip(
                    onClick = { viewModel.sendUserMessage(prompt, speakReply = true) },
                    label = { Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Active Screen Share Control Pill
        AnimatedVisibility(visible = screenSharing || screenReaderActive) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.ScreenShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == SathiLanguage.BN) {
                                if (screenSharing) "লাইভ স্ক্রিন শেয়ার চালু" else "স্ক্রিন ডাটা রিডার চালু"
                            } else {
                                if (screenSharing) "Screen Share Active" else "Screen Reader Active"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    AssistChip(
                        onClick = {
                            val q = typedInput.trim()
                            typedInput = ""
                            viewModel.analyzeSharedScreen(q)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (language == SathiLanguage.BN) "স্ক্রিন দেখো" else "Inspect") },
                        modifier = Modifier.testTag("analyze_screen_button")
                    )
                    if (screenSharing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { SathiScreenShareService.stopService(context) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Stop Screen Share",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Animated Gemini-Live Style Voice Orb + Auto-Voice Toggle + Thamo Interrupt + Mute/Camera/Stop
        SathiVoiceOrbBar(
            listening = voiceState.listening,
            speaking = voiceState.speaking,
            liveActive = voiceState.liveSessionActive,
            muted = voiceState.muted,
            thinking = isThinking,
            autoVoiceEnabled = localSettings.autoVoiceEnabled,
            mood = activeMood,
            language = language,
            onOrbClick = handleMicTap,
            onToggleAutoVoice = { viewModel.setAutoVoiceEnabled(!localSettings.autoVoiceEnabled) },
            onInterruptAndSpeak = { viewModel.interruptSpeakingAndListen() },
            onToggleMute = { viewModel.toggleLiveMute() },
            onOpenMedia = { showMediaSheet = true },
            onQuickCamera = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) homeCameraLauncher.launch(null)
                else homeCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onStopLive = { viewModel.stopLiveVoiceSession() }
        )

        // Input Row with + Attachment Icon (Photo & Video) + Text Box + Send
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { showMediaSheet = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("attach_media_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (language == SathiLanguage.BN) "ছবি বা ভিডিও আপলোড করুন" else "Upload photo or video"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = typedInput,
                onValueChange = { typedInput = it },
                placeholder = {
                    Text(
                        if (language == SathiLanguage.BN) "সাথীকে জিজ্ঞাসা করুন..."
                        else "Ask Sathi..."
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val toSend = typedInput.trim()
                    if (toSend.isNotEmpty()) {
                        typedInput = ""
                        viewModel.sendUserMessage(toSend, speakReply = true)
                    }
                },
                enabled = typedInput.isNotBlank() && !isThinking,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_text_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
        }
    }
}

fun toggleFloatingOverlay(
    context: Context,
    currentlyRunning: Boolean,
    onNeedPermission: () -> Unit
) {
    if (currentlyRunning) {
        context.startService(
            Intent(context, SathiFloatingService::class.java).apply {
                action = SathiFloatingService.ACTION_STOP
            }
        )
    } else if (!Settings.canDrawOverlays(context)) {
        onNeedPermission()
    } else {
        val intent = Intent(context, SathiFloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

@Composable
private fun SathiHistoryDrawerSheet(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    language: SathiLanguage,
    floatingRunning: Boolean,
    onNewChat: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onToggleFloating: () -> Unit
) {
    val pinned = sessions.filter { it.isPinned }
    val recent = sessions.filter { !it.isPinned }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == SathiLanguage.BN) "সাথী • কথোপকথন" else "Sathi Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (language == SathiLanguage.BN) "নতুন কথোপকথন শুরু করুন" else "Start New Chat")
            }

            OutlinedButton(
                onClick = onToggleFloating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BubbleChart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (language == SathiLanguage.BN) {
                        if (floatingRunning) "ভাসমান বাবল বন্ধ করুন" else "ভাসমান বাবল চালু করুন"
                    } else {
                        if (floatingRunning) "Turn Off Floating Orb" else "Enable Floating Orb"
                    }
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (pinned.isNotEmpty()) {
                item {
                    Text(
                        text = if (language == SathiLanguage.BN) "চিহ্নিত কথোপকথন" else "Pinned Chats",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                items(pinned, key = { "pin_${it.id}" }) { session ->
                    DrawerSessionRow(
                        session = session,
                        selected = session.id == activeSessionId,
                        onSelect = { onSelectSession(session) },
                        onTogglePin = { onTogglePin(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }

            item {
                Text(
                    text = if (language == SathiLanguage.BN) "সাম্প্রতিক চ্যাট" else "Recent Chats",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            if (recent.isEmpty() && pinned.isEmpty()) {
                item {
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "এখনও কোনো সংরক্ষিত চ্যাট নেই। কথা বললেই এখানে যুক্ত হবে।"
                        } else {
                            "No saved chats yet. Start talking to save conversations here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(recent, key = { "rec_${it.id}" }) { session ->
                    DrawerSessionRow(
                        session = session,
                        selected = session.id == activeSessionId,
                        onSelect = { onSelectSession(session) },
                        onTogglePin = { onTogglePin(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSessionRow(
    session: ChatSession,
    selected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onSelect,
        icon = {
            Icon(
                imageVector = if (session.isPinned) Icons.Default.Bookmark else Icons.Default.ChatBubbleOutline,
                contentDescription = null
            )
        },
        label = {
            Text(
                text = session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (session.isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Pin chat",
                        modifier = Modifier.size(18.dp),
                        tint = if (session.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete chat",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
    )
}

@Composable
private fun SathiEmojiMoodBar(
    selectedEmotion: UserEmotion?,
    language: SathiLanguage,
    onSelectEmotion: (UserEmotion) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(UserEmotion.entries, key = { it.code }) { emotion ->
            val selected = selectedEmotion == emotion
            FilterChip(
                selected = selected,
                onClick = { onSelectEmotion(emotion) },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("emotion_chip_${emotion.code}"),
                label = {
                    val labelText = if (language == SathiLanguage.BN) emotion.labelBn else emotion.labelEn
                    Text("${emotion.emoji} $labelText", maxLines = 1)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun SathiVoiceOrbBar(
    listening: Boolean,
    speaking: Boolean,
    liveActive: Boolean,
    muted: Boolean,
    thinking: Boolean,
    autoVoiceEnabled: Boolean,
    mood: SathiMood,
    language: SathiLanguage,
    onOrbClick: () -> Unit,
    onToggleAutoVoice: () -> Unit,
    onInterruptAndSpeak: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenMedia: () -> Unit,
    onQuickCamera: () -> Unit,
    onStopLive: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if ((listening && !muted) || speaking || liveActive) 1.24f else 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (listening || speaking) 620 else 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowColor = when {
        muted -> MaterialTheme.colorScheme.outline
        listening -> MaterialTheme.colorScheme.secondary
        speaking -> when (mood) {
            SathiMood.HAPPY, SathiMood.PLAYFUL -> MoodHappyGlow
            SathiMood.COMFORTING -> MoodComfortGlow
            SathiMood.SERIOUS -> MoodSeriousGlow
            SathiMood.WARM -> MoodWarmGlow
        }
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (liveActive) {
            // Gemini-Live Style Floating Pill Control Bar
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenMedia,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Share media or screen")
                    }

                    FilterChip(
                        selected = autoVoiceEnabled,
                        onClick = onToggleAutoVoice,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("auto_voice_toggle_button"),
                        label = {
                            Text(
                                if (language == SathiLanguage.BN) {
                                    if (autoVoiceEnabled) "অটো ON" else "অটো OFF"
                                } else {
                                    if (autoVoiceEnabled) "Auto ON" else "Auto OFF"
                                },
                                maxLines = 1
                            )
                        }
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.minDimension * 0.42f
                            drawCircle(color = glowColor.copy(alpha = 0.22f), radius = r * pulse)
                            drawCircle(
                                color = glowColor.copy(alpha = 0.45f),
                                radius = r * (0.88f + (pulse - 1f) * 0.5f),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                        FilledIconButton(
                            onClick = onOrbClick,
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("mic_button"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = glowColor,
                                contentColor = Color.White
                            )
                        ) {
                            when {
                                thinking -> CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                speaking -> Icon(Icons.Default.Stop, contentDescription = "Interrupt")
                                else -> Icon(Icons.Default.GraphicEq, contentDescription = "Live orb")
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mute_live_mic_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (muted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (muted) "Unmute microphone" else "Mute microphone"
                        )
                    }

                    FilledIconButton(
                        onClick = onStopLive,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("end_live_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "End Live")
                    }
                }
            }
        } else {
            // Center Voice Orb + Side Auto-Voice ON/OFF Toggle + Instant Thamo Interrupt / Quick Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (speaking) {
                    AssistChip(
                        onClick = onInterruptAndSpeak,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("interrupt_speaking_button"),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                if (language == SathiLanguage.BN) "থামো" else "Stop",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                } else {
                    FilledTonalIconButton(
                        onClick = onQuickCamera,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = if (language == SathiLanguage.BN) "ক্যামেরা" else "Camera"
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(86.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = size.minDimension * 0.34f
                        drawCircle(
                            color = glowColor.copy(alpha = 0.18f),
                            radius = baseRadius * pulse * 1.28f
                        )
                        drawCircle(
                            color = glowColor.copy(alpha = 0.35f),
                            radius = baseRadius * pulse,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    FilledIconButton(
                        onClick = onOrbClick,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("mic_button"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = glowColor,
                            contentColor = Color.White
                        )
                    ) {
                        when {
                            thinking -> CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            speaking -> Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interrupt Sathi and speak",
                                modifier = Modifier.size(30.dp)
                            )
                            listening -> Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Listening, tap to stop",
                                modifier = Modifier.size(30.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Tap to start live voice conversation",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Prominent Auto-Voice ON / OFF Toggle right beside the center button
                FilterChip(
                    selected = autoVoiceEnabled,
                    onClick = onToggleAutoVoice,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("auto_voice_toggle_button"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            if (language == SathiLanguage.BN) {
                                if (autoVoiceEnabled) "অটো ভয়েস ON" else "অটো ভয়েস OFF"
                            } else {
                                if (autoVoiceEnabled) "Auto Voice ON" else "Auto Voice OFF"
                            },
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        val statusLabel = when {
            thinking -> if (language == SathiLanguage.BN) "সাথী ভাবছে..." else "Sathi is thinking..."
            muted && liveActive -> if (language == SathiLanguage.BN) "মাইক মিউট আছে • চালু করতে মাইক চাপুন" else "Mic muted • Tap mic to unmute"
            speaking && liveActive -> if (language == SathiLanguage.BN) "বলছে — 'থামো' বললে বা ট্যাপ করলে থেমে যাবে" else "Speaking — say 'Stop' or tap to interrupt"
            speaking -> if (language == SathiLanguage.BN) "বলছে — 'থামো' চাপুন বা নতুন কথা বলুন" else "Speaking — tap Stop to interrupt"
            listening && autoVoiceEnabled -> if (language == SathiLanguage.BN) "অটো ভয়েস শুনছে... অনর্গল বলুন" else "Auto Voice listening... speak freely"
            listening -> if (language == SathiLanguage.BN) "শুনছি... বলুন" else "Listening... speak now"
            else -> if (language == SathiLanguage.BN) {
                if (autoVoiceEnabled) "মাঝের বাটনে চাপুন — অটোমেটিক চলবে"
                else "অটো ভয়েস OFF — প্রতিবার ট্যাপে শুনবে"
            } else {
                if (autoVoiceEnabled) "Tap center orb for automatic voice chat"
                else "Auto Voice OFF — tap orb for single turn"
            }
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = glowColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChatBubbleCard(
    message: ChatMessage,
    displayedText: String,
    language: SathiLanguage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onReact: (Int) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 336.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 2.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val moodLabel = if (language == SathiLanguage.BN) message.mood.labelBn else message.mood.labelEn
                        Text(
                            text = if (message.isOfflineReply) "সাথী • অফলাইন" else "সাথী • $moodLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Replay voice",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                message.mediaThumbnail?.let { thumb ->
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = message.mediaBadge ?: "Attached media",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (!message.mediaBadge.isNullOrBlank()) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = message.mediaBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = displayedText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                if (!message.actionLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "✓ ${message.actionLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (language == SathiLanguage.BN) "তথ্যসূত্র:" else "Sources:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    message.sources.forEach { src ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { onOpenUrl(src.url) }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = src.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (src.snippet.isNotBlank()) {
                                        Text(
                                            text = src.snippet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Open source link",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onReact(1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (message.reaction == 1) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = "Like response",
                                modifier = Modifier.size(18.dp),
                                tint = if (message.reaction == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onReact(-1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (message.reaction == -1) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                contentDescription = "Dislike response",
                                modifier = Modifier.size(18.dp),
                                tint = if (message.reaction == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speak aloud",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterSathiAiLogo(
    isProminent: Boolean,
    isActive: Boolean,
    language: SathiLanguage,
    availableHeight: androidx.compose.ui.unit.Dp = 240.dp,
    modifier: Modifier = Modifier
) {
    val targetAlpha = if (isProminent) 0.94f else 0.22f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 450),
        label = "center_logo_alpha"
    )
    val transition = rememberInfiniteTransition(label = "center_logo_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isActive) 1100 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center_logo_scale"
    )

    val outerSize = when {
        availableHeight < 130.dp -> 72.dp
        availableHeight < 190.dp -> 112.dp
        else -> 148.dp
    }
    val innerSize = outerSize * 0.76f
    val showPill = isProminent && availableHeight >= 150.dp

    Column(
        modifier = modifier
            .alpha(animatedAlpha)
            .testTag("center_ai_logo"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(outerSize)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x55FFF59D),
                            Color(0x4464B5F6),
                            Color.Transparent
                        )
                    )
                )
            }

            Surface(
                modifier = Modifier
                    .size(innerSize)
                    .border(
                        width = 2.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFEE58),
                                Color(0xFFFFFFFF),
                                Color(0xFF42A5F5)
                            )
                        ),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                shadowElevation = if (isProminent) 10.dp else 0.dp,
                color = Color.Transparent
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_sathi_ai_logo),
                    contentDescription = if (language == SathiLanguage.BN) "সাথী এআই লোগো" else "Sathi AI Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showPill) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFD54F),
                            Color(0xFF42A5F5)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) "সাথী এআই" else "Sathi AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

